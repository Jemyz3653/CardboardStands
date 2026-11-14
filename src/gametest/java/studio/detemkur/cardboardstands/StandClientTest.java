package studio.detemkur.cardboardstands;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class StandClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try {
            runUploadTest(context);
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }

    private void runUploadTest(ClientGameTestContext context) throws java.io.IOException {
        Path image = Files.createTempFile("cardboard-test-8k", ".png");
        Path invalid = Files.createTempFile("cardboard-test-invalid", ".png");
        try {
            writeImage(image);
            String hash = PngFile.verify(image);
            byte[] corrupt = Files.readAllBytes(image);
            corrupt[corrupt.length - 1] ^= 1;
            Files.write(invalid, corrupt);
            try {
                PngFile.verify(invalid);
                throw new AssertionError("PNG with broken CRC was accepted");
            } catch (java.io.IOException expected) {
                // Corrupt data should never be committed to the world.
            }
            context.runOnClient(mc -> {
                var models = mc.getModelManager().getBlockStateModelSet();
                for (var state : CardboardStands.STAND.getStateDefinition().getPossibleStates()) {
                    if (models.get(state) == models.missingModel()) throw new AssertionError("Missing model " + state);
                }
            });
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer();
                var pos = new BlockPos(2, 65, 4);
                server.runCommand("time set noon");
                server.runCommand("weather clear");
                server.runCommand("gamemode creative @a");
                server.runCommand("fill -2 64 0 6 64 9 minecraft:oak_planks");
                server.runCommand("fill -2 65 0 6 71 9 minecraft:air");
                server.runCommand("setblock 2 65 4 cardboardstands:stand[facing=north]");
                server.runCommand("tp @a 2.85 65 2.1 10 27");
                world.getConnection().waitForChunksRender();
                server.runOnServer(s -> {
                    var level = s.overworld();
                    var player = s.getPlayerList().getPlayers().getFirst();
                    var stand = (StandEntity) level.getBlockEntity(pos);
                    stand.setOwner(player.getUUID());
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
                    level.getBlockState(pos).useWithoutItem(level, player, hit);
                });
                context.waitFor(mc -> mc.gui.screen() instanceof StandScreen, 100);
                context.runOnClient(mc -> ((StandScreen) mc.gui.screen()).onFilesDrop(List.of(image)));
                context.waitFor(mc -> StandTextures.get(hash) != null, 600);
                context.runOnClient(mc -> {
                    var texture = StandTextures.get(hash);
                    if (texture.width() != 8192 || texture.height() != 8192) {
                        throw new AssertionError("The test renderer did not retain the 8192×8192 texture");
                    }
                });
                server.runOnServer(s -> {
                    Path stored = s.getWorldPath(LevelResource.ROOT).resolve("cardboardstands").resolve(hash + ".png");
                    if (!Files.isRegularFile(stored)) throw new AssertionError("PNG is missing from world storage");
                });
                context.takeScreenshot("cardboard-stands-editor");
                context.runOnClient(mc -> mc.gui.screen().onClose());
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
                context.waitTicks(6);
                context.takeScreenshot("cardboard-stands-front");
                server.runCommand("tp @a 3.6 65 6.6 153 26");
                world.getConnection().waitForChunksRender();
                context.waitTicks(6);
                context.takeScreenshot("cardboard-stands-back");
                server.runCommand("fill -1 65 5 5 68 5 minecraft:spruce_planks");
                server.runCommand("setblock 2 65 4 cardboardstands:stand[facing=north,wall=true]");
                server.runCommand("tp @a 2.5 65 2.6 0 23");
                context.waitFor(mc -> mc.level.getBlockState(pos).getValue(StandBlock.WALL), 100);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("cardboard-stands-wall");
                context.runOnClient(mc -> {
                    var hit = new BlockHitResult(new Vec3(2.5, 65.5, 4.9), Direction.NORTH, pos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                });
                context.waitFor(mc -> mc.gui.screen() instanceof StandScreen, 100);
                for (String angle : List.of("10", "15", "45", "22,5", "45")) {
                    context.runOnClient(mc -> {
                        var field = mc.gui.screen().children().stream().filter(EditBox.class::isInstance)
                                .map(EditBox.class::cast).reduce((path, degrees) -> degrees).orElseThrow();
                        field.setValue(angle);
                    });
                    context.clickScreenButton("Apply angle (°)");
                    int tenths = (int) (Float.parseFloat(angle.replace(',', '.')) * 10);
                    context.waitFor(mc -> mc.level.getBlockEntity(pos) instanceof StandEntity stand
                            && stand.angle() == tenths && stand.picture().equals(hash), 100);
                }
                context.takeScreenshot("cardboard-stands-angle-editor");
                context.runOnClient(mc -> mc.gui.screen().onClose());
                context.waitTicks(4);
                context.takeScreenshot("cardboard-stands-wall-45");
                var input = context.getInput();
                input.holdKey(options -> options.keyShift);
                try {
                    context.waitTicks(2);
                    context.runOnClient(mc -> {
                        var hit = new BlockHitResult(new Vec3(2.5, 65.5, 4.9), Direction.NORTH, pos, false);
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                    });
                    context.waitFor(mc -> mc.level.getBlockState(pos).getValue(StandBlock.TURN) == 1
                            && mc.level.getBlockEntity(pos) instanceof StandEntity stand
                            && stand.angle() == 1350
                            && stand.picture().equals(hash), 100);
                } finally {
                    input.releaseKey(options -> options.keyShift);
                }
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("cardboard-stands-wall-rotated");
                server.runCommand("setblock 2 65 4 cardboardstands:stand[facing=north]");
                context.waitFor(mc -> mc.level.getBlockState(pos).is(CardboardStands.STAND)
                        && !mc.level.getBlockState(pos).getValue(StandBlock.WALL), 100);
                context.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new StandPackets.Angle(pos, 450)));
                context.waitFor(mc -> mc.level.getBlockEntity(pos) instanceof StandEntity stand
                        && stand.angle() == 450 && stand.picture().equals(hash), 100);
                context.waitTicks(4);
                context.takeScreenshot("cardboard-stands-floor-45");
                context.runOnClient(mc -> {
                    var screen = new StandScreen(pos, hash);
                    try {
                        StandClient.start(screen, pos, null);
                    } catch (java.io.IOException error) {
                        throw new AssertionError(error);
                    }
                });
                context.waitFor(mc -> !StandClient.busy() && mc.level.getBlockEntity(pos) instanceof StandEntity stand
                        && stand.picture().isEmpty(), 100);
                System.out.println("CARDBOARD STANDS: 8192×8192 imported, rotated to exact angles on wall and floor, and removed.");
            }
        } finally {
            Files.deleteIfExists(image);
            Files.deleteIfExists(invalid);
        }
    }

    private static void writeImage(Path file) throws java.io.IOException {
        int size = 8192;
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var canvas = image.createGraphics();
        try {
            canvas.setColor(new java.awt.Color(0x25495a));
            canvas.fillRoundRect(128, 128, size - 256, size - 256, 1200, 1200);
            canvas.setColor(new java.awt.Color(0xf4c66a));
            canvas.fillOval(5700, 1050, 1300, 1300);
            canvas.setColor(new java.awt.Color(0x75a260));
            canvas.fillPolygon(new int[]{500, 3050, 4200, 5980, 7680, 7680, 500},
                    new int[]{6300, 2880, 4800, 3780, 6400, 7440, 7440}, 7);
            canvas.setColor(new java.awt.Color(0xe3f1c1));
            canvas.fillPolygon(new int[]{2350, 3050, 3700}, new int[]{3830, 2880, 3960}, 3);
        } finally {
            canvas.dispose();
        }
        if (!ImageIO.write(image, "png", file.toFile())) throw new java.io.IOException("No PNG writer");
        image.flush();
    }
}
