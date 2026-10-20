package sector.mafia_sector.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sector terminals: each click shows the next document of the Sector archive. */
public final class Consoles {
    /** Number of Sector documents in the lang files (lore.mafia_sector.N.title / .text). */
    public static final int LORE_ENTRIES = 12;
    /** Each player reads the documents in order, so nobody sees the same one twice in a row. */
    private static final Map<UUID, Integer> nextDocument = new HashMap<>();

    private Consoles() {
    }

    public static void readDocument(ServerPlayer player) {
        int index = nextDocument.merge(player.getUUID(), 1, (old, one) -> (old + one) % LORE_ENTRIES);
        int number = Math.floorMod(index - 1, LORE_ENTRIES) + 1;
        player.sendSystemMessage(Component.translatable("lore.mafia_sector.header", number, LORE_ENTRIES,
                Component.translatable("lore.mafia_sector." + number + ".title")).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("lore.mafia_sector." + number + ".text").withStyle(ChatFormatting.GRAY));
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 0.8F);
    }
}
