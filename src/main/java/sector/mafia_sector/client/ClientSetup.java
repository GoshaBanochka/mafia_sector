package sector.mafia_sector.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.registry.ModBlockEntities;

/** Client-only registration on the mod bus. */
@EventBusSubscriber(modid = Mafia_sector.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping DOSSIER_KEY = new KeyMapping("key.mafia_sector.dossier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B,
            "key.categories.mafia_sector");

    private ClientSetup() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.BOARD.get(), BoardRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(DOSSIER_KEY);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(Mafia_sector.MODID, "look_panel"), LookPanel::render);
    }
}
