package sector.mafia_sector.game;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The narrow door into the game state for the action package: vote modifiers, messages, the dirty flag. */
public final class GameState {
    private GameState() {
    }

    /** Votes still to come this round, the running one included. */
    public static int votesLeftThisRound(GameSession game) {
        return game.votesLeftThisRound;
    }

    public static Set<UUID> doubleVote(GameSession game) {
        return game.doubleVote;
    }

    public static Set<UUID> immune(GameSession game) {
        return game.immune;
    }

    public static Set<UUID> protectedThisRound(GameSession game) {
        return game.protectedThisRound;
    }

    public static Set<UUID> gagged(GameSession game) {
        return game.gagged;
    }

    public static void markDirty(GameSession game) {
        game.markDirty();
    }

    public static MutableComponent name(GameSession.Participant participant) {
        return GameManager.name(participant);
    }

    public static void broadcast(MinecraftServer server, GameSession game, Component message) {
        GameManager.broadcast(server, game, message);
    }

    public static List<ServerPlayer> audience(MinecraftServer server, GameSession game) {
        return GameManager.audience(server, game);
    }
}
