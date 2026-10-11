package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.phys.Vec3;

/**
 * The tower's mobs are one side. A shot, a blast or a swing from one never hurts another, none takes another for its
 * target, and no hit sets one against another. The same shot from a mob that is not the tower's still lands, and a
 * ghost, which is not the tower's side, may hurt and be hurt.
 */
public class TowerPeaceGameTest {
	private static final int LOW = 5;

	private static <T extends Mob> T tower(final ServerLevel tower, final T mob, final Vec3 at) {
		TowerMobs.makeTowerMob(mob, LOW, TowerElement.WIND);
		mob.snapTo(at);
		mob.setNoAi(true);
		tower.addFreshEntity(mob);
		return mob;
	}

	private static <T extends Mob> T stranger(final ServerLevel tower, final T mob, final Vec3 at) {
		TowerTestSupport.unrolled(mob);
		// Kept: a mob that is not persistent is sent away at once when some other test left a player far off.
		mob.setPersistenceRequired();
		mob.snapTo(at);
		mob.setNoAi(true);
		tower.addFreshEntity(mob);
		return mob;
	}

	private static Arrow arrowFrom(final ServerLevel tower, final Mob owner, final Vec3 at, final Vec3 toward) {
		Arrow arrow = EntityTypes.ARROW.create(tower, EntitySpawnReason.COMMAND);
		arrow.setOwner(owner);
		arrow.snapTo(at);
		Vec3 way = toward.subtract(at).normalize();
		arrow.shoot(way.x, way.y, way.z, 2.5F, 0.0F);
		tower.addFreshEntity(arrow);
		return arrow;
	}

	private static BreezeWindCharge chargeFrom(final ServerLevel tower, final Breeze owner, final Vec3 at, final Vec3 toward) {
		BreezeWindCharge charge = new BreezeWindCharge(owner, tower);
		charge.snapTo(at);
		Vec3 way = toward.subtract(at).normalize();
		charge.shoot(way.x, way.y, way.z, 1.0F, 0.0F);
		tower.addFreshEntity(charge);
		return charge;
	}

	private static boolean unhurt(final Mob mob) {
		return mob.isAlive() && mob.getHealth() >= mob.getMaxHealth() && mob.getLastHurtByMob() == null && mob.getTarget() == null;
	}

	/**
	 * Catches friendly fire and the grudge it starts: a tower skeleton's arrow and a tower breeze's wind charge fly into a
	 * tower zombie and leave it unhurt, unmarked and untargeting, while the same shots from mobs that are not the tower's
	 * hurt a zombie and set it on the shooter. No tower mob can be given another for its target.
	 */
	@GameTest(maxTicks = 300)
	public void towerMobsNeverHurtOrTargetEachOther(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		Vec3 m = TowerTestSupport.arena(tower, floor, 12);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		// Four lanes along x. The two wind charges fly far apart: a blast pushes the charge beside it and hands it its owner.
		Skeleton ownSkeleton = tower(tower, EntityTypes.SKELETON.create(tower, EntitySpawnReason.COMMAND), m.add(-9.0, 0.0, -10.0));
		Zombie shotByOwn = tower(tower, EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND), m.add(-3.0, 0.0, -10.0));
		Skeleton otherSkeleton = stranger(tower, EntityTypes.SKELETON.create(tower, EntitySpawnReason.COMMAND), m.add(-9.0, 0.0, -6.0));
		Zombie shotByOther = tower(tower, EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND), m.add(-3.0, 0.0, -6.0));
		Breeze ownBreeze = tower(tower, EntityTypes.BREEZE.create(tower, EntitySpawnReason.COMMAND), m.add(-9.0, 0.0, -2.0));
		Zombie chargedByOwn = tower(tower, EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND), m.add(-3.0, 0.0, -2.0));
		Breeze otherBreeze = stranger(tower, EntityTypes.BREEZE.create(tower, EntitySpawnReason.COMMAND), m.add(-9.0, 0.0, 10.0));
		Zombie chargedByOther = tower(tower, EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND), m.add(-3.0, 0.0, 10.0));
		helper.runAfterDelay(20L, () -> {
			arrowFrom(tower, ownSkeleton, ownSkeleton.position().add(up.scale(1.5)).add(1.0, 0.0, 0.0), shotByOwn.position().add(up.scale(1.2)));
			arrowFrom(tower, otherSkeleton, otherSkeleton.position().add(up.scale(1.5)).add(1.0, 0.0, 0.0), shotByOther.position().add(up.scale(1.2)));
			chargeFrom(tower, ownBreeze, ownBreeze.position().add(up.scale(1.5)).add(1.0, 0.0, 0.0), chargedByOwn.position().add(up.scale(1.2)));
			chargeFrom(tower, otherBreeze, otherBreeze.position().add(up.scale(1.5)).add(1.0, 0.0, 0.0), chargedByOther.position().add(up.scale(1.2)));
		});
		helper.runAfterDelay(80L, () -> {
			boolean arrowKept = unhurt(shotByOwn);
			boolean arrowLands = shotByOther.getHealth() < shotByOther.getMaxHealth();
			boolean chargeKept = unhurt(chargedByOwn);
			boolean chargeLands = chargedByOther.getHealth() < chargedByOther.getMaxHealth();
			TowerTestSupport.result("peaceShots", "ownArrowKept=" + arrowKept + " strangerArrowLands=" + arrowLands + " ownChargeKept=" + chargeKept
				+ " strangerChargeLands=" + chargeLands);
			helper.assertTrue(arrowLands, "the control arrow from a mob that is not the tower's never hurt the zombie, so the case proves nothing: health " + shotByOther.getHealth() + "/" + shotByOther.getMaxHealth() + " by=" + shotByOther.getLastHurtByMob());
			helper.assertTrue(chargeLands, "the control wind charge from a mob that is not the tower's never hurt the zombie, so the case proves nothing: health " + chargedByOther.getHealth() + "/" + chargedByOther.getMaxHealth() + " by=" + chargedByOther.getLastHurtByMob());
			helper.assertTrue(arrowKept, "a tower skeleton's arrow hurt or angered a tower zombie: health " + shotByOwn.getHealth() + "/" + shotByOwn.getMaxHealth()
				+ " by=" + shotByOwn.getLastHurtByMob() + " target=" + shotByOwn.getTarget());
			helper.assertTrue(chargeKept, "a tower breeze's wind charge hurt or angered a tower zombie: health " + chargedByOwn.getHealth() + "/" + chargedByOwn.getMaxHealth()
				+ " by=" + chargedByOwn.getLastHurtByMob() + " target=" + chargedByOwn.getTarget());
			List<Mob> all = List.of(ownSkeleton, shotByOwn, ownBreeze, chargedByOwn, otherSkeleton, shotByOther, otherBreeze, chargedByOther);
			all.forEach(Mob::discard);
			// Every tower mob of the goal-driven kinds, set against every other by hand: none takes the other.
			List<Mob> kinds = new ArrayList<>();
			kinds.add(tower(tower, EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND), m.add(0.0, 0.0, -9.0)));
			kinds.add(tower(tower, EntityTypes.SKELETON.create(tower, EntitySpawnReason.COMMAND), m.add(2.0, 0.0, -9.0)));
			kinds.add(tower(tower, EntityTypes.CREEPER.create(tower, EntitySpawnReason.COMMAND), m.add(4.0, 0.0, -9.0)));
			kinds.add(tower(tower, EntityTypes.SHULKER.create(tower, EntitySpawnReason.COMMAND), m.add(6.0, 0.0, -9.0)));
			kinds.add(tower(tower, EntityTypes.SPIDER.create(tower, EntitySpawnReason.COMMAND), m.add(8.0, 0.0, -9.0)));
			int taken = 0;
			for (Mob hunter : kinds) {
				for (Mob prey : kinds) {
					if (hunter != prey) {
						hunter.setTarget(prey);
						taken += hunter.getTarget() == prey ? 1 : 0;
						hunter.setTarget(null);
					}
				}
			}
			Skeleton outsider = stranger(tower, EntityTypes.SKELETON.create(tower, EntitySpawnReason.COMMAND), m.add(0.0, 0.0, 4.0));
			kinds.get(0).setTarget(outsider);
			boolean outsiderTaken = kinds.get(0).getTarget() == outsider;
			TowerTestSupport.result("peaceTargets", "taken=" + taken + " outsiderTaken=" + outsiderTaken);
			kinds.forEach(Mob::discard);
			outsider.discard();
			helper.assertTrue(outsiderTaken, "a tower zombie cannot take a mob that is not the tower's for its target, so the case proves nothing");
			helper.assertTrue(taken == 0, "tower mobs took each other for targets " + taken + " times");
			helper.succeed();
		});
	}

	/**
	 * Catches a blast that spares nothing: a tower creeper's explosion leaves the tower zombie beside it unhurt, and the
	 * same blast from a creeper that is not the tower's hurts it.
	 */
	@GameTest(maxTicks = 300)
	public void aTowerCreepersBlastSparesTheTowersMobs(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		Vec3 m = TowerTestSupport.arena(tower, floor, 12);
		Creeper own = tower(tower, EntityTypes.CREEPER.create(tower, EntitySpawnReason.COMMAND), m.add(-8.0, 0.0, 0.0));
		Zombie beside = tower(tower, EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND), m.add(-6.5, 0.0, 0.0));
		Creeper other = stranger(tower, EntityTypes.CREEPER.create(tower, EntitySpawnReason.COMMAND), m.add(8.0, 0.0, 0.0));
		Zombie besideOther = tower(tower, EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND), m.add(6.5, 0.0, 0.0));
		helper.runAfterDelay(20L, () -> {
			own.ignite();
			other.ignite();
		});
		helper.runAfterDelay(100L, () -> {
			boolean kept = unhurt(beside);
			boolean hurt = !besideOther.isAlive() || besideOther.getHealth() < besideOther.getMaxHealth();
			TowerTestSupport.result("peaceBlast", "ownBlastSpared=" + kept + " strangerBlastHurt=" + hurt + " ownGone=" + own.isRemoved() + " strangerGone=" + other.isRemoved());
			helper.assertTrue(other.isRemoved() && own.isRemoved(), "a creeper never blew, so the case proves nothing");
			helper.assertTrue(hurt, "the control blast from a creeper that is not the tower's never hurt the zombie, so the case proves nothing");
			helper.assertTrue(kept, "a tower creeper's blast hurt or angered a tower zombie: health " + beside.getHealth() + "/" + beside.getMaxHealth());
			beside.discard();
			besideOther.discard();
			helper.succeed();
		});
	}

	/**
	 * Catches a ghost that is held to the tower's peace: a tower zombie's blow hurts a ghost and a ghost's spell hurts a
	 * tower zombie, and nothing a ghost casts or strikes with ever hurts a player.
	 */
	@GameTest(maxTicks = 100)
	public void ghostsAreNotOfTheTowersSideAndSpareThePlayers(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		Vec3 m = TowerTestSupport.arena(tower, floor, 6);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		player.teleportTo(tower, m.x, m.y, m.z, Set.of(), 0.0F, 0.0F, true);
		Zombie zombie = tower(tower, EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND), m.add(3.0, 0.0, 0.0));
		TowerGhost ghost = TowerGhosts.summon(tower, LOW, m.add(-3.0, 2.0, 0.0));
		helper.assertTrue(ghost != null, "no ghost could be raised");
		float ghostHealth = ghost.getHealth();
		ghost.hurtServer(tower, tower.damageSources().mobAttack(zombie), 2.0F);
		boolean ghostHurt = ghost.getHealth() < ghostHealth;
		float zombieHealth = zombie.getHealth();
		zombie.hurtServer(tower, tower.damageSources().indirectMagic(ghost, ghost), 2.0F);
		boolean zombieHurt = zombie.getHealth() < zombieHealth;
		float playerHealth = player.getHealth();
		boolean spell = player.hurtServer(tower, tower.damageSources().indirectMagic(ghost, ghost), 4.0F);
		boolean blow = player.hurtServer(tower, tower.damageSources().mobAttack(ghost), 4.0F);
		boolean unharmed = player.getHealth() == playerHealth;
		TowerTestSupport.result("peaceGhost", "ghostHurtByZombie=" + ghostHurt + " zombieHurtByGhost=" + zombieHurt + " spellLanded=" + spell + " blowLanded=" + blow
			+ " playerUnharmed=" + unharmed);
		ghost.discard();
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(ghostHurt, "a tower zombie cannot hurt a ghost");
		helper.assertTrue(zombieHurt, "a ghost cannot hurt a tower zombie");
		helper.assertTrue(!spell && !blow && unharmed, "a ghost hurt the player: spell=" + spell + " blow=" + blow + " health " + player.getHealth() + "/" + playerHealth);
		helper.succeed();
	}
}
