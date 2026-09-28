package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Runes: dropped by tower mobs, set at the anvil, and felt in every blow. */
public class TowerRuneGameTest {
	/** A floor-1000 rune: full strength, the spec's own table row. */
	private static final int TOP = 1_000;
	private static final int LOW = 5;
	/** The drop case's own floor, so its pinned chance touches no other case. */
	private static final int DROP_FLOOR = 7;

	@GameTest
	public void runeStrengthFollowsTheSpec(final GameTestHelper helper) {
		double p100 = TowerRunes.power(100);
		double p1000 = TowerRunes.power(TOP);
		double p5000 = TowerRunes.power(5_000);
		double weapon = TowerRunes.weaponMultiplier(TOP);
		double armor = Math.pow(TowerRunes.armorDivisor(TOP), TowerRunes.ARMOR_PIECES);
		double weaponOne = TowerRunes.weaponMultiplier(1);
		TowerTestSupport.result("runeStrength", "p100=" + p100 + " p1000=" + p1000 + " p5000=" + p5000 + " weapon=" + weapon + " armor=" + armor + " weaponOne=" + weaponOne);
		// The spec's table: x63 and /14.8 at floor 1000, power 100 at 100 and 400 from 1000 on.
		helper.assertTrue(Math.abs(p100 - 100.0) < 1.0E-9 && Math.abs(p1000 - 400.0) < 1.0E-9 && p5000 == p1000, "power is " + p100 + ", " + p1000 + ", " + p5000);
		helper.assertTrue(Math.abs(weapon - Math.pow(TowerRunes.WEAPON_MAX, TowerRunes.TIER_EXPONENT)) < 1.0E-6 && Math.round(weapon) == 63,
			"a floor 1000 weapon rune multiplies by " + weapon);
		helper.assertTrue(Math.abs(armor - Math.pow(TowerRunes.ARMOR_MAX, TowerRunes.TIER_EXPONENT)) < 1.0E-6 && Math.abs(armor - 14.8) < 0.05,
			"four floor 1000 armor runes divide by " + armor);
		// The table gives one decimal: x1.0 at floor 1.
		helper.assertTrue(Math.round(weaponOne * 10.0) == 10, "a floor 1 rune multiplies by " + weaponOne);
		helper.succeed();
	}

	/** A tower mob a player slays may leave a rune of its floor; the chance is pinned certain for the case. */
	@GameTest
	public void aSlainTowerMobMayLeaveARuneOfItsFloor(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, DROP_FLOOR, TowerElement.FIRE);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		zombie.snapTo(at);
		level.addFreshEntity(zombie);
		List<ItemEntity> runes;
		try {
			TowerRunes.pinDropChance(DROP_FLOOR, 1.0);
			zombie.hurtServer(level, player.damageSources().playerAttack(player), 1.0E30F);
			runes = level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0), item -> item.getItem().is(TowerItems.RUNE));
		} finally {
			TowerRunes.pinDropChance(DROP_FLOOR, null);
		}
		RuneData rune = runes.isEmpty() ? null : runes.get(0).getItem().get(TowerItems.RUNE_DATA);
		TowerTestSupport.result("runeDrop", "dead=" + !zombie.isAlive() + " runes=" + runes.size() + " rune=" + rune);
		runes.forEach(ItemEntity::discard);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(!zombie.isAlive(), "the zombie did not die, so the case proves nothing");
		helper.assertTrue(runes.size() == 1 && rune != null && rune.floor() == DROP_FLOOR, "a slain floor " + DROP_FLOOR + " mob left " + runes.size() + " runes: " + rune);
		helper.succeed();
	}

	@GameTest
	public void theRuneChanceGrowsWithRank(final GameTestHelper helper) {
		double mob = TowerRunes.dropChance(TowerRank.MOB);
		double elite = TowerRunes.dropChance(TowerRank.ELITE);
		double boss = TowerRunes.dropChance(TowerRank.BOSS);
		TowerTestSupport.result("runeChances", "mob=" + mob + " elite=" + elite + " boss=" + boss);
		helper.assertTrue(mob == TowerRunes.MOB_CHANCE && elite == TowerRunes.ELITE_CHANCE && boss == TowerRunes.BOSS_CHANCE && mob < elite && elite < boss,
			"rune chances are mob=" + mob + " elite=" + elite + " boss=" + boss);
		helper.succeed();
	}

	/** At the anvil a rune goes into a weapon for 64 emeralds, which the player must hold. */
	@GameTest
	public void theAnvilSetsARuneForSixtyFourEmeralds(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(TowerRunes.rune(TowerElement.ICE, TOP));
		menu.createResult();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		RuneData set = TowerRunes.runeOf(result);
		boolean poor = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		player.getInventory().add(new ItemStack(Items.EMERALD, TowerRunes.EMERALDS));
		boolean paid = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		ItemStack taken = result.copy();
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, taken);
		int emeralds = player.getInventory().countItem(Items.EMERALD);
		boolean runeUsed = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty();
		TowerTestSupport.result("runeAnvil", "set=" + set + " poor=" + poor + " paid=" + paid + " emeraldsLeft=" + emeralds + " runeUsed=" + runeUsed);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(set != null && set.element() == TowerElement.ICE && set.floor() == TOP, "the anvil made " + set);
		helper.assertTrue(!poor && paid, "the rune could be taken without the emeralds=" + poor + " or not with them=" + !paid);
		helper.assertTrue(emeralds == 0 && runeUsed, "the anvil left " + emeralds + " emeralds and the rune used=" + runeUsed);
		helper.succeed();
	}

	@GameTest
	public void aRuneIsSetOnlyIntoWeaponsAndArmor(final GameTestHelper helper) {
		TowerRunes.Slot sword = TowerRunes.slotOf(new ItemStack(Items.NETHERITE_SWORD));
		TowerRunes.Slot bow = TowerRunes.slotOf(new ItemStack(Items.BOW));
		TowerRunes.Slot chest = TowerRunes.slotOf(new ItemStack(Items.IRON_CHESTPLATE));
		TowerRunes.Slot dirt = TowerRunes.slotOf(new ItemStack(Items.DIRT));
		TowerTestSupport.result("runeSlots", "sword=" + sword + " bow=" + bow + " chest=" + chest + " dirt=" + dirt);
		helper.assertTrue(sword == TowerRunes.Slot.WEAPON && bow == TowerRunes.Slot.WEAPON && chest == TowerRunes.Slot.ARMOR && dirt == null,
			"rune slots: sword=" + sword + " bow=" + bow + " chest=" + chest + " dirt=" + dirt);
		helper.succeed();
	}

	/** A weapon rune multiplies a hit; the added part hits its opposite half again and its own element at half. */
	@GameTest
	public void aWeaponRuneStrikesByElement(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		ItemStack sword = TowerRunes.apply(new ItemStack(Items.DIAMOND_SWORD), new RuneData(TowerElement.FIRE, TOP));
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		float base = 10.0F;
		double m = TowerRunes.weaponMultiplier(TOP);
		double[] got = new double[3];
		double[] want = {base + base * (m - 1.0) * TowerRunes.OPPOSED, base + base * (m - 1.0) * TowerRunes.SAME, base * m};
		TowerElement[] targets = {TowerElement.ICE, TowerElement.FIRE, TowerElement.WIND};
		for (int i = 0; i < targets.length; i++) {
			Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(zombie, LOW, targets[i]);
			double divisor = TowerMobs.stateOf(zombie).healthDivisor() * TowerBuffs.healthFactor(zombie);
			got[i] = TowerMobs.scaleDamage(zombie, player.damageSources().playerAttack(player), base) * divisor;
			zombie.discard();
		}
		TowerTestSupport.result("weaponRune", "opposed=" + got[0] + "/" + want[0] + " same=" + got[1] + "/" + want[1] + " other=" + got[2] + "/" + want[2]);
		TowerTestSupport.leave(helper, player);
		for (int i = 0; i < targets.length; i++) {
			helper.assertTrue(Math.abs(got[i] - want[i]) < want[i] * 1.0E-4, "a fire rune hit a " + targets[i] + " mob for " + got[i] + ", not " + want[i]);
		}
		helper.succeed();
	}

	/** Four armor runes turn a tower mob's blow by the armor tier, and its own element's by more. */
	@GameTest
	public void armorRunesTurnTowerBlows(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		RuneData rune = new RuneData(TowerElement.DARK, TOP);
		player.setItemSlot(EquipmentSlot.HEAD, TowerRunes.apply(new ItemStack(Items.IRON_HELMET), rune));
		player.setItemSlot(EquipmentSlot.CHEST, TowerRunes.apply(new ItemStack(Items.IRON_CHESTPLATE), rune));
		player.setItemSlot(EquipmentSlot.LEGS, TowerRunes.apply(new ItemStack(Items.IRON_LEGGINGS), rune));
		player.setItemSlot(EquipmentSlot.FEET, TowerRunes.apply(new ItemStack(Items.IRON_BOOTS), rune));
		double divisor = Math.pow(TowerRunes.armorDivisor(TOP), TowerRunes.ARMOR_PIECES);
		double own = Math.min(TowerRunes.OWN_ELEMENT_CAP, TowerRunes.ARMOR_PIECES * (TowerRunes.OWN_ELEMENT_BASE + TowerRunes.power(TOP) / 1000.0));
		float base = 3.0F;
		double raw = TowerCurve.damage(base, LOW);
		double[] got = new double[2];
		double[] want = {raw / divisor, raw / divisor * (1.0 - own)};
		TowerElement[] attackers = {TowerElement.WIND, TowerElement.DARK};
		for (int i = 0; i < attackers.length; i++) {
			Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(zombie, LOW, attackers[i]);
			got[i] = TowerMobs.scaleDamage(player, zombie.damageSources().mobAttack(zombie), base);
			zombie.discard();
		}
		TowerTestSupport.result("armorRune", "other=" + got[0] + "/" + want[0] + " own=" + got[1] + "/" + want[1] + " divisor=" + divisor + " ownCut=" + own);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(got[0] - want[0]) < want[0] * 1.0E-4, "four dark armor runes let a wind blow through at " + got[0] + ", not " + want[0]);
		helper.assertTrue(Math.abs(got[1] - want[1]) < want[1] * 1.0E-4, "four dark armor runes let a dark blow through at " + got[1] + ", not " + want[1]);
		helper.succeed();
	}
}
