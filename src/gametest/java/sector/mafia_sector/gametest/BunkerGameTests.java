package sector.mafia_sector.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.block.BoardBlock;
import sector.mafia_sector.build.BunkerBuilder;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.dimension.Bunker7Dimension;
import sector.mafia_sector.registry.ModBlocks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Dev-only checks. Run with ./gradlew runGameTestServer; floor plans are written to run-gametest/bunker_plan_*.png. */
@GameTestHolder(Mafia_sector.MODID)
@PrefixGameTestTemplate(false)
public final class BunkerGameTests {
    private BunkerGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void buildBunker(GameTestHelper helper) {
        // The GameTest server bakes only vanilla dimensions, so build in its overworld at the same coordinates.
        // Our dimension type still goes through the data pack registries and must be there.
        expect(helper, helper.getLevel().registryAccess().registryOrThrow(Registries.DIMENSION_TYPE)
                .containsKey(Bunker7Dimension.LEVEL.location()), "dimension type mafia_sector:bunker7");
        Bunker7Dimension.useForTests(Level.OVERWORLD);
        ServerLevel level = helper.getLevel().getServer().overworld();
        long start = System.currentTimeMillis();
        BunkerBuilder.Result result = BunkerBuilder.build(level, true);
        Mafia_sector.LOGGER.info("[bunker test] built in {} ms, {} lamps", System.currentTimeMillis() - start, result.lamps().size());
        expect(helper, result.lamps().size() > 100, "lamps: " + result.lamps().size());

        List<String> missing = new ArrayList<>();
        expect(helper, BunkerLayout.SEATS.stream().map(BunkerLayout.Seat::spot).distinct().count() == BunkerLayout.MAX_PLAYERS, "16 distinct places");
        for (BunkerLayout.Seat seat : BunkerLayout.SEATS) {
            if (!level.getBlockState(seat.spot()).isAir() || !level.getBlockState(seat.spot().above()).isAir()) missing.add("free place " + seat.number());
            if (!level.getBlockState(seat.spot().below()).is(ModBlocks.SHELTER_STEEL)) missing.add("plate under place " + seat.number());
            if (!(level.getBlockState(seat.sign()).getBlock() instanceof SignBlock)) missing.add("sign of place " + seat.number());
        }
        for (BunkerLayout.VoteChest chest : BunkerLayout.VOTE_CHESTS) {
            var state = level.getBlockState(chest.pos());
            if (!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) missing.add("single ballot chest " + chest.number());
            if (!(level.getBlockState(chest.pos().above()).getBlock() instanceof SignBlock)) missing.add("sign of chest " + chest.number());
        }
        if (BunkerLayout.VOTE_CHESTS.size() != BunkerLayout.MAX_PLAYERS) missing.add("16 ballot chests");
        var board = level.getBlockState(BunkerLayout.BOARD_ANCHOR);
        if (!board.is(ModBlocks.BOARD) || board.getValue(BoardBlock.FACING) != BunkerLayout.BOARD_FACING) missing.add("board anchor");
        if (!level.getBlockState(BunkerLayout.BOARD_ANCHOR.offset(BunkerLayout.BOARD_WIDTH - 1, 1 - BunkerLayout.BOARD_HEIGHT, 0)).is(ModBlocks.BOARD_PANEL)) {
            missing.add("bottom-right board panel");
        }
        if (!level.getBlockState(BunkerLayout.PARTS_CHEST.pos()).is(Blocks.CHEST)) missing.add("parts chest");
        BunkerLayout.LORE_TERMINALS.forEach(s -> {
            if (!level.getBlockState(s.pos()).is(ModBlocks.SECTOR_TERMINAL)) missing.add("terminal " + s.id());
        });
        BunkerLayout.WATER_LANES.forEach(lane -> {
            boolean wet = level.getFluidState(new BlockPos(lane.minX(), lane.minY(), lane.minZ())).is(Fluids.WATER)
                    || level.getFluidState(new BlockPos(lane.minX(), lane.minY(), lane.maxZ())).is(Fluids.WATER);
            if (!wet) missing.add("water in lane " + lane);
        });
        // The view from the surface: hatch, shaft, skylight, hall, well, reactor hall. Straight down from the middle.
        for (int y : new int[]{BunkerLayout.SURFACE_Y, BunkerLayout.COUNCIL_HALL.maxY(), BunkerLayout.TIER1, BunkerLayout.TIER1 - 1}) {
            if (!level.getBlockState(new BlockPos(0, y, 0)).is(ModBlocks.BUNKER_GLASS)) missing.add("glass at y" + y);
        }
        for (int y = BunkerLayout.COUNCIL_HALL.maxY() + 1; y < BunkerLayout.SURFACE_Y; y++) {
            if (!level.getBlockState(new BlockPos(4, y, -5)).isAir()) {
                missing.add("open shaft at y" + y);
                break;
            }
        }
        expect(helper, missing.isEmpty(), "built: " + missing);

        // Every place, chest and the reactor zone must be reachable on foot from the hall; the surface must not be.
        Set<BlockPos> reach = Walkability.reachable(level, BlockPos.containing(BunkerLayout.ADMIN_SPAWN), BunkerLayout.MASS);
        Mafia_sector.LOGGER.info("[bunker test] {} standable spots reachable from the council hall", reach.size());
        List<String> unreachable = new ArrayList<>();
        for (BunkerLayout.Seat seat : BunkerLayout.SEATS) {
            if (!reach.contains(seat.spot())) unreachable.add("place " + seat.number());
        }
        for (BunkerLayout.VoteChest chest : BunkerLayout.VOTE_CHESTS) {
            if (!reach.contains(BlockPos.containing(chest.stand()))) unreachable.add("chest " + chest.number());
        }
        for (BunkerLayout.Seat spot : BunkerLayout.TRIAL_SPOTS) {
            if (!reach.contains(spot.spot())) unreachable.add("trial spot " + spot.number());
        }
        expect(helper, unreachable.isEmpty(), "reachable on foot: " + unreachable);
        Set<BlockPos> surface = Walkability.reachable(level, BlockPos.containing(BunkerLayout.SURFACE_SPAWN), BunkerLayout.FOOTPRINT);
        expect(helper, surface.stream().allMatch(BunkerLayout::isOnSurface) && surface.size() > 100,
                "the surface is closed and walkable: " + surface.size());
        expect(helper, distanceToCouncil(BunkerLayout.SURFACE_SPAWN.y) > 48.0, "surface out of voice range");

        Path dir = level.getServer().getServerDirectory();
        BunkerPlanRenderer.render(level, BunkerLayout.TIER0 + 1, dir.resolve("bunker_plan_tier0.png"));
        BunkerPlanRenderer.render(level, BunkerLayout.TIER1 + 1, dir.resolve("bunker_plan_tier1.png"));
        BunkerPlanRenderer.render(level, BunkerLayout.TIER2 + 1, dir.resolve("bunker_plan_tier2.png"));
        helper.succeed();
    }

    /** Height of a surface position above the feet of players in the council circle. */
    private static double distanceToCouncil(double y) {
        return y - (BunkerLayout.TIER1 + 1);
    }

    private static void expect(GameTestHelper helper, boolean condition, String what) {
        if (!condition) {
            helper.fail("Missing: " + what);
        }
    }
}
