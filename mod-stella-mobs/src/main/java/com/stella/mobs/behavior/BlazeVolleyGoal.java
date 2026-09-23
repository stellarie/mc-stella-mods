package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.stella.mobs.projectile.StellaSmallFireball;

/**
 * R24, the elite blaze: one volley every VOLLEY_INTERVAL_TICKS, ten balls spread over the first forty
 * ticks, then a long silence. The ground spread fires once per volley rather than once per ball, because
 * ten overlapping patches at one target are nine redundant patches. The elite flag is a field on this one
 * goal rather than a second class, because the vanilla goal is a private nested class.
 */
public class BlazeVolleyGoal extends Goal {
	public static final int VOLLEY_INTERVAL_TICKS = 200;
	public static final int BALL_SPACING_TICKS = 4;
	public static final int VOLLEY_SIZE = 10;
	public static final int SPREAD_RADIUS = 5;
	/** How far below the target's feet the patch looks for a floor, so an airborne target still gets one. */
	public static final int SPREAD_DEPTH = 4;
	private static final double SPREAD_CHANCE = 0.35;

	private final Blaze blaze;
	private final boolean elite;
	private int ticks;
	private int ballsThisCycle;
	private int nextBallTick = 1;
	private int nextCycleTick = VOLLEY_INTERVAL_TICKS;
	/** True once this cycle's single ground patch has been written. */
	private boolean spreadSpentThisVolley;
	public BlazeVolleyGoal(final Blaze blaze, final boolean elite) {
		this.blaze = blaze;
		this.elite = elite;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.blaze.getTarget();
		return target != null && target.isAlive() && this.blaze.canAttack(target);
	}

	@Override
	public void start() {
		// Nothing resets here on purpose: the cycle is monotonic, because a goal that stops and starts when
		// the target flickers out of sight would otherwise begin a fresh volley every time it restarts.
	}

	@Override
	public void stop() {
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.ticks++;

		// Thresholds, not a phase modulo. A goal that first ticks one tick late still looses ten balls,
		// because the count of balls in the cycle decides the volley and not the value of a counter.
		if (this.ticks >= this.nextCycleTick) {
			this.ballsThisCycle = 0;
			this.spreadSpentThisVolley = false;
			this.nextBallTick = this.ticks + 1;
			this.nextCycleTick = this.ticks + VOLLEY_INTERVAL_TICKS;
		}

		if (this.ballsThisCycle >= VOLLEY_SIZE || this.ticks < this.nextBallTick) {
			return;
		}

		LivingEntity target = this.blaze.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		this.ballsThisCycle++;
		this.nextBallTick = this.ticks + BALL_SPACING_TICKS;
		loose(target);
	}

	private void loose(final LivingEntity target) {
		if (!(this.blaze.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 aim = new Vec3(
			target.getX() - this.blaze.getX(),
			target.getY(0.5) - this.blaze.getY(0.5),
			target.getZ() - this.blaze.getZ()
		);

		if (aim.lengthSqr() < 1.0E-4) {
			return;
		}

		StellaSmallFireball ball = new StellaSmallFireball(
			level, this.blaze.getX(), this.blaze.getY(0.5), this.blaze.getZ(), aim.normalize()
		);
		ball.setOwner(this.blaze);
		level.addFreshEntity(ball);

		if (!this.spreadSpentThisVolley) {
			this.spreadSpentThisVolley = true;
			spread(level, target);
		}
	}

	/** One patch per volley, and nothing at all while mob griefing is off. */
	private void spread(final ServerLevel level, final LivingEntity target) {
		if (!this.elite || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}

		BlockPos centre = target.blockPosition();

		for (int dx = -SPREAD_RADIUS; dx <= SPREAD_RADIUS; dx++) {
			for (int dz = -SPREAD_RADIUS; dz <= SPREAD_RADIUS; dz++) {
				for (int dy = 1; dy >= -SPREAD_DEPTH; dy--) {
					BlockPos pos = centre.offset(dx, dy, dz);

					if (!level.isEmptyBlock(pos) || !level.getBlockState(pos.below()).isSolidRender()) {
						continue;
					}

					if (level.getRandom().nextDouble() >= SPREAD_CHANCE) {
						break;
					}

					BlockState fire = BaseFireBlock.getState(level, pos);
					level.setBlockAndUpdate(pos, fire);
					break;
				}
			}
		}
	}
}
