package com.TradeAura.addon.trading;

import com.TradeAura.addon.inventory.InvHelper;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Rerolls the trades of a single villager by taking its workstation away and giving it back.
 * <p>
 * This is the classic trading-hall routine, automated: a villager whose profession is not locked in yet (level 1
 * and no trade ever used) loses its profession when its job site block is removed, and rolls a brand new trade
 * list when it claims the block again. It is the only way to get a specific enchanted book or a cheap price out
 * of a villager, and it is what turns the module from "trade whatever is offered" into "keep rolling until the
 * trade I configured shows up".
 *
 * <h2>Why the "never traded" check matters</h2>
 * The moment a player completes even one trade with a villager, its profession and its offers are locked
 * forever; breaking the workstation after that only removes the villager's ability to restock. The task refuses
 * to start in that case instead of destroying a working villager, which is checked through
 * {@link VillagerUtil#canBeRerolled}.
 *
 * <h2>How the block comes back</h2>
 * The broken block drops as an item. The task waits for it to be picked back up before placing it again, so no
 * spare workstation has to be carried - but if one is already in the inventory it is used straight away and the
 * dropped one is simply collected later.
 */
public class RerollTask {
    public enum Status {
        RUNNING,
        DONE,
        FAILED
    }

    private enum State {
        Init,
        Break,
        WaitBroken,
        WaitItem,
        Place,
        WaitPlaced,
        Settle
    }

    /** Settings the task needs, passed in so it stays independent of the module's setting layout. */
    public record Config(
        double searchRadius,
        int timeoutTicks,
        int settleTicks,
        boolean rotate
    ) {
    }

    private final Entry villager;
    private final Config config;
    private final Consumer<String> log;

    private State state = State.Init;
    private int stateTicks;
    private String failReason = "";

    private BlockPos stationPos;
    private Block stationBlock;

    /** The villager this task is about, plus the profession it had when the task started. */
    public record Entry(Entity entity, String professionId) {
    }

    public RerollTask(Entry villager, Config config, Consumer<String> log) {
        this.villager = villager;
        this.config = config;
        this.log = log;
    }

    public String failReason() {
        return failReason;
    }

    public Entity villager() {
        return villager.entity();
    }

    /** Where the workstation is, so the module can render it. {@code null} until it has been found. */
    public BlockPos stationPos() {
        return stationPos;
    }

    public Status tick() {
        stateTicks++;

        if (mc.player == null || mc.world == null) return fail("no player");
        if (!villager.entity().isAlive()) return fail("the villager is gone");

        return switch (state) {
            case Init -> init();
            case Break -> breakStation();
            case WaitBroken -> waitBroken();
            case WaitItem -> waitItem();
            case Place -> place();
            case WaitPlaced -> waitPlaced();
            case Settle -> settle();
        };
    }

    private Status init() {
        stationBlock = TradeData.workstation(villager.professionId());
        if (stationBlock == null) return fail("no workstation is known for '" + villager.professionId() + "'");

        stationPos = findStation(stationBlock);
        if (stationPos == null) return fail("no " + name(stationBlock) + " within reach of the villager");

        if (!BlockUtils.canBreak(stationPos)) return fail("the " + name(stationBlock) + " cannot be broken");

        log.accept("Rerolling: breaking the " + name(stationBlock) + " at " + stationPos.toShortString());
        setState(State.Break);
        return Status.RUNNING;
    }

    private Status breakStation() {
        if (!mc.world.getBlockState(stationPos).isOf(stationBlock)) {
            setState(State.WaitBroken);
            return Status.RUNNING;
        }

        if (timedOut()) return fail("could not break the " + name(stationBlock));

        BlockUtils.breakBlock(stationPos, true);
        return Status.RUNNING;
    }

    private Status waitBroken() {
        if (mc.world.getBlockState(stationPos).isOf(stationBlock)) {
            // It came back, something replaced it - start over.
            setState(State.Break);
            return Status.RUNNING;
        }

        // Give the server a moment to tell the villager it lost its job before the block goes back down,
        // otherwise the same profession is simply re-claimed with the same offers.
        if (stateTicks < config.settleTicks()) return Status.RUNNING;

        setState(State.WaitItem);
        return Status.RUNNING;
    }

    private Status waitItem() {
        if (findBlockItem().found()) {
            setState(State.Place);
            return Status.RUNNING;
        }

        if (timedOut()) return fail("the " + name(stationBlock) + " was never picked back up");
        return Status.RUNNING;
    }

    private Status place() {
        FindItemResult item = findBlockItem();
        if (!item.found()) {
            setState(State.WaitItem);
            return Status.RUNNING;
        }

        if (!PlayerUtils.isWithinReach(stationPos)) return fail("the workstation spot is out of reach");

        BlockUtils.place(stationPos, item, config.rotate(), 100, true, true, true);

        setState(State.WaitPlaced);
        return Status.RUNNING;
    }

    private Status waitPlaced() {
        if (mc.world.getBlockState(stationPos).isOf(stationBlock)) {
            setState(State.Settle);
            return Status.RUNNING;
        }

        if (timedOut()) return fail("the " + name(stationBlock) + " could not be placed back");

        // Retry every few ticks, the first attempt can be eaten by a lag spike.
        if (stateTicks % 10 == 0) {
            FindItemResult item = findBlockItem();
            if (item.found()) BlockUtils.place(stationPos, item, config.rotate(), 100, true, true, true);
        }

        return Status.RUNNING;
    }

    /** Give the villager time to claim the block again and roll its new trades. */
    private Status settle() {
        if (stateTicks < config.settleTicks()) return Status.RUNNING;

        log.accept("Rerolling: the " + name(stationBlock) + " is back, checking the new trades");
        return Status.DONE;
    }

    // Helpers

    /**
     * The workstation belonging to this villager: the closest matching block that is both within the configured
     * radius of the villager and within reach of the player, so the task never targets a neighbour's job site
     * it cannot even touch.
     */
    private BlockPos findStation(Block block) {
        int r = (int) Math.ceil(config.searchRadius());
        BlockPos center = villager.entity().getBlockPos();

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!mc.world.getBlockState(pos).isOf(block)) continue;
                    if (!PlayerUtils.isWithinReach(pos)) continue;

                    double distance = villager.entity().squaredDistanceTo(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

                    if (distance >= bestDistance) continue;

                    best = pos;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    /** The workstation as an item, in the hotbar if possible so no inventory shuffling is needed. */
    private FindItemResult findBlockItem() {
        Item item = stationBlock.asItem();

        FindItemResult hotbar = InvUtils.findInHotbar(item);
        if (hotbar.found()) return hotbar;

        return InvUtils.find(item);
    }

    private static String name(Block block) {
        return block.asItem().getDefaultStack().getName().getString();
    }

    private boolean timedOut() {
        return stateTicks > config.timeoutTicks();
    }

    private void setState(State state) {
        this.state = state;
        this.stateTicks = 0;
    }

    private Status fail(String reason) {
        failReason = reason;
        return Status.FAILED;
    }

    /** Puts the player's held item back and releases anything the task was holding on to. */
    public void cleanup() {
        InvUtils.swapBack();
        InvHelper.returnCursor(-1);
    }
}
