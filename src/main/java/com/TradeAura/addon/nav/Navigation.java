package com.TradeAura.addon.nav;

import com.TradeAura.addon.inventory.MovementControl;
import meteordevelopment.meteorclient.pathing.IPathManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Walks the player to a villager.
 * <p>
 * Pathing goes through Meteor's own {@link PathManagers} abstraction rather than talking to Baritone directly.
 * That has two benefits: the addon has <b>no compile-time dependency on Baritone</b>, so it builds and runs
 * whether or not Baritone is installed, and it automatically uses whichever path manager the user actually has
 * (Baritone, Voyager, or none). When no path manager is present, {@link Mode#Simple} steering is used instead -
 * good enough for a flat trading hall, and honest about its limits everywhere else.
 *
 * <h2>The leash</h2>
 * Every navigation is bounded by an anchor and a radius. The anchor is set when the module is switched on (or
 * wherever you tell it to be) and the module refuses to path to anything outside that radius. Without it, one
 * villager wandering off would walk you across the world; with it, the module works a hall and stays in it.
 *
 * <h2>Manual override</h2>
 * The moment you touch a movement key the module hands control back and stays out of the way for a short while.
 * This is what makes walking through your own trading hall while the module trades feel normal instead of a
 * fight over the controls.
 */
public class Navigation {
    /** How the player is moved. */
    public enum Mode {
        /** Never move the player; the aura only trades with whatever is already in reach. */
        Off,
        /**
         * Use the installed path manager (Baritone or Voyager). Handles stairs, doors, gaps and obstacles.
         * Falls back to {@link #Simple} when nothing is installed.
         */
        Pathfind,
        /**
         * Press the movement keys towards the target and jump at obstacles. No dependencies, no pathfinding -
         * fine in an open, flat hall, useless in a maze.
         */
        Simple
    }

    /** The outcome of one navigation tick. */
    public enum Status {
        /** Close enough to trade. */
        Arrived,
        /** On the way. */
        Moving,
        /** Could not get there; the module should give up on this villager for a while. */
        GaveUp,
        /** Navigation is off, or the target is outside the leash. */
        Unavailable
    }

    /**
     * @param mode          how to move
     * @param stopDistance  stop this far from the villager; should be a little under the trade range
     * @param leashRadius   never path further than this from the anchor
     * @param repathTicks   how often the goal may be re-issued when the villager moves
     * @param timeoutTicks  give up on a villager after this many ticks of walking
     * @param releaseTicks  how long to keep hands off after the player touches a movement key
     */
    public record Config(
        Mode mode,
        double stopDistance,
        double leashRadius,
        int repathTicks,
        int timeoutTicks,
        int releaseTicks
    ) {
    }

    private BlockPos anchor;

    private Entity target;
    private BlockPos lastGoal;
    private int travelTicks;
    private int repathCooldown;
    private int manualOverride;
    private boolean controlling;

    // Anchor

    /** Sets the point every leash check is measured from. */
    public void setAnchor(BlockPos pos) {
        anchor = pos;
    }

    public BlockPos anchor() {
        return anchor;
    }

    /** Sets the anchor to the player's current position if it has none yet. */
    public void anchorHereIfUnset() {
        if (anchor == null && mc.player != null) anchor = mc.player.getBlockPos();
    }

    /** Is the entity inside the leash radius around the anchor? */
    public boolean withinLeash(Entity entity, double leashRadius) {
        if (anchor == null || leashRadius <= 0) return true;

        double dx = entity.getX() - (anchor.getX() + 0.5);
        double dz = entity.getZ() - (anchor.getZ() + 0.5);

        return dx * dx + dz * dz <= leashRadius * leashRadius;
    }

    /** How far the player has strayed from the anchor, horizontally. */
    public double distanceFromAnchor() {
        if (anchor == null || mc.player == null) return 0;

        double dx = mc.player.getX() - (anchor.getX() + 0.5);
        double dz = mc.player.getZ() - (anchor.getZ() + 0.5);

        return Math.sqrt(dx * dx + dz * dz);
    }

    // Manual override

    /**
     * True while the player is driving. Has to be polled every tick so a key press is never missed.
     *
     * @param releaseTicks how long a press keeps the module out of the way
     */
    public boolean playerIsDriving(int releaseTicks) {
        if (mc.options == null) return false;

        boolean pressed = Input.isPressed(mc.options.forwardKey)
            || Input.isPressed(mc.options.backKey)
            || Input.isPressed(mc.options.leftKey)
            || Input.isPressed(mc.options.rightKey)
            || Input.isPressed(mc.options.jumpKey);

        if (pressed) manualOverride = Math.max(1, releaseTicks);
        else if (manualOverride > 0) manualOverride--;

        return manualOverride > 0;
    }

    /** Ticks left before the module takes the controls back. */
    public int overrideTicksLeft() {
        return manualOverride;
    }

    // Navigation

    /**
     * Moves one tick towards the villager.
     *
     * @param villager the villager to reach, never {@code null}
     * @param config   the current settings
     */
    public Status tick(Entity villager, Config config) {
        if (mc.player == null) return Status.Unavailable;
        if (config.mode() == Mode.Off) return Status.Unavailable;

        if (!withinLeash(villager, config.leashRadius())) {
            release();
            return Status.Unavailable;
        }

        // New target: start the clock over.
        if (villager != target) {
            target = villager;
            travelTicks = 0;
            repathCooldown = 0;
            lastGoal = null;
        }

        if (PlayerUtils.distanceTo(villager) <= config.stopDistance()) {
            release();
            return Status.Arrived;
        }

        if (++travelTicks > config.timeoutTicks()) {
            release();
            return Status.GaveUp;
        }

        controlling = true;

        if (config.mode() == Mode.Pathfind && pathingAvailable()) pathTowards(villager, config);
        else MovementControl.walkTowards(villager.getX(), villager.getZ());

        return Status.Moving;
    }

    /** Walks back to the anchor, used when there is nothing to do and you would rather not be left in a corner. */
    public Status returnToAnchor(Config config) {
        if (mc.player == null || anchor == null || config.mode() == Mode.Off) return Status.Unavailable;

        if (distanceFromAnchor() <= Math.max(1.0, config.stopDistance())) {
            release();
            return Status.Arrived;
        }

        controlling = true;

        if (config.mode() == Mode.Pathfind && pathingAvailable()) {
            if (repathCooldown > 0) repathCooldown--;
            else {
                repathCooldown = Math.max(1, config.repathTicks());
                PathManagers.get().moveTo(anchor, false);
                lastGoal = anchor;
            }
        }
        else {
            MovementControl.walkTowards(anchor.getX() + 0.5, anchor.getZ() + 0.5);
        }

        return Status.Moving;
    }

    private void pathTowards(Entity villager, Config config) {
        BlockPos goal = villager.getBlockPos();

        // Re-issuing the goal every tick makes Baritone recalculate constantly and the player stutters in place.
        boolean goalMoved = lastGoal == null || lastGoal.getSquaredDistance(goal) > 4;

        if (repathCooldown > 0) repathCooldown--;
        if (!goalMoved && PathManagers.get().isPathing()) return;
        if (repathCooldown > 0) return;

        repathCooldown = Math.max(1, config.repathTicks());
        lastGoal = goal;

        PathManagers.get().moveTo(goal, false);
    }

    /** Stops whatever the module was doing with the controls. Safe to call at any time. */
    public void release() {
        if (!controlling) {
            MovementControl.stop();
            return;
        }

        controlling = false;
        target = null;
        lastGoal = null;
        travelTicks = 0;
        repathCooldown = 0;

        if (pathingAvailable()) PathManagers.get().stop();
        MovementControl.stop();
    }

    /**
     * Full reset, for when the module is switched off.
     * <p>
     * The anchor deliberately survives: one you placed by hand with the keybind is a decision, not runtime
     * state, and losing it every time the module is toggled would make the keybind useless.
     */
    public void reset() {
        release();
        manualOverride = 0;
    }

    /** Forgets the anchor as well. */
    public void clearAnchor() {
        anchor = null;
    }

    public boolean isControlling() {
        return controlling;
    }

    public Entity target() {
        return target;
    }

    // Path manager

    /** Is a real path manager installed? */
    public static boolean pathingAvailable() {
        return !"none".equalsIgnoreCase(pathManagerName());
    }

    /** "baritone", "voyager" or "none" - shown in the module GUI so it is obvious which backend is in use. */
    public static String pathManagerName() {
        IPathManager manager = PathManagers.get();
        return manager == null ? "none" : manager.getName();
    }
}
