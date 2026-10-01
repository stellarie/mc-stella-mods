package com.stella.villages;

import com.mojang.authlib.GameProfile;
import com.stella.mobs.behavior.Elite;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionKind;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import com.stella.villages.data.VillageWorldData.MissionStatus;
import com.stella.villages.mission.MissionBoardMenu;
import com.stella.villages.mission.MissionService;
import com.stella.villages.mission.MissionService.MissionOffer;
import com.stella.villages.mission.NemesisState;
import com.stella.villages.mission.GuardQuests;
import com.stella.villages.mission.QuestMissions;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The nine-offer quest board: three Nemesis, three elite and three kill contracts, targets 256 to 384 blocks
 * from the bell, the elite and kill quests' mobs and rewards, and what survives a save.
 */
public final class QuestBoardGameTest {
	private static final Identifier SKELETON = Identifier.parse("minecraft:skeleton");
	private static final List<Identifier> POOL = List.of(
		Identifier.parse("minecraft:zombie"), SKELETON, Identifier.parse("minecraft:spider")
	);
	/** Test seam: each kill's action bar text, and each reward as "kind:where", by owner. */
	private static final Map<UUID, List<String>> PROGRESS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<String>> PAID = new ConcurrentHashMap<>();
	private static final int SITE_RADIUS = 8;
	private static final int TEST_TICKS = 600;

	static {
		MissionService.setOfferPool(POOL);
		QuestMissions.setRecorder(new QuestMissions.Recorder() {
			@Override
			public void progress(final ServerPlayer owner, final String text) {
				PROGRESS.computeIfAbsent(owner.getUUID(), key -> new CopyOnWriteArrayList<>()).add(text);
			}

			@Override
			public void paid(final UUID owner, final MissionKind kind, final String where) {
				PAID.computeIfAbsent(owner, key -> new CopyOnWriteArrayList<>()).add(kind + ":" + where);
			}
		});
	}

	private record Fixture(ServerPlayer owner, Mob head, BlockPos bell) {
	}

	@GameTest(dimension = "minecraft:the_nether", padding = PatrolTestSupport.VILLAGE_PADDING)
	public void nineOffersThreeKinds(final GameTestHelper helper) {
		Fixture fixture = fixture(helper, "nineOffersThreeKinds");
		if (fixture == null) {
			return;
		}
		List<MissionOffer> offers = openBoard(helper, fixture);
		List<MissionKind> kinds = offers.stream().map(MissionOffer::kind).toList();
		helper.assertTrue(offers.size() == MissionService.BOARD_SIZE && MissionService.BOARD_SIZE == 9, "the board holds " + offers.size() + " offers");
		helper.assertTrue(kinds.equals(List.of(MissionKind.NEMESIS, MissionKind.NEMESIS, MissionKind.NEMESIS, MissionKind.ELITE, MissionKind.ELITE,
			MissionKind.ELITE, MissionKind.KILL, MissionKind.KILL, MissionKind.KILL)), "the board's kinds are " + kinds);
		for (MissionKind kind : MissionService.BOARD_KINDS) {
			List<Identifier> types = offers.stream().filter(offer -> offer.kind() == kind).map(MissionOffer::targetType).toList();
			helper.assertTrue(new HashSet<>(types).size() == 3, kind + " offers repeat a type: " + types);
		}
		for (MissionOffer offer : offers) {
			boolean counted = offer.kind() == MissionKind.KILL
				? offer.count() >= QuestMissions.KILL_MIN_COUNT && offer.count() <= QuestMissions.KILL_MAX_COUNT : offer.count() == 0;
			helper.assertTrue(counted, offer.kind() + " offer asks for " + offer.count());
		}
		MissionBoardMenu menu = (MissionBoardMenu) fixture.owner().containerMenu;
		int shown = 0;
		for (int slot = 0; slot < menu.getContainer().getContainerSize(); slot++) {
			if (MissionBoardMenu.offerIndex(slot) >= 0 && !menu.getContainer().getItem(slot).is(Items.STAINED_GLASS_PANE.gray())) {
				shown++;
			}
		}
		helper.assertTrue(menu.getContainer().getContainerSize() == 18 && shown == 9 && MissionBoardMenu.OFFER_SLOTS.length == 9,
			"the menu shows " + shown + " offers in " + menu.getContainer().getContainerSize() + " slots");
		PatrolTestSupport.result("nineOffersThreeKinds", "offers=" + offers.size() + " kinds=" + kinds);
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void targetsRandomNearBell(final GameTestHelper helper) {
		String test = "targetsRandomNearBell";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		ServerLevel level = helper.getLevel();
		BlockPos anchor = fixture.head().blockPosition();
		// A bell well off the anchor, so a search around the anchor could not pass for one around the bell.
		BlockPos bellSpot = anchor.offset(40, 0, 0);
		level.setChunkForced(bellSpot.getX() >> 4, bellSpot.getZ() >> 4, true);
		level.getChunkAt(bellSpot);
		level.setBlockAndUpdate(bellSpot.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(bellSpot, Blocks.BELL.defaultBlockState());
		var village = VillageWorldData.get(level).villageForMember(fixture.head().getUUID()).orElseThrow();
		BlockPos bell = PatrolService.bellOf(level, village);
		helper.assertTrue(bell.equals(bellSpot) && !bell.equals(village.anchor()), "the village bell is " + bell + ", not " + bellSpot);
		List<BlockPos> asked = new ArrayList<>();
		Set<Long> forced = new HashSet<>();
		// Ground is laid under each spot the real random search asks about, so its safety check can pass.
		MissionService.setTargetCandidateSource(bell, (origin, attempt, random) -> {
			BlockPos candidate = MissionService.targetCandidate(origin, random);
			asked.add(candidate);
			int chunkX = candidate.getX() >> 4;
			int chunkZ = candidate.getZ() >> 4;
			if (forced.add(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ))) {
				level.setChunkForced(chunkX, chunkZ, true);
			}
			level.getChunkAt(candidate);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos floor = candidate.offset(dx, -1, dz);
					level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
					for (int dy = 0; dy < 3; dy++) {
						level.setBlockAndUpdate(floor.above(dy + 1), Blocks.AIR.defaultBlockState());
					}
				}
			}
			return candidate;
		});
		Set<BlockPos> found = new HashSet<>();
		int outOfBand = 0;
		int nearAnchorBand = 0;
		try {
			for (int search = 0; search < 200; search++) {
				Optional<BlockPos> target = MissionService.findSafeTarget(level, bell, SKELETON);
				if (target.isEmpty()) {
					continue;
				}
				found.add(target.get());
				double distance = Math.sqrt(horizontalSquared(bell, target.get()));
				if (distance < MissionService.MISSION_MIN_DISTANCE || distance > MissionService.MISSION_MAX_DISTANCE) {
					outOfBand++;
				}
				double fromAnchor = Math.sqrt(horizontalSquared(village.anchor(), target.get()));
				if (fromAnchor >= MissionService.MISSION_MIN_DISTANCE && fromAnchor <= MissionService.MISSION_MAX_DISTANCE) {
					nearAnchorBand++;
				}
			}
		} finally {
			MissionService.setTargetCandidateSource(bell, null);
			forced.forEach(key -> level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key), false));
			level.setChunkForced(bellSpot.getX() >> 4, bellSpot.getZ() >> 4, false);
		}
		PatrolTestSupport.result(test, "searches=200 found=" + found.size() + " asked=" + asked.size() + " outOfBand=" + outOfBand
			+ " inAnchorBand=" + nearAnchorBand + " bellOffsetFromAnchor=" + bell.distManhattan(village.anchor()));
		helper.assertTrue(MissionService.MISSION_MIN_DISTANCE == 256 && MissionService.MISSION_MAX_DISTANCE == 384, "the target band is not 256 to 384 blocks");
		helper.assertTrue(found.size() >= 20, "200 searches gave only " + found.size() + " distinct targets");
		helper.assertTrue(outOfBand == 0, outOfBand + " targets were not 256 to 384 blocks from the bell");
		helper.assertTrue(nearAnchorBand < found.size(), "every target also sits in the anchor's band, so the search may be centred on the anchor");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void anAcceptedTargetIsMeasuredFromTheBellNotTheAnchor(final GameTestHelper helper) {
		Fixture fixture = fixture(helper, "anAcceptedTargetIsMeasuredFromTheBellNotTheAnchor");
		if (fixture == null) {
			return;
		}
		ServerLevel level = helper.getLevel();
		BlockPos bellSpot = fixture.head().blockPosition().offset(40, 0, 0);
		level.setChunkForced(bellSpot.getX() >> 4, bellSpot.getZ() >> 4, true);
		level.getChunkAt(bellSpot);
		level.setBlockAndUpdate(bellSpot.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(bellSpot, Blocks.BELL.defaultBlockState());
		var village = VillageWorldData.get(level).villageForMember(fixture.head().getUUID()).orElseThrow();
		BlockPos bell = PatrolService.bellOf(level, village);
		helper.assertTrue(bell.equals(bellSpot), "the village bell is " + bell + ", not " + bellSpot);
		// Ground and a search exist only around the bell, so a search around the anchor finds nothing.
		BlockPos target = prepareSite(helper, bell);
		try {
			MissionRecord mission = accept(helper, fixture, MissionKind.KILL);
			PatrolTestSupport.result("anAcceptedTargetIsMeasuredFromTheBellNotTheAnchor", "bell=" + bell + " target=" + mission.targetPosition());
			helper.assertTrue(mission.targetPosition().equals(target), "the contract's target is " + mission.targetPosition() + ", not " + target + " by the bell");
			VillageWorldData.get(level).removeMission(mission.id());
		} finally {
			release(helper, bell, target);
			level.setChunkForced(bellSpot.getX() >> 4, bellSpot.getZ() >> 4, false);
		}
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void eliteQuestSpawns(final GameTestHelper helper) {
		quest(helper, "eliteQuestSpawns", List.of(MissionKind.ELITE), (fixture, target, missions) -> {
			MissionRecord mission = missions.get(0);
			Mob elite = mobOf(helper, mission.entityId().orElse(null));
			List<Mob> escorts = mission.mobIds().stream().map(id -> mobOf(helper, id)).toList();
			helper.assertTrue(mission.status() == MissionStatus.ACTIVE && elite != null, "the elite quest did not spawn its elite");
			helper.assertTrue(Elite.isElite(elite), "the quest's target is not an elite");
			helper.assertTrue(escorts.size() == 2 && QuestMissions.ELITE_ESCORTS == 2 && escorts.stream().allMatch(mob -> mob != null && !Elite.isElite(mob)
				&& mob.getType() == elite.getType()), "the elite has " + escorts.size() + " escorts, not two ordinary mobs of its type");
			helper.assertTrue(near(elite, target, 1.5) && escorts.stream().allMatch(mob -> near(mob, target, 4.0)), "the quest mobs are not at the target " + target);
			PatrolTestSupport.result("eliteQuestSpawns", "elite=" + elite.getUUID() + " escorts=" + escorts.size() + " type=" + elite.getType());
			discardAll(elite, escorts);
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void eliteQuestRewards(final GameTestHelper helper) {
		quest(helper, "eliteQuestRewards", List.of(MissionKind.ELITE), (fixture, target, missions) -> {
			MissionRecord mission = missions.get(0);
			Mob elite = mobOf(helper, mission.entityId().orElse(null));
			List<Mob> escorts = mission.mobIds().stream().map(id -> mobOf(helper, id)).toList();
			helper.assertTrue(elite != null && escorts.size() == 2, "the elite quest did not spawn");
			int itemsBefore = drops(helper, target, Items.IRON_INGOT) + drops(helper, target, Items.EMERALD) + drops(helper, target, Items.POTION);
			for (Mob escort : escorts) {
				kill(helper, fixture.owner(), escort);
			}
			int itemsAfterEscorts = drops(helper, target, Items.IRON_INGOT) + drops(helper, target, Items.EMERALD) + drops(helper, target, Items.POTION);
			boolean stillOpen = questMission(helper, mission.id()) != null;
			kill(helper, fixture.owner(), elite);
			int emeralds = drops(helper, target, Items.EMERALD);
			int iron = drops(helper, target, Items.IRON_INGOT);
			int potions = drops(helper, target, Items.POTION);
			PatrolTestSupport.result("eliteQuestRewards", "escortsPaid=" + (itemsAfterEscorts - itemsBefore) + " stillOpen=" + stillOpen
				+ " emeralds=" + emeralds + " iron=" + iron + " potions=" + potions);
			helper.assertTrue(itemsAfterEscorts == itemsBefore && stillOpen, "an escort paid, or ended the quest");
			helper.assertTrue(emeralds == 64 && emeralds == QuestMissions.ELITE_REWARD_EMERALDS, "the elite paid " + emeralds + " emeralds");
			helper.assertTrue(iron == 64 && iron == QuestMissions.ELITE_REWARD_IRON, "the elite paid " + iron + " iron ingots");
			helper.assertTrue(potions == 2 && potions == QuestMissions.REWARD_POTIONS, "the elite paid " + potions + " potions");
			helper.assertTrue(questMission(helper, mission.id()) == null, "the quest is still open after the elite fell");
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void killQuestRewardsAtEnd(final GameTestHelper helper) {
		quest(helper, "killQuestRewardsAtEnd", List.of(MissionKind.KILL), (fixture, target, missions) -> {
			MissionRecord mission = missions.get(0);
			List<Mob> pack = mission.mobIds().stream().map(id -> mobOf(helper, id)).toList();
			int count = pack.size();
			helper.assertTrue(count >= QuestMissions.KILL_MIN_COUNT && count <= QuestMissions.KILL_MAX_COUNT && pack.stream().allMatch(mob -> mob != null
				&& mob.getType() == pack.get(0).getType() && !Elite.isElite(mob) && near(mob, target, QuestMissions.SPREAD_RADIUS + 1.5)),
				"the kill quest spawned " + count + " mobs that are not one ordinary type within six blocks of the target");
			ServerPlayer owner = fixture.owner();
			owner.getInventory().clearContent();
			for (int index = 0; index < count - 1; index++) {
				kill(helper, owner, pack.get(index));
			}
			int early = owner.getInventory().countItem(Items.IRON_INGOT) + owner.getInventory().countItem(Items.POTION);
			kill(helper, owner, pack.get(count - 1));
			int iron = owner.getInventory().countItem(Items.IRON_INGOT);
			int potions = owner.getInventory().countItem(Items.POTION);
			PatrolTestSupport.result("killQuestRewardsAtEnd", "mobs=" + count + " earlyReward=" + early + " iron=" + iron + " potions=" + potions
				+ " paid=" + PAID.get(owner.getUUID()));
			helper.assertTrue(early == 0, "the quest paid before its last kill");
			helper.assertTrue(iron == 64 && iron == QuestMissions.KILL_REWARD_IRON, "the owner received " + iron + " iron ingots");
			helper.assertTrue(potions == 2 && potions == QuestMissions.REWARD_POTIONS, "the owner received " + potions + " potions");
			helper.assertTrue(questMission(helper, mission.id()) == null, "the quest is still open after its last kill");
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void killQuestDropsRewardAtFeetWhenInventoryIsFull(final GameTestHelper helper) {
		quest(helper, "killQuestDropsRewardAtFeetWhenInventoryIsFull", List.of(MissionKind.KILL), (fixture, target, missions) -> {
			ServerPlayer owner = fixture.owner();
			// A creative player's inventory takes any item and keeps none, so the owner plays in survival.
			owner.setGameMode(GameType.SURVIVAL);
			for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
				owner.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
			}
			for (UUID id : missions.get(0).mobIds()) {
				kill(helper, owner, mobOf(helper, id));
			}
			BlockPos feet = owner.blockPosition();
			int iron = drops(helper, feet, Items.IRON_INGOT);
			int potions = drops(helper, feet, Items.POTION);
			PatrolTestSupport.result("killQuestDropsRewardAtFeetWhenInventoryIsFull", "iron=" + iron + " potions=" + potions
				+ " inInventory=" + owner.getInventory().countItem(Items.IRON_INGOT));
			helper.assertTrue(iron == 64 && potions == 2 && owner.getInventory().countItem(Items.IRON_INGOT) == 0,
				"a full inventory got iron=" + iron + " potions=" + potions + " at its feet");
		});
	}

	// An owner who is offline at the last kill has the reward stored and paid at the next login, so no reward is lost.
	// A guard gives each player one quest a day; the next day it gives another.
	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void guardQuestOncePerDay(final GameTestHelper helper) {
		Fixture fixture = fixture(helper, "guardQuestOncePerDay");
		if (fixture == null) {
			return;
		}
		BlockPos target = prepareSite(helper, fixture.bell());
		Mob guard = guardOf(helper, fixture);
		boolean[] given = new boolean[3];
		withPool(List.of(SKELETON), () -> {
			given[0] = GuardQuests.offer(helper.getLevel(), fixture.owner(), guard);
			given[1] = GuardQuests.offer(helper.getLevel(), fixture.owner(), guard);
			GuardQuests.markGiven(guard, fixture.owner().getUUID(), GuardQuests.day(helper.getLevel()) - 1);
			given[2] = GuardQuests.offer(helper.getLevel(), fixture.owner(), guard);
		});
		List<MissionRecord> guardMissions = missionsOf(helper, fixture.owner()).stream().filter(mission -> mission.kind() == MissionKind.GUARD).toList();
		boolean shaped = guardMissions.stream().allMatch(mission -> mission.remaining() >= QuestMissions.GUARD_MIN_COUNT
			&& mission.remaining() <= QuestMissions.GUARD_MAX_COUNT);
		PatrolTestSupport.result("guardQuestOncePerDay", "first=" + given[0] + " sameDay=" + given[1] + " nextDay=" + given[2]
			+ " missions=" + guardMissions.size() + " shaped=" + shaped);
		helper.assertTrue(given[0] && !given[1] && given[2], "the guard's daily limit is wrong: " + java.util.Arrays.toString(given));
		helper.assertTrue(guardMissions.size() == 2 && shaped, "the guard quests are " + guardMissions);
		release(helper, fixture.bell(), target);
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void guardQuestRewards(final GameTestHelper helper) {
		Fixture fixture = fixture(helper, "guardQuestRewards");
		if (fixture == null) {
			return;
		}
		BlockPos target = prepareSite(helper, fixture.bell());
		Mob guard = guardOf(helper, fixture);
		MissionRecord[] accepted = new MissionRecord[1];
		withPool(List.of(SKELETON), () -> {
			GuardQuests.offer(helper.getLevel(), fixture.owner(), guard);
			accepted[0] = missionsOf(helper, fixture.owner()).stream().filter(mission -> mission.kind() == MissionKind.GUARD).findFirst().orElse(null);
		});
		helper.assertTrue(accepted[0] != null, "the guard gave no quest");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(reachable(helper, accepted[0]), "the guard quest mobs are not loaded yet"))
			.thenExecute(() -> {
				MissionRecord mission = questMission(helper, accepted[0].id());
				ServerPlayer owner = fixture.owner();
				owner.setGameMode(GameType.SURVIVAL);
				owner.getInventory().clearContent();
				for (UUID id : mission.mobIds()) {
					kill(helper, owner, mobOf(helper, id));
				}
				int emeralds = owner.getInventory().countItem(Items.EMERALD);
				int food = QuestMissions.GUARD_FOODS.stream().mapToInt(item -> owner.getInventory().countItem(item)).sum();
				PatrolTestSupport.result("guardQuestRewards", "mobs=" + mission.mobIds().size() + " emeralds=" + emeralds + " food=" + food);
				helper.assertTrue(emeralds == QuestMissions.GUARD_REWARD_EMERALDS && food == QuestMissions.GUARD_REWARD_FOOD,
					"the guard quest paid emeralds=" + emeralds + " food=" + food);
				helper.assertTrue(questMission(helper, mission.id()) == null, "the guard quest is still open");
			})
			.thenExecute(() -> release(helper, fixture.bell(), target))
			.thenSucceed();
	}

	/** A pillager guard of the fixture's village, beside the duke. */
	private static Mob guardOf(final GameTestHelper helper, final Fixture fixture) {
		ServerLevel level = helper.getLevel();
		Mob guard = helper.spawn(EntityTypes.PILLAGER, 4, 1, 4);
		guard.setNoAi(true);
		guard.setPersistenceRequired();
		guard.snapTo(fixture.head().position().add(1.0, 0.0, 0.0), 0.0F, 0.0F);
		var data = VillageWorldData.get(level);
		var village = data.villageForMember(fixture.head().getUUID()).orElseThrow();
		PatrolState.attach(guard, village.id(), PatrolState.Role.PILLAGER);
		data.addMember(village, guard.getUUID(), PatrolState.Role.PILLAGER.name(), fixture.head().blockPosition());
		return guard;
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void offlineOwnerIsPaidAtNextLogin(final GameTestHelper helper) {
		quest(helper, "offlineOwnerIsPaidAtNextLogin", List.of(MissionKind.KILL), (fixture, target, missions) -> {
			MissionRecord mission = missions.get(0);
			UUID offlineId = UUID.randomUUID();
			VillageWorldData data = VillageWorldData.get(helper.getLevel());
			data.addMission(new MissionRecord(mission.id(), offlineId, mission.targetType(), mission.targetPosition(), mission.entityId(), mission.status(),
				mission.mapId(), mission.dimension(), mission.kind(), mission.remaining(), mission.mobIds()));
			ServerPlayer killer = helper.makeMockServerPlayerInLevel();
			for (UUID id : mission.mobIds()) {
				kill(helper, killer, mobOf(helper, id));
			}
			int fallen = drops(helper, target, Items.IRON_INGOT);
			int waiting = data.pendingKillRewards(offlineId);
			helper.assertTrue(waiting == 1 && fallen == 0 && questMission(helper, mission.id()) == null,
				"the offline owner's reward was not stored: waiting=" + waiting + " dropped=" + fallen);
			ServerPlayer returned = login(helper, offlineId);
			int iron = returned.getInventory().countItem(Items.IRON_INGOT);
			int potions = returned.getInventory().countItem(Items.POTION);
			PatrolTestSupport.result("offlineOwnerIsPaidAtNextLogin", "waiting=" + waiting + " iron=" + iron + " potions=" + potions
				+ " left=" + data.pendingKillRewards(offlineId) + " paid=" + PAID.get(offlineId));
			helper.assertTrue(iron == 64 && potions == 2 && data.pendingKillRewards(offlineId) == 0, "login paid iron=" + iron + " potions=" + potions);
			returned.getInventory().clearContent();
			ServerPlayer again = login(helper, offlineId);
			helper.assertTrue(again.getInventory().countItem(Items.IRON_INGOT) == 0, "the reward was paid twice");
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void questMobsPersistAndArePrefixed(final GameTestHelper helper) {
		quest(helper, "questMobsPersistAndArePrefixed", List.of(MissionKind.NEMESIS, MissionKind.ELITE, MissionKind.KILL), (fixture, target, missions) -> {
			List<Mob> all = new ArrayList<>();
			Mob boss = mobOf(helper, missions.get(0).entityId().orElse(null));
			helper.assertTrue(boss != null, "the Nemesis did not spawn");
			all.add(boss);
			NemesisState state = NemesisState.get(boss);
			Mob guard = state == null ? null : state.companionId().map(id -> mobOf(helper, id)).orElse(null);
			helper.assertTrue(guard != null, "the Nemesis has no companion");
			all.add(guard);
			all.add(mobOf(helper, missions.get(1).entityId().orElse(null)));
			missions.get(1).mobIds().forEach(id -> all.add(mobOf(helper, id)));
			missions.get(2).mobIds().forEach(id -> all.add(mobOf(helper, id)));
			List<String> bad = new ArrayList<>();
			for (Mob mob : all) {
				String name = mob == null || mob.getCustomName() == null ? "none" : mob.getCustomName().getString();
				if (mob == null || !mob.isPersistenceRequired() || !name.startsWith("[Quest] ")) {
					bad.add(name);
				}
			}
			PatrolTestSupport.result("questMobsPersistAndArePrefixed", "mobs=" + all.size() + " bad=" + bad + " names="
				+ all.stream().filter(mob -> mob != null && mob.getCustomName() != null).map(mob -> mob.getCustomName().getString()).distinct().toList());
			helper.assertTrue(all.size() >= 1 + 1 + 3 + QuestMissions.KILL_MIN_COUNT && bad.isEmpty(), all.size() + " quest mobs, of which these lack persistence or the prefix: " + bad);
			all.forEach(Entity::discard);
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void killQuestProgress(final GameTestHelper helper) {
		quest(helper, "killQuestProgress", List.of(MissionKind.KILL), (fixture, target, missions) -> {
			MissionRecord mission = missions.get(0);
			int count = mission.mobIds().size();
			ServerPlayer owner = fixture.owner();
			PROGRESS.remove(owner.getUUID());
			// The first mob falls to something other than the player: any death of a tracked mob counts.
			Mob first = mobOf(helper, mission.mobIds().get(0));
			first.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), first.getMaxHealth() * 4.0F);
			for (int index = 1; index < count; index++) {
				kill(helper, owner, mobOf(helper, mission.mobIds().get(index)));
			}
			List<String> shown = PROGRESS.getOrDefault(owner.getUUID(), List.of());
			List<String> expected = new ArrayList<>();
			for (int done = 1; done <= count; done++) {
				expected.add("[Quest] Kill Skeleton " + done + "/" + count);
			}
			PatrolTestSupport.result("killQuestProgress", "shown=" + shown);
			helper.assertTrue(shown.equals(expected), "the action bar showed " + shown + ", not " + expected);
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aFinishedQuestsMobsAreClearedWhenTheyLoad(final GameTestHelper helper) {
		Mob[] pair = new Mob[2];
		MissionRecord[] held = new MissionRecord[1];
		quest(helper, "aFinishedQuestsMobsAreClearedWhenTheyLoad", List.of(MissionKind.KILL), (fixture, target, missions) -> {
			held[0] = missions.get(0);
			pair[0] = mobOf(helper, held[0].mobIds().get(0));
			pair[1] = mobOf(helper, held[0].mobIds().get(1));
			// The quest is live: loading its mob clears nothing.
			ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(pair[1], helper.getLevel());
		}, () -> {
			helper.assertTrue(!pair[1].isRemoved(), "a mob of a live quest was cleared when it loaded");
			VillageWorldData.get(helper.getLevel()).removeMission(held[0].id());
			ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(pair[0], helper.getLevel());
			// Loading must not remove it on the spot: the chunk is walking its entities.
			helper.assertTrue(!pair[0].isRemoved(), "the straggler was removed inside its own load");
		}, () -> {
			PatrolTestSupport.result("aFinishedQuestsMobsAreClearedWhenTheyLoad", "stragglerRemoved=" + pair[0].isRemoved());
			helper.assertTrue(pair[0].isRemoved(), "a mob of a finished quest stayed in the world");
			held[0].mobIds().forEach(id -> {
				Mob mob = mobOf(helper, id);
				if (mob != null) {
					mob.discard();
				}
			});
		});
	}

	@GameTest
	public void questPersists(final GameTestHelper helper) {
		UUID owner = UUID.randomUUID();
		UUID eliteId = UUID.randomUUID();
		List<UUID> escorts = List.of(UUID.randomUUID(), UUID.randomUUID());
		List<UUID> pack = new ArrayList<>();
		for (int index = 0; index < 7; index++) {
			pack.add(UUID.randomUUID());
		}
		Identifier type = Identifier.parse("minecraft:zombie");
		var dimension = helper.getLevel().dimension();
		VillageWorldData data = new VillageWorldData();
		MissionRecord eliteQuest = new MissionRecord(UUID.randomUUID(), owner, type, new BlockPos(300, 64, -40), Optional.of(eliteId), MissionStatus.ACTIVE,
			Optional.empty(), dimension, MissionKind.ELITE, 0, escorts);
		MissionRecord killQuest = new MissionRecord(UUID.randomUUID(), owner, type, new BlockPos(-280, 70, 12), Optional.empty(), MissionStatus.ACTIVE,
			Optional.empty(), dimension, MissionKind.KILL, 4, pack);
		MissionRecord pending = new MissionRecord(UUID.randomUUID(), owner, type, new BlockPos(1, 2, 3), Optional.empty(), MissionStatus.TARGET_PENDING,
			Optional.empty(), dimension, MissionKind.KILL, 9, List.of());
		MissionRecord nemesis = new MissionRecord(UUID.randomUUID(), owner, type, new BlockPos(5, 6, 7), Optional.of(UUID.randomUUID()), MissionStatus.ACTIVE);
		List.of(eliteQuest, killQuest, pending, nemesis).forEach(data::addMission);
		data.addPendingKillReward(owner);
		data.addPendingKillReward(owner);

		CompoundTag saved = data.serializeState();
		VillageWorldData loaded = VillageWorldData.loadState(saved);
		MissionRecord elite = loaded.mission(eliteQuest.id()).orElse(null);
		MissionRecord kill = loaded.mission(killQuest.id()).orElse(null);
		MissionRecord waiting = loaded.mission(pending.id()).orElse(null);
		helper.assertTrue(elite != null && elite.kind() == MissionKind.ELITE && elite.entityId().equals(Optional.of(eliteId)) && elite.mobIds().equals(escorts),
			"the elite quest came back as " + elite);
		helper.assertTrue(kill != null && kill.kind() == MissionKind.KILL && kill.remaining() == 4 && kill.mobIds().equals(pack) && kill.status() == MissionStatus.ACTIVE,
			"the kill quest came back as " + kill);
		helper.assertTrue(waiting != null && waiting.kind() == MissionKind.KILL && waiting.remaining() == 9 && waiting.status() == MissionStatus.TARGET_PENDING,
			"the pending kill quest came back as " + waiting);
		helper.assertTrue(loaded.pendingKillRewards(owner) == 2, "the waiting rewards came back as " + loaded.pendingKillRewards(owner));

		// A record written before quest kinds existed has none of the new fields, and loads as a Nemesis.
		CompoundTag legacy = data.serializeState();
		for (Tag entry : legacy.getListOrEmpty("missions")) {
			if (entry instanceof CompoundTag mission) {
				mission.remove("kind");
				mission.remove("remaining");
				mission.remove("mob_ids");
			}
		}
		ListTag legacyMissions = legacy.getListOrEmpty("missions");
		VillageWorldData old = VillageWorldData.loadState(legacy);
		boolean allNemeses = old.missions().size() == legacyMissions.size() && old.missions().stream()
			.allMatch(mission -> mission.kind() == MissionKind.NEMESIS && mission.remaining() == 0 && mission.mobIds().isEmpty());
		helper.assertTrue(allNemeses, "an old save did not load as Nemesis missions: " + old.missions());
		helper.assertTrue(old.mission(nemesis.id()).map(mission -> mission.targetPosition().equals(new BlockPos(5, 6, 7))).orElse(false), "an old record lost its target");

		var ops = net.minecraft.nbt.NbtOps.INSTANCE;
		Tag encoded = VillageWorldData.CODEC.encodeStart(ops, data).getOrThrow();
		VillageWorldData decoded = VillageWorldData.CODEC.parse(ops, encoded).getOrThrow();
		helper.assertTrue(decoded.mission(killQuest.id()).map(mission -> mission.kind() == MissionKind.KILL && mission.remaining() == 4).orElse(false),
			"the saved-data codec lost a kill quest");
		PatrolTestSupport.result("questPersists", "elite=" + elite.kind() + " kill=" + kill.kind() + "/" + kill.remaining() + " waiting=" + loaded.pendingKillRewards(owner)
			+ " oldSaveMissions=" + old.missions().size());
		helper.succeed();
	}

	// ---- helpers ----

	private interface Body {
		void run(Fixture fixture, BlockPos target, List<MissionRecord> missions);
	}

	/**
	 * Accepts one contract of each kind named, from a board that draws skeletons only, then waits until every
	 * mob they spawned is reachable, which takes the target chunk a few ticks to tick. The body runs after that.
	 */
	private static void quest(final GameTestHelper helper, final String test, final List<MissionKind> kinds, final Body body) {
		quest(helper, test, kinds, body, new Runnable[0]);
	}

	/** As above, with stages that each run a few ticks after the one before, for what the server does at the end of a tick. */
	private static void quest(final GameTestHelper helper, final String test, final List<MissionKind> kinds, final Body body, final Runnable... later) {
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos target = prepareSite(helper, fixture.bell());
		List<MissionRecord> missions = new ArrayList<>();
		withPool(List.of(SKELETON), () -> kinds.forEach(kind -> missions.add(accept(helper, fixture, kind))));
		var sequence = helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(missions.stream().allMatch(mission -> reachable(helper, mission)), "the quest mobs are not loaded yet"))
			// The record the body reads is the current one: the spawn wrote its mobs into it.
			.thenExecute(() -> body.run(fixture, target, missions.stream().map(mission -> questMission(helper, mission.id())).toList()));
		for (Runnable stage : later) {
			sequence = sequence.thenIdle(3).thenExecute(stage);
		}
		// The target stays loaded until the last stage, or its mobs would unload with it.
		sequence.thenExecute(() -> release(helper, fixture.bell(), target)).thenSucceed();
	}

	private static boolean reachable(final GameTestHelper helper, final MissionRecord accepted) {
		MissionRecord mission = questMission(helper, accepted.id());
		if (mission == null || mission.status() != MissionStatus.ACTIVE) {
			return false;
		}
		Mob boss = mission.entityId().map(id -> mobOf(helper, id)).orElse(null);
		if (mission.entityId().isPresent() && boss == null) {
			return false;
		}
		NemesisState state = boss == null ? null : NemesisState.get(boss);
		if (state != null && state.companionId().isPresent() && mobOf(helper, state.companionId().get()) == null) {
			return false;
		}
		boolean escortsHere = mission.mobIds().stream().allMatch(id -> mobOf(helper, id) != null);
		return escortsHere && (mission.kind() == MissionKind.NEMESIS || !mission.mobIds().isEmpty());
	}

	private static @Nullable Fixture fixture(final GameTestHelper helper, final String test) {
		ServerLevel level = helper.getLevel();
		BlockPos anchor = PatrolTestSupport.findDistantVillageAnchor(helper, test);
		if (anchor == null) {
			return null;
		}
		level.setChunkForced(anchor.getX() >> 4, anchor.getZ() >> 4, true);
		level.getChunkAt(anchor);
		helper.setBlock(4, 0, 4, Blocks.STONE);
		Mob head = helper.spawn(EntityTypes.VINDICATOR, 4, 1, 4);
		head.setNoAi(true);
		head.setPersistenceRequired();
		head.snapTo(Vec3.atBottomCenterOf(anchor), 0.0F, 0.0F);
		var data = VillageWorldData.get(level);
		var village = data.findOrCreate(level.dimension(), anchor);
		PatrolState.attach(head, village.id(), PatrolState.Role.HEAD);
		data.addMember(village, head.getUUID(), PatrolState.Role.HEAD.name(), anchor);
		ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(head, level);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		move(owner, Vec3.atBottomCenterOf(anchor.offset(1, 0, 0)));
		return new Fixture(owner, head, PatrolService.bellOf(level, village));
	}

	private static void move(final ServerPlayer player, final Vec3 position) {
		player.connection.teleport(position.x, position.y, position.z, 0.0F, 0.0F);
		if (player.level() instanceof ServerLevel level) {
			level.getChunkSource().move(player);
		}
	}

	private static List<MissionOffer> openBoard(final GameTestHelper helper, final Fixture fixture) {
		UseEntityCallback.EVENT.invoker().interact(fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null);
		return MissionService.offersFor(fixture.owner().getUUID());
	}

	/** Opens the board, accepts its first contract of a kind, and returns the mission that made. */
	private static MissionRecord accept(final GameTestHelper helper, final Fixture fixture, final MissionKind kind) {
		MissionOffer offer = openBoard(helper, fixture).stream().filter(candidate -> candidate.kind() == kind).findFirst().orElse(null);
		helper.assertTrue(offer != null, "the board has no " + kind + " offer");
		List<UUID> before = missionsOf(helper, fixture.owner()).stream().map(MissionRecord::id).toList();
		boolean accepted = MissionService.acceptOffer(fixture.owner(), fixture.head().getUUID(), offer.id());
		helper.assertTrue(accepted, "the " + kind + " offer was refused: " + MissionService.lastRejection(fixture.owner().getUUID()));
		MissionRecord mission = missionsOf(helper, fixture.owner()).stream().filter(record -> !before.contains(record.id())).findFirst().orElse(null);
		helper.assertTrue(mission != null && mission.kind() == kind, "accepting the " + kind + " offer made " + mission);
		return mission;
	}

	private static List<MissionRecord> missionsOf(final GameTestHelper helper, final ServerPlayer owner) {
		return VillageWorldData.get(helper.getLevel()).missions().stream().filter(mission -> mission.ownerId().equals(owner.getUUID())).toList();
	}

	private static @Nullable MissionRecord questMission(final GameTestHelper helper, final UUID id) {
		return VillageWorldData.get(helper.getLevel()).mission(id).orElse(null);
	}

	private static @Nullable Mob mobOf(final GameTestHelper helper, final @Nullable UUID id) {
		return id != null && helper.getLevel().getEntity(id) instanceof Mob mob ? mob : null;
	}

	/** A safe platform where the search will look first, and a search that looks nowhere else. */
	private static BlockPos prepareSite(final GameTestHelper helper, final BlockPos bell) {
		ServerLevel level = helper.getLevel();
		BlockPos target = bell.offset(MissionService.MISSION_MIN_DISTANCE, 0, 0);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setChunkForced((target.getX() >> 4) + dx, (target.getZ() >> 4) + dz, true);
			}
		}
		level.getChunkAt(target);
		for (int dx = -SITE_RADIUS; dx <= SITE_RADIUS; dx++) {
			for (int dz = -SITE_RADIUS; dz <= SITE_RADIUS; dz++) {
				BlockPos floor = target.offset(dx, -1, dz);
				level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				for (int dy = 0; dy < 4; dy++) {
					level.setBlockAndUpdate(floor.above(dy + 1), Blocks.AIR.defaultBlockState());
				}
			}
		}
		MissionService.setTargetCandidateSource(bell, (origin, attempt, random) -> origin.offset(MissionService.MISSION_MIN_DISTANCE, 0, 0));
		return target;
	}

	private static void release(final GameTestHelper helper, final BlockPos bell, final BlockPos target) {
		MissionService.setTargetCandidateSource(bell, null);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.getLevel().setChunkForced((target.getX() >> 4) + dx, (target.getZ() >> 4) + dz, false);
			}
		}
	}

	/** Runs a case with a board that draws from one kind only, and gives the shared pool back after. */
	private static void withPool(final List<Identifier> pool, final Runnable body) {
		MissionService.setOfferPool(pool);
		try {
			body.run();
		} finally {
			MissionService.setOfferPool(POOL);
		}
	}

	private static void kill(final GameTestHelper helper, final ServerPlayer killer, final Mob mob) {
		mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), mob.getMaxHealth() * 4.0F);
	}

	private static boolean near(final Mob mob, final BlockPos target, final double blocks) {
		double dx = mob.getX() - (target.getX() + 0.5);
		double dz = mob.getZ() - (target.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz) <= blocks;
	}

	private static int drops(final GameTestHelper helper, final BlockPos around, final Item item) {
		int found = 0;
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(SITE_RADIUS + 2.0))) {
			if (drop.getItem().is(item)) {
				found += drop.getItem().getCount();
			}
		}
		return found;
	}

	private static void discardAll(final Mob elite, final List<Mob> escorts) {
		elite.discard();
		escorts.forEach(Entity::discard);
	}

	private static double horizontalSquared(final BlockPos first, final BlockPos second) {
		double dx = first.getX() - second.getX();
		double dz = first.getZ() - second.getZ();
		return dx * dx + dz * dz;
	}

	private static ServerPlayer login(final GameTestHelper helper, final UUID id) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "quest-returner"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}
}
