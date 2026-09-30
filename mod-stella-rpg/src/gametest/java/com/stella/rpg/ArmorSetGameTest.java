package com.stella.rpg;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ArmorSetGameTest {
	private static final float TOLERANCE = 0.001F;
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final Item[][] SETS = {
		{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
		{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
		{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
		{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
		{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
		{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}
	};
	private static final String[] NAMES = {"leather", "chainmail", "iron", "gold", "diamond", "netherite"};
	// Columns follow Stat order: STR, AGI, VIT, DEX, INT, LUK.
	private static final int[][] TOTALS = {
		{0, 7, 0, 2, 0, 0},
		{0, 2, 0, 7, 0, 0},
		{2, 0, 7, 0, 0, 0},
		{0, 0, 0, 0, 2, 7},
		{4, 0, 12, 0, 0, 0},
		{14, 0, 14, 0, 0, 2}
	};
	private static final double[] FLATS = {0.40, 0.80, 1.00, 0.40, 1.60, 2.00};

	@GameTest
	public void perPiece(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.bare(player);
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		int vit = StatBonuses.bonus(player, Stat.VIT);
		float flat = DefenseBonuses.flat(player);
		RpgTestSupport.result("perPiece", "vit=" + vit + " flat=" + flat);
		helper.assertTrue(vit == 1, "vit=" + vit);
		helper.assertTrue(Math.abs(flat - 0.25F) < TOLERANCE, "flat=" + flat);
		helper.succeed();
	}

	@GameTest
	public void fullSet(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		wear(player, SETS[2]);
		int vit = StatBonuses.bonus(player, Stat.VIT);
		int str = StatBonuses.bonus(player, Stat.STR);
		float flat = DefenseBonuses.flat(player);
		RpgTestSupport.result("fullSet", "vit=" + vit + " str=" + str + " flat=" + flat);
		helper.assertTrue(vit == 7, "vit=" + vit);
		helper.assertTrue(str == 2, "str=" + str);
		helper.assertTrue(Math.abs(flat - 1.0F) < TOLERANCE, "flat=" + flat);
		helper.succeed();
	}

	@GameTest
	public void mixedIsNoSet(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		wear(player, SETS[2]);
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		int vit = StatBonuses.bonus(player, Stat.VIT);
		int str = StatBonuses.bonus(player, Stat.STR);
		float flat = DefenseBonuses.flat(player);
		RpgTestSupport.result("mixedIsNoSet", "vit=" + vit + " str=" + str + " flat=" + flat);
		helper.assertTrue(vit == 3 + 2, "vit=" + vit);
		helper.assertTrue(str == 0, "set bonus leaked: str=" + str);
		helper.assertTrue(Math.abs(flat - (3 * 0.25F + 0.40F)) < TOLERANCE, "flat=" + flat);
		helper.succeed();
	}

	@GameTest
	public void table(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		for (int row = 0; row < SETS.length; row++) {
			wear(player, SETS[row]);
			for (Stat stat : Stat.values()) {
				int bonus = StatBonuses.bonus(player, stat);
				helper.assertTrue(bonus == TOTALS[row][stat.ordinal()],
					NAMES[row] + " " + stat + " bonus=" + bonus + " expected " + TOTALS[row][stat.ordinal()]);
			}
			float flat = DefenseBonuses.flat(player);
			RpgTestSupport.result("table", NAMES[row] + " flat=" + flat);
			helper.assertTrue(Math.abs(flat - FLATS[row]) < TOLERANCE, NAMES[row] + " flat=" + flat);
		}
		helper.succeed();
	}

	@GameTest
	public void mobWearsSet(final GameTestHelper helper) {
		MobsBridge.setFacts(new MobsBridge.Facts() {
			@Override
			public int threat(final Mob mob) {
				return 40;
			}

			@Override
			public String role(final Mob mob) {
				return MobStats.MELEE;
			}
		});
		try {
			Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
			wear(zombie, SETS[4]);
			int vit = StatBonuses.bonus(zombie, Stat.VIT);
			int str = StatBonuses.bonus(zombie, Stat.STR);
			int effectiveVit = StatBonuses.effective(zombie, Stat.VIT);
			int effectiveStr = StatBonuses.effective(zombie, Stat.STR);
			RpgTestSupport.result("mobWearsSet", "vit=" + vit + " str=" + str + " effective=" + effectiveVit + "/" + effectiveStr);
			helper.assertTrue(vit == 12 && str == 4, "vit=" + vit + " str=" + str);
			// Melee base at threat 40: VIT 12 x 5 and STR 12 x 5.
			helper.assertTrue(effectiveVit == 60 + 12, "effective vit=" + effectiveVit);
			helper.assertTrue(effectiveStr == 60 + 4, "effective str=" + effectiveStr);
		} finally {
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	public void flatReducesHit(final GameTestHelper helper) {
		Difficulty before = helper.getLevel().getDifficulty();
		float withoutFlat20;
		float withFlat20;
		float withoutFlat1;
		float withFlat1;
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			RpgConfigStore.set(RpgConfigStore.parse("{\"armor_sets\":{\"netherite\":{\"flat_per_piece\":0}}}", message -> { }));
			withoutFlat20 = netheriteHitLoss(helper, 20.0F);
			withoutFlat1 = netheriteHitLoss(helper, 1.0F);
			RpgConfigStore.set(null);
			withFlat20 = netheriteHitLoss(helper, 20.0F);
			withFlat1 = netheriteHitLoss(helper, 1.0F);
		} finally {
			RpgConfigStore.set(null);
			helper.getLevel().getServer().setDifficulty(before, true);
		}
		RpgTestSupport.result("flatReducesHit", "d20 " + withoutFlat20 + "->" + withFlat20 + " d1 " + withoutFlat1 + "->" + withFlat1);
		float flatLoss = 2.0F * RpgScale.kf();
		helper.assertTrue(withoutFlat20 > flatLoss, "case proves nothing, loss without flat=" + withoutFlat20);
		helper.assertTrue(Math.abs(withFlat20 - (withoutFlat20 - flatLoss)) < TOLERANCE * RpgScale.kf(),
			"loss=" + withFlat20 + " expected " + (withoutFlat20 - flatLoss));
		helper.assertTrue(withoutFlat1 > 0.0F && withoutFlat1 < flatLoss, "clamp case proves nothing, loss=" + withoutFlat1);
		helper.assertTrue(withFlat1 == 0.0F, "loss=" + withFlat1 + " expected 0");
		helper.succeed();
	}

	@GameTest
	public void configOverride(final GameTestHelper helper) {
		try {
			RpgConfigStore.set(RpgConfigStore.parse("{\"armor_sets\":{\"iron\":{\"per_piece\":{\"vit\":3}}}}", message -> { }));
			ServerPlayer player = RpgTestSupport.player(helper);
			RpgTestSupport.bare(player);
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			int vit = StatBonuses.bonus(player, Stat.VIT);
			float flat = DefenseBonuses.flat(player);
			RpgTestSupport.result("configOverride", "vit=" + vit + " flat=" + flat);
			helper.assertTrue(vit == 3, "vit=" + vit);
			helper.assertTrue(Math.abs(flat - 0.25F) < TOLERANCE, "the untouched key changed: flat=" + flat);
		} finally {
			RpgConfigStore.set(null);
		}
		helper.succeed();
	}

	@GameTest
	public void badConfigFallsBack(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig config = RpgConfigStore.parse(
			"{\"armor_sets\":{\"iron\":{\"items\":[\"minecraft:iron_helmet\",\"minecraft:not_an_item\"]}}}", errors::add);
		List<Identifier> items = config.armorSets().get("iron").items();
		RpgTestSupport.result("badConfigFallsBack", "errors=" + errors + " items=" + items);
		helper.assertTrue(errors.size() == 1 && errors.get(0).contains("not_an_item"), "errors=" + errors);
		helper.assertTrue(items.equals(List.of(Identifier.fromNamespaceAndPath("minecraft", "iron_helmet"))), "items=" + items);
		helper.assertTrue(config.armorSets().get("diamond").equals(RpgConfig.DEFAULT_ARMOR_SETS.get("diamond")), "other set changed");
		helper.succeed();
	}

	@GameTest
	public void badValueFallsBack(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig config = RpgConfigStore.parse(
			"{\"armor_sets\":{\"iron\":{\"per_piece\":{\"vit\":1.5},\"flat_per_piece\":-1,\"set_bonus\":{\"str\":5}}}}", errors::add);
		RpgConfig.ArmorSet iron = config.armorSets().get("iron");
		Map<Stat, Integer> expected = new EnumMap<>(Stat.class);
		expected.put(Stat.STR, 5);
		RpgTestSupport.result("badValueFallsBack", "errors=" + errors);
		helper.assertTrue(errors.size() == 2, "errors=" + errors);
		helper.assertTrue(iron.perPiece().equals(RpgConfig.DEFAULT_ARMOR_SETS.get("iron").perPiece()), "per_piece=" + iron.perPiece());
		helper.assertTrue(iron.flatPerPiece() == 0.25, "flat=" + iron.flatPerPiece());
		helper.assertTrue(iron.setBonus().equals(expected), "set_bonus=" + iron.setBonus());
		helper.succeed();
	}

	private static float netheriteHitLoss(final GameTestHelper helper, final float damage) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		wear(player, SETS[5]);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		player.setHealth(player.getMaxHealth());
		Zombie attacker = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
		RpgTestSupport.bare(attacker);
		attacker.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		float before = player.getHealth();
		try {
			RpgCombat.setRoller((roller, random, chance) -> false);
			RpgCombat.setDiceRoller((roller, random, dice) -> 0);
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), damage);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		return before - player.getHealth();
	}

	private static void wear(final LivingEntity entity, final Item[] pieces) {
		for (int i = 0; i < SLOTS.length; i++) {
			entity.setItemSlot(SLOTS[i], new ItemStack(pieces[i]));
		}
	}
}
