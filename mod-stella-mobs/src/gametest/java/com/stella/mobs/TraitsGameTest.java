package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.FasterMeleeGoal;
import com.stella.mobs.behavior.Traits;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.SpawnScaling;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;

/**
 * T03: a mob spawned below y=0 is hardened, and a mob spawned above it is not. The trait is taken once,
 * recorded durably, and its health is 1.1 times what the mob already carried rather than its base, so it
 * stacks with the elite double and with R12. Nothing here is a chance: the condition is the position.
 */
public class TraitsGameTest {
	private static final String TEST = "traits";
	private static final long NIGHT_TIME = 18000L;
	private static final float SPEC_MULTIPLIER = 1.1F;
	private static final double EPSILON = 1.0e-4;
	private static final int DEEP_Y = -20;
	private static final int HIGH_Y = 200;
	private static final double HIGH_FLOOR_Y = 199.0;
	private static final String NO_ROLL = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"boss_chance": 0.0
			}
		}""";

	/**
	 * The condition itself, from both sides: the mob below the line records the trait and the mob above it
	 * records nothing. The control is a real mob of the same kind at a different height, so a zero above
	 * is the condition and not an installer that never ran.
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void hardenedIsTakenBelowTheLineAndNotAboveIt(final GameTestHelper helper) {
		prepare(helper);
		Mob deep = spawn(helper, DEEP_Y);
		Mob high = spawn(helper, HIGH_Y);
		List<String> deepTraits = EliteState.traits(deep);
		List<String> highTraits = EliteState.traits(high);

		result(
			"hardenedTaken",
			"lineY=" + Traits.HARDENED_BELOW_Y + " deepY=" + (int) deep.getY() + " deepTraits=" + deepTraits
				+ " highY=" + (int) high.getY() + " highTraits=" + highTraits
		);
		helper.assertTrue(
			deep.getY() < Traits.HARDENED_BELOW_Y, "the deep subject spawned at " + deep.getY()
		);
		helper.assertTrue(
			high.getY() > Traits.HARDENED_BELOW_Y, "the high control spawned at " + high.getY()
		);
		helper.assertTrue(deepTraits.contains("hardened"), "a mob below the line took no hardened trait");
		helper.assertFalse(highTraits.contains("hardened"), "a mob above the line took the hardened trait");
		helper.succeed();
	}

	/**
	 * The arithmetic, asserted against what the mob already carries rather than against its base. The
	 * spawn scaling and the elite double both land before the trait, so the trait's own modifier is read
	 * off the mob and the product is compared against the base the mob had one step earlier.
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void hardenedHealthMultipliesWhatTheMobAlreadyCarried(final GameTestHelper helper) {
		prepare(helper);
		Mob plain = spawn(helper, DEEP_Y);
		double plainBase = plain.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		double plainModifier = multiplierOf(plain, Attributes.MAX_HEALTH, Traits.HARDENED_HEALTH_ID);
		double plainSpeed = multiplierOf(plain, Attributes.MOVEMENT_SPEED, Traits.HARDENED_SPEED_ID);
		// The elite is marked after the spawn scaling, so the base before the double is read here.
		Mob elite = spawn(helper, DEEP_Y);
		double scaledBase = elite.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		double elitePlainModifier = multiplierOf(elite, Attributes.MAX_HEALTH, Traits.HARDENED_HEALTH_ID);
		Elite.mark(elite, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		double doubledBase = elite.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		Traits.apply(elite);
		double eliteModifier = multiplierOf(elite, Attributes.MAX_HEALTH, Traits.HARDENED_HEALTH_ID);
		double eliteProduct = doubledBase * eliteModifier;

		result(
			"hardenedHealth",
			"specMultiplier=" + SPEC_MULTIPLIER + " plainBase=" + plainBase + " plainModifier=" + plainModifier
				+ " plainProduct=" + (plainBase * plainModifier) + " plainSpeedModifier=" + plainSpeed
				+ " spawnScalingReported=" + SpawnScaling.healthMultiplierOf(plain) + " spawnScalingModifier="
				+ multiplierOf(plain, Attributes.MAX_HEALTH, SpawnScaling.HEALTH_ID) + " spawnModifierAmount="
				+ spawnModifierAmount(plain) + " scaledBaseBeforeDouble=" + scaledBase + " doubleMultiplier="
				+ Elite.HEALTH_MULTIPLIER + " doubledBase=" + doubledBase + " eliteTraitModifier=" + eliteModifier
				+ " eliteProduct=" + eliteProduct + " elitePlainModifierBefore=" + elitePlainModifier
		);
		// The spec's 1.1, read as the health the mob actually has against the product it should have: the
		// base it carries, R12's spawn scaling, then the trait on top. getValue folds every modifier in,
		// so it is the product the player sees rather than one contribution to it.
		double scaledMultiplier = SpawnScaling.healthMultiplierOf(plain);
		double plainHealth = plain.getAttribute(Attributes.MAX_HEALTH).getValue();
		double expectedPlainHealth = plainBase * scaledMultiplier * SPEC_MULTIPLIER;
		double eliteHealth = elite.getAttribute(Attributes.MAX_HEALTH).getValue();
		double expectedEliteHealth = scaledBase * Elite.HEALTH_MULTIPLIER * scaledMultiplier * SPEC_MULTIPLIER;

		result(
			"hardenedRatio",
			"base=" + plainBase + " spawnMultiplier=" + scaledMultiplier + " specMultiplier=" + SPEC_MULTIPLIER
				+ " expectedPlainHealth=" + expectedPlainHealth + " plainHealth=" + plainHealth
				+ " expectedEliteHealth=" + expectedEliteHealth + " eliteHealth=" + eliteHealth
				+ " eliteDoubledBase=" + doubledBase + " eliteBaseBeforeDouble=" + scaledBase
		);
		helper.assertTrue(
			Math.abs(plainHealth - expectedPlainHealth) <= 1.0,
			"a hardened mob has " + plainHealth + " health, and 1.1 times what it carried before the trait is "
				+ expectedPlainHealth
		);
		helper.assertTrue(
			plainHealth > plainBase * scaledMultiplier,
			"the hardened write did not raise the health the mob already carried: " + plainHealth + " against "
				+ (plainBase * scaledMultiplier)
		);
		// The elite double lands first, then the trait multiplies the doubled base, so the two stack.
		assertClose(
			helper, doubledBase, scaledBase * Elite.HEALTH_MULTIPLIER, "the elite base health against its own base"
		);
		helper.assertTrue(
			Math.abs(eliteHealth - expectedEliteHealth) <= 1.0,
			"the elite hardened mob has " + eliteHealth + " health, and " + expectedEliteHealth
				+ " is the elite double times 1.1"
		);
		// The movement half, from the same source: the shipped bonus, read off the mob's own modifier.
		assertClose(
			helper, plainSpeed - 1.0, Traits.HARDENED_SPEED_BONUS,
			"the hardened movement bonus on a plain mob"
		);
		helper.succeed();
	}

	/**
	 * The guard, tested rather than commented: the recorded list is what stops a second application, so
	 * applying twice must leave both halves exactly where one application left them.
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void hardenedAppliedTwiceChangesNothing(final GameTestHelper helper) {
		prepare(helper);
		Mob mob = spawn(helper, DEEP_Y);
		double baseBefore = mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		double healthBefore = multiplierOf(mob, Attributes.MAX_HEALTH, Traits.HARDENED_HEALTH_ID);
		double speedBefore = multiplierOf(mob, Attributes.MOVEMENT_SPEED, Traits.HARDENED_SPEED_ID);
		int meleeGoalsBefore = meleeGoals(mob);
		List<String> traitsBefore = EliteState.traits(mob);
		Traits.apply(mob);
		Traits.apply(mob);
		double baseAfter = mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
		double healthAfter = multiplierOf(mob, Attributes.MAX_HEALTH, Traits.HARDENED_HEALTH_ID);
		double speedAfter = multiplierOf(mob, Attributes.MOVEMENT_SPEED, Traits.HARDENED_SPEED_ID);
		int meleeGoalsAfter = meleeGoals(mob);
		List<String> traitsAfter = EliteState.traits(mob);

		result(
			"hardenedTwice",
			"traitsBefore=" + traitsBefore + " traitsAfter=" + traitsAfter + " baseBefore=" + baseBefore
				+ " baseAfter=" + baseAfter + " healthBefore=" + healthBefore + " healthAfter=" + healthAfter
				+ " speedBefore=" + speedBefore + " speedAfter=" + speedAfter + " meleeGoalsBefore="
				+ meleeGoalsBefore + " meleeGoalsAfter=" + meleeGoalsAfter
		);
		assertClose(helper, healthAfter, healthBefore, "the hardened health multiplier after two applications");
		assertClose(helper, speedAfter, speedBefore, "the hardened movement bonus after two applications");
		assertClose(helper, baseAfter, baseBefore, "the base health after two applications");
		helper.assertValueEqual(
			Integer.valueOf(meleeGoalsAfter), Integer.valueOf(meleeGoalsBefore),
			"melee goals after two applications, against the one application"
		);
		helper.assertValueEqual(
			Integer.valueOf(traitsAfter.size()), Integer.valueOf(traitsBefore.size()),
			"recorded traits after two applications"
		);
		helper.succeed();
	}

	/** The cadence half: the hardened factor is its own number, and the goal it installs carries it. */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void hardenedAttackFactorIsItsOwnNumber(final GameTestHelper helper) {
		prepare(helper);
		Mob mob = spawn(helper, DEEP_Y);

		result(
			"hardenedCadence",
			"vanillaInterval=" + FasterMeleeGoal.VANILLA_INTERVAL + " hardenedFactor="
				+ Traits.HARDENED_ATTACK_FACTOR + " hardenedInterval="
				+ FasterMeleeGoal.intervalFor(Traits.HARDENED_ATTACK_FACTOR) + " eliteFactor="
				+ FasterMeleeGoal.SPEED_FACTOR + " eliteInterval=" + FasterMeleeGoal.intervalFor(FasterMeleeGoal.SPEED_FACTOR)
				+ " meleeGoals=" + meleeGoals(mob)
		);
		// Both factors are faster than vanilla, and the elite's half-again beats the trait's tenth: the
		// trait only ever has to be its own number, not the fastest one in the mod.
		helper.assertTrue(
			FasterMeleeGoal.intervalFor(Traits.HARDENED_ATTACK_FACTOR) < FasterMeleeGoal.VANILLA_INTERVAL,
			"the hardened cadence is not faster than vanilla's"
		);
		helper.assertTrue(
			FasterMeleeGoal.intervalFor(FasterMeleeGoal.SPEED_FACTOR)
				< FasterMeleeGoal.intervalFor(Traits.HARDENED_ATTACK_FACTOR),
			"the elite's half-again cadence is not faster than the trait's tenth"
		);
		helper.assertTrue(
			FasterMeleeGoal.intervalFor(Traits.HARDENED_ATTACK_FACTOR)
				< FasterMeleeGoal.intervalFor(1.0),
			"the hardened factor does not shorten the interval at all"
		);
		helper.assertValueEqual(
			Integer.valueOf(meleeGoals(mob)), Integer.valueOf(1), "melee goals on the hardened mob"
		);
		helper.succeed();
	}

	/** A floor under the deep subject, and one under the high control at its own height. */
	private static void prepare(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(NO_ROLL));
		helper.setTime(NIGHT_TIME);
		buildFloor(helper, 0);
		buildFloor(helper, (int) HIGH_FLOOR_Y);
	}

	private static void buildFloor(final GameTestHelper helper, final int y) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, y, z, Blocks.STONE);
			}
		}
	}

	@SuppressWarnings("removal")
	private static Mob spawn(final GameTestHelper helper, final int y) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 3, y, 3).spawnReason(EntitySpawnReason.NATURAL).spawn();
		mob.setPersistenceRequired();
		return mob;
	}

	/** The spawn scaling modifier's own amount, or zero when the mob does not carry it. */
	private static double spawnModifierAmount(final Mob mob) {
		AttributeInstance instance = mob.getAttribute(Attributes.MAX_HEALTH);

		if (instance == null) {
			return 0.0;
		}

		AttributeModifier modifier = instance.getModifier(SpawnScaling.HEALTH_ID);
		return modifier == null ? 0.0 : modifier.amount();
	}

	/** The modifier's own multiplier, as 1.0 plus its amount, or zero when the mob does not carry it. */
	private static double multiplierOf(
		final Mob mob, final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
		final net.minecraft.resources.Identifier id
	) {
		AttributeInstance instance = mob.getAttribute(attribute);

		if (instance == null) {
			return 0.0;
		}

		AttributeModifier modifier = instance.getModifier(id);
		return modifier == null ? 0.0 : modifier.amount() + 1.0;
	}

	private static int meleeGoals(final Mob mob) {
		int found = 0;

		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof MeleeAttackGoal || wrapped.getGoal() instanceof FasterMeleeGoal) {
				found++;
			}
		}

		return found;
	}

	private static void assertClose(
		final GameTestHelper helper, final double actual, final double expected, final String what
	) {
		helper.assertTrue(
			Math.abs(actual - expected) <= EPSILON,
			what + " is " + actual + ", expected " + expected + " within " + EPSILON
		);
	}

	private static void result(final String test, final String detail) {
		com.stella.mobs.StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {}", TEST, test, detail);
	}
}
