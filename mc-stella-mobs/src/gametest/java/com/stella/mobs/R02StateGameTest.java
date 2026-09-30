package com.stella.mobs;

import java.util.List;
import com.stella.mobs.behavior.CreeperEscapePositions;
import com.stella.mobs.behavior.ReactiveState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.commands.arguments.EntityAnchorArgument;

public class R02StateGameTest {
	private static final String NO_GEAR_CONFIG = """
		{
			"version": 1,
			"entities": {},
			"roles": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}
		""";

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02SlimeCube(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02SlimeCube", EntityTypes.SLIME, 2, 2, 4, 2);
		R02ControlGameTest.awaitEscape(helper, scenario, "slime-cube");
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02MagmaCube(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02MagmaCube", EntityTypes.MAGMA_CUBE, 2, 2, 4, 2);
		R02ControlGameTest.awaitEscape(helper, scenario, "magma-cube");
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02SulfurCube(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02SulfurCube", EntityTypes.SULFUR_CUBE, 2, 2, 4, 2);
		R02ControlGameTest.awaitEscape(helper, scenario, "sulfur-cube");
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02SulfurCubeBodyItemGate(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02SulfurCubeBodyItemGate", EntityTypes.SULFUR_CUBE, 2, 2, 4, 2);
		logResultOnEnd(helper, scenario, "sulfur-cube-body-item");
		SulfurCube cube = (SulfurCube)scenario.subject();
		cube.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.STONE));
		helper.runAfterDelay(8L, () -> {
			helper.assertFalse(cube.getItemBySlot(EquipmentSlot.BODY).isEmpty(), "R02 removed the SulfurCube body item");
			assertGated(helper, scenario, "controllerState", "sulfur-cube-body-item");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02CreakingActive(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02CreakingActive", EntityTypes.CREAKING, 2, 2, 4, 2);
		Creaking creaking = (Creaking)scenario.subject();
		Player player = scenario.player();
		creaking.activate(player);
		creaking.getBrain().setMemory(MemoryModuleType.NEAREST_PLAYERS, List.<Player>of(player));
		helper.onEachTick(() -> {
			player.lookAt(EntityAnchorArgument.Anchor.EYES,
				new Vec3(player.getX() + 10.0, player.getEyeY(), player.getZ()));
			creaking.getBrain().setMemory(MemoryModuleType.NEAREST_PLAYERS, List.<Player>of(player));
		});
		R02ControlGameTest.awaitEscape(helper, scenario, "creaking-active");
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02CreakingMoveGate(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02CreakingMoveGate", EntityTypes.CREAKING, 2, 2, 4, 2);
		logResultOnEnd(helper, scenario, "creaking-gaze");
		Creaking creaking = (Creaking)scenario.subject();
		creaking.activate(scenario.player());
		creaking.getBrain().setMemory(MemoryModuleType.NEAREST_PLAYERS, List.<Player>of(scenario.player()));
		creaking.getNavigation().stop();
		creaking.setDeltaMovement(Vec3.ZERO);
		helper.onEachTick(() -> {
			scenario.player().lookAt(EntityAnchorArgument.Anchor.EYES, creaking.getEyePosition());
			creaking.getBrain().setMemory(MemoryModuleType.NEAREST_PLAYERS, List.<Player>of(scenario.player()));
		});
		Vec3 start = creaking.position();
		helper.runAfterDelay(8L, () -> {
			helper.assertTrue(creaking.isActive(), "the Creaking left its active state");
			helper.assertFalse(creaking.canMove(), "the Creaking gaze gate did not freeze movement");
			helper.assertTrue(horizontalDistance(creaking.position(), start) < 0.5, "R02 moved a gaze-locked Creaking");
			assertGated(helper, scenario, "controllerState", "creaking-gaze");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02FoxActive(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02FoxActive", EntityTypes.FOX, 2, 2, 4, 2);
		((Fox)scenario.subject()).setSitting(false);
		R02ControlGameTest.awaitEscape(helper, scenario, "fox-active");
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02FoxMoveGate(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02FoxMoveGate", EntityTypes.FOX, 2, 2, 4, 2);
		logResultOnEnd(helper, scenario, "fox-sitting");
		Fox fox = (Fox)scenario.subject();
		fox.getNavigation().stop();
		fox.setDeltaMovement(Vec3.ZERO);
		helper.onEachTick(() -> fox.setSitting(true));
		Vec3 start = fox.position();
		helper.runAfterDelay(8L, () -> {
			helper.assertTrue(fox.isSitting(), "R02 cleared the Fox sitting state");
			helper.assertTrue(horizontalDistance(fox.position(), start) < 0.5, "R02 moved a sitting Fox");
			assertGated(helper, scenario, "controllerState", "fox-sitting");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02PandaActive(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02PandaActive", EntityTypes.PANDA, 2, 2, 4, 2);
		((Panda)scenario.subject()).setOnBack(false);
		R02ControlGameTest.awaitEscape(helper, scenario, "panda-active");
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02PandaActionGate(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02PandaActionGate", EntityTypes.PANDA, 2, 2, 4, 2);
		logResultOnEnd(helper, scenario, "panda-on-back");
		Panda panda = (Panda)scenario.subject();
		panda.getNavigation().stop();
		panda.setDeltaMovement(Vec3.ZERO);
		helper.onEachTick(() -> panda.setOnBack(true));
		Vec3 start = panda.position();
		helper.runAfterDelay(8L, () -> {
			helper.assertTrue(panda.isOnBack(), "R02 cleared the Panda on-back state");
			helper.assertTrue(horizontalDistance(panda.position(), start) < 0.5, "R02 moved a Panda during its action");
			assertGated(helper, scenario, "controllerState", "panda-on-back");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS, required = false)
	public void r02DolphinJointWaterEscapeManualGap(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02DolphinJointWaterEscapeManualGap", EntityTypes.DOLPHIN, 4, 4, 5, 4);
		R02ControlGameTest.fillWater(helper, -2, 1, 0, 7, 4, 7);
		Dolphin dolphin = (Dolphin)scenario.subject();
		dolphin.setAirSupply(20);
		scenario.quietOpponents();
		boolean[] pathReplaced = {false};
		helper.onEachTick(() -> {
			var navigation = dolphin.getNavigation();
			Vec3 cached = ReactiveState.get(dolphin).cachedR02Route(scenario.creeper().getUUID(), dolphin.tickCount);
			if (!pathReplaced[0] && cached != null && !navigation.isDone()) {
				helper.assertTrue(navigation.moveTo(scenario.player(), 1.0),
					"the Dolphin test could not replace the escape navigation path");
				pathReplaced[0] = true;
				return;
			}

			if (pathReplaced[0] && "COMMAND_SET".equals(R02ControlGameTest.decision(dolphin))) {
				Path activePath = navigation.getPath();
				helper.assertTrue(activePath != null && !activePath.isDone(),
					"the Dolphin lost navigation after a native path replacement");
				helper.assertTrue(CreeperEscapePositions.isSafe(
					Vec3.atBottomCenterOf(activePath.getTarget()), scenario.creeper()),
					"the Dolphin kept an unsafe path after its cached escape route was replaced");
			}
		});
		helper.runBeforeTestEnd(() -> scenario.log().r02Result("dolphin-liquid-joint", R02ControlGameTest.decision(dolphin), scenario.creeper(), scenario.player()));
		helper.succeedWhen(() -> {
			R02ControlGameTest.assertValidThreat(helper, scenario.creeper(), scenario.player());
			helper.assertTrue(pathReplaced[0], "the Dolphin navigation path was not replaced during the escape");
			helper.assertTrue(R02ControlGameTest.decision(dolphin).equals("COMMAND_SET"), "the Dolphin had no joint escape command");
			helper.assertTrue(dolphin.isInWater(), "the Dolphin's escape did not reach safe water");
			helper.assertTrue(dolphin.getAirSupply() > 0, "the Dolphin ran out of air during its joint escape");
			helper.assertTrue(dolphin.distanceTo(scenario.creeper()) >= R02ControlGameTest.radiusWithMargin(scenario.creeper()),
			"the Dolphin's water escape stayed inside the blast radius");
			scenario.log().r02Result("dolphin-liquid-joint", R02ControlGameTest.decision(dolphin), scenario.creeper(), scenario.player());
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02DolphinNoJointEscapePreservesAirAndLiquid(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02DolphinNoJointEscapePreservesAirAndLiquid", EntityTypes.DOLPHIN, 2, 2, 4, 2);
		logResultOnEnd(helper, scenario, "dolphin-no-joint-route");
		Dolphin dolphin = (Dolphin)scenario.subject();
		dolphin.setAirSupply(1000);
		helper.runAfterDelay(12L, () -> {
			R02ControlGameTest.assertSurvivalTarget(helper, scenario.creeper(), scenario.player());
			helper.assertTrue(R02ControlGameTest.decision(dolphin).equals("NO_SAFE_ROUTE"), "the Dolphin did not preserve its liquid-seeking command");
			helper.assertTrue(R02ControlGameTest.skipReason(dolphin).equals("NO_JOINT_LIQUID_ROUTE"), "the Dolphin skip reason was not named");
			helper.assertTrue(dolphin.getAirSupply() > 0, "the Dolphin lost its air while no joint route existed");
			helper.assertTrue(scenario.creeper().getTarget() == scenario.player(), "the Creeper lost its survival Player target");
			scenario.log().r02Result("dolphin-no-joint-route", R02ControlGameTest.decision(dolphin), scenario.creeper(), scenario.player());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02WardenDigEmergeGate(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02WardenDigEmergeGate", EntityTypes.WARDEN, 2, 2, 4, 2);
		logResultOnEnd(helper, scenario, "warden-dig-emerge");
		Warden warden = (Warden)scenario.subject();
		int[] digSkips = {0};
		int[] emergeSkips = {0};
		helper.onEachTick(() -> {
			if (helper.getTick() < 6L) {
				warden.setPose(Pose.DIGGING);
				if ("STATE_GATED".equals(R02ControlGameTest.decision(warden))) {
					digSkips[0]++;
				}
			} else {
				warden.setPose(Pose.EMERGING);
				if ("STATE_GATED".equals(R02ControlGameTest.decision(warden))) {
					emergeSkips[0]++;
				}
			}
		});
		helper.runAfterDelay(16L, () -> {
			helper.assertTrue(warden.getPose() == Pose.EMERGING, "the Warden left its emergence gate");
			helper.assertTrue(digSkips[0] > 0, "R02 did not record the Warden digging gate");
			helper.assertTrue(emergeSkips[0] > 0, "R02 did not record the Warden emerging gate");
			assertGated(helper, scenario, "controllerState", "warden-dig-emerge");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02HappyGhastStillTimeoutGate(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02HappyGhastStillTimeoutGate", EntityTypes.HAPPY_GHAST, 2, 3, 4, 2);
		R02ControlGameTest.liftFlightSubject(scenario, 7.0);
		scenario.creeper().setNoGravity(true);
		scenario.creeper().setPos(
			scenario.creeper().getX(), scenario.creeper().getY() + 1.0, scenario.creeper().getZ()
		);
		HappyGhast ghast = (HappyGhast)scenario.subject();
		helper.assertTrue(scenario.player().startRiding(ghast, true, false), "the Survival Player could not mount the HappyGhast");
		scenario.player().stopRiding();
		Vec3 playerPosition = helper.absoluteVec(new Vec3(7.5, 1.0, 7.5));
		scenario.player().setPos(playerPosition.x, playerPosition.y, playerPosition.z);
		helper.assertTrue(ghast.isOnStillTimeout(), "the fixture did not enter the HappyGhast still timeout");
		int[] gatedTicks = {0};
		helper.onEachTick(() -> {
			if (ghast.isOnStillTimeout() && "STATE_GATED".equals(R02ControlGameTest.decision(ghast))) {
				gatedTicks[0]++;
			}
		});
		helper.runBeforeTestEnd(() -> scenario.log().r02Result(
			"happyghast-still-timeout", R02ControlGameTest.decision(ghast), scenario.creeper(), scenario.player()
		));
		helper.startSequence()
			.thenIdle(60)
			.thenExecute(() -> {
				R02ControlGameTest.assertSurvivalTarget(helper, scenario.creeper(), scenario.player());
				helper.assertTrue(ghast.isOnStillTimeout(), "the HappyGhast timeout expired before age tick 60");
				helper.assertTrue(gatedTicks[0] > 0, "R02 did not preserve the age-gated still timeout");
				helper.assertTrue("STATE_GATED".equals(R02ControlGameTest.decision(ghast)),
					"R02 did not report the HappyGhast timeout gate");
				helper.assertTrue("controllerState".equals(R02ControlGameTest.skipReason(ghast)),
					"the HappyGhast timeout skip reason was not named");
				scenario.log().customResult("R02", "STATE_GATED", "controller=happyghast-still-timeout reason=controllerState");
			})
			.thenWaitUntil(() -> helper.assertFalse(ghast.isOnStillTimeout(),
				"the HappyGhast timeout did not expire after age tick 60 plus 10 ticks"))
			.thenWaitUntil(() -> helper.assertTrue("COMMAND_SET".equals(R02ControlGameTest.decision(ghast)),
				"the HappyGhast did not resume R02 after its timeout"))
			.thenWaitUntil(() -> helper.assertTrue(
				ghast.distanceTo(scenario.creeper()) >= R02ControlGameTest.radiusWithMargin(scenario.creeper()),
				"the HappyGhast did not reach safety after the timeout"))
			.thenExecute(() -> {
				R02ControlGameTest.assertValidThreat(helper, scenario.creeper(), scenario.player());
				scenario.log().r02Result("happyghast-still-timeout-resume", R02ControlGameTest.decision(ghast), scenario.creeper(), scenario.player());
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02ShulkerTeleportGuardMatrix(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02ShulkerTeleportGuardMatrix", EntityTypes.SHULKER, 2, 1, 4, 1);
		logResultOnEnd(helper, scenario, "shulker-passenger-guard");
		scenario.quietOpponents();
		Shulker shulker = (Shulker)scenario.subject();
		helper.getLevel().setBlockAndUpdate(shulker.blockPosition().below(), Blocks.STONE.defaultBlockState());
		Mob vehicle = helper.spawnMob(EntityTypes.ZOMBIE, 1, 3, 6).spawn();
		vehicle.setNoAi(true);
		vehicle.setNoGravity(true);
		vehicle.getNavigation().stop();
		vehicle.setDeltaMovement(Vec3.ZERO);
		shulker.getNavigation().stop();
		shulker.setDeltaMovement(Vec3.ZERO);
		helper.assertTrue(shulker.startRiding(vehicle, true, false), "the Shulker fixture could not enter the passenger guard");
		Vec3[] settledPosition = {null};
		helper.runAfterDelay(10L, () -> settledPosition[0] = shulker.position());
		helper.runAfterDelay(20L, () -> {
			helper.assertTrue(shulker.isPassenger(), "R02 dismounted the Shulker");
			helper.assertTrue(settledPosition[0] != null, "the Shulker did not settle as a passenger");
			if ("STATE_GATED".equals(R02ControlGameTest.decision(shulker))) {
				helper.assertTrue(horizontalDistance(shulker.position(), settledPosition[0]) < 0.5,
					"R02 moved a passenger Shulker after the gate decision");
			}
			helper.assertTrue(shulker.getAttachFace() != null, "the Shulker lost its attachment face");
			assertGated(helper, scenario, "passenger", "shulker-passenger-guard");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void r02NoAiImmobileBoundary(final GameTestHelper helper) {
		R02ControlGameTest.Scenario scenario = scenario(helper, "r02NoAiImmobileBoundary", EntityTypes.ZOMBIE, 2, 2, 4, 2);
		scenario.quietOpponents();
		Mob noAi = scenario.subject();
		noAi.setNoAi(true);
		Vec3 noAiStart = noAi.position();
		Ravager ravager = helper.spawnMob(EntityTypes.RAVAGER, 1, 3, 5).spawn();
		ravager.getGoalSelector().removeAllGoals(goal -> true);
		ravager.setNoGravity(true);
		ravager.getNavigation().stop();
		ravager.setDeltaMovement(Vec3.ZERO);
		helper.onEachTick(() -> ravager.handleEntityEvent((byte)39));
		Vec3 immobileStart = ravager.position();
		helper.runBeforeTestEnd(() -> scenario.log().customResult("R02", "ENGINE_GATE_OBSERVED",
			"noAi=" + noAi.isNoAi() + " immobile=" + (ravager.getStunnedTick() > 0)
				+ " noAiHorizontal=" + horizontalDistance(noAi.position(), noAiStart)
				+ " ravagerHorizontal=" + horizontalDistance(ravager.position(), immobileStart)));
		helper.runAfterDelay(10L, () -> {
			R02ControlGameTest.assertSurvivalTarget(helper, scenario.creeper(), scenario.player());
			helper.assertTrue(noAi.isNoAi(), "the NoAI boundary changed");
			helper.assertTrue(horizontalDistance(noAi.position(), noAiStart) < 0.5, "the NoAI mob moved");
			helper.assertTrue(ravager.getStunnedTick() > 0, "the Ravager left its immobile state");
			helper.assertTrue(horizontalDistance(ravager.position(), immobileStart) < 0.5, "the immobile Ravager moved");
			scenario.log().customResult("R02", "ENGINE_GATE_PRESERVED", "reason=noAI,immobile");
			helper.succeed();
		});
	}

	private static double horizontalDistance(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return Math.sqrt(x * x + z * z);
	}

	private static void logResultOnEnd(
		final GameTestHelper helper,
		final R02ControlGameTest.Scenario scenario,
		final String controller
	) {
		helper.runBeforeTestEnd(() -> scenario.log().r02Result(
			controller, R02ControlGameTest.decision(scenario.subject()), scenario.creeper(), scenario.player()
		));
	}

	private static R02ControlGameTest.Scenario scenario(
		final GameTestHelper helper,
		final String test,
		final net.minecraft.world.entity.EntityType<? extends Mob> type,
		final int subjectX,
		final int subjectZ,
		final int creeperX,
		final int creeperZ
	) {
		R02ControlGameTest.Scenario scenario = R02ControlGameTest.build(helper, test, type, subjectX, subjectZ, creeperX, creeperZ, false, NO_GEAR_CONFIG);
		R02ControlGameTest.assertValidThreat(helper, scenario.creeper(), scenario.player());
		return scenario;
	}

	private static void assertGated(
		final GameTestHelper helper,
		final R02ControlGameTest.Scenario scenario,
		final String reason,
		final String controller
	) {
		R02ControlGameTest.assertSurvivalTarget(helper, scenario.creeper(), scenario.player());
		helper.assertTrue(R02ControlGameTest.decision(scenario.subject()).equals("STATE_GATED"),
			controller + " did not report STATE_GATED: " + scenario.log().summary());
		helper.assertTrue(R02ControlGameTest.skipReason(scenario.subject()).equals(reason),
			controller + " did not report skip reason " + reason + ": " + scenario.log().summary());
		scenario.log().r02Result(controller, R02ControlGameTest.decision(scenario.subject()), scenario.creeper(), scenario.player());
	}
}
