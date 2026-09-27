package com.TradeAura.addon.trading;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the Buy Rules or Sell Rules table.
 * <p>
 * The two numbers mean different things per direction, which is why they are called {@code limitA} /
 * {@code limitB} in {@link TradeEvaluator.PriceRule} instead of "price" and "limit":
 * <table>
 *     <caption>Meaning of the two limits</caption>
 *     <tr><th></th><th>limitA</th><th>limitB</th></tr>
 *     <tr><td>Buy</td><td>highest emerald price to accept</td><td>stop once the inventory holds this many</td></tr>
 *     <tr><td>Sell</td><td>highest quantity to hand over per trade</td><td>stop at this many emeralds</td></tr>
 * </table>
 * {@code -1} disables a limit. Unlike the original version this is now honoured for buy prices too.
 */
public class TradeRule implements TradeEvaluator.PriceRule<Item> {
    public final List<Item> items = new ArrayList<>();
    public int limitA;
    public int limitB;

    public TradeRule() {
        this(-1, -1);
    }

    public TradeRule(int limitA, int limitB) {
        this.limitA = limitA;
        this.limitB = limitB;
    }

    @Override
    public List<Item> items() {
        return items;
    }

    @Override
    public int limitA() {
        return limitA;
    }

    @Override
    public int limitB() {
        return limitB;
    }

    public boolean isValid() {
        return !items.isEmpty();
    }

    // NBT

    public static NbtList listToTag(List<TradeRule> rules) {
        NbtList list = new NbtList();

        for (TradeRule rule : rules) {
            NbtCompound ruleTag = new NbtCompound();
            ruleTag.put("items", RuleNbt.itemsToTag(rule.items));
            ruleTag.putInt("limitA", rule.limitA);
            ruleTag.putInt("limitB", rule.limitB);
            list.add(ruleTag);
        }

        return list;
    }

    /**
     * Replaces the contents of {@code rules}. The list is cleared first so switching profiles cannot leave
     * rules from the previous profile behind, which the original version did.
     */
    public static void listFromTag(NbtList list, List<TradeRule> rules) {
        rules.clear();

        for (NbtElement element : list) {
            if (!(element instanceof NbtCompound ruleTag)) continue;

            TradeRule rule = new TradeRule();
            RuleNbt.readItems(ruleTag, rule.items);

            // "value1" / "value2" are the key names the original version wrote, kept so existing configs load.
            rule.limitA = RuleNbt.readInt(ruleTag, "limitA", "value1", -1);
            rule.limitB = RuleNbt.readInt(ruleTag, "limitB", "value2", -1);

            rules.add(rule);
        }
    }
}
