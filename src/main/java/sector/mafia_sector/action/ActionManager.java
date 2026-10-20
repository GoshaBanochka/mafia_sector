package sector.mafia_sector.action;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.card.Card;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.card.Dossier;
import sector.mafia_sector.game.GameSession;
import sector.mafia_sector.game.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Uses a player's special action card. Every check happens here on the server; the dossier screen only asks.
 * The card is revealed and spent, and the result is announced to everyone.
 */
public final class ActionManager {
    private ActionManager() {
    }

    /** Returns the reason if the action cannot be used now, null on success. */
    @Nullable
    public static Component use(MinecraftServer server, GameSession game, UUID userId, @Nullable UUID targetId, @Nullable CardType type) {
        GameSession.Participant user = game.get(userId);
        if (user == null || !user.isAlive()) {
            return Component.translatable("action.mafia_sector.error.not_in_game");
        }
        Dossier dossier = user.dossier();
        Card card = dossier.card(CardType.ACTION);
        if (card == null || card.action() == null) {
            return Component.translatable("action.mafia_sector.error.no_card");
        }
        if (dossier.isActionUsed()) {
            return Component.translatable("action.mafia_sector.error.used");
        }
        if (!game.phase().isRound()) {
            return Component.translatable("action.mafia_sector.error.phase");
        }
        SpecialAction action = card.action();
        if (action.isVoteModifier() && GameState.votesLeftThisRound(game) <= 0) {
            return Component.translatable("action.mafia_sector.error.no_votes");
        }
        GameSession.Participant target = targetId != null ? game.get(targetId) : null;
        if (action.needsTarget() && (target == null || !target.isAlive() || target == user)) {
            return Component.translatable("action.mafia_sector.error.target");
        }
        switch (action.choice()) {
            case CARD_TYPE -> {
                if (type == null || type == CardType.ACTION) return Component.translatable("action.mafia_sector.error.type");
            }
            case TARGET_HIDDEN -> {
                if (type == null || target == null || !target.dossier().hiddenRevealable().contains(type)) {
                    return Component.translatable("action.mafia_sector.error.hidden");
                }
            }
            case NONE -> {
            }
        }
        MutableComponent result = apply(game, action, user, target, type);
        if (result == null) {
            return Component.translatable("action.mafia_sector.error.deck_empty");
        }
        dossier.markActionUsed();
        GameState.markDirty(game);
        MutableComponent message = Component.translatable("action.mafia_sector.used", GameState.name(user),
                Component.literal(card.text()).withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.LIGHT_PURPLE);
        GameState.broadcast(server, game, message);
        GameState.broadcast(server, game, result.withStyle(ChatFormatting.WHITE));
        for (ServerPlayer viewer : GameState.audience(server, game)) {
            viewer.playNotifySound(SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.MASTER, 0.8F, 1.0F);
        }
        return null;
    }

    /** Applies the effect and describes it for everyone; null if the deck ran out. */
    @Nullable
    private static MutableComponent apply(GameSession game, SpecialAction action, GameSession.Participant user,
                                          @Nullable GameSession.Participant target, @Nullable CardType type) {
        return switch (action) {
            case BARTER -> swap(user, target, type);
            case BLIND_SWAP -> swap(user, target, CardType.REVEALABLE.get(ThreadLocalRandom.current().nextInt(CardType.REVEALABLE.size())));
            case EXPOSE -> {
                target.dossier().reveal(type);
                yield Component.translatable("action.mafia_sector.expose.result", GameState.name(target), Component.translatable(type.key()),
                        cardText(target, type));
            }
            case DOUBLE_VOTE -> {
                GameState.doubleVote(game).add(user.uuid());
                yield Component.translatable("action.mafia_sector.double_vote.result", GameState.name(user));
            }
            case IMMUNITY -> {
                GameState.immune(game).add(user.uuid());
                yield Component.translatable("action.mafia_sector.immunity.result", GameState.name(user));
            }
            case PROTECTION -> {
                GameState.protectedThisRound(game).add(user.uuid());
                yield Component.translatable("action.mafia_sector.protection.result", GameState.name(user));
            }
            case GAG -> {
                GameState.gagged(game).add(target.uuid());
                yield Component.translatable("action.mafia_sector.gag.result", GameState.name(target));
            }
            case EPIDEMIC -> redeal(game, CardType.HEALTH, "action.mafia_sector.epidemic.result");
            case CUSTOMS -> redeal(game, CardType.BAGGAGE, "action.mafia_sector.customs.result");
            case SECOND_CHANCE -> {
                Card fresh = game.deck().draw(type);
                if (fresh == null) yield null;
                user.dossier().set(type, fresh);
                yield user.dossier().isRevealed(type)
                        ? Component.translatable("action.mafia_sector.second_chance.result.open", GameState.name(user), Component.translatable(type.key()),
                        Component.literal(fresh.text()))
                        : Component.translatable("action.mafia_sector.second_chance.result", GameState.name(user), Component.translatable(type.key()));
            }
            case CONFESSION -> {
                MutableComponent text = Component.translatable("action.mafia_sector.confession.result", Component.translatable(type.key()));
                for (GameSession.Participant p : game.alive()) {
                    p.dossier().reveal(type);
                    text.append("\n ").append(GameState.name(p)).append(": ").append(cardText(p, type));
                }
                yield text;
            }
            case DOCTOR -> {
                Card fresh = game.deck().draw(CardType.HEALTH);
                if (fresh == null) yield null;
                target.dossier().set(CardType.HEALTH, fresh);
                target.dossier().reveal(CardType.HEALTH);
                yield Component.translatable("action.mafia_sector.doctor.result", GameState.name(target), Component.literal(fresh.text()));
            }
        };
    }

    /** Both cards change hands and are open afterwards. */
    private static MutableComponent swap(GameSession.Participant user, GameSession.Participant target, CardType type) {
        Card mine = user.dossier().card(type);
        Card theirs = target.dossier().card(type);
        user.dossier().set(type, theirs);
        target.dossier().set(type, mine);
        user.dossier().reveal(type);
        target.dossier().reveal(type);
        return Component.translatable("action.mafia_sector.swap.result", GameState.name(user), GameState.name(target),
                Component.translatable(type.key()), GameState.name(user), cardText(user, type), GameState.name(target), cardText(target, type));
    }

    /** The cards of one type of everyone in the bunker are shuffled and dealt again, face up. */
    private static MutableComponent redeal(GameSession game, CardType type, String key) {
        List<GameSession.Participant> alive = game.alive();
        List<Card> cards = new ArrayList<>();
        alive.forEach(p -> cards.add(p.dossier().card(type)));
        Collections.shuffle(cards);
        MutableComponent text = Component.translatable(key);
        for (int i = 0; i < alive.size(); i++) {
            GameSession.Participant p = alive.get(i);
            p.dossier().set(type, cards.get(i));
            p.dossier().reveal(type);
            text.append("\n ").append(GameState.name(p)).append(": ").append(cardText(p, type));
        }
        return text;
    }

    private static Component cardText(GameSession.Participant participant, CardType type) {
        Card card = participant.dossier().card(type);
        return Component.literal(card != null ? card.text() : "-").withStyle(ChatFormatting.WHITE);
    }
}
