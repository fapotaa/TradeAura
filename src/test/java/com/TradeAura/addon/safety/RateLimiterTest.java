package com.TradeAura.addon.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the packet budget that keeps the module from sending bursts. */
class RateLimiterTest {
    @Test
    @DisplayName("starts full and hands out the whole burst")
    void startsFull() {
        RateLimiter limiter = new RateLimiter(40, 10);

        assertEquals(10, limiter.available());
        assertTrue(limiter.trySend(10));
        assertEquals(0, limiter.available());
    }

    @Test
    @DisplayName("refuses to go over budget")
    void refusesOverBudget() {
        RateLimiter limiter = new RateLimiter(40, 10);

        assertTrue(limiter.trySend(10));
        assertFalse(limiter.trySend(1));
        assertFalse(limiter.canSend(1));
    }

    @Test
    @DisplayName("a refused send does not consume anything")
    void refusedSendCostsNothing() {
        RateLimiter limiter = new RateLimiter(40, 10);

        assertFalse(limiter.trySend(11));
        assertEquals(10, limiter.available());
    }

    @Test
    @DisplayName("refills at the configured rate per tick")
    void refillsPerTick() {
        // 20 per second over 20 ticks is exactly one token per tick.
        RateLimiter limiter = new RateLimiter(20, 10);
        limiter.trySend(10);

        for (int i = 0; i < 5; i++) limiter.tick();

        assertEquals(5, limiter.available());
    }

    @Test
    @DisplayName("never refills past the burst size")
    void capsAtBurst() {
        RateLimiter limiter = new RateLimiter(200, 8);

        for (int i = 0; i < 100; i++) limiter.tick();

        assertEquals(8, limiter.available());
    }

    @Test
    @DisplayName("lowering the burst size takes effect immediately")
    void shrinkingBurstClampsTokens() {
        RateLimiter limiter = new RateLimiter(40, 32);
        assertEquals(32, limiter.available());

        limiter.configure(40, 4);
        assertEquals(4, limiter.available());
    }

    @Test
    @DisplayName("holds the long run average at the configured rate")
    void holdsTheAverage() {
        RateLimiter limiter = new RateLimiter(40, 10);
        limiter.trySend(10);

        int sent = 0;
        // Two seconds of ticks, sending as much as allowed every tick.
        for (int tick = 0; tick < 40; tick++) {
            limiter.tick();
            while (limiter.trySend(1)) sent++;
        }

        // 40 per second for two seconds, give or take one token of rounding.
        assertTrue(sent >= 78 && sent <= 80, "sent " + sent + " packets in two seconds");
    }

    @Test
    @DisplayName("reset fills the bucket again")
    void resetRefills() {
        RateLimiter limiter = new RateLimiter(40, 10);
        limiter.trySend(10);
        limiter.reset();

        assertEquals(10, limiter.available());
    }
}
