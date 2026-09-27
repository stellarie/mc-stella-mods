package com.stella.equips.enchant;

import com.stella.equips.StellaEquips;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Waveform: a melee hit sends a wave out from the wielder that slows every enemy in a wide ring and throws
 * each one back, then rests for three seconds.
 */
public final class Waveform {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "weapon/waveform");
	public static final int COOLDOWN_TICKS = 60;
	public static final double BASE_RADIUS = 8.0;
	public static final double RADIUS_PER_LEVEL = 4.0;
	public static final int SLOW_TICKS = 60;
	public static final int SLOW_AMPLIFIER = 1;
	public static final double KNOCKBACK = 1.0;
	public static final double LIFT = 0.35;

	/** Test seam: each wave, with the foes it reached. */
	public interface Recorder {
		void waved(LivingEntity wielder, List<LivingEntity> reached);
	}

	private static final Map<LivingEntity, Integer> LAST_WAVE = new WeakHashMap<>();
	private static volatile Recorder recorder = (wielder, reached) -> {
	};

	private Waveform() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Waveform::afterDamage);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? (wielder, reached) -> {
		} : newRecorder;
	}

	public static double radius(final int level) {
		return BASE_RADIUS + RADIUS_PER_LEVEL * level;
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (!(source.getEntity() instanceof LivingEntity wielder) || source.getDirectEntity() != wielder || wielder == victim
			|| !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		Optional<Holder.Reference<Enchantment>> found = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ID);
		int rank = found.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, wielder.getMainHandItem())).orElse(0);
		if (rank <= 0) {
			return;
		}
		int now = level.getServer().getTickCount();
		synchronized (LAST_WAVE) {
			Integer last = LAST_WAVE.get(wielder);
			if (last != null && now >= last && now - last < COOLDOWN_TICKS) {
				return;
			}
			LAST_WAVE.put(wielder, now);
		}
		double radius = radius(rank);
		List<LivingEntity> reached = level.getEntitiesOfClass(LivingEntity.class, wielder.getBoundingBox().inflate(radius),
			other -> Foes.isFoe(wielder, other) && other.distanceToSqr(wielder) <= radius * radius);
		for (LivingEntity foe : reached) {
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER), wielder);
			Vec3 away = foe.position().subtract(wielder.position());
			if (away.horizontalDistanceSqr() > 1.0E-4) {
				Vec3 push = new Vec3(away.x, 0.0, away.z).normalize().scale(KNOCKBACK);
				foe.push(push.x, LIFT, push.z);
				foe.needsSync = true;
			}
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, wielder.getX(), wielder.getY(0.5), wielder.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.BUBBLE_POP, wielder.getX(), wielder.getY(0.2), wielder.getZ(), 60, radius * 0.4, 0.2, radius * 0.4, 0.2);
		level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=waveform WAVE wielder={} level={} reached={}", wielder.getUUID(), rank, reached.size());
		recorder.waved(wielder, reached);
	}
}
