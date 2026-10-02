package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.projectile.StellaSmallFireball;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

/**
 * Every blaze fires one fireball on a steady beat, and each ball bursts in an explosion that harms
 * bodies and never the ground. An elite fires faster. A boss blaze calls an ordinary blaze on some of
 * its attacks, up to a cap of living ones. Replaces the vanilla attack goal, a private nested class.
 */
public class BlazeAttackGoal extends Goal {
	public static final int ORDINARY_INTERVAL_TICKS = 40;
	public static final int ELITE_INTERVAL_TICKS = 30;
	public static final double BOSS_SUMMON_CHANCE = 0.10;
	/** Living called blazes a boss may have at once, so a long fight cannot fill the sky. */
	public static final int BOSS_SUMMON_CAP = 3;
	private static final double SUMMON_SPREAD = 2.0;

	public interface Recorder {
		default void fired(final Blaze blaze, final int serverTick) {
		}

		default void summoned(final Blaze boss, final Blaze called) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;
	private static final Set<UUID> FORCED_SUMMONS = ConcurrentHashMap.newKeySet();
	/** The blazes each boss has called, by identity, so its cap counts only its own. */
	private static final Map<UUID, List<UUID>> CALLED = new ConcurrentHashMap<>();

	private final Blaze blaze;
	private int cooldown;

	public BlazeAttackGoal(final Blaze blaze) {
		this.blaze = blaze;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/** A boss's record goes with it, and a called blaze leaves its boss's record only when it dies or is discarded. */
	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> forget(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				forget(entity);
			}
		});
	}

	private static void forget(final Entity entity) {
		if (entity instanceof Blaze) {
			CALLED.remove(entity.getUUID());
			CALLED.values().forEach(called -> called.remove(entity.getUUID()));
		}
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** Test seam: this blaze's summon roll always lands. */
	public static void forceSummonFor(final UUID blaze) {
		FORCED_SUMMONS.add(blaze);
	}

	/** Test seam: how many called blazes this boss holds on its record. */
	public static int calledCount(final UUID boss) {
		List<UUID> called = CALLED.get(boss);
		return called == null ? 0 : called.size();
	}

	public static boolean summons(final RandomSource random) {
		return random.nextDouble() < BOSS_SUMMON_CHANCE;
	}

	static int intervalFor(final Blaze blaze) {
		return Elite.isElite(blaze) ? ELITE_INTERVAL_TICKS : ORDINARY_INTERVAL_TICKS;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.blaze.getTarget();
		return target != null && target.isAlive() && this.blaze.canAttack(target);
	}

	@Override
	public void start() {
		this.cooldown = intervalFor(this.blaze);
	}

	@Override
	public void stop() {
		this.blaze.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.blaze.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		this.blaze.getLookControl().setLookAt(target, 10.0F, 10.0F);
		boolean sees = this.blaze.getSensing().hasLineOfSight(target);
		double range = this.blaze.getAttributeValue(Attributes.FOLLOW_RANGE);
		// Close in when the target is out of sight or out of range, and hold position to fire otherwise.
		if (!sees || this.blaze.distanceToSqr(target) > range * range) {
			this.blaze.getNavigation().moveTo(target, 1.0);
		} else {
			this.blaze.getNavigation().stop();
		}

		if (--this.cooldown > 0 || !sees) {
			return;
		}

		this.cooldown = intervalFor(this.blaze);
		this.fire(target);
	}

	private void fire(final LivingEntity target) {
		if (!(this.blaze.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 lead = ShotLead.offset(this.blaze, target, ShotLead.fireballSpeed(this.blaze.distanceTo(target)));
		Vec3 aim = new Vec3(
			target.getX() + lead.x - this.blaze.getX(), target.getY(0.5) + lead.y - this.blaze.getY(0.5), target.getZ() + lead.z - this.blaze.getZ()
		);
		if (aim.lengthSqr() < 1.0E-4) {
			return;
		}

		StellaSmallFireball ball = new StellaSmallFireball(level, this.blaze.getX(), this.blaze.getY(0.5), this.blaze.getZ(), aim.normalize());
		ball.setOwner(this.blaze);
		level.addFreshEntity(ball);
		recorder.fired(this.blaze, level.getServer().getTickCount());

		if (EliteState.isBoss(this.blaze) && (FORCED_SUMMONS.contains(this.blaze.getUUID()) || summons(this.blaze.getRandom()))) {
			this.call(level, target);
		}
	}

	// An ordinary blaze: it takes none of the spawn rolls, so it can never be elite or a boss itself.
	private void call(final ServerLevel level, final LivingEntity target) {
		// A called blaze in an unloaded chunk still counts: only its death or discard frees its place.
		List<UUID> called = CALLED.computeIfAbsent(this.blaze.getUUID(), key -> new CopyOnWriteArrayList<>());
		if (called.size() >= BOSS_SUMMON_CAP) {
			return;
		}

		Blaze helper = EntityTypes.BLAZE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (helper == null) {
			return;
		}

		RandomSource random = this.blaze.getRandom();
		helper.snapTo(
			this.blaze.getX() + (random.nextDouble() * 2.0 - 1.0) * SUMMON_SPREAD,
			this.blaze.getY(),
			this.blaze.getZ() + (random.nextDouble() * 2.0 - 1.0) * SUMMON_SPREAD,
			this.blaze.getYRot(), 0.0F
		);
		helper.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		EliteState.setSummoned(helper, true);
		helper.setTarget(target);
		if (!level.addFreshEntity(helper)) {
			return;
		}

		called.add(helper.getUUID());
		recorder.summoned(this.blaze, helper);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=blazeBoss SUMMON boss={} called={} alive={} cap={}",
			this.blaze.getUUID(), helper.getUUID(), called.size(), BOSS_SUMMON_CAP
		);
	}
}
