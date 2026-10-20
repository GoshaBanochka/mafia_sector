package sector.mafia_sector.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.client.DossierScreen;
import sector.mafia_sector.game.BunkerLighting;
import sector.mafia_sector.game.GameException;
import sector.mafia_sector.game.GameManager;
import sector.mafia_sector.game.GamePhase;
import sector.mafia_sector.game.GameSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Dev-only "photographer" for ./gradlew runPhotoClient: opens the photo world, rebuilds the bunker, flies a camera
 * through a list of viewpoints and saves a screenshot at each into run/screenshots, then quits. After the rooms it
 * plays a debug game with phantoms and photographs the board, every dossier tab, the look panel, the ballot and the final.
 * Inactive unless the system property mafia_sector.photo is set (only the photoClient run sets it).
 * Environment MAFIA_PHOTO_ONLY=game skips the rooms.
 */
@EventBusSubscriber(modid = Mafia_sector.MODID, value = Dist.CLIENT)
public final class Photographer {
    private static final boolean ENABLED = Boolean.getBoolean("mafia_sector.photo");
    private static final boolean ONLY_GAME = "game".equals(System.getenv("MAFIA_PHOTO_ONLY"));
    private static final int SETTLE_FIRST = 300;
    private static final int SETTLE = 140;

    /**
     * One screenshot: optional server-side setup, then the camera (feet position, yaw 0 south / 90 west / 180 north /
     * -90 east, pitch down positive; null = stay), the GUI on or off, the dossier screen open or not.
     */
    private record Shot(String name, @Nullable Consumer<MinecraftServer> setup, @Nullable Vec3 pos, float yaw, float pitch,
                        boolean emergency, boolean gui, @Nullable DossierScreen.Tab dossier) {
        static Shot room(String name, double x, double y, double z, float yaw, float pitch, boolean emergency) {
            return new Shot(name, null, new Vec3(x, y, z), yaw, pitch, emergency, false, null);
        }
    }

    /** The shelter, room by room; emergency = the red emergency lighting. */
    private static final List<Shot> ROOMS = List.of(
            Shot.room("61_council", 0.5, 51, 9.5, 180, -15, false),
            Shot.room("62_council_gallery", 9.5, 57, 9.5, 135, 25, false),
            Shot.room("63_board", 0.5, 57, -1.5, 180, -12, false),
            Shot.room("64_council_well", 0.5, 53, 5.5, 180, 55, false),
            Shot.room("65_voting_room", 21.5, 51, -3.5, 180, 12, false),
            Shot.room("66_surface", 0.5, 105, -13.5, 0, 5, false),
            Shot.room("67_surface_hatch", 0.5, 106, -2.5, 0, 82, false),
            Shot.room("68_canteen", -3.5, 51, -14.5, 135, 15, false),
            Shot.room("69_medbay", 3.5, 51, -14.5, -135, 15, false),
            Shot.room("70_radio", 0.5, 51, -32.5, 180, 15, false),
            Shot.room("71_pantry", 16.5, 51, 3.5, -45, 15, false),
            Shot.room("72_lab", 32.5, 51, 0.5, -90, 10, false),
            Shot.room("73_archive", -15.5, 51, -3.5, 135, 10, false),
            Shot.room("74_washroom", -15.5, 51, 3.5, 45, 15, false),
            Shot.room("75_security", -3.5, 51, 14.5, 45, 15, false),
            Shot.room("76_warehouse", 3.5, 51, 19.5, -90, 10, false),
            Shot.room("77_airlock", 0.5, 51, 28.5, 0, 5, false),
            Shot.room("78_stairwell", -32.5, 51, -1.5, 90, 35, false),
            Shot.room("79_reactor", 9.5, 43, 9.5, 135, 15, false),
            Shot.room("80_pumps", -17.5, 41, -4.5, 135, 10, false),
            Shot.room("81_ventilation", 12.5, 41, 0.5, -90, 10, false),
            Shot.room("82_council_emergency", 0.5, 51, 9.5, 180, -15, true));

    /** A debug game: the photographer plays with seven phantoms. */
    private static final List<Shot> GAME = List.of(
            new Shot("90_game_board", server -> {
                command(server, "bunker7 debug on");
                command(server, "bunker7 debug phantoms 7");
                command(server, "bunker7 start players @a");
                // Round one completely (professions), then a few turns of round two.
                advanceUntil(server, game -> game.round() == 2 && game.phase() == GamePhase.REVEAL);
                for (int i = 0; i < 4; i++) next(server);
            }, new Vec3(0.5, 57, -1.5), 180, -12, false, false, null),
            new Shot("91_game_circle", null, new Vec3(0.5, 51, 6.5), 180, -18, false, true, null),
            new Shot("92_game_look", null, null, 0, 0, false, true, null),
            new Shot("93_dossier_cards", null, new Vec3(0.5, 51, 3.5), 180, 0, false, true, DossierScreen.Tab.CARDS),
            new Shot("93_dossier_players", null, null, 0, 0, false, true, DossierScreen.Tab.PLAYERS),
            new Shot("93_dossier_bunker", null, null, 0, 0, false, true, DossierScreen.Tab.BUNKER),
            new Shot("93_dossier_action", null, null, 0, 0, false, true, DossierScreen.Tab.ACTION),
            // The ballot opens by itself when the vote starts.
            new Shot("94_game_ballot", server -> advanceUntil(server, game -> game.phase() == GamePhase.VOTING),
                    null, 0, 0, false, true, null),
            new Shot("95_game_final", server -> advanceUntil(server, game -> game.phase() == GamePhase.FINAL),
                    null, 0, 0, false, true, null),
            new Shot("96_dossier_final", null, null, 0, 0, false, true, DossierScreen.Tab.PLAYERS),
            new Shot("97_game_board_final", null, new Vec3(0.5, 57, -1.5), 180, -12, false, false, null));

    private static final List<Shot> SHOTS = shots();

    private static int tick;
    private static int stage = -1;
    private static int wait;
    /** Set on the server thread once the rebuild has finished. */
    private static volatile boolean built;

    private Photographer() {
    }

    private static List<Shot> shots() {
        List<Shot> shots = new ArrayList<>();
        if (!ONLY_GAME) shots.addAll(ROOMS);
        shots.addAll(GAME);
        return List.copyOf(shots);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) return;
        tick++;
        if (stage == -1) {
            if (tick < 80) return;
            mc.options.hideGui = true;
            mc.options.fov().set(85);
            run(server, mc, "bunker7 stop");
            run(server, mc, "gamemode spectator @s");
            run(server, mc, "bunker7 build force");
            server.execute(() -> built = true);
            stage = 0;
            wait = 0;
            return;
        }
        if (stage >= SHOTS.size()) {
            if (stage == SHOTS.size()) {
                Mafia_sector.LOGGER.info("[photographer] all {} shots taken", SHOTS.size());
                run(server, mc, "bunker7 stop");
                stage++;
                wait = 0;
            } else if (++wait == 40) {
                mc.stop();
            }
            return;
        }
        if (!built) return;
        Shot shot = SHOTS.get(stage);
        if (wait == 0 && !shot.name().endsWith("_ballot")) {
            mc.setScreen(null);
        }
        if (wait == 0) {
            mc.options.hideGui = !shot.gui();
            boolean emergency = shot.emergency();
            if (shot.setup() != null) {
                server.execute(() -> shot.setup().accept(server));
            } else if (!GameManager.isRunning()) {
                server.execute(() -> BunkerLighting.set(server, emergency));
            }
            if (shot.pos() != null) {
                run(server, mc, String.format(Locale.ROOT, "execute in mafia_sector:bunker7 run tp @s %.2f %.2f %.2f %.1f %.1f",
                        shot.pos().x, shot.pos().y, shot.pos().z, shot.yaw(), shot.pitch()));
            } else if (shot.name().endsWith("_look")) {
                server.execute(() -> lookAtPhantom(server, mc.player.getUUID()));
            }
        }
        wait++;
        if (shot.dossier() != null && wait == SETTLE - 20) {
            DossierScreen.lastTab = shot.dossier();
            mc.setScreen(new DossierScreen());
        }
        if (wait >= (stage == 0 ? SETTLE_FIRST : SETTLE)) {
            Mafia_sector.LOGGER.info("[photographer] {} at {} yaw {} pitch {}", shot.name(), mc.player.position(), mc.player.getYRot(), mc.player.getXRot());
            Screenshot.grab(mc.gameDirectory, "bunker_" + shot.name() + ".png", mc.getMainRenderTarget(),
                    message -> Mafia_sector.LOGGER.info("[photographer] {}", message.getString()));
            stage++;
            wait = 0;
        }
    }

    // ------------------------------------------------------------------ game helpers (server thread)

    private static void command(MinecraftServer server, String command) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
    }

    private static void next(MinecraftServer server) {
        try {
            GameManager.next(server);
        } catch (GameException e) {
            Mafia_sector.LOGGER.warn("[photographer] next: {}", e.getMessage());
        }
    }

    /** Presses "next" until the game reaches the wanted state (or gives up). */
    private static void advanceUntil(MinecraftServer server, java.util.function.Predicate<GameSession> done) {
        for (int i = 0; i < 200; i++) {
            GameSession game = GameManager.session();
            if (game == null || done.test(game)) return;
            next(server);
        }
    }

    /** Stands two blocks in front of a phantom in the circle, looking at it. */
    private static void lookAtPhantom(MinecraftServer server, java.util.UUID camera) {
        GameSession game = GameManager.session();
        ServerPlayer player = server.getPlayerList().getPlayer(camera);
        if (game == null || player == null) return;
        GameSession.Participant phantom = game.participants().stream().filter(p -> p.isPhantom() && p.isAlive()).findFirst().orElse(null);
        if (phantom == null) return;
        Vec3 seat = phantom.seat().spawn();
        Vec3 centre = Vec3.atBottomCenterOf(BunkerLayout.COUNCIL_CENTER);
        Vec3 pos = seat.add(centre.subtract(seat).normalize().scale(2.2));
        float yaw = BunkerLayout.yawToward(pos, seat.x, seat.z);
        player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, yaw, 8.0F);
    }

    /** Runs a command as the player with full permissions, on the server thread. */
    private static void run(MinecraftServer server, Minecraft mc, String command) {
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
            }
        });
    }
}
