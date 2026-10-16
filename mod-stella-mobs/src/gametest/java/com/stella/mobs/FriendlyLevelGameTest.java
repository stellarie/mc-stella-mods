package com.stella.mobs;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The {@code mod-stella-mobs:friendly-level} share: a friendly NPC levels with the nearest player and keeps pace. */
public class FriendlyLevelGameTest {
	private static final String KEY = "mod-stella-mobs:friendly-level";

	@SuppressWarnings("unchecked")
	private static Consumer<Mob> share() {
		return FabricLoader.getInstance().getObjectShare().get(KEY) instanceof Consumer<?> consumer ? (Consumer<Mob>) consumer : null;
	}

	@GameTest(maxTicks = 100)
	public void aFriendlyGolemLevelsWithTheNearestPlayerAndKeepsItsHealthShare(final GameTestHelper helper) {
		Consumer<Mob> refresh = share();
		helper.assertTrue(refresh != null, "the share " + KEY + " is not published");
		check(helper, refresh);
		helper.succeed();
	}

	private static void check(final GameTestHelper helper, final Consumer<Mob> refresh) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// The arena is far from 0,0, so the test stands at the spawn, where the area level is low.
		var spawn = helper.getLevel().getRespawnData().pos();
		player.snapTo(spawn.getX(), helper.getLevel().getSeaLevel() + 1, spawn.getZ(), 0.0F, 0.0F);
		player.experienceLevel = 80;
		Mob golem = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		golem.snapTo(player.position());

		refresh.accept(golem);
		int first = ThreatState.level(golem);
		double firstMultiplier = SpawnScaling.healthMultiplierOf(golem);
		golem.setHealth(golem.getMaxHealth() / 2.0F);
		float fraction = golem.getHealth() / golem.getMaxHealth();

		player.experienceLevel = 150;
		refresh.accept(golem);
		int second = ThreatState.level(golem);
		double secondMultiplier = SpawnScaling.healthMultiplierOf(golem);
		float after = golem.getHealth() / golem.getMaxHealth();
		AttributeInstance health = golem.getAttribute(Attributes.MAX_HEALTH);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=friendlyLevel RESULT level={}/{} healthMultiplier={}/{} fraction={}/{}",
			first, second, firstMultiplier, secondMultiplier, fraction, after);
		helper.assertTrue(first == 80, "the first level is " + first + ", not the player's 80");
		helper.assertTrue(second == 150, "the level did not follow the player: " + second);
		helper.assertTrue(Math.abs(firstMultiplier - SpawnScaling.tierMultiplier(80)) < 1.0e-9, "health multiplier " + firstMultiplier);
		helper.assertTrue(Math.abs(secondMultiplier - SpawnScaling.tierMultiplier(150)) < 1.0e-9, "health multiplier " + secondMultiplier);
		helper.assertTrue(secondMultiplier > firstMultiplier * 1.5, "no tier spike between 80 and 150");
		helper.assertTrue(Math.abs(after - fraction) < 0.02F, "the health fraction moved from " + fraction + " to " + after);
		helper.assertTrue(health != null && golem.entityTags().contains(StellaMobs.FRIENDLY_LEVEL_TAG), "the golem is not marked friendly");
		helper.assertTrue(!EliteState.isElite(golem), "a friendly golem was forced elite");
	}
}
