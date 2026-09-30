package com.stella.tower;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The tower's health cap follows the live attribute cap and the rpg health scale, and keeps the rpg modifier. */
public class TowerHealthCapGameTest {
	private static final String SHARE = "mod-stella-rpg:health-scale";
	private static final Identifier RPG_SCALE = Identifier.fromNamespaceAndPath("mod-stella-rpg", "health_scale");

	/** One method, so no parallel test sees the share stub. */
	@GameTest
	public void capFollowsShareAndKeepsRpgModifier(final GameTestHelper helper) {
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		Object previous = share.get(SHARE);
		double withShare;
		double without;
		boolean kept;
		try {
			share.put(SHARE, 15.0);
			withShare = TowerMobs.healthCap();
			Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
			zombie.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(
				new AttributeModifier(RPG_SCALE, 14.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			TowerMobs.makeTowerMob(zombie, 10, TowerElement.HOLY);
			kept = zombie.getAttribute(Attributes.MAX_HEALTH).getModifier(RPG_SCALE) != null;
			share.remove(SHARE);
			without = TowerMobs.healthCap();
		} finally {
			if (previous == null) {
				share.remove(SHARE);
			} else {
				share.put(SHARE, previous);
			}
		}
		TowerTestSupport.result("capFollowsShareAndKeepsRpgModifier", "withShare=" + withShare + " without=" + without + " kept=" + kept);
		helper.assertTrue(Math.abs(withShare - 1024.0 / 15.0) < 1.0E-6, "cap with K=15 was " + withShare);
		helper.assertTrue(Math.abs(without - 1024.0) < 1.0E-6, "cap without the share was " + without);
		helper.assertTrue(kept, "the tower stripped the rpg health-scale modifier");
		helper.succeed();
	}
}
