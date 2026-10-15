package com.stella.villages;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.scaling.FriendlyLevels;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import com.stella.villages.patrol.NpcScaling;
import com.stella.villages.patrol.PatrolState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Friendly NPCs scale with the player. The rpg mod is not loaded here, so the tests read the level and the health
 * modifier the mobs mod gives, which an rpg stat roll and the health tiers both follow.
 */
public final class NpcScalingGameTest {
	private static final int FIRST_LEVEL = 80;
	private static final int SECOND_LEVEL = 150;
	private static final double EPSILON = 1.0e-9;

	/** The arena is far from 0,0, so its area level can pass the player's. The expected level takes the higher. */
	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void everyVillageMemberTakesTheBaseHealthAndTheLevel(final GameTestHelper helper) {
		String test = "everyVillageMemberTakesTheBaseHealthAndTheLevel";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = Vec3.atBottomCenterOf(PatrolTestSupport.anchor(helper).above());
		player.setPos(at.x, at.y, at.z);
		player.experienceLevel = SECOND_LEVEL;
		List<Mob> members = new ArrayList<>(PatrolTestSupport.patrols(helper));
		helper.assertTrue(members.stream().anyMatch(mob -> PatrolState.role(mob) == PatrolState.Role.HEAD)
			&& members.stream().anyMatch(mob -> PatrolState.role(mob) == PatrolState.Role.TRAINER), "the roster lacks the duke or the trainer");

		helper.succeedWhen(() -> {
			for (Mob member : members) {
				assertScaled(helper, member, Math.min(SpawnScaling.MAX_FIXED_LEVEL, Math.max(SpawnScaling.areaLevel(member), SECOND_LEVEL)));
			}
			PatrolTestSupport.result(test, "members=" + members.size());
		});
	}

	@GameTest(maxTicks = 200)
	public void aGolemTakesTheBaseHealthIsNotForcedEliteAndKeepsPace(final GameTestHelper helper) {
		// The area level is the distance from 0,0, so the golem and the player stand there and the player's level sets it.
		helper.getLevel().getChunk(0, 0);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(8.5, helper.getLevel().getSeaLevel() + 1, 8.5);
		player.experienceLevel = FIRST_LEVEL;
		Mob golem = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		golem.snapTo(8.5, helper.getLevel().getSeaLevel() + 1, 8.5, 0.0F, 0.0F);
		golem.setNoAi(true);
		helper.getLevel().addFreshEntity(golem);
		helper.assertTrue(!EliteState.isElite(golem), "a village golem was forced elite");
		assertScaled(helper, golem, FIRST_LEVEL);
		double firstAmount = golem.getAttribute(Attributes.MAX_HEALTH).getModifier(SpawnScaling.HEALTH_ID).amount();
		player.experienceLevel = SECOND_LEVEL;
		helper.succeedWhen(() -> {
			assertScaled(helper, golem, SECOND_LEVEL);
			double secondAmount = golem.getAttribute(Attributes.MAX_HEALTH).getModifier(SpawnScaling.HEALTH_ID).amount();
			helper.assertTrue(secondAmount > firstAmount, "the health tier did not rise with the level");
			PatrolTestSupport.result("aGolemKeepsPace", "amount=" + firstAmount + "/" + secondAmount);
			golem.discard();
		});
	}

	private static void assertScaled(final GameTestHelper helper, final Mob mob, final int level) {
		String who = PatrolTestSupport.typeId(mob) + " " + PatrolState.role(mob);
		helper.assertTrue(ThreatState.level(mob) == level, who + " is level " + ThreatState.level(mob) + ", not " + level + " area=" + SpawnScaling.areaLevel(mob) + " friendly=" + FriendlyLevels.levelFor(mob) + " at=" + mob.position());
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		helper.assertTrue(health.getBaseValue() == NpcScaling.BASE_HEALTH, who + " base health is " + health.getBaseValue());
		double amount = health.getModifier(SpawnScaling.HEALTH_ID) == null ? Double.NaN : health.getModifier(SpawnScaling.HEALTH_ID).amount();
		helper.assertTrue(Math.abs(amount - (SpawnScaling.healthMultiplier(level) - 1.0)) < EPSILON, who + " health modifier is " + amount);
	}
}
