package studio.detemkur.cardboardstands;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

final class StandRenderer implements BlockEntityRenderer<StandEntity, StandRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private final BlockEntityRendererProvider.Context context;

    StandRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    static final class State extends BlockEntityRenderState {
        final BlockModelRenderState frame = new BlockModelRenderState();
        float yaw;
        boolean wall;
        float roll;
        StandTextures.Texture image;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StandEntity stand, State state, float partialTick,
                                   Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(stand, state, partialTick, camera, crumble);
        var block = stand.getBlockState();
        Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        state.wall = block.getValue(StandBlock.WALL);
        state.yaw = 180F - facing.toYRot() - (state.wall ? 0 : stand.offsetDegrees());
        state.roll = state.wall ? stand.angle() / 10F : 0;
        var model = block.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(StandBlock.TURN, 0);
        context.blockModelResolver().update(state.frame, model, DISPLAY);
        state.image = StandTextures.get(stand.picture());
        if (state.image == null && !stand.picture().isEmpty()) StandTextures.request(stand.getBlockPos(), stand.picture());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.rotateDegrees(Axis.YP, state.yaw);
        if (state.wall) {
            double rad = Math.toRadians(state.roll);
            float fit = (float) Math.min(1, 7.9 / (6 * (Math.abs(Math.cos(rad)) + Math.abs(Math.sin(rad)))));
            pose.translate(0, 0.5, 0);
            pose.rotateDegrees(Axis.ZP, state.roll);
            pose.scale(fit, fit, 1);
            pose.translate(0, -0.5, 0);
        }
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        state.frame.submit(pose, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        if (state.image != null) submitImage(state, pose, nodes);
        pose.popPose();
    }

    private static void submitImage(State state, PoseStack pose, SubmitNodeCollector nodes) {
        var image = state.image;
        float scale = Math.min(10.5F / image.width(), (state.wall ? 10.5F : 10.7F) / image.height());
        float halfWidth = image.width() * scale / 32F;
        float halfHeight = image.height() * scale / 32F;
        int light = state.lightCoords;
        if (state.wall) {
            pose.translate(0, 0.5, 6.35 / 16);
        } else {
            pose.translate(0, 0, -3.5 / 16);
            pose.rotateDegrees(Axis.XP, 15F);
            pose.translate(0, 7.65 / 16, -0.70 / 16);
        }
        nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(image.name()), (matrix, mesh) -> {
            vertex(mesh, matrix, halfWidth, -halfHeight, 0, 1, light);
            vertex(mesh, matrix, -halfWidth, -halfHeight, 1, 1, light);
            vertex(mesh, matrix, -halfWidth, halfHeight, 1, 0, light);
            vertex(mesh, matrix, halfWidth, halfHeight, 0, 0, light);
        });
    }

    private static void vertex(VertexConsumer mesh, PoseStack.Pose matrix, float x, float y, int u, int v, int light) {
        mesh.addVertex(matrix, x, y, 0).setColor(0xffffffff).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix, 0, 0, -1);
    }
}
