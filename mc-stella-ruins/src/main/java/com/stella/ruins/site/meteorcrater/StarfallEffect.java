package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.reward.EffectHook;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Fallen Star's effect. When the holder hits, there is a 10 percent chance that a small star falls on the
 * target for 6 magic damage. A star never calls another.
 */
public final class StarfallEffect implements EffectHook {
	public static final String ID = "starfall";
	public static final double CHANCE = 0.10;
	public static final float DAMAGE = 6.0F;

	private static boolean striking;
	/** A random source for one user's rolls. Tests set it. Without one, the roll uses the level's own source. */
	private static final Map<UUID, Supplier<RandomSource>> SOURCES = new ConcurrentHashMap<>();

	/** One roll at the star chance. */
	public static boolean roll(final RandomSource source) {
		return source.nextDouble() < CHANCE;
	}

	/** Sets the random source for one user's rolls, or clears it with null. */
	public static void setRandom(final UUID user, final Supplier<RandomSource> source) {
		if (source == null) {
			SOURCES.remove(user);
		} else {
			SOURCES.put(user, source);
		}
	}

	@Override
	public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
		if (striking || dealt <= 0.0F || !(user.level() instanceof ServerLevel level)) {
			return;
		}
		Supplier<RandomSource> custom = SOURCES.get(user.getUUID());
		if (!roll(custom == null ? level.getRandom() : custom.get())) {
			return;
		}
		level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 4.0, target.getZ(), 10, 0.1, 1.5, 0.1, 0.02);
		level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.5, target.getZ(), 14, 0.3, 0.3, 0.3, 0.04);
		level.playSound(null, target.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.5F);
		striking = true;
		try {
			target.damageCooldownTime = 0;
			target.hurtServer(level, level.damageSources().indirectMagic(user, user), DAMAGE);
		} finally {
			striking = false;
		}
	}
}
