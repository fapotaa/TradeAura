package com.TradeAura.addon.trading;

/**
 * How the whitelist is applied when choosing which villagers to trade with.
 * <p>
 * The <b>blacklist always applies</b>, in every mode. It is the "never touch this villager" list: a villager you
 * have rolled to a perfect trade, one that belongs to a friend, one standing somewhere the module cannot reach.
 * The mode below only decides what happens with the whitelist.
 */
public enum TargetMode {
    /**
     * Trade with any villager that passes the normal filters and is not blacklisted.
     * <p>
     * The whitelist is ignored. Use this when you want everything in the hall worked through.
     */
    Everyone,

    /**
     * Trade only with whitelisted villagers.
     * <p>
     * Everything else is skipped, even if it offers exactly what you want. Use this when you have picked out
     * the handful of villagers worth visiting and do not want the module wandering off to the rest of the hall.
     */
    WhitelistOnly,

    /**
     * Work through the whitelist first; once none of them has anything left to do, fall back to anyone else that
     * is not blacklisted.
     * <p>
     * This is "use what is available": the villagers you care about get priority, and the module keeps going
     * instead of standing still when they are exhausted.
     */
    PreferWhitelist;

    /** Is a villager allowed at all in this mode? */
    public boolean allows(boolean whitelisted, boolean blacklisted) {
        if (blacklisted) return false;

        return switch (this) {
            case Everyone, PreferWhitelist -> true;
            case WhitelistOnly -> whitelisted;
        };
    }

    /** Should whitelisted villagers be served before everyone else? */
    public boolean prioritisesWhitelist() {
        return this == PreferWhitelist;
    }
}
