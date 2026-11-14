package studio.detemkur.cardboardstands;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class StandGameTest {
    @GameTest
    public void recipeAndSupport(GameTestHelper test) {
        var level = test.getLevel();
        var recipe = ResourceKey.create(Registries.RECIPE, CardboardStands.id("stand"));
        test.assertTrue(level.recipeAccess().byKey(recipe).isPresent(), "Missing cardboard stand recipe");
        var pos = new BlockPos(1, 2, 1);
        test.setBlock(pos.below(), Blocks.OAK_PLANKS);
        var oldState = new CompoundTag();
        oldState.putString("id", "cardboardstands:stand");
        var properties = new CompoundTag();
        properties.putString("facing", "east");
        oldState.put("properties", properties);
        var migrated = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), oldState);
        test.assertTrue(migrated.is(CardboardStands.STAND) && !migrated.getValue(StandBlock.WALL)
                && migrated.getValue(StandBlock.TURN) == 0
                && migrated.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST,
                "The 1.0.0 block state did not migrate");
        test.setBlock(pos, migrated);
        test.assertBlockPresent(CardboardStands.STAND, pos);
        test.setBlock(pos.below(), Blocks.AIR);
        test.assertBlockPresent(Blocks.AIR, pos);
        test.succeed();
    }

    @GameTest
    public void pictureAndOwnerPersist(GameTestHelper test) {
        var pos = new BlockPos(1, 2, 1);
        test.setBlock(pos.below(), Blocks.OAK_PLANKS);
        test.setBlock(pos, CardboardStands.STAND);
        var level = test.getLevel();
        var stand = (StandEntity) level.getBlockEntity(test.absolutePos(pos));
        var owner = UUID.randomUUID();
        var id = "2".repeat(64);
        stand.setOwner(owner);
        stand.setPicture(id);
        stand.setAngle(225);
        var saved = stand.saveWithoutMetadata(level.registryAccess());
        var update = stand.getUpdateTag(level.registryAccess());
        test.assertTrue(update.getStringOr("picture", "").equals(id) && !update.contains("owner"),
                "Incorrect synchronized stand data");
        test.assertTrue(update.getIntOr("angle_offset", 0) == 225, "Missing synchronized angle");
        stand.setOwner(null);
        stand.setPicture("");
        stand.setAngle(0);
        stand.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        test.assertTrue(id.equals(stand.picture()) && owner.equals(stand.owner()) && stand.angle() == 225,
                "Lost stand data during save/load");
        test.succeed();
    }

    @GameTest
    public void mountAndRotateWithoutLosingPicture(GameTestHelper test) {
        var wall = new BlockPos(1, 2, 1);
        test.setBlock(wall, Blocks.OAK_PLANKS);
        var support = test.absolutePos(wall);
        var pos = support.north();
        var level = test.getLevel();
        var player = test.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atBottomCenterOf(pos.north()));
        player.setYRot(0);

        var block = (StandBlock) CardboardStands.STAND;
        var hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.NORTH, support, false);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(block), hit);
        var mount = block.getStateForPlacement(context);
        test.assertTrue(mount != null && mount.getValue(StandBlock.WALL)
                && mount.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH,
                "Clicking a wall did not select a wall mount");
        test.assertTrue(mount.canSurvive(level, pos), "Wall mount needs support at its back");
        level.setBlockAndUpdate(pos, mount);
        var entity = (StandEntity) level.getBlockEntity(pos);
        var picture = "f".repeat(64);
        entity.setOwner(UUID.randomUUID());
        entity.setPicture(picture);
        entity.setAngle(150);
        player.setShiftKeyDown(true);
        var standHit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        mount.useWithoutItem(level, player, standHit);
        test.assertTrue(level.getBlockState(pos).getValue(StandBlock.TURN) == 0,
                "Non-owner rotated the stand");
        entity.setOwner(player.getUUID());
        for (int turn = 1; turn <= 4; turn++) {
            level.getBlockState(pos).useWithoutItem(level, player, standHit);
            var rotated = level.getBlockState(pos);
            test.assertTrue(rotated.getValue(StandBlock.TURN) == turn % 4
                    && rotated.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH,
                    "Wall rotation changed the support face");
            test.assertTrue(level.getBlockEntity(pos) == entity && entity.picture().equals(picture),
                    "Rotation lost the image");
            test.assertTrue(entity.angle() == (150 + turn * 900) % 3600, "Rotation lost the custom angle");
        }
        test.setBlock(wall, Blocks.AIR);
        test.assertTrue(level.getBlockState(pos).isAir(), "Wall stand survived after removing its support");

        level.setBlockAndUpdate(pos.below(), Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        level.getBlockState(pos).useWithoutItem(level, player, standHit);
        test.assertTrue(level.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST,
                "Floor stand did not rotate clockwise");
        test.succeed();
    }
}
