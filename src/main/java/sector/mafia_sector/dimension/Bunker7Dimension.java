package sector.mafia_sector.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import sector.mafia_sector.Mafia_sector;

/** The empty void dimension that hosts the bunker. Defined by data/mafia_sector/dimension/bunker7.json. */
public final class Bunker7Dimension {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Mafia_sector.MODID, "bunker7"));

    /** The GameTest server cannot load data pack dimensions, so tests run the bunker in another level. */
    private static ResourceKey<Level> active = LEVEL;

    private Bunker7Dimension() {
    }

    public static ResourceKey<Level> key() {
        return active;
    }

    @Nullable
    public static ServerLevel get(MinecraftServer server) {
        return server.getLevel(active);
    }

    public static boolean is(Level level) {
        return level.dimension() == active;
    }

    @VisibleForTesting
    public static void useForTests(ResourceKey<Level> level) {
        active = level;
    }
}
