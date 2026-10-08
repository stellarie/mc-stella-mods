package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The archer books the rework left alone, plain and awakened: what each shot hits, how many times, and what
 * it leaves on the ground. Every shot is a hit-scan ray, so a case reads health rather than an arrow entity.
 * The reworked books have their own cases in {@link ArcherReworkGameTest}.
 */
public class ArcherBooksGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.archerbooks");
	/** The caster stands here, facing +Z, so every foe ahead sits on one line. */
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	/** The block the caster's feet stand on, and the one a point-targeting cast aims at. */
	private static final double FLOOR_Y = 0.0;
	/** The weapon slot the mod reads a bow from, and the pack slot a case puts its arrows in. */
	private static final int WEAPON_SLOT = 35;
	private static final int QUIVER_SLOT = 20;

	/** The arena's caster with the school's own kit: a bow in the weapon slot and arrows in the pack. */
	private static ServerPlayer archerCaster(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		caster.getInventory().setItem(QUIVER_SLOT, new ItemStack(Items.ARROW, 64));
		return caster;
	}

	@GameTest(maxTicks = 80)
	public void piercingShotHitsEveryFoeOnTheLine(final GameTestHelper helper) {
		ServerPlayer caster = archerCaster(helper);
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
		ServerPlayer caster = archerCaster(helper);
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
		ServerPlayer caster = archerCaster(helper);
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
		ServerPlayer caster = archerCaster(helper);
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
		ServerPlayer caster = archerCaster(helper);
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
		ServerPlayer caster = archerCaster(helper);
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
		ServerPlayer caster = archerCaster(helper);
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
		ServerPlayer caster = archerCaster(helper);
		Mob marked = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 7.0);
		Mob far = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 30.0);
		SkillSchoolGameTest.cast(caster, "hunters_mark", marked, true);
		String tagBefore = String.valueOf(ArcherSkills.markOwner(marked));
		MobEffectInstance onMarked = marked.getEffect(SpellStatus.MARKED);
		boolean jumped = ArcherSkills.onMarkedDeath(marked);
		MobEffectInstance onNear = near.getEffect(SpellStatus.MARKED);
		LOG.info("[BEHAVIOR] test=awakenedHuntersMarkJumpsToTheNearestFoeWhenItsHolderFalls RESULT jumped={} nearMarked={} farMarked={} range={} nearTag={} tagBefore={} markedEffectOnHolder={} nearEffect={}",
			jumped, near.hasEffect(SpellStatus.MARKED), far.hasEffect(SpellStatus.MARKED), ArcherSkills.MARK_JUMP_RANGE,
			ArcherSkills.markOwner(near), tagBefore, onMarked != null, onNear != null);
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
	public void eagleEyeLastsHalfAnHourAndRaisesArcherSkillHits(final GameTestHelper helper) {
		ServerPlayer caster = archerCaster(helper);
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
		ServerPlayer caster = archerCaster(helper);
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
	public void explosiveArrowHitsTheBodyExactlyOnItsReachAndNotTheOnePastIt(final GameTestHelper helper) {
		ServerPlayer caster = archerCaster(helper);
		double reach = ArcherSkills.EXPLOSIVE_SPLASH_RADIUS;
		Vec3 at = helper.absoluteVec(new Vec3(5.5, FLOOR_Y, CASTER_Z + 5.0));
		Mob inside = helper.spawnMob(net.minecraft.world.entity.EntityTypes.ZOMBIE, 5, 1, (int) CASTER_Z + 5).withNoFreeWill().spawn();
		inside.setPos(at.x, at.y, at.z);
		Mob outside = helper.spawnMob(net.minecraft.world.entity.EntityTypes.ZOMBIE, 5, 1, (int) CASTER_Z + 5).withNoFreeWill().spawn();
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

	private static Identifier id(final String path) {
		return SkillSchoolGameTest.id(path);
	}
}
