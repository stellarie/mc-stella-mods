package com.stella.tower;

import com.stella.tower.LostCastleLayout.Family;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The castle's floors as small tower floors: every floor but the bottom one rolls a maze, a cavern or a biome
 * field, the bottom floor is the great hall, and no light stands in the castle but lit candles and the portal's.
 */
public class TowerLostCastleLayoutGameTest {
	private static final int SEEDS = 20;
	private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/**
	 * Catches a castle whose floors all draw one layout, one that never draws a family over twenty seeds, a bottom floor
	 * that is not the great hall, and a layout that leaves the full height unused: a maze, a cavern, a hall or a
	 * field must raise stone to the ceiling somewhere.
	 */
	@GameTest(maxTicks = 100)
	public void everyFloorButTheBottomRollsAFamilyAndTheBottomIsTheGreatHall(final GameTestHelper helper) {
		Map<Family, Integer> seen = new EnumMap<>(Family.class);
		List<String> faults = new ArrayList<>();
		for (int seed = 0; seed < SEEDS; seed++) {
			LostCastlePlan plan = LostCastlePlan.of(seed * 7919L + 13L, LostCastleGrid.MAX_FLOORS, LostCastleGrid.MIN_DROP + seed % (LostCastleGrid.MAX_SLOPE + 1));
			for (LostCastlePlan.Level floor : plan.levels()) {
				boolean bottom = floor.index() == plan.floors() - 1;
				if (bottom != (floor.family() == Family.HALL)) {
					faults.add("seed " + seed + " floor " + floor.index() + " is a " + floor.family());
				}
				if (!bottom) {
					seen.merge(floor.family(), 1, Integer::sum);
				}
				int tallest = 0;
				for (int x = 0; x < LostCastleGrid.SIZE; x++) {
					for (int z = 0; z < LostCastleGrid.SIZE; z++) {
						tallest = Math.max(tallest, floor.layout().fill(x, z));
					}
				}
				if (tallest != LostCastleGrid.CLEAR) {
					faults.add("seed " + seed + " floor " + floor.index() + " " + floor.family() + " rises " + tallest + " of " + LostCastleGrid.CLEAR);
				}
			}
		}
		TowerTestSupport.result("castleFamilies", "seen=" + seen + " faults=" + faults);
		for (Family family : new Family[] {Family.MAZE, Family.CAVERN, Family.FIELD}) {
			helper.assertTrue(seen.getOrDefault(family, 0) > 0, "no floor of " + SEEDS + " seeds drew a " + family + ": " + seen);
		}
		helper.assertTrue(!seen.containsKey(Family.HALL), "a floor above the bottom drew the hall");
		helper.assertTrue(faults.isEmpty(), "the families are wrong: " + faults);
		helper.succeed();
	}

	/**
	 * Catches a bottom floor that is not the great hall as built: a ring of pillars that reach the ceiling, and the
	 * portal, its candle braziers, and the vault behind a cracked wall that the hall keeps.
	 */
	@GameTest(maxTicks = 200)
	public void theBottomFloorBuildsTheGreatHallWithPillarsPortalAndVault(final GameTestHelper helper) {
		TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, LostCastlePalette.GREEN, Direction.SOUTH, 4);
		LostCastlePlan.Level bottom = arena.level(3);
		int feet = LostCastleGrid.feet(4, 3);
		int head = LostCastleGrid.head(4, 3);
		int pillars = 0;
		for (int x = 0; x < LostCastleGrid.SIZE; x++) {
			for (int z = 0; z < LostCastleGrid.SIZE; z++) {
				boolean ring = x >= 10 && x <= 39 && z >= 10 && z <= 39 && (x < 12 || x > 37 || z < 12 || z > 37);
				boolean full = true;
				for (int y = feet; y <= head; y++) {
					full &= !arena.at(x, y, z).isAir();
				}
				pillars += ring && full ? 1 : 0;
			}
		}
		boolean portal = arena.at(LostCastleBuilder.PORTAL_X, feet + 1, LostCastleBuilder.PORTAL_Z).is(TowerBlocks.TOWER_PORTAL);
		int cracked = 0;
		LostCastlePlan.Vault vault = arena.piece().plan().vault();
		for (int x = vault.x0() - 2; x <= vault.x1() + 2; x++) {
			for (int z = vault.z0() - 2; z <= vault.z1() + 2; z++) {
				for (int y = feet; y <= head; y++) {
					cracked += arena.at(x, y, z).is(Blocks.CRACKED_STONE_BRICKS) ? 1 : 0;
				}
			}
		}
		TowerTestSupport.result("castleHall", "family=" + bottom.family() + " pillarColumns=" + pillars + " portal=" + portal + " cracked=" + cracked);
		helper.assertTrue(bottom.family() == Family.HALL, "the bottom floor is a " + bottom.family());
		helper.assertTrue(pillars >= 8 * 4, "the hall's pillar ring holds only " + pillars + " columns");
		helper.assertTrue(portal, "the hall holds no lit portal in its middle");
		helper.assertTrue(cracked >= LostCastlePlan.Vault.INSIDE * LostCastlePlan.Vault.INSIDE, "the vault's cracked wall is " + cracked + " blocks");
		helper.succeed();
	}

	/**
	 * Catches a lamp, torch, fire or other light in the castle below the ring: the only light is a lit candle, the
	 * portal soul lanterns, and the portal itself (its frame and its blue pane).
	 */
	@GameTest(maxTicks = 200)
	public void noLightSourceButLitCandlesAndThePortalStandsBelowTheRing(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (LostCastlePalette palette : LostCastlePalette.values()) {
			TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, palette, FACINGS[palette.ordinal()], LostCastleGrid.MIN_FLOORS + palette.ordinal());
			BoundingBox box = arena.box();
			int ring = box.minY() + arena.piece().surfaceFeet();
			int candles = 0;
			int lanterns = 0;
			for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), ring - 1, box.maxZ())) {
				BlockState state = arena.level().getBlockState(at);
				if (state.getLightEmission() <= 0) {
					continue;
				}
				if (state.is(Blocks.CANDLE) && state.getValue(CandleBlock.LIT)) {
					candles++;
				} else if (state.is(Blocks.SOUL_LANTERN)) {
					lanterns++;
				} else if (!state.is(TowerBlocks.TOWER_PORTAL) && !state.is(TowerBlocks.TOWER_FRAME)) {
					faults.add(palette + " has " + state.getBlock().getName().getString() + " at " + at.toShortString());
				}
			}
			report.append(" [").append(palette).append(" candles=").append(candles).append(" lanterns=").append(lanterns).append(']');
			if (candles < 20 * arena.piece().floors() || lanterns != 2) {
				faults.add(palette + " has " + candles + " lit candles and " + lanterns + " soul lanterns");
			}
		}
		TowerTestSupport.result("castleLight", "faults=" + faults + report);
		helper.assertTrue(faults.isEmpty(), "the castle holds a light it should not, or too few candles: " + faults);
		helper.succeed();
	}
}
