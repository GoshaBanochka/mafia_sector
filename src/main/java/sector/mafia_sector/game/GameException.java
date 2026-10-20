package sector.mafia_sector.game;

import net.minecraft.network.chat.Component;

/** A game action was refused; the message is shown to whoever asked for it. */
public class GameException extends Exception {
    private final Component message;

    public GameException(Component message) {
        super(message.getString());
        this.message = message;
    }

    public Component message() {
        return message;
    }
}
