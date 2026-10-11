package com.stella.tower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Each floor's traps: laid in the maze's paths as the floor is built, sprung by a player who steps on one,
 * and resting a while after. What a trap does is its theme's; what it deals grows with the floor as a tower
 * mob's blows do.
 */
public final class TowerTraps {
	public static final int TRAPS_PER_FLOOR = 20;
	public static final int REARM_TICKS = 100;
	/** No trap within this many cells of the arrival's cell. */
	public static final int ARRIVAL_CLEAR_CELLS = 2;
	public static final int DESERT_ARROWS = 3;
	public static final float ARROW_BASE = 2.0F;
	public static final float FIRE_BASE = 2.0F;
	public static final float STORM_BASE = 3.0F;
	public static final float SPIKE_BASE = 4.0F;
	public static final int FIRE_SECONDS = 4;
	public static final int FREEZE_TICKS = 140;
	public static final int VOID_MIN_DISTANCE = 16;
	public static final int AMBUSH = 2;
	public static final float FROST_BASE = 2.0F;
	/** A bog's poison grows a level with the floor's buff level, up to this. */
	public static final int BOG_MAX_AMPLIFIER = 4;
	private static final double ARROW_SPEED = 1.5;
	private static final int PLACE_TRIES = 64;
	private static final long SALT = 0x54524150L;
	/** When each trap last sprang, by the server's tick count, which runs on whatever the level's clock does. */
	private static final Map<Long, Integer> SPRUNG = new HashMap<>();

	/** One trap: its spot, as an offset from the room's inner corner, its theme, and its story's height. */
	public record Trap(int x, int z, TrapTheme theme, int y) {
		public Trap(final int x, final int z, final TrapTheme theme) {
			this(x, z, theme, 0);
		}
	}

	/** What a trap did when sprung: its theme, and what it brought into the world. */
	public record Sprung(TrapTheme theme, List<Entity> spawned) {
	}

	private TowerTraps() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SPRUNG.clear());
	}

	/** The floor's traps. A pure function of the seed and the floor, so a floor drawn twice is drawn the same. */
	public static List<Trap> layout(final long seed, final int floor) {
		return layout(seed, floor, TowerPlan.of(seed, floor));
	}

	/**
	 * The floor's traps on a given plan, over all its stories: one at most to each maze cell of a story, on the
	 * story's own floor, clear of the arrival's cells, the hall, the portal's way, the stairs' ends and the safe zones.
	 */
	public static List<Trap> layout(final long seed, final int floor, final TowerPlan plan) {
		TrapTheme theme = TrapTheme.of(floor);
		if (theme == TrapTheme.NONE) {
			return List.of();
		}
		Random random = new Random(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L));
		if (plan.isBiome()) {
			return biomeLayout(random, theme, plan);
		}
		List<int[]> cells = new ArrayList<>();
		int startX = plan.arrivalCell()[0];
		int startZ = plan.arrivalCell()[1];
		int[] portalCell = plan.portalCell();
		int hall = plan.hall();
		int hallLow = plan.hallLow();
		for (int s = 0; s < plan.stories().size(); s++) {
			for (int cx = 0; cx < TowerMaze.CELLS; cx++) {
				for (int cz = 0; cz < TowerMaze.CELLS; cz++) {
					int x = 1 + cx * TowerMaze.PITCH;
					int z = 1 + cz * TowerMaze.PITCH;
					boolean byArrival = s == 0 && Math.max(Math.abs(cx - startX), Math.abs(cz - startZ)) <= ARRIVAL_CLEAR_CELLS;
					boolean inHall = s == 0 && hall > 0 && x + TowerMaze.PATH > hallLow && x < hallLow + hall && z + TowerMaze.PATH > hallLow && z < hallLow + hall;
					// The portal's cell, the cell either side of it along the frame, and the cell it is entered from.
					boolean inNook = mazeLike(plan) && s == plan.portalStory() && hall == 0
						&& (cx == portalCell[0] && Math.abs(cz - portalCell[1]) <= 1 || cx == portalCell[0] + plan.portalFacing() && cz == portalCell[1]);
					// A cell that is mostly wall, or mostly off the story's floor, holds no trap.
					boolean solid = openIn(plan, s, x, z) < MIN_OPEN;
					if (!byArrival && !inHall && !inNook && !solid) {
						cells.add(new int[] {s, cx, cz});
					}
				}
			}
		}
		Collections.shuffle(cells, random);
		List<TrapTheme> laid = new ArrayList<>();
		for (TrapTheme each : TrapTheme.values()) {
			if (each.laid()) {
				laid.add(each);
			}
		}
		List<Trap> traps = new ArrayList<>();
		for (int[] cell : cells.subList(0, Math.min(TRAPS_PER_FLOOR, cells.size()))) {
			TrapTheme kind = theme == TrapTheme.CHAOS ? laid.get(random.nextInt(laid.size())) : theme;
			int s = cell[0];
			int x = 1 + cell[1] * TowerMaze.PITCH + random.nextInt(TowerMaze.PATH);
			int z = 1 + cell[2] * TowerMaze.PITCH + random.nextInt(TowerMaze.PATH);
			if (!trappable(plan, s, x, z)) {
				// Not the story's open floor: the cell's open spots, one drawn.
				List<int[]> spots = new ArrayList<>();
				for (int dx = 0; dx < TowerMaze.PATH; dx++) {
					for (int dz = 0; dz < TowerMaze.PATH; dz++) {
						int sx = 1 + cell[1] * TowerMaze.PITCH + dx;
						int sz = 1 + cell[2] * TowerMaze.PITCH + dz;
						if (trappable(plan, s, sx, sz)) {
							spots.add(new int[] {sx, sz});
						}
					}
				}
				if (spots.isEmpty()) {
					continue;
				}
				int[] pick = spots.get(random.nextInt(spots.size()));
				x = pick[0];
				z = pick[1];
			}
			traps.add(new Trap(x, z, kind, plan.stories().get(s).y()));
		}
		return traps;
	}

	/** On a biome floor, how far from the portal and from the arrival a trap keeps, and from another trap, in blocks. */
	private static final int BIOME_PORTAL_CLEAR = 10;
	private static final int BIOME_ARRIVAL_CLEAR = 10;
	private static final int BIOME_APART = 8;

	/** A biome floor's traps: bare level ground, off the stair, the arrival and the water, spread apart. */
	private static List<Trap> biomeLayout(final Random random, final TrapTheme theme, final TowerPlan plan) {
		TowerStory story = plan.stories().get(0);
		List<int[]> spots = new ArrayList<>();
		for (int x = 1; x < plan.width() - 1; x++) {
			for (int z = 1; z < plan.depth() - 1; z++) {
				boolean far = Math.max(Math.abs(x - plan.portalX()), Math.abs(z - plan.portalZ())) > BIOME_PORTAL_CLEAR
					&& Math.max(Math.abs(x - plan.arrivalX()), Math.abs(z - plan.arrivalZ())) > BIOME_ARRIVAL_CLEAR;
				int y = plan.standY(x, z);
				if (far && story.flatAt(x, z) && y >= 0 && !plan.biome().water()[x][z] && !plan.biome().fragile()[x][z] && !plan.inSafeZone(x, y, z) && !plan.biome().inDais(plan, x, z)) {
					spots.add(new int[] {x, z, y});
				}
			}
		}
		Collections.shuffle(spots, random);
		List<TrapTheme> laid = new ArrayList<>();
		for (TrapTheme each : TrapTheme.values()) {
			if (each.laid()) {
				laid.add(each);
			}
		}
		List<Trap> traps = new ArrayList<>();
		for (int[] spot : spots) {
			if (traps.size() >= TowerMobs.scaled(TRAPS_PER_FLOOR, plan)) {
				break;
			}
			boolean apart = true;
			for (Trap other : traps) {
				apart &= Math.max(Math.abs(other.x() - spot[0]), Math.abs(other.z() - spot[1])) >= BIOME_APART;
			}
			if (apart) {
				traps.add(new Trap(spot[0], spot[1], theme == TrapTheme.CHAOS ? laid.get(random.nextInt(laid.size())) : theme, spot[2]));
			}
		}
		return traps;
	}

	/** The fewest open spots a maze cell of a story may have, to be given a trap. */
	private static final int MIN_OPEN = 12;
	/** On a floor that is not a maze, how far from the portal a trap keeps on its story, in blocks. */
	private static final int PORTAL_CLEAR = 6;
	/** How far from a stair's or a ramp's ends a trap keeps, in blocks. */
	private static final int WAY_CLEAR = 1;

	/** A floor whose paths follow the maze's cells: a hall floor or a labyrinth, whose portal sits in a nook. */
	private static boolean mazeLike(final TowerPlan plan) {
		return plan.archetype() == TowerArchetype.HALL || plan.archetype() == TowerArchetype.LABYRINTH;
	}

	private static int openIn(final TowerPlan plan, final int story, final int x, final int z) {
		int open = 0;
		for (int dx = 0; dx < TowerMaze.PATH; dx++) {
			for (int dz = 0; dz < TowerMaze.PATH; dz++) {
				open += trappable(plan, story, x + dx, z + dz) ? 1 : 0;
			}
		}
		return open;
	}

	/**
	 * Whether a trap may lie at a spot of a story: its own floor to stand on, clear of the frame, the safe zones and the
	 * stairs' ends, and off the maze, clear of the portal.
	 */
	private static boolean trappable(final TowerPlan plan, final int story, final int x, final int z) {
		TowerStory at = plan.stories().get(story);
		if (!at.flatAt(x, z) || story == plan.portalStory() && plan.isFramePost(x, z) || plan.inSafeZone(x, at.y(), z)) {
			return false;
		}
		for (TowerFlight flight : plan.flights()) {
			for (int w = 0; w < flight.width(); w++) {
				if (flight.base() == at.y() && near(flight.cellX(-1, w), flight.cellZ(-1, w), x, z)
					|| flight.top() == at.y() && near(flight.cellX(flight.length(), w), flight.cellZ(flight.length(), w), x, z)) {
					return false;
				}
			}
		}
		return mazeLike(plan) || story != plan.portalStory() || Math.max(Math.abs(x - plan.portalX()), Math.abs(z - plan.portalZ())) > PORTAL_CLEAR;
	}

	private static boolean near(final int ax, final int az, final int bx, final int bz) {
		return Math.max(Math.abs(ax - bx), Math.abs(az - bz)) <= WAY_CLEAR;
	}

	/** Springs a trap of this theme under a player, as for this floor; null while it rests. */
	public static @Nullable Sprung spring(final ServerLevel tower, final BlockPos pos, final ServerPlayer player, final TrapTheme theme, final int floor) {
		if (!theme.laid()) {
			return null;
		}
		int now = tower.getServer().getTickCount();
		Integer last = SPRUNG.get(pos.asLong());
		if (last != null && now - last < REARM_TICKS) {
			return null;
		}
		SPRUNG.put(pos.asLong(), now);
		if (SPRUNG.size() > 512) {
			SPRUNG.values().removeIf(at -> now - at >= REARM_TICKS);
		}
		List<Entity> spawned = new ArrayList<>();
		switch (theme) {
			case DESERT -> desert(tower, player, floor, spawned);
			case INFERNO -> {
				player.igniteForSeconds(FIRE_SECONDS);
				player.hurtServer(tower, tower.damageSources().inFire(), dealt(FIRE_BASE, floor));
				burst(tower, player, ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE);
			}
			case VOID -> throwAway(tower, pos, player);
			case FROST -> {
				player.hurtServer(tower, tower.damageSources().freeze(), dealt(FROST_BASE, floor));
				player.setTicksFrozen(player.getTicksRequiredToFreeze() + FREEZE_TICKS);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
				burst(tower, player, ParticleTypes.SNOWFLAKE, SoundEvents.POWDER_SNOW_STEP);
			}
			case BOG -> {
				player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, Math.min(BOG_MAX_AMPLIFIER, TowerBuffs.level(floor))));
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
				burst(tower, player, ParticleTypes.ITEM_SLIME, SoundEvents.SLIME_SQUISH);
			}
			case STORM -> {
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(tower, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					// The strike is shown; the blow is the trap's own, grown with the floor.
					bolt.snapTo(player.position());
					bolt.setVisualOnly(true);
					if (tower.addFreshEntity(bolt)) {
						spawned.add(bolt);
					}
				}
				player.hurtServer(tower, tower.damageSources().lightningBolt(), dealt(STORM_BASE, floor));
			}
			case SHADOW -> shadow(tower, pos, player, floor, spawned);
			case SPIKES -> {
				// A cactus's kind of wound, which armor turns, as it turns a tower mob's blow.
				player.hurtServer(tower, tower.damageSources().cactus(), dealt(SPIKE_BASE, floor));
				burst(tower, player, ParticleTypes.CRIT, SoundEvents.POINTED_DRIPSTONE_LAND);
			}
			default -> {
			}
		}
		StellaTower.LOGGER.debug("[TOWER] trap {} sprung under {} at {}, floor {}", theme, player.getName().getString(), pos, floor);
		return new Sprung(theme, List.copyOf(spawned));
	}

	/** What a trap deals: its base, grown with the floor as a tower mob's blow is. */
	private static float dealt(final float base, final int floor) {
		return (float) TowerCurve.damage(base, floor);
	}

	private static void burst(final ServerLevel tower, final ServerPlayer player, final net.minecraft.core.particles.ParticleOptions particle, final SoundEvent sound) {
		tower.sendParticles(particle, player.getX(), player.getY() + 0.5, player.getZ(), 24, 0.4, 0.4, 0.4, 0.05);
		tower.playSound(null, player.blockPosition(), sound, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	/** Sand gives under the player, and arrows loosed from the ceiling come down on them. */
	private static void desert(final ServerLevel tower, final ServerPlayer player, final int floor, final List<Entity> spawned) {
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
		RandomSource random = tower.getRandom();
		double ceiling = ceilingOver(tower, player) - 1.0;
		for (int i = 0; i < DESERT_ARROWS; i++) {
			Arrow arrow = EntityTypes.ARROW.create(tower, EntitySpawnReason.TRIGGERED);
			if (arrow == null) {
				continue;
			}
			arrow.setPos(player.getX() + (random.nextDouble() - 0.5) * 0.6, ceiling, player.getZ() + (random.nextDouble() - 0.5) * 0.6);
			arrow.setDeltaMovement(0.0, -ARROW_SPEED, 0.0);
			// The volley's wound is dealt at once below: arrows landing together would meet the grace a hurt
			// player has after the first, and two of the three would count for nothing.
			arrow.setBaseDamage(0.0);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			if (tower.addFreshEntity(arrow)) {
				spawned.add(arrow);
			}
		}
		net.minecraft.world.damagesource.DamageSource source = spawned.isEmpty() || !(spawned.get(0) instanceof AbstractArrow first)
			? tower.damageSources().cactus() : tower.damageSources().arrow(first, null);
		player.hurtServer(tower, source, dealt(ARROW_BASE * DESERT_ARROWS, floor));
		burst(tower, player, ParticleTypes.CLOUD, SoundEvents.ARROW_SHOOT);
	}

	/** How high the arrows start: under the first block over the player's head, at most a few blocks up. */
	private static int ceilingOver(final ServerLevel tower, final ServerPlayer player) {
		BlockPos head = player.blockPosition().above(2);
		for (int up = 0; up < ARROW_DROP; up++) {
			if (!tower.getBlockState(head.above(up)).isAir()) {
				return head.getY() + up;
			}
		}
		return head.getY() + ARROW_DROP;
	}

	/** The highest the desert's arrows fall from, over the player's head. */
	private static final int ARROW_DROP = 5;

	/** The floor throws the player to some open spot of one of its stories, well away. */
	private static void throwAway(final ServerLevel tower, final BlockPos pos, final ServerPlayer player) {
		int floor = TowerLayout.floorAt(pos);
		if (floor < 1) {
			return;
		}
		BlockPos origin = TowerLayout.origin(floor);
		RandomSource random = tower.getRandom();
		TowerPlan plan = TowerPlan.at(tower, floor);
		List<TowerStory> stories = plan.stories();
		for (int attempt = 0; attempt < PLACE_TRIES; attempt++) {
			int x = random.nextInt(plan.width());
			int z = random.nextInt(plan.depth());
			int y = plan.isBiome() ? plan.standY(x, z) : stories.get(random.nextInt(stories.size())).y();
			if (y < TowerWalk.LOW) {
				continue;
			}
			BlockPos feet = origin.offset(x, y, z);
			Vec3 to = Vec3.atBottomCenterOf(feet);
			if (landsSafely(tower, feet) && to.distanceTo(player.position()) >= VOID_MIN_DISTANCE) {
				burst(tower, player, ParticleTypes.PORTAL, SoundEvents.ENDERMAN_TELEPORT);
				player.teleportTo(tower, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), true);
				burst(tower, player, ParticleTypes.PORTAL, SoundEvents.ENDERMAN_TELEPORT);
				return;
			}
		}
	}

	/** Darkness falls, and the floor's own mobs come out of it. */
	private static void shadow(final ServerLevel tower, final BlockPos pos, final ServerPlayer player, final int floor, final List<Entity> spawned) {
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
		burst(tower, player, ParticleTypes.SCULK_SOUL, SoundEvents.SCULK_SHRIEKER_SHRIEK);
		int room = TowerLayout.floorAt(pos);
		RandomSource random = tower.getRandom();
		List<EntityType<? extends Mob>> pool = room > 0 ? TowerMobs.poolOf(tower, floor) : TowerMobs.pool(floor);
		// A lost room's trap calls mobs of the room's own level, not of the floors' formula.
		int level = room > 0 ? TowerMobs.levelOf(floor) : floor + TowerMobs.LOST_ROOM_FLOOR_OFFSET - 1;
		// A kind that finds no room is let go, and another called in its place.
		for (int called = 0; called < AMBUSH * 4 && spawned.size() < AMBUSH; called++) {
			if (room > 0 && TowerMobs.count(tower, room) >= TowerMobs.FLOOR_CAP) {
				return;
			}
			Mob mob = pool.get(random.nextInt(pool.size())).create(tower, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			mob.snapTo(player.position());
			mob.finalizeSpawn(tower, tower.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.EVENT, null);
			TowerMobs.settle(mob);
			boolean placed = false;
			for (int attempt = 0; attempt < PLACE_TRIES && !placed; attempt++) {
				BlockPos feet = player.blockPosition().offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
				Vec3 spot = TowerMobs.spotFor(mob, feet);
				double away = spot.distanceTo(player.position());
				mob.snapTo(spot);
				placed = away >= 3.0 && TowerLayout.floorAt(feet) == room && TowerMobs.standsFree(tower, mob, feet);
			}
			if (!placed) {
				mob.discard();
				continue;
			}
			TowerMobs.makeTowerMob(mob, floor, TowerElement.values()[random.nextInt(TowerElement.values().length)], TowerRank.MOB, level);
			if (tower.addFreshEntity(mob)) {
				spawned.add(mob);
			}
		}
	}

	/** Whether a thrown player may land with their feet here: in open air, not in the portal, and not on a trap. */
	public static boolean landsSafely(final ServerLevel tower, final BlockPos feet) {
		return tower.getBlockState(feet).isAir() && tower.getBlockState(feet.above()).isAir()
			&& !(tower.getBlockState(feet.below()).getBlock() instanceof TowerTrapBlock) && open(tower, feet);
	}

	private static boolean open(final ServerLevel tower, final BlockPos feet) {
		return tower.getBlockState(feet).getCollisionShape(tower, feet).isEmpty()
			&& tower.getBlockState(feet.above()).getCollisionShape(tower, feet.above()).isEmpty()
			&& tower.getBlockState(feet.below()).isFaceSturdy(tower, feet.below(), net.minecraft.core.Direction.UP);
	}
}
