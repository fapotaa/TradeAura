package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.option.KeyBinding;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Client side movement control.
 * <p>
 * Everything here works on the <b>input</b> level (key bindings), never on packets: the movement packets keep
 * being sent exactly as usual, so the server always sees where the player really is and nothing desyncs. What is
 * suppressed is only the movement the client would produce out of the player's own key presses.
 * <p>
 * That is also why walking is done by pressing the movement keys instead of writing to the velocity directly:
 * velocity set before the player tick is immediately reworked by friction and by the (empty) movement input, so
 * the player barely moves. Pressing the keys makes the player walk for real, including sprinting rules, step
 * assist and collision handling.
 */
public final class MovementControl {
    /** Set while this class holds any key down, so the keys are only released when they were pressed by us. */
    private static boolean holding;

    private MovementControl() {
    }

    /**
     * Suppresses the player's own movement input for this tick. Has to be called every tick to stay in effect,
     * because a key binding is re-read by the client on every tick.
     */
    public static void freeze() {
        holding = true;
        releaseKeys();

        if (mc.player != null) {
            mc.player.setSprinting(false);
            mc.player.setSneaking(false);
        }
    }

    /**
     * Walks towards a position by pressing the movement keys that fit best, relative to where the player is
     * currently looking. The camera is never turned, so this also works while rotations are disabled.
     */
    public static void walkTowards(double targetX, double targetZ) {
        if (mc.player == null || mc.options == null) return;

        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length < 1.0E-4) {
            stop();
            return;
        }

        dx /= length;
        dz /= length;

        double yaw = Math.toRadians(mc.player.getYaw());
        // Where "W" and "A" would take the player with the current yaw.
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double leftX = Math.cos(yaw), leftZ = Math.sin(yaw);

        double forward = dx * forwardX + dz * forwardZ;
        double left = dx * leftX + dz * leftZ;

        releaseKeys();
        holding = true;

        // 0.35 is roughly the point where a diagonal is closer to the target than a single direction.
        if (forward > 0.35) mc.options.forwardKey.setPressed(true);
        else if (forward < -0.35) mc.options.backKey.setPressed(true);

        if (left > 0.35) mc.options.leftKey.setPressed(true);
        else if (left < -0.35) mc.options.rightKey.setPressed(true);

        // Simple obstacle handling: bump into something -> hop over it.
        if (mc.player.horizontalCollision && mc.player.isOnGround()) mc.options.jumpKey.setPressed(true);
    }

    /**
     * Hands control back to the player. Safe to call at any time.
     * <p>
     * The key bindings are restored to what the keyboard actually says instead of just being cleared: a key
     * binding that is forced to "up" while the key is physically held stays up until the next key event, which
     * is why the player had to let go and press again after the module released them.
     */
    public static void stop() {
        if (!holding) return;
        holding = false;

        if (mc.options == null) return;

        restore(mc.options.forwardKey);
        restore(mc.options.backKey);
        restore(mc.options.leftKey);
        restore(mc.options.rightKey);
        restore(mc.options.jumpKey);
        restore(mc.options.sneakKey);
    }

    private static void restore(KeyBinding key) {
        key.setPressed(Input.isPressed(key));
    }

    private static void releaseKeys() {
        if (mc.options == null) return;

        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }
}
