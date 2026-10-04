package com.stella.nether;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Blaze;

public class SovereignShareGameTest {
	@GameTest
	@SuppressWarnings("unchecked")
	public void shareAcceptsSovereignOnly(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(SovereignShare.OBJECT_SHARE_KEY);
		helper.assertTrue(shared instanceof Predicate<?>, "the sovereign share is missing: " + shared);
		Predicate<Entity> share = (Predicate<Entity>) shared;
		Blaze sovereign = EntityTypes.BLAZE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		sovereign.setAttached(BlazeSovereign.SOVEREIGN, 1);
		Blaze plain = EntityTypes.BLAZE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Entity zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		String detail = "sovereign=" + share.test(sovereign) + " plain=" + share.test(plain) + " zombie=" + share.test(zombie);
		StellaNether.LOGGER.info("[BEHAVIOR] test=shareAcceptsSovereignOnly RESULT {}", detail);
		helper.assertTrue(share.test(sovereign) && !share.test(plain) && !share.test(zombie), detail);
		helper.succeed();
	}
}
