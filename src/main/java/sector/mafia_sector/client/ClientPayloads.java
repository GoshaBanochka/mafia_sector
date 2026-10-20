package sector.mafia_sector.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.game.GamePhase;
import sector.mafia_sector.network.SyncPayload;

import java.util.UUID;

/**
 * Client side of the payloads, and the last game state the server sent. Only ever loaded on the client:
 * common code calls it from code paths that run on the client alone.
 */
public final class ClientPayloads {
    private static SyncPayload state = SyncPayload.inactive();
    /** Client tick at which {@link #state} arrived, to count the timer down between updates. */
    private static long receivedAt;
    private static long clientTicks;

    private ClientPayloads() {
    }

    public static SyncPayload state() {
        return state;
    }

    public static void onSync(SyncPayload payload) {
        SyncPayload previous = state;
        state = payload;
        receivedAt = clientTicks;
        Minecraft mc = Minecraft.getInstance();
        boolean vote = payload.active() && GamePhase.byOrdinal(payload.phase()).isVote();
        // The same vote goes on, unless the phase changed or the own vote was reset (the next vote of the round).
        boolean votedBefore = previous.own().map(o -> o.votedFor().isPresent()).orElse(false);
        boolean votedNow = payload.own().map(o -> o.votedFor().isPresent()).orElse(false);
        boolean wasVote = previous.active() && GamePhase.byOrdinal(previous.phase()).isVote() && previous.phase() == payload.phase()
                && !(votedBefore && !votedNow);
        if (mc.screen instanceof DossierScreen screen) {
            if (!payload.active()) screen.onClose();
            else screen.refresh();
        } else if (mc.screen instanceof BallotScreen screen) {
            if (!vote) screen.onClose();
            else screen.refresh();
        }
        // A vote has just begun: hand the ballot to whoever still has to vote, unless they are busy in another screen.
        if (vote && !wasVote && mustVote() && (mc.screen == null || mc.screen instanceof DossierScreen)) {
            mc.setScreen(new BallotScreen(null));
        }
    }

    private static boolean mustVote() {
        SyncPayload.OwnView me = state.own().orElse(null);
        SyncPayload.PlayerView view = me != null ? state.player(me.uuid()) : null;
        return view != null && view.alive() && me.votedFor().isEmpty();
    }

    public static void openDossier() {
        Minecraft mc = Minecraft.getInstance();
        if (state.active() && (mc.screen == null || mc.screen instanceof BallotScreen)) {
            mc.setScreen(new DossierScreen());
        } else if (!state.active() && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("dossier.mafia_sector.no_game"), true);
        }
    }

    /** The ballot screen, with {@code target} already marked if given. */
    public static void openBallot(@Nullable UUID target) {
        Minecraft mc = Minecraft.getInstance();
        if (!state.active() || !GamePhase.byOrdinal(state.phase()).isVote()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("ballot.mafia_sector.closed"), true);
            }
            return;
        }
        mc.setScreen(new BallotScreen(target));
    }

    static void tick() {
        clientTicks++;
    }

    /** Ticks left in the current phase, counted down locally between server updates. */
    static int ticksLeft() {
        return state.paused() ? state.ticksLeft() : (int) Math.max(0, state.ticksLeft() - (clientTicks - receivedAt));
    }

    static void reset() {
        state = SyncPayload.inactive();
    }
}
