package sector.mafia_sector.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import sector.mafia_sector.client.ClientPayloads;

/** The ballot of the running vote: right-click opens the ballot screen. Taken back once the vote is cast. */
public class BallotItem extends Item {
    public BallotItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            // Only reached on the client, so the client class is never loaded on a dedicated server.
            ClientPayloads.openBallot(null);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
