package com.stella.learn;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.learn.model.Contract;
import com.stella.learn.model.Mlp;
import com.stella.learn.model.ModelDriver;
import com.stella.learn.model.PolicyFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** The model that ships in the jar: it matches the trainer's export, and a live archer runs on it. */
public class ShippedModelGameTest {
	/** Its own batch, so the real policy is the only one loaded while these tests run. */
	static final String ENV = "mod-stella-learn-test:model_shipped";
	private static final String GOLDEN = "policies/test/golden-archer-v1-shipped.json";
	private static final int CASES = 200;
	private static final int DRIVE_TICKS = 60;
	private static final int SHOOT_TICKS = 200;

	@GameTest(maxTicks = 200, environment = ENV)
	public void parityWithTrainer(final GameTestHelper helper) {
		Contract contract = Contract.archer();
		JsonObject golden;
		PolicyFile file;
		try (InputStream stream = ShippedModelGameTest.class.getClassLoader().getResourceAsStream(GOLDEN)) {
			helper.assertTrue(stream != null, GOLDEN + " is missing from the test resources");
			golden = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			file = PolicyFile.loadResource(ModelDriver.POLICY_RESOURCE, contract);
		} catch (IOException | PolicyFile.Rejected exception) {
			helper.fail("the shipped model or its golden inputs did not load: " + exception.getMessage());
			return;
		}

		helper.assertTrue(golden.get("contract_hash").getAsString().equals(contract.hashHex()), "the golden file was written for another contract");
		helper.assertTrue("archer-v1.bin".equals(golden.get("policy").getAsString()), "the golden file names " + golden.get("policy"));
		double tolerance = golden.get("tolerance").getAsDouble();
		helper.assertTrue(tolerance <= 1.0E-5, "the golden tolerance is " + tolerance);
		Mlp mlp = new Mlp(file);
		JsonArray cases = golden.getAsJsonArray("cases");
		helper.assertTrue(cases.size() == CASES, "the golden file holds " + cases.size() + " cases");
		float[] observation = new float[contract.observationSize()];
		float[] out = new float[contract.outputSize()];
		double worst = 0.0;
		for (int index = 0; index < cases.size(); index++) {
			JsonObject one = cases.get(index).getAsJsonObject();
			JsonArray inputs = one.getAsJsonArray("obs");
			JsonArray expected = one.getAsJsonArray("out");
			helper.assertTrue(inputs.size() == observation.length && expected.size() == out.length, "case " + index + " has the wrong size");
			for (int i = 0; i < observation.length; i++) {
				observation[i] = inputs.get(i).getAsFloat();
			}

			mlp.forward(observation, out);
			for (int i = 0; i < out.length; i++) {
				double difference = Math.abs(out[i] - expected.get(i).getAsDouble());
				worst = Math.max(worst, difference);
				helper.assertTrue(difference <= 1.0E-5, "case " + index + " output " + i + " differs by " + difference);
			}
		}

		StellaLearn.LOGGER.info("[BEHAVIOR] test=parityWithTrainer RESULT cases={} worst_difference={} size={}", cases.size(), worst, file.size());
		helper.succeed();
	}

	/**
	 * Loads the shipped file through the same path a server start uses, with no model of the test's own, and puts a bow
	 * skeleton ten blocks from a player. It must become model driven and loose an arrow.
	 */
	@GameTest(maxTicks = 700, padding = LearnArena.PADDING, environment = ENV)
	public void liveArcherIsModelDriven(final GameTestHelper helper) {
		ModelDriver.reloadPolicy(ModelDriver.POLICY_RESOURCE);
		helper.assertTrue(ModelDriver.STATUS_OK.equals(ModelDriver.status()), "the shipped model did not load: " + ModelDriver.status());
		helper.runBeforeTestEnd(ModelDriver::useNoPolicy);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		LearnArena.keepTargeting(helper, archer, player);
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, () -> ModelTests.tagged(archer) && ModelTests.running(archer), DRIVE_TICKS, () -> {
			helper.assertTrue(ModelDriver.isDriven(archer), "the driver does not list the archer");
			helper.assertTrue(ModelTests.lines(archer, "INSTALL", "source=policy").size() == 1, "the install did not name the shipped policy as its source");
			LearnArena.pollUntil(helper, () -> LearnTestInit.loosedBy(archer.getUUID()) > 0, SHOOT_TICKS, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=liveArcherIsModelDriven RESULT arrows={} acts={}", LearnTestInit.loosedBy(archer.getUUID()), ModelTests.lines(archer, "ACT").size());
				ModelDriver.useNoPolicy();
				helper.succeed();
			}, () -> "the model-driven archer loosed no arrow in " + SHOOT_TICKS + " ticks");
		}, () -> "the archer was never made model driven: " + ModelTests.goalNames(archer)));
	}
}
