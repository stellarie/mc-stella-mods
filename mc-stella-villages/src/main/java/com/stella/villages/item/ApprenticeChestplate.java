package com.stella.villages.item;

import com.stella.villages.McStellaVillages;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The slayer contract's reward: an iron chestplate with 1000 durability that guards more in the dark.
 * It has no enchantable component and no item tags, so no enchantment applies to it. The equips mod
 * refines it as chest armor through its equippable slot.
 */
public final class ApprenticeChestplate extends Item {
	public static final int DURABILITY = 1000;
	public static final int FULL_LIGHT = 15;
	public static final float REDUCTION_PER_LIGHT = 0.015F;
	private static final Identifier ID = Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "apprentice_chestplate");
	private static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);
	public static final ApprenticeChestplate ITEM = Registry.register(BuiltInRegistries.ITEM, KEY, new ApprenticeChestplate(properties()));

	private ApprenticeChestplate(final Properties properties) {
		super(properties);
	}

	private static Properties properties() {
		return new Properties().setId(KEY)
			.durability(DURABILITY)
			.attributes(ArmorMaterials.IRON.createAttributes(ArmorType.CHESTPLATE))
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
				.setEquipSound(ArmorMaterials.IRON.equipSound()).setAsset(ArmorMaterials.IRON.assetId()).build())
			.repairable(ArmorMaterials.IRON.repairIngredient())
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("1.5% damage reduction for each"),
				Component.literal("light level below 15")
			)));
	}

	// Literal, because the village mod ships no language file.
	@Override
	public Component getName(final ItemStack stack) {
		return Component.literal("Apprentice's Chestplate");
	}

	public static void register() {
		McStellaVillages.LOGGER.debug("Registered the apprentice chestplate {}", ID);
	}

	/** The share of damage the chestplate removes at a light level: 1.5% for each level below 15. */
	public static float reduction(final int light) {
		return REDUCTION_PER_LIGHT * (FULL_LIGHT - Mth.clamp(light, 0, FULL_LIGHT));
	}

	/** The wearer's reduction, from the light at its feet; zero unless the chestplate is worn. */
	public static float reduction(final LivingEntity wearer) {
		if (!wearer.getItemBySlot(EquipmentSlot.CHEST).is(ITEM)) {
			return 0.0F;
		}
		return reduction(wearer.level().getMaxLocalRawBrightness(wearer.blockPosition()));
	}
}
