package sector.mafia_sector.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.item.BallotItem;
import sector.mafia_sector.item.DossierItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mafia_sector.MODID);

    public static final DeferredItem<BlockItem> BUNKER_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_WALL);
    public static final DeferredItem<BlockItem> BUNKER_PANEL = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_PANEL);
    public static final DeferredItem<BlockItem> BUNKER_CEILING = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_CEILING);
    public static final DeferredItem<BlockItem> BUNKER_PILLAR = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_PILLAR);
    public static final DeferredItem<BlockItem> BUNKER_FLOOR = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_FLOOR);
    public static final DeferredItem<BlockItem> BUNKER_FLOOR_TILES = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_FLOOR_TILES);
    public static final DeferredItem<BlockItem> BUNKER_FLOOR_ACCENT = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_FLOOR_ACCENT);
    public static final DeferredItem<BlockItem> BUNKER_HAZARD = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_HAZARD);
    public static final DeferredItem<BlockItem> BUNKER_HATCH = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_HATCH);
    public static final DeferredItem<BlockItem> BUNKER_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_GLASS);
    public static final DeferredItem<BlockItem> BUNKER_LAMP = ITEMS.registerSimpleBlockItem(ModBlocks.BUNKER_LAMP);
    public static final DeferredItem<BlockItem> EMERGENCY_LAMP = ITEMS.registerSimpleBlockItem(ModBlocks.EMERGENCY_LAMP);
    public static final DeferredItem<BlockItem> VENT_GRATE = ITEMS.registerSimpleBlockItem(ModBlocks.VENT_GRATE);
    public static final DeferredItem<BlockItem> SECTOR_TERMINAL = ITEMS.registerSimpleBlockItem(ModBlocks.SECTOR_TERMINAL);
    public static final DeferredItem<BlockItem> BOARD_PANEL = ITEMS.registerSimpleBlockItem(ModBlocks.BOARD_PANEL);
    public static final DeferredItem<BlockItem> BOARD = ITEMS.registerSimpleBlockItem(ModBlocks.BOARD);
    public static final DeferredItem<BlockItem> SHELTER_PLASTER = ITEMS.registerSimpleBlockItem(ModBlocks.SHELTER_PLASTER);
    public static final DeferredItem<BlockItem> SHELTER_PAINT = ITEMS.registerSimpleBlockItem(ModBlocks.SHELTER_PAINT);
    public static final DeferredItem<BlockItem> SHELTER_PAINT_TOP = ITEMS.registerSimpleBlockItem(ModBlocks.SHELTER_PAINT_TOP);
    public static final DeferredItem<BlockItem> SHELTER_CONCRETE = ITEMS.registerSimpleBlockItem(ModBlocks.SHELTER_CONCRETE);
    public static final DeferredItem<BlockItem> SHELTER_METLAKH = ITEMS.registerSimpleBlockItem(ModBlocks.SHELTER_METLAKH);
    public static final DeferredItem<BlockItem> SHELTER_TILE = ITEMS.registerSimpleBlockItem(ModBlocks.SHELTER_TILE);
    public static final DeferredItem<BlockItem> SHELTER_STEEL = ITEMS.registerSimpleBlockItem(ModBlocks.SHELTER_STEEL);
    public static final DeferredItem<BlockItem> PLAFOND = ITEMS.registerSimpleBlockItem(ModBlocks.PLAFOND);

    /** Given to every player at the start: opens the dossier screen. */
    public static final DeferredItem<DossierItem> DOSSIER = ITEMS.registerItem("dossier", DossierItem::new, new Item.Properties().stacksTo(1));
    /** One per vote, with the voter's name: opens the ballot screen. */
    public static final DeferredItem<BallotItem> BALLOT = ITEMS.registerItem("ballot", BallotItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {
    }
}
