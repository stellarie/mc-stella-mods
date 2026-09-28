package com.stella.villages.mission;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

final class NemesisKit {
	static final List<Holder<MobEffect>> BUFFS = List.of(
		MobEffects.RESISTANCE, MobEffects.REGENERATION, MobEffects.FIRE_RESISTANCE
	);
	static final EquipmentSlot[] REWARD_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
		EquipmentSlot.FEET, EquipmentSlot.MAINHAND
	};

	private NemesisKit() {
	}

	static int randomBuff(final Mob mob) {
		return mob.getRandom().nextInt(BUFFS.size());
	}

	static void applyElite(final Mob mob, final GearTier tier, final int buffIndex) {
		Elite.mark(mob, tier, Elite.HEALTH_MULTIPLIER);
		ensureBuff(mob, buffIndex);
	}

	static void ensureBuff(final Mob mob, final int buffIndex) {
		Holder<MobEffect> buff = BUFFS.get(Math.floorMod(buffIndex, BUFFS.size()));
		if (!mob.hasEffect(buff)) {
			mob.addEffect(new MobEffectInstance(buff, -1, 0));
		}
	}

	static void armNemesis(final Mob mob, final GearTier tier) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(health.getBaseValue() + MissionService.NEMESIS_BONUS_HEALTH);
			mob.setHealth(mob.getMaxHealth());
		}
		equipNemesis(mob, tier);
	}

	static boolean needsEquipmentUpgrade(final Mob mob) {
		for (EquipmentSlot slot : REWARD_SLOTS) {
			ItemStack stack = mob.getItemBySlot(slot);
			var enchantments = stack.get(DataComponents.ENCHANTMENTS);
			if (stack.isEmpty() || enchantments == null || enchantments.size() != 3) {
				return true;
			}
		}
		return false;
	}

	static void equipNemesis(final Mob mob, final GearTier tier) {
		boolean netherite = tier == GearTier.NETHERITE;
		arm(mob, EquipmentSlot.HEAD, netherite ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET);
		arm(mob, EquipmentSlot.CHEST, netherite ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE);
		arm(mob, EquipmentSlot.LEGS, netherite ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS);
		arm(mob, EquipmentSlot.FEET, netherite ? Items.NETHERITE_BOOTS : Items.DIAMOND_BOOTS);
		Item ranged = mob.canUseNonMeleeWeapon(new ItemStack(Items.BOW)) ? Items.BOW
			: mob.canUseNonMeleeWeapon(new ItemStack(Items.CROSSBOW)) ? Items.CROSSBOW : null;
		arm(mob, EquipmentSlot.MAINHAND,
			ranged != null ? ranged : netherite ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD);
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		mob.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
	}

	private static void arm(final Mob mob, final EquipmentSlot slot, final Item item) {
		ItemStack stack = new ItemStack(item);
		Registry<Enchantment> registry = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Holder<Enchantment>> candidates = new ArrayList<>(registry.listElements()
			.filter(holder -> holder.value().canEnchant(stack))
			.map(holder -> (Holder<Enchantment>) holder).toList());
		List<Holder<Enchantment>> chosen = List.of();
		int triples = 0;
		for (int first = 0; first < candidates.size(); first++) {
			for (int second = first + 1; second < candidates.size(); second++) {
				if (!Enchantment.areCompatible(candidates.get(first), candidates.get(second))) {
					continue;
				}
				for (int third = second + 1; third < candidates.size(); third++) {
					if (Enchantment.areCompatible(candidates.get(first), candidates.get(third))
						&& Enchantment.areCompatible(candidates.get(second), candidates.get(third))
						&& mob.getRandom().nextInt(++triples) == 0) {
						chosen = List.of(candidates.get(first), candidates.get(second), candidates.get(third));
					}
				}
			}
		}
		if (chosen.isEmpty() && candidates.size() >= 3) {
			chosen = List.of(candidates.remove(mob.getRandom().nextInt(candidates.size())),
				candidates.remove(mob.getRandom().nextInt(candidates.size())),
				candidates.remove(mob.getRandom().nextInt(candidates.size())));
		}
		for (Holder<Enchantment> enchantment : chosen) {
			int level = Mth.nextInt(mob.getRandom(), enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
			stack.enchant(enchantment, level);
		}
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, 0.0F);
	}
}
