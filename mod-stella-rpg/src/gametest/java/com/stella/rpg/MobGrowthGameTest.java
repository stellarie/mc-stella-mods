package com.stella.rpg;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Another mod grows a mob's stats through {@link MobGrowth}: half points add up to whole stat points, a grant through
 * the object share picks one of the six stats, and the growth is saved with the mob.
 */
public final class MobGrowthGameTest {
	@GameTest
	public void halfPointsAddUp(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		int before = MobStats.base(zombie, Stat.STR);
		MobGrowth.grow(zombie, Stat.STR, 0.5);
		int afterOne = MobStats.base(zombie, Stat.STR);
		MobGrowth.grow(zombie, Stat.STR, 0.5);
		int afterTwo = MobStats.base(zombie, Stat.STR);
		RpgTestSupport.result("mobGrowthHalves", "before=" + before + " afterOne=" + afterOne + " afterTwo=" + afterTwo);
		helper.assertTrue(afterOne == before && afterTwo == before + 1,
			"two half points gave STR " + before + " -> " + afterOne + " -> " + afterTwo);
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("unchecked")
	public void shareGrowsARandomStat(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		Object shared = FabricLoader.getInstance().getObjectShare().get(MobGrowth.SHARE_KEY);
		helper.assertTrue(shared instanceof BiFunction, "no grow function at " + MobGrowth.SHARE_KEY + ": " + shared);
		String key = ((BiFunction<Mob, Double, String>) shared).apply(zombie, 0.5);
		Map<String, Double> totals = zombie.getAttachedOrElse(MobGrowth.ATTACHMENT, Map.of());
		RpgTestSupport.result("mobGrowthShare", "key=" + key + " totals=" + totals);
		boolean known = Arrays.stream(Stat.values()).anyMatch(stat -> stat.key().equals(key));
		helper.assertTrue(known && totals.getOrDefault(key, 0.0) == 0.5, "the share grew " + key + " to " + totals);
		helper.succeed();
	}

	@GameTest
	public void growthIsSaved(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		MobGrowth.grow(zombie, Stat.VIT, 2.5);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		zombie.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		Zombie loaded = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.LOAD);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
		int bonus = MobGrowth.bonus(loaded, Stat.VIT);
		RpgTestSupport.result("mobGrowthSaved", "bonus=" + bonus);
		helper.assertTrue(bonus == 2, "a saved 2.5 VIT loaded as " + bonus);
		helper.succeed();
	}

	private static Zombie zombie(final GameTestHelper helper) {
		return helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 2.0, 1.5));
	}
}
