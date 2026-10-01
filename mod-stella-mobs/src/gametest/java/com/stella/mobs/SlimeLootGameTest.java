package com.stella.mobs;

import com.stella.mobs.entity.StellaEntities;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The colored slimes and the skeleton worker drop what their tables say, in the 26.3 loot format. */
public class SlimeLootGameTest {
	private static final int ROLLS = 400;

	@GameTest
	public void slimeLoot(final GameTestHelper helper) {
		int[] blue = balls(helper, StellaEntities.BLUE_SLIME);
		int[] white = balls(helper, StellaEntities.WHITE_SLIME);
		int[] black = balls(helper, StellaEntities.BLACK_SLIME);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=slimeLoot RESULT blue={} white={} black={}",
			java.util.Arrays.toString(blue), java.util.Arrays.toString(white), java.util.Arrays.toString(black));
		helper.assertTrue(blue[0] == 0 && blue[1] == 2 && blue[2] == 0, "blue slime balls ranged " + blue[0] + " to " + blue[1] + ", books " + blue[2]);
		for (int[] hostile : List.of(white, black)) {
			helper.assertTrue(hostile[0] == 1 && hostile[1] == 3, "hostile slime balls ranged " + hostile[0] + " to " + hostile[1]);
			helper.assertTrue(hostile[2] >= 60 && hostile[2] <= 140 && hostile[3] == hostile[2],
				"books " + hostile[2] + " of " + ROLLS + ", enchanted " + hostile[3]);
		}
		helper.succeed();
	}

	@GameTest
	public void skeletonWorkerLoot(final GameTestHelper helper) {
		int rolls = 2000;
		int coalRolls = 0;
		int diamonds = 0;
		Mob worker = mob(helper, StellaEntities.SKELETON_WORKER);
		for (int roll = 0; roll < rolls; roll++) {
			List<ItemStack> drops = roll(helper, worker);
			if (count(drops, Items.COAL) == 1) {
				coalRolls++;
			}
			diamonds += count(drops, Items.DIAMOND);
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=skeletonWorkerLoot RESULT rolls={} coal={} diamonds={}", rolls, coalRolls, diamonds);
		helper.assertTrue(coalRolls == rolls, "coal fell in " + coalRolls + " of " + rolls + " rolls");
		helper.assertTrue(diamonds >= 5 && diamonds <= 45, "diamonds fell " + diamonds + " times in " + rolls + " rolls");
		helper.succeed();
	}

	/** Lowest and highest slime ball count, books, and enchanted books over the rolls. */
	private static int[] balls(final GameTestHelper helper, final EntityType<? extends Mob> type) {
		Mob mob = mob(helper, type);
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		int books = 0;
		int enchanted = 0;
		for (int roll = 0; roll < ROLLS; roll++) {
			List<ItemStack> drops = roll(helper, mob);
			int balls = count(drops, Items.SLIME_BALL);
			low = Math.min(low, balls);
			high = Math.max(high, balls);
			books += count(drops, Items.BOOK) + count(drops, Items.ENCHANTED_BOOK);
			enchanted += count(drops, Items.ENCHANTED_BOOK);
		}
		return new int[] {low, high, books, enchanted};
	}

	private static Mob mob(final GameTestHelper helper, final EntityType<? extends Mob> type) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
		mob.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)), 0.0F, 0.0F);
		return mob;
	}

	private static List<ItemStack> roll(final GameTestHelper helper, final Mob mob) {
		ServerLevel level = helper.getLevel();
		List<ItemStack> drops = new ArrayList<>();
		mob.dropFromLootTable(level, level.damageSources().generic(), false, mob.getLootTable().orElseThrow(), drops::add);
		return drops;
	}

	private static int count(final List<ItemStack> drops, final Item item) {
		return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}
}
