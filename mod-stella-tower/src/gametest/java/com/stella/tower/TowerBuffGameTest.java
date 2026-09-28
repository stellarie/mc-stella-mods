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

	private static Zombie mobOn(final ServerLevel tower, final int room, final double dx, final int own) {
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, own, TowerElement.WIND);
		zombie.snapTo(TowerLayout.arrival(room).add(dx, 0.0, 0.0));
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
			Vec3 elsewhere = TowerLayout.arrival(away);
			player.teleportTo(tower, elsewhere.x, elsewhere.y, elsewhere.z, Set.of(), 0.0F, 0.0F, true);
		});
		// The buff is taken at the next refresh after leaving, well inside a dose's length.
		helper.runAfterDelay(40L + TowerBuffs.REFRESH_TICKS * 2L, () -> {
			boolean lingers = player.hasEffect(buff.effect());
			TowerTestSupport.result("playerBuff", "buff=" + buff + " had=" + had[0] + " amplifier=" + amplifier[0] + "/" + buff.amplifier(floor) + " lingers=" + lingers);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(had[0] && amplifier[0] == buff.amplifier(floor), "the player carried " + buff + " at " + amplifier[0] + ", not " + buff.amplifier(floor));
			helper.assertFalse(lingers, "the buff outlasted the player's leaving the floor");
			helper.succeed();
		});
	}

	@GameTest
	public void buffsGrowWithTheFloorWithinTheirCeilings(final GameTestHelper helper) {
		int low = TowerBuffs.PlayerBuff.STRENGTH.amplifier(10);
		int high = TowerBuffs.PlayerBuff.STRENGTH.amplifier(1000);
		int resistance = TowerBuffs.PlayerBuff.RESISTANCE.amplifier(1000);
		TowerTestSupport.result("buffLevels", "low=" + low + " high=" + high + " resistance=" + resistance);
		helper.assertTrue(low == 0 && high == TowerBuffs.level(1000) - 1, "strength at floors 10 and 1000 is " + low + " and " + high);
		helper.assertTrue(resistance == 2, "resistance at floor 1000 is " + resistance + ", past its ceiling");
		helper.succeed();
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
		Pig pig = EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND);
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
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		Zombie zombie = mobOn(tower, floor, 20.0, LIFE_FLOOR);
		int lives = 1 + LIFE_FLOOR / TowerBuffs.FLOORS_PER_EXTRA_LIFE;
		int stood = 0;
		try {
			TowerBuffs.pinMobBuffs(LIFE_FLOOR, List.of(TowerBuffs.MobBuff.EXTRA_LIFE, TowerBuffs.MobBuff.SPEED));
			for (int i = 0; i <= lives; i++) {
				zombie.hurtServer(tower, tower.damageSources().generic(), Float.MAX_VALUE);
				stood += zombie.isAlive() ? 1 : 0;
				zombie.damageCooldownTime = 0;
			}
		} finally {
			TowerBuffs.pinMobBuffs(LIFE_FLOOR, null);
			zombie.discard();
		}
		TowerTestSupport.result("extraLife", "lives=" + lives + " stood=" + stood);
		helper.assertTrue(stood == lives, "the mob rose " + stood + " times, not " + lives);
		helper.succeed();
	}
}
