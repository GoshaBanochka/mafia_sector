package sector.mafia_sector.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import sector.mafia_sector.compat.CuriosCompat;

/** Everything about a player that the game takes away and must give back: items, state and where they stood. */
public final class PlayerSnapshot {
    private static final String INVENTORY = "inventory";
    private static final String CURIOS = "curios";
    private static final String HEALTH = "health";
    private static final String FOOD = "food";
    private static final String SATURATION = "saturation";
    private static final String EFFECTS = "effects";
    private static final String GAME_MODE = "gameMode";
    private static final String DIMENSION = "dimension";
    private static final String POS = "pos";
    private static final String YAW = "yaw";
    private static final String PITCH = "pitch";

    private PlayerSnapshot() {
    }

    public static CompoundTag capture(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.put(INVENTORY, player.getInventory().save(new ListTag()));
        if (CuriosCompat.isLoaded()) {
            tag.put(CURIOS, CuriosCompat.save(player));
        }
        tag.putFloat(HEALTH, player.getHealth());
        tag.putInt(FOOD, player.getFoodData().getFoodLevel());
        tag.putFloat(SATURATION, player.getFoodData().getSaturationLevel());
        ListTag effects = new ListTag();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(effect.save());
        }
        tag.put(EFFECTS, effects);
        tag.putString(GAME_MODE, player.gameMode.getGameModeForPlayer().getName());
        tag.putString(DIMENSION, player.level().dimension().location().toString());
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(player.getX()));
        pos.add(DoubleTag.valueOf(player.getY()));
        pos.add(DoubleTag.valueOf(player.getZ()));
        tag.put(POS, pos);
        tag.putFloat(YAW, player.getYRot());
        tag.putFloat(PITCH, player.getXRot());
        return tag;
    }

    /** Empties inventory, Curios slots and effects. */
    public static void strip(ServerPlayer player) {
        player.closeContainer();
        player.getInventory().clearContent();
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.clear(player);
        }
        player.removeAllEffects();
    }

    /** Replaces the player's current state with the snapshot and sends them back. Safe to apply twice. */
    public static void apply(ServerPlayer player, CompoundTag tag) {
        strip(player);
        player.getInventory().load(tag.getList(INVENTORY, Tag.TAG_COMPOUND));
        if (CuriosCompat.isLoaded() && tag.contains(CURIOS)) {
            CuriosCompat.load(player, tag.getList(CURIOS, Tag.TAG_COMPOUND));
        }
        for (Tag effect : tag.getList(EFFECTS, Tag.TAG_COMPOUND)) {
            MobEffectInstance instance = MobEffectInstance.load((CompoundTag) effect);
            if (instance != null) {
                player.addEffect(instance);
            }
        }
        player.setGameMode(GameType.byName(tag.getString(GAME_MODE), GameType.SURVIVAL));
        player.setHealth(Math.max(1.0F, tag.getFloat(HEALTH)));
        player.getFoodData().setFoodLevel(tag.getInt(FOOD));
        player.getFoodData().setSaturation(tag.getFloat(SATURATION));

        MinecraftServer server = player.getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(DIMENSION))));
        ListTag pos = tag.getList(POS, Tag.TAG_DOUBLE);
        if (level == null || pos.size() != 3) {
            // The dimension is gone (removed mod?): fall back to world spawn.
            level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            GameManager.teleport(player, level, Vec3.atBottomCenterOf(spawn), 0.0F, 0.0F);
            return;
        }
        GameManager.teleport(player, level, new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)),
                tag.getFloat(YAW), tag.getFloat(PITCH));
    }
}
