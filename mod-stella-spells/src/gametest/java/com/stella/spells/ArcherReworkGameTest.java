package com.stella.spells;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The reworked archer school: the grappling hook, the three half-hour buffs, the two next-shot books, the
 * ammo-free quiver, and the school's own bow-and-arrow gate. Each case reads the constant it measures.
 */
public class ArcherReworkGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.archerrework");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;

	@GameTest(maxTicks = 80)
	public void grapplingHookMovesTheCasterToWhereTheArrowLands(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		ItemStack arrows = caster.getInventory().getItem(ArcherReworkGameTest.HELD_QUIVER_SLOT);
		int arrowsBefore = arrows.getCount();
		Vec3 landing = ArcherSkills.arrowLanding(caster);
		boolean applied = SkillSchoolGameTest.cast(caster, "multishot", null, false);
		Vec3 stopped = caster.position();
		int arrowsAfter = caster.getInventory().getItem(ArcherReworkGameTest.HELD_QUIVER_SLOT).getCount();
		double travelled = stopped.distanceTo(new Vec3(CASTER_X, stopped.y, CASTER_Z));
		LOG.info("[BEHAVIOR] test=grapplingHookMovesTheCasterToWhereTheArrowLands RESULT applied={} landing={} stopped={} travelled={} arrowsBefore={} arrowsAfter={} armed={} bow={} ammo={}",
			applied, landing, stopped, travelled, arrowsBefore, arrowsAfter,
			ArcherSkills.armed(caster),
			ArcherSkills.hasBowOrCrossbow(caster),
			ArcherSkills.hasAmmo(caster));
		helper.assertTrue(applied, "the grappling hook never fired");
		helper.assertTrue(landing != null, "no arrow landed on the clear floor ahead");
		helper.assertTrue(landing.subtract(stopped).length() < ArcherSkills.GRAPPLE_ARRIVAL_SLACK,
			"the caster stopped at " + stopped + ", not on the arrow at " + landing);
		helper.assertTrue(travelled > ArcherSkills.GRAPPLE_MIN_DISTANCE,
			"the arrow landed on the caster's own feet: " + travelled + " blocks");
		helper.assertTrue(arrowsAfter == arrowsBefore - 1,
			"the cast spent " + (arrowsBefore - arrowsAfter) + " arrows, not one");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void grapplingHookRefusesWithNoArrowAndLeavesTheCasterWhereItStands(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		SkillSchoolGameTest.setAutoKit(false);
		caster.getInventory().clearContent();
		caster.getInventory().setItem(ArcherReworkGameTest.WEAPON_SLOT, new ItemStack(Items.BOW));
		Vec3 before = caster.position();
		boolean applied = SkillSchoolGameTest.cast(caster, "multishot", null, false);
		LOG.info("[BEHAVIOR] test=grapplingHookRefusesWithNoArrowAndLeavesTheCasterWhereItStands RESULT applied={} before={} after={} arrows={}",
			applied, before, caster.position(), caster.getInventory().countItem(Items.ARROW));
		helper.assertFalse(applied, "the grappling hook fired with no arrow");
		helper.assertTrue(caster.position().distanceTo(before) == 0.0,
			"a refused grapple moved the caster to " + caster.position());
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void concentrateGrantsItsThreeBonusesForHalfAnHourWhileABowIsHeld(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		boolean applied = SkillSchoolGameTest.cast(caster, "snare_trap", null, false);
		MobEffectInstance concentrate = caster.getEffect(SpellStatus.CONCENTRATE);
		LOG.info("[BEHAVIOR] test=concentrateGrantsItsThreeBonusesForHalfAnHourWhileABowIsHeld RESULT applied={} held={} duration={} expected={} dexBonus={} critFactor={}",
			applied, concentrate != null, concentrate == null ? 0 : concentrate.getDuration(),
			SpellNumbers.SNARE_TRAP.duration().ticks(), ArcherSkills.CONCENTRATE_DEX_BONUS, ArcherSkills.CONCENTRATE_CRIT_FACTOR);
		helper.assertTrue(applied, "concentrate never applied");
		helper.assertTrue(concentrate != null, "concentrate left no effect on the caster");
		helper.assertTrue(concentrate.getDuration() == SpellNumbers.SNARE_TRAP.duration().ticks(),
			"concentrate lasts " + concentrate.getDuration() + " ticks, not " + SpellNumbers.SNARE_TRAP.duration().ticks());
		helper.assertTrue(SpellNumbers.SNARE_TRAP.duration().ticks() == ArcherSkills.CONCENTRATE_TICKS,
			"the effect and the book disagree on the length");
		helper.assertTrue(ArcherSkills.concentrateFactor(caster) > 1.0,
			"concentrate raised an archer hit by nothing: " + ArcherSkills.concentrateFactor(caster));
		helper.assertTrue(ArcherSkills.CONCENTRATE_DEX_BONUS > 0.0 && ArcherSkills.CONCENTRATE_CRIT_FACTOR >= 1.0,
			"a concentrate bonus is not a bonus at all");
		helper.succeed();
	}

	/** A bow grants the Concentrate bonus; a bare hand is refused the cast outright, so it grants nothing. */
	@GameTest(maxTicks = 80)
	public void concentrateReadsAsNoBonusWithoutABowInHand(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		SkillSchoolGameTest.setAutoKit(false);
		caster.getInventory().setItem(ArcherReworkGameTest.WEAPON_SLOT, ItemStack.EMPTY);
		caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		boolean applied = SkillSchoolGameTest.cast(caster, "snare_trap", null, false);
		double factor = ArcherSkills.concentrateFactor(caster);
		LOG.info("[BEHAVIOR] test=concentrateReadsAsNoBonusWithoutABowInHand RESULT applied={} factor={} bow={} held={}",
			applied, factor, ArcherSkills.hasBowOrCrossbow(caster), caster.hasEffect(SpellStatus.CONCENTRATE));
		helper.assertFalse(applied, "concentrate cast on a bare hand");
		helper.assertTrue(caster.getEffect(SpellStatus.CONCENTRATE) == null,
			"a refused concentrate still left its effect behind");
		helper.assertFalse(ArcherSkills.hasBowOrCrossbow(caster), "an empty hand read as a bow");
		helper.assertTrue(factor == 1.0, "concentrate paid its bonus with no bow held: " + factor);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void spectralQuiverLetsAFullQuiverFireWithNoArrowForHalfAnHour(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		boolean applied = SkillSchoolGameTest.cast(caster, "elemental_quiver", null, false);
		MobEffectInstance quiver = caster.getEffect(SpellStatus.SPECTRAL_QUIVER);
		caster.getInventory().clearContent();
		ItemStack bow = new ItemStack(Items.BOW);
		caster.getInventory().setItem(ArcherReworkGameTest.WEAPON_SLOT, bow);
		boolean firesFree = ArcherSkills.ammoFree(caster, bow);
		double cost = ArcherSkills.quiverDamageFactor(caster);
		LOG.info("[BEHAVIOR] test=spectralQuiverLetsAFullQuiverFireWithNoArrowForHalfAnHour RESULT applied={} held={} duration={} expected={} arrows={} firesFree={} cost={}",
			applied, quiver != null, quiver == null ? 0 : quiver.getDuration(),
			SpellNumbers.ELEMENTAL_QUIVER.duration().ticks(), caster.getInventory().countItem(Items.ARROW), firesFree, cost);
		helper.assertTrue(applied, "the spectral quiver never applied");
		helper.assertTrue(quiver != null, "the spectral quiver left no effect on the caster");
		helper.assertTrue(quiver.getDuration() == SpellNumbers.ELEMENTAL_QUIVER.duration().ticks(),
			"the quiver lasts " + quiver.getDuration() + " ticks, not " + SpellNumbers.ELEMENTAL_QUIVER.duration().ticks());
		helper.assertTrue(SpellNumbers.ELEMENTAL_QUIVER.duration().ticks() == ArcherSkills.SPECTRAL_QUIVER_TICKS,
			"the effect and the book disagree on the length");
		helper.assertTrue(firesFree, "a bow with an empty quiver still needed an arrow");
		helper.assertTrue(cost == ArcherSkills.QUIVER_DAMAGE_FACTOR,
			"the quiver cuts outgoing damage to " + cost + ", not " + ArcherSkills.QUIVER_DAMAGE_FACTOR);
		helper.assertTrue(ArcherSkills.QUIVER_DAMAGE_FACTOR == 0.75,
			"the quiver factor is " + ArcherSkills.QUIVER_DAMAGE_FACTOR + ", not a quarter cut");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void windWalkRaisesMovementSpeedByAQuarterForHalfAnHour(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
		double before = speed.getValue();
		boolean applied = SkillSchoolGameTest.cast(caster, "disengage", null, false);
		MobEffectInstance walk = caster.getEffect(SpellStatus.WIND_WALK);
		double after = speed.getValue();
		LOG.info("[BEHAVIOR] test=windWalkRaisesMovementSpeedByAQuarterForHalfAnHour RESULT applied={} held={} duration={} expected={} before={} after={} multiplier={} modifier={}",
			applied, walk != null, walk == null ? 0 : walk.getDuration(),
			SpellNumbers.DISENGAGE.duration().ticks(), before, after, ArcherSkills.WIND_WALK_SPEED,
			speed.getModifier(SpellStatus.WIND_WALK_ID));
		helper.assertTrue(applied, "wind walk never applied");
		helper.assertTrue(walk != null, "wind walk left no effect on the caster");
		helper.assertTrue(walk.getDuration() == SpellNumbers.DISENGAGE.duration().ticks(),
			"wind walk lasts " + walk.getDuration() + " ticks, not " + SpellNumbers.DISENGAGE.duration().ticks());
		helper.assertTrue(speed.getModifier(SpellStatus.WIND_WALK_ID) != null,
			"wind walk left no movement speed modifier behind");
		helper.assertTrue(Math.abs(after / before - ArcherSkills.WIND_WALK_SPEED) < 1.0E-6,
			"movement speed went from " + before + " to " + after + ", not by " + ArcherSkills.WIND_WALK_SPEED);
		helper.assertTrue(ArcherSkills.WIND_WALK_SPEED == 1.25,
			"the wind walk speed is " + ArcherSkills.WIND_WALK_SPEED + ", not a quarter more");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aimedBoltTurnsTheNextArrowIntoAFourfoldSlowingHit(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		Mob plainVictim = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 20.0);
		Mob aimedVictim = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 20.0);
		float plainBefore = plainVictim.getHealth();
		float aimedBefore = aimedVictim.getHealth();
		double plain = plainBefore - arrowHit(helper, caster, plainVictim).getHealth();
		boolean applied = SkillSchoolGameTest.cast(caster, "aimed_bolt", null, false);
		double aimed = aimedBefore - arrowHit(helper, caster, aimedVictim).getHealth();
		MobEffectInstance slow = aimedVictim.getEffect(MobEffects.SLOWNESS);
		LOG.info("[BEHAVIOR] test=aimedBoltTurnsTheNextArrowIntoAFourfoldSlowingHit RESULT applied={} plain={} aimed={} ratio={} slow={} slowTicks={} expectedTicks={} owed={}",
			applied, plain, aimed, aimed / plain, slow != null, slow == null ? 0 : slow.getDuration(),
			SpellNumbers.AIMED_BOLT.extraTicks(SkillSchoolGameTest.modifiers(caster, false)),
			ArcherSkills.shotOwed(caster));
		helper.assertTrue(applied, "the aimed bolt never applied");
		helper.assertTrue(plain > 0.0 && aimed > 0.0, "an arrow dealt nothing, so the ratio proves nothing");
		helper.assertTrue(Math.abs(aimed / plain - ArcherSkills.AIMED_BOLT_DAMAGE) < ArcherSkills.SHOT_RATIO_TOLERANCE,
			"the aimed bolt hit for " + (aimed / plain) + " arrows, not " + ArcherSkills.AIMED_BOLT_DAMAGE);
		helper.assertTrue(ArcherSkills.AIMED_BOLT_DAMAGE == 4.0F,
			"the aimed bolt multiplier is " + ArcherSkills.AIMED_BOLT_DAMAGE + ", not four");
		helper.assertTrue(slow != null, "the aimed bolt never slowed its target");
		helper.assertTrue(slow != null && slow.getDuration() == SpellNumbers.AIMED_BOLT.extraTicks(SkillSchoolGameTest.modifiers(caster, false)),
			"the slow lasts " + (slow == null ? 0 : slow.getDuration()) + " ticks, not the two seconds the book declares");
		helper.assertFalse(ArcherSkills.shotOwed(caster), "the aimed bolt outlived the shot it was meant for");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void explosiveArrowQueuesOneExplosiveShotAndUsesItUp(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		// The blast victim stands alone on the firing line, and the plain arrow's victim stands well off it,
		// so the plain shot can never land on the blast victim or on the neighbour the blast must catch.
		Mob plainVictim = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 24.0);
		Mob boomVictim = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 20.0);
		// The neighbour sits inside the shipped splash radius, sideways off the line, so the ray never reaches it.
		Mob beside = FighterBooksGameTest.foe(helper, CASTER_X + ArcherSkills.EXPLOSIVE_SPLASH_RADIUS / 2.0, CASTER_Z + 20.0);
		helper.assertTrue(beside.position().distanceTo(boomVictim.position()) <= ArcherSkills.EXPLOSIVE_SPLASH_RADIUS,
			"the neighbour stands outside the blast it is meant to prove");
		float besideBefore = beside.getHealth();
		float plainBefore = plainVictim.getHealth();
		float boomBefore = boomVictim.getHealth();
		double plain = plainBefore - arrowHit(helper, caster, plainVictim).getHealth();
		helper.assertTrue(plainVictim.getHealth() == plainBefore - plain && beside.getHealth() == besideBefore,
			"the plain shot reached the neighbour, so the blast is not what this case measures");
		boolean applied = SkillSchoolGameTest.cast(caster, "explosive_arrow", null, false);
		double boom = boomBefore - arrowHit(helper, caster, boomVictim).getHealth();
		double splash = besideBefore - beside.getHealth();
		LOG.info("[BEHAVIOR] test=explosiveArrowQueuesOneExplosiveShotAndUsesItUp RESULT applied={} plain={} boom={} ratio={} splash={} expectedRatio={} owed={}",
			applied, plain, boom, boom / plain, splash, ArcherSkills.EXPLOSIVE_ARROW_DAMAGE, ArcherSkills.shotOwed(caster));
		helper.assertTrue(applied, "the explosive arrow never queued a shot");
		helper.assertTrue(plain > 0.0 && boom > 0.0, "an arrow dealt nothing, so the ratio proves nothing");
		helper.assertTrue(Math.abs(boom / plain - ArcherSkills.EXPLOSIVE_ARROW_DAMAGE) < ArcherSkills.SHOT_RATIO_TOLERANCE,
			"the explosive arrow hit for " + (boom / plain) + " arrows, not " + ArcherSkills.EXPLOSIVE_ARROW_DAMAGE);
		helper.assertTrue(splash > 0.0, "the explosive arrow left the foe beside its target untouched");
		helper.assertFalse(ArcherSkills.shotOwed(caster), "the explosive arrow outlived the shot it was meant for");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void triangleShotRollsItsOwnBarrageOfArrows(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		boolean applied = SkillSchoolGameTest.cast(caster, "triangle_shot", null, false);
		MobEffectInstance shot = caster.getEffect(SpellStatus.TRIANGLE_SHOT);
		List<Integer> seen = new ArrayList<>();
		RandomSource random = RandomSource.create(4242L);
		boolean exact = true;
		for (int roll = 0; roll < ArcherSkills.TRIANGLE_SHOT_SAMPLES; roll++) {
			int arrows = ArcherSkills.barrage(caster, random);
			exact &= arrows == 1 || arrows == ArcherSkills.TRIANGLE_SHOT_ARROWS;
			seen.add(arrows);
		}
		long bursts = seen.stream().filter(arrows -> arrows == ArcherSkills.TRIANGLE_SHOT_ARROWS).count();
		double share = (double) bursts / seen.size();
		LOG.info("[BEHAVIOR] test=triangleShotRollsItsOwnBarrageOfArrows RESULT applied={} held={} duration={} expected={} barrage={} bursts={} share={} chance={} allOrNothing={}",
			applied, shot != null, shot == null ? 0 : shot.getDuration(),
			SpellNumbers.TRIANGLE_SHOT.duration().ticks(), ArcherSkills.TRIANGLE_SHOT_ARROWS, bursts, share,
			ArcherSkills.TRIANGLE_SHOT_CHANCE, exact);
		helper.assertTrue(applied, "the triangle shot never applied");
		helper.assertTrue(shot != null, "the triangle shot left no effect on the caster");
		helper.assertTrue(shot.getDuration() == SpellNumbers.TRIANGLE_SHOT.duration().ticks(),
			"the triangle shot lasts " + shot.getDuration() + " ticks, not " + SpellNumbers.TRIANGLE_SHOT.duration().ticks());
		helper.assertTrue(SpellNumbers.TRIANGLE_SHOT.duration().ticks() == ArcherSkills.TRIANGLE_SHOT_OFFER_TICKS,
			"the effect and the book disagree on the length");
		helper.assertTrue(exact, "the roll loosed a number of arrows that is neither one nor a barrage: " + seen);
		helper.assertTrue(bursts > 0, "no sample loosed a barrage over " + seen.size() + " rolls");
		helper.assertTrue(bursts < seen.size(), "every sample loosed a barrage, so the chance is not a chance");
		helper.assertTrue(Math.abs(share - ArcherSkills.TRIANGLE_SHOT_CHANCE) < ArcherSkills.TRIANGLE_SHOT_TOLERANCE,
			"the barrage landed on " + share + " of the rolls, not near " + ArcherSkills.TRIANGLE_SHOT_CHANCE);
		helper.assertTrue(ArcherSkills.TRIANGLE_SHOT_CHANCE == 0.30 && ArcherSkills.TRIANGLE_SHOT_ARROWS == 3,
			"the book is declared as " + ArcherSkills.TRIANGLE_SHOT_CHANCE + " for " + ArcherSkills.TRIANGLE_SHOT_ARROWS + " arrows");
		caster.removeEffect(SpellStatus.TRIANGLE_SHOT);
		helper.assertTrue(ArcherSkills.barrage(caster, RandomSource.create(4242L)) == 1,
			"a shot without the book still loosed a barrage");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void everyArcherBookRefusesWithoutABowAndWithoutAnArrow(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		SkillSchoolGameTest.setAutoKit(false);
		caster.getInventory().clearContent();
		caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		// The three buffs of the sweep before this one leave a quiver behind, which needs no arrow at all.
		caster.removeEffect(SpellStatus.SPECTRAL_QUIVER);
		// With no bow, no crossbow and no arrow, every one of the twelve is refused: the school's gate has no
		// exception, so the three half-hour buffs refuse a bare hand exactly as the nine shots do.
		List<String> unarmed = new ArrayList<>();
		for (String path : SkillSchoolGameTest.ARCHER_IDS) {
			if (!SkillSchoolGameTest.cast(caster, path, null, false)) {
				unarmed.add(path);
			}
		}
		// A bow with an empty quiver still refuses every book, and the gate stays shut for all twelve.
		List<String> unloaded = new ArrayList<>();
		List<String> gateOpen = new ArrayList<>();
		caster.getInventory().clearContent();
		caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		caster.removeAllEffects();
		caster.getInventory().setItem(ArcherReworkGameTest.WEAPON_SLOT, new ItemStack(Items.BOW));
		for (String path : SkillSchoolGameTest.ARCHER_IDS) {
			boolean applied = SkillSchoolGameTest.cast(caster, path, null, false);
			if (ArcherSkills.armed(caster)) {
				gateOpen.add(path);
			}
			if (applied) {
				unloaded.add(path);
			}
		}
		// With the bow and a full quiver the gate opens for every one of them, and each book then casts: the
		// three that also want a foe in front are read on the gate here, because this arena holds none.
		List<String> refused = new ArrayList<>();
		List<String> unarmedStill = new ArrayList<>();
		caster.getInventory().setItem(ArcherReworkGameTest.HELD_QUIVER_SLOT, new ItemStack(Items.ARROW, 64));
		for (String path : SkillSchoolGameTest.ARCHER_IDS) {
			if (!ArcherSkills.armed(caster)) {
				unarmedStill.add(path);
			}
			if (!TARGET_BOOKS.contains(path) && !SkillSchoolGameTest.cast(caster, path, null, false)) {
				refused.add(path);
			}
		}
		LOG.info("[BEHAVIOR] test=everyArcherBookRefusesWithoutABowAndWithoutAnArrow RESULT books={} refusedWithNothing={} refusedWithBowAndNoArrow={} refusedWithBowAndArrow={} gateOpenWithBowAndNoArrow={} gateShutWithBowAndArrow={}",
			SkillSchoolGameTest.ARCHER_IDS.size(), unarmed, unloaded, refused, gateOpen, unarmedStill);
		helper.assertTrue(unarmed.size() == SkillSchoolGameTest.ARCHER_IDS.size(),
			"these books cast with no bow, no crossbow and no arrow: " + unarmed);
		helper.assertTrue(unloaded.isEmpty(), "these books cast with a bow and no arrow: " + unloaded);
		helper.assertTrue(gateOpen.isEmpty(), "the school's gate was open with no arrow: " + gateOpen);
		helper.assertTrue(refused.isEmpty(), "these books refused a bow with a full quiver: " + refused);
		helper.assertTrue(unarmedStill.isEmpty(), "the gate stayed shut for: " + unarmedStill);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void archerStarterGrantGivesArrowRainAndOneOtherArcherBook(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		caster.getInventory().clearContent();
		List<Identifier> granted = new ArrayList<>();
		int count = ArcherStarter.grant(caster, RandomSource.create(7L), granted::add);
		Set<Identifier> archerTag = new LinkedHashSet<>(SkillSchoolGameTest.ARCHER_IDS.stream()
			.map(SkillSchoolGameTest::id).toList());
		Set<Identifier> given = new LinkedHashSet<>(granted);
		int carried = 0;
		for (Identifier id : given) {
			carried += caster.getInventory().countItem(BuiltInRegistries.ITEM.get(id)
				.map(net.minecraft.core.Holder.Reference::value).orElse(Items.AIR));
		}
		LOG.info("[BEHAVIOR] test=archerStarterGrantGivesArrowRainAndOneOtherArcherBook RESULT count={} granted={} carried={} arrowRain={} archBooks={}",
			count, granted, carried, ArcherStarter.STARTER_ID, archerTag.size());
		helper.assertTrue(count == ArcherStarter.STARTER_BOOKS, "the starter grant gave " + count + " books, not " + ArcherStarter.STARTER_BOOKS);
		helper.assertTrue(given.contains(ArcherStarter.STARTER_ID), "the starter grant skipped arrow rain");
		helper.assertTrue(archerTag.containsAll(given), "the starter grant gave a book outside the archer school: " + granted);
		helper.assertTrue(given.size() == ArcherStarter.STARTER_BOOKS,
			"the starter grant drew " + granted + " with a repeat");
		helper.assertTrue(given.stream().filter(id -> !id.equals(ArcherStarter.STARTER_ID)).count() == 1,
			"the starter grant gave " + given + ", not arrow rain plus one other book");
		helper.assertTrue(carried == count, "the grant returned " + count + " but the pack holds " + carried);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void archerStarterGrantRefusesAFullPackRatherThanDroppingBooks(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		caster.getInventory().clearContent();
		// Room for exactly one of the two books: the grant is all or nothing, so it hands out none.
		for (int slot = 0; slot < caster.getInventory().getContainerSize() - 1; slot++) {
			caster.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
		}
		List<Identifier> granted = new ArrayList<>();
		int count = ArcherStarter.grant(caster, RandomSource.create(7L), granted::add);
		LOG.info("[BEHAVIOR] test=archerStarterGrantRefusesAFullPackRatherThanDroppingBooks RESULT count={} granted={} books={} freeSlots={}",
			count, granted, ArcherStarter.STARTER_BOOKS, freeSlots(caster));
		helper.assertTrue(freeSlots(caster) < ArcherStarter.STARTER_BOOKS,
			"the case left " + freeSlots(caster) + " free slots, so it does not test a full pack");
		helper.assertTrue(count == 0, "a pack with room for one book took " + count + " of them");
		helper.assertTrue(granted.isEmpty(), "a refused grant still reported " + granted);
		helper.assertTrue(caster.getInventory().hasAnyMatching(stack -> stack.is(Items.STONE)),
			"a refused grant emptied the pack it could not fill");
		helper.succeed();
	}

	/** A second call hands out a second set, unless the pack cannot hold it: this is the recorded answer. */
	@GameTest(maxTicks = 80)
	public void aSecondStarterGrantHandsOutASecondSetIntoARoomyPack(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		caster.getInventory().clearContent();
		int first = ArcherStarter.grant(caster, RandomSource.create(7L));
		int carriedFirst = caster.getInventory().countItem(BuiltInRegistries.ITEM.getValue(ArcherStarter.STARTER_ID));
		caster.getInventory().clearContent();
		int second = ArcherStarter.grant(caster, RandomSource.create(7L));
		int carriedSecond = caster.getInventory().countItem(BuiltInRegistries.ITEM.getValue(ArcherStarter.STARTER_ID));
		LOG.info("[BEHAVIOR] test=aSecondStarterGrantHandsOutASecondSetIntoARoomyPack RESULT first={} second={} arrowRainFirst={} arrowRainSecond={}",
			first, second, carriedFirst, carriedSecond);
		helper.assertTrue(first == ArcherStarter.STARTER_BOOKS, "the first grant gave " + first + " books");
		helper.assertTrue(second == ArcherStarter.STARTER_BOOKS,
			"a second grant handed out " + second + " books, not another set of " + ArcherStarter.STARTER_BOOKS);
		helper.assertTrue(carriedSecond == carriedFirst, "the second set did not match the first");
		helper.succeed();
	}

	private static int freeSlots(final ServerPlayer caster) {
		int free = 0;
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			if (caster.getInventory().getItem(slot).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	private static ServerPlayer archer(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		// A stone floor across the arena, so the grappling hook has ground to land on and to read.
		for (int x = 0; x <= 12; x++) {
			for (int z = 0; z <= 12; z++) {
				helper.setBlock(x, 5, z, net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		Vec3 at = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		caster.getInventory().setItem(ArcherReworkGameTest.WEAPON_SLOT, new ItemStack(Items.BOW));
		caster.getInventory().setItem(ArcherReworkGameTest.HELD_QUIVER_SLOT, new ItemStack(Items.ARROW, 64));
		return caster;
	}

	private static Mob arrowHit(final GameTestHelper helper, final ServerPlayer caster, final Mob victim) {
		Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(arrow != null, "the level refused an arrow");
		arrow.setOwner(caster);
		arrow.setPos(caster.getX(), caster.getY() + 1.0, caster.getZ());
		helper.getLevel().addFreshEntity(arrow);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), ArcherSkills.SHOT_PROBE_DAMAGE);
		return victim;
	}

	private static final int WEAPON_SLOT = 35;
	/** The quiver for a case that casts: a cast replaces the hotbar row, so the arrows sit above it. */
	private static final int HELD_QUIVER_SLOT = 20;
	/** The three archer books that also need a foe in front of the caster before the shot can land. */
	private static final List<String> TARGET_BOOKS = List.of("piercing_shot", "rapid_fire", "hunters_mark");
}
