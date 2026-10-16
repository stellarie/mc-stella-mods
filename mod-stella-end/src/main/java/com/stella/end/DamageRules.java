package com.stella.end;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;

/**
 * Every change this mod makes to a blow before it lands: the roost and the crystals ease the dragon's damage taken, and
 * the draconic blade adds to the damage a mob of the End takes from it.
 */
public final class DamageRules {
	private DamageRules() {
	}

	public static float scale(final LivingEntity victim, final DamageSource source, final float amount) {
		float result = amount;
		if (victim instanceof EnderDragon dragon) {
			DragonState state = EndDragons.state(dragon);
			if (state != null) {
				if (state.mode() == DragonState.Mode.ROOST) {
					result *= DragonRules.ROOST_DAMAGE;
				}
				result *= DragonRules.damageTakenMultiplier(state.buffs());
			}
		}
		if (victim instanceof Mob && victim.level().dimension().equals(Level.END) && source.getEntity() instanceof LivingEntity attacker
			&& source.getDirectEntity() == attacker && attacker.getMainHandItem().is(DraconicGear.item(DraconicGear.BLADE))) {
			result *= 1.0F + (float) DraconicGear.END_BONUS;
		}
		return result;
	}
}
