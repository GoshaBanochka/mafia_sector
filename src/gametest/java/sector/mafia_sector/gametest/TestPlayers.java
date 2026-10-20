package sector.mafia_sector.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import sector.mafia_sector.build.BunkerBuilder;
import sector.mafia_sector.build.BunkerData;
import sector.mafia_sector.dimension.Bunker7Dimension;

/** Shared setup for game tests: a bunker in the overworld and bot players that can log out and in again. */
final class TestPlayers {
    private TestPlayers() {
    }

    /** Builds the bunker in the overworld (the GameTest server has no data pack dimensions). */
    static ServerLevel bunker(MinecraftServer server) {
        Bunker7Dimension.useForTests(Level.OVERWORLD);
        ServerLevel level = server.overworld();
        BunkerData.get(server).markBuilt(BunkerBuilder.build(level, true));
        return level;
    }

    /** Like GameTestHelper.makeMockServerPlayerInLevel, but with a fixed profile so the same player can log in again. */
    static ServerPlayer join(MinecraftServer server, ServerLevel level, GameProfile profile) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // Act like a modded client, otherwise Create and Curios sync payloads are refused.
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
