package com.stella.tower;

import java.util.Random;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The weather a biome floor sets on a player standing in the open. The tower has no sky, so the storms are done on the
 * server: a wind that blows along the floor, a frost that builds on a body in the snowstorm, and a gust that slows a body
 * in the desert. Each effect is bounded and runs only every {@value #INTERVAL} ticks.
 * <ul>
 * <li>Snowstorm: {@value #FREEZE_ADD} freeze ticks each time, up to {@value #FREEZE_CAP}, the cap at which vanilla's own
 * freezing starts to hurt. A body that vanilla's freezing spares, such as one in leather boots, takes none. The vanilla
 * thaw takes twenty back between two storms, so the frost builds four at a time.</li>
 * <li>Desert: for {@value #GUST_LENGTH} ticks in every {@value #GUST_PERIOD}, Slowness I.</li>
 * <li>Both: a push of at most {@value #PUSH} along the floor's wind.</li>
 * </ul>
 */
public final class TowerWeather {
	public static final int INTERVAL = 10;
	public static final int FREEZE_ADD = 24;
	public static final int FREEZE_CAP = 140;
	public static final double PUSH = 0.05;
	public static final int GUST_PERIOD = 300;
	public static final int GUST_LENGTH = 60;
	private static final int GUST_EFFECT_TICKS = 40;
	private static final long SALT = 0x57494E44L;

	private TowerWeather() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerWeather::tick);
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null) {
			return;
		}
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (floor < 1 || player.isSpectator() || player.isCreative() || !TowerFloors.isBuilt(tower, floor)) {
				continue;
			}
			TowerPlan plan = TowerPlan.atQuiet(tower, floor);
			if (plan.isBiome()) {
				affect(player, plan, server.getTickCount());
			}
		}
	}

	/** One round of the floor's weather on a player: nothing under a roof, in a biome without weather, or off the floor. */
	static void affect(final ServerPlayer player, final TowerPlan plan, final int tick) {
		FloorBiome.Weather weather = plan.biome().biome().weather();
		if (weather == FloorBiome.Weather.NONE || !inTheOpen(player, plan)) {
			return;
		}
		switch (weather) {
			case SNOW -> {
				if (player.canFreeze()) {
					player.setTicksFrozen(Math.min(FREEZE_CAP, player.getTicksFrozen() + FREEZE_ADD));
				}
				push(player, plan);
			}
			case SAND -> {
				if (tick % GUST_PERIOD < GUST_LENGTH) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GUST_EFFECT_TICKS, 0, true, false));
					push(player, plan);
				}
			}
			default -> {
			}
		}
	}

	/** Whether no block stands over the player's head up to the floor's roof, the barrier of an open roof apart. */
	static boolean inTheOpen(final ServerPlayer player, final TowerPlan plan) {
		ServerLevel tower = (ServerLevel) player.level();
		BlockPos head = player.blockPosition().above(2);
		for (int y = head.getY(); y < TowerLayout.BASE_Y + plan.roofY(); y++) {
			BlockPos at = new BlockPos(head.getX(), y, head.getZ());
			if (!tower.getBlockState(at).getCollisionShape(tower, at).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The way the floor's wind blows along the ground: a unit vector, drawn from the seed and the floor. */
	public static Vec3 wind(final TowerPlan plan) {
		double angle = new Random(TowerPlan.mix(plan.seed() ^ SALT ^ (plan.floor() * 0x9E3779B97F4A7C15L))).nextDouble() * 2.0 * Math.PI;
		return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
	}

	private static void push(final ServerPlayer player, final TowerPlan plan) {
		Vec3 wind = wind(plan);
		player.setDeltaMovement(player.getDeltaMovement().add(wind.x * PUSH, 0.0, wind.z * PUSH));
		player.needsSync = true;
	}
}
