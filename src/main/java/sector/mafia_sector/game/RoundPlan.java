package sector.mafia_sector.game;

import sector.mafia_sector.Config;
import sector.mafia_sector.Mafia_sector;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** How many players leave in which round, so that exactly the number of places is left at the end. */
public final class RoundPlan {
    private RoundPlan() {
    }

    public static int seats(int players) {
        return Math.max(1, players * Config.SEATS_PERCENT.get() / 100);
    }

    /** The plan for this many players: from the config table if it fits, else spread evenly. */
    public static List<Integer> forPlayers(int players, int seats) {
        int exiles = Math.max(0, players - seats);
        for (String line : Config.ROUND_TABLE.get()) {
            List<Integer> parsed = parse(line, players);
            if (parsed == null) continue;
            if (parsed.stream().mapToInt(Integer::intValue).sum() == exiles) {
                return parsed;
            }
            Mafia_sector.LOGGER.warn("Bunker 7: roundTable entry '{}' does not add up to {} exiles, spreading them instead", line, exiles);
        }
        return spread(exiles, Config.ROUNDS.get());
    }

    /** No exiles in round 1 (only professions are known); the rest spread over the others, more towards the end. */
    public static List<Integer> spread(int exiles, int rounds) {
        List<Integer> plan = new ArrayList<>();
        if (rounds <= 1) {
            plan.add(exiles);
            return plan;
        }
        int voting = rounds - 1;
        int base = exiles / voting;
        int extra = exiles % voting;
        plan.add(0);
        for (int r = 2; r <= rounds; r++) {
            plan.add(base + (r > rounds - extra ? 1 : 0));
        }
        return plan;
    }

    public static String format(int players, List<Integer> plan) {
        return players + "=" + plan.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Integer> parse(String line, int players) {
        String[] parts = line.trim().split("=");
        if (parts.length != 2) return null;
        try {
            if (Integer.parseInt(parts[0].trim()) != players) return null;
            List<Integer> plan = new ArrayList<>();
            for (String n : parts[1].split(",")) {
                int value = Integer.parseInt(n.trim());
                if (value < 0) return null;
                plan.add(value);
            }
            return plan.isEmpty() ? null : plan;
        } catch (NumberFormatException e) {
            Mafia_sector.LOGGER.warn("Bunker 7: bad roundTable entry '{}'", line);
            return null;
        }
    }
}
