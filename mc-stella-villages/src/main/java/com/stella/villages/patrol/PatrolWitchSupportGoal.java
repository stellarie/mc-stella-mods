package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

final class PatrolWitchSupportGoal extends Goal {
	private final Witch witch;
	private final UUID villageId;
	private LivingEntity ally;
	private ItemStack potion = ItemStack.EMPTY;
	private int nextThrowTick;

	PatrolWitchSupportGoal(final Witch witch, final UUID villageId) {
		this.witch = witch;
		this.villageId = villageId;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (this.witch.tickCount < this.nextThrowTick) {
			return false;
		}

		List<Mob> patrol = PatrolService.members(this.witch.level(), this.villageId).stream()
			.filter(mob -> mob != this.witch && mob.isAlive())
			.filter(mob -> mob.distanceToSqr(this.witch) <= PatrolService.WITCH_SUPPORT_RANGE * PatrolService.WITCH_SUPPORT_RANGE)
			.sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(this.witch)))
			.toList();
		List<LivingEntity> allies = new ArrayList<>(patrol);
		allies.addAll(this.witch.level().getEntitiesOfClass(LivingEntity.class,
			this.witch.getBoundingBox().inflate(PatrolService.WITCH_SUPPORT_RANGE),
			entity -> entity.isAlive() && entity.distanceToSqr(this.witch)
					<= PatrolService.WITCH_SUPPORT_RANGE * PatrolService.WITCH_SUPPORT_RANGE
				&& (entity instanceof AbstractVillager || entity instanceof ServerPlayer player && !player.isSpectator()
					&& this.witch.level() instanceof ServerLevel level
					&& !PatrolService.hasThreat(level, this.villageId, player.getUUID()))));
		allies.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(this.witch)));

		for (LivingEntity candidate : allies) {
			if (candidate.getHealth() < candidate.getMaxHealth()) {
				this.ally = candidate;
				this.potion = potion(Potions.HEALING);
				return true;
			}
		}

		for (Mob candidate : patrol) {
			if (!candidate.hasEffect(MobEffects.REGENERATION)) {
				this.ally = candidate;
				this.potion = potion(Potions.REGENERATION);
				return true;
			}

			if (!candidate.hasEffect(MobEffects.SPEED)) {
				this.ally = candidate;
				this.potion = potion(Potions.SWIFTNESS);
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		LivingEntity target = this.ally;
		ItemStack stack = this.potion;
		this.ally = null;
		this.potion = ItemStack.EMPTY;
		if (target == null || stack.isEmpty() || !(this.witch.level() instanceof ServerLevel level)) {
			return;
		}

		this.witch.getLookControl().setLookAt(target);
		this.nextThrowTick = this.witch.tickCount + PatrolService.WITCH_MIN_THROW_INTERVAL;
		Vec3 movement = target.getDeltaMovement();
		double dx = target.getX() + movement.x - this.witch.getX();
		double dz = target.getZ() + movement.z - this.witch.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		double midpoint = target.getY() + target.getBbHeight() * 0.5;
		double dy = midpoint - (this.witch.getEyeY() - 0.1) + 0.015 * distance * distance;
		Projectile.spawnProjectileUsingShoot(
			ThrownSplashPotion::new, level, stack, this.witch, dx, dy, dz, 0.75F, 0.0F
		);
		if (!this.witch.isSilent()) {
			level.playSound(null, this.witch.getX(), this.witch.getY(), this.witch.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
		}

		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=witch_potion village={} witch={} ally={} potion={} tick={}",
			McStellaVillages.MOD_ID, this.villageId, this.witch.getUUID(), target.getUUID(), potionName(stack), this.witch.tickCount
		);
	}

	private static ItemStack potion(final net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
		return PotionContents.createItemStack(Items.SPLASH_POTION, potion);
	}

	private static String potionName(final ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null ? "none" : contents.potion().map(holder -> holder.value().toString()).orElse("custom");
	}
}
