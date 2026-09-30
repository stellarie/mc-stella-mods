package com.stella.equips;

import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineSpecials;
import com.stella.equips.refine.RefineSpecials.Special;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The refine specials of +7, +10, +12 and +15, one test per row group of the tables. Each row is checked at its
 * level and one level below it, and a chance effect is forced through the roller so neither outcome flakes.
 */
public class RefineSpecialsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.refine-specials");
	private static final float HIT = 100.0F;
	private static final double TOLERANCE = 1.0e-2;
	private static final int PADDING = 20;
	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private record ArmourSet(String name, List<Item> pieces, Special material) {
		Item chestplate() {
			return this.pieces.get(1);
		}
	}

	private static final List<ArmourSet> SETS = List.of(
		new ArmourSet("leather", List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS), Special.EVASION),
		new ArmourSet("chainmail", List.of(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS), Special.RIPOSTE),
		new ArmourSet("iron", List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS), Special.VIGOR),
		new ArmourSet("gold", List.of(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS), Special.FORTUNE),
		new ArmourSet("diamond", List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS), Special.BULWARK),
		new ArmourSet("netherite", List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS), Special.EMBERGUARD)
	);

	static {
		RefineRolls.install();
	}

	// ---- weapons ----

	@GameTest(padding = PADDING)
	public void keenMeleeAtSeven(final GameTestHelper helper) {
		ServerPlayer keen = holder(helper, refined(Items.IRON_SWORD, Refine.KEEN_LEVEL));
		RefineRolls.force(keen, true);
		float won = melee(helper, keen, spider(helper, 3, 3), HIT);
		RefineRolls.force(keen, false);
		float lost = melee(helper, keen, spider(helper, 4, 3), HIT);
		ServerPlayer below = holder(helper, refined(Items.IRON_SWORD, Refine.KEEN_LEVEL - 1));
		RefineRolls.force(below, true);
		float atSix = melee(helper, below, spider(helper, 5, 3), HIT);
		LOG.info("[BEHAVIOR] test=keenMeleeAtSeven RESULT won={} lost={} atSix={} rolls={} sixRolls={}", won, lost, atSix, RefineRolls.rolls(keen), RefineRolls.rolls(below));
		helper.assertTrue(Math.abs(won - HIT * RefineEffects.KEEN_MULTIPLIER) < TOLERANCE, "a Keen hit did " + won);
		helper.assertTrue(Math.abs(lost - HIT) < TOLERANCE, "a Keen hit that lost its roll did " + lost);
		helper.assertTrue(RefineRolls.rolls(keen).equals(List.of(RefineRolls.roll(Special.KEEN, RefineEffects.KEEN_CHANCE), RefineRolls.roll(Special.KEEN, RefineEffects.KEEN_CHANCE))),
			"the +7 wielder rolled " + RefineRolls.rolls(keen));
		helper.assertTrue(Math.abs(atSix - HIT) < TOLERANCE && RefineRolls.rolls(below).isEmpty(), "a +6 sword rolled " + RefineRolls.rolls(below) + " and did " + atSix);
		helper.succeed();
	}

	@GameTest
	public void theShippedKeenRollMeetsItsChance(final GameTestHelper helper) {
		ServerPlayer keen = holder(helper, refined(Items.IRON_SWORD, Refine.KEEN_LEVEL));
		Mob victim = spider(helper, 3, 3);
		DamageSource source = helper.getLevel().damageSources().playerAttack(keen);
		int trials = 4000;
		int boosted = 0;
		RefineEffects.setRoller(null);
		try {
			for (int i = 0; i < trials; i++) {
				if (RefineEffects.outgoing(victim, source, HIT) > HIT) {
					boosted++;
				}
			}
		} finally {
			RefineRolls.install();
		}
		double p = RefineEffects.KEEN_CHANCE / 100.0;
		double sigma = Math.sqrt(trials * p * (1 - p));
		LOG.info("[BEHAVIOR] test=theShippedKeenRollMeetsItsChance RESULT boosted={} trials={} expected={}", boosted, trials, trials * p);
		helper.assertTrue(Math.abs(boosted - trials * p) <= 5 * sigma, boosted + " of " + trials + " hits were Keen");
		helper.succeed();
	}

	@GameTest(maxTicks = 150, padding = PADDING)
	public void splinterBleedsForLowTierWeapons(final GameTestHelper helper) {
		for (Item low : List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.GOLDEN_SWORD, Items.COPPER_SWORD)) {
			helper.assertTrue(RefineSpecials.of(refined(low, Refine.SEARING_LEVEL), Refine.SEARING_LEVEL).contains(Special.SPLINTER), low + " has no Splinter at +10");
		}
		helper.assertTrue(!RefineSpecials.of(refined(Items.IRON_SWORD, Refine.SEARING_LEVEL), Refine.SEARING_LEVEL).contains(Special.SPLINTER), "an iron sword has Splinter");
		ServerPlayer splinter = holder(helper, refined(Items.WOODEN_SWORD, Refine.SEARING_LEVEL));
		ServerPlayer below = holder(helper, refined(Items.WOODEN_SWORD, Refine.SEARING_LEVEL - 1));
		Mob bled = spider(helper, 3, 3);
		Mob control = spider(helper, 5, 3);
		float bledBefore = bled.getHealth();
		float controlBefore = control.getHealth();
		float first = melee(helper, splinter, bled, HIT);
		float controlFirst = melee(helper, below, control, HIT);
		helper.assertTrue(Math.abs(first - HIT) < TOLERANCE && Math.abs(controlFirst - HIT) < TOLERANCE, "the first hits did " + first + " and " + controlFirst);
		float expected = HIT + RefineEffects.SPLINTER_SECONDS * (float) RefineEffects.SPLINTER_SHARE * HIT;
		helper.runAfterDelay(70, () -> {
			float total = bledBefore - bled.getHealth();
			LOG.info("[BEHAVIOR] test=splinterBleedsForLowTierWeapons RESULT total={} expected={} control={}", total, expected, controlBefore - control.getHealth());
			helper.assertTrue(Math.abs(total - expected) < TOLERANCE, "a Splinter hit and its bleed took " + total + ", not " + expected);
			helper.assertTrue(Math.abs(controlBefore - control.getHealth() - HIT) < TOLERANCE, "a +9 hit bled: " + (controlBefore - control.getHealth()));
		});
		helper.runAfterDelay(110, () -> {
			helper.assertTrue(Math.abs(bledBefore - bled.getHealth() - expected) < TOLERANCE, "the bleed outlasted its seconds: " + (bledBefore - bled.getHealth()));
			helper.succeed();
		});
	}

	@GameTest
	public void searingBurnsForIronAndLongerForNetherite(final GameTestHelper helper) {
		int iron = burn(helper, Items.IRON_SWORD, Refine.SEARING_LEVEL, 3);
		int netherite = burn(helper, Items.NETHERITE_SWORD, Refine.SEARING_LEVEL, 4);
		int ironNine = burn(helper, Items.IRON_SWORD, Refine.SEARING_LEVEL - 1, 5);
		int netheriteNine = burn(helper, Items.NETHERITE_SWORD, Refine.SEARING_LEVEL - 1, 6);
		int diamond = burn(helper, Items.DIAMOND_SWORD, Refine.SEARING_LEVEL, 3);
		LOG.info("[BEHAVIOR] test=searingBurnsForIronAndLongerForNetherite RESULT iron={} netherite={} ironNine={} netheriteNine={} diamond={}", iron, netherite, ironNine, netheriteNine, diamond);
		helper.assertTrue(iron > RefineEffects.SEARING_TICKS - 5 && iron <= RefineEffects.SEARING_TICKS, "an iron +10 hit burned for " + iron + " ticks");
		helper.assertTrue(netherite > RefineEffects.NETHERITE_SEARING_TICKS - 5 && netherite <= RefineEffects.NETHERITE_SEARING_TICKS, "a netherite +10 hit burned for " + netherite + " ticks");
		helper.assertTrue(ironNine <= 0 && netheriteNine <= 0, "a +9 hit burned: " + ironNine + ", " + netheriteNine);
		helper.assertTrue(diamond <= 0, "a diamond +10 hit burned for " + diamond + " ticks");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void frostbiteSlowsForDiamondTridentAndMace(final GameTestHelper helper) {
		for (Item item : List.of(Items.DIAMOND_SWORD, Items.TRIDENT, Items.MACE)) {
			MobEffectInstance slow = slowAfterHit(helper, item, Refine.SEARING_LEVEL, 3);
			helper.assertTrue(slow != null && slow.getAmplifier() == RefineEffects.FROSTBITE_AMPLIFIER && slow.getDuration() > RefineEffects.FROSTBITE_TICKS - 5
				&& slow.getDuration() <= RefineEffects.FROSTBITE_TICKS, item + " at +10 left " + slow);
			helper.assertTrue(slowAfterHit(helper, item, Refine.SEARING_LEVEL - 1, 5) == null, item + " at +9 slowed");
		}
		helper.assertTrue(slowAfterHit(helper, Items.IRON_SWORD, Refine.SEARING_LEVEL, 3) == null, "an iron sword slowed at +10");
		helper.succeed();
	}

	@GameTest
	public void rendingWithersEveryMeleeWeaponAtTwelve(final GameTestHelper helper) {
		for (Item item : List.of(Items.WOODEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.TRIDENT, Items.MACE)) {
			ServerPlayer at = holder(helper, refined(item, Refine.RENDING_LEVEL));
			Mob withered = spider(helper, 3, 3);
			melee(helper, at, withered, HIT);
			ServerPlayer below = holder(helper, refined(item, Refine.RENDING_LEVEL - 1));
			Mob spared = spider(helper, 5, 3);
			melee(helper, below, spared, HIT);
			MobEffectInstance wither = withered.getEffect(MobEffects.WITHER);
			helper.assertTrue(wither != null && wither.getDuration() > RefineEffects.RENDING_TICKS - 5 && wither.getDuration() <= RefineEffects.RENDING_TICKS, item + " at +12 left " + wither);
			helper.assertTrue(!spared.hasEffect(MobEffects.WITHER), item + " at +11 withered");
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void ascendedNeedsFifteen(final GameTestHelper helper) {
		float at = shockwaveLoss(helper, Refine.ASCENDED_LEVEL);
		float below = shockwaveLoss(helper, Refine.ASCENDED_LEVEL - 1);
		LOG.info("[BEHAVIOR] test=ascendedNeedsFifteen RESULT at={} below={}", at, below);
		helper.assertTrue(Math.abs(at - HIT * RefineEffects.SHOCKWAVE_SHARE) < TOLERANCE, "the +15 shockwave did " + at);
		helper.assertTrue(below == 0.0F, "a +14 blow sent a shockwave that did " + below);
		helper.succeed();
	}

	// ---- bows and crossbows ----

	@GameTest
	public void keenBowsAtSeven(final GameTestHelper helper) {
		for (Item bow : List.of(Items.BOW, Items.CROSSBOW)) {
			ItemStack seven = refined(bow, Refine.KEEN_LEVEL);
			ServerPlayer shooter = holder(helper, seven);
			RefineRolls.force(shooter, true);
			float boosted = shot(helper, shooter, seven, spider(helper, 3, 3), HIT);
			RefineRolls.force(shooter, false);
			float plain = shot(helper, shooter, seven, spider(helper, 4, 3), HIT);
			ItemStack six = refined(bow, Refine.KEEN_LEVEL - 1);
			ServerPlayer below = holder(helper, six);
			RefineRolls.force(below, true);
			float atSix = shot(helper, below, six, spider(helper, 5, 3), HIT);
			helper.assertTrue(Math.abs(boosted - HIT * RefineEffects.KEEN_MULTIPLIER) < TOLERANCE, bow + " Keen arrow did " + boosted);
			helper.assertTrue(Math.abs(plain - HIT) < TOLERANCE, bow + " Keen arrow that lost its roll did " + plain);
			helper.assertTrue(Math.abs(atSix - HIT) < TOLERANCE && RefineRolls.rolls(below).isEmpty(), bow + " at +6 rolled " + RefineRolls.rolls(below));
		}
		helper.succeed();
	}

	@GameTest
	public void piercingLetsAnArrowThroughOneMoreMob(final GameTestHelper helper) {
		for (Item bow : List.of(Items.BOW, Items.CROSSBOW)) {
			int at = pierceOf(helper, refined(bow, Refine.SEARING_LEVEL));
			int below = pierceOf(helper, refined(bow, Refine.SEARING_LEVEL - 1));
			LOG.info("[BEHAVIOR] test=piercingLetsAnArrowThroughOneMoreMob RESULT bow={} at={} below={}", bow, at, below);
			helper.assertTrue(at == 1, bow + " at +10 gave an arrow of pierce level " + at);
			helper.assertTrue(below == 0, bow + " at +9 gave an arrow of pierce level " + below);
		}
		helper.succeed();
	}

	@GameTest
	public void bowsWitherAtTwelveOnArrowHitsOnly(final GameTestHelper helper) {
		ItemStack twelve = refined(Items.BOW, Refine.RENDING_LEVEL);
		ServerPlayer shooter = holder(helper, twelve);
		Mob withered = spider(helper, 3, 3);
		shot(helper, shooter, twelve, withered, HIT);
		ItemStack eleven = refined(Items.BOW, Refine.RENDING_LEVEL - 1);
		Mob spared = spider(helper, 4, 3);
		shot(helper, holder(helper, eleven), eleven, spared, HIT);
		Mob bashed = spider(helper, 5, 3);
		melee(helper, shooter, bashed, HIT);
		helper.assertTrue(withered.hasEffect(MobEffects.WITHER), "a +12 arrow did not wither");
		helper.assertTrue(!spared.hasEffect(MobEffects.WITHER), "a +11 arrow withered");
		helper.assertTrue(!bashed.hasEffect(MobEffects.WITHER), "a bash with a +12 bow withered");
		helper.succeed();
	}

	@GameTest(maxTicks = 60, padding = PADDING)
	public void volleyLoosesOneMoreArrowAtHalfDamage(final GameTestHelper helper) {
		ItemStack fifteen = refined(Items.BOW, Refine.ASCENDED_LEVEL);
		ServerPlayer shooter = holder(helper, fifteen);
		Arrow main = shoot(helper, shooter, fifteen);
		ItemStack fourteen = refined(Items.BOW, Refine.ASCENDED_LEVEL - 1);
		ServerPlayer other = holder(helper, fourteen);
		Arrow unvolleyed = shoot(helper, other, fourteen);
		Mob near = spider(helper, 4, 3);
		float nearBefore = near.getHealth();
		shot(helper, shooter, fifteen, spider(helper, 3, 5), HIT);
		helper.runAfterDelay(4, () -> {
			List<Arrow> extras = volleyArrows(helper, shooter);
			List<Arrow> none = volleyArrows(helper, other);
			double share = extras.isEmpty() ? -1.0 : RefineEffects.baseDamage(extras.get(0)) / RefineEffects.baseDamage(main);
			double turn = extras.isEmpty() ? -1.0 : Math.abs(Math.toDegrees(Math.atan2(extras.get(0).getDeltaMovement().z, extras.get(0).getDeltaMovement().x)
				- Math.atan2(main.getDeltaMovement().z, main.getDeltaMovement().x)));
			LOG.info("[BEHAVIOR] test=volleyLoosesOneMoreArrowAtHalfDamage RESULT extras={} none={} share={} turn={}", extras.size(), none.size(), share, turn);
			helper.assertTrue(extras.size() == 1, "a +15 shot loosed " + extras.size() + " extra arrows");
			helper.assertTrue(Math.abs(share - RefineEffects.VOLLEY_SHARE) < TOLERANCE, "the extra arrow did " + share + " of the shot");
			helper.assertTrue(Math.abs(turn - RefineEffects.VOLLEY_SPREAD_DEGREES) < 0.5, "the extra arrow strayed " + turn + " degrees");
			helper.assertTrue(extras.get(0).pickup == net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED, "the extra arrow can be picked up");
			helper.assertTrue(none.isEmpty(), "a +14 shot loosed " + none.size() + " extra arrows");
			helper.assertTrue(near.getHealth() == nearBefore, "a +15 arrow hit sent a shockwave");
			extras.forEach(Arrow::discard);
			main.discard();
			unvolleyed.discard();
			helper.succeed();
		});
	}

	// ---- armour, +7 by piece ----

	@GameTest
	public void clarityShortensHarmfulEffectsOnAHelmet(final GameTestHelper helper) {
		int at = durationAfter(helper, Items.IRON_HELMET, Refine.KEEN_LEVEL, new MobEffectInstance(MobEffects.POISON, 200));
		int below = durationAfter(helper, Items.IRON_HELMET, Refine.KEEN_LEVEL - 1, new MobEffectInstance(MobEffects.POISON, 200));
		int chest = durationAfter(helper, Items.IRON_CHESTPLATE, Refine.KEEN_LEVEL, new MobEffectInstance(MobEffects.POISON, 200));
		int kind = durationAfter(helper, Items.IRON_HELMET, Refine.KEEN_LEVEL, new MobEffectInstance(MobEffects.SPEED, 200));
		LOG.info("[BEHAVIOR] test=clarityShortensHarmfulEffectsOnAHelmet RESULT at={} below={} chest={} kind={}", at, below, chest, kind);
		helper.assertTrue(at == Math.round(200 * (1.0 - RefineEffects.CLARITY_SHARE)), "poison at +7 lasted " + at);
		helper.assertTrue(below == 200 && chest == 200, "poison lasted " + below + " at +6 and " + chest + " on a chestplate");
		helper.assertTrue(kind == 200, "a helpful effect lasted " + kind);
		helper.succeed();
	}

	@GameTest
	public void fortitudeAndStrideAreSharesOfBase(final GameTestHelper helper) {
		double fort = attribute(refined(Items.IRON_CHESTPLATE, Refine.KEEN_LEVEL), Attributes.MAX_HEALTH, 200.0, EquipmentSlot.CHEST);
		double fortBelow = attribute(refined(Items.IRON_CHESTPLATE, Refine.KEEN_LEVEL - 1), Attributes.MAX_HEALTH, 200.0, EquipmentSlot.CHEST);
		double stride = attribute(refined(Items.IRON_LEGGINGS, Refine.KEEN_LEVEL), Attributes.MOVEMENT_SPEED, 0.1, EquipmentSlot.LEGS);
		double strideBelow = attribute(refined(Items.IRON_LEGGINGS, Refine.KEEN_LEVEL - 1), Attributes.MOVEMENT_SPEED, 0.1, EquipmentSlot.LEGS);
		double elsewhere = attribute(refined(Items.IRON_HELMET, Refine.KEEN_LEVEL), Attributes.MAX_HEALTH, 200.0, EquipmentSlot.HEAD)
			+ attribute(refined(Items.IRON_BOOTS, Refine.KEEN_LEVEL), Attributes.MOVEMENT_SPEED, 0.1, EquipmentSlot.FEET);
		LOG.info("[BEHAVIOR] test=fortitudeAndStrideAreSharesOfBase RESULT fort={} fortBelow={} stride={} strideBelow={}", fort, fortBelow, stride, strideBelow);
		helper.assertTrue(Math.abs(fort - 200.0 * (1 + Refine.FORTITUDE_MULTIPLIER)) < 1.0e-6 && fortBelow == 200.0, "a chestplate made 200 health " + fort + " at +7 and " + fortBelow + " at +6");
		helper.assertTrue(Math.abs(stride - 0.1 * (1 + Refine.STRIDE_MULTIPLIER)) < 1.0e-9 && strideBelow == 0.1, "leggings made 0.1 speed " + stride + " at +7 and " + strideBelow + " at +6");
		helper.assertTrue(elsewhere == 200.0 + 0.1, "a helmet or boots at +7 changed health or speed: " + elsewhere);
		helper.succeed();
	}

	@GameTest
	public void featherfallCutsFallDamageOnBoots(final GameTestHelper helper) {
		float at = fallLoss(helper, Items.IRON_BOOTS, Refine.KEEN_LEVEL, 4);
		float below = fallLoss(helper, Items.IRON_BOOTS, Refine.KEEN_LEVEL - 1, 5);
		float leggings = fallLoss(helper, Items.IRON_LEGGINGS, Refine.KEEN_LEVEL, 6);
		LOG.info("[BEHAVIOR] test=featherfallCutsFallDamageOnBoots RESULT at={} below={} leggings={}", at, below, leggings);
		helper.assertTrue(Math.abs(at - 50.0F * (1.0F - (float) RefineEffects.FEATHERFALL_SHARE)) < TOLERANCE, "a fall of 50 hurt boots at +7 for " + at);
		helper.assertTrue(Math.abs(below - 50.0F) < TOLERANCE && Math.abs(leggings - 50.0F) < TOLERANCE, "a fall hurt +6 boots for " + below + " and +7 leggings for " + leggings);
		helper.succeed();
	}

	// ---- armour, +10 by material ----

	@GameTest
	public void materialsPickTheirOwnEffectAtTen(final GameTestHelper helper) {
		for (ArmourSet set : SETS) {
			ItemStack ten = refined(set.chestplate(), Refine.SEARING_LEVEL);
			ItemStack nine = refined(set.chestplate(), Refine.SEARING_LEVEL - 1);
			helper.assertTrue(RefineSpecials.of(ten, Refine.SEARING_LEVEL).equals(List.of(Special.FORTITUDE, set.material)), set.name + " at +10 has " + RefineSpecials.of(ten, Refine.SEARING_LEVEL));
			helper.assertTrue(RefineSpecials.of(nine, Refine.SEARING_LEVEL - 1).equals(List.of(Special.FORTITUDE)), set.name + " at +9 has " + RefineSpecials.of(nine, Refine.SEARING_LEVEL - 1));
		}
		ItemStack copper = refined(Items.COPPER_CHESTPLATE, Refine.SEARING_LEVEL);
		helper.assertTrue(RefineSpecials.of(copper, Refine.SEARING_LEVEL).equals(List.of(Special.FORTITUDE)), "copper armour has a material effect: " + RefineSpecials.of(copper, Refine.SEARING_LEVEL));
		helper.succeed();
	}

	@GameTest
	public void evasionIgnoresAHitWithThreePercentPerPiece(final GameTestHelper helper) {
		Mob attacker = spider(helper, 5, 3);
		Mob wearer = wearing(helper, SETS.get(0), Refine.SEARING_LEVEL, 3);
		RefineRolls.force(wearer, true);
		float before = wearer.getHealth();
		boolean landed = wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT / 2);
		float evaded = before - wearer.getHealth();
		RefineRolls.force(wearer, false);
		wearer.damageCooldownTime = 0;
		wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT / 2);
		float hurt = before - wearer.getHealth();
		Mob nine = wearing(helper, SETS.get(0), Refine.SEARING_LEVEL - 1, 4);
		RefineRolls.force(nine, true);
		float nineBefore = nine.getHealth();
		nine.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT / 2);
		LOG.info("[BEHAVIOR] test=evasionIgnoresAHitWithThreePercentPerPiece RESULT landed={} evaded={} hurt={} nineLost={} rolls={}", landed, evaded, hurt, nineBefore - nine.getHealth(), RefineRolls.rolls(wearer));
		helper.assertTrue(!landed && evaded == 0.0F, "an evaded hit still hurt: " + evaded);
		helper.assertTrue(hurt > 0.0F, "a hit that lost the roll did nothing");
		helper.assertTrue(RefineRolls.rolls(wearer).equals(List.of(RefineRolls.roll(Special.EVASION, 4 * RefineEffects.EVASION_CHANCE), RefineRolls.roll(Special.EVASION, 4 * RefineEffects.EVASION_CHANCE))),
			"four pieces rolled " + RefineRolls.rolls(wearer));
		helper.assertTrue(nineBefore - nine.getHealth() > 0.0F && RefineRolls.rolls(nine).isEmpty(), "+9 leather rolled " + RefineRolls.rolls(nine));
		helper.succeed();
	}

	@GameTest
	public void riposteReflectsAShareOfABlow(final GameTestHelper helper) {
		float[] won = riposte(helper, Refine.SEARING_LEVEL, true, 3);
		float[] lost = riposte(helper, Refine.SEARING_LEVEL, false, 4);
		float[] nine = riposte(helper, Refine.SEARING_LEVEL - 1, true, 5);
		LOG.info("[BEHAVIOR] test=riposteReflectsAShareOfABlow RESULT won={} lost={} nine={}", List.of(won[0], won[1]), List.of(lost[0], lost[1]), List.of(nine[0], nine[1]));
		helper.assertTrue(won[0] > 0.0F && Math.abs(won[1] - RefineEffects.RIPOSTE_SHARE * won[0]) < TOLERANCE, "a Riposte reflected " + won[1] + " of a blow of " + won[0]);
		helper.assertTrue(lost[0] > 0.0F && lost[1] == 0.0F, "a Riposte that lost its roll reflected " + lost[1]);
		helper.assertTrue(nine[0] > 0.0F && nine[1] == 0.0F, "a +9 chainmail piece reflected " + nine[1]);
		helper.succeed();
	}

	@GameTest
	public void fortuneAddsExperienceFromKills(final GameTestHelper helper) {
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		ServerPlayer gold = helper.makeMockServerPlayerInLevel();
		ServerPlayer nine = helper.makeMockServerPlayerInLevel();
		wear(gold, SETS.get(3), Refine.SEARING_LEVEL, 4);
		wear(nine, SETS.get(3), Refine.SEARING_LEVEL - 1, 4);
		Mob victim = spider(helper, 3, 3);
		ServerLevel level = helper.getLevel();
		int base = victim.getExperienceReward(level, plain);
		int boosted = victim.getExperienceReward(level, gold);
		int atNine = victim.getExperienceReward(level, nine);
		ServerPlayer one = helper.makeMockServerPlayerInLevel();
		wear(one, SETS.get(3), Refine.SEARING_LEVEL, 1);
		int trials = 4000;
		long total = 0;
		for (int i = 0; i < trials; i++) {
			total += RefineEffects.experience(one, 5);
		}
		double mean = total / (double) trials;
		double expected = 5 * (1 + RefineEffects.FORTUNE_SHARE);
		LOG.info("[BEHAVIOR] test=fortuneAddsExperienceFromKills RESULT base={} boosted={} atNine={} mean={} expected={}", base, boosted, atNine, mean, expected);
		helper.assertTrue(base > 0 && boosted == Math.round(base * (1 + 4 * RefineEffects.FORTUNE_SHARE)), "four gold pieces turned " + base + " experience into " + boosted);
		helper.assertTrue(atNine == base, "+9 gold turned " + base + " experience into " + atNine);
		helper.assertTrue(Math.abs(mean - expected) < 0.05, "one gold piece turned 5 experience into a mean of " + mean);
		helper.succeed();
	}

	@GameTest
	public void bulwarkTakesAShareOffEveryPiece(final GameTestHelper helper) {
		float four = damageTaken(helper, SETS.get(4), Refine.SEARING_LEVEL, 4, false, 3);
		float one = damageTaken(helper, SETS.get(4), Refine.SEARING_LEVEL, 1, false, 4);
		float nine = damageTaken(helper, SETS.get(4), Refine.SEARING_LEVEL - 1, 4, false, 5);
		LOG.info("[BEHAVIOR] test=bulwarkTakesAShareOffEveryPiece RESULT four={} one={} nine={}", four, one, nine);
		helper.assertTrue(Math.abs(four - 50.0F * (1.0F - 4 * (float) RefineEffects.BULWARK_SHARE)) < TOLERANCE, "four +10 diamond pieces took " + four + " of 50");
		helper.assertTrue(Math.abs(one - 50.0F * (1.0F - (float) RefineEffects.BULWARK_SHARE)) < TOLERANCE, "one +10 diamond piece took " + one + " of 50");
		helper.assertTrue(Math.abs(nine - 50.0F) < TOLERANCE, "four +9 diamond pieces took " + nine + " of 50");
		helper.succeed();
	}

	@GameTest
	public void emberguardHalvesFireDamagePerPiece(final GameTestHelper helper) {
		float one = damageTaken(helper, SETS.get(5), Refine.SEARING_LEVEL, 1, true, 3);
		float four = damageTaken(helper, SETS.get(5), Refine.SEARING_LEVEL, 4, true, 4);
		float nine = damageTaken(helper, SETS.get(5), Refine.SEARING_LEVEL - 1, 1, true, 5);
		float notFire = damageTaken(helper, SETS.get(5), Refine.SEARING_LEVEL, 1, false, 6);
		LOG.info("[BEHAVIOR] test=emberguardHalvesFireDamagePerPiece RESULT one={} four={} nine={} notFire={}", one, four, nine, notFire);
		helper.assertTrue(Math.abs(one - 50.0F * (1.0F - (float) RefineEffects.EMBERGUARD_SHARE)) < TOLERANCE, "one +10 netherite piece took " + one + " fire damage of 50");
		helper.assertTrue(Math.abs(four - 50.0F * (1.0F - 4 * (float) RefineEffects.EMBERGUARD_SHARE)) < TOLERANCE, "four +10 netherite pieces took " + four + " fire damage of 50");
		helper.assertTrue(Math.abs(nine - 50.0F) < TOLERANCE, "a +9 netherite piece took " + nine + " fire damage of 50");
		helper.assertTrue(Math.abs(notFire - 50.0F) < TOLERANCE, "a +10 netherite piece took " + notFire + " damage of 50 from something that is not fire");
		helper.succeed();
	}

	// ---- armour, +12 and +15 for every material ----

	@GameTest
	public void thornedAndBlessedComeToEveryMaterial(final GameTestHelper helper) {
		for (ArmourSet set : SETS) {
			ItemStack twelve = refined(set.chestplate(), Refine.RENDING_LEVEL);
			ItemStack eleven = refined(set.chestplate(), Refine.RENDING_LEVEL - 1);
			helper.assertTrue(RefineSpecials.of(twelve, Refine.RENDING_LEVEL).contains(Special.THORNED) && !RefineSpecials.of(eleven, Refine.RENDING_LEVEL - 1).contains(Special.THORNED),
				set.name + " has Thorned at the wrong level");
			float[] pricked = thornedExchange(helper, set, Refine.RENDING_LEVEL, 3);
			float[] spared = thornedExchange(helper, set, Refine.RENDING_LEVEL - 1, 5);
			helper.assertTrue(pricked[0] > 0.0F && Math.abs(pricked[1] - RefineEffects.THORN_SHARE * pricked[0]) < TOLERANCE, set.name + " +12 pricked for " + pricked[1] + " after " + pricked[0]);
			helper.assertTrue(spared[1] == 0.0F, set.name + " +11 pricked for " + spared[1]);

			Mob blessed = wearing(helper, set, Refine.ASCENDED_LEVEL, 3);
			Mob nearly = wearing(helper, set, Refine.ASCENDED_LEVEL - 1, 5);
			blessed.setHealth(blessed.getMaxHealth() / 2);
			nearly.setHealth(nearly.getMaxHealth() / 2);
			float blessedBefore = blessed.getHealth();
			float nearlyBefore = nearly.getHealth();
			RefineEffects.blessOnce(blessed);
			RefineEffects.blessOnce(nearly);
			helper.assertTrue(Math.abs(blessed.getHealth() - blessedBefore - 4 * RefineEffects.BLESSING_SHARE * blessed.getMaxHealth()) < TOLERANCE, set.name + " +15 healed " + (blessed.getHealth() - blessedBefore));
			helper.assertTrue(nearly.getHealth() == nearlyBefore, set.name + " +14 healed " + (nearly.getHealth() - nearlyBefore));
		}
		helper.succeed();
	}

	// ---- stacking and the tooltip ----

	@GameTest(padding = PADDING)
	public void effectsStack(final GameTestHelper helper) {
		ItemStack sword = refined(Items.IRON_SWORD, Refine.ASCENDED_LEVEL);
		ItemStack bow = refined(Items.BOW, Refine.ASCENDED_LEVEL);
		ItemStack plate = refined(Items.DIAMOND_CHESTPLATE, Refine.ASCENDED_LEVEL);
		helper.assertTrue(RefineSpecials.of(sword, Refine.ASCENDED_LEVEL).equals(List.of(Special.KEEN, Special.SEARING, Special.RENDING, Special.ASCENDED)), "an iron sword at +15 has " + RefineSpecials.of(sword, Refine.ASCENDED_LEVEL));
		helper.assertTrue(RefineSpecials.of(bow, Refine.ASCENDED_LEVEL).equals(List.of(Special.KEEN, Special.PIERCING, Special.RENDING, Special.VOLLEY)), "a bow at +15 has " + RefineSpecials.of(bow, Refine.ASCENDED_LEVEL));
		helper.assertTrue(RefineSpecials.of(plate, Refine.ASCENDED_LEVEL).equals(List.of(Special.FORTITUDE, Special.BULWARK, Special.THORNED, Special.BLESSED)), "a diamond chestplate at +15 has " + RefineSpecials.of(plate, Refine.ASCENDED_LEVEL));

		ServerPlayer wielder = holder(helper, sword);
		RefineRolls.force(wielder, true);
		Mob struck = spider(helper, 3, 3);
		Mob near = spider(helper, 5, 3);
		float nearBefore = near.getHealth();
		float dealt = melee(helper, wielder, struck, HIT);
		helper.assertTrue(Math.abs(dealt - HIT * RefineEffects.KEEN_MULTIPLIER) < TOLERANCE, "the +7 effect of a +15 sword was lost: " + dealt);
		helper.assertTrue(struck.getRemainingFireTicks() > 0, "the +10 effect of a +15 sword was lost");
		helper.assertTrue(struck.hasEffect(MobEffects.WITHER), "the +12 effect of a +15 sword was lost");
		helper.assertTrue(Math.abs(nearBefore - near.getHealth() - dealt * RefineEffects.SHOCKWAVE_SHARE) < TOLERANCE, "the +15 effect of a +15 sword was lost: " + (nearBefore - near.getHealth()));

		Mob wearer = spider(helper, 3, 5);
		wearer.setItemSlot(EquipmentSlot.CHEST, plate);
		Mob attacker = spider(helper, 5, 5);
		float attackerBefore = attacker.getHealth();
		wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
		helper.assertTrue(Math.abs(attackerBefore - attacker.getHealth() - RefineEffects.THORN_SHARE * HIT) < TOLERANCE, "the +12 effect of +15 armour was lost");
		wearer.setHealth(wearer.getMaxHealth() / 2);
		float healedFrom = wearer.getHealth();
		RefineEffects.blessOnce(wearer);
		helper.assertTrue(Math.abs(wearer.getHealth() - healedFrom - RefineEffects.BLESSING_SHARE * wearer.getMaxHealth()) < TOLERANCE, "the +15 effect of +15 armour was lost");
		wearer.damageCooldownTime = 0;
		float sharedBefore = wearer.getHealth();
		wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), HIT);
		helper.assertTrue(Math.abs(sharedBefore - wearer.getHealth() - HIT * (1.0F - (float) RefineEffects.BULWARK_SHARE)) < TOLERANCE, "the +10 effect of +15 armour was lost");
		helper.succeed();
	}

	@GameTest
	public void theTooltipNamesTheSpecialsAtEachLevel(final GameTestHelper helper) {
		ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
		Refine.setLevel(plate, Refine.SEARING_LEVEL);
		List<String> ten = lore(plate);
		List<String> effects = Refine.effects(plate, Refine.SEARING_LEVEL);
		Refine.setLevel(plate, Refine.SEARING_LEVEL - 1);
		List<String> nine = lore(plate);
		Refine.setLevel(plate, 0);
		List<String> zero = lore(plate);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Vigor: +2 max health"), Component.literal("Searing: hits set foes alight"), Component.literal("A keepsake"))));
		Refine.setLevel(sword, Refine.SEARING_LEVEL);
		List<String> swordLore = lore(sword);
		LOG.info("[BEHAVIOR] test=theTooltipNamesTheSpecialsAtEachLevel RESULT ten={} nine={} zero={} sword={}", ten, nine, zero, swordLore);
		helper.assertTrue(ten.size() == 3 && ten.get(0).equals("Refine +10") && ten.get(1).startsWith("Fortitude:") && ten.get(2).startsWith("Vigor:"), "a +10 iron chestplate lists " + ten);
		helper.assertTrue(effects.size() == 2 && effects.get(0).equals(ten.get(1)) && effects.get(1).equals(ten.get(2)), "Refine.effects lists " + effects);
		helper.assertTrue(nine.size() == 2 && nine.get(1).startsWith("Fortitude:"), "a +9 iron chestplate lists " + nine);
		helper.assertTrue(zero.isEmpty(), "a +0 piece lists " + zero);
		helper.assertTrue(swordLore.size() == 4 && swordLore.get(0).equals("Refine +10") && swordLore.get(1).startsWith("Keen:") && swordLore.get(2).startsWith("Searing:")
			&& !swordLore.get(2).equals("Searing: hits set foes alight") && swordLore.get(3).equals("A keepsake") && swordLore.stream().noneMatch(line -> line.startsWith("Vigor:")),
			"an old line was not replaced: " + swordLore);
		helper.succeed();
	}

	// ---- helpers ----

	private static List<String> lore(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static ItemStack refined(final Item item, final int level) {
		ItemStack stack = new ItemStack(item);
		Refine.setLevel(stack, level);
		return stack;
	}

	private static ServerPlayer holder(final GameTestHelper helper, final ItemStack held) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	private static Mob spider(final GameTestHelper helper, final int x, final int z) {
		Mob spider = helper.spawnMob(EntityTypes.SPIDER, x, 2, z).withNoFreeWill().spawn();
		spider.setPersistenceRequired();
		spider.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		spider.setHealth(1000.0F);
		return spider;
	}

	private static Mob pig(final GameTestHelper helper, final int z) {
		Mob pig = helper.spawnMob(EntityTypes.PIG, 3, 2, z).withNoFreeWill().spawn();
		pig.setPersistenceRequired();
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		pig.setHealth(100.0F);
		return pig;
	}

	private static void wear(final LivingEntity wearer, final ArmourSet set, final int level, final int pieces) {
		for (int i = 0; i < pieces; i++) {
			wearer.setItemSlot(ARMOR_SLOTS.get(i), refined(set.pieces.get(i), level));
		}
	}

	private static Mob wearing(final GameTestHelper helper, final ArmourSet set, final int level, final int z) {
		Mob wearer = pig(helper, z);
		wear(wearer, set, level, 4);
		return wearer;
	}

	private static float melee(final GameTestHelper helper, final ServerPlayer player, final Mob victim, final float hit) {
		return hurt(victim, helper.getLevel().damageSources().playerAttack(player), hit);
	}

	private static float shot(final GameTestHelper helper, final ServerPlayer player, final ItemStack bow, final Mob victim, final float hit) {
		Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), bow);
		return hurt(victim, helper.getLevel().damageSources().arrow(arrow, player), hit);
	}

	private static float hurt(final Mob victim, final DamageSource source, final float hit) {
		victim.damageCooldownTime = 0;
		float before = victim.getHealth();
		victim.hurtServer((ServerLevel) victim.level(), source, hit);
		return before - victim.getHealth();
	}

	private static int burn(final GameTestHelper helper, final Item item, final int level, final int z) {
		Mob victim = spider(helper, z, z);
		melee(helper, holder(helper, refined(item, level)), victim, HIT);
		return victim.getRemainingFireTicks();
	}

	private static MobEffectInstance slowAfterHit(final GameTestHelper helper, final Item item, final int level, final int z) {
		Mob victim = spider(helper, z, z);
		melee(helper, holder(helper, refined(item, level)), victim, HIT);
		return victim.getEffect(MobEffects.SLOWNESS);
	}

	private static float shockwaveLoss(final GameTestHelper helper, final int level) {
		ServerPlayer player = holder(helper, refined(Items.IRON_SWORD, level));
		Mob struck = spider(helper, 3, 3);
		Mob near = spider(helper, 5, 3);
		float before = near.getHealth();
		melee(helper, player, struck, HIT);
		return before - near.getHealth();
	}

	private static Arrow shoot(final GameTestHelper helper, final ServerPlayer player, final ItemStack bow) {
		Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), bow);
		arrow.setDeltaMovement(1.0, 0.0, 0.0);
		helper.getLevel().addFreshEntity(arrow);
		return arrow;
	}

	private static int pierceOf(final GameTestHelper helper, final ItemStack bow) {
		Arrow arrow = shoot(helper, holder(helper, bow), bow);
		int level = arrow.getPierceLevel();
		arrow.discard();
		return level;
	}

	private static List<Arrow> volleyArrows(final GameTestHelper helper, final ServerPlayer owner) {
		return helper.getLevel().getEntitiesOfClass(Arrow.class, owner.getBoundingBox().inflate(32.0),
			arrow -> arrow.entityTags().contains(RefineEffects.VOLLEY_TAG) && arrow.getOwner() == owner);
	}

	private static int durationAfter(final GameTestHelper helper, final Item piece, final int level, final MobEffectInstance effect) {
		Mob wearer = pig(helper, 3);
		wearer.setItemSlot(Refine.armorSlotOf(new ItemStack(piece)), refined(piece, level));
		wearer.addEffect(effect);
		MobEffectInstance applied = wearer.getEffect(effect.getEffect());
		return applied == null ? -1 : applied.getDuration();
	}

	private static double attribute(final ItemStack piece, final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, final double base, final EquipmentSlot slot) {
		return piece.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(attribute, base, slot);
	}

	private static float fallLoss(final GameTestHelper helper, final Item piece, final int level, final int z) {
		Mob wearer = pig(helper, z);
		wearer.setItemSlot(Refine.armorSlotOf(new ItemStack(piece)), refined(piece, level));
		return hurt(wearer, helper.getLevel().damageSources().fall(), 50.0F);
	}

	private static float damageTaken(final GameTestHelper helper, final ArmourSet set, final int level, final int pieces, final boolean fire, final int z) {
		Mob wearer = pig(helper, z);
		wear(wearer, set, level, pieces);
		DamageSource source = fire ? helper.getLevel().damageSources().onFire() : helper.getLevel().damageSources().magic();
		return hurt(wearer, source, 50.0F);
	}

	/** A blow on a wearer of chainmail: the blow, then the health its attacker lost. */
	private static float[] riposte(final GameTestHelper helper, final int level, final boolean win, final int z) {
		Mob attacker = spider(helper, 5, z);
		Mob wearer = pig(helper, z);
		wearer.setItemSlot(EquipmentSlot.CHEST, refined(SETS.get(1).chestplate(), level));
		RefineRolls.force(wearer, win);
		float attackerBefore = attacker.getHealth();
		hurt(wearer, helper.getLevel().damageSources().mobAttack(attacker), HIT / 2);
		return new float[] {HIT / 2, attackerBefore - attacker.getHealth()};
	}

	private static float[] thornedExchange(final GameTestHelper helper, final ArmourSet set, final int level, final int z) {
		Mob attacker = spider(helper, 5, z);
		Mob wearer = pig(helper, z);
		wearer.setItemSlot(EquipmentSlot.CHEST, refined(set.chestplate(), level));
		float attackerBefore = attacker.getHealth();
		hurt(wearer, helper.getLevel().damageSources().mobAttack(attacker), HIT / 2);
		return new float[] {HIT / 2, attackerBefore - attacker.getHealth()};
	}
}
