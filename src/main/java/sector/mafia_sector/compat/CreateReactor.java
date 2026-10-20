package sector.mafia_sector.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.Mafia_sector;

import java.lang.reflect.Method;

/**
 * The Reactor of the final trial is a Create stressometer set into the reactor stand. Its block entity knows the
 * speed and the stress capacity of the network the players build. Read by reflection over public methods of
 * Create 6 (KineticBlockEntity.getSpeed, StressGaugeBlockEntity.getNetworkCapacity/getNetworkStress), so the mod
 * neither compiles nor links against Create.
 */
public final class CreateReactor {
    private static Class<?> cachedClass;
    private static Method getSpeed;
    private static Method getCapacity;
    private static Method getStress;

    public record Reading(float speed, float capacity, float stress) {
    }

    private CreateReactor() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("create");
    }

    /** Null if Create is missing or there is no stressometer at {@code pos}. */
    @Nullable
    public static Reading read(ServerLevel level, BlockPos pos) {
        if (!isLoaded()) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return null;
        }
        try {
            if (entity.getClass() != cachedClass) {
                getSpeed = entity.getClass().getMethod("getSpeed");
                getCapacity = entity.getClass().getMethod("getNetworkCapacity");
                getStress = entity.getClass().getMethod("getNetworkStress");
                cachedClass = entity.getClass();
            }
            return new Reading((float) getSpeed.invoke(entity), (float) getCapacity.invoke(entity), (float) getStress.invoke(entity));
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException | ClassCastException e) {
            Mafia_sector.LOGGER.warn("Bunker 7: cannot read the Reactor gauge at {}: {}", pos, e.toString());
            return null;
        }
    }
}
