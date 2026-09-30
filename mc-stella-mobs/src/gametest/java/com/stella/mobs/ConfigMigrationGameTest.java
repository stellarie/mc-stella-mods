package com.stella.mobs;

import com.google.gson.JsonObject;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RollSpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StrictJsonParser;

/**
 * B2: a config file written before a roll key existed must gain that key from the shipped default,
 * because the code default keeps the boss roll off and every existing install would otherwise never
 * meet a boss. The migration only ever adds a missing key: a hand-set value survives, a hand-set value
 * in the same file is never reformatted into another number, and the parser the rigs use never sees the
 * fill at all.
 */
public class ConfigMigrationGameTest {
	private static final String TEST = "configMigration";
	private static final double EPSILON = 1.0e-9;
	private static final Identifier SKELETON = Identifier.parse("minecraft:skeleton");
	/** The shape oniichan's own install has: two roll keys, no boss chance. */
	private static final String OLDER_CONFIG = """
		{
			"version": 1,
			"entities": {},
			"rolls": {
				"roll_chance": 0.3,
				"drop_chance_multiplier": 1.0
			}
		}""";
	/**
	 * A hand-set boss chance of zero beside keys the file omits. Zero is the value that matters most:
	 * a file that turns the boss roll off must stay off, and a fill that overwrote it would turn the
	 * feature back on for a user who said no.
	 */
	private static final String HAND_SET_CONFIG = """
		{
			"version": 1,
			"entities": {},
			"rolls": {
				"roll_chance": 0.3,
				"boss_chance": 0.0
			}
		}""";

	@GameTest(maxTicks = 20)
	public void anOlderConfigGainsTheShippedBossChance(final GameTestHelper helper) {
		withStoreRestored(() -> {
			// A path with no file: the store writes the shipped default there, and that file is the real
			// source of the shipped number. The older shape must read the same value, not the code default.
			Path fresh = file(helper, "fresh.json");
			delete(fresh);
			double shipped = GearConfigStore.loadFrom(fresh).rollFor(SKELETON).bossChance();
			Path older = write(helper, "older.json", OLDER_CONFIG);
			double migrated = GearConfigStore.loadFrom(older).rollFor(SKELETON).bossChance();
			boolean keyInFile = read(older).contains("boss_chance");

			result(
				"configMigration",
				"shipped=" + shipped + " migrated=" + migrated + " codeDefault=" + RollSpec.DEFAULT_BOSS_CHANCE
					+ " keyInFile=" + keyInFile + " file=" + older
			);

			helper.assertTrue(
				migrated > RollSpec.DEFAULT_BOSS_CHANCE,
				"an older config still reads the code default " + RollSpec.DEFAULT_BOSS_CHANCE
					+ ", so a ship with this file can never meet a boss"
			);
			helper.assertTrue(
				Math.abs(migrated - shipped) < EPSILON,
				"an older config reads " + migrated + " where a freshly written default file reads " + shipped
			);
			helper.assertTrue(
				keyInFile, "the filled key was not written into the file, so the user cannot see or tune it: " + older
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void aHandSetRollKeySurvivesTheMigration(final GameTestHelper helper) {
		withStoreRestored(() -> {
			Path handSet = write(helper, "hand-set.json", HAND_SET_CONFIG);
			RollSpec loaded = GearConfigStore.loadFrom(handSet).rollFor(SKELETON);
			JsonObject written = StrictJsonParser.parse(read(handSet)).getAsJsonObject();
			JsonObject rolls = written.getAsJsonObject("rolls");
			double fileBoss = rolls.get("boss_chance").getAsDouble();
			double fileRoll = rolls.get("roll_chance").getAsDouble();
			boolean versionKept = written.has("version");
			boolean keyFilled = rolls.has("elite_chance");

			result(
				"configPreserved",
				"loadedBoss=" + loaded.bossChance() + " fileBoss=" + fileBoss + " loadedRoll=" + loaded.rollChance()
					+ " fileRoll=" + fileRoll + " versionKept=" + versionKept + " keyFilled=" + keyFilled
			);

			helper.assertTrue(
				Math.abs(loaded.bossChance() - 0.0) < EPSILON,
				"the hand-set boss chance of 0.0 was rewritten to " + loaded.bossChance()
			);
			helper.assertTrue(
				Math.abs(fileBoss - 0.0) < EPSILON, "the file no longer turns the boss roll off: " + fileBoss
			);
			helper.assertTrue(
				Math.abs(fileRoll - 0.3) < EPSILON, "the file rewrote the hand-set roll chance of 0.3 to " + fileRoll
			);
			helper.assertTrue(versionKept, "the migration dropped the version key from the file");
			helper.assertTrue(keyFilled, "the migration filled no missing key, so the file was not migrated");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void theRigPathStillReadsTheCodeDefault(final GameTestHelper helper) {
		// A rig parses a JSON string and never touches the file, so the fill must live on the file path
		// and nowhere else. A rig that forgets to pin the chance measures a boss-free world, on purpose.
		double parsed = GearConfigParser.parse(OLDER_CONFIG).rollFor(SKELETON).bossChance();

		result(
			"configRigDefault",
			"parsed=" + parsed + " codeDefault=" + RollSpec.DEFAULT_BOSS_CHANCE + " source=parsed-json-string"
		);

		helper.assertTrue(
			Math.abs(parsed - RollSpec.DEFAULT_BOSS_CHANCE) < EPSILON,
			"the parser filled the boss chance to " + parsed + " where a rig expects the code default "
				+ RollSpec.DEFAULT_BOSS_CHANCE
		);
		helper.succeed();
	}

	/**
	 * The config store is global, so a test that reads a file through it leaves that file's values in
	 * front of every other test in the suite. The previous value goes back before this test ends.
	 */
	private static void withStoreRestored(final Runnable body) {
		GearConfig previous = GearConfigStore.get();

		try {
			body.run();
		} finally {
			GearConfigStore.set(previous);
		}
	}

	private static Path file(final GameTestHelper helper, final String name) {
		return helper.getLevel().getServer().getServerDirectory().resolve("config-migration").resolve(name);
	}

	private static Path write(final GameTestHelper helper, final String name, final String json) {
		Path path = file(helper, name);

		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return path;
	}

	private static void delete(final Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(final Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {}", TEST, test, detail);
	}
}
