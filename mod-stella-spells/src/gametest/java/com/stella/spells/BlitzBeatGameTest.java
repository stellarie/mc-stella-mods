package com.stella.spells;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blitz Beat as a half-hour buff: a melee hit or an own arrow hit may call the falcon, which strikes the
 * struck foe at the caster floor ATK. Skill damage, an imbued cast and a falcon strike never call it, and a
 * strike never crits. The roll is driven by a seeded source, or forced, so no case waits on luck.
 */
public class BlitzBeatGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.blitzbeat");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final int WEAPON_SLOT = 35;
	private static final double FAR_CORNER = 7.5;
	private static final float BLOW = 3.0F;
	private static final int SAMPLES = 4_000;
	private static final long SEED = 4242L;
	/** Long enough for the slowest falcon to land every strike. */
	private static final long SETTLE_TICKS = 12L * ArcherSkills.BLITZ_BEAT_INTERVAL_TICKS + 20L;

	/** A source whose every roll comes up on the lucky end. */
	private static final class Always extends LegacyRandomSource {
		Always() {
			super(1L);
		}

		@Override
		public double nextDouble() {
			return 0.0;
		}
	}

	@GameTest(maxTicks = 40)
	public void blitzBeatCastsWithAnEmptyHandAndHoldsForThirtyMinutes(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SkillSchoolGameTest.book("hunters_mark")));
		SpellCasting.CastResult result = SpellCasting.cast(caster, SkillSchoolGameTest.book("hunters_mark"), InteractionHand.MAIN_HAND);
		MobEffectInstance held = caster.getEffect(SpellStatus.BLITZ_BEAT);
		LOG.info("[BEHAVIOR] test=blitzBeatCastsWithAnEmptyHandAndHoldsForThirtyMinutes RESULT result={} held={} duration={} mode={}",
			result, held != null, held == null ? 0 : held.getDuration(), SkillSchoolGameTest.book("hunters_mark").definition().targetMode());
		helper.assertFalse(ArcherSkills.hasBowOrCrossbow(caster), "the caster carried a bow, so the case proves nothing");
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "blitz beat read " + result + " with no bow and no target");
		helper.assertTrue(held != null, "the cast left no buff");
		helper.assertTrue(held.getDuration() == SpellNumbers.HUNTERS_MARK.duration().ticks()
			&& SpellNumbers.HUNTERS_MARK.duration().ticks() == ArcherSkills.BLITZ_BEAT_TICKS && ArcherSkills.BLITZ_BEAT_TICKS == 36_000,
			"the buff lasts " + held.getDuration() + " ticks");
		helper.assertTrue(SkillSchoolGameTest.book("hunters_mark").definition().targetMode() == TargetMode.SELF, "blitz beat still wants a target");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void luckRaisesTheFalconRateByOnePercentPerFivePoints(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		UUIDHolder.LUCKY = caster.getUUID();
		ArcherSkills.setBlitzStats((holder, key) -> holder.getUUID().equals(UUIDHolder.LUCKY) && key.equals("luk") ? 100.0 : 0.0);
		try {
			double expected = ArcherSkills.BLITZ_BEAT_CHANCE + 0.20;
			double share = rate(caster, ArcherSkills.BLITZ_BEAT_STRIKES);
			double sigma = Math.sqrt(expected * (1.0 - expected) / SAMPLES);
			LOG.info("[BEHAVIOR] test=luckRaisesTheFalconRate RESULT luk=100 share={} expected={} sigma={}", share, expected, sigma);
			helper.assertTrue(Math.abs(share - expected) <= 3.0 * sigma, "with 100 LUK the falcon came on " + share + ", not near " + expected);
		} finally {
			ArcherSkills.setBlitzStats(null);
		}
		helper.succeed();
	}

	/** The caster the LUK stand-in answers for, so a parallel case reads 0. */
	private static final class UUIDHolder {
		static volatile java.util.UUID LUCKY = new java.util.UUID(0L, 0L);
	}

	@GameTest(maxTicks = 40)
	public void theFalconProcRateIsAQuarterWithoutABowAndThreeStrikes(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		double share = rate(caster, ArcherSkills.BLITZ_BEAT_STRIKES);
		double sigma = Math.sqrt(ArcherSkills.BLITZ_BEAT_CHANCE * (1.0 - ArcherSkills.BLITZ_BEAT_CHANCE) / SAMPLES);
		LOG.info("[BEHAVIOR] test=theFalconProcRateIsAQuarterWithoutABowAndThreeStrikes RESULT share={} chance={} sigma={} samples={}",
			share, ArcherSkills.BLITZ_BEAT_CHANCE, sigma, SAMPLES);
		helper.assertTrue(share > 0.0, "no sample called the falcon");
		helper.assertTrue(Math.abs(share - ArcherSkills.BLITZ_BEAT_CHANCE) <= 3.0 * sigma,
			"the falcon came on " + share + " of the hits, not near " + ArcherSkills.BLITZ_BEAT_CHANCE);
		helper.assertTrue(ArcherSkills.BLITZ_BEAT_CHANCE == 0.25 && ArcherSkills.BLITZ_BEAT_STRIKES == 3,
			"the book is declared as " + ArcherSkills.BLITZ_BEAT_CHANCE + " for " + ArcherSkills.BLITZ_BEAT_STRIKES + " strikes");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theFalconProcRateWithABowInEitherHandIsThirtyFivePercentAndSixStrikes(final GameTestHelper helper) {
		ServerPlayer main = buffed(helper, false);
		main.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		double mainShare = rate(main, ArcherSkills.BLITZ_BEAT_BOW_STRIKES);
		ServerPlayer off = buffed(helper, false);
		off.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CROSSBOW));
		double offShare = rate(off, ArcherSkills.BLITZ_BEAT_BOW_STRIKES);
		double sigma = Math.sqrt(ArcherSkills.BLITZ_BEAT_BOW_CHANCE * (1.0 - ArcherSkills.BLITZ_BEAT_BOW_CHANCE) / SAMPLES);
		LOG.info("[BEHAVIOR] test=theFalconProcRateWithABowInEitherHandIsThirtyFivePercentAndSixStrikes RESULT main={} off={} chance={} sigma={}",
			mainShare, offShare, ArcherSkills.BLITZ_BEAT_BOW_CHANCE, sigma);
		helper.assertTrue(Math.abs(mainShare - ArcherSkills.BLITZ_BEAT_BOW_CHANCE) <= 3.0 * sigma, "a bow in the main hand gave " + mainShare);
		helper.assertTrue(Math.abs(offShare - ArcherSkills.BLITZ_BEAT_BOW_CHANCE) <= 3.0 * sigma, "a crossbow in the off hand gave " + offShare);
		helper.assertTrue(ArcherSkills.BLITZ_BEAT_BOW_CHANCE == 0.35 && ArcherSkills.BLITZ_BEAT_BOW_STRIKES == 6,
			"the bow numbers are " + ArcherSkills.BLITZ_BEAT_BOW_CHANCE + " and " + ArcherSkills.BLITZ_BEAT_BOW_STRIKES);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aBowOnlyInTheWeaponSlotDoesNotRaiseTheChance(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		double share = rate(caster, ArcherSkills.BLITZ_BEAT_STRIKES);
		double sigma = Math.sqrt(ArcherSkills.BLITZ_BEAT_CHANCE * (1.0 - ArcherSkills.BLITZ_BEAT_CHANCE) / SAMPLES);
		LOG.info("[BEHAVIOR] test=aBowOnlyInTheWeaponSlotDoesNotRaiseTheChance RESULT share={}", share);
		helper.assertTrue(Math.abs(share - ArcherSkills.BLITZ_BEAT_CHANCE) <= 3.0 * sigma, "a pack bow gave " + share);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aCasterWithoutTheBuffNeverCallsTheFalcon(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		int strikes = ArcherSkills.blitzStrikes(caster, new Always());
		LOG.info("[BEHAVIOR] test=aCasterWithoutTheBuffNeverCallsTheFalcon RESULT strikes={}", strikes);
		helper.assertTrue(strikes == 0, "a caster with no buff called " + strikes + " strikes");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aMeleeHitCallsThreeStrikesAtBaseDamageWithoutTheRpgMod(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		Mob foe = foe(helper);
		forced(() -> foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(caster), BLOW));
		settle(helper, "aMeleeHitCallsThreeStrikesAtBaseDamageWithoutTheRpgMod", caster, foe,
			ArcherSkills.BLITZ_BEAT_STRIKES, SpellNumbers.HUNTERS_MARK.damage());
	}

	@GameTest(maxTicks = 200)
	public void aMeleeHitWithABowInHandCallsSixStrikes(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOW));
		Mob foe = foe(helper);
		forced(() -> foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(caster), BLOW));
		settle(helper, "aMeleeHitWithABowInHandCallsSixStrikes", caster, foe,
			ArcherSkills.BLITZ_BEAT_BOW_STRIKES, SpellNumbers.HUNTERS_MARK.damage());
	}

	@GameTest(maxTicks = 200)
	public void anOwnArrowHitCallsTheFalcon(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		Mob foe = foe(helper);
		Arrow arrow = new Arrow(helper.getLevel(), caster, new ItemStack(Items.ARROW), null);
		forced(() -> foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), BLOW));
		settle(helper, "anOwnArrowHitCallsTheFalcon", caster, foe, ArcherSkills.BLITZ_BEAT_STRIKES, SpellNumbers.HUNTERS_MARK.damage());
	}

	@GameTest(maxTicks = 200)
	public void anAwakenedBuffDoublesTheStrikes(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, true);
		Mob foe = foe(helper);
		forced(() -> foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(caster), BLOW));
		settle(helper, "anAwakenedBuffDoublesTheStrikes", caster, foe,
			ArcherSkills.BLITZ_BEAT_STRIKES * ArcherSkills.AWAKENED_BLITZ_BEAT_FACTOR, SpellNumbers.HUNTERS_MARK.damage());
	}

	@GameTest(maxTicks = 200)
	public void skillDamageNeverCallsTheFalcon(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		Mob foe = foe(helper);
		forced(() -> {
			ArcherSkills.hit(caster, foe, BLOW, SkillSchoolGameTest.modifiers(caster, false));
			foe.damageCooldownTime = 0;
			ArcherSkills.applyDoubleStrafe(caster, foe, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		});
		settle(helper, "skillDamageNeverCallsTheFalcon", caster, foe, 0, 0.0F, BLOW + SpellNumbers.RAPID_FIRE.damage() * ArcherSkills.DOUBLE_STRAFE_HITS);
	}

	@GameTest(maxTicks = 200)
	public void anImbuedCastNeverCallsTheFalcon(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		Mob foe = foe(helper);
		CastContext.setImbuedVictim(foe);
		try {
			forced(() -> foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(caster), BLOW));
		} finally {
			CastContext.setImbuedVictim(null);
		}
		settle(helper, "anImbuedCastNeverCallsTheFalcon", caster, foe, 0, 0.0F, BLOW);
	}

	@GameTest(maxTicks = 200)
	public void aCasterWithoutTheBuffHitsOnceEvenWhenTheRollIsForced(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob foe = foe(helper);
		forced(() -> foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(caster), BLOW));
		settle(helper, "aCasterWithoutTheBuffHitsOnceEvenWhenTheRollIsForced", caster, foe, 0, 0.0F, BLOW);
	}

	@GameTest(maxTicks = 200)
	public void eachStrikeDealsTheRpgFloorAtkMeleeOrRanged(final GameTestHelper helper) {
		ServerPlayer melee = buffed(helper, false);
		Mob meleeFoe = foe(helper);
		ServerPlayer ranged = buffed(helper, false);
		ranged.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		Mob rangedFoe = foe(helper);
		// The stub is global and the cases run side by side, so it is held only across the two blows.
		RpgBridge.setStats(new FloorStats());
		try {
			forced(() -> {
				meleeFoe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(melee), BLOW);
				rangedFoe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(ranged), BLOW);
			});
		} finally {
			RpgBridge.setStats(null);
		}
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			double k = RpgBridge.k();
			double meleeLost = 1000.0 - meleeFoe.getHealth();
			double rangedLost = 1000.0 - rangedFoe.getHealth();
			double meleeWant = BLOW + ArcherSkills.BLITZ_BEAT_STRIKES * FloorStats.MELEE / k;
			double rangedWant = BLOW + ArcherSkills.BLITZ_BEAT_BOW_STRIKES * FloorStats.RANGED / k;
			LOG.info("[BEHAVIOR] test=eachStrikeDealsTheRpgFloorAtkMeleeOrRanged RESULT meleeLost={} want={} rangedLost={} want={}",
				meleeLost, meleeWant, rangedLost, rangedWant);
			helper.assertTrue(Math.abs(meleeLost - meleeWant) < 0.05, "melee strikes took " + meleeLost + ", not " + meleeWant);
			helper.assertTrue(Math.abs(rangedLost - rangedWant) < 0.05, "ranged strikes took " + rangedLost + ", not " + rangedWant);
			ArcherSkills.clearFor(melee);
			ArcherSkills.clearFor(ranged);
			meleeFoe.discard();
			rangedFoe.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void theBridgeReadsMeleeFloorFromStrAndRangedFloorFromDexThroughTheRpgClasses(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		RpgBridge.Stats bridge = RpgBridge.findAttack(RpgAttackStandIn.class.getName(), RpgAttackStandIn.class.getName());
		double melee = bridge.floorAtk(caster, false);
		double ranged = bridge.floorAtk(caster, true);
		LOG.info("[BEHAVIOR] test=theBridgeReadsMeleeFloorFromStrAndRangedFloorFromDexThroughTheRpgClasses RESULT melee={} ranged={}", melee, ranged);
		helper.assertTrue(melee == RpgAttackStandIn.STR * RpgAttackStandIn.MELEE_LOW_PER_STR, "the melee floor read " + melee);
		helper.assertTrue(ranged == RpgAttackStandIn.DEX * RpgAttackStandIn.RANGED_LOW_PER_DEX, "the ranged floor read " + ranged);
		helper.assertTrue(Double.isNaN(RpgBridge.floorAtk(caster, true)), "an absent RPG mod gave a floor ATK");
		helper.succeed();
	}

	/** The real RPG classes, not a stand-in: this harness carries no copy, so the case is not required and fails with what it lacks. */
	@GameTest(required = false, maxTicks = 40)
	public void theRealRpgFloorAtkEqualsTheAttackPreviewLowEnd(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		double expected = Double.NaN;
		try {
			Class<?> preview = Class.forName("com.stella.rpg.AttackPreview");
			Class<?> skills = Class.forName("com.stella.rpg.RpgSkills");
			Method stat = skills.getMethod("stat", LivingEntity.class, String.class);
			Method range = preview.getMethod("range", LivingEntity.class, int.class);
			int str = (int) (double) (Double) stat.invoke(null, caster, "str");
			expected = ((double[]) range.invoke(null, caster, str))[0];
		} catch (ReflectiveOperationException | LinkageError e) {
			helper.fail("the RPG mod AttackPreview is absent: " + e);
			return;
		}
		double read = RpgBridge.floorAtk(caster, false);
		LOG.info("[BEHAVIOR] test=theRealRpgFloorAtkEqualsTheAttackPreviewLowEnd RESULT read={} expected={}", read, expected);
		helper.assertTrue(read == expected, "the bridge read " + read + ", not " + expected);
		helper.succeed();
	}

	/** A crit cannot be forced without the RPG mod critical seam, so this is a manual gap. */
	@GameTest(required = false, maxTicks = 40)
	public void aFalconStrikeNeverCritsRequiresTheRpgJar(final GameTestHelper helper) {
		helper.fail("[MANUAL] with the RPG mod and a critical chance of 100 percent, call the falcon and confirm every strike deals the same floor ATK. The strike uses a fixed number through the skill damage source, so the RPG critical roll has nothing to multiply; this harness cannot load the critical seam.");
	}

	/** The melee floor is 5 and the ranged floor 8, whatever the caster. */
	private static final class FloorStats implements RpgBridge.Stats {
		static final double MELEE = 5.0;
		static final double RANGED = 8.0;

		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double floorAtk(final LivingEntity caster, final boolean ranged) {
			return ranged ? RANGED : MELEE;
		}
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		player.getInventory().clearContent();
		player.removeAllEffects();
		return player;
	}

	private static ServerPlayer buffed(final GameTestHelper helper, final boolean awakened) {
		ServerPlayer player = caster(helper);
		player.addEffect(new MobEffectInstance(SpellStatus.BLITZ_BEAT, ArcherSkills.BLITZ_BEAT_TICKS, awakened ? 1 : 0));
		return player;
	}

	// The far corner of the arena: past the idle falcon's reach, and clear of the casters of the cells beside this one.
	private static Mob foe(final GameTestHelper helper) {
		return FighterBooksGameTest.foe(helper, FAR_CORNER, FAR_CORNER);
	}

	/** Runs the work with every falcon roll coming up lucky. */
	private static void forced(final Runnable work) {
		ArcherSkills.setBlitzRandom(new Always());
		try {
			work.run();
		} finally {
			ArcherSkills.setBlitzRandom(null);
		}
	}

	/** The share of seeded hits that call the falcon, with every call checked for its strike count. */
	private static double rate(final ServerPlayer caster, final int strikes) {
		RandomSource random = RandomSource.create(SEED);
		int called = 0;
		List<Integer> wrong = new ArrayList<>();
		for (int sample = 0; sample < SAMPLES; sample++) {
			int rolled = ArcherSkills.blitzStrikes(caster, random);
			if (rolled > 0) {
				called++;
				if (rolled != strikes) {
					wrong.add(rolled);
				}
			}
		}
		if (!wrong.isEmpty()) {
			throw new IllegalStateException("the falcon came with " + wrong.get(0) + " strikes, not " + strikes);
		}
		return (double) called / SAMPLES;
	}

	private static void settle(
		final GameTestHelper helper, final String test, final ServerPlayer caster, final Mob foe, final int strikes, final float strike
	) {
		settle(helper, test, caster, foe, strikes, strike, BLOW);
	}

	// The strike count is read from the caster own pulse count and from the foe health, never from a timer alone.
	private static void settle(
		final GameTestHelper helper, final String test, final ServerPlayer caster, final Mob foe, final int strikes,
		final float strike, final double blows
	) {
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			double lost = 1000.0 - foe.getHealth();
			double want = blows + strikes * strike;
			LOG.info("[BEHAVIOR] test={} RESULT strikes={} ran={} owed={} lost={} want={}",
				test, strikes, ArcherSkills.pulsesRun(caster), ArcherSkills.pulsesOwed(caster), lost, want);
			helper.assertTrue(ArcherSkills.pulsesRun(caster) == strikes, "the falcon ran " + ArcherSkills.pulsesRun(caster) + " strikes, not " + strikes);
			helper.assertTrue(ArcherSkills.pulsesOwed(caster) == 0, "the falcon left strikes owed");
			helper.assertTrue(Math.abs(lost - want) < 0.05, "the foe lost " + lost + ", not " + want);
			ArcherSkills.clearFor(caster);
			foe.discard();
			helper.succeed();
		});
	}
}
