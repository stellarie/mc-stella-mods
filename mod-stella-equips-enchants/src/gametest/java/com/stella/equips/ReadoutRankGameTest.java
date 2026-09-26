package com.stella.equips;

import com.stella.equips.readout.ReadoutRank;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;

public class ReadoutRankGameTest {
	@GameTest
	public void theRankFollowsTheProviderAndIgnoresAnythingElse(final GameTestHelper helper) {
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(1, 2, 1));
		Object previous = FabricLoader.getInstance().getObjectShare().get(ReadoutRank.OBJECT_SHARE_KEY);
		Map<String, ReadoutRank> seen = new LinkedHashMap<>();
		try {
			seen.put("absent", rankWith(null, cow));
			seen.put("elite", rankWith(provider("elite"), cow));
			seen.put("boss", rankWith(provider("boss"), cow));
			seen.put("normal", rankWith(provider("normal"), cow));
			seen.put("unknown", rankWith(provider("legendary"), cow));
			seen.put("nullRank", rankWith(provider(null), cow));
			seen.put("wrongType", rankWith("elite", cow));
			seen.put("throws", rankWith((Function<Entity, String>) entity -> {
				throw new IllegalStateException("provider failure");
			}, cow));
		} finally {
			restore(previous);
			cow.discard();
		}

		Map<String, ReadoutRank> expected = new LinkedHashMap<>();
		expected.put("absent", ReadoutRank.NORMAL);
		expected.put("elite", ReadoutRank.ELITE);
		expected.put("boss", ReadoutRank.BOSS);
		expected.put("normal", ReadoutRank.NORMAL);
		expected.put("unknown", ReadoutRank.NORMAL);
		expected.put("nullRank", ReadoutRank.NORMAL);
		expected.put("wrongType", ReadoutRank.NORMAL);
		expected.put("throws", ReadoutRank.NORMAL);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutRankProvider RESULT seen={}", seen);
		helper.assertTrue(seen.equals(expected), "the provider values read as " + seen + " instead of " + expected);
		helper.succeed();
	}

	private static Function<Entity, String> provider(final String rank) {
		return entity -> rank;
	}

	private static ReadoutRank rankWith(final Object provider, final Entity entity) {
		restore(provider);
		return ReadoutRank.of(entity);
	}

	private static void restore(final Object value) {
		if (value == null) {
			FabricLoader.getInstance().getObjectShare().remove(ReadoutRank.OBJECT_SHARE_KEY);
		} else {
			FabricLoader.getInstance().getObjectShare().put(ReadoutRank.OBJECT_SHARE_KEY, value);
		}
	}
}
