package com.stella.learn;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.learn.model.Contract;
import com.stella.learn.model.Mlp;
import com.stella.learn.model.PolicyFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The Java network gives the numbers the Python reference wrote for the same weights and inputs. */
public class ModelParityGameTest {
	private static final double TOLERANCE = 1.0E-5;
	private static final int CASES = 200;

	@GameTest(maxTicks = 200)
	public void javaMatchesPython(final GameTestHelper helper) {
		JsonObject golden;
		PolicyFile file;
		Contract contract = Contract.archer();
		try (InputStream stream = ModelParityGameTest.class.getClassLoader().getResourceAsStream("policies/test/golden-archer-v1.json")) {
			helper.assertTrue(stream != null, "golden-archer-v1.json is missing from the test resources");
			golden = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			file = PolicyFile.loadResource("policies/test/" + golden.get("policy").getAsString(), contract);
		} catch (IOException | PolicyFile.Rejected exception) {
			helper.fail("the golden inputs did not load: " + exception.getMessage());
			return;
		}

		// The same hash on both sides shows Python and Java hashed the same contract bytes.
		String hash = golden.get("contract_hash").getAsString();
		helper.assertTrue(hash.equals(contract.hashHex()), "Python hashed " + hash + ", Java hashed " + contract.hashHex());
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
				helper.assertTrue(difference <= TOLERANCE, "case " + index + " output " + i + " differs by " + difference);
			}
		}

		StellaLearn.LOGGER.info("[BEHAVIOR] test=javaMatchesPython RESULT cases={} worst_difference={} tolerance={}", cases.size(), worst, TOLERANCE);
		helper.succeed();
	}
}
