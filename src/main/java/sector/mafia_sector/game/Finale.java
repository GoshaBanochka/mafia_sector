package sector.mafia_sector.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import sector.mafia_sector.Config;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.build.BunkerBuilder;
import sector.mafia_sector.build.BunkerData;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.card.Card;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.compat.CreateReactor;
import sector.mafia_sector.dimension.Bunker7Dimension;

import java.util.Comparator;
import java.util.List;

/** "The bunker doors are closed": the summary, the optional Reactor trial, and the way home. */
public final class Finale {
    private static final int READ_INTERVAL = 10;

    private Finale() {
    }

    static void begin(MinecraftServer server, GameSession game) {
        game.setCurrentTurn(null);
        game.candidates.clear();
        // With a trial ahead the summary stays up briefly; without one this is the 20 seconds before going home.
        boolean trial = trialAvailable(server, game);
        game.enterPhase(GamePhase.FINAL, RoundController.ticks(game, trial ? Config.FINAL_SECONDS.get() : Config.ENDING_SECONDS.get()));
        for (GameSession.Participant participant : game.participants()) {
            participant.dossier().revealAll();
        }
        game.markDirty();
        Component title = Component.translatable("title.mafia_sector.final").withStyle(ChatFormatting.GOLD);
        Component sub = Component.translatable("title.mafia_sector.final.sub", game.alive().size());
        for (ServerPlayer player : GameManager.audience(server, game)) {
            RoundController.title(player, title, sub, 100);
            player.playNotifySound(SoundEvents.IRON_DOOR_CLOSE, SoundSource.MASTER, 1.0F, 0.5F);
            player.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.MASTER, 0.6F, 0.5F);
        }
        GameManager.forEachOnline(server, game, (player, participant) -> {
            VoteManager.takeBallots(player);
            RoundController.place(server, game, player, participant);
        });
        summary(server, game);
        if (!trial) {
            GameManager.broadcast(server, game, Component.translatable("final.mafia_sector.going_home", Config.ENDING_SECONDS.get())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean trialAvailable(MinecraftServer server, GameSession game) {
        ServerLevel bunker = Bunker7Dimension.get(server);
        return Config.TRIAL_ENABLED.get() && !game.alive().isEmpty() && bunker != null
                && CreateReactor.read(bunker, BunkerLayout.REACTOR_POS) != null;
    }

    /** The full picture in chat: who stayed with all their cards, who left and when. */
    private static void summary(MinecraftServer server, GameSession game) {
        GameManager.broadcast(server, game, Component.translatable("final.mafia_sector.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        GameManager.broadcast(server, game, Component.translatable("final.mafia_sector.setting", game.catastrophe().title(), game.bunker().title())
                .withStyle(ChatFormatting.GRAY));
        GameManager.broadcast(server, game, Component.translatable("final.mafia_sector.survivors", game.alive().size(), game.seats())
                .withStyle(ChatFormatting.GREEN));
        for (GameSession.Participant participant : game.alive()) {
            GameManager.broadcast(server, game, Component.literal(" ").append(GameManager.name(participant)).append(": ").append(cards(participant)));
        }
        List<GameSession.Participant> exiled = game.participants().stream().filter(p -> !p.isAlive())
                .sorted(Comparator.comparingInt(GameSession.Participant::exileOrder)).toList();
        if (!exiled.isEmpty()) {
            GameManager.broadcast(server, game, Component.translatable("final.mafia_sector.exiled").withStyle(ChatFormatting.RED));
            for (GameSession.Participant participant : exiled) {
                GameManager.broadcast(server, game, Component.translatable("final.mafia_sector.exiled.line", participant.exileOrder(),
                        GameManager.name(participant), participant.exileRound()).withStyle(ChatFormatting.GRAY)
                        .append(": ").append(cards(participant)));
            }
        }
    }

    private static MutableComponent cards(GameSession.Participant participant) {
        MutableComponent text = Component.empty().withStyle(ChatFormatting.GRAY);
        boolean first = true;
        for (CardType type : CardType.values()) {
            Card card = participant.dossier().card(type);
            if (card == null) continue;
            if (!first) text.append("; ");
            text.append(Component.literal(card.text()).withStyle(ChatFormatting.WHITE));
            first = false;
        }
        return text;
    }

    static void afterFinal(MinecraftServer server, GameSession game) {
        ServerLevel bunker = Bunker7Dimension.get(server);
        if (trialAvailable(server, game)) {
            startTrial(server, game, bunker);
            return;
        }
        if (Config.TRIAL_ENABLED.get()) {
            Mafia_sector.LOGGER.info("Bunker 7: final trial skipped (Create not loaded or no Reactor gauge at {})", BunkerLayout.REACTOR_POS);
        }
        end(server);
    }

    // ------------------------------------------------------------------ trial

    private static void startTrial(MinecraftServer server, GameSession game, ServerLevel bunker) {
        BunkerBuilder.resetReactorZone(bunker, BunkerData.get(server));
        int survivors = game.alive().size();
        BunkerBuilder.fillPartsChest(bunker, survivors);
        game.reactorNeeded = (float) Config.REACTOR_POWER_PER_SURVIVOR.get() * survivors;
        game.reactorHoldTicks = 0;
        game.trialWon = null;
        game.enterPhase(GamePhase.TRIAL, RoundController.ticks(game, Config.TRIAL_MINUTES.get() * 60));
        Component title = Component.translatable("title.mafia_sector.trial").withStyle(ChatFormatting.RED);
        Component sub = Component.translatable("title.mafia_sector.trial.sub", Config.REACTOR_SPEED.get(), Math.round(game.reactorNeeded));
        GameManager.forEachOnline(server, game, (player, participant) -> {
            if (participant.isAlive()) {
                player.setGameMode(GameType.SURVIVAL);
            }
            RoundController.place(server, game, player, participant);
            RoundController.title(player, title, sub, 80);
        });
        GameManager.broadcast(server, game, Component.translatable("game.mafia_sector.trial_rules", Config.TRIAL_MINUTES.get(),
                Config.REACTOR_SPEED.get(), Math.round(game.reactorNeeded), Config.REACTOR_HOLD_SECONDS.get()).withStyle(ChatFormatting.YELLOW));
    }

    static void tickTrial(MinecraftServer server, GameSession game, boolean timeUp) {
        ServerLevel bunker = Bunker7Dimension.get(server);
        if (bunker != null && game.phaseTicks() % READ_INTERVAL == 0) {
            CreateReactor.Reading reading = CreateReactor.read(bunker, BunkerLayout.REACTOR_POS);
            float speed = reading != null ? Math.abs(reading.speed()) : 0.0F;
            float capacity = reading != null ? reading.capacity() : 0.0F;
            game.reactorSpeed = speed;
            game.reactorCapacity = capacity;
            if (speed >= Config.REACTOR_SPEED.get() && capacity >= game.reactorNeeded) {
                game.reactorHoldTicks += READ_INTERVAL;
            } else {
                game.reactorHoldTicks = 0;
            }
            game.markDirty();
            if (game.reactorHoldTicks >= Config.REACTOR_HOLD_SECONDS.get() * 20) {
                trialOver(server, game, true);
                return;
            }
        }
        if (timeUp) {
            trialOver(server, game, false);
        }
    }

    private static void trialOver(MinecraftServer server, GameSession game, boolean won) {
        game.trialWon = won;
        if (!won) {
            BunkerLighting.set(server, true);
        }
        Component title = Component.translatable(won ? "title.mafia_sector.trial.won" : "title.mafia_sector.trial.lost")
                .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.DARK_RED);
        Component sub = Component.translatable(won ? "title.mafia_sector.trial.won.sub" : "title.mafia_sector.trial.lost.sub");
        for (ServerPlayer player : GameManager.audience(server, game)) {
            RoundController.title(player, title, sub, 100);
            player.playNotifySound(won ? SoundEvents.BEACON_ACTIVATE : SoundEvents.GENERIC_EXPLODE.value(), SoundSource.MASTER, 1.0F, won ? 1.0F : 0.6F);
        }
        GameManager.broadcast(server, game, Component.translatable(won ? "final.mafia_sector.trial.won" : "final.mafia_sector.trial.lost")
                .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED));
        GameManager.forEachOnline(server, game, (player, participant) -> player.setGameMode(GameType.ADVENTURE));
        ending(server, game);
    }

    // ------------------------------------------------------------------ home

    private static void ending(MinecraftServer server, GameSession game) {
        game.enterPhase(GamePhase.ENDING, RoundController.ticks(game, Config.ENDING_SECONDS.get()));
        GameManager.broadcast(server, game, Component.translatable("final.mafia_sector.going_home", Config.ENDING_SECONDS.get())
                .withStyle(ChatFormatting.GRAY));
    }

    static void end(MinecraftServer server) {
        try {
            GameManager.stop(server, Component.translatable("game.mafia_sector.finished"));
        } catch (sector.mafia_sector.game.GameException ignored) {
            // Already stopped.
        }
    }
}
