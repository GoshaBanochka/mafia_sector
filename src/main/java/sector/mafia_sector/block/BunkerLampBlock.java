package sector.mafia_sector.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Lamp switched by the game: plafonds lit and emergency lamps dark normally, the other way round in emergency mode. */
public class BunkerLampBlock extends Block {
    public static final MapCodec<BunkerLampBlock> CODEC = simpleCodec(BunkerLampBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BunkerLampBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
