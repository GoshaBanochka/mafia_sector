package sector.mafia_sector.card;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A player's eight cards and which of them everybody can see. */
public final class Dossier {
    private final Map<CardType, Card> cards = new EnumMap<>(CardType.class);
    private final Set<CardType> revealed = EnumSet.noneOf(CardType.class);
    private boolean actionUsed;

    @Nullable
    public Card card(CardType type) {
        return cards.get(type);
    }

    public void set(CardType type, Card card) {
        cards.put(type, card);
    }

    public boolean isRevealed(CardType type) {
        return revealed.contains(type);
    }

    /** True if the card was hidden until now. */
    public boolean reveal(CardType type) {
        return cards.containsKey(type) && revealed.add(type);
    }

    public void revealAll() {
        revealed.addAll(cards.keySet());
    }

    /** Hidden cards the owner may still reveal on a turn. */
    public List<CardType> hiddenRevealable() {
        return CardType.REVEALABLE.stream().filter(t -> cards.containsKey(t) && !revealed.contains(t)).toList();
    }

    public boolean isActionUsed() {
        return actionUsed;
    }

    public void markActionUsed() {
        actionUsed = true;
        revealed.add(CardType.ACTION);
    }
}
