package sector.mafia_sector.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import sector.mafia_sector.card.Card;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.card.Dossier;
import sector.mafia_sector.network.SyncPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sends the game state to clients: on every change (checked every few ticks) and once a second for the timers.
 * Each participant gets their own copy with their own cards; everybody else only sees revealed cards.
 */
public final class GameSync {
    private static final int CHECK_INTERVAL = 5;
    /** Timers count down on the client; this resend only catches anything missed. */
    private static final int REFRESH_INTERVAL = 100;

    private GameSync() {
    }

    static void tick(MinecraftServer server, GameSession game) {
        int tick = server.getTickCount();
        if (tick % CHECK_INTERVAL != 0) {
            return;
        }
        if (game.takeDirty() || tick % REFRESH_INTERVAL == 0) {
            send(server, game);
        }
    }

    static void send(MinecraftServer server, GameSession game) {
        SyncPayload common = build(game, null);
        for (ServerPlayer player : GameManager.audience(server, game)) {
            GameSession.Participant participant = game.get(player.getUUID());
            PacketDistributor.sendToPlayer(player, participant != null ? build(game, participant) : common);
        }
    }

    /** Tells former participants and onlookers that the game is over, so the board and the dossier clear. */
    static void clear(MinecraftServer server, GameSession game) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (game.has(player.getUUID()) || sector.mafia_sector.dimension.Bunker7Dimension.is(player.level())) {
                PacketDistributor.sendToPlayer(player, SyncPayload.inactive());
            }
        }
    }

    public static SyncPayload build(GameSession game, GameSession.Participant receiver) {
        List<SyncPayload.PlayerView> players = new ArrayList<>();
        List<GameSession.Participant> sorted = new ArrayList<>(game.participants());
        sorted.sort(Comparator.comparingInt(GameSession.Participant::place));
        for (GameSession.Participant participant : sorted) {
            List<SyncPayload.CardView> revealed = new ArrayList<>();
            Dossier dossier = participant.dossier();
            for (CardType type : CardType.values()) {
                if (dossier.isRevealed(type)) {
                    // Others only need the short text; the explanation stays in the owner's copy.
                    Card card = dossier.card(type);
                    revealed.add(new SyncPayload.CardView(type.ordinal(), card != null ? card.text() : "", ""));
                }
            }
            players.add(new SyncPayload.PlayerView(participant.uuid(), participant.name(), participant.place(), participant.isAlive(),
                    participant.isOnline(), participant.exileOrder(), game.phase().isVote() && game.hasVoted(participant.uuid()), revealed));
        }
        Optional<SyncPayload.OwnView> own = Optional.empty();
        if (receiver != null) {
            Dossier dossier = receiver.dossier();
            List<SyncPayload.CardView> cards = new ArrayList<>();
            List<Integer> revealedTypes = new ArrayList<>();
            for (CardType type : CardType.values()) {
                Card card = dossier.card(type);
                if (card == null) continue;
                cards.add(view(type, card));
                if (dossier.isRevealed(type)) revealedTypes.add(type.ordinal());
            }
            Card action = dossier.card(CardType.ACTION);
            own = Optional.of(new SyncPayload.OwnView(receiver.uuid(), cards, revealedTypes, dossier.isActionUsed(),
                    action != null && action.action() != null ? action.action().id() : "", Optional.ofNullable(game.votes.get(receiver.uuid()))));
        }
        return new SyncPayload(true, game.phase().ordinal(), game.round(), game.totalRounds(), game.phaseTicksLeft(), game.phaseTicksTotal(),
                game.isPaused(), game.seats(), game.catastrophe().title(), game.catastrophe().description(), game.bunker().title(),
                game.bunker().area(), game.bunker().supplies(), game.bunker().duration(), game.bunker().features(),
                Optional.ofNullable(game.currentTurn()), game.revealedThisTurn(), players, List.copyOf(game.candidates()), own);
    }

    private static SyncPayload.CardView view(CardType type, Card card) {
        return new SyncPayload.CardView(type.ordinal(), card != null ? card.text() : "", card != null ? card.detail() : "");
    }
}
