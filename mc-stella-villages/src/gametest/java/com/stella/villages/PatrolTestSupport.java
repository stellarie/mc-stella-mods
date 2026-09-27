package com.stella.villages;

import com.stella.villages.patrol.MobPatrolAccess;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class PatrolTestSupport {
	private static final String SERVICE = "com.stella.villages.patrol.PatrolService";
	private static final String STATE = "com.stella.villages.patrol.PatrolState";
	private static final String ACCESS = "com.stella.villages.patrol.MobPatrolAccess";
	private static final Map<GameTestHelper, UUID> VILLAGE_IDS = new WeakHashMap<>();
	private static final Map<GameTestHelper, BlockPos> VILLAGE_ANCHORS = new WeakHashMap<>();
	// Distant anchors handed out this run, so two tests never build on one chunk.
	private static final Set<String> CLAIMED_ANCHORS = ConcurrentHashMap.newKeySet();

	private PatrolTestSupport() {
	}

	/**
	 * Every test in this suite carries this padding. The test grid sizes each row by the tests in it, so a
	 * row of unpadded tests would let two village tests sit inside vanilla's village distance of six
	 * sections of each other, and their records would merge.
	 */
	static final int VILLAGE_PADDING = 64;

	static boolean loadVillage(final GameTestHelper helper, final String test, final boolean addSecondBell) {
		return prepareVillage(helper, test, addSecondBell) && scanChunk(helper, test);
	}

	static boolean prepareVillage(final GameTestHelper helper, final String test, final boolean addSecondBell) {
		BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
		VILLAGE_ANCHORS.put(helper, anchor);
		forgetEarlierBatchVillages(helper, test, anchor);
		int chunkMinX = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(anchor.getX()));
		int chunkMinZ = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(anchor.getZ()));
		for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
			for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
				BlockPos floor = new BlockPos(chunkMinX + x, anchor.getY() - 1, chunkMinZ + z);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
		}

		helper.setBlock(2, 1, 2, Blocks.BELL);

		if (addSecondBell) {
			helper.setBlock(8, 1, 8, Blocks.BELL);
		}

		helper.spawn(EntityTypes.VILLAGER, 4, 1, 4).setPersistenceRequired();
		BlockPos poi = VILLAGE_ANCHORS.get(helper);
		boolean claimed = helper.getLevel().getPoiManager().take(
			holder -> holder.is(PoiTypeTags.VILLAGE),
			(holder, position) -> position.equals(poi),
			poi,
			1
		).filter(poi::equals).isPresent();
		detail(test, "villagePoiClaimed=" + claimed);
		if (!claimed) {
			helper.fail("test village POI could not acquire an occupancy ticket");
			return false;
		}
		return true;
	}

	/** The test that registered each village, so a cleanup never touches a running test's village. */
	private static final Map<UUID, GameTestHelper> OWNERS = new ConcurrentHashMap<>();

	/**
	 * Each test batch is laid out on the ground the batch before it used, but village records live in the
	 * level's saved data and outlast the batch. A record within vanilla's village distance of this anchor
	 * would take in this test's village, members and all. Such a record whose test has finished is left
	 * over, so it and its patrol are removed. A running test's record, such as a neighbour's distant
	 * village, stays.
	 */
	@SuppressWarnings("unchecked")
	static void forgetEarlierBatchVillages(final GameTestHelper helper, final String test, final BlockPos anchor) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Field field = dataClass.getDeclaredField("villages");
			field.setAccessible(true);
			Map<UUID, ?> villages = (Map<UUID, ?>) field.get(data);
			Method distance = dataClass.getDeclaredMethod("sectionDistance", BlockPos.class, BlockPos.class);
			distance.setAccessible(true);
			Method stateVillageId = Class.forName(STATE).getMethod("villageId", Mob.class);
			for (Object village : List.copyOf(villages.values())) {
				UUID id = (UUID) village.getClass().getMethod("id").invoke(village);
				BlockPos other = (BlockPos) village.getClass().getMethod("anchor").invoke(village);
				GameTestHelper owner = OWNERS.get(id);
				if ((long) distance.invoke(null, anchor, other) > PoiManager.MAX_VILLAGE_DISTANCE || (owner != null && !finished(owner))) {
					continue;
				}

				villages.remove(id);
				OWNERS.remove(id);
				int discarded = 0;
				for (Mob mob : mobs(helper, other)) {
					if (id.equals(stateVillageId.invoke(null, mob))) {
						mob.discard();
						discarded++;
					}
				}
				detail(test, "forgotEarlierBatchVillage=" + id + " discardedMembers=" + discarded);
			}
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalStateException("could not clear an earlier batch's village", error);
		}
	}

	private static boolean finished(final GameTestHelper owner) throws ReflectiveOperationException {
		Field info = GameTestHelper.class.getDeclaredField("testInfo");
		info.setAccessible(true);
		return ((net.minecraft.gametest.framework.GameTestInfo) info.get(owner)).isDone();
	}

	static boolean prepareOccupiedVillagePoi(
		final GameTestHelper helper,
		final String test,
		final BlockPos poi
	) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos floor = poi.offset(dx, -1, dz);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				for (int y = 0; y < 3; y++) {
					BlockPos clear = floor.above(y + 1);
					if (!clear.equals(poi)) {
						helper.getLevel().setBlockAndUpdate(clear, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		helper.getLevel().setBlockAndUpdate(poi, Blocks.BELL.defaultBlockState());

		var created = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		if (!(created instanceof Mob villager)) {
			result(test, "remoteVillager=MISSING poi=" + poi);
			helper.fail("could not create the remote village villager");
			return false;
		}
		villager.setNoAi(true);
		villager.setPersistenceRequired();
		villager.snapTo(Vec3.atBottomCenterOf(poi.offset(1, 0, 1)), 0.0F, 0.0F);
		boolean villagerInserted = helper.getLevel().addFreshEntity(villager);
		boolean claimed = helper.getLevel().getPoiManager().take(
			holder -> holder.is(PoiTypeTags.VILLAGE),
			(holder, position) -> position.equals(poi),
			poi,
			1
		).filter(poi::equals).isPresent();
		result(test, "remotePoi=" + poi + " villager=" + villager.getUUID()
			+ " villagerInserted=" + villagerInserted + " claimed=" + claimed);
		if (!villagerInserted || !claimed) {
			helper.fail("remote village POI could not create an occupied record");
			return false;
		}
		return true;
	}

	static boolean claimVillagePoi(
		final GameTestHelper helper,
		final String test,
		final BlockPos poi
	) {
		LevelChunk chunk = helper.getLevel().getChunkAt(poi);
		boolean occupied = helper.getLevel().getPoiManager()
			.getInChunk(holder -> holder.is(PoiTypeTags.VILLAGE), chunk.getPos(), PoiManager.Occupancy.IS_OCCUPIED)
			.anyMatch(record -> record.getPos().equals(poi));
		if (occupied) {
			return true;
		}

		boolean claimed = helper.getLevel().getPoiManager().take(
			holder -> holder.is(PoiTypeTags.VILLAGE),
			(holder, position) -> position.equals(poi),
			poi,
			1
		).filter(poi::equals).isPresent();
		detail(test, "poi=" + poi + " occupiedAfterReload=" + claimed);
		if (!claimed) {
			helper.fail("village POI could not reacquire an occupancy ticket");
			return false;
		}
		return true;
	}

	static boolean loadVillageAt(
		final GameTestHelper helper,
		final String test,
		final BlockPos anchor
	) {
		VILLAGE_ANCHORS.put(helper, anchor);
		forgetEarlierBatchVillages(helper, test, anchor);
		helper.getLevel().getChunkAt(anchor);
		int chunkMinX = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(anchor.getX()));
		int chunkMinZ = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(anchor.getZ()));
		for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
			for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
				BlockPos floor = new BlockPos(chunkMinX + x, anchor.getY() - 1, chunkMinZ + z);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
		}
		return prepareOccupiedVillagePoi(helper, test, anchor) && scanChunk(helper, test);
	}

	static boolean scanChunk(final GameTestHelper helper, final String test) {
		try {
			Class<?> service = Class.forName(SERVICE);
			Method onChunkLoad = service.getMethod("onChunkLoad", ServerLevel.class, LevelChunk.class);
			BlockPos poi = VILLAGE_ANCHORS.get(helper);
			LevelChunk chunk = helper.getLevel().getChunkAt(poi);
			Object value = onChunkLoad.invoke(null, helper.getLevel(), chunk);
			if (!(value instanceof List<?> villageIds) || villageIds.isEmpty()) {
				result(test, "villageId=MISSING");
				helper.fail("chunk scan found no village POI");
				return false;
			}

			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Object nearest = dataClass.getMethod("findNearest", ResourceKey.class, BlockPos.class)
				.invoke(data, helper.getLevel().dimension(), poi);
			if (!(nearest instanceof Optional<?> option) || option.isEmpty()) {
				result(test, "villageId=MISSING");
				helper.fail("chunk scan did not register the test village anchor");
				return false;
			}

			Object village = option.get();
			UUID villageId = (UUID) village.getClass().getMethod("id").invoke(village);
			VILLAGE_IDS.put(helper, villageId);
			GameTestHelper previousOwner = OWNERS.put(villageId, helper);
			boolean shared = previousOwner != null && previousOwner != helper && !finished(previousOwner);
			detail(test, "village=" + villageId + " anchor=" + village.getClass().getMethod("anchor").invoke(village) + " poi=" + poi
				+ " sharedWithRunningTest=" + shared);
			if (shared) {
				helper.fail("the test village merged into a running test's village, so the test grid put them too close");
				return false;
			}
			return true;
		} catch (ReflectiveOperationException | LinkageError error) {
			String detail = error instanceof InvocationTargetException invocation && invocation.getCause() != null
				? invocation.getCause().toString()
				: error.toString();
			result(test, "patrolHook=MISSING detail=" + detail);
			helper.fail("missing patrol chunk-load behavior: " + detail);
			return false;
		}
	}

	static UUID villageId(final GameTestHelper helper) {
		return VILLAGE_IDS.get(helper);
	}

	static BlockPos anchor(final GameTestHelper helper) {
		return VILLAGE_ANCHORS.get(helper);
	}

	static boolean rosterStarted(final GameTestHelper helper, final String test) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Object value = dataClass.getMethod("village", UUID.class).invoke(data, VILLAGE_IDS.get(helper));
			if (value instanceof Optional<?> village && village.isPresent()) {
				return (Boolean) village.get().getClass().getMethod("rosterStarted").invoke(village.get());
			}
			helper.fail("village record is missing for roster state");
			return false;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "rosterStarted=MISSING detail=" + error);
			helper.fail("missing patrol roster state: " + error);
			return false;
		}
	}

	static boolean setRosterStarted(final GameTestHelper helper, final String test, final boolean started) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Object value = dataClass.getMethod("village", UUID.class).invoke(data, VILLAGE_IDS.get(helper));
			if (!(value instanceof Optional<?> village) || village.isEmpty()) {
				helper.fail("village record is missing for roster state mutation");
				return false;
			}
			Field field = village.get().getClass().getDeclaredField("rosterStarted");
			field.setAccessible(true);
			field.setBoolean(village.get(), started);
			return true;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "rosterStarted=MISSING detail=" + error);
			helper.fail("missing patrol roster state: " + error);
			return false;
		}
	}

	static @Nullable UUID nearestVillageId(
		final GameTestHelper helper,
		final String test,
		final BlockPos position
	) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Object value = dataClass.getMethod("findNearest", ResourceKey.class, BlockPos.class)
				.invoke(data, helper.getLevel().dimension(), position);
			if (value instanceof Optional<?> village && village.isPresent()) {
				return (UUID) village.get().getClass().getMethod("id").invoke(village.get());
			}
			return null;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "nearestVillage=MISSING detail=" + error);
			helper.fail("missing nearest village index: " + error);
			return null;
		}
	}

	/**
	 * A distant village sits this many sections from its test: beyond the whole test grid, so no test
	 * that starts later can put its own village within vanilla's village distance of it and merge.
	 */
	static final int DISTANT_VILLAGE_SECTIONS = 128;

	static @Nullable BlockPos findDistantVillageAnchor(final GameTestHelper helper, final String test) {
		BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
		int distance = DISTANT_VILLAGE_SECTIONS;
		int originSectionX = SectionPos.blockToSectionCoord(origin.getX());
		int originSectionZ = SectionPos.blockToSectionCoord(origin.getZ());
		// The test grid grows toward +x and +z from its first corner, so the far side of -x and -z is clear of it.
		int[][] directions = {{-1, -1}, {-1, 0}, {0, -1}, {-1, 1}, {1, -1}, {1, 1}, {1, 0}, {0, 1}};
		for (int[] direction : directions) {
			int chunkX = originSectionX + direction[0] * distance;
			int chunkZ = originSectionZ + direction[1] * distance;
			BlockPos candidate = new BlockPos(
				SectionPos.sectionToBlockCoord(chunkX) + 8, origin.getY(), SectionPos.sectionToBlockCoord(chunkZ) + 8
			);
			String claim = helper.getLevel().dimension().identifier() + "@" + chunkX + "," + chunkZ;
			if (CLAIMED_ANCHORS.contains(claim)) {
				continue;
			}
			helper.getLevel().setChunkForced(chunkX, chunkZ, true);
			LevelChunk chunk = helper.getLevel().getChunkAt(candidate);
			boolean hasVillagePoi = helper.getLevel().getPoiManager()
				.getInChunk(holder -> holder.is(PoiTypeTags.VILLAGE), chunk.getPos(), PoiManager.Occupancy.ANY)
				.findAny().isPresent();
			boolean hasEntities = !mobs(helper, candidate).isEmpty();
			boolean hasBlocks = false;
			int chunkMinX = SectionPos.sectionToBlockCoord(chunkX);
			int chunkMinZ = SectionPos.sectionToBlockCoord(chunkZ);
			for (int dx = 0; dx < SectionPos.SECTION_SIZE && !hasBlocks; dx++) {
				for (int dz = 0; dz < SectionPos.SECTION_SIZE && !hasBlocks; dz++) {
					for (int dy = -1; dy <= 2; dy++) {
						BlockPos site = new BlockPos(chunkMinX + dx, candidate.getY() + dy, chunkMinZ + dz);
						if (!helper.getLevel().getBlockState(site).isAir()) {
							hasBlocks = true;
							break;
						}
					}
				}
			}
			UUID nearest = nearestVillageId(helper, test, candidate);
			helper.getLevel().setChunkForced(chunkX, chunkZ, false);
			if (!hasVillagePoi && !hasEntities && !hasBlocks && nearest == null && CLAIMED_ANCHORS.add(claim)) {
				detail(test, "distantAnchor=" + candidate + " sectionDistance=" + distance);
				return candidate;
			}
		}

		result(test, "distantVillageAnchor=MISSING distanceSections=" + distance);
		helper.fail("no empty distant chunk for the patrol unload test");
		return null;
	}

	static @Nullable List<UUID> scanChunkIds(final GameTestHelper helper, final String test, final BlockPos poi) {
		try {
			Class<?> service = Class.forName(SERVICE);
			Method onChunkLoad = service.getMethod("onChunkLoad", ServerLevel.class, LevelChunk.class);
			LevelChunk chunk = helper.getLevel().getChunkAt(poi);
			Object value = onChunkLoad.invoke(null, helper.getLevel(), chunk);
			if (value instanceof List<?> values) {
				return values.stream().filter(UUID.class::isInstance).map(UUID.class::cast).toList();
			}

			result(test, "chunkScanResult=MISSING");
			helper.fail("chunk scan returned no village list");
			return null;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "chunkScanResult=MISSING detail=" + error);
			helper.fail("missing patrol chunk-load behavior: " + error);
			return null;
		}
	}

	static List<Mob> mobs(final GameTestHelper helper) {
		return mobs(helper, anchor(helper));
	}

	static List<Mob> mobs(final GameTestHelper helper, final BlockPos centerPosition) {
		Vec3 center = Vec3.atCenterOf(centerPosition);
		AABB bounds = new AABB(
			center.x - 128.0, center.y - 64.0, center.z - 128.0,
			center.x + 128.0, center.y + 128.0, center.z + 128.0
		);
		return helper.getLevel().getEntitiesOfClass(Mob.class, bounds, Mob::isAlive);
	}

	static List<Mob> patrols(final GameTestHelper helper) {
		return patrols(helper, anchor(helper));
	}

	static List<Mob> patrols(final GameTestHelper helper, final BlockPos center) {
		UUID villageId = VILLAGE_IDS.get(helper);
		if (villageId == null) {
			return List.of();
		}

		try {
			Method getVillageId = Class.forName(STATE).getMethod("villageId", Mob.class);
			List<Mob> members = new ArrayList<>();
			for (Mob mob : mobs(helper, center)) {
				if (isPatrolType(mob) && villageId.equals(getVillageId.invoke(null, mob))) {
					members.add(mob);
				}
			}

			return List.copyOf(members);
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalStateException("Could not read patrol identity", error);
		}
	}

	static List<Mob> activePatrols(final GameTestHelper helper, final String test) {
		try {
			Method members = Class.forName(SERVICE).getDeclaredMethod("members", Level.class, UUID.class);
			members.setAccessible(true);
			Object value = members.invoke(null, helper.getLevel(), VILLAGE_IDS.get(helper));
			if (value instanceof List<?> values) {
				return values.stream().filter(Mob.class::isInstance).map(Mob.class::cast).toList();
			}

			result(test, "activeMembers=MISSING");
			helper.fail("patrol active-member list is missing");
			return List.of();
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "activeMembers=MISSING detail=" + error);
			helper.fail("missing patrol active-member state: " + error);
			return List.of();
		}
	}

	static @Nullable BlockPos memberLastPosition(
		final GameTestHelper helper,
		final String test,
		final UUID memberId
	) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Object value = dataClass.getMethod("villageForMember", UUID.class).invoke(data, memberId);
			if (!(value instanceof Optional<?> village) || village.isEmpty()) {
				result(test, "memberRecord=MISSING member=" + memberId);
				helper.fail("village data lost patrol member " + memberId);
				return null;
			}

			for (Object member : (List<?>) village.get().getClass().getMethod("members").invoke(village.get())) {
				if (memberId.equals(member.getClass().getMethod("id").invoke(member))) {
					return (BlockPos) member.getClass().getMethod("lastPosition").invoke(member);
				}
			}

			result(test, "memberRecord=MISSING member=" + memberId);
			helper.fail("village member record is missing " + memberId);
			return null;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "memberRecord=MISSING detail=" + error);
			helper.fail("missing saved patrol member position: " + error);
			return null;
		}
	}

	static void updateThreats(final GameTestHelper helper, final String test) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Object value = dataClass.getMethod("village", UUID.class).invoke(data, VILLAGE_IDS.get(helper));
			if (!(value instanceof Optional<?> village) || village.isEmpty()) {
				result(test, "villageRecord=MISSING");
				helper.fail("village record is missing for threat update");
				return;
			}

			Method update = Class.forName(SERVICE).getDeclaredMethod(
				"updateThreats", ServerLevel.class, dataClass, village.get().getClass()
			);
			update.setAccessible(true);
			update.invoke(null, helper.getLevel(), data, village.get());
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "threatUpdate=MISSING detail=" + error);
			helper.fail("missing patrol threat update: " + error);
		}
	}

	static @Nullable Integer threatOutsideTicks(
		final GameTestHelper helper,
		final String test,
		final UUID attackerId
	) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Object value = dataClass.getMethod("village", UUID.class).invoke(data, VILLAGE_IDS.get(helper));
			if (!(value instanceof Optional<?> village) || village.isEmpty()) {
				helper.fail("village record is missing for threat timeout state");
				return null;
			}

			for (Object threat : (List<?>) village.get().getClass().getMethod("threats").invoke(village.get())) {
				if (attackerId.equals(threat.getClass().getMethod("attackerId").invoke(threat))) {
					return (Integer) threat.getClass().getMethod("outsideTicks").invoke(threat);
				}
			}

			return null;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "threatState=MISSING detail=" + error);
			helper.fail("missing threat timeout state: " + error);
			return null;
		}
	}

	static @Nullable Mob villager(final GameTestHelper helper) {
		Vec3 center = Vec3.atCenterOf(anchor(helper).offset(2, 0, 2));
		return mobs(helper).stream()
			.filter(mob -> typeId(mob).equals("minecraft:villager"))
			.min((first, second) -> Double.compare(first.distanceToSqr(center), second.distanceToSqr(center)))
			.orElse(null);
	}

	static List<Mob> ofType(final GameTestHelper helper, final String id) {
		return patrols(helper).stream().filter(mob -> typeId(mob).equals(id)).toList();
	}

	static @Nullable Mob role(final GameTestHelper helper, final String test, final String wanted) {
		try {
			Class<?> state = Class.forName(STATE);
			Method role = state.getMethod("role", Mob.class);
			for (Mob mob : patrols(helper)) {
				if (wanted.equals(String.valueOf(role.invoke(null, mob)))) {
					return mob;
				}
			}

			return null;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "patrolRole=MISSING detail=" + error);
			helper.fail("missing patrol role state: " + error);
			return null;
		}
	}

	static @Nullable Mob activeRole(final GameTestHelper helper, final String test, final String wanted) {
		try {
			Method role = Class.forName(STATE).getMethod("role", Mob.class);
			for (Mob mob : activePatrols(helper, test)) {
				if (wanted.equals(String.valueOf(role.invoke(null, mob)))) {
					return mob;
				}
			}

			return null;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "activePatrolRole=MISSING detail=" + error);
			helper.fail("missing active patrol role state: " + error);
			return null;
		}
	}

	static int intConstant(final GameTestHelper helper, final String test, final String name) {
		try {
			return Class.forName(SERVICE).getField(name).getInt(null);
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, name + "=MISSING detail=" + error);
			helper.fail("missing patrol constant " + name + ": " + error);
			return -1;
		}
	}

	static boolean marker(final Mob mob) {
		try {
			return (Boolean) Class.forName(ACCESS).getMethod("isPatrolMarker").invoke(mob);
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalStateException("Could not read the synced patrol marker", error);
		}
	}

	static boolean hasThreat(final GameTestHelper helper, final String test, final UUID attackerId) {
		try {
			Method hasThreat = Class.forName(SERVICE).getMethod("hasThreat", ServerLevel.class, UUID.class, UUID.class);
			return Boolean.TRUE.equals(hasThreat.invoke(null, helper.getLevel(), VILLAGE_IDS.get(helper), attackerId));
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "threatState=MISSING detail=" + error);
			helper.fail("missing village threat state: " + error);
			return false;
		}
	}

	static @Nullable Boolean targetGoalContinues(
		final GameTestHelper helper,
		final String test,
		final Mob guard,
		final LivingEntity target
	) {
		try {
			Object goal = ((MobPatrolAccess) guard).patrolTargetSelector().getAvailableGoals().stream()
				.map(wrapped -> wrapped.getGoal())
				.filter(candidate -> candidate.getClass().getName()
					.equals("com.stella.villages.patrol.PatrolThreatTargetGoal"))
				.findFirst()
				.orElse(null);
			if (goal == null) {
				result(test, "threatTargetGoal=MISSING");
				helper.fail("patrol threat target goal is missing");
				return null;
			}

			Field cachedTarget = goal.getClass().getSuperclass().getDeclaredField("targetMob");
			cachedTarget.setAccessible(true);
			cachedTarget.set(goal, target);
			Method canContinue = goal.getClass().getMethod("canContinueToUse");
			canContinue.setAccessible(true);
			return (Boolean) canContinue.invoke(goal);
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "threatTargetGoal=MISSING detail=" + error);
			helper.fail("missing patrol target continuation behavior: " + error);
			return null;
		}
	}

	static boolean startThreat(final GameTestHelper helper, final String test, final UUID attackerId) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("get", ServerLevel.class).invoke(null, helper.getLevel());
			Object found = dataClass.getMethod("findNearest", ResourceKey.class, BlockPos.class)
				.invoke(data, helper.getLevel().dimension(), anchor(helper));
			if (!(found instanceof Optional<?> village) || village.isEmpty()) {
				helper.fail("missing village record for threat timer test");
				return false;
			}

			Object record = village.get();
			dataClass.getMethod("startThreat", record.getClass(), UUID.class).invoke(data, record, attackerId);
			return true;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "threatRecord=MISSING detail=" + error);
			helper.fail("missing threat record API: " + error);
			return false;
		}
	}

	static boolean diagonalAnchorIndex(final GameTestHelper helper, final String test) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("empty").invoke(null);
			Method findOrCreate = dataClass.getMethod("findOrCreate", ResourceKey.class, BlockPos.class);
			ResourceKey<Level> dimension = helper.getLevel().dimension();
			BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
			int distance = PoiManager.MAX_VILLAGE_DISTANCE;
			Object first = findOrCreate.invoke(data, dimension, base);
			Object inside = findOrCreate.invoke(
				data, dimension, base.offset((distance - 1) * 16, (distance - 1) * 16, (distance - 1) * 16)
			);
			Object boundary = findOrCreate.invoke(
				data, dimension, base.offset(distance * 16, distance * 16, distance * 16)
			);
			Object beyond = findOrCreate.invoke(
				data, dimension, base.offset((distance + 1) * 16, (distance + 1) * 16, (distance + 1) * 16)
			);
			UUID firstId = (UUID) first.getClass().getMethod("id").invoke(first);
			UUID insideId = (UUID) inside.getClass().getMethod("id").invoke(inside);
			UUID boundaryId = (UUID) boundary.getClass().getMethod("id").invoke(boundary);
			UUID beyondId = (UUID) beyond.getClass().getMethod("id").invoke(beyond);
			boolean within = firstId.equals(insideId);
			boolean onBoundary = firstId.equals(boundaryId);
			boolean beyondBoundary = !firstId.equals(beyondId);

			result(test, "maximumSectionDistance=" + distance + " diagonalWithin=" + within
				+ " diagonalBoundary=" + onBoundary + " diagonalBeyond=" + beyondBoundary);
			helper.assertTrue(within, "diagonal POI inside the section bound created a new village");
			helper.assertTrue(onBoundary, "diagonal POI on the section bound created a new village");
			helper.assertTrue(beyondBoundary, "diagonal POI beyond the section bound reused a village");
			return true;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "villageIndex=MISSING detail=" + error);
			helper.fail("missing diagonal village index behavior: " + error);
			return false;
		}
	}

	static boolean anchorIndex(final GameTestHelper helper, final String test) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Object data = dataClass.getMethod("empty").invoke(null);
			Method findOrCreate = dataClass.getMethod("findOrCreate", ResourceKey.class, BlockPos.class);
			ResourceKey<Level> overworld = helper.getLevel().dimension();
			ResourceKey<Level> nether = ResourceKey.create(
				Registries.DIMENSION, Identifier.parse("minecraft:the_nether")
			);
			BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
			int distance = PoiManager.MAX_VILLAGE_DISTANCE;
			Object first = findOrCreate.invoke(data, overworld, base);
			Object near = findOrCreate.invoke(data, overworld, base.offset((distance - 1) * 16, 0, 0));
			Object boundary = findOrCreate.invoke(data, overworld, base.offset(distance * 16, 0, 0));
			Object beyond = findOrCreate.invoke(data, overworld, base.offset((distance + 1) * 16, 0, 0));
			Object otherDimension = findOrCreate.invoke(data, nether, base);
			Object left = findOrCreate.invoke(data, overworld, base.offset(20 * 16, 0, 0));
			Object right = findOrCreate.invoke(data, overworld, base.offset(32 * 16, 0, 0));
			Object middle = findOrCreate.invoke(data, overworld, base.offset(26 * 16, 0, 0));
			UUID firstId = (UUID) first.getClass().getMethod("id").invoke(first);
			UUID nearId = (UUID) near.getClass().getMethod("id").invoke(near);
			UUID boundaryId = (UUID) boundary.getClass().getMethod("id").invoke(boundary);
			UUID beyondId = (UUID) beyond.getClass().getMethod("id").invoke(beyond);
			UUID otherDimensionId = (UUID) otherDimension.getClass().getMethod("id").invoke(otherDimension);
			UUID leftId = (UUID) left.getClass().getMethod("id").invoke(left);
			UUID rightId = (UUID) right.getClass().getMethod("id").invoke(right);
			UUID middleId = (UUID) middle.getClass().getMethod("id").invoke(middle);
			UUID expectedTie = leftId.toString().compareTo(rightId.toString()) < 0 ? leftId : rightId;
			boolean within = firstId.equals(nearId);
			boolean onBoundary = firstId.equals(boundaryId);
			boolean beyondBoundary = !firstId.equals(beyondId);
			boolean dimensionScoped = !firstId.equals(otherDimensionId);
			boolean stableTie = expectedTie.equals(middleId);

			result(test, "within=" + within + " boundary=" + onBoundary + " beyond=" + beyondBoundary
				+ " dimension=" + dimensionScoped + " stableTie=" + stableTie);
			helper.assertTrue(within, "POIs inside the section bound split into different villages");
			helper.assertTrue(onBoundary, "POIs on the section bound split into different villages");
			helper.assertTrue(beyondBoundary, "POIs beyond the section bound merged into one village");
			helper.assertTrue(dimensionScoped, "villages with matching coordinates merged across dimensions");
			helper.assertTrue(stableTie, "equidistant village anchors did not use the UUID tie-break");
			return true;
		} catch (ReflectiveOperationException | LinkageError error) {
			result(test, "villageIndex=MISSING detail=" + error);
			helper.fail("missing village index behavior: " + error);
			return false;
		}
	}

	static double followRange(final Mob mob) {
		return mob.getAttribute(Attributes.FOLLOW_RANGE) == null ? -1.0
			: mob.getAttribute(Attributes.FOLLOW_RANGE).getValue();
	}

	static String typeId(final Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	static void result(final String test, final String details) {
		McStellaVillages.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, details);
	}

	static void detail(final String test, final String details) {
		McStellaVillages.LOGGER.info("[BEHAVIOR] test={} state {}", test, details);
	}

	private static boolean isPatrolType(final Mob mob) {
		String id = typeId(mob);
		return id.equals("minecraft:vindicator")
			|| id.equals("minecraft:pillager")
			|| id.equals("minecraft:evoker")
			|| id.equals("minecraft:ravager")
			|| id.equals("minecraft:witch");
	}
}
