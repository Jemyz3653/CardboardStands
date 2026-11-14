package studio.detemkur.cardboardstands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import studio.detemkur.cardboardstands.StandPackets.Status;

final class StandServer {
    private static final int CHUNKS_PER_TICK = 8;
    private static final long TRANSFER_TIMEOUT = 60_000_000_000L;
    private static final Map<ServerPlayer, Upload> UPLOADS = new HashMap<>();
    private static final Map<ServerPlayer, Download> DOWNLOADS = new HashMap<>();

    private StandServer() {}

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(StandPackets.Start.TYPE,
                (packet, context) -> start(context.player(), packet));
        ServerPlayNetworking.registerGlobalReceiver(StandPackets.Upload.TYPE,
                (packet, context) -> upload(context.player(), packet.bytes()));
        ServerPlayNetworking.registerGlobalReceiver(StandPackets.Request.TYPE,
                (packet, context) -> request(context.player(), packet));
        ServerPlayNetworking.registerGlobalReceiver(StandPackets.Angle.TYPE,
                (packet, context) -> setAngle(context.player(), packet));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            UPLOADS.entrySet().removeIf(entry -> {
                var upload = entry.getValue();
                if (upload.stream == null || System.nanoTime() - upload.activity < TRANSFER_TIMEOUT) return false;
                upload.close();
                status(entry.getKey(), Status.IO_ERROR);
                return true;
            });
            DOWNLOADS.entrySet().removeIf(entry -> !send(entry.getKey(), entry.getValue()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            var upload = UPLOADS.remove(handler.player);
            if (upload != null) upload.close();
            var download = DOWNLOADS.remove(handler.player);
            if (download != null) download.close();
        }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            UPLOADS.values().forEach(Upload::close);
            UPLOADS.clear();
            DOWNLOADS.values().forEach(Download::close);
            DOWNLOADS.clear();
        });
    }

    static void openEditor(ServerPlayer player, BlockPos pos) {
        StandEntity stand = editable(player, pos);
        if (stand == null) {
            player.sendOverlayMessage(Component.translatable("cardboardstands.denied"));
            return;
        }
        if (ServerPlayNetworking.canSend(player, StandPackets.Open.TYPE)) {
            ServerPlayNetworking.send(player, new StandPackets.Open(pos, stand.picture()));
        }
    }

    static void turn(ServerPlayer player, BlockPos pos) {
        StandEntity stand = editable(player, pos);
        if (stand == null) {
            player.sendOverlayMessage(Component.translatable("cardboardstands.denied"));
            return;
        }
        var state = stand.getBlockState();
        var turned = state.getValue(StandBlock.WALL)
                ? state.setValue(StandBlock.TURN, (state.getValue(StandBlock.TURN) + 1) % 4)
                : state.rotate(Rotation.CLOCKWISE_90);
        player.level().setBlock(pos, turned, Block.UPDATE_ALL);
    }

    private static void setAngle(ServerPlayer player, StandPackets.Angle packet) {
        StandEntity stand = editable(player, packet.pos());
        if (stand == null) {
            player.sendOverlayMessage(Component.translatable("cardboardstands.denied"));
        } else if (packet.tenths() >= 0 && packet.tenths() < StandEntity.FULL_TURN && stand.angle() != packet.tenths()) {
            stand.setAngle(packet.tenths());
        }
    }

    private static StandEntity editable(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        if (!player.getAbilities().mayBuild || !player.isWithinBlockInteractionRange(pos, 0)
                || !level.hasChunkAt(pos) || !player.mayInteract(level, pos)) return null;
        if (!(level.getBlockEntity(pos) instanceof StandEntity stand)) return null;
        if (stand.owner() != null && !stand.owner().equals(player.getUUID())
                && !level.getServer().getPlayerList().isOp(player.nameAndId())) return null;
        return stand;
    }

    private static Path directory(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("cardboardstands");
    }

    private static void start(ServerPlayer player, StandPackets.Start packet) {
        if (UPLOADS.containsKey(player)) {
            status(player, Status.BUSY);
            return;
        }
        StandEntity stand = editable(player, packet.pos());
        if (stand == null) {
            status(player, Status.DENIED);
            return;
        }
        if (packet.size() == 0) {
            stand.setPicture("");
            status(player, Status.CLEARED);
            return;
        }
        if (packet.size() > PngFile.MAX_BYTES || packet.size() < 45) {
            status(player, Status.BAD_PNG);
            return;
        }
        Path temp = null;
        try {
            var directory = Files.createDirectories(directory(player.level()));
            temp = Files.createTempFile(directory, "pending-", ".tmp");
            UPLOADS.put(player, new Upload(player.level(), stand, temp, packet.size(), Files.newOutputStream(temp)));
            status(player, Status.READY);
        } catch (IOException e) {
            delete(temp);
            status(player, Status.IO_ERROR);
        }
    }

    private static void upload(ServerPlayer player, byte[] bytes) {
        Upload transfer = UPLOADS.get(player);
        if (transfer == null || transfer.stream == null) return;
        if (player.level() != transfer.level || editable(player, transfer.stand.getBlockPos()) != transfer.stand
                || bytes.length == 0 || bytes.length > transfer.remaining) {
            UPLOADS.remove(player);
            transfer.close();
            status(player, Status.DENIED);
            return;
        }
        try {
            transfer.stream.write(bytes);
            transfer.remaining -= bytes.length;
            transfer.activity = System.nanoTime();
            if (transfer.remaining != 0) return;
            transfer.stream.close();
            transfer.stream = null;
        } catch (IOException e) {
            UPLOADS.remove(player);
            transfer.close();
            status(player, Status.IO_ERROR);
            return;
        }

        var server = transfer.level.getServer();
        CompletableFuture.supplyAsync(() -> {
            try {
                return PngFile.verify(transfer.file);
            } catch (IOException invalid) {
                return "";
            }
        }).whenComplete((id, error) -> {
            if (server.isStopped()) {
                delete(transfer.file);
                return;
            }
            server.execute(() -> finish(player, transfer, error == null ? id : ""));
        });
    }

    private static void finish(ServerPlayer player, Upload transfer, String id) {
        try {
            if (!UPLOADS.remove(player, transfer)) return;
            if (player.level() != transfer.level || editable(player, transfer.stand.getBlockPos()) != transfer.stand) {
                status(player, Status.DENIED);
                return;
            }
            if (id.isEmpty()) {
                status(player, Status.BAD_PNG);
                return;
            }
            Path image = directory(transfer.level).resolve(id + ".png");
            if (!Files.exists(image)) Files.move(transfer.file, image, StandardCopyOption.REPLACE_EXISTING);
            transfer.stand.setPicture(id);
            status(player, Status.SAVED);
        } catch (IOException e) {
            status(player, Status.IO_ERROR);
        } finally {
            delete(transfer.file);
        }
    }

    private static void request(ServerPlayer player, StandPackets.Request packet) {
        var level = player.level();
        var pos = packet.pos();
        if (!PngFile.isId(packet.id()) || !ServerPlayNetworking.canSend(player, StandPackets.Image.TYPE)
                || DOWNLOADS.containsKey(player) || !viewable(player, level, pos)
                || !(level.getBlockEntity(pos) instanceof StandEntity stand)
                || !stand.picture().equals(packet.id())) return;
        try {
            var file = directory(level).resolve(packet.id() + ".png");
            long size = Files.size(file);
            if (size >= 45 && size <= PngFile.MAX_BYTES) {
                DOWNLOADS.put(player, new Download(level, pos, packet.id(), (int) size, Files.newInputStream(file)));
            }
        } catch (IOException e) {
            // A moved or missing image leaves the stand's blank front visible.
        }
    }

    private static boolean viewable(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.level() == level && player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= 72 * 72
                && level.hasChunkAt(pos);
    }

    private static boolean send(ServerPlayer player, Download download) {
        if (viewable(player, download.level, download.pos)) {
            try {
                for (int i = 0; i < CHUNKS_PER_TICK && download.remaining > 0; i++) {
                    int wanted = Math.min(download.remaining, StandPackets.CHUNK);
                    byte[] bytes = download.stream.readNBytes(wanted);
                    if (bytes.length != wanted) break;
                    ServerPlayNetworking.send(player, new StandPackets.Image(download.id, download.size, bytes));
                    download.remaining -= bytes.length;
                }
                if (download.remaining == 0) {
                    download.close();
                    return false;
                }
                if (download.stream.available() > 0) return true;
            } catch (IOException e) {
                CardboardStands.LOG.debug("Could not send stand image", e);
            }
        }
        download.close();
        return false;
    }

    private static void status(ServerPlayer player, int code) {
        if (ServerPlayNetworking.canSend(player, Status.TYPE)) {
            ServerPlayNetworking.send(player, new Status(code));
        }
    }

    private static void delete(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            CardboardStands.LOG.debug("Could not delete temporary stand image", e);
        }
    }

    private static final class Upload {
        final ServerLevel level;
        final StandEntity stand;
        final Path file;
        OutputStream stream;
        int remaining;
        long activity = System.nanoTime();

        Upload(ServerLevel level, StandEntity stand, Path file, int remaining, OutputStream stream) {
            this.level = level;
            this.stand = stand;
            this.file = file;
            this.remaining = remaining;
            this.stream = stream;
        }

        void close() {
            try {
                if (stream != null) stream.close();
            } catch (IOException error) {
                CardboardStands.LOG.debug("Could not close stand upload", error);
            }
            delete(file);
        }
    }

    private static final class Download {
        final ServerLevel level;
        final BlockPos pos;
        final String id;
        final int size;
        final InputStream stream;
        int remaining;

        Download(ServerLevel level, BlockPos pos, String id, int size, InputStream stream) {
            this.level = level;
            this.pos = pos;
            this.id = id;
            this.size = size;
            this.remaining = size;
            this.stream = stream;
        }

        void close() {
            try {
                stream.close();
            } catch (IOException error) {
                CardboardStands.LOG.debug("Could not close stand download", error);
            }
        }
    }
}
