package sector.mafia_sector.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.Config;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.card.BunkerInfo;
import sector.mafia_sector.card.Card;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.dimension.Bunker7Dimension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The rounds: turns of the reveal phase, discussion, votes with justification and revote, exiles; then the finale.
 * Every transition goes through here. All methods run on the server thread.
 */
public final class RoundController {
    private static final int HUD_INTERVAL = 10;
    private static final int LEASH_INTERVAL = 10;
    /** A phantom reveals this long after its turn starts and ends the turn a little later. */
    private static final int PHANTOM_REVEAL_TICKS = 30;
    private static final int PHANTOM_END_TICKS = 60;
    /** Once everybody has voted, the vote closes after this many ticks. */
    private static final int ALL_VOTED_TICKS = 40;

    public enum ExileReason { VOTED, SECTOR, LEFT }

    private RoundController() {
    }

    /** Timer length in ticks; debug games run faster. */
    static int ticks(GameSession game, int seconds) {
        if (!game.isDebug()) {
            return seconds * 20;
        }
        return Math.max(40, (int) Math.round(seconds * 20 * Config.DEBUG_TIME_SCALE.get()));
    }

    // ------------------------------------------------------------------ start

    static void begin(MinecraftServer server, GameSession game) {
        game.enterPhase(GamePhase.INTRO, ticks(game, Config.INTRO_SECONDS.get()));
        BunkerLighting.set(server, false);
        GameManager.forEachOnline(server, game, (player, participant) -> {
            place(server, game, player, participant);
            title(player, Component.literal(game.catastrophe().title()).withStyle(ChatFormatting.RED),
                    Component.translatable("title.mafia_sector.intro.sub", game.seats(), game.participants().size()), 100);
            player.playNotifySound(SoundEvents.WARDEN_EMERGE, SoundSource.MASTER, 0.6F, 0.8F);
        });
        for (Component line : introLines(game)) {
            GameManager.broadcast(server, game, line);
        }
        game.hud().update(server, game);
    }

    /** The opening message: catastrophe, bunker, places. Also shown by /b7 info. */
    public static List<Component> introLines(GameSession game) {
        BunkerInfo bunker = game.bunker();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("game.mafia_sector.intro.header").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        lines.add(Component.translatable("game.mafia_sector.intro.catastrophe", Component.literal(game.catastrophe().title())
                .withStyle(ChatFormatting.RED)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal(game.catastrophe().description()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("game.mafia_sector.intro.bunker", Component.literal(bunker.title()).withStyle(ChatFormatting.AQUA),
                bunker.area(), bunker.duration()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("game.mafia_sector.intro.supplies", bunker.supplies()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("game.mafia_sector.intro.features", String.join(", ", bunker.features())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("game.mafia_sector.intro.seats", game.seats(), game.participants().size())
                .withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    // ------------------------------------------------------------------ tick

    static void tick(MinecraftServer server, GameSession game) {
        if (server.getTickCount() % LEASH_INTERVAL == 0) {
            keepInPlace(server, game);
        }
        if (game.isPaused()) {
            if (server.getTickCount() % HUD_INTERVAL == 0) game.hud().update(server, game);
            return;
        }
        boolean timeUp = game.tickClock();
        switch (game.phase()) {
            case INTRO -> {
                if (timeUp) startRound(server, game, 1);
            }
            case REVEAL -> {
                Phantoms.onTurnTick(server, game);
                if (timeUp && game.phase() == GamePhase.REVEAL) turnTimeout(server, game);
            }
            case DISCUSSION -> {
                if (timeUp) afterDiscussion(server, game);
            }
            case VOTING, REVOTE -> {
                Phantoms.onVoteTick(server, game);
                if (VoteManager.allVoted(game) && game.phaseTicksLeft() > ALL_VOTED_TICKS) {
                    game.restartClockKeepingPhase(ALL_VOTED_TICKS);
                }
                if (timeUp) finishVote(server, game);
            }
            case JUSTIFY -> {
                if (timeUp) startVote(server, game, true);
            }
            case FINAL -> {
                if (timeUp) Finale.afterFinal(server, game);
            }
            case TRIAL -> Finale.tickTrial(server, game, timeUp);
            case ENDING -> {
                if (timeUp) Finale.end(server);
            }
        }
        if (GameManager.session() == game && server.getTickCount() % HUD_INTERVAL == 0) {
            game.hud().update(server, game);
        }
    }

    /** Admin "next": ends the current turn or phase now, as if its time had run out. */
    static void skip(MinecraftServer server, GameSession game) {
        switch (game.phase()) {
            case INTRO -> startRound(server, game, 1);
            case REVEAL -> turnTimeout(server, game);
            case DISCUSSION -> afterDiscussion(server, game);
            case VOTING, REVOTE -> finishVote(server, game);
            case JUSTIFY -> startVote(server, game, true);
            case FINAL -> Finale.afterFinal(server, game);
            case TRIAL -> Finale.tickTrial(server, game, true);
            case ENDING -> Finale.end(server);
        }
        if (GameManager.session() == game) {
            game.hud().update(server, game);
        }
    }

    // ------------------------------------------------------------------ rounds and turns

    private static void startRound(MinecraftServer server, GameSession game, int round) {
        game.setRound(round);
        game.newRoundModifiers();
        game.votesLeftThisRound = game.exilesInRound(round);
        List<GameSession.Participant> order = new ArrayList<>(game.alive());
        order.sort(Comparator.comparingInt(GameSession.Participant::place));
        game.turnQueue().clear();
        game.missedTurns().clear();
        int shift = order.isEmpty() ? 0 : (round - 1) % order.size();
        for (int i = 0; i < order.size(); i++) {
            game.turnQueue().add(order.get((i + shift) % order.size()).uuid());
        }
        game.enterPhase(GamePhase.REVEAL, ticks(game, Config.TURN_SECONDS.get()));
        Component title = Component.translatable("title.mafia_sector.round", round, game.totalRounds());
        Component sub = Component.translatable(round == 1 ? "title.mafia_sector.round.first" : "title.mafia_sector.round.sub",
                game.votesLeftThisRound);
        GameManager.forEachOnline(server, game, (player, participant) -> {
            place(server, game, player, participant);
            title(player, title, sub, 60);
            player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0F, 1.0F);
        });
        GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.round_start", round, game.totalRounds(),
                game.votesLeftThisRound).withStyle(ChatFormatting.YELLOW));
        nextTurn(server, game);
    }

    /** Hands the turn to the next player in the queue; offline players are skipped and get their turn when back. */
    private static void nextTurn(MinecraftServer server, GameSession game) {
        while (true) {
            UUID next = game.turnQueue().poll();
            if (next == null) {
                next = takeReturnedMissedTurn(game);
            }
            if (next == null) {
                game.setCurrentTurn(null);
                startDiscussion(server, game);
                return;
            }
            GameSession.Participant participant = game.get(next);
            if (participant == null || !participant.isAlive()) {
                continue;
            }
            if (!participant.isOnline()) {
                game.missedTurns().add(next);
                GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.turn_skipped",
                        GameManager.name(participant)).withStyle(ChatFormatting.GRAY));
                continue;
            }
            game.setCurrentTurn(next);
            game.restartClock(ticks(game, Config.TURN_SECONDS.get()));
            startTurn(server, game, participant);
            return;
        }
    }

    @Nullable
    private static UUID takeReturnedMissedTurn(GameSession game) {
        Iterator<UUID> it = game.missedTurns().iterator();
        while (it.hasNext()) {
            GameSession.Participant participant = game.get(it.next());
            if (participant != null && participant.isAlive() && participant.isOnline()) {
                it.remove();
                return participant.uuid();
            }
        }
        return null;
    }

    private static void startTurn(MinecraftServer server, GameSession game, GameSession.Participant participant) {
        GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.turn", GameManager.name(participant))
                .withStyle(ChatFormatting.YELLOW));
        ServerPlayer player = player(server, participant);
        if (player != null) {
            title(player, Component.translatable("title.mafia_sector.your_turn").withStyle(ChatFormatting.GREEN),
                    Component.translatable(game.round() == 1 ? "title.mafia_sector.your_turn.first" : "title.mafia_sector.your_turn.sub"), 40);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1.0F, 1.2F);
        }
        // Round one: everybody shows the profession. With nothing left to show, the turn is just for talking.
        List<CardType> hidden = participant.dossier().hiddenRevealable();
        if (game.round() == 1 && hidden.contains(CardType.PROFESSION)) {
            doReveal(server, game, participant, CardType.PROFESSION);
        } else if (game.round() == 1 || hidden.isEmpty()) {
            game.setRevealedThisTurn();
        }
    }

    /** A player asks to reveal a card on their turn. Returns the reason if it is not allowed. */
    @Nullable
    public static Component reveal(MinecraftServer server, GameSession game, UUID player, CardType type) {
        GameSession.Participant participant = game.get(player);
        if (participant == null || !participant.isAlive()) {
            return Component.translatable("game.mafia_sector.not_in_game");
        }
        if (game.phase() != GamePhase.REVEAL || !player.equals(game.currentTurn())) {
            return Component.translatable("game.mafia_sector.not_your_turn");
        }
        if (game.revealedThisTurn()) {
            return Component.translatable("game.mafia_sector.already_revealed");
        }
        if (!participant.dossier().hiddenRevealable().contains(type)) {
            return Component.translatable("game.mafia_sector.cannot_reveal");
        }
        doReveal(server, game, participant, type);
        return null;
    }

    private static void doReveal(MinecraftServer server, GameSession game, GameSession.Participant participant, CardType type) {
        participant.dossier().reveal(type);
        game.setRevealedThisTurn();
        Card card = participant.dossier().card(type);
        GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.revealed", GameManager.name(participant),
                Component.translatable(type.key()), Component.literal(card.text()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer viewer : GameManager.audience(server, game)) {
            viewer.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    /** "End turn" from the dossier. Only after a card was revealed. */
    @Nullable
    public static Component endTurn(MinecraftServer server, GameSession game, UUID player) {
        if (game.phase() != GamePhase.REVEAL || !player.equals(game.currentTurn())) {
            return Component.translatable("game.mafia_sector.not_your_turn");
        }
        if (!game.revealedThisTurn()) {
            return Component.translatable("game.mafia_sector.reveal_first");
        }
        nextTurn(server, game);
        return null;
    }

    /** Time is up (or the admin skipped): a random hidden card is revealed for whoever did not choose. */
    private static void turnTimeout(MinecraftServer server, GameSession game) {
        UUID current = game.currentTurn();
        GameSession.Participant participant = current != null ? game.get(current) : null;
        if (participant != null && participant.isAlive() && !game.revealedThisTurn()) {
            List<CardType> hidden = participant.dossier().hiddenRevealable();
            if (!hidden.isEmpty()) {
                CardType type = hidden.get(ThreadLocalRandom.current().nextInt(hidden.size()));
                GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.random_reveal", GameManager.name(participant))
                        .withStyle(ChatFormatting.GRAY));
                doReveal(server, game, participant, type);
            }
        }
        nextTurn(server, game);
    }

    /** A participant went offline. Their running turn passes on; they get it back if they return in time. */
    static void onLeft(MinecraftServer server, GameSession game, GameSession.Participant participant) {
        game.markDirty();
        GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.player_left", GameManager.name(participant))
                .withStyle(ChatFormatting.GRAY));
        if (game.phase() == GamePhase.REVEAL && participant.uuid().equals(game.currentTurn())) {
            if (!game.revealedThisTurn()) {
                game.missedTurns().add(participant.uuid());
            }
            nextTurn(server, game);
        }
    }

    private static void startDiscussion(MinecraftServer server, GameSession game) {
        game.enterPhase(GamePhase.DISCUSSION, ticks(game, Config.DISCUSSION_SECONDS.get()));
        announcePhase(server, game, "discussion", SoundEvents.NOTE_BLOCK_CHIME.value());
    }

    private static void afterDiscussion(MinecraftServer server, GameSession game) {
        if (game.votesLeftThisRound > 0) {
            startVote(server, game, false);
        } else {
            endRound(server, game);
        }
    }

    // ------------------------------------------------------------------ votes

    /** A vote, or the revote between the tied players (who stay in {@link GameSession#candidates}). */
    private static void startVote(MinecraftServer server, GameSession game, boolean revote) {
        if (!revote) {
            game.candidates.clear();
        }
        game.votes.clear();
        game.enterPhase(revote ? GamePhase.REVOTE : GamePhase.VOTING, ticks(game, Config.VOTING_SECONDS.get()));
        GameManager.forEachOnline(server, game, (player, participant) -> {
            place(server, game, player, participant);
            if (participant.isAlive()) {
                VoteManager.giveBallot(player, participant);
            }
        });
        announcePhase(server, game, revote ? "revote" : "voting", SoundEvents.NOTE_BLOCK_BELL.value());
        if (revote) {
            GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.revote_between", names(game, game.candidates))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void finishVote(MinecraftServer server, GameSession game) {
        boolean revote = game.phase() == GamePhase.REVOTE;
        GameManager.forEachOnline(server, game, (player, participant) -> VoteManager.takeBallots(player));
        VoteManager.Result result = VoteManager.tally(game);
        VoteManager.announce(server, game, result);
        List<UUID> top = result.top();
        if (top.size() == 1) {
            exile(server, game, game.get(top.getFirst()), ExileReason.VOTED);
            afterVoteSlot(server, game);
            return;
        }
        if (!revote && top.size() > 1) {
            game.candidates.clear();
            game.candidates.addAll(top);
            game.votes.clear();
            game.enterPhase(GamePhase.JUSTIFY, ticks(game, Config.JUSTIFY_SECONDS.get()));
            announcePhase(server, game, "justify", SoundEvents.NOTE_BLOCK_BASEDRUM.value());
            GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.tie", names(game, top)).withStyle(ChatFormatting.YELLOW));
            return;
        }
        // A second tie, or nobody voted at all: nobody leaves now. The exile moves to the next round;
        // in the last round the Sector decides by lot, so the game still ends with the right number of places.
        if (game.round() >= game.totalRounds()) {
            List<UUID> pool = top.isEmpty() ? game.alive().stream().filter(p -> game.isCandidate(p.uuid())
                    && !game.protectedThisRound.contains(p.uuid())).map(GameSession.Participant::uuid).toList() : top;
            if (pool.isEmpty()) {
                pool = game.alive().stream().map(GameSession.Participant::uuid).toList();
            }
            GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.sector_decides").withStyle(ChatFormatting.DARK_RED));
            exile(server, game, game.get(pool.get(ThreadLocalRandom.current().nextInt(pool.size()))), ExileReason.SECTOR);
        } else {
            game.setExilesInRound(game.round() + 1, game.exilesInRound(game.round() + 1) + 1);
            GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.nobody_exiled").withStyle(ChatFormatting.GRAY));
        }
        afterVoteSlot(server, game);
    }

    private static void afterVoteSlot(MinecraftServer server, GameSession game) {
        game.candidates.clear();
        game.votesLeftThisRound--;
        if (checkFinal(server, game)) {
            return;
        }
        if (game.votesLeftThisRound > 0) {
            startVote(server, game, false);
        } else {
            endRound(server, game);
        }
    }

    /** Whoever is still offline at the end of the round counts as exiled; that uses up a later exile. */
    private static void endRound(MinecraftServer server, GameSession game) {
        for (GameSession.Participant participant : List.copyOf(game.alive())) {
            if (!participant.isOnline()) {
                exile(server, game, participant, ExileReason.LEFT);
                consumeLaterExile(game);
            }
        }
        if (checkFinal(server, game)) {
            return;
        }
        int next = game.round() + 1;
        if (next > game.totalRounds()) {
            // Exiles were lost to ties earlier than the last round, or the table was short: one more round.
            game.setExilesInRound(next, game.alive().size() - game.seats());
        }
        startRound(server, game, next);
    }

    private static void consumeLaterExile(GameSession game) {
        for (int r = game.round() + 1; r <= game.totalRounds(); r++) {
            if (game.exilesInRound(r) > 0) {
                game.setExilesInRound(r, game.exilesInRound(r) - 1);
                return;
            }
        }
    }

    private static boolean checkFinal(MinecraftServer server, GameSession game) {
        if (game.alive().size() <= game.seats()) {
            Finale.begin(server, game);
            return true;
        }
        return false;
    }

    /** Sends a participant to the surface: all cards open, signs crossed out, a spyglass to watch from above. */
    static void exile(MinecraftServer server, GameSession game, GameSession.Participant participant, ExileReason reason) {
        if (participant == null || !participant.isAlive()) {
            return;
        }
        participant.exile(game.nextExileOrder(), game.round());
        participant.dossier().revealAll();
        game.candidates.remove(participant.uuid());
        game.markDirty();
        ServerLevel bunker = Bunker7Dimension.get(server);
        if (bunker != null) {
            GameManager.updateNameSigns(bunker, participant);
            Phantoms.moveToSurface(bunker, participant);
        }
        GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.exiled." + reason.name().toLowerCase(java.util.Locale.ROOT),
                GameManager.name(participant)).withStyle(ChatFormatting.RED));
        for (ServerPlayer viewer : GameManager.audience(server, game)) {
            viewer.playNotifySound(SoundEvents.IRON_DOOR_CLOSE, SoundSource.MASTER, 1.0F, 0.6F);
        }
        ServerPlayer player = player(server, participant);
        if (player != null) {
            VoteManager.takeBallots(player);
            player.getInventory().add(new ItemStack(Items.SPYGLASS));
            place(server, game, player, participant);
            title(player, Component.translatable("title.mafia_sector.exiled").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("title.mafia_sector.exiled.sub"), 80);
        }
    }

    // ------------------------------------------------------------------ positions

    /**
     * Puts a participant where the current phase wants them: the own place in the council circle, in front of
     * the own chest in the voting room, by the Reactor in the trial. The exiled stay on the surface.
     * Also used when someone logs back in.
     */
    static void place(MinecraftServer server, GameSession game, ServerPlayer player, GameSession.Participant participant) {
        ServerLevel bunker = Bunker7Dimension.get(server);
        if (bunker == null) {
            return;
        }
        if (!participant.isAlive()) {
            GameManager.teleport(player, bunker, BunkerLayout.SURFACE_SPAWN, BunkerLayout.SURFACE_YAW, 0.0F);
            return;
        }
        switch (game.phase()) {
            case VOTING, JUSTIFY, REVOTE -> {
                BunkerLayout.VoteChest chest = participant.voteChest();
                GameManager.teleport(player, bunker, chest.stand(), chest.yaw(), 0.0F);
            }
            case TRIAL -> {
                BunkerLayout.Seat spot = BunkerLayout.TRIAL_SPOTS.get(participant.place());
                GameManager.teleport(player, bunker, spot.spawn(), spot.yaw(), 0.0F);
            }
            default -> GameManager.sendToSeat(player, bunker, participant.seat());
        }
    }

    /** The room each phase keeps its players in; null = anywhere in the bunker. */
    @Nullable
    private static BoundingBox roomOf(GamePhase phase) {
        return switch (phase) {
            case INTRO, REVEAL, DISCUSSION -> BunkerLayout.COUNCIL_HALL;
            case VOTING, JUSTIFY, REVOTE -> BunkerLayout.VOTING_ROOM;
            case TRIAL -> BunkerLayout.REACTOR_HALL;
            default -> null;
        };
    }

    /** Puts back whoever left their room (or the bunker), and keeps everyone fed: there is no food in the game. */
    private static void keepInPlace(MinecraftServer server, GameSession game) {
        BoundingBox room = roomOf(game.phase());
        GameManager.forEachOnline(server, game, (player, participant) -> {
            player.getFoodData().setFoodLevel(20);
            boolean inBunker = Bunker7Dimension.is(player.level());
            boolean outside = !participant.isAlive()
                    ? !BunkerLayout.isOnSurface(player.position())
                    : room != null && !room.isInside(player.blockPosition());
            if (!inBunker || outside) {
                place(server, game, player, participant);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    static ServerPlayer player(MinecraftServer server, GameSession.Participant participant) {
        return participant.isPhantom() ? null : server.getPlayerList().getPlayer(participant.uuid());
    }

    static MutableComponent names(GameSession game, java.util.Collection<UUID> uuids) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (UUID uuid : uuids) {
            GameSession.Participant participant = game.get(uuid);
            if (participant == null) continue;
            if (!first) text.append(", ");
            text.append(GameManager.name(participant));
            first = false;
        }
        return text;
    }

    private static void announcePhase(MinecraftServer server, GameSession game, String key, SoundEvent sound) {
        Component title = Component.translatable("title.mafia_sector." + key);
        Component sub = Component.translatable("title.mafia_sector." + key + ".sub");
        GameManager.forEachOnline(server, game, (player, participant) -> {
            title(player, title, sub, 50);
            player.playNotifySound(sound, SoundSource.MASTER, 1.0F, 1.0F);
        });
    }

    static void title(ServerPlayer player, Component title, Component subtitle, int stay) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, stay, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }
}
