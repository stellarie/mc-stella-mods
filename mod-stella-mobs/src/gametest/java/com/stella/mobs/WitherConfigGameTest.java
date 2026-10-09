package com.stella.mobs;

import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The Wither numbers load from a hand-editable file: a missing key falls back, a bad one is reported and skipped. */
public final class WitherConfigGameTest {
	@GameTest
	public void shippedDefaults(final GameTestHelper helper) {
		WitherConfig defaults = WitherConfig.DEFAULTS;
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherConfigDefaults RESULT base={} over={} vit={} ceiling={}",
			defaults.get(WitherConfig.LEVEL_BASE), defaults.get(WitherConfig.LEVEL_OVER_PLAYER),
			defaults.get(WitherConfig.STATS_VIT), defaults.get(WitherConfig.MOVE_CEILING));
		helper.assertTrue(defaults.getInt(WitherConfig.LEVEL_BASE) == 100 && defaults.getInt(WitherConfig.LEVEL_OVER_PLAYER) == 20,
			"the level defaults are not 100 and 20");
		helper.assertTrue(defaults.getInt(WitherConfig.STATS_VIT) == 200 && defaults.getInt(WitherConfig.STATS_MIN) == 50
			&& defaults.getInt(WitherConfig.STATS_MAX) == 150, "the stat defaults are not 200, 50 and 150");
		helper.assertTrue(defaults.get(WitherConfig.MOVE_CEILING) == 3.0, "the ceiling is not 3 blocks above the ground");
		helper.succeed();
	}

	@GameTest
	public void editsOverrideAndBadEntriesAreSkipped(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		WitherConfig parsed = WitherConfigStore.parse(
			"{\"level\":{\"base\":120,\"nope\":1,\"over_player\":\"high\"},\"stats\":{\"vit\":250},\"mystery\":{\"a\":1},\"move\":5}",
			errors::add
		);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherConfigParse RESULT base={} over={} vit={} errors={}",
			parsed.get(WitherConfig.LEVEL_BASE), parsed.get(WitherConfig.LEVEL_OVER_PLAYER), parsed.get(WitherConfig.STATS_VIT), errors.size());
		helper.assertTrue(parsed.getInt(WitherConfig.LEVEL_BASE) == 120 && parsed.getInt(WitherConfig.STATS_VIT) == 250, "an edit was not read");
		helper.assertTrue(parsed.getInt(WitherConfig.LEVEL_OVER_PLAYER) == 20, "a bad value replaced the default");
		helper.assertTrue(parsed.get(WitherConfig.MOVE_CEILING) == 3.0, "a missing key lost its default");
		helper.assertTrue(errors.size() == 4, "expected 4 reports (unknown key, bad value, unknown key in a section, bad section), got " + errors);
		WitherConfig notObject = WitherConfigStore.parse("[1]", errors::add);
		helper.assertTrue(notObject.equals(WitherConfig.DEFAULTS), "a non-object file did not give the defaults");
		helper.succeed();
	}

	@GameTest
	public void aMissingFileIsWrittenWithTheDefaults(final GameTestHelper helper) throws IOException {
		Path dir = Files.createTempDirectory("wither-config");
		Path file = dir.resolve("sub").resolve("wither.json");
		WitherConfig previous = WitherConfigStore.get();
		try {
			List<String> errors = new ArrayList<>();
			WitherConfig loaded = WitherConfigStore.loadFrom(file, errors::add);
			boolean written = Files.exists(file);
			String text = written ? Files.readString(file) : "";
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherConfigFile RESULT written={} errors={}", written, errors.size());
			helper.assertTrue(written && text.contains("\"level\"") && text.contains("\"over_player\""), "the defaults file was not written: " + text);
			helper.assertTrue(loaded.equals(WitherConfig.DEFAULTS) && errors.isEmpty(), "the written file did not read back as the defaults");
			Files.writeString(file, text.replace("\"base\": 100.0", "\"base\": 111.0"));
			WitherConfig edited = WitherConfigStore.loadFrom(file, errors::add);
			helper.assertTrue(edited.getInt(WitherConfig.LEVEL_BASE) == 111, "a hand edit was lost: " + edited.get(WitherConfig.LEVEL_BASE));
		} finally {
			WitherConfigStore.set(previous);
			Files.deleteIfExists(file);
			Files.deleteIfExists(file.getParent());
			Files.deleteIfExists(dir);
		}
		helper.succeed();
	}
}
