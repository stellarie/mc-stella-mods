package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The boss of floor 6: a blaze that does not move, bursts soul charges in every direction, rains them from above,
 * volleys them at players, withers what it hits and calls undead blazes. It rises when a player reaches the middle step
 * of the stair into its hall, with two Wither Knights beside it. It leaves a dark blaze core. The barrier door to the
 * rest area opens when it and both knights are dead, and it rises again later.
 */
public final class UndeadSovereign {
	public static final String TAG = "mod-stella-end.sovereign";
	/** Its whole maximum health, whatever its level or the health scale adds. */
	public static final float MAX_HEALTH = 350000.0F;
	public static final double SCALE = 3.0;
	public static final double BAR_RANGE = 48.0;
	public static final int BURST_PERIOD = 100;
	/** The burst fires this many charges level round the Sovereign, evenly spaced. */
	public static final int BURST_RING = 16;
	/** And one charge at each of these pitches in degrees, above the level (positive) or below it. */
	public static final List<Double> BURST_PITCHES = List.of(60.0, 30.0, -20.0, -45.0);
	public static final int RAIN_PERIOD = 40;
	public static final int RAIN_COUNT = 6;
	public static final int RAIN_MIN_HEIGHT = 12;
	public static final int RAIN_MAX_HEIGHT = 16;
	public static final double RAIN_RADIUS = 7.0;
	public static final int VOLLEY_PERIOD = 60;
	public static final int VOLLEY_SIZE = 5;
	public static final int SUMMON_PERIOD = 200;
	public static final int SUMMON_COUNT = 3;
	public static final int SUMMON_CAP = 12;
	public static final long RESPAWN_TICKS = 36000L;
	public static final int WITHER_AMPLIFIER = 1;
	public static final int WITHER_TICKS = 100;
	private static final int BAR_PERIOD = 10;
	/** A player this far over the step's top block still stands on it: a jump or a stride on the stair. */
	private static final double TRIGGER_HEADROOM = 3.0;
	/** Records keep this many entries; the oldest go first. */
	private static final int KEPT = 256;
	/** A rain charge needs this many free cells over its drop point, or the point is skipped. */
	private static final int RAIN_LEAST_ROOM = 6;
	private static final int RAIN_TRIES = 3;
	private static final double SUMMON_NEAR = 4.0;
	private static final double SUMMON_FAR = 8.0;

	/** One burst: the charges it fired. */
	public record Burst(long at, List<UUID> charges) {
	}

	/** One fall of rain: the charges it dropped. */
	public record Rain(long at, List<UUID> charges) {
	}

	/** One volley: the charges it fired. */
	public record Volley(long at, List<UUID> charges) {
	}

	/** One call of undead blazes: the blazes it added. */
	public record Brood(long at, List<UUID> blazes) {
	}

	private static final List<Burst> BURSTS = new ArrayList<>();
	private static final List<Rain> RAINS = new ArrayList<>();
	private static final List<Volley> VOLLEYS = new ArrayList<>();
	private static final List<Brood> BROODS = new ArrayList<>();
	private static @Nullable UUID id;
	private static @Nullable Blaze held;
	private static @Nullable ServerBossEvent bar;
	private static long nextBurstAt;
	private static long nextRainAt;
	private static long nextVolleyAt;
	private static long nextSummonAt;

	private UndeadSovereign() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (is(entity) && entity instanceof Blaze blaze && level.dimension().equals(Level.END) && !blaze.getUUID().equals(id)) {
				adopt(level, blaze);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (is(entity) && entity.level() instanceof ServerLevel level && level.dimension().equals(Level.END)) {
				fall(level, (Blaze) entity);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension().equals(Level.END)) {
				tick(level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	/** Raises the Sovereign at the arena anchor and shuts the door. Null when one stands, or the level refuses it. */
	public static @Nullable Blaze spawn(final ServerLevel end) {
		return spawn(end, true);
	}

	/** As {@link #spawn(ServerLevel)}, with the knights left out when {@code knights} is false. */
	public static @Nullable Blaze spawn(final ServerLevel end, final boolean knights) {
		if (current(end) != null) {
			return null;
		}
		Blaze blaze = EntityTypes.BLAZE.create(end, EntitySpawnReason.EVENT);
		if (blaze == null) {
			return null;
		}
		LevelBridge.claim(blaze);
		blaze.addTag(TAG);
		blaze.setAttached(UndeadBlazes.MARK, UndeadBlazes.MARK_SOVEREIGN);
		blaze.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		blaze.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		blaze.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
		blaze.refreshDimensions();
		blaze.setNoGravity(true);
		blaze.setPersistenceRequired();
		blaze.setCustomName(Component.translatable("entity.mod-stella-end.undead_sovereign"));
		LevelBridge.apply(blaze, EndLevels.SOVEREIGN);
		HealthBridge.fixMaxHealth(blaze, MAX_HEALTH);
		Vec3 at = anchor();
		blaze.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		CastleBuilder.setExitDoor(end, EndCastles.layout(end), EndCastle.min(), false);
		EndCastles.setState(end, EndCastles.state(end).withSovereignDeadAt(EndState.NEVER));
		if (!end.addFreshEntity(blaze)) {
			return null;
		}
		// A chunk not yet entity ticking loads late.
		// Take the Sovereign up now.
		if (!blaze.getUUID().equals(id)) {
			adopt(end, blaze);
		}
		BehaviorLog.info("[END] sovereign risen at={} health={} level={}", blaze.blockPosition(), blaze.getMaxHealth(), LevelBridge.read(blaze));
		if (knights) {
			WitherKnights.raise(end);
		}
		return blaze;
	}

	private static Vec3 anchor() {
		return Vec3.atBottomCenterOf(EndCastle.arenaAnchor());
	}

	/** Takes up a Sovereign that has just loaded, new or from a save: its timers start and its bar opens. */
	private static void adopt(final ServerLevel end, final Blaze blaze) {
		id = blaze.getUUID();
		held = blaze;
		long now = EndClock.now(end);
		nextBurstAt = now + BURST_PERIOD;
		nextRainAt = now + RAIN_PERIOD;
		nextVolleyAt = now + VOLLEY_PERIOD;
		nextSummonAt = now + SUMMON_PERIOD;
		closeBar();
		bar = new ServerBossEvent(blaze.getUUID(), blaze.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
	}

	public static @Nullable Blaze current(final ServerLevel end) {
		if (held != null && !held.isRemoved() && held.isAlive()) {
			return held;
		}
		if (id != null && end.getEntity(id) instanceof Blaze blaze && blaze.isAlive()) {
			held = blaze;
			return blaze;
		}
		return null;
	}

	public static boolean is(final @Nullable Entity entity) {
		return entity instanceof Blaze && entity.entityTags().contains(TAG);
	}

	public static List<Burst> bursts() {
		return List.copyOf(BURSTS);
	}

	public static List<Rain> rains() {
		return List.copyOf(RAINS);
	}

	public static List<Volley> volleys() {
		return List.copyOf(VOLLEYS);
	}

	public static List<Brood> broods() {
		return List.copyOf(BROODS);
	}

	public static @Nullable ServerBossEvent bar() {
		return bar;
	}

	public static void reset() {
		closeBar();
		id = null;
		held = null;
		BURSTS.clear();
		RAINS.clear();
		VOLLEYS.clear();
		BROODS.clear();
	}

	private static void closeBar() {
		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}
	}


	private static void tick(final ServerLevel end) {
		long now = EndClock.now(end);
		Blaze sovereign = current(end);
		if (sovereign == null) {
			rise(end, now);
			return;
		}
		Vec3 at = anchor();
		sovereign.setPos(at.x, at.y, at.z);
		sovereign.setDeltaMovement(Vec3.ZERO);
		if (sovereign.tickCount % BAR_PERIOD == 0) {
			updateBar(end, sovereign);
		}
		if (now >= nextBurstAt && burst(end, sovereign, now)) {
			nextBurstAt = now + BURST_PERIOD;
		}
		if (now >= nextRainAt && rain(end, sovereign, now)) {
			nextRainAt = now + RAIN_PERIOD;
		}
		if (now >= nextVolleyAt && volley(end, sovereign, now)) {
			nextVolleyAt = now + VOLLEY_PERIOD;
		}
		if (now >= nextSummonAt) {
			summon(end, now);
			nextSummonAt = now + SUMMON_PERIOD;
		}
	}

	/** Raises the Sovereign when a player stands on the middle step of the stair and none stands or lies in its waiting time. */
	private static void rise(final ServerLevel end, final long now) {
		EndState state = EndCastles.state(end);
		boolean waiting = state.sovereignDeadAt() != EndState.NEVER && now < state.sovereignDeadAt() + RESPAWN_TICKS;
		if (!waiting && playerOnTrigger(end)) {
			spawn(end);
		}
	}

	/** Whether a player stands on the middle step of the stair into the hall, in any of its lanes. */
	private static boolean playerOnTrigger(final ServerLevel end) {
		CastleLayout.Flight flight = CastleLayout.FLIGHT_5;
		int step = CastleLayout.Flight.STEPS / 2;
		int top = CastleLayout.sovereignTrigger().getY();
		for (ServerPlayer player : end.players()) {
			if (!player.isAlive() || player.isSpectator() || player.getY() < top || player.getY() >= top + TRIGGER_HEADROOM) {
				continue;
			}
			for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
				if (player.getBlockX() == EndCastle.MIN_X + flight.x(step, lane) && player.getBlockZ() == EndCastle.MIN_Z + flight.z(step, lane)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void updateBar(final ServerLevel end, final Blaze sovereign) {
		if (bar == null) {
			return;
		}
		bar.setProgress(Math.max(0.0F, Math.min(1.0F, sovereign.getHealth() / sovereign.getMaxHealth())));
		for (ServerPlayer player : end.players()) {
			if (player.isAlive() && player.distanceTo(sovereign) <= BAR_RANGE) {
				bar.addPlayer(player);
			} else {
				bar.removePlayer(player);
			}
		}
	}

	/** Fires a charge level in each of sixteen directions round it and one at each pitch up and down. False, and nothing fired, with nobody in range. */
	private static boolean burst(final ServerLevel end, final Blaze sovereign, final long now) {
		if (targets(end, sovereign).isEmpty()) {
			return false;
		}
		List<UUID> charges = new ArrayList<>();
		for (int i = 0; i < BURST_RING; i++) {
			double yaw = Math.PI * 2.0 * i / BURST_RING;
			fireBurst(end, sovereign, new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw)), charges);
		}
		for (int i = 0; i < BURST_PITCHES.size(); i++) {
			double yaw = Math.PI * 2.0 * (i + 0.5) / BURST_PITCHES.size();
			double pitch = Math.toRadians(BURST_PITCHES.get(i));
			fireBurst(end, sovereign, new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw)), charges);
		}
		keep(BURSTS, new Burst(now, List.copyOf(charges)));
		BehaviorLog.info("[END] sovereign burst charges={}", charges.size());
		return true;
	}

	private static void fireBurst(final ServerLevel end, final Blaze sovereign, final Vec3 direction, final List<UUID> charges) {
		var charge = SoulCharges.fire(end, sovereign, direction, SoulCharges.SOVEREIGN_FACTOR);
		if (charge != null) {
			charges.add(charge.getUUID());
		}
	}

	/**
	 * Drops soul charges from twelve to sixteen blocks over random points of the arena, straight down. A column with
	 * less room than that takes the charge as high as it can. False, and nothing dropped, with nobody in range.
	 */
	private static boolean rain(final ServerLevel end, final Blaze sovereign, final long now) {
		if (targets(end, sovereign).isEmpty()) {
			return false;
		}
		int floorY = EndCastle.standY(EndCastle.FLOORS);
		Vec3 at = anchor();
		List<UUID> charges = new ArrayList<>();
		for (int i = 0; i < RAIN_COUNT; i++) {
			for (int attempt = 0; attempt < RAIN_TRIES; attempt++) {
				double angle = end.getRandom().nextDouble() * Math.PI * 2.0;
				double reach = RAIN_RADIUS * Math.sqrt(end.getRandom().nextDouble());
				double x = at.x + Math.cos(angle) * reach;
				double z = at.z + Math.sin(angle) * reach;
				int room = room(end, x, floorY, z);
				if (room < RAIN_LEAST_ROOM) {
					continue;
				}
				int wanted = RAIN_MIN_HEIGHT + end.getRandom().nextInt(RAIN_MAX_HEIGHT - RAIN_MIN_HEIGHT + 1);
				Vec3 from = new Vec3(x, floorY + Math.min(wanted, room - 1) + 0.5, z);
				var charge = SoulCharges.fire(end, sovereign, from, new Vec3(0.0, -1.0, 0.0), SoulCharges.SOVEREIGN_FACTOR);
				if (charge != null) {
					charges.add(charge.getUUID());
				}
				break;
			}
		}
		keep(RAINS, new Rain(now, List.copyOf(charges)));
		BehaviorLog.info("[END] sovereign rain charges={}", charges.size());
		return true;
	}

	/** How many cells a charge can pass through over a column, counted up from the standing cell of the floor. */
	private static int room(final ServerLevel end, final double x, final int floorY, final double z) {
		int free = 0;
		while (free <= RAIN_MAX_HEIGHT) {
			BlockPos cell = new BlockPos(Mth.floor(x), floorY + free, Mth.floor(z));
			if (!end.getBlockState(cell).getCollisionShape(end, cell).isEmpty()) {
				break;
			}
			free++;
		}
		return free;
	}

	/** The players the Sovereign fights: alive, in survival or adventure, and within the range of its bar. */
	private static List<ServerPlayer> targets(final ServerLevel end, final Blaze sovereign) {
		List<ServerPlayer> targets = new ArrayList<>();
		for (ServerPlayer player : end.players()) {
			if (player.isAlive() && !player.isSpectator() && !player.isCreative() && player.distanceTo(sovereign) <= BAR_RANGE) {
				targets.add(player);
			}
		}
		return targets;
	}

	/** Fires five soul charges at the players in range, spread over them. False, and nothing fired, with nobody in range. */
	private static boolean volley(final ServerLevel end, final Blaze sovereign, final long now) {
		List<ServerPlayer> targets = targets(end, sovereign);
		if (targets.isEmpty()) {
			return false;
		}
		List<UUID> charges = new ArrayList<>();
		for (int i = 0; i < VOLLEY_SIZE; i++) {
			ServerPlayer target = targets.get(i % targets.size());
			Vec3 from = sovereign.position().add(0.0, sovereign.getBbHeight() * 0.5, 0.0);
			Vec3 aim = target.getEyePosition().subtract(from);
			double spread = i == 0 ? 0.0 : 0.04 * aim.length();
			Vec3 direction = aim.add(end.getRandom().triangle(0.0, spread), end.getRandom().triangle(0.0, spread), end.getRandom().triangle(0.0, spread));
			var charge = SoulCharges.fire(end, sovereign, direction, SoulCharges.SOVEREIGN_FACTOR);
			if (charge != null) {
				charges.add(charge.getUUID());
			}
		}
		keep(VOLLEYS, new Volley(now, List.copyOf(charges)));
		BehaviorLog.info("[END] sovereign volley charges={} targets={}", charges.size(), targets.size());
		return true;
	}

	/** Calls up to three undead blazes round the Sovereign, while fewer than twelve stand. */
	private static void summon(final ServerLevel end, final long now) {
		int room = SUMMON_CAP - UndeadBlazes.alive(end).size();
		int count = Math.min(SUMMON_COUNT, room);
		if (count <= 0) {
			return;
		}
		Vec3 at = anchor();
		List<UUID> added = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double angle = end.getRandom().nextDouble() * Math.PI * 2.0;
			double reach = SUMMON_NEAR + end.getRandom().nextDouble() * (SUMMON_FAR - SUMMON_NEAR);
			Vec3 spot = new Vec3(at.x + Math.cos(angle) * reach, at.y + 2.0 + end.getRandom().nextDouble() * 3.0, at.z + Math.sin(angle) * reach);
			Blaze blaze = UndeadBlazes.spawn(end, spot);
			if (blaze != null) {
				added.add(blaze.getUUID());
			}
		}
		if (!added.isEmpty()) {
			keep(BROODS, new Brood(now, List.copyOf(added)));
			BehaviorLog.info("[END] sovereign brood added={} alive={}", added.size(), UndeadBlazes.alive(end).size());
		}
	}

	/** The Sovereign fell: it leaves a dark blaze core, the time is stored, the bar closes, and the door opens if both knights are down. */
	private static void fall(final ServerLevel end, final Blaze sovereign) {
		long now = EndClock.now(end);
		sovereign.spawnAtLocation(end, new ItemStack(EndItems.darkBlazeCore()));
		EndCastles.setState(end, EndCastles.state(end).withSovereignDeadAt(now));
		ExitSeal.update(end);
		closeBar();
		id = null;
		held = null;
		BehaviorLog.info("[END] sovereign fell at={} clock={}", sovereign.blockPosition(), now);
	}

	private static <T> void keep(final List<T> record, final T entry) {
		record.add(entry);
		if (record.size() > KEPT) {
			record.remove(0);
		}
	}
}
