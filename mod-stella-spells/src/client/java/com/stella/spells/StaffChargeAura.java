package com.stella.spells;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/** A soul-fire aura at the head of the held staff while its swing is fully charged. */
public final class StaffChargeAura implements ClientModInitializer {
	private static final int PARTICLES_PER_TICK = 2;
	private static final double RING_RADIUS = 0.22;
	/** How far ahead of the eye, how far beside it and how far below it the staff head sits. */
	private static final double HEAD_AHEAD = 0.9;
	private static final double HEAD_ASIDE = 0.35;
	private static final double HEAD_BELOW = 0.1;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(StaffChargeAura::tick);
	}

	private static void tick(final Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.isPaused() || !StaffPower.firesBolt(player.getMainHandItem())
			|| !StaffBolt.isFull(player.getAttackStrengthScale(0.0F))) {
			return;
		}
		Vec3 look = player.getViewVector(1.0F);
		double yaw = Math.toRadians(player.getYRot());
		double hand = player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
		Vec3 head = player.getEyePosition()
			.add(look.scale(HEAD_AHEAD))
			.add(-Math.cos(yaw) * HEAD_ASIDE * hand, -HEAD_BELOW, -Math.sin(yaw) * HEAD_ASIDE * hand);
		for (int i = 0; i < PARTICLES_PER_TICK; i++) {
			double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
			minecraft.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
				head.x + Math.cos(angle) * RING_RADIUS, head.y + (player.getRandom().nextDouble() - 0.5) * RING_RADIUS,
				head.z + Math.sin(angle) * RING_RADIUS, 0.0, 0.01, 0.0);
		}
	}
}
