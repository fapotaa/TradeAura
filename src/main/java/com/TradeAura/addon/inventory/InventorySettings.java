package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Every setting of the "Inventory manipulation" tab.
 * <p>
 * Kept out of the module itself so {@code TradeAura} stays readable. The per item limits live in
 * {@link ItemRule} lists that are edited through the module GUI (same style as the buy / sell rules) and are
 * serialized by the module.
 */
public class InventorySettings {
    public final SettingGroup group;

    // Not final on purpose: the visibility lambdas reference toggles that are only built further down in the
    // constructor, and a blank final field may not be read before it is assigned - not even from a lambda that
    // runs long after the constructor finished.

    // General

    public Setting<Boolean> enabled;
    public Setting<Boolean> runWithoutAura;
    public Setting<Integer> actionDelay;
    public Setting<Integer> actionTimeout;
    public Setting<Integer> failCooldown;
    public Setting<Integer> maxChain;
    public Setting<Boolean> cancelScreens;
    public Setting<AmountMode> amountMode;

    // Drop excess items

    public Setting<Boolean> dropEnabled;
    public Setting<DropDirection> dropDirection;
    public final List<ItemRule> dropRules = new ArrayList<>();

    // Compress emeralds

    public Setting<Boolean> compressEnabled;
    public Setting<Integer> compressTrigger;
    public Setting<Integer> compressLeave;
    public Setting<Double> craftingTableRange;
    public Setting<Integer> maxCrafts;

    // Decompress emeralds

    public Setting<Boolean> decompressEnabled;
    public Setting<Integer> decompressTrigger;
    public Setting<Integer> decompressLeave;

    // Glass panes (6 glass -> 16 panes)

    public Setting<Boolean> glassPanesEnabled;
    public Setting<Integer> glassTrigger;
    public Setting<Integer> glassLeave;

    // Dump to shulker

    public Setting<Boolean> dumpEnabled;
    public final List<ItemRule> dumpRules = new ArrayList<>();

    // Refill from shulker

    public Setting<Boolean> refillEnabled;
    public final List<ItemRule> refillRules = new ArrayList<>();

    // Automatic shulker use, derived from the trade rules

    public Setting<Boolean> autoShulker;
    public Setting<Boolean> autoEmeralds;
    public Setting<Integer> emeraldLow;
    public Setting<Integer> emeraldTarget;
    public Setting<Integer> emeraldHigh;
    public Setting<Boolean> autoSellStock;
    public Setting<Integer> sellStockLow;
    public Setting<Integer> sellStockTarget;
    public Setting<Boolean> autoStorePurchases;
    public Setting<Integer> purchaseKeep;
    public Setting<Integer> dumpWhenFreeSlots;

    // Shulker handling

    public Setting<Boolean> shulkerAutoTool;
    public Setting<Boolean> rotate;
    public Setting<Integer> shulkerPickupTicks;
    public Setting<Integer> transferBatch;
    public Setting<Boolean> walkToDrop;
    public Setting<Boolean> lockMovement;

    public InventorySettings(SettingGroup group, Runnable onVisibilityChanged) {
        this.group = group;

        enabled = group.add(new BoolSetting.Builder()
            .name("inventory-manipulation")
            .description("Master switch of every inventory trigger below. Each trigger interrupts the aura while it runs.")
            .defaultValue(false)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        runWithoutAura = group.add(new BoolSetting.Builder()
            .name("run-without-aura")
            .description("Keep the inventory triggers running while Villager-Aura is off. The original version silently did nothing in that case.")
            .defaultValue(true)
            .visible(enabled::get)
            .build()
        );

        actionDelay = group.add(new IntSetting.Builder()
            .name("action-delay")
            .description("Ticks to wait between two inventory operations, gives the server time to confirm the previous one.")
            .defaultValue(2)
            .min(0)
            .sliderMax(20)
            .visible(enabled::get)
            .build()
        );

        actionTimeout = group.add(new IntSetting.Builder()
            .name("action-timeout")
            .description("How many ticks a single step (placing, opening, breaking, ...) may take before the action is aborted.")
            .defaultValue(60)
            .min(10)
            .sliderMax(200)
            .visible(enabled::get)
            .build()
        );

        failCooldown = group.add(new IntSetting.Builder()
            .name("fail-cooldown")
            .description("Ticks a trigger is skipped after it failed, stops the module from retrying a hopeless action forever.")
            .defaultValue(100)
            .min(0)
            .sliderMax(600)
            .visible(enabled::get)
            .build()
        );

        maxChain = group.add(new IntSetting.Builder()
            .name("max-chained-actions")
            .description("Triggers may chain into each other (low emeralds -> craft -> low blocks -> refill). This caps how many actions may run back to back before the module assumes the settings contradict each other.")
            .defaultValue(12)
            .min(1)
            .sliderMax(50)
            .visible(enabled::get)
            .build()
        );

        cancelScreens = group.add(new BoolSetting.Builder()
            .name("cancel-screens")
            .description("Do not render the crafting / shulker screens the module opens.")
            .defaultValue(true)
            .visible(enabled::get)
            .build()
        );

        rotate = group.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate towards the crafting table / shulker box the module interacts with.")
            .defaultValue(true)
            .visible(enabled::get)
            .build()
        );

        amountMode = group.add(new EnumSetting.Builder<AmountMode>()
            .name("amount-mode")
            .description("ToLimit moves everything down/up to the leave value in one go. TriggerMinusLeave moves exactly (trigger - leave) per action, which may need several chained actions.")
            .defaultValue(AmountMode.ToLimit)
            .visible(enabled::get)
            .build()
        );

        // Drop

        dropEnabled = group.add(new BoolSetting.Builder()
            .name("drop-excess-items")
            .description("Throws away everything above the limit configured in the Drop Rules table.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        dropDirection = group.add(new EnumSetting.Builder<DropDirection>()
            .name("drop-direction")
            .description("Where to throw the items, relative to the player.")
            .defaultValue(DropDirection.Forward)
            .visible(() -> enabled.get() && dropEnabled.get())
            .build()
        );

        // Compress

        compressEnabled = group.add(new BoolSetting.Builder()
            .name("compress-emeralds")
            .description("Crafts excess emeralds into emerald blocks. Needs a crafting table in range, otherwise the trigger does not fire.")
            .defaultValue(false)
            .visible(enabled::get)
            .build()
        );

        compressTrigger = group.add(new IntSetting.Builder()
            .name("compress-trigger")
            .description("Fires when the emerald count is above this value.")
            .defaultValue(128)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && compressEnabled.get())
            .build()
        );

        compressLeave = group.add(new IntSetting.Builder()
            .name("compress-leave")
            .description("How many emeralds stay in the inventory, the rest is crafted into blocks.")
            .defaultValue(64)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && compressEnabled.get())
            .build()
        );

        craftingTableRange = group.add(new DoubleSetting.Builder()
            .name("crafting-table-range")
            .description("How far away a crafting table may be for the table recipes to fire.")
            .defaultValue(4.0)
            .min(1)
            .sliderRange(1, 5)
            .visible(() -> enabled.get() && (compressEnabled.get() || glassPanesEnabled.get()))
            .build()
        );

        maxCrafts = group.add(new IntSetting.Builder()
            .name("max-crafts-per-action")
            .description("Upper limit of crafts a single crafting action performs. A grid slot cannot hold more than a stack, so 64 is the maximum.")
            .defaultValue(CraftTask.MAX_CRAFTS)
            .min(1)
            .sliderRange(1, CraftTask.MAX_CRAFTS)
            .visible(() -> enabled.get() && (compressEnabled.get() || decompressEnabled.get() || glassPanesEnabled.get()))
            .build()
        );

        // Decompress

        decompressEnabled = group.add(new BoolSetting.Builder()
            .name("decompress-emeralds")
            .description("Crafts emerald blocks back into emeralds using the 2x2 grid, no crafting table needed. Does not fire without blocks in the inventory.")
            .defaultValue(false)
            .visible(enabled::get)
            .build()
        );

        decompressTrigger = group.add(new IntSetting.Builder()
            .name("decompress-trigger")
            .description("Fires when the emerald count is below this value.")
            .defaultValue(32)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && decompressEnabled.get())
            .build()
        );

        decompressLeave = group.add(new IntSetting.Builder()
            .name("decompress-target")
            .description("Emerald count to restore, the missing amount is crafted from blocks.")
            .defaultValue(128)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && decompressEnabled.get())
            .build()
        );

        // Glass panes

        glassPanesEnabled = group.add(new BoolSetting.Builder()
            .name("craft-glass-panes")
            .description("Crafts excess glass into glass panes (6 -> 16). Needs a crafting table in range, otherwise the trigger does not fire.")
            .defaultValue(false)
            .visible(enabled::get)
            .build()
        );

        glassTrigger = group.add(new IntSetting.Builder()
            .name("glass-trigger")
            .description("Fires when the glass count is above this value.")
            .defaultValue(64)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && glassPanesEnabled.get())
            .build()
        );

        glassLeave = group.add(new IntSetting.Builder()
            .name("glass-leave")
            .description("How much glass stays in the inventory, the rest is crafted into panes.")
            .defaultValue(0)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && glassPanesEnabled.get())
            .build()
        );

        // Dump

        dumpEnabled = group.add(new BoolSetting.Builder()
            .name("dump-to-shulker")
            .description("Places a shulker box, stores everything above the limit configured in the Dump Rules table, breaks it and picks it back up.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        // Refill

        refillEnabled = group.add(new BoolSetting.Builder()
            .name("refill-from-shulker")
            .description("Same as Dump, but pulls the missing items out of the shulker box.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        // Automatic shulker use

        autoShulker = group.add(new BoolSetting.Builder()
            .name("auto-shulker")
            .description("Use the shulker boxes in your backpack as a stockroom, working out what belongs in them from your buy and sell rules. No Dump or Refill rules needed. Reading a shulker box costs nothing: its contents travel with the item, so the module always knows what is inside before it places anything.")
            .defaultValue(true)
            .visible(enabled::get)
            .build()
        );

        autoEmeralds = group.add(new BoolSetting.Builder()
            .name("auto-emeralds")
            .description("Fetch emeralds out of a shulker box when you run low, and put them away when they pile up.")
            .defaultValue(true)
            .visible(() -> enabled.get() && autoShulker.get())
            .build()
        );

        emeraldLow = group.add(new IntSetting.Builder()
            .name("emerald-low")
            .description("Fetch emeralds from a shulker box once you hold fewer than this many.")
            .defaultValue(64)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && autoShulker.get() && autoEmeralds.get())
            .build()
        );

        emeraldTarget = group.add(new IntSetting.Builder()
            .name("emerald-target")
            .description("How many emeralds to carry. Fetching fills up to this, putting away trims down to it.")
            .defaultValue(256)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && autoShulker.get() && autoEmeralds.get())
            .build()
        );

        emeraldHigh = group.add(new IntSetting.Builder()
            .name("emerald-high")
            .description("Put emeralds away once you hold more than this many. Keep it well above the target or the two will fight each other.")
            .defaultValue(1024)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && autoShulker.get() && autoEmeralds.get())
            .build()
        );

        autoSellStock = group.add(new BoolSetting.Builder()
            .name("auto-sell-stock")
            .description("Fetch whatever your Sell Rules list out of a shulker box when it runs low, so you never stand in front of a villager with your stock in a box.")
            .defaultValue(true)
            .visible(() -> enabled.get() && autoShulker.get())
            .build()
        );

        sellStockLow = group.add(new IntSetting.Builder()
            .name("sell-stock-low")
            .description("Fetch a sell item once you hold fewer than this many of it.")
            .defaultValue(16)
            .min(0)
            .sliderMax(1024)
            .visible(() -> enabled.get() && autoShulker.get() && autoSellStock.get())
            .build()
        );

        sellStockTarget = group.add(new IntSetting.Builder()
            .name("sell-stock-target")
            .description("How many of a sell item to carry once it has been fetched.")
            .defaultValue(256)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && autoShulker.get() && autoSellStock.get())
            .build()
        );

        autoStorePurchases = group.add(new BoolSetting.Builder()
            .name("auto-store-purchases")
            .description("Put whatever your Buy Rules list into a shulker box once the inventory gets tight. Items no rule mentions are never touched.")
            .defaultValue(true)
            .visible(() -> enabled.get() && autoShulker.get())
            .build()
        );

        purchaseKeep = group.add(new IntSetting.Builder()
            .name("purchase-keep")
            .description("How many of a bought item to leave in the inventory when storing the rest. 0 stores all of it.")
            .defaultValue(0)
            .min(0)
            .sliderMax(1024)
            .visible(() -> enabled.get() && autoShulker.get() && autoStorePurchases.get())
            .build()
        );

        dumpWhenFreeSlots = group.add(new IntSetting.Builder()
            .name("store-when-free-slots")
            .description("Start putting things away once this many inventory slots or fewer are empty. The trading loop also asks for a specific item to be stored the moment a trade is refused for lack of room, so this is a safety net rather than the main trigger.")
            .defaultValue(3)
            .min(0)
            .sliderRange(0, 20)
            .visible(() -> enabled.get() && autoShulker.get())
            .build()
        );

        shulkerAutoTool = group.add(new BoolSetting.Builder()
            .name("shulker-auto-tool")
            .description("Swap to the fastest tool in the hotbar before breaking the shulker box.")
            .defaultValue(true)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get() || autoShulker.get()))
            .build()
        );

        transferBatch = group.add(new IntSetting.Builder()
            .name("transfers-per-tick")
            .description("How many stacks are moved into / out of the shulker box per tick. The packet rate limit still applies on top of this.")
            .defaultValue(12)
            .min(1)
            .sliderRange(1, 36)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get() || autoShulker.get()))
            .build()
        );

        lockMovement = group.add(new BoolSetting.Builder()
            .name("lock-movement-while-placed")
            .description("Suppress your own movement input from the moment the shulker box is placed until it is broken again. Only the input is blocked, movement packets keep being sent, so nothing desyncs.")
            .defaultValue(true)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get() || autoShulker.get()))
            .build()
        );

        walkToDrop = group.add(new BoolSetting.Builder()
            .name("walk-to-dropped-shulker")
            .description("Walk over to the broken shulker box if it landed out of pickup range instead of leaving it behind.")
            .defaultValue(true)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get() || autoShulker.get()))
            .build()
        );

        shulkerPickupTicks = group.add(new IntSetting.Builder()
            .name("shulker-pickup-ticks")
            .description("How long to wait for (and walk towards) the broken shulker box before giving up on it.")
            .defaultValue(80)
            .min(0)
            .sliderMax(200)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get() || autoShulker.get()))
            .build()
        );
    }
}
