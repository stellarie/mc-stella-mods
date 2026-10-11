package com.stella.nether;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every hit a Sovereign lands comes in two halves of one size: a physical half, and a fire half that is a
 * {@link #FLAME} hit. Fire Resistance, Fire Protection and the Flame Mantle meet the fire half only, so Fire
 * Resistance spares at most half of a hit. Armor and shields meet both halves.
 *
 * <p>The halves are struck one after the other through the vanilla hit, by {@code SovereignHitMixin}, from outside
 * every other mod's change to the amount. Each half is therefore scaled once, as a whole hit would be, and no half
 * meets the split again.
 */
public final class SovereignHits {
	/** The fire half of a Sovereign hit; tagged as fire, and as a hit that does not push. */
	public static final ResourceKey<DamageType> FLAME = ResourceKey.create(Registries.DAMAGE_TYPE, StellaNether.id("sovereign_flame"));

	/** Vanilla refuses an equal hit while a victim's cooldown is above this. */
	private static final float COOLDOWN_OPEN = 10.0F;

	/** One whole hit through the vanilla method. */
	@FunctionalInterface
	public interface Strike {
		boolean hurt(DamageSource source, float amount);
	}

	private SovereignHits() {
	}

	/** Whether the Sovereign's hit on this victim comes in halves. A fire-immune victim could take only the physical half, so it takes the hit whole. */
	public static boolean splits(final LivingEntity victim, final DamageSource source, final float amount) {
		return amount > 0.0F && BlazeSovereign.isSovereign(source.getEntity()) && source.getEntity() != victim
			&& !victim.fireImmune() && !source.is(FLAME);
	}

	/** Strikes a Sovereign's hit in its two halves, physical then fire. Returns whether any of it landed. */
	public static boolean hurtInHalves(final LivingEntity victim, final ServerLevel level, final DamageSource source, final float amount, final Strike strike) {
		// The fireball share is a property of the whole hit; a half no longer reads as a fireball.
		float half = BlazeSovereign.scaleFireball(source, amount) / 2.0F;
		DamageSource physical = source.is(DamageTypeTags.IS_FIRE)
			? new DamageSource(holder(level, DamageTypes.MOB_ATTACK), source.getDirectEntity(), source.getEntity()) : source;
		DamageSource flame = new DamageSource(holder(level, FLAME), source.getDirectEntity(), source.getEntity());
		boolean fresh = victim.damageCooldownTime <= COOLDOWN_OPEN;
		// The physical half goes first: a totem that answers it grants Fire Resistance before the fire half arrives.
		boolean landed = strike.hurt(physical, half);
		int cooldown = victim.damageCooldownTime;
		if (fresh) {
			// The first half opened the cooldown, and an equal second half would be refused by it.
			victim.damageCooldownTime = 0;
		}
		if (strike.hurt(flame, half)) {
			return true;
		}
		victim.damageCooldownTime = cooldown;
		return landed;
	}

	private static Holder<DamageType> holder(final ServerLevel level, final ResourceKey<DamageType> key) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
	}
}
