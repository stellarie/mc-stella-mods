package com.stella.mobs;

import com.stella.mobs.behavior.DangerSense;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Danger sense warns a player once when an elite, a boss, or a far stronger mob targets them; an even match stays quiet. */
public class DangerSenseGameTest {
	@GameTest
	public void warnsOnce(final GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		var previousGear = com.stella.mobs.config.GearConfigStore.get();
		com.stella.mobs.config.GearConfigStore.set(com.stella.mobs.config.GearConfigParser.parse("{ \"entities\": {}, \"rolls\": { \"roll_chance\": 0.0, \"elite_chance\": 0.0 } }"));
		helper.runBeforeTestEnd(() -> com.stella.mobs.config.GearConfigStore.set(previousGear));
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		player.experienceLevel = 5;
		Zombie elite = zombie(helper, 5, 1.5);
		EliteState.markElite(elite, com.stella.mobs.gear.GearTier.IRON);
		Zombie even = zombie(helper, 6, 3.5);
		Zombie strong = zombie(helper, 15, 5.5);
		List<String> warnings = new ArrayList<>();
		DangerSense.setRecorder((who, mob, text) -> {
			if (who == player) {
				warnings.add(text);
			}
		});
		int afterElite;
		int afterRepeat;
		int afterEven;
		try {
			elite.setTarget(player);
			afterElite = warnings.size();
			elite.setTarget(null);
			elite.setTarget(player);
			afterRepeat = warnings.size();
			even.setTarget(player);
			afterEven = warnings.size();
			strong.setTarget(player);
		} finally {
			DangerSense.setRecorder(null);
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=dangerSense RESULT warnings={}", warnings);
		helper.assertTrue(afterElite == 1 && warnings.getFirst().contains("Elite"), "the elite warned " + warnings);
		helper.assertTrue(afterRepeat == 1, "a second target within the cooldown warned again: " + warnings);
		helper.assertTrue(afterEven == 1, "a mob one level above the player warned: " + warnings);
		helper.assertTrue(warnings.size() == 2 && warnings.getLast().contains("Lv 15"), "a mob ten levels above did not warn: " + warnings);
		helper.succeed();
	}

	private static Zombie zombie(final GameTestHelper helper, final int threat, final double x) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		((AttachmentTarget) zombie).setAttached(ThreatState.TYPE, new ThreatState(threat, 0, 0, 0));
		zombie.snapTo(helper.absoluteVec(new Vec3(x, 1.0, 1.5)), 0.0F, 0.0F);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}
}
