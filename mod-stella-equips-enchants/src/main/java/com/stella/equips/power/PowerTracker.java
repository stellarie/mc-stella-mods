package com.stella.equips.power;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class PowerTracker {
	private static final double DEATH_LOSS = 0.05;
	private static int serverTicks;
	private static boolean registered;

	private PowerTracker() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PowerTracker::onServerTick);
		ServerLivingEntityEvents.AFTER_DEATH.register(PowerTracker::onDeath);
		registered = true;
	}

	public static boolean isRegistered() {
		return registered;
	}

	public static int serverTicks() {
		return serverTicks;
	}

	public static void onServerTick(final MinecraftServer server) {
		serverTicks++;
		PowerConfig config = PowerConfigStore.get();
		boolean changed = false;

		if (serverTicks % config.tracker().scanIntervalTicks() == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				changed |= scanPlayer(player, config);
			}
		}

		if (!changed && serverTicks % config.tracker().heartbeatTicks() == 0) {
			PowerLog.recorder().powerHeartbeat(serverTicks, server.getPlayerList().getPlayers().size());
		}
	}

	public static boolean scanPlayer(final ServerPlayer player, final PowerConfig config) {
		PowerState state = PowerState.get(player);
		int level = PowerCurve.levelFor((long) Math.floor(state.combatPoints()), config);
		PowerAttributes.apply(player, level, config);
		PowerState.set(player, new PowerState(state.combatPoints(), level));

		if (level == state.level()) {
			return false;
		}

		PowerLog.recorder().powerLevel(player.getScoreboardName(), level, state.level(), state.combatPoints());
		return true;
	}

	public static void onDeath(final LivingEntity entity, final DamageSource source) {
		PowerConfig config = PowerConfigStore.get();
		if (entity instanceof ServerPlayer dead) {
			PowerState state = PowerState.get(dead);
			double lost = state.combatPoints() * DEATH_LOSS;
			double remaining = state.combatPoints() - lost;
			int level = PowerCurve.levelFor((long) Math.floor(remaining), config);
			PowerState.set(dead, new PowerState(remaining, level));
			PowerLog.recorder().powerDeath(dead.getScoreboardName(), lost, remaining, level);
		}

		if (!(source.getEntity() instanceof ServerPlayer killer) || killer == entity) {
			return;
		}

		int mobLevel = mobLevel(entity, config);
		int award = PowerCurve.awardFor(mobLevel, config);
		PowerState state = PowerState.get(killer);
		double total = state.combatPoints() + award;
		PowerState.set(killer, new PowerState(total, state.level()));
		PowerLog.recorder().powerKill(
			EntityType.getKey(entity.getType()).toString(), mobLevel, award, total
		);
	}

	public static int playerLevel(final ServerPlayer player, final PowerConfig config) {
		return PowerCurve.levelFor((long) Math.floor(PowerState.get(player).combatPoints()), config);
	}

	public static int mobLevel(final LivingEntity entity, final PowerConfig config) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return 0;
		}

		double scaled = PowerCurve.scaledDistance(
			entity.getX(),
			entity.getZ(),
			level.getRespawnData().pos().getX(),
			level.getRespawnData().pos().getZ(),
			level.dimension().identifier(),
			config
		);
		return PowerCurve.mobLevel((int) Math.floor(scaled), entity.getBlockY(), config);
	}
}
