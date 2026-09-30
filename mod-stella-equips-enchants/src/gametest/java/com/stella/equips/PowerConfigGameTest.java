package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigParser;
import com.stella.equips.power.PowerConfigStore;
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
		"{\"curve\": 6}"
	};
	private static final int EDITED_LEVEL_CAP = 7;
	private static final int EDITED_SCAN_INTERVAL = 20;

	@GameTest
	public void bundledDocumentCarriesTheAcceptanceNumbers(final GameTestHelper helper) {
		PowerConfig expected = PowerFixtures.acceptanceConfig();
		PowerConfig actual = PowerConfigStore.bundled();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bundledNumbers RESULT levelCap={} bands={} tracker={} healthPerLevel={} readout={}",
			actual.levelCap(), actual.bands(), actual.tracker(),
			actual.attributes().healthPerLevel(), actual.readout()
		);
		helper.assertTrue(
			actual.levelCap() == 1024
				&& actual.bands().equals(expected.bands())
				&& actual.tracker().equals(expected.tracker())
				&& actual.attributes().equals(new PowerConfig.Attributes(expected.attributes().healthBase(), 2.5))
				&& actual.readout().equals(expected.readout()),
			"the bundled document parsed to " + actual + " but the acceptance numbers are " + expected
		);
		helper.succeed();
	}

	@GameTest
	public void bundledNoteNamesVanillaXpAsTheSourceOfPower(final GameTestHelper helper) {
		String note = noteOf(PowerConfigStore.DEFAULT_JSON);
		boolean vanilla = note.contains("vanilla experience level");
		boolean valueAtCap = note.contains("value at level " + PowerConfigStore.bundled().levelCap());
		boolean staleClaims = note.contains("Kills are the only source") || note.contains("removes 5 percent")
			|| note.contains("divisor");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=unitNote RESULT vanilla={} valueAtCap={} staleClaims={}", vanilla, valueAtCap, staleClaims
		);
		helper.assertTrue(vanilla && valueAtCap && !staleClaims, "the bundled note reads: " + note);
		helper.succeed();
	}

	@GameTest
	public void anOlderFileKeepsItsValuesAndReportsTheRetiredCurveAndAwardKeys(final GameTestHelper helper) {
		PowerConfig fallback = PowerFixtures.acceptanceConfig();
		String older = PowerConfigStore.DEFAULT_JSON
			.replace("\"level_cap\": 1024", "\"level_cap\": " + EDITED_LEVEL_CAP
				+ ",\n\t\"curve\": { \"base\": 3.0, \"growth\": 1.5 },\n\t\"award\": { \"divisor\": 2.0, \"minimum\": 4 }");
		PowerConfig parsed = PowerConfigParser.parse(older, fallback);
		java.util.List<String> ignored = PowerConfigParser.ignoredKeys();
		boolean reported = ignored.containsAll(java.util.List.of("curve.base", "curve.growth", "award.divisor", "award.minimum"));
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=retiredCurveAward RESULT levelCap={} ignored={} reported={}", parsed.levelCap(), ignored, reported
		);
		helper.assertTrue(
			reported && parsed.levelCap() == EDITED_LEVEL_CAP,
			"an older file parsed to level cap " + parsed.levelCap() + " and reported " + ignored
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
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=parserPerField RESULT editedLevelCap={} editedScanInterval={} heartbeat={}",
			edited.levelCap(),
			edited.tracker().scanIntervalTicks(),
			edited.tracker().heartbeatTicks()
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
			text.contains("\"level_cap\": 1024"),
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
		String edited = PowerConfigStore.DEFAULT_JSON.replace("\"level_cap\": 1024", "\"level_cap\": " + EDITED_LEVEL_CAP);
		Files.writeString(file, edited, StandardCharsets.UTF_8);
		PowerConfig loaded = PowerConfigStore.loadFrom(file);
		PowerConfigStore.load();
		helper.assertTrue(
			loaded.levelCap() == EDITED_LEVEL_CAP,
			"a hand edited level cap of " + EDITED_LEVEL_CAP + " loaded as " + loaded.levelCap()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=storeReadsEdited RESULT levelCap={} liveLevelCap={}",
			loaded.levelCap(), PowerConfigStore.get().levelCap()
		);
		helper.succeed();
	}

	@GameTest
	public void storeKeepsTheLastGoodConfigWhenTheFileBreaks(final GameTestHelper helper) throws IOException {
		Path file = Files.createTempDirectory("power-config-broken").resolve(PowerConfigStore.FILE_NAME);
		Files.writeString(
			file,
			PowerConfigStore.DEFAULT_JSON.replace("\"level_cap\": 1024", "\"level_cap\": " + EDITED_LEVEL_CAP),
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
			"[BEHAVIOR] test=storeBrokenFile RESULT keptLevelCap={} liveLevelCap={}",
			afterBreak.levelCap(), PowerConfigStore.get().levelCap()
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

	private static String noteOf(final String document) {
		int start = document.indexOf("\"_note\"");
		int end = document.indexOf("\n", start);
		return start < 0 ? "" : document.substring(start, end < 0 ? document.length() : end);
	}
}
