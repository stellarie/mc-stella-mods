package com.stella.tower.colosseum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The guardians' special moves. A move is a short run of ticks: it shows what it is about to do (a sound, a cloud of
 * particles at the spot it will strike, a raised bow), then does it. A move holds no state outside itself, so the
 * brain can drop it at any tick when its target is lost.
 */
final class GuardianMoves {
	/** A lobbed arrow's launch speed, in blocks a tick. */
	static final double LOB_SPEED = 1.5;

	static final int BLINK_WINDUP = 12;
	static final double BLINK_BEHIND = 2.8;
	static final int CONE_WINDUP = 20;
	static final int CONE_ACTIVE = 10;
	static final double CONE_RANGE = 6.0;
	static final double CONE_HALF_ANGLE = 30.0;
	static final double CONE_DAMAGE = 0.8;
	static final int CONE_BURN_SECONDS = 6;
	static final int SLAM_WINDUP = 25;
	static final double SLAM_LENGTH = 16.0;
	static final double SLAM_HALF_WIDTH = 2.0;
	static final double SLAM_DAMAGE = 1.3;
	static final double SLAM_KNOCKBACK = 1.6;
	static final int SLAM_NAUSEA_TICKS = 40;
	static final int VANISH_TICKS = 40;
	static final int VANISH_STRIKE_AT = 42;
	static final int VANISH_END = 48;
	static final int VOLLEY_WINDUP = 12;
	static final int VOLLEY_ARROWS = 8;
	static final int VOLLEY_GAP = 3;
	static final float VOLLEY_SPEED = 2.0F;
	static final float VOLLEY_SPREAD = 4.0F;
	static final int LOB_WINDUP = 16;
	static final int LOB_ARROWS = 24;
	static final int LOB_GAP = 1;
	/** The lobbed arrows ring the target between these reaches. */
	static final double LOB_NEAR = 2.0;
	static final double LOB_FAR = 12.0;
	/** An arrow with this tag never calls the Blitz Beat falcon; the spells mod reads it. */
	static final String NO_BLITZ_TAG = "mod-stella-spells.no_blitz";
	static final float BURST_POWER = 1.7F;
	static final double BURST_FIRE_RADIUS = 2.2;
	static final int BURST_BURN_SECONDS = 5;

	private GuardianMoves() {
	}

	/** One move, played tick by tick. */
	interface Move {
		/** Plays tick {@code t} (from 0); true when the move is over. */
		boolean tick(GuardianBrain brain, LivingEntity target, int t);

		/** Called once when the move ends, however it ends. */
		default void end(final GuardianBrain brain) {
		}
	}

	static Move create(final MoveKind kind) {
		return switch (kind) {
			case BLINK -> new Blink();
			case FIRE_CONE -> new FireCone();
			case SLAM -> new Slam();
			case VANISH -> new Vanish();
			case VOLLEY -> new Volley();
			case LOB -> new Lob();
		};
	}

	// ---------------------------------------------------------------- melee

	/** The guardian vanishes in a swirl of portal particles and arrives behind its target, and hits. */
	static final class Blink implements Move {
		private @Nullable Vec3 spot;

		@Override
		public boolean tick(final GuardianBrain brain, final LivingEntity target, final int t) {
			ServerLevel level = brain.level();
			if (t == 0) {
				brain.stopMoving();
				brain.face(target);
				sound(level, brain.rider().position(), SoundEvents.ENDERMAN_STARE, 1.6F, 0.7F);
			}
			if (t < BLINK_WINDUP) {
				this.spot = behind(brain, target);
				Vec3 here = brain.mover().position();
				level.sendParticles(ParticleTypes.PORTAL, here.x, here.y + 2.0, here.z, 14, 0.8, 1.6, 0.8, 0.2);
				if (this.spot != null) {
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.spot.x, this.spot.y + 1.5, this.spot.z, 10, 0.7, 1.4, 0.7, 0.05);
				}
				return false;
			}
			if (t == BLINK_WINDUP) {
				if (this.spot == null) {
					return true;
				}
				Vec3 from = brain.mover().position();
				level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 2.0, from.z, 40, 1.0, 2.0, 1.0, 0.3);
				brain.teleport(this.spot, yawTo(this.spot, target.position()));
				brain.face(target);
				sound(level, this.spot, SoundEvents.ENDERMAN_TELEPORT, 1.6F, 0.8F);
				level.sendParticles(ParticleTypes.PORTAL, this.spot.x, this.spot.y + 2.0, this.spot.z, 40, 1.0, 2.0, 1.0, 0.3);
				return false;
			}
			if (target.isAlive()) {
				brain.strike(target);
			}
			return true;
		}

		/** A standing spot behind the target, the way it faces, at about three blocks. */
		private static @Nullable Vec3 behind(final GuardianBrain brain, final LivingEntity target) {
			Vec3 look = target.getLookAngle();
			Vec3 back = new Vec3(-look.x, 0.0, -look.z);
			if (back.lengthSqr() < 1.0E-4) {
				back = flat(target.position(), brain.rider().position());
			} else {
				back = back.normalize();
			}
			for (double distance : new double[] {BLINK_BEHIND, BLINK_BEHIND + 1.0, BLINK_BEHIND - 0.6}) {
				Vec3 at = target.position().add(back.scale(distance));
				Vec3 stand = stand(brain.level(), brain.mover(), at.x, target.getY(), at.z);
				if (stand != null) {
					return stand;
				}
			}
			return null;
		}
	}

	/** A cone of fire: it gathers for a second, then burns whatever stands in the 60 degrees ahead. */
	static final class FireCone implements Move {
		private Vec3 forward = new Vec3(1.0, 0.0, 0.0);

		@Override
		public boolean tick(final GuardianBrain brain, final LivingEntity target, final int t) {
			ServerLevel level = brain.level();
			if (t == 0) {
				brain.stopMoving();
				sound(level, brain.rider().position(), SoundEvents.FIRECHARGE_USE, 1.6F, 0.6F);
			}
			if (t < CONE_WINDUP) {
				this.forward = flat(brain.mover().position(), target.position());
				brain.face(target);
				Vec3 mouth = origin(brain, this.forward, 1.8);
				level.sendParticles(ParticleTypes.SMALL_FLAME, mouth.x, mouth.y, mouth.z, 4 + t / 3, 0.25 + t * 0.02, 0.25, 0.25 + t * 0.02, 0.02);
				if (t % 5 == 0) {
					level.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 8, 0.4, 0.3, 0.4, 0.02);
				}
				return false;
			}
			if (t == CONE_WINDUP) {
				sound(level, brain.rider().position(), SoundEvents.BLAZE_SHOOT, 2.0F, 0.6F);
				burn(brain, level);
			}
			spray(brain, level);
			return t >= CONE_WINDUP + CONE_ACTIVE;
		}

		private void burn(final GuardianBrain brain, final ServerLevel level) {
			Vec3 from = origin(brain, this.forward, 0.0);
			AABB box = new AABB(from, from).inflate(CONE_RANGE + 2.0, 5.0, CONE_RANGE + 2.0);
			for (LivingEntity victim : brain.victims(box)) {
				if (inCone(from, this.forward, victim)) {
					brain.hurt(victim, CONE_DAMAGE);
					victim.igniteForSeconds(CONE_BURN_SECONDS);
				}
			}
		}

		private void spray(final GuardianBrain brain, final ServerLevel level) {
			Vec3 from = origin(brain, this.forward, 1.0);
			for (int i = 0; i < 10; i++) {
				double angle = Math.toRadians((level.getRandom().nextDouble() * 2.0 - 1.0) * CONE_HALF_ANGLE);
				double reach = 1.0 + level.getRandom().nextDouble() * (CONE_RANGE - 1.0);
				Vec3 along = rotateY(this.forward, angle).scale(reach);
				level.sendParticles(ParticleTypes.FLAME, from.x + along.x, from.y + (level.getRandom().nextDouble() - 0.5), from.z + along.z, 1, 0.1, 0.1, 0.1, 0.02);
			}
		}

		private static boolean inCone(final Vec3 from, final Vec3 forward, final LivingEntity victim) {
			Vec3 to = victim.getBoundingBox().getCenter().subtract(from);
			double flat = Math.hypot(to.x, to.z);
			if (flat > CONE_RANGE + victim.getBbWidth() / 2.0 || Math.abs(to.y) > 4.0) {
				return false;
			}
			if (flat < 1.0E-3) {
				return true;
			}
			double cosine = (forward.x * to.x + forward.z * to.z) / flat;
			double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
			double slack = Math.toDegrees(Math.atan2(victim.getBbWidth() / 2.0, Math.max(flat, 0.5)));
			return angle <= CONE_HALF_ANGLE + slack;
		}
	}

	/** The horse rears and the strip ahead is marked in flame; then the ground bursts along it. */
	static final class Slam implements Move {
		private Vec3 forward = new Vec3(1.0, 0.0, 0.0);
		private Vec3 start = Vec3.ZERO;

		@Override
		public boolean tick(final GuardianBrain brain, final LivingEntity target, final int t) {
			ServerLevel level = brain.level();
			if (t == 0) {
				brain.stopMoving();
				if (brain.mount() instanceof AbstractHorse horse) {
					horse.setStanding(SLAM_WINDUP);
				}
				sound(level, brain.rider().position(), SoundEvents.HORSE_ANGRY, 2.0F, 0.5F);
			}
			if (t < SLAM_WINDUP) {
				if (t < SLAM_WINDUP - 3) {
					this.forward = flat(brain.mover().position(), target.position());
					brain.face(target);
				}
				this.start = brain.mover().position().add(this.forward.scale(1.5));
				if (t % 3 == 0) {
					mark(level);
				}
				return false;
			}
			if (t == SLAM_WINDUP) {
				smash(brain, level);
			}
			return t >= SLAM_WINDUP + 4;
		}

		/** The strip's two edges and its far end, in flame, so the line can be read and stepped out of. */
		private void mark(final ServerLevel level) {
			Vec3 side = new Vec3(-this.forward.z, 0.0, this.forward.x).scale(SLAM_HALF_WIDTH);
			for (double along = 0.0; along <= SLAM_LENGTH; along += 2.0) {
				Vec3 centre = this.start.add(this.forward.scale(along));
				level.sendParticles(ParticleTypes.SMALL_FLAME, centre.x + side.x, centre.y + 0.2, centre.z + side.z, 1, 0.05, 0.05, 0.05, 0.0);
				level.sendParticles(ParticleTypes.SMALL_FLAME, centre.x - side.x, centre.y + 0.2, centre.z - side.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}

		private void smash(final GuardianBrain brain, final ServerLevel level) {
			sound(level, this.start, SoundEvents.DRAGON_FIREBALL_EXPLODE, 3.0F, 0.6F);
			for (double along = 1.0; along <= SLAM_LENGTH; along += 2.0) {
				Vec3 centre = this.start.add(this.forward.scale(along));
				level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.5, centre.z, 1, 0.8, 0.2, 0.8, 0.0);
			}
			Vec3 mid = this.start.add(this.forward.scale(SLAM_LENGTH / 2.0));
			AABB box = new AABB(mid, mid).inflate(SLAM_LENGTH / 2.0 + 2.0, 4.0, SLAM_LENGTH / 2.0 + 2.0);
			Vec3 side = new Vec3(-this.forward.z, 0.0, this.forward.x);
			for (LivingEntity victim : brain.victims(box)) {
				Vec3 to = victim.position().subtract(this.start);
				double along = to.x * this.forward.x + to.z * this.forward.z;
				double across = to.x * side.x + to.z * side.z;
				double slack = victim.getBbWidth() / 2.0;
				if (along >= -slack && along <= SLAM_LENGTH + slack && Math.abs(across) <= SLAM_HALF_WIDTH + slack && Math.abs(to.y) <= 4.0) {
					float before = victim.getHealth();
					brain.hurt(victim, SLAM_DAMAGE);
					victim.knockback(SLAM_KNOCKBACK, this.start.x - victim.getX(), this.start.z - victim.getZ(), level.damageSources().mobAttack(brain.rider()), Math.max(0.0F, before - victim.getHealth()));
					victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SLAM_NAUSEA_TICKS, 0, false, true));
				}
			}
		}
	}

	// ---------------------------------------------------------------- ranged

	/** Two seconds unseen, rider and horse, on the move to another side of the target; then a hard shot. */
	static final class Vanish implements Move {
		private @Nullable Vec3 goal;

		@Override
		public boolean tick(final GuardianBrain brain, final LivingEntity target, final int t) {
			ServerLevel level = brain.level();
			if (t == 0) {
				brain.stopMoving();
				MobEffectInstance hide = new MobEffectInstance(MobEffects.INVISIBILITY, VANISH_TICKS, 0, false, false);
				brain.rider().addEffect(hide);
				Mob horse = brain.mount();
				if (horse != null) {
					horse.addEffect(new MobEffectInstance(hide));
				}
				Vec3 here = brain.mover().position();
				sound(level, here, SoundEvents.ENDERMAN_TELEPORT, 1.6F, 1.4F);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, here.x, here.y + 2.0, here.z, 40, 1.0, 2.0, 1.0, 0.05);
				this.goal = sideOf(brain, target);
			}
			if (t < VANISH_TICKS) {
				if (t % 5 == 0 && this.goal != null) {
					brain.mover().getNavigation().moveTo(this.goal.x, this.goal.y, this.goal.z, 1.4);
				}
				return false;
			}
			if (t == VANISH_STRIKE_AT) {
				brain.stopMoving();
				brain.face(target);
				Vec3 here = brain.mover().position();
				level.sendParticles(ParticleTypes.CRIT, here.x, here.y + 3.0, here.z, 24, 0.8, 1.0, 0.8, 0.3);
				sound(level, here, SoundEvents.EVOKER_CAST_SPELL, 1.4F, 1.2F);
				brain.shoot(target.getBoundingBox().getCenter(), 3.0F, 0.0F, true);
			}
			return t >= VANISH_END;
		}

		/** A spot about twelve blocks from the target, round to one side of where the guardian stands. */
		private static @Nullable Vec3 sideOf(final GuardianBrain brain, final LivingEntity target) {
			Vec3 away = brain.rider().position().subtract(target.position());
			double base = Math.atan2(away.z, away.x);
			for (double turn : new double[] {Math.toRadians(80.0), Math.toRadians(-80.0), Math.toRadians(130.0), Math.toRadians(-130.0)}) {
				Vec3 spot = target.position().add(Math.cos(base + turn) * 12.0, 0.0, Math.sin(base + turn) * 12.0);
				if (brain.insideArena(spot)) {
					return spot;
				}
			}
			return null;
		}
	}

	/** The bow is drawn and held for most of a second; then eight arrows leave three ticks apart. */
	static final class Volley implements Move {
		@Override
		public boolean tick(final GuardianBrain brain, final LivingEntity target, final int t) {
			ServerLevel level = brain.level();
			if (t == 0) {
				brain.stopMoving();
				brain.rider().startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
				sound(level, brain.rider().position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1.6F, 1.3F);
			}
			if (t < VOLLEY_WINDUP) {
				brain.face(target);
				if (t % 3 == 0) {
					Vec3 hand = brain.rider().position().add(0.0, brain.rider().getBbHeight() * 0.7, 0.0);
					level.sendParticles(ParticleTypes.CRIT, hand.x, hand.y, hand.z, 6, 0.5, 0.4, 0.5, 0.1);
				}
				return false;
			}
			int shot = t - VOLLEY_WINDUP;
			if (shot % VOLLEY_GAP == 0 && shot / VOLLEY_GAP < VOLLEY_ARROWS) {
				brain.face(target);
				net.minecraft.world.entity.projectile.arrow.Arrow arrow = brain.shoot(target.getBoundingBox().getCenter(), VOLLEY_SPEED, VOLLEY_SPREAD, false);
				if (arrow != null) {
					arrow.addTag(NO_BLITZ_TAG);
				}
			}
			return shot >= VOLLEY_GAP * (VOLLEY_ARROWS - 1) + 2;
		}

		@Override
		public void end(final GuardianBrain brain) {
			brain.rider().stopUsingItem();
		}
	}

	/** Twenty-four burning arrows are lobbed to land over a wide ring round the target; the spots glow first, and each arrow bursts where it falls. */
	static final class Lob implements Move {
		private Vec3[] points = new Vec3[LOB_ARROWS];

		@Override
		public boolean tick(final GuardianBrain brain, final LivingEntity target, final int t) {
			ServerLevel level = brain.level();
			if (t == 0) {
				brain.stopMoving();
				brain.rider().startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
				sound(level, brain.rider().position(), SoundEvents.BLAZE_AMBIENT, 2.0F, 0.6F);
			}
			if (t < LOB_WINDUP) {
				brain.face(target);
				if (t == 0 || t == LOB_WINDUP - 1) {
					this.points = spots(target, level);
				}
				if (t % 3 == 0) {
					for (Vec3 point : this.points) {
						level.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.1, point.z, 3, 0.25, 0.05, 0.25, 0.01);
					}
				}
				return false;
			}
			int shot = t - LOB_WINDUP;
			if (shot % LOB_GAP == 0 && shot / LOB_GAP < LOB_ARROWS) {
				brain.lob(this.points[shot / LOB_GAP]);
			}
			return shot >= LOB_GAP * LOB_ARROWS;
		}

		@Override
		public void end(final GuardianBrain brain) {
			brain.rider().stopUsingItem();
		}

		/** The first falls on the target; the rest spread over a wide ring between the near and far reach. */
		private static Vec3[] spots(final LivingEntity target, final ServerLevel level) {
			Vec3[] points = new Vec3[LOB_ARROWS];
			Vec3 centre = target.position();
			points[0] = centre;
			double turn = level.getRandom().nextDouble() * Math.PI * 2.0;
			for (int i = 1; i < LOB_ARROWS; i++) {
				double angle = turn + Math.PI * 2.0 * i / (LOB_ARROWS - 1);
				double radius = LOB_NEAR + Math.sqrt(level.getRandom().nextDouble()) * (LOB_FAR - LOB_NEAR);
				points[i] = centre.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
			}
			return points;
		}
	}

	// ---------------------------------------------------------------- the burst

	/** Hurts and pushes living bodies only, and never a guardian: the blast is for the one the guardian fights. */
	private static final ExplosionDamageCalculator BODIES = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(final Explosion explosion, final Entity entity) {
			return entity instanceof LivingEntity && !Guardians.isPart(entity) && super.shouldDamageEntity(explosion, entity);
		}

		@Override
		public float getKnockbackMultiplier(final Entity entity) {
			return Guardians.isPart(entity) ? 0.0F : super.getKnockbackMultiplier(entity);
		}
	};

	/** Where a lobbed arrow fell: a blast that hurts bodies and breaks no block, and fire on the ground round it. */
	static void burst(final GuardianBrain brain, final ServerLevel level, final Vec3 at) {
		level.explode(brain.rider(), null, BODIES, at.x, at.y, at.z, BURST_POWER, false, Level.ExplosionInteraction.NONE);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 30, 1.2, 0.3, 1.2, 0.05);
		int lit = 0;
		int reach = (int) Math.ceil(BURST_FIRE_RADIUS);
		BlockPos centre = BlockPos.containing(at);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx * dx + dz * dz > BURST_FIRE_RADIUS * BURST_FIRE_RADIUS || level.getRandom().nextDouble() > 0.75) {
					continue;
				}
				for (int dy = 2; dy >= -3; dy--) {
					BlockPos pos = centre.offset(dx, dy, dz);
					BlockState fire = Blocks.FIRE.defaultBlockState();
					if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir() && fire.canSurvive(level, pos)) {
						level.setBlockAndUpdate(pos, fire);
						lit++;
						break;
					}
				}
			}
		}
		AABB box = new AABB(at, at).inflate(BURST_FIRE_RADIUS + 0.5);
		for (LivingEntity victim : brain.victims(box)) {
			victim.igniteForSeconds(BURST_BURN_SECONDS);
		}
		com.stella.tower.StellaTower.LOGGER.debug("[COLOSSEUM] burst at {} lit {}", at, lit);
	}

	// ---------------------------------------------------------------- geometry

	/** The horizontal unit vector from one spot to another; east if they coincide. */
	static Vec3 flat(final Vec3 from, final Vec3 to) {
		Vec3 d = new Vec3(to.x - from.x, 0.0, to.z - from.z);
		return d.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : d.normalize();
	}

	static Vec3 rotateY(final Vec3 v, final double radians) {
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	/** A spot ahead of the guardian's horse, at about the height of the horse's head. */
	static Vec3 origin(final GuardianBrain brain, final Vec3 forward, final double ahead) {
		Mob body = brain.mover();
		return body.position().add(forward.x * ahead, body.getBbHeight() * 0.5, forward.z * ahead);
	}

	/** The yaw, in degrees, that faces from one spot to another. */
	static float yawTo(final Vec3 from, final Vec3 to) {
		return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
	}

	/** A spot to stand a body of the mover's size on, near a column, or null if none fits. */
	static @Nullable Vec3 stand(final ServerLevel level, final Mob body, final double x, final double y, final double z) {
		for (int dy = 3; dy >= -4; dy--) {
			BlockPos feet = BlockPos.containing(x, y + dy, z);
			if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
				continue;
			}
			Vec3 at = new Vec3(x, feet.getY(), z);
			AABB box = body.getDimensions(body.getPose()).makeBoundingBox(at).expandTowards(0.0, 3.0, 0.0);
			if (level.noCollision(body, box) && !level.containsAnyLiquid(box)) {
				return at;
			}
		}
		return null;
	}

	static void sound(final ServerLevel level, final Vec3 at, final SoundEvent sound, final float volume, final float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}
}
