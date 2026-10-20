package sector.mafia_sector.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.block.BunkerLampBlock;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.registry.ModBlocks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Renders a top-down cutaway of the bunker: everything below {@code cutY}, coloured by map colour. */
final class BunkerPlanRenderer {
    private static final int SCALE = 6;

    private BunkerPlanRenderer() {
    }

    static void render(ServerLevel level, int cutY, Path file) {
        BoundingBox box = BunkerLayout.MASS;
        int w = box.getXSpan();
        int h = box.getZSpan();
        BufferedImage image = new BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_RGB);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int rgb = 0x101418;
                for (int y = cutY; y >= box.minY(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    rgb = shade(color(level, pos, state), cutY - y, y == cutY);
                    break;
                }
                for (int px = 0; px < SCALE; px++) {
                    for (int pz = 0; pz < SCALE; pz++) {
                        image.setRGB((x - box.minX()) * SCALE + px, (z - box.minZ()) * SCALE + pz, rgb);
                    }
                }
            }
        }
        try {
            ImageIO.write(image, "png", file.toFile());
            Mafia_sector.LOGGER.info("[bunker test] plan written to {}", file.toAbsolutePath());
        } catch (IOException e) {
            Mafia_sector.LOGGER.error("[bunker test] cannot write plan", e);
        }
    }

    private static int color(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BunkerLampBlock) {
            return state.is(ModBlocks.EMERGENCY_LAMP) ? 0xD03040 : 0xFFD070;
        }
        if (state.is(ModBlocks.BUNKER_GLASS)) return 0x9FD8F0;
        if (state.is(ModBlocks.BUNKER_HAZARD)) return 0xE0B020;
        return state.getMapColor(level, pos).col;
    }

    /** Darker the deeper below the cut; blocks cut at the plane (walls) get a dark outline tone. */
    private static int shade(int rgb, int depth, boolean cut) {
        double f = cut ? 0.45 : Math.max(0.55, 1.0 - depth * 0.06);
        int r = (int) (((rgb >> 16) & 255) * f);
        int g = (int) (((rgb >> 8) & 255) * f);
        int b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }
}
