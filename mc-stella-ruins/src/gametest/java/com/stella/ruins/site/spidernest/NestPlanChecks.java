package com.stella.ruins.site.spidernest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The checks on a plan that need no world. The game tests call them, and so can a plain main, which is how the plan
 * was tuned before any game test ran. Each check lists its failures as text and never throws.
 */
final class NestPlanChecks {
	private NestPlanChecks() {
	}

	/** The failures of one plan, or an empty list when it passes every check. */
	static List<String> check(final NestPlan plan) {
		List<String> fail = new ArrayList<>();
		String where = "seed " + plan.seed + ": ";
		List<NestPlan.Sac> sacs = plan.sacs();
		if (sacs.size() != plan.sacTarget || sacs.size() < NestPlan.MIN_SACS || sacs.size() > NestPlan.MAX_SACS) {
			fail.add(where + "sac count " + sacs.size() + " target " + plan.sacTarget);
		}
		long ceilings = sacs.stream().filter(s -> s.ceiling).count();
		long walls = sacs.stream().filter(s -> !s.ceiling).count();
		if (ceilings < 2 || walls < 3) {
			fail.add(where + "sac mix ceiling " + ceilings + " wall " + walls);
		}
		Set<Integer> chambers = new HashSet<>();
		boolean tunnelSac = false;
		for (NestPlan.Sac sac : sacs) {
			chambers.add(sac.chamber);
			tunnelSac |= sac.tunnel();
			if (plan.cell(sac.x, sac.y, sac.z) != NestPlan.SAC) {
				fail.add(where + "sac " + sac.id + " is not in the grid");
			}
			if (sac.reachDistance > NestPlan.REACH + 1.0E-9 || !plan.standable(sac.reachX, sac.reachZ)) {
				fail.add(where + "sac " + sac.id + " has no reach cell: " + sac.reachDistance);
			}
			for (int[] cell : plan.sightOf(sac)) {
				byte id = plan.cell(cell[0], cell[1], cell[2]);
				if (id == NestPlan.WEB || id == NestPlan.ROCK) {
					fail.add(where + "sac " + sac.id + " sight line holds " + id + " at " + cell[0] + "," + cell[1] + "," + cell[2]);
				}
			}
			if (!plan.reachableWithoutWeb(sac.reachX, sac.reachZ)) {
				fail.add(where + "sac " + sac.id + " has no web-free path");
			}
		}
		for (int k = 0; k < plan.blobCount; k++) {
			if (!chambers.contains(k)) {
				fail.add(where + "chamber " + k + " holds no sac");
			}
		}
		if (!tunnelSac) {
			fail.add(where + "the side tunnel holds no sac");
		}
		fail.addAll(markers(plan, where));
		NestPlan.Spot core = plan.core();
		NestPlan.Spot vault = plan.vault();
		if (plan.cell(core.x(), core.y(), core.z()) != NestPlan.CORE || plan.cell(vault.x(), vault.y(), vault.z()) != NestPlan.VAULT) {
			fail.add(where + "core or vault missing");
		}
		for (NestPlan.Spot front : new NestPlan.Spot[] {plan.coreFront(), plan.vaultFront()}) {
			if (front == null || !plan.standable(front.x(), front.z()) || !plan.reachableWithoutWeb(front.x(), front.z())) {
				fail.add(where + "core or vault front is not standable and web-free");
			}
		}
		for (int iz = -2; iz <= 2; iz++) {
			for (int ix = -2; ix <= 2; ix++) {
				int x = plan.pitX() + ix;
				int z = plan.pitZ() + iz;
				if (plan.floorAt(x, z) != plan.pitFloor() || plan.topAt(x, z) - plan.floorAt(x, z) < 4) {
					fail.add(where + "the pit is not flat or high at " + x + "," + z);
				}
			}
		}
		fail.addAll(shell(plan, where));
		double share = plan.webRatio() * 100.0;
		if (Math.abs(share - plan.webPercent) > 1.0) {
			fail.add(where + "web share " + share + " target " + plan.webPercent);
		}
		if (plan.webPercent < NestPlan.MIN_WEB_PERCENT || plan.webPercent > NestPlan.MAX_WEB_PERCENT) {
			fail.add(where + "web roll " + plan.webPercent);
		}
		if (plan.lichens().size() < 4 || plan.lichens().size() > 24) {
			fail.add(where + "lichen cells " + plan.lichens().size());
		}
		if (plan.cocoonCount() < 4 || plan.bonePileCount() < 1) {
			fail.add(where + "cocoons " + plan.cocoonCount() + " bone piles " + plan.bonePileCount());
		}
		if (!Double.isFinite(plan.route().timed()) || plan.route().timed() >= 1.0E8) {
			fail.add(where + "the route is not finite");
		}
		return fail;
	}

	private static List<String> markers(final NestPlan plan, final String where) {
		List<String> fail = new ArrayList<>();
		if (plan.weavers().size() != NestPlan.WEAVERS || plan.crawlers().size() != NestPlan.CRAWLERS) {
			fail.add(where + "weavers " + plan.weavers().size() + " crawlers " + plan.crawlers().size());
		}
		List<NestPlan.Spot> all = new ArrayList<>(plan.weavers());
		all.addAll(plan.crawlers());
		all.add(plan.matriarch());
		for (NestPlan.Spot spot : all) {
			if (!plan.standable(spot.x(), spot.z()) || spot.y() != plan.floorAt(spot.x(), spot.z())) {
				fail.add(where + "a marker does not stand on a floor at " + spot);
			}
			if (!plan.reachableWithoutWeb(spot.x(), spot.z())) {
				fail.add(where + "no web-free path to marker " + spot);
			}
		}
		for (List<NestPlan.Spot> kind : List.of(plan.weavers(), plan.crawlers())) {
			for (int a = 0; a < kind.size(); a++) {
				for (int b = a + 1; b < kind.size(); b++) {
					if (Math.hypot(kind.get(a).x() - kind.get(b).x(), kind.get(a).z() - kind.get(b).z()) < 3.99) {
						fail.add(where + "two markers of one kind are closer than 4");
					}
				}
			}
		}
		return fail;
	}

	private static List<String> shell(final NestPlan plan, final String where) {
		List<String> fail = new ArrayList<>();
		for (int z = 0; z < NestPlan.NEST_Z; z++) {
			for (int x = 0; x < NestPlan.SIZE_X; x++) {
				boolean rim = x < 2 || x >= NestPlan.SIZE_X - 2 || z < 2 || z >= NestPlan.NEST_Z - 2;
				if (rim && plan.floorAt(x, z) >= 0 && plan.tagAt(x, z) != NestPlan.TAG_MOUTH) {
					fail.add(where + "air in the rock rim at " + x + "," + z);
				}
				if (plan.inPlug(x, z)) {
					for (int y = plan.floorAt(x, z); y <= plan.topAt(x, z); y++) {
						if (plan.cell(x, y, z) != NestPlan.WEB) {
							fail.add(where + "a hole in the plug at " + x + "," + y + "," + z);
						}
					}
				}
				for (int y = 0; y < NestPlan.SIZE_Y; y++) {
					if ((y == 0 || y > NestPlan.MAX_AIR_Y) && plan.cell(x, y, z) != NestPlan.ROCK) {
						fail.add(where + "the floor or roof rock is broken at " + x + "," + y + "," + z);
					}
				}
			}
		}
		return fail;
	}
}
