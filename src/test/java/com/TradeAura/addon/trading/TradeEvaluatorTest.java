package com.TradeAura.addon.trading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the trading decision logic.
 * <p>
 * The evaluator is written without any Minecraft references precisely so this file can exist: every rule that
 * decides whether real emeralds get spent is checked here with plain strings as item keys. Most of these cases
 * are bugs the original implementation actually had.
 */
class TradeEvaluatorTest {
    private static final String EMERALD = "emerald";
    private static final String BOOK = "book";
    private static final String ENCHANTED_BOOK = "enchanted_book";
    private static final String PAPER = "paper";
    private static final String WHEAT = "wheat";

    // Helpers

    /** A rule, as the GUI would produce it. */
    private record Rule(List<String> items, int limitA, int limitB) implements TradeEvaluator.PriceRule<String> {
    }

    private static Rule rule(String item, int limitA, int limitB) {
        return new Rule(List.of(item), limitA, limitB);
    }

    /** An inventory with fixed counts and, by default, plenty of room. */
    private static final class Inventory implements TradeEvaluator.Stock<String> {
        private final Map<String, Integer> counts = new HashMap<>();
        private final Map<String, Integer> space = new HashMap<>();

        Inventory with(String item, int count) {
            counts.put(item, count);
            return this;
        }

        Inventory withSpace(String item, int amount) {
            space.put(item, amount);
            return this;
        }

        @Override
        public int count(String item) {
            return counts.getOrDefault(item, 0);
        }

        @Override
        public int freeSpace(String item) {
            return space.getOrDefault(item, 2304);
        }
    }

    /** "Give me <sell> for <costACount> emeralds". */
    private static TradeEvaluator.Offer<String> buyOffer(int index, String sell, int sellCount, int price) {
        return new TradeEvaluator.Offer<>(index, sell, sellCount, EMERALD, price, null, 0, false);
    }

    /** "Give me <count> <item> and I pay <emeralds> emeralds". */
    private static TradeEvaluator.Offer<String> sellOffer(int index, String item, int count, int emeralds) {
        return new TradeEvaluator.Offer<>(index, EMERALD, emeralds, item, count, null, 0, false);
    }

    private static TradeEvaluator.Decision evalBuy(TradeEvaluator.Offer<String> offer, Rule r, Inventory inv) {
        return TradeEvaluator.evaluate(offer, List.of(r), List.of(), inv, EMERALD);
    }

    private static TradeEvaluator.Decision evalSell(TradeEvaluator.Offer<String> offer, Rule r, Inventory inv) {
        return TradeEvaluator.evaluate(offer, List.of(), List.of(r), inv, EMERALD);
    }

    // Buying

    @Test
    @DisplayName("buys when the price and the limit allow it")
    void buysWithinLimits() {
        var decision = evalBuy(buyOffer(0, ENCHANTED_BOOK, 1, 12),
            rule(ENCHANTED_BOOK, 16, 8),
            new Inventory().with(EMERALD, 64));

        assertEquals(TradeEvaluator.Verdict.BUY, decision.verdict());
        assertEquals(12, decision.price());
        assertTrue(decision.actionable());
    }

    @Test
    @DisplayName("a price limit of -1 means no limit (the original rejected every buy in that case)")
    void minusOneMeansNoPriceLimit() {
        var decision = evalBuy(buyOffer(0, ENCHANTED_BOOK, 1, 64),
            rule(ENCHANTED_BOOK, -1, -1),
            new Inventory().with(EMERALD, 64));

        assertEquals(TradeEvaluator.Verdict.BUY, decision.verdict());
    }

    @Test
    @DisplayName("rejects a trade above the price limit")
    void rejectsTooExpensive() {
        var decision = evalBuy(buyOffer(0, ENCHANTED_BOOK, 1, 30),
            rule(ENCHANTED_BOOK, 16, -1),
            new Inventory().with(EMERALD, 64));

        assertEquals(TradeEvaluator.Verdict.TOO_EXPENSIVE, decision.verdict());
        assertFalse(decision.actionable());
    }

    @Test
    @DisplayName("owning one emerald is not enough for a 30 emerald trade")
    void checksExactAffordability() {
        var decision = evalBuy(buyOffer(0, ENCHANTED_BOOK, 1, 30),
            rule(ENCHANTED_BOOK, 64, -1),
            new Inventory().with(EMERALD, 1));

        assertEquals(TradeEvaluator.Verdict.NOT_ENOUGH_EMERALDS, decision.verdict());
    }

    @Test
    @DisplayName("the second ingredient has to be in the inventory too")
    void checksSecondIngredient() {
        // 12 emeralds + 1 book -> 1 enchanted book, but there is no book.
        var offer = new TradeEvaluator.Offer<>(0, ENCHANTED_BOOK, 1, EMERALD, 12, BOOK, 1, false);

        var decision = TradeEvaluator.evaluate(offer,
            List.of(rule(ENCHANTED_BOOK, 16, -1)), List.of(),
            new Inventory().with(EMERALD, 64), EMERALD);

        assertEquals(TradeEvaluator.Verdict.NOT_ENOUGH_ITEMS, decision.verdict());
    }

    @Test
    @DisplayName("the second ingredient being present makes the same trade go through")
    void secondIngredientPresent() {
        var offer = new TradeEvaluator.Offer<>(0, ENCHANTED_BOOK, 1, EMERALD, 12, BOOK, 1, false);

        var decision = TradeEvaluator.evaluate(offer,
            List.of(rule(ENCHANTED_BOOK, 16, -1)), List.of(),
            new Inventory().with(EMERALD, 64).with(BOOK, 4), EMERALD);

        assertEquals(TradeEvaluator.Verdict.BUY, decision.verdict());
    }

    @Test
    @DisplayName("emeralds in the second slot still count towards the price")
    void emeraldsAsSecondIngredientCount() {
        var offer = new TradeEvaluator.Offer<>(0, ENCHANTED_BOOK, 1, BOOK, 1, EMERALD, 20, false);

        var decision = TradeEvaluator.evaluate(offer,
            List.of(rule(ENCHANTED_BOOK, 16, -1)), List.of(),
            new Inventory().with(EMERALD, 64).with(BOOK, 4), EMERALD);

        assertEquals(TradeEvaluator.Verdict.TOO_EXPENSIVE, decision.verdict());
        assertEquals(20, decision.price());
    }

    @Test
    @DisplayName("stops once the inventory holds as much as the rule allows")
    void respectsBuyLimit() {
        var decision = evalBuy(buyOffer(0, PAPER, 16, 1),
            rule(PAPER, 8, 64),
            new Inventory().with(EMERALD, 64).with(PAPER, 64));

        assertEquals(TradeEvaluator.Verdict.LIMIT_REACHED, decision.verdict());
    }

    @Test
    @DisplayName("does not trade when the result would not fit")
    void checksInventorySpace() {
        var decision = evalBuy(buyOffer(0, PAPER, 16, 1),
            rule(PAPER, 8, -1),
            new Inventory().with(EMERALD, 64).withSpace(PAPER, 4));

        assertEquals(TradeEvaluator.Verdict.NO_INVENTORY_SPACE, decision.verdict());
    }

    @Test
    @DisplayName("a locked trade is reported as out of stock")
    void detectsOutOfStock() {
        var offer = new TradeEvaluator.Offer<>(0, ENCHANTED_BOOK, 1, EMERALD, 12, null, 0, true);

        var decision = TradeEvaluator.evaluate(offer,
            List.of(rule(ENCHANTED_BOOK, 16, -1)), List.of(),
            new Inventory().with(EMERALD, 64), EMERALD);

        assertEquals(TradeEvaluator.Verdict.OUT_OF_STOCK, decision.verdict());
    }

    @Test
    @DisplayName("an offer no rule mentions is left alone")
    void ignoresUnconfiguredOffers() {
        var decision = evalBuy(buyOffer(0, "diamond_hoe", 1, 4),
            rule(ENCHANTED_BOOK, 16, -1),
            new Inventory().with(EMERALD, 64));

        assertEquals(TradeEvaluator.Verdict.NO_RULE, decision.verdict());
    }

    @Test
    @DisplayName("an empty rule row does not match anything")
    void emptyRuleMatchesNothing() {
        var decision = evalBuy(buyOffer(0, ENCHANTED_BOOK, 1, 4),
            new Rule(List.of(), 64, -1),
            new Inventory().with(EMERALD, 64));

        assertEquals(TradeEvaluator.Verdict.NO_RULE, decision.verdict());
    }

    // Selling

    @Test
    @DisplayName("sells when the quantity and the emerald limit allow it")
    void sellsWithinLimits() {
        var decision = evalSell(sellOffer(0, PAPER, 24, 1),
            rule(PAPER, 32, 256),
            new Inventory().with(PAPER, 64).with(EMERALD, 10));

        assertEquals(TradeEvaluator.Verdict.SELL, decision.verdict());
    }

    @Test
    @DisplayName("refuses to hand over more per trade than the rule allows")
    void respectsMaxSellQuantity() {
        var decision = evalSell(sellOffer(0, PAPER, 32, 1),
            rule(PAPER, 24, -1),
            new Inventory().with(PAPER, 64));

        assertEquals(TradeEvaluator.Verdict.TOO_EXPENSIVE, decision.verdict());
    }

    @Test
    @DisplayName("stops selling at the emerald limit")
    void respectsEmeraldLimit() {
        var decision = evalSell(sellOffer(0, PAPER, 24, 1),
            rule(PAPER, 32, 64),
            new Inventory().with(PAPER, 64).with(EMERALD, 64));

        assertEquals(TradeEvaluator.Verdict.LIMIT_REACHED, decision.verdict());
    }

    @Test
    @DisplayName("does not sell what is not there")
    void refusesToSellMissingItems() {
        var decision = evalSell(sellOffer(0, WHEAT, 20, 1),
            rule(WHEAT, 32, -1),
            new Inventory().with(WHEAT, 5));

        assertEquals(TradeEvaluator.Verdict.NOT_ENOUGH_ITEMS, decision.verdict());
    }

    @Test
    @DisplayName("does not sell when the emeralds would not fit")
    void refusesToSellWithoutRoom() {
        var decision = evalSell(sellOffer(0, WHEAT, 20, 1),
            rule(WHEAT, 32, -1),
            new Inventory().with(WHEAT, 64).withSpace(EMERALD, 0));

        assertEquals(TradeEvaluator.Verdict.NO_INVENTORY_SPACE, decision.verdict());
    }

    // Direction detection

    @Test
    @DisplayName("an emerald-for-emerald-block trade is a purchase, not a sale")
    void emeraldBlockIsABuy() {
        var offer = buyOffer(0, "emerald_block", 1, 9);

        var decision = TradeEvaluator.evaluate(offer,
            List.of(rule("emerald_block", 9, -1)), List.of(rule(EMERALD, 64, -1)),
            new Inventory().with(EMERALD, 64), EMERALD);

        assertEquals(TradeEvaluator.Verdict.BUY, decision.verdict());
    }

    @Test
    @DisplayName("the first matching rule wins when several could apply")
    void firstMatchingRuleWins() {
        var offer = buyOffer(0, PAPER, 16, 5);

        var decision = TradeEvaluator.evaluate(offer,
            List.of(rule(PAPER, 1, -1), rule(PAPER, 64, -1)), List.of(),
            new Inventory().with(EMERALD, 64), EMERALD);

        assertEquals(TradeEvaluator.Verdict.TOO_EXPENSIVE, decision.verdict());
    }

    @Test
    @DisplayName("the decision carries the offer index so the right trade is selected")
    void keepsOfferIndex() {
        var decision = evalBuy(buyOffer(3, ENCHANTED_BOOK, 1, 5),
            rule(ENCHANTED_BOOK, 16, -1),
            new Inventory().with(EMERALD, 64));

        assertEquals(3, decision.offerIndex());
    }
}
