package sector.mafia_sector.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import sector.mafia_sector.block.BunkerLampBlock;
import sector.mafia_sector.build.BunkerData;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.dimension.Bunker7Dimension;
import sector.mafia_sector.registry.ModBlocks;

import java.util.Comparator;

/** Normal: main lamps on, emergency lamps off. Emergency: the other way round. Lamp positions come from the build. */
public final class BunkerLighting {
    /**
     * Keeps the bunker chunks loaded for five minutes. Light of a full build takes many seconds to compute; if the
     * chunks unload first (nobody is in the bunker), the queued updates are dropped and the lamps stay dark for good.
     */
    private static final TicketType<ChunkPos> LIGHT_TICKET =
            TicketType.create("mafia_sector_light", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 5);

    private BunkerLighting() {
    }

    public static void set(MinecraftServer server, boolean emergency) {
        ServerLevel level = Bunker7Dimension.get(server);
        if (level == null) {
            return;
        }
        holdChunks(level);
        BunkerData data = BunkerData.get(server);
        for (BlockPos pos : data.getLamps()) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BunkerLampBlock)) {
                continue;
            }
            boolean lit = state.is(ModBlocks.EMERGENCY_LAMP) == emergency;
            if (state.getValue(BunkerLampBlock.LIT) != lit) {
                level.setBlock(pos, state.setValue(BunkerLampBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Call before placing many blocks in the bunker: their light must be computed before the chunks unload. */
    public static void holdChunks(ServerLevel level) {
        BoundingBox box = BunkerLayout.FOOTPRINT;
        for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(LIGHT_TICKET, pos, 0, pos);
            }
        }
    }
}
