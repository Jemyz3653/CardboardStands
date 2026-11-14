package studio.detemkur.cardboardstands;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class StandPackets {
    public static final int CHUNK = 32 * 1024;

    private StandPackets() {}

    static void register() {
        var toServer = PayloadTypeRegistry.serverboundPlay();
        toServer.register(Start.TYPE, Start.CODEC);
        toServer.register(Upload.TYPE, Upload.CODEC);
        toServer.register(Request.TYPE, Request.CODEC);
        toServer.register(Angle.TYPE, Angle.CODEC);
        var toClient = PayloadTypeRegistry.clientboundPlay();
        toClient.register(Open.TYPE, Open.CODEC);
        toClient.register(Status.TYPE, Status.CODEC);
        toClient.register(Image.TYPE, Image.CODEC);
    }

    public record Start(BlockPos pos, int size) implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(CardboardStands.id("start"));
        static final StreamCodec<FriendlyByteBuf, Start> CODEC = CustomPacketPayload.codec(
                (p, buf) -> { buf.writeBlockPos(p.pos); buf.writeVarInt(p.size); },
                buf -> new Start(buf.readBlockPos(), buf.readVarInt()));
        @Override public Type<Start> type() { return TYPE; }
    }

    public record Upload(byte[] bytes) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = new Type<>(CardboardStands.id("upload"));
        static final StreamCodec<FriendlyByteBuf, Upload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.bytes), buf -> new Upload(buf.readByteArray(CHUNK)));
        @Override public Type<Upload> type() { return TYPE; }
    }

    public record Request(BlockPos pos, String id) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CardboardStands.id("request"));
        static final StreamCodec<FriendlyByteBuf, Request> CODEC = CustomPacketPayload.codec(
                (p, buf) -> { buf.writeBlockPos(p.pos); buf.writeUtf(p.id, 64); },
                buf -> new Request(buf.readBlockPos(), buf.readUtf(64)));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Open(BlockPos pos, String id) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(CardboardStands.id("open"));
        static final StreamCodec<FriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
                (p, buf) -> { buf.writeBlockPos(p.pos); buf.writeUtf(p.id, 64); },
                buf -> new Open(buf.readBlockPos(), buf.readUtf(64)));
        @Override public Type<Open> type() { return TYPE; }
    }

    public record Angle(BlockPos pos, int tenths) implements CustomPacketPayload {
        public static final Type<Angle> TYPE = new Type<>(CardboardStands.id("angle"));
        static final StreamCodec<FriendlyByteBuf, Angle> CODEC = CustomPacketPayload.codec(
                (p, buf) -> { buf.writeBlockPos(p.pos); buf.writeVarInt(p.tenths); },
                buf -> new Angle(buf.readBlockPos(), buf.readVarInt()));
        @Override public Type<Angle> type() { return TYPE; }
    }

    public record Status(int code) implements CustomPacketPayload {
        public static final int READY = 0, SAVED = 1, CLEARED = 2, DENIED = 3, BAD_PNG = 4, BUSY = 5, IO_ERROR = 6;
        public static final Type<Status> TYPE = new Type<>(CardboardStands.id("status"));
        static final StreamCodec<FriendlyByteBuf, Status> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.code), buf -> new Status(buf.readVarInt()));
        @Override public Type<Status> type() { return TYPE; }
    }

    public record Image(String id, int size, byte[] bytes) implements CustomPacketPayload {
        public static final Type<Image> TYPE = new Type<>(CardboardStands.id("image"));
        static final StreamCodec<FriendlyByteBuf, Image> CODEC = CustomPacketPayload.codec(
                (p, buf) -> { buf.writeUtf(p.id, 64); buf.writeVarInt(p.size); buf.writeByteArray(p.bytes); },
                buf -> new Image(buf.readUtf(64), buf.readVarInt(), buf.readByteArray(CHUNK)));
        @Override public Type<Image> type() { return TYPE; }
    }
}
