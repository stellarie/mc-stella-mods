package com.stella.end;

import com.stella.end.mixin.EnderDragonFightAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The End's dragon. It flies in the hover phase and this controller sets its place every tick, so it stays over the
 * rooftop and out of the castle from its first tick, whatever vanilla's phases would do. It is passive until a player
 * stands on the roof, attacks every hundred ticks, roosts once at half health, and when it dies it pays out and returns.
 */
public final class EndDragons {
	public static final String TAG = "mod-stella-end.dragon";
	/** Marks the wyverns, their riders and the roost summons: the dragon's own side. */
	public static final String SIDE_TAG = "mod-stella-end.dragon_side";
	/** Saved with the dragon, so a roost already spent is not spent again after a restart. */
	static final String ROOSTED_TAG = "mod-stella-end.roosted";
	private static final long WAKE_DELAY = 20L;
	private static final int REVIVE_RETRY = 20;
	/** Where vanilla puts a new dragon: inside floor 5, which is why the dragon is moved the moment it loads. */
	private static final double VANILLA_SPAWN_Y = 128.0;
	private static final int KEPT = 32;

	/** What a death gave: the drops, and the experience each rooftop player was given. */
	public record Death(long at, List<UUID> drops, Map<UUID, Integer> experience) {
	}

	private static long reviveRetryAt;
	private static final Map<UUID, DragonState> STATES = new HashMap<>();
	private static final List<Death> DEATHS = new ArrayList<>();

	private EndDragons() {
	}

	public static void register() {
		// Claim the dragon before the mobs mod rolls it.
		// This listener runs ahead of the default phase.
		Identifier early = StellaEnd.id("early");
		ServerEntityEvents.ENTITY_LOAD.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ServerEntityEvents.ENTITY_LOAD.register(early, (entity, level) -> {
			if (entity instanceof EnderDragon dragon && level.dimension().equals(Level.END)) {
				adopt(level, dragon);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof EnderDragon && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				STATES.remove(entity.getUUID());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof EnderDragon dragon && dragon.level() instanceof ServerLevel level && level.dimension().equals(Level.END)) {
				fall(level, dragon);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension().equals(Level.END)) {
				tick(level);
			}
		});
		// A bed or an anchor in the End blows with this damage type, and the game lets it past the dragon's guard.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			boolean refused = entity instanceof EnderDragon && entity.level().dimension().equals(Level.END) && source.is(DamageTypes.BAD_RESPAWN_POINT);
			if (refused) {
				BehaviorLog.info("[END] dragon refuses a respawn point blast of {}", amount);
			}
			return !refused;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
		Wyverns.register();
		DragonBreath.register();
	}


	/** A new dragon, made as vanilla makes one: at the old spawn point, in a holding pattern, then adopted on load. */
	public static @Nullable EnderDragon spawn(final ServerLevel end) {
		EnderDragon dragon = EntityTypes.ENDER_DRAGON.create(end, EntitySpawnReason.EVENT);
		if (dragon == null) {
			return null;
		}
		dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
		dragon.snapTo(0.0, VANILLA_SPAWN_Y, 0.0, end.getRandom().nextFloat() * 360.0F, 0.0F);
		if (!end.addFreshEntity(dragon)) {
			StellaEnd.LOGGER.warn("The level refused a new dragon");
			return null;
		}
		// A chunk not yet entity ticking loads late.
		// Hold the dragon over the roof now.
		if (!STATES.containsKey(dragon.getUUID())) {
			adopt(end, dragon);
		}
		return dragon;
	}

	/** Takes up a dragon that has just loaded, new or from a save: claims it, levels it and moves it over the roof. */
	private static void adopt(final ServerLevel end, final EnderDragon dragon) {
		if (!dragon.entityTags().contains(TAG)) {
			LevelBridge.claim(dragon);
			dragon.addTag(TAG);
			LevelBridge.apply(dragon, EndLevels.DRAGON);
			HealthBridge.fixMaxHealth(dragon, DragonRules.MAX_HEALTH);
		}
		DragonState state = STATES.computeIfAbsent(dragon.getUUID(), DragonState::new);
		long now = EndClock.now(end);
		state.roosted = dragon.entityTags().contains(ROOSTED_TAG);
		state.modeSince = now;
		state.lastRoofSeen = now;
		state.nextAttackAt = now + WAKE_DELAY;
		DragonPerch.schedule(state, now, end.getRandom());
		dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
		Vec3 hover = Rooftop.hoverPoint();
		if (dragon.getY() < Rooftop.DECK_Y + Rooftop.HOVER_HEIGHT - 4.0 || Math.hypot(dragon.getX(), dragon.getZ()) > DragonRules.LEASH) {
			dragon.setPos(hover.x, hover.y, hover.z);
		}
		EndState saved = EndCastles.state(end);
		if (saved.dragonDead()) {
			EndCastles.setState(end, saved.withDragonRisen());
		}
		BehaviorLog.info("[END] dragon adopted at={} level={} roosted={}", dragon.position(), LevelBridge.read(dragon), state.roosted);
	}

	public static @Nullable EnderDragon current(final ServerLevel end) {
		for (UUID id : STATES.keySet()) {
			if (end.getEntity(id) instanceof EnderDragon dragon && dragon.isAlive() && !dragon.isDeadOrDying()) {
				return dragon;
			}
		}
		return null;
	}

	public static @Nullable DragonState state(final @Nullable Entity dragon) {
		return dragon == null ? null : STATES.get(dragon.getUUID());
	}

	/** Picks the dragon's next attack by hand, and starts it at the next tick. */
	public static void force(final EnderDragon dragon, final DragonState.Attack attack) {
		DragonState state = state(dragon);
		if (state != null) {
			state.forced = attack;
			state.nextAttackAt = 0L;
		}
	}

	public static List<Death> deaths() {
		return List.copyOf(DEATHS);
	}

	public static void reset() {
		STATES.clear();
		DEATHS.clear();
		reviveRetryAt = 0L;
		Wyverns.reset();
		DragonBreath.reset();
	}


	private static void tick(final ServerLevel end) {
		long now = EndClock.now(end);
		watchRevival(end, now);
		if (STATES.isEmpty()) {
			return;
		}
		for (DragonState state : List.copyOf(STATES.values())) {
			if (!(end.getEntity(state.id) instanceof EnderDragon dragon)) {
				continue;
			}
			if (dragon.isDeadOrDying()) {
				fall(end, dragon);
			} else {
				control(end, dragon, state, now);
			}
		}
	}

	private static void control(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now) {
		if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
		}
		if (dragon.tickCount % DragonBuffs.PERIOD == 0) {
			DragonBuffs.refresh(end, dragon, state);
		}
		DragonBuffs.regenerate(dragon, state, now);
		// The sleep holds the perch timer: the time it lasts is added back.
		if (state.mode == DragonState.Mode.ROOST && state.lastTick != EndState.NEVER) {
			state.perchAt += now - state.lastTick;
		}
		state.lastTick = now;
		List<ServerPlayer> roof = Rooftop.players(end);
		if (!roof.isEmpty()) {
			state.lastRoofSeen = now;
		}
		decide(end, dragon, state, roof.isEmpty(), now);
		double speed = DragonRules.speedMultiplier(state.buffs);
		switch (state.mode) {
			case ROOST -> DragonRoost.tick(end, dragon, state, now);
			case PERCH -> DragonPerch.tick(end, dragon, state, now);
			case PASSIVE -> DragonFlight.arrive(dragon, state, Rooftop.hoverPoint(), DragonFlight.CRUISE * speed);
			case AWAKE -> {
				if (state.current != null) {
					DragonAttacks.step(end, dragon, state, now);
				} else {
					DragonFlight.orbit(dragon, state, DragonFlight.CRUISE * speed);
					if (!roof.isEmpty() && now >= state.nextAttackAt) {
						DragonAttacks.begin(end, dragon, state, now);
					}
				}
			}
		}
		DragonFlight.leash(dragon, state);
		Wyverns.tick(end, dragon, state, now);
	}

	/** Moves the dragon between passive, awake, roosting and perching. A roost takes it from a perch. */
	private static void decide(final ServerLevel end, final EnderDragon dragon, final DragonState state, final boolean roofEmpty, final long now) {
		if (state.mode == DragonState.Mode.ROOST) {
			if (DragonRoost.over(state, now)) {
				switchTo(state, roofEmpty ? DragonState.Mode.PASSIVE : DragonState.Mode.AWAKE, now);
			}
		} else if (DragonRoost.due(dragon, state, now)) {
			DragonPerch.interrupt(state, now);
			DragonRoost.begin(dragon, state, now);
		} else if (state.mode == DragonState.Mode.PERCH) {
			if (DragonPerch.over(state, now)) {
				DragonPerch.finish(state, now, end.getRandom());
				switchTo(state, roofEmpty ? DragonState.Mode.PASSIVE : DragonState.Mode.AWAKE, now);
			}
		} else if (state.mode == DragonState.Mode.AWAKE && DragonPerch.due(state, now)) {
			DragonPerch.begin(state, now);
		} else if (!roofEmpty && state.mode == DragonState.Mode.PASSIVE) {
			switchTo(state, DragonState.Mode.AWAKE, now);
		} else if (roofEmpty && state.mode == DragonState.Mode.AWAKE && now - state.lastRoofSeen >= DragonRules.CALM_TICKS) {
			switchTo(state, DragonState.Mode.PASSIVE, now);
		}
	}

	private static void switchTo(final DragonState state, final DragonState.Mode mode, final long now) {
		BehaviorLog.info("[END] dragon mode {} -> {} clock={}", state.mode, mode, now);
		if (mode == DragonState.Mode.AWAKE && state.mode == DragonState.Mode.PASSIVE) {
			state.awokeAt = now;
		}
		state.mode = mode;
		state.modeSince = now;
		state.current = null;
		state.launchAt = EndState.NEVER;
		state.path.clear();
		if (mode == DragonState.Mode.AWAKE) {
			state.nextAttackAt = now + WAKE_DELAY;
		}
	}


	/** The dragon died: the time is stored, the spoils drop, the roof is paid, and its side leaves with it. */
	private static void fall(final ServerLevel end, final EnderDragon dragon) {
		DragonState state = STATES.computeIfAbsent(dragon.getUUID(), DragonState::new);
		if (state.dead) {
			return;
		}
		state.dead = true;
		long now = EndClock.now(end);
		EndCastles.setState(end, EndCastles.state(end).withDragonDead(now));
		// Vanilla sets an egg block at the first kill.
		// The egg drops as an item instead.
		EnderDragonFight fight = end.getDragonFight();
		if (fight != null) {
			((EnderDragonFightAccessor) fight).stellaEnd$setPreviouslyKilled(true);
		}
		List<UUID> drops = DragonRewards.drop(end);
		Map<UUID, Integer> paid = DragonRewards.pay(end);
		int dismissed = 0;
		List<Entity> everything = new ArrayList<>();
		end.getAllEntities().forEach(everything::add);
		for (Entity entity : everything) {
			if (entity.entityTags().contains(SIDE_TAG)) {
				entity.discard();
				dismissed++;
			}
		}
		DEATHS.add(new Death(now, List.copyOf(drops), Map.copyOf(paid)));
		if (DEATHS.size() > KEPT) {
			DEATHS.remove(0);
		}
		BehaviorLog.info("[END] dragon fell clock={} drops={} paid={} dismissed={}", now, drops.size(), paid.size(), dismissed);
	}

	/** After the wait without a summon, the dragon returns the way the fight would make it, once someone is in the End to see it. */
	private static void watchRevival(final ServerLevel end, final long now) {
		EndState saved = EndCastles.state(end);
		if (!saved.dragonDead() || now < saved.dragonDeadAt() + DragonRules.REVIVE_TICKS || now < reviveRetryAt || end.players().isEmpty()) {
			return;
		}
		if (current(end) == null) {
			reviveRetryAt = now + REVIVE_RETRY;
			revive(end);
		}
	}

	/** Revives the dragon the way the two hour timer does: the fight's own factory, and a portal that is shut again. */
	public static @Nullable EnderDragon revive(final ServerLevel end) {
		EnderDragonFight fight = end.getDragonFight();
		if (fight == null) {
			return spawn(end);
		}
		EnderDragonFightAccessor access = (EnderDragonFightAccessor) fight;
		access.stellaEnd$setDragonKilled(false);
		access.stellaEnd$spawnExitPortal(false);
		EnderDragon dragon = access.stellaEnd$createNewDragon();
		BehaviorLog.info("[END] dragon revived by the timer: {}", dragon != null);
		return dragon;
	}
}
