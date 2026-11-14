package studio.detemkur.cardboardstands;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class StandScreen extends Screen {
    private final BlockPos pos;
    private String picture;
    private EditBox path;
    private EditBox angle;
    private Button load;
    private Button clear;
    private Button applyAngle;
    private int lastAngle = -1;
    private Component status = Component.empty();

    StandScreen(BlockPos pos, String picture) {
        super(Component.translatable("cardboardstands.editor"));
        this.pos = pos;
        this.picture = picture;
    }

    @Override
    protected void init() {
        String oldPath = path == null ? "" : path.getValue();
        String oldAngle = angle == null ? "" : angle.getValue();
        int x = width / 2;
        int y = height / 2 - 13;
        path = new EditBox(font, x - 117, y + 16, 234, 20, Component.translatable("cardboardstands.path"));
        path.setHint(Component.translatable("cardboardstands.path"));
        path.setMaxLength(4096);
        path.setValue(oldPath);
        addRenderableWidget(path);
        load = addRenderableWidget(Button.builder(Component.translatable("cardboardstands.load"), button -> loadPath())
                .bounds(x - 117, y + 42, 112, 20).build());
        clear = addRenderableWidget(Button.builder(Component.translatable("cardboardstands.clear"), button -> upload(null))
                .bounds(x + 5, y + 42, 112, 20).build());
        angle = new EditBox(font, x - 117, y + 68, 78, 20, Component.translatable("cardboardstands.angle"));
        angle.setMaxLength(8);
        angle.setValue(oldAngle);
        angle.setHint(Component.translatable("cardboardstands.angle"));
        addRenderableWidget(angle);
        applyAngle = addRenderableWidget(Button.builder(Component.translatable("cardboardstands.apply_angle"),
                button -> applyAngle()).bounds(x - 33, y + 68, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x - 49, y + 109, 98, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = width / 2;
        int y = height / 2 - 13;
        graphics.fill(x - 130, y - 104, x + 130, y + 134, 0xff2a231e);
        graphics.fill(x - 129, y - 103, x + 129, y + 133, 0xff3b3028);
        graphics.centeredText(font, title, x, y - 94, 0xffffe1b0);
        graphics.fill(x - 117, y - 77, x + 117, y + 10, 0xff1c1714);
        if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof StandEntity stand) {
            picture = stand.picture();
            int current = stand.angle();
            if (lastAngle != current) {
                lastAngle = current;
                if (!angle.isFocused()) angle.setValue(BigDecimal.valueOf(current, 1).stripTrailingZeros().toPlainString());
            }
        }
        var image = StandTextures.get(picture);
        if (image != null) {
            float scale = Math.min(228F / image.width(), 81F / image.height());
            int w = Math.max(1, Math.round(image.width() * scale));
            int h = Math.max(1, Math.round(image.height() * scale));
            graphics.blit(image.name(), x - w / 2, y - 34 - h / 2, x - w / 2 + w, y - 34 - h / 2 + h,
                    0F, 1F, 0F, 1F);
        } else {
            StandTextures.request(pos, picture);
            graphics.centeredText(font, Component.translatable("cardboardstands.drop"), x, y - 44, 0xffffe1b0);
            graphics.centeredText(font, Component.translatable("cardboardstands.limit"), x, y - 30, 0xffc9b8a4);
        }
        int progress = StandClient.progress(this);
        Component message = progress < 0 ? status : Component.translatable("cardboardstands.progress", progress);
        graphics.centeredText(font, message, x, y + 94, 0xffffe1b0);
        load.active = !StandClient.busy();
        clear.active = load.active && !picture.isEmpty();
        applyAngle.active = lastAngle >= 0 && ClientPlayNetworking.canSend(StandPackets.Angle.TYPE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (files.size() == 1) {
            path.setValue(files.getFirst().toString());
            upload(files.getFirst());
        } else setStatus(Component.translatable("cardboardstands.one_png"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void setStatus(Component text) {
        status = text;
    }

    void clearPreview() {
        picture = "";
    }

    private void applyAngle() {
        try {
            String value = angle.getValue().strip().replace(',', '.');
            if (!value.matches("[0-9]{1,3}(\\.[0-9])?")) throw new NumberFormatException();
            int tenths = new BigDecimal(value).movePointRight(1).intValueExact();
            if (tenths > StandEntity.FULL_TURN) throw new NumberFormatException();
            ClientPlayNetworking.send(new StandPackets.Angle(pos, tenths % StandEntity.FULL_TURN));
            setStatus(Component.empty());
        } catch (NumberFormatException | ArithmeticException error) {
            setStatus(Component.translatable("cardboardstands.bad_angle"));
        }
    }

    private void loadPath() {
        String value = path.getValue().strip();
        if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            upload(Path.of(value));
        } catch (InvalidPathException error) {
            setStatus(Component.translatable("cardboardstands.bad_png"));
        }
    }

    private void upload(Path file) {
        if (StandClient.busy()) return;
        try {
            if (file != null) {
                if (!Files.isRegularFile(file)) throw new IOException("Not a local file");
                PngFile.header(file);
            }
            StandClient.start(this, pos, file);
            setStatus(Component.translatable("cardboardstands.uploading"));
        } catch (IOException error) {
            setStatus(Component.translatable(file == null ? "cardboardstands.io_error" : "cardboardstands.bad_png"));
        }
    }
}
