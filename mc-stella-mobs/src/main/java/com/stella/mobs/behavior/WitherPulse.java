package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A wither skeleton hunting a target that has turned its back sends out a slowing pulse, sixteen blocks
 * round, at most once every ten seconds. An elite or boss one answers a blow with a weakness pulse, at most
 * once every fifteen seconds. A pulse reaches players and the skeleton's own target.
 */
public final class WitherPulse {
	public static final double RADIUS = 16.0;
	public static final int SLOW_COOLDOWN_TICKS = 200;
	public static final int SLOW_TICKS = 60;
	public static final int SLOW_AMPLIFIER = 1;
	public static final int WEAKNESS_COOLDOWN_TICKS = 300;
	public static final int WEAKNESS_TICKS = 100;
	public static final int WEAKNESS_AMPLIFIER = 0;
	/** Out of melee reach, so the pulse comes while it closes in rather than while it swings. */
	public static final double PURSUIT_DISTANCE = 3.0;

	/** Test seam: each pulse, with who it reached. */
	public interface Recorder {
		void pulsed(WitherSkeleton skeleton, Holder<MobEffect> effect, List<LivingEntity> reached);
	}

	private static volatile Recorder recorder = (skeleton, effect, reached) -> {
	};
	private static final Map<WitherSkeleton, Integer> LAST_WEAKNESS = new WeakHashMap<>();

	private WitherPulse() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? (skeleton, effect, reached) -> {
		} : newRecorder;
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(WitherPulse::afterDamage);
	}

	public static void install(final WitherSkeleton skeleton) {
		if (BossAbilities.copiesOf(skeleton, SlowPulseGoal.class) == 0) {
			skeleton.getGoalSelector().addGoal(BehaviorInstaller.WITCH_BLOCK_START, new SlowPulseGoal(skeleton));
		}
	}

	/** True when the target faces away from the skeleton. */
	public static boolean facesAway(final LivingEntity target, final LivingEntity from) {
		Vec3 view = target.getViewVector(1.0F);
		Vec3 toward = from.position().subtract(target.position());
		return new Vec3(view.x, 0.0, view.z).dot(new Vec3(toward.x, 0.0, toward.z)) < 0.0;
	}

	static void pulse(final WitherSkeleton skeleton, final Holder<MobEffect> effect, final int ticks, final int amplifier) {
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity target = skeleton.getTarget();
		List<LivingEntity> reached = level.getEntitiesOfClass(LivingEntity.class, skeleton.getBoundingBox().inflate(RADIUS),
			other -> other != skeleton && other.isAlive() && other.distanceToSqr(skeleton) <= RADIUS * RADIUS
				&& (other == target || other instanceof Player player && !player.isCreative() && !player.isSpectator()));
		for (LivingEntity other : reached) {
			other.addEffect(new MobEffectInstance(effect, ticks, amplifier), skeleton);
		}
		level.sendParticles(effect.equals(MobEffects.SLOWNESS) ? ParticleTypes.SQUID_INK : ParticleTypes.SMOKE,
			skeleton.getX(), skeleton.getY(0.5), skeleton.getZ(), 40, 2.0, 0.5, 2.0, 0.05);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherPulse PULSE effect={} reached={}", effect.getRegisteredName(), reached.size());
		recorder.pulsed(skeleton, effect, List.copyOf(reached));
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (!(victim instanceof WitherSkeleton skeleton) || !skeleton.isAlive() || !(Elite.isElite(skeleton) || Bosses.isBoss(skeleton))
			|| !(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		int now = level.getServer().getTickCount();
		synchronized (LAST_WEAKNESS) {
			Integer last = LAST_WEAKNESS.get(skeleton);
			if (last != null && now >= last && now - last < WEAKNESS_COOLDOWN_TICKS) {
				return;
			}
			LAST_WEAKNESS.put(skeleton, now);
		}
		pulse(skeleton, MobEffects.WEAKNESS, WEAKNESS_TICKS, WEAKNESS_AMPLIFIER);
	}

	/** The slowing pulse, on the skeleton's own clock. Flagless. */
	public static class SlowPulseGoal extends Goal {
		private final WitherSkeleton skeleton;
		private int cooldown;

		public SlowPulseGoal(final WitherSkeleton skeleton) {
			this.skeleton = skeleton;
		}

		@Override
		public boolean canUse() {
			if (this.cooldown > 0) {
				this.cooldown--;
				return false;
			}
			LivingEntity target = this.skeleton.getTarget();
			return target != null && target.isAlive() && this.skeleton.distanceToSqr(target) > PURSUIT_DISTANCE * PURSUIT_DISTANCE
				&& this.skeleton.distanceToSqr(target) <= RADIUS * RADIUS && facesAway(target, this.skeleton);
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			this.cooldown = SLOW_COOLDOWN_TICKS;
			pulse(this.skeleton, MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER);
		}
	}
}
