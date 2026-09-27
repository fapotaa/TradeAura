package com.TradeAura.addon.modules;

import com.TradeAura.addon.inventory.AutoShulker;
import com.TradeAura.addon.inventory.InvHelper;
import com.TradeAura.addon.inventory.InventoryManager;
import com.TradeAura.addon.inventory.InventorySettings;
import com.TradeAura.addon.inventory.ItemRule;
import com.TradeAura.addon.inventory.MovementControl;
import com.TradeAura.addon.nav.Navigation;
import com.TradeAura.addon.safety.RateLimiter;
import com.TradeAura.addon.trading.RerollTask;
import com.TradeAura.addon.trading.TargetMode;
import com.TradeAura.addon.trading.TradeData;
import com.TradeAura.addon.trading.TradeEvaluator;
import com.TradeAura.addon.trading.TradeRule;
import com.TradeAura.addon.trading.VillagerList;
import com.TradeAura.addon.trading.VillagerUtil;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Automated villager trading.
 *
 * <h2>The loop, once per client tick</h2>
 * <ol>
 *     <li><b>Safety.</b> Rate limiter refill, pause checks (lag, health, using an item).</li>
 *     <li><b>Reroll.</b> If a reroll is in progress it owns the player until it finishes.</li>
 *     <li><b>Inventory.</b> Dump / refill / craft / drop, including the shulker jobs the trading loop asked
 *     for. While one runs, everything else stands down.</li>
 *     <li><b>Trading screen.</b> If one is open: evaluate the offers and run <b>one</b> trade, re-reading the
 *     inventory every pass, until there is nothing left to do. Then close.</li>
 *     <li><b>Navigation.</b> Nothing in reach? Walk to the nearest villager worth visiting.</li>
 *     <li><b>Aura.</b> Something in reach? Interact with it.</li>
 * </ol>
 * Navigation and the aura run in the same tick, so the module keeps trading while it walks rather than stopping
 * to think between villagers.
 */
public class TradeAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAura = settings.createGroup("Aura");
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgLists = settings.createGroup("Villager lists");
    private final SettingGroup sgReroll = settings.createGroup("Rerolling");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgInventory = settings.createGroup("Inventory manipulation");
    private final SettingGroup sgRender = settings.createGroup("Render");

    /** Every setting of the "Inventory manipulation" tab. */
    public final InventorySettings invSettings = new InventorySettings(sgInventory, this::rebuildGuiIfPossible);
    /** Runs the inventory triggers, interrupts the aura while an action is in progress. */
    private final InventoryManager invManager = new InventoryManager(this, invSettings);

    /**
     * Villagers you never want touched, and villagers you specifically want worked.
     * <p>
     * Declared here rather than further down with the other rule lists because the keybind settings below
     * capture them in a lambda, and a field initializer may not reference a field declared after it.
     */
    private final VillagerList whitelist = new VillagerList();
    private final VillagerList blacklist = new VillagerList();

    // ---------------------------------------------------------------------------------------------------
    // General
    // ---------------------------------------------------------------------------------------------------

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print what the module decides for every offer, every target and every shulker job. Noisy, but it is the fastest way to find out why something is not being traded.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> close = sgGeneral.add(new BoolSetting.Builder()
        .name("close-screen")
        .description("Close the trading screen once there is nothing left to trade. Turn this off only if you want to inspect what the module saw; with it off the module stops working that villager and waits for you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cancelEvent = sgGeneral.add(new BoolSetting.Builder()
        .name("cancel-event")
        .description("Do not render the villager trading screen. The trade still happens - only the window is suppressed, so the screen does not flash open and shut while the aura works a hall.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> ticksToClose = sgGeneral.add(new IntSetting.Builder()
        .name("ticks-to-close")
        .description("Ticks to wait before closing a finished trading screen. 0 closes immediately, which is what you want while moving; raise it only if your server dislikes fast open/close cycles.")
        .defaultValue(0)
        .min(0)
        .sliderMax(40)
        .visible(close::get)
        .build()
    );

    private final Setting<Integer> maxTradesPerVillager = sgGeneral.add(new IntSetting.Builder()
        .name("max-trades-per-villager")
        .description("How many trades to run with one villager before moving on. The villager is revisited later, so a low value spreads your buying across the hall while a high value drains one villager completely.")
        .defaultValue(12)
        .min(1)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Integer> tradeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("trade-delay")
        .description("Ticks between two trades with the same villager. 1 is the minimum, so the inventory the next decision reads is the one the server just confirmed. Raise it on a laggy server.")
        .defaultValue(1)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> offersTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("offers-timeout")
        .description("How long to keep a trading screen open while waiting for the server to send the trade list before giving up on that villager.")
        .defaultValue(40)
        .min(5)
        .sliderMax(200)
        .build()
    );

    // ---------------------------------------------------------------------------------------------------
    // Aura
    // ---------------------------------------------------------------------------------------------------

    private final Setting<Boolean> aura = sgAura.add(new BoolSetting.Builder()
        .name("villager-aura")
        .description("Automatically interact with villagers in range. Without this the module only completes trades on villagers you click yourself.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> interactDelay = sgAura.add(new IntSetting.Builder()
        .name("interact-delay")
        .description("Ticks between two villager interactions. This is the single biggest lever on how fast the module works a hall: at 2 it can start a new villager ten times a second, at 10 you have to walk slowly for it to keep up. Villagers are scanned every tick regardless, so this only paces the clicking.")
        .defaultValue(2)
        .min(1)
        .sliderMax(40)
        .visible(aura::get)
        .build()
    );

    private final Setting<Boolean> rotateToVillager = sgAura.add(new BoolSetting.Builder()
        .name("rotate-to-villager")
        .description("Look at the villager before interacting. The interaction is sent from inside the rotation callback, so the server already sees the new angle when the click arrives. Turning this off is faster but far more obvious to anti-cheat.")
        .defaultValue(true)
        .visible(aura::get)
        .build()
    );

    private final Setting<Integer> forget = sgAura.add(new IntSetting.Builder()
        .name("revisit-delay")
        .description("Ticks before a villager the module already handled is considered again. Low values re-open the same villager constantly; high values make the module ignore a villager that has since restocked.")
        .defaultValue(40)
        .min(5)
        .sliderMax(1200)
        .visible(aura::get)
        .build()
    );

    private final Setting<Boolean> refreshUnsynced = sgAura.add(new BoolSetting.Builder()
        .name("retry-unanswered")
        .description("If a villager never answered with its trade list, interact with it again instead of leaving it alone. Interactions do get eaten, especially while moving.")
        .defaultValue(true)
        .visible(aura::get)
        .build()
    );

    private final Setting<Integer> refreshDelay = sgAura.add(new IntSetting.Builder()
        .name("retry-delay")
        .description("Ticks to give the server to answer before retrying an unanswered villager.")
        .defaultValue(10)
        .min(1)
        .sliderMax(200)
        .visible(() -> aura.get() && refreshUnsynced.get())
        .build()
    );

    private final Setting<Integer> maxRefreshes = sgAura.add(new IntSetting.Builder()
        .name("max-retries")
        .description("How often the same villager may be retried in a row before it is left alone until the revisit delay expires.")
        .defaultValue(3)
        .min(1)
        .sliderMax(20)
        .visible(() -> aura.get() && refreshUnsynced.get())
        .build()
    );

    // ---------------------------------------------------------------------------------------------------
    // Movement
    // ---------------------------------------------------------------------------------------------------

    private final Setting<Navigation.Mode> movementMode = sgMovement.add(new EnumSetting.Builder<Navigation.Mode>()
        .name("movement-mode")
        .description("Off: never move you, trade only with whatever is already in reach. Pathfind: use the installed path manager (Baritone or Voyager) to walk to villagers - handles stairs, doors and obstacles. Simple: press the movement keys towards the target and jump at obstacles, no dependencies, fine in an open flat hall.")
        .defaultValue(Navigation.Mode.Off)
        .build()
    );

    private final Setting<Double> searchRadius = sgMovement.add(new DoubleSetting.Builder()
        .name("search-radius")
        .description("How far to look for the next villager to walk to. Trading still only happens inside 'range'; this is purely how far the module is willing to travel for the next one.")
        .defaultValue(24.0)
        .min(2)
        .sliderRange(2, 96)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Double> stopDistance = sgMovement.add(new DoubleSetting.Builder()
        .name("stop-distance")
        .description("Stop walking once you are this close to the villager. Keep it a little under 'range' so the villager is comfortably in reach when you arrive instead of right on the edge.")
        .defaultValue(3.0)
        .min(1)
        .sliderRange(1, 6)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Double> leashRadius = sgMovement.add(new DoubleSetting.Builder()
        .name("leash-radius")
        .description("Never walk further than this from the anchor. This is what keeps the module inside your trading hall instead of following a wandering villager across the map. 0 removes the leash entirely.")
        .defaultValue(48.0)
        .min(0)
        .sliderRange(0, 256)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Boolean> anchorOnEnable = sgMovement.add(new BoolSetting.Builder()
        .name("anchor-on-enable")
        .description("Set the anchor to where you are standing when the module is switched on. Turn this off to keep an anchor you placed by hand with the keybind below.")
        .defaultValue(true)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Keybind> setAnchorBind = sgMovement.add(new KeybindSetting.Builder()
        .name("set-anchor-key")
        .description("Sets the anchor to where you are standing right now.")
        .defaultValue(Keybind.none())
        .action(this::setAnchorHere)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Boolean> returnToAnchor = sgMovement.add(new BoolSetting.Builder()
        .name("return-to-anchor")
        .description("Walk back to the anchor when there is nothing left to do, instead of standing wherever the last villager happened to be.")
        .defaultValue(false)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Integer> repathInterval = sgMovement.add(new IntSetting.Builder()
        .name("repath-interval")
        .description("Minimum ticks between two path requests for the same villager. Re-issuing a goal every tick makes a pathfinder recalculate constantly and the player stutters in place.")
        .defaultValue(10)
        .min(1)
        .sliderMax(60)
        .visible(() -> movementMode.get() == Navigation.Mode.Pathfind)
        .build()
    );

    private final Setting<Integer> travelTimeout = sgMovement.add(new IntSetting.Builder()
        .name("travel-timeout")
        .description("Give up on a villager after this many ticks of walking towards it. Stops the module from grinding against a wall forever when a villager is behind glass.")
        .defaultValue(120)
        .min(20)
        .sliderMax(600)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Integer> unreachableCooldown = sgMovement.add(new IntSetting.Builder()
        .name("unreachable-cooldown")
        .description("How long a villager the module could not reach is ignored before it is tried again.")
        .defaultValue(600)
        .min(20)
        .sliderMax(6000)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Integer> manualOverride = sgMovement.add(new IntSetting.Builder()
        .name("manual-override-ticks")
        .description("The moment you touch a movement key the module lets go of the controls for this many ticks. This is what lets you walk through your own hall while it trades, instead of fighting it for the keyboard. 0 disables the override.")
        .defaultValue(20)
        .min(0)
        .sliderMax(200)
        .visible(() -> movementMode.get() != Navigation.Mode.Off)
        .build()
    );

    private final Setting<Boolean> cancelMovement = sgMovement.add(new BoolSetting.Builder()
        .name("freeze-while-trading")
        .description("Block your own movement input while a villager the aura can click is in range, so you cannot walk out of range mid trade. Only available with movement-mode Off, because the other modes already control where you stand.")
        .defaultValue(false)
        .visible(() -> movementMode.get() == Navigation.Mode.Off)
        .build()
    );

    private final Setting<Integer> ticksToCancelMovement = sgMovement.add(new IntSetting.Builder()
        .name("freeze-window")
        .description("Freeze only when fewer than this many ticks remain until the next interaction. Keep it clearly below 'interact-delay' or every tick falls inside the window and you are frozen the whole time.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .visible(() -> movementMode.get() == Navigation.Mode.Off && cancelMovement.get())
        .build()
    );

    private final Setting<Boolean> cancelMovementNearVillager = sgMovement.add(new BoolSetting.Builder()
        .name("freeze-whole-time")
        .description("Freeze the entire time a villager is in range rather than only inside the window above.")
        .defaultValue(false)
        .visible(() -> movementMode.get() == Navigation.Mode.Off && cancelMovement.get())
        .build()
    );

    // ---------------------------------------------------------------------------------------------------
    // Targeting
    // ---------------------------------------------------------------------------------------------------

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to pick a villager when several are in reach at once. ClosestAngle favours whatever you are already looking at, LowestDistance the nearest one.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance at which a villager is interacted with. The server rejects interactions beyond your reach, so anything much above 4.5 only wastes packets and produces failed clicks.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("max-targets")
        .description("How many entities to consider per scan. Only worth lowering on a machine that struggles in a village with hundreds of villagers.")
        .defaultValue(128)
        .min(1)
        .sliderRange(1, 512)
        .build()
    );

    private final Setting<Boolean> requireLineOfSight = sgTargeting.add(new BoolSetting.Builder()
        .name("require-line-of-sight")
        .description("Skip villagers you cannot actually see. Clicking through a wall is always rejected by the server, and every rejected click then gets retried as an unanswered villager. Turn this off only for trading cells where the villager is behind a block you can still reach past.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smartTargeting = sgTargeting.add(new BoolSetting.Builder()
        .name("profession-filter")
        .description("Use the villager's real profession to skip ones that can never offer what you configured, so a librarian is not opened when every rule you wrote is about wool. Items the built-in table does not know are always allowed through, so modded trades are never skipped by mistake.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> skipNonTrading = sgTargeting.add(new BoolSetting.Builder()
        .name("skip-babies-and-nitwits")
        .description("Skip baby villagers, nitwits and unemployed villagers. None of them can trade, so opening them is pure waste - unless you are rerolling, where an unemployed villager is exactly the point.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> skipBusy = sgTargeting.add(new BoolSetting.Builder()
        .name("skip-busy-villagers")
        .description("Skip villagers another player is already trading with; the server refuses the interaction anyway.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wanderingTraders = sgTargeting.add(new BoolSetting.Builder()
        .name("wandering-traders")
        .description("Also trade with wandering traders. They have no profession, so the profession filter never applies to them and they are never rerolled.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minLevel = sgTargeting.add(new IntSetting.Builder()
        .name("min-level")
        .description("Only trade with villagers of at least this level (1 novice, 5 master). Useful to ignore fresh villagers whose good trades are still locked.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> maxLevel = sgTargeting.add(new IntSetting.Builder()
        .name("max-level")
        .description("Only trade with villagers up to this level.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    // ---------------------------------------------------------------------------------------------------
    // Villager lists
    // ---------------------------------------------------------------------------------------------------

    private final Setting<TargetMode> targetMode = sgLists.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("Everyone: trade with anything not blacklisted. WhitelistOnly: trade only with the villagers you picked. PreferWhitelist: work through the whitelist first, then fall back to everyone else. The blacklist always applies, in every mode.")
        .defaultValue(TargetMode.Everyone)
        .onChanged(v -> rebuildGuiIfPossible())
        .build()
    );

    private final Setting<Keybind> whitelistBind = sgLists.add(new KeybindSetting.Builder()
        .name("whitelist-key")
        .description("Adds the villager you are looking at to the whitelist, or removes it if it is already on it. Look at the villager and press the key.")
        .defaultValue(Keybind.none())
        .action(() -> toggleList(whitelist, "whitelist"))
        .build()
    );

    private final Setting<Keybind> blacklistBind = sgLists.add(new KeybindSetting.Builder()
        .name("blacklist-key")
        .description("Adds the villager you are looking at to the blacklist, or removes it if it is already on it.")
        .defaultValue(Keybind.none())
        .action(() -> toggleList(blacklist, "blacklist"))
        .build()
    );

    private final Setting<Boolean> announceListChanges = sgLists.add(new BoolSetting.Builder()
        .name("announce-list-changes")
        .description("Print a chat line whenever a villager is added to or removed from a list, so you can tell whether the key press landed on the villager you meant.")
        .defaultValue(true)
        .build()
    );

    // ---------------------------------------------------------------------------------------------------
    // Rerolling
    // ---------------------------------------------------------------------------------------------------

    private final Setting<Boolean> rerollEnabled = sgReroll.add(new BoolSetting.Builder()
        .name("reroll-villagers")
        .description("When a villager has nothing you want, break and replace its workstation so it rolls a new trade list. Only ever done to villagers that have never been traded with, because a single completed trade locks a villager's offers forever.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rerollOnPrice = sgReroll.add(new BoolSetting.Builder()
        .name("reroll-on-price")
        .description("Also reroll when the villager does offer what you want but above your price limit. This is how you roll for a cheap enchanted book.")
        .defaultValue(true)
        .visible(rerollEnabled::get)
        .build()
    );

    private final Setting<Integer> maxRerolls = sgReroll.add(new IntSetting.Builder()
        .name("max-rerolls")
        .description("How often the same villager may be rerolled before the module gives up on it.")
        .defaultValue(20)
        .min(1)
        .sliderMax(500)
        .visible(rerollEnabled::get)
        .build()
    );

    private final Setting<Double> rerollRadius = sgReroll.add(new DoubleSetting.Builder()
        .name("workstation-radius")
        .description("How far from the villager to look for its workstation block. The block also has to be within your own reach.")
        .defaultValue(3.0)
        .min(1)
        .sliderRange(1, 6)
        .visible(rerollEnabled::get)
        .build()
    );

    private final Setting<Integer> rerollSettle = sgReroll.add(new IntSetting.Builder()
        .name("reroll-settle-ticks")
        .description("Ticks to wait after breaking and again after replacing the workstation. Too low and the villager simply re-claims the block with the same trades, which looks like the reroll silently not working.")
        .defaultValue(20)
        .min(5)
        .sliderMax(200)
        .visible(rerollEnabled::get)
        .build()
    );

    private final Setting<Integer> rerollTimeout = sgReroll.add(new IntSetting.Builder()
        .name("reroll-timeout")
        .description("Ticks a single reroll step may take before the attempt is abandoned.")
        .defaultValue(60)
        .min(20)
        .sliderMax(400)
        .visible(rerollEnabled::get)
        .build()
    );

    // ---------------------------------------------------------------------------------------------------
    // Safety
    // ---------------------------------------------------------------------------------------------------

    private final Setting<Integer> packetsPerSecond = sgSafety.add(new IntSetting.Builder()
        .name("packets-per-second")
        .description("Upper limit for every packet this module sends - trades, inventory clicks, crafting, shulker transfers and drops all draw from one shared budget. Lower is safer and slower.")
        .defaultValue(40)
        .min(4)
        .sliderRange(4, 200)
        .build()
    );

    private final Setting<Integer> packetBurst = sgSafety.add(new IntSetting.Builder()
        .name("packet-burst")
        .description("How many packets may be sent back to back before the per-second limit takes over. A shulker transfer needs a handful at once, so do not set this too low.")
        .defaultValue(24)
        .min(2)
        .sliderRange(2, 128)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgSafety.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Stand still while the server is not keeping up. Trading against a lagging server is the fastest way to a desynced inventory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minTickRate = sgSafety.add(new DoubleSetting.Builder()
        .name("min-tick-rate")
        .description("Pause below this server tick rate. 20 is a healthy server.")
        .defaultValue(12.0)
        .min(1)
        .sliderRange(1, 20)
        .visible(pauseOnLag::get)
        .build()
    );

    private final Setting<Boolean> pauseWhileUsing = sgSafety.add(new BoolSetting.Builder()
        .name("pause-while-using-items")
        .description("Do nothing while you are eating, drinking or breaking a block, so the module does not interrupt you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnLowHealth = sgSafety.add(new BoolSetting.Builder()
        .name("pause-on-low-health")
        .description("Stop trading when your health drops below the threshold, so you are not stuck in a trading screen while something is hitting you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Health to pause at.")
        .defaultValue(8.0)
        .min(1)
        .sliderRange(1, 20)
        .visible(pauseOnLowHealth::get)
        .build()
    );

    private final Setting<Boolean> disableWhenIdle = sgSafety.add(new BoolSetting.Builder()
        .name("disable-when-idle")
        .description("Turn the module off once every rule has hit its limit and there is nothing left to do anywhere in range. Handy for leaving it running until your emerald target is met.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> idleTicks = sgSafety.add(new IntSetting.Builder()
        .name("idle-ticks")
        .description("How long nothing may happen before the module turns itself off.")
        .defaultValue(600)
        .min(100)
        .sliderMax(6000)
        .visible(disableWhenIdle::get)
        .build()
    );

    // ---------------------------------------------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------------------------------------------

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw a box around villagers the module handled, coloured by what happened. The single most useful setting for working out why a villager is being skipped.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> fillOpacity = sgRender.add(new DoubleSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .visible(render::get)
        .defaultValue(0.3)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    private final Setting<Boolean> renderLists = sgRender.add(new BoolSetting.Builder()
        .name("highlight-lists")
        .description("Always outline whitelisted and blacklisted villagers in range, even when the module has not touched them yet, so you can see your lists at a glance.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> defaultColor = sgRender.add(new ColorSetting.Builder()
        .name("default-color")
        .description("Waiting for the server to answer.")
        .defaultValue(new SettingColor(160, 160, 160))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> noEmeraldColor = sgRender.add(new ColorSetting.Builder()
        .name("no-emerald-color")
        .description("Not enough emeralds for the trade.")
        .defaultValue(new SettingColor(0, 170, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> noSellItemsColor = sgRender.add(new ColorSetting.Builder()
        .name("no-sell-item-color")
        .description("Not enough of the items the trade asks for.")
        .defaultValue(new SettingColor(200, 200, 200))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> noTradesColor = sgRender.add(new ColorSetting.Builder()
        .name("no-trades-color")
        .description("None of the villager's offers matches a rule.")
        .defaultValue(new SettingColor(255, 45, 45))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> disabledTradeColor = sgRender.add(new ColorSetting.Builder()
        .name("out-of-stock-color")
        .description("The trade is locked until the villager restocks.")
        .defaultValue(new SettingColor(255, 210, 0))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> tooExpensiveColor = sgRender.add(new ColorSetting.Builder()
        .name("too-expensive-color")
        .description("Above the configured price.")
        .defaultValue(new SettingColor(255, 0, 150))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> limitReachedColor = sgRender.add(new ColorSetting.Builder()
        .name("limit-reached-color")
        .description("The per-item inventory limit has been reached.")
        .defaultValue(new SettingColor(255, 140, 0))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> inventoryFullColor = sgRender.add(new ColorSetting.Builder()
        .name("inventory-full-color")
        .description("The result would not fit into the inventory. With auto-shulker on, this also means a storage job was just requested.")
        .defaultValue(new SettingColor(150, 75, 0))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> skippedColor = sgRender.add(new ColorSetting.Builder()
        .name("skipped-color")
        .description("Skipped by the profession filter or a list, without ever being opened.")
        .defaultValue(new SettingColor(90, 90, 120))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> rerollColor = sgRender.add(new ColorSetting.Builder()
        .name("reroll-color")
        .description("Currently being rerolled.")
        .defaultValue(new SettingColor(190, 0, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> tradedColor = sgRender.add(new ColorSetting.Builder()
        .name("traded-color")
        .description("A trade went through.")
        .defaultValue(new SettingColor(45, 255, 45))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> whitelistColor = sgRender.add(new ColorSetting.Builder()
        .name("whitelist-color")
        .description("Outline for whitelisted villagers.")
        .defaultValue(new SettingColor(80, 255, 160, 120))
        .visible(() -> render.get() && renderLists.get())
        .build()
    );

    private final Setting<SettingColor> blacklistColor = sgRender.add(new ColorSetting.Builder()
        .name("blacklist-color")
        .description("Outline for blacklisted villagers.")
        .defaultValue(new SettingColor(255, 60, 60, 120))
        .visible(() -> render.get() && renderLists.get())
        .build()
    );

    private final Setting<SettingColor> navTargetColor = sgRender.add(new ColorSetting.Builder()
        .name("nav-target-color")
        .description("Outline for the villager currently being walked to.")
        .defaultValue(new SettingColor(0, 200, 255))
        .visible(render::get)
        .build()
    );

    // ---------------------------------------------------------------------------------------------------
    // Rules and lists
    // ---------------------------------------------------------------------------------------------------

    private final List<TradeRule> buyRules = new ArrayList<>();
    private final List<TradeRule> sellRules = new ArrayList<>();

    private GuiTheme lastTheme;
    private WVerticalList lastList;

    // ---------------------------------------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------------------------------------

    /** What the module last decided about a villager, used for the render colours and the revisit delay. */
    private static final class TargetState {
        int age;
        Color color;
        boolean synced;

        TargetState(Color color) {
            this.color = color;
        }
    }

    private final List<Entity> targets = new ArrayList<>();
    private final List<Entity> scratch = new ArrayList<>();
    private final Map<Entity, TargetState> handled = new HashMap<>();
    private final Map<Entity, Integer> refreshCount = new HashMap<>();
    private final Map<Entity, Integer> rerollCount = new HashMap<>();
    /** Villagers the navigation could not reach, with the tick count until they are tried again. */
    private final Map<Entity, Integer> unreachable = new HashMap<>();

    private final RateLimiter limiter = new RateLimiter(40, 24);
    private final Navigation navigation = new Navigation();
    private final AutoShulker autoShulker = new AutoShulker(invSettings, this::buyItems, this::sellItems);

    private Entity pendingVillager;
    private Entity activeVillager;
    private Entity navTarget;

    private boolean offersReady;
    private int screenTicks;
    private int tradeCooldown;
    private int tradesThisVisit;
    private int closeTicks;
    private boolean closing;
    private boolean finished;

    private RerollTask reroll;

    private int auraTicker;
    private int idleTicker;

    /**
     * Per-tick caches.
     * <p>
     * The profession pre-check and the "is anything in reach" scan are both asked several times per tick, once
     * per villager in the first case. Computing them once at the top of the tick turns thousands of map lookups
     * per tick into a handful.
     */
    private final java.util.Set<String> viableProfessions = new java.util.HashSet<>();
    private boolean interactableInRange;

    public TradeAura(Category category) {
        super(category, "Trade-Aura", "Trades with villagers for you.");
        invManager.setAutoShulker(autoShulker);
    }

    public boolean isDebug() {
        return debug.get();
    }

    /** Every item any buy rule mentions - what the module accumulates, and therefore what it stores away. */
    private List<Item> buyItems() {
        return itemsOf(buyRules);
    }

    /** Every item any sell rule mentions - the stock the module hands to villagers, and therefore refills. */
    private List<Item> sellItems() {
        return itemsOf(sellRules);
    }

    private static List<Item> itemsOf(List<TradeRule> rules) {
        List<Item> items = new ArrayList<>();

        for (TradeRule rule : rules) {
            for (Item item : rule.items) {
                if (!items.contains(item)) items.add(item);
            }
        }

        return items;
    }

    // ---------------------------------------------------------------------------------------------------
    // GUI
    // ---------------------------------------------------------------------------------------------------

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        lastTheme = theme;
        lastList = list;
        rebuildGui(theme, list);
        return list;
    }

    private void rebuildGuiIfPossible() {
        if (lastTheme != null && lastList != null) rebuildGui(lastTheme, lastList);
    }

    private void rebuildGui(GuiTheme theme, WVerticalList rootList) {
        rootList.clear();

        addTradeRuleSection(theme, rootList, "Buy Rules", buyRules, "Max Price", "Buy Limit", "Add Buy Rule");
        rootList.add(theme.horizontalSeparator()).expandX();
        addTradeRuleSection(theme, rootList, "Sell Rules", sellRules, "Max Sell Qty", "Emerald Limit", "Add Sell Rule");

        addVillagerListSection(theme, rootList, "Whitelist", whitelist, "whitelist");
        addVillagerListSection(theme, rootList, "Blacklist", blacklist, "blacklist");

        if (!invSettings.enabled.get()) return;

        if (invSettings.dropEnabled.get()) {
            addItemRuleSection(theme, rootList, "Drop Rules", invSettings.dropRules,
                "Drop above", "Keep", "Add Drop Rule", 64, 32);
        }

        if (invSettings.dumpEnabled.get()) {
            addItemRuleSection(theme, rootList, "Dump Rules", invSettings.dumpRules,
                "Dump above", "Keep", "Add Dump Rule", 64, 32);
        }

        if (invSettings.refillEnabled.get()) {
            addItemRuleSection(theme, rootList, "Refill Rules", invSettings.refillRules,
                "Refill below", "Fill to", "Add Refill Rule", 32, 64);
        }
    }

    private void addTradeRuleSection(GuiTheme theme, WVerticalList rootList, String title, List<TradeRule> rules,
                                     String labelA, String labelB, String addLabel) {
        WSection section = rootList.add(theme.section(title, true)).expandX().widget();
        WTable table = section.add(theme.table()).expandX().widget();

        table.add(theme.label("Items")).expandX();
        table.add(theme.label(labelA)).minWidth(70);
        table.add(theme.label(labelB)).minWidth(70);
        table.add(theme.label(""));
        table.row();

        for (TradeRule rule : rules) {
            table.add(theme.settings(itemPicker(rule.items))).expandX().top();

            WIntEdit editA = table.add(theme.intEdit(rule.limitA, -1, 10000, false)).minWidth(70).top().widget();
            editA.action = () -> rule.limitA = editA.get();

            WIntEdit editB = table.add(theme.intEdit(rule.limitB, -1, 10000, false)).minWidth(70).top().widget();
            editB.action = () -> rule.limitB = editB.get();

            WMinus removeBtn = table.add(theme.minus()).top().widget();
            removeBtn.action = () -> {
                rules.remove(rule);
                rebuildGui(theme, rootList);
            };

            table.row();
        }

        rootList.add(theme.button(addLabel)).expandX().widget().action = () -> {
            rules.add(new TradeRule(-1, -1));
            rebuildGui(theme, rootList);
        };
    }

    /**
     * One villager list, with a button that adds whatever you are looking at.
     * <p>
     * The button and the keybind do the same thing; the button is there because a keybind you have to set up
     * first is a poor way to discover that the feature exists at all.
     */
    private void addVillagerListSection(GuiTheme theme, WVerticalList rootList, String title, VillagerList list, String id) {
        rootList.add(theme.horizontalSeparator()).expandX();

        WSection section = rootList.add(theme.section(title + " (" + list.size() + ")", !list.isEmpty())).expandX().widget();
        WTable table = section.add(theme.table()).expandX().widget();

        if (list.isEmpty()) {
            table.add(theme.label("empty - look at a villager and press the button below")).expandX();
            table.row();
        }
        else {
            table.add(theme.label("Villager")).expandX();
            table.add(theme.label("Where")).minWidth(110);
            table.add(theme.label(""));
            table.row();

            for (VillagerList.Entry entry : new ArrayList<>(list.entries())) {
                table.add(theme.label(entry.label())).expandX();
                table.add(theme.label(entry.pos() == null ? "-" : entry.pos().toShortString())).minWidth(110);

                WMinus removeBtn = table.add(theme.minus()).widget();
                removeBtn.action = () -> {
                    list.remove(entry.uuid());
                    rebuildGui(theme, rootList);
                };

                table.row();
            }
        }

        rootList.add(theme.button("Add villager you are looking at to " + title.toLowerCase())).expandX().widget().action = () -> {
            toggleList(list, id);
            rebuildGui(theme, rootList);
        };

        if (!list.isEmpty()) {
            rootList.add(theme.button("Clear " + title.toLowerCase())).expandX().widget().action = () -> {
                list.clear();
                rebuildGui(theme, rootList);
            };
        }
    }

    private void addItemRuleSection(GuiTheme theme, WVerticalList rootList, String title, List<ItemRule> rules,
                                    String triggerLabel, String leaveLabel, String addLabel,
                                    int defaultTrigger, int defaultLeave) {
        rootList.add(theme.horizontalSeparator()).expandX();

        WSection section = rootList.add(theme.section(title, true)).expandX().widget();
        WTable table = section.add(theme.table()).expandX().widget();

        table.add(theme.label("Items")).expandX();
        table.add(theme.label(triggerLabel)).minWidth(70);
        table.add(theme.label(leaveLabel)).minWidth(70);
        table.add(theme.label(""));
        table.row();

        for (ItemRule rule : rules) {
            table.add(theme.settings(itemPicker(rule.items))).expandX().top();

            WIntEdit triggerEdit = table.add(theme.intEdit(rule.trigger, 0, 10000, false)).minWidth(70).top().widget();
            triggerEdit.action = () -> rule.trigger = triggerEdit.get();

            WIntEdit leaveEdit = table.add(theme.intEdit(rule.leave, 0, 10000, false)).minWidth(70).top().widget();
            leaveEdit.action = () -> rule.leave = leaveEdit.get();

            WMinus removeBtn = table.add(theme.minus()).top().widget();
            removeBtn.action = () -> {
                rules.remove(rule);
                rebuildGui(theme, rootList);
            };

            table.row();
        }

        rootList.add(theme.button(addLabel)).expandX().widget().action = () -> {
            rules.add(new ItemRule(defaultTrigger, defaultLeave));
            rebuildGui(theme, rootList);
        };
    }

    /** A throwaway {@link Settings} holding a single item list, so meteor's item picker can be reused per row. */
    private Settings itemPicker(List<Item> backing) {
        Setting<List<Item>> itemSetting = new ItemListSetting.Builder()
            .name("items")
            .description("Items for this rule")
            .defaultValue(new ArrayList<>(backing))
            .onChanged(items -> {
                backing.clear();
                backing.addAll(items);
            })
            .build();

        Settings dummySettings = new Settings();
        SettingGroup hiddenGroup = dummySettings.createGroup("");
        hiddenGroup.sectionExpanded = true;
        hiddenGroup.add(itemSetting);

        return dummySettings;
    }

    // ---------------------------------------------------------------------------------------------------
    // Villager lists
    // ---------------------------------------------------------------------------------------------------

    /** Adds or removes whatever the crosshair is on. Bound to a key and to the GUI buttons. */
    private void toggleList(VillagerList list, String listName) {
        Entity entity = mc.targetedEntity;

        if (entity == null) {
            if (announceListChanges.get()) warning("Look at a villager first, nothing is under your crosshair.");
            return;
        }

        if (!VillagerUtil.isMerchant(entity, true)) {
            if (announceListChanges.get()) warning("That is not a villager.");
            return;
        }

        boolean added = list.toggle(entity, null);
        VillagerList.Entry entry = list.get(entity.getUuid());
        String label = entry != null ? entry.label() : "villager";

        // A villager cannot usefully be on both lists.
        if (added) {
            VillagerList other = list == whitelist ? blacklist : whitelist;
            other.remove(entity);
        }

        if (announceListChanges.get()) {
            info((added ? "Added " : "Removed ") + label + (added ? " to the " : " from the ") + listName + ".");
        }

        rebuildGuiIfPossible();
    }

    private void setAnchorHere() {
        if (mc.player == null) return;

        navigation.setAnchor(mc.player.getBlockPos());
        info("Anchor set to " + mc.player.getBlockPos().toShortString() + ".");
    }

    // ---------------------------------------------------------------------------------------------------
    // Serialization
    // ---------------------------------------------------------------------------------------------------

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        tag.put("buyRules", TradeRule.listToTag(buyRules));
        tag.put("sellRules", TradeRule.listToTag(sellRules));
        tag.put("dropRules", ItemRule.listToTag(invSettings.dropRules));
        tag.put("dumpRules", ItemRule.listToTag(invSettings.dumpRules));
        tag.put("refillRules", ItemRule.listToTag(invSettings.refillRules));
        tag.put("whitelist", whitelist.toTag());
        tag.put("blacklist", blacklist.toTag());
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);

        // Every list is cleared even when its tag is missing, so switching profiles cannot leave entries from
        // the previous profile behind.
        readRules(tag, "buyRules", buyRules);
        readRules(tag, "sellRules", sellRules);
        readItemRules(tag, "dropRules", invSettings.dropRules);
        readItemRules(tag, "dumpRules", invSettings.dumpRules);
        readItemRules(tag, "refillRules", invSettings.refillRules);
        readVillagers(tag, "whitelist", whitelist);
        readVillagers(tag, "blacklist", blacklist);

        rebuildGuiIfPossible();
        return this;
    }

    private void readRules(NbtCompound tag, String key, List<TradeRule> into) {
        if (tag.get(key) instanceof NbtList list) TradeRule.listFromTag(list, into);
        else into.clear();
    }

    private void readItemRules(NbtCompound tag, String key, List<ItemRule> into) {
        if (tag.get(key) instanceof NbtList list) ItemRule.listFromTag(list, into);
        else into.clear();
    }

    private void readVillagers(NbtCompound tag, String key, VillagerList into) {
        if (tag.get(key) instanceof NbtList list) into.fromTag(list);
        else into.clear();
    }

    // ---------------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------------

    @Override
    public void onActivate() {
        resetState();

        InvHelper.setRateLimiter(limiter);
        limiter.configure(packetsPerSecond.get(), packetBurst.get());
        limiter.reset();

        invManager.reset();
        invManager.reportConflicts();

        navigation.reset();
        if (anchorOnEnable.get() && mc.player != null) navigation.setAnchor(mc.player.getBlockPos());
        else navigation.anchorHereIfUnset();

        if (buyRules.isEmpty() && sellRules.isEmpty()) {
            warning("No buy or sell rules are configured, nothing will be traded. Add them in the module's GUI.");
        }

        if (movementMode.get() == Navigation.Mode.Pathfind && !Navigation.pathingAvailable()) {
            warning("Movement mode is Pathfind but no path manager is installed; falling back to Simple steering. Install Baritone for real pathfinding.");
        }

        if (debug.get()) info("Shulkers: " + autoShulker.describeShulkers());
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            InvHelper.closeScreen();
        }

        if (reroll != null) {
            reroll.cleanup();
            reroll = null;
        }

        resetState();
        invManager.reset();
        navigation.reset();
        MovementControl.stop();
        InvHelper.setRateLimiter(null);
    }

    private void resetState() {
        targets.clear();
        scratch.clear();
        handled.clear();
        refreshCount.clear();
        rerollCount.clear();
        unreachable.clear();

        pendingVillager = null;
        activeVillager = null;
        navTarget = null;
        offersReady = false;
        screenTicks = 0;
        tradeCooldown = 0;
        tradesThisVisit = 0;
        closeTicks = 0;
        closing = false;
        finished = false;
        auraTicker = 0;
        idleTicker = 0;

        autoShulker.clearRequests();
    }

    // ---------------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------------

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof MerchantScreen) {
            if (cancelEvent.get()) event.cancel();
            return;
        }

        if (!invSettings.cancelScreens.get() || !invManager.isBusy()) return;
        if (!(event.screen instanceof HandledScreen<?> screen)) return;

        if (screen.getScreenHandler() instanceof ShulkerBoxScreenHandler
            || screen.getScreenHandler() instanceof CraftingScreenHandler) {
            event.cancel();
        }
    }

    /**
     * The trade list arrived. Nothing is traded here on purpose: the packet is received off the main thread and
     * the client inventory has not caught up yet. All decisions happen in {@link #onTick(TickEvent.Pre)}.
     */
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof SetTradeOffersS2CPacket)) return;

        mc.execute(() -> {
            if (mc.player == null) return;
            if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler)) return;

            offersReady = true;
            screenTicks = 0;
            tradesThisVisit = 0;
            closing = false;
            closeTicks = 0;
            finished = false;

            if (pendingVillager != null) {
                activeVillager = pendingVillager;
                pendingVillager = null;

                TargetState state = handled.get(activeVillager);
                if (state != null) state.synced = true;
                refreshCount.remove(activeVillager);
            }
        });
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;

        limiter.configure(packetsPerSecond.get(), packetBurst.get());
        limiter.tick();
        autoShulker.tick();

        rebuildViableProfessions();
        ageTargets();

        // Polled every tick so a key press is never missed, even on ticks the module does nothing else.
        boolean driving = navigation.playerIsDriving(manualOverride.get());

        if (paused()) {
            navigation.release();
            return;
        }

        if (reroll != null) {
            navigation.release();
            tickReroll();
            return;
        }

        boolean invRuns = aura.get() || invSettings.runWithoutAura.get();
        if (invRuns && invManager.tick()) {
            auraTicker = 0;
            navigation.release();
            return;
        }

        if (mc.player.currentScreenHandler instanceof MerchantScreenHandler handler) {
            // Standing still while a trading screen is open: releasing also restores any suppressed key.
            navigation.release();
            tickMerchantScreen(handler);
            return;
        }

        // The screen is gone, so whatever we were doing with that villager is over.
        if (activeVillager != null || offersReady || finished) {
            activeVillager = null;
            offersReady = false;
            finished = false;
            closing = false;
            screenTicks = 0;
        }

        if (!aura.get()) {
            navigation.release();
            MovementControl.stop();
            return;
        }

        interactableInRange = scanForInteractable();

        // The player is walking: stay out of the way but keep trading whatever comes into reach.
        if (driving) navigation.release();
        else tickNavigation();

        updateMovementLock(driving);
        tickAura();
        tickIdle();
    }

    // ---------------------------------------------------------------------------------------------------
    // Pausing
    // ---------------------------------------------------------------------------------------------------

    private boolean paused() {
        if (pauseWhileUsing.get() && PlayerUtils.shouldPause(true, true, true)) return true;
        if (pauseOnLowHealth.get() && PlayerUtils.getTotalHealth() < minHealth.get()) return true;

        return pauseOnLag.get() && TickRate.INSTANCE.getTickRate() < minTickRate.get();
    }

    // ---------------------------------------------------------------------------------------------------
    // The trading screen
    // ---------------------------------------------------------------------------------------------------

    private void tickMerchantScreen(MerchantScreenHandler handler) {
        screenTicks++;

        if (finished) return;

        if (!offersReady) {
            if (screenTicks > offersTimeout.get()) {
                if (debug.get()) info("The villager never sent its trade list, closing the screen.");
                markActive(defaultColor.get());
                closeMerchantScreen();
            }
            return;
        }

        if (tradeCooldown > 0) {
            tradeCooldown--;
            return;
        }

        if (closing) {
            if (++closeTicks <= ticksToClose.get()) return;
            closeMerchantScreen();
            return;
        }

        TradeOfferList offers = handler.getRecipes();
        if (offers == null || offers.isEmpty()) {
            markActive(noTradesColor.get());
            beginClose();
            return;
        }

        if (tradesThisVisit >= maxTradesPerVillager.get()) {
            if (debug.get()) info("Reached the per-villager trade limit (" + maxTradesPerVillager.get() + ").");
            beginClose();
            return;
        }

        List<TradeEvaluator.Offer<Item>> snapshot = VillagerUtil.snapshot(offers);
        TradeEvaluator.Stock<Item> stock = VillagerUtil.stock();

        TradeEvaluator.Decision best = null;
        TradeEvaluator.Decision chosen = null;

        for (TradeEvaluator.Offer<Item> offer : snapshot) {
            TradeEvaluator.Decision decision = TradeEvaluator.evaluate(offer, buyRules, sellRules, stock, Items.EMERALD);

            if (debug.get()) info("Offer " + offer.index() + ": " + decision.verdict() + " - " + decision.detail());

            if (decision.actionable()) {
                chosen = decision;
                break;
            }

            // Whatever blocked this offer may be something the shulker boxes can fix.
            requestShulkerHelp(offer, decision.verdict());

            if (best == null || rank(decision.verdict()) > rank(best.verdict())) best = decision;
        }

        if (chosen != null) {
            executeTrade(chosen);
            return;
        }

        markActive(colorFor(best == null ? TradeEvaluator.Verdict.NO_RULE : best.verdict()));

        if (shouldReroll(snapshot, offers)) {
            startReroll();
            return;
        }

        beginClose();
    }

    /**
     * Turns a rejected trade into a shulker job.
     * <p>
     * This is the feedback loop that makes the backpack useful: the module does not wait for a threshold to be
     * crossed, it reacts to the exact trade that just failed. Out of emeralds in front of a librarian means
     * "fetch emeralds", not "wait until the emerald count happens to drop below a number".
     */
    private void requestShulkerHelp(TradeEvaluator.Offer<Item> offer, TradeEvaluator.Verdict verdict) {
        if (!invSettings.enabled.get() || !invSettings.autoShulker.get()) return;

        switch (verdict) {
            case NOT_ENOUGH_EMERALDS -> requestRefillIfStocked(Items.EMERALD);
            case NOT_ENOUGH_ITEMS -> {
                requestRefillIfStocked(offer.costA());
                requestRefillIfStocked(offer.costB());
            }
            case NO_INVENTORY_SPACE -> {
                if (AutoShulker.roomInShulkers(offer.sellItem())) {
                    autoShulker.requestDump(offer.sellItem());
                    if (debug.get()) info("Inventory full, asking to store " + name(offer.sellItem()) + " in a shulker box.");
                }
                else if (debug.get()) {
                    info("Inventory full and no shulker box has room for " + name(offer.sellItem()) + ".");
                }
            }
            default -> {
            }
        }
    }

    private void requestRefillIfStocked(Item item) {
        if (item == null) return;

        if (AutoShulker.hasInShulkers(item)) {
            autoShulker.requestRefill(item);
            if (debug.get()) info("Asking to fetch " + name(item) + " out of a shulker box.");
        }
        else if (debug.get()) {
            info("Out of " + name(item) + " and none of the carried shulker boxes has any.");
        }
    }

    private void executeTrade(TradeEvaluator.Decision decision) {
        if (!limiter.trySend(1)) return;

        mc.player.networkHandler.sendPacket(new SelectMerchantTradeC2SPacket(decision.offerIndex()));

        // Slot 2 is the merchant's result slot.
        if (!InvHelper.click(2, 0, SlotActionType.QUICK_MOVE)) {
            tradeCooldown = 1;
            return;
        }

        tradesThisVisit++;
        tradeCooldown = tradeDelay.get();
        idleTicker = 0;

        markActive(tradedColor.get());

        if (debug.get()) info("Traded: " + decision.detail());
    }

    /**
     * Nothing left to do with this villager.
     * <p>
     * With {@code close-screen} off the screen is deliberately left open, so the module only stops evaluating it
     * and waits for you to close it.
     */
    private void beginClose() {
        if (!close.get()) {
            finished = true;
            return;
        }

        closing = true;
        closeTicks = 0;
    }

    private void closeMerchantScreen() {
        InvHelper.closeScreen();

        offersReady = false;
        closing = false;
        closeTicks = 0;
        screenTicks = 0;
        finished = false;
        activeVillager = null;

        // Let the aura pick the next villager on the very next tick instead of waiting out a whole delay.
        auraTicker = interactDelay.get();
    }

    /** Display priority: the most informative reason wins. */
    private static int rank(TradeEvaluator.Verdict verdict) {
        return switch (verdict) {
            case BUY, SELL -> 100;
            case NO_INVENTORY_SPACE -> 60;
            case NOT_ENOUGH_EMERALDS, NOT_ENOUGH_ITEMS -> 50;
            case LIMIT_REACHED -> 40;
            case TOO_EXPENSIVE -> 30;
            case OUT_OF_STOCK -> 20;
            case NO_RULE -> 0;
        };
    }

    private Color colorFor(TradeEvaluator.Verdict verdict) {
        return switch (verdict) {
            case BUY, SELL -> tradedColor.get();
            case NO_INVENTORY_SPACE -> inventoryFullColor.get();
            case NOT_ENOUGH_EMERALDS -> noEmeraldColor.get();
            case NOT_ENOUGH_ITEMS -> noSellItemsColor.get();
            case LIMIT_REACHED -> limitReachedColor.get();
            case TOO_EXPENSIVE -> tooExpensiveColor.get();
            case OUT_OF_STOCK -> disabledTradeColor.get();
            case NO_RULE -> noTradesColor.get();
        };
    }

    // ---------------------------------------------------------------------------------------------------
    // Rerolling
    // ---------------------------------------------------------------------------------------------------

    private boolean shouldReroll(List<TradeEvaluator.Offer<Item>> snapshot, TradeOfferList offers) {
        if (!rerollEnabled.get() || activeVillager == null) return false;
        if (rerollCount.getOrDefault(activeVillager, 0) >= maxRerolls.get()) return false;

        // Never reroll a villager you deliberately kept.
        if (whitelist.contains(activeVillager) && targetMode.get() != TargetMode.Everyone) return false;

        String profession = VillagerUtil.professionId(activeVillager);
        if (profession == null || TradeData.workstation(profession) == null) return false;

        if (!VillagerUtil.canBeRerolled(offers, VillagerUtil.level(activeVillager))) {
            if (debug.get()) info("Not rerolling: this villager has already been traded with, its offers are locked.");
            return false;
        }

        TradeEvaluator.Stock<Item> stock = VillagerUtil.stock();

        for (TradeEvaluator.Offer<Item> offer : snapshot) {
            TradeEvaluator.Verdict verdict = TradeEvaluator.evaluate(offer, buyRules, sellRules, stock, Items.EMERALD).verdict();

            switch (verdict) {
                case NO_RULE, OUT_OF_STOCK -> {
                    // Neutral: says nothing about whether a reroll would help.
                }
                case TOO_EXPENSIVE -> {
                    if (!rerollOnPrice.get()) return false;
                }
                default -> {
                    // Affordability, space or a limit: a reroll would not fix any of those.
                    return false;
                }
            }
        }

        return true;
    }

    private void startReroll() {
        Entity villager = activeVillager;
        String profession = VillagerUtil.professionId(villager);

        closeMerchantScreen();

        RerollTask.Config config = new RerollTask.Config(
            rerollRadius.get(),
            rerollTimeout.get(),
            rerollSettle.get(),
            rotateToVillager.get()
        );

        reroll = new RerollTask(new RerollTask.Entry(villager, profession), config, message -> {
            if (debug.get()) info(message);
        });

        rerollCount.merge(villager, 1, Integer::sum);
        markState(villager, rerollColor.get());
    }

    private void tickReroll() {
        MovementControl.stop();

        RerollTask.Status status = reroll.tick();
        if (status == RerollTask.Status.RUNNING) return;

        Entity villager = reroll.villager();

        if (status == RerollTask.Status.FAILED) {
            warning("Reroll aborted: " + reroll.failReason());
            rerollCount.put(villager, maxRerolls.get());
        }
        else {
            handled.remove(villager);
            refreshCount.remove(villager);
            idleTicker = 0;
        }

        reroll.cleanup();
        reroll = null;
    }

    // ---------------------------------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------------------------------

    private Navigation.Config navConfig() {
        Navigation.Mode mode = movementMode.get();

        // Pathfind without a path manager installed would simply stand still; Simple steering is the honest
        // fallback and is said so at activation time.
        if (mode == Navigation.Mode.Pathfind && !Navigation.pathingAvailable()) mode = Navigation.Mode.Simple;

        return new Navigation.Config(
            mode,
            stopDistance.get(),
            leashRadius.get(),
            repathInterval.get(),
            travelTimeout.get(),
            manualOverride.get()
        );
    }

    private void tickNavigation() {
        if (movementMode.get() == Navigation.Mode.Off) {
            navTarget = null;
            return;
        }

        // An interaction was just sent and the screen is on its way: do not walk off in the meantime.
        if (pendingVillager != null) {
            navTarget = null;
            navigation.release();
            return;
        }

        // Something is already in reach: stand still and let the aura do its job.
        if (hasInteractableInRange()) {
            navTarget = null;
            navigation.release();
            return;
        }

        Entity target = findNavigationTarget();

        if (target == null) {
            navTarget = null;

            if (returnToAnchor.get()) navigation.returnToAnchor(navConfig());
            else navigation.release();

            return;
        }

        navTarget = target;
        Navigation.Status status = navigation.tick(target, navConfig());

        if (status == Navigation.Status.GaveUp) {
            unreachable.put(target, unreachableCooldown.get());
            navTarget = null;

            if (debug.get()) info("Could not reach " + describe(target) + ", ignoring it for a while.");
        }
        else if (status == Navigation.Status.Moving) {
            idleTicker = 0;
        }
    }

    /** The next villager worth walking to, honouring the leash, the lists and the unreachable cooldown. */
    private Entity findNavigationTarget() {
        scratch.clear();
        TargetUtils.getList(scratch, this::isNavigable, SortPriority.LowestDistance, maxTargets.get());

        if (scratch.isEmpty()) return null;

        // In PreferWhitelist mode a whitelisted villager anywhere in the search radius beats a closer one that
        // is not on the list - that is the whole point of the mode.
        if (targetMode.get().prioritisesWhitelist()) {
            for (Entity entity : scratch) {
                if (whitelist.contains(entity)) return entity;
            }
        }

        return scratch.get(0);
    }

    private boolean isNavigable(Entity entity) {
        if (!passesFilters(entity, searchRadius.get())) return false;

        // No point walking across the hall to a villager whose profession cannot offer what you configured.
        if (smartTargeting.get() && !couldOfferAnything(entity)) return false;
        if (unreachable.containsKey(entity)) return false;
        if (handled.containsKey(entity)) return false;

        return navigation.withinLeash(entity, leashRadius.get());
    }

    /** Cached at the top of the tick; both navigation and the movement lock ask for it. */
    private boolean hasInteractableInRange() {
        return interactableInRange;
    }

    /** Is there a villager the aura could click right now? */
    private boolean scanForInteractable() {
        scratch.clear();
        TargetUtils.getList(scratch, this::isTargetable, priority.get(), maxTargets.get());

        for (Entity entity : scratch) {
            if (!handled.containsKey(entity)) return true;
        }

        return false;
    }

    // ---------------------------------------------------------------------------------------------------
    // The aura
    // ---------------------------------------------------------------------------------------------------

    private void tickAura() {
        if (++auraTicker < interactDelay.get()) return;
        auraTicker = 0;

        targets.clear();
        TargetUtils.getList(targets, this::isTargetable, priority.get(), maxTargets.get());

        // In PreferWhitelist mode, serve a whitelisted villager in reach before anyone else.
        if (targetMode.get().prioritisesWhitelist()) {
            for (Entity target : targets) {
                if (whitelist.contains(target) && tryInteract(target)) return;
            }
        }

        for (Entity target : targets) {
            if (tryInteract(target)) return;
        }
    }

    /** @return true when this villager was interacted with (or deliberately skipped and marked) */
    private boolean tryInteract(Entity target) {
        if (handled.containsKey(target)) {
            if (!shouldRetry(target)) return false;
            handled.remove(target);
        }

        if (smartTargeting.get() && !couldOfferAnything(target)) {
            if (debug.get()) info("Skipping " + describe(target) + ": its profession cannot offer anything you configured.");
            markState(target, skippedColor.get());
            return false;
        }

        interactWithVillager(target);
        return true;
    }

    private void interactWithVillager(Entity target) {
        pendingVillager = target;
        markState(target, defaultColor.get());

        if (!rotateToVillager.get()) {
            ActionResult result = interact(target);
            if (!result.isAccepted() && debug.get()) info("The interaction was not accepted.");
            return;
        }

        Vec3d villagerEyes = target.getEyePos();

        Rotations.rotate(Rotations.getYaw(villagerEyes), Rotations.getPitch(villagerEyes), 100, () -> {
            ActionResult result = interact(target);
            if (!result.isAccepted() && debug.get()) info("The interaction was not accepted.");
        });
    }

    private ActionResult interact(Entity entity) {
        if (mc.player == null || mc.interactionManager == null) return ActionResult.FAIL;

        Vec3d eyes = mc.player.getEyePos();
        Vec3d target = entity.getEyePos();
        Box box = entity.getBoundingBox().expand(0.1);

        EntityHitResult hit = ProjectileUtil.getEntityCollision(mc.world, mc.player, eyes, target, box, e -> e == entity);

        if (hit != null) {
            ActionResult result = mc.interactionManager.interactEntityAtLocation(mc.player, entity, hit, Hand.MAIN_HAND);
            if (result.isAccepted()) return result;
        }

        return mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
    }

    /** Filters that apply to a villager at any distance: type, lists, profession, level, busy state. */
    private boolean passesFilters(Entity entity, double maxDistance) {
        if (mc.player == null) return false;
        if (entity == mc.player || entity == mc.getCameraEntity()) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof LivingEntity living && living.isDead()) return false;

        if (!VillagerUtil.isMerchant(entity, wanderingTraders.get())) return false;
        if (!targetMode.get().allows(whitelist.contains(entity), blacklist.contains(entity))) return false;
        if (skipNonTrading.get() && VillagerUtil.cannotTrade(entity)) return false;
        if (skipBusy.get() && VillagerUtil.isBusy(entity)) return false;

        int level = VillagerUtil.level(entity);
        // Level 0 means "has no level", which is the wandering trader - never filtered by level.
        if (level > 0 && (level < minLevel.get() || level > maxLevel.get())) return false;

        return PlayerUtils.distanceTo(entity) <= maxDistance;
    }

    /** Everything above, plus the interaction range and the line of sight check. */
    private boolean isTargetable(Entity entity) {
        if (!passesFilters(entity, Math.max(range.get(), 1))) return false;

        Box hitbox = entity.getBoundingBox();
        boolean inRange = PlayerUtils.isWithin(
            MathHelper.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
            MathHelper.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
            MathHelper.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
            range.get()
        );
        if (!inRange) return false;

        return !requireLineOfSight.get() || PlayerUtils.canSeeEntity(entity);
    }

    /**
     * Works out, once per tick, which professions could offer anything the current rules ask for.
     * <p>
     * Doing this per villager meant walking every rule and every item for every entity in range, several times
     * a tick. The rules change at human speed, so once a tick is plenty.
     */
    private void rebuildViableProfessions() {
        viableProfessions.clear();
        if (!smartTargeting.get()) return;

        for (String profession : TradeData.WORKSTATIONS.keySet()) {
            if (professionCouldOffer(profession)) viableProfessions.add(profession);
        }
    }

    private boolean professionCouldOffer(String profession) {
        for (TradeRule rule : buyRules) {
            for (Item item : rule.items) {
                if (TradeData.canTrade(profession, item, true)) return true;
            }
        }

        for (TradeRule rule : sellRules) {
            for (Item item : rule.items) {
                if (TradeData.canTrade(profession, item, false)) return true;
            }
        }

        return false;
    }

    /** The profession pre-check, using the villager's real profession. */
    private boolean couldOfferAnything(Entity entity) {
        if (!smartTargeting.get()) return true;

        String profession = VillagerUtil.professionId(entity);

        // Wandering traders and anything without a profession are never filtered out.
        if (profession == null) return true;

        // A profession with no workstation is not in the table above, so fall back to the full check.
        if (!TradeData.WORKSTATIONS.containsKey(profession)) return professionCouldOffer(profession);

        return viableProfessions.contains(profession);
    }

    private boolean shouldRetry(Entity target) {
        if (!refreshUnsynced.get()) return false;

        TargetState state = handled.get(target);
        if (state == null) return false;
        if (state.synced) return false;
        if (state.age < refreshDelay.get()) return false;

        int tries = refreshCount.getOrDefault(target, 0);
        if (tries >= maxRefreshes.get()) return false;

        refreshCount.put(target, tries + 1);
        if (debug.get()) info("Villager never answered, retrying (" + (tries + 1) + "/" + maxRefreshes.get() + ").");
        return true;
    }

    private void markActive(Color color) {
        if (activeVillager != null) markState(activeVillager, color);
    }

    private void markState(Entity entity, Color color) {
        TargetState state = handled.get(entity);

        if (state == null) {
            state = new TargetState(color);
            handled.put(entity, state);
        }
        else {
            state.color = color;
            state.age = 0;
        }

        if (!color.equals(defaultColor.get())) state.synced = true;
    }

    /** Ages every timer keyed on an entity and drops entries whose entity is gone. */
    private void ageTargets() {
        Iterator<Map.Entry<Entity, TargetState>> it = handled.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Entity, TargetState> entry = it.next();
            Entity entity = entry.getKey();

            // Entities are recreated when a chunk reloads, so without this the map would grow forever.
            if (!entity.isAlive() || entry.getValue().age++ > forget.get()) {
                it.remove();
                refreshCount.remove(entity);
            }
        }

        Iterator<Map.Entry<Entity, Integer>> unreachableIt = unreachable.entrySet().iterator();

        while (unreachableIt.hasNext()) {
            Map.Entry<Entity, Integer> entry = unreachableIt.next();

            if (!entry.getKey().isAlive() || entry.getValue() <= 1) unreachableIt.remove();
            else entry.setValue(entry.getValue() - 1);
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Movement lock (only used with movement-mode Off)
    // ---------------------------------------------------------------------------------------------------

    private void updateMovementLock(boolean driving) {
        if (movementMode.get() != Navigation.Mode.Off || !cancelMovement.get() || driving) return;

        if (!hasInteractableInRange()) {
            MovementControl.stop();
            return;
        }

        int ticksRemaining = interactDelay.get() - auraTicker;
        boolean interactionClose = ticksRemaining >= 0 && ticksRemaining <= ticksToCancelMovement.get();

        if (cancelMovementNearVillager.get() || interactionClose) MovementControl.freeze();
        else MovementControl.stop();
    }

    // ---------------------------------------------------------------------------------------------------
    // Idle shutdown
    // ---------------------------------------------------------------------------------------------------

    private void tickIdle() {
        if (!disableWhenIdle.get()) return;
        if (++idleTicker < idleTicks.get()) return;

        info("Nothing left to trade for " + (idleTicks.get() / 20) + "s, turning off.");
        toggle();
    }

    // ---------------------------------------------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------------------------------------------

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get()) return;

        for (Map.Entry<Entity, TargetState> entry : handled.entrySet()) {
            drawBoundingBox(event, entry.getKey(), entry.getValue().color);
        }

        if (navTarget != null && navTarget.isAlive()) drawBoundingBox(event, navTarget, navTargetColor.get());

        if (!renderLists.get() || mc.player == null) return;

        Box area = mc.player.getBoundingBox().expand(searchRadius.get());

        for (Entity entity : mc.world.getOtherEntities(mc.player, area, e -> whitelist.contains(e) || blacklist.contains(e))) {
            if (blacklist.contains(entity)) drawBoundingBox(event, entity, blacklistColor.get());
            else drawBoundingBox(event, entity, whitelistColor.get());
        }
    }

    private final Color lineColor = new Color();
    private final Color sideColor = new Color();

    private void drawBoundingBox(Render3DEvent event, Entity entity, Color color) {
        lineColor.set(color);
        sideColor.set(color);
        sideColor.a((int) (sideColor.a * fillOpacity.get()));

        double x = MathHelper.lerp(event.tickDelta, entity.prevX, entity.getX()) - entity.getX();
        double y = MathHelper.lerp(event.tickDelta, entity.prevY, entity.getY()) - entity.getY();
        double z = MathHelper.lerp(event.tickDelta, entity.prevZ, entity.getZ()) - entity.getZ();

        Box box = entity.getBoundingBox();
        event.renderer.box(
            x + box.minX, y + box.minY, z + box.minZ,
            x + box.maxX, y + box.maxY, z + box.maxZ,
            sideColor, lineColor, ShapeMode.Both, 0
        );
    }

    // ---------------------------------------------------------------------------------------------------
    // Status
    // ---------------------------------------------------------------------------------------------------

    @Override
    public String getInfoString() {
        if (reroll != null) return "reroll";
        if (activeVillager != null) return "trading";
        if (navTarget != null) return "walking";
        if (navigation.overrideTicksLeft() > 0) return "you drive";
        return null;
    }

    private String describe(Entity entity) {
        String profession = VillagerUtil.professionId(entity);
        return profession == null ? "villager" : profession;
    }

    private static String name(Item item) {
        return item == null ? "?" : item.getDefaultStack().getName().getString();
    }
}
