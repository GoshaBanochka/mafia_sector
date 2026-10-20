package sector.mafia_sector;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import sector.mafia_sector.registry.ModBlockEntities;
import sector.mafia_sector.registry.ModBlocks;
import sector.mafia_sector.registry.ModCreativeTabs;
import sector.mafia_sector.registry.ModItems;

@Mod(Mafia_sector.MODID)
public class Mafia_sector {
    public static final String MODID = "mafia_sector";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mafia_sector(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
