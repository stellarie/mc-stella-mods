package com.stella.ruins.world;

import com.google.gson.JsonParser;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The config: the plan's defaults, and bad entries that log and fall back instead of stopping the server. */
public class RuinConfigGameTest {
	@GameTest
	public void theDefaultsAreThePlansNumbers(final GameTestHelper helper) {
		RuinConfig.Values defaults = RuinConfig.Values.defaults();
		helper.assertTrue(defaults.clueChance() == 0.08, "clue_chance default is " + defaults.clueChance());
		helper.assertTrue(defaults.uniqueDropChance() == 0.001, "unique_drop_chance default is " + defaults.uniqueDropChance());
		helper.assertTrue(defaults.vaultLevelsPerRoll() == 25 && defaults.vaultRollsMax() == 5, "the vault roll defaults are " + defaults.vaultLevelsPerRoll() + " and " + defaults.vaultRollsMax());
		helper.assertTrue(defaults.resetDays().isEmpty(), "reset_days is not empty by default");
		helper.assertTrue(RuinConfig.DEFAULT_CLUE_CHANCE == defaults.clueChance() && RuinConfig.DEFAULT_UNIQUE_DROP_CHANCE == defaults.uniqueDropChance(), "the constants and the defaults differ");
		helper.succeed();
	}

	@GameTest
	public void aBadEntryKeepsItsDefaultAndTheGoodOnesStay(final GameTestHelper helper) {
		RuinConfig.Values values = RuinConfig.parse(JsonParser.parseString(
			"{\"clue_chance\":\"lots\",\"unique_drop_chance\":2,\"vault_levels_per_roll\":0,\"vault_rolls_max\":7,"
				+ "\"reset_days\":{\"alpha\":2,\"beta\":\"x\",\"gamma\":0,\"delta\":1.5}}"));
		RuinConfig.Values defaults = RuinConfig.Values.defaults();
		helper.assertTrue(values.clueChance() == defaults.clueChance(), "a string chance was accepted: " + values.clueChance());
		helper.assertTrue(values.uniqueDropChance() == defaults.uniqueDropChance(), "a chance above 1 was accepted: " + values.uniqueDropChance());
		helper.assertTrue(values.vaultLevelsPerRoll() == defaults.vaultLevelsPerRoll(), "zero levels per roll was accepted");
		helper.assertTrue(values.vaultRollsMax() == 7, "the good vault_rolls_max entry was lost");
		helper.assertTrue(values.resetDays().equals(Map.of("alpha", 2)), "reset_days kept " + values.resetDays());
		helper.succeed();
	}

	@GameTest
	public void aDocumentThatIsNotAnObjectFallsBackToDefaults(final GameTestHelper helper) {
		helper.assertTrue(RuinConfig.parse(JsonParser.parseString("[1,2]")).equals(RuinConfig.Values.defaults()), "an array changed the config");
		helper.succeed();
	}

	@GameTest
	public void vaultRollsGrowWithLevelAndStopAtTheCap(final GameTestHelper helper) {
		RuinConfig.Values values = RuinConfig.Values.defaults();
		int step = values.vaultLevelsPerRoll();
		helper.assertTrue(values.vaultRolls(0) == 1 && values.vaultRolls(step - 1) == 1, "a low level rolls more than once");
		helper.assertTrue(values.vaultRolls(step) == 2 && values.vaultRolls(2 * step + 10) == 3, "rolls do not follow 1 + level / " + step);
		helper.assertTrue(values.vaultRolls(10_000) == values.vaultRollsMax(), "rolls pass the cap of " + values.vaultRollsMax());
		helper.succeed();
	}

	@GameTest
	public void aResetSiteTakesItsDelayFromTheConfigWhenItNamesOne(final GameTestHelper helper) {
		Claim claim = Claim.resetAfterDays(1);
		RuinConfig.Values old = RuinConfig.replace(RuinConfig.get().withResetDays(Map.of("test_reset", 3)));
		try {
			helper.assertTrue(claim.resetTicks("test_reset") == 3 * Claim.DAY_TICKS, "the config entry was ignored: " + claim.resetTicks("test_reset"));
			helper.assertTrue(claim.resetTicks("other_site") == Claim.DAY_TICKS, "an unnamed site lost its own delay");
			helper.assertTrue(Claim.once().resetTicks("test_reset") < 0, "a once site got a reset delay");
		} finally {
			RuinConfig.replace(old);
		}
		helper.succeed();
	}
}
