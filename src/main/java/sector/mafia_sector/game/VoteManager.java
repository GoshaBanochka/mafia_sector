package sector.mafia_sector.game;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.registry.ModItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ballots and the count. Every player in the bunker gets a ballot with their name; it opens the ballot screen, where
 * they mark the one to exile. Votes are secret until the count; then everyone sees who voted for whom.
 */
public final class VoteManager {
    private static final String VOTER = "voter";

    /** Votes per player after special actions; {@code top} are the ones with the most votes (several = a tie). */
    public record Result(Map<UUID, Integer> counts, List<UUID> top, int topVotes) {
    }

    private VoteManager() {
    }

    // ------------------------------------------------------------------ ballots

    public static ItemStack ballot(GameSession.Participant voter) {
        ItemStack stack = new ItemStack(ModItems.BALLOT.get());
        CompoundTag tag = new CompoundTag();
        tag.putUUID(VOTER, voter.uuid());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.mafia_sector.ballot.of", voter.name()));
        return stack;
    }

    static void giveBallot(ServerPlayer player, GameSession.Participant participant) {
        takeBallots(player);
        if (!player.getInventory().add(ballot(participant))) {
            player.getInventory().setItem(1, ballot(participant));
        }
    }

    /** Removes every ballot from the inventory, the cursor included. */
    public static void takeBallots(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.BALLOT.get())) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (player.containerMenu.getCarried().is(ModItems.BALLOT.get())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ voting

    /** {@code voter} votes to exile {@code target}. Returns the reason if the vote is refused; votes cannot be changed. */
    @Nullable
    public static Component cast(MinecraftServer server, GameSession game, UUID voter, @Nullable GameSession.Participant target) {
        GameSession.Participant participant = game.get(voter);
        if (participant == null || !participant.isAlive()) {
            return Component.translatable("vote.mafia_sector.not_voter");
        }
        if (!game.phase().isVote()) {
            return Component.translatable("vote.mafia_sector.closed");
        }
        if (game.hasVoted(voter)) {
            return Component.translatable("vote.mafia_sector.already");
        }
        if (target == null || !target.isAlive()) {
            return Component.translatable("vote.mafia_sector.sealed");
        }
        if (target.uuid().equals(voter)) {
            return Component.translatable("vote.mafia_sector.self");
        }
        if (!game.isCandidate(target.uuid())) {
            return Component.translatable("vote.mafia_sector.not_candidate");
        }
        game.votes.put(voter, target.uuid());
        game.markDirty();
        ServerPlayer player = RoundController.player(server, participant);
        if (player != null) {
            player.displayClientMessage(Component.translatable("vote.mafia_sector.accepted", GameManager.name(target)).withStyle(ChatFormatting.GREEN), true);
            player.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return null;
    }

    /** Everybody who can vote (in the bunker and online) has voted. */
    static boolean allVoted(GameSession game) {
        boolean any = false;
        for (GameSession.Participant participant : game.alive()) {
            if (!participant.isOnline()) continue;
            if (!game.hasVoted(participant.uuid())) return false;
            any = true;
        }
        return any;
    }

    /** The count after special actions: gagged voters and votes against the immune do not count, doubled ones count twice. */
    static Result tally(GameSession game) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<UUID, UUID> vote : game.votes.entrySet()) {
            UUID voter = vote.getKey();
            UUID target = vote.getValue();
            if (game.gagged.contains(voter) || game.immune.contains(target) || game.protectedThisRound.contains(target)
                    || !game.isCandidate(target)) {
                continue;
            }
            counts.merge(target, game.doubleVote.contains(voter) ? 2 : 1, Integer::sum);
        }
        int best = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<UUID> top = new ArrayList<>();
        if (best > 0) {
            counts.forEach((uuid, count) -> {
                if (count == best) top.add(uuid);
            });
        }
        return new Result(counts, top, best);
    }

    /** The open part of the vote: who voted for whom, and the totals. */
    static void announce(MinecraftServer server, GameSession game, Result result) {
        GameManager.broadcast(server, game, Component.translatable("vote.mafia_sector.results").withStyle(ChatFormatting.AQUA));
        if (game.votes.isEmpty()) {
            GameManager.broadcast(server, game, Component.translatable("vote.mafia_sector.no_votes").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (Map.Entry<UUID, UUID> vote : game.votes.entrySet()) {
            GameSession.Participant voter = game.get(vote.getKey());
            GameSession.Participant target = game.get(vote.getValue());
            if (voter == null || target == null) continue;
            MutableComponent line = Component.translatable("vote.mafia_sector.line", GameManager.name(voter), GameManager.name(target))
                    .withStyle(ChatFormatting.GRAY);
            if (game.gagged.contains(voter.uuid())) line.append(Component.translatable("vote.mafia_sector.gagged").withStyle(ChatFormatting.DARK_GRAY));
            else if (game.immune.contains(target.uuid())) line.append(Component.translatable("vote.mafia_sector.immune").withStyle(ChatFormatting.DARK_GRAY));
            else if (game.protectedThisRound.contains(target.uuid())) line.append(Component.translatable("vote.mafia_sector.protected").withStyle(ChatFormatting.DARK_GRAY));
            else if (game.doubleVote.contains(voter.uuid())) line.append(Component.translatable("vote.mafia_sector.double").withStyle(ChatFormatting.LIGHT_PURPLE));
            GameManager.broadcast(server, game, line);
        }
        MutableComponent totals = Component.translatable("vote.mafia_sector.totals").withStyle(ChatFormatting.GRAY);
        result.counts().entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).forEach(entry -> {
            GameSession.Participant target = game.get(entry.getKey());
            if (target != null) {
                totals.append(" ").append(GameManager.name(target)).append(Component.literal(" " + entry.getValue()).withStyle(ChatFormatting.WHITE));
            }
        });
        GameManager.broadcast(server, game, totals);
    }
}
