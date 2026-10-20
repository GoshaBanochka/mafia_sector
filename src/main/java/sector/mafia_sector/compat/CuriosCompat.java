package sector.mafia_sector.compat;

import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Optional Curios support: its slots (e.g. the Iron's Spells spellbook) are stashed with the inventory.
 * Callers must check {@link #isLoaded()} first; otherwise the Curios classes are never touched.
 */
public final class CuriosCompat {
    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    public static ListTag save(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).map(handler -> handler.saveInventory(false)).orElseGet(ListTag::new);
    }

    public static void clear(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (ICurioStacksHandler slots : handler.getCurios().values()) {
                empty(slots.getStacks());
                empty(slots.getCosmeticStacks());
            }
        });
    }

    public static void load(ServerPlayer player, ListTag tag) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.loadInventory(tag));
    }

    private static void empty(IDynamicStackHandler stacks) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            stacks.setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
