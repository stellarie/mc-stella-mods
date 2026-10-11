package com.stella.tower;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A witch of the tower's ghosts. It throws potions at the tower's mobs and at nothing else: a strong potion of harming, or of
 * healing at the undead, which the potion harms. A splash that reaches a player does them no harm: the tower spares a player
 * every blow of a ghost.
 */
public class GhostWitch extends Witch implements Ghostly {
	public GhostWitch(final EntityType<? extends GhostWitch> type, final Level level) {
		super(type, level);
		GhostKit.settle(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return GhostKit.paced(Witch::createAttributes);
	}

	@Override
	protected void registerGoals() {
		// The witch keeps handles on its own target goals, so its goals are made first and then stripped.
		super.registerGoals();
		GhostKit.clearVanilla(this.goalSelector, this.targetSelector);
		GhostSquads.addSquadGoals(this, this.goalSelector);
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
	public void performRangedAttack(final LivingEntity target, final float power) {
		if (this.isDrinkingPotion() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 moving = target.getDeltaMovement();
		double dx = target.getX() + moving.x - this.getX();
		double dy = target.getEyeY() - 1.1F - this.getY();
		double dz = target.getZ() + moving.z - this.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		Holder<Potion> potion = potionFor(target);
		ItemStack thrown = PotionContents.createItemStack(Items.SPLASH_POTION, potion);
		Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, level, thrown, this, dx, dy + distance * 0.2, dz,
			distance <= 2.0 ? 0.45F : 0.75F, this.rangedAttackUncertainty(level));
		if (!this.isSilent()) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_THROW, this.getSoundSource(), 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
		}
	}

	/** What the witch throws at a quarry: harming, or healing for the undead, whom healing harms. */
	public static Holder<Potion> potionFor(final LivingEntity target) {
		return target.is(EntityTypeTags.INVERTED_HEALING_AND_HARM) ? Potions.STRONG_HEALING : Potions.STRONG_HARMING;
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
