package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.reward.EffectHook;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The Bandit's Mark affix effect: a 10 percent chance that a hit is a critical, which adds half of the damage dealt.
 * The extra hit goes through the attack hook, because the game gives no crit chance attribute.
 */
public final class BanditsMarkEffect implements EffectHook {
	public static final String ID = "bandits_mark";
	public static final double CHANCE = 0.10;
	public static final float BONUS = 0.5F;

	/** A random source for one user's rolls. Tests set it. Without one, the roll uses the level's own source. */
	private static final Map<UUID, Supplier<RandomSource>> SOURCES = new ConcurrentHashMap<>();

	/** One roll at the crit chance. */
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
		if (dealt <= 0.0F || !(user.level() instanceof ServerLevel level)) {
			return;
		}
		Supplier<RandomSource> custom = SOURCES.get(user.getUUID());
		if (!roll(custom == null ? level.getRandom() : custom.get())) {
			return;
		}
		DamageSource extra = user instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(user);
		target.damageCooldownTime = 0;
		target.hurtServer(level, extra, dealt * BONUS);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
	}
}
