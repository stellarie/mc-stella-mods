package com.stella.mobs;

import com.stella.mobs.behavior.WitherEncounter;
import com.stella.mobs.behavior.WitherMinions;
import com.stella.mobs.behavior.WitherSpawnlings;
import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * At a quarter health the Wither summons four small withers, once. A small wither has a quarter of the health, half the
 * skull damage, the volley with its two random skulls, and nothing else: no sphere, no summons, no loot. It leaves with
 * the original. Each scene runs its phases in order, because each arena costs the server a large chunk load.
 */
public final class WitherSpawnlingGameTest {
	private static final double ABOVE_QUARTER = 0.30;
	private static final double JUST_ABOVE_QUARTER = 0.27;
	private static final double BELOW_QUARTER = 0.20;
	private static final double HALF_ABOVE = 0.60;
	private static final int GAP = 30;
	private static final int SUMMON_WAIT = 12;
	private static final double HIT = 10.0;
	private static final double HIT_SLACK = 0.5;
	private static final double SIZE_SLACK = 0.01;
	private static final double TOLERANCE = 0.05;
	/** Speed II adds 20 percent of the base per level. */
	private static final double SPEED_II_BONUS = 0.40;
	private static final double RING_SLACK = 0.6;
	private static final double SHARE_SLACK = 1.0E-6;
	private static final int WATCH_TICKS = 130;
	private static final int SETTLE_TICKS = 40;
	private static final int REMOVAL_TICKS = 6;
	private static final long SEED = 24680L;
	private static final List<Drop> DROPS = new CopyOnWriteArrayList<>();
	private static boolean watching;

	private record Drop(Vec3 at, boolean star, boolean orb) {
	}

	private static synchronized void watchDrops() {
		if (watching) {
			return;
		}
		watching = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				DROPS.add(new Drop(item.position(), item.getItem().is(Items.NETHER_STAR), false));
			} else if (entity instanceof ExperienceOrb) {
				DROPS.add(new Drop(entity.position(), false, true));
			}
		});
	}

	private static void hit(final ServerLevel level, final WitherBoss wither, final double healthLoss) {
		double divisor = WitherState.find(wither).healthDivisor();
		wither.setInvulnerableTime(0);
		wither.hurtServer(level, level.damageSources().genericKill(), (float) (divisor * healthLoss));
	}

	/** Removes the small withers a scene summoned, then the arena. A parent's removal would also take them. */
	private static void finish(final GameTestHelper helper, final WideArena arena, final WitherBoss... parents) {
		for (WitherBoss parent : parents) {
			if (parent != null) {
				WitherRig.smallWithersOf(helper.getLevel(), parent.getUUID()).forEach(Entity::discard);
			}
		}
		arena.finish();
	}

	private static void setFraction(final WitherBoss wither, final double fraction) {
		wither.setHealth((float) (wither.getMaxHealth() * fraction));
	}

	@GameTest(maxTicks = 900, padding = WideArena.PADDING)
	public void fourSmallWithersComeOnceAtAQuarter(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		WitherBoss[] wither = new WitherBoss[1];
		helper.runBeforeTestEnd(() -> finish(helper, arena, wither[0]));
		ServerPlayer[] player = new ServerPlayer[1];
		int[] phase = {0};
		long[] phaseStart = {0};
		helper.onEachTick(() -> {
			if (wither[0] == null) {
				return;
			}
			WideArena.keepValid(player[0]);
			ServerLevel level = helper.getLevel();
			WitherBoss boss = wither[0];
			WitherConfig config = WitherConfigStore.get();
			long elapsed = helper.getTick() - phaseStart[0];
			switch (phase[0]) {
				case 0 -> {
					if (elapsed >= GAP) {
						helper.assertTrue(WitherState.find(boss).spawnlings().isEmpty() && !WitherState.find(boss).rageBUsed(), "small withers came above a quarter health");
						setFraction(boss, JUST_ABOVE_QUARTER);
						hit(level, boss, boss.getMaxHealth() * (JUST_ABOVE_QUARTER - BELOW_QUARTER));
						next(phase, phaseStart, helper);
					}
				}
				case 1 -> {
					if (WitherState.find(boss).spawnlings().isEmpty()) {
						if (elapsed > SUMMON_WAIT) {
							helper.fail("no small wither came " + elapsed + " ticks after the hit took the Wither under a quarter health");
						}
						return;
					}
					WitherState state = WitherState.find(boss);
					List<WitherBoss> recorded = WitherRig.recordedSmallWithers(level, boss);
					List<WitherBoss> linked = WitherRig.smallWithersOf(level, boss.getUUID());
					StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSpawnlings SUMMON recorded={} linked={} parentMax={} parentFraction={}",
						recorded.size(), linked.size(), boss.getMaxHealth(), boss.getHealth() / boss.getMaxHealth());
					helper.assertTrue(state.spawnlings().size() == config.getInt(WitherConfig.SMALL_COUNT) && recorded.size() == state.spawnlings().size()
						&& linked.size() == state.spawnlings().size(), "the record holds " + state.spawnlings().size() + ", " + recorded.size() + " stand, "
						+ linked.size() + " name the Wither as parent; want " + config.getInt(WitherConfig.SMALL_COUNT));
					Set<UUID> distinct = new HashSet<>(state.spawnlings());
					helper.assertTrue(distinct.size() == state.spawnlings().size(), "the record holds a small wither twice");
					helper.assertTrue(state.rageBUsed(), "the summon is not marked spent");
					for (WitherBoss small : recorded) {
						WitherState smallState = WitherState.find(small);
						double wantedHealth = boss.getMaxHealth() * config.get(WitherConfig.SMALL_HEALTH_SHARE);
						helper.assertTrue(Math.abs(small.getMaxHealth() - wantedHealth) <= wantedHealth * SIZE_SLACK, "a small wither has max health " + small.getMaxHealth() + ", want " + wantedHealth);
						helper.assertTrue(small.getHealth() == small.getMaxHealth() && small.isAlive(), "a small wither started hurt or dead");
						helper.assertTrue(Math.abs(small.getAttributeValue(Attributes.SCALE) - config.get(WitherConfig.SMALL_SCALE)) < SIZE_SLACK, "a small wither has scale " + small.getAttributeValue(Attributes.SCALE));
						helper.assertTrue(smallState.small() && smallState.parent().filter(boss.getUUID()::equals).isPresent(), "a small wither does not name the Wither as parent");
						helper.assertTrue(smallState.healthDivisor() == state.healthDivisor(), "a small wither has divisor " + smallState.healthDivisor() + ", the Wither " + state.healthDivisor());
						helper.assertTrue(small.getInvulnerableTicks() == 0, "a small wither came with a charge-up of " + small.getInvulnerableTicks());
						helper.assertTrue(WitherEncounter.isActive(small), "a small wither is not active at once");
						helper.assertTrue(ThreatState.level(small) == ThreatState.level(boss), "a small wither is level " + ThreatState.level(small) + ", the Wither " + ThreatState.level(boss));
						helper.assertTrue(small.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG), "a small wither takes the spawn rolls");
						// A Wither takes no effect, so Speed II is an attribute bonus of the same size on its flight.
						double flight = small.getAttributeValue(Attributes.FLYING_SPEED) / boss.getAttributeValue(Attributes.FLYING_SPEED);
						helper.assertTrue(Math.abs(flight - (1.0 + config.get(WitherConfig.SMALL_SPEED_BONUS))) < SIZE_SLACK, "a small wither flies at " + flight + " of the original's speed");
						helper.assertTrue(Math.abs(config.get(WitherConfig.SMALL_SPEED_BONUS) - SPEED_II_BONUS) < SIZE_SLACK, "the small wither's speed bonus is not Speed II's");
						double apart = Math.hypot(small.getX() - boss.getX(), small.getZ() - boss.getZ());
						helper.assertTrue(apart <= config.get(WitherConfig.SMALL_DISTANCE) + RING_SLACK, "a small wither stands " + apart + " blocks away");
					}
					WitherBoss first = recorded.get(0);
					float before = first.getHealth();
					hit(level, first, HIT);
					StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSpawnlings HIT lost={} divisor={}", before - first.getHealth(), WitherState.find(first).healthDivisor());
					helper.assertTrue(Math.abs(before - first.getHealth() - HIT) <= HIT_SLACK, "a hit of " + HIT + " health took " + (before - first.getHealth()) + " from a small wither");
					next(phase, phaseStart, helper);
				}
				case 2 -> {
					if (elapsed >= GAP) {
						setFraction(boss, HALF_ABOVE);
						next(phase, phaseStart, helper);
					}
				}
				case 3 -> {
					if (elapsed >= SUMMON_WAIT) {
						setFraction(boss, BELOW_QUARTER);
						next(phase, phaseStart, helper);
					}
				}
				case 4 -> {
					if (elapsed >= GAP) {
						WitherState state = WitherState.find(boss);
						int linked = WitherRig.smallWithersOf(level, boss.getUUID()).size();
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSpawnlings AGAIN recorded={} linked={}", state.spawnlings().size(), linked);
						helper.assertTrue(state.spawnlings().size() == config.getInt(WitherConfig.SMALL_COUNT) && linked == config.getInt(WitherConfig.SMALL_COUNT),
							"a second crossing left " + state.spawnlings().size() + " recorded and " + linked + " standing");
						double aimShare = config.get(WitherConfig.AIM_SHARE);
						double sphereShare = config.get(WitherConfig.SPHERE_SHARE);
						List<Double> shares = new ArrayList<>();
						for (WideArena.Shot shot : WideArena.shots(boss)) {
							double share = shot.projectile() instanceof WitherSkullShot skull ? skull.spec().share() : -1.0;
							shares.add(share);
							helper.assertTrue(Math.abs(share - aimShare) < SHARE_SLACK || Math.abs(share - sphereShare) < SHARE_SLACK,
								"the Wither loosed a skull with share " + share + " that is neither a volley skull nor a sphere skull");
						}
						helper.assertTrue(!shares.isEmpty(), "the Wither loosed no skull the whole scene");
						helper.succeed();
					}
				}
				default -> {
				}
			}
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			wither[0] = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
			WideArena.watch(wither[0], () -> player[0]);
			setFraction(wither[0], ABOVE_QUARTER);
			phaseStart[0] = helper.getTick();
		});
	}

	/** A restart must not orphan the small withers: the parent link, the size and the no-roll tag all come back. */
	@GameTest
	public void aSmallWitherKeepsItsLinkAndSizeAcrossASave(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WitherBoss parent = EntityTypes.WITHER.create(level, EntitySpawnReason.TRIGGERED);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 12.0, 1.5));
		parent.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		level.addFreshEntity(parent);
		List<WitherBoss> added = WitherSpawnlings.summon(level, parent);
		try {
			helper.assertTrue(!added.isEmpty(), "no small wither came");
			WitherBoss small = added.get(0);
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			small.saveWithoutId(output);
			WitherBoss loaded = EntityTypes.WITHER.create(level, EntitySpawnReason.LOAD);
			loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
			WitherState state = WitherState.find(loaded);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSmallSaved RESULT state={} max={} scale={}", state, loaded.getMaxHealth(), loaded.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(state != null && state.small() && state.parent().filter(parent.getUUID()::equals).isPresent(), "a saved small wither lost its parent link: " + state);
			helper.assertTrue(state.activated() && state.healthDivisor() == WitherState.find(parent).healthDivisor(), "a saved small wither lost its activation or divisor");
			helper.assertTrue(loaded.getMaxHealth() == small.getMaxHealth(), "a saved small wither has max health " + loaded.getMaxHealth() + ", was " + small.getMaxHealth());
			helper.assertTrue(Math.abs(loaded.getAttributeValue(Attributes.SCALE) - small.getAttributeValue(Attributes.SCALE)) < SIZE_SLACK, "a saved small wither lost its size");
			helper.assertTrue(loaded.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG), "a saved small wither lost the no-roll tag");
			helper.assertTrue(ThreatState.level(loaded) == ThreatState.level(parent), "a saved small wither changed level");
			double flight = loaded.getAttributeValue(Attributes.FLYING_SPEED) / parent.getAttributeValue(Attributes.FLYING_SPEED);
			helper.assertTrue(Math.abs(flight - 1.4) < SIZE_SLACK, "a saved small wither lost its speed bonus: " + flight);
		} finally {
			added.forEach(Entity::discard);
			parent.discard();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 900, padding = WideArena.PADDING)
	public void aSmallWitherHasHalfTheDamageAndNoPhases(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		WitherBoss[] wither = new WitherBoss[1];
		helper.runBeforeTestEnd(() -> finish(helper, arena, wither[0]));
		WitherBoss[] small = new WitherBoss[1];
		ServerPlayer[] player = new ServerPlayer[1];
		Cow[] cow = new Cow[2];
		boolean[] done = new boolean[1];
		int[] phase = {0};
		long[] phaseStart = {0};
		helper.onEachTick(() -> {
			if (wither[0] == null) {
				return;
			}
			WideArena.keepValid(player[0]);
			ServerLevel level = helper.getLevel();
			WitherBoss boss = wither[0];
			WitherConfig config = WitherConfigStore.get();
			long elapsed = helper.getTick() - phaseStart[0];
			if (phase[0] == 0) {
				List<WitherBoss> recorded = WitherRig.recordedSmallWithers(level, boss);
				if (recorded.isEmpty()) {
					if (elapsed > SUMMON_WAIT) {
						helper.fail("no small wither came");
					}
					return;
				}
				small[0] = recorded.get(0);
				// Below a quarter and below half: a phase-bearing Wither would sphere and summon from here.
				setFraction(small[0], 0.10);
				WideArena.watch(small[0], () -> player[0]);
				phase[0] = 1;
				phaseStart[0] = helper.getTick();
				return;
			}
			if (elapsed < WATCH_TICKS || done[0]) {
				return;
			}
			done[0] = true;
			WitherBoss little = small[0];
			List<List<WideArena.Shot>> volleys = new ArrayList<>();
			int tick = -1;
			for (WideArena.Shot shot : WideArena.shots(little)) {
				if (shot.ownerTick() != tick) {
					tick = shot.ownerTick();
					volleys.add(new ArrayList<>());
				}
				volleys.get(volleys.size() - 1).add(shot);
			}
			double wantedShare = config.get(WitherConfig.AIM_SHARE) * config.get(WitherConfig.SMALL_DAMAGE_FACTOR);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSmallVolley RESULT volleys={} sizes={} minionsOfParent={}",
				volleys.size(), volleys.stream().map(List::size).toList(), WitherState.find(boss).minions().size());
			helper.assertTrue(volleys.size() >= 3, "the small wither loosed only " + volleys.size() + " volleys in " + elapsed + " ticks");
			for (List<WideArena.Shot> volley : volleys) {
				helper.assertTrue(volley.size() == 1 + config.getInt(WitherConfig.AIM_RANDOM_SKULLS), "a volley of the small wither held " + volley.size() + " skulls");
				for (WideArena.Shot shot : volley) {
					double share = ((WitherSkullShot) shot.projectile()).spec().share();
					helper.assertTrue(Math.abs(share - wantedShare) < SHARE_SLACK, "a small wither skull has share " + share + ", want " + wantedShare);
				}
			}
			WitherState littleState = WitherState.find(little);
			helper.assertTrue(littleState.spawnlings().isEmpty() && WitherRig.smallWithersOf(level, little.getUUID()).isEmpty(), "a small wither summoned small withers");
			helper.assertTrue(littleState.minions().isEmpty() && WitherMinions.alive(level, little).isEmpty(), "a small wither called minions");
			helper.assertTrue(!WitherState.find(boss).minions().isEmpty(), "the original called no minions, so the control for the small wither shows nothing");
			// The real hit of a small wither's skull against the original's, on one body.
			WideArena.Shot parentShot = WideArena.shots(boss).stream()
				.filter(shot -> ((WitherSkullShot) shot.projectile()).spec().share() == config.get(WitherConfig.AIM_SHARE)).findFirst().orElse(null);
			helper.assertTrue(parentShot != null, "the original loosed no volley skull");
			WitherSkullShot smallSkull = (WitherSkullShot) volleys.get(0).get(0).projectile();
			float parentLoss = detonate(cow[0], (WitherSkullShot) parentShot.projectile());
			float smallLoss = detonate(cow[1], smallSkull);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSmallDamage RESULT parentLoss={} smallLoss={} ratio={} parentLevel={} smallLevel={} parentExpected={} smallExpected={}",
				parentLoss, smallLoss, smallLoss / parentLoss, ThreatState.level(boss), ThreatState.level(little),
				expected(boss, (WitherSkullShot) parentShot.projectile()), expected(little, smallSkull));
			double parentWanted = expected(boss, (WitherSkullShot) parentShot.projectile());
			double smallWanted = expected(little, smallSkull);
			helper.assertTrue(Math.abs(parentLoss - parentWanted) < TOLERANCE && Math.abs(smallLoss - smallWanted) < TOLERANCE,
				"the bodies lost " + parentLoss + " and " + smallLoss + ", want " + parentWanted + " and " + smallWanted);
			helper.assertTrue(parentLoss > 0.0F && Math.abs(smallLoss - parentLoss * config.get(WitherConfig.SMALL_DAMAGE_FACTOR)) <= parentLoss * SIZE_SLACK,
				"a small wither skull took " + smallLoss + " from the body, the original's took " + parentLoss);
			helper.succeed();
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44), new Vec3(14, 3, 8), new Vec3(22, 3, 8)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			cow[0] = plainCow(helper, arena, 14, 8);
			cow[1] = plainCow(helper, arena, 22, 8);
			wither[0] = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
			WideArena.watch(wither[0], () -> player[0]);
			setFraction(wither[0], BELOW_QUARTER);
			phaseStart[0] = helper.getTick();
		});
	}

	private static double expected(final WitherBoss owner, final WitherSkullShot skull) {
		int level = ThreatState.level(owner);
		return skull.spec().share() * (8.0 * SpawnScaling.damageMultiplier(level) + SpawnScaling.tailFlat(level));
	}

	/** A cow off the spawn rolls, so no trait or scaling changes the hit it takes. */
	private static Cow plainCow(final GameTestHelper helper, final WideArena arena, final double x, final double z) {
		Cow cow = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 at = arena.at(x, WideArena.SPAWN_Y, z);
		cow.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		cow.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		cow.setNoAi(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(WitherRig.BIG_HEALTH);
		cow.setHealth((float) WitherRig.BIG_HEALTH);
		helper.getLevel().addFreshEntity(cow);
		arena.track(cow);
		return cow;
	}

	/** One blast on a body that has taken no hit this tick: a second hit on one body in one tick is shrugged off. */
	private static float detonate(final Cow body, final WitherSkullShot skull) {
		body.removeAllEffects();
		body.clearFire();
		body.setHealth(body.getMaxHealth());
		body.setInvulnerableTime(0);
		float before = body.getHealth();
		skull.detonate(body.position(), RandomSource.create(SEED));
		return before - body.getHealth();
	}

	@GameTest(maxTicks = 900, padding = WideArena.PADDING)
	public void smallWithersDropNothingAndFollowTheOriginalOut(final GameTestHelper helper) {
		watchDrops();
		WideArena arena = WideArena.build(helper);
		WitherBoss[] wither = new WitherBoss[1];
		helper.runBeforeTestEnd(() -> finish(helper, arena, wither[0]));
		ServerPlayer[] player = new ServerPlayer[1];
		List<WitherBoss> smalls = new ArrayList<>();
		Vec3[] diedAt = new Vec3[1];
		int[] mark = {0};
		int[] phase = {0};
		long[] phaseStart = {0};
		helper.onEachTick(() -> {
			if (wither[0] == null) {
				return;
			}
			WideArena.keepValid(player[0]);
			ServerLevel level = helper.getLevel();
			WitherBoss boss = wither[0];
			long elapsed = helper.getTick() - phaseStart[0];
			switch (phase[0]) {
				case 0 -> {
					List<WitherBoss> recorded = WitherRig.recordedSmallWithers(level, boss);
					if (recorded.isEmpty()) {
						if (elapsed > SUMMON_WAIT) {
							helper.fail("no small wither came");
						}
						return;
					}
					smalls.addAll(recorded);
					WitherBoss victim = smalls.get(0);
					diedAt[0] = victim.position();
					mark[0] = DROPS.size();
					victim.setLastHurtByPlayer(player[0], 100);
					victim.hurtServer(level, level.damageSources().playerAttack(player[0]), 1.0E9F);
					helper.assertTrue(victim.isDeadOrDying(), "the small wither did not die");
					next(phase, phaseStart, helper);
				}
				case 1 -> {
					if (elapsed >= SETTLE_TICKS) {
						long stars = DROPS.subList(mark[0], DROPS.size()).stream().filter(drop -> drop.star() && near(drop, diedAt[0])).count();
						long orbs = DROPS.subList(mark[0], DROPS.size()).stream().filter(drop -> drop.orb() && near(drop, diedAt[0])).count();
						long items = DROPS.subList(mark[0], DROPS.size()).stream().filter(drop -> !drop.orb() && near(drop, diedAt[0])).count();
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSmallDrops RESULT stars={} orbs={} items={}", stars, orbs, items);
						helper.assertTrue(stars == 0 && orbs == 0 && items == 0, "a dead small wither left " + stars + " stars, " + orbs + " orbs and " + items + " items");
						diedAt[0] = boss.position();
						mark[0] = DROPS.size();
						boss.setLastHurtByPlayer(player[0], 100);
						boss.hurtServer(level, level.damageSources().playerAttack(player[0]), 1.0E9F);
						helper.assertTrue(boss.isDeadOrDying(), "the Wither did not die");
						next(phase, phaseStart, helper);
					}
				}
				case 2 -> {
					if (elapsed >= REMOVAL_TICKS) {
						long left = smalls.stream().skip(1).filter(small -> !small.isRemoved()).count();
						long linked = WitherRig.smallWithersOf(level, boss.getUUID()).stream().filter(small -> small.isAlive() && !small.isRemoved()).count();
						long stars = DROPS.subList(mark[0], DROPS.size()).stream().filter(drop -> drop.star() && near(drop, diedAt[0])).count();
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSmallDeath RESULT survivors={} linkedAlive={} controlStars={}", left, linked, stars);
						helper.assertTrue(stars >= 1, "the watcher saw no star from the original, so it proves nothing about the small withers");
						helper.assertTrue(left == 0 && linked == 0, left + " of the other small withers outlived the original");
						helper.succeed();
					}
				}
				default -> {
				}
			}
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			wither[0] = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
			setFraction(wither[0], BELOW_QUARTER);
			phaseStart[0] = helper.getTick();
		});
	}

	private static boolean near(final Drop drop, final Vec3 spot) {
		return drop.at().distanceToSqr(spot) < 36.0;
	}

	private static void next(final int[] phase, final long[] phaseStart, final GameTestHelper helper) {
		phase[0]++;
		phaseStart[0] = helper.getTick();
	}

	@GameTest(maxTicks = 900, padding = WideArena.PADDING)
	public void orphanedSmallWithersLeaveAndRemovedParentsTakeTheirs(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		WitherBoss[] unloaded = new WitherBoss[1];
		WitherBoss[] discarded = new WitherBoss[1];
		helper.runBeforeTestEnd(() -> finish(helper, arena, unloaded[0], discarded[0]));
		List<WitherBoss> unloadedSmalls = new ArrayList<>();
		List<WitherBoss> discardedSmalls = new ArrayList<>();
		int[] phase = {0};
		long[] phaseStart = {0};
		helper.onEachTick(() -> {
			if (unloaded[0] == null) {
				return;
			}
			ServerLevel level = helper.getLevel();
			WitherConfig config = WitherConfigStore.get();
			int orphanTicks = config.getInt(WitherConfig.SMALL_ORPHAN_TICKS);
			long elapsed = helper.getTick() - phaseStart[0];
			switch (phase[0]) {
				case 0 -> {
					List<WitherBoss> mine = WitherRig.recordedSmallWithers(level, unloaded[0]);
					List<WitherBoss> theirs = WitherRig.recordedSmallWithers(level, discarded[0]);
					if (mine.size() < config.getInt(WitherConfig.SMALL_COUNT) || theirs.size() < config.getInt(WitherConfig.SMALL_COUNT)) {
						if (elapsed > SUMMON_WAIT) {
							helper.fail("small withers did not come: " + mine.size() + " and " + theirs.size());
						}
						return;
					}
					unloadedSmalls.addAll(mine);
					discardedSmalls.addAll(theirs);
					// An unload keeps the entity's small withers loaded in other chunks; a discard destroys it.
					unloaded[0].remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
					discarded[0].discard();
					next(phase, phaseStart, helper);
				}
				case 1 -> {
					if (elapsed == REMOVAL_TICKS) {
						long gone = discardedSmalls.stream().filter(Entity::isRemoved).count();
						long kept = unloadedSmalls.stream().filter(small -> !small.isRemoved()).count();
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherOrphans EARLY t={} discardedGone={} unloadedKept={}", elapsed, gone, kept);
						helper.assertTrue(gone == discardedSmalls.size(), "a discarded Wither left " + (discardedSmalls.size() - gone) + " small withers after " + elapsed + " ticks");
						helper.assertTrue(kept == unloadedSmalls.size(), "small withers of an unloaded Wither left after " + elapsed + " ticks, before the " + orphanTicks + " tick grace");
					}
					if (elapsed == orphanTicks - REMOVAL_TICKS) {
						long kept = unloadedSmalls.stream().filter(small -> !small.isRemoved()).count();
						helper.assertTrue(kept == unloadedSmalls.size(), "orphans left " + (orphanTicks - elapsed) + " ticks before the grace ends");
					}
					if (elapsed >= orphanTicks + 2L * REMOVAL_TICKS) {
						long kept = unloadedSmalls.stream().filter(small -> !small.isRemoved()).count();
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherOrphans LATE t={} orphansKept={}", elapsed, kept);
						helper.assertTrue(kept == 0, kept + " orphaned small withers outlived the " + orphanTicks + " tick grace");
						helper.succeed();
					}
				}
				default -> {
				}
			}
		});
		arena.whenReady(List.of(new Vec3(12, 3, 28), new Vec3(44, 3, 28)), () -> {
			unloaded[0] = WitherRig.wither(helper, arena, 12, WideArena.SPAWN_Y, 28, 0);
			discarded[0] = WitherRig.wither(helper, arena, 44, WideArena.SPAWN_Y, 28, 0);
			setFraction(unloaded[0], BELOW_QUARTER);
			setFraction(discarded[0], BELOW_QUARTER);
			phaseStart[0] = helper.getTick();
		});
	}
}
