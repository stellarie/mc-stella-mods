package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An active encounter Wither calls skeletons, zombies, husks, strays and wither skeletons to its side. They come
 * within 32 blocks at half its level, never above a cap that grows with the players near it, and drop nothing.
 * A new wave waits out a rage and a pause after it. The Wither's death, or its removal from the world, takes them.
 */
public final class WitherMinions {
	private static final int SPOT_ATTEMPTS = 10;
	private static final int FLOOR_SEARCH = 8;
	private static final int NO_END = Integer.MAX_VALUE;
	private static final List<Kind> KINDS = List.of(
		new Kind(EntityTypes.SKELETON, WitherConfig.MINIONS_WEIGHT_SKELETON),
		new Kind(EntityTypes.ZOMBIE, WitherConfig.MINIONS_WEIGHT_ZOMBIE),
		new Kind(EntityTypes.HUSK, WitherConfig.MINIONS_WEIGHT_HUSK),
		new Kind(EntityTypes.STRAY, WitherConfig.MINIONS_WEIGHT_STRAY),
		new Kind(EntityTypes.WITHER_SKELETON, WitherConfig.MINIONS_WEIGHT_WITHER_SKELETON)
	);

	private record Kind(EntityType<? extends Mob> type, String weightKey) {
	}

	/** Timers that restart with the server: when the next wave is due, and how long a rage's pause still holds. */
	private static final class Timers {
		boolean firstDone;
		int untilWave;
		int pause;
	}

	/** Test seam: each minion at the moment it was added, with its distance from the Wither. */
	public interface Recorder {
		void spawned(WitherBoss wither, Mob minion, double distance);
	}

	private static final List<Recorder> RECORDERS = new CopyOnWriteArrayList<>();
	private static final Set<WitherBoss> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<WitherBoss, Timers> TIMERS = new WeakHashMap<>();

	private WitherMinions() {
	}

	/** Several tests run at once, so each adds its own recorder and removes it at its end. */
	public static void addRecorder(final Recorder added) {
		RECORDERS.add(added);
	}

	public static void removeRecorder(final Recorder removed) {
		RECORDERS.remove(removed);
	}

	/** Puts an encounter Wither on the watch list. The list holds the Wither only until it is collected. */
	public static void watch(final WitherBoss wither) {
		WATCHED.add(wither);
	}

	public static void tick(final ServerLevel level) {
		if (WATCHED.isEmpty()) {
			return;
		}
		for (WitherBoss wither : List.copyOf(WATCHED)) {
			if (wither.level() != level) {
				continue;
			}
			if (wither.isRemoved()) {
				WATCHED.remove(wither);
				TIMERS.remove(wither);
				// An unloaded Wither leaves its minions to unload with their chunk.
				if (wither.getRemovalReason() != null && wither.getRemovalReason().shouldDestroy()) {
					discardAll(wither);
				}
				continue;
			}
			tickOne(level, wither);
		}
	}

	private static void tickOne(final ServerLevel level, final WitherBoss wither) {
		// A dying Wither calls no more help; its death hook takes the ones it has.
		if (!wither.isAlive() || !WitherEncounter.isActive(wither)) {
			return;
		}
		WitherConfig config = WitherConfigStore.get();
		Timers timers = TIMERS.computeIfAbsent(wither, key -> new Timers());
		if (WitherEncounter.isRaging(wither)) {
			timers.pause = config.getInt(WitherConfig.MINIONS_RAGE_PAUSE);
			return;
		}
		if (timers.pause > 0) {
			timers.pause--;
			return;
		}
		if (!timers.firstDone) {
			timers.firstDone = true;
			timers.untilWave = config.getInt(WitherConfig.MINIONS_WAVE_INTERVAL);
			summon(level, wither, config.getInt(WitherConfig.MINIONS_FIRST_WAVE));
			return;
		}
		if (--timers.untilWave > 0) {
			return;
		}
		timers.untilWave = config.getInt(WitherConfig.MINIONS_WAVE_INTERVAL);
		summon(level, wither, config.getInt(WitherConfig.MINIONS_WAVE_SIZE));
	}

	/** The most minions this Wither may have alive: a base, and a few more for each player within range. */
	public static int cap(final ServerLevel level, final WitherBoss wither) {
		WitherConfig config = WitherConfigStore.get();
		double radius = config.get(WitherConfig.MINIONS_CAP_RADIUS);
		int near = 0;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(wither) <= radius * radius) {
				near++;
			}
		}
		int cap = config.getInt(WitherConfig.MINIONS_CAP_BASE) + config.getInt(WitherConfig.MINIONS_CAP_PER_PLAYER) * near;
		return Math.max(config.getInt(WitherConfig.MINIONS_CAP_BASE), Math.min(config.getInt(WitherConfig.MINIONS_CAP_MAX), cap));
	}

	/** The living minions in the Wither's record. The record is what it added, so a stranger never counts. */
	public static List<Mob> alive(final ServerLevel level, final WitherBoss wither) {
		WitherState state = WitherState.find(wither);
		List<Mob> alive = new ArrayList<>();
		if (state == null) {
			return alive;
		}
		for (UUID id : state.minions()) {
			if (level.getEntity(id) instanceof Mob minion && minion.isAlive() && !minion.isRemoved()) {
				alive.add(minion);
			}
		}
		return alive;
	}

	/** Adds up to `count` minions, never past the cap. Returns the ones it added. */
	public static List<Mob> summon(final ServerLevel level, final WitherBoss wither, final int count) {
		WitherState state = WitherState.find(wither);
		List<Mob> added = new ArrayList<>();
		if (state == null || count <= 0) {
			return added;
		}
		List<Mob> living = alive(level, wither);
		int room = cap(level, wither) - living.size();
		RandomSource random = wither.getRandom();
		for (int i = 0; i < Math.min(count, room); i++) {
			Vec3 spot = findSpot(level, wither, random);
			if (spot == null) {
				StellaMobs.LOGGER.info("[BEHAVIOR] test=witherMinions SKIP uuid={} reason=no_spot", wither.getUUID());
				continue;
			}
			Mob minion = create(level, wither, pick(random), spot);
			if (minion != null && level.addFreshEntity(minion)) {
				added.add(minion);
				double distance = minion.position().distanceTo(wither.position());
				RECORDERS.forEach(recorder -> recorder.spawned(wither, minion, distance));
			}
		}
		List<UUID> record = new ArrayList<>();
		living.forEach(minion -> record.add(minion.getUUID()));
		added.forEach(minion -> record.add(minion.getUUID()));
		WitherState.set(wither, WitherState.find(wither).withMinions(record));
		if (!added.isEmpty()) {
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherMinions WAVE uuid={} asked={} added={} alive={} cap={}",
				wither.getUUID(), count, added.size(), record.size(), cap(level, wither));
		}
		return added;
	}

	/** Weighted pick among the five kinds. */
	public static EntityType<? extends Mob> pick(final RandomSource random) {
		WitherConfig config = WitherConfigStore.get();
		double total = 0.0;
		for (Kind kind : KINDS) {
			total += Math.max(0.0, config.get(kind.weightKey()));
		}
		double roll = random.nextDouble() * total;
		for (Kind kind : KINDS) {
			roll -= Math.max(0.0, config.get(kind.weightKey()));
			if (roll < 0.0) {
				return kind.type();
			}
		}
		return KINDS.get(0).type();
	}

	public static boolean isKind(final EntityType<?> type) {
		return KINDS.stream().anyMatch(kind -> kind.type() == type);
	}

	private static @Nullable Mob create(final ServerLevel level, final WitherBoss wither, final EntityType<? extends Mob> type, final Vec3 spot) {
		Mob minion = type.create(level, EntitySpawnReason.EVENT);
		if (minion == null) {
			return null;
		}
		RandomSource random = minion.getRandom();
		minion.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
		minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()), EntitySpawnReason.EVENT, null);
		minion.setBaby(false);
		int half = ThreatState.level(wither) / 2;
		// All of these must exist before the add: ENTITY_LOAD fires inside addFreshEntity and reads them.
		((AttachmentTarget) minion).setAttached(ThreatState.TYPE, new ThreatState(half, 0, 0, 0));
		EliteState.setSummoned(minion, true);
		minion.addTag(WitherEncounter.MINION_TAG);
		minion.skipDropExperience();
		minion.setPersistenceRequired();
		// Fire resistance keeps a zombie or skeleton from burning off in daylight.
		minion.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		return minion;
	}

	/** A floor within reach of the Wither's height, two blocks clear above it, away from the players. */
	private static @Nullable Vec3 findSpot(final ServerLevel level, final WitherBoss wither, final RandomSource random) {
		WitherConfig config = WitherConfigStore.get();
		double min = config.get(WitherConfig.MINIONS_SPAWN_MIN);
		double max = config.get(WitherConfig.MINIONS_SPAWN_MAX);
		double clearance = config.get(WitherConfig.MINIONS_PLAYER_CLEARANCE);
		for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + (max - min) * random.nextDouble();
			double x = wither.getX() + Math.cos(angle) * distance;
			double z = wither.getZ() + Math.sin(angle) * distance;
			Double feet = floorAt(level, x, wither.getY(), z);
			if (feet == null) {
				continue;
			}
			Vec3 spot = new Vec3(x, feet, z);
			if (spot.distanceToSqr(wither.position()) > max * max || nearAPlayer(level, spot, clearance)) {
				continue;
			}
			return spot;
		}
		return null;
	}

	private static boolean nearAPlayer(final ServerLevel level, final Vec3 spot, final double clearance) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.position().distanceToSqr(spot) < clearance * clearance) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Double floorAt(final ServerLevel level, final double x, final double y, final double z) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y + FLOOR_SEARCH, z);
		if (!level.isLoaded(cursor)) {
			return null;
		}
		int top = cursor.getY();
		for (int blockY = top; blockY >= top - 2 * FLOOR_SEARCH; blockY--) {
			cursor.setY(blockY);
			boolean solid = !level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty();
			if (!solid) {
				continue;
			}
			cursor.setY(blockY + 1);
			boolean room = level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty();
			cursor.setY(blockY + 2);
			room &= level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty();
			return room ? (double) (blockY + 1) : null;
		}
		return null;
	}

	/** Takes every minion in the record out of the world. */
	public static void discardAll(final WitherBoss wither) {
		WitherState state = WitherState.find(wither);
		if (state == null || !(wither.level() instanceof ServerLevel level)) {
			return;
		}
		int removed = 0;
		for (UUID id : state.minions()) {
			Entity minion = level.getEntity(id);
			if (minion != null) {
				minion.discard();
				removed++;
			}
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherMinions DISCARD uuid={} removed={}", wither.getUUID(), removed);
	}
}
