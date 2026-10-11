package com.stella.tower;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** How strong a floor makes its mobs: the S-curve, the per-floor growth, and the health past the cap. */
public class TowerThreatGameTest {
	private static final double EPSILON = 1.0E-6;

	@GameTest
	public void theCurveIsFlatAtTheGroundHalfAtTheMidpointAndDoneAtTheEnd(final GameTestHelper helper) {
		double ground = TowerCurve.s(0);
		double middle = TowerCurve.s((int) TowerCurve.MIDPOINT);
		double end = TowerCurve.s((int) TowerCurve.END);
		double beyond = TowerCurve.s((int) TowerCurve.END * 5);
		boolean rising = true;
		for (int floor = 1; floor <= TowerCurve.END; floor++) {
			rising &= TowerCurve.s(floor) >= TowerCurve.s(floor - 1);
		}
		TowerTestSupport.result("threatCurve", "ground=" + ground + " middle=" + middle + " end=" + end + " beyond=" + beyond + " rising=" + rising);
		helper.assertTrue(Math.abs(ground) < EPSILON && Math.abs(middle - 0.5) < EPSILON && Math.abs(end - 1.0) < EPSILON
			&& Math.abs(beyond - 1.0) < EPSILON && rising, "the curve is ground=" + ground + " middle=" + middle + " end=" + end + " beyond=" + beyond);
		helper.succeed();
	}

	@GameTest
	public void damageKeepsGrowingPastTheEnd(final GameTestHelper helper) {
		int end = (int) TowerCurve.END;
		double damageAtEnd = TowerCurve.damage(3.0, end);
		double damageBeyond = TowerCurve.damage(3.0, end + 100);
		double expectedDamage = 3.0 * TowerCurve.DAMAGE_MAX + TowerCurve.DAMAGE_PER_FLOOR * end;
		TowerTestSupport.result("threatGrowth", "damageAtEnd=" + damageAtEnd + " damageBeyond=" + damageBeyond);
		helper.assertTrue(Math.abs(damageAtEnd - expectedDamage) < EPSILON, "floor " + end + " gives damage " + damageAtEnd);
		helper.assertTrue(Math.abs(damageBeyond - damageAtEnd - TowerCurve.DAMAGE_PER_FLOOR * 100) < EPSILON, "past the end the floors stop growing");
		helper.succeed();
	}

	@GameTest
	public void aTowerMobCarriesItsFloorsHealthArmorAndSpeed(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = 100;
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		double baseHealth = zombie.getAttributeBaseValue(Attributes.MAX_HEALTH);
		double baseArmor = zombie.getAttributeValue(Attributes.ARMOR);
		double baseSpeed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.FIRE);
		double health = zombie.getMaxHealth() * TowerMobs.stateOf(zombie).healthDivisor();
		double armor = zombie.getAttributeValue(Attributes.ARMOR);
		double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double wantHealth = TowerTestSupport.floorHealth(baseHealth, floor, TowerRank.MOB);
		double wantArmor = Math.min(TowerCurve.ARMOR_CAP, baseArmor + TowerCurve.armorBonus(floor));
		double wantSpeed = baseSpeed * (1.0 + TowerCurve.speedBonus(floor));
		TowerTestSupport.result("mobScaled", "health=" + health + "/" + wantHealth + " armor=" + armor + "/" + wantArmor
			+ " speed=" + speed + "/" + wantSpeed + " full=" + (zombie.getHealth() == zombie.getMaxHealth()));
		zombie.discard();
		helper.assertTrue(Math.abs(health - wantHealth) < 0.01 && Math.abs(armor - wantArmor) < 0.01 && Math.abs(speed - wantSpeed) < 1.0E-4,
			"a floor " + floor + " zombie has health " + health + " armor " + armor + " speed " + speed);
		helper.succeed();
	}

	/** Past the health attribute's cap, the rest of a mob's size is taken off every hit it takes. */
	@GameTest
	public void aMobTooBigForTheHealthCapTakesLessFromEachHit(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = (int) TowerCurve.END;
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		double want = TowerTestSupport.floorHealth(zombie.getAttributeBaseValue(Attributes.MAX_HEALTH), floor, TowerRank.MOB);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.ICE);
		Vec3 at = TowerLayout.arrival(tower, TowerTestSupport.freshFloor());
		TowerFloors.ensure(tower, TowerLayout.floorAt(net.minecraft.core.BlockPos.containing(at)));
		zombie.snapTo(at);
		tower.addFreshEntity(zombie);
		float before = zombie.getHealth();
		zombie.hurtServer(tower, tower.damageSources().generic(), 100.0F);
		float lost = before - zombie.getHealth();
		double expected = 100.0 * TowerMobs.healthCap() / want;
		TowerTestSupport.result("healthPastCap", "max=" + zombie.getMaxHealth() + " want=" + want + " lost=" + lost + " expected=" + expected);
		zombie.discard();
		helper.assertTrue(zombie.getMaxHealth() <= TowerMobs.healthCap() && Math.abs(lost - expected) < 0.05,
			"a floor " + floor + " zombie of " + want + " health lost " + lost + " to a 100 hit, not " + expected);
		helper.succeed();
	}

	/** Whatever a tower mob hits with, the hit grows with its floor. */
	@GameTest
	public void aTowerMobHitsHarderOnAHigherFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = 50;
		int home = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, home);
		Vec3 at = TowerLayout.arrival(tower, home);
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.DARK);
		zombie.snapTo(at);
		tower.addFreshEntity(zombie);
		Pig pig = TowerTestSupport.unrolled(EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND));
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		pig.setHealth(1000.0F);
		pig.snapTo(at.add(2.0, 0.0, 0.0));
		tower.addFreshEntity(pig);
		pig.hurtServer(tower, zombie.damageSources().mobAttack(zombie), 3.0F);
		float lost = 1000.0F - pig.getHealth();
		double expected = 3.0 * com.stella.mobs.scaling.SelfScaled.factor(TowerTestSupport.levelOf(floor));
		TowerTestSupport.result("mobHitsHarder", "lost=" + lost + " expected=" + expected);
		zombie.discard();
		pig.discard();
		helper.assertTrue(Math.abs(lost - expected) < 0.05, "a floor " + floor + " zombie's 3 hit took " + lost + ", not " + expected);
		helper.succeed();
	}

	@GameTest
	public void theFirstTwentyFloorsHoldOnlyOverworldMobsAndNoWitch(final GameTestHelper helper) {
		Set<EntityType<?>> notOverworld = Set.of(EntityTypes.WITCH, EntityTypes.BLAZE, EntityTypes.WITHER_SKELETON,
			EntityTypes.PIGLIN_BRUTE, EntityTypes.MAGMA_CUBE, EntityTypes.ENDERMAN, EntityTypes.BREEZE, EntityTypes.SHULKER, EntityTypes.HOGLIN);
		int leaks = 0;
		for (int floor = 1; floor <= TowerMobs.OVERWORLD_ONLY_UNTIL; floor++) {
			for (EntityType<? extends Mob> type : TowerMobs.pool(floor)) {
				leaks += notOverworld.contains(type) ? 1 : 0;
			}
		}
		List<EntityType<? extends Mob>> later = TowerMobs.pool(TowerMobs.OVERWORLD_ONLY_UNTIL + 1);
		boolean opens = later.contains(EntityTypes.WITCH) && later.contains(EntityTypes.BLAZE) && later.contains(EntityTypes.ENDERMAN);
		TowerTestSupport.result("floorMix", "leaks=" + leaks + " early=" + TowerMobs.pool(1).size() + " later=" + later.size() + " opens=" + opens);
		helper.assertTrue(!TowerMobs.pool(1).isEmpty() && leaks == 0, "the first floors hold " + leaks + " mobs from outside the overworld");
		helper.assertTrue(opens, "floor " + (TowerMobs.OVERWORLD_ONLY_UNTIL + 1) + " does not open to witches, the nether and the end");
		helper.succeed();
	}
}
