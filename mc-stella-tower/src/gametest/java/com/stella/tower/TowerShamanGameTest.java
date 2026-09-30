package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Void shamans: skeletons of the spells mod's making that blink away when struck, and leave void ingots. */
public class TowerShamanGameTest {

	/** A spells mod that records what it is asked to cast. */
	private static final class Fake implements TowerSpells.Caster {
		final List<String> strikes = new ArrayList<>();
		final List<String> aids = new ArrayList<>();
		final int cooldown;

		Fake(final int cooldown) {
			this.cooldown = cooldown;
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public List<String> offensive() {
			return List.of("spark_shot", "frostbind", "ice_lance");
		}

		@Override
		public List<String> support() {
			return List.of("sanctuary", "tailwind", "stoneguard");
		}

		@Override
		public int cooldownOf(final String spell) {
			return this.cooldown;
		}

		@Override
		public boolean castAt(final Mob caster, final String spell, final LivingEntity target) {
			this.strikes.add(spell);
			return true;
		}

		@Override
		public boolean support(final Mob caster, final String spell) {
			this.aids.add(spell);
			return true;
		}
	}

	/** A floor of the tower with none of its own mobs or ghosts, so the case's shaman is the only life on it. */
	private static int quietFloor(final ServerLevel tower) {
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerMobs.pinFloorCap(floor, 0);
		TowerGhosts.pinRiseChance(floor, 0.0);
		TowerFloors.ensure(tower, floor);
		return floor;
	}

	private static void release(final int floor) {
		TowerMobs.pinFloorCap(floor, null);
		TowerGhosts.pinRiseChance(floor, null);
		TowerShamans.pinHolds(floor, null);
		TowerShamans.pinIngotChance(floor, null);
		TowerShamans.pinRestTicks(floor, null);
		TowerShamans.pinBlinkTicks(floor, null);
		TowerSpells.install(floor, null);
	}

	@GameTest
	public void aShamanIsASkeletonWithOneStrikeAndOneAid(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = quietFloor(tower);
		Fake fake = new Fake(40);
		TowerSpells.install(floor, fake);
		try {
			ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
			Skeleton shaman = TowerShamans.summon(tower, floor, player.position().add(2.0, 0.0, 0.0));
			helper.assertTrue(shaman != null, "no shaman could be raised");
			TowerShamans.Kit kit = shaman.getAttached(TowerShamans.KIT);
			TowerMobs.State state = TowerMobs.stateOf(shaman);
			String name = shaman.getDisplayName().getString();
			TowerTestSupport.result("shamanKit", "kit=" + kit + " rank=" + (state == null ? null : state.rank()) + " name=" + name);
			shaman.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(kit != null && fake.offensive().contains(kit.offensive()) && fake.support().contains(kit.support()),
				"a shaman's spells were " + kit);
			helper.assertTrue(state != null && state.rank() == TowerRank.MOB, "a shaman is a " + (state == null ? "no tower mob" : state.rank()));
			helper.assertTrue(name.endsWith("Void Shaman"), "a shaman is named " + name);
			helper.succeed();
		} finally {
			release(floor);
		}
	}

	/** A shaman that sees a player strikes and aids itself at its spells' cooldown, no faster. */
	@GameTest(maxTicks = 200)
	public void shamansCastTheirSpellsOnACooldown(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = quietFloor(tower);
		Fake fake = new Fake(40);
		TowerSpells.install(floor, fake);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		// Beside the arrival's zone, not in it: a player inside a zone is never a shaman's quarry.
		TowerTestSupport.ZoneSpots spots = TowerTestSupport.spotsBesideZone(TowerPlan.at(tower, floor));
		player.teleportTo(tower, spots.outside().x, spots.outside().y, spots.outside().z, java.util.Set.of(), 0.0F, 0.0F, true);
		TowerTestSupport.holdRoom(tower, spots.mob());
		Skeleton shaman = TowerShamans.summon(tower, floor, spots.mob());
		helper.assertTrue(shaman != null, "no shaman could be raised");
		shaman.setNoAi(true);
		shaman.snapTo(spots.mob());
		TowerShamans.Kit kit = shaman.getAttached(TowerShamans.KIT);
		long ticks = 110L;
		helper.runAfterDelay(ticks, () -> {
			int strikes = fake.strikes.size();
			int aids = fake.aids.size();
			boolean right = fake.strikes.stream().allMatch(kit.offensive()::equals) && fake.aids.stream().allMatch(kit.support()::equals);
			TowerTestSupport.result("shamanCasts", "strikes=" + strikes + " aids=" + aids + " over=" + ticks + " cooldown=" + fake.cooldown + " right=" + right);
			shaman.discard();
			TowerTestSupport.leave(helper, player);
			release(floor);
			int most = (int) (ticks / fake.cooldown) + 1;
			helper.assertTrue(strikes >= 2 && strikes <= most, "a shaman struck " + strikes + " times in " + ticks + " ticks, not 2 to " + most);
			helper.assertTrue(aids >= 2 && aids <= most, "a shaman aided itself " + aids + " times in " + ticks + " ticks, not 2 to " + most);
			helper.assertTrue(right, "a shaman cast a spell it does not carry");
			helper.succeed();
		});
	}

	/** Struck, a shaman blinks to a safe spot away from the players, and not again until it has rested. */
	@GameTest(maxTicks = 150)
	public void aStruckShamanBlinksAwayAndRests(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = quietFloor(tower);
		TowerSpells.install(floor, new Fake(1000));
		TowerShamans.pinBlinkTicks(floor, 60);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.holdRoom(tower, TowerLayout.arrival(tower, floor));
		Skeleton shaman = TowerShamans.summon(tower, floor, player.position().add(2.0, 0.0, 0.0));
		helper.assertTrue(shaman != null, "no shaman could be raised");
		shaman.setNoAi(true);
		shaman.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400.0);
		shaman.setHealth(shaman.getMaxHealth());
		Vec3 start = shaman.position();
		shaman.hurtServer(tower, player.damageSources().playerAttack(player), 1.0F);
		Vec3 first = shaman.position();
		// It may land in a chunk that only a neighbour's ticket keeps: holding its own, so it is not unloaded from under the case.
		TowerTestSupport.holdRoom(tower, first);
		shaman.damageCooldownTime = 0;
		shaman.hurtServer(tower, player.damageSources().playerAttack(player), 1.0F);
		Vec3 second = shaman.position();
		double blinked = first.distanceTo(start);
		double fromPlayer = first.distanceTo(player.position());
		TowerTestSupport.holdRoom(tower, TowerLayout.arrival(tower, floor));
		helper.runAfterDelay(80L, () -> {
			// A blink needs the floor's chunks loaded, which a ticket keeps for 300 ticks: asked again, as a
			// lagging server may have let the first lapse.
			TowerTestSupport.holdRoom(tower, TowerLayout.arrival(tower, floor));
			TowerTestSupport.holdRoom(tower, shaman.position());
			// A blink that finds no safe spot leaves the shaman able to try again, so the rested shaman is
			// struck until one is found: a lagging server may have left few of the floor's chunks loaded.
			double later = 0.0;
			for (int strike = 0; strike < 8 && later == 0.0; strike++) {
				shaman.damageCooldownTime = 0;
				shaman.hurtServer(tower, player.damageSources().playerAttack(player), 1.0F);
				later = shaman.position().distanceTo(second);
			}
			TowerTestSupport.result("shamanBlink", "blinked=" + blinked + " fromPlayer=" + fromPlayer + " again=" + second.distanceTo(first) + " later=" + later);
			shaman.discard();
			TowerTestSupport.leave(helper, player);
			release(floor);
			helper.assertTrue(blinked > 0.0 && fromPlayer >= TowerMobs.MIN_PLAYER_DISTANCE, "a struck shaman moved " + blinked + " and stands " + fromPlayer + " from the player");
			helper.assertTrue(second.distanceTo(first) == 0.0, "a shaman blinked again within its rest");
			helper.assertTrue(later > 0.0, "a shaman did not blink again after its rest (removed=" + shaman.isRemoved() + ")");
			helper.succeed();
		});
	}

	/** A fixed share of floors hold a shaman, drawn from the seed and never changing for a floor. */
	@GameTest
	public void aSharesOfFloorsHoldAShaman(final GameTestHelper helper) {
		long seed = helper.getLevel().getSeed();
		int floors = 200_000;
		int held = 0;
		int changed = 0;
		for (int floor = 1; floor <= floors; floor++) {
			boolean holds = TowerShamans.holds(seed, floor);
			held += holds ? 1 : 0;
			changed += holds == TowerShamans.holds(seed, floor) ? 0 : 1;
		}
		double rate = held / (double) floors;
		double bound = 6.0 * Math.sqrt(TowerShamans.FLOOR_CHANCE * (1.0 - TowerShamans.FLOOR_CHANCE) / floors);
		TowerTestSupport.result("shamanFloors", "held=" + held + "/" + floors + " rate=" + rate + " want=" + TowerShamans.FLOOR_CHANCE);
		helper.assertTrue(Math.abs(rate - TowerShamans.FLOOR_CHANCE) < bound, "a share of " + rate + " of floors hold a shaman, not " + TowerShamans.FLOOR_CHANCE);
		helper.assertTrue(changed == 0, "a floor's shaman changed between draws");
		helper.succeed();
	}

	/** A floor that holds a shaman keeps one, and no more, if the spells mod is here; if it is not, it keeps none. */
	@GameTest(maxTicks = 160)
	public void aFloorThatHoldsAShamanKeepsOneIfSpellsAreHere(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int withSpells = quietFloor(tower);
		int without = quietFloor(tower);
		TowerShamans.pinHolds(withSpells, true);
		TowerShamans.pinHolds(without, true);
		TowerSpells.install(withSpells, new Fake(40));
		TowerSpells.install(without, TowerSpells.ABSENT);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, withSpells);
		ServerPlayer other = TowerTestSupport.onFloor(helper, tower, without);
		TowerTestSupport.holdRoom(tower, TowerLayout.arrival(tower, withSpells));
		TowerTestSupport.holdRoom(tower, TowerLayout.arrival(tower, without));
		helper.runAfterDelay(100L, () -> {
			int kept = TowerShamans.on(tower, withSpells).size();
			int missing = TowerShamans.on(tower, without).size();
			TowerTestSupport.result("shamanKept", "kept=" + kept + " withoutSpells=" + missing);
			TowerTestSupport.leave(helper, player);
			TowerTestSupport.leave(helper, other);
			TowerShamans.on(tower, withSpells).forEach(Skeleton::discard);
			release(withSpells);
			release(without);
			helper.assertTrue(kept == 1, "a floor that holds a shaman kept " + kept + ", not one");
			helper.assertTrue(missing == 0, "a floor with no spells mod raised " + missing + " shamans");
			helper.succeed();
		});
	}

	/** Without the spells mod no floor raises a shaman, whatever it holds. */
	@GameTest(maxTicks = 120)
	public void withoutTheSpellsModNoShamanRises(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = quietFloor(tower);
		TowerShamans.pinHolds(floor, true);
		TowerSpells.install(floor, TowerSpells.ABSENT);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.holdRoom(tower, TowerLayout.arrival(tower, floor));
		helper.runAfterDelay(80L, () -> {
			int shamans = TowerShamans.on(tower, floor).size();
			TowerTestSupport.result("shamanAbsent", "shamans=" + shamans);
			TowerTestSupport.leave(helper, player);
			release(floor);
			helper.assertTrue(shamans == 0, shamans + " shamans rose with no spells mod");
			helper.succeed();
		});
	}

	/** A slain shaman's ingot chance grows with its floor, and a slain shaman may leave an ingot and a rune. */
	@GameTest
	public void aSlainShamanLeavesAVoidIngotAtItsChance(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = quietFloor(tower);
		TowerSpells.install(floor, new Fake(40));
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		int slain = 0;
		int ingots = 0;
		try {
			TowerShamans.pinIngotChance(floor, 1.0);
			Skeleton shaman = TowerShamans.summon(tower, floor, player.position().add(2.0, 0.0, 0.0));
			helper.assertTrue(shaman != null, "no shaman could be raised");
			AABB near = new AABB(shaman.position(), shaman.position()).inflate(4.0);
			shaman.hurtServer(tower, player.damageSources().playerAttack(player), 1.0E30F);
			slain += shaman.isAlive() ? 0 : 1;
			ingots += tower.getEntitiesOfClass(ItemEntity.class, near, item -> item.getItem().is(TowerItems.VOID_INGOT)).size();
			tower.getEntitiesOfClass(ItemEntity.class, near).forEach(ItemEntity::discard);
			TowerShamans.pinIngotChance(floor, 0.0);
			Skeleton second = TowerShamans.summon(tower, floor, player.position().add(2.0, 0.0, 0.0));
			second.hurtServer(tower, player.damageSources().playerAttack(player), 1.0E30F);
			int none = tower.getEntitiesOfClass(ItemEntity.class, near, item -> item.getItem().is(TowerItems.VOID_INGOT)).size();
			tower.getEntitiesOfClass(ItemEntity.class, near).forEach(ItemEntity::discard);
			Skeleton third = TowerShamans.summon(tower, floor, player.position().add(2.0, 0.0, 0.0));
			double runeChance = TowerRunes.dropChanceOf(third);
			third.discard();
			TowerTestSupport.result("shamanIngot", "slain=" + slain + " ingots=" + ingots + " noneAtZero=" + none + " floor1=" + TowerShamans.ingotChance(1)
				+ " floor100=" + TowerShamans.ingotChance(100) + " runeChance=" + runeChance);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(slain == 1 && ingots == 1, "a shaman slain at a certain chance left " + ingots + " ingots");
			helper.assertTrue(none == 0, "a shaman slain at no chance left " + none + " ingots");
			helper.assertTrue(Math.abs(TowerShamans.ingotChance(1) - (TowerShamans.INGOT_BASE + TowerShamans.INGOT_PER_FLOOR)) < 1.0E-12
				&& Math.abs(TowerShamans.ingotChance(100) - (TowerShamans.INGOT_BASE + 100 * TowerShamans.INGOT_PER_FLOOR)) < 1.0E-12
				&& TowerShamans.ingotChance(1000) > TowerShamans.ingotChance(100), "the ingot chance does not grow with the floor");
			helper.assertTrue(runeChance == TowerShamans.RUNE_CHANCE, "a shaman's rune chance is " + runeChance);
			helper.succeed();
		} finally {
			release(floor);
		}
	}

	/** Its floor rests after a shaman is slain, and raises another once the rest is over. */
	@GameTest(maxTicks = 200)
	public void aFloorRestsAfterItsShamanIsSlain(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = quietFloor(tower);
		TowerShamans.pinHolds(floor, true);
		TowerShamans.pinRestTicks(floor, 80);
		TowerSpells.install(floor, new Fake(40));
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.holdRoom(tower, TowerLayout.arrival(tower, floor));
		Skeleton first = TowerShamans.summon(tower, floor, player.position().add(2.0, 0.0, 0.0));
		helper.assertTrue(first != null, "no shaman could be raised");
		first.hurtServer(tower, player.damageSources().playerAttack(player), 1.0E30F);
		helper.runAfterDelay(40L, () -> {
			int resting = TowerShamans.on(tower, floor).size();
			helper.runAfterDelay(100L, () -> {
				int rested = TowerShamans.on(tower, floor).size();
				TowerTestSupport.result("shamanRest", "duringRest=" + resting + " afterRest=" + rested);
				TowerShamans.on(tower, floor).forEach(Skeleton::discard);
				TowerTestSupport.leave(helper, player);
				release(floor);
				helper.assertTrue(!first.isAlive() && resting == 0, "a floor raised a shaman during its rest: " + resting);
				helper.assertTrue(rested == 1, "a floor held " + rested + " shamans after its rest, not one");
				helper.succeed();
			});
		});
	}

	/** The shaman's own draw of its spells and blink need the spells mod's own casting: check in play with both installed. */
	@GameTest(required = false)
	public void theRealSpellsModCastsForAShamanRequiresManualTest(final GameTestHelper helper) {
		helper.fail("the spells mod is not loaded in this harness: with both mods installed, find a floor with a void shaman and check that it strikes and aids itself with real spells");
	}
}
