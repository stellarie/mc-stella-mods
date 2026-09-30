package com.stella.mobs;

import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Arrows, explosions, and other non-melee mob damage scale with the mob's level; melee does not double. */
public class NonMeleeScalingGameTest {
	private static final float HIT = 4.0F;

	@GameTest
	public void arrowScales(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		float ratio = ratio(helper, EntityTypes.SKELETON, mob -> {
			Arrow arrow = new Arrow(level, mob, new ItemStack(Items.ARROW), null);
			return level.damageSources().arrow(arrow, mob);
		});
		double expected = SpawnScaling.damageMultiplier(20);
		result("arrowScales", "ratio=" + ratio + " expected=" + expected);
		helper.assertTrue(Math.abs(ratio - expected) < 0.01, "arrow ratio " + ratio + " expected " + expected);
		helper.succeed();
	}

	@GameTest
	public void explosionScales(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		float ratio = ratio(helper, EntityTypes.CREEPER, mob -> level.damageSources().explosion(mob, mob));
		double expected = SpawnScaling.damageMultiplier(20);
		result("explosionScales", "ratio=" + ratio + " expected=" + expected);
		helper.assertTrue(Math.abs(ratio - expected) < 0.01, "explosion ratio " + ratio + " expected " + expected);
		helper.succeed();
	}

	@GameTest
	public void meleeNotDoubled(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		float ratio = ratio(helper, EntityTypes.ZOMBIE, mob -> level.damageSources().mobAttack(mob));
		result("meleeNotDoubled", "ratio=" + ratio);
		helper.assertTrue(Math.abs(ratio - 1.0F) < 0.001F, "a melee hit was scaled at the hurt call: " + ratio);
		helper.succeed();
	}

	@GameTest
	public void noRollTagSkipped(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob raid = level(helper, EntityTypes.SKELETON, 20);
		raid.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		Arrow arrow = new Arrow(level, raid, new ItemStack(Items.ARROW), null);
		float loss = loss(helper, level.damageSources().arrow(arrow, raid));
		result("noRollTagSkipped", "loss=" + loss);
		helper.assertTrue(Math.abs(loss - HIT) < 0.001F, "a no-roll mob was scaled: " + loss);
		helper.succeed();
	}

	private static <T extends Mob> float ratio(
		final GameTestHelper helper, final net.minecraft.world.entity.EntityType<T> type, final Function<Mob, DamageSource> source
	) {
		float weak = loss(helper, source.apply(level(helper, type, 0)));
		float strong = loss(helper, source.apply(level(helper, type, 20)));
		return strong / weak;
	}

	private static <T extends Mob> Mob level(final GameTestHelper helper, final net.minecraft.world.entity.EntityType<T> type, final int threat) {
		Mob mob = type.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(0, ThreatState.MIN_VARIATION, 0, threat));
		return mob;
	}

	private static float loss(final GameTestHelper helper, final DamageSource source) {
		Mob victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
		victim.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		victim.setHealth(victim.getMaxHealth());
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), source, HIT);
		return before - victim.getHealth();
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
