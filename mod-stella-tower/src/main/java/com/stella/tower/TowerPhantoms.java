package com.stella.tower;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where the tower's phantoms come from. Some open floors, about {@value #CHANCE_PERCENT} in a hundred of those with sky
 * enough, are drawn with one or two phantoms from the tower's seed and the floor number alone. While a player is on such
 * a floor and it holds fewer than it was drawn with, a round of the spawner may raise one: a {@link TowerPhantom} with a
 * skeleton archer riding it, both tower mobs of the floor.
 */
public final class TowerPhantoms {
	public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, StellaTower.id("tower_phantom"));
	public static final EntityType<TowerPhantom> PHANTOM = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.<TowerPhantom>of(TowerPhantom::new, MobCategory.MONSTER).sized(0.9F, 0.5F).eyeHeight(0.175F)
			.passengerAttachments(0.3375F).ridingOffset(-0.125F).clientTrackingRange(8).build(KEY));
	/** The sky a floor needs over its highest ground for a phantom to hold its height in, in blocks. */
	public static final int MIN_SKY = 16;
	/** The lowest floor that draws a phantom. */
	public static final int MIN_FLOOR = 5;
	/** The share of floors with sky enough that draw a phantom, in a hundred. */
	public static final int CHANCE_PERCENT = 15;
	/** The chance a round of the spawner raises a phantom a floor lacks. */
	public static final double RISE_CHANCE = 0.1;
	/** How high over the player a phantom holds, nearest and farthest, in blocks. */
	public static final double HOLD_MIN = 10.0;
	public static final double HOLD_MAX = 14.0;
	/** The least a phantom is raised from a player, in blocks across. */
	public static final double RISE_CLEAR = 16.0;
	private static final long SALT = 0x50484E54L;
	private static final int PLACE_TRIES = 40;
	/** The test seam: a floor's pinned chance that a round raises a phantom. */
	private static final Map<Integer, Double> PINNED_RISE = new java.util.concurrent.ConcurrentHashMap<>();

	private TowerPhantoms() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(PHANTOM, net.minecraft.world.entity.monster.Monster.createMonsterAttributes());
	}

	public static void pinRiseChance(final int floor, final @Nullable Double chance) {
		if (chance == null) {
			PINNED_RISE.remove(floor);
		} else {
			PINNED_RISE.put(floor, chance);
		}
	}

	/** Whether the floor has sky enough over its ground for a phantom: an open roof, and {@value #MIN_SKY} blocks over its highest ground. */
	public static boolean eligible(final TowerPlan plan) {
		if (!plan.isBiome() || !plan.biome().biome().openRoof() || plan.floor() < MIN_FLOOR) {
			return false;
		}
		int highest = 0;
		for (short[] column : plan.biome().feet()) {
			for (short feet : column) {
				highest = Math.max(highest, feet);
			}
		}
		return plan.roofY() - highest >= MIN_SKY;
	}

	/** How many phantoms the seed draws for a floor, if it has sky enough: none for most, one or two for {@value #CHANCE_PERCENT} in a hundred. */
	public static int drawn(final long seed, final int floor) {
		long h = TowerPlan.mix(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L));
		if ((h >>> 8) % 100 >= CHANCE_PERCENT) {
			return 0;
		}
		return (h >>> 40) % 5 < 2 ? 2 : 1;
	}

	/** How many phantoms the floor holds when it is stocked: those the seed draws, if it has sky enough. */
	public static int wanted(final TowerPlan plan) {
		return eligible(plan) ? drawn(plan.seed(), plan.floor()) : 0;
	}

	/** The phantoms alive on a floor. */
	public static List<TowerPhantom> on(final ServerLevel tower, final int floor) {
		return tower.getEntitiesOfClass(TowerPhantom.class, TowerMobs.room(floor), Phantom::isAlive);
	}

	/** A round of the spawner on an occupied floor: may raise a phantom the floor lacks. */
	static void haunt(final ServerLevel tower, final int floor) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		int wanted = wanted(plan);
		if (wanted == 0 || !TowerElites.roomLoaded(tower, floor) || on(tower, floor).size() >= wanted
			|| tower.getRandom().nextDouble() >= PINNED_RISE.getOrDefault(floor, RISE_CHANCE)) {
			return;
		}
		RandomSource random = tower.getRandom();
		BlockPos origin = plan.origin();
		List<ServerPlayer> players = TowerMobs.playersOn(tower, floor);
		for (int attempt = 0; attempt < PLACE_TRIES; attempt++) {
			int x = 2 + random.nextInt(plan.width() - 4);
			int z = 2 + random.nextInt(plan.depth() - 4);
			int ground = plan.standY(x, z);
			if (ground < 0) {
				continue;
			}
			Vec3 at = new Vec3(origin.getX() + x + 0.5, TowerLayout.BASE_Y + ground + (HOLD_MIN + HOLD_MAX) / 2.0, origin.getZ() + z + 0.5);
			boolean near = false;
			for (ServerPlayer player : players) {
				near |= Math.hypot(player.getX() - at.x, player.getZ() - at.z) < RISE_CLEAR;
			}
			if (!near && raise(tower, floor, at) != null) {
				return;
			}
		}
	}

	/** Raises a phantom and its archer at a spot, both tower mobs of the floor; null if the spot is not clear. */
	public static @Nullable TowerPhantom raise(final ServerLevel tower, final int floor, final Vec3 at) {
		return raise(tower, floor, TowerMobs.levelOf(floor), at);
	}

	/** {@link #raise(ServerLevel, int, Vec3)} for a pair of a level of its own, as a test needs one that a bow does not kill by. */
	public static @Nullable TowerPhantom raise(final ServerLevel tower, final int floor, final int level, final Vec3 at) {
		TowerPhantom phantom = PHANTOM.create(tower, EntitySpawnReason.EVENT);
		Skeleton archer = EntityTypes.SKELETON.create(tower, EntitySpawnReason.EVENT);
		if (phantom == null || archer == null) {
			return null;
		}
		phantom.snapTo(at);
		if (!tower.noCollision(phantom, phantom.getBoundingBox().inflate(0.5))) {
			phantom.discard();
			archer.discard();
			return null;
		}
		BlockPos here = phantom.blockPosition();
		phantom.finalizeSpawn(tower, tower.getCurrentDifficultyAt(here), EntitySpawnReason.EVENT, null);
		archer.snapTo(at);
		archer.finalizeSpawn(tower, tower.getCurrentDifficultyAt(here), EntitySpawnReason.EVENT, null);
		TowerElement element = TowerElement.values()[tower.getRandom().nextInt(TowerElement.values().length)];
		TowerMobs.makeTowerMob(phantom, floor, element, TowerRank.MOB, level);
		TowerMobs.makeTowerMob(archer, floor, element, TowerRank.MOB, level);
		archer.startRiding(phantom, true, false);
		return tower.tryAddFreshEntityWithPassengers(phantom) ? phantom : null;
	}
}
