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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every blaze fires one fireball on a steady beat, and each ball bursts in an explosion that harms
 * bodies and never the ground. An elite fires faster. A ball fired at a target far enough off splits in
 * three at the middle of its flight. A boss blaze calls an ordinary blaze on some of its attacks, up to a
 * cap of living ones. Replaces the vanilla attack goal, a private nested class.
 */
public class BlazeAttackGoal extends Goal {
	public static final int ORDINARY_INTERVAL_TICKS = 20;
	public static final int ELITE_INTERVAL_TICKS = 15;
	/** A target nearer than this gets an unsplit ball: the children would have no room to fan out. */
	public static final double MIN_SPLIT_RANGE = 4.0;
	/** The share of the flight to the aim point after which the ball splits. */
	public static final double SPLIT_AT_FRACTION = 0.5;
	public static final double BOSS_SUMMON_CHANCE = 0.10;
	/** Living called blazes a boss may have at once, so a long fight cannot fill the sky. */
	public static final int BOSS_SUMMON_CAP = 3;
	private static final double SUMMON_SPREAD = 2.0;
	private static final int SPOT_REFRESH_TICKS = 10;
	private static final double SPOT_SPEED = 0.08;
	/** Height changes, tried before any sideways move. */
	private static final int[] HEIGHT_OFFSETS = {0, 2, -2, 4, -4, 6, -6};
	private static final double[] SIDE_OFFSETS = {0.0, 3.0, 6.0};

	public interface Recorder {
		default void fired(final Blaze blaze, final int serverTick) {
		}

		/** The ball a blaze launched, and the length of the flight it was aimed over. */
		default void launched(final Blaze blaze, final StellaSmallFireball ball, final double flightLength) {
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
	private @Nullable Vec3 spot;
	private int spotTicks;

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
		if (!sees && this.reposition(target)) {
			return;
		}

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

	/** Flies toward an air spot with a clear line to the target. False when no spot is near. */
	private boolean reposition(final LivingEntity target) {
		if (this.spot == null || --this.spotTicks <= 0 || !this.clear(this.spot, target)) {
			this.spot = this.findSpot(target);
			this.spotTicks = SPOT_REFRESH_TICKS;
		}

		if (this.spot == null) {
			return false;
		}

		this.blaze.getNavigation().stop();
		Vec3 toSpot = this.spot.subtract(this.blaze.position());
		if (toSpot.lengthSqr() > 0.04) {
			Vec3 push = toSpot.normalize().scale(SPOT_SPEED);
			this.blaze.setDeltaMovement(this.blaze.getDeltaMovement().scale(0.6).add(push));
		}

		return true;
	}

	/** Tries other heights first, nearest first, then spots to the side at the same heights. */
	private @Nullable Vec3 findSpot(final LivingEntity target) {
		Level level = this.blaze.level();
		Vec3 base = this.blaze.position();
		for (int side = 0; side < SIDE_OFFSETS.length; side++) {
			for (int dy : HEIGHT_OFFSETS) {
				for (int dir = 0; dir < 8; dir++) {
					double angle = dir * Math.PI / 4.0;
					double radius = SIDE_OFFSETS[side];
					Vec3 spot = base.add(Math.cos(angle) * radius, dy, Math.sin(angle) * radius);
					if (level.noCollision(this.blaze, this.blaze.getBoundingBox().move(spot.subtract(base))) && this.clear(spot, target)) {
						return spot;
					}

					if (radius == 0.0) {
						break;
					}
				}
			}
		}

		return null;
	}

	private boolean clear(final Vec3 feet, final LivingEntity target) {
		Vec3 eye = feet.add(0.0, this.blaze.getEyeHeight(), 0.0);
		Vec3 end = new Vec3(target.getX(), target.getEyeY(), target.getZ());
		return this.blaze.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.blaze)).getType()
			== HitResult.Type.MISS;
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
		double flight = aim.length();
		if (flight >= MIN_SPLIT_RANGE) {
			ball.setSplitDistance(flight * SPLIT_AT_FRACTION);
		}

		level.addFreshEntity(ball);
		recorder.launched(this.blaze, ball, flight);
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
