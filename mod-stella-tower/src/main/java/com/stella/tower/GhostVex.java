package com.stella.tower;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A vex that a ghost evoker summons. It copies its summoner's quarry, hunts the tower's mobs on its own, and flies at three
 * quarters of a vex's pace: a vex's flight is set by its move control, not by an attribute, so the control is its own.
 */
public class GhostVex extends Vex implements Ghostly {
	public GhostVex(final EntityType<? extends GhostVex> type, final Level level) {
		super(type, level);
		GhostKit.settle(this);
		this.moveControl = new GhostMoveControl(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return GhostKit.paced(Vex::createAttributes);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// Only the vex's copy of its owner's quarry stays; its call to arms and its hunt for a player go.
		this.targetSelector.removeAllGoals(goal -> !goal.getClass().getSimpleName().equals("VexCopyOwnerTargetGoal"));
		GhostKit.hunt(this, this.targetSelector);
	}

	@Override
	public void setTarget(final @Nullable LivingEntity target) {
		if (!GhostKit.refuses(target)) {
			super.setTarget(target);
		}
	}

	@Override
	public boolean canAttack(final LivingEntity target) {
		return !GhostKit.refuses(target) && super.canAttack(target);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		GhostKit.wreathe(level, this);
	}

	/** A ghost leaves no loot, no gear and no experience. */
	@Override
	protected void dropAllDeathLoot(final ServerLevel level, final DamageSource source) {
	}

	@Override
	public @Nullable ItemEntity spawnAtLocation(final ServerLevel level, final ItemStack itemStack, final Vec3 offset) {
		return null;
	}

	@Override
	public boolean isPreventingPlayerRest(final ServerLevel level, final Player player) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(final double distSqr) {
		return false;
	}

	/** The vex's own flight, with its pull toward the wanted spot cut to {@link GhostKit#PACE}. */
	private static final class GhostMoveControl extends MoveControl<GhostVex> {
		private GhostMoveControl(final GhostVex vex) {
			super(vex);
		}

		@Override
		public void tick() {
			if (this.operation != MoveControl.Operation.MOVE_TO) {
				return;
			}
			Vec3 delta = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
			double length = delta.length();
			if (length < this.mob.getBoundingBox().getSize()) {
				this.operation = MoveControl.Operation.WAIT;
				this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.5));
				return;
			}
			this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(delta.scale(this.speedModifier * 0.05 * GhostKit.PACE / length)));
			LivingEntity target = this.mob.getTarget();
			double facingX = target == null ? this.mob.getDeltaMovement().x : target.getX() - this.mob.getX();
			double facingZ = target == null ? this.mob.getDeltaMovement().z : target.getZ() - this.mob.getZ();
			this.mob.setYRot(-((float) net.minecraft.util.Mth.atan2(facingX, facingZ)) * (180.0F / (float) Math.PI));
			this.mob.yBodyRot = this.mob.getYRot();
		}
	}
}
