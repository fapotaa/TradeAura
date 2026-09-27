package com.TradeAura.addon.trading;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Static knowledge about which profession trades which item, and which block a profession works at.
 * <p>
 * In the original version this table existed but was never usable: the profession of a villager was hardcoded to
 * {@code "any"} with the comment "idk how to get the profession", so every villager matched every rule and the
 * pre-check degenerated into "do I have any rule at all". {@link VillagerUtil#professionId} reads the real
 * profession now, which makes this table do what it was written for - the aura walks past villagers that can
 * never offer what you configured instead of opening every one of them.
 *
 * <h2>How the table is meant to be read</h2>
 * The mapping is intentionally <b>permissive</b>. An item that is not listed here is treated as "could be
 * anyone's trade", so a modded or data-pack trade is never skipped by mistake; only items that are listed and
 * whose profession does not match are skipped. Being wrong in this direction costs one wasted interaction,
 * being wrong in the other direction would silently stop the module from finding a trade.
 */
public final class TradeData {
    /** What villagers of a profession accept in exchange for emeralds. */
    public static final Map<Item, Set<String>> VILLAGER_BUYS = createBuyMap();

    /** What villagers of a profession hand out in exchange for emeralds. */
    public static final Map<Item, Set<String>> VILLAGER_SELLS = createSellMap();

    /** Block a profession claims as its job site, used by the rerolling logic. */
    public static final Map<String, Block> WORKSTATIONS = createWorkstationMap();

    /** Professions that never trade at all. */
    public static final Set<String> NON_TRADING = Set.of("none", "nitwit");

    private TradeData() {
    }

    /**
     * Can a villager of this profession plausibly offer this trade?
     *
     * @param professionId the villager's profession id, or {@code null} when it is not known
     * @param item         the item the rule is about
     * @param buyingFromVillager true when the player wants to buy {@code item}, false when selling it
     * @return false only when the item is known and definitely belongs to another profession
     */
    public static boolean canTrade(String professionId, Item item, boolean buyingFromVillager) {
        if (professionId == null) return true;
        if (NON_TRADING.contains(professionId)) return false;

        Set<String> professions = (buyingFromVillager ? VILLAGER_SELLS : VILLAGER_BUYS).get(item);

        // Unknown item: assume it could be this villager's, so nothing is skipped by mistake.
        if (professions == null) return true;

        return professions.contains(professionId);
    }

    /** The block a villager of this profession works at, or {@code null} when there is none. */
    public static Block workstation(String professionId) {
        return professionId == null ? null : WORKSTATIONS.get(professionId);
    }

    private static Map<String, Block> createWorkstationMap() {
        Map<String, Block> map = new HashMap<>();

        map.put("armorer", Blocks.BLAST_FURNACE);
        map.put("butcher", Blocks.SMOKER);
        map.put("cartographer", Blocks.CARTOGRAPHY_TABLE);
        map.put("cleric", Blocks.BREWING_STAND);
        map.put("farmer", Blocks.COMPOSTER);
        map.put("fisherman", Blocks.BARREL);
        map.put("fletcher", Blocks.FLETCHING_TABLE);
        map.put("leatherworker", Blocks.CAULDRON);
        map.put("librarian", Blocks.LECTERN);
        map.put("mason", Blocks.STONECUTTER);
        map.put("shepherd", Blocks.LOOM);
        map.put("toolsmith", Blocks.SMITHING_TABLE);
        map.put("weaponsmith", Blocks.GRINDSTONE);

        return Map.copyOf(map);
    }

    private static Map<Item, Set<String>> createBuyMap() {
        Map<Item, Set<String>> map = new HashMap<>();

        map.put(Items.WHEAT, Set.of("farmer"));
        map.put(Items.POTATO, Set.of("farmer"));
        map.put(Items.CARROT, Set.of("farmer"));
        map.put(Items.BEETROOT, Set.of("farmer"));
        map.put(Items.PUMPKIN, Set.of("farmer"));
        map.put(Items.MELON_SLICE, Set.of("farmer"));

        map.put(Items.PAPER, Set.of("cartographer", "librarian"));
        map.put(Items.GLASS_PANE, Set.of("cartographer"));
        map.put(Items.COMPASS, Set.of("cartographer"));
        map.put(Items.BOOK, Set.of("librarian"));
        map.put(Items.INK_SAC, Set.of("librarian"));

        map.put(Items.STRING, Set.of("fletcher", "fisherman"));
        map.put(Items.FEATHER, Set.of("fletcher"));
        map.put(Items.STICK, Set.of("fletcher"));
        map.put(Items.FLINT, Set.of("fletcher", "toolsmith", "weaponsmith"));
        map.put(Items.COD, Set.of("fisherman"));
        map.put(Items.SALMON, Set.of("fisherman"));

        map.put(Items.RAW_IRON, Set.of("armorer", "weaponsmith", "toolsmith"));
        map.put(Items.IRON_INGOT, Set.of("armorer", "weaponsmith", "toolsmith"));
        map.put(Items.RAW_GOLD, Set.of("armorer"));
        map.put(Items.GOLD_INGOT, Set.of("cleric"));
        map.put(Items.RAW_COPPER, Set.of("armorer"));
        map.put(Items.COAL, Set.of("armorer", "weaponsmith", "toolsmith", "butcher", "fisherman"));
        map.put(Items.DIAMOND, Set.of("armorer", "weaponsmith", "toolsmith"));
        map.put(Items.LAPIS_LAZULI, Set.of("cleric", "armorer"));
        map.put(Items.REDSTONE, Set.of("cleric"));

        map.put(Items.ROTTEN_FLESH, Set.of("cleric"));
        map.put(Items.RABBIT_FOOT, Set.of("cleric"));
        map.put(Items.GLASS_BOTTLE, Set.of("cleric"));
        map.put(Items.NETHER_WART, Set.of("cleric"));

        map.put(Items.LEATHER, Set.of("leatherworker"));
        map.put(Items.RABBIT_HIDE, Set.of("leatherworker"));
        map.put(Items.TURTLE_SCUTE, Set.of("leatherworker"));

        map.put(Items.MUTTON, Set.of("butcher"));
        map.put(Items.PORKCHOP, Set.of("butcher"));
        map.put(Items.CHICKEN, Set.of("butcher"));
        map.put(Items.BEEF, Set.of("butcher"));
        map.put(Items.RABBIT, Set.of("butcher"));
        map.put(Items.DRIED_KELP_BLOCK, Set.of("butcher"));
        map.put(Items.SWEET_BERRIES, Set.of("butcher"));

        map.put(Items.CLAY_BALL, Set.of("mason"));
        map.put(Items.STONE, Set.of("mason"));
        map.put(Items.GRANITE, Set.of("mason"));
        map.put(Items.ANDESITE, Set.of("mason"));
        map.put(Items.DIORITE, Set.of("mason"));
        map.put(Items.NETHERRACK, Set.of("mason"));
        map.put(Items.BLACKSTONE, Set.of("mason"));
        map.put(Items.END_STONE, Set.of("mason"));
        map.put(Items.TERRACOTTA, Set.of("mason"));
        map.put(Items.QUARTZ, Set.of("mason"));

        map.put(Items.WHITE_WOOL, Set.of("shepherd"));
        map.put(Items.BLACK_WOOL, Set.of("shepherd"));
        map.put(Items.GRAY_WOOL, Set.of("shepherd"));
        map.put(Items.BROWN_WOOL, Set.of("shepherd"));
        map.put(Items.WHITE_DYE, Set.of("shepherd"));
        map.put(Items.BLACK_DYE, Set.of("shepherd"));
        map.put(Items.BROWN_DYE, Set.of("shepherd"));
        map.put(Items.BLUE_DYE, Set.of("shepherd"));

        return Map.copyOf(map);
    }

    private static Map<Item, Set<String>> createSellMap() {
        Map<Item, Set<String>> map = new HashMap<>();

        map.put(Items.BREAD, Set.of("farmer"));
        map.put(Items.APPLE, Set.of("farmer"));
        map.put(Items.PUMPKIN_PIE, Set.of("farmer"));
        map.put(Items.COOKIE, Set.of("farmer"));
        map.put(Items.CAKE, Set.of("farmer"));
        map.put(Items.GOLDEN_CARROT, Set.of("farmer"));
        map.put(Items.GLISTERING_MELON_SLICE, Set.of("farmer"));
        map.put(Items.SUSPICIOUS_STEW, Set.of("farmer"));

        map.put(Items.GLASS, Set.of("librarian"));
        map.put(Items.ENCHANTED_BOOK, Set.of("librarian"));
        map.put(Items.BOOKSHELF, Set.of("librarian", "cleric"));
        map.put(Items.NAME_TAG, Set.of("librarian"));
        map.put(Items.CLOCK, Set.of("librarian"));
        map.put(Items.LANTERN, Set.of("librarian"));
        map.put(Items.COMPASS, Set.of("librarian"));
        map.put(Items.INK_SAC, Set.of("librarian"));

        map.put(Items.MAP, Set.of("cartographer"));
        map.put(Items.FILLED_MAP, Set.of("cartographer"));
        map.put(Items.ITEM_FRAME, Set.of("cartographer"));
        map.put(Items.GLOW_ITEM_FRAME, Set.of("cartographer"));
        map.put(Items.CARTOGRAPHY_TABLE, Set.of("cartographer"));
        map.put(Items.WHITE_BANNER, Set.of("cartographer", "shepherd"));
        map.put(Items.RED_BANNER, Set.of("cartographer", "shepherd"));
        map.put(Items.BLUE_BANNER, Set.of("cartographer", "shepherd"));

        map.put(Items.ARROW, Set.of("fletcher"));
        map.put(Items.BOW, Set.of("fletcher"));
        map.put(Items.CROSSBOW, Set.of("fletcher"));
        map.put(Items.FLINT_AND_STEEL, Set.of("fletcher"));
        map.put(Items.TIPPED_ARROW, Set.of("fletcher"));

        map.put(Items.COOKED_COD, Set.of("fisherman"));
        map.put(Items.COOKED_SALMON, Set.of("fisherman"));
        map.put(Items.FISHING_ROD, Set.of("fisherman"));
        map.put(Items.COD_BUCKET, Set.of("fisherman"));
        map.put(Items.CAMPFIRE, Set.of("fisherman"));

        map.put(Items.IRON_HELMET, Set.of("armorer"));
        map.put(Items.IRON_CHESTPLATE, Set.of("armorer"));
        map.put(Items.IRON_LEGGINGS, Set.of("armorer"));
        map.put(Items.IRON_BOOTS, Set.of("armorer"));
        map.put(Items.SHIELD, Set.of("armorer"));
        map.put(Items.CHAINMAIL_HELMET, Set.of("armorer"));
        map.put(Items.CHAINMAIL_CHESTPLATE, Set.of("armorer"));
        map.put(Items.CHAINMAIL_LEGGINGS, Set.of("armorer"));
        map.put(Items.CHAINMAIL_BOOTS, Set.of("armorer"));
        map.put(Items.DIAMOND_HELMET, Set.of("armorer"));
        map.put(Items.DIAMOND_CHESTPLATE, Set.of("armorer"));
        map.put(Items.DIAMOND_LEGGINGS, Set.of("armorer"));
        map.put(Items.DIAMOND_BOOTS, Set.of("armorer"));
        map.put(Items.BELL, Set.of("armorer", "weaponsmith", "toolsmith"));

        map.put(Items.STONE_AXE, Set.of("weaponsmith", "toolsmith"));
        map.put(Items.STONE_SWORD, Set.of("weaponsmith"));
        map.put(Items.IRON_SWORD, Set.of("weaponsmith"));
        map.put(Items.IRON_AXE, Set.of("weaponsmith", "toolsmith"));
        map.put(Items.DIAMOND_SWORD, Set.of("weaponsmith"));
        map.put(Items.DIAMOND_AXE, Set.of("weaponsmith", "toolsmith"));

        map.put(Items.STONE_PICKAXE, Set.of("toolsmith"));
        map.put(Items.STONE_SHOVEL, Set.of("toolsmith"));
        map.put(Items.STONE_HOE, Set.of("toolsmith"));
        map.put(Items.IRON_PICKAXE, Set.of("toolsmith"));
        map.put(Items.IRON_SHOVEL, Set.of("toolsmith"));
        map.put(Items.IRON_HOE, Set.of("toolsmith"));
        map.put(Items.DIAMOND_PICKAXE, Set.of("toolsmith"));
        map.put(Items.DIAMOND_SHOVEL, Set.of("toolsmith"));
        map.put(Items.DIAMOND_HOE, Set.of("toolsmith"));

        map.put(Items.LEATHER_HELMET, Set.of("leatherworker"));
        map.put(Items.LEATHER_CHESTPLATE, Set.of("leatherworker"));
        map.put(Items.LEATHER_LEGGINGS, Set.of("leatherworker"));
        map.put(Items.LEATHER_BOOTS, Set.of("leatherworker"));
        map.put(Items.SADDLE, Set.of("leatherworker"));
        map.put(Items.LEATHER_HORSE_ARMOR, Set.of("leatherworker"));
        map.put(Items.WOLF_ARMOR, Set.of("leatherworker"));

        map.put(Items.ENDER_PEARL, Set.of("cleric"));
        map.put(Items.GLOWSTONE, Set.of("cleric"));
        map.put(Items.EXPERIENCE_BOTTLE, Set.of("cleric"));
        map.put(Items.REDSTONE, Set.of("cleric"));
        map.put(Items.LAPIS_LAZULI, Set.of("cleric"));
        map.put(Items.ENDER_EYE, Set.of("cleric"));

        map.put(Items.COOKED_MUTTON, Set.of("butcher"));
        map.put(Items.COOKED_PORKCHOP, Set.of("butcher"));
        map.put(Items.COOKED_CHICKEN, Set.of("butcher"));
        map.put(Items.COOKED_BEEF, Set.of("butcher"));
        map.put(Items.COOKED_RABBIT, Set.of("butcher"));
        map.put(Items.RABBIT_STEW, Set.of("butcher"));

        map.put(Items.BRICK, Set.of("mason"));
        map.put(Items.QUARTZ, Set.of("mason"));
        map.put(Items.DRIPSTONE_BLOCK, Set.of("mason"));
        map.put(Items.CHISELED_STONE_BRICKS, Set.of("mason"));
        map.put(Items.POLISHED_GRANITE, Set.of("mason"));
        map.put(Items.POLISHED_ANDESITE, Set.of("mason"));
        map.put(Items.POLISHED_DIORITE, Set.of("mason"));
        map.put(Items.QUARTZ_BLOCK, Set.of("mason"));
        map.put(Items.QUARTZ_PILLAR, Set.of("mason"));
        map.put(Items.TERRACOTTA, Set.of("mason"));

        map.put(Items.SHEARS, Set.of("shepherd"));
        map.put(Items.WHITE_BED, Set.of("shepherd"));
        map.put(Items.BLACK_BED, Set.of("shepherd"));
        map.put(Items.RED_BED, Set.of("shepherd"));
        map.put(Items.BLUE_BED, Set.of("shepherd"));
        map.put(Items.WHITE_CARPET, Set.of("shepherd"));
        map.put(Items.BLACK_CARPET, Set.of("shepherd"));
        map.put(Items.PAINTING, Set.of("shepherd"));

        return Map.copyOf(map);
    }
}
