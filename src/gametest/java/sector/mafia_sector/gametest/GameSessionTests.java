package sector.mafia_sector.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.compat.CuriosCompat;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.event.DamageEvents;
import sector.mafia_sector.game.GameException;
import sector.mafia_sector.game.GameManager;
import sector.mafia_sector.game.GameSession;
import sector.mafia_sector.game.PlayerStash;
import sector.mafia_sector.registry.ModItems;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Start/stop, the stash, and the rules that keep participants inside. Runs in its own batch after the build test. */
@GameTestHolder(Mafia_sector.MODID)
@PrefixGameTestTemplate(false)
public final class GameSessionTests {
    private static final Vec3 HOME = new Vec3(200.5, 70, 200.5);

    private GameSessionTests() {
    }

    @GameTest(template = "empty", batch = "session", timeoutTicks = 200)
    public static void stashSurvivesEverything(GameTestHelper helper) throws GameException {
        MinecraftServer server = helper.getLevel().getServer();
        RoundTests.stopLeftovers(server);
        ServerLevel level = TestPlayers.bunker(server);
        GameManager.setDebug(true);

        GameProfile profile = new GameProfile(UUID.randomUUID(), "bunker_tester");
        ServerPlayer player = TestPlayers.join(server, level, profile);
        equip(player, level);

        // 1. Start: stripped, stashed, at the own place in the circle.
        GameManager.start(server, List.of(player));
        GameSession.Participant participant = GameManager.session().get(player.getUUID());
        helper.assertTrue(player.getInventory().countItem(ModItems.DOSSIER.get()) == 1
                && player.getInventory().items.stream().filter(s -> !s.isEmpty()).count() == 1, "stripped on start, only the dossier");
        helper.assertTrue(!CuriosCompat.isLoaded() || ringSlot(player).isPresent(), "test Curios ring slot exists");
        ringSlot(player).ifPresent(ring -> helper.assertTrue(ring.getStackInSlot(0).isEmpty(), "Curios slot is stripped on start"));
        helper.assertTrue(PlayerStash.get(server).has(player.getUUID()), "snapshot is stashed");
        helper.assertTrue(player.position().distanceTo(participant.seat().spawn()) < 0.1,
                "player stands at place " + participant.seat().number() + ", is at " + player.blockPosition());
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "adventure in the bunker");

        // 2. Damage whitelist and the travel guard.
        helper.assertTrue(DamageEvents.isBlocked(player, level.damageSources().playerAttack(player)), "PvP is blocked");
        helper.assertTrue(DamageEvents.isBlocked(player, level.damageSources().lava()), "lava is blocked");
        helper.assertTrue(DamageEvents.isBlocked(player, level.damageSources().fall()), "fall is blocked");
        helper.assertTrue(!DamageEvents.isBlocked(player, level.damageSources().genericKill()), "/kill works");
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            player.teleportTo(nether, 0.5, 70, 0.5, 0, 0);
            helper.assertTrue(player.level() == level, "participant cannot leave the bunker dimension");
        }

        // 3. Stop: everything comes back, bunker items are gone.
        player.getInventory().add(new ItemStack(Items.STICK));
        GameManager.stop(server, Component.literal("test"));
        assertRestored(helper, player, "after stop");

        // 4. Logout during the game, the game ends while offline, restore on the next login.
        GameManager.start(server, List.of(player));
        server.getPlayerList().remove(player);
        GameManager.stop(server, Component.literal("test"));
        helper.assertTrue(PlayerStash.get(server).has(profile.getId()), "offline player keeps the snapshot");
        ServerPlayer second = TestPlayers.join(server, level, profile);

        helper.runAfterDelay(3, () -> {
            assertRestored(helper, second, "after relog");

            // 5. Server restart in the middle of a game: the session is lost, the stash is not.
            try {
                GameManager.start(server, List.of(second));
            } catch (GameException e) {
                helper.fail("restart: " + e.getMessage());
            }
            GameManager.reset();
            server.getPlayerList().remove(second);
            ServerPlayer third = TestPlayers.join(server, level, profile);
            helper.runAfterDelay(3, () -> {
                assertRestored(helper, third, "after restart");
                server.getPlayerList().remove(third);
                GameManager.setDebug(false);
                helper.succeed();
            });
        });
    }

    private static void equip(ServerPlayer player, ServerLevel level) {
        player.teleportTo(level, HOME.x, HOME.y, HOME.z, 90.0F, 0.0F);
        player.setGameMode(GameType.ADVENTURE);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
        player.getInventory().offhand.set(0, new ItemStack(Items.SHIELD));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600));
        player.setHealth(10.0F);
        player.getFoodData().setFoodLevel(7);
        ringSlot(player).ifPresent(ring -> ring.setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET)));
    }

    /** The test data pack gives players a Curios "ring" slot; the Iron's Spells spellbook lives in a slot like it. */
    private static Optional<IDynamicStackHandler> ringSlot(ServerPlayer player) {
        if (!CuriosCompat.isLoaded()) {
            return Optional.empty();
        }
        return CuriosApi.getCuriosInventory(player).map(handler -> handler.getCurios().get("ring")).map(ICurioStacksHandler::getStacks);
    }

    private static void assertRestored(GameTestHelper helper, ServerPlayer player, String when) {
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND) && player.getInventory().getItem(0).getCount() == 5,
                "diamonds back " + when);
        helper.assertTrue(player.getInventory().armor.get(3).is(Items.IRON_HELMET), "helmet back " + when);
        helper.assertTrue(player.getInventory().offhand.get(0).is(Items.SHIELD), "shield back " + when);
        helper.assertTrue(player.getInventory().countItem(Items.STICK) == 0, "bunker items gone " + when);
        ringSlot(player).ifPresent(ring -> helper.assertTrue(ring.getStackInSlot(0).is(Items.GOLD_NUGGET), "Curios item back " + when));
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "effects back " + when);
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "game mode back " + when);
        helper.assertTrue(Math.abs(player.getHealth() - 10.0F) < 0.01F, "health back " + when + ": " + player.getHealth());
        helper.assertTrue(player.getFoodData().getFoodLevel() == 7, "food back " + when);
        helper.assertTrue(player.position().distanceTo(HOME) < 0.01, "position back " + when + ": " + player.position());
        helper.assertTrue(!PlayerStash.get(player.getServer()).has(player.getUUID()), "stash entry removed " + when);
    }
}
