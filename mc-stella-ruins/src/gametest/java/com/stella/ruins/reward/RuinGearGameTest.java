package com.stella.ruins.reward;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;

/** Gear that build sites make from one {@link GearSpec}, and the registration door they use. */
public class RuinGearGameTest {
	private static double attack(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
	}

	@GameTest
	public void aSpecBuildsSwordAndAxeWithItsNumbers(final GameTestHelper helper) {
		GearSpec spec = RewardTestInit.GEAR;
		ItemStack sword = new ItemStack(RewardTestInit.gearSword);
		ItemStack axe = new ItemStack(RewardTestInit.gearAxe);
		ItemStack pickaxe = new ItemStack(RewardTestInit.gearPickaxe);
		helper.assertTrue(sword.getMaxDamage() == spec.toolDurability(), "sword durability " + sword.getMaxDamage());
		helper.assertTrue(attack(sword) == RuinGear.SWORD_DAMAGE + spec.attackDamageBonus(), "sword attack " + attack(sword));
		helper.assertTrue(attack(axe) == RuinGear.AXE_DAMAGE + spec.attackDamageBonus(), "axe attack " + attack(axe));
		helper.assertTrue(attack(pickaxe) == RuinGear.PICKAXE_DAMAGE + spec.attackDamageBonus(), "pickaxe attack " + attack(pickaxe));
		helper.assertTrue(sword.get(DataComponents.ENCHANTABLE).value() == spec.enchantValue(), "enchant value");
		helper.assertTrue(sword.get(DataComponents.TOOL) != null && axe.get(DataComponents.TOOL) != null, "a tool lost its tool component");
		RewardTestSupport.result("gearTools", "swordAttack=" + attack(sword) + " axeAttack=" + attack(axe));
		helper.succeed();
	}

	@GameTest
	public void aSpecBuildsArmorWithItsNumbers(final GameTestHelper helper) {
		GearSpec spec = RewardTestInit.GEAR;
		ItemStack chest = new ItemStack(RewardTestInit.gearChest);
		helper.assertTrue(chest.getMaxDamage() == ArmorType.CHESTPLATE.getDurability(spec.armorDurabilityFactor()), "durability " + chest.getMaxDamage());
		double armor = chest.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ARMOR, 0.0, EquipmentSlot.CHEST);
		helper.assertTrue(armor == spec.chestplate(), "armor " + armor);
		var equippable = chest.get(DataComponents.EQUIPPABLE);
		helper.assertTrue(equippable != null && equippable.assetId().isPresent()
			&& equippable.assetId().get().identifier().equals(spec.equipmentAsset()), "equipment asset " + equippable);
		helper.succeed();
	}

	@GameTest
	public void everyItemRegistersWithIdAndTab(final GameTestHelper helper) {
		Item[] items = {
			RewardTestInit.gearSword, RewardTestInit.gearAxe, RewardTestInit.uniqueSword, RewardTestInit.keen.template(),
			RewardTestInit.alpha.pieces().get(EquipmentSlot.HEAD)
		};
		for (Item item : items) {
			var id = BuiltInRegistries.ITEM.getKey(item);
			helper.assertTrue(id.getNamespace().equals("mod-stella-ruins") && id.getPath().startsWith("test_"), "id " + id);
			helper.assertTrue(RuinsTab.items().contains(item), id + " is not in the tab");
		}
		helper.succeed();
	}
}
