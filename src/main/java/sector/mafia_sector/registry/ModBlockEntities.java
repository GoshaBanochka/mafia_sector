package sector.mafia_sector.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.block.BoardBlockEntity;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mafia_sector.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoardBlockEntity>> BOARD = BLOCK_ENTITIES.register("board",
            () -> BlockEntityType.Builder.of(BoardBlockEntity::new, ModBlocks.BOARD.get()).build(null));

    private ModBlockEntities() {
    }
}
