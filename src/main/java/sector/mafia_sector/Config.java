package sector.mafia_sector;

import net.neoforged.neoforge.common.ModConfigSpec;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.game.RoundPlan;

import java.util.ArrayList;
import java.util.List;

/** Server config: serverconfig/mafia_sector-server.toml in the world folder. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("game");
    }

    public static final ModConfigSpec.IntValue MIN_PLAYERS = BUILDER
            .comment("Fewest players needed to start a game. Debug mode ignores it.")
            .defineInRange("minPlayers", 4, 1, BunkerLayout.MAX_PLAYERS);
    public static final ModConfigSpec.IntValue SEATS_PERCENT = BUILDER
            .comment("Places in the bunker, in percent of the players, rounded down (at least 1). 50 = half, as in the board game.")
            .defineInRange("seatsPercent", 50, 1, 99);
    public static final ModConfigSpec.IntValue ROUNDS = BUILDER
            .comment("Rounds for player counts missing from roundTable. Exiles are spread over rounds 2..N, more in the later ones.")
            .defineInRange("rounds", 5, 1, 7);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ROUND_TABLE = BUILDER
            .comment("Exiles per round by player count: \"players=round1,round2,...\". The numbers must add up to",
                    "players - places; otherwise (or if the count is missing) the game spreads the exiles itself.")
            .defineListAllowEmpty("roundTable", defaultTable(), () -> "10=0,1,1,1,2", o -> o instanceof String);

    static {
        BUILDER.pop().push("timers");
    }

    public static final ModConfigSpec.IntValue INTRO_SECONDS = BUILDER
            .comment("Opening: the catastrophe and the bunker are announced.")
            .defineInRange("introSeconds", 15, 3, 600);
    public static final ModConfigSpec.IntValue TURN_SECONDS = BUILDER
            .comment("One player's turn in the reveal phase. If no card was chosen in time, a random one is revealed.")
            .defineInRange("turnSeconds", 90, 5, 600);
    public static final ModConfigSpec.IntValue DISCUSSION_SECONDS = BUILDER
            .defineInRange("discussionSeconds", 300, 5, 3600);
    public static final ModConfigSpec.IntValue VOTING_SECONDS = BUILDER
            .defineInRange("votingSeconds", 60, 5, 600);
    public static final ModConfigSpec.IntValue JUSTIFY_SECONDS = BUILDER
            .comment("After a tie: time for the tied players to speak before the revote.")
            .defineInRange("justifySeconds", 30, 5, 600);
    public static final ModConfigSpec.IntValue FINAL_SECONDS = BUILDER
            .comment("\"The bunker doors are closed\": the summary stays on screen this long.")
            .defineInRange("finalSeconds", 15, 3, 600);
    public static final ModConfigSpec.IntValue ENDING_SECONDS = BUILDER
            .comment("After the game, everyone goes home after this many seconds.")
            .defineInRange("endingSeconds", 20, 3, 600);
    public static final ModConfigSpec.DoubleValue DEBUG_TIME_SCALE = BUILDER
            .comment("Debug mode: every timer is multiplied by this. 1.0 = the same timers as a real game; 0.2 = five times faster.")
            .defineInRange("debugTimeScale", 1.0, 0.01, 1.0);

    static {
        BUILDER.pop().push("finalTrial");
    }

    public static final ModConfigSpec.BooleanValue TRIAL_ENABLED = BUILDER
            .comment("After the doors close, the survivors must start the Reactor with Create machines. Needs Create.")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue TRIAL_MINUTES = BUILDER
            .defineInRange("minutes", 5, 1, 60);
    public static final ModConfigSpec.IntValue REACTOR_SPEED = BUILDER
            .comment("Rotation speed the Reactor needs, RPM (either direction).")
            .defineInRange("reactorSpeed", 32, 1, 256);
    public static final ModConfigSpec.IntValue REACTOR_POWER_PER_SURVIVOR = BUILDER
            .comment("Stress capacity (SU) the Reactor needs per survivor. A Create hand crank gives 256.")
            .defineInRange("reactorPowerPerSurvivor", 256, 0, 100000);
    public static final ModConfigSpec.IntValue REACTOR_HOLD_SECONDS = BUILDER
            .comment("The Reactor starts once the speed and power are held this long without a break.")
            .defineInRange("reactorHoldSeconds", 10, 1, 600);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    private static List<String> defaultTable() {
        List<String> table = new ArrayList<>();
        for (int players = 4; players <= BunkerLayout.MAX_PLAYERS; players++) {
            table.add(RoundPlan.format(players, RoundPlan.spread(players - Math.max(1, players / 2), 5)));
        }
        return table;
    }
}
