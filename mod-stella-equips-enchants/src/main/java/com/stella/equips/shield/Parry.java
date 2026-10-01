package com.stella.equips.shield;

import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineSpecials;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Parry, the special of a refined shield. A raised shield that blocks a melee hit sends a share of the blocked
 * damage back to the attacker: a quarter at +10, a half at +12 and three quarters at +15. A projectile has no
 * melee attacker to answer. The answer is thorns damage, and thorns damage is never parried, so two raised
 * shields cannot parry each other for ever.
 */
public final class Parry {
	private Parry() {
	}

	/**
	 * Answers a hit that the defender's raised shield has just blocked. The shield mixin calls this where the
	 * blocked amount is known. The shield is still whole there, so a blow that breaks it is still parried.
	 */
	public static void answer(final LivingEntity defender, final DamageSource source, final float blocked) {
		if (blocked <= 0.0F || source.is(DamageTypes.THORNS) || source.is(DamageTypeTags.IS_PROJECTILE)
			|| !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == defender || !(defender.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack shield = defender.getItemBlockingWith();
		if (shield == null || !RefineSpecials.has(shield, RefineSpecials.Special.PARRY)) {
			return;
		}
		float back = blocked * (float) RefineEffects.parryShare(Refine.level(shield));
		if (back > 0.0F) {
			attacker.hurtServer(level, level.damageSources().thorns(defender), back);
		}
	}
}
