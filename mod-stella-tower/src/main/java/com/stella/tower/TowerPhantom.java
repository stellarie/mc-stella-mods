package com.stella.tower;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tower's phantom: a phantom that carries a skeleton archer and never swoops. It holds a height above the player it
 * follows and circles there, so its archer can shoot ({@link TowerPhantoms}). It is a type of its own, so that how it
 * flies is built in and survives a save, which a vanilla phantom's goals do not.
 * <p>
 * Its one goal, {@link HoldAboveGoal}, sets its velocity itself each tick. The vanilla move control, which steers a
 * phantom toward a point of its own on each swoop, is replaced by one that does nothing.
 */
public class TowerPhantom extends Phantom {
	public TowerPhantom(final EntityType<? extends TowerPhantom> type, final Level level) {
		super(type, level);
		this.moveControl = new MoveControl(this);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new HoldAboveGoal(this));
	}

	/**
	 * The archer never steers. A mob that rides a mob takes over its mount's movement and look goals, which would leave
	 * {@link HoldAboveGoal} switched off and the phantom flying wherever the archer's own walk sends it.
	 */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return null;
	}

	/**
	 * Circles at a fixed height over the nearest player on the floor, and over its own post while none is in reach. The
	 * height is {@link TowerPhantoms#HOLD_MIN} to {@link TowerPhantoms#HOLD_MAX} blocks over the player's feet, a little
	 * different for each phantom, and it never goes below it while a player is near: it is not steered down, only up, so
	 * a blow that pushes it does not bring it to the player.
	 */
	static final class HoldAboveGoal extends Goal {
		/** The farthest across a player is followed from, in blocks. */
		static final double FOLLOW = 48.0;
		/** How far outside its circle a phantom flies straight for the nearest point of it, in blocks. */
		private static final double APPROACH = 6.0;
		private static final double TOP_SPEED = 0.55;
		/** How far ahead on its circle the point it steers for lies, in radians. */
		private static final double LEAD = 0.35;
		private static final double CEILING_CLEAR = 2.0;
		private final TowerPhantom phantom;
		private final double radius;
		private final double rise;
		private final int spin;
		private final double phase;
		private @Nullable Player target;
		private @Nullable Vec3 post;

		HoldAboveGoal(final TowerPhantom phantom) {
			this.phantom = phantom;
			long bits = phantom.getUUID().getLeastSignificantBits();
			this.radius = 3.0 + (bits & 3) * 0.5;
			this.rise = 11.0 + ((bits >>> 2) & 3) * 0.5;
			this.spin = ((bits >>> 4) & 1) == 0 ? 1 : -1;
			this.phase = ((bits >>> 5) & 63) / 10.0;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private boolean valid(final Player player) {
			return player.isAlive() && !player.isSpectator() && !player.isCreative() && player.level() == this.phantom.level() && !TowerSafety.sheltered(player)
				&& Math.hypot(player.getX() - this.phantom.getX(), player.getZ() - this.phantom.getZ()) <= FOLLOW;
		}

		private @Nullable Player nearest(final ServerLevel level) {
			Player best = null;
			double bestAway = Double.MAX_VALUE;
			for (Player player : level.players()) {
				double away = Math.hypot(player.getX() - this.phantom.getX(), player.getZ() - this.phantom.getZ());
				if (valid(player) && away < bestAway) {
					bestAway = away;
					best = player;
				}
			}
			return best;
		}

		@Override
		public void tick() {
			if (!(this.phantom.level() instanceof ServerLevel level)) {
				return;
			}
			Vec3 here = this.phantom.position();
			if (this.post == null) {
				this.post = here;
			}
			if (this.target == null || !valid(this.target) || this.phantom.tickCount % 20 == 0) {
				this.target = nearest(level);
			}
			double ceiling = TowerLayout.BASE_Y + TowerLayout.ROOM_HEIGHT - CEILING_CLEAR;
			Vec3 want;
			if (this.target != null) {
				Vec3 centre = this.target.position();
				double dx = here.x - centre.x;
				double dz = here.z - centre.z;
				double across = Math.hypot(dx, dz);
				double hold = this.rise + 0.8 * Math.sin(this.phantom.tickCount * 0.05 + this.phase);
				double y = Math.min(centre.y + hold, ceiling);
				double angle = Math.atan2(dz, dx) + (across > this.radius + APPROACH ? 0.0 : this.spin * LEAD);
				want = new Vec3(centre.x + Math.cos(angle) * this.radius, y, centre.z + Math.sin(angle) * this.radius);
				// Pushed under its height near the player, it is lifted and never lowered.
				if (across < FOLLOW && here.y < centre.y + TowerPhantoms.HOLD_MIN - 0.5 && y > here.y) {
					want = new Vec3(want.x, Math.max(want.y, here.y + 1.0), want.z);
				}
			} else {
				double dx = here.x - this.post.x;
				double dz = here.z - this.post.z;
				double angle = Math.atan2(dz, dx) + this.spin * LEAD;
				double wide = this.radius * 3.0;
				want = new Vec3(this.post.x + Math.cos(angle) * wide, Math.min(this.post.y, ceiling), this.post.z + Math.sin(angle) * wide);
			}
			steer(level, want);
		}

		private void steer(final ServerLevel level, final Vec3 want) {
			Vec3 delta = want.subtract(this.phantom.position());
			double dist = delta.length();
			Vec3 desired = dist < 1.0E-3 ? Vec3.ZERO : delta.scale(Math.min(TOP_SPEED, dist * 0.3) / dist);
			if (!level.noCollision(this.phantom, this.phantom.getBoundingBox().move(desired.scale(2.0)))) {
				// Something stands in the way, a tower or a hill's flank: over it.
				desired = new Vec3(desired.x * 0.2, 0.35, desired.z * 0.2);
			}
			Vec3 velocity = this.phantom.getDeltaMovement().lerp(desired, 0.2);
			this.phantom.setDeltaMovement(velocity);
			if (Math.hypot(velocity.x, velocity.z) > 0.02) {
				float yaw = (float) Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0F;
				this.phantom.setYRot(yaw);
				this.phantom.yBodyRot = yaw;
				this.phantom.yHeadRot = yaw;
			}
			this.phantom.setXRot(0.0F);
		}
	}
}
