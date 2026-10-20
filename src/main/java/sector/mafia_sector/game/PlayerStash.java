package sector.mafia_sector.game;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Snapshots of players taken before they enter the bunker. An entry lives until the player has their things back,
 * across logouts and server restarts. Every change is written to disk immediately via {@link #flush}.
 */
public class PlayerStash extends SavedData {
    private static final String NAME = "mafia_sector_stash";

    private final Map<UUID, CompoundTag> entries = new HashMap<>();

    public static PlayerStash get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PlayerStash::new, PlayerStash::load), NAME);
    }

    private static PlayerStash load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerStash stash = new PlayerStash();
        for (String key : tag.getAllKeys()) {
            stash.entries.put(UUID.fromString(key), tag.getCompound(key));
        }
        return stash;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        entries.forEach((uuid, snapshot) -> tag.put(uuid.toString(), snapshot));
        return tag;
    }

    public boolean has(UUID player) {
        return entries.containsKey(player);
    }

    @Nullable
    public CompoundTag get(UUID player) {
        return entries.get(player);
    }

    public Set<UUID> players() {
        return Set.copyOf(entries.keySet());
    }

    public void put(UUID player, CompoundTag snapshot) {
        entries.put(player, snapshot);
        setDirty();
    }

    public void remove(UUID player) {
        if (entries.remove(player) != null) {
            setDirty();
        }
    }

    /** Writes all dirty saved data of the overworld to disk right now, not at the next autosave. */
    public void flush(MinecraftServer server) {
        setDirty();
        server.overworld().getDataStorage().save();
    }
}
