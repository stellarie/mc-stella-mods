package com.stella.rpg;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The dice multiplier is (1 + 0.02 x level) x material x refine. It multiplies dice and nothing else. */
public final class DiceMultiplierGameTest {
	private static final float TOLERANCE = 0.001F;
	private static final float HIT = 4.0F;

	private static final class Stub implements MobsBridge.Facts {
		private final Map<Mob, Integer> threats = new IdentityHashMap<>();

		@Override
		public int threat(final Mob mob) {
			return this.threats.getOrDefault(mob, 0);
		}

		@Override
		public String role(final Mob mob) {
			return MobStats.MELEE;
		}
	}

	@GameTest
	public void refineMilestones(final GameTestHelper helper) {
		int[] levels = {0, 9, 10, 11, 12, 14, 15};
		double[] expected = {1.0, 1.0, 1.15, 1.15, 2.30, 2.30, 3.45};
		for (int i = 0; i < levels.length; i++) {
			double actual = StatTables.DEFAULT.refineMultiplier(levels[i]);
			helper.assertTrue(Math.abs(actual - expected[i]) < 1e-9, "refine +" + levels[i] + " gave " + actual + " expected " + expected[i]);
			ItemStack sword = WeaponDiceGameTest.refined(Items.IRON_SWORD, levels[i]);
			ServerPlayer player = RpgTestSupport.player(helper);
			player.setItemSlot(EquipmentSlot.MAINHAND, sword);
			double total = PhysicalDice.multiplier(player, sword);
			helper.assertTrue(Math.abs(total - 1.2 * expected[i]) < 1e-9, "the multiplier at +" + levels[i] + " was " + total);
		}
		double[] material = {1.0, 1.1, 1.1, 1.3, 1.2, 1.4, 1.6, 2.0};
		String[] names = {"wood", "stone", "copper", "gold", "iron", "diamond", "netherite", "void"};
		for (int i = 0; i < names.length; i++) {
			helper.assertTrue(Math.abs(StatTables.DEFAULT.materialMultiplier(names[i]) - material[i]) < 1e-9, "material " + names[i]);
		}
		double mace = PhysicalDice.materialMultiplier(new ItemStack(Items.MACE));
		double trident = PhysicalDice.materialMultiplier(new ItemStack(Items.TRIDENT));
		double bow = PhysicalDice.materialMultiplier(new ItemStack(Items.BOW));
		double crossbow = PhysicalDice.materialMultiplier(new ItemStack(Items.CROSSBOW));
		RpgTestSupport.result("refineMilestones", "mace=" + mace + " trident=" + trident + " bow=" + bow + " crossbow=" + crossbow);
		helper.assertTrue(mace == 1.4 && trident == 1.4 && bow == 1.2 && crossbow == 1.2, "heavy and ranged materials");
		helper.assertTrue(PhysicalDice.materialMultiplier(new ItemStack(Items.IRON_PICKAXE)) == 1.0
			&& PhysicalDice.materialMultiplier(new ItemStack(Items.IRON_CHESTPLATE)) == 1.0 && PhysicalDice.materialMultiplier(ItemStack.EMPTY) == 1.0,
			"a non weapon multiplied the dice");
		helper.succeed();
	}

	@GameTest
	public void playerMultiplier(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		DamageSource melee = level.damageSources().playerAttack(player);
		StringBuilder log = new StringBuilder();
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		try {
			int[] levels = {0, 10, 50};
			for (int experience : levels) {
				player.experienceLevel = experience;
				// Bare hand: STR 0 rolls 1d4, no weapon die, material 1.
				player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				float bare = RpgCombat.scaleOutgoing(victim, melee, HIT);
				float status = (float) RpgCombat.statusAtk(StatBonuses.effective(player, Stat.STR));
				helper.assertTrue(near(bare, HIT + 4 * (1.0F + 0.02F * experience) + status), "bare hand at level " + experience + " hit " + bare);
				// Netherite sword +15: 1d4 + (1 + 3)d8 dice, x1.6 material, x3.45 refine.
				player.setItemSlot(EquipmentSlot.MAINHAND, WeaponDiceGameTest.refined(Items.NETHERITE_SWORD, 15));
				float sword = RpgCombat.scaleOutgoing(victim, melee, HIT);
				float expected = HIT + (4 + 4 * 8) * (1.0F + 0.02F * experience) * 1.6F * 3.45F + status;
				log.append("L").append(experience).append(':').append(bare).append('/').append(sword).append(' ');
				helper.assertTrue(near(sword, expected), "netherite +15 at level " + experience + " hit " + sword + " expected " + expected);
			}
			player.experienceLevel = 50;
			player.setItemSlot(EquipmentSlot.MAINHAND, WeaponDiceGameTest.refined(Items.IRON_SWORD, 12));
			RpgCombat.setDiceRoller((attacker, random, dice) -> 0);
			float base = RpgCombat.scaleOutgoing(victim, melee, HIT);
			float levelStatus = (float) RpgCombat.statusAtk(StatBonuses.effective(player, Stat.STR));
			helper.assertTrue(near(base, HIT + levelStatus), "the multiplier reached the base damage: " + base);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		RpgTestSupport.result("playerMultiplier", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void mobMultiplierAndShare(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			Zombie attacker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
			RpgTestSupport.bare(attacker);
			attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			stub.threats.put(attacker, 50);
			ServerPlayer victim = RpgTestSupport.player(helper);
			RpgCombat.setRoller((mob, random, chance) -> false);
			RpgCombat.setDiceRoller((mob, random, dice) -> dice.max());
			float hit;
			try {
				hit = RpgCombat.scaleOutgoing(victim, level.damageSources().mobAttack(attacker), HIT);
			} finally {
				RpgCombat.setRoller(null);
				RpgCombat.setDiceRoller(null);
			}
			int str = StatBonuses.effective(attacker, Stat.STR);
			float dice = RpgDice.of(str).max() + 4;
			// The level 0 victim takes 10 percent more for each 10 threat levels: 1.5 at threat 50.
			float expected = 1.5F * (HIT + dice * (1.0F + 0.02F * 50) * 1.2F * (float) RpgConfig.DEFAULT_MOB_DICE_SHARE + (float) RpgCombat.statusAtk(str));
			RpgTestSupport.result("mobMultiplierAndShare", "hit=" + hit + " expected=" + expected + " threat=50");
			helper.assertTrue(near(hit, expected), "a threat 50 mob with an iron sword hit " + hit + " expected " + expected);
		} finally {
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < TOLERANCE * Math.max(1.0F, Math.abs(expected));
	}
}
