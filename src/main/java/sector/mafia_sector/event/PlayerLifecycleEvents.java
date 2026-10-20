package sector.mafia_sector.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.dimension.Bunker7Dimension;
import sector.mafia_sector.game.GameManager;
import sector.mafia_sector.game.PlayerStash;

/** Logins, logouts, server start and stop, and keeping participants inside the bunker. */
@EventBusSubscriber(modid = Mafia_sector.MODID)
public final class PlayerLifecycleEvents {
    private PlayerLifecycleEvents() {
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GameManager.onLogin(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GameManager.onLogout(player);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        GameManager.tick(event.getServer());
    }

    /** Participants cannot leave bunker7 by portals, other mods' teleports or admin commands; only the game moves them. */
    @SubscribeEvent
    static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && Bunker7Dimension.is(player.level())
                && event.getDimension() != Bunker7Dimension.key()
                && GameManager.isParticipant(player.getUUID())
                && !GameManager.isTeleporting()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        GameManager.reset();
        int pending = PlayerStash.get(event.getServer()).players().size();
        if (pending > 0) {
            Mafia_sector.LOGGER.warn("Bunker 7: a game was interrupted; {} players get their things back on their next login", pending);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        if (GameManager.isRunning()) {
            Mafia_sector.LOGGER.warn("Bunker 7: server stops during a game; it is cancelled, things are returned on next login");
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        GameManager.reset();
    }
}
