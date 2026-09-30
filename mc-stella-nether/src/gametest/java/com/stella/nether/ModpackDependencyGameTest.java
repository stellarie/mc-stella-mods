package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.gametest.framework.GameTestHelper;

/** The nether raid runs on the whole modpack: every sibling mod is loaded beside it and declared as required. */
public class ModpackDependencyGameTest {
	@GameTest
	public void everyModpackModIsLoaded(final GameTestHelper helper) {
		List<String> missing = StellaNether.MODPACK.stream().filter(id -> !FabricLoader.getInstance().isModLoaded(id)).toList();
		StellaNether.LOGGER.info("[BEHAVIOR] test=everyModpackModIsLoaded RESULT expected={} missing={}", StellaNether.MODPACK.size(), missing);
		helper.assertTrue(missing.isEmpty(), "modpack mods not loaded: " + missing);
		helper.succeed();
	}

	@GameTest
	public void everyModpackModIsARequiredDependency(final GameTestHelper helper) {
		List<String> required = FabricLoader.getInstance().getModContainer(StellaNether.MOD_ID).orElseThrow().getMetadata()
			.getDependencies().stream().filter(dependency -> dependency.getKind() == ModDependency.Kind.DEPENDS)
			.map(ModDependency::getModId).toList();
		List<String> undeclared = StellaNether.MODPACK.stream().filter(id -> !required.contains(id)).toList();
		StellaNether.LOGGER.info("[BEHAVIOR] test=everyModpackModIsARequiredDependency RESULT undeclared={}", undeclared);
		helper.assertTrue(undeclared.isEmpty(), "modpack mods not declared in depends: " + undeclared);
		helper.succeed();
	}
}
