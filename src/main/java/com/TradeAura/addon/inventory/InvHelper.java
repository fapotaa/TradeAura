package com.TradeAura.addon.inventory;

import com.TradeAura.addon.safety.RateLimiter;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Low level helpers shared by every inventory task.
 * <p>
 * Everything here works with <b>slot indices</b> (0-8 hotbar, 9-35 main, 40 offhand) unless the name says
 * {@code SlotId}, in which case it is a slot id of the currently open screen handler
 * (see {@link SlotUtils#indexToId(int)}).
 * <p>
 * Every click goes through {@link #click(int, int, SlotActionType)}, which asks the shared {@link RateLimiter}
 * for budget first. That is what keeps a big crafting or dropping action from turning into a burst of hundreds
 * of packets in a single tick, which is how the original version behaved.
 */
public final class InvHelper {
    /** Container size of a shulker box. */
    public static final int SHULKER_SLOTS = 27;

    /** Shared with the module, so inventory clicks and trade packets draw from the same budget. */
    private static RateLimiter limiter;

    private InvHelper() {
    }

    public static void setRateLimiter(RateLimiter rateLimiter) {
        limiter = rateLimiter;
    }

    /** How many clicks may still be sent right now. {@link Integer#MAX_VALUE} when there is no limiter. */
    public static int clickBudget() {
        return limiter == null ? Integer.MAX_VALUE : limiter.available();
    }

    // Counting

    /** Counts an item in the hotbar + main inventory (armor and offhand are ignored on purpose). */
    public static int count(Item item) {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) count += stack.getCount();
        }

        return count;
    }

    public static int count(List<Item> items) {
        if (mc.player == null || items.isEmpty()) return 0;

        int count = 0;
        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && items.contains(stack.getItem())) count += stack.getCount();
        }

        return count;
    }

    public static int count(ItemRule rule) {
        return count(rule.items);
    }

    // Finding

    /** @return index of the first inventory slot holding one of the items, -1 if there is none */
    public static int findIndex(List<Item> items) {
        if (mc.player == null) return -1;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && items.contains(stack.getItem())) return i;
        }

        return -1;
    }

    public static int findIndex(Item item) {
        return findIndex(List.of(item));
    }

    /** @return index of the smallest non empty stack of that item, useful when only a few items have to move */
    public static int findSmallestIndex(Item item) {
        if (mc.player == null) return -1;

        int slot = -1, best = Integer.MAX_VALUE;
        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isOf(item)) continue;

            if (stack.getCount() < best) {
                best = stack.getCount();
                slot = i;
            }
        }

        return slot;
    }

    public static int findEmptyIndex() {
        if (mc.player == null) return -1;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }

        return -1;
    }

    public static int findEmptyHotbarIndex() {
        if (mc.player == null) return -1;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.HOTBAR_END; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }

        return -1;
    }

    /**
     * @return index of a slot that can accept at least one more of that item (a partially filled stack first,
     * then an empty slot)
     */
    public static int findTargetIndex(Item item) {
        if (mc.player == null) return -1;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(item) && stack.getCount() < stack.getMaxCount()) return i;
        }

        return findEmptyIndex();
    }

    /** @return how many more items of that type fit into the inventory */
    public static int freeSpaceFor(Item item) {
        if (mc.player == null) return 0;

        int max = item.getDefaultStack().getMaxCount();
        int space = 0;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isEmpty()) space += max;
            else if (stack.isOf(item)) space += Math.max(0, stack.getMaxCount() - stack.getCount());
        }

        return space;
    }

    // Clicking

    public static boolean isValidSlotId(int slotId) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;
        return slotId >= 0 && slotId < mc.player.currentScreenHandler.slots.size();
    }

    public static ItemStack stackInSlotId(int slotId) {
        if (!isValidSlotId(slotId)) return ItemStack.EMPTY;
        return mc.player.currentScreenHandler.getSlot(slotId).getStack();
    }

    /**
     * Sends one slot click, if the rate limiter allows it.
     *
     * @return true when the click was actually sent
     */
    public static boolean click(int slotId, int button, SlotActionType type) {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (!isValidSlotId(slotId)) return false;
        if (limiter != null && !limiter.trySend(1)) return false;

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, button, type, mc.player);
        return true;
    }

    /** Drops the whole stack in a slot. */
    public static boolean drop(int slotId) {
        return click(slotId, 1, SlotActionType.THROW);
    }

    /**
     * Drops a single item out of a slot.
     * <p>
     * Meteor's {@code InvUtils.drop()} always throws the whole stack on this version, so the "drop an exact
     * remainder" case is done here with the vanilla button-0 throw.
     */
    public static boolean dropOne(int slotId) {
        return click(slotId, 0, SlotActionType.THROW);
    }

    public static ItemStack cursor() {
        if (mc.player == null || mc.player.currentScreenHandler == null) return ItemStack.EMPTY;
        return mc.player.currentScreenHandler.getCursorStack();
    }

    /**
     * Puts whatever is on the cursor back into the inventory. Called defensively after every multi click
     * operation so a failed transfer can never leave items stuck on the cursor.
     */
    public static void returnCursor(int preferredSlotId) {
        if (cursor().isEmpty()) return;

        if (isValidSlotId(preferredSlotId)) {
            ItemStack target = stackInSlotId(preferredSlotId);
            if (target.isEmpty() || (target.isOf(cursor().getItem()) && target.getCount() < target.getMaxCount())) {
                click(preferredSlotId, 0, SlotActionType.PICKUP);
                if (cursor().isEmpty()) return;
            }
        }

        int index = findTargetIndex(cursor().getItem());
        if (index != -1) {
            int id = SlotUtils.indexToId(index);
            if (id != -1) click(id, 0, SlotActionType.PICKUP);
        }
    }

    /**
     * Moves exactly {@code amount} items from one slot to another, within a single tick.
     * <p>
     * Whole stacks are moved with one pickup/place pair, a partial amount is placed one by one with right clicks
     * and the remainder of the source stack is put back, which is the only way of moving an exact amount without
     * relying on the server confirming anything in between. The per-item clicks are capped by the remaining
     * rate limiter budget, so this can no longer produce a burst of 64 packets in one tick.
     *
     * @return the number of items that were moved
     */
    public static int moveExact(int fromSlotId, int toSlotId, int amount) {
        if (amount <= 0 || !isValidSlotId(fromSlotId) || !isValidSlotId(toSlotId)) return 0;
        if (!cursor().isEmpty()) returnCursor(fromSlotId);

        ItemStack source = stackInSlotId(fromSlotId);
        if (source.isEmpty()) return 0;

        ItemStack target = stackInSlotId(toSlotId);
        if (!target.isEmpty() && !target.isOf(source.getItem())) return 0;

        int space = target.isEmpty() ? source.getMaxCount() : target.getMaxCount() - target.getCount();
        int move = Math.min(Math.min(amount, source.getCount()), space);
        if (move <= 0) return 0;

        boolean wholeStack = move == source.getCount();

        // A partial move needs `move` right clicks plus the pickup and the put back.
        int needed = wholeStack ? 2 : move + 2;
        if (clickBudget() < needed) {
            // Not enough budget this tick; the task retries next tick rather than sending half a transfer.
            if (!wholeStack) return 0;
        }

        if (!click(fromSlotId, 0, SlotActionType.PICKUP)) return 0;
        if (cursor().isEmpty()) return 0;

        int placed;

        if (wholeStack) {
            click(toSlotId, 0, SlotActionType.PICKUP);
            placed = move;
        }
        else {
            placed = 0;
            for (int i = 0; i < move; i++) {
                if (!click(toSlotId, 1, SlotActionType.PICKUP)) break;
                placed++;
            }
        }

        returnCursor(fromSlotId);
        return placed;
    }

    /**
     * Closes whatever container is currently open.
     * <p>
     * Done explicitly instead of through the player method so it also works when the screen itself was never
     * shown (the module cancels those).
     */
    public static void closeScreen() {
        if (mc.player == null || mc.player.currentScreenHandler == mc.player.playerScreenHandler) return;

        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        mc.player.currentScreenHandler = mc.player.playerScreenHandler;

        if (mc.currentScreen instanceof HandledScreen<?>) mc.setScreen(null);
    }

    // Shulker box items

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && Utils.isShulker(stack.getItem());
    }

    /** Reads the contents of a shulker box item, empty stacks included. */
    public static ItemStack[] readContainer(ItemStack shulker) {
        ItemStack[] items = new ItemStack[SHULKER_SLOTS];
        for (int i = 0; i < items.length; i++) items[i] = ItemStack.EMPTY;

        if (isShulker(shulker)) Utils.getItemsInContainerItem(shulker, items);
        return items;
    }

    /** Free slots of a shulker box item, as required before placing it down for a dump. */
    public static int containerFreeSlots(ItemStack shulker) {
        int free = 0;
        for (ItemStack stack : readContainer(shulker)) {
            if (stack.isEmpty()) free++;
        }
        return free;
    }

    /** How many items of that type the shulker box item can still accept. */
    public static int containerSpaceFor(ItemStack shulker, Item item) {
        int max = item.getDefaultStack().getMaxCount();
        int space = 0;

        for (ItemStack stack : readContainer(shulker)) {
            if (stack.isEmpty()) space += max;
            else if (stack.isOf(item)) space += Math.max(0, stack.getMaxCount() - stack.getCount());
        }

        return space;
    }

    public static int containerCount(ItemStack shulker, List<Item> items) {
        int count = 0;
        for (ItemStack stack : readContainer(shulker)) {
            if (!stack.isEmpty() && items.contains(stack.getItem())) count += stack.getCount();
        }
        return count;
    }

    /** Finds a slot inside an open container screen that can accept the item, container slots only. */
    public static int findContainerTargetSlot(ScreenHandler handler, int containerSlots, Item item) {
        int empty = -1;

        for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();

            if (stack.isEmpty()) {
                if (empty == -1) empty = i;
            }
            else if (stack.isOf(item) && stack.getCount() < stack.getMaxCount()) return i;
        }

        return empty;
    }

    /** Finds a container slot holding one of the items. */
    public static int findContainerSlotWith(ScreenHandler handler, int containerSlots, List<Item> items) {
        for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && items.contains(stack.getItem())) return i;
        }

        return -1;
    }
}
