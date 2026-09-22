package com.stella.mobs.gear;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.Loadout;
import com.stella.mobs.config.SlotSpec;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class GearApplier {
	private GearApplier() {
	}

	public static void apply(final Mob mob, final Loadout loadout, final Registry<Enchantment> enchantments) {
		for (Map.Entry<EquipmentSlot, SlotSpec> entry : loadout.slots().entrySet()) {
			ItemStack stack = buildStack(entry.getKey(), entry.getValue(), enchantments);
			if (stack != null) {
				mob.setItemSlot(entry.getKey(), stack);
			}
		}

		loadout.dropChance().ifPresent(chance -> {
			for (EquipmentSlot slot : loadout.slots().keySet()) {
				mob.setDropChance(slot, chance);
			}
		});
	}

	private static ItemStack buildStack(final EquipmentSlot slot, final SlotSpec spec, final Registry<Enchantment> enchantments) {
		Item item = BuiltInRegistries.ITEM.get(spec.item()).map(Holder.Reference::value).orElse(null);
		if (item == null) {
			StellaMobs.LOGGER.warn("Gear slot '{}': unknown item id '{}'", slot, spec.item());
			return null;
		}

		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

		for (Map.Entry<Identifier, Integer> entry : spec.enchantments().entrySet()) {
			ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, entry.getKey());
			Holder.Reference<Enchantment> holder = enchantments.get(key).orElse(null);
			if (holder == null) {
				StellaMobs.LOGGER.warn("Gear slot '{}': unknown enchantment id '{}'", slot, entry.getKey());
				continue;
			}

			mutable.set(holder, entry.getValue());
		}

		ItemStack stack = new ItemStack(item);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}
}
