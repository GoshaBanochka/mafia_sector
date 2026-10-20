package sector.mafia_sector.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import sector.mafia_sector.registry.ModBlockEntities;

/** Anchor of the board. Holds no data yet: the public game state reaches the client in a later stage. */
public class BoardBlockEntity extends BlockEntity {
    public BoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOARD.get(), pos, state);
    }
}
