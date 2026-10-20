package sector.mafia_sector.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.Mafia_sector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Server to client: everything the game shows. Public part for everyone following the game; {@link #own} only
 * in the copy for a participant, with their own cards. Other players' hidden cards are never in it.
 */
public record SyncPayload(boolean active, int phase, int round, int totalRounds, int ticksLeft, int ticksTotal, boolean paused,
                          int seats, String catastrophe, String catastropheText, String bunker, int bunkerArea,
                          String bunkerSupplies, String bunkerDuration, List<String> bunkerFeatures,
                          Optional<UUID> currentTurn, boolean revealedThisTurn, List<PlayerView> players,
                          List<UUID> candidates, Optional<OwnView> own) implements CustomPacketPayload {
    public static final Type<SyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mafia_sector.MODID, "sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.ofMember(SyncPayload::write, SyncPayload::read);

    /** A card everybody can see: type (CardType ordinal), short text, explanation. */
    public record CardView(int type, String text, String detail) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(type);
            buf.writeUtf(text);
            buf.writeUtf(detail);
        }

        static CardView read(FriendlyByteBuf buf) {
            return new CardView(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }
    }

    public record PlayerView(UUID uuid, String name, int place, boolean alive, boolean online, int exileOrder, boolean voted,
                             List<CardView> revealed) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(uuid);
            buf.writeUtf(name);
            buf.writeVarInt(place);
            buf.writeBoolean(alive);
            buf.writeBoolean(online);
            buf.writeVarInt(exileOrder);
            buf.writeBoolean(voted);
            writeList(buf, revealed, (b, c) -> c.write(b));
        }

        static PlayerView read(FriendlyByteBuf buf) {
            return new PlayerView(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readBoolean(), readList(buf, CardView::read));
        }

        @Nullable
        public CardView card(int type) {
            for (CardView card : revealed) {
                if (card.type() == type) return card;
            }
            return null;
        }
    }

    /** The receiver's own cards, hidden ones included; {@code action} is the SpecialAction id of the action card. */
    public record OwnView(UUID uuid, List<CardView> cards, List<Integer> revealedTypes, boolean actionUsed, String action,
                          Optional<UUID> votedFor) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(uuid);
            writeList(buf, cards, (b, c) -> c.write(b));
            writeList(buf, revealedTypes, FriendlyByteBuf::writeVarInt);
            buf.writeBoolean(actionUsed);
            buf.writeUtf(action);
            buf.writeOptional(votedFor, (b, u) -> b.writeUUID(u));
        }

        static OwnView read(FriendlyByteBuf buf) {
            return new OwnView(buf.readUUID(), readList(buf, CardView::read), readList(buf, FriendlyByteBuf::readVarInt),
                    buf.readBoolean(), buf.readUtf(), buf.readOptional(b -> b.readUUID()));
        }

        public boolean isRevealed(int type) {
            return revealedTypes.contains(type);
        }

        @Nullable
        public CardView card(int type) {
            for (CardView card : cards) {
                if (card.type() == type) return card;
            }
            return null;
        }
    }

    public static SyncPayload inactive() {
        return new SyncPayload(false, 0, 0, 0, 0, 0, false, 0, "", "", "", 0, "", "", List.of(),
                Optional.empty(), false, List.of(), List.of(), Optional.empty());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(phase);
        buf.writeVarInt(round);
        buf.writeVarInt(totalRounds);
        buf.writeVarInt(ticksLeft);
        buf.writeVarInt(ticksTotal);
        buf.writeBoolean(paused);
        buf.writeVarInt(seats);
        buf.writeUtf(catastrophe);
        buf.writeUtf(catastropheText);
        buf.writeUtf(bunker);
        buf.writeVarInt(bunkerArea);
        buf.writeUtf(bunkerSupplies);
        buf.writeUtf(bunkerDuration);
        writeList(buf, bunkerFeatures, (b, s) -> b.writeUtf(s));
        buf.writeOptional(currentTurn, (b, u) -> b.writeUUID(u));
        buf.writeBoolean(revealedThisTurn);
        writeList(buf, players, (b, p) -> p.write(b));
        writeList(buf, candidates, (b, u) -> b.writeUUID(u));
        buf.writeOptional(own, (b, o) -> o.write(b));
    }

    private static SyncPayload read(RegistryFriendlyByteBuf buf) {
        return new SyncPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(),
                buf.readUtf(), readList(buf, b -> b.readUtf()), buf.readOptional(b -> b.readUUID()), buf.readBoolean(),
                readList(buf, PlayerView::read), readList(buf, b -> b.readUUID()), buf.readOptional(b -> OwnView.read(b)));
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, BiConsumer<FriendlyByteBuf, T> writer) {
        buf.writeVarInt(list.size());
        list.forEach(item -> writer.accept(buf, item));
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    /** Who one may vote against now: anyone in the bunker, or only the tied players at a revote. */
    public boolean isCandidate(UUID uuid) {
        PlayerView player = player(uuid);
        return player != null && player.alive() && (candidates.isEmpty() || candidates.contains(uuid));
    }

    @Nullable
    public PlayerView player(UUID uuid) {
        for (PlayerView player : players) {
            if (player.uuid().equals(uuid)) return player;
        }
        return null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
