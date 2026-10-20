package sector.mafia_sector.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.game.GameManager;
import sector.mafia_sector.game.GameSession;
import sector.mafia_sector.game.RoundController;
import sector.mafia_sector.network.OpenDossierPayload;

import java.util.function.BiFunction;

/** Player command /b7: the same as the dossier screen, for those who prefer typing. Replies go to the sender only. */
public final class B7Command {
    private B7Command() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("b7")
                .executes(ctx -> {
                    ctx.getSource().sendSystemMessage(Component.translatable("b7.mafia_sector.help"));
                    return 1;
                })
                .then(Commands.literal("dossier").executes(ctx -> {
                    PacketDistributor.sendToPlayer(ctx.getSource().getPlayerOrException(), OpenDossierPayload.INSTANCE);
                    return 1;
                }))
                .then(Commands.literal("info").executes(ctx -> withGame(ctx, (player, game) -> {
                    RoundController.introLines(game).forEach(player::sendSystemMessage);
                    return null;
                })))
                .then(Commands.literal("reveal")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CardType.REVEALABLE.stream().map(CardType::id), builder))
                                .executes(ctx -> withGame(ctx, (player, game) -> {
                                    CardType type = CardType.byId(StringArgumentType.getString(ctx, "type"));
                                    return type == null ? Component.translatable("game.mafia_sector.cannot_reveal")
                                            : RoundController.reveal(player.server, game, player.getUUID(), type);
                                }))))
                .then(Commands.literal("end").executes(ctx -> withGame(ctx,
                        (player, game) -> RoundController.endTurn(player.server, game, player.getUUID())))));
    }

    /** Runs {@code action} for a participant of the running game; a returned component is an error for the sender. */
    private static int withGame(CommandContext<CommandSourceStack> ctx, BiFunction<ServerPlayer, GameSession, Component> action)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GameSession game = GameManager.session();
        if (game == null || !game.has(player.getUUID())) {
            ctx.getSource().sendFailure(Component.translatable("game.mafia_sector.not_in_game"));
            return 0;
        }
        Component error = action.apply(player, game);
        if (error != null) {
            ctx.getSource().sendFailure(error.copy().withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }
}
