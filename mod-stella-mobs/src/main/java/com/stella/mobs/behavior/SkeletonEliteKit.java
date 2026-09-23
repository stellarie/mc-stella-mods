package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * R22: the elite skeleton's kit and attributes. A full Thorns set, a knockback sword, a plain shield in
 * the off hand, 25 percent more movement. The shield carries no enchantment on purpose and never can:
 * THORNS is registered against the armour slots and a shield has no ENCHANTABLE component, so the
 * durability enchantment rides the armour with the Thorns instead. The kit is written here because the
 * elite mark happens before the installer runs, which is what gives the shield its goal.
 */
public final class SkeletonEliteKit {
	public static final double MOVEMENT_BONUS = 0.25;
	public static final int THORNS_LEVEL = 3;
	public static final int KNOCKBACK_LEVEL = 2;
	public static final Identifier MOVEMENT_ID =
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "elite_skeleton_speed");

	private SkeletonEliteKit() {
	}

	/** True when the kit is already on, so a second mark cannot stack the movement modifier. */
	public static boolean applied(final Mob mob) {
		AttributeInstance movement = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		return movement != null && movement.getModifier(MOVEMENT_ID) != null;
	}

	public static void apply(final Mob mob) {
		Registry<Enchantment> registry = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> thorns = registry.getOrThrow(Enchantments.THORNS);
		Holder<Enchantment> knockback = registry.getOrThrow(Enchantments.KNOCKBACK);

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!slot.isArmor()) {
				continue;
			}

			ItemStack armour = mob.getItemBySlot(slot);
			if (!armour.isEmpty()) {
				armour.enchant(thorns, THORNS_LEVEL);
			}
		}

		ItemStack weapon = mob.getMainHandItem();
		if (!weapon.isEmpty()) {
			weapon.enchant(knockback, KNOCKBACK_LEVEL);
		}

		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		// The invitation: a boss or elite marked by hand reaches this after the installer, so the shield goal
		// is asked for here as well, and the installer's own call is a no-op when it already exists.
		BehaviorInstaller.installShield(mob);

		AttributeInstance movement = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (movement != null) {
			movement.addOrReplacePermanentModifier(
				new AttributeModifier(MOVEMENT_ID, MOVEMENT_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
			);
		}

		FasterMeleeGoal.install(mob);
	}
}
