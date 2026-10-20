package sector.mafia_sector.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import sector.mafia_sector.Mafia_sector;

import java.util.Optional;
import java.util.UUID;

/**
 * Client to server, from the dossier and the ballot: reveal a card, end the turn, use the special action (with a
 * target player and a card type where the action needs them), or vote against the target. The server checks everything.
 */
public record PlayerRequestPayload(Kind kind, int cardType, Optional<UUID> target) implements CustomPacketPayload {
    public enum Kind { REVEAL, END_TURN, USE_ACTION, VOTE }

    public static final Type<PlayerRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mafia_sector.MODID, "request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerRequestPayload> STREAM_CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeEnum(payload.kind);
                buf.writeVarInt(payload.cardType + 1);
                buf.writeOptional(payload.target, (b, u) -> b.writeUUID(u));
            },
            buf -> new PlayerRequestPayload(buf.readEnum(Kind.class), buf.readVarInt() - 1, buf.readOptional(b -> b.readUUID())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
