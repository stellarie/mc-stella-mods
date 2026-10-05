package com.stella.ruins.site.spidernest;

import com.stella.ruins.reward.EffectHook;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/** The effect of Widow's Fang: a hit poisons the target, and 1 hit in 5 webs it where it stands. */
public final class WidowEffect implements EffectHook {
	public static final String ID = "widow";
	public static final int POISON_TICKS = 60;
	public static final double WEB_CHANCE = 0.20;

	/** A random source for one user's rolls. Tests set it. Without one, the roll uses the level's own source. */
	private static final Map<UUID, Supplier<RandomSource>> SOURCES = new ConcurrentHashMap<>();

	/** One roll at the web chance. */
	public static boolean roll(final RandomSource source) {
		return source.nextDouble() < WEB_CHANCE;
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
		target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
		Supplier<RandomSource> supplier = SOURCES.get(user.getUUID());
		if (roll(supplier == null ? level.getRandom() : supplier.get())) {
			BlockPos feet = target.blockPosition();
			if (level.getBlockState(feet).isAir()) {
				level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
			}
		}
	}
}
