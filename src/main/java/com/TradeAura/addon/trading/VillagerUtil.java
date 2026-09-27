package com.TradeAura.addon.trading;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerData;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * The bridge between Minecraft's merchant classes and the Minecraft-free {@link TradeEvaluator}.
 * <p>
 * Everything version specific about reading a villager lives here, so a future port only has to touch this file
 * and the module, never the trading rules themselves.
 */
public final class VillagerUtil {
    private VillagerUtil() {
    }

    /**
     * The villager's profession id, for example {@code "librarian"}.
     * <p>
     * This is the piece the original version was missing ("idk how to get the profession, reflection doesn't
     * seem to work"). No reflection is needed: {@code VillagerEntity} implements {@code VillagerDataContainer},
     * and in 1.21.4 {@code VillagerProfession} is a record whose {@code id()} is exactly the string the trade
     * tables are keyed by.
     *
     * @return the profession id, or {@code null} when the entity has no profession concept (wandering trader)
     */
    public static String professionId(Entity entity) {
        if (!(entity instanceof VillagerEntity villager)) return null;

        VillagerData data = villager.getVillagerData();
        if (data == null || data.getProfession() == null) return null;

        return data.getProfession().id();
    }

    /** Trading level, 1 (novice) to 5 (master). Returns 0 when the entity has no level. */
    public static int level(Entity entity) {
        if (!(entity instanceof VillagerEntity villager)) return 0;

        VillagerData data = villager.getVillagerData();
        return data == null ? 0 : data.getLevel();
    }

    /** True for a villager that can never trade: a baby, a nitwit or an unemployed one. */
    public static boolean cannotTrade(Entity entity) {
        if (entity instanceof VillagerEntity villager && villager.isBaby()) return true;

        String profession = professionId(entity);
        return profession != null && TradeData.NON_TRADING.contains(profession);
    }

    /** True when the entity is something the module can trade with at all. */
    public static boolean isMerchant(Entity entity, boolean includeWanderingTraders) {
        if (entity instanceof VillagerEntity) return true;
        return includeWanderingTraders && entity instanceof WanderingTraderEntity;
    }

    /** True while another player already has the trading screen of this villager open. */
    public static boolean isBusy(Entity entity) {
        return entity instanceof MerchantEntity merchant && merchant.hasCustomer();
    }

    /**
     * Turns the offers of an open merchant screen into the plain records the evaluator works on.
     * <p>
     * The prices come from {@code getDisplayedFirstBuyItem()} / {@code getDisplayedSecondBuyItem()}, which are
     * the values the trading screen actually shows: base price plus demand, minus any hero-of-the-village or
     * cured-villager discount. The original version read the raw first ingredient, so a trade whose price had
     * risen through demand was still accepted at its base price.
     */
    public static List<TradeEvaluator.Offer<Item>> snapshot(TradeOfferList offers) {
        List<TradeEvaluator.Offer<Item>> result = new ArrayList<>(offers.size());

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);

            ItemStack sell = offer.getSellItem();
            ItemStack costA = offer.getDisplayedFirstBuyItem();
            ItemStack costB = offer.getDisplayedSecondBuyItem();

            result.add(new TradeEvaluator.Offer<>(
                i,
                sell.getItem(),
                sell.getCount(),
                costA.isEmpty() ? null : costA.getItem(),
                costA.getCount(),
                costB.isEmpty() ? null : costB.getItem(),
                costB.getCount(),
                offer.isDisabled()
            ));
        }

        return result;
    }

    /**
     * True when none of the offers has ever been used, which is the client side way of telling that a villager's
     * profession is not locked in yet and can still be rerolled by removing its workstation.
     */
    public static boolean canBeRerolled(TradeOfferList offers, int level) {
        if (level > 1) return false;

        for (TradeOffer offer : offers) {
            if (offer.getUses() > 0) return false;
        }

        return true;
    }

    /** Counts an item across the whole player inventory, hotbar, main, armor and offhand included. */
    public static int count(Item item) {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) count += stack.getCount();
        }

        return count;
    }

    /** How many more of that item fit into the hotbar and the main inventory. */
    public static int freeSpaceFor(Item item) {
        if (mc.player == null) return 0;

        int max = item.getDefaultStack().getMaxCount();
        int space = 0;

        for (int i = 0; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isEmpty()) space += max;
            else if (stack.isOf(item)) space += Math.max(0, stack.getMaxCount() - stack.getCount());
        }

        return space;
    }

    /** The player inventory, as the evaluator sees it. */
    public static TradeEvaluator.Stock<Item> stock() {
        return new TradeEvaluator.Stock<>() {
            @Override
            public int count(Item item) {
                return VillagerUtil.count(item);
            }

            @Override
            public int freeSpace(Item item) {
                return VillagerUtil.freeSpaceFor(item);
            }
        };
    }

    public static Item emerald() {
        return Items.EMERALD;
    }
}
