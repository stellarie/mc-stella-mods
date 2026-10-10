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
			Files.writeString(file, "{\"crit_chance\":{\"max\":-1,\"half\":200},\"attack_speed\":{\"max\":2,\"half\":100}}", StandardCharsets.UTF_8);
			List<String> errors = new ArrayList<>();
			RpgConfig config = RpgConfigStore.load(file, errors::add);
			RpgConfigStore.set(config);
			double crit = RpgCurve.critChance(100);
			double speed = RpgCurve.attackSpeedBonus(100);
			RpgTestSupport.result("badEntryFallsBack", "errors=" + errors + " crit=" + crit + " speed=" + speed);
			helper.assertTrue(errors.size() == 1, "errors=" + errors);
			helper.assertTrue(errors.get(0).contains("crit_chance"), "error lacks the key: " + errors.get(0));
			helper.assertTrue(Math.abs(crit - 0.125) < 1e-9, "crit chance fell back to " + crit);
			helper.assertTrue(Math.abs(speed - 1.0) < 1e-9, "the valid entry was lost: " + speed);
		} finally {
			RpgConfigStore.set(null);
			Files.deleteIfExists(file);
		}
		helper.succeed();
	}

	@GameTest
	public void removedKeysAreIgnored(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig config = RpgConfigStore.parse(
			"{\"melee_damage\":{\"max\":1.5,\"half\":200},\"ranged_damage\":{\"max\":1.5,\"half\":200}}", errors::add);
		helper.assertTrue(errors.size() == 2, "errors=" + errors);
		helper.assertTrue(errors.get(0).contains("melee_damage") && errors.get(1).contains("ranged_damage"), "errors=" + errors);
		helper.assertTrue(config.equals(RpgConfig.defaults()), "config=" + config);
		helper.succeed();
	}

	@GameTest
	public void diceConfigOverrides(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig config = RpgConfigStore.parse(
			"{\"dice\":{\"base_count\":2,\"base_sides\":6,\"count_step\":25,\"sides_step\":0}}", errors::add);
		RpgConfig.DiceRules rules = config.dice();
		helper.assertTrue(errors.size() == 1 && errors.get(0).contains("dice.sides_step"), "errors=" + errors);
		helper.assertTrue(rules.equals(new RpgConfig.DiceRules(2, 6, 25, 100)), "rules=" + rules);
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
