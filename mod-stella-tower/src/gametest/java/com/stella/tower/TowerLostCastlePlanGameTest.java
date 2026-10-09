package com.stella.tower;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** What a lost castle holds is decided from its seed alone, and the plan keeps its rules over many seeds. */
public class TowerLostCastlePlanGameTest {
	/** Seeds read for each variant. */
	private static final int SEEDS = 2000;

	private static long seed(final int index) {
		return index * 0x9E3779B97F4A7C15L + 0x5851F42D4C957F2DL;
	}

	/** The columns a chest site claims: the whole nook it stands in, since the nook's other columns stay clear. */
	private static Set<Long> nookColumns(final LostCastlePlan.Chest chest, final List<TowerHex.Nook> nooks) {
		Set<Long> columns = new HashSet<>();
		for (TowerHex.Nook nook : nooks) {
			if (nook.holds(chest.x(), chest.z())) {
				for (int[] column : nook.columns()) {
					columns.add(key(column[0], column[1]));
				}
			}
		}
		return columns;
	}

	private static long key(final int x, final int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/** What is wrong with one plan, or nothing. */
	static String fault(final LostCastlePlan plan) {
		List<TowerHex.Nook> nooks = TowerHex.nooks();
		Set<LostFloorKind> kinds = EnumSet.noneOf(LostFloorKind.class);
		int[] court = LostCastleVariant.court();
		int blocks = 0;
		int doubles = 0;
		List<TowerHex.Nook> below = List.of();
		if (plan.floors().size() != TowerHex.STOREYS) {
			return "holds " + plan.floors().size() + " storeys";
		}
		for (LostCastlePlan.FloorPlan floor : plan.floors()) {
			int storey = floor.storey();
			boolean top = storey == TowerHex.STOREYS - 1;
			if (!kinds.add(floor.kind())) {
				return "kind " + floor.kind() + " stands twice";
			}
			LostFloorKind.Slot slot = storey == 0 ? LostFloorKind.Slot.GROUND : top ? LostFloorKind.Slot.TOP : LostFloorKind.Slot.MIDDLE;
			if (floor.kind().slot() != slot) {
				return "storey " + storey + " is " + floor.kind() + ", a " + floor.kind().slot() + " kind";
			}
			int wantedTurrets = top ? TowerHex.cells().size() - 1 : 1;
			if (floor.turrets().size() != wantedTurrets) {
				return "storey " + storey + " holds " + floor.turrets().size() + " turrets, not " + wantedTurrets;
			}
			if (top) {
				Set<Long> rooms = new HashSet<>();
				for (LostCastlePlan.Turret turret : floor.turrets()) {
					rooms.add(key(turret.nook().cx(), turret.nook().cz()));
				}
				boolean hubHeld = rooms.contains(key(TowerHex.COLS / 2, TowerHex.COLS / 2));
				if (rooms.size() != wantedTurrets || hubHeld) {
					return "the top storey does not hold one turret in every room but the portal room: " + rooms.size() + " rooms, portal room held " + hubHeld;
				}
			}
			if (floor.turrets().size() != LostCastlePlan.turretsAt(storey)) {
				return "storey " + storey + " holds " + floor.turrets().size() + " turrets, not " + LostCastlePlan.turretsAt(storey);
			}
			Set<Long> claimed = new HashSet<>();
			for (LostCastlePlan.Turret turret : floor.turrets()) {
				TowerHex.Nook site = turret.nook();
				if (!site.usable() || top && site.hub() || turret.cover() == null) {
					return "storey " + storey + " turret " + site + " is unusable, in the portal room or uncovered";
				}
				if (below.contains(site)) {
					for (TowerHex.Nook other : nooks) {
						if (other.usable() && other.cx() == site.cx() && other.cz() == site.cz() && !other.equals(site) && LostCastlePlan.reachHeld(other)
							&& !LostCastleHex.inStairLane(other.tuckX(), other.tuckZ()) && !(storey == 0 && other.cz() == 0 && other.cx() == TowerHex.COLS / 2)) {
							return "storey " + storey + " turret stacks on the storey below although its room has another site";
						}
					}
				}
				for (int[] column : site.columns()) {
					if (LostCastleHex.inStairLane(column[0], column[1])) {
						return "storey " + storey + " turret stands in the stair lane";
					}
					if (!claimed.add(key(column[0], column[1]))) {
						return "storey " + storey + " two turrets claim a column";
					}
				}
				if (!LostCastlePlan.reachHeld(site)) {
					return "storey " + storey + " turret reaches outside the octagon";
				}
			}
			int storeyBlocks = top ? 1 : 0;
			for (LostCastlePlan.Chest chest : floor.chests()) {
				Set<Long> nook = nookColumns(chest, nooks);
				if (nook.size() != 4) {
					return "storey " + storey + " chest at " + chest.x() + "," + chest.z() + " is in no nook";
				}
				for (long column : nook) {
					if (!claimed.add(column)) {
						return "storey " + storey + " chest claims a column twice";
					}
				}
				for (int[] column : new int[][] {{chest.x(), chest.z()}, {chest.x2(), chest.z2()}}) {
					if (TowerHex.onCrossing(column[0], column[1]) || LostCastleHex.inStairLane(column[0], column[1])) {
						return "storey " + storey + " chest on the crossing or in the stair lane";
					}
					if (storey == 0 && column[0] >= court[0] && column[0] <= court[2] && column[1] >= court[1] && column[1] <= court[3]) {
						return "ground chest stands in the yard";
					}
					if (top && LostCastleHex.inPortalHall(column[0], LostCastleHex.portalSpot().getY(), column[1])) {
						return "top chest stands in the portal hall";
					}
				}
				if (chest.isDouble()) {
					doubles++;
					storeyBlocks += 2;
					if (Math.abs(chest.x() - chest.x2()) + Math.abs(chest.z() - chest.z2()) != 1) {
						return "storey " + storey + " double chest halves do not touch";
					}
				} else {
					storeyBlocks++;
				}
			}
			if (storeyBlocks != LostCastleHex.chestBlocksAt(storey) || storeyBlocks != floor.kind().chestBlocks()) {
				return "storey " + storey + " holds " + storeyBlocks + " chest blocks, not " + LostCastleHex.chestBlocksAt(storey);
			}
			blocks += storeyBlocks;
			int[] counts = new int[LostCastlePlan.ScatterKind.values().length];
			for (LostCastlePlan.Scatter item : floor.scatter()) {
				if (!claimed.add(key(item.x(), item.z()))) {
					return "storey " + storey + " ruin claims the column " + item.x() + "," + item.z() + " twice";
				}
				if (TowerHex.onCrossing(item.x(), item.z()) || LostCastleHex.inStairLane(item.x(), item.z())
					|| TowerHex.kind(item.x(), item.z()) != TowerHex.Part.WAY || top && LostCastleHex.inPortalHall(item.x(), LostCastleHex.portalSpot().getY(), item.z())) {
					return "storey " + storey + " ruin " + item.kind() + " on the crossing, the stair, the hall or outside a way";
				}
				counts[item.kind().ordinal()]++;
			}
			int[] wanted = {LostCastleHex.CANDLES, LostCastleHex.SKULLS, LostCastleHex.RUBBLE, LostCastleHex.GROWTH, LostCastleHex.COBWEBS, LostCastleHex.CHAINS, LostCastleHex.FLOOR_GAPS};
			LostCastlePlan.ScatterKind[] order = {LostCastlePlan.ScatterKind.CANDLE, LostCastlePlan.ScatterKind.SKULL, LostCastlePlan.ScatterKind.RUBBLE,
				LostCastlePlan.ScatterKind.GROWTH, LostCastlePlan.ScatterKind.COBWEB, LostCastlePlan.ScatterKind.CHAIN, LostCastlePlan.ScatterKind.GAP};
			for (int i = 0; i < order.length; i++) {
				if (counts[order[i].ordinal()] != wanted[i]) {
					return "storey " + storey + " holds " + counts[order[i].ordinal()] + " " + order[i] + ", not " + wanted[i];
				}
			}
			below = floor.turrets().stream().map(LostCastlePlan.Turret::nook).toList();
		}
		if (blocks != LostCastleHex.castleChestBlocks()) {
			return "holds " + blocks + " chest blocks, not " + LostCastleHex.castleChestBlocks();
		}
		if (doubles < LostCastleHex.MIN_DOUBLES) {
			return "holds " + doubles + " double chests";
		}
		return "";
	}

	/** Every rule of a plan, over every seed and every variant. */
	@GameTest(maxTicks = 200)
	public void planInvariantsHoldOverManySeeds(final GameTestHelper helper) {
		int bad = 0;
		String first = "";
		Set<LostFloorKind> seen = EnumSet.noneOf(LostFloorKind.class);
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (int i = 0; i < SEEDS; i++) {
				LostCastlePlan plan = LostCastlePlan.of(seed(i), variant);
				String fault = fault(plan);
				if (!fault.isEmpty()) {
					bad++;
					first = first.isEmpty() ? variant + "/" + i + ": " + fault : first;
				}
				if (!plan.equals(LostCastlePlan.of(seed(i), variant))) {
					bad++;
					first = first.isEmpty() ? variant + "/" + i + ": the plan differs on a second draw" : first;
				}
				plan.floors().forEach(floor -> seen.add(floor.kind()));
			}
		}
		TowerTestSupport.result("castlePlans", "plans=" + SEEDS * LostCastleVariant.values().length + " bad=" + bad + " kindsSeen=" + seen + " first=" + first);
		helper.assertTrue(bad == 0, bad + " plans break a rule; first: " + first);
		helper.assertTrue(seen.size() == LostFloorKind.values().length, "some kinds never occur: " + seen);
		helper.succeed();
	}

	/** Every usable nook is walled on both sides, four columns of a way, and the usable ones are numerous. */
	@GameTest(maxTicks = 20)
	public void everyUsableNookStandsAgainstTwoWalls(final GameTestHelper helper) {
		List<TowerHex.Nook> usable = new ArrayList<>();
		for (TowerHex.Nook nook : TowerHex.nooks()) {
			if (nook.usable()) {
				usable.add(nook);
				for (int[] column : nook.columns()) {
					helper.assertTrue(TowerHex.cellOf(column[0], column[1]) != null && !TowerHex.onCrossing(column[0], column[1]), "a nook column is not floor of a held way");
				}
				helper.assertTrue(TowerHex.kind(nook.tuckX() - nook.dx(), nook.tuckZ()) == TowerHex.Part.WALL, "no wall behind the tuck column along x");
				helper.assertTrue(TowerHex.kind(nook.tuckX(), nook.tuckZ() - nook.dz()) == TowerHex.Part.WALL, "no wall behind the tuck column along z");
			}
		}
		TowerTestSupport.result("castleNooks", "usable=" + usable.size() + " all=" + TowerHex.nooks().size());
		helper.assertTrue(usable.size() >= 12, "only " + usable.size() + " usable nooks");
		helper.succeed();
	}
}
