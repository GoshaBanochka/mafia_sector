package sector.mafia_sector.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.action.ActionManager;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.client.ClientPayloads;
import sector.mafia_sector.game.GameManager;
import sector.mafia_sector.game.GameSession;
import sector.mafia_sector.game.RoundController;
import sector.mafia_sector.game.VoteManager;

/** Payload registration and the server side of player requests. */
@EventBusSubscriber(modid = Mafia_sector.MODID)
public final class ModNetwork {
    private ModNetwork() {
    }

    @SubscribeEvent
    static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // The lambdas only touch client classes when a payload actually arrives on the client.
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, context) -> ClientPayloads.onSync(payload));
        registrar.playToClient(OpenDossierPayload.TYPE, OpenDossierPayload.STREAM_CODEC, (payload, context) -> ClientPayloads.openDossier());
        registrar.playToClient(OpenBallotPayload.TYPE, OpenBallotPayload.STREAM_CODEC, (payload, context) -> ClientPayloads.openBallot(payload.target().orElse(null)));
        registrar.playToServer(PlayerRequestPayload.TYPE, PlayerRequestPayload.STREAM_CODEC, ModNetwork::onRequest);
    }

    private static void onRequest(PlayerRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        GameSession game = GameManager.session();
        if (game == null || !game.has(player.getUUID())) {
            player.displayClientMessage(Component.translatable("game.mafia_sector.not_in_game").withStyle(ChatFormatting.RED), true);
            return;
        }
        CardType type = request.cardType() >= 0 && request.cardType() < CardType.values().length ? CardType.values()[request.cardType()] : null;
        Component error = switch (request.kind()) {
            case REVEAL -> type == null ? Component.translatable("game.mafia_sector.cannot_reveal")
                    : RoundController.reveal(player.server, game, player.getUUID(), type);
            case END_TURN -> RoundController.endTurn(player.server, game, player.getUUID());
            case USE_ACTION -> ActionManager.use(player.server, game, player.getUUID(), request.target().orElse(null), type);
            case VOTE -> {
                Component refused = VoteManager.cast(player.server, game, player.getUUID(), request.target().map(game::get).orElse(null));
                if (refused == null) {
                    VoteManager.takeBallots(player);
                }
                yield refused;
            }
        };
        if (error != null) {
            player.displayClientMessage(error.copy().withStyle(ChatFormatting.RED), true);
        }
    }
}
