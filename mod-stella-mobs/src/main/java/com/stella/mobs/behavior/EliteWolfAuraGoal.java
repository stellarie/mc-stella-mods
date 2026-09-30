package com.stella.mobs.behavior;

import com.stella.mobs.entity.BlueSlime;
import com.stella.mobs.StellaMobs;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * The elite wolf's aura, every two seconds: hostiles close by are slowed and weakened, and allies close by
 * are sped and strengthened. A wild elite's allies are the wolves of its pack; a tamed one's are every
 * player, tamed pet and wolf around it. It takes no control flag, so it runs beside every other goal.
 */
final class EliteWolfAuraGoal extends Goal {
	private final Wolf wolf;

	EliteWolfAuraGoal(final Wolf wolf) {
		this.wolf = wolf;
	}

	@Override
	public boolean canUse() {
		return this.wolf.isAlive();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!(this.wolf.level() instanceof ServerLevel level) || level.getServer().getTickCount() % WolfPack.AURA_INTERVAL_TICKS != 0) {
			return;
		}
		double radius = WolfPack.AURA_RADIUS;
		List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, this.wolf.getBoundingBox().inflate(radius),
			other -> other.isAlive() && other.distanceToSqr(this.wolf) <= radius * radius);
		int hostiles = 0;
		int allies = 0;
		for (LivingEntity other : around) {
			if (other instanceof Enemy && !FriendlyPatrol.isVillageFriend(other) && !BlueSlime.isHarmless(other)) {
				other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WolfPack.AURA_TICKS, WolfPack.AURA_AMPLIFIER), this.wolf);
				other.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WolfPack.AURA_TICKS, WolfPack.AURA_AMPLIFIER), this.wolf);
				hostiles++;
			} else if (isAlly(other)) {
				other.addEffect(new MobEffectInstance(MobEffects.SPEED, WolfPack.AURA_TICKS, WolfPack.AURA_AMPLIFIER), this.wolf);
				other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, WolfPack.AURA_TICKS, WolfPack.AURA_AMPLIFIER), this.wolf);
				allies++;
			}
		}
		if (hostiles + allies > 0) {
			StellaMobs.LOGGER.info("[BEHAVIOR] test=wolfPack AURA wolf={} hostiles={} allies={}", this.wolf.getUUID(), hostiles, allies);
		}
	}

	private boolean isAlly(final LivingEntity other) {
		if (other instanceof Wolf) {
			return true;
		}
		if (!this.wolf.isTame()) {
			return false;
		}
		return other instanceof Player player && !player.isSpectator()
			|| other instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
	}
}
