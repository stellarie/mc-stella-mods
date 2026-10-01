package com.stella.food;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Well Rested follows a full sleep that ends in the morning, and it multiplies a kill's experience by 1.2. */
public final class WellRestedGameTest {
	@GameTest
	public void grantsOnMorningWake(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, WellRested.earned(100, 24000L + 500L), true, "full sleep, morning");
		check(wrong, WellRested.earned(100, 1999L), true, "full sleep, edge of morning");
		check(wrong, WellRested.earned(99, 500L), false, "short sleep");
		check(wrong, WellRested.earned(100, 2000L), false, "woke after the morning");
		check(wrong, WellRested.earned(100, 13000L), false, "evening nap");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		WellRested.grant(player);
		var effect = player.getEffect(FoodEffects.WELL_RESTED);
		StellaFood.LOGGER.info("[BEHAVIOR] test=wellRestedGrant RESULT wrong={} duration={}", wrong, effect == null ? -1 : effect.getDuration());
		helper.assertTrue(wrong.isEmpty(), "the wake rule is wrong for " + wrong);
		helper.assertTrue(effect != null && effect.getDuration() == WellRested.DURATION_TICKS, "the grant gave " + effect);
		helper.succeed();
	}

	@GameTest
	public void boostsKillExperience(final GameTestHelper helper) {
		ServerPlayer rested = helper.makeMockServerPlayerInLevel();
		rested.setGameMode(GameType.SURVIVAL);
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		plain.setGameMode(GameType.SURVIVAL);
		WellRested.grant(rested);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1.0, 2.5));
		// Worn gear adds a random bonus on every call.
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			zombie.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
		}
		int base = zombie.getExperienceReward(helper.getLevel(), plain);
		int boosted = zombie.getExperienceReward(helper.getLevel(), rested);
		StellaFood.LOGGER.info("[BEHAVIOR] test=wellRestedXp RESULT base={} boosted={}", base, boosted);
		helper.assertTrue(base > 0 && boosted == (int) Math.round(base * WellRested.XP_FACTOR), "a kill gave " + boosted + " from " + base);
		helper.succeed();
	}

	private static void check(final List<String> wrong, final boolean got, final boolean expected, final String label) {
		if (got != expected) {
			wrong.add(label);
		}
	}
}
