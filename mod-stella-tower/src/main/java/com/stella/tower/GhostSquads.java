package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tower's ghost illagers and ghost witches. A squad of three to five rises on a floor with a player on it, of vindicators,
 * pillagers and evokers and now and then a witch, and drifts about together: the squad's leader strolls, and the rest keep
 * within {@value #CLOSE} blocks of it. A ghost evoker summons ghost vexes. Every one of them is translucent, wreathed in soul
 * fire, slower than its kind, and on the players' side: it hunts the tower's mobs, and the tower's mobs hunt it back.
 */
public final class GhostSquads {
	public static final int MIN_SIZE = 3;
	public static final int MAX_SIZE = 5;
	/** The chance a spawning round raises a squad on a floor that holds none. */
	public static final double RISE_CHANCE = 0.02;
	/** The share of a squad's members that are witches. */
	public static final double WITCH_SHARE = 0.15;
	/** A member of a squad keeps within this many blocks of its leader. */
	public static final double CLOSE = 4.0;
	/** The tag a member of a squad carries, followed by the squad's id. */
	public static final String TAG = "mod-stella-tower.ghost_squad:";
	private static final double SEARCH = 48.0;
	private static final int PLACE_TRIES = 24;

	public static final EntityType<GhostVindicator> VINDICATOR = register("ghost_vindicator", EntityType.Builder.<GhostVindicator>of(GhostVindicator::new, MobCategory.MISC)
		.sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8));
	public static final EntityType<GhostPillager> PILLAGER = register("ghost_pillager", EntityType.Builder.<GhostPillager>of(GhostPillager::new, MobCategory.MISC)
		.sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8));
	public static final EntityType<GhostEvoker> EVOKER = register("ghost_evoker", EntityType.Builder.<GhostEvoker>of(GhostEvoker::new, MobCategory.MISC)
		.sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8));
	public static final EntityType<GhostWitch> WITCH = register("ghost_witch", EntityType.Builder.<GhostWitch>of(GhostWitch::new, MobCategory.MISC)
		.sized(0.6F, 1.95F).eyeHeight(1.62F).passengerAttachments(2.2625F).clientTrackingRange(8));
	public static final EntityType<GhostVex> VEX = register("ghost_vex", EntityType.Builder.<GhostVex>of(GhostVex::new, MobCategory.MISC)
		.fireImmune().sized(0.4F, 0.8F).eyeHeight(0.51875F).passengerAttachments(0.7375F).ridingOffset(0.04F).clientTrackingRange(8));

	/** The test seam: pins, by floor, the chance a spawning round raises a squad. */
	private static final Map<Integer, Double> PINNED_RISE = new java.util.concurrent.ConcurrentHashMap<>();
	private static volatile @Nullable Double defaultRise;

	private GhostSquads() {
	}

	private static <T extends Mob> EntityType<T> register(final String path, final EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, StellaTower.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(VINDICATOR, GhostVindicator.createAttributes());
		FabricDefaultAttributeRegistry.register(PILLAGER, GhostPillager.createAttributes());
		FabricDefaultAttributeRegistry.register(EVOKER, GhostEvoker.createAttributes());
		FabricDefaultAttributeRegistry.register(WITCH, GhostWitch.createAttributes());
		FabricDefaultAttributeRegistry.register(VEX, GhostVex.createAttributes());
		// Goals are never saved: a tower mob takes up its hunt for the ghosts each time it enters a level.
		ServerEntityEvents.ENTITY_LOAD.register(GhostSquads::drawAggro);
	}

	/** The test seam: the chance on every floor not pinned; null restores {@link #RISE_CHANCE}. */
	public static void pinDefaultRiseChance(final @Nullable Double chance) {
		defaultRise = chance;
	}

	public static void pinRiseChance(final int floor, final @Nullable Double chance) {
		if (chance == null) {
			PINNED_RISE.remove(floor);
		} else {
			PINNED_RISE.put(floor, chance);
		}
	}

	/** Every tower mob of the tower hunts the ghosts as well as the players, so a ghost draws the mobs' aggro. */
	private static void drawAggro(final Entity entity, final ServerLevel level) {
		if (!(entity instanceof Mob mob) || mob instanceof Ghostly || !level.dimension().equals(TowerTravel.TOWER) || TowerMobs.stateOf(mob) == null) {
			return;
		}
		((com.stella.tower.mixin.MobSelectorAccessor) mob).stellaTower$targetSelector().addGoal(2,
			new NearestAttackableTargetGoal<>(mob, Mob.class, 10, true, false, (target, ignored) -> target instanceof Ghostly));
	}

	// ---------------------------------------------------------------- the squad

	/** The id of the squad a ghost belongs to, or null for a ghost on its own. */
	public static @Nullable String squadOf(final Entity ghost) {
		for (String tag : ghost.entityTags()) {
			if (tag.startsWith(TAG)) {
				return tag.substring(TAG.length());
			}
		}
		return null;
	}

	/** The living members of a ghost's squad, itself among them; a ghost with no squad is alone. */
	public static List<Mob> membersOf(final Mob ghost) {
		String squad = squadOf(ghost);
		if (squad == null) {
			return List.of(ghost);
		}
		return ghost.level().getEntitiesOfClass(Mob.class, ghost.getBoundingBox().inflate(SEARCH), member -> member.isAlive() && squad.equals(squadOf(member)));
	}

	/** The living member of a squad with the lowest id, so every member reads the same leader and the next in line takes over. */
	public static Mob leaderOf(final Mob ghost) {
		Mob leader = ghost;
		for (Mob other : membersOf(ghost)) {
			if (other.getUUID().compareTo(leader.getUUID()) < 0) {
				leader = other;
			}
		}
		return leader;
	}

	/** The squad's own goals: keep with the leader, and, for the leader, stroll. */
	static void addSquadGoals(final PathfinderMob ghost, final GoalSelector goals) {
		goals.addGoal(6, new KeepWithSquadGoal(ghost));
		goals.addGoal(7, new LeaderStrollGoal(ghost, 0.6));
	}

	/** The ghosts of squads on a floor, vexes apart: those are the squad's own, and are not counted. */
	public static List<Mob> on(final ServerLevel tower, final int floor) {
		return tower.getEntitiesOfClass(Mob.class, TowerMobs.room(floor), mob -> mob.isAlive() && mob instanceof Ghostly && !(mob instanceof TowerGhost) && !(mob instanceof GhostVex));
	}

	// ---------------------------------------------------------------- raising

	/** The kind of the next member of a squad: a witch now and then, else a vindicator, a pillager or an evoker. */
	static EntityType<? extends Mob> draw(final RandomSource random) {
		if (random.nextDouble() < WITCH_SHARE) {
			return WITCH;
		}
		int roll = random.nextInt(10);
		return roll < 4 ? VINDICATOR : roll < 8 ? PILLAGER : EVOKER;
	}

	/** A ghost of a kind, as strong as its floor; not yet in the world and not yet placed. */
	public static @Nullable Mob make(final ServerLevel tower, final int floor, final EntityType<? extends Mob> type, final BlockPos near) {
		Mob ghost = type.create(tower, EntitySpawnReason.EVENT);
		if (ghost == null) {
			return null;
		}
		ghost.snapTo(Vec3.atBottomCenterOf(near));
		ghost.finalizeSpawn(tower, tower.getCurrentDifficultyAt(near), EntitySpawnReason.EVENT, null);
		TowerMobs.settle(ghost);
		TowerMobs.makeTowerMob(ghost, floor, TowerElement.values()[tower.getRandom().nextInt(TowerElement.values().length)]);
		return ghost;
	}

	/** Raises one ghost of a kind at a spot, on its own; null if it could not be added. */
	public static <T extends Mob> @Nullable T summon(final ServerLevel tower, final int floor, final EntityType<T> type, final Vec3 at) {
		Mob ghost = make(tower, floor, type, BlockPos.containing(at));
		if (ghost == null) {
			return null;
		}
		ghost.snapTo(at);
		if (!tower.addFreshEntity(ghost)) {
			ghost.discard();
			return null;
		}
		@SuppressWarnings("unchecked")
		T added = (T) ghost;
		return added;
	}

	/** Raises a squad of {@code size} round a spot; the ghosts it could add, none if the first could not be. */
	public static List<Mob> raiseAt(final ServerLevel tower, final int floor, final Vec3 at, final int size) {
		Mob first = make(tower, floor, draw(tower.getRandom()), BlockPos.containing(at));
		if (first == null) {
			return List.of();
		}
		first.snapTo(at);
		return raise(tower, floor, first, size);
	}

	/** Adds the placed ghost and its fellows beside it, all of one new squad. */
	private static List<Mob> raise(final ServerLevel tower, final int floor, final Mob first, final int size) {
		String tag = TAG + UUID.randomUUID();
		List<Mob> squad = new ArrayList<>();
		first.addTag(tag);
		if (!tower.addFreshEntity(first)) {
			first.discard();
			return squad;
		}
		squad.add(first);
		RandomSource random = tower.getRandom();
		for (int i = 1; i < size; i++) {
			Mob ghost = make(tower, floor, draw(random), first.blockPosition());
			if (ghost == null) {
				continue;
			}
			ghost.addTag(tag);
			ghost.snapTo(first.position());
			// A free spot on the ground within a few blocks of the first; where none is free, on it.
			for (int attempt = 0; attempt < PLACE_TRIES; attempt++) {
				double x = first.getX() + (random.nextDouble() - 0.5) * 2.0 * CLOSE;
				double z = first.getZ() + (random.nextDouble() - 0.5) * 2.0 * CLOSE;
				ghost.snapTo(x, first.getY(), z, random.nextFloat() * 360.0F, 0.0F);
				if (TowerMobs.standsFree(tower, ghost, ghost.blockPosition())) {
					break;
				}
				ghost.snapTo(first.position());
			}
			if (tower.addFreshEntity(ghost)) {
				squad.add(ghost);
			} else {
				ghost.discard();
			}
		}
		return squad;
	}

	/** Keeps an occupied floor's ghosts: a spawning round may raise a squad while the floor holds none. */
	public static void haunt(final ServerLevel tower, final int floor) {
		Double standing = defaultRise;
		double chance = PINNED_RISE.getOrDefault(floor, standing != null ? standing : RISE_CHANCE);
		if (!TowerElites.roomLoaded(tower, floor) || on(tower, floor).size() >= MIN_SIZE || tower.getRandom().nextDouble() >= chance) {
			return;
		}
		RandomSource random = tower.getRandom();
		Mob leader = make(tower, floor, draw(random), TowerLayout.origin(floor).offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2));
		if (leader == null) {
			return;
		}
		if (!TowerMobs.placeInRoom(tower, floor, leader, random)) {
			leader.discard();
			return;
		}
		List<Mob> squad = raise(tower, floor, leader, MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1));
		StellaTower.LOGGER.debug("[TOWER] floor={} a squad of {} ghosts rises at {}", floor, squad.size(), leader.blockPosition());
	}

	/** Summons a ghost vex of a ghost evoker's, of its floor and element; null if it could not be added. */
	static @Nullable GhostVex summonVex(final ServerLevel level, final GhostEvoker evoker, final BlockPos at) {
		GhostVex vex = VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (vex == null) {
			return null;
		}
		vex.snapTo(at, 0.0F, 0.0F);
		vex.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
		vex.setOwner(evoker);
		vex.setBoundOrigin(at);
		vex.setLimitedLife(20 * (30 + evoker.getRandom().nextInt(90)));
		TowerMobs.State state = TowerMobs.stateOf(evoker);
		if (state != null) {
			TowerMobs.makeTowerMob(vex, state.floor(), state.element());
		}
		level.addFreshEntityWithPassengers(vex);
		return vex;
	}

	// ---------------------------------------------------------------- goals

	/** Keeps a member of a squad within {@value #CLOSE} blocks of its leader. */
	static final class KeepWithSquadGoal extends Goal {
		private final PathfinderMob ghost;
		private @Nullable Mob leader;
		private int recalc;

		KeepWithSquadGoal(final PathfinderMob ghost) {
			this.ghost = ghost;
			this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			Mob lead = leaderOf(this.ghost);
			if (lead == this.ghost || this.ghost.distanceToSqr(lead) <= CLOSE * CLOSE) {
				return false;
			}
			this.leader = lead;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.leader != null && this.leader.isAlive() && this.ghost.distanceToSqr(this.leader) > CLOSE * CLOSE / 4.0;
		}

		@Override
		public void start() {
			this.recalc = 0;
		}

		@Override
		public void tick() {
			if (this.leader != null && --this.recalc <= 0) {
				this.recalc = 10;
				this.ghost.getNavigation().moveTo(this.leader, 1.0);
			}
		}

		@Override
		public void stop() {
			this.leader = null;
			this.ghost.getNavigation().stop();
		}
	}

	/** Only the squad's leader, or a ghost on its own, strolls; the rest of the squad goes where the leader goes. */
	static final class LeaderStrollGoal extends RandomStrollGoal {
		LeaderStrollGoal(final PathfinderMob ghost, final double speed) {
			super(ghost, speed);
		}

		@Override
		public boolean canUse() {
			return leaderOf(this.mob) == this.mob && super.canUse();
		}
	}
}
