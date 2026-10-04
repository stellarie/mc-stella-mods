package com.stella.tower;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Boss floors: their mist, their players' buff, and their mobs' two buffs, each growing with the floor. */
public class TowerBuffGameTest {
	/** Realistic boss floors for the mobs' own records, one per test so pinned buffs never cross. */
	private static final int EFFECTS_FLOOR = 250;
	private static final int GUARDS_FLOOR = 260;
	private static final int LIFE_FLOOR = 270;
	private static final int DEEP_LIFE_FLOOR = 1070;
	private static final int OUTSIDE_FLOOR = 280;

	private static Zombie mobOn(final ServerLevel tower, final int room, final double dx, final int own) {
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, own, TowerElement.WIND);
		zombie.snapTo(TowerTestSupport.awayFromArrival(tower, room, dx));
		tower.addFreshEntity(zombie);
		return zombie;
	}

	@GameTest
	public void aBossFloorIsWrappedInMistAndAnOrdinaryFloorIsNot(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int boss = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		int plain = boss + 1;
		TowerFloors.ensure(tower, boss);
		TowerFloors.ensure(tower, plain);
		BlockPos bossMiddle = TowerLayout.origin(boss).offset(TowerLayout.SIZE / 2, 2, TowerLayout.SIZE / 2);
		BlockPos plainMiddle = TowerLayout.origin(plain).offset(TowerLayout.SIZE / 2, 2, TowerLayout.SIZE / 2);
		boolean misty = tower.getBiome(bossMiddle).is(TowerBuffs.BOSS_BIOME);
		boolean plainMisty = tower.getBiome(plainMiddle).is(TowerBuffs.BOSS_BIOME);
		TowerTestSupport.result("bossMist", "misty=" + misty + " plainMisty=" + plainMisty);
		helper.assertTrue(misty, "boss floor " + boss + " carries no mist");
		helper.assertFalse(plainMisty, "ordinary floor " + plain + " is misty");
		helper.succeed();
	}

	@GameTest
	public void aBossFloorsBuffsAreItsOwnAndStayTheSame(final GameTestHelper helper) {
		long seed = 99L;
		int floor = 250;
		List<TowerBuffs.MobBuff> mobs = TowerBuffs.mobBuffs(seed, floor);
		boolean same = mobs.equals(TowerBuffs.mobBuffs(seed, floor)) && TowerBuffs.playerBuff(seed, floor) == TowerBuffs.playerBuff(seed, floor);
		int distinctPlayer = 0;
		java.util.Set<TowerBuffs.PlayerBuff> seen = new java.util.HashSet<>();
		for (int f = 10; f <= 2000; f += 10) {
			seen.add(TowerBuffs.playerBuff(seed, f));
		}
		TowerTestSupport.result("buffDraw", "mobs=" + mobs + " same=" + same + " playerKinds=" + seen.size());
		helper.assertTrue(mobs.size() == 2 && mobs.get(0) != mobs.get(1), "a boss floor's mobs carry " + mobs);
		helper.assertTrue(same && seen.size() > 1, "boss floor buffs are not drawn from the floor: same=" + same + " kinds=" + seen.size());
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aPlayerOnABossFloorCarriesItsBuffUntilLeaving(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		int away = floor + 1;
		TowerFloors.ensure(tower, floor);
		TowerFloors.ensure(tower, away);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerBuffs.PlayerBuff buff = TowerBuffs.playerBuff(tower.getSeed(), floor);
		boolean[] had = {false};
		int[] amplifier = {-1};
		helper.runAfterDelay(40L, () -> {
			MobEffectInstance effect = player.getEffect(buff.effect());
			had[0] = effect != null;
			amplifier[0] = effect == null ? -1 : effect.getAmplifier();
			Vec3 elsewhere = TowerLayout.arrival(tower, away);
			player.teleportTo(tower, elsewhere.x, elsewhere.y, elsewhere.z, Set.of(), 0.0F, 0.0F, true);
		});
		// The buff is taken at the next refresh after leaving. The last dose, given no earlier than a refresh before
		// the move, would last past this sample, so only the removal can pass it.
		helper.runAfterDelay(40L + TowerBuffs.REFRESH_TICKS + 1L, () -> {
			boolean lingers = player.hasEffect(buff.effect());
			TowerTestSupport.result("playerBuff", "buff=" + buff + " had=" + had[0] + " amplifier=" + amplifier[0] + "/" + buff.amplifier(floor) + " lingers=" + lingers);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(TowerBuffs.REFRESH_TICKS + 1 < TowerBuffs.DURATION_TICKS - TowerBuffs.REFRESH_TICKS,
				"a dose lapses on its own before the sample, so the case proves nothing");
			helper.assertTrue(had[0] && amplifier[0] == buff.amplifier(floor), "the player carried " + buff + " at " + amplifier[0] + ", not " + buff.amplifier(floor));
			helper.assertFalse(lingers, "the buff outlasted the player's leaving the floor");
			helper.succeed();
		});
	}

	@GameTest
	public void buffsGrowWithTheFloorWithinTheirCeilings(final GameTestHelper helper) {
		int low = TowerBuffs.PlayerBuff.STRENGTH.amplifier(10);
		int high = TowerBuffs.PlayerBuff.STRENGTH.amplifier(1000);
		TowerTestSupport.result("buffLevels", "low=" + low + " high=" + high);
		helper.assertTrue(low == Math.min(TowerBuffs.PlayerBuff.STRENGTH.maxAmplifier(), TowerBuffs.level(10) - 1)
			&& high == Math.min(TowerBuffs.PlayerBuff.STRENGTH.maxAmplifier(), TowerBuffs.level(1000) - 1) && high > low,
			"strength at floors 10 and 1000 is " + low + " and " + high);
		helper.succeed();
	}

	/** Every buff, a player's or a mob's, stops at its own ceiling however high the floor. */
	@GameTest
	public void everyBuffStopsAtItsCeiling(final GameTestHelper helper) {
		int deep = 1_000_000;
		List<String> past = new java.util.ArrayList<>();
		for (TowerBuffs.PlayerBuff buff : TowerBuffs.PlayerBuff.values()) {
			if (buff.amplifier(deep) != buff.maxAmplifier() || buff.amplifier(1) != 0) {
				past.add(buff + "=" + buff.amplifier(1) + ".." + buff.amplifier(deep) + "/" + buff.maxAmplifier());
			}
		}
		for (TowerBuffs.MobBuff buff : TowerBuffs.MobBuff.values()) {
			if (buff.amplifier(deep) != buff.maxAmplifier() || buff.amplifier(1) != 0) {
				past.add(buff + "=" + buff.amplifier(1) + ".." + buff.amplifier(deep) + "/" + buff.maxAmplifier());
			}
		}
		TowerTestSupport.result("buffCeilings", "past=" + past);
		helper.assertTrue(past.isEmpty(), "buffs off their ceilings: " + past);
		helper.succeed();
	}

	/** Absorption is a shield given once for the stay: topped up with each refresh, it would be a wall that heals. */
	@GameTest(maxTicks = 120)
	public void anAbsorptionShieldDoesNotRefill(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerBuffs.pinPlayerBuff(floor, TowerBuffs.PlayerBuff.ABSORPTION);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		float[] given = {-1.0F};
		helper.runAfterDelay(TowerBuffs.REFRESH_TICKS + 10L, () -> {
			given[0] = player.getAbsorptionAmount();
			// Spent in a fight. A spent shield's effect then ends on the player's own tick, which a test player
			// never runs, so the test ends it as the game does (AbsorptionMobEffect.applyEffectTick).
			player.setAbsorptionAmount(0.0F);
			player.removeEffect(TowerBuffs.PlayerBuff.ABSORPTION.effect());
		});
		helper.runAfterDelay(TowerBuffs.REFRESH_TICKS * 3L + 11L, () -> {
			float after = player.getAbsorptionAmount();
			TowerBuffs.pinPlayerBuff(floor, null);
			TowerTestSupport.result("absorptionOnce", "given=" + given[0] + " after=" + after);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(given[0] > 0.0F, "the floor gave no shield, so the case proves nothing");
			helper.assertTrue(after == 0.0F, "the spent shield came back to " + after);
			helper.succeed();
		});
	}

	/** Milk ends the shield too; drinking it on the floor must not buy a fresh one. */
	@GameTest(maxTicks = 120)
	public void milkBuysNoFreshShield(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerBuffs.pinPlayerBuff(floor, TowerBuffs.PlayerBuff.ABSORPTION);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		float[] given = {-1.0F};
		helper.runAfterDelay(TowerBuffs.REFRESH_TICKS + 10L, () -> {
			given[0] = player.getAbsorptionAmount();
			player.setAbsorptionAmount(given[0] / 2.0F);
			player.removeAllEffects();
		});
		helper.runAfterDelay(TowerBuffs.REFRESH_TICKS * 3L + 11L, () -> {
			float after = player.getAbsorptionAmount();
			TowerBuffs.pinPlayerBuff(floor, null);
			TowerTestSupport.result("milkShield", "given=" + given[0] + " after=" + after);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(given[0] > 0.0F, "the floor gave no shield, so the case proves nothing");
			helper.assertTrue(after < given[0], "milk bought a fresh shield of " + after);
			helper.succeed();
		});
	}

	/** A boss floor's buffs belong to the floor: a mob of that floor loose in the world carries none of them. */
	@GameTest
	public void aBossFloorsMobOutsideTheTowerCarriesNoBuff(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, OUTSIDE_FLOOR, TowerElement.WIND);
		zombie.snapTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
		level.addFreshEntity(zombie);
		double factor;
		List<TowerBuffs.MobBuff> buffs;
		try {
			TowerBuffs.pinMobBuffs(OUTSIDE_FLOOR, List.of(TowerBuffs.MobBuff.EXTRA_HEALTH, TowerBuffs.MobBuff.THORNS));
			buffs = TowerBuffs.mobBuffsOf(zombie);
			factor = TowerBuffs.healthFactor(zombie);
		} finally {
			TowerBuffs.pinMobBuffs(OUTSIDE_FLOOR, null);
			zombie.discard();
		}
		TowerTestSupport.result("buffOutside", "buffs=" + buffs + " factor=" + factor);
		helper.assertTrue(buffs.isEmpty() && factor == 1.0, "a floor " + OUTSIDE_FLOOR + " mob outside the tower carries " + buffs);
		helper.succeed();
	}

	/** A player's own potion, held under the floor's stronger buff, is still theirs after the floor. */
	@GameTest(maxTicks = 120)
	public void aPotionUnderTheBuffOutlivesLeaving(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		int away = floor + 1;
		TowerFloors.ensure(tower, floor);
		TowerFloors.ensure(tower, away);
		TowerBuffs.pinPlayerBuff(floor, TowerBuffs.PlayerBuff.STRENGTH);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		int potion = 9600;
		player.addEffect(new MobEffectInstance(TowerBuffs.PlayerBuff.STRENGTH.effect(), potion, 0));
		int[] floorAmplifier = {-1};
		helper.runAfterDelay(40L, () -> {
			MobEffectInstance held = player.getEffect(TowerBuffs.PlayerBuff.STRENGTH.effect());
			floorAmplifier[0] = held == null ? -1 : held.getAmplifier();
			Vec3 elsewhere = TowerLayout.arrival(tower, away);
			player.teleportTo(tower, elsewhere.x, elsewhere.y, elsewhere.z, Set.of(), 0.0F, 0.0F, true);
		});
		helper.runAfterDelay(40L + TowerBuffs.REFRESH_TICKS + 1L, () -> {
			TowerBuffs.pinPlayerBuff(floor, null);
			MobEffectInstance left = player.getEffect(TowerBuffs.PlayerBuff.STRENGTH.effect());
			TowerTestSupport.result("potionUnder", "floorAmplifier=" + floorAmplifier[0] + " left=" + left);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(floorAmplifier[0] > 0, "the floor's buff never covered the potion, so the case proves nothing");
			helper.assertTrue(left != null && left.getAmplifier() == 0 && left.getDuration() > TowerBuffs.DURATION_TICKS,
				"the player's own potion is gone after the floor: " + left);
			helper.succeed();
		});
	}

	/** Leaving the tower, by portal, key or scroll, takes a boss floor's buff at once, not a dose later. */
	@GameTest(maxTicks = 100)
	public void leavingTheTowerTakesTheBuffAtOnce(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerBuffs.pinPlayerBuff(floor, TowerBuffs.PlayerBuff.STRENGTH);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		boolean[] had = {false};
		helper.runAfterDelay(40L, () -> {
			had[0] = player.hasEffect(TowerBuffs.PlayerBuff.STRENGTH.effect());
			Vec3 home = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
			player.teleportTo(helper.getLevel(), home.x, home.y, home.z, Set.of(), 0.0F, 0.0F, true);
		});
		helper.runAfterDelay(42L, () -> {
			TowerBuffs.pinPlayerBuff(floor, null);
			boolean lingers = player.hasEffect(TowerBuffs.PlayerBuff.STRENGTH.effect());
			TowerTestSupport.result("leftTower", "had=" + had[0] + " lingers=" + lingers + " in=" + player.level().dimension().identifier());
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(had[0], "the player never had the buff, so the case proves nothing");
			helper.assertFalse(lingers, "the buff went out of the tower with the player");
			helper.succeed();
		});
	}

	/** A boss floor built before the mist came gets its mist when a player stands on it. */
	@GameTest(maxTicks = 100)
	public void anOldBossFloorGetsItsMistOnEntry(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		BlockPos middle = TowerLayout.origin(floor).offset(TowerLayout.SIZE / 2, 2, TowerLayout.SIZE / 2);
		// As an older build left it: the plain floor's biome.
		net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> plain = tower.registryAccess()
			.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
			.getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, StellaTower.id("tower_floor")));
		BlockPos origin = TowerLayout.origin(floor);
		for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + TowerLayout.SIZE) >> 4; cx++) {
			for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + TowerLayout.SIZE) >> 4; cz++) {
				tower.getChunk(cx, cz).fillBiomesFromNoise((x, y, z) -> plain);
			}
		}
		boolean plainBefore = !tower.getBiome(middle).is(TowerBuffs.BOSS_BIOME);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(plainBefore, "the floor was misty before the test, so the case proves nothing");
			helper.assertTrue(tower.getBiome(middle).is(TowerBuffs.BOSS_BIOME), "the old boss floor has no mist with a player on it");
			if (!done[0]) {
				done[0] = true;
				TowerTestSupport.result("oldMist", "tick=" + helper.getTick());
				TowerTestSupport.leave(helper, player);
			}
		});
	}

	@GameTest(maxTicks = 100)
	public void aBossFloorsMobsCarryTheirEffects(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerBuffs.pinMobBuffs(EFFECTS_FLOOR, List.of(TowerBuffs.MobBuff.SPEED, TowerBuffs.MobBuff.STRENGTH));
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		Zombie zombie = mobOn(tower, floor, 20.0, EFFECTS_FLOOR);
		helper.runAfterDelay(40L, () -> {
			TowerBuffs.pinMobBuffs(EFFECTS_FLOOR, null);
			boolean fast = zombie.hasEffect(TowerBuffs.MobBuff.SPEED.effect());
			boolean strong = zombie.hasEffect(TowerBuffs.MobBuff.STRENGTH.effect());
			TowerTestSupport.result("mobEffects", "fast=" + fast + " strong=" + strong);
			zombie.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(fast && strong, "the boss floor's mob carries fast=" + fast + " strong=" + strong);
			helper.succeed();
		});
	}

	@GameTest
	public void extraHealthAndThornsGuardABossFloorsMobs(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		Zombie zombie = mobOn(tower, floor, 20.0, GUARDS_FLOOR);
		Pig pig = TowerTestSupport.unrolled(EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND));
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		pig.setHealth(1000.0F);
		pig.snapTo(zombie.position().add(1.5, 0.0, 0.0));
		tower.addFreshEntity(pig);
		try {
			TowerBuffs.pinMobBuffs(GUARDS_FLOOR, List.of(TowerBuffs.MobBuff.EXTRA_HEALTH, TowerBuffs.MobBuff.THORNS));
			float before = zombie.getHealth();
			// Magic, which armor does not stop, so the floor's armor does not blur the clause.
			zombie.hurtServer(tower, pig.damageSources().indirectMagic(pig, pig), 20.0F);
			float lost = before - zombie.getHealth();
			float reflected = 1000.0F - pig.getHealth();
			double divisor = TowerMobs.stateOf(zombie).healthDivisor();
			double expectedLost = 20.0 / divisor / (1.0 + TowerBuffs.EXTRA_HEALTH_PER_LEVEL * TowerBuffs.level(GUARDS_FLOOR));
			double expectedReflect = lost * Math.min(TowerBuffs.THORNS_CAP, TowerBuffs.THORNS_PER_LEVEL * TowerBuffs.level(GUARDS_FLOOR));
			TowerTestSupport.result("mobGuards", "lost=" + lost + "/" + expectedLost + " reflected=" + reflected + "/" + expectedReflect);
			helper.assertTrue(Math.abs(lost - expectedLost) < 0.05, "the buffed mob lost " + lost + ", not " + expectedLost);
			helper.assertTrue(Math.abs(reflected - expectedReflect) < 0.05, "thorns returned " + reflected + ", not " + expectedReflect);
		} finally {
			TowerBuffs.pinMobBuffs(GUARDS_FLOOR, null);
			zombie.discard();
			pig.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void anExtraLifeRaisesABossFloorsMobOnce(final GameTestHelper helper) {
		extraLives(helper, LIFE_FLOOR);
	}

	/** One more life for each five hundred floors: a deep floor's mob rises three times. */
	@GameTest
	public void aDeepFloorsMobRisesOnceMorePerFiveHundredFloors(final GameTestHelper helper) {
		extraLives(helper, DEEP_LIFE_FLOOR);
	}

	private static void extraLives(final GameTestHelper helper, final int own) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		Zombie zombie = mobOn(tower, floor, 20.0, own);
		int lives = 1 + own / TowerBuffs.FLOORS_PER_EXTRA_LIFE;
		int stood = 0;
		try {
			TowerBuffs.pinMobBuffs(own, List.of(TowerBuffs.MobBuff.EXTRA_LIFE, TowerBuffs.MobBuff.SPEED));
			for (int i = 0; i <= lives; i++) {
				zombie.hurtServer(tower, tower.damageSources().generic(), Float.MAX_VALUE);
				stood += zombie.isAlive() ? 1 : 0;
				zombie.damageCooldownTime = 0;
			}
		} finally {
			TowerBuffs.pinMobBuffs(own, null);
			zombie.discard();
		}
		TowerTestSupport.result("extraLife", "own=" + own + " lives=" + lives + " stood=" + stood);
		helper.assertTrue(stood == lives, "the mob rose " + stood + " times, not " + lives);
		helper.succeed();
	}
}
