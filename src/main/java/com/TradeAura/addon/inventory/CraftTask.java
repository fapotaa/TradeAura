package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Compress emeralds, decompress emeralds and the glass pane recipe.
 * <p>
 * Every used grid slot gets exactly {@code crafts} ingredients and the result is shift clicked exactly once,
 * which produces exactly {@code crafts} results - no over crafting.
 * <p>
 * Filling the grid is limited by the shared click budget rather than by a fixed number of slots per tick, so a
 * full 64-craft operation is spread over as many ticks as the rate limit needs instead of being fired in one
 * burst. Recipes that fit into the 2x2 grid never open a crafting table.
 */
public class CraftTask extends InvTask {
    private enum State {
        Init,
        OpenTable,
        WaitScreen,
        Fill,
        Take,
        Verify,
        Done
    }

    /** A grid slot can never hold more than a stack, so this is the hard ceiling for one operation. */
    public static final int MAX_CRAFTS = 64;

    private final CraftRecipe recipe;
    private final int crafts;
    private final int[] gridSlots;

    private State state = State.Init;
    private BlockPos tablePos;
    private int gridIndex;
    private int delay;

    public CraftTask(InventoryManager mgr, CraftRecipe recipe, int crafts) {
        super(mgr);
        this.recipe = recipe;
        this.crafts = Math.min(crafts, MAX_CRAFTS);
        this.gridSlots = recipe.gridSlots();
    }

    @Override
    public String id() {
        return recipe.id();
    }

    @Override
    protected Status run() {
        if (mc.player == null || mc.world == null) return fail("no player");
        if (crafts <= 0) return fail("nothing to craft");

        if (delay > 0) {
            delay--;
            return Status.RUNNING;
        }

        return switch (state) {
            case Init -> init();
            case OpenTable -> openTable();
            case WaitScreen -> waitScreen();
            case Fill -> fill();
            case Take -> take();
            case Verify -> verify();
            case Done -> Status.DONE;
        };
    }

    private Status init() {
        if (!recipe.needsTable()) {
            // 2x2 grid of the player screen handler, no table needed - but no other screen may be open.
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                InvHelper.closeScreen();
                return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
            }

            setState(State.Fill);
            return Status.RUNNING;
        }

        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) {
            setState(State.Fill);
            return Status.RUNNING;
        }

        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            InvHelper.closeScreen();
            return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
        }

        tablePos = findCraftingTable(s.craftingTableRange.get());
        if (tablePos == null) return fail("no crafting table in range");

        setState(State.OpenTable);
        return Status.RUNNING;
    }

    private Status openTable() {
        if (tablePos == null) return fail("no crafting table in range");
        if (!mc.world.getBlockState(tablePos).isOf(Blocks.CRAFTING_TABLE)) return fail("crafting table vanished");

        Vec3d hitPos = Vec3d.ofCenter(tablePos);
        BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, tablePos, false);

        Runnable interact = () -> {
            // Sneaking would place the held block instead of opening the table.
            boolean sneaking = mc.player.isSneaking();
            mc.player.setSneaking(false);

            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);

            mc.player.setSneaking(sneaking);
        };

        if (s.rotate.get()) Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 100, interact);
        else interact.run();

        setState(State.WaitScreen);
        return Status.RUNNING;
    }

    private Status waitScreen() {
        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) {
            setState(State.Fill);
            return Status.RUNNING;
        }

        return timedOut() ? fail("crafting table did not open") : Status.RUNNING;
    }

    private Status fill() {
        if (!handlerValid()) return fail("screen closed while filling the grid");

        while (gridIndex < gridSlots.length) {
            int slotId = gridSlots[gridIndex];

            if (InvHelper.stackInSlotId(slotId).getCount() >= crafts) {
                gridIndex++;
                continue;
            }

            // Out of click budget for this tick: come back next tick instead of half-filling a slot.
            if (InvHelper.clickBudget() <= 0) return Status.RUNNING;

            int filled = fillGridSlot(slotId, recipe.input(), crafts);
            if (filled == FILL_OUT_OF_ITEMS) return fail("ran out of " + name(recipe.input()));
            if (filled == FILL_INCOMPLETE) return Status.RUNNING;

            gridIndex++;
        }

        setState(State.Take);
        delay = s.actionDelay.get();
        return Status.RUNNING;
    }

    private Status take() {
        if (!handlerValid()) return fail("screen closed before taking the result");

        // Every used grid slot holds exactly `crafts` ingredients, so one shift click crafts exactly `crafts`
        // times. If the budget is momentarily empty the click is simply retried next tick.
        if (!InvHelper.click(0, 0, SlotActionType.QUICK_MOVE)) {
            return timedOut() ? fail("could not take the crafting result") : Status.RUNNING;
        }

        setState(State.Verify);
        delay = Math.max(1, s.actionDelay.get());
        return Status.RUNNING;
    }

    private Status verify() {
        if (!handlerValid()) return fail("screen closed before the grid was cleared");

        // Pull back whatever is left in the grid (happens when the inventory filled up mid craft).
        boolean leftovers = clearGrid();

        if (leftovers && !timedOut()) {
            delay = Math.max(1, s.actionDelay.get());
            return Status.RUNNING;
        }

        if (leftovers) {
            // The grid could not be emptied, most likely because the inventory is full. cleanup() tries again.
            return fail("the crafting grid could not be emptied, inventory full?");
        }

        debug(recipe.id() + ": crafted " + crafts + "x " + name(recipe.output()));
        setState(State.Done);
        return Status.DONE;
    }

    private static final int FILL_OUT_OF_ITEMS = -1;
    private static final int FILL_INCOMPLETE = -2;

    /**
     * Puts exactly {@code need} items into a grid slot, pulling from as many inventory stacks as necessary.
     *
     * @return the number of items in the slot afterwards, {@link #FILL_OUT_OF_ITEMS} when the ingredients ran
     * out, or {@link #FILL_INCOMPLETE} when the click budget ran out and the slot needs another tick
     */
    private int fillGridSlot(int slotId, Item item, int need) {
        int have = InvHelper.stackInSlotId(slotId).getCount();
        int guard = 0;

        while (have < need && guard++ < 16) {
            if (InvHelper.clickBudget() <= 0) {
                InvHelper.returnCursor(-1);
                return FILL_INCOMPLETE;
            }

            int index = InvHelper.findIndex(item);
            if (index == -1) break;

            int fromId = SlotUtils.indexToId(index);
            if (fromId == -1) break;

            int moved = InvHelper.moveExact(fromId, slotId, need - have);
            if (moved <= 0) {
                InvHelper.returnCursor(-1);
                // Budget, not a missing item: try again next tick.
                return InvHelper.clickBudget() <= 0 ? FILL_INCOMPLETE : FILL_OUT_OF_ITEMS;
            }

            have += moved;
        }

        InvHelper.returnCursor(-1);
        return have >= need ? have : FILL_OUT_OF_ITEMS;
    }

    private boolean clearGrid() {
        boolean leftovers = false;

        for (int i = 1; i <= recipe.gridSize(); i++) {
            if (InvHelper.stackInSlotId(i).isEmpty()) continue;

            InvHelper.click(i, 0, SlotActionType.QUICK_MOVE);
            if (!InvHelper.stackInSlotId(i).isEmpty()) leftovers = true;
        }

        return leftovers;
    }

    private boolean handlerValid() {
        if (recipe.needsTable()) return mc.player.currentScreenHandler instanceof CraftingScreenHandler;
        return mc.player.currentScreenHandler == mc.player.playerScreenHandler;
    }

    /** Nearest crafting table within range, {@code null} when there is none - the trigger then does not fire. */
    public static BlockPos findCraftingTable(double range) {
        if (mc.player == null || mc.world == null) return null;

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        int r = (int) Math.ceil(range) + 1;
        BlockPos center = mc.player.getBlockPos();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) continue;

                    double distance = PlayerUtils.distanceTo(pos);
                    if (distance > range || distance >= bestDistance) continue;

                    best = pos;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private static String name(Item item) {
        return item.getDefaultStack().getName().getString();
    }

    private void setState(State state) {
        this.state = state;
        resetStateTimer();
    }

    @Override
    public void cleanup() {
        if (mc.player == null) return;

        InvHelper.returnCursor(-1);

        // Never leave ingredients behind in the grid, the 2x2 grid in particular would keep them until the
        // player opens their inventory manually.
        if (handlerValid()) clearGrid();

        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) InvHelper.closeScreen();
    }
}
