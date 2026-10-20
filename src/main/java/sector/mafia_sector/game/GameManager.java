package sector.mafia_sector.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.Config;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.build.BunkerBuilder;
import sector.mafia_sector.build.BunkerData;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.card.BunkerInfo;
import sector.mafia_sector.card.Card;
import sector.mafia_sector.card.CardLibrary;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.card.Catastrophe;
import sector.mafia_sector.card.Deck;
import sector.mafia_sector.dimension.Bunker7Dimension;
import sector.mafia_sector.registry.ModItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Owns the single running game. All game logic runs on the server thread.
 * <p>
 * Inventory safety: snapshots go to {@link PlayerStash} and are flushed to disk before anyone is stripped or moved.
 * A snapshot is removed only after it was applied and the player data was saved, and applying it twice is harmless,
 * so a crash at any point either loses nothing or replays the same restore.
 */
public final class GameManager {
    public static final int MAX_PHANTOMS = BunkerLayout.MAX_PLAYERS - 1;

    @Nullable
    private static GameSession session;
    private static boolean debug;
    private static int phantoms = 3;
    /** Set while the game itself moves a player between dimensions, so the travel guard lets it through. */
    private static boolean teleporting;
    /** Logins are handled one tick later, once the player is fully in the world. */
    private static final Set<UUID> pendingLogins = new LinkedHashSet<>();

    private GameManager() {
    }

    @Nullable
    public static GameSession session() {
        return session;
    }

    public static boolean isRunning() {
        return session != null;
    }

    public static boolean isParticipant(UUID player) {
        return session != null && session.has(player);
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    /** Debug games are filled up with this many phantoms. */
    public static int phantoms() {
        return phantoms;
    }

    public static void setPhantoms(int count) {
        phantoms = Math.max(0, Math.min(MAX_PHANTOMS, count));
    }

    public static boolean isTeleporting() {
        return teleporting;
    }

    // ------------------------------------------------------------------ start / stop

    public static void start(MinecraftServer server, Collection<ServerPlayer> players) throws GameException {
        if (session != null) {
            throw new GameException(Component.translatable("game.mafia_sector.already_running"));
        }
        ServerLevel bunker = Bunker7Dimension.get(server);
        if (bunker == null) {
            throw new GameException(Component.translatable("commands.mafia_sector.no_dimension"));
        }
        BunkerData data = BunkerData.get(server);
        if (!data.isBuilt()) {
            throw new GameException(Component.translatable("commands.mafia_sector.not_built"));
        }
        if (data.isOutdated()) {
            throw new GameException(Component.translatable("commands.mafia_sector.build.outdated", data.getBuiltVersion(), BunkerLayout.VERSION));
        }
        List<ServerPlayer> list = players.stream().distinct().toList();
        if (list.isEmpty()) {
            throw new GameException(Component.translatable("game.mafia_sector.no_players"));
        }
        int phantomCount = debug ? Math.min(phantoms, BunkerLayout.MAX_PLAYERS - list.size()) : 0;
        int total = list.size() + phantomCount;
        if (list.size() > BunkerLayout.MAX_PLAYERS) {
            throw new GameException(Component.translatable("game.mafia_sector.too_many", list.size(), BunkerLayout.MAX_PLAYERS));
        }
        int min = Config.MIN_PLAYERS.get();
        if (!debug && total < min) {
            throw new GameException(Component.translatable("game.mafia_sector.too_few", total, min));
        }
        CardLibrary.Contents cards = CardLibrary.get();
        if (cards.catastrophes().isEmpty() || cards.bunkers().isEmpty()) {
            throw new GameException(Component.translatable("game.mafia_sector.no_cards", String.join("; ", cards.errors())));
        }
        for (CardType type : CardType.values()) {
            if (cards.cards(type).size() < total) {
                throw new GameException(Component.translatable("game.mafia_sector.too_few_cards",
                        Component.translatable(type.key()), cards.cards(type).size(), total));
            }
        }

        // Leftovers from an interrupted game go back first, so a fresh snapshot never overwrites them.
        PlayerStash stash = PlayerStash.get(server);
        restore(server, list.stream().filter(p -> stash.has(p.getUUID())).toList());
        for (ServerPlayer player : list) {
            stash.put(player.getUUID(), PlayerSnapshot.capture(player));
        }
        stash.flush(server);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Catastrophe catastrophe = cards.catastrophes().get(random.nextInt(cards.catastrophes().size()));
        BunkerInfo shelter = cards.bunkers().get(random.nextInt(cards.bunkers().size()));
        int seats = RoundPlan.seats(total);
        GameSession game = new GameSession(debug, catastrophe, shelter, new Deck(cards), seats, RoundPlan.forPlayers(total, seats));
        List<Integer> places = new ArrayList<>();
        for (int i = 0; i < BunkerLayout.MAX_PLAYERS; i++) {
            places.add(i);
        }
        Collections.shuffle(places);
        for (int i = 0; i < list.size(); i++) {
            ServerPlayer player = list.get(i);
            game.add(new GameSession.Participant(player.getUUID(), player.getGameProfile().getName(), places.get(i), false));
        }
        for (int i = 0; i < phantomCount; i++) {
            game.add(new GameSession.Participant(UUID.randomUUID(), "Фантом-" + (i + 1),
                    places.get(list.size() + i), true));
        }
        for (GameSession.Participant participant : game.participants()) {
            for (CardType type : CardType.values()) {
                participant.dossier().set(type, game.deck().draw(type));
            }
        }
        session = game;

        BunkerBuilder.resetReactorZone(bunker, data);
        for (ServerPlayer player : list) {
            enterBunker(player, bunker, game.get(player.getUUID()));
        }
        Phantoms.spawn(bunker, game);
        writeNameSigns(bunker, game);
        RoundController.begin(server, game);
        Mafia_sector.LOGGER.info("Bunker 7: game started with {} players{}, {} places, plan {}", total,
                debug ? " (debug, " + phantomCount + " phantoms)" : "", seats, game.plan());
    }

    /** Ends the game and sends everyone online home. Offline participants get their things back on their next login. */
    public static void stop(MinecraftServer server, Component reason) throws GameException {
        GameSession game = session;
        if (game == null) {
            throw new GameException(Component.translatable("game.mafia_sector.not_running"));
        }
        session = null;
        game.hud().hide();
        BunkerLighting.set(server, false);
        List<ServerPlayer> online = new ArrayList<>();
        for (GameSession.Participant participant : game.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.uuid());
            if (player != null) {
                online.add(player);
            }
        }
        restore(server, online);
        online.forEach(player -> player.sendSystemMessage(reason));
        GameSync.clear(server, game);
        ServerLevel bunker = Bunker7Dimension.get(server);
        if (bunker != null) {
            Phantoms.remove(bunker, game);
            clearNameSigns(bunker);
            BunkerBuilder.resetReactorZone(bunker, BunkerData.get(server));
        }
        Mafia_sector.LOGGER.info("Bunker 7: game stopped, {} players returned now, {} on next login",
                online.size(), game.participants().stream().filter(p -> !p.isPhantom()).count() - online.size());
    }

    /** Gives stashed players their things back and removes their snapshots. Players without a snapshot are skipped. */
    public static void restore(MinecraftServer server, List<ServerPlayer> players) {
        PlayerStash stash = PlayerStash.get(server);
        List<ServerPlayer> restored = new ArrayList<>();
        for (ServerPlayer player : players) {
            var snapshot = stash.get(player.getUUID());
            if (snapshot != null) {
                PlayerSnapshot.apply(player, snapshot);
                restored.add(player);
            }
        }
        if (restored.isEmpty()) {
            return;
        }
        // Player files first, then the stash: a crash in between only replays the same restore.
        server.getPlayerList().saveAll();
        restored.forEach(player -> stash.remove(player.getUUID()));
        stash.flush(server);
    }

    private static void enterBunker(ServerPlayer player, ServerLevel bunker, GameSession.Participant participant) {
        PlayerSnapshot.strip(player);
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.getInventory().setItem(0, ModItems.DOSSIER.get().getDefaultInstance());
        sendToSeat(player, bunker, participant.seat());
        player.sendSystemMessage(Component.translatable("game.mafia_sector.welcome", participant.seat().number()));
    }

    // ------------------------------------------------------------------ admin control

    public static void next(MinecraftServer server) throws GameException {
        RoundController.skip(server, running());
    }

    public static void setPaused(MinecraftServer server, boolean paused) throws GameException {
        GameSession game = running();
        if (game.isPaused() == paused) {
            throw new GameException(Component.translatable(paused ? "game.mafia_sector.already_paused" : "game.mafia_sector.not_paused"));
        }
        game.setPaused(paused);
        game.hud().update(server, game);
    }

    /** Debug: deals a specific card to a participant. The old card is gone; the new one leaves the deck. */
    public static void giveCard(MinecraftServer server, GameSession.Participant participant, Card card) throws GameException {
        GameSession game = running();
        for (GameSession.Participant other : game.participants()) {
            if (other != participant && other.dossier().card(card.type()) != null && other.dossier().card(card.type()).id().equals(card.id())) {
                throw new GameException(Component.translatable("commands.mafia_sector.card.taken", other.name()));
            }
        }
        game.deck().take(card);
        participant.dossier().set(card.type(), card);
        game.markDirty();
    }

    static GameSession running() throws GameException {
        if (session == null) {
            throw new GameException(Component.translatable("game.mafia_sector.not_running"));
        }
        return session;
    }

    public static void sendToSeat(ServerPlayer player, ServerLevel bunker, BunkerLayout.Seat seat) {
        teleport(player, bunker, seat.spawn(), seat.yaw(), 0.0F);
    }

    /** The only way the game moves players; also drops them off Create seats first. */
    public static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        player.stopRiding();
        teleporting = true;
        try {
            player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
        } finally {
            teleporting = false;
        }
    }

    // ------------------------------------------------------------------ messages

    /** Everyone who follows the game: online participants and anyone else standing in the bunker. */
    static List<ServerPlayer> audience(MinecraftServer server, GameSession game) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (game.has(player.getUUID()) || Bunker7Dimension.is(player.level())) {
                players.add(player);
            }
        }
        return players;
    }

    static void broadcast(MinecraftServer server, GameSession game, Component message) {
        audience(server, game).forEach(player -> player.sendSystemMessage(message));
    }

    static void forEachOnline(MinecraftServer server, GameSession game, BiConsumer<ServerPlayer, GameSession.Participant> action) {
        for (GameSession.Participant participant : game.participants()) {
            ServerPlayer player = participant.isPhantom() ? null : server.getPlayerList().getPlayer(participant.uuid());
            if (player != null) {
                action.accept(player, participant);
            }
        }
    }

    /** The player's name for messages, gold. */
    static MutableComponent name(GameSession.Participant participant) {
        return Component.literal(participant.name()).withStyle(ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ signs

    /** Names on the signs behind the places in the council circle and over the ballot chests. */
    private static void writeNameSigns(ServerLevel bunker, GameSession game) {
        clearNameSigns(bunker);
        for (GameSession.Participant participant : game.participants()) {
            updateNameSigns(bunker, participant);
        }
    }

    static void updateNameSigns(ServerLevel bunker, GameSession.Participant participant) {
        Component name = participant.isAlive() ? Component.literal(participant.name())
                : Component.literal(participant.name()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.STRIKETHROUGH);
        BunkerBuilder.writeSign(bunker, participant.seat().sign(), BunkerBuilder.seatLines(participant.seat().number(), name));
        BunkerBuilder.writeSign(bunker, participant.voteChest().pos().above(), BunkerBuilder.seatLines(participant.voteChest().number(), name));
    }

    private static void clearNameSigns(ServerLevel bunker) {
        for (BunkerLayout.Seat seat : BunkerLayout.SEATS) {
            BunkerBuilder.writeSign(bunker, seat.sign(), BunkerBuilder.seatLines(seat.number(), null));
        }
        for (BunkerLayout.VoteChest chest : BunkerLayout.VOTE_CHESTS) {
            BunkerBuilder.writeSign(bunker, chest.pos().above(), BunkerBuilder.seatLines(chest.number(), null));
        }
    }

    // ------------------------------------------------------------------ connection and lifecycle

    public static void onLogin(ServerPlayer player) {
        pendingLogins.add(player.getUUID());
    }

    public static void onLogout(ServerPlayer player) {
        pendingLogins.remove(player.getUUID());
        if (session != null) {
            session.hud().removePlayer(player);
            if (session.get(player.getUUID()) instanceof GameSession.Participant participant) {
                participant.setOnline(false, player.getServer().getTickCount());
                RoundController.onLeft(player.getServer(), session, participant);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (session != null) {
            RoundController.tick(server, session);
        }
        if (session != null) {
            GameSync.tick(server, session);
        }
        if (pendingLogins.isEmpty()) {
            return;
        }
        List<UUID> logins = List.copyOf(pendingLogins);
        pendingLogins.clear();
        for (UUID uuid : logins) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                handleLogin(server, player);
            }
        }
    }

    private static void handleLogin(MinecraftServer server, ServerPlayer player) {
        GameSession.Participant participant = session != null ? session.get(player.getUUID()) : null;
        if (participant != null) {
            participant.setOnline(true, server.getTickCount());
            RoundController.place(server, session, player, participant);
            session.hud().update(server, session);
            session.markDirty();
            player.sendSystemMessage(Component.translatable("game.mafia_sector.rejoined"));
            broadcast(server, session, Component.translatable("game.mafia_sector.player_back", name(participant)).withStyle(ChatFormatting.GRAY));
        } else if (PlayerStash.get(server).has(player.getUUID())) {
            restore(server, List.of(player));
            player.sendSystemMessage(Component.translatable("game.mafia_sector.returned_after_absence"));
        }
    }

    /** Called on server start and stop: nothing survives a restart except the stash. */
    public static void reset() {
        if (session != null) {
            session.hud().hide();
        }
        session = null;
        debug = false;
        teleporting = false;
        pendingLogins.clear();
    }
}
