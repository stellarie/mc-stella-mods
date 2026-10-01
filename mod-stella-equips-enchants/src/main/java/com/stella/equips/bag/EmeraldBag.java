package com.stella.equips.bag;

import com.mojang.serialization.Codec;
import com.stella.equips.StellaEquips;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A green bag that holds emeralds as a count. Use opens a screen on the client. The server moves the emeralds
 * when a button asks it to, and it checks the hand and the amounts every time.
 */
public final class EmeraldBag {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "emerald_bag");
	public static final int ALL = -1;
	public static final DataComponentType<Integer> EMERALDS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "bag_emeralds"),
		DataComponentType.<Integer>builder().persistent(Codec.intRange(0, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT).build()
	);
	/** Whether acquired emeralds go into this bag. A bag without the component counts as on. */
	public static final DataComponentType<Boolean> AUTO = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "bag_auto"),
		DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build()
	);
	public static final String RPG_BAG_SHARE = "mod-stella-rpg:bag-view";
	private static final ThreadLocal<Boolean> SUPPRESS = new ThreadLocal<>();
	public static final Item ITEM = Registry.register(
		BuiltInRegistries.ITEM, ID,
		new BagItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID)).stacksTo(1).component(EMERALDS, 0))
	);
	/** The client sets this to open the screen; the server never calls it. */
	private static volatile Consumer<InteractionHand> clientOpener = hand -> { };

	private EmeraldBag() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> handle(context.player(), payload));
		PayloadTypeRegistry.serverboundPlay().register(AutoRequest.TYPE, AutoRequest.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AutoRequest.TYPE, (payload, context) -> setAuto(context.player(), payload));
	}

	public static void setClientOpener(final Consumer<InteractionHand> opener) {
		clientOpener = opener;
	}

	public static boolean isBag(final ItemStack stack) {
		return stack.is(ITEM);
	}

	public static boolean isAuto(final ItemStack bag) {
		return isBag(bag) && bag.getOrDefault(AUTO, true);
	}

	/** Sets the checkbox of the bag in the asked hand. Returns false when that hand holds no bag. */
	public static boolean setAuto(final ServerPlayer player, final AutoRequest request) {
		ItemStack bag = player.getItemInHand(request.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (!isBag(bag)) {
			return false;
		}
		bag.set(AUTO, request.auto());
		return true;
	}

	/**
	 * Puts acquired emeralds into the first bag that has its checkbox on: the inventory first, then the rpg bag.
	 * Returns true when it took the whole stack. A withdraw sets the suppress flag, so its emeralds stay loose.
	 */
	public static boolean absorb(final Player player, final ItemStack stack) {
		if (!(player instanceof ServerPlayer) || !stack.is(Items.EMERALD) || stack.isEmpty() || SUPPRESS.get() != null) {
			return false;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack bag = inventory.getItem(slot);
			if (isAuto(bag)) {
				bag.set(EMERALDS, stored(bag) + stack.getCount());
				stack.setCount(0);
				inventory.setChanged();
				return true;
			}
		}
		Container rpgBag = rpgBag(player);
		for (int slot = 0; rpgBag != null && slot < rpgBag.getContainerSize(); slot++) {
			ItemStack bag = rpgBag.getItem(slot);
			if (isAuto(bag)) {
				ItemStack filled = bag.copy();
				filled.set(EMERALDS, stored(bag) + stack.getCount());
				rpgBag.setItem(slot, filled);
				stack.setCount(0);
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private static Container rpgBag(final Player player) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(RPG_BAG_SHARE);
		return shared instanceof Function<?, ?> view ? ((Function<Player, Container>) view).apply(player) : null;
	}

	public static int stored(final ItemStack bag) {
		return isBag(bag) ? bag.getOrDefault(EMERALDS, 0) : 0;
	}

	/** Emeralds as items in the inventory, not counting bags. */
	public static int loose(final Inventory inventory) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Moves emeralds between the inventory and the bag in that hand. Returns the number moved, or -1 for no bag. */
	public static int handle(final ServerPlayer player, final Request request) {
		InteractionHand hand = request.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack bag = player.getItemInHand(hand);
		if (!isBag(bag)) {
			return -1;
		}
		int wanted = request.amount() == ALL ? Integer.MAX_VALUE : Math.max(0, request.amount());
		Inventory inventory = player.getInventory();
		int moved;
		if (request.deposit()) {
			moved = Math.min(wanted, loose(inventory));
			removeLoose(inventory, moved);
			bag.set(EMERALDS, stored(bag) + moved);
		} else {
			moved = Math.min(wanted, stored(bag));
			bag.set(EMERALDS, stored(bag) - moved);
			give(player, moved);
		}
		inventory.setChanged();
		return moved;
	}

	public static void removeLoose(final Inventory inventory, final int amount) {
		int left = amount;
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				int taken = Math.min(left, stack.getCount());
				inventory.removeItem(slot, taken);
				left -= taken;
			}
		}
	}

	private static void give(final Player player, final int amount) {
		SUPPRESS.set(Boolean.TRUE);
		try {
			giveLoose(player, amount);
		} finally {
			SUPPRESS.remove();
		}
	}

	private static void giveLoose(final Player player, final int amount) {
		int left = amount;
		while (left > 0) {
			ItemStack stack = new ItemStack(Items.EMERALD, Math.min(left, Items.EMERALD.getDefaultMaxStackSize()));
			left -= stack.getCount();
			player.getInventory().add(stack);
			if (!stack.isEmpty()) {
				ItemEntity dropped = player.createItemStackToDrop(stack, false, false);
				if (dropped != null) {
					player.level().addFreshEntity(dropped);
				}
			}
		}
	}

	/** Deposit or withdraw an amount, or {@link #ALL}, with the bag in the main hand or the offhand. */
	public record Request(boolean offhand, boolean deposit, int amount) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "emerald_bag"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Request::offhand,
			ByteBufCodecs.BOOL, Request::deposit,
			ByteBufCodecs.VAR_INT, Request::amount,
			Request::new
		);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** Turns the auto-deposit checkbox of the bag in the main hand or the offhand on or off. */
	public record AutoRequest(boolean offhand, boolean auto) implements CustomPacketPayload {
		public static final Type<AutoRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "emerald_bag_auto"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AutoRequest> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, AutoRequest::offhand,
			ByteBufCodecs.BOOL, AutoRequest::auto,
			AutoRequest::new
		);

		@Override
		public Type<AutoRequest> type() {
			return TYPE;
		}
	}

	private static final class BagItem extends Item {
		BagItem(final Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
			if (level.isClientSide()) {
				clientOpener.accept(hand);
			}
			return InteractionResult.SUCCESS;
		}
	}
}
