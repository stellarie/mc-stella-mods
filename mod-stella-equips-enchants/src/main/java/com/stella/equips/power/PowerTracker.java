package com.stella.equips.power;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class PowerTracker {
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
		long survivabilityPoints = survivabilityPoints(player, config);
		PowerState state = PowerState.get(player);
		PowerAttributes.apply(player, survivabilityPoints, state.combatPoints(), config);
		int level = PowerCurve.levelFor(survivabilityPoints + (long) Math.floor(state.combatPoints()), config);
		PowerState.set(player, new PowerState(state.combatPoints(), level));

		if (level == state.level()) {
			return false;
		}

		PowerLog.recorder().powerLevel(
			player.getScoreboardName(), level, state.level(), survivabilityPoints, state.combatPoints()
		);
		return true;
	}

	public static void onDeath(final LivingEntity entity, final DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		PowerConfig config = PowerConfigStore.get();
		Identifier entityType = EntityType.getKey(entity.getType());
		boolean boss = config.kills().isBoss(entityType);
		long basePoints = boss ? config.kills().pointsPerBoss() : config.kills().pointsPerKill();
		int mobLevel = mobLevel(entity, config);
		int playerLevel = playerLevel(player, config);
		double multiplier = PowerCurve.xpMultiplier(mobLevel, playerLevel, config);
		double awarded = basePoints * multiplier;
		PowerState state = PowerState.get(player);
		double total = state.combatPoints() + awarded;
		PowerState.set(player, new PowerState(total, state.level()));
		PowerLog.recorder().powerKill(
			entityType.toString(), boss, mobLevel, playerLevel, multiplier, awarded, total
		);
	}

	private static long survivabilityPoints(final ServerPlayer player, final PowerConfig config) {
		ServerLevel level = (ServerLevel) player.level();
		double scaled = scaledDistance(player.getX(), player.getZ(), level, config);
		return PowerCurve.distancePoints(scaled, config) + PowerCurve.depthPoints(player.getY(), config);
	}

	private static double scaledDistance(
		final double x, final double z, final ServerLevel level, final PowerConfig config
	) {
		BlockPos spawn = level.getRespawnData().pos();
		return PowerCurve.scaledDistance(
			x, z, spawn.getX(), spawn.getZ(), level.dimension().identifier(), config
		);
	}

	public static int mobLevel(final LivingEntity entity, final PowerConfig config) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return 0;
		}

		double scaled = scaledDistance(entity.getX(), entity.getZ(), level, config);
		return PowerCurve.mobLevel((int) Math.floor(scaled), entity.getBlockY(), config);
	}

	public static int playerLevel(final ServerPlayer player, final PowerConfig config) {
		PowerState state = PowerState.get(player);
		return PowerCurve.levelFor(
			survivabilityPoints(player, config) + (long) Math.floor(state.combatPoints()), config
		);
	}
}
