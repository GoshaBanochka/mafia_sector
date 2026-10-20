package sector.mafia_sector.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.block.ConsoleBlock;
import sector.mafia_sector.build.BunkerData;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.dimension.Bunker7Dimension;
import sector.mafia_sector.registry.ModBlocks;

/**
 * Nothing in bunker7 can be broken, placed or reshaped except inside the reactor zone (the final trial).
 * Applies to everyone, creative admins included: to change the bunker, use /bunker7 build force.
 * The "only survivors, only during the trial" rule is layered on top of this by the final stage.
 */
@EventBusSubscriber(modid = Mafia_sector.MODID)
public final class ProtectionEvents {
    private static final ResourceLocation CREATE_WRENCH = ResourceLocation.fromNamespaceAndPath("create", "wrench");

    private ProtectionEvents() {
    }

    /** True if the block at {@code pos} may be broken or replaced. */
    public static boolean isEditable(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!(level instanceof Level l) || !Bunker7Dimension.is(l)) {
            return true;
        }
        return BunkerLayout.REACTOR_ZONE.isInside(pos) && !BunkerLayout.isFixtureInReactorZone(pos) && !ModBlocks.isShell(state)
                && (l.getServer() == null || !BunkerData.get(l.getServer()).isReactorZoneBlock(pos));
    }

    @SubscribeEvent
    static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (inBunker(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    private static boolean inBunker(LevelAccessor level) {
        return level instanceof Level l && Bunker7Dimension.is(l);
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!isEditable(event.getLevel(), event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    /** Covers players, Create deployers (fake players) and endermen; multi-block placements (beds, doors) too. */
    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!isEditable(event.getLevel(), event.getPos(), event.getBlockSnapshot().getState())) {
            event.setCanceled(true);
        }
    }

    /** Axe stripping, shovel paths, hoe tilling and the like. */
    @SubscribeEvent
    static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (!isEditable(event.getLevel(), event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!inBunker(level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (isEditable(level, pos, state)) {
            return;
        }
        // The Create wrench rotates blocks and picks them up when sneaking.
        if (BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).equals(CREATE_WRENCH)) {
            event.setCanceled(true);
            return;
        }
        // Flower pots, candles, cauldrons and the like give items away or change state on click.
        // Beds are excluded too: they explode in this dimension (bed_works=false).
        if (!isInteractive(state)) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    /** Furniture players may use anywhere in the bunker. */
    private static boolean isInteractive(BlockState state) {
        Block block = state.getBlock();
        // Buttons and levers drive the map's piston doors and the snack machine.
        return block instanceof ChestBlock || block instanceof DoorBlock || block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock || block instanceof JukeboxBlock || block instanceof ConsoleBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock || block instanceof BellBlock;
    }

    /** Buckets work only for someone standing in the reactor zone; elsewhere they would flood the bunker. */
    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (inBunker(event.getLevel()) && event.getItemStack().getItem() instanceof BucketItem
                && !BunkerLayout.REACTOR_ZONE.isInside(event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }

    /** Explosions in bunker7 never destroy blocks; they may still hurt entities. */
    @SubscribeEvent
    static void onDetonate(ExplosionEvent.Detonate event) {
        if (Bunker7Dimension.is(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }
}
