package com.stella.end;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The charged slash of a Wither Knight. It winds up for {@link #WINDUP} ticks, turned to its target and standing still,
 * then throws back whatever stands on a line {@link #LINE_LENGTH} blocks long and one block wide ahead of it, and lights
 * soul fire on that line. The line ends at a wall. The wind-up counts server ticks and the wait that follows counts the
 * End clock, which a game test moves on.
 */
public final class KnightSlashGoal extends Goal {
	public static final int WINDUP = 40;
	public static final int LINE_LENGTH = 16;
	public static final double RANGE = 12.0;
	public static final int COOLDOWN = 200;
	public static final double KNOCKBACK = 2.0;
	public static final double DAMAGE_FACTOR = 1.5;
	/** Ticks to wait before a slash that was cut short is tried again. */
	private static final int RETRY = 20;
	/** A knight that is carried this far from where its wind-up began loses the slash: the line would point the wrong way. */
	private static final double DRIFT = 1.0;
	/** The telegraph shows its flames this often, in ticks. */
	private static final int SHOW_PERIOD = 4;
	/** The line is as wide as a block; a creature is in it when its middle lies within half a block plus half its own width. */
	private static final double HALF_LINE = 0.5;
	private static final double LINE_HEIGHT = 3.0;
	private static final int KEPT = 64;

	/** One slash: the End clock and the server ticks it began and fell on, its aim, the cells it lit and the creatures it struck. */
	public record Slash(long clock, int began, int released, Vec3 aim, List<BlockPos> lit, List<UUID> struck) {
	}

	private final Mob mob;
	private final List<Slash> slashes = new ArrayList<>();
	private long nextAt;
	private int began;
	private boolean finished;
	private Vec3 aim = Vec3.ZERO;
	private Vec3 anchor = Vec3.ZERO;

	public KnightSlashGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public List<Slash> slashes() {
		return slashes;
	}

	@Override
	public boolean canUse() {
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return false;
		}
		LivingEntity target = this.mob.getTarget();
		return target != null && target.isAlive() && EndClock.now(level) >= this.nextAt && this.mob.distanceTo(target) <= RANGE && this.mob.hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return !this.finished && this.mob.isAlive();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		ServerLevel level = (ServerLevel) this.mob.level();
		LivingEntity target = this.mob.getTarget();
		Vec3 toward = new Vec3(target.getX() - this.mob.getX(), 0.0, target.getZ() - this.mob.getZ());
		this.aim = toward.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, this.mob.getYRot()).multiply(1.0, 0.0, 1.0).normalize() : toward.normalize();
		this.anchor = this.mob.position();
		this.began = level.getServer().getTickCount();
		this.finished = false;
		this.mob.getNavigation().stop();
		BehaviorLog.info("[END] knightSlash windup begins mob={} aim={} target={}", this.mob.getType().getDescriptionId(), this.aim, target.getType().getDescriptionId());
	}

	@Override
	public void stop() {
		if (!this.finished && this.mob.level() instanceof ServerLevel level) {
			this.nextAt = EndClock.now(level) + RETRY;
		}
	}

	@Override
	public void tick() {
		if (this.finished) {
			return;
		}
		ServerLevel level = (ServerLevel) this.mob.level();
		this.face();
		if (this.mob.position().distanceToSqr(this.anchor) > DRIFT * DRIFT) {
			this.finished = true;
			this.nextAt = EndClock.now(level) + RETRY;
			BehaviorLog.info("[END] knightSlash cut short mob={}", this.mob.getType().getDescriptionId());
			return;
		}
		int elapsed = level.getServer().getTickCount() - this.began;
		if (elapsed >= WINDUP) {
			this.release(level);
		} else if (elapsed % SHOW_PERIOD == 0) {
			this.telegraph(level);
		}
	}

	private void face() {
		float yaw = (float) (Mth.atan2(this.aim.z, this.aim.x) * (180.0 / Math.PI)) - 90.0F;
		this.mob.setYRot(yaw);
		this.mob.setYBodyRot(yaw);
		this.mob.setYHeadRot(yaw);
	}

	/** Soul flames over the cells the slash will light, so a player can see where to step. */
	private void telegraph(final ServerLevel level) {
		for (double t = 1.0; t <= LINE_LENGTH; t += 2.0) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.mob.getX() + this.aim.x * t, this.mob.getY() + 0.1, this.mob.getZ() + this.aim.z * t, 1, 0.2, 0.0, 0.2, 0.0);
		}
	}

	private void release(final ServerLevel level) {
		this.finished = true;
		int released = level.getServer().getTickCount();
		long clock = EndClock.now(level);
		this.nextAt = clock + COOLDOWN;
		List<BlockPos> lit = this.light(level);
		List<UUID> struck = this.strike(level);
		level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.mob.getX() + this.aim.x * 1.5, this.mob.getY() + this.mob.getBbHeight() * 0.5, this.mob.getZ() + this.aim.z * 1.5, 1, 0.0, 0.0, 0.0, 0.0);
		this.slashes.add(new Slash(clock, this.began, released, this.aim, List.copyOf(lit), List.copyOf(struck)));
		if (this.slashes.size() > KEPT) {
			this.slashes.remove(0);
		}
		BehaviorLog.info("[END] knightSlash fell mob={} windup={} lit={} struck={}", this.mob.getType().getDescriptionId(), released - this.began, lit.size(), struck.size());
	}

	private static boolean solid(final ServerLevel level, final BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Lights a soul flame on every open cell of the line that has a floor, and stops at the first wall: a cell solid at both knee and head height. */
	private List<BlockPos> light(final ServerLevel level) {
		List<BlockPos> lit = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		int y = this.mob.getBlockY();
		for (double t = 1.0; t <= LINE_LENGTH; t += 0.5) {
			BlockPos cell = BlockPos.containing(this.mob.getX() + this.aim.x * t, y, this.mob.getZ() + this.aim.z * t);
			if (!seen.add(cell)) {
				continue;
			}
			if (solid(level, cell) && solid(level, cell.above())) {
				break;
			}
			BlockState state = level.getBlockState(cell);
			BlockPos under = cell.below();
			if (state.is(EndBlocks.soulFlame())) {
				lit.add(cell);
			} else if (state.isAir() && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
				level.setBlock(cell, EndBlocks.soulFlame().defaultBlockState(), Block.UPDATE_ALL);
				if (level.getBlockState(cell).is(EndBlocks.soulFlame())) {
					lit.add(cell);
				}
			}
		}
		return lit;
	}

	/** Hurts and throws back every creature on the line that is not of the castle, and returns who they were. */
	private List<UUID> strike(final ServerLevel level) {
		List<UUID> struck = new ArrayList<>();
		AABB area = new AABB(this.mob.position(), this.mob.position().add(this.aim.scale(LINE_LENGTH))).inflate(2.0, LINE_HEIGHT + 1.0, 2.0);
		DamageSource source = level.damageSources().mobAttack(this.mob);
		float damage = (float) (this.mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_FACTOR);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != this.mob && entity.isAlive() && !Factions.sameSide(this.mob, entity))) {
			if (victim instanceof Player player && (player.isSpectator() || player.isCreative())) {
				continue;
			}
			Vec3 rel = victim.position().subtract(this.mob.position());
			double along = rel.x * this.aim.x + rel.z * this.aim.z;
			double across = Math.abs(rel.x * this.aim.z - rel.z * this.aim.x);
			boolean inLine = along >= -HALF_LINE && along <= LINE_LENGTH + HALF_LINE && across <= HALF_LINE + victim.getBbWidth() * 0.5 && Math.abs(rel.y) <= LINE_HEIGHT;
			if (inLine) {
				victim.hurtServer(level, source, damage);
				victim.knockback(KNOCKBACK, -this.aim.x, -this.aim.z, source, 0.0F);
				struck.add(victim.getUUID());
			}
		}
		return struck;
	}
}
