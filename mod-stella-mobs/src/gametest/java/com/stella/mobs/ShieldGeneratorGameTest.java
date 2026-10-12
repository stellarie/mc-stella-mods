package com.stella.mobs;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.WitherEncounter;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.shield.ShieldBlocks;
import com.stella.mobs.shield.ShieldConfig;
import com.stella.mobs.shield.ShieldDrops;
import com.stella.mobs.shield.ShieldGeneratorBlockEntity;
import com.stella.mobs.shield.ShieldZones;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Shield Generator keeps hostile mobs out of a 33 block box and keeps them from aggro inside it. Every case runs on
 * the wide arena, with the generator at {@link #GEN_X}, {@link #GEN_Y}, {@link #GEN_Z}. Each case that proves a refusal
 * also proves the same act succeeds outside the box or after the generator breaks, so a green case cannot come from a
 * mob that never would have acted.
 */
public class ShieldGeneratorGameTest {
	private static final String TEST = "shieldGenerator";
	private static final int GEN_X = 24;
	private static final int GEN_Y = 1;
	private static final int GEN_Z = 28;
	private static final int HALF = ShieldZones.HALF_SIZE;
	private static final int INSIDE_X = GEN_X + 2;
	private static final int LAST_INSIDE_X = GEN_X + HALF;
	private static final int FIRST_OUTSIDE_X = GEN_X + HALF + 1;
	private static final int OUTSIDE_X = GEN_X + HALF + 4;
	private static final int READY_TICKS = StanceGameTest.READY_TICKS;
	private static final int SHIELD_WATCH_TICKS = 100;
	private static final int ACQUIRE_TICKS = 200;
	private static final int SPAWNER_TICKS = 200;
	private static final int SPARKLE_WINDOW_TICKS = 40;
	private static final int ROLL_SAMPLE = 1_000_000;
	private static final long ROLL_SEED = 20261011L;
	private static final double ROLL_SIGMAS = 5.0;
	private static final String PROMOTE_ELITE = """
		{ "entities": {}, "rolls": { "roll_chance": 0.0, "elite_chance": 1.0, "boss_chance": 0.0, "spawn_safety_radius": 0.0 } }""";
	private static final String PROMOTE_BOSS = """
		{ "entities": {}, "rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 1.0, "spawn_safety_radius": 0.0 } }""";

	private static final Map<BlockPos, List<Vec3>> SPARKLES = new ConcurrentHashMap<>();

	static {
		ShieldGeneratorBlockEntity.setRecorder((generator, x, y, z) ->
			SPARKLES.computeIfAbsent(generator.getBlockPos(), key -> new CopyOnWriteArrayList<>()).add(new Vec3(x, y, z)));
	}

	/** One arena with a generator in it and a flag that says the mobs are placed. */
	private static final class Scene {
		private final GameTestHelper helper;
		private final WideArena arena;
		private volatile boolean ready;
		private int ticks;

		private Scene(final GameTestHelper helper) {
			this.helper = helper;
			this.arena = WideArena.build(helper);
			helper.runBeforeTestEnd(() -> {
				helper.setBlock(GEN_X, GEN_Y, GEN_Z, Blocks.AIR);
				this.arena.finish();
			});
		}

		/** Places the generator. Mobs the test wants inside the box go in first, because the box refuses them after. */
		private void place() {
			this.helper.setBlock(GEN_X, GEN_Y, GEN_Z, ShieldBlocks.BLOCK);
		}

		private BlockPos generatorPos() {
			return this.helper.absolutePos(new BlockPos(GEN_X, GEN_Y, GEN_Z));
		}

		private ShieldGeneratorBlockEntity generator() {
			return (ShieldGeneratorBlockEntity) this.helper.getLevel().getBlockEntity(generatorPos());
		}

		private void breakGenerator() {
			this.helper.setBlock(GEN_X, GEN_Y, GEN_Z, Blocks.AIR);
		}

		/** Runs {@code populate} once every listed spot ticks entities, then lets the per-tick hook count. */
		private void whenReady(final List<Vec3> spots, final Runnable populate) {
			this.arena.whenReady(spots, () -> {
				populate.run();
				this.ready = true;
			});
		}

		private boolean tick() {
			if (!this.ready) {
				return false;
			}
			this.ticks++;
			return true;
		}

		private <T extends Mob> T candidate(final EntityType<T> type, final double x, final double y, final double z) {
			T mob = type.create(this.helper.getLevel(), EntitySpawnReason.NATURAL);
			Vec3 at = this.arena.at(x, y, z);
			mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
			mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
			mob.setPersistenceRequired();
			mob.setNoAi(true);
			this.arena.track(mob);
			return mob;
		}

		private boolean present(final Entity entity) {
			return this.helper.getLevel().getEntity(entity.getUUID()) != null;
		}
	}

	private static Vec3 spot(final double x) {
		return new Vec3(x, 1.0, GEN_Z);
	}

	private static void result(final String name, final String text) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {}", TEST, name, text);
	}

	/** The seeded sample: the configured chance yields about one drop in ten thousand rolls. */
	@GameTest
	public void theDefaultDropChanceIsAboutOneInTenThousand(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(ROLL_SEED);
		int hits = 0;
		for (int i = 0; i < ROLL_SAMPLE; i++) {
			if (ShieldDrops.roll(random, ShieldConfig.DEFAULT_DROP_CHANCE)) {
				hits++;
			}
		}
		double expected = ROLL_SAMPLE * ShieldConfig.DEFAULT_DROP_CHANCE;
		double spread = ROLL_SIGMAS * Math.sqrt(ROLL_SAMPLE * ShieldConfig.DEFAULT_DROP_CHANCE * (1.0 - ShieldConfig.DEFAULT_DROP_CHANCE));
		result("rollRate", "hits=" + hits + " expected=" + expected + " spread=" + spread + " chance=" + ShieldConfig.DEFAULT_DROP_CHANCE);
		helper.assertTrue(Math.abs(hits - expected) <= spread, hits + " drops in " + ROLL_SAMPLE + " rolls, expected " + expected + " +- " + spread);
		helper.assertTrue(ShieldConfig.DEFAULT_DROP_CHANCE == 0.0001, "the shipped chance is not 0.01 percent");
		helper.succeed();
	}

	/** A real kill drops one generator at the corpse. A Wither minion and a summon drop none at a certain chance. */
	@GameTest(maxTicks = READY_TICKS + 100, padding = WideArena.PADDING)
	public void aKilledMobDropsOneGeneratorExceptWhereLootIsSuppressed(final GameTestHelper helper) {
		double previous = ShieldConfig.dropChance();
		ShieldConfig.setDropChance(1.0);
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(() -> {
			ShieldConfig.setDropChance(previous);
			arena.finish();
		});
		arena.whenReady(List.of(new Vec3(8.0, 1.0, 8.0)), () -> {
			ServerLevel level = helper.getLevel();
			Zombie plain = arena.spawn(EntityTypes.ZOMBIE, 8.0, 8.0, mob -> { });
			Zombie minion = arena.spawn(EntityTypes.ZOMBIE, 8.0, 16.0, mob -> mob.addTag(WitherEncounter.MINION_TAG));
			Zombie summoned = arena.spawn(EntityTypes.ZOMBIE, 8.0, 24.0, mob -> { });
			EliteState.setSummoned(summoned, true);
			int plainDrops = killAndCount(level, arena, plain);
			int minionDrops = killAndCount(level, arena, minion);
			int summonDrops = killAndCount(level, arena, summoned);
			result("killDrop", "plain=" + plainDrops + " minion=" + minionDrops + " summoned=" + summonDrops);
			helper.assertTrue(plainDrops == 1, "a plain kill dropped " + plainDrops + " generators at a certain chance");
			helper.assertTrue(minionDrops == 0, "a Wither minion dropped " + minionDrops + " generators");
			helper.assertTrue(summonDrops == 0, "a summoned mob dropped " + summonDrops + " generators");
			helper.succeed();
		});
	}

	private static int killAndCount(final ServerLevel level, final WideArena arena, final Mob mob) {
		Vec3 at = mob.position();
		mob.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
		AABB near = new AABB(at, at).inflate(3.0);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, near, item -> item.getItem().is(ShieldBlocks.ITEM));
		drops.forEach(arena::track);
		return drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
	}

	/** Every way a hostile mob enters the level is refused in the box and allowed outside it. */
	@GameTest(maxTicks = READY_TICKS + 100, padding = WideArena.PADDING)
	public void hostileMobsAreRefusedInsideTheBoxOnEveryEntryPath(final GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.whenReady(List.of(spot(INSIDE_X), spot(OUTSIDE_X)), () -> {
			scene.place();
			ServerLevel level = helper.getLevel();
			List<String> problems = new ArrayList<>();
			Map<String, BiConsumer<ServerLevel, Mob>> paths = Map.of(
				"addFreshEntity", ServerLevel::addFreshEntity,
				"addFreshEntityWithPassengers", ServerLevel::addFreshEntityWithPassengers,
				"tryAddFreshEntityWithPassengers", ServerLevel::tryAddFreshEntityWithPassengers,
				"addWithUUID", ServerLevel::addWithUUID
			);
			for (Map.Entry<String, BiConsumer<ServerLevel, Mob>> path : paths.entrySet()) {
				Zombie inside = scene.candidate(EntityTypes.ZOMBIE, INSIDE_X, WideArena.SPAWN_Y, GEN_Z);
				Zombie outside = scene.candidate(EntityTypes.ZOMBIE, OUTSIDE_X, WideArena.SPAWN_Y, GEN_Z);
				path.getValue().accept(level, inside);
				path.getValue().accept(level, outside);
				if (scene.present(inside)) {
					problems.add(path.getKey() + " let a zombie into the box");
				}
				if (!scene.present(outside)) {
					problems.add(path.getKey() + " refused a zombie outside the box");
				}
			}
			for (EntitySpawnReason reason : List.of(EntitySpawnReason.NATURAL, EntitySpawnReason.SPAWNER, EntitySpawnReason.REINFORCEMENT)) {
				Mob inside = EntityTypes.CREEPER.spawn(level, helper.absolutePos(new BlockPos(INSIDE_X, WideArena.SPAWN_Y, GEN_Z)), reason);
				Mob outside = EntityTypes.CREEPER.spawn(level, helper.absolutePos(new BlockPos(OUTSIDE_X, WideArena.SPAWN_Y, GEN_Z)), reason);
				scene.arena.track(inside);
				scene.arena.track(outside);
				if (scene.present(inside)) {
					problems.add("EntityType.spawn " + reason + " let a creeper into the box");
				}
				if (!scene.present(outside)) {
					problems.add("EntityType.spawn " + reason + " refused a creeper outside the box");
				}
			}
			Zombie lastInside = scene.candidate(EntityTypes.ZOMBIE, LAST_INSIDE_X, WideArena.SPAWN_Y, GEN_Z);
			Zombie firstOutside = scene.candidate(EntityTypes.ZOMBIE, FIRST_OUTSIDE_X, WideArena.SPAWN_Y, GEN_Z);
			Zombie highInside = scene.candidate(EntityTypes.ZOMBIE, INSIDE_X, GEN_Y + HALF, GEN_Z);
			Zombie highOutside = scene.candidate(EntityTypes.ZOMBIE, INSIDE_X, GEN_Y + HALF + 1, GEN_Z);
			Mob cow = scene.candidate(EntityTypes.COW, INSIDE_X, WideArena.SPAWN_Y, GEN_Z);
			for (Mob mob : List.of(lastInside, firstOutside, highInside, highOutside, cow)) {
				level.addFreshEntity(mob);
			}
			if (scene.present(lastInside)) {
				problems.add("the last block of the box (" + HALF + " from the generator) was not covered");
			}
			if (!scene.present(firstOutside)) {
				problems.add("the first block past the box was covered");
			}
			if (scene.present(highInside)) {
				problems.add("the top layer of the box was not covered");
			}
			if (!scene.present(highOutside)) {
				problems.add("the layer above the box was covered");
			}
			if (!scene.present(cow)) {
				problems.add("a cow was refused inside the box");
			}
			result("entryPaths", "problems=" + problems);
			helper.assertTrue(problems.isEmpty(), "entry paths: " + problems);
			helper.succeed();
		});
	}

	/** An elite or boss promotion rides the load of a spawn, so a refused spawn promotes nothing. */
	@GameTest(maxTicks = READY_TICKS + 100, padding = WideArena.PADDING)
	public void elitesAndBossesAreRefusedInsideTheBox(final GameTestHelper helper) {
		Scene scene = new Scene(helper);
		GearConfig previous = GearConfigStore.get();
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		scene.whenReady(List.of(spot(INSIDE_X), spot(OUTSIDE_X)), () -> {
			scene.place();
			ServerLevel level = helper.getLevel();
			List<String> problems = new ArrayList<>();
			GearConfigStore.set(GearConfigParser.parse(PROMOTE_ELITE));
			Mob eliteInside = promotable(scene, EntityTypes.ZOMBIE, INSIDE_X);
			Mob eliteOutside = promotable(scene, EntityTypes.ZOMBIE, OUTSIDE_X);
			level.addFreshEntity(eliteInside);
			level.addFreshEntity(eliteOutside);
			GearConfigStore.set(GearConfigParser.parse(PROMOTE_BOSS));
			Mob bossInside = promotable(scene, EntityTypes.SKELETON, INSIDE_X);
			Mob bossOutside = promotable(scene, EntityTypes.SKELETON, OUTSIDE_X);
			level.addFreshEntity(bossInside);
			level.addFreshEntity(bossOutside);
			if (!scene.present(eliteOutside) || !EliteState.isElite(eliteOutside)) {
				problems.add("the elite roll did not promote the zombie outside the box");
			}
			if (!scene.present(bossOutside) || !EliteState.isBoss(bossOutside)) {
				problems.add("the boss roll did not promote the skeleton outside the box");
			}
			if (scene.present(eliteInside) || EliteState.hasEliteFlag(eliteInside)) {
				problems.add("an elite entered the box");
			}
			if (scene.present(bossInside) || EliteState.hasBossFlag(bossInside)) {
				problems.add("a boss entered the box");
			}
			result("promotions", "problems=" + problems);
			helper.assertTrue(problems.isEmpty(), "promotions: " + problems);
			helper.succeed();
		});
	}

	/** A mob that takes the spawn rolls: the candidate without the skip tag. */
	private static <T extends Mob> T promotable(final Scene scene, final EntityType<T> type, final double x) {
		T mob = scene.candidate(type, x, WideArena.SPAWN_Y, GEN_Z);
		mob.removeTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		return mob;
	}

	/** A vanilla spawner block inside the box makes nothing. One outside the box, with the same player, does. */
	@GameTest(maxTicks = READY_TICKS + SPAWNER_TICKS + 100, padding = WideArena.PADDING)
	public void aSpawnerBlockInsideTheBoxMakesNothing(final GameTestHelper helper) {
		Scene scene = new Scene(helper);
		int insideSpawnerX = GEN_X + 12;
		int outsideSpawnerX = GEN_X + HALF + 8;
		scene.whenReady(List.of(spot(insideSpawnerX), spot(outsideSpawnerX)), () -> {
			scene.place();
			scene.arena.survivalPlayer(LAST_INSIDE_X, GEN_Z);
			for (int x : new int[] {insideSpawnerX, outsideSpawnerX}) {
				helper.setBlock(x, GEN_Y, GEN_Z, Blocks.SPAWNER);
				SpawnerBlockEntity spawner = (SpawnerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, GEN_Y, GEN_Z)));
				spawner.setEntityId(EntityTypes.ZOMBIE, helper.getLevel().getRandom());
			}
		});
		helper.onEachTick(() -> {
			if (!scene.tick()) {
				return;
			}
			int inside = zombiesAround(scene, insideSpawnerX);
			int outside = zombiesAround(scene, outsideSpawnerX);
			if (inside > 0) {
				result("spawnerBlock", "inside=" + inside + " outside=" + outside + " tick=" + scene.ticks);
				helper.fail("a spawner inside the box made " + inside + " zombies");
			}
			if (outside > 0 && scene.ticks >= SPAWNER_TICKS / 2) {
				result("spawnerBlock", "inside=" + inside + " outside=" + outside + " tick=" + scene.ticks);
				helper.succeed();
			}
			if (scene.ticks > SPAWNER_TICKS) {
				helper.fail("the spawner outside the box never made a zombie, so the control proves nothing");
			}
		});
	}

	private static int zombiesAround(final Scene scene, final int x) {
		Vec3 at = scene.arena.at(x + 0.5, 2.0, GEN_Z + 0.5);
		List<Zombie> found = scene.helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(at, at).inflate(6.0, 4.0, 6.0));
		found.forEach(scene.arena::track);
		return found.size();
	}

	/** A hostile mob and a player both inside the box keep normal aggro: the zombie takes the player. */
	@GameTest(maxTicks = READY_TICKS + ACQUIRE_TICKS + 100, padding = WideArena.PADDING)
	public void aZombieInsideTheBoxTakesThePlayerBesideIt(final GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer[] player = new ServerPlayer[1];
		Zombie[] zombie = new Zombie[1];
		scene.whenReady(List.of(spot(INSIDE_X)), () -> {
			player[0] = scene.arena.survivalPlayer(INSIDE_X, GEN_Z + 2);
			zombie[0] = scene.arena.spawn(EntityTypes.ZOMBIE, INSIDE_X, GEN_Z, mob -> { });
			scene.place();
		});
		helper.onEachTick(() -> {
			if (!scene.tick()) {
				return;
			}
			WideArena.keepValid(player[0]);
			if (zombie[0].getTarget() == player[0]) {
				result("zombieInside", "acquiredAfter=" + scene.ticks);
				helper.succeed();
			} else if (scene.ticks > ACQUIRE_TICKS) {
				helper.fail("the zombie never took the player inside the box, target=" + zombie[0].getTargetUnchecked());
			}
		});
	}

	/** Breaking the generator restores aggro and spawning in the same spot. */
	@GameTest(maxTicks = READY_TICKS + SHIELD_WATCH_TICKS + ACQUIRE_TICKS + 100, padding = WideArena.PADDING)
	public void breakingTheGeneratorRestoresAggroAndSpawning(final GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer[] player = new ServerPlayer[1];
		Zombie[] zombie = new Zombie[1];
		Zombie[] later = new Zombie[1];
		int[] held = new int[1];
		int breakTick = SHIELD_WATCH_TICKS / 2;
		scene.whenReady(List.of(spot(INSIDE_X), spot(LAST_INSIDE_X - 1), spot(FIRST_OUTSIDE_X + 1)), () -> {
			player[0] = scene.arena.survivalPlayer(FIRST_OUTSIDE_X + 1, GEN_Z);
			zombie[0] = scene.arena.spawn(EntityTypes.ZOMBIE, LAST_INSIDE_X - 1, GEN_Z, mob -> { });
			scene.place();
		});
		helper.onEachTick(() -> {
			if (!scene.tick()) {
				return;
			}
			WideArena.keepValid(player[0]);
			if (scene.ticks <= breakTick) {
				helper.assertTrue(zombie[0].getTarget() != player[0], "the zombie took the player while the generator stood");
				if (scene.ticks == breakTick) {
					scene.breakGenerator();
					later[0] = scene.candidate(EntityTypes.ZOMBIE, INSIDE_X + 1, WideArena.SPAWN_Y, GEN_Z);
					helper.getLevel().addFreshEntity(later[0]);
				}
				return;
			}
			if (zombie[0].getTarget() == player[0]) {
				result("brokenGenerator", "acquiredAfter=" + (scene.ticks - breakTick) + " respawned=" + scene.present(later[0]));
				helper.assertTrue(scene.present(later[0]), "a zombie was still refused after the generator broke");
				helper.succeed();
			} else if (scene.ticks > breakTick + ACQUIRE_TICKS) {
				helper.fail("the zombie never took the player after the generator broke, canAttack=" + zombie[0].canAttack(player[0]) + " target=" + zombie[0].getTargetUnchecked());
			}
		});
	}

	/** A mob with a brain keeps its target in a memory. A brute inside never fills it for a player outside, and does once the generator breaks. */
	@GameTest(maxTicks = READY_TICKS + SHIELD_WATCH_TICKS + ACQUIRE_TICKS + 100, padding = WideArena.PADDING)
	public void aPiglinBruteInsideTheBoxHoldsNoAttackTarget(final GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer[] player = new ServerPlayer[1];
		Mob[] brute = new Mob[1];
		int[] held = new int[1];
		scene.whenReady(List.of(spot(LAST_INSIDE_X - 1), spot(FIRST_OUTSIDE_X + 1)), () -> {
			player[0] = scene.arena.survivalPlayer(FIRST_OUTSIDE_X + 1, GEN_Z);
			brute[0] = scene.arena.spawn(EntityTypes.PIGLIN_BRUTE, LAST_INSIDE_X - 1, GEN_Z, mob -> { });
			scene.place();
		});
		helper.onEachTick(() -> {
			if (!scene.tick()) {
				return;
			}
			WideArena.keepValid(player[0]);
			boolean holds = brute[0].getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent() || brute[0].getTarget() == player[0];
			boolean split = ShieldZones.covers(brute[0]) != ShieldZones.covers(player[0]);
			if (holds && (split || scene.ticks > SHIELD_WATCH_TICKS)) {
				held[0]++;
			}
			if (scene.ticks == SHIELD_WATCH_TICKS) {
				result("bruteInside", "heldTicks=" + held[0]);
				helper.assertTrue(held[0] == 0, "the brute held a target for " + held[0] + " ticks inside the box");
				scene.breakGenerator();
			} else if (scene.ticks > SHIELD_WATCH_TICKS) {
				if (holds) {
					result("bruteAfterBreak", "acquiredAfter=" + (scene.ticks - SHIELD_WATCH_TICKS));
					helper.succeed();
				} else if (scene.ticks > SHIELD_WATCH_TICKS + ACQUIRE_TICKS) {
					helper.fail("the brute never took the player after the generator broke");
				}
			}
		});
	}

	/** A bow skeleton outside the box takes and shoots a player inside only once the generator is gone. */
	@GameTest(maxTicks = READY_TICKS + SHIELD_WATCH_TICKS + ACQUIRE_TICKS + 100, padding = WideArena.PADDING)
	public void aSkeletonOutsideTheBoxNeitherTargetsNorShootsAPlayerInside(final GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer[] player = new ServerPlayer[1];
		Skeleton[] skeleton = new Skeleton[1];
		int[] held = new int[1];
		scene.whenReady(List.of(spot(LAST_INSIDE_X)), () -> {
			player[0] = scene.arena.survivalPlayer(LAST_INSIDE_X - 1, GEN_Z);
			skeleton[0] = scene.arena.spawn(EntityTypes.SKELETON, FIRST_OUTSIDE_X + 3, GEN_Z,
				mob -> mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)));
			WideArena.watch(skeleton[0], () -> player[0]);
			scene.place();
		});
		helper.onEachTick(() -> {
			if (!scene.tick()) {
				return;
			}
			WideArena.keepValid(player[0]);
			boolean split = ShieldZones.covers(skeleton[0]) != ShieldZones.covers(player[0]);
			if (skeleton[0].getTarget() == player[0] && (split || scene.ticks > SHIELD_WATCH_TICKS)) {
				held[0]++;
			}
			if (scene.ticks == SHIELD_WATCH_TICKS) {
				int shots = WideArena.shots(skeleton[0]).size();
				result("skeletonOutside", "heldTicks=" + held[0] + " shots=" + shots);
				helper.assertTrue(held[0] == 0 && shots == 0, "the skeleton held the player " + held[0] + " ticks and shot " + shots + " arrows");
				scene.breakGenerator();
			} else if (scene.ticks > SHIELD_WATCH_TICKS) {
				int shots = WideArena.shots(skeleton[0]).size();
				if (held[0] > 0 && shots > 0) {
					result("skeletonOutsideAfterBreak", "heldTicks=" + held[0] + " shots=" + shots);
					helper.succeed();
				} else if (scene.ticks > SHIELD_WATCH_TICKS + ACQUIRE_TICKS) {
					helper.fail("after the break the skeleton held the player " + held[0] + " ticks and shot " + shots + " arrows");
				}
			}
		});
	}

	/** The edges of the box get green sparkles, on the edges and nowhere else. */
	@GameTest(maxTicks = READY_TICKS + SPARKLE_WINDOW_TICKS + 100, padding = WideArena.PADDING)
	public void theBoxEdgesShowGreenSparkles(final GameTestHelper helper) {
		Scene scene = new Scene(helper);
		long[] start = new long[1];
		scene.whenReady(List.of(spot(GEN_X)), () -> {
			scene.place();
			scene.arena.survivalPlayer(GEN_X + 5, GEN_Z + 5);
			start[0] = scene.generator().sparklesSent();
		});
		helper.onEachTick(() -> {
			if (!scene.tick() || scene.ticks != SPARKLE_WINDOW_TICKS) {
				return;
			}
			long sent = scene.generator().sparklesSent() - start[0];
			List<Vec3> points = SPARKLES.getOrDefault(scene.generatorPos(), List.of());
			BlockPos gen = scene.generatorPos();
			int off = 0;
			for (Vec3 point : points) {
				if (!onEdge(gen, point)) {
					off++;
				}
			}
			result("sparkles", "sent=" + sent + " recorded=" + points.size() + " offEdge=" + off + " window=" + SPARKLE_WINDOW_TICKS);
			helper.assertTrue(sent >= (long) ShieldGeneratorBlockEntity.POINTS_PER_TICK * (SPARKLE_WINDOW_TICKS - 2), "only " + sent + " sparkles in " + SPARKLE_WINDOW_TICKS + " ticks");
			helper.assertTrue(off == 0, off + " of " + points.size() + " sparkles sat off the box edges");
			helper.succeed();
		});
	}

	private static boolean onEdge(final BlockPos gen, final Vec3 point) {
		double[] at = {point.x, point.y, point.z};
		double[] center = {gen.getX(), gen.getY(), gen.getZ()};
		int extremes = 0;
		for (int axis = 0; axis < 3; axis++) {
			double low = center[axis] - HALF;
			double high = center[axis] + HALF + 1;
			if (at[axis] < low - 1.0E-6 || at[axis] > high + 1.0E-6) {
				return false;
			}
			if (Math.abs(at[axis] - low) < 1.0E-6 || Math.abs(at[axis] - high) < 1.0E-6) {
				extremes++;
			}
		}
		return extremes >= 2;
	}
}
