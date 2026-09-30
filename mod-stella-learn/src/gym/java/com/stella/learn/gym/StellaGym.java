package com.stella.learn.gym;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.stella.learn.StellaLearn;
import com.stella.learn.model.ArcherAction;
import com.stella.learn.model.ModelDriver;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/** Entrypoint of the training server. It does nothing unless `stella.gym.port` is set. */
public final class StellaGym implements ModInitializer {
	private static final long NIGHT = 18000L;
	/** Comma separated logger names to set to WARN in the gym. */
	private static final String QUIET_MODS = "stella.gym.quietMods";
	/** Set to true to keep the learn mod's debug lines. */
	private static final String DEBUG = "stella.gym.debug";

	private Stepper stepper;

	@Override
	public void onInitialize() {
		GymConfig config = GymConfig.fromProperties();
		if (!config.enabled()) {
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(server -> start(server, config));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (stepper != null) {
				stepper.shutdown();
			}
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			if (stepper != null) {
				stepper.holdActions();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (stepper != null) {
				stepper.endTick();
			}
		});
	}

	private void start(final MinecraftServer server, final GymConfig config) {
		GymLog.info(GymLog.CONFIG, "LOAD", config.detail());
		GymLog.keepWhenBehaviorLogIsOff();
		quietOtherMods();
		if (config.baseline()) {
			BaselineMode.apply();
		} else {
			ModelDriver.useSource((mob, observation) -> ArcherAction.STILL);
		}

		ServerLevel level = server.overworld();
		applyRules(server, level);
		ArenaPool pool = new ArenaPool(level, config.arenas());
		pool.buildAll();
		List<GymEpisode> episodes = new ArrayList<>();
		for (Arena arena : pool.all()) {
			episodes.add(new GymEpisode(arena, config));
		}

		try {
			Bridge bridge = new Bridge(config.port());
			stepper = new Stepper(server, config, pool, episodes, bridge);
			GymLog.info(GymLog.BRIDGE, "LISTEN", "port=" + bridge.port() + " arenas=" + pool.size());
		} catch (IOException e) {
			GymLog.info(GymLog.BRIDGE, "LISTEN_FAILED", "port=" + config.port() + " reason=" + GymLog.LISTEN_FAILED + " error=" + e.getMessage());
			server.halt(false);
		}
	}

	/** The mobs mod writes a line per arrow, which would fill a training log; its warnings and errors stay. */
	private static void quietOtherMods() {
		if (!Boolean.getBoolean(DEBUG)) {
			// The debug log grows by hundreds of megabytes a minute otherwise; the sampled debug lines are for one-server runs.
			Configurator.setLevel(StellaLearn.MOD_ID, Level.INFO);
		}

		for (String name : System.getProperty(QUIET_MODS, "mod-stella-mobs").split(",")) {
			if (!name.isBlank()) {
				Configurator.setLevel(name.trim(), Level.WARN);
			}
		}
	}

	/** Night and calm weather keep the sun from burning zombies; the other rules stop noise and item drops. */
	private static void applyRules(final MinecraftServer server, final ServerLevel level) {
		GameRules rules = level.getGameRules();
		set(server, rules, GameRules.KEEP_INVENTORY, true);
		set(server, rules, GameRules.SHOW_DEATH_MESSAGES, false);
		set(server, rules, GameRules.IMMEDIATE_RESPAWN, true);
		set(server, rules, GameRules.ADVANCE_TIME, false);
		set(server, rules, GameRules.ADVANCE_WEATHER, false);
		set(server, rules, GameRules.MOB_DROPS, false);
		set(server, rules, GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
		set(server, rules, GameRules.SEND_COMMAND_FEEDBACK, false);
		set(server, rules, GameRules.LOCATOR_BAR, false);
		set(server, rules, GameRules.MOB_GRIEFING, false);
		// The natural spawner counts every entity of every chunk each tick, and the gym spawns its own mobs.
		set(server, rules, GameRules.SPAWN_MOBS, false);
		set(server, rules, GameRules.SPAWN_MONSTERS, false);
		set(server, rules, GameRules.SPAWN_PATROLS, false);
		set(server, rules, GameRules.SPAWN_PHANTOMS, false);
		set(server, rules, GameRules.SPAWN_WANDERING_TRADERS, false);
		set(server, rules, GameRules.SPAWN_WARDENS, false);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + NIGHT);
		GymLog.info(GymLog.CONFIG, "RULES", "time=" + NIGHT + " keepInventory=true advanceTime=false mobDrops=false");
	}

	private static void set(final MinecraftServer server, final GameRules rules, final GameRule<Boolean> rule, final boolean value) {
		rules.set(rule, value, server);
	}
}
