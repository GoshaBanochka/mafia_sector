package sector.mafia_sector.event;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.dimension.Bunker7Dimension;

/**
 * No damage to players in bunker7 at all: weapons, projectiles, Iron's Spells, Create machines (fake players),
 * lava, falling, starving. Checked by damage type, not by attacker, so indirect sources are caught too.
 * Nothing changes outside bunker7.
 */
@EventBusSubscriber(modid = Mafia_sector.MODID)
public final class DamageEvents {
    private DamageEvents() {
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && isBlocked(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    public static boolean isBlocked(Player player, DamageSource source) {
        // /kill and the void still work, so admins are never stuck with an unkillable player.
        return Bunker7Dimension.is(player.level()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
}
