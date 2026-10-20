package sector.mafia_sector.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.network.SyncPayload;

import java.util.ArrayList;
import java.util.List;

/** Look at a player: their revealed cards appear next to the crosshair. */
final class LookPanel {
    private static final int MAX_WIDTH = 220;

    private LookPanel() {
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        SyncPayload state = ClientPayloads.state();
        Entity target = mc.crosshairPickEntity;
        if (!state.active() || target == null || mc.options.hideGui) {
            return;
        }
        SyncPayload.PlayerView player = state.player(target.getUUID());
        if (player == null) {
            return;
        }
        Font font = mc.font;
        List<Component> lines = new ArrayList<>();
        lines.add(ClientEvents.nameTag(player).copy().withStyle(ChatFormatting.BOLD));
        if (!player.alive()) {
            lines.add(Component.translatable("dossier.mafia_sector.exiled", player.exileOrder()).withStyle(ChatFormatting.RED));
        }
        for (CardType type : CardType.values()) {
            SyncPayload.CardView card = player.card(type.ordinal());
            if (card != null) {
                lines.add(Component.translatable(type.shortKey()).withStyle(ChatFormatting.GRAY).append(" ")
                        .append(Component.literal(card.text()).withStyle(ChatFormatting.WHITE)));
            }
        }
        if (lines.size() == 1) {
            lines.add(Component.translatable("dossier.mafia_sector.nothing_revealed").withStyle(ChatFormatting.DARK_GRAY));
        }
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, Math.min(MAX_WIDTH, font.width(line)));
        }
        int x = graphics.guiWidth() / 2 + 14;
        int y = graphics.guiHeight() / 2 - lines.size() * 5;
        graphics.fill(x - 4, y - 4, x + width + 4, y + lines.size() * 10 + 2, 0xB0101010);
        for (Component line : lines) {
            graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(line, MAX_WIDTH)), x, y, 0xFFFFFF, false);
            y += 10;
        }
    }
}
