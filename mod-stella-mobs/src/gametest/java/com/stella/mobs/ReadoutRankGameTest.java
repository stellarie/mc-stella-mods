package com.stella.mobs;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.gear.GearTier;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

public class ReadoutRankGameTest {
	private static final String RANK_KEY = "mod-stella-mobs:readout-rank";

	@GameTest
	public void normalMobKeepsItsAttachmentAbsent(final GameTestHelper helper) {
		Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.SPAWNER);
		if (zombie == null) {
			throw new AssertionError("the test zombie could not be created");
		}

		EliteState before = ((AttachmentTarget) zombie).getAttached(EliteState.TYPE);
		String rank = provider(helper).apply(zombie);
		EliteState after = ((AttachmentTarget) zombie).getAttached(EliteState.TYPE);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=readoutRankNormal RESULT rank={} stateBefore={} stateAfter={}",
			rank, before != null, after != null
		);
		helper.assertTrue("normal".equals(rank), "an unmarked zombie returned rank " + rank);
		helper.assertTrue(before == null && after == null, "the normal-rank lookup created elite state");
		helper.succeed();
	}

	@GameTest
	public void nonMobEntityReturnsNormal(final GameTestHelper helper) {
		Entity armorStand = EntityTypes.ARMOR_STAND.create(helper.getLevel(), EntitySpawnReason.SPAWNER);
		if (armorStand == null) {
			throw new AssertionError("the test armor stand could not be created");
		}

		String rank = provider(helper).apply(armorStand);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=readoutRankNonMob RESULT rank={}", rank);
		helper.assertTrue("normal".equals(rank), "an armor stand returned rank " + rank);
		helper.succeed();
	}

	@GameTest
	public void eliteFlagReturnsElite(final GameTestHelper helper) {
		Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.SPAWNER);
		if (zombie == null) {
			throw new AssertionError("the test zombie could not be created");
		}

		EliteState.markElite(zombie, GearTier.DIAMOND);
		String rank = provider(helper).apply(zombie);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=readoutRankElite RESULT rank={}", rank);
		helper.assertTrue("elite".equals(rank), "an elite zombie returned rank " + rank);
		helper.succeed();
	}

	@GameTest
	public void bossFlagTakesPrecedence(final GameTestHelper helper) {
		Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.SPAWNER);
		if (zombie == null) {
			throw new AssertionError("the test zombie could not be created");
		}

		EliteState.setBoss(zombie, true);
		EliteState.markElite(zombie, GearTier.NETHERITE);
		String rank = provider(helper).apply(zombie);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=readoutRankBoss RESULT rank={}", rank);
		helper.assertTrue("boss".equals(rank), "a boss and elite zombie returned rank " + rank);
		helper.succeed();
	}

	private static Function<Entity, String> provider(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(RANK_KEY);
		if (!(shared instanceof Function<?, ?>)) {
			helper.assertTrue(false, "ObjectShare has no function at " + RANK_KEY);
			return entity -> "missing";
		}

		return castProvider(shared);
	}

	@SuppressWarnings("unchecked")
	private static Function<Entity, String> castProvider(final Object shared) {
		return (Function<Entity, String>) shared;
	}
}
