package com.stella.tower;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lost rooms: overworld castles ({@link LostRoomPiece}) whose portal leads to a one-floor boss room of the tower.
 * <p>
 * Each castle's room is an instance in the tower dimension, west of the floors, so no floor's spawning,
 * progress or keys reach it. The room is a boss floor's plan, moved there, with every kind of trap.
 * Its level is the average level of the players online when it is first entered; each mob is that
 * level less five to fifteen more, and the boss fifteen more. Nothing respawns: a second entry finds the
 * room as it was left, and a room whose boss has fallen stays open, cleared.
 */
public final class LostRooms {
	public static final String TAG = "mod-stella-tower.lost_room";
	public static final int LEVEL_BELOW = 5;
	public static final int LEVEL_ABOVE = 15;
	public static final int BOSS_ABOVE = 15;
	public static final int ROOM_MOBS = 24;
	/** A room takes the plan of a hundredth floor: a boss floor laid with every kind of trap. */
	public static final int LOOKS_EVERY = 100;
	public static final int LOOKS_KINDS = 10;
	public static final int BOSS_TRIES = 40;

	public static final StructureType<LostRoomStructure> STRUCTURE_TYPE = Registry.register(
		BuiltInRegistries.STRUCTURE_TYPE, StellaTower.id("lost_room"), () -> LostRoomStructure.CODEC);
	public static final StructurePieceType PIECE = Registry.register(
		BuiltInRegistries.STRUCTURE_PIECE, StellaTower.id("lost_room"), (StructurePieceType) (context, tag) -> new LostRoomPiece(tag));
	public static final ResourceKey<Structure> STRUCTURE = ResourceKey.create(Registries.STRUCTURE, StellaTower.id("lost_room"));

	/** Test seam: the level last shown to each player on arrival. */
	private static final Map<UUID, Integer> SHOWN = new java.util.concurrent.ConcurrentHashMap<>();

	private LostRooms() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			TowerMobs.State state = TowerMobs.stateOf(entity);
			if (state == null || state.rank() != TowerRank.BOSS || !isLostMob(entity) || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			int room = TowerLayout.roomAt(entity.blockPosition());
			LostRoomData data = LostRoomData.get(level.getServer());
			LostRoomData.Instance instance = data.room(room);
			if (instance != null && !instance.cleared()) {
				data.put(new LostRoomData.Instance(instance.hut(), room, instance.level(), true, instance.back()));
				StellaTower.LOGGER.debug("[TOWER] lost room {} CLEARED", room);
			}
		});
	}

	public static boolean isLostMob(final Entity entity) {
		return entity.entityTags().contains(TAG);
	}

	/** The floor whose plan a room borrows. */
	public static int looksFloor(final int room) {
		return LOOKS_EVERY * (1 + Math.floorMod(-room - 1, LOOKS_KINDS));
	}

	public static TowerPlan plan(final long seed, final int room) {
		return LegacyRoomPlan.of(seed, room);
	}

	/** The tower floor a mob level matches: its rewards, gear and pool. */
	public static int equivalentFloor(final int level) {
		return Math.max(1, level - TowerMobs.LOST_ROOM_FLOOR_OFFSET + 1);
	}

	/** The floor a trap in a lost room springs as: its room's level; 1 anywhere else. */
	public static int floorAt(final ServerLevel level, final BlockPos pos) {
		int room = TowerLayout.roomAt(pos);
		LostRoomData.Instance instance = room < 0 ? LostRoomData.get(level.getServer()).room(room) : null;
		return instance == null ? 1 : equivalentFloor(instance.level());
	}

	/** The players' average experience level, rounded; spectators are left out. */
	public static int averageLevel(final Collection<ServerPlayer> players) {
		int total = 0;
		int count = 0;
		for (ServerPlayer player : players) {
			if (!player.isSpectator()) {
				total += player.experienceLevel;
				count++;
			}
		}
		return count == 0 ? 0 : Math.round((float) total / count);
	}

	/** A castle's portal: the blue portal outside the tower, standing on tower frame, which no player can place. */
	public static boolean isHutPortal(final ServerLevel level, final BlockPos portal) {
		return !level.dimension().equals(TowerTravel.TOWER) && level.getBlockState(portal).is(TowerBlocks.TOWER_PORTAL)
			&& level.getBlockState(bottom(level, portal).below()).is(TowerBlocks.TOWER_FRAME);
	}

	private static BlockPos bottom(final ServerLevel level, final BlockPos portal) {
		BlockPos at = portal;
		for (int i = 0; i < TowerPortalShape.MAX_SIZE && level.getBlockState(at.below()).is(TowerBlocks.TOWER_PORTAL); i++) {
			at = at.below();
		}
		return at;
	}

	/** The key a hut's instance is kept by: its dimension and its portal's lowest block. */
	public static String hutKey(final ServerLevel level, final BlockPos portal) {
		BlockPos at = bottom(level, portal);
		return level.dimension().identifier() + "@" + at.getX() + "," + at.getY() + "," + at.getZ();
	}

	/** Where a lost room's portal leads: from a hut to its room, from a room back to its hut. */
	public static @Nullable TeleportTransition destination(final ServerLevel from, final Entity entity, final BlockPos portal) {
		// Only players travel; mobs stay behind.
		boolean carriesPlayer = entity instanceof ServerPlayer || entity.hasPassenger(rider -> rider instanceof ServerPlayer);
		MinecraftServer server = from.getServer();
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (!carriesPlayer || tower == null) {
			return null;
		}
		LostRoomData data = LostRoomData.get(server);
		if (from == tower) {
			LostRoomData.Instance instance = data.room(TowerLayout.roomAt(portal));
			ServerLevel hut = instance == null ? null : server.getLevel(instance.back().dimension());
			if (hut == null) {
				return null;
			}
			TowerTravel.Entry back = instance.back();
			return new TeleportTransition(hut, back.position(), Vec3.ZERO, back.yaw(), 0.0F,
				TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
		}
		TowerTravel.Entry beside = TowerTravel.entryBeside(from, entity, portal);
		List<ServerPlayer> travellers = new ArrayList<>();
		if (entity instanceof ServerPlayer player) {
			travellers.add(player);
		}
		for (Entity rider : entity.getIndirectPassengers()) {
			if (rider instanceof ServerPlayer player) {
				travellers.add(player);
			}
		}
		travellers.forEach(player -> TowerTravel.record(player, beside));
		LostRoomData.Instance instance = open(tower, hutKey(from, portal), beside, server.getPlayerList().getPlayers());
		TowerPlan plan = plan(tower.getSeed(), instance.room());
		int level = instance.level();
		return new TeleportTransition(tower, plan.arrivalPos(), Vec3.ZERO, plan.arrivalYaw(), 0.0F,
			TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET).then(moved -> arrive(moved, level)));
	}

	/**
	 * The hut's instance, opened on its first entry from the players' average level, built and stocked.
	 * A later entry keeps the instance and only notes the newest way back.
	 */
	public static LostRoomData.Instance open(final ServerLevel tower, final String hut, final TowerTravel.Entry back, final Collection<ServerPlayer> players) {
		LostRoomData data = LostRoomData.get(tower.getServer());
		LostRoomData.Instance instance = data.hut(hut);
		if (instance == null) {
			instance = data.open(hut, averageLevel(players), back);
			StellaTower.LOGGER.debug("[TOWER] lost room {} OPENED for {} at level {}", instance.room(), hut, instance.level());
		} else if (!instance.back().equals(back)) {
			instance = new LostRoomData.Instance(hut, instance.room(), instance.level(), instance.cleared(), back);
			data.put(instance);
		}
		if (TowerFloors.buildRoom(tower, instance.room(), plan(tower.getSeed(), instance.room()), looksFloor(instance.room()), equivalentFloor(instance.level()))) {
			light(tower, instance.room());
			populate(tower, instance);
		}
		return instance;
	}

	/** Fills the room's frame with the blue portal, which leads back to the hut. */
	private static void light(final ServerLevel tower, final int room) {
		TowerPlan plan = plan(tower.getSeed(), room);
		BlockPos inside = plan.portalInside().get(0);
		TowerPortalShape.find(tower, inside).ifPresent(shape -> shape.createPortal(tower));
	}

	/** Raises the room's mobs and its boss, once. */
	static void populate(final ServerLevel tower, final LostRoomData.Instance instance) {
		RandomSource random = tower.getRandom();
		int room = instance.room();
		for (int i = 0; i < ROOM_MOBS; i++) {
			int level = Math.max(0, instance.level() + random.nextIntBetweenInclusive(-LEVEL_BELOW, LEVEL_ABOVE));
			List<EntityType<? extends Mob>> pool = TowerMobs.pool(equivalentFloor(level));
			Mob mob = pool.get(random.nextInt(pool.size())).create(tower, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			BlockPos middle = TowerLayout.origin(room).offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2);
			mob.snapTo(Vec3.atBottomCenterOf(middle));
			mob.finalizeSpawn(tower, tower.getCurrentDifficultyAt(middle), EntitySpawnReason.EVENT, null);
			TowerMobs.settle(mob);
			if (!TowerMobs.placeInRoom(tower, room, mob, random)) {
				mob.discard();
				continue;
			}
			raise(tower, mob, level, TowerRank.MOB, random);
		}
		boss(tower, instance, random);
	}

	private static void boss(final ServerLevel tower, final LostRoomData.Instance instance, final RandomSource random) {
		int level = instance.level() + BOSS_ABOVE;
		List<EntityType<? extends Mob>> pool = TowerElites.guardianPool(equivalentFloor(level));
		Mob mob = pool.get(random.nextInt(pool.size())).create(tower, EntitySpawnReason.EVENT);
		if (mob == null) {
			return;
		}
		BlockPos front = TowerElites.post(plan(tower.getSeed(), instance.room()));
		mob.snapTo(Vec3.atBottomCenterOf(front));
		mob.finalizeSpawn(tower, tower.getCurrentDifficultyAt(front), EntitySpawnReason.EVENT, null);
		TowerMobs.settle(mob);
		for (int attempt = 0; attempt < BOSS_TRIES; attempt++) {
			BlockPos feet = front.offset(random.nextInt(5) - 2, 0, random.nextInt(7) - 3);
			mob.snapTo(Vec3.atBottomCenterOf(feet).x, feet.getY(), Vec3.atBottomCenterOf(feet).z, -90.0F, 0.0F);
			if (TowerMobs.standsFree(tower, mob, feet)) {
				if (raise(tower, mob, level, TowerRank.BOSS, random)) {
					TowerElites.showBar(mob);
				}
				return;
			}
		}
		mob.discard();
		StellaTower.LOGGER.warn("Lost room {}: no spot for its boss", instance.room());
	}

	static boolean raise(final ServerLevel tower, final Mob mob, final int level, final TowerRank rank, final RandomSource random) {
		TowerElement element = TowerElement.values()[random.nextInt(TowerElement.values().length)];
		TowerMobs.makeTowerMob(mob, equivalentFloor(level), element, rank, level);
		mob.addTag(TAG);
		return tower.addFreshEntity(mob);
	}

	private static void arrive(final Entity entity, final int level) {
		if (entity instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.lost_room", level));
			SHOWN.put(player.getUUID(), level);
		}
	}

	/** The room level last shown to this player, or -1. */
	static int shownTo(final ServerPlayer player) {
		return SHOWN.getOrDefault(player.getUUID(), -1);
	}

	/** The tower's own mobs, of a room, standing in it now. */
	static List<Mob> mobsIn(final ServerLevel tower, final int room) {
		return tower.getEntitiesOfClass(Mob.class, TowerMobs.room(room), mob -> mob.isAlive() && isLostMob(mob));
	}

	/** Test seam: makes a hut's next entry open a new instance. */
	static void forget(final MinecraftServer server, final String hut) {
		LostRoomData.get(server).forget(hut);
	}

	/** The portal block a room's way back stands in. */
	static BlockPos returnPortal(final ServerLevel tower, final int room) {
		return plan(tower.getSeed(), room).portalInside().get(0);
	}
}
