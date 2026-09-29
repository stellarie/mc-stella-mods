package com.stella.spells;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Four extra hotbar keys: each key owns one slot that holds any item and uses it in place, without the item ever
 * being selected. The slots persist with the player, show as extra slots on the inventory screen, and drop on
 * death like the rest of the inventory.
 */
public final class QuickSlots {
	public static final int COUNT = 4;
	/** The first quick slot's index in the inventory menu: the four sit after the offhand, so no vanilla index moves. */
	public static final int MENU_FIRST_SLOT = InventoryMenu.SHIELD_SLOT + 1;
	/** Where the slots sit on the inventory screen: one row under its 166 pixel panel, lined up with the hotbar. */
	public static final int MENU_X = 8;
	public static final int MENU_Y = 172;
	public static final int MENU_STEP = 18;

	public static final Codec<QuickSlots> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(QuickSlots::fromList, QuickSlots::toList);
	public static final AttachmentType<QuickSlots> TYPE = AttachmentRegistry.<QuickSlots>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.initializer(QuickSlots::new)
		.buildAndRegister(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "quick_slots"));

	/** What a press did. */
	public enum Outcome {
		/** The item's use ran and did something. */
		USED,
		/** The slot holds nothing. */
		EMPTY,
		/** The player is already using an item, and swapping stacks would cut it short. */
		BUSY,
		/** The slot number is not one of ours. */
		INVALID,
		/** The item's use ran and did nothing, such as a spell with no target, or a bow that needs holding. */
		NO_EFFECT
	}

	private final NonNullList<ItemStack> stacks = NonNullList.withSize(COUNT, ItemStack.EMPTY);

	/** Registers the storage and the key packet. Called from the mod initializer, before any world loads. */
	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(QuickSlotUsePayload.TYPE, QuickSlotUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuickSlotUsePayload.TYPE, (payload, context) -> use(context.player(), payload.slot()));
	}

	private static QuickSlots fromList(final List<ItemStack> list) {
		QuickSlots slots = new QuickSlots();
		for (int i = 0; i < COUNT && i < list.size(); i++) {
			slots.stacks.set(i, list.get(i));
		}
		return slots;
	}

	private static List<ItemStack> toList(final QuickSlots slots) {
		return new ArrayList<>(slots.stacks);
	}

	/** The player's slots. Read through this every time: loading a save replaces the object the attachment holds. */
	public static QuickSlots of(final Player player) {
		return ((AttachmentTarget) player).getAttachedOrCreate(TYPE);
	}

	/** The inventory menu's window onto the player's slots, which follows the attachment when a save replaces it. */
	public static Container view(final Player owner) {
		return new View(owner);
	}

	public ItemStack get(final int index) {
		return this.stacks.get(index);
	}

	public void set(final int index, final ItemStack stack) {
		this.stacks.set(index, stack);
	}

	public boolean isEmpty() {
		return this.stacks.stream().allMatch(ItemStack::isEmpty);
	}

	/**
	 * Uses the item in a slot as though it were in the main hand: the stack is swapped into the hand, used through the
	 * ordinary use path, and swapped back, so the selected item never changes. Food and drink finish at once. An item
	 * that has to be held to work, such as a bow, is let go rather than left half drawn.
	 */
	public static Outcome use(final ServerPlayer player, final int index) {
		if (index < 0 || index >= COUNT) {
			return Outcome.INVALID;
		}
		QuickSlots slots = of(player);
		ItemStack stack = slots.get(index);
		if (stack.isEmpty()) {
			return Outcome.EMPTY;
		}
		if (player.isUsingItem()) {
			return Outcome.BUSY;
		}
		InteractionHand hand = InteractionHand.MAIN_HAND;
		ItemStack held = player.getItemInHand(hand);
		boolean used = false;
		player.setItemInHand(hand, stack);
		try {
			InteractionResult result = player.gameMode.useItem(player, player.level(), stack, hand);
			used = result.consumesAction();
			if (player.isUsingItem()) {
				used = finishOrRelease(player, hand);
			}
			if (used) {
				player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			}
		} finally {
			slots.set(index, player.getItemInHand(hand));
			player.setItemInHand(hand, held);
		}
		return used ? Outcome.USED : Outcome.NO_EFFECT;
	}

	// An item that starts a use has the player using it: eating and drinking are completed on the spot, the rest are released.
	private static boolean finishOrRelease(final ServerPlayer player, final InteractionHand hand) {
		ItemStack inUse = player.getItemInHand(hand);
		ItemUseAnimation animation = inUse.getUseAnimation();
		player.stopUsingItem();
		if (animation != ItemUseAnimation.EAT && animation != ItemUseAnimation.DRINK) {
			return false;
		}
		player.setItemInHand(hand, inUse.finishUsingItem(player.level(), player));
		return true;
	}

	private static final class View implements Container {
		private final Player owner;

		private View(final Player owner) {
			this.owner = owner;
		}

		private QuickSlots slots() {
			return of(this.owner);
		}

		@Override
		public int getContainerSize() {
			return COUNT;
		}

		@Override
		public boolean isEmpty() {
			return this.slots().isEmpty();
		}

		@Override
		public ItemStack getItem(final int slot) {
			return this.slots().get(slot);
		}

		@Override
		public ItemStack removeItem(final int slot, final int count) {
			ItemStack stack = this.slots().get(slot);
			return stack.isEmpty() ? ItemStack.EMPTY : stack.split(count);
		}

		@Override
		public ItemStack removeItemNoUpdate(final int slot) {
			ItemStack stack = this.slots().get(slot);
			this.slots().set(slot, ItemStack.EMPTY);
			return stack;
		}

		@Override
		public void setItem(final int slot, final ItemStack stack) {
			this.slots().set(slot, stack);
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(final Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < COUNT; i++) {
				this.slots().set(i, ItemStack.EMPTY);
			}
		}
	}
}
