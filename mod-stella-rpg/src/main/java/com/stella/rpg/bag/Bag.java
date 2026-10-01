package com.stella.rpg.bag;

import com.mojang.serialization.Codec;
import com.stella.rpg.StellaRpg;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Three extra rows of storage on the player. The vanilla inventory keeps its 36 item slots, because its equipment
 * slots use the indices right after them, so the bag is a separate container. It persists and copies on death.
 * The Bag tab opens it as a vanilla three-row chest menu. A pickup that finds no room in the inventory goes here.
 */
public final class Bag {
	public static final int ROWS = 3;
	public static final int SIZE = ROWS * 9;
	/** The bag menu's title; the client finds the bag screen by this key. */
	public static final String TITLE_KEY = "container.mod-stella-rpg.bag";

	public static final Codec<Bag> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(Bag::fromList, Bag::toList);
	public static final AttachmentType<Bag> TYPE = AttachmentRegistry.<Bag>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.initializer(Bag::new)
		.buildAndRegister(StellaRpg.id("bag"));

	private final NonNullList<ItemStack> stacks = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(OpenBagPayload.TYPE, OpenBagPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenBagPayload.TYPE, (payload, context) -> open(context.player()));
	}

	/** Opens the bag as a vanilla three-row chest menu over the player inventory. */
	public static void open(final ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider(
			(id, inventory, opener) -> menu(id, inventory, opener), Component.translatable(TITLE_KEY)
		));
	}

	public static ChestMenu menu(final int id, final Inventory inventory, final Player owner) {
		return new ChestMenu(MenuType.GENERIC_9x3, id, inventory, view(owner), ROWS);
	}

	/** Asks the server to open the bag. */
	public record OpenBagPayload() implements CustomPacketPayload {
		public static final Type<OpenBagPayload> TYPE = new Type<>(StellaRpg.id("open_bag"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenBagPayload> CODEC = StreamCodec.unit(new OpenBagPayload());

		@Override
		public Type<OpenBagPayload> type() {
			return TYPE;
		}
	}

	private static Bag fromList(final List<ItemStack> list) {
		Bag bag = new Bag();
		for (int i = 0; i < SIZE && i < list.size(); i++) {
			bag.stacks.set(i, list.get(i));
		}
		return bag;
	}

	private static List<ItemStack> toList(final Bag bag) {
		return new ArrayList<>(bag.stacks);
	}

	/** Read through this every time: loading a save replaces the object the attachment holds. */
	public static Bag of(final Player player) {
		return ((AttachmentTarget) player).getAttachedOrCreate(TYPE);
	}

	public static Container view(final Player owner) {
		return new View(owner);
	}

	public ItemStack get(final int index) {
		return this.stacks.get(index);
	}

	public void set(final int index, final ItemStack stack) {
		this.stacks.set(index, stack);
	}

	/** Fills matching partial stacks first, then empty slots. The stack keeps what did not fit. */
	public static void insert(final Player player, final ItemStack stack) {
		Bag bag = of(player);
		for (int i = 0; i < SIZE && !stack.isEmpty(); i++) {
			ItemStack held = bag.get(i);
			if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize()) {
				int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
				held.grow(moved);
				stack.shrink(moved);
			}
		}
		for (int i = 0; i < SIZE && !stack.isEmpty(); i++) {
			if (bag.get(i).isEmpty()) {
				bag.set(i, stack.copyAndClear());
			}
		}
	}

	private record View(Player owner) implements Container {
		@Override
		public int getContainerSize() {
			return SIZE;
		}

		@Override
		public boolean isEmpty() {
			Bag bag = of(this.owner);
			for (int i = 0; i < SIZE; i++) {
				if (!bag.get(i).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getItem(final int slot) {
			return of(this.owner).get(slot);
		}

		@Override
		public ItemStack removeItem(final int slot, final int count) {
			ItemStack stack = of(this.owner).get(slot);
			return stack.isEmpty() ? ItemStack.EMPTY : stack.split(count);
		}

		@Override
		public ItemStack removeItemNoUpdate(final int slot) {
			ItemStack stack = of(this.owner).get(slot);
			of(this.owner).set(slot, ItemStack.EMPTY);
			return stack;
		}

		@Override
		public void setItem(final int slot, final ItemStack stack) {
			of(this.owner).set(slot, stack);
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(final Player player) {
			return player == this.owner;
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < SIZE; i++) {
				of(this.owner).set(i, ItemStack.EMPTY);
			}
		}
	}
}
