package sector.mafia_sector.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import sector.mafia_sector.Mafia_sector;

/** Server to client: open the dossier screen (/b7 dossier). */
public record OpenDossierPayload() implements CustomPacketPayload {
    public static final OpenDossierPayload INSTANCE = new OpenDossierPayload();
    public static final Type<OpenDossierPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mafia_sector.MODID, "open_dossier"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDossierPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
