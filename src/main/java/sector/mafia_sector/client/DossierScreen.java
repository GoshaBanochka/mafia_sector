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
import sector.mafia_sector.action.SpecialAction;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.game.GameHud;
import sector.mafia_sector.game.GamePhase;
import sector.mafia_sector.network.PlayerRequestPayload;
import sector.mafia_sector.network.SyncPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The dossier, in tabs so the first look is not a wall of text: own cards (reveal on your turn, end the turn),
 * the other players (pick one, see what they revealed), the catastrophe and the bunker, the special action
 * (target, card, confirm). A hint line at the bottom says what to do right now.
 * Everything shown comes from the last {@link SyncPayload}; every button only asks the server.
 */
public class DossierScreen extends Screen {
    public enum Tab {
        CARDS, PLAYERS, BUNKER, ACTION;

        String key() {
            return "dossier.mafia_sector.tab." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Remembered while the client runs, so the dossier reopens where it was left. */
    public static Tab lastTab = Tab.CARDS;

    private static final int PANEL = 0xF0151515;
    private static final int TAB_BAR = 0xFF0E0E0E;
    private static final int TAB_ON = 0xFF2C2C2C;
    private static final int TAB_OFF = 0xFF1A1A1A;
    private static final int ROW_OPEN = 0xFF1F2A1F;
    private static final int ROW_HIDDEN = 0xFF202020;
    private static final int ROW_ACTION = 0xFF2A1F2E;
    private static final int TAB_H = 16;
    private static final int ROW = 22;

    private Tab tab = lastTab;
    @Nullable
    private UUID selectedPlayer;
    @Nullable
    private UUID target;
    @Nullable
    private CardType chosenType;
    @Nullable
    private List<FormattedCharSequence> tooltip;

    private int left;
    private int top;
    private int right;
    private int bottom;
    private int contentTop;
    private int contentBottom;
    /** Row height of the card lists: 22, less on small screens so all eight fit. */
    private int row = ROW;

    public DossierScreen() {
        super(Component.translatable("dossier.mafia_sector.title"));
    }

    /** New state from the server: rebuild the buttons. */
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ state

    private static SyncPayload state() {
        return ClientPayloads.state();
    }

    private static GamePhase phase() {
        return GamePhase.byOrdinal(state().phase());
    }

    @Nullable
    private static SyncPayload.OwnView me() {
        return state().own().orElse(null);
    }

    private static boolean amAlive() {
        SyncPayload.OwnView me = me();
        SyncPayload.PlayerView view = me != null ? state().player(me.uuid()) : null;
        return view != null && view.alive();
    }

    private static boolean myTurn() {
        SyncPayload.OwnView me = me();
        return me != null && phase() == GamePhase.REVEAL && state().currentTurn().map(me.uuid()::equals).orElse(false);
    }

    private static boolean mustReveal() {
        return myTurn() && !state().revealedThisTurn() && amAlive();
    }

    private static boolean mustVote() {
        SyncPayload.OwnView me = me();
        return me != null && amAlive() && phase().isVote() && me.votedFor().isEmpty();
    }

    @Nullable
    private static SpecialAction myAction() {
        SyncPayload.OwnView me = me();
        return me != null ? SpecialAction.byId(me.action()) : null;
    }

    private static boolean actionUsable() {
        SyncPayload.OwnView me = me();
        return me != null && myAction() != null && !me.actionUsed() && amAlive() && phase().isRound();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init() {
        int w = Math.min(width - 16, 460);
        int h = Math.min(height - 16, 280);
        left = (width - w) / 2;
        top = (height - h) / 2;
        right = left + w;
        bottom = top + h;
        contentTop = top + 20 + TAB_H + 6;
        contentBottom = bottom - 22;
        row = Math.max(19, Math.min(ROW, (contentBottom - contentTop - 13) / CardType.values().length + 1));
        if (me() == null && (tab == Tab.CARDS || tab == Tab.ACTION)) {
            tab = Tab.PLAYERS;
        }
        switch (tab) {
            case CARDS -> initCards();
            case PLAYERS -> initPlayers();
            case ACTION -> initAction();
            case BUNKER -> {
            }
        }
        if (mustVote()) {
            addRenderableWidget(Button.builder(Component.translatable("dossier.mafia_sector.open_ballot").withStyle(ChatFormatting.YELLOW),
                    b -> ClientPayloads.openBallot(null)).bounds(right - 128, bottom - 20, 120, 16).build());
        }
    }

    private void initCards() {
        SyncPayload.OwnView me = me();
        boolean canReveal = mustReveal();
        int x = right - 8 - 64;
        for (CardType type : CardType.values()) {
            int y = rowY(type.ordinal());
            if (type == CardType.ACTION) {
                if (myAction() != null) {
                    addRenderableWidget(Button.builder(Component.translatable("dossier.mafia_sector.details"), b -> switchTab(Tab.ACTION))
                            .bounds(x, y + 3, 64, 14).build());
                }
            } else if (canReveal && !me.isRevealed(type.ordinal()) && (state().round() > 1 || type == CardType.PROFESSION)) {
                addRenderableWidget(Button.builder(Component.translatable("dossier.mafia_sector.reveal").withStyle(ChatFormatting.GREEN),
                        b -> send(PlayerRequestPayload.Kind.REVEAL, type, null)).bounds(x, y + 3, 64, 14).build());
            }
        }
        if (myTurn() && state().revealedThisTurn() && amAlive()) {
            addRenderableWidget(Button.builder(Component.translatable("dossier.mafia_sector.end_turn").withStyle(ChatFormatting.GREEN),
                    b -> send(PlayerRequestPayload.Kind.END_TURN, null, null)).bounds(right - 128, bottom - 20, 120, 16).build());
        }
    }

    private void initPlayers() {
        List<SyncPayload.PlayerView> players = state().players();
        if (selectedPlayer == null || state().player(selectedPlayer) == null) {
            SyncPayload.OwnView me = me();
            selectedPlayer = players.stream().map(SyncPayload.PlayerView::uuid)
                    .filter(u -> me == null || !u.equals(me.uuid())).findFirst().orElse(players.isEmpty() ? null : players.getFirst().uuid());
        }
        int listW = Math.min(150, (right - left) / 3);
        int rowH = Math.max(12, Math.min(16, (contentBottom - contentTop) / Math.max(1, players.size())));
        for (int i = 0; i < players.size(); i++) {
            SyncPayload.PlayerView player = players.get(i);
            Button button = Button.builder(playerLabel(player), b -> {
                selectedPlayer = player.uuid();
                refresh();
            }).bounds(left + 8, contentTop + i * rowH, listW, rowH - 1).build();
            button.active = !player.uuid().equals(selectedPlayer);
            addRenderableWidget(button);
        }
    }

    /** The special action step: pick target and card where needed, then confirm. */
    private void initAction() {
        SpecialAction action = myAction();
        if (action == null || !actionUsable()) {
            return;
        }
        int y = contentTop + 70;
        int w = (right - left) / 2 - 12;
        List<SyncPayload.PlayerView> targets = targets();
        if (action.needsTarget()) {
            if (target == null || targets.stream().noneMatch(p -> p.uuid().equals(target))) {
                target = targets.isEmpty() ? null : targets.getFirst().uuid();
            }
            SyncPayload.PlayerView chosen = target != null ? state().player(target) : null;
            addRenderableWidget(Button.builder(Component.translatable("dossier.mafia_sector.target", chosen != null ? chosen.name() : "-"), b -> {
                target = cycle(targets.stream().map(SyncPayload.PlayerView::uuid).toList(), target);
                chosenType = null;
                refresh();
            }).bounds(left + 8, y, w, 18).build());
            y += 22;
        }
        if (action.choice() != SpecialAction.Choice.NONE) {
            List<CardType> types = typeChoices(action);
            if (chosenType == null || !types.contains(chosenType)) {
                chosenType = types.isEmpty() ? null : types.getFirst();
            }
            addRenderableWidget(Button.builder(Component.translatable("dossier.mafia_sector.card_choice",
                    chosenType != null ? Component.translatable(chosenType.key()) : Component.literal("-")), b -> {
                chosenType = cycle(types, chosenType);
                refresh();
            }).bounds(left + 8, y, w, 18).build());
            y += 22;
        }
        boolean ready = (!action.needsTarget() || target != null) && (action.choice() == SpecialAction.Choice.NONE || chosenType != null);
        Button confirm = addRenderableWidget(Button.builder(Component.translatable("dossier.mafia_sector.confirm").withStyle(ChatFormatting.GOLD),
                b -> send(PlayerRequestPayload.Kind.USE_ACTION, chosenType, target)).bounds(left + 8, y + 4, w, 20).build());
        confirm.active = ready;
    }

    private void switchTab(Tab next) {
        tab = next;
        lastTab = next;
        refresh();
    }

    private List<SyncPayload.PlayerView> targets() {
        SyncPayload.OwnView me = me();
        return state().players().stream().filter(p -> p.alive() && (me == null || !p.uuid().equals(me.uuid()))).toList();
    }

    private List<CardType> typeChoices(SpecialAction action) {
        if (action.choice() == SpecialAction.Choice.TARGET_HIDDEN) {
            SyncPayload.PlayerView chosen = target != null ? state().player(target) : null;
            return chosen == null ? List.of() : CardType.REVEALABLE.stream().filter(t -> chosen.card(t.ordinal()) == null).toList();
        }
        return CardType.REVEALABLE;
    }

    private static <T> T cycle(List<T> options, @Nullable T current) {
        if (options.isEmpty()) return current;
        return options.get((options.indexOf(current) + 1) % options.size());
    }

    private static void send(PlayerRequestPayload.Kind kind, @Nullable CardType type, @Nullable UUID target) {
        PacketDistributor.sendToServer(new PlayerRequestPayload(kind, type != null ? type.ordinal() : -1, Optional.ofNullable(target)));
    }

    private int rowY(int index) {
        return contentTop + index * this.row;
    }

    private static Component playerLabel(SyncPayload.PlayerView player) {
        SyncPayload.OwnView me = ClientPayloads.state().own().orElse(null);
        boolean self = me != null && me.uuid().equals(player.uuid());
        MutableComponent text = Component.literal("№" + (player.place() + 1) + " " + player.name());
        if (self) text.append(" ").append(Component.translatable("dossier.mafia_sector.you"));
        if (!player.alive()) text.withStyle(ChatFormatting.DARK_RED);
        else if (ClientPayloads.state().currentTurn().map(player.uuid()::equals).orElse(false)) text.withStyle(ChatFormatting.GREEN);
        return text;
    }

    // ------------------------------------------------------------------ tabs

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Tab[] tabs = Tab.values();
        int tabW = (right - left - 16) / tabs.length;
        int y = top + 20;
        if (mouseY >= y && mouseY < y + TAB_H && mouseX >= left + 8 && mouseX < left + 8 + tabW * tabs.length) {
            Tab clicked = tabs[(int) ((mouseX - left - 8) / tabW)];
            if (clicked != tab && (me() != null || (clicked != Tab.CARDS && clicked != Tab.ACTION))) {
                switchTab(clicked);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        int tabW = (right - left - 16) / tabs.length;
        int y = top + 20;
        g.fill(left + 4, y + TAB_H, right - 4, y + TAB_H + 1, TAB_ON);
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = left + 8 + i * tabW;
            boolean on = t == tab;
            boolean disabled = me() == null && (t == Tab.CARDS || t == Tab.ACTION);
            boolean hover = mouseX >= x && mouseX < x + tabW - 2 && mouseY >= y && mouseY < y + TAB_H;
            g.fill(x, y, x + tabW - 2, y + TAB_H + (on ? 1 : 0), on ? TAB_ON : hover && !disabled ? 0xFF242424 : TAB_OFF);
            if (on) g.fill(x, y, x + tabW - 2, y + 1, 0xFFFFB347);
            MutableComponent label = Component.translatable(t.key());
            if (attention(t)) label = Component.literal("● ").withStyle(t == Tab.ACTION ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GREEN).append(label);
            int color = disabled ? 0x555555 : on ? 0xFFB347 : 0xBBBBBB;
            g.drawCenteredString(font, label, x + (tabW - 2) / 2, y + 4, color);
        }
    }

    /** A dot on the tab that needs you now: your turn on the cards, an unused action. */
    private static boolean attention(Tab t) {
        return switch (t) {
            case CARDS -> mustReveal();
            case ACTION -> actionUsable();
            default -> false;
        };
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        tooltip = null;
        g.fill(left, top, right, bottom, PANEL);
        g.fill(left, top, right, top + 18, TAB_BAR);
        g.drawString(font, Component.translatable("dossier.mafia_sector.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + 8, top + 5, 0xFFFFFF);
        Component status = status();
        g.drawString(font, status, right - 8 - font.width(status), top + 5, 0xFFFFFF);
        drawTabs(g, mouseX, mouseY);
        switch (tab) {
            case CARDS -> drawCards(g, mouseX, mouseY);
            case PLAYERS -> drawPlayers(g);
            case BUNKER -> drawBunker(g);
            case ACTION -> drawAction(g);
        }
        g.drawString(font, hint(), left + 8, bottom - 16, 0xFFFFFF);
        for (var renderable : renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
        if (tooltip != null) {
            g.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    /** "Round 2/5 · Reveal · 00:42" in the title bar. */
    private Component status() {
        SyncPayload s = state();
        return Component.translatable("dossier.mafia_sector.status", s.round(), s.totalRounds(), Component.translatable(phase().key()),
                GameHud.clock(ClientPayloads.ticksLeft())).withStyle(ChatFormatting.GRAY);
    }

    /** What to do right now, in one line at the bottom. */
    private Component hint() {
        SyncPayload s = state();
        if (me() == null) return Component.translatable("dossier.mafia_sector.hint.spectator").withStyle(ChatFormatting.GRAY);
        if (!amAlive()) return Component.translatable("dossier.mafia_sector.hint.exiled").withStyle(ChatFormatting.RED);
        if (mustReveal()) return Component.translatable(s.round() == 1 ? "dossier.mafia_sector.hint.reveal_first" : "dossier.mafia_sector.hint.reveal")
                .withStyle(ChatFormatting.GREEN);
        if (myTurn()) return Component.translatable("dossier.mafia_sector.hint.talk").withStyle(ChatFormatting.GREEN);
        if (mustVote()) return Component.translatable("dossier.mafia_sector.hint.vote").withStyle(ChatFormatting.YELLOW);
        Optional<SyncPayload.PlayerView> turn = s.currentTurn().map(s::player);
        if (turn.isPresent()) return Component.translatable("dossier.mafia_sector.hint.turn", turn.get().name()).withStyle(ChatFormatting.GRAY);
        return Component.translatable("dossier.mafia_sector.hint.phase", Component.translatable(phase().key())).withStyle(ChatFormatting.GRAY);
    }

    /** Own cards: one row per card, revealed ones tinted green. */
    private void drawCards(GuiGraphics g, int mouseX, int mouseY) {
        SyncPayload.OwnView me = me();
        int textW = right - left - 16 - 72;
        for (CardType type : CardType.values()) {
            int y = rowY(type.ordinal());
            SyncPayload.CardView card = me.card(type.ordinal());
            boolean open = me.isRevealed(type.ordinal());
            boolean action = type == CardType.ACTION;
            g.fill(left + 6, y, right - 6, y + row - 2, action ? ROW_ACTION : open ? ROW_OPEN : ROW_HIDDEN);
            MutableComponent label = Component.translatable(type.key()).withStyle(ChatFormatting.GRAY);
            label.append(Component.translatable(action ? (me.actionUsed() ? "dossier.mafia_sector.used" : actionUsable()
                    ? "dossier.mafia_sector.unused" : "dossier.mafia_sector.unused_idle")
                    : open ? "dossier.mafia_sector.open" : "dossier.mafia_sector.hidden")
                    .withStyle(open || action && actionUsable() ? (action ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GREEN) : ChatFormatting.DARK_GRAY));
            g.drawString(font, label, left + 10, y + 2, 0xFFFFFF);
            String text = card != null ? card.text() : "-";
            g.drawString(font, font.plainSubstrByWidth(text, textW), left + 10, y + 11, open ? 0xFFFFFF : 0xC8B88A);
            if (card != null && mouseX >= left + 6 && mouseX < right - 76 && mouseY >= y && mouseY < y + row - 2) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(card.text()).withStyle(ChatFormatting.WHITE));
                if (!card.detail().isEmpty()) lines.add(Component.literal(card.detail()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(open ? "dossier.mafia_sector.everyone_sees" : "dossier.mafia_sector.only_you")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                tooltip = wrap(lines, 240);
            }
        }
    }

    /** The selected player: all eight cards, the revealed ones readable. */
    private void drawPlayers(GuiGraphics g) {
        SyncPayload.PlayerView player = selectedPlayer != null ? state().player(selectedPlayer) : null;
        int x = left + 8 + Math.min(150, (right - left) / 3) + 10;
        int w = right - 8 - x;
        if (player == null) return;
        int y = contentTop;
        MutableComponent head = Component.literal(player.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        if (!player.alive()) head.append(Component.translatable("dossier.mafia_sector.exiled_tag", player.exileOrder()).withStyle(ChatFormatting.RED));
        else if (!player.online()) head.append(Component.translatable("dossier.mafia_sector.offline").withStyle(ChatFormatting.DARK_GRAY));
        g.drawString(font, head, x, y, 0xFFFFFF);
        y += 13;
        for (CardType type : CardType.values()) {
            SyncPayload.CardView card = player.card(type.ordinal());
            g.fill(x - 2, y - 1, right - 6, y + row - 4, card != null ? ROW_OPEN : ROW_HIDDEN);
            g.drawString(font, Component.translatable(type.key()).withStyle(ChatFormatting.GRAY), x + 2, y, 0xFFFFFF);
            Component text = card != null ? Component.literal(font.plainSubstrByWidth(card.text(), w - 6)).withStyle(ChatFormatting.WHITE)
                    : Component.translatable("dossier.mafia_sector.hidden_card").withStyle(ChatFormatting.DARK_GRAY);
            g.drawString(font, text, x + 2, y + 8, 0xFFFFFF);
            y += row - 2;
        }
    }

    /** The catastrophe on the left, the bunker on the right, the places below. */
    private void drawBunker(GuiGraphics g) {
        SyncPayload s = state();
        int mid = (left + right) / 2;
        int colW = mid - left - 14;
        int y = contentTop;
        y = text(g, Component.translatable("dossier.mafia_sector.catastrophe").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), left + 8, y, colW);
        y = text(g, Component.literal(s.catastrophe()).withStyle(ChatFormatting.WHITE), left + 8, y + 2, colW);
        text(g, Component.literal(s.catastropheText()).withStyle(ChatFormatting.GRAY), left + 8, y + 2, colW);
        int x = mid + 6;
        y = contentTop;
        y = text(g, Component.translatable("dossier.mafia_sector.bunker").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), x, y, colW);
        y = text(g, Component.literal(s.bunker()).withStyle(ChatFormatting.WHITE), x, y + 2, colW);
        y = text(g, Component.translatable("dossier.mafia_sector.area", s.bunkerArea(), s.bunkerDuration()).withStyle(ChatFormatting.GRAY), x, y + 2, colW);
        y = text(g, Component.translatable("dossier.mafia_sector.supplies", s.bunkerSupplies()).withStyle(ChatFormatting.GRAY), x, y + 2, colW);
        text(g, Component.translatable("dossier.mafia_sector.features", String.join(", ", s.bunkerFeatures())).withStyle(ChatFormatting.GRAY), x, y + 2, colW);
        long inside = s.players().stream().filter(SyncPayload.PlayerView::alive).count();
        g.fill(left + 6, contentBottom - 14, right - 6, contentBottom - 2, 0xFF2A2410);
        g.drawCenteredString(font, Component.translatable("dossier.mafia_sector.places", s.seats(), inside, s.players().size())
                .withStyle(ChatFormatting.YELLOW), mid, contentBottom - 12, 0xFFFFFF);
    }

    private void drawAction(GuiGraphics g) {
        SyncPayload.OwnView me = me();
        SyncPayload.CardView card = me.card(CardType.ACTION.ordinal());
        int w = right - left - 16;
        int y = contentTop;
        y = text(g, Component.literal(card != null ? card.text() : "-").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), left + 8, y, w);
        text(g, Component.literal(card != null ? card.detail() : "").withStyle(ChatFormatting.GRAY), left + 8, y + 3, w);
        Component state = me.actionUsed() ? Component.translatable("dossier.mafia_sector.action.used")
                : !amAlive() ? Component.translatable("dossier.mafia_sector.action.exiled")
                : !phase().isRound() ? Component.translatable("dossier.mafia_sector.action.not_now")
                : Component.translatable("dossier.mafia_sector.confirm_hint");
        int x = (left + right) / 2 + 6;
        text(g, state.copy().withStyle(ChatFormatting.DARK_GRAY), actionUsable() ? x : left + 8, contentTop + 70, actionUsable() ? right - 8 - x : w);
    }

    /** Draws wrapped text and returns the y below it. */
    private int text(GuiGraphics g, Component text, int x, int y, int width) {
        for (FormattedCharSequence line : font.split(text, width)) {
            g.drawString(font, line, x, y, 0xFFFFFF);
            y += 10;
        }
        return y;
    }

    private List<FormattedCharSequence> wrap(List<Component> lines, int width) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(font.split(line, width));
        }
        return out;
    }
}
