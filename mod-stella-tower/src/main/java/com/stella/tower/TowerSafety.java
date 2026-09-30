package com.stella.tower;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a floor's safe zones keep out. Nothing is raised in a zone (see {@link TowerMobs#placeInRoom}), no tower
 * mob stays in one, and a player inside one draws no aggro from any mob, a boss's or an elite's included.
 * <p>
 * Three parts. {@link #sheltered} is the one test, and the targeting mixins ask it: a mob is never given a
 * sheltered player as a target, so it never takes one up, whatever its goals or its brain. The sweep, every
 * {@link #SWEEP_TICKS} ticks over the floors with a player on them, sends out any tower mob whose body touches a
 * zone, to the nearest free spot outside, and lets go of any target a mob held on a player now in a zone: its
 * target, its brain's attack target and anger, its neutral anger.
 */
public final class TowerSafety {
	/** How often the floors with a player on them are swept. */
	public static final int SWEEP_TICKS = 5;
	/** How far round a mob an ejection looks for a free spot outside the zones, in blocks. */
	public static final int SEARCH = TowerMaze.SAFE;

	private TowerSafety() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerSafety::tick);
	}

	/** Whether this entity is a player standing in a safe zone of the tower floor they are on. */
	public static boolean sheltered(final @Nullable LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel tower) || !tower.dimension().equals(TowerTravel.TOWER)) {
			return false;
		}
		BlockPos at = player.blockPosition();
		int floor = TowerLayout.floorAt(at);
		if (floor <= 0) {
			return false;
		}
		BlockPos origin = TowerLayout.origin(floor);
		return TowerPlan.at(tower, floor).inSafeZone(at.getX() - origin.getX(), at.getZ() - origin.getZ());
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % SWEEP_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null || tower.players().isEmpty()) {
			return;
		}
		Set<Integer> occupied = new LinkedHashSet<>();
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (floor > 0 && !player.isSpectator() && TowerFloors.isBuilt(tower, floor)) {
				occupied.add(floor);
			}
		}
		for (int floor : occupied) {
			sweep(tower, floor);
		}
	}

	/** One sweep of a floor: the mobs in its zones sent out, and the players in them let be. */
	static void sweep(final ServerLevel tower, final int floor) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		for (TowerMaze.Zone zone : plan.safeZones()) {
			AABB box = zoneBox(plan, zone);
			for (Mob mob : tower.getEntitiesOfClass(Mob.class, box.inflate(SEARCH), TowerSafety::keptOut)) {
				if (mob.isAlive() && touchesZone(plan, mob.getBoundingBox())) {
					eject(tower, plan, mob);
				}
			}
		}
		Set<UUID> shelter = new LinkedHashSet<>();
		for (ServerPlayer player : TowerMobs.playersOn(tower, floor)) {
			if (sheltered(player)) {
				shelter.add(player.getUUID());
			}
		}
		if (!shelter.isEmpty()) {
			for (Mob mob : tower.getEntitiesOfClass(Mob.class, TowerMobs.room(floor))) {
				calm(mob, shelter);
			}
		}
	}

	/** The tower mobs the zones keep out: every one but a ghost, which is on the players' side. */
	private static boolean keptOut(final Mob mob) {
		return TowerMobs.stateOf(mob) != null && !(mob instanceof TowerGhost);
	}

	/** A zone's whole column of air, as the box a mob's body is tested against. */
	static AABB zoneBox(final TowerPlan plan, final TowerMaze.Zone zone) {
		BlockPos o = plan.origin();
		return new AABB(o.getX() + zone.x(), o.getY() - 1, o.getZ() + zone.z(),
			o.getX() + zone.x() + TowerMaze.SAFE, o.getY() + TowerLayout.HEIGHT, o.getZ() + zone.z() + TowerMaze.SAFE);
	}

	static boolean touchesZone(final TowerPlan plan, final AABB body) {
		for (TowerMaze.Zone zone : plan.safeZones()) {
			if (body.intersects(zoneBox(plan, zone))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Moves a mob to the nearest spot of the room where its whole body stands clear of every zone and of every block,
	 * on the ground if it was on it. A mob with no such spot in reach is left where it is.
	 */
	private static void eject(final ServerLevel tower, final TowerPlan plan, final Mob mob) {
		Vec3 from = mob.position();
		AABB body = mob.getBoundingBox();
		BlockPos origin = plan.origin();
		boolean grounded = mob.onGround();
		int y = (int) Math.floor(from.y);
		Vec3 best = null;
		double nearest = Double.MAX_VALUE;
		int lowX = Math.max(origin.getX() + 1, (int) Math.floor(from.x) - SEARCH);
		int highX = Math.min(origin.getX() + TowerLayout.SIZE - 2, (int) Math.floor(from.x) + SEARCH);
		int lowZ = Math.max(origin.getZ() + 1, (int) Math.floor(from.z) - SEARCH);
		int highZ = Math.min(origin.getZ() + TowerLayout.SIZE - 2, (int) Math.floor(from.z) + SEARCH);
		for (int x = lowX; x <= highX; x++) {
			for (int z = lowZ; z <= highZ; z++) {
				Vec3 spot = TowerMobs.spotFor(mob, new BlockPos(x, y, z));
				double away = Math.hypot(spot.x - from.x, spot.z - from.z);
				if (away >= nearest) {
					continue;
				}
				AABB there = body.move(spot.x - from.x, 0.0, spot.z - from.z);
				if (touchesZone(plan, there) || !tower.noCollision(mob, there)) {
					continue;
				}
				BlockPos below = BlockPos.containing(spot.x, there.minY - 0.5, spot.z);
				if (grounded && !tower.getBlockState(below).isFaceSturdy(tower, below, net.minecraft.core.Direction.UP)) {
					continue;
				}
				best = new Vec3(spot.x, from.y, spot.z);
				nearest = away;
			}
		}
		if (best != null) {
			mob.snapTo(best.x, best.y, best.z, mob.getYRot(), mob.getXRot());
			mob.getNavigation().stop();
			mob.setDeltaMovement(Vec3.ZERO);
		}
	}

	private static boolean holds(final @Nullable LivingEntity target, final Set<UUID> shelter) {
		return target != null && shelter.contains(target.getUUID());
	}

	/** Makes a mob let go of every player in the set: its target, its brain's memories of one, its anger. */
	private static void calm(final Mob mob, final Set<UUID> shelter) {
		if (holds(mob.getTargetUnchecked(), shelter)) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		if (holds(mob.getLastHurtByMob(), shelter)) {
			mob.setLastHurtByMob(null);
		}
		Brain<?> brain = mob.getBrain();
		forget(brain, MemoryModuleType.ATTACK_TARGET, shelter);
		forget(brain, MemoryModuleType.HURT_BY_ENTITY, shelter);
		forget(brain, MemoryModuleType.NEAREST_ATTACKABLE, shelter);
		if (brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER)
			&& brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER).filter(player -> shelter.contains(player.getUUID())).isPresent()) {
			brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
		}
		if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT) && brain.getMemory(MemoryModuleType.ANGRY_AT).filter(shelter::contains).isPresent()) {
			brain.eraseMemory(MemoryModuleType.ANGRY_AT);
		}
		if (mob instanceof NeutralMob neutral) {
			EntityReference<LivingEntity> angry = neutral.getPersistentAngerTarget();
			if (angry != null && shelter.contains(angry.getUUID())) {
				neutral.stopBeingAngry();
			}
		}
	}

	private static void forget(final Brain<?> brain, final MemoryModuleType<? extends LivingEntity> type, final Set<UUID> shelter) {
		if (brain.hasMemoryValue(type) && brain.getMemory(type).filter(target -> shelter.contains(target.getUUID())).isPresent()) {
			brain.eraseMemory(type);
		}
	}

	/** The players a sweep of this floor would let be, for a test. */
	static List<ServerPlayer> shelteredOn(final ServerLevel tower, final int floor) {
		return TowerMobs.playersOn(tower, floor).stream().filter(TowerSafety::sheltered).toList();
	}
}
