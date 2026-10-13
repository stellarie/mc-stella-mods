package com.stella.villages;

import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.scaling.ThreatState;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionKind;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import com.stella.villages.data.VillageWorldData.MissionStatus;
import com.stella.villages.mission.GuardQuests;
import com.stella.villages.mission.JobKits;
import com.stella.villages.mission.JobTrainer;
import com.stella.villages.mission.JobTrials;
import com.stella.villages.mission.MissionService;
import com.stella.villages.mission.RpgJobs;
import com.stella.villages.mission.TrainerMenu;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The job trainer: a guard that stays beside the duke, the job menu at level 10, the kit of each job, and the boss
 * trial from level 40. The rpg mod is not loaded in this suite, so the tests put a stand-in into the object share
 * and take it out again.
 */
public final class TrainerGameTest {
	private static final int TEST_TICKS = 600;
	/** The roster version before the trainer. */
	private static final int ROSTER_VERSION_WITHOUT_TRAINER = 4;
	private static final int SITE_RADIUS = 8;
	private static final Set<String> TRAINER_GOALS = Set.of("TrainerStayGoal", "FloatGoal", "LookAtPlayerGoal");
	private static final Identifier UNBREAKING = Identifier.parse("minecraft:unbreaking");
	private static final Map<String, Item> JOB_ITEMS = Map.of(
		"archer", Items.BOW, "fighter", Items.IRON_SWORD, "mage", Items.DIAMOND_HELMET, "priest", Items.IRON_CHESTPLATE
	);
	/** Stand-ins for the spells mod's books, each a different item. */
	private static final Map<Identifier, Item> BOOKS = Map.of(
		JobKits.SPARK_SHOT, Items.BLAZE_ROD,
		JobKits.HEAL_BOOK, Items.GHAST_TEAR,
		JobKits.BUFF_BOOK, Items.SHIELD,
		JobKits.SUMMON_SKELETON, Items.BONE
	);
	private static final List<Item> BOOK_POOL = List.of(Items.BONE, Items.BLAZE_ROD, Items.GHAST_TEAR, Items.SHIELD, Items.FEATHER, Items.PAPER);
	/** Test seam: what the trainer said to each player, and each choice and trial request. */
	private static final Map<UUID, List<String>> SAID = new ConcurrentHashMap<>();
	private static final Map<UUID, List<String>> CHOICES = new ConcurrentHashMap<>();

	static {
		JobTrainer.setRecorder(new JobTrainer.Recorder() {
			@Override
			public void said(final ServerPlayer player, final String text) {
				SAID.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add(text);
			}

			@Override
			public void chose(final ServerPlayer player, final String job, final boolean accepted, final String reason) {
				CHOICES.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add("job:" + job + ":" + reason);
			}

			@Override
			public void trial(final ServerPlayer player, final int level, final boolean accepted, final String reason) {
				CHOICES.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add("trial:" + level + ":" + reason);
			}
		});
	}

	/** A stand-in for the rpg mod's share, with the rules of its job core. Tests of one batch share one copy. */
	private static final class StandIn {
		private static final Map<UUID, String> JOBS = new ConcurrentHashMap<>();
		private static final Map<UUID, Set<Integer>> TRIALS = new ConcurrentHashMap<>();
		private static final List<String> CALLS = new CopyOnWriteArrayList<>();
		private static int users;

		static synchronized void acquire() {
			if (users++ == 0) {
				FabricLoader.getInstance().getObjectShare().put(RpgJobs.SHARE_KEY, share());
			}
		}

		static synchronized void release() {
			if (--users <= 0) {
				clear();
			}
		}

		/** Takes the stand-in out whatever its users, so a test that must see no rpg mod sees none. */
		static synchronized void clear() {
			users = 0;
			FabricLoader.getInstance().getObjectShare().remove(RpgJobs.SHARE_KEY);
		}

		static String job(final ServerPlayer player) {
			return JOBS.getOrDefault(player.getUUID(), "none");
		}

		static Set<Integer> done(final ServerPlayer player) {
			return TRIALS.computeIfAbsent(player.getUUID(), key -> ConcurrentHashMap.newKeySet());
		}

		static boolean called(final ServerPlayer player, final String call) {
			return CALLS.contains(player.getUUID() + ":" + call);
		}

		private static Map<String, Object> share() {
			Function<ServerPlayer, String> get = StandIn::job;
			BiFunction<ServerPlayer, String, Boolean> choose = (player, job) -> {
				CALLS.add(player.getUUID() + ":choose:" + job);
				if (player.experienceLevel < RpgJobs.MIN_LEVEL || !job(player).equals("none") || !JOB_ITEMS.containsKey(job)) {
					return false;
				}
				JOBS.put(player.getUUID(), job);
				return true;
			};
			Function<ServerPlayer, Boolean> grantItems = player -> {
				CALLS.add(player.getUUID() + ":grantItems");
				Item item = JOB_ITEMS.get(job(player));
				return item != null && player.getInventory().add(new ItemStack(item));
			};
			BiFunction<ServerPlayer, Integer, Boolean> grantTrial = (player, level) -> {
				CALLS.add(player.getUUID() + ":grantTrial:" + level);
				return !job(player).equals("none") && RpgJobs.isTrialLevel(level) && done(player).add(level);
			};
			Function<ServerPlayer, Set<Integer>> trialsDone = player -> Set.copyOf(done(player));
			return Map.of("get", get, "choose", choose, "grantItems", grantItems, "grantTrial", grantTrial, "trialsDone", trialsDone);
		}
	}

	private record Fixture(ServerPlayer owner, Mob head, Mob trainer, BlockPos bell) {
	}

	@GameTest(maxTicks = 400, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void trainerStandsByTheDuke(final GameTestHelper helper) {
		String test = "trainerStandsByTheDuke";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		List<Mob> trainers = membersOf(helper, PatrolState.Role.TRAINER);
		Mob head = membersOf(helper, PatrolState.Role.HEAD).getFirst();
		helper.assertValueEqual(Integer.valueOf(trainers.size()), Integer.valueOf(PatrolService.TRAINER_COUNT), "trainers of a new village");
		Mob trainer = trainers.getFirst();
		double spawned = trainer.distanceTo(head);
		helper.assertTrue(spawned <= 3.0, "the trainer spawned " + spawned + " blocks from the duke");
		// The duke walks off, and the trainer follows it.
		head.setNoAi(true);
		Vec3 away = helper.absoluteVec(new Vec3(7.5, 1.0, 2.5));
		head.teleportTo(away.x, away.y, away.z);
		helper.assertTrue(trainer.distanceTo(head) > 3.0, "the duke moved only " + trainer.distanceTo(head) + " blocks from the trainer, so the case proves nothing");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(trainer.distanceTo(head) <= 3.0, "the trainer is " + trainer.distanceTo(head) + " blocks from the duke"))
			.thenIdle(40)
			.thenExecute(() -> {
				double stayed = trainer.distanceTo(head);
				PatrolTestSupport.result(test, "spawned=" + spawned + " stayed=" + stayed + " duke=" + head.blockPosition() + " trainer=" + trainer.blockPosition());
				helper.assertTrue(stayed <= 3.0, "the trainer drifted " + stayed + " blocks from the duke");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void trainerNeverFights(final GameTestHelper helper) {
		String test = "trainerNeverFights";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		Mob trainer = membersOf(helper, PatrolState.Role.TRAINER).getFirst();
		Mob zombie = helper.spawn(EntityTypes.ZOMBIE, 5, 2, 5);
		zombie.setNoAi(true);
		// The mobs mod adds its fighting goals after the spawn, so the check waits for a sweep.
		helper.startSequence()
			.thenExecuteAfter(10, () -> {
				Set<String> goals = goalsOf(trainer);
				PatrolTestSupport.result(test, "goals=" + goals + " target=" + trainer.getTarget());
				helper.assertTrue(TRAINER_GOALS.containsAll(goals) && goals.contains("TrainerStayGoal"), "the trainer's goals are " + goals);
				helper.assertTrue(trainer.getTarget() == null, "the trainer has a target");
				helper.assertTrue(!GuardQuests.isGuard(trainer), "the trainer offers the guard quest");
				// A goal added later and a target forced on it both leave on the next sweep.
				trainer.getGoalSelector().addGoal(1, new MeleeAttackGoal((net.minecraft.world.entity.PathfinderMob) trainer, 1.0, true));
				trainer.setTarget(zombie);
			})
			.thenExecuteAfter(10, () -> {
				Set<String> after = goalsOf(trainer);
				PatrolTestSupport.result(test, "goalsAfter=" + after + " target=" + trainer.getTarget());
				helper.assertTrue(TRAINER_GOALS.containsAll(after), "the trainer kept a foreign goal: " + after);
				helper.assertTrue(trainer.getTarget() == null, "the trainer kept its target");
			})
			.thenSucceed();
	}

	/** A boss evoker survives its first lethal hit, so the test marks that spared life as used. */
	private static void killBoss(final GameTestHelper helper, final Mob boss, final net.minecraft.world.damagesource.DamageSource source) {
		EliteState.setSecondPhaseUsed(boss, true);
		boss.hurtServer(helper.getLevel(), source, 1000000.0F);
	}

	private static Set<String> goalsOf(final Mob mob) {
		Set<String> goals = new HashSet<>();
		mob.getGoalSelector().getAvailableGoals().forEach(wrapped -> goals.add(wrapped.getGoal().getClass().getSimpleName()));
		return goals;
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void rosterSealedBeforeTheTrainerReopensOnLoad(final GameTestHelper helper) {
		VillageWorldData data = VillageWorldData.empty();
		VillageWorldData.VillageRecord village = data.findOrCreate(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 1, 3)));
		List<PatrolState.Role> roles = List.of(PatrolState.Role.HEAD, PatrolState.Role.PILLAGER, PatrolState.Role.PILLAGER,
			PatrolState.Role.RANGED_GUARD, PatrolState.Role.RANGED_GUARD, PatrolState.Role.VILLAGE_WITCH);
		for (PatrolState.Role role : roles) {
			data.addMember(village, UUID.randomUUID(), role.name(), helper.absolutePos(new BlockPos(4, 1, 4)));
		}
		data.markRosterStarted(village);
		CompoundTag saved = data.serializeState();
		int previous = ROSTER_VERSION_WITHOUT_TRAINER;
		helper.assertTrue(VillageWorldData.ROSTER_VERSION > previous, "the roster version " + VillageWorldData.ROSTER_VERSION + " did not move past " + previous);
		for (Tag entry : saved.getListOrEmpty("villages")) {
			if (entry instanceof CompoundTag villageTag) {
				villageTag.putInt("roster_version", previous);
			}
		}
		VillageWorldData.VillageRecord loaded = VillageWorldData.loadState(saved).village(village.id()).orElseThrow();
		PatrolTestSupport.result("rosterSealedBeforeTheTrainerReopensOnLoad", "version=" + previous + " rosterStartedAfterLoad=" + loaded.rosterStarted());
		helper.assertFalse(loaded.rosterStarted(), "a roster sealed before the trainer stayed sealed, so the trainer can never be added");
		helper.assertValueEqual(Integer.valueOf(loaded.members().size()), Integer.valueOf(roles.size()), "members kept by the migration");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void oldVillageGainsATrainer(final GameTestHelper helper) {
		String test = "oldVillageGainsATrainer";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		VillageWorldData data = VillageWorldData.get(helper.getLevel());
		Mob trainer = membersOf(helper, PatrolState.Role.TRAINER).getFirst();
		int others = PatrolTestSupport.patrols(helper).size() - 1;
		data.removeMember(trainer.getUUID());
		trainer.discard();
		helper.assertTrue(membersOf(helper, PatrolState.Role.TRAINER).isEmpty(), "the old village still has a trainer");
		if (!PatrolTestSupport.setRosterStarted(helper, test, false) || !PatrolTestSupport.scanChunk(helper, test)) {
			return;
		}
		List<Mob> grown = membersOf(helper, PatrolState.Role.TRAINER);
		PatrolTestSupport.result(test, "trainers=" + grown.size() + " others=" + others + " members=" + PatrolTestSupport.patrols(helper).size());
		helper.assertValueEqual(Integer.valueOf(grown.size()), Integer.valueOf(1), "trainers after the patrol check");
		helper.assertValueEqual(Integer.valueOf(PatrolTestSupport.patrols(helper).size()), Integer.valueOf(others + 1), "members after the patrol check");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void chooseGrantsTheKit(final GameTestHelper helper) {
		Fixture fixture = fixture(helper, "chooseGrantsTheKit");
		if (fixture == null) {
			return;
		}
		StandIn.acquire();
		try {
			ServerPlayer mage = fixture.owner();
			mage.experienceLevel = RpgJobs.MIN_LEVEL;
			ServerPlayer priest = nearby(helper, fixture);
			priest.experienceLevel = RpgJobs.MIN_LEVEL;
			withBooks(() -> {
				click(helper, fixture, mage, TrainerMenu.JOB_SLOTS[2]);
				click(helper, fixture, priest, TrainerMenu.JOB_SLOTS[3]);
			});
			List<Item> mageBooks = books(mage);
			List<Item> priestBooks = books(priest);
			PatrolTestSupport.result("chooseGrantsTheKit", "mage=" + StandIn.job(mage) + " mageBooks=" + mageBooks
				+ " priest=" + StandIn.job(priest) + " priestBooks=" + priestBooks);
			helper.assertTrue(StandIn.job(mage).equals("mage") && StandIn.job(priest).equals("priest"), "the jobs are " + StandIn.job(mage) + " and " + StandIn.job(priest));
			helper.assertTrue(StandIn.called(mage, "grantItems") && mage.getInventory().countItem(Items.DIAMOND_HELMET) == 1, "the mage got no job item");
			helper.assertTrue(priest.getInventory().countItem(Items.IRON_CHESTPLATE) == 1, "the priest got no job item");
			helper.assertTrue(mageBooks.size() == 2 && mageBooks.contains(Items.BLAZE_ROD) && !mageBooks.contains(Items.BONE), "the mage books are " + mageBooks);
			helper.assertTrue(priestBooks.size() == 2 && priestBooks.containsAll(List.of(Items.GHAST_TEAR, Items.SHIELD)), "the priest books are " + priestBooks);
			helper.assertTrue(mage.containerMenu == mage.inventoryMenu, "the menu stayed open after the choice");
			helper.succeed();
		} finally {
			StandIn.release();
		}
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void mageNeverGetsTheSkeletonBook(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int summons = 0;
		int drawn = 0;
		JobKits.setItemLookup(id -> Optional.ofNullable(BOOKS.get(id)));
		MissionService.setSpellBookPool(BOOK_POOL);
		try {
			for (int roll = 0; roll < 200; roll++) {
				for (ItemStack book : JobKits.books(level, "mage", level.getRandom())) {
					drawn++;
					summons += book.is(Items.BONE) ? 1 : 0;
				}
			}
		} finally {
			JobKits.setItemLookup(null);
			MissionService.setSpellBookPool(null);
		}
		PatrolTestSupport.result("mageNeverGetsTheSkeletonBook", "drawn=" + drawn + " summons=" + summons);
		helper.assertTrue(drawn == 400 && summons == 0, "drawn " + drawn + " books, " + summons + " were Summon Skeleton");
		helper.succeed();
	}

	/** With the spells mod loaded the books are the real ones; without it a job gets its own item only. */
	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void spellsBridgeLoadedOrAbsent(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean loaded = BuiltInRegistries.ITEM.containsKey(JobKits.SPARK_SHOT);
		List<ItemStack> mage = JobKits.books(level, "mage", level.getRandom());
		List<ItemStack> priest = JobKits.books(level, "priest", level.getRandom());
		PatrolTestSupport.result("spellsBridgeLoadedOrAbsent", "loaded=" + loaded + " mage=" + mage + " priest=" + priest);
		if (!loaded) {
			helper.assertTrue(mage.isEmpty() && priest.isEmpty(), "books appeared without the spells mod: " + mage + " " + priest);
			helper.succeed();
			return;
		}
		Item spark = BuiltInRegistries.ITEM.getValue(JobKits.SPARK_SHOT);
		helper.assertTrue(mage.size() == 2 && mage.getFirst().is(spark), "the mage books are " + mage);
		helper.assertTrue(mage.stream().noneMatch(book -> BuiltInRegistries.ITEM.getKey(book.getItem()).equals(JobKits.SUMMON_SKELETON)), "the mage got Summon Skeleton");
		helper.assertTrue(priest.size() == 2 && BuiltInRegistries.ITEM.getKey(priest.get(0).getItem()).equals(JobKits.HEAL_BOOK)
			&& BuiltInRegistries.ITEM.getKey(priest.get(1).getItem()).equals(JobKits.BUFF_BOOK), "the priest books are " + priest);
		for (ItemStack book : List.of(mage.getFirst(), mage.get(1), priest.get(0), priest.get(1))) {
			helper.assertTrue(book.getEnchantments().keySet().stream().anyMatch(holder -> holder.is(JobKits.DOUBLECAST)), "a book lacks Doublecast: " + book);
		}
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void refusesBelowTenAndTwice(final GameTestHelper helper) {
		Fixture fixture = fixture(helper, "refusesBelowTenAndTwice");
		if (fixture == null) {
			return;
		}
		StandIn.acquire();
		try {
			ServerPlayer player = fixture.owner();
			player.experienceLevel = RpgJobs.MIN_LEVEL - 1;
			TrainerMenu menu = open(helper, fixture, player);
			boolean reasonShown = menu.getContainer().getItem(TrainerMenu.INFO_SLOT).is(Items.BARRIER)
				&& menu.getContainer().getItem(TrainerMenu.INFO_SLOT).get(DataComponents.LORE).lines().stream()
					.anyMatch(line -> line.getString().contains("level " + RpgJobs.MIN_LEVEL));
			boolean noJobIcons = TrainerMenu.JOB_SLOTS.length == 4 && java.util.Arrays.stream(TrainerMenu.JOB_SLOTS)
				.allMatch(slot -> menu.getContainer().getItem(slot).is(Items.STAINED_GLASS_PANE.gray()));
			menu.clicked(TrainerMenu.JOB_SLOTS[0], 0, ContainerInput.PICKUP, player);
			boolean forced = JobTrainer.choose(player, "archer");
			String below = StandIn.job(player) + " items=" + inventoryCount(player) + " forced=" + forced;
			player.closeContainer();

			player.experienceLevel = RpgJobs.MIN_LEVEL;
			click(helper, fixture, player, TrainerMenu.JOB_SLOTS[0]);
			int itemsAfterChoice = inventoryCount(player);
			TrainerMenu again = open(helper, fixture, player);
			boolean ownJobShown = again.getContainer().getItem(TrainerMenu.INFO_SLOT).get(DataComponents.ITEM_NAME).getString().contains("archer");
			boolean second = JobTrainer.choose(player, "fighter");
			PatrolTestSupport.result("refusesBelowTenAndTwice", "below=" + below + " reasonShown=" + reasonShown + " noJobIcons=" + noJobIcons
				+ " job=" + StandIn.job(player) + " second=" + second + " choices=" + CHOICES.get(player.getUUID()));
			helper.assertTrue(reasonShown && noJobIcons, "the menu below level ten shows reason=" + reasonShown + " noJobIcons=" + noJobIcons);
			helper.assertTrue(below.equals("none items=0 forced=false"), "a player below level ten changed state: " + below);
			helper.assertTrue(StandIn.job(player).equals("archer") && itemsAfterChoice == 1, "the first choice left job=" + StandIn.job(player) + " items=" + itemsAfterChoice);
			helper.assertTrue(ownJobShown && !second && StandIn.job(player).equals("archer") && inventoryCount(player) == itemsAfterChoice,
				"a second choice changed state: second=" + second + " job=" + StandIn.job(player) + " items=" + inventoryCount(player));
			helper.assertTrue(CHOICES.get(player.getUUID()).containsAll(List.of("job:archer:level", "job:fighter:has_job")), "the refusals were " + CHOICES.get(player.getUUID()));
			helper.succeed();
		} finally {
			StandIn.release();
		}
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void trialRewardsOnKill(final GameTestHelper helper) {
		Fixture fixture = fixture(helper, "trialRewardsOnKill");
		if (fixture == null) {
			return;
		}
		StandIn.acquire();
		BlockPos target = prepareSite(helper, fixture.bell());
		ServerPlayer owner = fixture.owner();
		owner.experienceLevel = 40;
		StandIn.JOBS.put(owner.getUUID(), "archer");
		TrainerMenu menu = open(helper, fixture, owner);
		boolean offered = menu.getContainer().getItem(TrainerMenu.TRIAL_SLOT).is(Items.SKELETON_SKULL);
		menu.clicked(TrainerMenu.TRIAL_SLOT, 0, ContainerInput.PICKUP, owner);
		MissionRecord accepted = missionsOf(helper, owner).stream().filter(mission -> mission.kind() == MissionKind.TRIAL).findFirst().orElse(null);
		helper.assertTrue(offered && accepted != null, "the trial was offered=" + offered + " accepted=" + accepted);
		helper.assertTrue(accepted.remaining() == 40 && accepted.targetPosition().equals(target), "the trial mission is " + accepted);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(reachable(helper, accepted), "the trial boss is not loaded yet"))
			.thenExecute(() -> {
				MissionRecord mission = mission(helper, accepted.id());
				Mob boss = mobOf(helper, mission.entityId().orElse(null));
				ThreatState state = ThreatState.get(boss);
				double bellDistance = Math.sqrt(boss.blockPosition().distSqr(fixture.bell()));
				owner.setGameMode(GameType.SURVIVAL);
				killBoss(helper, boss, helper.getLevel().damageSources().playerAttack(owner));
				boolean granted = StandIn.called(owner, "grantTrial:40") && StandIn.done(owner).contains(40);
				boolean closed = mission(helper, accepted.id()) == null;
				owner.experienceLevel = 69;
				OptionalInt at69 = JobTrials.offer(owner);
				owner.experienceLevel = 70;
				OptionalInt at70 = JobTrials.offer(owner);
				PatrolTestSupport.result("trialRewardsOnKill", "boss=" + boss.getType() + " isBoss=" + Bosses.isBoss(boss) + " state=" + state
					+ " bellDistance=" + bellDistance + " granted=" + granted + " closed=" + closed + " at69=" + at69 + " at70=" + at70);
				helper.assertTrue(Bosses.isBoss(boss) && state != null && state.areaLevel() == 40, "the trial boss is " + state + ", boss " + Bosses.isBoss(boss));
				helper.assertTrue(bellDistance >= JobTrials.MIN_DISTANCE && bellDistance <= JobTrials.MAX_DISTANCE + 2, "the boss stands " + bellDistance + " blocks from the bell");
				helper.assertTrue(granted && closed, "the kill granted=" + granted + " closed=" + closed);
				helper.assertTrue(at69.isEmpty() && at70.orElse(0) == 70, "the next trial is at69=" + at69 + " at70=" + at70);
			})
			.thenExecute(() -> {
				release(helper, fixture.bell(), target);
				StandIn.release();
			})
			.thenSucceed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void trialLostWithoutAPlayerKillCanBeRetaken(final GameTestHelper helper) {
		Fixture fixture = fixture(helper, "trialLostWithoutAPlayerKillCanBeRetaken");
		if (fixture == null) {
			return;
		}
		StandIn.acquire();
		BlockPos target = prepareSite(helper, fixture.bell());
		ServerPlayer owner = fixture.owner();
		owner.experienceLevel = 40;
		StandIn.JOBS.put(owner.getUUID(), "fighter");
		helper.assertTrue(JobTrials.accept(owner, fixture.trainer()), "the trial was refused");
		MissionRecord first = missionsOf(helper, owner).stream().filter(mission -> mission.kind() == MissionKind.TRIAL).findFirst().orElse(null);
		helper.assertTrue(first != null, "the trial made no mission");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(reachable(helper, first), "the trial boss is not loaded yet"))
			.thenExecute(() -> {
				Mob boss = mobOf(helper, mission(helper, first.id()).entityId().orElse(null));
				killBoss(helper, boss, helper.getLevel().damageSources().genericKill());
				boolean granted = StandIn.called(owner, "grantTrial:40");
				boolean closed = mission(helper, first.id()) == null;
				boolean again = JobTrials.offer(owner).orElse(0) == 40 && JobTrials.accept(owner, fixture.trainer());
				long open = missionsOf(helper, owner).stream().filter(mission -> mission.kind() == MissionKind.TRIAL).count();
				PatrolTestSupport.result("trialLostWithoutAPlayerKillCanBeRetaken", "granted=" + granted + " closed=" + closed + " again=" + again + " open=" + open);
				helper.assertTrue(!granted && closed, "a boss lost to a fall granted=" + granted + " closed=" + closed);
				helper.assertTrue(again && open == 1, "the trial was not retaken: again=" + again + " open=" + open);
				missionsOf(helper, owner).forEach(mission -> VillageWorldData.get(helper.getLevel()).removeMission(mission.id()));
			})
			.thenExecute(() -> {
				release(helper, fixture.bell(), target);
				StandIn.release();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void noRpgNoJobs(final GameTestHelper helper) {
		StandIn.clear();
		Mob trainer = helper.spawn(EntityTypes.PILLAGER, 4, 2, 4);
		trainer.setNoAi(true);
		trainer.setPersistenceRequired();
		PatrolState.attach(trainer, UUID.randomUUID(), PatrolState.Role.TRAINER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		move(player, trainer.position().add(1.0, 0.0, 0.0));
		player.experienceLevel = 50;
		SAID.remove(player.getUUID());
		InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, trainer, null);
		boolean chose = JobTrainer.choose(player, "mage");
		PatrolTestSupport.result("noRpgNoJobs", "result=" + result + " said=" + SAID.get(player.getUUID()) + " chose=" + chose
			+ " refusal=" + JobTrainer.chooseRefusal(player) + " trial=" + JobTrials.offer(player));
		helper.assertTrue(result == InteractionResult.SUCCESS, "the trainer did not answer: " + result);
		helper.assertTrue(player.containerMenu == player.inventoryMenu, "a menu opened without the rpg mod");
		helper.assertTrue(SAID.getOrDefault(player.getUUID(), List.of()).stream().anyMatch(text -> text.contains("unavailable")),
			"the trainer said " + SAID.get(player.getUUID()));
		helper.assertTrue(!chose && "unavailable".equals(JobTrainer.chooseRefusal(player)) && JobTrials.offer(player).isEmpty()
			&& inventoryCount(player) == 0, "a choice or a trial worked without the rpg mod");
		helper.succeed();
	}

	/** A village whose head and trainer stand at a distant anchor, and an owner beside them. */
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
		Mob trainer = helper.spawn(EntityTypes.PILLAGER, 4, 1, 4);
		trainer.setNoAi(true);
		trainer.setPersistenceRequired();
		trainer.snapTo(Vec3.atBottomCenterOf(anchor).add(1.0, 0.0, 0.0), 0.0F, 0.0F);
		VillageWorldData data = VillageWorldData.get(level);
		VillageWorldData.VillageRecord village = data.findOrCreate(level.dimension(), anchor);
		PatrolState.attach(head, village.id(), PatrolState.Role.HEAD);
		data.addMember(village, head.getUUID(), PatrolState.Role.HEAD.name(), anchor);
		PatrolState.attach(trainer, village.id(), PatrolState.Role.TRAINER);
		data.addMember(village, trainer.getUUID(), PatrolState.Role.TRAINER.name(), anchor);
		ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(head, level);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		move(owner, Vec3.atBottomCenterOf(anchor.offset(2, 0, 0)));
		return new Fixture(owner, head, trainer, PatrolService.bellOf(level, village));
	}

	private static ServerPlayer nearby(final GameTestHelper helper, final Fixture fixture) {
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		move(other, fixture.owner().position());
		return other;
	}

	private static void move(final ServerPlayer player, final Vec3 position) {
		player.connection.teleport(position.x, position.y, position.z, 0.0F, 0.0F);
		if (player.level() instanceof ServerLevel level) {
			level.getChunkSource().move(player);
		}
	}

	private static TrainerMenu open(final GameTestHelper helper, final Fixture fixture, final ServerPlayer player) {
		InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, fixture.trainer(), null);
		helper.assertTrue(result == InteractionResult.SUCCESS && player.containerMenu instanceof TrainerMenu, "the trainer opened " + player.containerMenu);
		return (TrainerMenu) player.containerMenu;
	}

	private static void click(final GameTestHelper helper, final Fixture fixture, final ServerPlayer player, final int slot) {
		open(helper, fixture, player).clicked(slot, 0, ContainerInput.PICKUP, player);
	}

	private static void withBooks(final Runnable body) {
		JobKits.setItemLookup(id -> Optional.ofNullable(BOOKS.get(id)));
		JobKits.setBookEnchantment(UNBREAKING);
		MissionService.setSpellBookPool(BOOK_POOL);
		try {
			body.run();
		} finally {
			JobKits.setItemLookup(null);
			JobKits.setBookEnchantment(null);
			MissionService.setSpellBookPool(null);
		}
	}

	/** The enchanted stacks of the inventory: the books, which carry the stand-in for Doublecast. */
	private static List<Item> books(final ServerPlayer player) {
		List<Item> books = new ArrayList<>();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && stack.getEnchantments().keySet().stream().anyMatch(holder -> holder.is(UNBREAKING))) {
				for (int copy = 0; copy < stack.getCount(); copy++) {
					books.add(stack.getItem());
				}
			}
		}
		return books;
	}

	private static int inventoryCount(final ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			count += player.getInventory().getItem(slot).isEmpty() ? 0 : 1;
		}
		return count;
	}

	private static List<Mob> membersOf(final GameTestHelper helper, final PatrolState.Role role) {
		return PatrolTestSupport.patrols(helper).stream().filter(mob -> PatrolState.role(mob) == role).toList();
	}

	private static List<MissionRecord> missionsOf(final GameTestHelper helper, final ServerPlayer owner) {
		return VillageWorldData.get(helper.getLevel()).missions().stream().filter(mission -> mission.ownerId().equals(owner.getUUID())).toList();
	}

	private static @Nullable MissionRecord mission(final GameTestHelper helper, final UUID id) {
		return VillageWorldData.get(helper.getLevel()).mission(id).orElse(null);
	}

	private static @Nullable Mob mobOf(final GameTestHelper helper, final @Nullable UUID id) {
		return id != null && helper.getLevel().getEntity(id) instanceof Mob mob ? mob : null;
	}

	private static boolean reachable(final GameTestHelper helper, final MissionRecord accepted) {
		MissionRecord mission = mission(helper, accepted.id());
		return mission != null && mission.status() == MissionStatus.ACTIVE && mission.entityId().map(id -> mobOf(helper, id) != null).orElse(false);
	}

	/** A safe platform where the search will look first, and a search that looks nowhere else. */
	private static BlockPos prepareSite(final GameTestHelper helper, final BlockPos bell) {
		ServerLevel level = helper.getLevel();
		BlockPos target = bell.offset(JobTrials.MAX_DISTANCE, 0, 0);
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
		MissionService.setTargetCandidateSource(bell, (origin, attempt, random) -> origin.offset(JobTrials.MAX_DISTANCE, 0, 0));
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
}
