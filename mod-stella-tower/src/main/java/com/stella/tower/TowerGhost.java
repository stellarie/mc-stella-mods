package com.stella.tower;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A ghost of the tower: it drifts beside the players, never turns on one, and casts at what hunts them,
 * mending and strengthening the players near it. Kill one, and its curse blinds its killer.
 */
public class TowerGhost extends PathfinderMob {
	/** The ticks of its own life at which it next casts, mends and strengthens. */
	int nextCast = TowerGhosts.CAST_TICKS;
	int nextMend = TowerGhosts.HEAL_TICKS;
	int nextStrengthen = TowerGhosts.BUFF_TICKS;

	public TowerGhost(final EntityType<? extends TowerGhost> type, final Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.FLYING_SPEED, 0.5)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected PathNavigation createNavigation(final Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(4, new TowerGhosts.StayNearPlayerGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
		// Only what its bolt can reach: a quarry further off would draw it away from the players it keeps.
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
			(target, level) -> target instanceof Enemy && !(target instanceof TowerGhost) && target.distanceTo(this) <= TowerGhosts.CAST_RANGE));
	}

	/** A ghost never takes a player, or another ghost, for its quarry. */
	@Override
	public void setTarget(final @Nullable LivingEntity target) {
		if (target instanceof Player || target instanceof TowerGhost) {
			return;
		}
		super.setTarget(target);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		TowerGhosts.tend(level, this);
	}

	@Override
	public void travel(final Vec3 input) {
		this.travelFlying(input, this.getSpeed());
	}

	/** Shots pass through a ghost: a volley loosed at a monster never strikes the players' ghost. */
	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}
}
