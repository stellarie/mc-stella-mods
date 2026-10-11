package com.stella.tower;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A pillager of the tower's ghosts: it looses its crossbow at the tower's mobs and at nothing else. */
public class GhostPillager extends Pillager implements Ghostly {
	public GhostPillager(final EntityType<? extends GhostPillager> type, final Level level) {
		super(type, level);
		GhostKit.settle(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return GhostKit.paced(Pillager::createAttributes);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new RangedCrossbowAttackGoal<>(this, 1.0, 8.0F));
		GhostSquads.addSquadGoals(this, this.goalSelector);
		this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
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
}
