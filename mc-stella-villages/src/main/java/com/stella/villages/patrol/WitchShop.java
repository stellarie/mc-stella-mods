package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.ShopOffer;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The village witch sells five random enchanted books for emeralds. A player rerolls them for a fee, and
 * every midnight rerolls them for free. The offers live in the village's saved data, and every purchase is
 * checked here, never in the menu.
 */
public final class WitchShop {
	public static final int OFFER_COUNT = 5;
	public static final int MIN_PRICE = 320;
	public static final int MAX_PRICE = 960;
	public static final int REROLL_PRICE = 32;
	public static final int SHOP_RANGE = 8;
	/** Day time at which the shops reroll, counted from the start of a day. */
	public static final long MIDNIGHT_TICK = 18_000L;
	public static final long TICKS_PER_DAY = 24_000L;
	private static final Map<ServerLevel, Long> SEEN_MIDNIGHTS = new WeakHashMap<>();

	/** Test seam: each reroll and what caused it. */
	public interface Recorder {
		default void rerolled(final UUID village, final String cause) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private WitchShop() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(WitchShop::onUseEntity);
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getAllLevels()
			.forEach(level -> tick(level, level.getOverworldClockTime())));
	}

	public static void setRecorder(final @Nullable Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	private static InteractionResult onUseEntity(
		final Player player,
		final Level world,
		final InteractionHand hand,
		final Entity entity,
		final net.minecraft.world.phys.EntityHitResult hitResult
	) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level)
			|| !(entity instanceof Mob witch) || villageOf(level, witch) == null) {
			return InteractionResult.PASS;
		}
		if (serverPlayer.distanceToSqr(witch) > (double) SHOP_RANGE * SHOP_RANGE) {
			return InteractionResult.PASS;
		}
		VillageRecord village = villageOf(level, witch);
		List<ShopOffer> offers = offersOf(level, village);
		WitchShopMenu.open(serverPlayer, witch, level, offers);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=witch_shop_opened player={} witch={} village={} offers={}",
			McStellaVillages.MOD_ID, serverPlayer.getUUID(), witch.getUUID(), village.id(), offers.size()
		);
		return InteractionResult.SUCCESS;
	}

	/** The village a registered, living village witch belongs to, or null for any other mob. */
	static @Nullable VillageRecord villageOf(final ServerLevel level, final Mob witch) {
		if (PatrolState.role(witch) != PatrolState.Role.VILLAGE_WITCH || PatrolState.villageId(witch) == null) {
			return null;
		}
		VillageRecord village = VillageWorldData.get(level).villageForMember(witch.getUUID()).orElse(null);
		return village != null && village.dimension().equals(level.dimension())
			&& village.members().stream().anyMatch(member -> member.id().equals(witch.getUUID())
				&& member.role().equals(PatrolState.Role.VILLAGE_WITCH.name()) && member.alive())
			? village : null;
	}

	/** The offers a village's witch holds now; a witch with none, or with some gone stale, fills the gap first. */
	public static List<ShopOffer> offersOf(final ServerLevel level, final VillageRecord village) {
		List<ShopOffer> kept = new ArrayList<>();
		for (ShopOffer offer : village.shopOffers()) {
			if (enchantment(level, offer.enchantment()).isPresent() && kept.size() < OFFER_COUNT) {
				kept.add(offer);
			}
		}
		if (kept.size() == OFFER_COUNT && kept.size() == village.shopOffers().size()) {
			return kept;
		}
		List<ShopOffer> filled = fill(level, kept, level.getRandom());
		VillageWorldData data = VillageWorldData.get(level);
		data.setShopOffers(village, filled);
		// A shop stocked after the last midnight check has seen no midnight yet, so it starts counting now.
		if (village.shopMidnight() == VillageWorldData.NO_MIDNIGHT) {
			data.setShopMidnight(village, midnightIndex(level.getOverworldClockTime()));
		}
		return filled;
	}

	/** Five offers of different enchantments, each at a level the enchantment allows, each at its own price. */
	public static List<ShopOffer> rollOffers(final ServerLevel level, final RandomSource random) {
		return fill(level, List.of(), random);
	}

	private static List<ShopOffer> fill(final ServerLevel level, final List<ShopOffer> kept, final RandomSource random) {
		List<Holder.Reference<Enchantment>> pool = new ArrayList<>(
			level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()
		);
		pool.removeIf(holder -> kept.stream().anyMatch(offer -> holder.key().identifier().equals(offer.enchantment())));
		List<ShopOffer> offers = new ArrayList<>(kept);
		while (offers.size() < OFFER_COUNT && !pool.isEmpty()) {
			Holder.Reference<Enchantment> holder = pool.remove(random.nextInt(pool.size()));
			Enchantment enchantment = holder.value();
			offers.add(new ShopOffer(
				holder.key().identifier(),
				Mth.nextInt(random, enchantment.getMinLevel(), enchantment.getMaxLevel()),
				Mth.nextInt(random, MIN_PRICE, MAX_PRICE)
			));
		}
		return List.copyOf(offers);
	}

	private static Optional<Holder.Reference<Enchantment>> enchantment(final ServerLevel level, final Identifier id) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(id);
	}

	/** The book an offer sells, with no shop lore on it. */
	public static ItemStack book(final ServerLevel level, final ShopOffer offer) {
		Holder<Enchantment> holder = enchantment(level, offer.enchantment()).orElseThrow();
		return EnchantmentHelper.createBook(new EnchantmentInstance(holder, offer.level()));
	}

	/** The book as the shelf shows it: with its price in the lore. */
	static ItemStack icon(final ServerLevel level, final ShopOffer offer) {
		ItemStack icon = book(level, offer);
		icon.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal(offer.price() + " emeralds"),
			Component.literal("Click to buy")
		)));
		return icon;
	}

	static void refresh(final WitchShopMenu menu) {
		Mob witch = menu.witch();
		if (witch.level() instanceof ServerLevel level) {
			VillageRecord village = villageOf(level, witch);
			if (village != null) {
				menu.show(level, offersOf(level, village));
			}
		}
	}

	/** Sells the offer at this index to the player, if the player holds the emeralds. */
	public static boolean buy(final ServerPlayer player, final Mob witch, final int index) {
		if (!(witch.level() instanceof ServerLevel level) || villageOf(level, witch) == null) {
			return false;
		}
		VillageRecord village = villageOf(level, witch);
		List<ShopOffer> offers = offersOf(level, village);
		if (index < 0 || index >= offers.size()) {
			return false;
		}
		ShopOffer offer = offers.get(index);
		if (!pay(player, offer.price(), "buy", village)) {
			return false;
		}
		ItemStack book = book(level, offer);
		if (!player.getInventory().add(book.copy())) {
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), book));
		}
		player.sendSystemMessage(Component.literal("You bought " + book.getHoverName().getString() + " for "
			+ offer.price() + " emeralds."));
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=witch_shop_bought player={} village={} enchantment={} level={} price={}",
			McStellaVillages.MOD_ID, player.getUUID(), village.id(), offer.enchantment(), offer.level(), offer.price()
		);
		return true;
	}

	/** Replaces all five offers for the reroll fee. */
	public static boolean reroll(final ServerPlayer player, final Mob witch) {
		if (!(witch.level() instanceof ServerLevel level) || villageOf(level, witch) == null) {
			return false;
		}
		VillageRecord village = villageOf(level, witch);
		if (!pay(player, REROLL_PRICE, "reroll", village)) {
			return false;
		}
		reroll(level, village, "player");
		return true;
	}

	static void reroll(final ServerLevel level, final VillageRecord village, final String cause) {
		VillageWorldData.get(level).setShopOffers(village, rollOffers(level, level.getRandom()));
		recorder.rerolled(village.id(), cause);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=witch_shop_rerolled village={} cause={}",
			McStellaVillages.MOD_ID, village.id(), cause
		);
	}

	private static boolean pay(final ServerPlayer player, final int price, final String action, final VillageRecord village) {
		int held = emeralds(player.getInventory());
		if (held < price) {
			player.sendSystemMessage(Component.literal("The witch wants " + price + " emeralds. You carry " + held + "."));
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=witch_shop_rejected player={} village={} action={} price={} held={} reason=short_purse",
				McStellaVillages.MOD_ID, player.getUUID(), village.id(), action, price, held
			);
			return false;
		}
		take(player.getInventory(), price);
		return true;
	}

	/** Every emerald the player carries, across the whole inventory. */
	public static int emeralds(final Inventory inventory) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void take(final Inventory inventory, final int amount) {
		int left = amount;
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				int removed = Math.min(left, stack.getCount());
				stack.shrink(removed);
				left -= removed;
			}
		}
		inventory.setChanged();
	}

	/** Which midnight the world clock stands after; it rises by one each time the clock passes 18000 of a day. */
	public static long midnightIndex(final long clockTime) {
		return Math.floorDiv(clockTime - MIDNIGHT_TICK, TICKS_PER_DAY);
	}

	/**
	 * Rerolls every stocked shop in the level once when the clock has passed a new midnight. The index is
	 * kept per village, so a reload or a clock jump across several midnights costs one reroll, not many.
	 */
	public static void tick(final ServerLevel level, final long clockTime) {
		long midnight = midnightIndex(clockTime);
		Long seen = SEEN_MIDNIGHTS.put(level, midnight);
		if (seen != null && seen == midnight) {
			return;
		}
		for (VillageRecord village : VillageWorldData.get(level).villages()) {
			if (village.dimension().equals(level.dimension())) {
				tickVillage(level, village, clockTime);
			}
		}
	}

	/** The midnight check for one village, so a test can drive a clock of its own without touching the others. */
	public static void tickVillage(final ServerLevel level, final VillageRecord village, final long clockTime) {
		long midnight = midnightIndex(clockTime);
		if (village.shopMidnight() == midnight) {
			return;
		}
		boolean passed = village.shopMidnight() != VillageWorldData.NO_MIDNIGHT && midnight > village.shopMidnight();
		VillageWorldData.get(level).setShopMidnight(village, midnight);
		if (passed && !village.shopOffers().isEmpty()) {
			reroll(level, village, "midnight");
		}
	}
}
