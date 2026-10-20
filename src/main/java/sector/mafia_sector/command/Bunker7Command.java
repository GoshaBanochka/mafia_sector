package sector.mafia_sector.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import sector.mafia_sector.build.BunkerBuilder;
import sector.mafia_sector.build.BunkerData;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.card.Card;
import sector.mafia_sector.card.CardLibrary;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.dimension.Bunker7Dimension;
import sector.mafia_sector.game.GameException;
import sector.mafia_sector.game.GameHud;
import sector.mafia_sector.game.GameManager;
import sector.mafia_sector.game.GameSession;
import sector.mafia_sector.game.PlayerStash;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Admin command /bunker7 (permission level 2). */
public final class Bunker7Command {
    private Bunker7Command() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bunker7")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("build")
                        .executes(ctx -> build(ctx.getSource(), false))
                        .then(Commands.literal("force")
                                .executes(ctx -> build(ctx.getSource(), true))))
                .then(Commands.literal("tp")
                        .executes(ctx -> tp(ctx.getSource())))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource(), ctx.getSource().getServer().getPlayerList().getPlayers()))
                        .then(Commands.literal("players")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> start(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> start(ctx.getSource(), inRadius(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "blocks")))))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("next")
                        .executes(ctx -> control(ctx.getSource(), "next")))
                .then(Commands.literal("pause")
                        .executes(ctx -> control(ctx.getSource(), "pause")))
                .then(Commands.literal("resume")
                        .executes(ctx -> control(ctx.getSource(), "resume")))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("card")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(participantNames(), builder))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(CardType.values()).map(CardType::id), builder))
                                        .then(Commands.argument("card", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    CardType type = CardType.byId(StringArgumentType.getString(ctx, "type"));
                                                    return SharedSuggestionProvider.suggest(type == null ? List.of()
                                                            : CardLibrary.get().cards(type).stream().map(Card::id).toList(), builder);
                                                })
                                                .executes(ctx -> giveCard(ctx.getSource(), StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "card")))))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on").executes(ctx -> debug(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> debug(ctx.getSource(), false)))
                        .then(Commands.literal("phantoms")
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, GameManager.MAX_PHANTOMS))
                                        .executes(ctx -> phantoms(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    private static List<String> participantNames() {
        GameSession game = GameManager.session();
        return game == null ? List.of() : game.participants().stream().map(p -> StringArgumentType.escapeIfRequired(p.name())).toList();
    }

    private static int build(CommandSourceStack source, boolean force) {
        if (GameManager.isRunning()) {
            source.sendFailure(Component.translatable("game.mafia_sector.already_running"));
            return 0;
        }
        ServerLevel level = Bunker7Dimension.get(source.getServer());
        if (level == null) {
            source.sendFailure(Component.translatable("commands.mafia_sector.no_dimension"));
            return 0;
        }
        BunkerData data = BunkerData.get(source.getServer());
        if (data.isBuilt() && !force) {
            source.sendFailure(Component.translatable(data.isOutdated()
                    ? "commands.mafia_sector.build.outdated" : "commands.mafia_sector.build.already",
                    data.getBuiltVersion(), BunkerLayout.VERSION));
            return 0;
        }
        long start = System.currentTimeMillis();
        data.markBuilt(BunkerBuilder.build(level, data.isBuilt()));
        long took = System.currentTimeMillis() - start;
        source.sendSuccess(() -> Component.translatable("commands.mafia_sector.build.done", took), true);
        return 1;
    }

    private static int tp(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = Bunker7Dimension.get(source.getServer());
        if (level == null) {
            source.sendFailure(Component.translatable("commands.mafia_sector.no_dimension"));
            return 0;
        }
        if (!BunkerData.get(source.getServer()).isBuilt()) {
            source.sendFailure(Component.translatable("commands.mafia_sector.not_built"));
            return 0;
        }
        player.teleportTo(level, BunkerLayout.ADMIN_SPAWN.x, BunkerLayout.ADMIN_SPAWN.y, BunkerLayout.ADMIN_SPAWN.z,
                player.getYRot(), player.getXRot());
        return 1;
    }

    private static List<ServerPlayer> inRadius(CommandSourceStack source, int blocks) {
        double max = (double) blocks * blocks;
        return source.getLevel().players().stream()
                .filter(player -> player.position().distanceToSqr(source.getPosition()) <= max)
                .toList();
    }

    private static int start(CommandSourceStack source, Collection<ServerPlayer> players) {
        try {
            GameManager.start(source.getServer(), players);
        } catch (GameException e) {
            source.sendFailure(e.message());
            return 0;
        }
        GameSession game = GameManager.session();
        int count = game.participants().size();
        source.sendSuccess(() -> Component.translatable("commands.mafia_sector.start.done", count, game.seats(), game.plan().toString()), true);
        return count;
    }

    private static int stop(CommandSourceStack source) {
        try {
            GameManager.stop(source.getServer(), Component.translatable("game.mafia_sector.stopped_by_admin"));
        } catch (GameException e) {
            source.sendFailure(e.message());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.mafia_sector.stop.done"), true);
        return 1;
    }

    private static int control(CommandSourceStack source, String action) {
        try {
            switch (action) {
                case "next" -> GameManager.next(source.getServer());
                case "pause" -> GameManager.setPaused(source.getServer(), true);
                default -> GameManager.setPaused(source.getServer(), false);
            }
        } catch (GameException e) {
            source.sendFailure(e.message());
            return 0;
        }
        GameSession game = GameManager.session();
        Component phase = game != null ? Component.translatable(game.phase().key()) : Component.translatable("commands.mafia_sector.status.idle");
        int round = game != null ? game.round() : 0;
        source.sendSuccess(() -> Component.translatable("commands.mafia_sector." + action + ".done", phase, round), true);
        return 1;
    }

    /** Admins see everything: every player's cards, hidden ones in grey. */
    private static int status(CommandSourceStack source) {
        GameSession game = GameManager.session();
        int pending = PlayerStash.get(source.getServer()).players().size();
        MutableComponent text = Component.translatable("commands.mafia_sector.status.header",
                Component.translatable(game != null ? "commands.mafia_sector.status.running" : "commands.mafia_sector.status.idle"),
                Component.translatable(GameManager.isDebug() ? "options.on" : "options.off"), GameManager.phantoms());
        if (game != null) {
            text.append("\n").append(Component.translatable("commands.mafia_sector.status.phase",
                    game.round(), game.totalRounds(), Component.translatable(game.phase().key()), GameHud.clock(game.phaseTicksLeft()),
                    GameHud.clock((int) game.elapsedTicks()), Component.translatable(game.isPaused() ? "options.on" : "options.off")));
            text.append("\n").append(Component.translatable("commands.mafia_sector.status.setting", game.catastrophe().title(),
                    game.bunker().title(), game.seats(), game.alive().size(), game.plan().toString()));
            for (GameSession.Participant participant : game.participants()) {
                Component state = Component.translatable(!participant.isAlive() ? "commands.mafia_sector.status.exiled"
                        : participant.isPhantom() ? "commands.mafia_sector.status.phantom"
                        : participant.isOnline() ? "commands.mafia_sector.status.online" : "commands.mafia_sector.status.offline")
                        .withStyle(participant.isAlive() && participant.isOnline() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                text.append("\n").append(Component.translatable("commands.mafia_sector.status.player",
                        participant.name(), participant.seat().number(), state));
                MutableComponent cards = Component.literal("   ");
                for (CardType type : CardType.values()) {
                    Card card = participant.dossier().card(type);
                    if (card == null) continue;
                    boolean open = participant.dossier().isRevealed(type);
                    cards.append(Component.literal(card.text()).withStyle(open ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)).append(" | ");
                }
                text.append("\n").append(cards);
            }
        }
        CardLibrary.Contents cards = CardLibrary.get();
        text.append("\n").append(Component.translatable("commands.mafia_sector.status.cards",
                cards.cards().values().stream().mapToInt(List::size).sum(), cards.catastrophes().size(), cards.bunkers().size(), cards.errors().size()));
        cards.errors().forEach(error -> text.append("\n").append(Component.literal(" ! " + error).withStyle(ChatFormatting.RED)));
        text.append("\n").append(Component.translatable("commands.mafia_sector.status.stash", pending));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int giveCard(CommandSourceStack source, String name, String typeId, String cardId) {
        GameSession game = GameManager.session();
        if (game == null) {
            source.sendFailure(Component.translatable("game.mafia_sector.not_running"));
            return 0;
        }
        if (!game.isDebug()) {
            source.sendFailure(Component.translatable("commands.mafia_sector.card.debug_only"));
            return 0;
        }
        GameSession.Participant participant = game.byName(name);
        CardType type = CardType.byId(typeId);
        Card card = type == null ? null : CardLibrary.get().cards(type).stream().filter(c -> c.id().equals(cardId)).findFirst().orElse(null);
        if (participant == null || card == null) {
            source.sendFailure(Component.translatable("commands.mafia_sector.card.unknown"));
            return 0;
        }
        try {
            GameManager.giveCard(source.getServer(), participant, card);
        } catch (GameException e) {
            source.sendFailure(e.message());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.mafia_sector.card.done", participant.name(), Component.translatable(type.key()),
                card.text()), true);
        return 1;
    }

    private static int debug(CommandSourceStack source, boolean on) {
        GameManager.setDebug(on);
        source.sendSuccess(() -> Component.translatable(on ? "commands.mafia_sector.debug.on" : "commands.mafia_sector.debug.off",
                GameManager.phantoms()), true);
        return 1;
    }

    private static int phantoms(CommandSourceStack source, int count) {
        GameManager.setPhantoms(count);
        source.sendSuccess(() -> Component.translatable("commands.mafia_sector.debug.phantoms", count), true);
        return 1;
    }
}
