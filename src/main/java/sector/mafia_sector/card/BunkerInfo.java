package sector.mafia_sector.card;

import java.util.List;

/** The shelter as the game describes it: area in square metres, supplies, how long to stay, what is inside. */
public record BunkerInfo(String id, String title, int area, String supplies, String duration, List<String> features) {
}
