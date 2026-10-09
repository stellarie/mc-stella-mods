package com.stella.ruins.world;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Leads: a cleared common site pays a clue to a rarer one, and no clue map points at a site that is spent. */
public class RuinLeadGameTest {
	static final int TICKS = 6000;
	static final int FAR = 3000;

	static int clues(final RuinVaultBlockEntity vault, final String site) {
		int total = 0;
		for (int slot = 0; slot < vault.getContainerSize(); slot++) {
			ItemStack stack = vault.getItem(slot);
			if (stack.is(RuinClueItem.ITEM) && site.equals(stack.get(RuinClueItem.CLUE_SITE))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	@GameTest(maxTicks = TICKS)
	public void aClearedSiteThatDeclaresALeadPaysItsClueIntoTheVaultAtTheLeadChance(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.leader, arena -> {
			ServerLevel level = arena.level();
			AtomicLong clock = new AtomicLong(23_000_000L);
			RuinClock.Source oldClock = RuinClock.replace(l -> clock.get());
			RuinConfig.Values oldConfig = RuinConfig.get();
			try {
				RuinConfig.replace(oldConfig.withLeadChance(0.0));
				ServerPlayer player = arena.finish(helper);
				int atZero = clues(arena.vault(), "test_findable");
				arena.leave(player);
				clock.addAndGet(Claim.DAY_TICKS);
				arena.core().pulse(level);
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the site did not rearm: " + arena.phase());
				RuinConfig.replace(oldConfig.withLeadChance(1.0));
				arena.finish(helper);
				int atOne = clues(arena.vault(), "test_findable");
				WorldTestSupport.result("leadPaid", "cluesAtZero=" + atZero + " cluesAtOne=" + atOne);
				helper.assertTrue(atZero == 0, "a lead chance of 0 paid " + atZero + " clues");
				helper.assertTrue(atOne == 1, "a lead chance of 1.0 left " + atOne + " clues to test_findable in the vault, not 1");
			} finally {
				RuinConfig.replace(oldConfig);
				RuinClock.replace(oldClock);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aClueMapSkipsACompletedOnceSiteAndTargetsTheNextOne(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.finish(helper);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the once site did not complete");
			BlockPos claimed = arena.corePos();
			BlockPos far = claimed.offset(FAR, 0, 0);
			RuinClues.Locator old = RuinClues.replaceLocator((where, siteId, from) -> siteId.equals("test_surface")
				? Optional.of(from.distSqr(claimed) < 400.0 * 400.0 ? claimed : far) : Optional.empty());
			try {
				player.setItemInHand(InteractionHand.MAIN_HAND, RuinClueItem.of("test_surface"));
				RuinClueItem.ITEM.use(level, player, InteractionHand.MAIN_HAND);
				ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
				helper.assertTrue(held.is(Items.FILLED_MAP), "the clue became " + held.getItem() + ", not a map");
				var decorations = held.get(DataComponents.MAP_DECORATIONS);
				helper.assertTrue(decorations != null && decorations.decorations().size() == 1, "the map has no single target");
				var target = decorations.decorations().values().iterator().next();
				WorldTestSupport.result("clueSkipsSpent", "claimed=" + claimed.toShortString() + " far=" + far.toShortString() + " target=" + target.x() + "," + target.z());
				helper.assertTrue(Math.abs(target.x() - far.getX()) < 1.0 && Math.abs(target.z() - far.getZ()) < 1.0, "the map targets " + target.x() + "," + target.z() + ", not the next site " + far.toShortString());
			} finally {
				RuinClues.replaceLocator(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aSearchThatOnlyFindsCompletedOnceSitesFindsNothingAndAFreshSiteIsTakenAtOnce(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			arena.finish(helper);
			BlockPos claimed = arena.corePos();
			BlockPos fresh = claimed.offset(FAR, 0, 0);
			AtomicInteger calls = new AtomicInteger();
			RuinClues.Locator old = RuinClues.replaceLocator((where, siteId, from) -> {
				calls.incrementAndGet();
				return Optional.of(claimed);
			});
			try {
				Optional<BlockPos> spent = RuinClues.locate(level, "test_surface", claimed);
				int spentCalls = calls.get();
				calls.set(0);
				RuinClues.replaceLocator((where, siteId, from) -> {
					calls.incrementAndGet();
					return Optional.of(fresh);
				});
				Optional<BlockPos> open = RuinClues.locate(level, "test_surface", claimed);
				WorldTestSupport.result("locateFilter", "spent=" + spent + " spentCalls=" + spentCalls + " open=" + open + " openCalls=" + calls.get());
				helper.assertTrue(spent.isEmpty(), "a search that only finds a spent site returned " + spent);
				helper.assertTrue(spentCalls > 1 && spentCalls <= RuinClues.MAX_SEARCHES, "the spent search ran " + spentCalls + " times, not 2 to " + RuinClues.MAX_SEARCHES);
				helper.assertTrue(open.isPresent() && open.get().equals(fresh) && calls.get() == 1, "a site no one cleared was " + open + " after " + calls.get() + " searches");
			} finally {
				RuinClues.replaceLocator(old);
			}
		});
	}

	@GameTest
	public void theLeadMapSendsEachCommonSiteToOneRarerSiteAndTheRareThreeLeaveTheChestClues(final GameTestHelper helper) {
		java.util.Map<String, String> leads = java.util.Map.of(
			"wayshrine", "proving_grounds",
			"ambush_camp", "ruined_keep",
			"fallen_watchtower", "ruined_keep",
			"lost_miners_camp", "deep_forge",
			"meteor_crater", "deep_forge",
			"crystal_grotto", "sealed_vault",
			"spider_nest", "sealed_vault");
		leads.forEach((id, target) -> {
			RuinSite site = RuinSites.get(id);
			helper.assertTrue(site != null && site.leads().equals(java.util.List.of(target)), id + " leads to " + (site == null ? "nothing, unregistered" : site.leads()) + ", not " + target);
			helper.assertTrue(RuinSites.get(target) != null, id + " names " + target + ", which no mod registers");
		});
		for (String rare : java.util.List.of("ruined_keep", "deep_forge", "sealed_vault")) {
			RuinSite site = RuinSites.get(rare);
			helper.assertTrue(site != null && site.clueWeight() == 0, rare + " still rides the chest clues with weight " + (site == null ? -1 : site.clueWeight()));
		}
		helper.succeed();
	}
}
