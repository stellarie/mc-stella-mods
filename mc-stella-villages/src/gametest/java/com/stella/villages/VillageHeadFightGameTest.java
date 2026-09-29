package com.stella.villages;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** The village head fights a hostile mob that stands inside the bell radius. */
public final class VillageHeadFightGameTest {
	private static final float FOE_HEALTH = 500.0F;

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void headDamagesAHostileMobInsideTheBellRadius(final GameTestHelper helper) {
		fightFor(helper, "headDamagesAHostileMobInsideTheBellRadius", EntityTypes.ZOMBIE);
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void headDamagesAHostileMobThatIsNotAMonster(final GameTestHelper helper) {
		fightFor(helper, "headDamagesAHostileMobThatIsNotAMonster", EntityTypes.SLIME);
	}

	private static void fightFor(final GameTestHelper helper, final String test, final EntityType<? extends Mob> foeType) {
		helper.setTime(18_000L);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		helper.assertTrue(head != null, "the village has no head");
		BlockPos bell = PatrolTestSupport.anchor(helper);
		VillagePatrolGameTest.prepareMovementLane(helper, bell, 12);
		for (Mob member : PatrolTestSupport.patrols(helper)) {
			if (member != head) {
				member.setNoAi(true);
			}
		}
		Mob foe = foeType.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(foe != null, "the foe could not be created");
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(FOE_HEALTH);
		foe.setHealth(FOE_HEALTH);
		foe.setNoAi(true);
		foe.setPersistenceRequired();
		BlockPos spot = bell.offset(3, 0, 0);
		foe.snapTo(Vec3.atBottomCenterOf(spot));
		helper.assertTrue(helper.getLevel().addFreshEntity(foe), "the foe could not be inserted");
		head.snapTo(Vec3.atBottomCenterOf(bell.offset(1, 0, 0)));
		helper.succeedWhen(() -> {
			helper.assertTrue(foe.getHealth() < foe.getMaxHealth(), "the head did not hurt the " + foeType.toShortString()
				+ ": health=" + foe.getHealth() + "/" + foe.getMaxHealth() + " alive=" + foe.isAlive()
				+ " target=" + head.getTarget() + " distance=" + head.distanceTo(foe));
			PatrolTestSupport.result(test, "foe=" + foeType.toShortString() + " health=" + foe.getHealth()
				+ " target=" + head.getTarget());
			foe.discard();
		});
	}
}
