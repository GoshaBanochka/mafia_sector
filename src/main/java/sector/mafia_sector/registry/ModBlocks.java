package sector.mafia_sector.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.block.BoardBlock;
import sector.mafia_sector.block.BunkerLampBlock;
import sector.mafia_sector.block.ConsoleBlock;

import java.util.List;
import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mafia_sector.MODID);

    // Shell: everything the bunker is made of. Unbreakable and unmovable.
    public static final DeferredBlock<Block> BUNKER_WALL = shellBlock("bunker_wall", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<Block> BUNKER_PANEL = shellBlock("bunker_panel", MapColor.METAL, SoundType.NETHERITE_BLOCK);
    public static final DeferredBlock<Block> BUNKER_CEILING = shellBlock("bunker_ceiling", MapColor.METAL, SoundType.NETHERITE_BLOCK);
    public static final DeferredBlock<RotatedPillarBlock> BUNKER_PILLAR = BLOCKS.registerBlock("bunker_pillar", RotatedPillarBlock::new,
            shell().mapColor(MapColor.STONE).sound(SoundType.STONE));
    public static final DeferredBlock<Block> BUNKER_FLOOR = shellBlock("bunker_floor", MapColor.DEEPSLATE, SoundType.POLISHED_DEEPSLATE);
    public static final DeferredBlock<Block> BUNKER_FLOOR_TILES = shellBlock("bunker_floor_tiles", MapColor.DEEPSLATE, SoundType.DEEPSLATE_TILES);
    public static final DeferredBlock<Block> BUNKER_FLOOR_ACCENT = shellBlock("bunker_floor_accent", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<Block> BUNKER_HAZARD = shellBlock("bunker_hazard", MapColor.COLOR_YELLOW, SoundType.NETHERITE_BLOCK);
    public static final DeferredBlock<Block> BUNKER_HATCH = shellBlock("bunker_hatch", MapColor.METAL, SoundType.NETHERITE_BLOCK);
    public static final DeferredBlock<TransparentBlock> BUNKER_GLASS = BLOCKS.registerBlock("bunker_glass", TransparentBlock::new,
            shell().sound(SoundType.GLASS).noOcclusion()
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false));
    /** Main light: on normally. */
    public static final DeferredBlock<BunkerLampBlock> BUNKER_LAMP = BLOCKS.registerBlock("bunker_lamp", BunkerLampBlock::new,
            shell().sound(SoundType.COPPER_BULB).lightLevel(s -> s.getValue(BunkerLampBlock.LIT) ? 15 : 0));
    /** Dim red light: on in emergency mode. */
    public static final DeferredBlock<BunkerLampBlock> EMERGENCY_LAMP = BLOCKS.registerBlock("emergency_lamp", BunkerLampBlock::new,
            shell().sound(SoundType.GLASS).lightLevel(s -> s.getValue(BunkerLampBlock.LIT) ? 6 : 0));
    /** Ventilation grate: looks like a wall grille, but players walk through it into the ducts. */
    public static final DeferredBlock<Block> VENT_GRATE = BLOCKS.registerBlock("vent_grate", Block::new,
            shell().mapColor(MapColor.COLOR_CYAN).sound(SoundType.COPPER_GRATE).noCollission().noOcclusion()
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false));
    public static final DeferredBlock<ConsoleBlock> SECTOR_TERMINAL = BLOCKS.registerBlock("sector_terminal",
            ConsoleBlock::new, shell().mapColor(MapColor.METAL).sound(SoundType.NETHERITE_BLOCK));
    /** The dark face of the board in the council hall. */
    public static final DeferredBlock<Block> BOARD_PANEL = shellBlock("board_panel", MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK);
    /** Top-left block of the board: draws all of its text. */
    public static final DeferredBlock<BoardBlock> BOARD = BLOCKS.registerBlock("board", BoardBlock::new,
            shell().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK));

    // Shelter palette: whitewash above, oil paint below, concrete, tiles, steel.
    public static final DeferredBlock<Block> SHELTER_PLASTER = shellBlock("shelter_plaster", MapColor.SAND, SoundType.STONE);
    public static final DeferredBlock<Block> SHELTER_PAINT = shellBlock("shelter_paint", MapColor.COLOR_GREEN, SoundType.STONE);
    public static final DeferredBlock<Block> SHELTER_PAINT_TOP = shellBlock("shelter_paint_top", MapColor.COLOR_GREEN, SoundType.STONE);
    public static final DeferredBlock<Block> SHELTER_CONCRETE = shellBlock("shelter_concrete", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<Block> SHELTER_METLAKH = shellBlock("shelter_metlakh", MapColor.TERRACOTTA_RED, SoundType.STONE);
    public static final DeferredBlock<Block> SHELTER_TILE = shellBlock("shelter_tile", MapColor.SNOW, SoundType.STONE);
    public static final DeferredBlock<Block> SHELTER_STEEL = shellBlock("shelter_steel", MapColor.METAL, SoundType.NETHERITE_BLOCK);
    /** Ceiling lamp of the shelter: frosted round glass underneath, concrete on top so it also works as floor. */
    public static final DeferredBlock<BunkerLampBlock> PLAFOND = BLOCKS.registerBlock("plafond", BunkerLampBlock::new,
            shell().sound(SoundType.GLASS).lightLevel(s -> s.getValue(BunkerLampBlock.LIT) ? 15 : 0));

    private static final List<Supplier<? extends Block>> SHELL = List.of(BUNKER_WALL, BUNKER_PANEL, BUNKER_CEILING, BUNKER_PILLAR,
            BUNKER_FLOOR, BUNKER_FLOOR_TILES, BUNKER_FLOOR_ACCENT, BUNKER_HAZARD, BUNKER_HATCH, BUNKER_GLASS,
            BUNKER_LAMP, EMERGENCY_LAMP, VENT_GRATE, SECTOR_TERMINAL, BOARD_PANEL, BOARD,
            SHELTER_PLASTER, SHELTER_PAINT, SHELTER_PAINT_TOP, SHELTER_CONCRETE, SHELTER_METLAKH, SHELTER_TILE, SHELTER_STEEL, PLAFOND);

    private ModBlocks() {
    }

    private static DeferredBlock<Block> shellBlock(String name, MapColor color, SoundType sound) {
        return BLOCKS.registerBlock(name, Block::new, shell().mapColor(color).sound(sound));
    }

    /** Unbreakable, unmovable, no drops, no mob spawns. Hardness -1 also stops Create drills and contraptions. */
    private static BlockBehaviour.Properties shell() {
        return BlockBehaviour.Properties.of()
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((s, l, p, e) -> false);
    }

    /** Blocks that form the bunker itself and must never be broken, even by admins in creative. */
    public static boolean isShell(BlockState state) {
        for (Supplier<? extends Block> block : SHELL) {
            if (state.is(block.get())) {
                return true;
            }
        }
        return false;
    }
}
