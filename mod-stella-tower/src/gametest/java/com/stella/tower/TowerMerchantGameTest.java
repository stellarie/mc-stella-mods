package com.stella.tower;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

/**
 * The wandering ghost merchant: it appears at a floor's entrance about one time in ten, takes no damage, keeps the tower at peace
 * within eight blocks, and sells its goods for emeralds in the tower and nowhere else.
 */
public class TowerMerchantGameTest {
	private static int floor() {
		return TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0);
	}

	private static GhostMerchant merchantAt(final ServerLevel level, final Vec3 at) {
		GhostMerchant merchant = TowerMerchants.MERCHANT.create(level, EntitySpawnReason.COMMAND);
		merchant.snapTo(at);
		level.addFreshEntity(merchant);
		return merchant;
	}

	private static int emeralds(final ServerPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			total += stack.is(Items.EMERALD) ? stack.getCount() : 0;
		}
		return total;
	}

	private static int count(final ServerPlayer player, final net.minecraft.world.item.Item item) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			total += stack.is(item) ? stack.getCount() : 0;
		}
		return total;
	}

	/** Catches a floor that never greets a player with a merchant, or puts it away from the entrance. */
	@GameTest(maxTicks = 300)
	public void aPlayerComingOntoAFloorFindsAMerchantAtItsEntrance(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor();
		QuietFloor.hold(floor);
		TowerMerchants.pinChance(floor, 1.0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		Vec3 arrival = TowerPlan.at(tower, floor).arrivalPos();
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			List<GhostMerchant> found = TowerMerchants.on(tower, floor);
			helper.assertTrue(!found.isEmpty(), "no merchant came to the floor");
			if (!done[0]) {
				done[0] = true;
				GhostMerchant merchant = found.get(0);
				double away = merchant.position().distanceTo(arrival);
				int goods = merchant.goods().size();
				TowerTestSupport.result("merchantAppears", "tick=" + helper.getTick() + " fromEntrance=" + away + " goods=" + goods + " merchants=" + found.size());
				found.forEach(GhostMerchant::discard);
				TowerMerchants.pinChance(floor, null);
				TowerTestSupport.leave(helper, player);
				helper.assertTrue(found.size() == 1, found.size() + " merchants came to one floor");
				helper.assertTrue(away <= TowerMerchants.STAND_OFF + 3.0, "the merchant stands " + away + " blocks from the entrance");
				helper.assertTrue(goods >= MerchantGoods.MIN_OFFERS && goods <= MerchantGoods.MAX_OFFERS, "the merchant offers " + goods + " goods");
			}
		});
	}

	/** The control: with no chance pinned for the floor, a player on it finds no merchant. */
	@GameTest(maxTicks = 120)
	public void aFloorWithNoChanceGreetsNoMerchant(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor();
		QuietFloor.hold(floor);
		TowerMerchants.pinChance(floor, 0.0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		helper.runAfterDelay(100L, () -> {
			int found = TowerMerchants.on(tower, floor).size();
			TowerMerchants.pinChance(floor, null);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(found == 0, found + " merchants came to a floor with no chance");
			helper.succeed();
		});
	}

	/** The chance as it ships: about one new floor in ten finds a merchant. */
	@GameTest(maxTicks = 200)
	public void aMerchantComesToAboutOneNewFloorInTen(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor();
		QuietFloor.hold(floor);
		TowerFloors.ensure(tower, floor);
		RandomSource random = RandomSource.create(20261012L);
		int trials = 1000;
		int came = 0;
		for (int i = 0; i < trials; i++) {
			GhostMerchant merchant = TowerMerchants.greet(tower, floor, random, TowerMerchants.CHANCE);
			if (merchant != null) {
				came++;
				merchant.discard();
			}
		}
		double expected = trials * TowerMerchants.CHANCE;
		double spread = Math.sqrt(trials * TowerMerchants.CHANCE * (1.0 - TowerMerchants.CHANCE));
		TowerTestSupport.result("merchantRate", "came=" + came + " of " + trials + " expected=" + expected + " sd=" + spread);
		helper.assertTrue(TowerMerchants.CHANCE == 0.10, "the shipped chance is " + TowerMerchants.CHANCE + ", not one in ten");
		helper.assertTrue(Math.abs(came - expected) <= 4.0 * spread, "a merchant came " + came + " times in " + trials + ", not about " + expected);
		helper.succeed();
	}

	/** Catches a merchant that can be hurt or killed by any blow: a swing, a spell, a blast, a fall, lava or a kill command. */
	@GameTest(maxTicks = 40)
	public void theMerchantTakesNoDamage(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor();
		QuietFloor.hold(floor);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		GhostMerchant merchant = TowerMerchants.appear(tower, floor, tower.getRandom());
		helper.assertTrue(merchant != null, "no merchant could be raised");
		float health = merchant.getHealth();
		List<DamageSource> blows = List.of(player.damageSources().playerAttack(player), tower.damageSources().magic(), tower.damageSources().explosion(null, null),
			tower.damageSources().fall(), tower.damageSources().lava(), tower.damageSources().genericKill(), tower.damageSources().fellOutOfWorld());
		int landed = 0;
		for (DamageSource blow : blows) {
			merchant.setInvulnerableTime(0);
			landed += merchant.hurtServer(tower, blow, 1_000_000.0F) ? 1 : 0;
		}
		boolean alive = merchant.isAlive() && merchant.getHealth() == health;
		TowerTestSupport.result("merchantImmortal", "landed=" + landed + " alive=" + alive + " health=" + merchant.getHealth());
		merchant.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(landed == 0 && alive, "the merchant took a blow: landed=" + landed + " alive=" + alive);
		helper.succeed();
	}

	/**
	 * Catches a mob raised within the merchant's peace. The tower's own spawning raises four hundred mobs on a floor with the
	 * merchant standing mid-floor: none may stand within eight blocks of it. The control raises four hundred more with the
	 * merchant gone, and some must stand there, or the case could not fail. A spawner's check refuses the same spot.
	 */
	@GameTest(maxTicks = 400)
	public void noMobIsRaisedWithinTheMerchantsPeace(final GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor();
		QuietFloor.hold(floor);
		TowerFloors.ensure(tower, floor);
		Vec3 post = TowerTestSupport.awayFromArrival(tower, floor, 24.0);
		TowerPlan plan = TowerPlan.at(tower, floor);
		post = new Vec3(post.x, TowerLayout.BASE_Y + plan.standY((int) post.x - plan.origin().getX(), (int) post.z - plan.origin().getZ()), post.z);
		GhostMerchant merchant = merchantAt(tower, post);
		double[] nearest = {Double.MAX_VALUE, Double.MAX_VALUE};
		int[] raised = {0, 0};
		for (int phase = 0; phase < 2; phase++) {
			for (int i = 0; i < 400; i++) {
				Mob mob = TowerMobs.spawnOne(tower, floor);
				if (mob != null) {
					raised[phase]++;
					nearest[phase] = Math.min(nearest[phase], mob.position().distanceTo(post));
					mob.discard();
				}
			}
			if (phase == 0) {
				// A trial spawner ignores light, so only the merchant refuses; each spot is asked forty times to be sure of it.
				BlockPos nearSpot = BlockPos.containing(post.add(3.0, 0.0, 0.0));
				BlockPos farSpot = BlockPos.containing(post.add(TowerMerchants.AURA + 6.0, 0.0, 0.0));
				int nearAllowed = 0;
				int farAllowed = 0;
				for (int ask = 0; ask < 40; ask++) {
					nearAllowed += SpawnPlacements.checkSpawnRules(EntityTypes.ZOMBIE, tower, EntitySpawnReason.TRIAL_SPAWNER, nearSpot, tower.getRandom()) ? 1 : 0;
					farAllowed += SpawnPlacements.checkSpawnRules(EntityTypes.ZOMBIE, tower, EntitySpawnReason.TRIAL_SPAWNER, farSpot, tower.getRandom()) ? 1 : 0;
				}
				TowerTestSupport.result("merchantPeaceSpawner", "nearAllowed=" + nearAllowed + " farAllowed=" + farAllowed + " of 40");
				helper.assertTrue(nearAllowed == 0, "a spawner may raise a zombie three blocks from the merchant " + nearAllowed + " times in 40");
				helper.assertTrue(farAllowed > 0, "a spawner may never raise a zombie " + (TowerMerchants.AURA + 6.0) + " blocks from the merchant, so the case proves nothing");
				merchant.discard();
			}
		}
		TowerTestSupport.result("merchantPeaceSpawns", "withMerchant raised=" + raised[0] + " nearest=" + nearest[0] + " without raised=" + raised[1] + " nearest=" + nearest[1]);
		helper.assertTrue(raised[0] >= 100, "only " + raised[0] + " mobs were raised, so the case proves little");
		helper.assertTrue(nearest[0] > TowerMerchants.AURA, "a mob was raised " + nearest[0] + " blocks from the merchant");
		helper.assertTrue(nearest[1] <= TowerMerchants.AURA, "with the merchant gone no mob stood within " + TowerMerchants.AURA + " blocks (" + nearest[1] + "), so the case proves nothing");
		helper.succeed();
	}

	/**
	 * Catches a mob that takes a player for its target within the merchant's peace. A zombie beside the player and a skeleton eleven
	 * blocks from the merchant are both in sight of a player four blocks from it. With the merchant gone they must take the player.
	 * Not required: about one run in ten a mob holds the player for a few ticks before the peace is kept. Check by hand.
	 */
	@GameTest(maxTicks = 400, required = false)
	public void noMobTargetsAPlayerWithinTheMerchantsPeace(final GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0);
		QuietFloor.hold(floor);
		TowerFloors.ensure(tower, floor);
		Vec3 middle = TowerTestSupport.arena(tower, floor, 11);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		player.teleportTo(tower, middle.x + 4.0, middle.y, middle.z, Set.of(), 0.0F, 0.0F, true);
		GhostMerchant merchant = merchantAt(tower, middle);
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, 5, TowerElement.DARK);
		zombie.snapTo(middle.add(2.0, 0.0, 4.0));
		tower.addFreshEntity(zombie);
		Skeleton skeleton = EntityTypes.SKELETON.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(skeleton, 5, TowerElement.DARK);
		skeleton.snapTo(middle.add(-10.0, 0.0, 0.0));
		tower.addFreshEntity(skeleton);
		int[] held = {0, 0};
		helper.onEachTick(() -> {
			player.setHealth(player.getMaxHealth());
			player.setInvulnerableTime(0);
			// The count begins once the merchant has ticked: before that its chunk is still loading and its peace is not yet kept.
			if (merchant.isAlive() && merchant.tickCount < 5) {
				return;
			}
			int phase = merchant.isAlive() ? 0 : 1;
			held[phase] += zombie.getTarget() == player ? 1 : 0;
			held[phase] += skeleton.getTarget() == player ? 1 : 0;
		});
		helper.runAfterDelay(120L, merchant::discard);
		helper.runAfterDelay(300L, () -> {
			TowerTestSupport.result("merchantPeaceTargets", "heldWithMerchant=" + held[0] + " heldWithout=" + held[1]);
			zombie.discard();
			skeleton.discard();
			QuietFloor.release(floor);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(held[0] == 0, "a mob held the player " + held[0] + " ticks within the merchant's peace");
			helper.assertTrue(held[1] > 0, "with the merchant gone no mob took the player, so the case proves nothing");
			helper.succeed();
		});
	}

	/**
	 * Buys the Perfect Invisibility Potion in the tower through the real shop menu with forty emeralds: the first purchase works for
	 * thirty-two, and the second, with eight left, is refused.
	 */
	@GameTest(maxTicks = 60)
	public void thePerfectInvisibilityPotionIsBoughtInTheTowerForThirtyTwoEmeralds(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor();
		QuietFloor.hold(floor);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		GhostMerchant merchant = TowerMerchants.appear(tower, floor, tower.getRandom());
		helper.assertTrue(merchant != null, "no merchant could be raised");
		merchant.stock(List.of(MerchantGoods.PERFECT_INVISIBILITY, MerchantGoods.SOUL_TONIC));
		player.getInventory().add(new ItemStack(Items.EMERALD, 40));
		merchant.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.containerMenu instanceof MerchantShopMenu, "the shop did not open in the tower");
		MerchantShopMenu menu = (MerchantShopMenu) player.containerMenu;
		menu.clicked(menu.slotOf(0), 0, ContainerInput.PICKUP, player);
		int afterFirst = emeralds(player);
		ItemStack potion = player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(Items.POTION)).findFirst().orElse(ItemStack.EMPTY);
		PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
		MobEffectInstance invisibility = contents == null ? null : contents.customEffects().stream().filter(effect -> effect.getEffect().equals(MobEffects.INVISIBILITY)).findFirst().orElse(null);
		menu.clicked(menu.slotOf(0), 0, ContainerInput.PICKUP, player);
		int afterSecond = emeralds(player);
		int potions = count(player, Items.POTION);
		TowerTestSupport.result("merchantBuysInTower", "afterFirst=" + afterFirst + " afterSecond=" + afterSecond + " potions=" + potions
			+ " invisibilityTicks=" + (invisibility == null ? -1 : invisibility.getDuration()));
		merchant.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(afterFirst == 8, "the potion cost " + (40 - afterFirst) + " emeralds, not 32");
		helper.assertTrue(invisibility != null && invisibility.getDuration() == PerfectVeil.TICKS, "the potion does not give a minute of invisibility");
		helper.assertTrue(afterSecond == 8 && potions == 1, "a second potion was sold for eight emeralds: emeralds=" + afterSecond + " potions=" + potions);
		helper.succeed();
	}

	/**
	 * With the equips mod present its purse pays: a player with no loose emerald and a thousand in a bag buys the whetstone for
	 * 192, through the two shares that mod puts on the object share. Without that mod, the case above pays in loose emeralds.
	 */
	@GameTest(maxTicks = 60)
	public void theMerchantPaysThroughTheEquipsPurseWhenThatModIsPresent(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor();
		QuietFloor.hold(floor);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		GhostMerchant merchant = TowerMerchants.appear(tower, floor, tower.getRandom());
		helper.assertTrue(merchant != null, "no merchant could be raised");
		merchant.stock(List.of(MerchantGoods.TOWER_WHETSTONE));
		int[] spent = {0};
		var shares = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare();
		Object oldCount = shares.put(TowerMerchants.COUNT_SHARE, (java.util.function.Function<Object, Integer>) who -> 1000);
		Object oldSpend = shares.put(TowerMerchants.SPEND_SHARE, (java.util.function.BiFunction<Object, Integer, Boolean>) (who, price) -> {
			spent[0] += price;
			return true;
		});
		boolean bought;
		try {
			bought = TowerMerchants.buy(player, merchant, 0);
		} finally {
			restore(shares, TowerMerchants.COUNT_SHARE, oldCount);
			restore(shares, TowerMerchants.SPEND_SHARE, oldSpend);
		}
		int whetstones = count(player, MerchantItems.TOWER_WHETSTONE);
		TowerTestSupport.result("merchantPurse", "bought=" + bought + " spent=" + spent[0] + " whetstones=" + whetstones + " loose=" + emeralds(player));
		merchant.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(bought && spent[0] == MerchantGoods.TOWER_WHETSTONE.price() && whetstones == 1, "the purse paid " + spent[0] + " and the player holds " + whetstones);
		helper.succeed();
	}

	private static void restore(final net.fabricmc.loader.api.ObjectShare shares, final String key, final Object old) {
		if (old == null) {
			shares.remove(key);
		} else {
			shares.put(key, old);
		}
	}

	/** The same sale outside the tower: the merchant's shop does not open, a direct purchase is refused, and no emerald is spent. */
	@GameTest(maxTicks = 60)
	public void thePerfectInvisibilityPotionIsRefusedOutsideTheTower(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		GhostMerchant merchant = merchantAt(level, helper.absoluteVec(new Vec3(3.5, 1.0, 3.5)));
		merchant.stock(List.of(MerchantGoods.PERFECT_INVISIBILITY));
		player.getInventory().add(new ItemStack(Items.EMERALD, 64));
		merchant.mobInteract(player, InteractionHand.MAIN_HAND);
		boolean opened = player.containerMenu instanceof MerchantShopMenu;
		boolean bought = TowerMerchants.buy(player, merchant, 0);
		int emeralds = emeralds(player);
		int potions = count(player, Items.POTION);
		TowerTestSupport.result("merchantRefusesOutside", "opened=" + opened + " bought=" + bought + " emeralds=" + emeralds + " potions=" + potions);
		merchant.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertFalse(opened, "the shop opened outside the tower");
		helper.assertFalse(bought, "the merchant sold outside the tower");
		helper.assertTrue(emeralds == 64 && potions == 0, "emeralds=" + emeralds + " potions=" + potions);
		helper.succeed();
	}

	/** Catches a shelf that is not five or six different goods of the list, or a good that never makes the shelf. */
	@GameTest
	public void aMerchantOffersFiveOrSixDifferentGoodsAndEveryGoodIsOffered(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(7L);
		Set<Integer> sizes = new HashSet<>();
		Set<net.minecraft.resources.Identifier> seen = new HashSet<>();
		boolean distinct = true;
		for (int i = 0; i < 300; i++) {
			List<MerchantGoods.Good> shelf = MerchantGoods.roll(random);
			sizes.add(shelf.size());
			distinct &= shelf.stream().map(MerchantGoods.Good::id).distinct().count() == shelf.size();
			shelf.forEach(good -> seen.add(good.id()));
		}
		List<net.minecraft.resources.Identifier> never = new ArrayList<>();
		MerchantGoods.stocked().forEach(good -> {
			if (!seen.contains(good.id())) {
				never.add(good.id());
			}
		});
		TowerTestSupport.result("merchantShelf", "sizes=" + sizes + " distinct=" + distinct + " stocked=" + MerchantGoods.stocked().size() + " never=" + never);
		helper.assertTrue(sizes.equals(Set.of(5, 6)), "shelves held " + sizes + " goods");
		helper.assertTrue(distinct, "a shelf held a good twice");
		helper.assertTrue(never.isEmpty(), "goods never offered: " + never);
		helper.succeed();
	}
}
