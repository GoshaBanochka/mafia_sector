package sector.mafia_sector.card;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The eight cards every player gets, one of each type. Order = order on the board and in the dossier. */
public enum CardType {
    PROFESSION,
    HEALTH,
    BIOLOGY,
    HOBBY,
    PHOBIA,
    BAGGAGE,
    FACT,
    /** The special action: never revealed by choice, only by using it. */
    ACTION;

    /** Types a player can reveal on their turn. */
    public static final List<CardType> REVEALABLE = Arrays.stream(values()).filter(t -> t != ACTION).toList();

    /** File name in data/bunker7/cards/ and command argument. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Full name, e.g. "Профессия". */
    public String key() {
        return "card.mafia_sector.type." + id();
    }

    /** Short label for the board, e.g. "Проф.". */
    public String shortKey() {
        return "card.mafia_sector.short." + id();
    }

    @Nullable
    public static CardType byId(String id) {
        for (CardType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
