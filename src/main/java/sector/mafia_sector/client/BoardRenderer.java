package sector.mafia_sector.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import sector.mafia_sector.block.BoardBlock;
import sector.mafia_sector.block.BoardBlockEntity;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.card.CardType;
import sector.mafia_sector.game.GamePhase;
import sector.mafia_sector.network.SyncPayload;

/**
 * Draws the council board over the panels below and to the right of its anchor block: a title row and a 4x4 grid
 * of tiles, one per place in the circle, with every revealed card. Updates itself from the synced game state.
 */
public class BoardRenderer implements BlockEntityRenderer<BoardBlockEntity> {
    /** Text pixels per block: one board line is a little under a quarter of a block. */
    private static final float PX_PER_BLOCK = 48.0F;
    private static final int COLUMNS = 4;
    private static final int ROWS = 4;
    private static final int TITLE_HEIGHT = 48;
    private static final int LINE = 11;
    private static final int TITLE_COLOR = 0xFFFFB347;
    private static final int SEAT_COLOR = 0xFFE0C080;
    private static final int TURN_COLOR = 0xFF60FF60;
    private static final int EXILED_COLOR = 0xFFD04040;
    private static final int LABEL_COLOR = 0xFF8A8A8A;
    private static final int CARD_COLOR = 0xFFFFFFFF;
    private static final int HIDDEN_COLOR = 0xFF4A4A4A;
    private static final int FREE_COLOR = 0xFF5A5A5A;
    private static final int INFO_COLOR = 0xFFB0B0B0;

    private final Font font;

    public BoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(BoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = board.getBlockState().getValue(BoardBlock.FACING);
        int width = (int) (BunkerLayout.BOARD_WIDTH * PX_PER_BLOCK);
        int height = (int) (BunkerLayout.BOARD_HEIGHT * PX_PER_BLOCK);
        SyncPayload state = ClientPayloads.state();
        pose.pushPose();
        // Into the frame of the block (front = +z), then to the top-left corner of its front face, text y down.
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5F, 0.5F, 0.502F);
        pose.scale(1.0F / PX_PER_BLOCK, -1.0F / PX_PER_BLOCK, 1.0F / PX_PER_BLOCK);

        pose.pushPose();
        pose.translate(width / 2.0F, 8.0F, 0.0F);
        pose.scale(2.5F, 2.5F, 1.0F);
        Component title = Component.translatable("board.mafia_sector.title");
        draw(pose, buffers, title, -font.width(title) / 2.0F, 0.0F, TITLE_COLOR);
        pose.popPose();
        if (state.active()) {
            Component info = Component.translatable("board.mafia_sector.info", state.round(), state.totalRounds(),
                    Component.translatable(GamePhase.byOrdinal(state.phase()).key()), state.seats(),
                    state.players().stream().filter(SyncPayload.PlayerView::alive).count(), state.catastrophe());
            draw(pose, buffers, info, (width - font.width(info)) / 2.0F, 34.0F, INFO_COLOR);
        }

        int tileWidth = width / COLUMNS;
        int tileHeight = (height - TITLE_HEIGHT) / ROWS;
        for (int place = 0; place < BunkerLayout.MAX_PLAYERS; place++) {
            float x = (place % COLUMNS) * tileWidth + 6.0F;
            float y = TITLE_HEIGHT + (float) (place / COLUMNS) * tileHeight + 2.0F;
            SyncPayload.PlayerView player = playerAt(state, place);
            if (player == null) {
                draw(pose, buffers, Component.translatable("board.mafia_sector.seat", place + 1), x, y, FREE_COLOR);
                draw(pose, buffers, Component.translatable("board.mafia_sector.free"), x, y + LINE, FREE_COLOR);
                continue;
            }
            tile(pose, buffers, state, player, x, y, tileWidth - 12);
        }
        pose.popPose();
    }

    private void tile(PoseStack pose, MultiBufferSource buffers, SyncPayload state, SyncPayload.PlayerView player, float x, float y, int width) {
        boolean turn = state.currentTurn().map(player.uuid()::equals).orElse(false);
        String status = !player.alive() ? Component.translatable("board.mafia_sector.exiled").getString()
                : !player.online() ? Component.translatable("board.mafia_sector.offline").getString() : "";
        String head = (turn ? "▶ " : "") + "№" + (player.place() + 1) + " " + player.name() + status;
        draw(pose, buffers, font.plainSubstrByWidth(head, width), x, y, !player.alive() ? EXILED_COLOR : turn ? TURN_COLOR : SEAT_COLOR);
        int labelWidth = 0;
        for (CardType type : CardType.values()) {
            labelWidth = Math.max(labelWidth, font.width(Component.translatable(type.shortKey())));
        }
        for (CardType type : CardType.values()) {
            float ly = y + LINE * (type.ordinal() + 1);
            draw(pose, buffers, Component.translatable(type.shortKey()).getString(), x, ly, LABEL_COLOR);
            SyncPayload.CardView card = player.card(type.ordinal());
            String text = card != null ? card.text() : "▒▒▒▒▒▒";
            draw(pose, buffers, font.plainSubstrByWidth(text, width - labelWidth - 4), x + labelWidth + 4, ly, card != null ? CARD_COLOR : HIDDEN_COLOR);
        }
    }

    private static SyncPayload.PlayerView playerAt(SyncPayload state, int place) {
        if (!state.active()) return null;
        for (SyncPayload.PlayerView player : state.players()) {
            if (player.place() == place) return player;
        }
        return null;
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, Component text, float x, float y, int color) {
        font.drawInBatch(text, x, y, color, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, String text, float x, float y, int color) {
        font.drawInBatch(text, x, y, color, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    @Override
    public AABB getRenderBoundingBox(BoardBlockEntity board) {
        return new AABB(board.getBlockPos()).inflate(BunkerLayout.BOARD_WIDTH, BunkerLayout.BOARD_HEIGHT, BunkerLayout.BOARD_WIDTH);
    }
}
