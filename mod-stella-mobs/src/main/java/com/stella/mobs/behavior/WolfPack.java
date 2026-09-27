package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.gear.GearTier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.wolf.Wolf;

/**
 * Wolves: every wolf wears Resistance through the night, and one in ten is born elite. An elite wolf is
 * larger and tougher like any elite, brings no minions, can be tamed like any wolf, and carries an aura
 * that slows and weakens the hostiles around it while it speeds and strengthens its allies.
 */
public final class WolfPack {
	public static final double ELITE_CHANCE = 0.10;
	public static final int NIGHT_RESISTANCE_AMPLIFIER = 0;
	public static final int CHECK_INTERVAL_TICKS = 20;
	/** Longer than the check interval, so the Resistance never lapses between two checks at night. */
	public static final int NIGHT_RESISTANCE_TICKS = 60;
	public static final double AURA_RADIUS = 8.0;
	public static final int AURA_INTERVAL_TICKS = 40;
	public static final int AURA_TICKS = 80;
	public static final int AURA_AMPLIFIER = 0;
	/** The loaded wolves, so the night check never sweeps the whole level. */
	private static final Set<Wolf> WATCHED = ConcurrentHashMap.newKeySet();

	private WolfPack() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Wolf wolf)) {
				return;
			}
			WATCHED.add(wolf);
			boolean loadedFromDisk = entity instanceof EntityLoadData data && data.isLoadedFromDisk();
			if (!loadedFromDisk && !wolf.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG) && !Elite.isElite(wolf) && rollsElite(wolf.getRandom())) {
				makeElite(wolf);
			} else if (Elite.isElite(wolf)) {
				installAura(wolf);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Wolf wolf) {
				WATCHED.remove(wolf);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(WolfPack::tick);
	}

	public static boolean rollsElite(final RandomSource random) {
		return random.nextDouble() < ELITE_CHANCE;
	}

	/** Marks a wolf elite without escorts, fills its health and gives it the pack aura. */
	public static void makeElite(final Wolf wolf) {
		if (Elite.mark(wolf, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER)) {
			wolf.setHealth(wolf.getMaxHealth());
			StellaMobs.LOGGER.info("[BEHAVIOR] test=wolfPack ELITE wolf={} health={}", wolf.getUUID(), wolf.getMaxHealth());
		}
		installAura(wolf);
	}

	private static void installAura(final Wolf wolf) {
		wolf.getGoalSelector().removeAllGoals(goal -> goal instanceof EliteWolfAuraGoal);
		wolf.getGoalSelector().addGoal(0, new EliteWolfAuraGoal(wolf));
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		for (Wolf wolf : WATCHED) {
			if (wolf.isRemoved()) {
				WATCHED.remove(wolf);
			} else if (wolf.isAlive() && wolf.level().isDarkOutside()) {
				wolf.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, NIGHT_RESISTANCE_TICKS, NIGHT_RESISTANCE_AMPLIFIER, true, false));
			}
		}
	}
}
