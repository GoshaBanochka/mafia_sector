package sector.mafia_sector.card;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The cards of one game, shuffled per type. A card leaves the deck once and never comes back: no repeats. */
public final class Deck {
    private final Map<CardType, Deque<Card>> piles = new EnumMap<>(CardType.class);

    public Deck(CardLibrary.Contents contents) {
        for (CardType type : CardType.values()) {
            List<Card> cards = new ArrayList<>(contents.cards(type));
            Collections.shuffle(cards);
            piles.put(type, new ArrayDeque<>(cards));
        }
    }

    @Nullable
    public Card draw(CardType type) {
        return piles.get(type).pollFirst();
    }

    public int remaining(CardType type) {
        return piles.get(type).size();
    }

    /** Takes a specific card out of the deck (manual dealing in debug). False if it is not there any more. */
    public boolean take(Card card) {
        return piles.get(card.type()).remove(card);
    }
}
