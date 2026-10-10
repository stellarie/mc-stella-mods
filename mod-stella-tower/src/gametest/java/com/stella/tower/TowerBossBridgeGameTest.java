package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** The boss of every fiftieth floor: the nether mod's Sovereign through its share, the Ember Tyrant without it. */
public class TowerBossBridgeGameTest {
	private static final String STUB_TAG = "test.sovereign";

	/** Builds a floor and clears it of what earlier runs left standing, so a boss finds room at its post. */
	private static void ready(final ServerLevel tower, final int floor) {
		TowerFloors.ensure(tower, floor);
		// A floor that stands from an earlier run has no chunk held: load its room, or no boss finds a place.
		BlockPos origin = TowerLayout.origin(floor);
		for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + TowerLayout.SIZE) >> 4; cx++) {
			for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + TowerLayout.SIZE) >> 4; cz++) {
				tower.getChunk(cx, cz);
			}
		}
		tower.getEntitiesOfClass(Mob.class, TowerMobs.room(floor)).forEach(net.minecraft.world.entity.Entity::discard);
	}

	private static void share(final Object factory) {
		FabricLoader.getInstance().getObjectShare().put(TowerBosses.SHARE_KEY, factory);
	}

	private static void unshare() {
		FabricLoader.getInstance().getObjectShare().remove(TowerBosses.SHARE_KEY);
	}

	private static BiFunction<ServerLevel, CompoundTag, Mob> stub(final List<Integer> levels) {
		return (level, tag) -> {
			levels.add(tag.getInt(TowerBosses.LEVEL_KEY).orElse(-1));
			Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			zombie.addTag(STUB_TAG);
			return zombie;
		};
	}

	/** Catches a Sovereign the tower changes, a level the factory is not given, a boss that pays no key, and a factory asked on the wrong floor. */
	@GameTest(maxTicks = 400)
	public void aSovereignFloorRaisesTheFactorysMobAsItIsAndPaysItsKey(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		List<Integer> levels = new ArrayList<>();
		share(stub(levels));
		try {
			ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
			double base = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND).getMaxHealth();
			int wrongLevel = 0;
			int changed = 0;
			int notBoss = 0;
			int noKey = 0;
			for (int floor : new int[] {50, 100, 150}) {
				ready(tower, floor);
				int before = levels.size();
				Mob boss = TowerElites.rise(tower, floor, TowerRank.BOSS);
				helper.assertTrue(boss != null && boss.entityTags().contains(STUB_TAG), "floor " + floor + " did not raise the factory's mob");
				wrongLevel += levels.size() == before + 1 && levels.get(before) == TowerMobs.levelOf(floor) ? 0 : 1;
				changed += Math.abs(boss.getMaxHealth() - base) > 1.0E-6 ? 1 : 0;
				TowerMobs.State state = TowerMobs.stateOf(boss);
				notBoss += state != null && state.rank() == TowerRank.BOSS && state.floor() == floor && boss.entityTags().contains(TowerMobs.TAG) ? 0 : 1;
				player.teleportTo(tower, boss.getX(), boss.getY(), boss.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
				boss.setLastHurtByPlayer(player, 100);
				boss.kill(tower);
				List<ItemEntity> drops = tower.getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(6.0));
				noKey += drops.stream().anyMatch(item -> item.getItem().getItem() instanceof FloorKeyItem) ? 0 : 1;
				drops.forEach(ItemEntity::discard);
			}
			int calls = levels.size();
			for (int floor : new int[] {30, 40, 70}) {
				ready(tower, floor);
				Mob boss = TowerElites.rise(tower, floor, TowerRank.BOSS);
				helper.assertTrue(boss != null && !boss.entityTags().contains(STUB_TAG), "floor " + floor + " raised the factory's mob");
				boss.discard();
			}
			TowerTestSupport.result("sovereignBridge", "calls=" + calls + "/3 later=" + levels.size() + " wrongLevel=" + wrongLevel + " changed=" + changed + " notBoss=" + notBoss + " noKey=" + noKey);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(calls == 3 && levels.size() == 3, "the factory was asked " + levels.size() + " times, not three");
			helper.assertTrue(wrongLevel == 0, wrongLevel + " floors gave the factory another level than their own");
			helper.assertTrue(changed == 0, "the tower changed the health of " + changed + " Sovereigns");
			helper.assertTrue(notBoss == 0, notBoss + " Sovereigns are not tower bosses of their floor");
			helper.assertTrue(noKey == 0, noKey + " Sovereigns paid no Floor Key");
		} finally {
			unshare();
		}
		helper.succeed();
	}

	/** Catches a floor with no boss when the share is absent, null or throws: the Ember Tyrant rises, and tells once. */
	@GameTest(maxTicks = 400)
	public void withoutTheFactoryTheEmberTyrantRises(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		unshare();
		BiFunction<ServerLevel, CompoundTag, Mob> none = (level, tag) -> null;
		BiFunction<ServerLevel, CompoundTag, Mob> breaks = (level, tag) -> {
			throw new IllegalStateException("the nether mod is broken");
		};
		Object[] shares = {null, none, breaks, "not a function"};
		int wrong = 0;
		for (Object each : shares) {
			if (each != null) {
				share(each);
			} else {
				unshare();
			}
			try {
				ready(tower, 200);
				Mob boss = TowerElites.rise(tower, 200, TowerRank.BOSS);
				TowerMobs.State state = boss == null ? null : TowerMobs.stateOf(boss);
				boolean tyrant = boss != null && boss.getType() == EntityTypes.BLAZE && boss.entityTags().contains(TowerBosses.TYRANT_TAG)
					&& Math.abs(boss.getAttributeBaseValue(Attributes.SCALE) - TowerBosses.TYRANT_SCALE) < 1.0E-9
					&& state != null && state.rank() == TowerRank.BOSS && state.floor() == 200
					&& boss.getCustomName() != null && boss.getCustomName().getString().equals("Ember Tyrant");
				if (!tyrant) {
					TowerTestSupport.result("tyrantWhy", "boss=" + boss + " scale=" + (boss == null ? "-" : boss.getAttributeBaseValue(Attributes.SCALE)) + " name="
						+ (boss == null || boss.getCustomName() == null ? "-" : boss.getCustomName().getString()) + " state=" + state);
				}
				wrong += tyrant ? 0 : 1;
				if (boss != null) {
					boss.discard();
				}
			} finally {
				unshare();
			}
		}
		TowerTestSupport.result("emberTyrant", "wrong=" + wrong + "/" + shares.length);
		helper.assertTrue(wrong == 0, wrong + " of " + shares.length + " cases raised no Ember Tyrant of the floor");
		helper.succeed();
	}

	/** Catches a tyrant whose hit leaves its target cold, or that floats away from the ground. */
	@GameTest(maxTicks = 200)
	public void theTyrantSetsItsTargetAlightAndIsDrawnDown(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		unshare();
		ready(tower, 250);
		Mob tyrant = TowerElites.rise(tower, 250, TowerRank.BOSS);
		helper.assertTrue(tyrant != null && tyrant.entityTags().contains(TowerBosses.TYRANT_TAG), "no Ember Tyrant rose");
		Pig pig = TowerTestSupport.unrolled(EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND));
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		pig.setHealth(1000.0F);
		pig.snapTo(tyrant.position().add(2.0, 0.0, 0.0));
		tower.addFreshEntity(pig);
		pig.hurtServer(tower, tyrant.damageSources().mobAttack(tyrant), 3.0F);
		int fire = pig.getRemainingFireTicks();
		pig.discard();
		Vec3 spot = tyrant.position();
		tyrant.snapTo(spot.x, spot.y + 8.0, spot.z);
		tyrant.setDeltaMovement(Vec3.ZERO);
		boolean drawn = TowerBosses.keepNearGround(tyrant);
		double pull = tyrant.getDeltaMovement().y;
		tyrant.snapTo(spot.x, spot.y + 1.0, spot.z);
		tyrant.setDeltaMovement(Vec3.ZERO);
		boolean leftBe = !TowerBosses.keepNearGround(tyrant);
		BlockPos at = tyrant.blockPosition();
		tyrant.discard();
		TowerTestSupport.result("tyrantHit", "fireTicks=" + fire + " drawn=" + drawn + " pull=" + pull + " leftBeNearGround=" + leftBe + " at=" + at);
		helper.assertTrue(fire >= (TowerBosses.TYRANT_BURN_SECONDS - 1) * 20, "the tyrant's target burns for " + fire + " ticks");
		helper.assertTrue(drawn && pull <= -TowerBosses.TYRANT_PULL + 1.0E-9, "a tyrant 8 blocks up is not drawn down: pull " + pull);
		helper.assertTrue(leftBe, "a tyrant one block up was drawn down");
		helper.succeed();
	}
}
