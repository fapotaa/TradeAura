package com.TradeAura.addon.safety;

/**
 * A token bucket that caps how many packets the module may send per second.
 * <p>
 * The original implementation had no limit anywhere. A single tick could send a trade selection plus a shift
 * click for every matching offer of a villager, the crafting task could send up to 64 right clicks per grid slot
 * and the drop task up to 128 throw packets, all inside one tick. That is the shape of traffic that gets a
 * client kicked for "spamming" long before anything else goes wrong.
 * <p>
 * Refills happen per client tick rather than per millisecond so the budget behaves identically no matter what
 * the frame rate is, and so a lagging server (where ticks arrive late) automatically slows the module down with
 * it instead of racing ahead.
 * <p>
 * This class is deliberately free of Minecraft references so it can be unit tested.
 */
public final class RateLimiter {
    private static final int TICKS_PER_SECOND = 20;

    private double tokens;
    private double capacity;
    private double refillPerTick;

    /**
     * @param perSecond how many packets may be sent per second on average
     * @param burst     how many may be sent back to back before the average kicks in
     */
    public RateLimiter(int perSecond, int burst) {
        configure(perSecond, burst);
        tokens = capacity;
    }

    /** Changes the limits at runtime, keeping whatever budget is currently left. */
    public void configure(int perSecond, int burst) {
        this.refillPerTick = Math.max(1, perSecond) / (double) TICKS_PER_SECOND;
        this.capacity = Math.max(1, burst);
        if (tokens > capacity) tokens = capacity;
    }

    /** Call once per client tick. */
    public void tick() {
        tokens = Math.min(capacity, tokens + refillPerTick);
    }

    /** @return true when at least {@code count} packets may be sent right now */
    public boolean canSend(int count) {
        return tokens >= count;
    }

    /**
     * Consumes budget for packets that are about to be sent.
     *
     * @return true when the budget covered it, false when nothing should be sent
     */
    public boolean trySend(int count) {
        if (!canSend(count)) return false;

        tokens -= count;
        return true;
    }

    /** How many packets may still be sent this instant. */
    public int available() {
        return (int) Math.floor(tokens);
    }

    /** Refills the bucket, for example after the module was toggled off and on again. */
    public void reset() {
        tokens = capacity;
    }
}
