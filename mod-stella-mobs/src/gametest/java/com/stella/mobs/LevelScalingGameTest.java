package com.stella.mobs;

import com.stella.mobs.api.ThreatLevelProvider;
import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import com.stella.mobs.scaling.AltitudeLevels;
import com.stella.mobs.scaling.LevelFloor;
import com.stella.mobs.scaling.MobPiercing;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatExperience;
import com.stella.mobs.scaling.ThreatState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The level floor, altitude levels, curves, piercing, kill experience, the share, and the scaling config. */
public class LevelScalingGameTest {
	private static final double EPSILON = 1.0E-9;
	private static final ScalingConfig D = ScalingConfig.DEFAULTS;

	@GameTest
	public void floorFollowsLowestNearbyPlayer(final GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		List<Player> players = List.of(player(helper, GameType.SURVIVAL, at.add(10, 0, 0), 20));
		int floor = LevelFloor.floorOf(at, players, D);
		Mob mob = zombie(helper);
		attach(mob, new ThreatState(0, ThreatState.MIN_VARIATION, 0, floor));
		int level = ThreatState.level(mob);
		result("floorFollowsLowestNearbyPlayer", "floor=" + floor + " level=" + level);
		helper.assertTrue(floor == 18 && level >= 18, "floor=" + floor + " level=" + level);
		helper.succeed();
	}

	@GameTest
	public void floorUsesLowestLevel(final GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		List<Player> players = List.of(
			player(helper, GameType.SURVIVAL, at.add(5, 0, 0), 5),
			player(helper, GameType.SURVIVAL, at.add(0, 0, 5), 30)
		);
		int floor = LevelFloor.floorOf(at, players, D);
		result("floorUsesLowestLevel", "floor=" + floor);
		helper.assertTrue(floor == 3, "floor=" + floor);
		helper.succeed();
	}

	@GameTest
	public void floorIgnoresFarAndSpectator(final GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		List<Player> players = List.of(
			player(helper, GameType.SURVIVAL, at.add(200, 0, 0), 5),
			player(helper, GameType.SPECTATOR, at.add(3, 0, 0), 7),
			player(helper, GameType.SURVIVAL, at.add(0, 0, 100), 40)
		);
		int floor = LevelFloor.floorOf(at, players, D);
		int none = LevelFloor.floorOf(at, List.of(), D);
		result("floorIgnoresFarAndSpectator", "floor=" + floor + " none=" + none);
		helper.assertTrue(floor == 38 && none == 0, "floor=" + floor + " none=" + none);
		helper.succeed();
	}

	@GameTest
	public void floorIsStoredOnce(final GameTestHelper helper) {
		Mob mob = zombie(helper);
		attach(mob, new ThreatState(0, ThreatState.MIN_VARIATION, 0, 18));
		int before = ThreatState.level(mob);
		ThreatState again = ThreatState.getOrRoll(mob);
		int after = ThreatState.level(mob);
		result("floorIsStoredOnce", "before=" + before + " after=" + after + " floor=" + again.floor());
		helper.assertTrue(before == 18 && after == 18 && again.floor() == 18, "the floor moved after spawn");
		helper.succeed();
	}

	@GameTest
	public void altitudeLevels(final GameTestHelper helper) {
		int deep = AltitudeLevels.levels(Level.OVERWORLD, -64.0, D);
		int zero = AltitudeLevels.levels(Level.OVERWORLD, 0.0, D);
		int high = AltitudeLevels.levels(Level.OVERWORLD, 320.0, D);
		int nether = AltitudeLevels.levels(Level.NETHER, -64.0, D);
		int beyond = AltitudeLevels.levels(Level.OVERWORLD, -640.0, D);
		String detail = "deep=" + deep + " zero=" + zero + " high=" + high + " nether=" + nether + " beyond=" + beyond;
		result("altitudeLevels", detail);
		helper.assertTrue(deep == 10 && zero == 0 && high == 4 && nether == 0 && beyond == 100, detail);
		helper.succeed();
	}

	@GameTest
	public void oldSaveLoads(final GameTestHelper helper) {
		CompoundTag old = new CompoundTag();
		old.putInt("area_level", 3);
		old.putInt("variation", 2);
		ThreatState decoded = ThreatState.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		Mob mob = zombie(helper);
		attach(mob, decoded);
		ThreatState filled = ThreatState.getOrRoll(mob);
		int expectedAltitude = AltitudeLevels.levels(mob.level().dimension(), mob.getY(), ScalingConfigStore.get());
		result("oldSaveLoads", "decoded=" + decoded + " filled=" + filled);
		helper.assertTrue(decoded.altitude() == ThreatState.ALTITUDE_UNSET && decoded.floor() == 0
			&& filled.altitude() == expectedAltitude && filled.areaLevel() == 3 && filled.variation() == 2,
			"decoded=" + decoded + " filled=" + filled);
		helper.succeed();
	}

	@GameTest
	public void curveTable(final GameTestHelper helper) {
		double base80 = Math.pow(1.06, 80);
		boolean valid = close(SpawnScaling.healthMultiplier(0), 1.0)
			&& close(SpawnScaling.healthMultiplier(10), 3.0)
			&& close(SpawnScaling.damageMultiplier(0), 1.0)
			&& close(SpawnScaling.damageMultiplier(10), Math.pow(1.06, 10))
			&& close(SpawnScaling.damageMultiplier(80), base80)
			&& close(SpawnScaling.damageMultiplier(100), base80 * (1.0 + 19.0 * SpawnScaling.tail(20, D)))
			&& close(SpawnScaling.damageMultiplier(580), base80 * 10.5)
			&& close(SpawnScaling.damageMultiplier(1080), base80 * 20.0)
			&& close(SpawnScaling.damageMultiplier(5000), base80 * 20.0);
		result("curveTable", "d100=" + SpawnScaling.damageMultiplier(100) + " d580=" + SpawnScaling.damageMultiplier(580)
			+ " d1080=" + SpawnScaling.damageMultiplier(1080));
		helper.assertTrue(valid, "a curve value left the spec");
		helper.succeed();
	}

	@GameTest
	public void damageTailAddsFlat(final GameTestHelper helper) {
		double at80 = SpawnScaling.tailFlat(80);
		double at580 = SpawnScaling.tailFlat(580);
		result("damageTailAddsFlat", "at80=" + at80 + " at580=" + at580);
		helper.assertTrue(close(at80, 0.0) && close(at580, 10.0), "at80=" + at80 + " at580=" + at580);
		helper.succeed();
	}

	@GameTest
	public void piercingShare(final GameTestHelper helper) {
		float t20 = MobPiercing.share(20, D);
		float t40 = MobPiercing.share(40, D);
		float t60 = MobPiercing.share(60, D);
		Mob plain = zombie(helper);
		attach(plain, new ThreatState(0, ThreatState.MIN_VARIATION, 0, 0));
		Mob strong = zombie(helper);
		attach(strong, new ThreatState(0, ThreatState.MIN_VARIATION, 0, 40));
		float plainLoss = loss(helper, armored(helper), plain);
		float strongLoss = loss(helper, armored(helper), strong);
		result("piercingShare", "t20=" + t20 + " t40=" + t40 + " t60=" + t60
			+ " plainLoss=" + plainLoss + " strongLoss=" + strongLoss);
		helper.assertTrue(Math.abs(t20 - 0.30F) < 1.0E-6F && Math.abs(t40 - 0.5F) < 1.0E-6F && Math.abs(t60 - 0.5F) < 1.0E-6F,
			"shares t20=" + t20 + " t40=" + t40 + " t60=" + t60);
		helper.assertTrue(strongLoss - plainLoss > 2.0F, "a level-40 hit pierced too little: " + plainLoss + " vs " + strongLoss);
		helper.succeed();
	}

	@GameTest
	public void piercingOnlyForMobAttackers(final GameTestHelper helper) {
		ServerPlayer attacker = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)), 50);
		float fromPlayer = MobPiercing.share(helper.getLevel().damageSources().playerAttack(attacker));
		float generic = MobPiercing.share(helper.getLevel().damageSources().generic());
		result("piercingOnlyForMobAttackers", "fromPlayer=" + fromPlayer + " generic=" + generic);
		helper.assertTrue(fromPlayer == 0.0F && generic == 0.0F, "a non-mob hit pierced armor");
		helper.succeed();
	}

	@GameTest
	public void rewardTable(final GameTestHelper helper) {
		int onFloor = ThreatExperience.reward(5, 18, 20);
		int even = ThreatExperience.reward(5, 0, 0);
		int above = ThreatExperience.reward(5, 50, 10);
		int farBelow = ThreatExperience.reward(5, 0, 40);
		String detail = "onFloor=" + onFloor + " even=" + even + " above=" + above + " farBelow=" + farBelow;
		result("rewardTable", detail);
		helper.assertTrue(onFloor == 11 && even == 5 && above == 120 && farBelow == 1, detail);
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("unchecked")
	public void shareReturnsLevel(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(ThreatLevelProvider.OBJECT_SHARE_KEY);
		helper.assertTrue(shared instanceof Function<?, ?>, "the threat share is missing: " + shared);
		Function<Entity, Integer> share = (Function<Entity, Integer>) shared;
		Mob mob = zombie(helper);
		attach(mob, new ThreatState(0, ThreatState.MIN_VARIATION, 0, 27));
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		int mobLevel = share.apply(mob);
		int standLevel = share.apply(stand);
		result("shareReturnsLevel", "mob=" + mobLevel + " stand=" + standLevel);
		helper.assertTrue(mobLevel == 27 && standLevel == -1, "mob=" + mobLevel + " stand=" + standLevel);
		helper.succeed();
	}

	@GameTest
	public void configRoundTrip(final GameTestHelper helper) throws Exception {
		List<String> errors = new ArrayList<>();
		ScalingConfig parsed = ScalingConfigStore.parse("{\"health_per_level\": 0.2, \"bogus\": 1}", errors::add);
		Path file = Files.createTempDirectory("stella-scaling").resolve("scaling.json");
		ScalingConfig previous = ScalingConfigStore.get();
		ScalingConfig written;
		double edited;
		try {
			written = ScalingConfigStore.loadFrom(file, errors::add);
			ScalingConfigStore.set(parsed);
			edited = SpawnScaling.healthMultiplier(10);
		} finally {
			ScalingConfigStore.set(previous);
		}
		boolean valid = Files.exists(file) && written.equals(ScalingConfig.DEFAULTS)
			&& close(parsed.healthPerLevel(), 0.2) && close(edited, 3.0)
			&& errors.stream().anyMatch(message -> message.contains("bogus"));
		result("configRoundTrip", "edited=" + edited + " errors=" + errors);
		helper.assertTrue(valid, "the scaling config did not round-trip: edited=" + edited + " errors=" + errors);
		helper.succeed();
	}

	private static float loss(final GameTestHelper helper, final Mob target, final Mob attacker) {
		float before = target.getHealth();
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 10.0F);
		return before - target.getHealth();
	}

	private static Mob armored(final GameTestHelper helper) {
		Mob armored = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 3, 2, 3);
		armored.setHealth(armored.getMaxHealth());
		// Equipment applies on the next tick; the base value applies now.
		armored.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
		return armored;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(final GameTestHelper helper, final GameType mode, final Vec3 at, final int level) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(mode);
		player.setPos(at.x, at.y, at.z);
		player.experienceLevel = level;
		return player;
	}

	private static Mob zombie(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		return mob;
	}

	private static void attach(final Mob mob, final ThreatState state) {
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, state);
	}

	private static boolean close(final double actual, final double expected) {
		return Math.abs(actual - expected) <= EPSILON * Math.max(1.0, Math.abs(expected));
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
