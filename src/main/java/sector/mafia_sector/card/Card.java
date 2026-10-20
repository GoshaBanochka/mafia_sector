package sector.mafia_sector.card;

import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.action.SpecialAction;

/**
 * One card from data/bunker7/cards/&lt;type&gt;.json. {@code text} is short (it goes on the board), {@code detail}
 * is the longer explanation shown in the dossier. Special action cards name the {@link SpecialAction} they trigger.
 */
public record Card(String id, CardType type, String text, String detail, @Nullable SpecialAction action) {
}
