package sector.mafia_sector.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import sector.mafia_sector.Mafia_sector;

import java.util.Optional;
import java.util.UUID;

/** Server to client: open the ballot screen, with a player already marked (a click on their chest). */
public record OpenBallotPayload(Optional<UUID> target) implements CustomPacketPayload {
    public static final Type<OpenBallotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mafia_sector.MODID, "open_ballot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBallotPayload> STREAM_CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeOptional(payload.target, (b, u) -> b.writeUUID(u)),
            buf -> new OpenBallotPayload(buf.readOptional(b -> b.readUUID())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
