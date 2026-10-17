package com.stella.end;

import com.stella.equips.refine.Refine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.phys.Vec3;

/** The withering sword and bow: their grade, their wither, the refine bonus, and the one in a hundred drop. */
public class WitheringArmsGameTest {
	private static final Vec3 SPOT = SovereignGameTest.ANCHOR.add(0.0, 0.0, 6.0);
	/** Health a hit target holds, so a hit of a hundred leaves it standing and a float still tells the hits apart. */
	private static final double TARGET_HEALTH = 100000.0;
	private static final float BLOW = 100.0F;

	/** A stand-in wielder that never moves, holding a stack. */
	static Zombie wielder(final ServerLevel end, final Vec3 at, final ItemStack held) {
		Zombie zombie = EndTestSupport.dummy(end, at);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, held);
		return zombie;
	}

	/** A target with plain health and no armour, so the loss from a blow is the blow. */
	static Zombie target(final ServerLevel end, final Vec3 at) {
		Zombie zombie = EndTestSupport.dummy(end, at);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		zombie.setHealth(zombie.getMaxHealth());
		return zombie;
	}

	static ItemStack refined(final ItemStack stack, final int level) {
		ItemStack copy = stack.copy();
		CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> tag.putInt(WitheringArms.REFINE_KEY, level));
		return copy;
	}

	static float lost(final ServerLevel end, final DamageSource source) {
		Zombie victim = target(end, SPOT);
		float before = victim.getHealth();
		victim.hurtServer(end, source, BLOW);
		float lost = before - victim.getHealth();
		victim.discard();
		return lost;
	}

	static DamageSource arrowOf(final ServerLevel end, final Zombie shooter, final ItemStack bow) {
		Arrow arrow = new Arrow(end, shooter, new ItemStack(Items.ARROW), bow);
		return end.damageSources().arrow(arrow, shooter);
	}

	@GameTest(environment = "mod-stella-end-test:arms_grade", maxTicks = 20)
	public void theWitheringSwordAndBowMendWithANetheriteIngotAndTheEquipsRefineGradesThemAsNetherite(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(WitheringArms.sword());
		ItemStack bow = new ItemStack(WitheringArms.bow());
		Repairable swordRepair = sword.get(DataComponents.REPAIRABLE);
		Repairable bowRepair = bow.get(DataComponents.REPAIRABLE);
		ItemStack ingot = new ItemStack(Items.NETHERITE_INGOT);
		ItemStack iron = new ItemStack(Items.IRON_INGOT);
		boolean swordMends = swordRepair != null && swordRepair.isValidRepairItem(ingot) && !swordRepair.isValidRepairItem(iron);
		boolean bowMends = bowRepair != null && bowRepair.isValidRepairItem(ingot) && !bowRepair.isValidRepairItem(iron);
		boolean swordRefines = Refine.canRefine(sword) && Refine.grade(sword) == Refine.Grade.WEAPON_4;
		boolean bowRefines = Refine.canRefine(bow) && Refine.grade(bow) == Refine.Grade.WEAPON_4;
		boolean key = WitheringArms.REFINE_KEY.equals(Refine.LEVEL_KEY);
		boolean read = Refine.level(refined(sword, 6)) == 6;
		EndTestSupport.result("armsGrade", "swordMends=" + swordMends + " bowMends=" + bowMends + " swordRefines=" + swordRefines + " bowRefines=" + bowRefines
			+ " keyMatches=" + key + " equipsReadsKey=" + read);
		helper.assertTrue(swordMends && bowMends, "a withering arm does not mend with a netherite ingot alone");
		helper.assertTrue(swordRefines && bowRefines, "the equips refine does not take a withering arm as netherite grade");
		helper.assertTrue(key && read, "the refine key this mod copies is not the key the equips mod reads");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:arms_wither", maxTicks = 40)
	public void everyHitOfAWitheringSwordOrBowAppliesWitherAndNoOtherNetheriteArmDoes(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Zombie swordsman = wielder(end, SPOT.add(2.0, 0.0, 0.0), new ItemStack(WitheringArms.sword()));
		Zombie archer = wielder(end, SPOT.add(-2.0, 0.0, 0.0), new ItemStack(WitheringArms.bow()));
		Zombie plain = wielder(end, SPOT.add(0.0, 0.0, 2.0), new ItemStack(Items.NETHERITE_SWORD));
		Zombie plainArcher = wielder(end, SPOT.add(0.0, 0.0, -2.0), new ItemStack(Items.BOW));
		Zombie bySword = target(end, SPOT.add(4.0, 0.0, 0.0));
		Zombie byBow = target(end, SPOT.add(-4.0, 0.0, 0.0));
		Zombie byPlain = target(end, SPOT.add(0.0, 0.0, 4.0));
		Zombie byPlainBow = target(end, SPOT.add(0.0, 0.0, -4.0));
		// Other mods shorten harmful effects by the stats of the victim, so each victim is its own yardstick.
		int expectedSword = SovereignGameTest.lengthOn(bySword, WitheringArms.WITHER_TICKS, WitheringArms.WITHER_AMPLIFIER);
		int expectedBow = SovereignGameTest.lengthOn(byBow, WitheringArms.WITHER_TICKS, WitheringArms.WITHER_AMPLIFIER);
		bySword.hurtServer(end, end.damageSources().mobAttack(swordsman), 1.0F);
		byBow.hurtServer(end, arrowOf(end, archer, archer.getMainHandItem()), 1.0F);
		byPlain.hurtServer(end, end.damageSources().mobAttack(plain), 1.0F);
		byPlainBow.hurtServer(end, arrowOf(end, plainArcher, plainArcher.getMainHandItem()), 1.0F);
		MobEffectInstance sword = bySword.getEffect(MobEffects.WITHER);
		MobEffectInstance bow = byBow.getEffect(MobEffects.WITHER);
		EndTestSupport.result("armsWither", "sword=" + (sword == null ? "none" : sword.getAmplifier() + "/" + sword.getDuration()) + " bow="
			+ (bow == null ? "none" : bow.getAmplifier() + "/" + bow.getDuration()) + " expected=" + expectedSword + "/" + expectedBow + " plainSword=" + byPlain.hasEffect(MobEffects.WITHER)
			+ " plainBow=" + byPlainBow.hasEffect(MobEffects.WITHER));
		int swordLength = sword == null ? -1 : sword.getDuration();
		int bowLength = bow == null ? -1 : bow.getDuration();
		int swordAmplifier = sword == null ? -1 : sword.getAmplifier();
		int bowAmplifier = bow == null ? -1 : bow.getAmplifier();
		boolean plainWithered = byPlain.hasEffect(MobEffects.WITHER) || byPlainBow.hasEffect(MobEffects.WITHER);
		EndTestSupport.fresh(helper);
		helper.assertTrue(swordAmplifier == WitheringArms.WITHER_AMPLIFIER && swordLength == expectedSword,
			"a withering sword hit left amplifier " + swordAmplifier + " for " + swordLength + " ticks, expected " + expectedSword);
		helper.assertTrue(bowAmplifier == WitheringArms.WITHER_AMPLIFIER && bowLength == expectedBow,
			"a withering bow hit left amplifier " + bowAmplifier + " for " + bowLength + " ticks, expected " + expectedBow);
		helper.assertTrue(!plainWithered, "a plain netherite sword or bow withered its target");
		helper.succeed();
	}

	/** Hits per measurement. Other mods roll into every blow, so a mean over many blows by one wielder shows the direction and no more. */
	private static final int SAMPLES = 300;

	private static double meanLoss(final ServerLevel end, final Zombie wielder, final boolean shot) {
		double total = 0.0;
		for (int i = 0; i < SAMPLES; i++) {
			total += lost(end, shot ? arrowOf(end, wielder, wielder.getMainHandItem()) : end.damageSources().mobAttack(wielder));
		}
		return total / SAMPLES;
	}

	/** The one blow an arm raised in the last call: the record it left, or null when it left none. */
	private static WitheringArms.Scaled lastRaised(final int before) {
		List<WitheringArms.Scaled> all = WitheringArms.scaled();
		return all.size() == before + 1 ? all.get(all.size() - 1) : null;
	}

	@GameTest(environment = "mod-stella-end-test:arms_refine", maxTicks = 60)
	public void aWitheringArmRaisesEveryBlowFivePercentForEveryThreeRefineLevelsAndAPlainArmRaisesNone(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int[] levels = {0, 1, 2, 3, 5, 6, 9, 15};
		List<String> bad = new ArrayList<>();
		Zombie swordsman = wielder(end, SPOT.add(2.0, 0.0, 0.0), ItemStack.EMPTY);
		Zombie archer = wielder(end, SPOT.add(-2.0, 0.0, 0.0), ItemStack.EMPTY);
		for (int level : levels) {
			swordsman.setItemSlot(EquipmentSlot.MAINHAND, refined(new ItemStack(WitheringArms.sword()), level));
			archer.setItemSlot(EquipmentSlot.MAINHAND, refined(new ItemStack(WitheringArms.bow()), level));
			double expected = 1.0 + WitheringArms.REFINE_BONUS * (level / WitheringArms.REFINE_STEP);
			int before = WitheringArms.scaled().size();
			lost(end, end.damageSources().mobAttack(swordsman));
			WitheringArms.Scaled melee = lastRaised(before);
			before = WitheringArms.scaled().size();
			lost(end, arrowOf(end, archer, archer.getMainHandItem()));
			WitheringArms.Scaled shot = lastRaised(before);
			for (WitheringArms.Scaled raised : new WitheringArms.Scaled[] {melee, shot}) {
				if (raised == null || Math.abs(raised.factor() - expected) > 1.0E-9 || Math.abs(raised.after() - raised.before() * (float) expected) > 1.0E-3F * Math.max(1.0F, raised.before())) {
					bad.add("level " + level + " raised " + raised + ", expected a factor of " + expected);
				}
			}
		}
		swordsman.setItemSlot(EquipmentSlot.MAINHAND, refined(new ItemStack(Items.NETHERITE_SWORD), 15));
		int before = WitheringArms.scaled().size();
		lost(end, end.damageSources().mobAttack(swordsman));
		int plainRaised = WitheringArms.scaled().size() - before;
		EndTestSupport.result("armsRefine", "levels=" + levels.length + " plainRefined15Raised=" + plainRaised + " bad=" + bad.size());
		EndTestSupport.fresh(helper);
		helper.assertTrue(bad.isEmpty(), "the bonus off the five percent per three levels: " + bad);
		helper.assertTrue(plainRaised == 0, "a refined plain sword was raised " + plainRaised + " times");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:arms_refine_felt", maxTicks = 80)
	public void aSixLevelWitheringSwordAndBowTakeMoreHealthFromTheSameTargetThanAnUnrefinedOne(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Zombie swordsman = wielder(end, SPOT.add(2.0, 0.0, 0.0), ItemStack.EMPTY);
		Zombie archer = wielder(end, SPOT.add(-2.0, 0.0, 0.0), ItemStack.EMPTY);
		double[] ratios = new double[2];
		for (int shot = 0; shot < 2; shot++) {
			Zombie wielder = shot == 0 ? swordsman : archer;
			net.minecraft.world.item.Item arm = shot == 0 ? WitheringArms.sword() : WitheringArms.bow();
			wielder.setItemSlot(EquipmentSlot.MAINHAND, refined(new ItemStack(arm), 0));
			double base = meanLoss(end, wielder, shot == 1);
			wielder.setItemSlot(EquipmentSlot.MAINHAND, refined(new ItemStack(arm), 6));
			ratios[shot] = meanLoss(end, wielder, shot == 1) / base;
		}
		EndTestSupport.result("armsRefineFelt", "samples=" + SAMPLES + " swordRatio=" + ratios[0] + " bowRatio=" + ratios[1] + " exact=1.10");
		EndTestSupport.fresh(helper);
		helper.assertTrue(ratios[0] > 1.02 && ratios[1] > 1.02, "a level 6 arm took only " + ratios[0] + " and " + ratios[1] + " times the health of a level 0 arm");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:arms_bonus_steps", maxTicks = 20)
	public void theRefineBonusIsFivePercentPerFullThreeLevelsFromZeroToFifteenAndOneForAnythingElse(final GameTestHelper helper) {
		double[] expected = {1.00, 1.00, 1.00, 1.05, 1.05, 1.05, 1.10, 1.10, 1.10, 1.15, 1.15, 1.15, 1.20, 1.20, 1.20, 1.25};
		List<String> bad = new ArrayList<>();
		for (int level = 0; level < expected.length; level++) {
			double sword = WitheringArms.bonus(refined(new ItemStack(WitheringArms.sword()), level));
			double bow = WitheringArms.bonus(refined(new ItemStack(WitheringArms.bow()), level));
			double plain = WitheringArms.bonus(refined(new ItemStack(Items.NETHERITE_SWORD), level));
			if (Math.abs(sword - expected[level]) > 1.0E-9 || Math.abs(bow - expected[level]) > 1.0E-9 || plain != 1.0) {
				bad.add("level " + level + " sword " + sword + " bow " + bow + " plain " + plain + " expected " + expected[level]);
			}
		}
		EndTestSupport.result("armsBonusSteps", "levels=" + expected.length + " bad=" + bad.size());
		helper.assertTrue(bad.isEmpty(), "bonus steps off: " + bad);
		helper.assertTrue(WitheringArms.REFINE_BONUS == 0.05 && WitheringArms.REFINE_STEP == 3, "a refine constant departs from the spec");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:arms_drop_rate", maxTicks = 20)
	public void theDropChanceIsOnePercentAndASeededRunOfRollsLandsOnIt(final GameTestHelper helper) {
		int rolls = 200000;
		RandomSource random = RandomSource.create(20261017L);
		int wins = 0;
		for (int i = 0; i < rolls; i++) {
			wins += WitheringArms.roll(random) ? 1 : 0;
		}
		double expected = rolls * WitheringArms.DROP_CHANCE;
		double spread = Math.sqrt(rolls * WitheringArms.DROP_CHANCE * (1.0 - WitheringArms.DROP_CHANCE));
		EndTestSupport.result("armsDropRate", "chance=" + WitheringArms.DROP_CHANCE + " rolls=" + rolls + " wins=" + wins + " expected=" + expected + " sigma=" + spread);
		helper.assertTrue(WitheringArms.DROP_CHANCE == 0.01, "the shipped chance is " + WitheringArms.DROP_CHANCE);
		helper.assertTrue(Math.abs(wins - expected) <= 4.0 * spread, wins + " wins in " + rolls + " rolls, expected " + expected + " give or take " + spread);
		helper.succeed();
	}

	/** A seed whose first roll wins or loses, found by scanning, so the test names no number it cannot defend. */
	static long seedWhere(final boolean wins) {
		for (long seed = 0; seed < 100000L; seed++) {
			if (WitheringArms.roll(RandomSource.create(seed)) == wins) {
				return seed;
			}
		}
		throw new AssertionError("no seed " + (wins ? "wins" : "loses") + " its first roll");
	}

	/** Every item entity of the level, by identity. */
	static Set<UUID> itemIds(final ServerLevel end) {
		Set<UUID> ids = new HashSet<>();
		for (Entity entity : EndTestSupport.entities(end)) {
			if (entity instanceof ItemEntity) {
				ids.add(entity.getUUID());
			}
		}
		return ids;
	}

	/** The items that joined the level after a snapshot of identities, named by identity and not by what lies near. */
	static List<ItemStack> droppedSince(final ServerLevel end, final Set<UUID> before) {
		List<ItemStack> found = new ArrayList<>();
		for (Entity entity : EndTestSupport.entities(end)) {
			if (entity instanceof ItemEntity item && !before.contains(item.getUUID())) {
				found.add(item.getItem().copy());
			}
		}
		return found;
	}

	@GameTest(environment = "mod-stella-end-test:arms_drop_wired", maxTicks = 40)
	public void aCastleWitherSkeletonDropsTheSwordAndAnArcherTheBowOnAWinningRollAndNothingOnALosingOneOrOutsideTheCastle(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		long winning = seedWhere(true);
		long losing = seedWhere(false);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		int[] cell = plan.spawnCells().get(7);
		net.minecraft.core.BlockPos at = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(floor), cell[1]);
		List<String> got = new ArrayList<>();
		try {
			for (EndMobs.Kind kind : List.of(EndMobs.Kind.WITHER_SKELETON, EndMobs.Kind.ARCHER)) {
				for (boolean win : new boolean[] {true, false}) {
					Mob mob = EndMobs.spawn(end, floor, kind, at, false);
					helper.assertTrue(mob != null, kind + " was refused");
					WitheringArms.rollWith(RandomSource.create(win ? winning : losing));
					Set<UUID> before = itemIds(end);
					mob.kill(end);
					List<ItemStack> dropped = droppedSince(end, before);
					got.add(kind + (win ? " win " : " lose ") + dropped.stream().map(stack -> stack.getItem().toString()).toList());
					boolean sword = dropped.stream().anyMatch(stack -> stack.is(WitheringArms.sword()));
					boolean bow = dropped.stream().anyMatch(stack -> stack.is(WitheringArms.bow()));
					boolean wantSword = win && kind == EndMobs.Kind.WITHER_SKELETON;
					boolean wantBow = win && kind == EndMobs.Kind.ARCHER;
					helper.assertTrue(sword == wantSword && bow == wantBow, kind + (win ? " on a winning roll" : " on a losing roll") + " dropped " + dropped);
				}
			}
			WitherSkeleton stray = EntityTypes.WITHER_SKELETON.create(end, EntitySpawnReason.SPAWNER);
			LevelBridge.claim(stray);
			stray.setPersistenceRequired();
			stray.snapTo(SPOT.x, SPOT.y, SPOT.z, 0.0F, 0.0F);
			end.addFreshEntity(stray);
			Skeleton strayArcher = EntityTypes.SKELETON.create(end, EntitySpawnReason.SPAWNER);
			LevelBridge.claim(strayArcher);
			strayArcher.setPersistenceRequired();
			strayArcher.snapTo(SPOT.x + 3.0, SPOT.y, SPOT.z, 0.0F, 0.0F);
			end.addFreshEntity(strayArcher);
			Set<UUID> before = itemIds(end);
			WitheringArms.rollWith(RandomSource.create(winning));
			stray.kill(end);
			WitheringArms.rollWith(RandomSource.create(winning));
			strayArcher.kill(end);
			List<ItemStack> strayDrops = droppedSince(end, before);
			boolean strayArms = strayDrops.stream().anyMatch(WitheringArms::is);
			EndTestSupport.result("armsDropWired", "cases=" + got + " strayDrops=" + strayDrops.size() + " strayArms=" + strayArms);
			helper.assertTrue(!strayArms, "a wither skeleton or skeleton outside the castle dropped a withering arm: " + strayDrops);
		} finally {
			WitheringArms.rollWith(null);
			EndTestSupport.fresh(helper);
		}
		helper.succeed();
	}
}
