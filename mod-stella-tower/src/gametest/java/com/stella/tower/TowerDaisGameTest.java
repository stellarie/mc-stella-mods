package com.stella.tower;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The portal's dais: a stair of the biome's stone up to a platform, a gate round the frame, and nothing that a blast takes. */
public class TowerDaisGameTest {
	private static BlockState at(final ServerLevel tower, final TowerPlan plan, final int x, final int y, final int z) {
		return tower.getBlockState(plan.origin().offset(x, y, z));
	}

	/** Catches a dais built other than designed: platform, steps facing up, arch, lanterns, braziers and backdrop. */
	@GameTest(maxTicks = 400)
	public void theDaisIsBuiltAsDesigned(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloor();
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		int s = plan.biome().steps();
		int f = plan.portalFacing();
		int px = plan.portalX();
		int pz = plan.portalZ();
		int platformWrong = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int z = pz - 3; z <= pz + 4; z++) {
				BlockState top = at(tower, plan, px + dx, s - 1, z);
				// The frame's bottom row stands in the platform's top.
				platformWrong += top.is(TowerBlocks.TOWER_WALL) || top.is(TowerBlocks.TOWER_FRAME) && dx == 0 ? 0 : 1;
			}
		}
		Direction climbs = f == 1 ? Direction.WEST : Direction.EAST;
		int stairWrong = 0;
		int stairs = 0;
		for (int k = 1; k < s; k++) {
			for (int z = pz - 3 - k; z <= pz + 4 + k; z++) {
				BlockState step = at(tower, plan, px + f * (2 + k), s - k - 1, z);
				stairs++;
				boolean right = step.is(TowerBlocks.TOWER_STAIR) && step.getValue(StairBlock.FACING) == climbs
					&& step.getValue(TowerStairBlock.SKIN) == plan.biome().biome().daisSkin();
				stairWrong += right ? 0 : 1;
			}
		}
		int archWrong = 0;
		for (int z : new int[] {pz - 2, pz + 3}) {
			for (int y = s; y <= s + 6; y++) {
				archWrong += at(tower, plan, px, y, z).is(TowerBlocks.TOWER_WALL) ? 0 : 1;
			}
			archWrong += at(tower, plan, px, s + 7, z).is(TowerBlocks.TOWER_LAMP) ? 0 : 1;
		}
		for (int z = pz - 1; z <= pz + 2; z++) {
			archWrong += at(tower, plan, px, s + 6, z).is(TowerBlocks.TOWER_WALL) ? 0 : 1;
		}
		int hangWrong = 0;
		for (int z : new int[] {pz - 1, pz + 2}) {
			BlockState lantern = at(tower, plan, px, s + 4, z);
			hangWrong += lantern.is(Blocks.LANTERN) && lantern.getValue(LanternBlock.HANGING) ? 0 : 1;
			hangWrong += at(tower, plan, px, s + 5, z).is(Blocks.IRON_CHAIN) ? 0 : 1;
		}
		int braziers = 0;
		for (int z : new int[] {pz - 2, pz + 3}) {
			braziers += at(tower, plan, px + f * 2, s, z).is(Blocks.SOUL_CAMPFIRE) ? 1 : 0;
		}
		int backdropWrong = 0;
		int backX = px - f * 3;
		for (int z = pz - 4; z <= pz + 5; z++) {
			backdropWrong += at(tower, plan, backX, s + BiomeDraft.BACKDROP - 1, z).is(TowerBlocks.TOWER_WALL) ? 0 : 1;
		}
		TowerTestSupport.result("daisBuilt", "floor=" + floor + " steps=" + s + " facing=" + f + " platformWrong=" + platformWrong + " stairs=" + stairs
			+ " stairWrong=" + stairWrong + " archWrong=" + archWrong + " hangWrong=" + hangWrong + " braziers=" + braziers + " backdropWrong=" + backdropWrong);
		helper.assertTrue(platformWrong == 0, platformWrong + " platform columns are not tower wall");
		helper.assertTrue(stairs > 0 && stairWrong == 0, stairWrong + " of " + stairs + " steps are not the biome's stair facing the portal");
		helper.assertTrue(archWrong == 0, archWrong + " blocks of the gate are wrong");
		helper.assertTrue(hangWrong == 0, hangWrong + " blocks of the hanging lanterns are wrong");
		helper.assertTrue(braziers == 2, braziers + " of 2 braziers stand");
		helper.assertTrue(backdropWrong == 0, backdropWrong + " backdrop columns do not reach their height");
		helper.succeed();
	}

	/** Catches a stair a body cannot climb: the walk from the apron rises one step at a time to the platform and ends in the portal. */
	@GameTest(maxTicks = 400)
	public void theStairIsWalkedUpToThePortalOneStepAtATime(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloor();
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		int s = plan.biome().steps();
		int footX = plan.portalX() + plan.portalFacing() * (2 + s);
		List<int[]> path = TowerWalk.path(TowerWalk.world(tower, floor), new int[] {footX, 0, plan.portalZ()}, TowerVolume.portalGoals(plan));
		Set<Integer> heights = new HashSet<>();
		int jumps = 0;
		for (int i = 0; i < path.size(); i++) {
			heights.add(path.get(i)[1]);
			jumps += i > 0 && Math.abs(path.get(i)[1] - path.get(i - 1)[1]) > 1 ? 1 : 0;
		}
		int[] last = path.isEmpty() ? new int[] {0, -1, 0} : path.get(path.size() - 1);
		boolean complete = true;
		for (int y = 0; y <= s; y++) {
			complete &= heights.contains(y);
		}
		TowerTestSupport.result("daisWalk", "floor=" + floor + " path=" + path.size() + " heights=" + heights + " last=" + last[1] + " jumps=" + jumps);
		helper.assertTrue(!path.isEmpty() && last[1] == s, "the walk from the foot ends at height " + last[1] + ", not " + s);
		helper.assertTrue(complete && jumps == 0, "the walk skips a height: " + heights);
		List<BlockPos> wisp = TowerWisps.path(tower, floor, BlockPos.containing(plan.arrivalPos()));
		int top = wisp.stream().mapToInt(step -> step.getY() - TowerLayout.BASE_Y).max().orElse(-1);
		// The ground rises in hills now, so the way from the arrival may cross higher ground than the stair: it climbs at least the stair.
		helper.assertTrue(!wisp.isEmpty() && top >= s, "the wisp's way from the arrival peaks at " + top + ", under the stair's " + s);
		helper.succeed();
	}

	/** Catches a stair or a pillar a blast takes. */
	@GameTest(maxTicks = 100)
	public void noBlastTakesTheStairOrTheGate(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloor();
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		int s = plan.biome().steps();
		int f = plan.portalFacing();
		int px = plan.portalX();
		int pz = plan.portalZ();
		int before = countStrong(tower, plan, s);
		Vec3 centre = Vec3.atCenterOf(plan.origin().offset(px + f * 3, s - 1, pz));
		for (Level.ExplosionInteraction kind : new Level.ExplosionInteraction[] {Level.ExplosionInteraction.TNT, Level.ExplosionInteraction.MOB, Level.ExplosionInteraction.BLOCK}) {
			tower.explode(null, centre.x, centre.y, centre.z, 12.0F, kind);
		}
		int after = countStrong(tower, plan, s);
		BlockState step = at(tower, plan, px + f * 3, s - 2, pz);
		float speed = step.getDestroySpeed(tower, plan.origin().offset(px + f * 3, s - 2, pz));
		TowerTestSupport.result("daisBlast", "floor=" + floor + " before=" + before + " after=" + after + " destroySpeed=" + speed);
		helper.assertTrue(before > 50 && after == before, "a blast left " + after + " of " + before + " stairs and gate blocks");
		helper.assertTrue(speed < 0.0F, "a stair has destroy speed " + speed);
		helper.succeed();
	}

	private static int countStrong(final ServerLevel tower, final TowerPlan plan, final int s) {
		int count = 0;
		int f = plan.portalFacing();
		int px = plan.portalX();
		int pz = plan.portalZ();
		for (int k = 1; k < s; k++) {
			for (int z = pz - 3 - k; z <= pz + 4 + k; z++) {
				count += at(tower, plan, px + f * (2 + k), s - k - 1, z).is(TowerBlocks.TOWER_STAIR) ? 1 : 0;
			}
		}
		for (int z : new int[] {pz - 2, pz + 3}) {
			for (int y = s; y <= s + 6; y++) {
				count += at(tower, plan, px, y, z).is(TowerBlocks.TOWER_WALL) ? 1 : 0;
			}
		}
		return count;
	}

	/** Catches a skin the stair has no model for: each of the 40 states of each skin has a variant, and each model is a file. */
	@GameTest
	public void everySkinHasAStairModel(final GameTestHelper helper) {
		String base = "/assets/mod-stella-tower/";
		try (InputStreamReader reader = new InputStreamReader(TowerDaisGameTest.class.getResourceAsStream(base + "blockstates/tower_stair.json"), StandardCharsets.UTF_8)) {
			JsonObject variants = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("variants");
			Set<String> models = new HashSet<>();
			variants.entrySet().forEach(entry -> models.add(entry.getValue().getAsJsonObject().get("model").getAsString()));
			int missing = 0;
			for (String model : models) {
				missing += TowerDaisGameTest.class.getResource(base + "models/" + model.substring(model.indexOf(':') + 1) + ".json") == null ? 1 : 0;
			}
			int skins = TowerSkin.values().length;
			TowerTestSupport.result("stairModels", "skins=" + skins + " variants=" + variants.size() + " models=" + models.size() + " missing=" + missing);
			helper.assertTrue(variants.size() == skins * 40, "the stair has " + variants.size() + " variants, not " + skins * 40);
			helper.assertTrue(missing == 0, missing + " stair models are not files");
		} catch (java.io.IOException | NullPointerException e) {
			helper.fail("the stair's blockstate could not be read: " + e);
		}
		try (InputStreamReader reader = new InputStreamReader(TowerDaisGameTest.class.getResourceAsStream(base + "blockstates/tower_maze_wall.json"), StandardCharsets.UTF_8)) {
			JsonObject variants = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("variants");
			helper.assertTrue(variants.size() == TowerSkin.values().length, "the maze wall has " + variants.size() + " skins drawn of " + TowerSkin.values().length);
		} catch (java.io.IOException | NullPointerException e) {
			helper.fail("the maze wall's blockstate could not be read: " + e);
		}
		helper.succeed();
	}
}
