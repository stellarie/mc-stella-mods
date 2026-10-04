package com.stella.spells;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The retired quick slots. Oniichan removed them on 2026-10-04: the rpg weapon slot and hotkeys replace them. A save may
 * still hold items in them, so the attachment stays readable and every join moves those items back into the inventory,
 * or drops them at the player's feet when it is full.
 */
public final class QuickSlots {
	public static final Codec<List<ItemStack>> CODEC = ItemStack.OPTIONAL_CODEC.listOf();
	public static final AttachmentType<List<ItemStack>> TYPE = AttachmentRegistry.<List<ItemStack>>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.buildAndRegister(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "quick_slots"));

	private QuickSlots() {
	}

	static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> drain(handler.player));
	}

	/** Moves every item out of the retired slots. Returns how many stacks moved. */
	public static int drain(final ServerPlayer player) {
		List<ItemStack> held = ((AttachmentTarget) player).getAttached(TYPE);
		if (held == null) {
			return 0;
		}
		int moved = 0;
		for (ItemStack stack : new ArrayList<>(held)) {
			if (!stack.isEmpty()) {
				ItemStack copy = stack.copy();
				if (!player.getInventory().add(copy) && !copy.isEmpty()) {
					player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), copy);
				}
				moved++;
			}
		}
		((AttachmentTarget) player).removeAttached(TYPE);
		return moved;
	}
}
