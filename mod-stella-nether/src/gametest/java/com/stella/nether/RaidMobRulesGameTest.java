package com.stella.nether;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.Vec3;

/** The raid's own rules for its mobs: a plain brood, scrolls home, and strength by mode. */
public class RaidMobRulesGameTest {
	static final int BROODS = 10;

	/** Every blaze rolls elite and boss while it is pinned, so a brood the mobs mod may rank will be ranked. */
	static final String RANK_EVERY_BLAZE = """
		{
			"entities": {},
			"rolls": {
				"entities": { "minecraft:blaze": { "elite_chance": 1.0, "boss_chance": 1.0 } }
			}
		}""";

	@GameTest(environment = "mod-stella-nether-test:brood_plain", maxTicks = 600)
	public void theBroodHoldsNoEliteOrBossBlaze(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		GearConfig previous = GearConfigStore.get();
		boolean[] restored = new boolean[1];
		Runnable restore = () -> {
			if (!restored[0]) {
				restored[0] = true;
				GearConfigStore.set(previous);
			}
		};
		// A test that fails by timeout still gives the config back before the next batch reads it.
		helper.runAfterDelay(590, restore);
		GearConfigStore.set(GearConfigParser.parse(RANK_EVERY_BLAZE));
		List<Entity> brood = new ArrayList<>();
		for (int i = 0; i < BROODS; i++) {
			SovereignSpecials.Cast cast = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.BROOD, scene.player());
			SovereignSpecials.tickOnce(scene.raid(), scene.sovereign());
			// Held where they rise, so the wait below finds each one where the chunks are held.
			cast.spawned().forEach(entity -> ((Blaze) entity).setNoAi(true));
			brood.addAll(cast.spawned());
		}
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, brood.toArray(Entity[]::new)), "the brood has not loaded yet"))
			.thenExecute(() -> {
				restore.run();
				long ranked = brood.stream().filter(e -> e instanceof Blaze b && (EliteState.isElite(b) || EliteState.isBoss(b))).count();
				NetherTestSupport.result("broodPlain", "blazes=" + brood.size() + " eliteOrBoss=" + ranked);
				brood.forEach(Entity::discard);
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(brood.size() == BROODS * SovereignSpecials.BROOD_SIZE && ranked == 0, ranked + " of " + brood.size() + " brood blazes rank elite or boss");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:scroll_rate", maxTicks = 20)
	public void aRaidMobLeavesAScrollAtItsChance(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(23L);
		int rolls = 100_000;
		int scrolls = 0;
		for (int i = 0; i < rolls; i++) {
			scrolls += RaidDrops.scrollDrops(random) ? 1 : 0;
		}
		double rate = (double) scrolls / rolls;
		double sigma = Math.sqrt(RaidDrops.SCROLL_CHANCE * (1 - RaidDrops.SCROLL_CHANCE) / rolls);
		NetherTestSupport.result("scrollRate", "rate=" + rate + " chance=" + RaidDrops.SCROLL_CHANCE);
		helper.assertTrue(Math.abs(rate - RaidDrops.SCROLL_CHANCE) <= 4 * sigma && RaidDrops.SCROLL_CHANCE > 0, "scrolls fell at " + rate);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:scroll_drop", maxTicks = 100)
	public void aSlainRaidMobCanLeaveAScrollAndAMobOutsideCannot(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		ServerLevel raid = (ServerLevel) player.level();
		double previous = RaidDrops.setChance(1.0);
		try {
			ZombifiedPiglin inside = RaidModeGameTest.spawnInRaid(raid, player.position().add(2.0, 0.0, 0.0));
			ZombifiedPiglin outside = EntityTypes.ZOMBIFIED_PIGLIN.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
			outside.snapTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)), 0.0F, 0.0F);
			helper.getLevel().addFreshEntity(outside);
			inside.kill(raid);
			outside.kill(helper.getLevel());
			ItemEntity insideScroll = RaidDrops.droppedBy(inside.getUUID());
			ItemEntity outsideScroll = RaidDrops.droppedBy(outside.getUUID());
			boolean scroll = insideScroll != null && insideScroll.getItem().is(RaidDrops.scroll());
			NetherTestSupport.result("scrollDrops", "inside=" + scroll + " outside=" + (outsideScroll != null));
			helper.assertTrue(scroll && outsideScroll == null, "a raid mob left a scroll=" + scroll + ", one outside=" + (outsideScroll != null));
		} finally {
			RaidDrops.setChance(previous);
			NetherTestSupport.leave(helper, player);
		}
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:scroll_sovereign", maxTicks = 100)
	public void theSovereignLeavesAScrollForEachPlayerAndTwoMore(final GameTestHelper helper) {
		ServerPlayer first = BlazeSovereignGameTest.enter(helper);
		ServerPlayer second = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerLevel raid = (ServerLevel) first.level();
		Blaze sovereign = BlazeSovereign.rise(raid, BlazeSovereignGameTest.arena(raid, helper.getLevel().getServer(), 8));
		int players = raid.players().size();
		sovereign.kill(raid);
		int scrolls = BlazeSovereign.spoilsOf(sovereign).stream().filter(item -> item.getItem().is(RaidDrops.scroll()))
			.mapToInt(item -> item.getItem().getCount()).sum();
		NetherTestSupport.result("sovereignScrolls", "players=" + players + " scrolls=" + scrolls);
		NetherTestSupport.leave(helper, first);
		NetherTestSupport.leave(helper, second);
		helper.assertTrue(players >= 2 && scrolls == players + RaidDrops.SOVEREIGN_EXTRA_SCROLLS, players + " players, " + scrolls + " scrolls");
		helper.succeed();
	}
}
