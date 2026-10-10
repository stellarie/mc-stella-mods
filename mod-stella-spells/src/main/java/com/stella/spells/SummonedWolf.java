package com.stella.spells;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wolf Summon Wolf raises: a wolf tamed to its caster. It guards the caster, follows it, carries the tamed
 * elite wolf aura, and can be ridden. It is a new entity type so the ride and the goals need no change to a
 * vanilla wolf, and the wolf renderer draws it. It never sits, never feeds on a farm animal, and never attacks one.
 */
public final class SummonedWolf extends Wolf {
	/** The aura reach, how often it pulses, how long each pulse lasts, and its strength: the tamed elite wolf numbers. */
	public static final double AURA_RADIUS = 8.0;
	public static final int AURA_INTERVAL_TICKS = 40;
	public static final int AURA_TICKS = 80;
	public static final int AURA_AMPLIFIER = 0;
	/** How far from its master a hostile has to stand for the wolf to take it, and how far ahead a rider it bites. */
	public static final double GUARD_RADIUS = 6.0;
	public static final double RIDDEN_REACH = 3.0;
	public static final int RIDDEN_BITE_INTERVAL_TICKS = 10;
	private static final double RIDDEN_CONE = 0.5;
	private static final float FOLLOW_START = 5.0F;
	private static final float FOLLOW_STOP = 2.0F;
	private static final double CHASE_SPEED = 1.3;
	private static final double FOLLOW_SPEED = 1.2;
	private static final double SIDE_STEP_SCALE = 0.5;
	private static final double BACK_STEP_SCALE = 0.25;

	public SummonedWolf(final EntityType<? extends Wolf> type, final Level level) {
		super(type, level);
	}

	/** True for the four farm animals a summoned wolf never attacks. */
	public static boolean isFarmAnimal(final Entity entity) {
		return entity instanceof Sheep || entity instanceof Cow || entity instanceof Pig || entity instanceof Chicken;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, CHASE_SPEED, true));
		this.goalSelector.addGoal(3, new FollowOwnerGoal(this, FOLLOW_SPEED, FOLLOW_START, FOLLOW_STOP));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new GuardGoal(this));
	}

	@Override
	public boolean canAttack(final LivingEntity target) {
		return !isFarmAnimal(target) && super.canAttack(target);
	}

	@Override
	public void setTarget(final @Nullable LivingEntity target) {
		super.setTarget(target != null && !this.canAttack(target) ? null : target);
	}

	@Override
	public boolean wantsToAttack(final LivingEntity target, final LivingEntity owner) {
		return !isFarmAnimal(target) && super.wantsToAttack(target, owner);
	}

	@Override
	public void setOrderedToSit(final boolean sit) {
	}

	@Override
	public void setInSittingPose(final boolean sit) {
	}

	@Override
	public boolean isInSittingPose() {
		return false;
	}

	@Override
	public boolean isOrderedToSit() {
		return false;
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		if (this.isOwnedBy(player) && hand == InteractionHand.MAIN_HAND && !player.isSecondaryUseActive()
			&& player.getItemInHand(hand).isEmpty()) {
			if (!this.level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player rider && this.isOwnedBy(rider) ? rider : null;
	}

	@Override
	protected void tickRidden(final Player rider, final Vec3 input) {
		super.tickRidden(rider, input);
		Vec2 look = new Vec2(rider.getXRot() * 0.5F, rider.getYRot());
		this.setRot(look.y, look.x);
		this.yRotO = this.getYRot();
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	@Override
	protected Vec3 getRiddenInput(final Player rider, final Vec3 input) {
		double forward = rider.zza < 0.0F ? rider.zza * BACK_STEP_SCALE : rider.zza;
		return new Vec3(rider.xxa * SIDE_STEP_SCALE, 0.0, forward);
	}

	@Override
	protected float getRiddenSpeed(final Player rider) {
		return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || !this.isAlive()) {
			return;
		}
		if (level.getServer().getTickCount() % AURA_INTERVAL_TICKS == 0) {
			pulse(level);
		}
		if (this.getControllingPassenger() != null && this.tickCount % RIDDEN_BITE_INTERVAL_TICKS == 0) {
			biteAhead(level, this.getControllingPassenger());
		}
	}

	// Every two seconds: hostiles near the wolf are slowed and weakened, allies near it are sped and strengthened.
	private void pulse(final ServerLevel level) {
		List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(AURA_RADIUS),
			other -> other.isAlive() && other.distanceToSqr(this) <= AURA_RADIUS * AURA_RADIUS);
		for (LivingEntity other : around) {
			if (Friends.isFoe(other)) {
				other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, AURA_TICKS, AURA_AMPLIFIER), this);
				other.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, AURA_TICKS, AURA_AMPLIFIER), this);
			} else if (isAlly(other)) {
				other.addEffect(new MobEffectInstance(MobEffects.SPEED, AURA_TICKS, AURA_AMPLIFIER), this);
				other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, AURA_TICKS, AURA_AMPLIFIER), this);
			}
		}
	}

	private static boolean isAlly(final LivingEntity other) {
		return other instanceof Wolf
			|| other instanceof Player player && !player.isSpectator()
			|| other instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
	}

	// A rider steers; the wolf bites the hostile in front of the rider, never a farm animal.
	private void biteAhead(final ServerLevel level, final LivingEntity rider) {
		Vec3 look = rider.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0.0, look.z);
		if (forward.lengthSqr() < 1.0E-4) {
			return;
		}
		forward = forward.normalize();
		Mob best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob foe : level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(RIDDEN_REACH))) {
			if (foe == this || !foe.isAlive() || !Friends.isFoe(foe) || isFarmAnimal(foe)) {
				continue;
			}
			Vec3 offset = foe.position().subtract(this.position());
			double distance = offset.length();
			Vec3 flat = new Vec3(offset.x, 0.0, offset.z);
			if (distance > RIDDEN_REACH + foe.getBbWidth() || flat.lengthSqr() < 1.0E-6 || flat.normalize().dot(forward) < RIDDEN_CONE) {
				continue;
			}
			if (distance < bestDistance) {
				bestDistance = distance;
				best = foe;
			}
		}
		if (best != null) {
			this.doHurtTarget(level, best);
		}
	}

	/** The hostile this wolf takes for its master now, or null: within the guard radius of the master, never a farm animal. */
	@Nullable Mob guardTarget() {
		if (!(this.getOwner() instanceof ServerPlayer master) || this.isVehicle() || master.level() != this.level()) {
			return null;
		}
		Mob best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob foe : this.level().getEntitiesOfClass(Mob.class, master.getBoundingBox().inflate(GUARD_RADIUS))) {
			if (foe == this || !foe.isAlive() || !Friends.isFoe(foe) || isFarmAnimal(foe) || foe.distanceTo(master) > GUARD_RADIUS) {
				continue;
			}
			double distance = foe.distanceToSqr(this);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = foe;
			}
		}
		return best;
	}

	/** A foe is worth keeping while it lives and stays near the master. */
	boolean stillGuarded(final LivingEntity foe) {
		return foe.isAlive() && this.getOwner() instanceof ServerPlayer master && master.level() == foe.level()
			&& foe.distanceTo(master) <= GUARD_RADIUS * 2.0 && !isFarmAnimal(foe);
	}

	private static final class GuardGoal extends Goal {
		private final SummonedWolf wolf;
		private @Nullable Mob found;

		GuardGoal(final SummonedWolf wolf) {
			this.wolf = wolf;
			this.setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			this.found = this.wolf.getTarget() == null ? this.wolf.guardTarget() : null;
			return this.found != null;
		}

		@Override
		public void start() {
			this.wolf.setTarget(this.found);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = this.wolf.getTarget();
			return target != null && !this.wolf.isVehicle() && this.wolf.stillGuarded(target);
		}

		@Override
		public void stop() {
			this.wolf.setTarget(null);
		}
	}
}
