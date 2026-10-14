package com.stella.mobs;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A bow skeleton aims over a mob that stands in its line of fire. A zombie stands between the skeleton and its
 * target, and the skeleton shoots ten arrows through the vanilla ranged attack. The health that each zombie loses
 * tells which one the arrow hit, since an arrow hit costs health and an arrow that flies over costs none.
 */
public class ArcherClearanceGameTest {
	private static final String TEST = "archerClearance";
	private static final int MAX_TICKS = 300;
	private static final int SHOTS = 10;
	// Past the 10 ticks of hit immunity, and past the arrow's flight.
	private static final int SHOT_INTERVAL_TICKS = 14;
	private static final int MOST_OF_TEN = 6;
	private static final float ARROW_POWER = 1.6F;
	private static final int X = 20;
	private static final int SHOOTER_Z = 10;
	private static final int BLOCKER_Z = 13;
	private static final int TARGET_Z = 17;

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void arrowFliesOverAMobInTheLineOfFire(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		Skeleton shooter = scene.mob(EntityTypes.SKELETON, X, MeleeKitFloor.SPAWN_Y, SHOOTER_Z);
		shooter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		Zombie blocker = scene.mob(EntityTypes.ZOMBIE, X, MeleeKitFloor.SPAWN_Y, BLOCKER_Z);
		Zombie target = scene.mob(EntityTypes.ZOMBIE, X, MeleeKitFloor.SPAWN_Y, TARGET_Z);
		for (Mob mob : new Mob[] {shooter, blocker, target}) {
			mob.setNoAi(true);
		}

		for (Zombie zombie : new Zombie[] {blocker, target}) {
			zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Arena.OPPONENT_HEALTH);
			zombie.setHealth(Arena.OPPONENT_HEALTH);
		}

		int[] ticks = {0};
		int[] onTarget = {0};
		int[] onBlocker = {0};
		float[] before = new float[2];
		helper.onEachTick(() -> {
			if (!scene.ticking()) {
				return;
			}

			int shot = ticks[0] / SHOT_INTERVAL_TICKS;
			int phase = ticks[0]++ % SHOT_INTERVAL_TICKS;
			if (shot >= SHOTS) {
				return;
			}

			if (phase == 0) {
				before[0] = target.getHealth();
				before[1] = blocker.getHealth();
				shooter.performRangedAttack(target, ARROW_POWER);
			} else if (phase == SHOT_INTERVAL_TICKS - 1) {
				if (target.getHealth() < before[0]) {
					onTarget[0]++;
				}

				if (blocker.getHealth() < before[1]) {
					onBlocker[0]++;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ticks[0] >= SHOTS * SHOT_INTERVAL_TICKS, "the shots have not finished yet");
			String seen = "shots=" + SHOTS + " onTarget=" + onTarget[0] + " onBlocker=" + onBlocker[0];
			StellaMobs.LOGGER.info("[BEHAVIOR] test={}.arrowFliesOverAMobInTheLineOfFire RESULT {}", TEST, seen);
			scene.end();
			helper.assertTrue(onTarget[0] >= MOST_OF_TEN, "fewer than " + MOST_OF_TEN + " arrows reached the target: " + seen);
		});
	}
}
