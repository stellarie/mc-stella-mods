package com.stella.tower;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.scaling.SelfScaled;
import com.stella.mobs.scaling.ThreatState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** A tower mob is a mob of level floor + 19, and the mobs mod's curves do the rest. */
public class TowerLevelGameTest {
	private static final int[] FLOORS = {1, 10, 200};
	private static final float HIT = 3.0F;

	/** Pinned apart from the production constant, so a change to it fails here. */
	private static int levelOf(final int floor) {
		return floor + 19;
	}

	@GameTest
	public void levelFollowsTheFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		for (int floor : FLOORS) {
			Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(zombie, floor, TowerElement.FIRE);
			int level = ThreatState.level(zombie);
			boolean tagged = zombie.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG)
				&& zombie.entityTags().contains(StellaMobs.SELF_SCALED_TAG);
			TowerTestSupport.result("levelOfFloor", "floor=" + floor + " level=" + level + " want=" + levelOf(floor) + " tagged=" + tagged);
			zombie.discard();
			helper.assertTrue(level == levelOf(floor), "a floor " + floor + " mob is level " + level + ", not " + levelOf(floor));
			helper.assertTrue(tagged, "a floor " + floor + " mob lacks the no-roll tag or the self-scaled tag");
		}
		helper.succeed();
	}

	@GameTest
	public void healthFollowsTheLevel(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		TowerRank[] ranks = {TowerRank.MOB, TowerRank.MOB, TowerRank.MOB, TowerRank.ELITE, TowerRank.BOSS};
		int[] floors = {FLOORS[0], FLOORS[1], FLOORS[2], FLOORS[1], FLOORS[0]};
		for (int i = 0; i < floors.length; i++) {
			Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
			double base = zombie.getAttributeBaseValue(Attributes.MAX_HEALTH);
			TowerMobs.makeTowerMob(zombie, floors[i], TowerElement.ICE, ranks[i]);
			double want = TowerTestSupport.floorHealth(base, floors[i], ranks[i]);
			double held = zombie.getMaxHealth();
			double effective = held * TowerMobs.stateOf(zombie).healthDivisor();
			TowerTestSupport.result("healthOfLevel", "floor=" + floors[i] + " rank=" + ranks[i] + " held=" + held + " effective=" + effective + " want=" + want);
			zombie.discard();
			helper.assertTrue(Math.abs(effective - want) < want * 1.0E-6, "a floor " + floors[i] + " " + ranks[i] + " zombie has " + effective + " health, not " + want);
			helper.assertTrue(Math.abs(held - Math.min(want, TowerMobs.healthCap())) < 0.01, "its attribute holds " + held + ", not " + Math.min(want, TowerMobs.healthCap()));
		}
		helper.succeed();
	}

	/** A melee hit and a projectile hit each take the level path once, then the rank's damage factor. */
	@GameTest
	public void hitsFollowTheLevel(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int home = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, home);
		Vec3 at = TowerLayout.arrival(tower, home);
		TowerRank[] ranks = {TowerRank.MOB, TowerRank.ELITE};
		int floor = 50;
		for (TowerRank rank : ranks) {
			double expected = HIT * SelfScaled.factor(levelOf(floor)) * rank.damageFactor();
			Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(zombie, floor, TowerElement.DARK, rank);
			zombie.snapTo(at);
			zombie.setNoAi(true);
			tower.addFreshEntity(zombie);
			Skeleton skeleton = EntityTypes.SKELETON.create(tower, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(skeleton, floor, TowerElement.DARK, rank);
			skeleton.snapTo(at);
			skeleton.setNoAi(true);
			tower.addFreshEntity(skeleton);
			float melee = loss(tower, at, zombie.damageSources().mobAttack(zombie));
			Arrow arrow = new Arrow(tower, skeleton, new ItemStack(Items.ARROW), null);
			float shot = loss(tower, at, tower.damageSources().arrow(arrow, skeleton));
			TowerTestSupport.result("hitsOfLevel", "rank=" + rank + " melee=" + melee + " arrow=" + shot + " expected=" + expected);
			zombie.discard();
			skeleton.discard();
			helper.assertTrue(Math.abs(melee - expected) < expected * 1.0E-3, "a floor " + floor + " " + rank + " melee hit took " + melee + ", not " + expected);
			helper.assertTrue(Math.abs(shot - expected) < expected * 1.0E-3, "a floor " + floor + " " + rank + " arrow took " + shot + ", not " + expected);
		}
		helper.succeed();
	}

	private static float loss(final ServerLevel tower, final Vec3 at, final DamageSource source) {
		Pig pig = TowerTestSupport.unrolled(EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND));
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		pig.setHealth(1000.0F);
		pig.snapTo(at.add(2.0, 0.0, 0.0));
		tower.addFreshEntity(pig);
		pig.hurtServer(tower, source, HIT);
		float lost = 1000.0F - pig.getHealth();
		pig.discard();
		return lost;
	}
}
