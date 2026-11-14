package studio.detemkur.cardboardstands;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class StandBlock extends Block implements EntityBlock {
    static final BooleanProperty WALL = BooleanProperty.create("wall");
    static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);

    StandBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(WALL, false).setValue(TURN, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, WALL, TURN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.DOWN) return null;
        boolean wall = face.getAxis().isHorizontal();
        Direction facing = wall ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(WALL, wall);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction supportDirection = supportDirection(state);
        BlockPos support = pos.relative(supportDirection);
        return level.getBlockState(support).isFaceSturdy(level, support, supportDirection.getOpposite());
    }

    private static Direction supportDirection(BlockState state) {
        return state.getValue(WALL) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() : Direction.DOWN;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighbor, RandomSource random) {
        if (direction == supportDirection(state) && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = rotate(state, mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        return mirror != Mirror.NONE && state.getValue(WALL)
                ? mirrored.setValue(TURN, (4 - state.getValue(TURN)) % 4) : mirrored;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StandEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack item) {
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof StandEntity stand) {
            stand.setOwner(player.getUUID());
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (state.getValue(WALL)) {
            double rim = 2;
            if (level.getBlockEntity(pos) instanceof StandEntity stand) {
                double radians = Math.toRadians(stand.angle() / 10.0);
                rim = 8 - Math.min(7.9, 6 * (Math.abs(Math.cos(radians)) + Math.abs(Math.sin(radians))));
            }
            double end = 16 - rim;
            return switch (facing) {
                case EAST -> box(0, rim, rim, 1.65, end, end);
                case SOUTH -> box(rim, rim, 0, end, end, 1.65);
                case WEST -> box(14.35, rim, rim, 16, end, end);
                default -> box(rim, rim, 14.35, end, end, 16);
            };
        }
        if (level.getBlockEntity(pos) instanceof StandEntity stand && stand.angle() % StandEntity.QUARTER_TURN != 0) {
            double angle = Math.toRadians(stand.angle() / 10.0);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double centerX = 8 - 0.25 * sin;
            double centerZ = 8 + 0.25 * cos;
            double width = 6.5 * Math.abs(cos) + 4.75 * Math.abs(sin);
            double depth = 6.5 * Math.abs(sin) + 4.75 * Math.abs(cos);
            return box(Math.max(0, centerX - width), 0, Math.max(0, centerZ - depth),
                    Math.min(16, centerX + width), 14, Math.min(16, centerZ + depth));
        }
        if (level.getBlockEntity(pos) instanceof StandEntity stand) {
            facing = Direction.from2DDataValue(stand.angle() / StandEntity.QUARTER_TURN + 2);
        }
        return switch (facing) {
            case EAST -> box(3, 0, 1.5, 12.5, 14, 14.5);
            case SOUTH -> box(1.5, 0, 3, 14.5, 14, 12.5);
            case WEST -> box(3.5, 0, 1.5, 13, 14, 14.5);
            default -> box(1.5, 0, 3.5, 14.5, 14, 13);
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) StandServer.turn(serverPlayer, pos);
            else StandServer.openEditor(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
