package com.stella.learn;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.gametest.framework.GameTestHelper;

/** The learning mod runs on the mobs mod: it is loaded beside this one and declared as required. */
public class ModpackDependencyGameTest {
	private static final String MOBS = "mod-stella-mobs";

	@GameTest
	public void theMobsModIsLoaded(final GameTestHelper helper) {
		boolean loaded = FabricLoader.getInstance().isModLoaded(MOBS);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theMobsModIsLoaded RESULT loaded={}", loaded);
		helper.assertTrue(loaded, MOBS + " is not loaded");
		helper.succeed();
	}

	@GameTest
	public void theMobsModIsARequiredDependency(final GameTestHelper helper) {
		boolean required = FabricLoader.getInstance().getModContainer(StellaLearn.MOD_ID).orElseThrow().getMetadata()
			.getDependencies().stream()
			.anyMatch(dependency -> dependency.getKind() == ModDependency.Kind.DEPENDS && dependency.getModId().equals(MOBS));
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theMobsModIsARequiredDependency RESULT required={}", required);
		helper.assertTrue(required, MOBS + " is not declared in depends");
		helper.succeed();
	}
}
