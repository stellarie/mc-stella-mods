package com.stella.villages.dummy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

/**
 * A village training dummy: a large armor stand by the bell that records the damage of each hit it takes and
 * shows the average of the last hits above it. Hits never break it. A missing dummy is placed again.
 */
public final class TrainingDummy {
	public static final String TAG = "mc-stella-villages.training_dummy";
	public static final String VILLAGE_TAG_PREFIX = "mc-stella-villages.dummy_of.";
	public static final int WINDOW = 20;
	public static final int IDLE_TICKS = 200;
	public static final double SCALE = 1.5;
	public static final int MIN_DISTANCE = 3;
	public static final int MAX_DISTANCE = 5;
	public static final double SEARCH_RADIUS = 8.0;
	private static final int CLEARANCE = 3;

	private record Window(Deque<Float> hits, long lastHitTick) {
	}

	private static final Map<ArmorStand, Window> WINDOWS = new WeakHashMap<>();

	private TrainingDummy() {
	}

	public static boolean is(final Entity entity) {
		return entity instanceof ArmorStand && entity.entityTags().contains(TAG);
	}

	/** The village's dummy near its bell, placed when none stands there. Null when no spot is free. */
	public static ArmorStand ensure(final ServerLevel level, final UUID villageId, final BlockPos bell) {
		ArmorStand existing = find(level, villageId, bell);
		if (existing != null) {
			return existing;
		}
		BlockPos spot = freeSpot(level, bell);
		return spot == null ? null : spawn(level, spot, villageId);
	}

	public static ArmorStand find(final ServerLevel level, final UUID villageId, final BlockPos bell) {
		String villageTag = VILLAGE_TAG_PREFIX + villageId;
		return level.getEntitiesOfClass(ArmorStand.class, new AABB(bell).inflate(SEARCH_RADIUS),
			stand -> stand.isAlive() && stand.entityTags().contains(villageTag)).stream().findFirst().orElse(null);
	}

	/** Rings of 3, 4, then 5 blocks around the bell, in a fixed order, on a sturdy floor with room above. */
	public static BlockPos freeSpot(final ServerLevel level, final BlockPos bell) {
		for (int radius = MIN_DISTANCE; radius <= MAX_DISTANCE; radius++) {
			int[][] offsets = {{radius, 0}, {0, radius}, {-radius, 0}, {0, -radius},
				{radius, radius}, {-radius, radius}, {-radius, -radius}, {radius, -radius}};
			for (int[] offset : offsets) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos spot = bell.offset(offset[0], dy, offset[1]);
					if (fits(level, spot)) {
						return spot;
					}
				}
			}
		}
		return null;
	}

	private static boolean fits(final ServerLevel level, final BlockPos spot) {
		BlockPos floor = spot.below();
		if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
			return false;
		}
		for (int up = 0; up < CLEARANCE; up++) {
			BlockPos air = spot.above(up);
			if (!level.getBlockState(air).getCollisionShape(level, air).isEmpty() || !level.getFluidState(air).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public static ArmorStand spawn(final ServerLevel level, final BlockPos spot, final UUID villageId) {
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
		if (stand == null) {
			return null;
		}
		stand.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
		stand.setShowArms(true);
		stand.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
		stand.addTag(TAG);
		stand.addTag(VILLAGE_TAG_PREFIX + villageId);
		stand.setCustomName(Component.literal("Training Dummy"));
		stand.setCustomNameVisible(true);
		level.addFreshEntity(stand);
		return stand;
	}

	/** Records one hit and rebuilds the label. The hit never reaches the stand. */
	public static void record(final ArmorStand stand, final DamageSource source, final float amount) {
		float scaled = DamageShare.scale(stand, source, amount);
		long now = stand.level().getGameTime();
		Window window = WINDOWS.get(stand);
		Deque<Float> hits = window == null || now - window.lastHitTick() > IDLE_TICKS ? new ArrayDeque<>() : window.hits();
		hits.addLast(scaled);
		while (hits.size() > WINDOW) {
			hits.removeFirst();
		}
		WINDOWS.put(stand, new Window(hits, now));
		stand.setCustomName(Component.literal(label(hits)));
	}

	public static String label(final Deque<Float> hits) {
		double sum = 0.0;
		for (float hit : hits) {
			sum += hit;
		}
		double average = hits.isEmpty() ? 0.0 : sum / hits.size();
		return "Avg " + number(average) + " · Last " + number(hits.isEmpty() ? 0.0 : hits.peekLast()) + " · "
			+ hits.size() + " hits";
	}

	/** One decimal, and whole numbers bare. */
	public static String number(final double value) {
		double rounded = Math.round(value * 10.0) / 10.0;
		return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
	}
}
