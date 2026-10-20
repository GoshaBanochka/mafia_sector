package sector.mafia_sector.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import sector.mafia_sector.Config;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.action.ActionManager;
import sector.mafia_sector.action.SpecialAction;
import sector.mafia_sector.block.BunkerLampBlock;
import sector.mafia_sector.build.BunkerData;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.card.Card;
import sector.mafia_sector.card.CardLibrary;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.compat.CreateReactor;
import sector.mafia_sector.game.GameException;
import sector.mafia_sector.game.GameManager;
import sector.mafia_sector.game.GamePhase;
import sector.mafia_sector.game.GameSession;
import sector.mafia_sector.game.GameState;
import sector.mafia_sector.game.RoundController;
import sector.mafia_sector.game.RoundPlan;
import sector.mafia_sector.game.VoteManager;
import sector.mafia_sector.registry.ModBlocks;
import sector.mafia_sector.registry.ModItems;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A whole game driven step by step with the admin "next": dealing, turns, reveals, votes, a tie with a revote,
 * the Sector's lot, special actions, the final, the Reactor trial and the way home. One bot plus phantoms.
 */
@GameTestHolder(Mafia_sector.MODID)
@PrefixGameTestTemplate(false)
public final class RoundTests {
    private RoundTests() {
    }

    @GameTest(template = "empty", batch = "rounds", timeoutTicks = 400)
    public static void fullGame(GameTestHelper helper) throws GameException {
        MinecraftServer server = helper.getLevel().getServer();
        stopLeftovers(server);
        ServerLevel level = TestPlayers.bunker(server);
        GameManager.setDebug(true);
        GameManager.setPhantoms(5);
        ServerPlayer bot = TestPlayers.join(server, level, new GameProfile(UUID.randomUUID(), "round_tester"));
        GameManager.start(server, List.of(bot));
        GameSession game = GameManager.session();
        UUID me = bot.getUUID();

        // Dealing: eight cards each, never the same card twice in a game.
        check(helper, game.participants().size() == 6 && game.seats() == 3, "6 players, 3 places: " + game.seats());
        check(helper, game.plan().stream().mapToInt(Integer::intValue).sum() == 3, "plan exiles 3: " + game.plan());
        for (CardType type : CardType.values()) {
            Set<String> ids = new HashSet<>();
            for (GameSession.Participant p : game.participants()) {
                Card card = p.dossier().card(type);
                check(helper, card != null && ids.add(card.id()), "unique " + type + " card for " + p.name());
            }
        }
        check(helper, bot.getInventory().countItem(ModItems.DOSSIER.get()) == 1, "dossier given");
        check(helper, bot.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "adventure mode");
        check(helper, game.phase() == GamePhase.INTRO, "starts with the intro");

        // Round 1: professions only, revealed automatically at the start of each turn.
        GameManager.next(server);
        check(helper, game.phase() == GamePhase.REVEAL && game.round() == 1, "round 1 reveal");
        playRevealPhase(helper, server, game, me, null);
        check(helper, game.participants().stream().allMatch(p -> p.dossier().isRevealed(CardType.PROFESSION)), "all professions revealed");
        check(helper, game.participants().stream().allMatch(p -> p.dossier().hiddenRevealable().size() == 6), "only professions revealed");
        check(helper, game.phase() == GamePhase.DISCUSSION, "discussion after the turns");
        GameManager.next(server);

        // Round 2: the bot chooses health; others get a random card on timeout.
        check(helper, game.phase() == GamePhase.REVEAL && game.round() == 2, "round 2 reveal, no vote in round 1");
        playRevealPhase(helper, server, game, me, CardType.HEALTH);
        check(helper, game.get(me).dossier().isRevealed(CardType.HEALTH), "bot revealed health");
        check(helper, game.participants().stream().allMatch(p -> p.dossier().hiddenRevealable().size() == 5), "one more card each");
        GameManager.next(server);

        // Round 3: one exile. Everybody against phantom A; A is exiled to the surface with all cards open.
        playRevealPhase(helper, server, game, me, CardType.HOBBY);
        GameManager.next(server);
        check(helper, game.phase() == GamePhase.VOTING, "voting in round 3: " + game.phase());
        check(helper, bot.getInventory().countItem(ModItems.BALLOT.get()) == 1, "ballot given");
        check(helper, bot.position().distanceTo(game.get(me).voteChest().stand()) < 0.1, "bot at its chest");
        List<GameSession.Participant> phantoms = game.participants().stream().filter(GameSession.Participant::isPhantom).toList();
        GameSession.Participant a = phantoms.get(0);
        check(helper, VoteManager.cast(server, game, me, game.get(me)) != null, "no vote against yourself");
        for (GameSession.Participant p : game.alive()) {
            VoteManager.cast(server, game, p.uuid(), p == a ? game.get(me) : a);
        }
        check(helper, VoteManager.cast(server, game, me, a) != null, "only one vote");
        GameManager.next(server);
        check(helper, !a.isAlive() && a.exileOrder() == 1, "A exiled");
        check(helper, a.dossier().hiddenRevealable().isEmpty(), "exiled cards revealed");
        check(helper, bot.getInventory().countItem(ModItems.BALLOT.get()) == 0, "ballot taken back");
        check(helper, game.phase() == GamePhase.REVEAL && game.round() == 4, "round 4 after the exile");

        // Round 4: a double vote, then a tie between B and C, the justification and a revote that B loses.
        GameSession.Participant b = phantoms.get(1);
        GameSession.Participant c = phantoms.get(2);
        GameSession.Participant d = phantoms.get(3);
        GameSession.Participant e = phantoms.get(4);
        giveAction(server, game, game.get(me), SpecialAction.DOUBLE_VOTE);
        check(helper, ActionManager.use(server, game, me, null, null) == null, "double vote used");
        check(helper, ActionManager.use(server, game, me, null, null) != null, "action only once");
        playRevealPhase(helper, server, game, me, CardType.PHOBIA);
        GameManager.next(server);
        VoteManager.cast(server, game, me, b);
        VoteManager.cast(server, game, b.uuid(), c);
        VoteManager.cast(server, game, d.uuid(), c);
        GameManager.next(server);
        check(helper, game.phase() == GamePhase.JUSTIFY && game.candidates().equals(Set.of(b.uuid(), c.uuid())), "tie B-C");
        GameManager.next(server);
        check(helper, game.phase() == GamePhase.REVOTE, "revote");
        check(helper, VoteManager.cast(server, game, me, d) != null, "revote only between the tied");
        VoteManager.cast(server, game, me, b);
        VoteManager.cast(server, game, c.uuid(), b);
        VoteManager.cast(server, game, b.uuid(), c);
        GameManager.next(server);
        check(helper, !b.isAlive() && c.isAlive(), "B lost the revote");

        // Round 5 (the last): two ties in a row, the Sector draws lots between the tied, the doors close.
        playRevealPhase(helper, server, game, me, CardType.BAGGAGE);
        GameManager.next(server);
        VoteManager.cast(server, game, me, d);
        VoteManager.cast(server, game, c.uuid(), e);
        GameManager.next(server);
        GameManager.next(server);
        check(helper, game.phase() == GamePhase.REVOTE, "revote in round 5");
        VoteManager.cast(server, game, me, d);
        VoteManager.cast(server, game, c.uuid(), e);
        GameManager.next(server);
        check(helper, !d.isAlive() ^ !e.isAlive(), "the Sector exiled one of the tied");
        check(helper, game.phase() == GamePhase.FINAL && game.alive().size() == 3, "doors closed with 3 inside: " + game.phase());
        check(helper, game.participants().stream().allMatch(p -> p.dossier().hiddenRevealable().isEmpty()), "all cards open at the end");

        // The trial (with Create): the bot builds in survival, parts in the chest; nobody starts the Reactor, the bunker falls.
        GameManager.next(server);
        if (CreateReactor.isLoaded()) {
            check(helper, game.phase() == GamePhase.TRIAL, "Reactor trial: " + game.phase());
            check(helper, CreateReactor.read(level, BunkerLayout.REACTOR_POS) != null, "Reactor gauge readable");
            check(helper, bot.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "survival in the trial");
            check(helper, level.getBlockEntity(BunkerLayout.PARTS_CHEST.pos()) instanceof Container chest && !chest.isEmpty(), "parts in the chest");
            GameManager.next(server);
            check(helper, game.phase() == GamePhase.ENDING && Boolean.FALSE.equals(game.trialWon()), "trial lost");
            check(helper, lit(level, server, true), "emergency light after the lost trial");
            check(helper, game.phase() == GamePhase.ENDING, "ending: " + game.phase());
            GameManager.next(server);
        }
        check(helper, !GameManager.isRunning(), "game over");
        check(helper, bot.getInventory().countItem(ModItems.DOSSIER.get()) == 0, "dossier gone after the game");
        check(helper, lit(level, server, false), "normal light after the game");
        server.getPlayerList().remove(bot);
        GameManager.setDebug(false);
        helper.succeed();
    }

    /**
     * The game runs on its own clock: phantoms reveal and vote, the bot's turns and votes time out, the doors close,
     * the trial times out and everybody goes home. Timers are cut to the minimum for the test.
     */
    @GameTest(template = "empty", batch = "rounds_clock", timeoutTicks = 4000)
    public static void playsByTheClock(GameTestHelper helper) throws GameException {
        MinecraftServer server = helper.getLevel().getServer();
        stopLeftovers(server);
        ServerLevel level = TestPlayers.bunker(server);
        double scale = Config.DEBUG_TIME_SCALE.get();
        Config.DEBUG_TIME_SCALE.set(0.01);
        GameManager.setDebug(true);
        GameManager.setPhantoms(3);
        ServerPlayer bot = TestPlayers.join(server, level, new GameProfile(UUID.randomUUID(), "clock_tester"));
        GameManager.start(server, List.of(bot));
        GameSession game = GameManager.session();
        Set<GamePhase> seen = new HashSet<>();
        helper.onEachTick(() -> {
            if (GameManager.session() == game) seen.add(game.phase());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!GameManager.isRunning(), "still running: round " + game.round() + " " + game.phase());
            Config.DEBUG_TIME_SCALE.set(scale);
            GameManager.setDebug(false);
            long exiled = game.participants().stream().filter(p -> !p.isAlive()).count();
            helper.assertTrue(exiled == game.participants().size() - game.seats(), "exiled " + exiled);
            helper.assertTrue(seen.containsAll(List.of(GamePhase.REVEAL, GamePhase.DISCUSSION, GamePhase.VOTING, GamePhase.FINAL)), "phases seen: " + seen);
            helper.assertTrue(!CreateReactor.isLoaded() || seen.containsAll(List.of(GamePhase.TRIAL, GamePhase.ENDING)), "trial and ending: " + seen);
            helper.assertTrue(bot.getInventory().countItem(ModItems.DOSSIER.get()) == 0, "bot is home");
            server.getPlayerList().remove(bot);
        });
    }

    /** A player who leaves and does not come back by the end of the round counts as exiled. */
    @GameTest(template = "empty", batch = "rounds_leaver", timeoutTicks = 200)
    public static void leaverIsExiled(GameTestHelper helper) throws GameException {
        MinecraftServer server = helper.getLevel().getServer();
        stopLeftovers(server);
        ServerLevel level = TestPlayers.bunker(server);
        GameManager.setDebug(true);
        GameManager.setPhantoms(3);
        ServerPlayer bot = TestPlayers.join(server, level, new GameProfile(UUID.randomUUID(), "leaver"));
        GameManager.start(server, List.of(bot));
        GameSession game = GameManager.session();
        int planned = game.plan().stream().mapToInt(Integer::intValue).sum();
        GameManager.next(server);
        server.getPlayerList().remove(bot);
        for (int i = 0; i < 20 && game.round() == 1; i++) {
            GameManager.next(server);
        }
        GameSession.Participant participant = game.get(bot.getUUID());
        check(helper, !participant.isAlive(), "leaver exiled at the end of the round");
        check(helper, game.plan().stream().mapToInt(Integer::intValue).sum() == planned - 1, "a later exile is used up: " + game.plan());
        GameManager.stop(server, Component.literal("test"));
        GameManager.setDebug(false);
        helper.succeed();
    }

    /** Every special action, each in a fresh game, used by the bot. */
    @GameTest(template = "empty", batch = "rounds_actions", timeoutTicks = 300)
    public static void specialActions(GameTestHelper helper) throws GameException {
        MinecraftServer server = helper.getLevel().getServer();
        stopLeftovers(server);
        ServerLevel level = TestPlayers.bunker(server);
        GameManager.setDebug(true);
        GameManager.setPhantoms(4);
        ServerPlayer bot = TestPlayers.join(server, level, new GameProfile(UUID.randomUUID(), "actor"));
        UUID me = bot.getUUID();
        for (SpecialAction action : SpecialAction.values()) {
            GameManager.start(server, List.of(bot));
            GameSession game = GameManager.session();
            GameManager.next(server);
            // Vote modifiers need a round with a vote ahead.
            for (int guard = 0; guard < 80 && action.isVoteModifier() && GameState.votesLeftThisRound(game) <= 0; guard++) {
                GameManager.next(server);
            }
            GameSession.Participant self = game.get(me);
            GameSession.Participant other = game.participants().stream().filter(GameSession.Participant::isPhantom).findFirst().orElseThrow();
            giveAction(server, game, self, action);
            Card myHealth = self.dossier().card(CardType.HEALTH);
            Card theirHealth = other.dossier().card(CardType.HEALTH);
            java.util.Map<CardType, Card> before = new java.util.EnumMap<>(CardType.class);
            for (CardType t : CardType.REVEALABLE) before.put(t, self.dossier().card(t));
            CardType type = action == SpecialAction.EXPOSE ? other.dossier().hiddenRevealable().getFirst() : CardType.HEALTH;
            Component error = ActionManager.use(server, game, me, action.needsTarget() ? other.uuid() : null,
                    action.choice() == SpecialAction.Choice.NONE ? null : type);
            check(helper, error == null, action + " works: " + (error != null ? error.getString() : ""));
            check(helper, self.dossier().isActionUsed() && self.dossier().isRevealed(CardType.ACTION), action + " card spent and shown");
            switch (action) {
                case BARTER -> check(helper, self.dossier().card(CardType.HEALTH) == theirHealth && other.dossier().card(CardType.HEALTH) == myHealth
                        && self.dossier().isRevealed(CardType.HEALTH), "barter swapped health");
                case EXPOSE -> check(helper, other.dossier().isRevealed(type), "exposed");
                case DOUBLE_VOTE -> check(helper, GameState.doubleVote(game).contains(me), "double vote");
                case IMMUNITY -> check(helper, GameState.immune(game).contains(me), "immunity");
                case PROTECTION -> check(helper, GameState.protectedThisRound(game).contains(me), "protection");
                case GAG -> check(helper, GameState.gagged(game).contains(other.uuid()), "gag");
                case EPIDEMIC -> check(helper, game.alive().stream().allMatch(p -> p.dossier().isRevealed(CardType.HEALTH)), "epidemic opens health");
                case CUSTOMS -> check(helper, game.alive().stream().allMatch(p -> p.dossier().isRevealed(CardType.BAGGAGE)), "customs opens baggage");
                case SECOND_CHANCE -> check(helper, self.dossier().card(CardType.HEALTH) != myHealth, "new health card");
                case CONFESSION -> check(helper, game.alive().stream().allMatch(p -> p.dossier().isRevealed(CardType.HEALTH)), "confession");
                case DOCTOR -> check(helper, other.dossier().card(CardType.HEALTH) != theirHealth && other.dossier().isRevealed(CardType.HEALTH), "doctor");
                case BLIND_SWAP -> check(helper, CardType.REVEALABLE.stream().anyMatch(t -> before.get(t) != self.dossier().card(t)), "blind swap changed a card");
            }
            GameManager.stop(server, Component.literal("test"));
        }
        // Round plans always add up, for every player count.
        for (int players = 2; players <= BunkerLayout.MAX_PLAYERS; players++) {
            int seats = RoundPlan.seats(players);
            check(helper, RoundPlan.forPlayers(players, seats).stream().mapToInt(Integer::intValue).sum() == players - seats, "plan for " + players);
        }
        check(helper, CardLibrary.get().errors().isEmpty(), "card data has no errors: " + CardLibrary.get().errors());
        server.getPlayerList().remove(bot);
        GameManager.setDebug(false);
        helper.succeed();
    }

    /** Plays the turns of a reveal phase: the bot reveals {@code mine} on its turn, everybody else times out. */
    private static void playRevealPhase(GameTestHelper helper, MinecraftServer server, GameSession game, UUID me, CardType mine) throws GameException {
        for (int guard = 0; guard < 20 && game.phase() == GamePhase.REVEAL; guard++) {
            UUID current = game.currentTurn();
            if (me.equals(current)) {
                if (mine != null) {
                    check(helper, RoundController.endTurn(server, game, me) != null, "cannot end the turn before revealing");
                    check(helper, RoundController.reveal(server, game, me, mine) == null, "bot reveals " + mine);
                    check(helper, RoundController.reveal(server, game, me, CardType.FACT) != null, "one card per turn");
                } else {
                    check(helper, game.get(me).dossier().isRevealed(CardType.PROFESSION), "profession shown on the first turn");
                }
                check(helper, RoundController.endTurn(server, game, me) == null, "bot ends its turn");
            } else {
                check(helper, current == null || RoundController.reveal(server, game, me, CardType.FACT) != null, "not your turn");
                GameManager.next(server);
            }
        }
    }

    /** Gives {@code to} a card with the action, first taking it from a phantom that holds it (it gets a spare one). */
    private static void giveAction(MinecraftServer server, GameSession game, GameSession.Participant to, SpecialAction action) throws GameException {
        List<Card> all = CardLibrary.get().cards(CardType.ACTION);
        Card card = all.stream().filter(c -> c.action() == action).findFirst().orElseThrow();
        for (GameSession.Participant holder : game.participants()) {
            Card held = holder.dossier().card(CardType.ACTION);
            if (holder != to && held != null && held.id().equals(card.id())) {
                Card spare = all.stream().filter(c -> c.action() != action && game.participants().stream()
                        .noneMatch(p -> p.dossier().card(CardType.ACTION).id().equals(c.id()))).findFirst().orElseThrow();
                GameManager.giveCard(server, holder, spare);
            }
        }
        GameManager.giveCard(server, to, card);
    }

    /** A failed test may leave its game running; never start on top of it. */
    static void stopLeftovers(MinecraftServer server) {
        if (GameManager.isRunning()) {
            try {
                GameManager.stop(server, Component.literal("leftover"));
            } catch (GameException ignored) {
            }
        }
    }

    private static boolean lit(ServerLevel level, MinecraftServer server, boolean emergency) {
        for (var pos : BunkerData.get(server).getLamps()) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BunkerLampBlock)) continue;
            if (state.getValue(BunkerLampBlock.LIT) != (state.is(ModBlocks.EMERGENCY_LAMP) == emergency)) return false;
        }
        return true;
    }

    private static void check(GameTestHelper helper, boolean condition, String what) {
        if (!condition) {
            GameManager.setDebug(false);
            try {
                if (GameManager.isRunning()) GameManager.stop(helper.getLevel().getServer(), Component.literal("test failed"));
            } catch (GameException ignored) {
            }
            helper.fail("Failed: " + what);
        }
    }
}
