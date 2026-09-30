package com.stella.rpg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class RpgConfigGameTest {
	@GameTest
	public void badEntryFallsBack(final GameTestHelper helper) throws IOException {
		Path file = Files.createTempFile("rpg-stats", ".json");
		try {
			Files.writeString(file, "{\"melee_damage\":{\"max\":-1,\"half\":200},\"attack_speed\":{\"max\":2,\"half\":100}}", StandardCharsets.UTF_8);
			List<String> errors = new ArrayList<>();
			RpgConfig config = RpgConfigStore.load(file, errors::add);
			RpgConfigStore.set(config);
			double melee = RpgCurve.meleeMultiplier(100);
			double speed = RpgCurve.attackSpeedBonus(100);
			RpgTestSupport.result("badEntryFallsBack", "errors=" + errors + " melee=" + melee + " speed=" + speed);
			helper.assertTrue(errors.size() == 1, "errors=" + errors);
			helper.assertTrue(errors.get(0).contains("melee_damage"), "error lacks the key: " + errors.get(0));
			helper.assertTrue(Math.abs(melee - 1.5) < 1e-9, "melee fell back to " + melee);
			helper.assertTrue(Math.abs(speed - 1.0) < 1e-9, "the valid entry was lost: " + speed);
		} finally {
			RpgConfigStore.set(null);
			Files.deleteIfExists(file);
		}
		helper.succeed();
	}

	@GameTest
	public void zeroHalfFallsBack(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig config = RpgConfigStore.parse("{\"crit_chance\":{\"max\":0.5,\"half\":0},\"crit_multiplier\":0.5}", errors::add);
		helper.assertTrue(errors.size() == 2, "errors=" + errors);
		helper.assertTrue(config.equals(RpgConfig.defaults()), "config=" + config);
		helper.succeed();
	}

	@GameTest
	public void missingFileWritesDefaults(final GameTestHelper helper) throws IOException {
		Path dir = Files.createTempDirectory("rpg-config");
		Path file = dir.resolve("nested").resolve("stats.json");
		try {
			List<String> errors = new ArrayList<>();
			RpgConfig config = RpgConfigStore.load(file, errors::add);
			helper.assertTrue(errors.isEmpty(), "errors=" + errors);
			helper.assertTrue(Files.exists(file), "defaults were not written");
			helper.assertTrue(config.equals(RpgConfig.defaults()), "config=" + config);
			helper.assertTrue(RpgConfigStore.load(file, errors::add).equals(RpgConfig.defaults()) && errors.isEmpty(), "written file did not parse back");
		} finally {
			Files.deleteIfExists(file);
			Files.deleteIfExists(file.getParent());
			Files.deleteIfExists(dir);
		}
		helper.succeed();
	}
}
