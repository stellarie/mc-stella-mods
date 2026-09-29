package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

/** Void equipment: netherite upgraded with void ingots, and a rune that does more in it. */
public class TowerVoidGearGameTest {
	private static final int TOP = 1_000;

	private static Zombie zombieOf(final GameTestHelper helper, final TowerElement element) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, 5, element);
		zombie.snapTo(helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)));
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(zombie.getMaxHealth());
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static ItemStack craft(final ServerLevel level, final ItemStack base, final int ingots) {
		java.util.List<ItemStack> grid = new java.util.ArrayList<>();
		grid.add(base);
		for (int i = 0; i < ingots; i++) {
			grid.add(new ItemStack(TowerItems.VOID_INGOT));
		}
		while (grid.size() < 9) {
			grid.add(ItemStack.EMPTY);
		}
		CraftingInput input = CraftingInput.of(3, 3, grid);
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	/** Void ingots upgrade netherite gear, keeping what it carries, and nothing less will do. */
	@GameTest
	public void voidIngotsUpgradeNetheriteGearAndKeepItsRune(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		record Cost(ItemStack base, int ingots, net.minecraft.world.item.Item result) {
		}
		List<Cost> costs = List.of(
			new Cost(new ItemStack(Items.NETHERITE_SWORD), 2, TowerItems.VOID_SWORD),
			new Cost(new ItemStack(Items.NETHERITE_HELMET), 3, TowerItems.VOID_HELMET),
			new Cost(new ItemStack(Items.NETHERITE_CHESTPLATE), 5, TowerItems.VOID_CHESTPLATE),
			new Cost(new ItemStack(Items.NETHERITE_LEGGINGS), 4, TowerItems.VOID_LEGGINGS),
			new Cost(new ItemStack(Items.NETHERITE_BOOTS), 2, TowerItems.VOID_BOOTS)
		);
		java.util.List<String> faults = new java.util.ArrayList<>();
		for (Cost cost : costs) {
			ItemStack made = craft(level, cost.base(), cost.ingots());
			ItemStack short1 = craft(level, cost.base(), cost.ingots() - 1);
			ItemStack diamond = craft(level, new ItemStack(Items.DIAMOND_SWORD), cost.ingots());
			if (!made.is(cost.result())) {
				faults.add(cost.result() + " came out as " + made);
			}
			if (!short1.isEmpty() && short1.is(cost.result())) {
				faults.add(cost.result() + " came out one ingot short");
			}
			if (!diamond.isEmpty() && diamond.is(cost.result())) {
				faults.add(cost.result() + " came out of diamond gear");
			}
		}
		ItemStack runed = TowerRunes.apply(new ItemStack(Items.NETHERITE_SWORD), new RuneData(TowerElement.FIRE, TOP));
		ItemStack upgraded = craft(level, runed, 2);
		TowerTestSupport.result("voidRecipes", "faults=" + faults + " runeKept=" + TowerRunes.runeOf(upgraded));
		helper.assertTrue(faults.isEmpty(), String.join("; ", faults));
		helper.assertTrue(upgraded.is(TowerItems.VOID_SWORD) && TowerRunes.runeOf(upgraded) != null && TowerRunes.runeOf(upgraded).element() == TowerElement.FIRE,
			"the upgrade lost the rune: " + upgraded);
		helper.succeed();
	}

	/** Void gear is a little above netherite, is repaired with void ingots, and takes a rune like any gear. */
	@GameTest
	public void voidGearOutdoesNetheriteAndTakesRunes(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(TowerItems.VOID_SWORD);
		ItemStack netheriteSword = new ItemStack(Items.NETHERITE_SWORD);
		double swordDamage = damageOf(sword);
		double netheriteDamage = damageOf(netheriteSword);
		int armor = armorOf(new ItemStack(TowerItems.VOID_HELMET)) + armorOf(new ItemStack(TowerItems.VOID_CHESTPLATE))
			+ armorOf(new ItemStack(TowerItems.VOID_LEGGINGS)) + armorOf(new ItemStack(TowerItems.VOID_BOOTS));
		int netheriteArmor = armorOf(new ItemStack(Items.NETHERITE_HELMET)) + armorOf(new ItemStack(Items.NETHERITE_CHESTPLATE))
			+ armorOf(new ItemStack(Items.NETHERITE_LEGGINGS)) + armorOf(new ItemStack(Items.NETHERITE_BOOTS));
		boolean repairs = new ItemStack(TowerItems.VOID_INGOT).is(VoidGear.REPAIR);
		boolean sworded = TowerRunes.slotOf(sword) == TowerRunes.Slot.WEAPON;
		boolean armored = TowerRunes.slotOf(new ItemStack(TowerItems.VOID_CHESTPLATE)) == TowerRunes.Slot.ARMOR;
		TowerTestSupport.result("voidStats", "sword=" + swordDamage + "/" + netheriteDamage + " armor=" + armor + "/" + netheriteArmor
			+ " durability=" + sword.getMaxDamage() + "/" + netheriteSword.getMaxDamage() + " repairs=" + repairs);
		helper.assertTrue(swordDamage > netheriteDamage && armor > netheriteArmor && sword.getMaxDamage() > netheriteSword.getMaxDamage(),
			"void gear does not outdo netherite: sword " + swordDamage + " vs " + netheriteDamage + ", armor " + armor + " vs " + netheriteArmor);
		helper.assertTrue(repairs && sworded && armored, "repairs=" + repairs + " sword takes a rune=" + sworded + " armor takes a rune=" + armored);
		helper.succeed();
	}

	private static double damageOf(final ItemStack sword) {
		return sword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
			.filter(entry -> entry.attribute().is(Attributes.ATTACK_DAMAGE)).mapToDouble(entry -> entry.modifier().amount()).sum();
	}

	private static int armorOf(final ItemStack piece) {
		return (int) piece.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
			.filter(entry -> entry.attribute().is(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum();
	}

	/**
	 * A rune in a void sword counts half as much again: an Ice rune strikes the slowed harder, by its power
	 * as the sword carries it, than the same rune in a netherite sword.
	 */
	@GameTest
	public void aRuneInAVoidSwordCountsHalfAsMuchAgain(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		RuneData rune = new RuneData(TowerElement.ICE, TOP);
		Zombie plain = zombieOf(helper, TowerElement.WIND);
		Zombie slowed = zombieOf(helper, TowerElement.WIND);
		slowed.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200, 0));
		double[] ratios = new double[2];
		int i = 0;
		for (net.minecraft.world.item.Item sword : new net.minecraft.world.item.Item[] {Items.NETHERITE_SWORD, TowerItems.VOID_SWORD}) {
			player.setItemInHand(InteractionHand.MAIN_HAND, TowerRunes.apply(new ItemStack(sword), rune));
			float a = TowerMobs.scaleDamage(plain, player.damageSources().playerAttack(player), 10.0F);
			float b = TowerMobs.scaleDamage(slowed, player.damageSources().playerAttack(player), 10.0F);
			ratios[i++] = b / a;
		}
		double base = TowerRunes.power(TOP);
		double wantPlain = 1.0 + RuneEffects.iceBonus(base);
		double wantVoid = 1.0 + RuneEffects.iceBonus(base * VoidGear.POWER_FACTOR);
		TowerTestSupport.result("voidSwordPower", "netherite=" + ratios[0] + "/" + wantPlain + " void=" + ratios[1] + "/" + wantVoid);
		plain.discard();
		slowed.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(ratios[0] - wantPlain) < 1.0E-3, "a netherite sword struck the slowed at x" + ratios[0] + ", not x" + wantPlain);
		helper.assertTrue(Math.abs(ratios[1] - wantVoid) < 1.0E-3, "a void sword struck the slowed at x" + ratios[1] + ", not x" + wantVoid);
		helper.succeed();
	}

	/** A void sword's rune is struck in full against its own element, where any other sword's is halved. */
	@GameTest
	public void aVoidSwordStrikesItsOwnElementInFull(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		RuneData rune = new RuneData(TowerElement.FIRE, TOP);
		Zombie ownKind = zombieOf(helper, TowerElement.FIRE);
		Zombie neutral = zombieOf(helper, TowerElement.WIND);
		double[] shares = new double[2];
		int i = 0;
		for (net.minecraft.world.item.Item sword : new net.minecraft.world.item.Item[] {Items.NETHERITE_SWORD, TowerItems.VOID_SWORD}) {
			player.setItemInHand(InteractionHand.MAIN_HAND, TowerRunes.apply(new ItemStack(sword), rune));
			float own = TowerMobs.scaleDamage(ownKind, player.damageSources().playerAttack(player), 10.0F);
			float other = TowerMobs.scaleDamage(neutral, player.damageSources().playerAttack(player), 10.0F);
			// The part the rune added, over the whole: the same against any element but the rune's own.
			shares[i++] = (own - 10.0) / (other - 10.0);
		}
		TowerTestSupport.result("voidSwordOwn", "netherite=" + shares[0] + " void=" + shares[1] + " want=" + TowerRunes.SAME + "/1.0");
		ownKind.discard();
		neutral.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(shares[0] - TowerRunes.SAME) < 1.0E-3, "a netherite sword struck its own element at " + shares[0] + " of the rest, not " + TowerRunes.SAME);
		helper.assertTrue(Math.abs(shares[1] - 1.0) < 1.0E-3, "a void sword struck its own element at " + shares[1] + " of the rest, not in full");
		helper.succeed();
	}

	/** A rune on void armor cuts its own element's blows twice as much, and counts half as much again. */
	@GameTest
	public void aRuneOnVoidArmorCutsItsOwnElementTwiceOver(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		TowerTestSupport.vulnerable(player);
		// The highest floor whose void cut stays clear of the cap, so the test sees the power factor as well as the doubling.
		int floor = TOP;
		while (floor > 1 && (TowerRunes.OWN_ELEMENT_BASE + TowerRunes.power(floor) * VoidGear.POWER_FACTOR / 1000.0) * VoidGear.OWN_ELEMENT_FACTOR > TowerRunes.OWN_ELEMENT_CAP - 0.05) {
			floor--;
		}
		RuneData rune = new RuneData(TowerElement.WIND, floor);
		double[] cut = new double[2];
		int i = 0;
		for (net.minecraft.world.item.Item chest : new net.minecraft.world.item.Item[] {Items.NETHERITE_CHESTPLATE, TowerItems.VOID_CHESTPLATE}) {
			player.setItemSlot(EquipmentSlot.CHEST, TowerRunes.apply(new ItemStack(chest), rune));
			float ownBlow = TowerRunes.armor(player, TowerElement.WIND, 100.0F);
			float otherBlow = TowerRunes.armor(player, TowerElement.FIRE, 100.0F);
			cut[i++] = 1.0 - ownBlow / otherBlow;
		}
		double wantPlain = TowerRunes.OWN_ELEMENT_BASE + TowerRunes.power(floor) / 1000.0;
		double wantVoid = Math.min(TowerRunes.OWN_ELEMENT_CAP, (TowerRunes.OWN_ELEMENT_BASE + TowerRunes.power(floor) * VoidGear.POWER_FACTOR / 1000.0) * VoidGear.OWN_ELEMENT_FACTOR);
		double withoutFactor = (TowerRunes.OWN_ELEMENT_BASE + TowerRunes.power(floor) / 1000.0) * VoidGear.OWN_ELEMENT_FACTOR;
		helper.assertTrue(wantVoid < TowerRunes.OWN_ELEMENT_CAP && wantVoid - withoutFactor > 0.01, "floor " + floor + " leaves no room to see the power factor: " + wantVoid + " vs " + withoutFactor);
		TowerTestSupport.result("voidArmorCut", "floor=" + floor + " netherite=" + cut[0] + "/" + wantPlain + " void=" + cut[1] + "/" + wantVoid);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(cut[0] - wantPlain) < 1.0E-3, "netherite armor cut its own element by " + cut[0] + ", not " + wantPlain);
		helper.assertTrue(Math.abs(cut[1] - wantVoid) < 1.0E-3, "void armor cut its own element by " + cut[1] + ", not " + wantVoid);
		helper.succeed();
	}
}
