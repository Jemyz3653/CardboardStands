package studio.detemkur.cardboardstands;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class StandEntity extends BlockEntity {
    static final int FULL_TURN = 3600;
    static final int QUARTER_TURN = 900;
    private String picture = "";
    private UUID owner;
    private int angleOffset;

    StandEntity(BlockPos pos, BlockState state) {
        super(CardboardStands.STAND_ENTITY, pos, state);
    }

    public String picture() {
        return picture;
    }

    UUID owner() {
        return owner;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    void setPicture(String picture) {
        this.picture = picture;
        sync();
    }

    int angle() {
        return Math.floorMod(baseAngle() + angleOffset, FULL_TURN);
    }

    void setAngle(int angle) {
        angleOffset = Math.floorMod(angle - baseAngle(), FULL_TURN);
        sync();
    }

    float offsetDegrees() {
        return angleOffset / 10F;
    }

    private int baseAngle() {
        var state = getBlockState();
        if (state.getValue(StandBlock.WALL)) return state.getValue(StandBlock.TURN) * QUARTER_TURN;
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return ((facing.get2DDataValue() + 2) % 4) * QUARTER_TURN;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String saved = input.getStringOr("picture", "");
        picture = PngFile.isId(saved) ? saved : "";
        angleOffset = Math.floorMod(input.getIntOr("angle_offset", 0), FULL_TURN);
        owner = input.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!picture.isEmpty()) output.putString("picture", picture);
        if (angleOffset != 0) output.putInt("angle_offset", angleOffset);
        if (owner != null) output.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("picture", picture);
        if (angleOffset != 0) tag.putInt("angle_offset", angleOffset);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
