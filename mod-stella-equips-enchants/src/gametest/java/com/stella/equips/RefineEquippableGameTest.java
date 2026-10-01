package com.stella.equips;

import com.stella.equips.refine.Refine;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;

/** A chest piece outside the armor tags refines when it equips to an armor slot and gives armor. */
public final class RefineEquippableGameTest {
	@GameTest
	public void untaggedChestRefines(final GameTestHelper helper) {
		ItemStack piece = new ItemStack(Items.PAPER);
		piece.set(DataComponents.MAX_STACK_SIZE, 1);
		piece.set(DataComponents.MAX_DAMAGE, 1000);
		piece.set(DataComponents.DAMAGE, 0);
		piece.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build());
		piece.set(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(Items.IRON_INGOT.builtInRegistryHolder())));
		piece.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().add(Attributes.ARMOR,
			new AttributeModifier(Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "test_armor"), 6.0, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.CHEST).build());
		ItemStack elytra = new ItemStack(Items.ELYTRA);
		ItemStack tagged = new ItemStack(Items.IRON_HELMET);
		List<Object> seen = List.of(Refine.canRefine(piece), Refine.armorSlotOf(piece), Refine.grade(piece), Refine.material(piece),
			Refine.canRefine(elytra), Refine.armorSlotOf(tagged), Refine.grade(tagged));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=refineEquippable RESULT piece={} elytra={} helmet={}", seen.subList(0, 4), seen.get(4), seen.subList(5, 7));
		helper.assertTrue(Refine.canRefine(piece) && Refine.armorSlotOf(piece) == EquipmentSlot.CHEST
			&& Refine.grade(piece) == Refine.Grade.ARMOR && Refine.material(piece) == Items.IRON_INGOT, "the untagged chest piece reads " + seen.subList(0, 4));
		helper.assertTrue(!Refine.canRefine(elytra), "an elytra refines");
		helper.assertTrue(Refine.armorSlotOf(tagged) == EquipmentSlot.HEAD && Refine.grade(tagged) == Refine.Grade.ARMOR, "a helmet reads " + seen.subList(5, 7));
		helper.succeed();
	}
}
