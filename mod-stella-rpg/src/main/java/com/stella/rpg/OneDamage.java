package com.stella.rpg;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A weapon in the item tag {@code mod-stella-rpg:one_damage_weapons} deals exactly one damage with a melee hit or an arrow hit.
 * The rule runs at the last step, after K, armor and every buff, so nothing adds to the hit.
 */
public final class OneDamage {
	public static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, StellaRpg.id("one_damage_weapons"));
	public static final float HIT = 1.0F;

	private OneDamage() {
	}

	/** The final damage of a hit: {@link #HIT} for a melee hit or an arrow hit of a tagged weapon, else the damage as it was. */
	public static float apply(final DamageSource source, final float damage) {
		if (damage <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker)) {
			return damage;
		}
		if (source.getDirectEntity() == attacker) {
			boolean melee = source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
			return melee && attacker.getMainHandItem().is(WEAPONS) ? HIT : damage;
		}
		if (source.getDirectEntity() instanceof AbstractArrow && source.is(DamageTypeTags.IS_PROJECTILE)) {
			ItemStack weapon = source.getWeaponItem();
			return weapon != null && weapon.is(WEAPONS) ? HIT : damage;
		}
		return damage;
	}
}
