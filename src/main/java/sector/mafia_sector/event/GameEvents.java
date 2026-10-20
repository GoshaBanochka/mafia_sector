package sector.mafia_sector.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.dimension.Bunker7Dimension;
import sector.mafia_sector.game.GameManager;
import sector.mafia_sector.game.GameSession;
import sector.mafia_sector.network.OpenBallotPayload;
import sector.mafia_sector.registry.ModItems;

import java.util.Optional;

/** The ballot chests, and the game's items that must stay with their owner. */
@EventBusSubscriber(modid = Mafia_sector.MODID)
public final class GameEvents {
    private GameEvents() {
    }

    /**
     * Ballot chests never open. During a vote a click opens the ballot screen with the chest's owner marked;
     * otherwise it tells whose chest it is. Runs before the protection handler, which then sees it cancelled.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Bunker7Dimension.is(event.getLevel())) {
            return;
        }
        int place = chestAt(event.getPos());
        if (place < 0) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GameSession game = GameManager.session();
        GameSession.Participant owner = game == null ? null : game.participants().stream()
                .filter(p -> p.place() == place).findFirst().orElse(null);
        GameSession.Participant voter = game == null ? null : game.get(player.getUUID());
        if (voter != null && voter.isAlive() && game.phase().isVote() && !game.hasVoted(voter.uuid())) {
            PacketDistributor.sendToPlayer(player, new OpenBallotPayload(Optional.ofNullable(owner).map(GameSession.Participant::uuid)));
            return;
        }
        player.displayClientMessage(owner == null ? Component.translatable("vote.mafia_sector.chest_free")
                : Component.translatable("vote.mafia_sector.chest_of", owner.name()), true);
    }

    private static int chestAt(BlockPos pos) {
        for (BunkerLayout.VoteChest chest : BunkerLayout.VOTE_CHESTS) {
            if (chest.pos().equals(pos)) {
                return chest.number() - 1;
            }
        }
        return -1;
    }

    /** The dossier, ballots and the spyglass of the exiled cannot be thrown away; they come straight back. */
    @SubscribeEvent
    static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!GameManager.isParticipant(event.getPlayer().getUUID())) {
            return;
        }
        if (stack.is(ModItems.DOSSIER.get()) || stack.is(ModItems.BALLOT.get()) || stack.is(Items.SPYGLASS)) {
            event.setCanceled(true);
            event.getPlayer().getInventory().add(stack.copy());
        }
    }
}
