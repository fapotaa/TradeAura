package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Turns the shulker boxes in your backpack into the module's stockroom, without you configuring a single rule.
 *
 * <h2>What it does</h2>
 * The buy and sell rules already say everything needed to work out what belongs in a shulker box:
 * <ul>
 *     <li>Emeralds are what you spend when buying and what you collect when selling, so they are pulled out when
 *     they run low and put away when they pile up.</li>
 *     <li>Whatever your <b>sell</b> rules list is stock you hand to villagers, so it is pulled out when it runs
 *     low - there is no point standing in front of a farmer with all your wheat in a box.</li>
 *     <li>Whatever your <b>buy</b> rules list is what you accumulate, so it is the first thing put away when the
 *     inventory fills up.</li>
 * </ul>
 * Nothing here moves an item the rules never mentioned, so the tools, food and blocks you are carrying are left
 * alone.
 *
 * <h2>The two ways a job appears</h2>
 * <ol>
 *     <li><b>Thresholds.</b> Emeralds below the low mark, a sell item below its low mark, free inventory slots
 *     below the limit - checked every time the manager looks for work.</li>
 *     <li><b>Requests from the trading loop.</b> When a trade is rejected because there are not enough emeralds,
 *     not enough of an item, or no room for the result, the module asks for exactly that item to be fetched or
 *     stored. This is the part that makes the whole thing self-sustaining: it does not wait for a threshold, it
 *     reacts to the trade that just failed.</li>
 * </ol>
 * A request is only kept while a carried shulker box can actually serve it, so asking for emeralds you do not
 * have anywhere does not put the module into a loop.
 */
public class AutoShulker {
    private final InventorySettings s;
    private final Supplier<List<Item>> buyItems;
    private final Supplier<List<Item>> sellItems;

    /** Items the trading loop asked for, because a trade failed for want of them. */
    private final Set<Item> refillRequests = new LinkedHashSet<>();
    /** Items the trading loop asked to store, because there was no room for them. */
    private final Set<Item> dumpRequests = new LinkedHashSet<>();

    /**
     * Deriving the rules means reading every carried shulker box, and the manager asks for them on every idle
     * tick. Recomputing that twenty times a second is pure waste when the inventory changes far more slowly, so
     * the result is cached for a few ticks and dropped immediately whenever a request comes in.
     */
    private static final int CACHE_TICKS = 10;

    private List<ItemRule> cachedRefill = List.of();
    private List<ItemRule> cachedDump = List.of();
    private int cacheAge = Integer.MAX_VALUE;

    public AutoShulker(InventorySettings settings, Supplier<List<Item>> buyItems, Supplier<List<Item>> sellItems) {
        this.s = settings;
        this.buyItems = buyItems;
        this.sellItems = sellItems;
    }

    // Requests from the trading loop

    /** "A trade needed this and I did not have enough." */
    public void requestRefill(Item item) {
        if (item == null) return;

        if (refillRequests.add(item)) invalidate();
    }

    /** "A trade could not run because there was no room." */
    public void requestDump(Item item) {
        if (item == null) return;

        if (dumpRequests.add(item)) invalidate();
    }

    public void clearRequests() {
        if (refillRequests.isEmpty() && dumpRequests.isEmpty()) return;

        refillRequests.clear();
        dumpRequests.clear();
        invalidate();
    }

    /** Forces the next call to rebuild the derived rules. */
    public void invalidate() {
        cacheAge = Integer.MAX_VALUE;
    }

    /** Call once per tick; ages the cache. */
    public void tick() {
        if (cacheAge < Integer.MAX_VALUE) cacheAge++;
    }

    public boolean hasRequests() {
        return !refillRequests.isEmpty() || !dumpRequests.isEmpty();
    }

    // Derived rules

    /**
     * Everything that should be pulled out of a shulker box right now.
     * <p>
     * The rules are rebuilt from scratch on every call rather than kept around, because every input (inventory
     * counts, shulker contents, the user's rule lists) can change between two ticks.
     */
    public List<ItemRule> refillRules() {
        rebuildIfStale();
        return cachedRefill;
    }

    /** Everything that should be put away into a shulker box right now. */
    public List<ItemRule> dumpRules() {
        rebuildIfStale();
        return cachedDump;
    }

    private void rebuildIfStale() {
        if (cacheAge <= CACHE_TICKS) return;

        cacheAge = 0;
        cachedRefill = buildRefillRules();
        cachedDump = buildDumpRules();
    }

    private List<ItemRule> buildRefillRules() {
        List<ItemRule> rules = new ArrayList<>();
        if (!s.enabled.get() || !s.autoShulker.get()) return rules;

        Set<Item> done = new LinkedHashSet<>();

        // 1. Emeralds, the currency.
        if (s.autoEmeralds.get()) {
            int have = InvHelper.count(Items.EMERALD);
            boolean wanted = have < s.emeraldLow.get() || refillRequests.contains(Items.EMERALD);

            if (wanted && hasInShulkers(Items.EMERALD)) {
                rules.add(rule(Items.EMERALD, s.emeraldLow.get(), s.emeraldTarget.get()));
                done.add(Items.EMERALD);
            }
        }

        // 2. Stock to sell.
        if (s.autoSellStock.get()) {
            for (Item item : sellItems.get()) {
                if (item == null || !done.add(item)) continue;

                int have = InvHelper.count(item);
                boolean wanted = have < s.sellStockLow.get() || refillRequests.contains(item);

                if (wanted && hasInShulkers(item)) {
                    rules.add(rule(item, s.sellStockLow.get(), s.sellStockTarget.get()));
                }
            }
        }

        // 3. Anything the trading loop asked for that is not covered above.
        for (Item item : refillRequests) {
            if (item == null || !done.add(item)) continue;
            if (!hasInShulkers(item)) continue;

            rules.add(rule(item, s.sellStockLow.get(), s.sellStockTarget.get()));
        }

        return rules;
    }

    private List<ItemRule> buildDumpRules() {
        List<ItemRule> rules = new ArrayList<>();
        if (!s.enabled.get() || !s.autoShulker.get()) return rules;

        boolean inventoryTight = freeSlots() <= s.dumpWhenFreeSlots.get();
        Set<Item> done = new LinkedHashSet<>();

        // An item that is both bought and sold must never be stored, or it would be fetched straight back out.
        if (s.autoSellStock.get()) done.addAll(sellItems.get());
        done.remove(null);

        // 1. Emeralds above the high mark. Selling fills the inventory with them faster than anything else.
        if (s.autoEmeralds.get()) {
            int have = InvHelper.count(Items.EMERALD);

            if (have > s.emeraldHigh.get() || (inventoryTight && have > s.emeraldTarget.get())) {
                rules.add(rule(Items.EMERALD, s.emeraldTarget.get(), s.emeraldTarget.get()));
                done.add(Items.EMERALD);
            }
        }

        // 2. What the trading loop could not find room for.
        for (Item item : dumpRequests) {
            if (item == null || !done.add(item)) continue;
            rules.add(rule(item, 0, s.purchaseKeep.get()));
        }

        // 3. Purchases, once the inventory is getting tight.
        if (inventoryTight && s.autoStorePurchases.get()) {
            for (Item item : buyItems.get()) {
                if (item == null || !done.add(item)) continue;

                int have = InvHelper.count(item);
                if (have > s.purchaseKeep.get()) rules.add(rule(item, s.purchaseKeep.get(), s.purchaseKeep.get()));
            }
        }

        return rules;
    }

    // Reporting

    /**
     * A one-line summary of what the carried shulker boxes hold, for the module's status output. Reading a
     * shulker box item is free and needs no interaction: the contents travel with the item.
     */
    public String describeShulkers() {
        int boxes = 0;
        int emeralds = 0;
        int freeSlotsInBoxes = 0;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getStack(i);
            if (!InvHelper.isShulker(stack)) continue;

            boxes += stack.getCount();
            emeralds += InvHelper.containerCount(stack, List.of(Items.EMERALD));
            freeSlotsInBoxes += InvHelper.containerFreeSlots(stack);
        }

        if (boxes == 0) return "no shulker boxes carried";

        return boxes + " shulker box(es), " + emeralds + " emerald(s) inside, " + freeSlotsInBoxes + " free slot(s)";
    }

    /** True when at least one carried shulker box holds this item. */
    public static boolean hasInShulkers(Item item) {
        return countInShulkers(item) > 0;
    }

    /** How many of an item are sitting in the carried shulker boxes. */
    public static int countInShulkers(Item item) {
        if (mc.player == null) return 0;

        List<Item> one = List.of(item);
        int count = 0;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (InvHelper.isShulker(stack)) count += InvHelper.containerCount(stack, one);
        }

        return count;
    }

    /** True when a carried shulker box could take more of this item. */
    public static boolean roomInShulkers(Item item) {
        if (mc.player == null) return false;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (InvHelper.isShulker(stack) && InvHelper.containerSpaceFor(stack, item) > 0) return true;
        }

        return false;
    }

    /** Empty slots in the hotbar and main inventory. */
    public static int freeSlots() {
        if (mc.player == null) return 0;

        int free = 0;
        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) free++;
        }

        return free;
    }

    private static ItemRule rule(Item item, int trigger, int leave) {
        ItemRule rule = new ItemRule(trigger, leave);
        rule.items.add(item);
        return rule;
    }
}
