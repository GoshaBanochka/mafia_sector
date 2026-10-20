package sector.mafia_sector.action;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The one-off special actions. A card in data/bunker7/cards/action.json names one of them by {@link #id()}.
 * Effects happen at once when used ({@link ActionManager}); vote modifiers last until the end of the round.
 */
public enum SpecialAction {
    /** Swap a card of the chosen type with the chosen player. */
    BARTER(true, Choice.CARD_TYPE, false),
    /** Swap a random card with the chosen player. */
    BLIND_SWAP(true, Choice.NONE, false),
    /** Reveal any hidden card of the chosen player. */
    EXPOSE(true, Choice.TARGET_HIDDEN, false),
    /** Your vote counts twice in this round. */
    DOUBLE_VOTE(false, Choice.NONE, true),
    /** Votes against you in this round do not count. */
    IMMUNITY(false, Choice.NONE, true),
    /** Health cards of everyone in the bunker are shuffled and dealt again. */
    EPIDEMIC(false, Choice.NONE, false),
    /** You cannot be exiled in this round. */
    PROTECTION(false, Choice.NONE, true),
    /** Replace one of your cards with a new one from the deck. */
    SECOND_CHANCE(false, Choice.CARD_TYPE, false),
    /** Baggage cards of everyone in the bunker are shuffled and dealt again. */
    CUSTOMS(false, Choice.NONE, false),
    /** The chosen player's vote does not count in this round. */
    GAG(true, Choice.NONE, true),
    /** Everyone in the bunker reveals the card of the chosen type. */
    CONFESSION(false, Choice.CARD_TYPE, false),
    /** The chosen player's health card is replaced with a new one from the deck. */
    DOCTOR(true, Choice.NONE, false);

    /** What the user picks besides the target player. */
    public enum Choice { NONE, CARD_TYPE, TARGET_HIDDEN }

    private final boolean needsTarget;
    private final Choice choice;
    private final boolean voteModifier;

    SpecialAction(boolean needsTarget, Choice choice, boolean voteModifier) {
        this.needsTarget = needsTarget;
        this.choice = choice;
        this.voteModifier = voteModifier;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean needsTarget() {
        return needsTarget;
    }

    public Choice choice() {
        return choice;
    }

    /** Works on this round's votes, so it is pointless once the round's last vote is over. */
    public boolean isVoteModifier() {
        return voteModifier;
    }

    public String key() {
        return "action.mafia_sector." + id();
    }

    @Nullable
    public static SpecialAction byId(String id) {
        for (SpecialAction action : values()) {
            if (action.id().equals(id)) {
                return action;
            }
        }
        return null;
    }
}
