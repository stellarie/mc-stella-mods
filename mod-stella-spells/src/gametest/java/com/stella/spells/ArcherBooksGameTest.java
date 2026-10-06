package com.stella.spells;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ten archer books, plain and awakened: what each shot hits, how many times, and what it leaves on the
 * ground. Every shot is a hit-scan ray, so a case reads health rather than an arrow entity.
 */
public class ArcherBooksGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.archerbooks");
	/** The caster stands here, facing +Z, so every foe ahead sits on one line. */
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	/** The block the caster's feet stand on, and the one a point-targeting cast aims at. */
	private static final double FLOOR_Y = 0.0;

	@GameTest(maxTicks = 80)
	public void multishotFiresFiveRaysAndEachHitsTheFirstFoeOnIt(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob front = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob behind = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z - 4.0);
		float frontBefore = front.getHealth();
		float behindBefore = behind.getHealth();
		SkillSchoolGameTest.cast(caster, "multishot", null, false);
		LOG.info("[BEHAVIOR] test=multishotFiresFiveRaysAndEachHitsTheFirstFoeOnIt RESULT rays={} frontLost={} behindLost={}",
			ArcherSkills.MULTISHOT_RAYS, frontBefore - front.getHealth(), behindBefore - behind.getHealth());
		helper.assertTrue(frontBefore - front.getHealth() > 0.0F, "the foe in the fan took nothing");
		helper.assertTrue(behindBefore - behind.getHealth() == 0.0F,
			"the foe behind the caster took " + (behindBefore - behind.getHealth()) + " from a fan that never points there");
		helper.assertTrue(ArcherSkills.MULTISHOT_RAYS == 5, "the fan is " + ArcherSkills.MULTISHOT_RAYS + " rays, not 5");
		helper.assertTrue(ArcherSkills.AWAKENED_MULTISHOT_RAYS == 9,
			"the awakened fan is " + ArcherSkills.AWAKENED_MULTISHOT_RAYS + " rays, not 9");
		helper.assertTrue(ArcherSkills.MULTISHOT_FAN_DEGREES == 30.0,
			"the fan spans " + ArcherSkills.MULTISHOT_FAN_DEGREES + " degrees, not 30");
		front.discard();
		behind.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedMultishotBendsEachRayOntoTheNearestFoe(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		// The fan's outer rays leave the caster's line, so a foe a little aside is hit only by the bend.
		Mob aside = FighterBooksGameTest.foe(helper, CASTER_X + 1.8, CASTER_Z + 6.0);
		float before = aside.getHealth();
		SkillSchoolGameTest.cast(caster, "multishot", null, true);
		LOG.info("[BEHAVIOR] test=awakenedMultishotBendsEachRayOntoTheNearestFoe RESULT asideLost={} bendDegrees={} rays={} fanDegrees={}",
			before - aside.getHealth(), ArcherSkills.AWAKENED_HOMING_DEGREES, ArcherSkills.AWAKENED_MULTISHOT_RAYS,
			ArcherSkills.MULTISHOT_FAN_DEGREES);
		helper.assertTrue(before - aside.getHealth() > 0.0F,
			"the awakened fan never bent onto a foe outside its own fan");
		helper.assertTrue(ArcherSkills.AWAKENED_HOMING_DEGREES == 8.0,
			"the bend is " + ArcherSkills.AWAKENED_HOMING_DEGREES + " degrees, not 8");
		aside.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void piercingShotHitsEveryFoeOnTheLine(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		// The eye reaches about 6.5 blocks down the arena's own line, so the whole line sits inside that.
		Mob first = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.5);
		Mob second = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.5);
		Mob aside = FighterBooksGameTest.foe(helper, CASTER_X + 3.0, CASTER_Z + 3.5);
		float firstBefore = first.getHealth();
		float secondBefore = second.getHealth();
		float asideBefore = aside.getHealth();
		SkillSchoolGameTest.cast(caster, "piercing_shot", null, false);
		LOG.info("[BEHAVIOR] test=piercingShotHitsEveryFoeOnTheLine RESULT firstLost={} secondLost={} asideLost={} range={}",
			firstBefore - first.getHealth(), secondBefore - second.getHealth(), asideBefore - aside.getHealth(),
			SpellNumbers.PIERCING_SHOT.radius());
		helper.assertTrue(firstBefore - first.getHealth() > 0.0F, "the first foe on the line took nothing");
		helper.assertTrue(secondBefore - second.getHealth() > 0.0F, "the second foe on the line took nothing");
		helper.assertTrue(asideBefore - aside.getHealth() == 0.0F, "a foe three blocks off the line was hit");
		helper.assertTrue(SpellNumbers.PIERCING_SHOT.radius() == 32.0,
			"the line is " + SpellNumbers.PIERCING_SHOT.radius() + " blocks, not 32");
		first.discard();
		second.discard();
		aside.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedPiercingShotRaisesEachLaterFoeByAQuarter(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob first = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.5);
		Mob second = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.5);
		float firstBefore = first.getHealth();
		float secondBefore = second.getHealth();
		SkillSchoolGameTest.cast(caster, "piercing_shot", null, true);
		double firstLost = firstBefore - first.getHealth();
		double secondLost = secondBefore - second.getHealth();
		LOG.info("[BEHAVIOR] test=awakenedPiercingShotRaisesEachLaterFoeByAQuarter RESULT firstLost={} secondLost={} ratio={} step={}",
			firstLost, secondLost, secondLost / firstLost, ArcherSkills.PIERCING_STEP);
		helper.assertTrue(firstLost > 0.0, "the first foe on the line took nothing");
		helper.assertTrue(secondLost > 0.0, "the second foe on the line took nothing, so the ratio proves nothing");
		helper.assertTrue(Math.abs(secondLost / firstLost - (1.0 + ArcherSkills.PIERCING_STEP)) < 0.05,
			"the second foe took " + secondLost / firstLost + " of the first, not " + (1.0 + ArcherSkills.PIERCING_STEP));
		first.discard();
		second.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void arrowRainPulsesFiveTimesTenTicksApart(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, 5.5, CASTER_Z + 5.0);
		Vec3 point = helper.absoluteVec(new Vec3(5.5, FLOOR_Y, CASTER_Z + 5.0));
		List<Float> samples = new ArrayList<>();
		boolean applied = ArcherSkills.applyArrowRain(caster, null, point, SkillSchoolGameTest.modifiers(caster, false));
		helper.onEachTick(() -> samples.add(foe.getHealth()));
		helper.runAfterDelay((long) ArcherSkills.ARROW_RAIN_PULSES * ArcherSkills.RAIN_INTERVAL_TICKS + 20L, () -> {
			double lost = 1000.0 - foe.getHealth();
			LOG.info("[BEHAVIOR] test=arrowRainPulsesFiveTimesTenTicksApart RESULT applied={} pulses={} interval={} lost={} ran={} owed={}",
				applied, ArcherSkills.ARROW_RAIN_PULSES, ArcherSkills.RAIN_INTERVAL_TICKS, lost,
				ArcherSkills.pulsesRun(caster), ArcherSkills.pulsesOwed(caster));
			helper.assertTrue(applied, "the arrow rain never started");
			helper.assertTrue(ArcherSkills.ARROW_RAIN_PULSES == 5, "the rain is " + ArcherSkills.ARROW_RAIN_PULSES + " pulses, not 5");
			helper.assertTrue(ArcherSkills.RAIN_INTERVAL_TICKS == 10, "the gap is " + ArcherSkills.RAIN_INTERVAL_TICKS + " ticks, not 10");
			helper.assertTrue(ArcherSkills.pulsesOwed(caster) == 0, "the rain left pulses owed: " + ArcherSkills.pulsesOwed(caster));
			helper.assertTrue(lost > 0.0, "the rain took nothing from the foe under it");
			helper.assertTrue(ArcherSkills.pulsesRun(caster) == ArcherSkills.ARROW_RAIN_PULSES - 1,
				"the rain ran " + ArcherSkills.pulsesRun(caster) + " later pulses, not " + (ArcherSkills.ARROW_RAIN_PULSES - 1));
			helper.assertTrue(lost >= SpellNumbers.ARROW_RAIN.damage() * ArcherSkills.ARROW_RAIN_PULSES,
				"the foe under the rain lost " + lost + ", less than " + (SpellNumbers.ARROW_RAIN.damage() * ArcherSkills.ARROW_RAIN_PULSES));
			foe.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void awakenedArrowRainPulsesTenTimes(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, 5.5, CASTER_Z + 5.0);
		Vec3 point = helper.absoluteVec(new Vec3(5.5, FLOOR_Y, CASTER_Z + 5.0));
		List<Float> samples = new ArrayList<>();
		ArcherSkills.applyArrowRain(caster, null, point, SkillSchoolGameTest.modifiers(caster, true));
		helper.onEachTick(() -> samples.add(foe.getHealth()));
		helper.runAfterDelay((long) ArcherSkills.AWAKENED_ARROW_RAIN_PULSES * ArcherSkills.RAIN_INTERVAL_TICKS + 20L, () -> {
			LOG.info("[BEHAVIOR] test=awakenedArrowRainPulsesTenTimes RESULT awakenedPulses={} ran={} lost={}",
				ArcherSkills.AWAKENED_ARROW_RAIN_PULSES, ArcherSkills.pulsesRun(caster), 1000.0 - foe.getHealth());
			helper.assertTrue(ArcherSkills.AWAKENED_ARROW_RAIN_PULSES == 10,
				"the awakened rain is " + ArcherSkills.AWAKENED_ARROW_RAIN_PULSES + " pulses, not 10");
			helper.assertTrue(ArcherSkills.AWAKENED_ARROW_RAIN_PULSES > ArcherSkills.ARROW_RAIN_PULSES,
				"the awakened rain is no longer than the plain one");
			helper.assertTrue(ArcherSkills.pulsesRun(caster) == ArcherSkills.AWAKENED_ARROW_RAIN_PULSES - 1,
				"the awakened rain ran " + ArcherSkills.pulsesRun(caster) + " later pulses, not " + (ArcherSkills.AWAKENED_ARROW_RAIN_PULSES - 1));
			helper.assertTrue(1000.0 - foe.getHealth() >= SpellNumbers.ARROW_RAIN.damage() * ArcherSkills.AWAKENED_ARROW_RAIN_PULSES,
				"the awakened rain took " + (1000.0 - foe.getHealth()) + " over all its pulses");
			foe.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void rapidFireLoosesTwelveShotsFiveTicksApart(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		List<Float> samples = new ArrayList<>();
		boolean applied = ArcherSkills.applyRapidFire(caster, foe, null, SkillSchoolGameTest.modifiers(caster, false));
		int owed = ArcherSkills.pulsesOwed(caster);
		helper.onEachTick(() -> samples.add(foe.getHealth()));
		helper.runAfterDelay((long) ArcherSkills.RAPID_FIRE_SHOTS * ArcherSkills.RAPID_FIRE_INTERVAL_TICKS + 20L, () -> {
			LOG.info("[BEHAVIOR] test=rapidFireLoosesTwelveShotsFiveTicksApart RESULT applied={} shots={} interval={} owed={} ran={} lost={}",
				applied, ArcherSkills.RAPID_FIRE_SHOTS, ArcherSkills.RAPID_FIRE_INTERVAL_TICKS, owed,
				ArcherSkills.pulsesRun(caster), 1000.0 - foe.getHealth());
			helper.assertTrue(applied, "rapid fire never started");
			helper.assertTrue(ArcherSkills.RAPID_FIRE_SHOTS == 12, "rapid fire is " + ArcherSkills.RAPID_FIRE_SHOTS + " shots, not 12");
			helper.assertTrue(ArcherSkills.RAPID_FIRE_INTERVAL_TICKS == 5,
				"the shot gap is " + ArcherSkills.RAPID_FIRE_INTERVAL_TICKS + " ticks, not 5");
			helper.assertTrue(owed == ArcherSkills.RAPID_FIRE_SHOTS - 1, "the cast owed " + owed + " further shots");
			helper.assertTrue(ArcherSkills.pulsesRun(caster) == ArcherSkills.RAPID_FIRE_SHOTS - 1,
				"rapid fire ran " + ArcherSkills.pulsesRun(caster) + " later shots, not " + (ArcherSkills.RAPID_FIRE_SHOTS - 1));
			helper.assertTrue(ArcherSkills.pulsesOwed(caster) == 0, "rapid fire left shots owed");
			foe.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void awakenedRapidFireEndsInAnExplosiveArrow(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob target = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X + 1.5, CASTER_Z + 4.0);
		float nearBefore = near.getHealth();
		ArcherSkills.applyRapidFire(caster, target, null, SkillSchoolGameTest.modifiers(caster, true));
		helper.runAfterDelay((long) ArcherSkills.RAPID_FIRE_SHOTS * ArcherSkills.RAPID_FIRE_INTERVAL_TICKS + 20L, () -> {
			LOG.info("[BEHAVIOR] test=awakenedRapidFireEndsInAnExplosiveArrow RESULT nearLost={} targetLost={} splash={}",
				nearBefore - near.getHealth(), 1000.0 - target.getHealth(), ArcherSkills.EXPLOSIVE_SPLASH_RADIUS);
			helper.assertTrue(nearBefore - near.getHealth() > 0.0F, "the last shot did not burst on the foes beside the target");
			target.discard();
			near.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void huntersMarkMarksTheFoeForSixHundredTicks(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		SkillSchoolGameTest.cast(caster, "hunters_mark", foe, false);
		MobEffectInstance mark = foe.getEffect(SpellStatus.MARKED);
		LOG.info("[BEHAVIOR] test=huntersMarkMarksTheFoeForSixHundredTicks RESULT marked={} duration={} expected={} jumpTag={}",
			mark != null, mark == null ? 0 : mark.getDuration(), SpellNumbers.HUNTERS_MARK.duration().ticks(), ArcherSkills.markOwner(foe));
		helper.assertTrue(mark != null, "the mark never landed");
		helper.assertTrue(mark.getDuration() == SpellNumbers.HUNTERS_MARK.duration().ticks(),
			"the mark lasts " + mark.getDuration() + " ticks, not " + SpellNumbers.HUNTERS_MARK.duration().ticks());
		helper.assertTrue(SpellNumbers.HUNTERS_MARK.duration().ticks() == 600,
			"the mark is declared as " + SpellNumbers.HUNTERS_MARK.duration().ticks() + " ticks, not 600");
		helper.assertTrue(ArcherSkills.markOwner(foe) == null, "a plain mark carries the awakened jump tag");
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedHuntersMarkJumpsToTheNearestFoeWhenItsHolderFalls(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob marked = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 7.0);
		Mob far = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 30.0);
		SkillSchoolGameTest.cast(caster, "hunters_mark", marked, true);
		String tagBefore = String.valueOf(ArcherSkills.markOwner(marked));
		MobEffectInstance onMarked = marked.getEffect(SpellStatus.MARKED);
		boolean jumped = ArcherSkills.onMarkedDeath(marked);
		MobEffectInstance onNear = near.getEffect(SpellStatus.MARKED);
		LOG.info("[BEHAVIOR] test=awakenedHuntersMarkJumpsToTheNearestFoeWhenItsHolderFalls RESULT jumped={} nearMarked={} farMarked={} range={} nearTag={} tagBefore={} markedEffectOnHolder={} nearEffect={} nearPos={} markedPos={}",
			jumped, near.hasEffect(SpellStatus.MARKED), far.hasEffect(SpellStatus.MARKED), ArcherSkills.MARK_JUMP_RANGE,
			ArcherSkills.markOwner(near), tagBefore, onMarked != null, onNear != null, near.position(), marked.position());
		helper.assertTrue(jumped, "the awakened mark never jumped");
		helper.assertTrue(near.hasEffect(SpellStatus.MARKED), "the nearest foe within reach took no mark");
		helper.assertFalse(far.hasEffect(SpellStatus.MARKED), "the mark reached a foe past its own reach");
		helper.assertTrue(ArcherSkills.markOwner(near) != null, "the jumped mark lost the caster who may jump it again");
		helper.assertTrue(ArcherSkills.MARK_JUMP_RANGE == 16.0,
			"the jump reaches " + ArcherSkills.MARK_JUMP_RANGE + " blocks, not 16");
		marked.discard();
		near.discard();
		far.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void explosiveArrowHitsTheTargetAndItsNeighboursAndBreaksNoBlock(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob target = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X + 1.5, CASTER_Z + 4.0);
		Mob far = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z - 2.0);
		// A wall in the blast's own ring, past both foes: nothing here may break it.
		int wallX = (int) (CASTER_X + 4.0);
		int wallZ = (int) (CASTER_Z + 4.0);
		helper.setBlock(wallX, 1, wallZ, net.minecraft.world.level.block.Blocks.STONE);
		float targetBefore = target.getHealth();
		float nearBefore = near.getHealth();
		float farBefore = far.getHealth();
		SkillSchoolGameTest.cast(caster, "explosive_arrow", target, false);
		boolean blockHeld = helper.getBlockState(new net.minecraft.core.BlockPos(wallX, 1, wallZ))
			.is(net.minecraft.world.level.block.Blocks.STONE);
		List<Mob> splash = ArcherSkills.foesAroundPoint(caster, target.position(), ArcherSkills.EXPLOSIVE_SPLASH_RADIUS);
		String ids = splash.stream().map(mob -> mob.getId() + (mob == target ? "(target)" : mob == near ? "(near)" : "?"))
			.toList().toString();
		LOG.info("[BEHAVIOR] test=explosiveArrowHitsTheTargetAndItsNeighboursAndBreaksNoBlock RESULT targetLost={} nearLost={} farLost={} blockHeld={} splash={} splashIds={} nearPos={} targetPos={} nearAlive={} nearFoe={}",
			targetBefore - target.getHealth(), nearBefore - near.getHealth(), farBefore - far.getHealth(),
			blockHeld, ArcherSkills.EXPLOSIVE_SPLASH_RADIUS, ids, near.position(), target.position(),
			near.isAlive(), Friends.isFoe(near));
		helper.assertTrue(targetBefore - target.getHealth() > 0.0F, "the target took nothing");
		helper.assertTrue(nearBefore - near.getHealth() > 0.0F, "the foe inside the splash took nothing");
		helper.assertTrue(farBefore - far.getHealth() == 0.0F, "a foe well away from the blast was hit");
		helper.assertTrue(blockHeld, "the blast broke a block");
		target.discard();
		near.discard();
		far.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void awakenedExplosiveArrowScattersThreeBomblets(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob target = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 6.0);
		// The bomblets land inside the blast, so the foe beside the target must stand within one's reach.
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X + 1.0, CASTER_Z + 6.0);
		float nearBefore = near.getHealth();
		SkillSchoolGameTest.cast(caster, "explosive_arrow", target, true);
		helper.runAfterDelay((long) ArcherSkills.EXPLOSIVE_BOMBLETS * ArcherSkills.BOMBLET_FLIGHT_TICKS + 20L, () -> {
			LOG.info("[BEHAVIOR] test=awakenedExplosiveArrowScattersThreeBomblets RESULT bomblets={} ran={} nearLost={} radius={}",
				ArcherSkills.EXPLOSIVE_BOMBLETS, ArcherSkills.pulsesRun(caster), nearBefore - near.getHealth(),
				ArcherSkills.BOMBLET_RADIUS);
			helper.assertTrue(ArcherSkills.EXPLOSIVE_BOMBLETS == 3,
				"the awakened arrow scatters " + ArcherSkills.EXPLOSIVE_BOMBLETS + " bomblets, not 3");
			helper.assertTrue(nearBefore - near.getHealth() > 0.0F, "no bomblet reached the foe inside its radius");
			helper.assertTrue(ArcherSkills.pulsesRun(caster) == ArcherSkills.EXPLOSIVE_BOMBLETS,
				"" + ArcherSkills.pulsesRun(caster) + " bomblets landed, not " + ArcherSkills.EXPLOSIVE_BOMBLETS);
			target.discard();
			near.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void disengageSpringsTheCasterBackAndSlowsTheGroundItLeft(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.0);
		// The ground left behind reaches 3 blocks plus the one of slack every area reads.
		Mob far = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 6.0);
		boolean applied = ArcherSkills.applyDisengage(caster, null, null, SkillSchoolGameTest.modifiers(caster, false));
		MobEffectInstance slow = near.getEffect(MobEffects.SLOWNESS);
		LOG.info("[BEHAVIOR] test=disengageSpringsTheCasterBackAndSlowsTheGroundItLeft RESULT applied={} backward={} up={} nearSlow={} farSlow={} radius={} farDistance={}",
			applied, caster.getDeltaMovement().z, caster.getDeltaMovement().y, slow != null, far.hasEffect(MobEffects.SLOWNESS),
			ArcherSkills.DISENGAGE_RADIUS, far.position().distanceTo(caster.position()));
		helper.assertTrue(applied, "disengage never applied");
		helper.assertTrue(caster.getDeltaMovement().z < 0.0, "the caster did not spring backwards: " + caster.getDeltaMovement().z);
		helper.assertTrue(caster.getDeltaMovement().y > 0.0, "the caster did not spring upwards: " + caster.getDeltaMovement().y);
		helper.assertTrue(slow != null, "the foe beside where the caster stood was not slowed");
		helper.assertTrue(slow != null && slow.getDuration() == ArcherSkills.DISENGAGE_SLOW_TICKS,
			"the slow lasts " + (slow == null ? 0 : slow.getDuration()) + " ticks, not " + ArcherSkills.DISENGAGE_SLOW_TICKS);
		helper.assertFalse(far.hasEffect(MobEffects.SLOWNESS), "a foe outside the ground left behind was slowed");
		near.discard();
		far.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedDisengageStaggersTheGroundItLeft(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.0);
		SkillSchoolGameTest.cast(caster, "disengage", null, true);
		LOG.info("[BEHAVIOR] test=awakenedDisengageStaggersTheGroundItLeft RESULT nearStaggered={} nearSlowed={}",
			near.hasEffect(SpellStatus.STAGGERED), near.hasEffect(MobEffects.SLOWNESS));
		helper.assertTrue(near.hasEffect(SpellStatus.STAGGERED), "an awakened disengage left the foe unshaken");
		helper.assertFalse(near.hasEffect(MobEffects.SLOWNESS), "an awakened disengage also slowed, which is the plain behaviour");
		near.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void snareTrapRootsTheFirstFoeThatStepsOnIt(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Vec3 point = helper.absoluteVec(new Vec3(6.5, FLOOR_Y, CASTER_Z + 5.0));
		boolean applied = ArcherSkills.applySnareTrap(caster, null, point, SkillSchoolGameTest.modifiers(caster, false));
		int standing = ArcherSkills.trapsStanding(caster);
		helper.runAfterDelay(6L, () -> {
			Mob caught = FighterBooksGameTest.foe(helper, 6.5, CASTER_Z + 5.0);
			float before = caught.getHealth();
			helper.runAfterDelay(4L, () -> {
				LOG.info("[BEHAVIOR] test=snareTrapRootsTheFirstFoeThatStepsOnIt RESULT applied={} standing={} lost={} slowed={} standingAfter={}",
					applied, standing, before - caught.getHealth(), caught.hasEffect(MobEffects.SLOWNESS), ArcherSkills.trapsStanding(caster));
				helper.assertTrue(applied, "the trap never went down");
				helper.assertTrue(standing == ArcherSkills.TRAPS_PER_CASTER, "the caster set " + standing + " traps");
				helper.assertTrue(before - caught.getHealth() > 0.0F, "the trap took nothing from the foe that stepped on it");
				helper.assertTrue(caught.hasEffect(MobEffects.SLOWNESS), "the trap did not root its victim");
				helper.assertTrue(ArcherSkills.trapsStanding(caster) == 0, "the sprung trap stayed in the ground");
				helper.assertTrue(ArcherSkills.TRAP_TICKS == 600, "a trap waits " + ArcherSkills.TRAP_TICKS + " ticks, not 600");
				caught.discard();
				helper.succeed();
			});
		});
	}

	/**
	 * The squared check on the body's own position is the rule for "inside the area", and the search box is
	 * fitted to it. A body exactly on the reach is inside; one past it is not. This pins that boundary, so
	 * neither check can drift away from the other again.
	 */
	@GameTest(maxTicks = 80)
	public void explosiveArrowHitsTheBodyExactlyOnItsReachAndNotTheOnePastIt(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		double reach = ArcherSkills.EXPLOSIVE_SPLASH_RADIUS;
		Vec3 at = helper.absoluteVec(new Vec3(5.5, FLOOR_Y, CASTER_Z + 5.0));
		Mob inside = helper.spawnMob(EntityTypes.ZOMBIE, 5, 1, (int) CASTER_Z + 5).withNoFreeWill().spawn();
		inside.setPos(at.x, at.y, at.z);
		Mob outside = helper.spawnMob(EntityTypes.ZOMBIE, 5, 1, (int) CASTER_Z + 5).withNoFreeWill().spawn();
		outside.setPos(at.x, at.y, at.z + reach + 0.5);
		for (Mob foe : List.of(inside, outside)) {
			foe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
			foe.setHealth(1000.0F);
		}
		List<Mob> caught = ArcherSkills.foesAroundPoint(caster, at, reach);
		LOG.info("[BEHAVIOR] test=explosiveArrowHitsTheBodyExactlyOnItsReachAndNotTheOnePastIt RESULT reach={} insideDistance={} outsideDistance={} caught={}",
			reach, inside.position().distanceTo(at), outside.position().distanceTo(at), caught.size());
		helper.assertTrue(caught.contains(inside), "a body exactly on the reach was left out");
		helper.assertFalse(caught.contains(outside), "a body past the reach was counted inside");
		helper.assertTrue(caught.size() == 1, "the sweep caught " + caught.size() + " bodies, not one");
		inside.discard();
		outside.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void snareTrapHoldsOnePerCasterAndAwakenedHoldsThree(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Vec3 point = helper.absoluteVec(new Vec3(10.5, FLOOR_Y, CASTER_Z + 10.0));
		for (int trap = 0; trap < 4; trap++) {
			ArcherSkills.applySnareTrap(caster, null, point, SkillSchoolGameTest.modifiers(caster, false));
		}
		int plain = ArcherSkills.trapsStanding(caster);
		ArcherSkills.clearFor(caster);
		for (int trap = 0; trap < 5; trap++) {
			ArcherSkills.applySnareTrap(caster, null, point, SkillSchoolGameTest.modifiers(caster, true));
		}
		int awakened = ArcherSkills.trapsStanding(caster);
		LOG.info("[BEHAVIOR] test=snareTrapHoldsOnePerCasterAndAwakenedHoldsThree RESULT plainCap={} plainStanding={} awakenedCap={} awakenedStanding={}",
			ArcherSkills.TRAPS_PER_CASTER, plain, ArcherSkills.AWAKENED_TRAPS_PER_CASTER, awakened);
		helper.assertTrue(ArcherSkills.TRAPS_PER_CASTER == 1, "the plain cap is " + ArcherSkills.TRAPS_PER_CASTER + ", not 1");
		helper.assertTrue(plain == ArcherSkills.TRAPS_PER_CASTER, "four plain casts left " + plain + " traps standing");
		helper.assertTrue(ArcherSkills.AWAKENED_TRAPS_PER_CASTER == 3,
			"the awakened cap is " + ArcherSkills.AWAKENED_TRAPS_PER_CASTER + ", not 3");
		helper.assertTrue(awakened == ArcherSkills.AWAKENED_TRAPS_PER_CASTER, "five awakened casts left " + awakened + " traps standing");
		ArcherSkills.clearFor(caster);
		helper.assertTrue(ArcherSkills.trapsStanding(caster) == 0, "the server stop left a trap behind");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void eagleEyeLastsHalfAnHourAndRaisesArcherSkillHits(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		SkillSchoolGameTest.cast(caster, "eagle_eye", null, false);
		MobEffectInstance eye = caster.getEffect(SpellStatus.EAGLE_EYE);
		float before = foe.getHealth();
		ArcherSkills.hit(caster, foe, SpellNumbers.MULTISHOT.damage(), SkillSchoolGameTest.modifiers(caster, false));
		double dealt = before - foe.getHealth();
		LOG.info("[BEHAVIOR] test=eagleEyeLastsHalfAnHourAndRaisesArcherSkillHits RESULT eye={} duration={} expected={} amplifier={} flat={} dealt={} factor={}",
			eye != null, eye == null ? 0 : eye.getDuration(), SpellNumbers.EAGLE_EYE.duration().ticks(),
			eye == null ? -1 : eye.getAmplifier(), SpellNumbers.MULTISHOT.damage(), dealt, ArcherSkills.eagleEyeFactor(caster));
		helper.assertTrue(eye != null, "Eagle Eye left no effect");
		helper.assertTrue(eye.getDuration() == SpellNumbers.EAGLE_EYE.duration().ticks(),
			"Eagle Eye lasts " + eye.getDuration() + " ticks, not " + SpellNumbers.EAGLE_EYE.duration().ticks());
		helper.assertTrue(SpellNumbers.EAGLE_EYE.duration().ticks() == ArcherSkills.EAGLE_EYE_TICKS,
			"the effect and the book disagree on the length");
		helper.assertTrue(Math.abs(dealt - SpellNumbers.MULTISHOT.damage() * ArcherSkills.EAGLE_EYE_PLAIN_FACTOR) < 0.05,
			"a plain Eagle Eye hit was worth " + dealt + ", not " + SpellNumbers.MULTISHOT.damage() * ArcherSkills.EAGLE_EYE_PLAIN_FACTOR);
		helper.assertTrue(Math.abs(ArcherSkills.eagleEyeFactor(caster) - ArcherSkills.EAGLE_EYE_PLAIN_FACTOR) < 1.0E-6,
			"a plain Eye reads as " + ArcherSkills.eagleEyeFactor(caster));
		helper.assertTrue(ArcherSkills.EAGLE_EYE_PLAIN_FACTOR == 1.2, "the plain factor is " + ArcherSkills.EAGLE_EYE_PLAIN_FACTOR + ", not 1.2");
		helper.assertTrue(ArcherSkills.EAGLE_EYE_AWAKENED_FACTOR == 1.4,
			"the awakened factor is " + ArcherSkills.EAGLE_EYE_AWAKENED_FACTOR + ", not 1.4");
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedEagleEyeReadsHigherAndRaisesTheHitFurther(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		SkillSchoolGameTest.cast(caster, "eagle_eye", null, true);
		float before = foe.getHealth();
		ArcherSkills.hit(caster, foe, SpellNumbers.MULTISHOT.damage(), SkillSchoolGameTest.modifiers(caster, false));
		double dealt = before - foe.getHealth();
		LOG.info("[BEHAVIOR] test=awakenedEagleEyeReadsHigherAndRaisesTheHitFurther RESULT factor={} dealt={} expected={}",
			ArcherSkills.eagleEyeFactor(caster), dealt, SpellNumbers.MULTISHOT.damage() * ArcherSkills.EAGLE_EYE_AWAKENED_FACTOR);
		helper.assertTrue(Math.abs(ArcherSkills.eagleEyeFactor(caster) - ArcherSkills.EAGLE_EYE_AWAKENED_FACTOR) < 1.0E-6,
			"an awakened Eye reads as " + ArcherSkills.eagleEyeFactor(caster));
		helper.assertTrue(Math.abs(dealt - SpellNumbers.MULTISHOT.damage() * ArcherSkills.EAGLE_EYE_AWAKENED_FACTOR) < 0.05,
			"an awakened Eagle Eye hit was worth " + dealt);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void elementalQuiverTakesTheFirstScrollAndImbuesTenArrows(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		ItemStack scrollStack = new ItemStack(BuiltInRegistries.ITEM.getValue(Spellbooks.scrollId(id("multishot"))), 2);
		caster.getInventory().setItem(0, scrollStack);
		caster.getInventory().setItem(1, new ItemStack(Items.ARROW, 16));
		boolean applied = ArcherSkills.applyElementalQuiver(caster, null, null, SkillSchoolGameTest.modifiers(caster, false));
		int imbued = 0;
		int plain = 0;
		int scrolls = 0;
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (stack.getItem() instanceof SpellScrollItem) {
				scrolls += stack.getCount();
			} else if (stack.is(Items.ARROW) && stack.has(SpellImbue.IMBUED)) {
				imbued += stack.getCount();
			} else if (stack.is(Items.ARROW)) {
				plain += stack.getCount();
			}
		}
		LOG.info("[BEHAVIOR] test=elementalQuiverTakesTheFirstScrollAndImbuesTenArrows RESULT applied={} imbued={} plain={} scrollsLeft={}",
			applied, imbued, plain, scrolls);
		helper.assertTrue(applied, "the quiver never applied");
		helper.assertTrue(imbued == ArcherSkills.QUIVER_ARROWS,
			"the quiver imbued " + imbued + " arrows, not " + ArcherSkills.QUIVER_ARROWS);
		helper.assertTrue(plain == 16 - ArcherSkills.QUIVER_ARROWS, "the plain arrows left number " + plain + ", not " + (16 - ArcherSkills.QUIVER_ARROWS));
		helper.assertTrue(scrolls == 1, "a plain cast left " + scrolls + " scrolls, not 1");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void awakenedElementalQuiverImbuesTwentyAndKeepsTheScroll(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(Spellbooks.scrollId(id("multishot"))), 2));
		caster.getInventory().setItem(1, new ItemStack(Items.ARROW, 32));
		boolean applied = ArcherSkills.applyElementalQuiver(caster, null, null, SkillSchoolGameTest.modifiers(caster, true));
		int imbued = 0;
		int scrolls = 0;
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (stack.getItem() instanceof SpellScrollItem) {
				scrolls += stack.getCount();
			} else if (stack.is(Items.ARROW) && stack.has(SpellImbue.IMBUED)) {
				imbued += stack.getCount();
			}
		}
		LOG.info("[BEHAVIOR] test=awakenedElementalQuiverImbuesTwentyAndKeepsTheScroll RESULT applied={} imbued={} scrollsLeft={} expected={}",
			applied, imbued, scrolls, ArcherSkills.AWAKENED_QUIVER_ARROWS);
		helper.assertTrue(ArcherSkills.AWAKENED_QUIVER_ARROWS == 20,
			"the awakened quiver holds " + ArcherSkills.AWAKENED_QUIVER_ARROWS + " arrows, not 20");
		helper.assertTrue(applied, "the awakened quiver never applied");
		helper.assertTrue(imbued == ArcherSkills.AWAKENED_QUIVER_ARROWS,
			"the awakened quiver imbued " + imbued + " arrows, not " + ArcherSkills.AWAKENED_QUIVER_ARROWS);
		helper.assertTrue(scrolls == 2, "the awakened quiver took the scroll: " + scrolls + " left");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void elementalQuiverWithoutAScrollOrArrowsRefusesAndStartsNoCooldown(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SkillSchoolGameTest.book("elemental_quiver")));
		SpellCasting.CastResult noScroll = SpellCasting.cast(
			caster, SkillSchoolGameTest.book("elemental_quiver"), InteractionHand.MAIN_HAND
		);
		caster.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(Spellbooks.scrollId(id("multishot"))), 1));
		SpellCasting.CastResult noArrows = SpellCasting.cast(
			caster, SkillSchoolGameTest.book("elemental_quiver"), InteractionHand.MAIN_HAND
		);
		int scrollsLeft = caster.getInventory().getItem(0).getCount();
		LOG.info("[BEHAVIOR] test=elementalQuiverWithoutAScrollOrArrowsRefusesAndStartsNoCooldown RESULT noScroll={} noArrows={} scrollsLeft={} hand={}",
			noScroll, noArrows, scrollsLeft, caster.getMainHandItem().getItem());
		helper.assertTrue(noScroll == SpellCasting.CastResult.NO_TARGET, "a quiver with no scroll went " + noScroll);
		helper.assertTrue(noArrows == SpellCasting.CastResult.NO_TARGET, "a quiver with no arrow went " + noArrows);
		helper.assertTrue(scrollsLeft == 1, "a refused cast ate the scroll: " + scrollsLeft + " left");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void everyArcherBookScalesWithDexterityAndNotWithStrength(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		caster.getInventory().setItem(35, new ItemStack(Items.BOW));
		Mob strongFoe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob weakFoe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z - 4.0);
		double strong = dexHit(caster, strongFoe, 1.0);
		double weak = dexHit(caster, weakFoe, 0.5);
		double withIntelligence = dexHit(caster, strongFoe, 0.0, 1.0);
		LOG.info("[BEHAVIOR] test=everyArcherBookScalesWithDexterityAndNotWithStrength RESULT strong={} weak={} withIntelligenceOnly={} dexKey={} strKey={}",
			strong, weak, withIntelligence, School.ARCHER.statKey(), School.FIGHTER.statKey());
		helper.assertTrue(strong > weak, "Dexterity raised the archer hit by nothing: " + strong + " against " + weak);
		helper.assertTrue(withIntelligence == 0.0,
			"an archer book read a stat other than Dexterity: " + withIntelligence);
		strongFoe.discard();
		weakFoe.discard();
		helper.succeed();
	}

	/** The hit a shot deals under a stub whose archer coefficient is given, and whose other stats are fixed. */
	private static double dexHit(final ServerPlayer caster, final Mob foe, final double dexCoefficient) {
		return dexHit(caster, foe, dexCoefficient, 0.0);
	}

	private static double dexHit(
		final ServerPlayer caster, final Mob foe, final double dexCoefficient, final double otherCoefficient
	) {
		RpgBridge.setStats(new RpgBridge.Stats() {
			@Override
			public double power(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double cooldown(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double skillDamage(
				final net.minecraft.world.entity.LivingEntity subject, final String statKey, final double skillPercent
			) {
				return statKey.equals(School.ARCHER.statKey()) ? skillPercent * dexCoefficient
					: skillPercent * otherCoefficient;
			}
		});
		try {
			float before = foe.getHealth();
			ArcherSkills.hit(caster, foe, SpellNumbers.MULTISHOT.damage(), SkillSchoolGameTest.modifiers(caster, false));
			return before - foe.getHealth();
		} finally {
			RpgBridge.setStats(null);
		}
	}

	@GameTest
	public void twentyThousandContainerRollsProduceEveryArcherBookAndNoAwakenedOne(final GameTestHelper helper) {
		Set<Identifier> archer = SkillSchoolGameTest.ARCHER_IDS.stream()
			.map(SkillSchoolGameTest::id)
			.collect(Collectors.toUnmodifiableSet());
		Map<Identifier, Integer> counts = new LinkedHashMap<>();
		archer.forEach(book -> counts.put(book, 0));
		Identifier table = Identifier.parse("minecraft:chests/simple_dungeon");
		RandomSource random = RandomSource.create(90210L);
		int awakened = 0;
		int total = 0;
		for (int roll = 0; roll < 20_000; roll++) {
			ItemStack stack = SpellbookTestAccess.rollChest(table, true, random);
			if (stack.isEmpty()) {
				continue;
			}
			total++;
			counts.computeIfPresent(BuiltInRegistries.ITEM.getKey(stack.getItem()), (ignored, count) -> count + 1);
			if (Awakening.isAwakened(stack)) {
				awakened++;
			}
		}
		LOG.info("[BEHAVIOR] test=twentyThousandContainerRollsProduceEveryArcherBookAndNoAwakenedOne RESULT rolls=20000 pool={} total={} awakened={} counts={}",
			SpellbookCatalog.DEFINITIONS.size(), total, awakened, counts);
		helper.assertTrue(counts.values().stream().allMatch(count -> count > 0), "an archer book never dropped: " + counts);
		helper.assertTrue(awakened == 0, "a container roll produced " + awakened + " awakened books");
		helper.succeed();
	}

	@GameTest
	public void noMobCastsAnArcherBook(final GameTestHelper helper) {
		List<String> mobSpells = new ArrayList<>(MobSpells.OFFENSIVE);
		mobSpells.addAll(MobSpells.SUPPORT);
		mobSpells.addAll(MobSpells.PACK_SUPPORT);
		mobSpells.addAll(MobSpells.PACK_AREA_OFFENSIVE);
		mobSpells.addAll(MobSpells.VILLAGE_WITCH_OFFENSIVE);
		mobSpells.addAll(MobSpells.VILLAGE_WITCH_SUPPORT);
		List<String> mob = mobSpells.stream().filter(SkillSchoolGameTest.ARCHER_IDS::contains).toList();
		List<Identifier> imbued = SpellImbue.MOB_SPELLS.stream()
			.filter(identifier -> SkillSchoolGameTest.ARCHER_IDS.contains(identifier.getPath()))
			.toList();
		LOG.info("[BEHAVIOR] test=noMobCastsAnArcherBook RESULT mobSpellLists={} archerInMobLists={} mobImbueSpells={} archerImbued={}",
			mobSpells.size(), mob, SpellImbue.MOB_SPELLS.size(), imbued);
		helper.assertTrue(mob.isEmpty(), "a mob spell list holds an archer book: " + mob);
		helper.assertTrue(imbued.isEmpty(), "a mob's imbued weapon can cast an archer book: " + imbued);
		helper.succeed();
	}

	@GameTest
	public void everyArcherBookCraftsFromItsOwnRecipes(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String path : SkillSchoolGameTest.ARCHER_IDS) {
			if (Spellbooks.BOOK_ONLY.contains(SkillSchoolGameTest.id(path))) {
				continue;
			}
			ItemStack material = new ItemStack(BuiltInRegistries.ITEM.getValue(SkillSchoolGameTest.materialOf(path)));
			ItemStack crafted = craft(helper, shaped("M M", " B ", "M M", 'M', material, 'B', new ItemStack(Items.BOOK)));
			ItemStack scroll = craft(helper, shapeless(new ItemStack(Items.PAPER), material));
			ItemStack fromScrolls = craft(helper, shaped("SSS", "SBS", "SSS", 'S',
				new ItemStack(BuiltInRegistries.ITEM.getValue(SkillSchoolGameTest.id(path + "_scroll"))), 'B', new ItemStack(Items.BOOK)));
			if (!crafted.is(SkillSchoolGameTest.book(path))) {
				wrong.add(path + " book -> " + BuiltInRegistries.ITEM.getKey(crafted.getItem()));
			}
			if (scroll.getCount() != 2 || !scroll.is(BuiltInRegistries.ITEM.getValue(SkillSchoolGameTest.id(path + "_scroll")))) {
				wrong.add(path + " scroll -> " + BuiltInRegistries.ITEM.getKey(scroll.getItem()) + " x" + scroll.getCount());
			}
			if (!fromScrolls.is(SkillSchoolGameTest.book(path))) {
				wrong.add(path + " from scrolls -> " + BuiltInRegistries.ITEM.getKey(fromScrolls.getItem()));
			}
		}
		LOG.info("[BEHAVIOR] test=everyArcherBookCraftsFromItsOwnRecipes RESULT books={} wrong={}",
			SkillSchoolGameTest.ARCHER_IDS.size(), wrong);
		helper.assertTrue(wrong.isEmpty(), "these recipes craft the wrong item: " + wrong);
		helper.succeed();
	}

	private static CraftingInput shaped(
		final String rowA, final String rowB, final String rowC,
		final char first, final ItemStack firstStack, final char second, final ItemStack secondStack
	) {
		List<ItemStack> items = new ArrayList<>();
		for (String row : List.of(rowA, rowB, rowC)) {
			for (char symbol : row.toCharArray()) {
				items.add(symbol == first ? firstStack : symbol == second ? secondStack : ItemStack.EMPTY);
			}
		}
		return CraftingInput.of(3, 3, items);
	}

	private static CraftingInput shapeless(final ItemStack... stacks) {
		return CraftingInput.of(1, stacks.length, List.of(stacks));
	}

	private static ItemStack craft(final GameTestHelper helper, final CraftingInput input) {
		CraftingRecipe recipe = helper.getLevel().getServer().getRecipeManager()
			.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(holder -> holder.value())
			.orElse(null);
		return recipe == null ? ItemStack.EMPTY : recipe.assemble(input);
	}

	/** How many distinct values a run of health samples holds: one per pulse that landed. */
	private static int distinct(final List<Float> samples) {
		Set<Float> seen = new java.util.LinkedHashSet<>(samples);
		return seen.size();
	}

	private static Identifier id(final String path) {
		return SkillSchoolGameTest.id(path);
	}
}
