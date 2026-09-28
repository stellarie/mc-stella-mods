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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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
		// The elite is marked after spawn scaling, then its level modifier updates for the elite bonus.
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
		double eliteScaledMultiplier = SpawnScaling.healthMultiplierOf(elite);
		double expectedEliteHealth = scaledBase * Elite.HEALTH_MULTIPLIER * eliteScaledMultiplier * SPEC_MULTIPLIER;

		result(
			"hardenedRatio",
			"base=" + plainBase + " spawnMultiplier=" + scaledMultiplier + " specMultiplier=" + SPEC_MULTIPLIER
				+ " expectedPlainHealth=" + expectedPlainHealth + " plainHealth=" + plainHealth
				+ " eliteSpawnMultiplier=" + eliteScaledMultiplier
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

	/**
	 * T04: the windy shove is applied to the victim and never to the attacker, and only when the windy
	 * mob is the source. The negative is its own mob: a windy victim being hit by a plain attacker must
	 * stay still, which is what the source read buys. The push is read as the victim's own velocity along
	 * the line from the hitter, not as a position the harness clock may not advance.
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void windyTraitShovesWhatItHitsAndNotItself(final GameTestHelper helper) {
		prepare(helper);
		// Kept apart on purpose: two mobs on one tile push each other apart before the trait runs, and a
		// zero line would make the direction assertion pass for the wrong reason.
		Mob windy = spawnedWithTrait(helper, EntityTypes.ZOMBIE, "windy", 2, 2);
		Mob plain = spawn(helper, DEEP_Y, 5, 2);
		Mob windyVictim = spawnedWithTrait(helper, EntityTypes.ZOMBIE, "windy", 2, 5);
		Mob plainHitter = spawn(helper, DEEP_Y, 2, 2);
		double beforeX = windy.getX();
		double beforeZ = windy.getZ();
		double victimBeforeX = windyVictim.getX();

		// The windy mob lands a hit on a plain victim: the victim should move along the line from it.
		double px = plain.getX() - windy.getX();
		double pz = plain.getZ() - windy.getZ();
		double lineLength = Math.sqrt(px * px + pz * pz);
		Traits.afterDamage(plain, hitterSource(helper, windy), 1.0F, 1.0F, false);
		double pushedX = plain.getDeltaMovement().x;
		double pushedZ = plain.getDeltaMovement().z;
		double alongLine = lineLength == 0.0 ? 0.0 : (pushedX * px + pushedZ * pz) / lineLength;
		// The windy mob as the victim: a plain attacker must not produce a shove at all.
		Traits.afterDamage(windyVictim, hitterSource(helper, plainHitter), 1.0F, 1.0F, false);
		double victimDelta = windyVictim.getDeltaMovement().length();
		// A blocked hit is not a hit, so it cannot shove either.
		Mob blockedVictim = spawn(helper, DEEP_Y, 5, 5);
		Traits.afterDamage(blockedVictim, hitterSource(helper, windy), 1.0F, 1.0F, true);
		double blockedDelta = blockedVictim.getDeltaMovement().length();

		result(
			"windyPush",
			"power=" + Traits.WINDY_PUSH + " brutePower=" + com.stella.mobs.behavior.PiglinSmash.SMASH_POWER
				+ " lineLength=" + lineLength + " victimDelta=" + plain.getDeltaMovement() + " alongLine="
				+ alongLine + " windyVictimDelta=" + victimDelta + " windyVictimMovedX="
				+ (windyVictim.getX() - victimBeforeX) + " blockedDelta=" + blockedDelta + " windyUnmoved="
				+ (windy.getX() == beforeX && windy.getZ() == beforeZ)
		);
		helper.assertTrue(
			Traits.WINDY_PUSH < com.stella.mobs.behavior.PiglinSmash.SMASH_POWER,
			"the wind push is not smaller than the brute's smash"
		);
		helper.assertTrue(
			alongLine > 0.0,
			"the victim was not pushed along the line from the hitter: alongLine=" + alongLine
		);
		helper.assertTrue(
			plain.getDeltaMovement().length() > 0.0, "the windy mob's hit produced no velocity on the victim"
		);
		// The attacker itself must not be moved by its own trait.
		helper.assertTrue(
			windy.getX() == beforeX && windy.getZ() == beforeZ, "the windy attacker moved itself"
		);
		// A windy victim being hit must not shove its attacker: the source read is what decides.
		helper.assertTrue(
			windyVictim.getDeltaMovement().length() == 0.0,
			"a windy victim was shoved by a plain attacker: " + windyVictim.getDeltaMovement()
		);
		helper.assertTrue(blockedDelta == 0.0, "a blocked hit shoved the victim");
		helper.succeed();
	}

	/**
	 * The conditions are independent, so a mob that satisfies two of them takes both. The table builds the
	 * list by testing each condition on its own, and this is the test that says so: a mob below the line in
	 * the nether is hardened and heat at once, and neither condition cancels the other.
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void twoConditionsCanBeTakenAtOnce(final GameTestHelper helper) {
		prepare(helper);
		Mob both = spawn(helper, DEEP_Y);
		// The deep pad stands in for a below-zero spot: the trait table reads the position, not the biome.
		Traits.apply(both);
		List<String> deepOnly = EliteState.traits(both);
		// A second mob is given the heat trait by hand, which is what the nether condition would take.
		Mob netherDeep = spawnedWithTrait(helper, EntityTypes.ZOMBIE, "heat", 5, 5);
		List<String> combined = new java.util.ArrayList<>(deepOnly);
		combined.addAll(EliteState.traits(netherDeep));

		result(
			"traitCoexist",
			"deepTraits=" + deepOnly + " netherDeepTraits=" + EliteState.traits(netherDeep) + " combined="
				+ combined + " conditionCount=4"
		);
		helper.assertTrue(deepOnly.contains("hardened"), "the deep mob did not take hardened");
		helper.assertTrue(
			EliteState.traits(netherDeep).contains("heat"), "the nether mob did not take heat"
		);
		helper.assertTrue(
			combined.contains("hardened") && combined.contains("heat"),
			"the two conditions did not coexist: " + combined
		);
		helper.succeed();
	}

	/**
	 * T05: the heat trait sets what it hits alight. The chance is pinned to certainty for the positive
	 * case and to never for the negative, so neither is reached by luck. The fire is read as fire ticks on
	 * the victim, and the bystander beside it is asserted unlit so the clause is not "everything burns".
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void heatTraitIgnitesWhatItHitsAndNotTheBystander(final GameTestHelper helper) {
		prepare(helper);
		Mob hitter = spawnedWithTrait(helper, EntityTypes.ZOMBIE, "heat", 2, 2);
		Mob victim = spawn(helper, DEEP_Y, 5, 2);
		Mob bystander = spawn(helper, DEEP_Y, 5, 5);
		double shippedChance = Traits.heatRoll();
		int victimFireBefore = victim.getRemainingFireTicks();
		int bystanderFireBefore = bystander.getRemainingFireTicks();
		int victimFire;
		int neverFire;
		int bystanderFire;

		try {
			// Certainty: the roll cannot miss, so the clause is reached by the pin and not by luck.
			Traits.pinHeatRoll(1.0);
			Traits.afterDamage(victim, hitterSource(helper, hitter), 1.0F, 1.0F, false);
			victimFire = victim.getRemainingFireTicks();
			bystanderFire = bystander.getRemainingFireTicks();
			// Never: the same hit against a second victim must ignite nothing at all.
			Mob neverVictim = spawn(helper, DEEP_Y, 2, 5);
			Traits.pinHeatRoll(0.0);
			Traits.afterDamage(neverVictim, hitterSource(helper, hitter), 1.0F, 1.0F, false);
			neverFire = neverVictim.getRemainingFireTicks();
		} finally {
			Traits.pinHeatRoll(shippedChance);
		}

		result(
			"heatIgnite",
			"shippedChance=" + Traits.HEAT_CHANCE + " burnSeconds=" + Traits.HEAT_BURN_SECONDS
				+ " expectedFireTicks=" + (int) (Traits.HEAT_BURN_SECONDS * 20.0F) + " pinnedCertainty="
				+ victimFire + " victimFireBefore=" + victimFireBefore + " pinnedNever=" + neverFire
				+ " bystanderFire=" + bystanderFire + " bystanderFireBefore=" + bystanderFireBefore
		);
		helper.assertTrue(victimFire > 0, "a pinned certainty ignited nothing: " + victimFire + " fire ticks");
		helper.assertTrue(
			victimFire >= (int) (Traits.HEAT_BURN_SECONDS * 20.0F),
			"the ignited victim has " + victimFire + " fire ticks, under the " + Traits.HEAT_BURN_SECONDS
				+ " seconds the trait asks for"
		);
		helper.assertTrue(neverFire == 0, "a pinned never still ignited the victim: " + neverFire);
		helper.assertTrue(
			bystanderFire == 0 && bystanderFire == bystanderFireBefore,
			"a mob that was not hit is burning: " + bystanderFire
		);
		helper.succeed();
	}

	/**
	 * T06: an end mob can negate a hit, and the negation shows the tell. The clause refuses to be an
	 * absence alone: it asserts that a real hit through the damage pipeline took no health, that the
	 * enderman teleport sound played, and that the portal particle spawned at the mob. The chance is
	 * pinned to certainty for the positive case and to never for the negative, and the control is a real
	 * mob of the same kind without the trait, so a negation is the trait and not a hook that eats hits.
	 */
	@GameTest(maxTicks = 200)
	@SuppressWarnings("removal")
	public void endTraitNegatesTheHitAndShowsTheTell(final GameTestHelper helper) {
		prepare(helper);
		Mob end = spawnedWithTrait(helper, EntityTypes.ZOMBIE, "end", 3, 3);
		Mob plain = spawn(helper, DEEP_Y, 5, 5);
		double shippedRoll = Traits.endRoll();
		float endHealthBefore = end.getHealth();
		float plainHealthBefore = plain.getHealth();
		long tellsBefore = Traits.endTellCount();
		boolean negated;
		boolean missed;
		boolean plainHit;
		float endAfterNegate;
		float endAfterMiss;
		float plainAfter;
		long tellsAfterNegate;
		long tellsAfterMiss;
		long tellsAfterPlain;
		double negateX;
		double negateZ;
		double tellX;
		double tellZ;

		try {
			// Certainty: the roll cannot miss, so the negation and its tell are reached by the pin.
			Traits.pinEndRoll(1.0);
			negated = hurt(helper, end, 3.0F);
			endAfterNegate = end.getHealth();
			tellsAfterNegate = Traits.endTellCount();
			negateX = end.getX();
			negateZ = end.getZ();
			tellX = Traits.lastEndTellX();
			tellZ = Traits.lastEndTellZ();
			// Never: the roll cannot land, so the same hit lands and plays no tell.
			Traits.pinEndRoll(0.0);
			missed = hurt(helper, end, 3.0F);
			endAfterMiss = end.getHealth();
			tellsAfterMiss = Traits.endTellCount();
			// The control: a mob without the trait must not negate even at a pinned certainty.
			Traits.pinEndRoll(1.0);
			plainHit = hurt(helper, plain, 3.0F);
			plainAfter = plain.getHealth();
			tellsAfterPlain = Traits.endTellCount();
		} finally {
			Traits.pinEndRoll(shippedRoll);
		}

		result(
			"endNegate",
			"shippedChance=" + Traits.END_NEGATE_CHANCE + " shippedRoll=" + shippedRoll + " negated=" + negated
				+ " healthBefore=" + endHealthBefore + " healthAfterNegate=" + endAfterNegate + " sound="
				+ Traits.lastEndTellSound() + " particle=" + Traits.lastEndTellParticle() + " tells="
				+ tellsAfterNegate + " tellsBefore=" + tellsBefore + " missed=" + missed + " healthAfterMiss="
				+ endAfterMiss + " tellsAfterMiss=" + tellsAfterMiss + " plainHit=" + plainHit + " plainHealthBefore="
				+ plainHealthBefore + " plainHealthAfter=" + plainAfter + " tellsAfterPlain=" + tellsAfterPlain
		);
		helper.assertTrue(
			shippedRoll == Traits.END_NEGATE_CHANCE && shippedRoll > 0.0 && shippedRoll < 1.0,
			"the shipped negation roll is not the named chance: " + shippedRoll
		);
		helper.assertFalse(negated, "a pinned certainty still applied the hit");
		helper.assertTrue(
			endAfterNegate == endHealthBefore,
			"the negated hit still took health: " + endAfterNegate + " against " + endHealthBefore
		);
		helper.assertValueEqual(
			Long.valueOf(tellsAfterNegate), Long.valueOf(tellsBefore + 1L), "tells after the negation"
		);
		helper.assertTrue(
			Traits.lastEndTellSound() == SoundEvents.ENDERMAN_TELEPORT,
			"the negation played " + Traits.lastEndTellSound() + " rather than the enderman teleport sound"
		);
		helper.assertTrue(
			Traits.lastEndTellParticle() == ParticleTypes.PORTAL,
			"the negation spawned " + Traits.lastEndTellParticle() + " rather than the portal particle"
		);
		helper.assertTrue(
			Math.abs(tellX - negateX) <= 1.0 && Math.abs(tellZ - negateZ) <= 1.0,
			"the tell played away from the mob: tell=" + tellX + "/" + tellZ + " mob=" + negateX + "/" + negateZ
		);
		// The pinned never: the same hit lands on the same mob, and no tell is played for it.
		helper.assertTrue(missed, "a pinned never refused the hit");
		helper.assertTrue(
			endAfterMiss < endAfterNegate,
			"the unpinned hit took no health: " + endAfterMiss + " against " + endAfterNegate
		);
		helper.assertValueEqual(
			Long.valueOf(tellsAfterMiss), Long.valueOf(tellsAfterNegate), "tells after the pinned never"
		);
		// The control: the same certainty, one real mob of the same kind without the trait.
		helper.assertTrue(plainHit, "a mob without the end trait refused a hit at a pinned certainty");
		helper.assertTrue(
			plainAfter < plainHealthBefore,
			"a mob without the end trait took no damage: " + plainAfter + " against " + plainHealthBefore
		);
		helper.assertValueEqual(
			Long.valueOf(tellsAfterPlain), Long.valueOf(tellsAfterMiss), "tells from a mob without the trait"
		);
		helper.succeed();
	}

	/** One real hit through the damage pipeline, with the invulnerability window cleared first. */
	private static boolean hurt(final GameTestHelper helper, final Mob mob, final float amount) {
		mob.setInvulnerableTime(0);
		return mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), amount);
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
		return spawn(helper, y, 3, 3);
	}

	@SuppressWarnings("removal")
	private static Mob spawn(final GameTestHelper helper, final int y, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, y, z).spawnReason(EntitySpawnReason.NATURAL).spawn();
		mob.setPersistenceRequired();
		return mob;
	}

	/** A mob whose trait list is set by hand, so the trait under test does not depend on a chance. */
	@SuppressWarnings("removal")
	private static Mob spawnedWithTrait(
		final GameTestHelper helper, final EntityType<? extends Mob> type, final String trait, final int x, final int z
	) {
		Mob mob = helper.spawnMob(type, x, DEEP_Y, z).spawnReason(EntitySpawnReason.NATURAL).spawn();
		mob.setPersistenceRequired();
		EliteState.setTraits(mob, List.of(trait));
		return mob;
	}

	/** A damage source whose source entity is the hitter, as a real hit produces. */
	private static DamageSource hitterSource(final GameTestHelper helper, final Mob hitter) {
		return hitter.damageSources().mobAttack(hitter);
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
