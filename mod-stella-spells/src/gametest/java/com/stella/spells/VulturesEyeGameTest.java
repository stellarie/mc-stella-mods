package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulture's Eye: a half-hour buff in which an arrow hit or an archer skill hit gains 2 percent per block of
 * distance beyond 8, up to 40 percent, 60 percent awakened. The distances are the spec numbers, not the constants.
 */
public class VulturesEyeGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.vultureseye");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final float BLOW = 10.0F;
	private static final double TOLERANCE = 0.005;

	@GameTest(maxTicks = 40)
	public void anArrowHitGainsTwoPercentPerBlockBeyondEightUpToFortyPercent(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 1.0);
		double[][] cases = {{5.0, 1.00}, {8.0, 1.00}, {18.0, 1.20}, {28.0, 1.40}, {60.0, 1.40}};
		StringBuilder report = new StringBuilder();
		for (double[] row : cases) {
			double ratio = arrowAt(helper, caster, foe, row[0]) / BLOW;
			report.append(row[0]).append("=").append(ratio).append(" ");
			helper.assertTrue(Math.abs(ratio - row[1]) < TOLERANCE, "an arrow at " + row[0] + " blocks dealt " + ratio + " of its blow, not " + row[1]);
		}
		LOG.info("[BEHAVIOR] test=anArrowHitGainsTwoPercentPerBlockBeyondEightUpToFortyPercent RESULT report={}", report);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void anAwakenedBuffCapsAtSixtyPercent(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, true);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 1.0);
		double[][] cases = {{18.0, 1.20}, {28.0, 1.40}, {38.0, 1.60}, {70.0, 1.60}};
		for (double[] row : cases) {
			double ratio = arrowAt(helper, caster, foe, row[0]) / BLOW;
			helper.assertTrue(Math.abs(ratio - row[1]) < TOLERANCE, "an awakened arrow at " + row[0] + " blocks dealt " + ratio + ", not " + row[1]);
		}
		LOG.info("[BEHAVIOR] test=anAwakenedBuffCapsAtSixtyPercent RESULT ok=true caps={}/{}", ArcherSkills.VULTURE_CAP, ArcherSkills.VULTURE_AWAKENED_CAP);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void anArcherSkillHitGainsTheSameDistanceBonus(final GameTestHelper helper) {
		ServerPlayer caster = buffed(helper, false);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 1.0);
		double near = skillAt(helper, caster, foe, 5.0);
		double far = skillAt(helper, caster, foe, 28.0);
		LOG.info("[BEHAVIOR] test=anArcherSkillHitGainsTheSameDistanceBonus RESULT near={} far={} ratio={}", near, far, far / near);
		helper.assertTrue(near > 0.0, "a near skill hit dealt nothing");
		helper.assertTrue(Math.abs(far / near - 1.40) < TOLERANCE, "a far skill hit dealt " + far / near + " of a near one, not 1.40");
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aCasterWithoutTheBuffGainsNothingAtRange(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.removeAllEffects();
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 1.0);
		double ratio = arrowAt(helper, caster, foe, 40.0) / BLOW;
		LOG.info("[BEHAVIOR] test=aCasterWithoutTheBuffGainsNothingAtRange RESULT ratio={}", ratio);
		helper.assertTrue(Math.abs(ratio - 1.0) < TOLERANCE, "an unbuffed arrow at 40 blocks dealt " + ratio);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void vulturesEyeNeedsABowAndHoldsForThirtyMinutes(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.removeAllEffects();
		SkillSchoolGameTest.setAutoKit(false);
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SkillSchoolGameTest.book("vultures_eye")));
		SpellCasting.CastResult bare = SpellCasting.cast(caster, SkillSchoolGameTest.book("vultures_eye"), InteractionHand.MAIN_HAND);
		boolean heldBare = caster.hasEffect(SpellStatus.VULTURES_EYE);
		caster.getInventory().setItem(35, new ItemStack(Items.BOW));
		SpellCasting.CastResult armed = SpellCasting.cast(caster, SkillSchoolGameTest.book("vultures_eye"), InteractionHand.MAIN_HAND);
		MobEffectInstance held = caster.getEffect(SpellStatus.VULTURES_EYE);
		LOG.info("[BEHAVIOR] test=vulturesEyeNeedsABowAndHoldsForThirtyMinutes RESULT bare={} bareHeld={} armed={} duration={}",
			bare, heldBare, armed, held == null ? 0 : held.getDuration());
		helper.assertTrue(bare != SpellCasting.CastResult.CAST && !heldBare, "vulture's eye cast with no bow: " + bare);
		helper.assertTrue(armed == SpellCasting.CastResult.CAST, "vulture's eye read " + armed + " with a bow");
		helper.assertTrue(held != null && held.getDuration() == SpellNumbers.VULTURES_EYE.duration().ticks()
			&& SpellNumbers.VULTURES_EYE.duration().ticks() == 36_000, "the buff lasts " + (held == null ? 0 : held.getDuration()));
		helper.assertTrue(SkillSchoolGameTest.book("vultures_eye").definition().school() == School.ARCHER, "vulture's eye is not an archer book");
		helper.succeed();
	}

	private static ServerPlayer buffed(final GameTestHelper helper, final boolean awakened) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.removeAllEffects();
		caster.addEffect(new MobEffectInstance(SpellStatus.VULTURES_EYE, ArcherSkills.VULTURES_EYE_TICKS, awakened ? 1 : 0));
		return caster;
	}

	/** The health an arrow from this far away takes off the foe. */
	private static double arrowAt(final GameTestHelper helper, final ServerPlayer caster, final Mob foe, final double distance) {
		Vec3 at = foe.position();
		caster.setPos(at.x, at.y, at.z - distance);
		foe.damageCooldownTime = 0;
		foe.setHealth(1000.0F);
		Arrow arrow = new Arrow(helper.getLevel(), caster, new ItemStack(Items.ARROW), null);
		foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), BLOW);
		return 1000.0 - foe.getHealth();
	}

	private static double skillAt(final GameTestHelper helper, final ServerPlayer caster, final Mob foe, final double distance) {
		Vec3 at = foe.position();
		caster.setPos(at.x, at.y, at.z - distance);
		foe.damageCooldownTime = 0;
		foe.setHealth(1000.0F);
		ArcherSkills.hit(caster, foe, BLOW, SkillSchoolGameTest.modifiers(caster, false));
		return 1000.0 - foe.getHealth();
	}
}
