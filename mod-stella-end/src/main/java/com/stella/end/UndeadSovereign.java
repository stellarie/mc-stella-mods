package com.stella.end;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The boss of floor 6: a blaze that does not move, rings itself in soul flame, volleys soul charges, withers what it
 * hits and calls undead blazes. Its death opens the barrier door to the rest area, and it rises again later.
 */
public final class UndeadSovereign {
	public static final String TAG = "mod-stella-end.sovereign";
	public static final float BASE_HEALTH = 600.0F;
	public static final double SCALE = 3.0;
	public static final double BAR_RANGE = 48.0;
	public static final int RING_PERIOD = 20;
	public static final int RING_LIFE = 100;
	public static final int RING_MIN = 8;
	public static final int RING_MAX = 12;
	public static final int RING_RADIUS = 10;
	public static final int VOLLEY_PERIOD = 60;
	public static final int VOLLEY_SIZE = 5;
	public static final int SUMMON_PERIOD = 200;
	public static final int SUMMON_COUNT = 3;
	public static final int SUMMON_CAP = 12;
	public static final long RESPAWN_TICKS = 36000L;
	public static final int WITHER_AMPLIFIER = 1;
	public static final int WITHER_TICKS = 100;
	private static final int BAR_PERIOD = 10;
	/** Records keep this many entries; the oldest go first. */
	private static final int KEPT = 256;
	private static final double SUMMON_NEAR = 4.0;
	private static final double SUMMON_FAR = 8.0;

	/** One ring pulse: the flames it set, and the clock when it did. */
	public record Pulse(long at, List<BlockPos> cells) {
	}

	/** One volley: the charges it fired. */
	public record Volley(long at, List<UUID> charges) {
	}

	/** One call of undead blazes: the blazes it added. */
	public record Brood(long at, List<UUID> blazes) {
	}

	private static final List<Pulse> PULSES = new ArrayList<>();
	private static final List<Volley> VOLLEYS = new ArrayList<>();
	private static final List<Brood> BROODS = new ArrayList<>();
	/** Every flame the ring has set and not yet put out, with the clock at which it burns out. */
	private static final Map<BlockPos, Long> FLAMES = new LinkedHashMap<>();
	private static @Nullable UUID id;
	private static @Nullable Blaze held;
	private static @Nullable ServerBossEvent bar;
	private static long nextRingAt;
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
		blaze.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BASE_HEALTH);
		blaze.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		blaze.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		blaze.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
		blaze.refreshDimensions();
		blaze.setNoGravity(true);
		blaze.setPersistenceRequired();
		blaze.setCustomName(Component.translatable("entity.mod-stella-end.undead_sovereign"));
		LevelBridge.apply(blaze, EndLevels.SOVEREIGN);
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
		nextRingAt = now + RING_PERIOD;
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

	public static List<Pulse> pulses() {
		return List.copyOf(PULSES);
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
		PULSES.clear();
		VOLLEYS.clear();
		BROODS.clear();
		FLAMES.clear();
	}

	private static void closeBar() {
		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}
	}


	private static void tick(final ServerLevel end) {
		long now = EndClock.now(end);
		burnOut(end, now);
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
		if (now >= nextRingAt) {
			ring(end, now);
			nextRingAt = now + RING_PERIOD;
		}
		if (now >= nextVolleyAt && volley(end, sovereign, now)) {
			nextVolleyAt = now + VOLLEY_PERIOD;
		}
		if (now >= nextSummonAt) {
			summon(end, now);
			nextSummonAt = now + SUMMON_PERIOD;
		}
	}

	/** Raises the Sovereign when a player stands in the arena and none stands there or lies in its waiting time. */
	private static void rise(final ServerLevel end, final long now) {
		EndState state = EndCastles.state(end);
		boolean waiting = state.sovereignDeadAt() != EndState.NEVER && now < state.sovereignDeadAt() + RESPAWN_TICKS;
		if (!waiting && playerInArena(end)) {
			spawn(end);
		}
	}

	/** Whether a player stands in the arena room of floor 6, as the floor plan draws it. */
	private static boolean playerInArena(final ServerLevel end) {
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(EndCastle.FLOORS);
		for (ServerPlayer player : end.players()) {
			int x = player.getBlockX() - EndCastle.MIN_X;
			int z = player.getBlockZ() - EndCastle.MIN_Z;
			boolean inside = x >= 0 && z >= 0 && x < EndCastle.SIZE && z < EndCastle.SIZE;
			if (player.isAlive() && !player.isSpectator() && inside && EndCastle.floorAt(player.getY()) == EndCastle.FLOORS && plan.room(x, z) == 1) {
				return true;
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

	/** Sets eight to twelve soul flames on random floor cells within ten blocks, and writes down when each burns out. */
	private static void ring(final ServerLevel end, final long now) {
		int y = EndCastle.standY(EndCastle.FLOORS);
		Vec3 at = anchor();
		List<BlockPos> candidates = new ArrayList<>();
		for (int dx = -RING_RADIUS; dx <= RING_RADIUS; dx++) {
			for (int dz = -RING_RADIUS; dz <= RING_RADIUS; dz++) {
				BlockPos cell = new BlockPos(Mth.floor(at.x) + dx, y, Mth.floor(at.z) + dz);
				boolean inReach = Math.hypot(cell.getX() + 0.5 - at.x, cell.getZ() + 0.5 - at.z) <= RING_RADIUS;
				if (inReach && (end.getBlockState(cell).isAir() || end.getBlockState(cell).is(EndBlocks.soulFlame()))
					&& end.getBlockState(cell.below()).isFaceSturdy(end, cell.below(), Direction.UP)) {
					candidates.add(cell);
				}
			}
		}
		Collections.shuffle(candidates, new java.util.Random(end.getRandom().nextLong()));
		int wanted = RING_MIN + end.getRandom().nextInt(RING_MAX - RING_MIN + 1);
		List<BlockPos> set = new ArrayList<>(candidates.subList(0, Math.min(wanted, candidates.size())));
		for (BlockPos cell : set) {
			if (!end.getBlockState(cell).is(EndBlocks.soulFlame())) {
				end.setBlock(cell, EndBlocks.soulFlame().defaultBlockState(), Block.UPDATE_ALL);
			}
			FLAMES.put(cell.immutable(), now + RING_LIFE);
		}
		keep(PULSES, new Pulse(now, List.copyOf(set)));
	}

	/** Puts out each flame whose time is up, unless a later pulse set it again. */
	private static void burnOut(final ServerLevel end, final long now) {
		if (FLAMES.isEmpty()) {
			return;
		}
		var flames = FLAMES.entrySet().iterator();
		while (flames.hasNext()) {
			var flame = flames.next();
			if (flame.getValue() <= now) {
				if (end.getBlockState(flame.getKey()).is(EndBlocks.soulFlame())) {
					end.setBlock(flame.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				flames.remove();
			}
		}
	}

	/** Fires five soul charges at the players in range, spread over them. False, and nothing fired, with nobody in range. */
	private static boolean volley(final ServerLevel end, final Blaze sovereign, final long now) {
		List<ServerPlayer> targets = new ArrayList<>();
		for (ServerPlayer player : end.players()) {
			if (player.isAlive() && !player.isSpectator() && !player.isCreative() && player.distanceTo(sovereign) <= BAR_RANGE) {
				targets.add(player);
			}
		}
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

	/** The Sovereign fell: the door to the rest area opens, the time is stored, and the bar closes. */
	private static void fall(final ServerLevel end, final Blaze sovereign) {
		long now = EndClock.now(end);
		CastleBuilder.setExitDoor(end, EndCastles.layout(end), EndCastle.min(), true);
		EndCastles.setState(end, EndCastles.state(end).withSovereignDeadAt(now));
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
