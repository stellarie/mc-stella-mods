package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Wisps light the way to a floor's portal: the shortest walk from where the player stands, shown for
 * twenty minutes. Whatever hostile mob stands on a lit way is enraged, faster and stronger, while it does.
 */
public final class TowerWisps {
	public static final int USES = 24;
	public static final int LIGHT_TICKS = 20 * 60 * 20;
	/** How close to a lit step a mob must stand to be enraged. */
	public static final double RAGE_REACH = 2.0;
	public static final int RAGE_TICKS = 60;
	public static final int RAGE_SPEED = 1;
	public static final int RAGE_STRENGTH = 1;
	/** How often the lit way is drawn and its mobs enraged. */
	public static final int SHOW_TICKS = 10;
	public static final int RAGE_EVERY = 20;
	/** A wisp rests this long after it is used, so a double click spends one use. */
	public static final int COOLDOWN_TICKS = 20;

	/**
	 * A lit way on a floor: whose it is, its steps from the player to the portal, the server tick it fades
	 * at, and the spots of the room within its reach, [x][z] from the room's inner corner.
	 */
	public record Trail(java.util.UUID owner, int floor, List<BlockPos> path, int until, boolean[][] reach) {
	}

	private static final Map<Integer, Integer> PINNED_LIGHT = new java.util.concurrent.ConcurrentHashMap<>();
	/** Every lit way; only ever touched on the server thread. */
	private static final List<Trail> TRAILS = new ArrayList<>();

	private TowerWisps() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerWisps::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TRAILS.clear());
	}

	/** The test seam: how long a floor's lit way lasts; null restores the shipped twenty minutes. */
	public static void pinLightTicks(final int floor, final @Nullable Integer ticks) {
		if (ticks == null) {
			PINNED_LIGHT.remove(floor);
		} else {
			PINNED_LIGHT.put(floor, ticks);
		}
	}

	public static List<Trail> trailsOn(final int floor) {
		return TRAILS.stream().filter(trail -> trail.floor() == floor).toList();
	}

	public static int usesOf(final net.minecraft.world.item.ItemStack wisp) {
		return wisp.getOrDefault(TowerItems.WISP_USES, 0);
	}

	public static void setUses(final net.minecraft.world.item.ItemStack wisp, final int uses) {
		wisp.set(TowerItems.WISP_USES, uses);
	}

	/** Lights the way from the player to their floor's portal; null if there is none to light. */
	public static @Nullable Trail light(final ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel tower) || !tower.dimension().equals(TowerTravel.TOWER)) {
			return null;
		}
		int floor = TowerLayout.floorAt(player.blockPosition());
		if (floor < 1 || !TowerFloors.isBuilt(tower, floor)) {
			return null;
		}
		List<BlockPos> path = path(tower, floor, new BlockPos(player.getBlockX(), TowerLayout.BASE_Y, player.getBlockZ()));
		if (path.isEmpty()) {
			return null;
		}
		Trail trail = new Trail(player.getUUID(), floor, List.copyOf(path),
			tower.getServer().getTickCount() + PINNED_LIGHT.getOrDefault(floor, LIGHT_TICKS), reach(floor, path));
		// A player keeps one lit way: a new one puts out the old.
		TRAILS.removeIf(old -> old.owner().equals(player.getUUID()));
		TRAILS.add(trail);
		StellaTower.LOGGER.debug("[TOWER] floor={} WISP lit {} steps to the portal", floor, path.size() - 1);
		return trail;
	}

	/** The shortest walk over the floor from a spot to its portal, both ends included; empty if there is none. */
	public static List<BlockPos> path(final ServerLevel tower, final int floor, final BlockPos from) {
		BlockPos origin = TowerLayout.origin(floor);
		int size = TowerLayout.SIZE;
		int startX = from.getX() - origin.getX();
		int startZ = from.getZ() - origin.getZ();
		List<BlockPos> portal = TowerPlan.at(tower, floor).portalInside();
		if (startX < 0 || startZ < 0 || startX >= size || startZ >= size) {
			return List.of();
		}
		int[][] came = new int[size][size];
		for (int[] row : came) {
			java.util.Arrays.fill(row, -1);
		}
		Deque<int[]> queue = new ArrayDeque<>();
		came[startX][startZ] = startX * size + startZ;
		queue.add(new int[] {startX, startZ});
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			BlockPos here = origin.offset(at[0], 0, at[1]);
			if (portal.contains(here)) {
				List<BlockPos> steps = new ArrayList<>();
				int x = at[0];
				int z = at[1];
				while (true) {
					steps.add(origin.offset(x, 0, z));
					int back = came[x][z];
					if (back == x * size + z) {
						break;
					}
					x = back / size;
					z = back % size;
				}
				Collections.reverse(steps);
				return steps;
			}
			for (int[] way : ways) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x < 0 || z < 0 || x >= size || z >= size || came[x][z] >= 0) {
					continue;
				}
				BlockPos feet = origin.offset(x, 0, z);
				if (tower.getBlockState(feet).getCollisionShape(tower, feet).isEmpty()
					&& tower.getBlockState(feet.above()).getCollisionShape(tower, feet.above()).isEmpty()) {
					came[x][z] = at[0] * size + at[1];
					queue.add(new int[] {x, z});
				}
			}
		}
		return List.of();
	}

	/** The spots of the room within reach of a way's steps. */
	private static boolean[][] reach(final int floor, final List<BlockPos> path) {
		BlockPos origin = TowerLayout.origin(floor);
		int size = TowerLayout.SIZE;
		int span = (int) Math.ceil(RAGE_REACH);
		boolean[][] near = new boolean[size][size];
		for (BlockPos step : path) {
			int sx = step.getX() - origin.getX();
			int sz = step.getZ() - origin.getZ();
			for (int x = Math.max(0, sx - span); x <= Math.min(size - 1, sx + span); x++) {
				for (int z = Math.max(0, sz - span); z <= Math.min(size - 1, sz + span); z++) {
					if ((x - sx) * (x - sx) + (z - sz) * (z - sz) <= RAGE_REACH * RAGE_REACH) {
						near[x][z] = true;
					}
				}
			}
		}
		return near;
	}

	private static void tick(final MinecraftServer server) {
		if (TRAILS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		TRAILS.removeIf(trail -> now >= trail.until());
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null) {
			return;
		}
		for (Trail trail : List.copyOf(TRAILS)) {
			// A way on a floor no one stands on keeps its time but is neither drawn nor felt.
			if (TowerMobs.playersOn(tower, trail.floor()).isEmpty()) {
				continue;
			}
			if (now % SHOW_TICKS == 0) {
				for (int i = 0; i < trail.path().size(); i += 2) {
					BlockPos step = trail.path().get(i);
					tower.sendParticles(ParticleTypes.END_ROD, step.getX() + 0.5, step.getY() + 0.3, step.getZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.0);
				}
			}
			if (now % RAGE_EVERY == 0) {
				enrage(tower, trail);
			}
		}
	}

	/** Every hostile mob standing on the lit way is enraged, faster and stronger, for a while after. */
	private static void enrage(final ServerLevel tower, final Trail trail) {
		BlockPos origin = TowerLayout.origin(trail.floor());
		AABB room = new AABB(origin.getX(), TowerLayout.BASE_Y, origin.getZ(),
			origin.getX() + TowerLayout.SIZE, TowerLayout.BASE_Y + TowerLayout.HEIGHT, origin.getZ() + TowerLayout.SIZE);
		for (Mob mob : tower.getEntitiesOfClass(Mob.class, room, mob -> mob instanceof Enemy && !(mob instanceof TowerGhost) && mob.isAlive())) {
			int x = mob.getBlockX() - origin.getX();
			int z = mob.getBlockZ() - origin.getZ();
			if (x >= 0 && z >= 0 && x < TowerLayout.SIZE && z < TowerLayout.SIZE && trail.reach()[x][z]) {
				mob.addEffect(new MobEffectInstance(MobEffects.SPEED, RAGE_TICKS, RAGE_SPEED));
				mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RAGE_TICKS, RAGE_STRENGTH));
				tower.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
			}
		}
	}
}
