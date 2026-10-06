package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The ten fighter books, plain and awakened: what each one strikes, what it leaves behind, and what it moves. */
public class FighterBooksGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.fighterbooks");
	/** The caster stands here, facing +Z, so every foe ahead sits on one line. */
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	/** A foe in front of the caster at this distance, and one behind it at this one. */
	private static final double FRONT = 3.0;
	private static final double BEHIND = 3.0;
	/** How many ticks a three-pulse Whirlwind, or a 30-tick window, needs. */
	private static final long THREE_PULSES_TICKS = FighterSkills.WHIRLWIND_INTERVAL_TICKS * FighterSkills.WHIRLWIND_PULSES + 10;

	@GameTest(maxTicks = 80)
	public void cleaveHitsTheConeAheadAndLeavesTheFoeBleeding(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob front = foe(helper, CASTER_X, CASTER_Z + FRONT);
		Mob behind = foe(helper, CASTER_X, CASTER_Z - BEHIND);
		float frontBefore = front.getHealth();
		float behindBefore = behind.getHealth();
		SkillSchoolGameTest.cast(caster, "cleave", null, false);
		float frontLost = frontBefore - front.getHealth();
		float behindLost = behindBefore - behind.getHealth();
		LOG.info("[BEHAVIOR] test=cleaveHitsTheConeAheadAndLeavesTheFoeBleeding RESULT frontLost={} behindLost={} frontBleeding={} behindBleeding={}",
			frontLost, behindLost, front.hasEffect(SpellStatus.BLEEDING), behind.hasEffect(SpellStatus.BLEEDING));
		helper.assertTrue(frontLost > 0.0F, "the foe in the cone took nothing");
		helper.assertTrue(behindLost == 0.0F, "the foe behind the caster took " + behindLost);
		helper.assertTrue(front.hasEffect(SpellStatus.BLEEDING), "the foe in the cone does not bleed");
		helper.assertFalse(behind.hasEffect(SpellStatus.BLEEDING), "the foe behind the caster bleeds");
		cleanup(helper, front, behind);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedCleaveDeepensTheBleedWithEachCastAndStopsAtTwo(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob foe = foe(helper, CASTER_X, CASTER_Z + FRONT);
		List<Integer> amplifiers = new ArrayList<>();
		for (int cast = 0; cast < 4; cast++) {
			SkillSchoolGameTest.cast(caster, "cleave", null, true);
			MobEffectInstance bleeding = foe.getEffect(SpellStatus.BLEEDING);
			amplifiers.add(bleeding == null ? -1 : bleeding.getAmplifier());
		}
		LOG.info("[BEHAVIOR] test=awakenedCleaveDeepensTheBleedWithEachCastAndStopsAtTwo RESULT amplifiers={} cap={}",
			amplifiers, SpellNumbers.CLEAVE.amplifier());
		helper.assertTrue(amplifiers.get(0) == 1, "the first awakened cleave left amplifier " + amplifiers.get(0));
		helper.assertTrue(amplifiers.get(1) == 2, "the second awakened cleave left amplifier " + amplifiers.get(1));
		helper.assertTrue(amplifiers.get(2) == 2 && amplifiers.get(3) == 2,
			"a third or fourth cleave went past the cap: " + amplifiers);		cleanup(helper, foe);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void leapSlamLandsBesideTheTargetAndHitsWhatStandsThere(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob target = foe(helper, CASTER_X, CASTER_Z + 6.0);
		Mob near = foe(helper, CASTER_X + 1.0, CASTER_Z + 6.0);
		float targetBefore = target.getHealth();
		float nearBefore = near.getHealth();
		SkillSchoolGameTest.cast(caster, "leap_slam", target, false);
		double landed = caster.position().distanceTo(target.position());
		LOG.info("[BEHAVIOR] test=leapSlamLandsBesideTheTargetAndHitsWhatStandsThere RESULT landed={} targetLost={} nearLost={} slowness={}",
			landed, targetBefore - target.getHealth(), nearBefore - near.getHealth(), near.hasEffect(MobEffects.SLOWNESS));
		helper.assertTrue(landed < 3.0, "the caster landed " + landed + " blocks from the target");
		helper.assertTrue(targetBefore - target.getHealth() > 0.0F, "the target took nothing from the landing");
		helper.assertTrue(nearBefore - near.getHealth() > 0.0F, "the foe beside the landing took nothing");
		helper.assertFalse(near.hasEffect(MobEffects.SLOWNESS), "a plain landing slowed a foe");
		cleanup(helper, target, near);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedLeapSlamSlowsWhatItHits(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob target = foe(helper, CASTER_X, CASTER_Z + 6.0);
		SkillSchoolGameTest.cast(caster, "leap_slam", target, true);
		MobEffectInstance slowness = target.getEffect(MobEffects.SLOWNESS);
		LOG.info("[BEHAVIOR] test=awakenedLeapSlamSlowsWhatItHits RESULT slowness={} amplifier={} seconds={} targetEffects={} casterEffects={} moved={} targetAlive={}",
			slowness != null, slowness == null ? -1 : slowness.getAmplifier(), slowness == null ? 0 : slowness.getDuration() / 20,
			target.getActiveEffects(), caster.getActiveEffects(), caster.position().distanceTo(target.position()), target.isAlive());
		helper.assertTrue(slowness != null, "an awakened landing left no Slowness");
		helper.assertTrue(slowness.getAmplifier() == SpellNumbers.LEAP_SLAM.amplifier(),
			"the slowness is amplifier " + slowness.getAmplifier() + ", not " + SpellNumbers.LEAP_SLAM.amplifier());
		cleanup(helper, target);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void whirlwindStrikesThreeTimesTwentyTicksApart(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob foe = foe(helper, CASTER_X, CASTER_Z + 2.0);
		float before = foe.getHealth();
		SkillSchoolGameTest.cast(caster, "whirlwind", null, false);
		int firstPulse = (int) (before - foe.getHealth());
		float afterFirst = foe.getHealth();
		int owed = FighterSkills.whirlwindPulses(caster);
		helper.runAfterDelay(THREE_PULSES_TICKS, () -> {
			float lost = before - foe.getHealth();
			int pulses = FighterSkills.whirlwindPulses(caster);
			LOG.info("[BEHAVIOR] test=whirlwindStrikesThreeTimesTwentyTicksApart RESULT firstPulse={} afterFirstPulse={} lostTotal={} pulsesOwed={} pulsesLeft={}",
				firstPulse, afterFirst, lost, owed, pulses);
			helper.assertTrue(firstPulse > 0, "the first spin took nothing");
			helper.assertTrue(owed == FighterSkills.WHIRLWIND_PULSES - 1, "the cast owed " + owed + " further spins");
			helper.assertTrue(pulses == 0, "the sequence left " + pulses + " spins owed");
			helper.assertTrue(lost > firstPulse, "the later spins took nothing: " + lost + " against " + firstPulse);
			cleanup(helper, foe);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void awakenedWhirlwindPullsEachFoeTowardTheCaster(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob foe = foe(helper, CASTER_X, CASTER_Z + 2.5);
		double before = foe.distanceTo(caster);
		SkillSchoolGameTest.cast(caster, "whirlwind", null, true);
		helper.runAfterDelay(3L, () -> {
			double pulled = foe.distanceTo(caster);
			LOG.info("[BEHAVIOR] test=awakenedWhirlwindPullsEachFoeTowardTheCaster RESULT before={} after={} pull={}",
				before, pulled, FighterSkills.WHIRLWIND_PULL);
			helper.assertTrue(pulled < before, "the awakened spin pulled nothing: " + before + " to " + pulled);
			cleanup(helper, foe);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void shieldBashStaggersTheTargetAndWithoutAShieldItRefuses(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob target = foe(helper, CASTER_X, CASTER_Z + 4.0);
		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		SkillSchoolGameTest.cast(caster, "shield_bash", target, false);
		boolean staggered = target.hasEffect(SpellStatus.STAGGERED);
		// Without the shield the same cast refuses, which is the book's own condition.
		caster.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		Mob unshielded = foe(helper, CASTER_X, CASTER_Z + 2.0);
		float before = unshielded.getHealth();
		boolean applied = SkillSchoolGameTest.cast(caster, "shield_bash", unshielded, false);
		LOG.info("[BEHAVIOR] test=shieldBashStaggersTheTargetAndWithoutAShieldItRefuses RESULT staggered={} withShield={} withoutShield={} lostWithoutShield={}",
			staggered, true, applied, before - unshielded.getHealth());
		helper.assertTrue(staggered, "a shield bash left the target unstaggered");
		helper.assertFalse(applied, "a shield bash with no shield in either hand applied");
		helper.assertTrue(before - unshielded.getHealth() == 0.0F, "a refused shield bash still hit");
		cleanup(helper, target, unshielded);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedShieldBashStaggersTheWakeBehindTheTarget(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob target = foe(helper, CASTER_X, CASTER_Z + 3.0);
		Mob wake = foe(helper, CASTER_X, CASTER_Z + 4.5);
		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		SkillSchoolGameTest.cast(caster, "shield_bash", target, true);
		LOG.info("[BEHAVIOR] test=awakenedShieldBashStaggersTheWakeBehindTheTarget RESULT target={} wake={} wakeDistance={}",
			target.hasEffect(SpellStatus.STAGGERED), wake.hasEffect(SpellStatus.STAGGERED), wake.distanceTo(target));
		helper.assertTrue(target.hasEffect(SpellStatus.STAGGERED), "an awakened bash left the target unstaggered");
		helper.assertTrue(wake.hasEffect(SpellStatus.STAGGERED), "the foe behind the target was not staggered");
		cleanup(helper, target, wake);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void chargeMovesTheCasterForwardAndHitsThePath(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob onPath = foe(helper, CASTER_X, CASTER_Z + FighterSkills.CHARGE_REACH / 2.0);
		Mob aside = foe(helper, CASTER_X + 2.0, CASTER_Z + FighterSkills.CHARGE_REACH / 2.0);
		float onPathBefore = onPath.getHealth();
		float asideBefore = aside.getHealth();
		double startZ = caster.getZ();
		SkillSchoolGameTest.cast(caster, "charge", null, false);
		double travelled = caster.getZ() - startZ;
		LOG.info("[BEHAVIOR] test=chargeMovesTheCasterForwardAndHitsThePath RESULT travelled={} onPathLost={} asideLost={} reached={}",
			travelled, onPathBefore - onPath.getHealth(), asideBefore - aside.getHealth(), FighterSkills.CHARGE_REACH);
		helper.assertTrue(travelled > 1.0, "the caster moved " + travelled + " blocks forward");
		helper.assertTrue(travelled <= FighterSkills.CHARGE_REACH + 1.0E-6,
			"the caster overshot its reach: " + travelled + " against " + FighterSkills.CHARGE_REACH);
		helper.assertTrue(onPathBefore - onPath.getHealth() > 0.0F, "the foe on the path took nothing");
		helper.assertTrue(asideBefore - aside.getHealth() == 0.0F, "the foe two blocks aside took a hit");
		cleanup(helper, onPath, aside);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void chargeStopsBeforeAWall(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		// A wall two blocks ahead: the charge stops at the last spot before it.
		for (int y = 1; y <= 3; y++) {
			for (int x = 0; x <= 4; x++) {
				helper.setBlock(x, y, (int) CASTER_Z + 4, net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		double startZ = caster.getZ();
		SkillSchoolGameTest.cast(caster, "charge", null, false);
		double travelled = caster.getZ() - startZ;
		LOG.info("[BEHAVIOR] test=chargeStopsBeforeAWall RESULT travelled={} wallAt={} limit={}",
			travelled, CASTER_Z + 4, FighterSkills.CHARGE_REACH);
		helper.assertTrue(travelled < FighterSkills.CHARGE_REACH,
			"the charge travelled " + travelled + " into a wall 4 blocks ahead");
		helper.assertTrue(travelled >= 0.0, "the charge moved backwards: " + travelled);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedChargeStaggersTheFirstFoeItHits(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob first = foe(helper, CASTER_X, CASTER_Z + 2.0);
		Mob second = foe(helper, CASTER_X, CASTER_Z + 5.0);
		SkillSchoolGameTest.cast(caster, "charge", null, true);
		LOG.info("[BEHAVIOR] test=awakenedChargeStaggersTheFirstFoeItHits RESULT first={} second={}",
			first.hasEffect(SpellStatus.STAGGERED), second.hasEffect(SpellStatus.STAGGERED));
		helper.assertTrue(first.hasEffect(SpellStatus.STAGGERED), "the first foe on the path was not staggered");
		helper.assertFalse(second.hasEffect(SpellStatus.STAGGERED), "a later foe on the path was staggered too");
		cleanup(helper, first, second);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void executeTreblesUnderTheHealthThresholdAndDoublesAgainWhenTheFoeBleeds(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob healthy = foe(helper, CASTER_X, CASTER_Z + 2.0);
		Mob wounded = foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob woundedBleeding = foe(helper, CASTER_X, CASTER_Z + 6.0);
		wounded.setHealth(wounded.getMaxHealth() * FighterSkills.EXECUTE_THRESHOLD * 0.5F);
		woundedBleeding.setHealth(woundedBleeding.getMaxHealth() * FighterSkills.EXECUTE_THRESHOLD * 0.5F);
		SpellStatusApi.bleed(woundedBleeding, 600, 2, caster);
		float healthyBefore = healthy.getHealth();
		float woundedBefore = wounded.getHealth();
		float bleedingBefore = woundedBleeding.getHealth();
		SkillSchoolGameTest.cast(caster, "execute", healthy, false);
		SkillSchoolGameTest.cast(caster, "execute", wounded, false);
		SkillSchoolGameTest.cast(caster, "execute", woundedBleeding, false);
		double plain = healthyBefore - healthy.getHealth();
		double low = woundedBefore - wounded.getHealth();
		double bleeding = bleedingBefore - woundedBleeding.getHealth();
		LOG.info("[BEHAVIOR] test=executeTreblesUnderTheHealthThresholdAndDoublesAgainWhenTheFoeBleeds RESULT plain={} low={} bleeding={} lowFactor={} bleedFactor={}",
			plain, low, bleeding, low / plain, bleeding / low);
		helper.assertTrue(Math.abs(low / plain - FighterSkills.EXECUTE_LOW_FACTOR) < 0.05,
			"a wounded foe took " + low / plain + " times the plain hit");
		helper.assertTrue(Math.abs(bleeding / low - FighterSkills.EXECUTE_BLEED_FACTOR) < 0.05,
			"a bleeding wounded foe took " + bleeding / low + " times the wounded hit");
		cleanup(helper, healthy, wounded, woundedBleeding);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedExecuteClearsTheCooldownOnAKill(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob doomed = foe(helper, CASTER_X, CASTER_Z + 2.0);
		doomed.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
		doomed.setHealth(1.0F);
		boolean applied = SkillSchoolGameTest.cast(caster, "execute", doomed, true);
		boolean skipped = !doomed.isAlive();
		LOG.info("[BEHAVIOR] test=awakenedExecuteClearsTheCooldownOnAKill RESULT applied={} killed={} cooldownSkipped={}",
			applied, skipped, SpellCasting.skipCooldownRequested());
		helper.assertTrue(skipped, "the doomed foe survived a plain execute, so the case proves nothing");
		helper.assertTrue(SpellCasting.skipCooldownRequested(), "an awakened kill did not clear the cooldown");
		cleanup(helper, doomed);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void warCryTurnsTheFoesOnTheCasterAndHeartensIt(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob foe = foe(helper, CASTER_X, CASTER_Z + 4.0);
		SkillSchoolGameTest.cast(caster, "war_cry", null, false);
		LOG.info("[BEHAVIOR] test=warCryTurnsTheFoesOnTheCasterAndHeartensIt RESULT target={} resistance={} strength={}",
			foe.getTarget() == caster, caster.hasEffect(MobEffects.RESISTANCE), caster.hasEffect(MobEffects.STRENGTH));
		helper.assertTrue(foe.getTarget() == caster, "the foe did not turn on the caster");
		helper.assertTrue(caster.hasEffect(MobEffects.RESISTANCE), "the caster gained no Resistance");
		helper.assertFalse(caster.hasEffect(MobEffects.STRENGTH), "a plain war cry gave the caster Strength");
		cleanup(helper, foe);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedWarCryHeartensTheAlliesWithinReach(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		IronGolem ally = helper.spawn(EntityTypes.IRON_GOLEM, (int) CASTER_X + 2, 1, (int) CASTER_Z + 2);
		SkillSchoolGameTest.cast(caster, "war_cry", null, true);
		LOG.info("[BEHAVIOR] test=awakenedWarCryHeartensTheAlliesWithinReach RESULT casterStrength={} allyStrength={} allyDistance={}",
			caster.hasEffect(MobEffects.STRENGTH), ally.hasEffect(MobEffects.STRENGTH), ally.distanceTo(caster));
		helper.assertTrue(caster.hasEffect(MobEffects.STRENGTH), "an awakened war cry left the caster unstirred");
		helper.assertTrue(ally.hasEffect(MobEffects.STRENGTH), "the ally within reach was not heartened");
		ally.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void counterStanceTurnsTheFirstMeleeBlowAsideAndAnswersIt(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob attacker = foe(helper, CASTER_X, CASTER_Z + 2.0);
		SkillSchoolGameTest.cast(caster, "counter_stance", null, false);
		float casterBefore = caster.getHealth();
		float attackerBefore = attacker.getHealth();
		caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
		float taken = casterBefore - caster.getHealth();
		LOG.info("[BEHAVIOR] test=counterStanceTurnsTheFirstMeleeBlowAsideAndAnswersIt RESULT windows={} taken={} attackerLost={} attackerStaggered={}",
			FighterSkills.counterWindows(), taken, attackerBefore - attacker.getHealth(), attacker.hasEffect(SpellStatus.STAGGERED));
		helper.assertTrue(taken == 0.0F, "the first blow inside the window took " + taken + " health");
		helper.assertTrue(attackerBefore - attacker.getHealth() > 0.0F, "the answer took nothing from the attacker");
		helper.assertTrue(attacker.hasEffect(SpellStatus.STAGGERED), "the answer did not stagger the attacker");
		cleanup(helper, attacker);
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void counterStanceClosesItsWindowAfterTheAnswer(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob attacker = foe(helper, CASTER_X, CASTER_Z + 2.0);
		SkillSchoolGameTest.cast(caster, "counter_stance", null, false);
		caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
		int windowsAfterAnswer = FighterSkills.counterWindows();
		helper.runAfterDelay(6L, () -> {
			float before = caster.getHealth();
			caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
			float taken = before - caster.getHealth();
			LOG.info("[BEHAVIOR] test=counterStanceClosesItsWindowAfterTheAnswer RESULT windowsAfterAnswer={} secondBlowTaken={}",
				windowsAfterAnswer, taken);
			helper.assertTrue(windowsAfterAnswer == 0, "the window stayed open after its answer: " + windowsAfterAnswer);
			helper.assertTrue(taken > 0.0F, "a second blow was turned aside too");
			cleanup(helper, attacker);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void awakenedCounterStanceTurnsEveryBlowInsideItsWindow(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob attacker = foe(helper, CASTER_X, CASTER_Z + 2.0);
		SkillSchoolGameTest.cast(caster, "counter_stance", null, true);
		float before = caster.getHealth();
		caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
		caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
		float taken = before - caster.getHealth();
		LOG.info("[BEHAVIOR] test=awakenedCounterStanceTurnsEveryBlowInsideItsWindow RESULT twoBlowsTaken={} windowsLeft={} tagged={}",
			taken, FighterSkills.counterWindows(), caster.entityTags().contains(FighterSkills.COUNTER_AWAKENED_TAG));
		helper.assertTrue(taken == 0.0F, "an awakened stance let " + taken + " through two blows");
		helper.assertTrue(FighterSkills.counterWindows() == 1, "the awakened window closed after its first answer");
		cleanup(helper, attacker);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void ironSkinGivesResistanceForHalfAnHour(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		SkillSchoolGameTest.cast(caster, "iron_skin", null, false);
		MobEffectInstance resistance = caster.getEffect(MobEffects.RESISTANCE);
		LOG.info("[BEHAVIOR] test=ironSkinGivesResistanceForHalfAnHour RESULT resistance={} duration={} expected={} amplified={}",
			resistance != null, resistance == null ? 0 : resistance.getDuration(), SpellNumbers.IRON_SKIN.duration().ticks(),
			caster.entityTags().contains(FighterSkills.IRON_SKIN_AWAKENED_TAG));
		helper.assertTrue(resistance != null, "Iron Skin left no Resistance");
		helper.assertTrue(resistance.getDuration() == SpellNumbers.IRON_SKIN.duration().ticks(),
			"the Resistance lasts " + resistance.getDuration() + " ticks, not " + SpellNumbers.IRON_SKIN.duration().ticks());
		helper.assertFalse(caster.entityTags().contains(FighterSkills.IRON_SKIN_AWAKENED_TAG), "a plain Iron Skin carries the awakened mark");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedIronSkinSendsTheShareBackAtItsAttacker(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob attacker = foe(helper, CASTER_X, CASTER_Z + 2.0);
		SkillSchoolGameTest.cast(caster, "iron_skin", null, true);
		float attackerBefore = attacker.getHealth();
		float melee = 10.0F;
		caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), melee);
		float sentBack = attackerBefore - attacker.getHealth();
		LOG.info("[BEHAVIOR] test=awakenedIronSkinSendsTheShareBackAtItsAttacker RESULT melee={} sentBack={} share={} expected={}",
			melee, sentBack, FighterSkills.IRON_SKIN_REFLECT, melee * FighterSkills.IRON_SKIN_REFLECT);
		helper.assertTrue(Math.abs(sentBack - melee * FighterSkills.IRON_SKIN_REFLECT) < 1.0E-3,
			"the skin sent back " + sentBack + ", not " + melee * FighterSkills.IRON_SKIN_REFLECT);
		cleanup(helper, attacker);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void earthsplitterBreaksTheLineAhead(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob onLine = foe(helper, CASTER_X, CASTER_Z + 5.0);
		Mob aside = foe(helper, CASTER_X + 3.0, CASTER_Z + 5.0);
		float onLineBefore = onLine.getHealth();
		float asideBefore = aside.getHealth();
		SkillSchoolGameTest.cast(caster, "earthsplitter", null, false);
		double pushed = onLine.getDeltaMovement().y;
		LOG.info("[BEHAVIOR] test=earthsplitterBreaksTheLineAhead RESULT onLineLost={} asideLost={} pushUp={} lineWidth={}",
			onLineBefore - onLine.getHealth(), asideBefore - aside.getHealth(), pushed, SpellNumbers.EARTHSPLITTER.radius());
		helper.assertTrue(onLineBefore - onLine.getHealth() > 0.0F, "the foe on the line took nothing");
		helper.assertTrue(asideBefore - aside.getHealth() == 0.0F, "the foe three blocks aside took a hit");
		helper.assertTrue(pushed > 0.0, "the broken ground pushed nothing upward: " + pushed);
		cleanup(helper, onLine, aside);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedEarthsplitterBreaksThreeLinesAndHitsEachFoeOnce(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob centre = foe(helper, CASTER_X, CASTER_Z + 5.0);
		Mob left = foe(helper, CASTER_X - 2.0, CASTER_Z + 5.0);
		Mob right = foe(helper, CASTER_X + 2.0, CASTER_Z + 5.0);
		Mob farAside = foe(helper, CASTER_X + 3.5, CASTER_Z - 3.0);
		float centreBefore = centre.getHealth();
		float leftBefore = left.getHealth();
		float rightBefore = right.getHealth();
		float asideBefore = farAside.getHealth();
		SkillSchoolGameTest.cast(caster, "earthsplitter", null, true);
		double centreLost = centreBefore - centre.getHealth();
		LOG.info("[BEHAVIOR] test=awakenedEarthsplitterBreaksThreeLinesAndHitsEachFoeOnce RESULT centre={} left={} right={} behindMissing={} angles=3",
			centreLost, leftBefore - left.getHealth(), rightBefore - right.getHealth(), asideBefore - farAside.getHealth() == 0.0F);
		helper.assertTrue(centreLost > 0.0F, "the centre line took nothing");
		helper.assertTrue(leftBefore - left.getHealth() > 0.0F, "the left line took nothing");
		helper.assertTrue(rightBefore - right.getHealth() > 0.0F, "the right line took nothing");
		helper.assertTrue(asideBefore - farAside.getHealth() == 0.0F, "a foe behind the caster was hit");
		cleanup(helper, centre, left, right, farAside);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void theAwakenedHelperHandsTheFlagToTheEffect(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		ItemStack awakenedStack = SkillSchoolGameTest.awakenedBook("cleave");
		boolean stackAwakened = Awakening.isAwakened(awakenedStack);
		caster.setItemInHand(InteractionHand.MAIN_HAND, awakenedStack);
		boolean handAwakened = Awakening.isAwakened(caster.getItemInHand(InteractionHand.MAIN_HAND));
		SpellModifiers.CastModifiers seen = SkillSchoolGameTest.modifiers(caster, true);
		SpellModifiers.CastModifiers plain = SkillSchoolGameTest.modifiers(caster, false);
		LOG.info("[BEHAVIOR] test=theAwakenedHelperHandsTheFlagToTheEffect RESULT stackAwakened={} handAwakened={} awakenedModifiers={} plainModifiers={} count={}",
			stackAwakened, handAwakened, seen.awakened(), plain.awakened(), awakenedStack.get(Awakening.COUNT));
		helper.assertTrue(stackAwakened && handAwakened && seen.awakened() && !plain.awakened(),
			"the helper lost the awakened flag: stack=" + stackAwakened + " hand=" + handAwakened
				+ " awakened=" + seen.awakened() + " plain=" + plain.awakened());
		helper.succeed();
	}

	/** A player at the arena's casting spot, facing +Z, able to take a blow. */
	static ServerPlayer caster(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		helper.setTime(18_000L);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		// The harness marks a mock player permanently invulnerable; a case about taking blows must clear that first.
		player.setPermanentlyInvulnerable(false);
		player.getAbilities().invulnerable = false;
		Vec3 at = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		player.setNoGravity(true);
		player.getInventory().setItem(35, new ItemStack(Items.IRON_SWORD));
		return player;
	}

	/** A foe that cannot fight back, with no armor to blunt a hit, and enough health to outlast the case. */
	static Mob foe(final GameTestHelper helper, final double x, final double z) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a foe");
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		mob.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		mob.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static void cleanup(final GameTestHelper helper, final Entity... subjects) {
		for (Entity subject : subjects) {
			subject.discard();
		}
	}
}
