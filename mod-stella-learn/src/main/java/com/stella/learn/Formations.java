package com.stella.learn;

import com.stella.learn.Formation.Role;
import com.stella.learn.Formation.Slot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Groups hostile monsters into formations and gives each member a slot. A scan runs every {@link #SCAN_TICKS} server
 * ticks. It joins monsters that stand within {@link #RADIUS} blocks of each other, directly or through a chain, so a
 * mob that walks into the area joins on the next scan. Three or more make a formation.
 *
 * <p>With a focus player, the slots are these. A shield bearer leads and fights where it stands. An unshielded melee
 * unit stands behind a bearer and strikes in bursts, or, when it is an odd one out, flanks the player from the side.
 * A ranged unit stays behind the nearest melee unit. A member at {@link #HURT_SHARE} health or less falls back to the
 * nearest healthier member, and a ranged member stands between it and the player. {@link FormationGoal} walks the slots.
 *
 * <p>The scan costs a grid pass over the loaded monsters. A formation holds at most {@link #MAX_MEMBERS} slots.
 */
public final class Formations {
	public static final double RADIUS = 32.0;
	public static final int MIN_SIZE = 3;
	public static final int MAX_MEMBERS = 24;
	public static final int SCAN_TICKS = 10;
	/** A member at this share of its health or less falls back. */
	public static final double HURT_SHARE = 0.20;
	/** A member that fell back stays out until it has healed to this share. */
	public static final double HURT_LEAVE_SHARE = 0.30;
	/** Blocks from the player to a flanker's point, to the side of the line between the player and the formation. */
	public static final double FLANK_DISTANCE = 6.0;
	/** Blocks behind its shield bearer that a striker waits. */
	public static final double BEHIND_GAP = 2.5;
	/** Blocks from the player at which a shield bearer counts as engaged. */
	public static final double ENGAGE_DISTANCE = 5.5;
	/** A ranged unit nearer than the nearest melee unit plus this margin walks back. */
	public static final double LINE_MARGIN = 2.0;
	/** Blocks behind the nearest melee unit that a ranged unit walks to. */
	public static final double LINE_GAP = 5.0;
	/** Blocks from the hurt mob, on the player's side, that a guard stands. */
	public static final double GUARD_GAP = 2.5;
	static final String NO_BEHAVIOR_TAG = "mod-stella-mobs.no_behavior";
	/** The tags of mobs that fight for the village or the player, whose fights this mod leaves alone. */
	private static final Set<String> FRIENDLY_TAGS = Set.of(
		NO_BEHAVIOR_TAG, "mod-stella-mobs.friendly_patrol", "mc-stella-villages.patrol", "mc-stella-villages.village_witch"
	);
	private static final Set<EntityType<?>> EXCLUDED = Set.of(EntityTypes.WITHER, EntityTypes.WARDEN);
	private static final Set<Monster> REGISTRY = new LinkedHashSet<>();
	private static final Map<UUID, Formation> MEMBERSHIP = new HashMap<>();
	private static int clock;

	/** What a member is, for the slots. */
	public enum Kind {
		SHIELD,
		MELEE,
		RANGED
	}

	private Formations() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Monster monster && monster.getNavigation() instanceof GroundPathNavigation) {
				REGISTRY.add(monster);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Monster monster) {
				REGISTRY.remove(monster);
				MEMBERSHIP.remove(monster.getUUID());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++clock % SCAN_TICKS == 0) {
				scanAll();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REGISTRY.clear();
			MEMBERSHIP.clear();
		});
		MobShare.publish();
	}

	/** A monster that fights for the hostile side: not a servant, a village friend, or a boss. */
	public static boolean hostile(final Mob mob) {
		return mob instanceof Monster && !EXCLUDED.contains(mob.getType()) && mob.entityTags().stream().noneMatch(FRIENDLY_TAGS::contains);
	}

	public static @Nullable Formation of(final Mob mob) {
		return MEMBERSHIP.get(mob.getUUID());
	}

	public static @Nullable Slot slotOf(final Mob mob) {
		Formation formation = MEMBERSHIP.get(mob.getUUID());
		return formation == null ? null : formation.slotOf(mob);
	}

	public static Kind kindOf(final Mob mob) {
		if (!MobShare.MELEE.equals(MobShare.role(mob))) {
			return Kind.RANGED;
		}

		return mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) ? Kind.SHIELD : Kind.MELEE;
	}

	private static void scanAll() {
		if (REGISTRY.isEmpty()) {
			return;
		}

		Map<ServerLevel, List<Monster>> byLevel = new LinkedHashMap<>();
		for (Monster monster : List.copyOf(REGISTRY)) {
			if (monster.level() instanceof ServerLevel level) {
				byLevel.computeIfAbsent(level, key -> new ArrayList<>()).add(monster);
			}
		}

		byLevel.forEach(Formations::scan);
	}

	/** Scans the registered monsters of one level. A test calls it to avoid waiting for the beat. */
	public static void scan(final ServerLevel level) {
		List<Monster> mobs = new ArrayList<>();
		for (Monster monster : List.copyOf(REGISTRY)) {
			if (monster.level() == level) {
				mobs.add(monster);
			}
		}

		scan(level, mobs);
	}

	private static void scan(final ServerLevel level, final List<Monster> mobs) {
		List<Monster> live = new ArrayList<>();
		for (Monster mob : mobs) {
			if (mob.isAlive() && hostile(mob)) {
				live.add(mob);
				FriendlyFire.sweep(mob);
			}
		}

		live.sort(Comparator.comparingInt(Monster::getId));
		Map<UUID, Formation> before = new HashMap<>();
		for (Monster mob : mobs) {
			Formation previous = MEMBERSHIP.remove(mob.getUUID());
			if (previous != null) {
				before.put(mob.getUUID(), previous);
			}
		}

		for (List<Monster> cluster : cluster(live)) {
			SharedSight.share(level, cluster);
			if (cluster.size() >= MIN_SIZE) {
				build(cluster, before);
			}
		}

		for (Monster mob : live) {
			logMembership(mob, before.get(mob.getUUID()), MEMBERSHIP.get(mob.getUUID()));
		}
	}

	/** Groups the monsters that stand within the radius of each other, directly or through a chain. */
	private static List<List<Monster>> cluster(final List<Monster> mobs) {
		int[] parent = new int[mobs.size()];
		Map<Long, List<Integer>> cells = new HashMap<>();
		for (int i = 0; i < parent.length; i++) {
			parent[i] = i;
			cells.computeIfAbsent(cell(mobs.get(i).getX(), mobs.get(i).getZ()), key -> new ArrayList<>()).add(i);
		}

		for (int i = 0; i < parent.length; i++) {
			Monster mob = mobs.get(i);
			int cellX = (int) Math.floor(mob.getX() / RADIUS);
			int cellZ = (int) Math.floor(mob.getZ() / RADIUS);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int j : cells.getOrDefault(pack(cellX + dx, cellZ + dz), List.of())) {
						if (j > i && mob.distanceToSqr(mobs.get(j)) <= RADIUS * RADIUS) {
							parent[root(parent, i)] = root(parent, j);
						}
					}
				}
			}
		}

		Map<Integer, List<Monster>> groups = new LinkedHashMap<>();
		for (int i = 0; i < parent.length; i++) {
			groups.computeIfAbsent(root(parent, i), key -> new ArrayList<>()).add(mobs.get(i));
		}

		return groups.values().stream().filter(group -> group.size() >= 2).toList();
	}

	private static int root(final int[] parent, final int index) {
		int current = index;
		while (parent[current] != current) {
			parent[current] = parent[parent[current]];
			current = parent[current];
		}

		return current;
	}

	private static long cell(final double x, final double z) {
		return pack((int) Math.floor(x / RADIUS), (int) Math.floor(z / RADIUS));
	}

	private static long pack(final int x, final int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static void build(final List<Monster> cluster, final Map<UUID, Formation> before) {
		List<Monster> members = List.copyOf(cluster.subList(0, Math.min(cluster.size(), MAX_MEMBERS)));
		ServerPlayer focus = focusOf(members);
		Formation formation = new Formation(members, focus);
		if (focus != null) {
			assign(formation, focus, before);
		}

		for (Monster mob : cluster) {
			MEMBERSHIP.put(mob.getUUID(), formation);
		}
	}

	/** The player most members hunt; the first of a tie in member order. */
	private static @Nullable ServerPlayer focusOf(final List<Monster> members) {
		Map<ServerPlayer, Integer> votes = new LinkedHashMap<>();
		for (Monster mob : members) {
			if (mob.getTarget() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
				votes.merge(player, 1, Integer::sum);
			}
		}

		return votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
	}

	private static void assign(final Formation formation, final ServerPlayer focus, final Map<UUID, Formation> before) {
		Vec3 target = focus.position();
		List<Monster> hurt = new ArrayList<>();
		List<Monster> healthy = new ArrayList<>();
		for (Monster mob : formation.members()) {
			double share = mob.getHealth() / mob.getMaxHealth();
			Formation previous = before.get(mob.getUUID());
			Slot last = previous == null ? null : previous.slotOf(mob);
			boolean falling = last != null && last.role() == Role.FALL_BACK;
			(share <= HURT_SHARE || falling && share <= HURT_LEAVE_SHARE ? hurt : healthy).add(mob);
		}

		// Nobody healthier to fall back to: everyone fights on.
		if (healthy.isEmpty()) {
			healthy.addAll(hurt);
			hurt.clear();
		}

		List<Monster> guards = new ArrayList<>();
		for (Monster mob : hurt) {
			Monster ally = nearest(healthy, mob, null);
			formation.put(mob, new Slot(Role.FALL_BACK, ally.position(), false));
			Monster guard = nearest(healthy, mob, candidate -> kindOf(candidate) == Kind.RANGED && !guards.contains(candidate));
			if (guard != null) {
				guards.add(guard);
				Vec3 toward = flat(target.subtract(mob.position()), new Vec3(0.0, 0.0, 1.0));
				formation.put(guard, new Slot(Role.GUARD, mob.position().add(toward.scale(GUARD_GAP)), false));
			}
		}

		List<Monster> shields = new ArrayList<>();
		List<Monster> melees = new ArrayList<>();
		List<Monster> ranged = new ArrayList<>();
		Vec3 centre = Vec3.ZERO;
		for (Monster mob : healthy) {
			centre = centre.add(mob.position());
			if (guards.contains(mob)) {
				continue;
			}

			switch (kindOf(mob)) {
				case SHIELD -> shields.add(mob);
				case MELEE -> melees.add(mob);
				case RANGED -> ranged.add(mob);
			}
		}

		Vec3 axis = flat(centre.scale(1.0 / healthy.size()).subtract(target), new Vec3(0.0, 0.0, 1.0));
		for (Monster mob : shields) {
			formation.put(mob, new Slot(Role.FRONT, null, false));
		}

		for (int i = 0; i < melees.size(); i++) {
			formation.put(melees.get(i), meleeSlot(i, shields, target, axis));
		}

		assignRanged(formation, ranged, shields, melees, target, axis);
	}

	/** Odd units flank. Even units strike from behind a bearer, or fight where they stand when there is none. */
	private static Slot meleeSlot(final int index, final List<Monster> shields, final Vec3 target, final Vec3 axis) {
		if (index % 2 == 1) {
			double side = (index / 2) % 2 == 0 ? 1.0 : -1.0;
			Vec3 perpendicular = new Vec3(-axis.z, 0.0, axis.x).scale(side * FLANK_DISTANCE);
			return new Slot(Role.FLANK, target.add(perpendicular), false);
		}

		if (shields.isEmpty()) {
			return new Slot(Role.FRONT, null, false);
		}

		Monster bearer = shields.get((index / 2) % shields.size());
		Vec3 away = flat(bearer.position().subtract(target), axis);
		return new Slot(Role.STRIKER, bearer.position().add(away.scale(BEHIND_GAP)), flatDistance(bearer.position(), target) <= ENGAGE_DISTANCE);
	}

	private static void assignRanged(
		final Formation formation, final List<Monster> ranged, final List<Monster> shields, final List<Monster> melees,
		final Vec3 target, final Vec3 axis
	) {
		double line = Double.MAX_VALUE;
		for (List<Monster> units : List.of(shields, melees)) {
			for (Monster unit : units) {
				line = Math.min(line, flatDistance(unit.position(), target));
			}
		}

		for (Monster mob : ranged) {
			double distance = flatDistance(mob.position(), target);
			double want = Math.min(line + LINE_GAP, Math.max(MobShare.band(mob)[1] - 1.0, MobShare.band(mob)[0]));
			if (line == Double.MAX_VALUE || distance >= line + LINE_MARGIN || want <= distance + 0.5) {
				formation.put(mob, new Slot(Role.BACK, null, false));
				continue;
			}

			Vec3 out = flat(mob.position().subtract(target), axis);
			formation.put(mob, new Slot(Role.BACK, new Vec3(target.x + out.x * want, target.y, target.z + out.z * want), false));
		}
	}

	private static @Nullable Monster nearest(final List<Monster> from, final Monster to, final @Nullable Predicate<Monster> filter) {
		Monster best = null;
		for (Monster candidate : from) {
			if (candidate != to && (filter == null || filter.test(candidate))
				&& (best == null || candidate.distanceToSqr(to) < best.distanceToSqr(to))) {
				best = candidate;
			}
		}

		return best;
	}

	private static Vec3 flat(final Vec3 vector, final Vec3 fallback) {
		Vec3 flat = new Vec3(vector.x, 0.0, vector.z);
		return flat.lengthSqr() < 1.0E-6 ? fallback : flat.normalize();
	}

	static double flatDistance(final Vec3 a, final Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static void logMembership(final Monster mob, final @Nullable Formation before, final @Nullable Formation now) {
		if (now != null && before == null) {
			LearnLog.info("learnFormation", "JOIN", LearnLog.who(mob) + " size=" + now.size());
		} else if (now == null && before != null) {
			LearnLog.info("learnFormation", "LEAVE", LearnLog.who(mob));
		}

		Slot was = before == null ? null : before.slotOf(mob);
		Slot is = now == null ? null : now.slotOf(mob);
		if (is != null && (was == null || was.role() != is.role())) {
			LearnLog.info("learnFormation", "SLOT", LearnLog.who(mob) + " role=" + is.role() + " kind=" + kindOf(mob)
				+ (is.pos() == null ? "" : " pos=" + Rally.text(is.pos())));
		}
	}
}
