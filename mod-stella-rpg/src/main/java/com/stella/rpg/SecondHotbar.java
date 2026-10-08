package com.stella.rpg;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A per-player second hotbar of {@link #SIZE} slots. The main row is inventory slots 0 to {@code SIZE - 1}, the row
 * the vanilla hotbar shows; the second row is stored on the player. {@link #swap} exchanges the two rows in place,
 * and the held slot index is relative to the row, so the hand after the swap is the shown row's item at that index.
 */
public final class SecondHotbar {
	/** The row width, so the second row matches the vanilla hotbar. */
	public static final int SIZE = 9;

	/** One row of slots, saved with the player. */
	public static final Codec<SecondHotbar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("slots").forGetter(SecondHotbar::toList)
	).apply(instance, SecondHotbar::fromList));

	/** The same row, sent to its owner on a change. */
	public static final StreamCodec<RegistryFriendlyByteBuf, SecondHotbar> STREAM_CODEC = StreamCodec.composite(
		ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(SIZE)), SecondHotbar::toList,
		SecondHotbar::fromList
	);

	public static final AttachmentType<SecondHotbar> TYPE = AttachmentRegistry.<SecondHotbar>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.initializer(SecondHotbar::new)
		.syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(StellaRpg.id("second_hotbar"));

	private final NonNullList<ItemStack> stacks;

	private SecondHotbar() {
		this(NonNullList.withSize(SIZE, ItemStack.EMPTY));
	}

	private SecondHotbar(final NonNullList<ItemStack> stacks) {
		this.stacks = stacks;
	}

	/** The inventory screen's toggle asks the server to exchange the two rows. */
	public record SwapPayload() implements CustomPacketPayload {
		public static final Type<SwapPayload> TYPE = new Type<>(StellaRpg.id("swap_hotbar_row"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SwapPayload> CODEC = StreamCodec.unit(new SwapPayload());

		@Override
		public Type<SwapPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SwapPayload.TYPE, SwapPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SwapPayload.TYPE, (payload, context) -> swap(context.player()));
	}

	/** Read through this every time: loading a save replaces the object the attachment holds. */
	public static SecondHotbar of(final Player player) {
		return ((AttachmentTarget) player).getAttachedOrCreate(TYPE);
	}

	/**
	 * Exchanges the main row and the second row in place, and keeps the held slot index. A spectator keeps both rows.
	 */
	public static boolean swap(final ServerPlayer player) {
		if (player.isSpectator()) {
			return false;
		}
		Inventory inventory = player.getInventory();
		SecondHotbar stored = of(player);
		SecondHotbar row = new SecondHotbar();
		for (int i = 0; i < SIZE; i++) {
			ItemStack main = inventory.getItem(i);
			row.set(i, main);
			inventory.setItem(i, stored.get(i));
		}
		// A new object on every swap, so the owner is sent the new row.
		((AttachmentTarget) player).setAttached(TYPE, row);
		if (player.connection != null) {
			player.connection.send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
		}
		player.inventoryMenu.broadcastChanges();
		return true;
	}

	public ItemStack get(final int index) {
		return this.stacks.get(index);
	}

	public void set(final int index, final ItemStack stack) {
		this.stacks.set(index, stack);
	}

	private static SecondHotbar fromList(final List<ItemStack> list) {
		SecondHotbar row = new SecondHotbar();
		for (int i = 0; i < SIZE && i < list.size(); i++) {
			row.stacks.set(i, list.get(i));
		}
		return row;
	}

	private List<ItemStack> toList() {
		return List.copyOf(this.stacks);
	}
}
