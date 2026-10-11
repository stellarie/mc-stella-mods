package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.GhastFireballGoal;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.projectile.GhastWitherShot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Every ghast fires a volley of three slow shots every seven ticks, aimed as a triangle with the target at its
 * centre. A shot bursts in a blast that hurts bodies but breaks no block. Where it lands it hurts a player in
 * reach, lights one to three fires at random spots when mobs may grief, and leaves a cloud of Wither and
 * Slowness that lingers five seconds, its Wither stronger from an elite. A ghast is not a Monster, so it never
 * rolls gear, and the kit drop is vacuous rather than broken.
 */
public class GhastEliteGameTest {
	private static final String TEST = "ghastElite";
	private static final int GHAST_BASE_HEALTH = 10;
	private static final int VICTIM_HEALTH = 400;
	private static final double EPSILON = 1.0E-6;
	private static final double LANDING_TOLERANCE = 0.5;
	private static final double MAX_ACCEPTED_SPEED = 0.5;
	private static final int FLOOD_WINDOW_TICKS = 200;

	private record Volley(int tick, Vec3 center, List<Vec3> points) {
	}

	private record Cloud(float radius, int duration, List<MobEffectInstance> effects) {
	}

	private record Landing(Vec3 at, List<BlockPos> fires, List<Player> hurt) {
	}

	private static final Map<UUID, List<Integer>> FIRED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Volley>> VOLLEYS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<GhastWitherShot>> SHOTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<MobEffectInstance>> CLOUDS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<AreaEffectCloud>> CLOUD_ENTITIES = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Cloud>> SNAPSHOTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Landing>> LANDINGS = new ConcurrentHashMap<>();
	private static final Set<UUID> BLASTED = ConcurrentHashMap.newKeySet();
	/** Fireball hits a ghast dealt to a body, keyed by the victim's entity id, since mock players share one UUID. */
	private static final Map<Integer, List<Float>> FIREBALL_HITS = new ConcurrentHashMap<>();

	static {
		GhastFireballGoal.setRecorder(new GhastFireballGoal.Recorder() {
			@Override
			public void fired(final Ghast ghast, final int serverTick) {
				FIRED.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(serverTick);
			}

			@Override
			public void volley(final Ghast ghast, final int serverTick, final Vec3 center, final List<Vec3> points) {
				VOLLEYS.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(new Volley(serverTick, center, List.copyOf(points)));
			}

			@Override
			public void shot(final Ghast ghast, final GhastWitherShot shot) {
				SHOTS.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(shot);
			}

			@Override
			public void cloud(final Ghast ghast, final AreaEffectCloud cloud, final MobEffectInstance wither) {
				CLOUDS.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(wither);
				CLOUD_ENTITIES.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(cloud);
				SNAPSHOTS.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>())
					.add(new Cloud(cloud.getRadius(), cloud.getDuration(), List.copyOf(cloud.get(DataComponents.POTION_CONTENTS).customEffects())));
			}

			@Override
			public void landed(final Ghast ghast, final Vec3 at, final List<BlockPos> fires, final List<Player> hurt) {
				LANDINGS.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(new Landing(at, List.copyOf(fires), List.copyOf(hurt)));
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
			if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() instanceof Ghast) {
				BLASTED.add(victim.getUUID());
			}
			if (source.is(DamageTypes.FIREBALL) && source.getEntity() instanceof Ghast) {
				FIREBALL_HITS.computeIfAbsent(victim.getId(), key -> new CopyOnWriteArrayList<>()).add(damage);
			}
		});
	}

	@GameTest
	public void theGhastVolleyIsTheAcceptedOne(final GameTestHelper helper) {
		helper.assertTrue(GhastFireballGoal.ATTACK_INTERVAL_TICKS == 7, "a ghast attacks every " + GhastFireballGoal.ATTACK_INTERVAL_TICKS + " ticks, not 7");
		helper.assertTrue(GhastFireballGoal.SHOTS_PER_ATTACK == 3, "a ghast fires " + GhastFireballGoal.SHOTS_PER_ATTACK + " shots per attack, not 3");
		helper.assertTrue(GhastFireballGoal.TRIANGLE_RADIUS == 3.0, "the triangle radius is " + GhastFireballGoal.TRIANGLE_RADIUS + ", not 3");
		helper.assertTrue(GhastFireballGoal.CLOUD_RADIUS == 3.0F, "the cloud radius is " + GhastFireballGoal.CLOUD_RADIUS + ", not 3");
		helper.assertTrue(GhastFireballGoal.CLOUD_DURATION_TICKS == 100, "the cloud lingers " + GhastFireballGoal.CLOUD_DURATION_TICKS + " ticks, not 100");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aGhastFiresAVolleyOfThreeEverySevenTicks(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		aim(helper, ghast, spawnSubject(helper, 4, 1, 4));
		helper.succeedWhen(() -> {
			List<Volley> volleys = VOLLEYS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(volleys.size() >= 5, "the ghast has attacked " + volleys.size() + " times");
			List<Integer> gaps = new ArrayList<>();
			for (int i = 1; i < volleys.size(); i++) {
				gaps.add(volleys.get(i).tick() - volleys.get(i - 1).tick());
			}
			helper.assertTrue(gaps.stream().allMatch(gap -> gap == GhastFireballGoal.ATTACK_INTERVAL_TICKS), "the ghast attacked " + gaps + " ticks apart, not every " + GhastFireballGoal.ATTACK_INTERVAL_TICKS);
			List<Integer> fired = FIRED.getOrDefault(ghast.getUUID(), List.of());
			for (Volley volley : volleys) {
				int shots = Collections.frequency(fired, volley.tick());
				helper.assertTrue(shots == GhastFireballGoal.SHOTS_PER_ATTACK, "the attack on tick " + volley.tick() + " fired " + shots + " shots");
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastFiresAVolleyOfThreeEverySevenTicks gaps={} shots={}", TEST, gaps, fired.size());
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void aVolleyAimsAtATriangleAroundTheTarget(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		Mob victim = spawnSubject(helper, 4, 1, 4);
		aim(helper, ghast, victim);
		helper.succeedWhen(() -> {
			List<Volley> volleys = VOLLEYS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!volleys.isEmpty(), "the ghast has not attacked yet");
			Volley volley = volleys.get(0);
			helper.assertTrue(volley.points().size() == GhastFireballGoal.SHOTS_PER_ATTACK, "the volley aims at " + volley.points().size() + " points");
			helper.assertTrue(Math.abs(volley.center().x - victim.getX()) < 0.05 && Math.abs(volley.center().z - victim.getZ()) < 0.05 && Math.abs(volley.center().y - victim.getY()) < 0.05, "the triangle centres on " + volley.center() + ", not on the target at " + victim.position());
			List<Double> angles = new ArrayList<>();
			for (Vec3 point : volley.points()) {
				double reach = Math.hypot(point.x - volley.center().x, point.z - volley.center().z);
				helper.assertTrue(Math.abs(reach - GhastFireballGoal.TRIANGLE_RADIUS) < EPSILON, "an aim point lies " + reach + " blocks from the centre, not " + GhastFireballGoal.TRIANGLE_RADIUS);
				helper.assertTrue(Math.abs(point.y - volley.center().y) < EPSILON, "an aim point is off the horizontal circle by " + (point.y - volley.center().y));
				angles.add(Math.toDegrees(Math.atan2(point.z - volley.center().z, point.x - volley.center().x)));
			}
			Collections.sort(angles);
			double first = angles.get(1) - angles.get(0);
			double second = angles.get(2) - angles.get(1);
			double third = 360.0 - (angles.get(2) - angles.get(0));
			helper.assertTrue(Math.abs(first - 120.0) < 1.0E-4 && Math.abs(second - 120.0) < 1.0E-4 && Math.abs(third - 120.0) < 1.0E-4, "the aim points are not 120 degrees apart: " + angles);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aVolleyAimsAtATriangleAroundTheTarget angles={} centre={}", TEST, angles, volley.center());
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void aGhastShotCrawlsNoFasterThanHalfABlockPerTick(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		aim(helper, ghast, spawnSubject(helper, 4, 1, 4));
		List<Double> speeds = new CopyOnWriteArrayList<>();
		helper.onEachTick(() -> SHOTS.getOrDefault(ghast.getUUID(), List.of()).stream().filter(shot -> !shot.isRemoved() && shot.tickCount > 0).forEach(shot -> speeds.add(shot.getDeltaMovement().length())));
		helper.succeedWhen(() -> {
			helper.assertTrue(speeds.size() >= 20, "only " + speeds.size() + " shot speeds were sampled");
			double fastest = speeds.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
			double slowest = speeds.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
			helper.assertTrue(GhastFireballGoal.SHOT_SPEED <= MAX_ACCEPTED_SPEED, "the shipped shot speed is " + GhastFireballGoal.SHOT_SPEED);
			helper.assertTrue(fastest <= MAX_ACCEPTED_SPEED, "a shot flew " + fastest + " blocks in one tick");
			helper.assertTrue(slowest > 0.0, "a shot stood still in flight");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastShotCrawlsNoFasterThanHalfABlockPerTick samples={} fastest={} slowest={}", TEST, speeds.size(), fastest, slowest);
			ghast.discard();
		});
	}

	@GameTest
	public void aGhastShotLivesLongEnoughToCrossItsRange(final GameTestHelper helper) {
		double reach = GhastFireballGoal.SHOT_LIFE_TICKS * GhastFireballGoal.SHOT_SPEED;
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastShotLivesLongEnoughToCrossItsRange life={} speed={} reach={} range={}", TEST, GhastFireballGoal.SHOT_LIFE_TICKS, GhastFireballGoal.SHOT_SPEED, reach, GhastFireballGoal.MAX_RANGE);
		helper.assertTrue(GhastFireballGoal.SHOT_LIFE_TICKS >= 200, "a shot lives only " + GhastFireballGoal.SHOT_LIFE_TICKS + " ticks");
		helper.assertTrue(reach >= GhastFireballGoal.MAX_RANGE, "a shot flies " + reach + " blocks before it burns out, short of the " + GhastFireballGoal.MAX_RANGE + " block range");
		helper.succeed();
	}

	@GameTest(maxTicks = 260)
	public void aGhastShotThatHitsNothingBurnsOutAtTheEndOfItsLife(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		GhastWitherShot shot = new GhastWitherShot(helper.getLevel(), ghast, Vec3.ZERO, 0);
		shot.setPos(helper.absoluteVec(new Vec3(4.5, 4.0, 4.5)));
		shot.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(shot);
		helper.runAfterDelay(GhastFireballGoal.SHOT_LIFE_TICKS - 5L, () -> helper.assertFalse(shot.isRemoved(), "a shot burned out after " + shot.tickCount + " ticks, before the end of its life"));
		helper.runAfterDelay(GhastFireballGoal.SHOT_LIFE_TICKS + 5L, () -> {
			helper.assertTrue(shot.isRemoved(), "a shot that hit nothing is still flying after " + shot.tickCount + " ticks");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastShotThatHitsNothingBurnsOutAtTheEndOfItsLife removed=true", TEST);
			ghast.discard();
			helper.succeed();
		});
	}

	@GameTest
	public void aGhastShotIsNeverSaved(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		GhastWitherShot shot = new GhastWitherShot(helper.getLevel(), ghast, Vec3.ZERO, 0);
		boolean saved = shot.shouldBeSaved();
		ghast.discard();
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastShotIsNeverSaved saved={}", TEST, saved);
		helper.assertFalse(saved, "a ghast shot would be saved with its chunk and reload as a vanilla fireball");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aLandedShotLeavesACloudOfRadiusThreeForFiveSeconds(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		dropShot(helper, ghast, 4.5, 4.5);
		helper.succeedWhen(() -> {
			List<Cloud> clouds = SNAPSHOTS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!clouds.isEmpty(), "no shot has landed yet");
			helper.assertTrue(clouds.get(0).radius() == GhastFireballGoal.CLOUD_RADIUS, "the cloud's radius is " + clouds.get(0).radius());
			helper.assertTrue(clouds.get(0).duration() == GhastFireballGoal.CLOUD_DURATION_TICKS, "the cloud lingers " + clouds.get(0).duration() + " ticks");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aLandedShotLeavesACloudOfRadiusThreeForFiveSeconds radius={} duration={}", TEST, clouds.get(0).radius(), clouds.get(0).duration());
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 100)
	public void aLandedShotsCloudCarriesWitherAndSlownessButNoPoison(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		dropShot(helper, ghast, 4.5, 4.5);
		helper.succeedWhen(() -> {
			List<Cloud> clouds = SNAPSHOTS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!clouds.isEmpty(), "no shot has landed yet");
			List<MobEffectInstance> effects = clouds.get(0).effects();
			MobEffectInstance wither = effects.stream().filter(effect -> effect.getEffect().equals(MobEffects.WITHER)).findFirst().orElse(null);
			MobEffectInstance slowness = effects.stream().filter(effect -> effect.getEffect().equals(MobEffects.SLOWNESS)).findFirst().orElse(null);
			helper.assertTrue(wither != null && wither.getAmplifier() == GhastFireballGoal.ORDINARY_WITHER_AMPLIFIER, "the cloud carries no ordinary Wither: " + effects);
			helper.assertTrue(slowness != null && slowness.getAmplifier() == GhastFireballGoal.SLOWNESS_AMPLIFIER, "the cloud carries no Slowness: " + effects);
			helper.assertTrue(effects.stream().noneMatch(effect -> effect.getEffect().equals(MobEffects.POISON)), "the cloud still carries Poison: " + effects);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aLandedShotsCloudCarriesWitherAndSlownessButNoPoison effects={}", TEST, effects.size());
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 100)
	public void aLandedShotWithersAndSlowsABodyInItsCloud(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		Mob victim = spawnSubject(helper, 4, 1, 4);
		dropShot(helper, ghast, 3.5, 4.5);
		helper.succeedWhen(() -> {
			helper.assertTrue(victim.hasEffect(MobEffects.WITHER) && victim.hasEffect(MobEffects.SLOWNESS), "the victim carries no Wither and Slowness yet");
			helper.assertFalse(victim.hasEffect(MobEffects.POISON), "the victim was poisoned");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aLandedShotWithersAndSlowsABodyInItsCloud wither=true slowness=true poison=false", TEST);
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 100)
	public void aLandedShotStillBlastsABodyBesideItWithoutBreakingBlocks(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		Mob victim = spawnSubject(helper, 4, 1, 4);
		int blocksBefore = solidBlocks(helper, helper.absolutePos(new BlockPos(4, 1, 4)));
		dropShot(helper, ghast, 3.5, 4.5);
		helper.succeedWhen(() -> {
			helper.assertTrue(BLASTED.contains(victim.getUUID()), "no ghast shot's blast reached the victim yet");
			int blocksNow = solidBlocks(helper, helper.absolutePos(new BlockPos(4, 1, 4)));
			helper.assertTrue(blocksNow == blocksBefore, "the shot changed the ground: " + blocksBefore + " -> " + blocksNow);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aLandedShotStillBlastsABodyBesideItWithoutBreakingBlocks blasted=true blocks={}", TEST, blocksNow);
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void aGhastVolleyLandsOnItsThreeAimPoints(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		aim(helper, ghast, spawnSubject(helper, 4, 1, 4));
		helper.succeedWhen(() -> {
			List<Volley> volleys = VOLLEYS.getOrDefault(ghast.getUUID(), List.of());
			List<Landing> landings = LANDINGS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!volleys.isEmpty() && landings.size() >= GhastFireballGoal.SHOTS_PER_ATTACK, "only " + landings.size() + " shots have landed");
			for (Vec3 point : volleys.get(0).points()) {
				double nearest = landings.stream().mapToDouble(landing -> Math.hypot(landing.at().x - point.x, landing.at().z - point.z)).min().orElse(Double.MAX_VALUE);
				helper.assertTrue(nearest < LANDING_TOLERANCE, "no shot landed within " + LANDING_TOLERANCE + " blocks of the aim point " + point + ": nearest " + nearest);
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastVolleyLandsOnItsThreeAimPoints landings={}", TEST, landings.size());
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void anEliteGhastsCloudWithersHarder(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, true);
		aim(helper, ghast, spawnSubject(helper, 4, 1, 4));
		helper.succeedWhen(() -> {
			List<MobEffectInstance> clouds = CLOUDS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!clouds.isEmpty(), "the elite ghast has left no cloud yet");
			helper.assertTrue(clouds.get(0).getAmplifier() == GhastFireballGoal.ELITE_WITHER_AMPLIFIER, "the elite cloud carries Wither " + (clouds.get(0).getAmplifier() + 1));
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=anEliteGhastsCloudWithersHarder wither={}", TEST, clouds.get(0).getAmplifier() + 1);
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 100)
	public void aLandedShotLightsOneToThreeFires(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		dropShot(helper, ghast, 4.5, 4.5);
		helper.succeedWhen(() -> {
			List<Landing> landings = LANDINGS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!landings.isEmpty(), "no shot has landed yet");
			int fires = landings.get(0).fires().size();
			helper.assertTrue(fires >= GhastFireballGoal.FIRE_MIN && fires <= GhastFireballGoal.FIRE_MAX, "the landing lit " + fires + " fires");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aLandedShotLightsOneToThreeFires fires={}", TEST, fires);
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 100)
	public void theFiresStandOnSolidGroundWithinTheRadius(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		dropShot(helper, ghast, 4.5, 4.5);
		helper.succeedWhen(() -> {
			List<Landing> landings = LANDINGS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!landings.isEmpty(), "no shot has landed yet");
			Landing landing = landings.get(0);
			helper.assertTrue(!landing.fires().isEmpty(), "the landing lit no fire");
			ServerLevel level = helper.getLevel();
			for (BlockPos pos : landing.fires()) {
				helper.assertTrue(level.getBlockState(pos).is(Blocks.FIRE), "no fire burns at " + pos);
				helper.assertTrue(level.getBlockState(pos.below()).is(Blocks.STONE), "the fire at " + pos + " does not stand on the ground");
				double reach = Math.hypot(pos.getX() + 0.5 - landing.at().x, pos.getZ() + 0.5 - landing.at().z);
				helper.assertTrue(reach <= GhastFireballGoal.CLOUD_RADIUS + EPSILON, "the fire at " + pos + " lies " + reach + " blocks from the landing");
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=theFiresStandOnSolidGroundWithinTheRadius fires={}", TEST, landing.fires().size());
			ghast.discard();
		});
	}

	@GameTest
	public void theFiresChooseTheirNumberAndSpotsAtRandom(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		ServerLevel level = helper.getLevel();
		Vec3 centre = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		Set<Integer> counts = new HashSet<>();
		Set<BlockPos> spots = new HashSet<>();
		boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
		try {
			level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
			for (long seed = 0; seed < 300; seed++) {
				List<BlockPos> lit = GhastWitherShot.scorch(level, centre, RandomSource.create(seed));
				counts.add(lit.size());
				spots.addAll(lit);
				helper.assertTrue(new HashSet<>(lit).size() == lit.size(), "two fires were set on one block");
				lit.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
			}
		} finally {
			level.getGameRules().set(GameRules.MOB_GRIEFING, griefing, level.getServer());
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=theFiresChooseTheirNumberAndSpotsAtRandom counts={} spots={}", TEST, counts, spots.size());
		helper.assertTrue(counts.equals(Set.of(1, 2, 3)), "over 300 landings the fire counts were " + counts + ", not 1 to 3");
		helper.assertTrue(spots.size() >= 12, "300 landings lit only " + spots.size() + " distinct spots");
		helper.succeed();
	}

	@GameTest
	public void aLandedShotLightsNoFireWhenMobGriefingIsOff(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		ServerLevel level = helper.getLevel();
		Vec3 centre = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
		List<BlockPos> lit;
		try {
			level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
			lit = GhastWitherShot.scorch(level, centre, RandomSource.create(7L));
		} finally {
			level.getGameRules().set(GameRules.MOB_GRIEFING, griefing, level.getServer());
		}
		int burning = 0;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				burning += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.FIRE) ? 1 : 0;
			}
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aLandedShotLightsNoFireWhenMobGriefingIsOff lit={} burning={}", TEST, lit.size(), burning);
		helper.assertTrue(lit.isEmpty() && burning == 0, "mobGriefing was off, yet " + lit.size() + " fires were lit and " + burning + " burn");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aLandedShotHurtsAPlayerInsideTheRadius(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		// A ghast with a mind of its own would pick the player as a target and add landings of its own.
		ghast.setNoAi(true);
		net.minecraft.server.level.ServerPlayer inside = player(helper, 4.5, 4.5);
		dropShot(helper, ghast, 2.0, 4.5);
		helper.succeedWhen(() -> {
			List<Float> hits = FIREBALL_HITS.getOrDefault(inside.getId(), List.of());
			helper.assertTrue(!hits.isEmpty(), "the player inside the radius took no landing damage yet");
			helper.assertTrue(hits.get(0) > 0.0F, "the landing damage was " + hits.get(0));
			helper.assertTrue(LANDINGS.getOrDefault(ghast.getUUID(), List.of()).get(0).hurt().contains(inside), "the landing did not record the player it hurt: " + LANDINGS.getOrDefault(ghast.getUUID(), List.of()).get(0).hurt() + " against " + inside + " hits " + hits);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aLandedShotHurtsAPlayerInsideTheRadius damage={}", TEST, hits.get(0));
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 100)
	public void aLandedShotSparesAPlayerOutsideTheRadius(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		ghast.setNoAi(true);
		net.minecraft.server.level.ServerPlayer outside = player(helper, 6.5, 4.5);
		dropShot(helper, ghast, 2.0, 4.5);
		helper.runAfterDelay(40L, () -> {
			boolean landed = !LANDINGS.getOrDefault(ghast.getUUID(), List.of()).isEmpty();
			List<Float> hits = FIREBALL_HITS.getOrDefault(outside.getId(), List.of());
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aLandedShotSparesAPlayerOutsideTheRadius landed={} hits={}", TEST, landed, hits.size());
			ghast.discard();
			helper.assertTrue(landed, "the shot never landed, so the case proves nothing");
			helper.assertTrue(hits.isEmpty(), "the player outside the radius took " + hits.size() + " landing hits");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aShotTurnedBackByAPlayerHurtsNoPlayer(final GameTestHelper helper) {
		net.minecraft.server.level.ServerPlayer striker = player(helper, 4.5, 4.5);
		float before = striker.getHealth();
		GhastWitherShot shot = new GhastWitherShot(helper.getLevel(), striker, new Vec3(0.0, -1.0, 0.0), 0);
		shot.setPos(helper.absoluteVec(new Vec3(2.0, 3.0, 4.5)));
		helper.getLevel().addFreshEntity(shot);
		helper.runAfterDelay(40L, () -> {
			boolean landed = shot.isRemoved();
			float after = striker.getHealth();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aShotTurnedBackByAPlayerHurtsNoPlayer landed={} before={} after={}", TEST, landed, before, after);
			helper.assertTrue(landed, "the shot never landed, so the case proves nothing");
			helper.assertTrue(after == before, "the player who owns the shot lost " + (before - after) + " health to its landing");
			helper.succeed();
		});
	}

	// Alone in its own batch, so no neighbouring test's barrier ceiling lies across the long sight line.
	@GameTest(environment = "mod-stella-mobs-test:ghast_range", maxTicks = 200, padding = 96)
	public void aGhastHoldsFireBeyondItsRange(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		// Low enough that its eyes sit below the test's barrier ceiling, so it sees out across the open floor.
		ghast.snapTo(helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)), 0.0F, 0.0F);
		ghast.setNoGravity(true);
		// A full-size ghast's eyes stand in the barrier ceiling, so this one is shrunk to see under it.
		ghast.getAttribute(Attributes.SCALE).setBaseValue(0.25);
		ghast.refreshDimensions();
		Vec3 far = ghast.position().add(GhastFireballGoal.MAX_RANGE + 6.0, 0.0, 0.0);
		int chunkX = net.minecraft.core.SectionPos.blockToSectionCoord(far.x);
		int chunkZ = net.minecraft.core.SectionPos.blockToSectionCoord(far.z);
		boolean forced = helper.getLevel().setChunkForced(chunkX, chunkZ, true);
		Mob victim = net.minecraft.world.entity.EntityTypes.COW.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
		victim.snapTo(far, 0.0F, 0.0F);
		victim.setNoAi(true);
		victim.setNoGravity(true);
		victim.setPersistenceRequired();
		helper.getLevel().addFreshEntity(victim);
		// The test area is walled with barrier; the few wall blocks on the sight line are opened so the ghast sees out.
		for (int opened = 0; opened < 4; opened++) {
			net.minecraft.world.phys.BlockHitResult wall = helper.getLevel().clip(new net.minecraft.world.level.ClipContext(ghast.getEyePosition(), victim.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, ghast));
			if (wall.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !helper.getLevel().getBlockState(wall.getBlockPos()).is(Blocks.BARRIER)) {
				break;
			}
			helper.getLevel().setBlockAndUpdate(wall.getBlockPos(), Blocks.AIR.defaultBlockState());
		}
		aim(helper, ghast, victim);
		helper.runAfterDelay(60L, () -> {
			int shots = FIRED.getOrDefault(ghast.getUUID(), List.of()).size();
			boolean sees = ghast.hasLineOfSight(victim);
			net.minecraft.world.phys.BlockHitResult clip = helper.getLevel().clip(new net.minecraft.world.level.ClipContext(ghast.getEyePosition(), victim.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, ghast));
			double distance = ghast.distanceTo(victim);
			ghast.discard();
			victim.discard();
			if (forced) {
				helper.getLevel().setChunkForced(chunkX, chunkZ, false);
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastHoldsFireBeyondItsRange shots={} distance={} sees={}", TEST, shots, distance, sees);
			helper.assertTrue(sees, "the ghast cannot see the far target, so the case proves nothing: the sight line stops at " + clip.getBlockPos() + " " + helper.getLevel().getBlockState(clip.getBlockPos()) + " from eye " + ghast.getEyePosition() + " to " + victim.getEyePosition() + " in bounds " + helper.getBounds());
			helper.assertTrue(shots == 0, "the ghast fired " + shots + " times at a target " + distance + " blocks away");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = FLOOD_WINDOW_TICKS + 40)
	public void aGhastsVolleysStayWithinTheEntityBudgetOverTenSeconds(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		Mob victim = spawnSubject(helper, 4, 1, 4);
		// The arena is small, so its own clouds reach the ghast. Only the count of entities is under test here.
		ghast.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH * 100.0);
		ghast.setHealth(VICTIM_HEALTH * 100.0F);
		aim(helper, ghast, victim);
		int[] peakShots = {0};
		int[] peakClouds = {0};
		helper.onEachTick(() -> {
			int shots = (int) SHOTS.getOrDefault(ghast.getUUID(), List.of()).stream().filter(shot -> !shot.isRemoved()).count();
			int clouds = (int) CLOUD_ENTITIES.getOrDefault(ghast.getUUID(), List.of()).stream().filter(cloud -> !cloud.isRemoved()).count();
			peakShots[0] = Math.max(peakShots[0], shots);
			peakClouds[0] = Math.max(peakClouds[0], clouds);
		});
		helper.runAfterDelay(FLOOD_WINDOW_TICKS, () -> {
			int shotBudget = GhastFireballGoal.SHOTS_PER_ATTACK * (ceilDiv(GhastFireballGoal.SHOT_LIFE_TICKS, GhastFireballGoal.ATTACK_INTERVAL_TICKS) + 1);
			int cloudBudget = GhastFireballGoal.SHOTS_PER_ATTACK * (ceilDiv(GhastFireballGoal.CLOUD_DURATION_TICKS, GhastFireballGoal.ATTACK_INTERVAL_TICKS) + 1);
			int longRangeFlight = (int) Math.ceil(GhastFireballGoal.MAX_RANGE / GhastFireballGoal.SHOT_SPEED);
			int longRangeShots = GhastFireballGoal.SHOTS_PER_ATTACK * (ceilDiv(longRangeFlight, GhastFireballGoal.ATTACK_INTERVAL_TICKS) + 1);
			int volleys = VOLLEYS.getOrDefault(ghast.getUUID(), List.of()).size();
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} RESULT test=aGhastsVolleysStayWithinTheEntityBudgetOverTenSeconds window={} volleys={} peakShots={} peakClouds={} cloudBudget={} shotBudget={} longRangeShotsAt64={} ghastAlive={} victimAlive={} victimHealth={} sees={}",
				TEST, FLOOD_WINDOW_TICKS, volleys, peakShots[0], peakClouds[0], cloudBudget, shotBudget, longRangeShots, ghast.isAlive(), victim.isAlive(), victim.getHealth(), ghast.hasLineOfSight(victim)
			);
			ghast.discard();
			helper.assertTrue(volleys >= FLOOD_WINDOW_TICKS / GhastFireballGoal.ATTACK_INTERVAL_TICKS - 8, "the ghast attacked only " + volleys + " times in the window");
			helper.assertTrue(peakShots[0] <= shotBudget, "the ghast had " + peakShots[0] + " shots in flight, over the budget of " + shotBudget);
			helper.assertTrue(peakClouds[0] <= cloudBudget, "the ghast had " + peakClouds[0] + " clouds lingering, over the budget of " + cloudBudget);
			helper.succeed();
		});
	}

	private static int ceilDiv(final int a, final int b) {
		return (a + b - 1) / b;
	}

	// A hurt victim carries immunity frames, and setTarget refuses an invulnerable target, so they are cleared first.
	private static void aim(final GameTestHelper helper, final Ghast ghast, final Mob victim) {
		helper.onEachTick(() -> {
			victim.setInvulnerableTime(0);
			ghast.setTarget(victim);
		});
	}

	@GameTest(maxTicks = 200)
	public void eliteGhastCarriesNoGear(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Ghast ghast = spawnGhast(helper, true);

		helper.runAfterDelay(4L, () -> {
			int slots = 0;

			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (!ghast.getItemBySlot(slot).isEmpty()) {
					slots++;
				}
			}

			double scale = ghast.getAttribute(Attributes.SCALE) == null ? 1.0 : ghast.getAttribute(Attributes.SCALE).getValue();
			helper.assertValueEqual(Integer.valueOf(slots), Integer.valueOf(0), "gear slots filled on an elite ghast");
			helper.assertTrue(
				Math.abs(scale - (1.0 + Elite.SCALE_BONUS)) < 1.0e-6,
				"the elite ghast scale was " + scale + " against " + (1.0 + Elite.SCALE_BONUS)
			);
			helper.assertTrue(
				ghast.getMaxHealth() >= GHAST_BASE_HEALTH * Elite.HEALTH_MULTIPLIER,
				"the elite ghast health was " + ghast.getMaxHealth() + ", below the elite doubling of "
					+ (GHAST_BASE_HEALTH * Elite.HEALTH_MULTIPLIER)
			);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} RESULT slots={} scale={} health={} drops={}",
				TEST, slots, String.format(java.util.Locale.ROOT, "%.2f", scale), ghast.getMaxHealth(), 0
			);
			helper.succeed();
		});
	}

	/** Marked and installed by hand, per the fixture rule: no config write, no chance pin. */
	private static Ghast spawnGhast(final GameTestHelper helper, final boolean elite) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		// The fixture takes none of the natural spawn rolls, so an ordinary ghast can never roll elite or boss.
		Ghast ghast = EntityTypes.GHAST.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
		ghast.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		ghast.snapTo(helper.absoluteVec(new Vec3(4.5, 3.0, 0.5)), 0.0F, 0.0F);
		ghast.setPersistenceRequired();
		helper.getLevel().addFreshEntity(ghast);
		// A ghast drifts upward until its eyes sit in the test's barrier ceiling and it sees nothing, so it stays put.
		ghast.getGoalSelector().removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.monster.Ghast.RandomFloatAroundGoal);

		if (elite) {
			Elite.mark(ghast, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		}

		BehaviorInstaller.installGhastFireball(ghast);
		return ghast;
	}

	/** A shot of the shipped kind that falls straight onto the floor at x, z, so its landing point is known. */
	private static GhastWitherShot dropShot(final GameTestHelper helper, final Ghast ghast, final double x, final double z) {
		GhastWitherShot shot = new GhastWitherShot(helper.getLevel(), ghast, new Vec3(0.0, -1.0, 0.0), 0);
		shot.setPos(helper.absoluteVec(new Vec3(x, 3.0, z)));
		helper.getLevel().addFreshEntity(shot);
		return shot;
	}

	/** A survival player standing on the floor, on a level that lets a player be hurt. */
	private static net.minecraft.server.level.ServerPlayer player(final GameTestHelper helper, final double x, final double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		net.minecraft.server.level.ServerPlayer player = Arena.addSurvivalPlayer(helper);
		// A player whose client has not reported loaded takes no damage, which would make a spared player prove nothing.
		player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	/** A cow: not undead, so Poison would be accepted, and not a Monster, so no gear path touches it. */
	private static Mob spawnSubject(final GameTestHelper helper, final int x, final int y, final int z) {
		Mob victim = helper.spawnMob(EntityTypes.COW, x, y, z).withNoFreeWill().spawn();
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
		victim.setHealth(VICTIM_HEALTH);
		victim.setPersistenceRequired();
		return victim;
	}

	private static int solidBlocks(final GameTestHelper helper, final BlockPos centre) {
		int solid = 0;

		for (int dx = -5; dx <= 5; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				for (int dz = -5; dz <= 5; dz++) {
					BlockPos pos = centre.offset(dx, dy, dz);

					// Fire is the one block the shot adds on purpose, so it is not a change to the ground.
					if (!helper.getLevel().getBlockState(pos).is(Blocks.AIR) && !helper.getLevel().getBlockState(pos).is(Blocks.FIRE)) {
						solid++;
					}
				}
			}
		}

		return solid;
	}
}
