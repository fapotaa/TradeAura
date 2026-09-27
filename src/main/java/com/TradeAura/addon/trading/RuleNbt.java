package com.TradeAura.addon.trading;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * NBT helpers shared by every rule type.
 * <p>
 * Rule lists are stored by item id rather than by registry index, so a config keeps working across versions and
 * across mod sets, and an item that no longer exists is dropped instead of loading as air.
 */
public final class RuleNbt {
    private RuleNbt() {
    }

    /** Writes a list of items as their registry ids. */
    public static NbtList itemsToTag(List<Item> items) {
        NbtList list = new NbtList();

        for (Item item : items) {
            Identifier id = Registries.ITEM.getId(item);
            if (id != null) list.add(NbtString.of(id.toString()));
        }

        return list;
    }

    /** Reads the "items" list of a rule tag, skipping ids this game does not know. */
    public static void readItems(NbtCompound tag, List<Item> into) {
        if (!(tag.get("items") instanceof NbtList itemsList)) return;

        for (NbtElement element : itemsList) {
            if (!(element instanceof NbtString itemString)) continue;

            Identifier id = Identifier.tryParse(itemString.asString());
            if (id != null && Registries.ITEM.containsId(id)) into.add(Registries.ITEM.get(id));
        }
    }

    /**
     * Reads an int, falling back to a legacy key so configs written by the original version still load.
     *
     * @param legacyKey the key the older version used, or {@code null} when there is none
     */
    public static int readInt(NbtCompound tag, String key, String legacyKey, int fallback) {
        if (tag.contains(key)) return tag.getInt(key);
        if (legacyKey != null && tag.contains(legacyKey)) return tag.getInt(legacyKey);
        return fallback;
    }
}
