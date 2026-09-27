package com.TradeAura.addon.trading;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A named set of villagers, stored by UUID.
 * <p>
 * UUIDs are the only villager identity that is both visible to the client and stable across relogs, world
 * reloads and chunk unloads - an entity's network id changes every time the chunk reloads, and a position is
 * only stable for a villager locked in a cell. That makes UUIDs the right key for a trading hall you come back
 * to every day.
 * <p>
 * Because a UUID is unreadable, every entry also carries a label and the position the villager was standing at
 * when it was added. Neither is used for matching; they exist so the list in the GUI is something a human can
 * read, and so a villager you cannot find again can still be identified by where it used to be.
 */
public class VillagerList {
    /** One remembered villager. */
    public record Entry(UUID uuid, String label, BlockPos pos) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    /** Insertion-ordered, so the GUI shows the list in the order it was built. */
    public Collection<Entry> entries() {
        return entries.values();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean contains(Entity entity) {
        return entity != null && entries.containsKey(entity.getUuid());
    }

    public boolean contains(UUID uuid) {
        return entries.containsKey(uuid);
    }

    public Entry get(UUID uuid) {
        return entries.get(uuid);
    }

    /**
     * Adds the entity, or replaces the entry if it is already listed.
     *
     * @param label a name for the GUI; when blank, the villager's custom name, then its profession, then a
     *              short form of the UUID is used
     * @return the entry that is now in the list
     */
    public Entry add(Entity entity, String label) {
        UUID uuid = entity.getUuid();
        Entry entry = new Entry(uuid, resolveLabel(entity, label), entity.getBlockPos());

        entries.put(uuid, entry);
        return entry;
    }

    public boolean remove(Entity entity) {
        return entity != null && entries.remove(entity.getUuid()) != null;
    }

    public boolean remove(UUID uuid) {
        return entries.remove(uuid) != null;
    }

    /**
     * Adds the entity if it is not listed, removes it if it is.
     *
     * @return true when the entity is in the list afterwards
     */
    public boolean toggle(Entity entity, String label) {
        if (remove(entity)) return false;

        add(entity, label);
        return true;
    }

    public void clear() {
        entries.clear();
    }

    private static String resolveLabel(Entity entity, String label) {
        if (label != null && !label.isBlank()) return label.trim();

        if (entity.hasCustomName() && entity.getCustomName() != null) {
            return entity.getCustomName().getString();
        }

        String profession = VillagerUtil.professionId(entity);
        if (profession != null) {
            int level = VillagerUtil.level(entity);
            return level > 0 ? profession + " lvl " + level : profession;
        }

        return entity.getUuid().toString().substring(0, 8);
    }

    // NBT

    public NbtList toTag() {
        NbtList list = new NbtList();

        for (Entry entry : entries.values()) {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("uuid", entry.uuid());
            tag.putString("label", entry.label());

            if (entry.pos() != null) {
                tag.putInt("x", entry.pos().getX());
                tag.putInt("y", entry.pos().getY());
                tag.putInt("z", entry.pos().getZ());
            }

            list.add(tag);
        }

        return list;
    }

    /** Replaces the whole list, so switching profiles cannot leave old entries behind. */
    public void fromTag(NbtList list) {
        entries.clear();

        for (NbtElement element : list) {
            if (!(element instanceof NbtCompound tag)) continue;
            if (!tag.containsUuid("uuid")) continue;

            UUID uuid = tag.getUuid("uuid");
            String label = tag.contains("label") ? tag.getString("label") : uuid.toString().substring(0, 8);

            BlockPos pos = null;
            if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
                pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            }

            entries.put(uuid, new Entry(uuid, label, pos));
        }
    }
}
