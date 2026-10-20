package sector.mafia_sector.game;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import sector.mafia_sector.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Top-of-screen HUD made of a vanilla boss bar, so it needs no client code: round, phase, whose turn, timer.
 * Shown to online participants and to anyone else inside the bunker (admins watching).
 */
public final class GameHud {
    private final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);

    public void update(MinecraftServer server, GameSession game) {
        bar.setName(title(game));
        bar.setColor(game.isPaused() ? BossEvent.BossBarColor.WHITE : game.phase().color());
        bar.setProgress(game.phaseTicksTotal() > 0 ? Math.max(0.0F, (float) game.phaseTicksLeft() / game.phaseTicksTotal()) : 0.0F);
        Set<ServerPlayer> viewers = new HashSet<>(GameManager.audience(server, game));
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!viewers.contains(player)) {
                bar.removePlayer(player);
            }
        }
        viewers.forEach(bar::addPlayer);
    }

    private static MutableComponent title(GameSession game) {
        String clock = clock(game.phaseTicksLeft());
        Component phase = Component.translatable(game.phase().key());
        MutableComponent title = switch (game.phase()) {
            case INTRO, FINAL -> Component.translatable("hud.mafia_sector.plain", phase, clock);
            case REVEAL -> {
                UUID turn = game.currentTurn();
                GameSession.Participant current = turn != null ? game.get(turn) : null;
                yield Component.translatable("hud.mafia_sector.turn", game.round(), game.totalRounds(),
                        current != null ? current.name() : "-", clock);
            }
            case VOTING, REVOTE -> Component.translatable("hud.mafia_sector.vote", game.round(), game.totalRounds(), phase,
                    game.votes.size(), game.alive().stream().filter(GameSession.Participant::isOnline).count(), clock);
            case TRIAL -> Component.translatable("hud.mafia_sector.trial", Math.round(game.reactorSpeed()), Config.REACTOR_SPEED.get(),
                    Math.round(game.reactorCapacity()), Math.round(game.reactorNeeded()), clock);
            case ENDING -> Component.translatable("hud.mafia_sector.ending", clock);
            default -> Component.translatable("hud.mafia_sector.round", game.round(), game.totalRounds(), phase, clock);
        };
        if (game.isPaused()) {
            title.append(Component.translatable("hud.mafia_sector.paused"));
        }
        return title;
    }

    public void removePlayer(ServerPlayer player) {
        bar.removePlayer(player);
    }

    public void hide() {
        bar.removeAllPlayers();
    }

    /** mm:ss, rounded up so the bar never shows 00:00 while time is left. */
    public static String clock(int ticks) {
        int seconds = (Math.max(0, ticks) + 19) / 20;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
