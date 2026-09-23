package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.GolemElite;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;

/**
 * G01 to G04: an iron golem is elite every time rather than by roll, moves 25 percent faster, slows what it
 * hits by a literal 100 percent for ten ticks, and carries 1.5 times the elite maximum on top of the elite
 * doubling and the level buff. It is not an Enemy, so none of this arrives through the shipped elite path.
 */
public class GolemEliteGameTest {
	private static final String TEST = "golemElite";
	private static final double IRON_GOLEM_BASE_HEALTH = 100.0;
	private static final double ELITE_MAXIMUM = IRON_GOLEM_BASE_HEALTH * Elite.HEALTH_MULTIPLIER;
	private static final double TOLERANCE = 1.0e-6;

	@GameTest(maxTicks = 200)
	public void ironGolemIsEliteOnEverySpawn(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Mob first = spawnGolem(helper, 2, 2);
		Mob second = spawnGolem(helper, 6, 6);

		helper.runAfterDelay(4L, () -> {
			helper.assertTrue(Elite.isElite(first), "the first iron golem is not elite");
			helper.assertTrue(Elite.isElite(second), "the second iron golem is not elite");
			// Never twice: a second mark must leave the health exactly where the first one put it.
			double before = second.getMaxHealth();
			double speedBefore = second.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
			Elite.mark(second, com.stella.mobs.gear.GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			helper.assertTrue(
				Math.abs(second.getMaxHealth() - before) < TOLERANCE,
				"a second mark changed the health from " + before + " to " + second.getMaxHealth()
			);
			helper.assertTrue(
				Math.abs(second.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - speedBefore) < TOLERANCE,
				"a second mark changed the speed"
			);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} RESULT firstElite={} secondElite={} healthAfterSecondMark={}",
				TEST, Elite.isElite(first), Elite.isElite(second), second.getMaxHealth()
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void ironGolemCarriesTheEliteAndGolemBonuses(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Mob golem = spawnGolem(helper, 4, 4);

		helper.runAfterDelay(4L, () -> {
			AttributeInstance health = golem.getAttribute(Attributes.MAX_HEALTH);
			AttributeInstance speed = golem.getAttribute(Attributes.MOVEMENT_SPEED);
			helper.assertTrue(
				health != null && health.hasModifier(GolemElite.HEALTH_ID), "no golem health modifier"
			);
			helper.assertTrue(
				speed != null && speed.hasModifier(GolemElite.SPEED_ID), "no golem speed modifier"
			);
			helper.assertTrue(
				Math.abs(health.getModifier(GolemElite.HEALTH_ID).amount() - GolemElite.HEALTH_BONUS) < TOLERANCE,
				"the golem health bonus is " + health.getModifier(GolemElite.HEALTH_ID).amount()
			);
			helper.assertTrue(
				Math.abs(speed.getModifier(GolemElite.SPEED_ID).amount() - GolemElite.SPEED_BONUS) < TOLERANCE,
				"the golem speed bonus is " + speed.getModifier(GolemElite.SPEED_ID).amount()
			);
			// 1.5 times the ELITE maximum, and the level buff rides its own modifier on top of that.
			helper.assertTrue(
				golem.getMaxHealth() >= ELITE_MAXIMUM * (1.0 + GolemElite.HEALTH_BONUS),
				"the elite golem health is " + golem.getMaxHealth() + ", below 1.5 times the elite maximum of "
					+ ELITE_MAXIMUM
			);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} RESULT eliteMaximum={} health={} healthBonus={} speedBonus={}",
				TEST, ELITE_MAXIMUM, golem.getMaxHealth(), GolemElite.HEALTH_BONUS, GolemElite.SPEED_BONUS
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void ironGolemHitSlowsItsVictim(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		IronGolem golem = (IronGolem) spawnGolem(helper, 4, 2);
		Mob victim = helper.spawnMob(EntityTypes.COW, 4, 3, 4).withNoFreeWill().spawn();
		victim.setPersistenceRequired();

		helper.runAfterDelay(6L, () -> {
			helper.hurt(victim, helper.getLevel().damageSources().mobAttack(golem), 1.0F);
			MobEffectInstance slow = victim.getEffect(MobEffects.SLOWNESS);
			helper.assertTrue(slow != null, "the golem's hit applied no Slowness");
			helper.assertTrue(
				slow.getAmplifier() == GolemElite.SLOW_AMPLIFIER,
				"the slow level is " + (slow.getAmplifier() + 1) + ", expected " + (GolemElite.SLOW_AMPLIFIER + 1)
			);
			helper.assertTrue(
				slow.getDuration() <= GolemElite.SLOW_TICKS,
				"the slow lasted " + slow.getDuration() + " ticks, over " + GolemElite.SLOW_TICKS
			);
			helper.assertTrue(
				EliteState.isElite(golem), "the golem slowed its victim while not elite"
			);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} RESULT slowLevel={} slowTicks={} literalPercent={}",
				TEST, slow.getAmplifier() + 1, slow.getDuration(), (slow.getAmplifier() + 1) * 15
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void forcingTwiceStacksNothing(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Mob golem = spawnGolem(helper, 4, 4);

		helper.runAfterDelay(6L, () -> {
			// What a reload does: the force call runs again on a mob that is already elite.
			double health = golem.getMaxHealth();
			double speed = golem.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
			GolemElite.force(golem);
			helper.assertTrue(
				Math.abs(golem.getMaxHealth() - health) < TOLERANCE,
				"a second force changed the health from " + health + " to " + golem.getMaxHealth()
			);
			helper.assertTrue(
				Math.abs(golem.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - speed) < TOLERANCE,
				"a second force changed the speed from " + speed + " to "
					+ golem.getAttribute(Attributes.MOVEMENT_SPEED).getValue()
			);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} RESULT healthAfterReforce={} speedAfterReforce={} elite={}",
				TEST, golem.getMaxHealth(), golem.getAttribute(Attributes.MOVEMENT_SPEED).getValue(),
				Elite.isElite(golem)
			);
			helper.succeed();
		});
	}

	private static Mob spawnGolem(final GameTestHelper helper, final int x, final int z) {
		Mob golem = helper.spawnMob(EntityTypes.IRON_GOLEM, x, 3, z).spawn();
		golem.setPersistenceRequired();
		return golem;
	}
}
