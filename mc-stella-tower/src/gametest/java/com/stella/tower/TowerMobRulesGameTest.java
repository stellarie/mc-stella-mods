package com.stella.tower;

import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** What keeps a tower mob a tower mob of its own floor: no portals, no escapes, no shedding its strength. */
public class TowerMobRulesGameTest {
	private static void holdLoaded(final ServerLevel tower, final int floor) {
		BlockPos middle = TowerLayout.origin(floor).offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2);
		tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(middle), TowerFloors.CHUNK_RADIUS);
	}

	@GameTest
	public void aTowerMobCannotTakeAFloorsPortal(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		BlockPos inside = TowerLayout.portalInside(tower, floor).get(0);
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.WIND);
		zombie.snapTo(Vec3.atBottomCenterOf(inside));
		TeleportTransition mobWay = TowerBlocks.TOWER_PORTAL.getPortalDestination(tower, zombie, inside);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.teleportTo(tower, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		TeleportTransition playerWay = TowerBlocks.TOWER_PORTAL.getPortalDestination(tower, player, inside);
		TowerTestSupport.result("mobNoPortal", "mobWay=" + (mobWay != null) + " playerWay=" + (playerWay != null));
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(mobWay == null, "a tower mob can take the floor's portal up");
		helper.assertTrue(playerWay != null, "a player cannot take the floor's portal up");
		helper.succeed();
	}

	@GameTest
	public void noShulkerJoinsTheTower(final GameTestHelper helper) {
		boolean shulker = TowerMobs.pool(TowerMobs.OVERWORLD_ONLY_UNTIL + 1).contains(EntityTypes.SHULKER);
		TowerTestSupport.result("noShulker", "shulker=" + shulker);
		helper.assertFalse(shulker, "shulkers, which teleport out of a room, are in the tower's pool");
		helper.succeed();
	}

	/** Piglins and hoglins never zombify in the tower, so a tower hoglin stays one. */
	@GameTest(maxTicks = 400)
	public void aHoglinStaysAHoglinInTheTower(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		holdLoaded(tower, floor);
		Hoglin hoglin = EntityTypes.HOGLIN.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(hoglin, 30, TowerElement.FIRE);
		// A player elsewhere in the tower, on another test's floor, would otherwise despawn it as far away.
		hoglin.setPersistenceRequired();
		hoglin.snapTo(TowerTestSupport.awayFromArrivalUpTo(tower, floor, 20.0));
		tower.addFreshEntity(hoglin);
		UUID id = hoglin.getUUID();
		String[] fate = {"alive"};
		helper.onEachTick(() -> {
			// A portal ticket lapses after 300 ticks; renewed, the room outlives the zombify timer.
			if (helper.getTick() % 100 == 0) {
				holdLoaded(tower, floor);
			}
			if (fate[0].equals("alive") && hoglin.isRemoved()) {
				fate[0] = hoglin.getRemovalReason() + "@" + helper.getTick() + " hp=" + hoglin.getHealth() + " at=" + hoglin.position();
			}
		});
		helper.runAfterDelay(360L, () -> {
			Entity now = tower.getEntity(id);
			boolean still = now instanceof Hoglin && now.isAlive();
			TowerTestSupport.result("hoglinStays", "still=" + still + " now=" + (now == null ? "gone" : now.getType().toString()));
			if (now != null) {
				now.discard();
			}
			helper.assertTrue(still, "the tower hoglin turned into " + (now == null ? "nothing" : now.getType().toString()) + " fate=" + fate[0]);
			helper.succeed();
		});
	}

	@GameTest
	public void aConvertedTowerMobStaysATowerMob(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		int of = 300;
		Hoglin hoglin = EntityTypes.HOGLIN.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(hoglin, of, TowerElement.HOLY);
		hoglin.snapTo(TowerTestSupport.awayFromArrivalUpTo(tower, floor, 20.0));
		tower.addFreshEntity(hoglin);
		Mob zoglin = hoglin.convertTo(EntityTypes.ZOGLIN, ConversionParams.single(hoglin, true, false), converted -> { });
		TowerMobs.State state = zoglin == null ? null : TowerMobs.stateOf(zoglin);
		double want = zoglin == null ? 0.0 : TowerCurve.health(zoglin.getAttributeBaseValue(Attributes.MAX_HEALTH), of);
		double held = zoglin == null ? 0.0 : zoglin.getMaxHealth();
		TowerTestSupport.result("conversionKeeps", "state=" + state + " held=" + held + " want=" + want);
		if (zoglin != null) {
			zoglin.discard();
		}
		helper.assertTrue(state != null && state.floor() == of && state.element() == TowerElement.HOLY,
			"the zoglin a tower hoglin became is no tower mob: " + state);
		helper.assertTrue(Math.abs(held - Math.min(want, TowerMobs.MAX_HEALTH_ATTRIBUTE)) < 0.01, "the zoglin has " + held + " health, not its floor's " + want);
		helper.succeed();
	}

	@GameTest
	public void aJockeysRiderIsSentAway(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		Spider spider = EntityTypes.SPIDER.create(tower, EntitySpawnReason.COMMAND);
		Skeleton rider = EntityTypes.SKELETON.create(tower, EntitySpawnReason.JOCKEY);
		rider.startRiding(spider, true, false);
		TowerMobs.settle(spider);
		boolean riding = !spider.getPassengers().isEmpty();
		TowerTestSupport.result("jockeySettled", "riding=" + riding + " riderRemoved=" + rider.isRemoved());
		spider.discard();
		rider.discard();
		helper.assertTrue(!riding, "a spider entered the tower with its rider");
		helper.succeed();
	}

	@GameTest
	public void everySpawnedMobFitsWhereItStands(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		holdLoaded(tower, floor);
		int spawned = 0;
		int stuck = 0;
		// Pieces cover a few in a hundred spots, so enough tries that a missing check shows.
		for (int i = 0; i < 600; i++) {
			Mob mob = TowerMobs.spawnOne(tower, floor);
			if (mob != null) {
				spawned++;
				stuck += tower.noCollision(mob, mob.getBoundingBox()) ? 0 : 1;
				mob.discard();
			}
		}
		TowerTestSupport.result("spawnFits", "spawned=" + spawned + " stuck=" + stuck);
		helper.assertTrue(spawned > 0, "no mob could be placed, so the case proves nothing");
		helper.assertTrue(stuck == 0, stuck + " of " + spawned + " mobs started inside a block");
		helper.succeed();
	}

	/** A zombie leader's extra health is not stacked on its floor's: it has the floor's health and no more. */
	@GameTest
	public void aLeaderZombieHasItsFloorsHealthAndNoMore(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = 100;
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		zombie.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(
			new AttributeModifier(Identifier.withDefaultNamespace("leader_zombie_bonus"), 3.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		double want = TowerCurve.health(zombie.getAttributeBaseValue(Attributes.MAX_HEALTH), floor);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.VOID);
		double held = zombie.getMaxHealth();
		double divisor = TowerMobs.stateOf(zombie).healthDivisor();
		TowerTestSupport.result("leaderHealth", "held=" + held + " want=" + want + " effective=" + held * divisor);
		zombie.discard();
		helper.assertTrue(Math.abs(held - Math.min(want, TowerMobs.MAX_HEALTH_ATTRIBUTE)) < 0.05 && Math.abs(held * divisor - want) < 0.05,
			"a leader zombie shows " + held + " health (effective " + held * divisor + "), not its floor's " + want);
		helper.succeed();
	}
}
