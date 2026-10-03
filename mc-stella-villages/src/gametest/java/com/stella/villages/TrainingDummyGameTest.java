package com.stella.villages;

import com.stella.villages.dummy.DamageShare;
import com.stella.villages.dummy.TrainingDummy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The village training dummy: placement, one per village, respawn, no breakage, and the damage label. */
public class TrainingDummyGameTest {
	private static BlockPos arena(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		helper.setBlock(4, 1, 4, Blocks.BELL);
		return helper.absolutePos(new BlockPos(4, 1, 4));
	}

	@GameTest
	public void spawnsNearBell(final GameTestHelper helper) {
		BlockPos bell = arena(helper);
		ArmorStand stand = TrainingDummy.ensure(helper.getLevel(), UUID.randomUUID(), bell);
		helper.assertTrue(stand != null, "no dummy was placed");
		int dx = Math.abs(stand.getBlockX() - bell.getX());
		int dz = Math.abs(stand.getBlockZ() - bell.getZ());
		int ring = Math.max(dx, dz);
		result("spawnsNearBell", "ring=" + ring + " at=" + stand.blockPosition());
		helper.assertTrue(ring >= TrainingDummy.MIN_DISTANCE && ring <= TrainingDummy.MAX_DISTANCE, "the dummy stands " + ring + " from the bell");
		helper.succeed();
	}

	@GameTest
	public void oneDummyPerVillage(final GameTestHelper helper) {
		BlockPos bell = arena(helper);
		UUID village = UUID.randomUUID();
		ArmorStand first = TrainingDummy.ensure(helper.getLevel(), village, bell);
		ArmorStand second = TrainingDummy.ensure(helper.getLevel(), village, bell);
		int count = dummies(helper.getLevel(), bell).size();
		result("oneDummyPerVillage", "same=" + (first == second) + " count=" + count);
		helper.assertTrue(first == second && count == 1, "two passes left " + count + " dummies");
		helper.succeed();
	}

	@GameTest
	public void respawnsWhenMissing(final GameTestHelper helper) {
		BlockPos bell = arena(helper);
		UUID village = UUID.randomUUID();
		ArmorStand first = TrainingDummy.ensure(helper.getLevel(), village, bell);
		first.discard();
		ArmorStand second = TrainingDummy.ensure(helper.getLevel(), village, bell);
		result("respawnsWhenMissing", "second=" + second);
		helper.assertTrue(second != null && second != first && second.isAlive(), "the dummy did not come back");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void neverBreaks(final GameTestHelper helper) {
		BlockPos bell = arena(helper);
		ServerLevel level = helper.getLevel();
		ArmorStand stand = TrainingDummy.ensure(level, UUID.randomUUID(), bell);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<DamageSource> sources = List.of(
			level.damageSources().playerAttack(player),
			level.damageSources().generic(),
			level.damageSources().explosion(null, null),
			level.damageSources().onFire()
		);
		for (DamageSource source : sources) {
			stand.hurtServer(level, source, 6.0F);
		}
		result("neverBreaks", "alive=" + stand.isAlive() + " removed=" + stand.isRemoved());
		helper.assertTrue(stand.isAlive() && !stand.isRemoved(), "a hit broke the dummy");
		helper.succeed();
	}

	/** One method, so no parallel test sees another's share stub. */
	@GameTest
	public void recordsThroughTheShare(final GameTestHelper helper) {
		BlockPos bell = arena(helper);
		ServerLevel level = helper.getLevel();
		ArmorStand doubled = TrainingDummy.ensure(level, UUID.randomUUID(), bell);
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		Object previous = share.get(DamageShare.OBJECT_SHARE_KEY);
		String withShare;
		String without;
		try {
			share.put(DamageShare.OBJECT_SHARE_KEY, (Function<Object[], Float>) args -> ((Float) args[2]) * 2.0F);
			doubled.hurtServer(level, level.damageSources().generic(), 5.0F);
			withShare = doubled.getCustomName().getString();
			share.remove(DamageShare.OBJECT_SHARE_KEY);
			doubled.hurtServer(level, level.damageSources().generic(), 5.0F);
			without = doubled.getCustomName().getString();
		} finally {
			if (previous == null) {
				share.remove(DamageShare.OBJECT_SHARE_KEY);
			} else {
				share.put(DamageShare.OBJECT_SHARE_KEY, previous);
			}
		}
		result("recordsThroughTheShare", "withShare=" + withShare + " without=" + without);
		helper.assertTrue(withShare.contains("Last 10 "), "the share was not used: " + withShare);
		helper.assertTrue(without.contains("Last 5 ") && without.contains("2 hits"), "the raw amount was not used: " + without);
		helper.succeed();
	}

	@GameTest
	public void labelFormat(final GameTestHelper helper) {
		ArrayDeque<Float> hits = new ArrayDeque<>(List.of(10.0F, 12.5F));
		String label = TrainingDummy.label(hits);
		result("labelFormat", label);
		helper.assertTrue(label.equals("Avg 11.3 · Last 12.5 · 2 hits"), "label=" + label);
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void resetsAfterIdle(final GameTestHelper helper) {
		BlockPos bell = arena(helper);
		ServerLevel level = helper.getLevel();
		ArmorStand stand = TrainingDummy.ensure(level, UUID.randomUUID(), bell);
		stand.hurtServer(level, level.damageSources().generic(), 4.0F);
		stand.hurtServer(level, level.damageSources().generic(), 4.0F);
		helper.runAfterDelay(TrainingDummy.IDLE_TICKS + 2, () -> {
			stand.hurtServer(level, level.damageSources().generic(), 7.0F);
			String label = stand.getCustomName().getString();
			result("resetsAfterIdle", label);
			helper.assertTrue(label.contains("· 1 hits") && label.startsWith("Avg 7 "), "the window did not reset: " + label);
			helper.succeed();
		});
	}

	@GameTest
	public void survivesKill(final GameTestHelper helper) {
		BlockPos bell = arena(helper);
		ServerLevel level = helper.getLevel();
		UUID village = UUID.randomUUID();
		ArmorStand first = TrainingDummy.ensure(level, village, bell);
		first.kill(level);
		ArmorStand again = TrainingDummy.ensure(level, village, bell);
		result("survivesKill", "firstAlive=" + first.isAlive() + " same=" + (again == first));
		helper.assertTrue(first.isAlive() && again == first && TrainingDummy.is(first), "the kill command broke the dummy");
		helper.succeed();
	}

	@GameTest
	public void scaledUp(final GameTestHelper helper) {
		BlockPos bell = arena(helper);
		ArmorStand stand = TrainingDummy.ensure(helper.getLevel(), UUID.randomUUID(), bell);
		double scale = stand.getAttributeValue(Attributes.SCALE);
		result("scaledUp", "scale=" + scale);
		helper.assertTrue(Math.abs(scale - TrainingDummy.SCALE) < 1.0E-9, "scale=" + scale);
		helper.succeed();
	}

	private static List<ArmorStand> dummies(final ServerLevel level, final BlockPos bell) {
		return level.getEntitiesOfClass(ArmorStand.class, new AABB(bell).inflate(TrainingDummy.SEARCH_RADIUS), TrainingDummy::is);
	}

	private static void result(final String test, final String detail) {
		McStellaVillages.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
