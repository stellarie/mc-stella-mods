package com.stella.villages;

import com.stella.villages.patrol.MobPatrolAccess;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import com.stella.villages.patrol.VillageCoverage;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * A patrol is part of its village: its members never despawn and never fill the mob cap, a village
 * settled after its chunk loaded still gets a patrol, and a village whose head is gone gets a new one.
 */
public class VillageCoverageGameTest {
	private static final Set<UUID> SCANNING = ConcurrentHashMap.newKeySet();
	/** Far enough above every test's players that only the despawn distance decides. */
	private static final int HIGH = 150;

	static {
		// Only a test's own player drives the coverage scan, so no other test's village changes under it.
		VillageCoverage.setScanFilter(player -> SCANNING.contains(player.getUUID()));
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aPatrolMemberNeverDespawns(final GameTestHelper helper) {
		helper.makeMockServerPlayerInLevel();
		Mob member = plain(helper, 2, HIGH, 2);
		// A member whose persistence flag was lost, which only its patrol mark can keep.
		PatrolState.attach(member, UUID.randomUUID(), PatrolState.Role.VINDICATOR);
		((MobPatrolAccess) member).setPatrolMarker(true);
		Mob stranger = plain(helper, 4, HIGH, 2);
		member.checkDespawn();
		stranger.checkDespawn();
		PatrolTestSupport.result("aPatrolMemberNeverDespawns", "member=" + !member.isRemoved() + " stranger=" + !stranger.isRemoved());
		helper.assertTrue(stranger.isRemoved(), "a stranger far from every player did not despawn, so the case proves nothing");
		helper.assertFalse(member.isRemoved(), "a patrol member despawned");
		helper.succeed();
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aPatrolMemberDoesNotCountTowardTheMobCap(final GameTestHelper helper) {
		int before = monsters(helper);
		Mob member = plain(helper, 2, 2, 2);
		PatrolState.attach(member, UUID.randomUUID(), PatrolState.Role.VINDICATOR);
		((MobPatrolAccess) member).setPatrolMarker(true);
		int withMember = monsters(helper);
		plain(helper, 4, 2, 2);
		int withStranger = monsters(helper);
		PatrolTestSupport.result("aPatrolMemberDoesNotCountTowardTheMobCap", "member=" + (withMember - before) + " stranger=" + (withStranger - withMember));
		helper.assertTrue(withStranger - withMember == 1, "a stranger added " + (withStranger - withMember) + " to the cap, so the case proves nothing");
		helper.assertTrue(withMember == before, "a patrol member added " + (withMember - before) + " to the mob cap");
		helper.succeed();
	}

	@GameTest(maxTicks = VillageCoverage.SCAN_INTERVAL_TICKS * 3, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aVillageSettledAfterItsChunkLoadedGetsAPatrol(final GameTestHelper helper) {
		String test = "aVillageSettledAfterItsChunkLoadedGetsAPatrol";
		// The village is settled without the chunk-load registration, as a village is when its villagers claim beds later.
		if (!PatrolTestSupport.prepareVillage(helper, test, false)) {
			return;
		}
		ServerPlayer visitor = visitor(helper);
		helper.succeedWhen(() -> {
			UUID villageId = PatrolTestSupport.nearestVillageId(helper, test, PatrolTestSupport.anchor(helper));
			helper.assertTrue(villageId != null, "the village has not registered yet");
			List<Mob> members = members(helper, villageId);
			helper.assertTrue(members.size() == PatrolService.PATROL_MEMBER_COUNT, "the village has " + members.size() + " patrol members");
			helper.assertTrue(members.stream().anyMatch(mob -> PatrolState.role(mob) == PatrolState.Role.HEAD), "the patrol has no head");
			SCANNING.remove(visitor.getUUID());
			PatrolTestSupport.result(test, "members=" + members.size() + " head=true");
		});
	}

	@GameTest(maxTicks = VillageCoverage.HEAD_RESPAWN_DELAY_TICKS + VillageCoverage.SCAN_INTERVAL_TICKS * 4, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aVillageWhoseHeadIsGoneGetsANewPatrol(final GameTestHelper helper) {
		String test = "aVillageWhoseHeadIsGoneGetsANewPatrol";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		UUID villageId = PatrolTestSupport.villageId(helper);
		Mob head = members(helper, villageId).stream().filter(mob -> PatrolState.role(mob) == PatrolState.Role.HEAD).findFirst().orElse(null);
		helper.assertTrue(head != null, "the roster has no head, so the case proves nothing");
		head.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fellOutOfWorld(), 10_000.0F);
		UUID oldHead = head.getUUID();
		int[] ticks = {0};
		int[] firstSeen = {-1};
		// Sampled every tick, so an early return is caught at the tick it happens rather than waited past.
		helper.onEachTick(() -> {
			ticks[0]++;
			if (firstSeen[0] < 0 && members(helper, villageId).stream().anyMatch(mob -> PatrolState.role(mob) == PatrolState.Role.HEAD && !mob.getUUID().equals(oldHead))) {
				firstSeen[0] = ticks[0];
			}
		});
		ServerPlayer visitor = visitor(helper);
		helper.succeedWhen(() -> {
			List<Mob> members = members(helper, villageId);
			helper.assertTrue(firstSeen[0] >= 0, "no new head yet at tick " + ticks[0]);
			helper.assertTrue(firstSeen[0] >= VillageCoverage.HEAD_RESPAWN_DELAY_TICKS, "the head came back after " + firstSeen[0] + " ticks, before the delay");
			helper.assertTrue(members.size() == PatrolService.PATROL_MEMBER_COUNT, "the refilled patrol has " + members.size() + " members");
			SCANNING.remove(visitor.getUUID());
			PatrolTestSupport.result(test, "newHeadAfter=" + firstSeen[0] + " members=" + members.size());
		});
	}

	// A vindicator as the world spawns one: the test helper's own spawn marks every mob persistent.
	private static Mob plain(final GameTestHelper helper, final int x, final int y, final int z) {
		Mob mob = EntityTypes.VINDICATOR.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.NATURAL);
		mob.setNoAi(true);
		mob.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, y, z))), 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static ServerPlayer visitor(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = Vec3.atBottomCenterOf(PatrolTestSupport.anchor(helper)).add(4.0, 0.0, 4.0);
		player.setPos(at.x, at.y, at.z);
		SCANNING.add(player.getUUID());
		return player;
	}

	private static List<Mob> members(final GameTestHelper helper, final UUID villageId) {
		return PatrolTestSupport.mobs(helper).stream().filter(mob -> mob.isAlive() && villageId.equals(PatrolState.villageId(mob))).toList();
	}

	// The monster count the natural spawner would see across the level right now.
	private static int monsters(final GameTestHelper helper) {
		var level = helper.getLevel();
		NaturalSpawner.SpawnState state = NaturalSpawner.createState(
			1, level,
			(packed, consumer) -> {
				LevelChunk chunk = level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(packed), net.minecraft.world.level.ChunkPos.getZ(packed));
				if (chunk != null) {
					consumer.accept(chunk);
				}
			},
			new LocalMobCapCalculator(level.getChunkSource().chunkMap)
		);
		return state.getMobCategoryCounts().getInt(MobCategory.MONSTER);
	}
}
