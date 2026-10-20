package sector.mafia_sector.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.game.GameHud;
import sector.mafia_sector.game.GamePhase;
import sector.mafia_sector.network.PlayerRequestPayload;
import sector.mafia_sector.network.SyncPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The ballot: everyone you may vote against, as big buttons with what is known about them. Pick one, then confirm
 * with the vote button. Opens by itself when a vote starts; the ballot item and the chests reopen it.
 */
public class BallotScreen extends Screen {
    private static final int PANEL = 0xF0151515;
    private static final int PAPER = 0xFF2A2620;
    private static final int GAP = 4;

    /** The candidate behind each button, for the hover card list. */
    private final Map<Button, UUID> buttons = new HashMap<>();
    @Nullable
    private UUID selected;
    @Nullable
    private List<FormattedCharSequence> tooltip;
    private int left;
    private int top;
    private int right;
    private int bottom;

    public BallotScreen(@Nullable UUID preselected) {
        super(Component.translatable("ballot.mafia_sector.title"));
        this.selected = preselected;
    }

    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static SyncPayload state() {
        return ClientPayloads.state();
    }

    @Nullable
    private static SyncPayload.OwnView me() {
        return state().own().orElse(null);
    }

    private static boolean canVote() {
        SyncPayload.OwnView me = me();
        SyncPayload.PlayerView view = me != null ? state().player(me.uuid()) : null;
        return view != null && view.alive() && GamePhase.byOrdinal(state().phase()).isVote() && me.votedFor().isEmpty();
    }

    private List<SyncPayload.PlayerView> candidates() {
        SyncPayload.OwnView me = me();
        return state().players().stream()
                .filter(p -> state().isCandidate(p.uuid()) && (me == null || !p.uuid().equals(me.uuid()))).toList();
    }

    @Override
    protected void init() {
        int w = Math.min(width - 16, 380);
        int h = Math.min(height - 16, 250);
        left = (width - w) / 2;
        top = (height - h) / 2;
        right = left + w;
        bottom = top + h;
        List<SyncPayload.PlayerView> candidates = candidates();
        if (selected != null && candidates.stream().noneMatch(p -> p.uuid().equals(selected))) {
            selected = null;
        }
        boolean open = canVote();
        buttons.clear();
        // Two columns, three for big games; rows shrink to fit fifteen candidates on small screens.
        int columns = candidates.size() > 10 ? 3 : 2;
        int rows = Math.max(1, (candidates.size() + columns - 1) / columns);
        int y0 = top + 44;
        int rowH = Math.max(16, Math.min(22, (bottom - 48 - y0) / rows));
        int buttonW = (w - 16 - GAP * (columns - 1)) / columns;
        for (int i = 0; i < candidates.size(); i++) {
            SyncPayload.PlayerView player = candidates.get(i);
            int x = left + 8 + (i % columns) * (buttonW + GAP);
            int y = y0 + (i / columns) * rowH;
            boolean chosen = player.uuid().equals(selected) || me() != null && me().votedFor().map(player.uuid()::equals).orElse(false);
            Button button = Button.builder(label(player, chosen), b -> {
                selected = player.uuid();
                refresh();
            }).bounds(x, y, buttonW, rowH - 2).build();
            button.active = open;
            buttons.put(button, player.uuid());
            addRenderableWidget(button);
        }
        SyncPayload.PlayerView target = selected != null ? state().player(selected) : null;
        Component voteLabel = target != null ? Component.translatable("ballot.mafia_sector.vote_against", target.name())
                : Component.translatable("ballot.mafia_sector.choose");
        Button vote = addRenderableWidget(Button.builder(voteLabel.copy().withStyle(target != null ? ChatFormatting.RED : ChatFormatting.GRAY), b -> {
            PacketDistributor.sendToServer(new PlayerRequestPayload(PlayerRequestPayload.Kind.VOTE, -1, Optional.ofNullable(selected)));
        }).bounds(left + 8, bottom - 28, w - 16 - 84, 20).build());
        vote.active = open && target != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(right - 8 - 80, bottom - 28, 80, 20).build());
    }

    private static Component label(SyncPayload.PlayerView player, boolean chosen) {
        MutableComponent text = Component.literal((chosen ? "▶ " : "") + "№" + (player.place() + 1) + " " + player.name());
        SyncPayload.CardView profession = player.card(CardType.PROFESSION.ordinal());
        if (profession != null) {
            text.append(Component.literal(" · " + profession.text()).withStyle(ChatFormatting.GOLD));
        }
        return chosen ? text.withStyle(ChatFormatting.YELLOW) : text;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        tooltip = null;
        SyncPayload s = state();
        GamePhase phase = GamePhase.byOrdinal(s.phase());
        g.fill(left, top, right, bottom, PANEL);
        g.fill(left + 4, top + 4, right - 4, top + 38, PAPER);
        g.drawString(font, Component.translatable("ballot.mafia_sector.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + 10, top + 9, 0xFFFFFF);
        Component clock = Component.literal(GameHud.clock(ClientPayloads.ticksLeft())).withStyle(ChatFormatting.WHITE);
        g.drawString(font, clock, right - 10 - font.width(clock), top + 9, 0xFFFFFF);
        g.drawString(font, subtitle(s, phase), left + 10, top + 23, 0xFFFFFF);

        for (var renderable : renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
        if (candidates().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("ballot.mafia_sector.nobody").withStyle(ChatFormatting.GRAY), (left + right) / 2, top + 60, 0xFFFFFF);
        }
        long voted = s.players().stream().filter(SyncPayload.PlayerView::voted).count();
        long voters = s.players().stream().filter(p -> p.alive() && p.online()).count();
        g.drawString(font, Component.translatable("ballot.mafia_sector.progress", voted, voters).withStyle(ChatFormatting.GRAY),
                left + 10, bottom - 42, 0xFFFFFF);
        hoverCards(mouseX, mouseY);
        if (tooltip != null) {
            g.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private Component subtitle(SyncPayload s, GamePhase phase) {
        SyncPayload.OwnView me = me();
        SyncPayload.PlayerView view = me != null ? s.player(me.uuid()) : null;
        if (me != null && me.votedFor().isPresent()) {
            SyncPayload.PlayerView target = s.player(me.votedFor().get());
            return Component.translatable("ballot.mafia_sector.voted", target != null ? target.name() : "?").withStyle(ChatFormatting.GREEN);
        }
        if (!phase.isVote()) return Component.translatable("ballot.mafia_sector.closed").withStyle(ChatFormatting.GRAY);
        if (view == null || !view.alive()) return Component.translatable("ballot.mafia_sector.no_vote").withStyle(ChatFormatting.GRAY);
        return Component.translatable(phase == GamePhase.REVOTE ? "ballot.mafia_sector.revote" : "ballot.mafia_sector.vote")
                .withStyle(ChatFormatting.YELLOW);
    }

    /** Hovering a candidate shows everything revealed about them. */
    private void hoverCards(int mouseX, int mouseY) {
        for (Map.Entry<Button, UUID> entry : buttons.entrySet()) {
            if (!entry.getKey().isHovered()) continue;
            SyncPayload.PlayerView player = state().player(entry.getValue());
            if (player != null) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(player.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
                for (SyncPayload.CardView card : player.revealed()) {
                    lines.add(Component.translatable(CardType.values()[card.type()].key()).withStyle(ChatFormatting.GRAY).append(": ")
                            .append(Component.literal(card.text()).withStyle(ChatFormatting.WHITE)));
                }
                tooltip = new ArrayList<>();
                for (Component line : lines) tooltip.addAll(font.split(line, 240));
                return;
            }
        }
    }
}
