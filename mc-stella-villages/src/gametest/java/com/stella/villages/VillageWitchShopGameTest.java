package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.ShopOffer;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import com.stella.villages.patrol.Conversation;
import com.stella.villages.patrol.PatrolState;
import com.stella.villages.patrol.VillageWitchKit;
import com.stella.villages.patrol.WitchShop;
import com.stella.villages.patrol.WitchShopMenu;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The village witch's enchanted-book shop. Each test runs in the witch environment, because the witch exists
 * only with the spells mod and the environment switches that on for the test's own batch.
 */
public final class VillageWitchShopGameTest {
	/** Both sides of the test's midnight are night, so no zombie in the shared level burns while the clock moves. */
	private static final long NIGHT_MARGIN_TICKS = 1_000L;
	private static final List<String> REROLLS = new CopyOnWriteArrayList<>();

	/** Each foe's attackers, by the entity the damage source credits. */
	private static final java.util.Map<UUID, List<UUID>> FOE_ATTACKERS = new java.util.concurrent.ConcurrentHashMap<>();

	static {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			List<UUID> attackers = FOE_ATTACKERS.get(entity.getUUID());
			if (attackers != null && source.getEntity() != null) {
				attackers.add(source.getEntity().getUUID());
			}
			return true;
		});
		WitchShop.setRecorder(new WitchShop.Recorder() {
			@Override
			public void rerolled(final UUID village, final String cause) {
				REROLLS.add(village + ":" + cause);
			}
		});
	}

	private record Fixture(ServerPlayer player, Mob witch, VillageRecord village) {
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void witchShopShowsFiveEnchantedBooksPricedFromThreeTwentyToNineSixty(final GameTestHelper helper) {
		String test = "witchShopShowsFiveEnchantedBooksPricedFromThreeTwentyToNineSixty";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		open(helper, fixture);
		helper.assertTrue(fixture.player().containerMenu instanceof WitchShopMenu, "right-clicking the witch opened "
			+ fixture.player().containerMenu);
		WitchShopMenu menu = (WitchShopMenu) fixture.player().containerMenu;
		List<ShopOffer> offers = fixture.village().shopOffers();
		helper.assertTrue(offers.size() == WitchShop.OFFER_COUNT, "the shop holds " + offers.size() + " offers");
		helper.assertTrue(WitchShopMenu.OFFER_SLOTS.length == WitchShop.OFFER_COUNT, "the menu has no slot for every offer");
		for (int index = 0; index < offers.size(); index++) {
			ShopOffer offer = offers.get(index);
			Enchantment enchantment = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.get(offer.enchantment()).orElseThrow().value();
			helper.assertTrue(offer.price() >= WitchShop.MIN_PRICE && offer.price() <= WitchShop.MAX_PRICE, "price " + offer.price() + " is out of range");
			helper.assertTrue(offer.level() >= enchantment.getMinLevel() && offer.level() <= enchantment.getMaxLevel(),
				"level " + offer.level() + " is out of range for " + offer.enchantment());
			ItemStack shown = menu.getContainer().getItem(WitchShopMenu.OFFER_SLOTS[index]);
			ItemEnchantments stored = shown.get(DataComponents.STORED_ENCHANTMENTS);
			helper.assertTrue(shown.is(Items.ENCHANTED_BOOK) && stored != null && stored.size() == 1
				&& stored.keySet().iterator().next().is(offer.enchantment())
				&& stored.getLevel(stored.keySet().iterator().next()) == offer.level(),
				"slot " + WitchShopMenu.OFFER_SLOTS[index] + " shows " + shown + " for " + offer);
			helper.assertTrue(shown.get(DataComponents.LORE).lines().stream()
				.anyMatch(line -> line.getString().contains(offer.price() + " emeralds")), "the lore misses the price");
		}
		helper.assertTrue(menu.getContainer().getItem(WitchShopMenu.REROLL_SLOT).is(Items.CLOCK), "the reroll control is missing");
		helper.assertTrue(menu.getContainer().getItem(WitchShopMenu.REROLL_SLOT).get(DataComponents.LORE).lines().stream()
			.anyMatch(line -> line.getString().contains(WitchShop.REROLL_PRICE + " emeralds")), "the reroll lore misses its price");
		for (int slot = 0; slot < menu.getContainer().getContainerSize(); slot++) {
			if (WitchShopMenu.offerIndex(slot) < 0 && slot != WitchShopMenu.REROLL_SLOT) {
				helper.assertTrue(menu.getContainer().getItem(slot).is(Items.STAINED_GLASS_PANE.gray()), "slot " + slot + " is not glass");
			}
		}
		PatrolTestSupport.result(test, "offers=" + offers);
		helper.succeed();
	}

	@GameTest
	public void theShopKeepsTheRequestedCountPricesAndMidnight(final GameTestHelper helper) {
		PatrolTestSupport.result("theShopKeepsTheRequestedCountPricesAndMidnight", "count=" + WitchShop.OFFER_COUNT
			+ " min=" + WitchShop.MIN_PRICE + " max=" + WitchShop.MAX_PRICE + " reroll=" + WitchShop.REROLL_PRICE
			+ " midnight=" + WitchShop.MIDNIGHT_TICK);
		helper.assertTrue(WitchShop.OFFER_COUNT == 5 && WitchShop.MIN_PRICE == 320 && WitchShop.MAX_PRICE == 960
			&& WitchShop.REROLL_PRICE == 32 && WitchShop.MIDNIGHT_TICK == 18_000L, "a shop constant left the request");
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void purchaseTakesThePriceAcrossSlotsAndLeavesTheOffer(final GameTestHelper helper) {
		String test = "purchaseTakesThePriceAcrossSlotsAndLeavesTheOffer";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		open(helper, fixture);
		WitchShopMenu menu = (WitchShopMenu) fixture.player().containerMenu;
		List<ShopOffer> before = List.copyOf(fixture.village().shopOffers());
		ShopOffer offer = before.get(2);
		int spare = 11;
		spread(fixture.player().getInventory(), offer.price() + spare);
		menu.clicked(WitchShopMenu.OFFER_SLOTS[2], 0, ContainerInput.PICKUP, fixture.player());
		int emeraldsLeft = WitchShop.emeralds(fixture.player().getInventory());
		int books = countOf(fixture.player().getInventory(), Items.ENCHANTED_BOOK);
		ItemStack book = firstOf(fixture.player().getInventory(), Items.ENCHANTED_BOOK);
		ItemEnchantments stored = book == null ? null : book.get(DataComponents.STORED_ENCHANTMENTS);
		boolean matches = stored != null && stored.size() == 1 && stored.keySet().iterator().next().is(offer.enchantment())
			&& stored.getLevel(stored.keySet().iterator().next()) == offer.level();
		helper.assertTrue(emeraldsLeft == spare, "the purchase left " + emeraldsLeft + " emeralds, not " + spare);
		helper.assertTrue(books == 1 && matches, "the player holds " + books + " books, matching=" + matches);
		helper.assertTrue(fixture.village().shopOffers().equals(before), "a purchase changed the offers");
		helper.assertTrue(menu.getContainer().getItem(WitchShopMenu.OFFER_SLOTS[2]).is(Items.ENCHANTED_BOOK), "the offer left the shelf");

		for (int round = 0; round < 2; round++) {
			spread(fixture.player().getInventory(), 0);
			spread(fixture.player().getInventory(), offer.price());
			menu.clicked(WitchShopMenu.OFFER_SLOTS[2], 0, ContainerInput.PICKUP, fixture.player());
		}
		helper.assertTrue(WitchShop.emeralds(fixture.player().getInventory()) == 0
			&& countOf(fixture.player().getInventory(), Items.ENCHANTED_BOOK) == 3, "a book can be bought again and again");
		PatrolTestSupport.result(test, "price=" + offer.price() + " emeraldsLeft=" + emeraldsLeft);
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void shortPurseChangesNothing(final GameTestHelper helper) {
		String test = "shortPurseChangesNothing";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		open(helper, fixture);
		WitchShopMenu menu = (WitchShopMenu) fixture.player().containerMenu;
		List<ShopOffer> before = List.copyOf(fixture.village().shopOffers());
		ShopOffer offer = before.get(0);
		spread(fixture.player().getInventory(), offer.price() - 1);
		menu.clicked(WitchShopMenu.OFFER_SLOTS[0], 0, ContainerInput.PICKUP, fixture.player());
		helper.assertTrue(WitchShop.emeralds(fixture.player().getInventory()) == offer.price() - 1,
			"a short purse still paid");
		helper.assertTrue(countOf(fixture.player().getInventory(), Items.ENCHANTED_BOOK) == 0, "a short purse still got a book");
		helper.assertTrue(fixture.village().shopOffers().equals(before), "a short purse changed the offers");
		spread(fixture.player().getInventory(), 0);
		spread(fixture.player().getInventory(), WitchShop.REROLL_PRICE - 1);
		menu.clicked(WitchShopMenu.REROLL_SLOT, 0, ContainerInput.PICKUP, fixture.player());
		helper.assertTrue(WitchShop.emeralds(fixture.player().getInventory()) == WitchShop.REROLL_PRICE - 1
			&& fixture.village().shopOffers().equals(before), "a short purse still rerolled");
		PatrolTestSupport.result(test, "price=" + offer.price());
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void noClickMovesAnItemOutOfTheShop(final GameTestHelper helper) {
		String test = "noClickMovesAnItemOutOfTheShop";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		open(helper, fixture);
		WitchShopMenu menu = (WitchShopMenu) fixture.player().containerMenu;
		List<ShopOffer> before = List.copyOf(fixture.village().shopOffers());
		Inventory inventory = fixture.player().getInventory();
		inventory.setItem(3, new ItemStack(Items.COBBLESTONE, 5));
		List<ItemStack> snapshot = snapshot(inventory);
		ContainerInput[] inputs = {ContainerInput.PICKUP, ContainerInput.QUICK_MOVE, ContainerInput.SWAP, ContainerInput.THROW,
			ContainerInput.CLONE, ContainerInput.PICKUP_ALL};
		for (int slot = 0; slot < menu.slots.size(); slot++) {
			for (ContainerInput input : inputs) {
				if ((slot == WitchShopMenu.REROLL_SLOT || WitchShopMenu.offerIndex(slot) >= 0) && input == ContainerInput.PICKUP
					|| input == ContainerInput.QUICK_MOVE && (slot == WitchShopMenu.REROLL_SLOT || WitchShopMenu.offerIndex(slot) >= 0)) {
					continue;
				}
				menu.clicked(slot, 0, input, fixture.player());
			}
		}
		helper.assertTrue(sameContents(snapshot(inventory), snapshot), "a click changed the player's inventory");
		helper.assertTrue(menu.getCarried().isEmpty(), "a click left an item on the cursor");
		helper.assertTrue(fixture.village().shopOffers().equals(before), "a click changed the offers");
		for (int slot = 0; slot < menu.getContainer().getContainerSize(); slot++) {
			if (WitchShopMenu.offerIndex(slot) < 0 && slot != WitchShopMenu.REROLL_SLOT) {
				helper.assertTrue(menu.getContainer().getItem(slot).is(Items.STAINED_GLASS_PANE.gray()), "glass left slot " + slot);
			}
		}
		helper.assertTrue(menu.quickMoveStack(fixture.player(), 1).isEmpty(), "quickMoveStack moved an item");
		PatrolTestSupport.result(test, "slots=" + menu.slots.size());
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void rerollTakesThirtyTwoEmeraldsAndReplacesTheOffers(final GameTestHelper helper) {
		String test = "rerollTakesThirtyTwoEmeraldsAndReplacesTheOffers";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		open(helper, fixture);
		WitchShopMenu menu = (WitchShopMenu) fixture.player().containerMenu;
		List<ShopOffer> before = List.copyOf(fixture.village().shopOffers());
		int rerolls = REROLLS.size();
		spread(fixture.player().getInventory(), WitchShop.REROLL_PRICE + 9);
		menu.clicked(WitchShopMenu.REROLL_SLOT, 0, ContainerInput.PICKUP, fixture.player());
		List<ShopOffer> after = fixture.village().shopOffers();
		helper.assertTrue(WitchShop.emeralds(fixture.player().getInventory()) == 9, "the reroll took the wrong fee");
		helper.assertTrue(after.size() == WitchShop.OFFER_COUNT && !after.equals(before), "the reroll did not replace the offers");
		helper.assertTrue(REROLLS.size() == rerolls + 1 && REROLLS.get(REROLLS.size() - 1).endsWith(":player"),
			"the reroll was not logged as a player reroll: " + REROLLS);
		for (int index = 0; index < after.size(); index++) {
			ItemEnchantments stored = menu.getContainer().getItem(WitchShopMenu.OFFER_SLOTS[index])
				.get(DataComponents.STORED_ENCHANTMENTS);
			helper.assertTrue(stored != null && stored.keySet().iterator().next().is(after.get(index).enchantment()),
				"the open shop still shows the old offer in slot " + WitchShopMenu.OFFER_SLOTS[index]);
		}
		PatrolTestSupport.result(test, "before=" + before + " after=" + after);
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void crossingMidnightRerollsTheOffersOnce(final GameTestHelper helper) {
		String test = "crossingMidnightRerollsTheOffersOnce";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		ServerLevel level = helper.getLevel();
		open(helper, fixture);
		WitchShop.tickVillage(level, fixture.village(), WitchShop.MIDNIGHT_TICK - 200L);
		List<ShopOffer> beforeMidnight = List.copyOf(fixture.village().shopOffers());
		String village = fixture.village().id().toString();
		long midnightRerolls = REROLLS.stream().filter(entry -> entry.equals(village + ":midnight")).count();
		WitchShop.tickVillage(level, fixture.village(), WitchShop.MIDNIGHT_TICK - 100L);
		helper.assertTrue(fixture.village().shopOffers().equals(beforeMidnight), "the offers changed before midnight");

		WitchShop.tickVillage(level, fixture.village(), WitchShop.MIDNIGHT_TICK + 200L);
		List<ShopOffer> afterMidnight = List.copyOf(fixture.village().shopOffers());
		WitchShop.tickVillage(level, fixture.village(), WitchShop.MIDNIGHT_TICK + 300L);
		WitchShop.tickVillage(level, fixture.village(), WitchShop.MIDNIGHT_TICK + 400L);
		long crossed = REROLLS.stream().filter(entry -> entry.equals(village + ":midnight")).count() - midnightRerolls;
		helper.assertTrue(crossed == 1L, "crossing midnight rerolled " + crossed + " times");
		helper.assertTrue(afterMidnight.size() == WitchShop.OFFER_COUNT && !afterMidnight.equals(beforeMidnight),
			"midnight did not replace the offers");
		helper.assertTrue(fixture.village().shopOffers().equals(afterMidnight), "a later tick rerolled again");

		WitchShop.tickVillage(level, fixture.village(), WitchShop.MIDNIGHT_TICK + 5L * WitchShop.TICKS_PER_DAY + 200L);
		WitchShop.tickVillage(level, fixture.village(), WitchShop.MIDNIGHT_TICK + 5L * WitchShop.TICKS_PER_DAY + 300L);
		long jumped = REROLLS.stream().filter(entry -> entry.equals(village + ":midnight")).count() - midnightRerolls - crossed;
		helper.assertTrue(jumped == 1L, "a jump over five midnights rerolled " + jumped + " times");
		PatrolTestSupport.result(test, "crossed=" + crossed + " jumped=" + jumped);
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theWorldClockCrossingMidnightRerollsTheOffersThroughTheServerTick(final GameTestHelper helper) {
		String test = "theWorldClockCrossingMidnightRerollsTheOffersThroughTheServerTick";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		ServerLevel level = helper.getLevel();
		open(helper, fixture);
		String village = fixture.village().id().toString();
		long before = REROLLS.stream().filter(entry -> entry.equals(village + ":midnight")).count();
		List<ShopOffer> offers = List.copyOf(fixture.village().shopOffers());
		List<Long> clocks = new ArrayList<>();
		helper.onEachTick(() -> clocks.add(level.getOverworldClockTime()));
		helper.setTime(WitchShop.MIDNIGHT_TICK - NIGHT_MARGIN_TICKS);
		helper.runAfterDelay(4, () -> helper.setTime(WitchShop.MIDNIGHT_TICK + NIGHT_MARGIN_TICKS));
		helper.succeedWhen(() -> {
			long rerolls = REROLLS.stream().filter(entry -> entry.equals(village + ":midnight")).count() - before;
			helper.assertTrue(helper.getTick() > 8, "waiting for the clock to pass midnight");
			helper.assertTrue(level.getOverworldClockTime() >= WitchShop.MIDNIGHT_TICK, "the clock is at " + level.getOverworldClockTime());
			helper.assertTrue(rerolls == 1L, "the server tick rerolled " + rerolls + " times, clock=" + clocks);
			helper.assertTrue(!fixture.village().shopOffers().equals(offers), "the offers did not change");
			PatrolTestSupport.result(test, "rerolls=" + rerolls + " clock=" + level.getOverworldClockTime());
			helper.setTime(WitchShop.MIDNIGHT_TICK);
		});
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void shopOffersSurviveASaveAndReload(final GameTestHelper helper) {
		String test = "shopOffersSurviveASaveAndReload";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		open(helper, fixture);
		VillageWorldData data = VillageWorldData.get(helper.getLevel());
		data.setShopMidnight(fixture.village(), 7L);
		List<ShopOffer> offers = List.copyOf(fixture.village().shopOffers());
		CompoundTag saved = data.serializeState();
		VillageRecord reloaded = VillageWorldData.loadState(saved).village(fixture.village().id()).orElse(null);
		helper.assertTrue(reloaded != null, "the village did not reload");
		helper.assertTrue(reloaded.shopOffers().equals(offers), "the offers changed over a reload: " + reloaded.shopOffers());
		helper.assertTrue(reloaded.shopMidnight() == 7L, "the midnight was not kept");

		VillageWorldData stale = VillageWorldData.loadState(saved);
		VillageRecord staleVillage = stale.village(fixture.village().id()).orElseThrow();
		List<ShopOffer> broken = new ArrayList<>(offers);
		broken.set(1, new ShopOffer(Identifier.parse("gone:vanished"), 1, 400));
		stale.setShopOffers(staleVillage, broken);
		List<ShopOffer> repaired = WitchShop.offersOf(helper.getLevel(), staleVillage);
		helper.assertTrue(repaired.size() == WitchShop.OFFER_COUNT
			&& repaired.stream().noneMatch(offer -> offer.enchantment().getNamespace().equals("gone"))
			&& repaired.contains(offers.get(0)) && repaired.contains(offers.get(2)),
			"a missing enchantment did not refill the shop: " + repaired);

		CompoundTag old = data.serializeState();
		old.getListOrEmpty("villages").forEach(tag -> {
			if (tag instanceof CompoundTag village) {
				village.remove("shop_offers");
				village.remove("shop_midnight");
			}
		});
		VillageRecord legacy = VillageWorldData.loadState(old).village(fixture.village().id()).orElse(null);
		helper.assertTrue(legacy != null && legacy.shopOffers().isEmpty() && legacy.shopMidnight() == VillageWorldData.NO_MIDNIGHT,
			"a save without shop data did not load empty");
		PatrolTestSupport.result(test, "offers=" + offers.size());
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 40 + Conversation.LINGER_TICKS + 60, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theWitchStandsStillAndFacesTheShopperUntilFiveSecondsAfterClosing(final GameTestHelper helper) {
		String test = "theWitchStandsStillAndFacesTheShopperUntilFiveSecondsAfterClosing";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		open(helper, fixture);
		helper.assertTrue(fixture.player().containerMenu instanceof WitchShopMenu, "the shop did not open");
		holdsUntilLinger(helper, test, fixture.player(), fixture.witch());
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 40 + Conversation.LINGER_TICKS + 60, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theHeadStandsStillAndFacesThePlayerUntilFiveSecondsAfterClosing(final GameTestHelper helper) {
		String test = "theHeadStandsStillAndFacesThePlayerUntilFiveSecondsAfterClosing";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		Mob head = null;
		for (var member : fixture.village().members()) {
			if (member.role().equals(PatrolState.Role.HEAD.name())
				&& helper.getLevel().getEntity(member.id()) instanceof Mob mob) {
				head = mob;
			}
		}
		helper.assertTrue(head != null, "the village has no head");
		Vec3 near = head.position().add(2.0, 0.0, 0.0);
		fixture.player().setPos(near.x, near.y, near.z);
		UseEntityCallback.EVENT.invoker().interact(fixture.player(), helper.getLevel(), InteractionHand.MAIN_HAND, head, null);
		helper.assertTrue(fixture.player().containerMenu instanceof com.stella.villages.mission.MissionBoardMenu,
			"the mission board did not open: " + fixture.player().containerMenu);
		holdsUntilLinger(helper, test, fixture.player(), head);
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 20 + 2 * Conversation.LINGER_TICKS + 60, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theWitchKeepsListeningWhileAnotherShopperStaysInTheShop(final GameTestHelper helper) {
		String test = "theWitchKeepsListeningWhileAnotherShopperStaysInTheShop";
		Fixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		ServerPlayer second = survivalPlayer(helper);
		Vec3 near = fixture.witch().position().add(-2.0, 0.0, 0.0);
		second.setPos(near.x, near.y, near.z);
		open(helper, fixture);
		UseEntityCallback.EVENT.invoker().interact(second, helper.getLevel(), InteractionHand.MAIN_HAND, fixture.witch(), null);
		helper.assertTrue(second.containerMenu instanceof WitchShopMenu, "the second shopper saw no shop");
		int secondLeaves = 20;
		int firstLeaves = secondLeaves + Conversation.LINGER_TICKS + 10;
		helper.runAfterDelay(secondLeaves, second::closeContainer);
		helper.runAfterDelay(firstLeaves, () -> {
			boolean heldWhileFirstShops = Conversation.held(fixture.witch());
			fixture.player().closeContainer();
			helper.assertTrue(heldWhileFirstShops, "the witch walked off while a shopper was still in the shop");
		});
		helper.runAfterDelay(firstLeaves + Conversation.LINGER_TICKS + 20, () -> {
			PatrolTestSupport.result(test, "heldAfter=" + Conversation.held(fixture.witch()));
			helper.assertFalse(Conversation.held(fixture.witch()), "the witch never went back to work");
			helper.succeed();
		});
	}

	/**
	 * The mob is sent walking away, so only the hold keeps it in place. The menu closes at tick 40; the hold
	 * must last exactly {@link Conversation#LINGER_TICKS} more.
	 */
	private static void holdsUntilLinger(final GameTestHelper helper, final String test, final ServerPlayer player, final Mob mob) {
		int closeTick = 40;
		Vec3 anchor = mob.position();
		Vec3 away = anchor.add(12.0, 0.0, 0.0);
		double[] drift = {0.0};
		double[] worstFacing = {1.0};
		int[] closedAt = {-1};
		Mob foe = listeningFoe(helper, mob.position().add(1.5, 0.0, 0.0));
		List<UUID> attackers = new CopyOnWriteArrayList<>();
		FOE_ATTACKERS.put(foe.getUUID(), attackers);
		int[] hitsAtRelease = {-1};
		helper.onEachTick(() -> {
			int tick = (int) helper.getTick();
			if (closedAt[0] < 0) {
				mob.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
			}
			if (tick >= 2 && (closedAt[0] < 0 || tick < closedAt[0] + Conversation.LINGER_TICKS)) {
				drift[0] = Math.max(drift[0], mob.position().distanceTo(anchor));
				Vec3 look = Vec3.directionFromRotation(0.0F, mob.getYHeadRot());
				Vec3 toPlayer = new Vec3(player.getX() - mob.getX(), 0.0, player.getZ() - mob.getZ()).normalize();
				worstFacing[0] = Math.min(worstFacing[0], look.dot(toPlayer));
			}
			if (tick == closeTick) {
				player.closeContainer();
				closedAt[0] = tick;
			}
		});
		helper.runAfterDelay(closeTick + Conversation.LINGER_TICKS - 2, () -> {
			hitsAtRelease[0] = (int) attackers.stream().filter(mob.getUUID()::equals).count();
			helper.assertTrue(Conversation.held(mob), "the hold ended before " + Conversation.LINGER_TICKS + " ticks passed");
		});
		helper.runAfterDelay(closeTick + Conversation.LINGER_TICKS + 40, () -> {
			PatrolTestSupport.result(test, "drift=" + drift[0] + " facing=" + worstFacing[0] + " hitsWhileListening=" + hitsAtRelease[0] + " allHits=" + attackers.size()
				+ " heldAfter=" + Conversation.held(mob) + " walkedAfter=" + mob.position().distanceTo(anchor));
			helper.assertTrue(drift[0] < 0.05, "the mob moved " + drift[0] + " blocks while it listened");
			helper.assertTrue(worstFacing[0] > 0.9, "the mob looked away from the player: " + worstFacing[0]);
			FOE_ATTACKERS.remove(foe.getUUID());
			foe.discard();
			helper.assertTrue(hitsAtRelease[0] == 0, "the mob hit a foe " + hitsAtRelease[0] + " times while it listened");
			helper.assertFalse(Conversation.held(mob), "the mob was still held after the linger");
			helper.succeed();
		});
	}

	/** A zombie beside the listener that cannot move, so any damage it takes is the listener's attack. */
	private static Mob listeningFoe(final GameTestHelper helper, final Vec3 at) {
		Mob foe = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		foe.setNoAi(true);
		foe.setPersistenceRequired();
		foe.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		foe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
		foe.setHealth(1000.0F);
		foe.setPos(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(foe);
		return foe;
	}

	private static Fixture fixture(final GameTestHelper helper, final String test) {
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return null;
		}
		VillageRecord village = VillageWorldData.get(helper.getLevel()).village(PatrolTestSupport.villageId(helper)).orElse(null);
		Mob witch = null;
		if (village != null) {
			for (var member : village.members()) {
				if (member.role().equals(PatrolState.Role.VILLAGE_WITCH.name())
					&& helper.getLevel().getEntity(member.id()) instanceof Mob mob) {
					witch = mob;
				}
			}
		}
		helper.assertTrue(witch != null, "the village has no witch");
		ServerPlayer player = survivalPlayer(helper);
		Vec3 near = witch.position().add(2.0, 0.0, 0.0);
		player.setPos(near.x, near.y, near.z);
		return new Fixture(player, witch, village);
	}

	private static void open(final GameTestHelper helper, final Fixture fixture) {
		UseEntityCallback.EVENT.invoker().interact(fixture.player(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.witch(), null);
	}

	private static ServerPlayer survivalPlayer(final GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
			CommonListenerCookie.createInitial(player.getGameProfile(), false));
		return player;
	}

	/** Puts exactly this many emeralds in the main inventory, in stacks of seven over scattered slots. */
	private static void spread(final Inventory inventory, final int total) {
		for (int slot = 0; slot < 36; slot++) {
			if (inventory.getItem(slot).is(Items.EMERALD)) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		int left = total;
		for (int slot = 35; slot >= 0 && left > 0; slot -= 2) {
			if (!inventory.getItem(slot).isEmpty()) {
				continue;
			}
			int put = Math.min(7, left);
			inventory.setItem(slot, new ItemStack(Items.EMERALD, put));
			left -= put;
		}
		for (int slot = 34; slot >= 0 && left > 0; slot -= 2) {
			if (!inventory.getItem(slot).isEmpty()) {
				continue;
			}
			int put = Math.min(64, left);
			inventory.setItem(slot, new ItemStack(Items.EMERALD, put));
			left -= put;
		}
	}

	private static int countOf(final Inventory inventory, final net.minecraft.world.item.Item item) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(item)) {
				total += inventory.getItem(slot).getCount();
			}
		}
		return total;
	}

	private static ItemStack firstOf(final Inventory inventory, final net.minecraft.world.item.Item item) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(item)) {
				return inventory.getItem(slot);
			}
		}
		return null;
	}

	private static boolean sameContents(final List<ItemStack> first, final List<ItemStack> second) {
		return ItemStack.listMatches(first, second);
	}

	private static List<ItemStack> snapshot(final Inventory inventory) {
		List<ItemStack> copy = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			copy.add(inventory.getItem(slot).copy());
		}
		return copy;
	}
}
