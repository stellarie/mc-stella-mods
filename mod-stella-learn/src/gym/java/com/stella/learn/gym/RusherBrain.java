package com.stella.learn.gym;

import java.util.function.Supplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The scripted opponent: face the mob, sprint at it, and swing whenever the attack cooldown is full. */
public final class RusherBrain implements Bot.Brain {
	private final Supplier<Mob> target;

	public RusherBrain(final Supplier<Mob> target) {
		this.target = target;
	}

	@Override
	public BotInput decide(final Bot bot) {
		Mob mob = target.get();
		if (mob == null || !mob.isAlive()) {
			return BotInput.IDLE;
		}

		Vec3 eye = bot.player().getEyePosition();
		AABB box = mob.getBoundingBox();
		Vec3 aim = new Vec3(mob.getX(), Math.min(Math.max(eye.y, box.minY), box.maxY), mob.getZ());
		float yaw = Bot.yawTo(eye, aim);
		float pitch = Bot.pitchTo(eye, aim);
		double reach = bot.player().getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		boolean ready = bot.player().getAttackStrengthScale(0.0F) >= 1.0F;
		boolean inReach = box.distanceToSqr(eye) <= reach * reach;
		return new BotInput(1.0F, 0.0F, false, true, false, yaw, pitch, ready && inReach, false, 0);
	}
}
