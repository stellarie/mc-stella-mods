package com.stella.learn;

import com.stella.learn.gym.Arena;
import com.stella.learn.gym.Bot;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** An arena is rebuilt exactly, whatever a fight did to it. */
public class GymArenaGameTest {
	private static final int INDEX = 917;

	private static BlockState expected(final int dy) {
		if (dy >= 0) {
			return Blocks.AIR.defaultBlockState();
		}

		return dy >= -2 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	private static int stray(final ServerLevel level, final AABB box) {
		return level.getEntities((Entity) null, box, e -> !(e instanceof Player)).size();
	}

	@GameTest(maxTicks = 600, padding = LearnArena.PADDING)
	public void resetRestoresTheArena(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(0, 20, 0));
		// The origin sits mid-chunk, so the arena spans a chunk border on both axes.
		BlockPos origin = new BlockPos((base.getX() & ~15) + 8, base.getY(), (base.getZ() & ~15) + 8);
		for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + Arena.OPEN) >> 4; cx++) {
			for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + Arena.OPEN) >> 4; cz++) {
				int x = cx;
				int z = cz;
				level.setChunkForced(x, z, true);
				level.getChunk(x, z);
				helper.runBeforeTestEnd(() -> level.setChunkForced(x, z, false));
			}
		}

		helper.assertTrue((origin.getX() >> 4) != ((origin.getX() + Arena.OPEN - 1) >> 4), "the arena does not cross a chunk border");
		Arena arena = new Arena(INDEX, level, origin);
		arena.build();
		Bot bot = Bot.spawn(level, "gym-arena-bot", new Vec3(origin.getX() + 8.5, origin.getY(), origin.getZ() + 8.5), 0.0F, INDEX);
		helper.runBeforeTestEnd(bot::remove);
		BlockPos hole = origin.offset(8, -1, 10);
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			bot.startBreaking(hole);
			LearnArena.pollUntil(helper, () -> level.getBlockState(hole).isAir(), 100, () -> {
				level.setBlock(origin.offset(2, -2, 2), Blocks.AIR.defaultBlockState(), 2);
				level.setBlock(origin.offset(3, 4, 3), Blocks.STONE.defaultBlockState(), 2);
				EntityTypes.ZOMBIE.spawn(level, origin.offset(12, 0, 12), EntitySpawnReason.COMMAND);
				EntityTypes.ARROW.spawn(level, origin.offset(6, 3, 6), EntitySpawnReason.COMMAND);
				level.addFreshEntity(new ItemEntity(level, origin.getX() + 5.5, origin.getY() + 1.0, origin.getZ() + 5.5, new ItemStack(Items.DIRT)));
				AABB box = arena.bounds().expandTowards(0.0, -Arena.FLOOR, 0.0);
				int before = stray(level, box);
				helper.assertTrue(before >= 3, "only " + before + " entities stand in the arena before the reset");
				arena.reset();
				int wrong = 0;
				for (int dx = 0; dx < Arena.OPEN; dx++) {
					for (int dy = -Arena.FLOOR; dy < Arena.OPEN; dy++) {
						for (int dz = 0; dz < Arena.OPEN; dz++) {
							if (level.getBlockState(origin.offset(dx, dy, dz)) != expected(dy)) {
								wrong++;
							}
						}
					}
				}

				int after = stray(level, box);
				List<String> resets = LearnTestInit.linesWith("gymArena RESET", "arena=" + INDEX + " ");
				StellaLearn.LOGGER.info("[BEHAVIOR] test=resetRestoresTheArena RESULT wrongBlocks={} entitiesBefore={} entitiesAfter={} resetLines={}", wrong, before, after, resets.size());
				helper.assertTrue(wrong == 0, wrong + " blocks differ from the built arena after the reset");
				helper.assertTrue(after == 0, after + " entities remain after the reset");
				helper.assertTrue(resets.size() == 1, "the reset wrote " + resets.size() + " gymArena RESET lines");
				helper.assertTrue(bot.player().isAlive(), "the reset removed the bot");
				helper.succeed();
			}, () -> "the bot never broke the floor block");
		});
	}
}
