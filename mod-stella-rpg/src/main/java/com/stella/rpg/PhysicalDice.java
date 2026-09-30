package com.stella.rpg;

import com.stella.rpg.roll.Affixes;
import com.stella.rpg.roll.ItemTiers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The dice of a weapon and the multiplier on every physical die: {@code (1 + 0.02 * level) * material * refine}.
 * The multiplier never touches the base damage, status ATK, or affixes.
 */
public final class PhysicalDice {
	private PhysicalDice() {
	}

	/**
	 * The weapon's own die with the refine extras, or null. Melee takes the die of a sword, axe, spear, mace, or
	 * trident. A projectile takes the die of the bow or crossbow.
	 */
	public static RpgDice.Dice weaponDice(final ItemStack weapon, final boolean ranged) {
		ItemTiers.Info info = ItemTiers.of(weapon);
		if (info == null || info.type() == null || !(ranged ? info.type().ranged() : info.type().melee())) {
			return null;
		}
		StatTables tables = RpgConfigStore.current().tables();
		RpgDice.Dice base = tables.dice(info.type().key(), info.material());
		if (base == null) {
			return null;
		}
		return new RpgDice.Dice(base.count() + tables.refineDice(Affixes.refineLevel(weapon)), base.sides());
	}

	/** Level is the XP level of a player and the threat of a mob. */
	public static int level(final LivingEntity entity) {
		if (entity instanceof Player player) {
			return player.experienceLevel;
		}
		return entity instanceof Mob mob ? Math.max(0, MobsBridge.threat(mob)) : 0;
	}

	public static double materialMultiplier(final ItemStack weapon) {
		ItemTiers.Info info = ItemTiers.of(weapon);
		if (info == null || info.type() == null || !info.type().melee() && !info.type().ranged()) {
			return 1.0;
		}
		return RpgConfigStore.current().tables().materialMultiplier(info.material());
	}

	public static double multiplier(final LivingEntity entity, final ItemStack weapon) {
		StatTables tables = RpgConfigStore.current().tables();
		return (1.0 + tables.dicePerLevel() * level(entity)) * materialMultiplier(weapon)
			* tables.refineMultiplier(Affixes.refineLevel(weapon));
	}
}
