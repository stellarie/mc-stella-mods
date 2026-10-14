package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.mixin.MobTargetSelectorAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A shielding zombie keeps its eyes on its target until a player hits it from behind. The player is not the target,
 * and the zombie has no retaliation goal, so only the shield goal can turn it. The zombie must face the player,
 * body and head, within ten ticks and with the shield still up.
 */
public class ShieldFacingGameTest {
	private static final String TEST = "shieldFacing";
	private static final int MAX_TICKS = 200;
	private static final int TURN_WITHIN_TICKS = 10;
	private static final float FACING_TOLERANCE_DEGREES = 20.0F;
	private static final float HIT_DAMAGE = 1.0F;
	private static final int X = 20;
	private static final int ZOMBIE_Z = 20;
	private static final int DECOY_Z = 25;
	private static final double PLAYER_Z = 15.5;

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void shieldBearerTurnsToAnAttackerBehindIt(final GameTestHelper helper) {
		MeleeKitFloor scene = MeleeKitFloor.build(helper);
		Zombie zombie = scene.mob(EntityTypes.ZOMBIE, X, MeleeKitFloor.SPAWN_Y, ZOMBIE_Z);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		BehaviorInstaller.installShield(zombie);
		// Without retaliation the target stays the decoy in front.
		((MobTargetSelectorAccessor) zombie).stellaMobs$targetSelector().removeAllGoals(goal -> true);
		Zombie decoy = scene.mob(EntityTypes.ZOMBIE, X, MeleeKitFloor.SPAWN_Y, DECOY_Z);
		decoy.setNoAi(true);
		ServerPlayer player = scene.player(X + 0.5, PLAYER_Z);
		int[] hitTick = {-1};
		int[] facedTick = {-1};
		boolean[] shieldUpWhenFaced = {false};

		helper.onEachTick(() -> {
			zombie.setTarget(decoy);
			if (hitTick[0] >= 0 && facedTick[0] < 0 && facing(zombie, player)) {
				facedTick[0] = zombie.tickCount;
				shieldUpWhenFaced[0] = zombie.isBlocking();
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(scene.ticking() && zombie.isBlocking(), "the shield never went up"))
			.thenExecute(() -> {
				helper.assertFalse(facing(zombie, player), "the zombie already faces the player, so the check proves nothing");
				hitTick[0] = zombie.tickCount;
				zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT_DAMAGE);
			})
			.thenWaitUntil(() -> helper.assertTrue(facedTick[0] >= 0, "the zombie never turned to the attacker"))
			.thenExecute(() -> {
				int turned = facedTick[0] - hitTick[0];
				String seen = "turnedInTicks=" + turned + " shieldUp=" + shieldUpWhenFaced[0] + " yaw=" + zombie.getYRot()
					+ " head=" + zombie.getYHeadRot() + " body=" + zombie.yBodyRot;
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.shieldBearerTurnsToAnAttackerBehindIt RESULT {}", TEST, seen);
				scene.end();
				helper.assertTrue(turned <= TURN_WITHIN_TICKS, "the zombie took " + turned + " ticks to turn: " + seen);
				helper.assertTrue(shieldUpWhenFaced[0], "the shield was down when the zombie finished turning: " + seen);
			})
			.thenSucceed();
	}

	private static boolean facing(final Zombie zombie, final ServerPlayer player) {
		double dx = player.getX() - zombie.getX();
		double dz = player.getZ() - zombie.getZ();
		float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		return Math.abs(Mth.wrapDegrees(zombie.getYRot() - wanted)) < FACING_TOLERANCE_DEGREES
			&& Math.abs(Mth.wrapDegrees(zombie.getYHeadRot() - wanted)) < FACING_TOLERANCE_DEGREES
			&& Math.abs(Mth.wrapDegrees(zombie.yBodyRot - wanted)) < FACING_TOLERANCE_DEGREES;
	}
}
