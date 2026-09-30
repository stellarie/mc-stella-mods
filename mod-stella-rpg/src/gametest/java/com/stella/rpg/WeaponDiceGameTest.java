package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** The die a weapon adds to the STR or DEX dice, by material and type, and the refine extras. */
public final class WeaponDiceGameTest {
	private static final float TOLERANCE = 0.0005F;

	@GameTest
	public void table(final GameTestHelper helper) {
		// Columns: wood, stone, copper, gold, iron, diamond, netherite. The plan table, not the shipped constants.
		String[] sword = {"1d2", "1d3", "1d3", "1d6", "1d4", "1d6", "1d8"};
		String[] axe = {"1d3", "1d4", "1d4", "1d8", "1d6", "1d8", "2d6"};
		Item[] swords = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.GOLDEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
		Item[] axes = {Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.GOLDEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
		Item[] spears = {Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.COPPER_SPEAR, Items.GOLDEN_SPEAR, Items.IRON_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR};
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < 7; i++) {
			check(helper, swords[i], false, sword[i]);
			check(helper, axes[i], false, axe[i]);
			check(helper, spears[i], false, sword[i]);
			log.append(sword[i]).append('/').append(axe[i]).append(' ');
		}
		check(helper, Items.MACE, false, "1d10");
		check(helper, Items.TRIDENT, false, "1d6");
		check(helper, Items.BOW, true, "1d4");
		check(helper, Items.CROSSBOW, true, "1d6");
		helper.assertTrue(StatTables.DEFAULT.dice("sword", "void").equals(StatTables.parseDice("2d6"))
			&& StatTables.DEFAULT.dice("axe", "void").equals(StatTables.parseDice("2d8"))
			&& StatTables.DEFAULT.dice("spear", "void").equals(StatTables.parseDice("2d6")), "the void dice");
		helper.assertTrue(PhysicalDice.weaponDice(new ItemStack(Items.IRON_SWORD), true) == null, "a sword gave a projectile die");
		helper.assertTrue(PhysicalDice.weaponDice(new ItemStack(Items.BOW), false) == null, "a bow gave a melee die");
		helper.assertTrue(PhysicalDice.weaponDice(new ItemStack(Items.IRON_PICKAXE), false) == null, "a pickaxe gave a die");
		helper.assertTrue(PhysicalDice.weaponDice(ItemStack.EMPTY, false) == null, "an empty hand gave a die");
		RpgTestSupport.result("table", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void dieReachesTheHit(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		DamageSource melee = level.damageSources().playerAttack(player);
		float hit = 4.0F;
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		try {
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			// STR 0 rolls 1d4, the iron sword adds 1d4, and the iron material multiplies both by 1.2.
			float sword = RpgCombat.scaleOutgoing(victim, melee, hit);
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
			float axe = RpgCombat.scaleOutgoing(victim, melee, hit);
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
			float pickaxe = RpgCombat.scaleOutgoing(victim, melee, hit);

			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			Arrow fromBow = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
			level.addFreshEntity(fromBow);
			float shot = RpgCombat.scaleOutgoing(victim, level.damageSources().arrow(fromBow, player), hit);
			Arrow loose = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			level.addFreshEntity(loose);
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			float swordShot = RpgCombat.scaleOutgoing(victim, level.damageSources().arrow(loose, player), hit);
			fromBow.discard();
			loose.discard();

			RpgTestSupport.result("dieReachesTheHit", "sword=" + sword + " axe=" + axe + " pickaxe=" + pickaxe + " shot=" + shot + " swordShot=" + swordShot);
			helper.assertTrue(near(sword, hit + (4 + 4) * 1.2F), "iron sword " + sword);
			helper.assertTrue(near(axe, hit + (4 + 12) * 1.6F), "netherite axe " + axe);
			helper.assertTrue(near(pickaxe, hit + 4), "a pickaxe added a die or a multiplier: " + pickaxe);
			helper.assertTrue(near(shot, hit + (4 + 4) * 1.2F), "an arrow from a bow " + shot);
			// The arrow names no weapon, so the main hand gives the material multiplier but no projectile die.
			helper.assertTrue(near(swordShot, hit + 4 * 1.2F), "an arrow held with a sword used the sword die: " + swordShot);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		helper.succeed();
	}

	@GameTest
	public void refineDice(final GameTestHelper helper) {
		int[] levels = {0, 9, 10, 11, 12, 14, 15};
		int[] extra = {0, 0, 1, 1, 2, 2, 3};
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < levels.length; i++) {
			ItemStack iron = refined(Items.IRON_SWORD, levels[i]);
			ItemStack netherite = refined(Items.NETHERITE_AXE, levels[i]);
			RpgDice.Dice ironDice = PhysicalDice.weaponDice(iron, false);
			RpgDice.Dice axeDice = PhysicalDice.weaponDice(netherite, false);
			log.append('+').append(levels[i]).append('=').append(ironDice).append('/').append(axeDice).append(' ');
			helper.assertTrue(ironDice.count() == 1 + extra[i] && ironDice.sides() == 4, "iron sword at +" + levels[i] + " rolled " + ironDice);
			helper.assertTrue(axeDice.count() == 2 + extra[i] && axeDice.sides() == 6, "netherite axe at +" + levels[i] + " rolled " + axeDice);
		}
		RpgDice.Dice bow = PhysicalDice.weaponDice(refined(Items.BOW, 15), true);
		helper.assertTrue(bow.count() == 4 && bow.sides() == 4, "a +15 bow rolled " + bow);
		RpgTestSupport.result("refineDice", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void configParses(final GameTestHelper helper) {
		java.util.List<String> errors = new java.util.ArrayList<>();
		RpgConfig config = RpgConfigStore.parse("{\"weapon_dice\":{\"sword\":{\"iron\":\"3d5\"},\"mace\":\"2d2\",\"axe\":\"banana\"},"
			+ "\"material_dice_multiplier\":{\"iron\":2.0,\"gold\":-1},\"dice_multiplier_per_level\":0.1,"
			+ "\"refine_dice_multiplier\":{\"5\":1.5},\"refine_extra_dice\":{\"5\":7},"
			+ "\"enchant_stats\":{\"sharpness\":{\"str\":9},\"bad\":{\"str\":\"x\"}},"
			+ "\"effect_stats\":{\"speed\":{\"agi\":3,\"per_level\":false}}}", errors::add);
		StatTables tables = config.tables();
		RpgTestSupport.result("configParses", "errors=" + errors.size() + " ironSword=" + tables.dice("sword", "iron"));
		helper.assertTrue(errors.size() == 3, "errors=" + errors);
		helper.assertTrue(tables.dice("sword", "iron").equals(new RpgDice.Dice(3, 5)) && tables.dice("sword", "wood").equals(new RpgDice.Dice(1, 2)),
			"the sword row: iron " + tables.dice("sword", "iron") + " wood " + tables.dice("sword", "wood"));
		helper.assertTrue(tables.dice("mace", "iron").equals(new RpgDice.Dice(2, 2)), "mace " + tables.dice("mace", "iron"));
		helper.assertTrue(tables.dice("axe", "iron").equals(StatTables.DEFAULT.dice("axe", "iron")), "a bad die replaced the default");
		helper.assertTrue(tables.materialMultiplier("iron") == 2.0 && tables.materialMultiplier("gold") == 1.3, "material multipliers");
		helper.assertTrue(tables.dicePerLevel() == 0.1 && tables.refineMultiplier(5) == 1.5 && tables.refineMultiplier(4) == 1.0 && tables.refineDice(9) == 7,
			"level rate and refine milestones");
		helper.assertTrue(tables.enchantStats().get("sharpness").get(Stat.STR) == 9 && !tables.enchantStats().containsKey("bad"), "enchant stats");
		helper.assertTrue(tables.effectStats().get("speed").stats().get(Stat.AGI) == 3 && !tables.effectStats().get("speed").perLevel(), "effect stats");
		helper.assertTrue(RpgConfig.defaults().tables().equals(StatTables.DEFAULT), "the default tables changed");
		helper.succeed();
	}

	static ItemStack refined(final Item item, final int level) {
		ItemStack stack = new ItemStack(item);
		if (level > 0) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("stella_refine", level));
		}
		return stack;
	}

	private static void check(final GameTestHelper helper, final Item item, final boolean ranged, final String expected) {
		RpgDice.Dice dice = PhysicalDice.weaponDice(new ItemStack(item), ranged);
		helper.assertTrue(dice != null && dice.toString().equals(expected), item + " rolled " + dice + " expected " + expected);
	}

	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < TOLERANCE;
	}
}
