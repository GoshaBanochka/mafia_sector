package sector.mafia_sector.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.dimension.Bunker7Dimension;
import sector.mafia_sector.network.SyncPayload;

/** The dossier key, the timer, and players' revealed profession and biology next to their names. */
@EventBusSubscriber(modid = Mafia_sector.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientPayloads.tick();
        while (ClientSetup.DOSSIER_KEY.consumeClick()) {
            ClientPayloads.openDossier();
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPayloads.reset();
    }

    @SubscribeEvent
    static void onNameTag(RenderNameTagEvent event) {
        SyncPayload state = ClientPayloads.state();
        Minecraft mc = Minecraft.getInstance();
        if (!state.active() || mc.level == null || !Bunker7Dimension.is(mc.level)) {
            return;
        }
        SyncPayload.PlayerView player = state.player(event.getEntity().getUUID());
        if (player == null) {
            return;
        }
        event.setContent(nameTag(player));
    }

    /** "Name · Profession · Biology", with what is revealed; the exiled are crossed out. */
    static MutableComponent nameTag(SyncPayload.PlayerView player) {
        MutableComponent text = Component.literal(player.name()).withStyle(player.alive() ? ChatFormatting.WHITE : ChatFormatting.STRIKETHROUGH);
        for (CardType type : new CardType[]{CardType.PROFESSION, CardType.BIOLOGY}) {
            SyncPayload.CardView card = player.card(type.ordinal());
            if (card != null) {
                text.append(Component.literal(" · " + card.text()).withStyle(ChatFormatting.GOLD));
            }
        }
        return text;
    }
}
