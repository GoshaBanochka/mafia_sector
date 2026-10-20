package sector.mafia_sector.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Rough walkability flood fill: where can a player stand, starting from one spot, walking and stepping up one block?
 * Doors count as open; low blocks (carpets, beds, seats, slabs) count as floor clutter one can stand on.
 */
final class Walkability {
    private Walkability() {
    }

    static Set<BlockPos> reachable(ServerLevel level, BlockPos start, BoundingBox bounds) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            // Ladders, vines and scaffolding: straight up and down.
            if (climbable(level, pos) || climbable(level, pos.above())) {
                for (int dy : new int[]{-1, 1}) {
                    BlockPos next = pos.above(dy);
                    if (bounds.isInside(next) && !seen.contains(next) && passable(level, next) && passable(level, next.above())) {
                        seen.add(next);
                        queue.add(next);
                    }
                }
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = pos.relative(dir).above(dy);
                    boolean onLadder = climbable(level, next) && passable(level, next) && passable(level, next.above());
                    if (!bounds.isInside(next) || seen.contains(next) || !(standable(level, next) || onLadder)) continue;
                    // Stepping up needs headroom above the current spot as well.
                    if (dy == 1 && !passable(level, pos.above(2))) continue;
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    static boolean climbable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(net.minecraft.tags.BlockTags.CLIMBABLE) || state.is(net.minecraft.world.level.block.Blocks.SCAFFOLDING);
    }

    /** Feet at {@code pos}: room for the body, something to stand on below. */
    static boolean standable(ServerLevel level, BlockPos pos) {
        return passable(level, pos) && passable(level, pos.above()) && !passable(level, pos.below())
                && level.getFluidState(pos.below()).isEmpty();
    }

    static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // Everything a player opens by hand.
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock
                || climbable(level, pos)) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.5625;
    }
}
