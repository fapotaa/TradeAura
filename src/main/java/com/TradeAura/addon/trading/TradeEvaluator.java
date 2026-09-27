package com.TradeAura.addon.trading;

import java.util.List;

/**
 * Decides what to do with a single villager offer.
 * <p>
 * This class deliberately contains <b>no Minecraft imports at all</b>. Everything it needs is handed in as plain
 * numbers plus an opaque item key, which has three consequences:
 * <ul>
 *     <li>the rules that decide whether real emeralds get spent are covered by ordinary unit tests
 *     (see {@code src/test/java}), instead of only being testable by standing in front of a villager;</li>
 *     <li>the module itself stays a thin adapter that reads the current screen and applies the verdict;</li>
 *     <li>porting to another Minecraft version only touches the adapter, never the trading rules.</li>
 * </ul>
 *
 * <h2>What this fixes compared to the original implementation</h2>
 * <ul>
 *     <li><b>The second ingredient is no longer ignored.</b> A trade costing "1 emerald + 1 book" used to be
 *     attempted as if it cost one emerald.</li>
 *     <li><b>Prices are checked against what you actually pay.</b> The caller passes the demand- and
 *     discount-adjusted cost, so a trade whose price rose because of demand is now rejected correctly.</li>
 *     <li><b>Affordability is checked exactly.</b> Owning a single emerald no longer counts as being able to
 *     afford a 30 emerald trade.</li>
 *     <li><b>A price limit of -1 means "no limit"</b> on buy rules as well. Previously -1 made every buy
 *     trade look too expensive, so buy rules silently never fired.</li>
 *     <li><b>Inventory space is checked</b> before a trade, so results can no longer be lost.</li>
 * </ul>
 *
 * <p>The type parameter {@code I} used throughout is the item key type - {@code net.minecraft.item.Item} in the
 * module, {@code String} in the tests. Keys are compared with {@link Object#equals(Object)}.
 */
public final class TradeEvaluator {
    private TradeEvaluator() {
    }

    /** A user configured buy or sell rule, as far as the decision logic cares about it. */
    public interface PriceRule<I> {
        /** Items this rule applies to. An empty list disables the rule. */
        List<I> items();

        /** Buy: the highest price to pay. Sell: the highest quantity to hand over. -1 means no limit. */
        int limitA();

        /** Buy: stop once the inventory holds this many. Sell: stop at this many emeralds. -1 means no limit. */
        int limitB();
    }

    /**
     * One villager offer, reduced to the numbers a decision needs.
     *
     * @param index      position in the villager's trade list, needed to select the trade
     * @param sellItem   what the villager hands over
     * @param sellCount  how many of it
     * @param costA      first ingredient the player pays, after demand and discount have been applied
     * @param costACount how many of it
     * @param costB      second ingredient, or {@code null} when the trade has none
     * @param costBCount how many of the second ingredient
     * @param disabled   true when the trade is locked out (out of stock until the villager restocks)
     */
    public record Offer<I>(
        int index,
        I sellItem,
        int sellCount,
        I costA,
        int costACount,
        I costB,
        int costBCount,
        boolean disabled
    ) {
    }

    /** The player's inventory, as far as a decision needs it. */
    public interface Stock<I> {
        /** How many of that item the player currently holds. */
        int count(I item);

        /** How many more of that item still fit into the inventory. */
        int freeSpace(I item);
    }

    /** Why an offer was accepted or rejected. The colours in the module map one to one onto these. */
    public enum Verdict {
        /** Buy from the villager. */
        BUY(true),
        /** Sell to the villager. */
        SELL(true),

        /** No configured rule covers this offer. */
        NO_RULE(false),
        /** The villager has run out of this trade until it restocks. */
        OUT_OF_STOCK(false),
        /** Above the configured price (buy) or quantity (sell) limit. */
        TOO_EXPENSIVE(false),
        /** The inventory already holds as much as the rule allows. */
        LIMIT_REACHED(false),
        /** Not enough emeralds to pay for it. */
        NOT_ENOUGH_EMERALDS(false),
        /** Not enough of the items the trade asks for. */
        NOT_ENOUGH_ITEMS(false),
        /** The result would not fit into the inventory. */
        NO_INVENTORY_SPACE(false);

        private final boolean actionable;

        Verdict(boolean actionable) {
            this.actionable = actionable;
        }

        /** True when this verdict means a trade should actually be executed. */
        public boolean actionable() {
            return actionable;
        }
    }

    /**
     * The verdict for one offer plus the numbers behind it, so the module can produce a useful debug line
     * without recomputing anything.
     *
     * @param price how much the offer costs, in emeralds where emeralds are involved and in units of the first
     *              ingredient otherwise
     */
    public record Decision(int offerIndex, Verdict verdict, int price, String detail) {
        public boolean actionable() {
            return verdict.actionable();
        }
    }

    /**
     * Applies the configured rules to one offer.
     *
     * @param offer     the offer, with adjusted prices already applied
     * @param buyRules  rules for items to buy from villagers
     * @param sellRules rules for items to sell to villagers
     * @param stock     the player's inventory
     * @param emerald   the key of the emerald item
     */
    public static <I> Decision evaluate(
        Offer<I> offer,
        List<? extends PriceRule<I>> buyRules,
        List<? extends PriceRule<I>> sellRules,
        Stock<I> stock,
        I emerald
    ) {
        boolean sellingToVillager = emerald.equals(offer.sellItem()) && !emerald.equals(offer.costA());

        return sellingToVillager
            ? evaluateSell(offer, sellRules, stock, emerald)
            : evaluateBuy(offer, buyRules, stock, emerald);
    }

    // Selling: the villager hands over emeralds, the player hands over items.

    private static <I> Decision evaluateSell(
        Offer<I> offer,
        List<? extends PriceRule<I>> sellRules,
        Stock<I> stock,
        I emerald
    ) {
        PriceRule<I> rule = findRule(sellRules, offer.costA());
        if (rule == null) return decision(offer, Verdict.NO_RULE, offer.costACount(), "no sell rule for this item");

        // Checked before the stock check so a locked trade is reported as locked and not as "not enough items".
        if (offer.disabled()) {
            return decision(offer, Verdict.OUT_OF_STOCK, offer.costACount(), "the villager is out of this trade");
        }

        if (rule.limitA() != -1 && offer.costACount() > rule.limitA()) {
            return decision(offer, Verdict.TOO_EXPENSIVE, offer.costACount(),
                "wants " + offer.costACount() + " per trade, limit is " + rule.limitA());
        }

        if (rule.limitB() != -1) {
            int emeralds = stock.count(emerald);
            if (emeralds >= rule.limitB()) {
                return decision(offer, Verdict.LIMIT_REACHED, offer.costACount(),
                    "emerald limit reached (" + emeralds + "/" + rule.limitB() + ")");
            }
        }

        Decision missing = checkIngredients(offer, stock, emerald);
        if (missing != null) return missing;

        if (stock.freeSpace(offer.sellItem()) < offer.sellCount()) {
            return decision(offer, Verdict.NO_INVENTORY_SPACE, offer.costACount(), "no room for the emeralds");
        }

        return decision(offer, Verdict.SELL, offer.costACount(), "selling " + offer.costACount() + " for " + offer.sellCount() + " emerald(s)");
    }

    // Buying: the player pays, the villager hands over the item.

    private static <I> Decision evaluateBuy(
        Offer<I> offer,
        List<? extends PriceRule<I>> buyRules,
        Stock<I> stock,
        I emerald
    ) {
        PriceRule<I> rule = findRule(buyRules, offer.sellItem());
        int price = price(offer, emerald);

        if (rule == null) return decision(offer, Verdict.NO_RULE, price, "no buy rule for this item");

        if (offer.disabled()) {
            return decision(offer, Verdict.OUT_OF_STOCK, price, "the villager is out of this trade");
        }

        if (rule.limitA() != -1 && price > rule.limitA()) {
            return decision(offer, Verdict.TOO_EXPENSIVE, price, "costs " + price + ", limit is " + rule.limitA());
        }

        if (rule.limitB() != -1) {
            int have = stock.count(offer.sellItem());
            if (have >= rule.limitB()) {
                return decision(offer, Verdict.LIMIT_REACHED, price, "limit reached (" + have + "/" + rule.limitB() + ")");
            }
        }

        Decision missing = checkIngredients(offer, stock, emerald);
        if (missing != null) return missing;

        if (stock.freeSpace(offer.sellItem()) < offer.sellCount()) {
            return decision(offer, Verdict.NO_INVENTORY_SPACE, price, "no room for the result");
        }

        return decision(offer, Verdict.BUY, price, "buying " + offer.sellCount() + " for " + price);
    }

    // Shared helpers

    /**
     * Verifies that the player can actually pay for the trade, both ingredients included.
     *
     * @return the rejecting decision, or {@code null} when everything is covered
     */
    private static <I> Decision checkIngredients(Offer<I> offer, Stock<I> stock, I emerald) {
        Decision a = checkIngredient(offer, stock, emerald, offer.costA(), offer.costACount());
        if (a != null) return a;

        return checkIngredient(offer, stock, emerald, offer.costB(), offer.costBCount());
    }

    private static <I> Decision checkIngredient(Offer<I> offer, Stock<I> stock, I emerald, I item, int needed) {
        if (item == null || needed <= 0) return null;

        int have = stock.count(item);
        if (have >= needed) return null;

        Verdict verdict = emerald.equals(item) ? Verdict.NOT_ENOUGH_EMERALDS : Verdict.NOT_ENOUGH_ITEMS;
        return decision(offer, verdict, price(offer, emerald), "need " + needed + ", have " + have);
    }

    /**
     * What the offer costs. Emeralds are the unit whenever the trade involves any, so a "3 emeralds + 1 book"
     * trade is priced at 3 and not at "1 book". Trades that do not involve emeralds at all are priced in units
     * of their first ingredient.
     */
    public static <I> int price(Offer<I> offer, I emerald) {
        int emeralds = 0;
        if (emerald.equals(offer.costA())) emeralds += offer.costACount();
        if (emerald.equals(offer.costB())) emeralds += offer.costBCount();

        return emeralds > 0 ? emeralds : offer.costACount();
    }

    /** First rule that lists the item. Rules without items are ignored so an empty GUI row does nothing. */
    private static <I> PriceRule<I> findRule(List<? extends PriceRule<I>> rules, I item) {
        if (item == null) return null;

        for (PriceRule<I> rule : rules) {
            if (rule.items().isEmpty()) continue;
            if (rule.items().contains(item)) return rule;
        }

        return null;
    }

    private static <I> Decision decision(Offer<I> offer, Verdict verdict, int price, String detail) {
        return new Decision(offer.index(), verdict, price, detail);
    }
}
