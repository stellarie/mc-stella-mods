package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigParser;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerCurve;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class PowerConfigGameTest {
	private static final String[] BAD_DOCUMENTS = {
		"",
		"not json at all",
		"[]",
		"{}",
		"null",
		"{",
		"{\"version\": 1",
		"{\"level_cap\": \"fifty\"}",
		"{\"points_per_level\": 6}"
	};
	private static final int EDITED_LEVEL_CAP = 7;
	private static final int EDITED_SCAN_INTERVAL = 20;

	@GameTest
	public void bundledDocumentCarriesTheAcceptanceNumbers(final GameTestHelper helper) {
		PowerConfig expected = PowerFixtures.acceptanceConfig();
		PowerConfig actual = PowerConfigStore.bundled();
		helper.assertTrue(
			actual.equals(expected),
			"the bundled document parsed to " + actual + " but the acceptance numbers are " + expected
		);
		long levelFifty = PowerCurve.pointsForLevel(50, actual);
		helper.assertTrue(
			levelFifty == PowerFixtures.LEVEL_50_POINTS,
			"the bundled document puts level 50 at " + levelFifty + " points"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bundledNumbers RESULT levelCap={} pointsForLevel50={} distanceMax={} depthMax={} mobLevelCap={} scanInterval={}",
			actual.levelCap(),
			levelFifty,
			actual.distance().maxPoints(),
			actual.depth().maxPoints(),
			actual.mobLevel().levelCap(),
			actual.tracker().scanIntervalTicks()
		);
		helper.succeed();
	}

	@GameTest
	public void bundledNoteNamesTheUnitOfEachKeyAndTheAxisDrivers(final GameTestHelper helper) {
		String document = PowerConfigStore.DEFAULT_JSON;
		boolean note = document.contains("\"_note\"");
		boolean healthUnit = document.contains("max_health_per_point");
		boolean armorUnit = document.contains("armor_per_level");
		boolean knockbackUnit = document.contains("knockback_resistance_per_level");
		boolean travelAxis = document.contains("travel level");
		boolean combatAxis = document.contains("combat level");
		helper.assertTrue(note, "the bundled document carries no _note");
		helper.assertTrue(
			healthUnit && armorUnit && knockbackUnit,
			"the surviving keys do not name their unit: per point " + healthUnit + ", per level " + armorUnit
				+ " and " + knockbackUnit
		);
		helper.assertTrue(
			travelAxis && combatAxis,
			"the note does not name which quantity drives which axis: travel " + travelAxis + ", combat " + combatAxis
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=unitNote RESULT note={} healthPerPointKey={} armorPerLevelKey={} knockbackPerLevelKey={} travelDriver={} combatDriver={}",
			note, healthUnit, armorUnit, knockbackUnit, travelAxis, combatAxis
		);
		helper.succeed();
	}

	@GameTest
	public void parserKeepsAGoodEntryAndReplacesABadOne(final GameTestHelper helper) {
		PowerConfig fallback = PowerFixtures.acceptanceConfig();
		PowerConfig edited = PowerConfigParser.parse(
			"{\"level_cap\": 12, \"tracker\": {\"scan_interval_ticks\": " + EDITED_SCAN_INTERVAL
				+ ", \"heartbeat_ticks\": 0}}",
			fallback
		);
		helper.assertTrue(
			edited.levelCap() == 12,
			"an edited level cap of 12 parsed to " + edited.levelCap()
		);
		helper.assertTrue(
			edited.tracker().scanIntervalTicks() == EDITED_SCAN_INTERVAL,
			"an edited scan interval parsed to " + edited.tracker().scanIntervalTicks()
		);
		helper.assertTrue(
			edited.tracker().heartbeatTicks() == fallback.tracker().heartbeatTicks(),
			"the invalid heartbeat of 0 became " + edited.tracker().heartbeatTicks() + " instead of the fallback "
				+ fallback.tracker().heartbeatTicks()
		);
		helper.assertTrue(
			edited.mobLevel().equals(fallback.mobLevel()),
			"an untouched section changed: " + edited.mobLevel() + " instead of " + fallback.mobLevel()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=parserPerField RESULT editedLevelCap={} editedScanInterval={} heartbeat={} untouchedMobCap={}",
			edited.levelCap(),
			edited.tracker().scanIntervalTicks(),
			edited.tracker().heartbeatTicks(),
			edited.mobLevel().cap()
		);
		helper.succeed();
	}

	@GameTest
	public void parserSurvivesEveryBadDocument(final GameTestHelper helper) {
		PowerConfig fallback = PowerFixtures.acceptanceConfig();
		int survived = 0;
		String firstBroken = "";
		for (String document : BAD_DOCUMENTS) {
			PowerConfig parsed = PowerConfigParser.parse(document, fallback);
			if (parsed.equals(fallback)) {
				survived++;
			} else if (firstBroken.isEmpty()) {
				firstBroken = document + " -> " + parsed;
			}
		}
		PowerConfig applied = PowerConfigParser.parse("{\"level_cap\": 12}", fallback);
		helper.assertTrue(
			applied.levelCap() == 12,
			"a valid document with one key parsed to a level cap of " + applied.levelCap() + " instead of 12"
		);
		helper.assertTrue(
			survived == BAD_DOCUMENTS.length,
			"only " + survived + " of " + BAD_DOCUMENTS.length + " bad documents fell back to the config; first deviation: "
				+ firstBroken
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=parserBadJson RESULT documents={} fellBack={} oneKeyDocLevelCap={}",
			BAD_DOCUMENTS.length, survived, applied.levelCap()
		);
		helper.succeed();
	}

	@GameTest
	public void completeParseRejectsAnIncompleteDocument(final GameTestHelper helper) {
		boolean completeAccepted = PowerConfigParser.parseComplete(PowerConfigStore.DEFAULT_JSON).isPresent();
		boolean emptyRejected = PowerConfigParser.parseComplete("{}").isEmpty();
		boolean trashRejected = PowerConfigParser.parseComplete("not json").isEmpty();
		helper.assertTrue(completeAccepted, "the bundled document was rejected by the complete parse");
		helper.assertTrue(emptyRejected, "an empty document was accepted by the complete parse");
		helper.assertTrue(trashRejected, "a non-JSON document was accepted by the complete parse");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=parseComplete RESULT bundledAccepted={} emptyRejected={} trashRejected={}",
			completeAccepted, emptyRejected, trashRejected
		);
		helper.succeed();
	}

	@GameTest
	public void storeWritesTheDefaultDocumentWhenTheFileIsAbsent(final GameTestHelper helper) throws IOException {
		Path file = Files.createTempDirectory("power-config-absent").resolve(PowerConfigStore.FILE_NAME);
		PowerConfig loaded = PowerConfigStore.loadFrom(file);
		boolean written = Files.exists(file);
		String text = written ? Files.readString(file, StandardCharsets.UTF_8) : "";
		helper.assertTrue(written, "loadFrom wrote no file at " + file);
		helper.assertTrue(
			loaded.equals(PowerConfigStore.bundled()),
			"the written document parsed to " + loaded + " instead of the bundled config"
		);
		helper.assertTrue(
			text.contains("\"level_cap\": 50"),
			"the written file does not carry the level cap of 50: " + text
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=storeWritesDefault RESULT path={} bytes={} levelCap={}",
			file, text.length(), loaded.levelCap()
		);
		helper.succeed();
	}

	@GameTest
	public void storeReadsAHandEditedFile(final GameTestHelper helper) throws IOException {
		Path file = Files.createTempDirectory("power-config-edited").resolve(PowerConfigStore.FILE_NAME);
		String edited = PowerConfigStore.DEFAULT_JSON.replace("\"level_cap\": 50", "\"level_cap\": " + EDITED_LEVEL_CAP);
		Files.writeString(file, edited, StandardCharsets.UTF_8);
		PowerConfig loaded = PowerConfigStore.loadFrom(file);
		PowerConfigStore.load();
		helper.assertTrue(
			loaded.levelCap() == EDITED_LEVEL_CAP,
			"a hand edited level cap of " + EDITED_LEVEL_CAP + " loaded as " + loaded.levelCap()
		);
		helper.assertTrue(
			loaded.mobLevel().levelCap() == PowerConfigStore.bundled().mobLevel().levelCap(),
			"the edit changed the mob level cap too: " + loaded.mobLevel().levelCap()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=storeReadsEdited RESULT levelCap={} mobLevelCap={} liveLevelCap={}",
			loaded.levelCap(), loaded.mobLevel().levelCap(), PowerConfigStore.get().levelCap()
		);
		helper.succeed();
	}

	@GameTest
	public void storeKeepsTheLastGoodConfigWhenTheFileBreaks(final GameTestHelper helper) throws IOException {
		Path file = Files.createTempDirectory("power-config-broken").resolve(PowerConfigStore.FILE_NAME);
		Files.writeString(
			file,
			PowerConfigStore.DEFAULT_JSON.replace("\"level_cap\": 50", "\"level_cap\": " + EDITED_LEVEL_CAP),
			StandardCharsets.UTF_8
		);
		PowerConfig good = PowerConfigStore.loadFrom(file);
		Files.writeString(file, "{\"level_cap\": ", StandardCharsets.UTF_8);
		PowerConfig afterBreak = PowerConfigStore.loadFrom(file);
		PowerConfigStore.load();
		helper.assertTrue(
			afterBreak.equals(good),
			"a broken file replaced the config with " + afterBreak + " instead of keeping " + good
		);
		helper.assertTrue(
			afterBreak.levelCap() == EDITED_LEVEL_CAP,
			"the last good level cap of " + EDITED_LEVEL_CAP + " was lost: " + afterBreak.levelCap()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=storeBrokenFile RESULT keptLevelCap={} keptMobCap={} liveLevelCap={}",
			afterBreak.levelCap(), afterBreak.mobLevel().cap(), PowerConfigStore.get().levelCap()
		);
		helper.succeed();
	}

	@GameTest
	public void storeWritesTheRealConfigFileInTheGameDirectory(final GameTestHelper helper) throws IOException {
		Path path = PowerConfigStore.path();
		boolean writtenByTheServerStartHook = Files.exists(path);
		PowerConfig loaded = PowerConfigStore.load();
		boolean written = Files.exists(path);
		boolean live = PowerConfigStore.get() != null;
		helper.assertTrue(
			writtenByTheServerStartHook,
			"the server start hook did not write the live config file at " + path
		);
		helper.assertTrue(written, "the live config file does not exist at " + path);
		helper.assertTrue(
			loaded.equals(PowerConfigStore.bundled()),
			"the live config file parsed to " + loaded + " instead of the bundled config"
		);
		helper.assertTrue(live, "PowerConfigStore.get() returned nothing after a load");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=storeLivePath RESULT path={} writtenByServerStart={} exists={} levelCap={}",
			path, writtenByTheServerStartHook, written, loaded.levelCap()
		);
		helper.succeed();
	}
}
