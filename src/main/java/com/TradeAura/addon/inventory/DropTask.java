package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.item.ItemStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Drops excess items.
 * <p>
 * Drops {@code total - leave} items of a single rule. The direction is relative to the player, so the task
 * rotates first and throws inside the rotation callback: meteor sends the rotation with the movement packet of
 * the very same tick, which means the server already knows where we look when the throw arrives.
 * <p>
 * Unlike the original version the throwing is spread over as many ticks as the rate limiter needs. The old code
 * could send up to 128 throw packets inside one tick, which is exactly the kind of burst that gets a client
 * kicked.
 */
public class DropTask extends InvTask {
    private final ItemRule rule;
    private final int amount;

    private int remaining;
    private boolean rotationRequested;
    private boolean rotated;

    public DropTask(InventoryManager mgr, ItemRule rule, int amount) {
        super(mgr);
        this.rule = rule;
        this.amount = amount;
        this.remaining = amount;
    }

    @Override
    public String id() {
        return "drop";
    }

    @Override
    protected Status run() {
        if (mc.player == null) return fail("no player");

        // Never drop with a container open, the throw would be routed through that screen handler.
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            InvHelper.closeScreen();
            return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
        }

        if (!rotationRequested) {
            rotationRequested = true;

            float[] rotation = s.dropDirection.get().getRotation(mc.player.getYaw());
            Rotations.rotate(rotation[0], rotation[1], 100, () -> rotated = true);

            return Status.RUNNING;
        }

        if (!rotated) {
            return timedOut() ? fail("the rotation was never applied") : Status.RUNNING;
        }

        // Keep looking the same way for every follow-up tick, otherwise the items scatter.
        float[] rotation = s.dropDirection.get().getRotation(mc.player.getYaw());
        Rotations.rotate(rotation[0], rotation[1], 100);

        int dropped = dropSome();

        if (remaining <= 0) {
            debug("Dropped " + (amount - remaining) + " item(s) " + s.dropDirection.get().name().toLowerCase());
            return Status.DONE;
        }

        // Nothing left to drop even though the counter says otherwise: the rule's items ran out.
        if (dropped == 0 && InvHelper.findIndex(rule.items) == -1) {
            debug("Dropped " + (amount - remaining) + " item(s), nothing left of this rule");
            return Status.DONE;
        }

        return timedOut() ? fail("could not drop everything in time") : Status.RUNNING;
    }

    /**
     * Drops as much as this tick's click budget allows.
     *
     * @return how many items went out this tick
     */
    private int dropSome() {
        int dropped = 0;

        while (remaining > 0 && InvHelper.clickBudget() > 0) {
            int index = InvHelper.findIndex(rule.items);
            if (index == -1) break;

            int slotId = SlotUtils.indexToId(index);
            if (slotId == -1) break;

            ItemStack stack = mc.player.getInventory().getStack(index);
            int count = stack.getCount();

            if (count <= remaining) {
                // Whole stack in one packet.
                if (!InvHelper.drop(slotId)) break;
                remaining -= count;
                dropped += count;
            }
            else {
                // Exact remainder, one item per packet.
                if (!InvHelper.dropOne(slotId)) break;
                remaining--;
                dropped++;
            }
        }

        return dropped;
    }
}
