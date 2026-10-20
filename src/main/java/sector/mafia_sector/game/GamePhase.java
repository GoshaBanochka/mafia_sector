package sector.mafia_sector.game;

import net.minecraft.world.BossEvent;

import java.util.Locale;

/**
 * INTRO, then rounds of REVEAL (turns) -> DISCUSSION -> VOTING [-> JUSTIFY -> REVOTE], then FINAL [-> TRIAL] -> ENDING.
 */
public enum GamePhase {
    INTRO(BossEvent.BossBarColor.WHITE),
    REVEAL(BossEvent.BossBarColor.YELLOW),
    DISCUSSION(BossEvent.BossBarColor.GREEN),
    VOTING(BossEvent.BossBarColor.BLUE),
    JUSTIFY(BossEvent.BossBarColor.PINK),
    REVOTE(BossEvent.BossBarColor.BLUE),
    FINAL(BossEvent.BossBarColor.WHITE),
    TRIAL(BossEvent.BossBarColor.RED),
    ENDING(BossEvent.BossBarColor.WHITE);

    private final BossEvent.BossBarColor color;

    GamePhase(BossEvent.BossBarColor color) {
        this.color = color;
    }

    public BossEvent.BossBarColor color() {
        return color;
    }

    public boolean isVote() {
        return this == VOTING || this == REVOTE;
    }

    /** Phases of a round, in which players play: reveal, talk, vote, use their actions. */
    public boolean isRound() {
        return this == REVEAL || this == DISCUSSION || this == VOTING || this == JUSTIFY || this == REVOTE;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Lang key, e.g. phase.mafia_sector.reveal. */
    public String key() {
        return "phase.mafia_sector." + id();
    }

    public static GamePhase byOrdinal(int ordinal) {
        GamePhase[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INTRO;
    }
}
