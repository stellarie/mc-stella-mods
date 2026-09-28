package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A deeper floor's mob pays more: drops a thousandth more often, experience a hundredth more, per floor. */
public class TowerRewardGameTest {
	private static final int XP_FLOOR = 100;
	/** Deep enough that a golem's iron outgrows anything it drops unscaled. */
	private static final int DROP_FLOOR = 10_000;

	@GameTest
	public void aDeepFloorsMobGivesMoreExperience(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Zombie plain = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		Zombie tower = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(tower, XP_FLOOR, TowerElement.WIND);
		int base = plain.getExperienceReward(level, player);
		int paid = tower.getExperienceReward(level, player);
		long want = Math.round(base * TowerRewards.experienceFactor(XP_FLOOR));
		TowerTestSupport.result("towerExperience", "base=" + base + " paid=" + paid + " want=" + want);
		plain.discard();
		tower.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(TowerRewards.experienceFactor(XP_FLOOR) > 1.0, "floor " + XP_FLOOR + " grows no experience, so the case proves nothing");
		helper.assertTrue(paid == want, "a floor " + XP_FLOOR + " zombie pays " + paid + " experience, not " + want);
		helper.succeed();
	}

	@GameTest
	public void aDeepFloorsMobDropsMore(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(golem, DROP_FLOOR, TowerElement.HOLY);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		golem.snapTo(at);
		level.addFreshEntity(golem);
		golem.hurtServer(level, player.damageSources().playerAttack(player), 1.0E30F);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0), item -> item.getItem().is(Items.IRON_INGOT));
		int iron = drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
		// A golem always drops three iron at the least.
		int least = (int) (3 * TowerRewards.dropFactor(DROP_FLOOR));
		TowerTestSupport.result("towerDrops", "dead=" + !golem.isAlive() + " iron=" + iron + " least=" + least + " factor=" + TowerRewards.dropFactor(DROP_FLOOR));
		drops.forEach(ItemEntity::discard);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(!golem.isAlive(), "the golem did not die, so the case proves nothing");
		helper.assertTrue(TowerRewards.dropFactor(DROP_FLOOR) > 1.0, "floor " + DROP_FLOOR + " grows no drops, so the case proves nothing");
		helper.assertTrue(iron >= least, "a floor " + DROP_FLOOR + " golem dropped " + iron + " iron, fewer than " + least);
		helper.succeed();
	}

	/** Over many drops, a stack grows by the factor on average, a part item dropped whole by its chance. */
	@GameTest
	public void dropsGrowByTheFactorOnAverage(final GameTestHelper helper) {
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(5L);
		double factor = TowerRewards.dropFactor(1_500);
		int trials = 20_000;
		long total = 0;
		for (int i = 0; i < trials; i++) {
			List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>(List.of(new net.minecraft.world.item.ItemStack(Items.BONE, 1)));
			TowerRewards.scale(drops, factor, random);
			total += drops.stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
		}
		double mean = (double) total / trials;
		TowerTestSupport.result("dropAverage", "factor=" + factor + " mean=" + mean);
		helper.assertTrue(factor > 1.0 && Math.abs(mean - factor) < 0.05, "one bone grew to " + mean + " on average, not " + factor);
		helper.succeed();
	}
}
