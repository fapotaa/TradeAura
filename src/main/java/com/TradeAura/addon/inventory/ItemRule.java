package com.TradeAura.addon.inventory;

import com.TradeAura.addon.trading.RuleNbt;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * A single user configured rule for the "Inventory manipulation" tab.
 * <p>
 * {@link #trigger} and {@link #leave} are interpreted differently depending on the trigger that owns the rule:
 * <ul>
 *     <li>Drop / Dump: fires when the total amount of {@link #items} in the inventory is <b>above</b>
 *     {@code trigger}; the amount that gets removed is {@code total - leave}.</li>
 *     <li>Refill: fires when the total amount is <b>below</b> {@code trigger}; the amount that gets pulled in is
 *     {@code leave - total}.</li>
 * </ul>
 */
public class ItemRule {
    public final List<Item> items = new ArrayList<>();
    public int trigger;
    public int leave;

    public ItemRule() {
        this(64, 32);
    }

    public ItemRule(int trigger, int leave) {
        this.trigger = trigger;
        this.leave = leave;
    }

    public boolean isValid() {
        return !items.isEmpty();
    }

    // NBT

    public static NbtList listToTag(List<ItemRule> rules) {
        NbtList list = new NbtList();

        for (ItemRule rule : rules) {
            NbtCompound ruleTag = new NbtCompound();
            ruleTag.put("items", RuleNbt.itemsToTag(rule.items));
            ruleTag.putInt("trigger", rule.trigger);
            ruleTag.putInt("leave", rule.leave);
            list.add(ruleTag);
        }

        return list;
    }

    public static void listFromTag(NbtList list, List<ItemRule> rules) {
        rules.clear();

        for (NbtElement element : list) {
            if (!(element instanceof NbtCompound ruleTag)) continue;

            ItemRule rule = new ItemRule();
            RuleNbt.readItems(ruleTag, rule.items);

            rule.trigger = RuleNbt.readInt(ruleTag, "trigger", null, 64);
            rule.leave = RuleNbt.readInt(ruleTag, "leave", null, 32);

            rules.add(rule);
        }
    }
}
