package com.stella.learn;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.learn.gym.Bot;
import com.stella.learn.model.ActionSource;
import com.stella.learn.model.ArcherAction;
import com.stella.learn.model.Contract;
import com.stella.learn.model.Mlp;
import com.stella.learn.model.MlpSource;
import com.stella.learn.model.ModelDriver;
import com.stella.learn.model.PolicyFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * A diagnostic, off unless `STELLA_CHECK_POLICY` names a weights file. It puts a driven archer and a scripted zero-offset archer
 * ten blocks from a player who stands still, and logs every arrow: the model's raw yaw and pitch, how far the look is from the
 * true eye line, and how close the arrow came to the player's hitbox. `STELLA_CHECK_GOLDEN` names a file of observations with
 * the trainer's deterministic actions, which the Java network must reproduce.
 */
public class PolicyCheckGameTest {
	private static final int TICKS = Integer.parseInt(System.getenv().getOrDefault("STELLA_CHECK_TICKS", "500"));
	private static final int YAW = 2;
	private static final int PITCH = 3;

	private record Tracked(String source, int index, AbstractArrow arrow, double yawError, double pitchError, double rawYaw, double rawPitch, double[] closest) {
	}

	private static double[] lookError(final Mob mob, final Vec3 velocity, final Vec3 eyes) {
		Vec3 line = eyes.subtract(mob.getEyePosition());
		double lineYaw = Mth.atan2(-line.x, line.z) * 180.0 / Math.PI;
		double linePitch = -Mth.atan2(line.y, Math.hypot(line.x, line.z)) * 180.0 / Math.PI;
		double shotYaw = Mth.atan2(-velocity.x, velocity.z) * 180.0 / Math.PI;
		double shotPitch = -Mth.atan2(velocity.y, Math.hypot(velocity.x, velocity.z)) * 180.0 / Math.PI;
		return new double[] {Mth.wrapDegrees(shotYaw - lineYaw), shotPitch - linePitch};
	}

	private static final double[] RANGES = java.util.Arrays.stream(System.getenv().getOrDefault("STELLA_CHECK_RANGES", "12,24").split(",")).mapToDouble(Double::parseDouble).toArray();

	@GameTest(maxTicks = 1500, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void perShotAim(final GameTestHelper helper) {
		String path = System.getenv("STELLA_CHECK_POLICY");
		if (path == null) {
			helper.succeed();
			return;
		}

		PolicyFile file;
		try {
			file = PolicyFile.parse(Files.readAllBytes(Path.of(path)), Contract.archer());
		} catch (Exception exception) {
			helper.fail("the policy did not load: " + exception);
			return;
		}

		Mlp mlp = new Mlp(file);
		MlpSource decode = new MlpSource(mlp);
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 44.5);
		List<Mob> archers = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		double[][] lastRaw = new double[2 * RANGES.length][2];
		for (int r = 0; r < RANGES.length; r++) {
			for (int which = 0; which < 2; which++) {
				int slot = 2 * r + which;
				Mob archer = ModelTests.archer(helper, 24.5 + (which == 0 ? -2.5 : 2.5), 44.5 - RANGES[r]);
				float[] raw = new float[mlp.outputSize()];
				if (which == 0) {
					ModelDriver.setActionSource(archer, (mob, observation) -> {
						mlp.forward(observation, raw);
						lastRaw[slot][0] = raw[YAW];
						lastRaw[slot][1] = raw[PITCH];
						return decode.decide(mob, observation);
					});
				} else {
					ModelDriver.setActionSource(archer, ModelTests.script(decision -> new ArcherAction(0.0F, 0.0F, 0.0F, 0.0F, false, ArcherAction.Bow.FIRE)));
				}

				archers.add(archer);
				labels.add((which == 0 ? "model" : "script") + "@" + (int) RANGES[r]);
			}
		}

		List<List<AbstractArrow>> seen = new ArrayList<>();
		for (int i = 0; i < archers.size(); i++) {
			seen.add(new ArrayList<>());
		}

		List<Tracked> tracked = new ArrayList<>();
		int[] shots = new int[archers.size()];
		int[] hits = new int[archers.size()];
		ModelTests.everyTick(helper, player, () -> {
			for (int i = 0; i < archers.size(); i++) {
				Mob mob = archers.get(i);
				int before = seen.get(i).size();
				ModelTests.collectArrows(helper, mob, seen.get(i));
				for (int k = before; k < seen.get(i).size(); k++) {
					AbstractArrow arrow = seen.get(i).get(k);
					double[] error = lookError(mob, arrow.getDeltaMovement(), player.getEyePosition());
					tracked.add(new Tracked(labels.get(i), i, arrow, error[0], error[1], lastRaw[i][0], lastRaw[i][1], new double[] {Double.MAX_VALUE}));
					shots[i]++;
				}
			}

			for (Tracked one : new ArrayList<>(tracked)) {
				one.closest()[0] = Math.min(one.closest()[0], Math.sqrt(player.getBoundingBox().distanceToSqr(one.arrow().position())));
				if (one.arrow().isRemoved() || one.arrow().getDeltaMovement().lengthSqr() < 1.0E-4) {
					tracked.remove(one);
					boolean hit = one.closest()[0] < 0.3;
					hits[one.index()] += hit ? 1 : 0;
					StellaLearn.LOGGER.info("[BEHAVIOR] test=perShotAim SHOT source={} raw_yaw={} raw_pitch={} yaw_error_deg={} pitch_error_deg={} closest_to_hitbox={} hit={}",
						one.source(), one.source().startsWith("model") ? one.rawYaw() : "-", one.source().startsWith("model") ? one.rawPitch() : "-", one.yawError(), one.pitchError(), one.closest()[0], hit);
				}
			}
		}, archers.toArray(new Mob[0]));
		List<net.minecraft.world.entity.Entity> watched = new ArrayList<>(archers);
		watched.add(player);
		LearnArena.whenReady(helper, watched, () -> helper.runAfterDelay(TICKS, () -> {
			for (int i = 0; i < archers.size(); i++) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=perShotAim RESULT source={} shots={} hits={}", labels.get(i), shots[i], hits[i]);
			}

			ModelTests.pass(helper);
		}));
	}

	/** The Java network and decoding give the trainer's deterministic action for every recorded observation. */
	@GameTest(maxTicks = 200, environment = ModelTests.ENV)
	public void decodingMatchesTheTrainer(final GameTestHelper helper) {
		String policy = System.getenv("STELLA_CHECK_POLICY");
		String golden = System.getenv("STELLA_CHECK_GOLDEN");
		if (policy == null || golden == null) {
			helper.succeed();
			return;
		}

		try {
			MlpSource source = new MlpSource(new Mlp(PolicyFile.parse(Files.readAllBytes(Path.of(policy)), Contract.archer())));
			JsonArray cases = JsonParser.parseString(Files.readString(Path.of(golden))).getAsJsonObject().getAsJsonArray("cases");
			double worst = 0.0;
			int discrete = 0;
			for (int i = 0; i < cases.size(); i++) {
				JsonObject one = cases.get(i).getAsJsonObject();
				JsonArray obs = one.getAsJsonArray("obs");
				float[] observation = new float[obs.size()];
				for (int k = 0; k < observation.length; k++) {
					observation[k] = obs.get(k).getAsFloat();
				}

				ArcherAction action = source.decide(null, observation);
				JsonArray expected = one.getAsJsonArray("action");
				float[] mine = {action.forward(), action.strafe(), action.yaw(), action.pitch()};
				for (int k = 0; k < 4; k++) {
					worst = Math.max(worst, Math.abs(mine[k] - expected.get(k).getAsDouble()));
				}

				if (action.jumpIndex() != expected.get(4).getAsInt() || action.bow().ordinal() != expected.get(5).getAsInt()) {
					discrete++;
				}
			}

			StellaLearn.LOGGER.info("[BEHAVIOR] test=decodingMatchesTheTrainer RESULT cases={} worst_continuous_difference={} discrete_mismatches={}", cases.size(), worst, discrete);
			helper.assertTrue(worst < 1.0E-4 && discrete == 0, "worst " + worst + ", discrete mismatches " + discrete);
		} catch (Exception exception) {
			helper.fail("the check failed: " + exception);
			return;
		}

		helper.succeed();
	}
}
