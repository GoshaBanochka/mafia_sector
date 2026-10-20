package sector.mafia_sector.client;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.dimension.Bunker7Dimension;

/**
 * The Haze on the surface: thick ash-coloured fog, falling ash and a low rumble. The fog thins when the player looks
 * down, so the exiled can watch the council hall through the hatch. Client only, decided by the camera position.
 */
@EventBusSubscriber(modid = Mafia_sector.MODID, value = Dist.CLIENT)
public final class SurfaceAtmosphere {
    private static final float HAZE_DISTANCE = 9.0F;
    /** Looking down through the hatch: the hall floor is about 55 blocks below, the Reactor 65. */
    private static final float HATCH_DISTANCE = 96.0F;
    private static final int ASH_PER_TICK = 6;

    private SurfaceAtmosphere() {
    }

    private static boolean inHaze(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && Bunker7Dimension.is(mc.level) && BunkerLayout.isOnSurface(camera.getPosition());
    }

    @SubscribeEvent
    static void onFog(ViewportEvent.RenderFog event) {
        if (!inHaze(event.getCamera())) {
            return;
        }
        // 0 looking ahead, 1 looking steeply down.
        float down = Mth.clamp((event.getCamera().getXRot() - 35.0F) / 30.0F, 0.0F, 1.0F);
        event.setNearPlaneDistance(0.0F);
        event.setFarPlaneDistance(Mth.lerp(down, HAZE_DISTANCE, HATCH_DISTANCE));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (inHaze(event.getCamera())) {
            event.setRed(0.42F);
            event.setGreen(0.40F);
            event.setBlue(0.33F);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (mc.level == null || player == null || mc.isPaused() || !Bunker7Dimension.is(mc.level)
                || !BunkerLayout.isOnSurface(player.position())) {
            return;
        }
        RandomSource random = mc.level.random;
        for (int i = 0; i < ASH_PER_TICK; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 16.0;
            double y = player.getY() + random.nextDouble() * 6.0;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 16.0;
            mc.level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.02, -0.03, 0.01);
        }
        if (random.nextInt(240) == 0) {
            mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(),
                    SoundSource.AMBIENT, 0.7F, 0.7F + random.nextFloat() * 0.2F, false);
        }
    }
}
