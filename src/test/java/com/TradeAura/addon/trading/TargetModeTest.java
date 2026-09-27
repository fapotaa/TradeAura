package com.TradeAura.addon.trading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the whitelist / blacklist rules.
 * <p>
 * The important invariant is the one that is easy to get wrong by accident: the blacklist wins in every mode.
 * A villager you deliberately protected must never be traded with, no matter how the whitelist is configured.
 */
class TargetModeTest {
    @Test
    @DisplayName("the blacklist wins in every mode")
    void blacklistAlwaysWins() {
        for (TargetMode mode : TargetMode.values()) {
            assertFalse(mode.allows(false, true), mode + " allowed a blacklisted villager");
            assertFalse(mode.allows(true, true), mode + " allowed a villager that is on both lists");
        }
    }

    @Test
    @DisplayName("Everyone ignores the whitelist")
    void everyoneIgnoresWhitelist() {
        assertTrue(TargetMode.Everyone.allows(false, false));
        assertTrue(TargetMode.Everyone.allows(true, false));
        assertFalse(TargetMode.Everyone.prioritisesWhitelist());
    }

    @Test
    @DisplayName("WhitelistOnly refuses anyone not on the list")
    void whitelistOnlyIsExclusive() {
        assertTrue(TargetMode.WhitelistOnly.allows(true, false));
        assertFalse(TargetMode.WhitelistOnly.allows(false, false));
    }

    @Test
    @DisplayName("PreferWhitelist allows everyone but serves the list first")
    void preferWhitelistFallsBack() {
        assertTrue(TargetMode.PreferWhitelist.allows(true, false));
        assertTrue(TargetMode.PreferWhitelist.allows(false, false));
        assertTrue(TargetMode.PreferWhitelist.prioritisesWhitelist());
    }

    @Test
    @DisplayName("only PreferWhitelist reorders targets")
    void onlyPreferReorders() {
        assertFalse(TargetMode.Everyone.prioritisesWhitelist());
        assertFalse(TargetMode.WhitelistOnly.prioritisesWhitelist());
        assertTrue(TargetMode.PreferWhitelist.prioritisesWhitelist());
    }
}
