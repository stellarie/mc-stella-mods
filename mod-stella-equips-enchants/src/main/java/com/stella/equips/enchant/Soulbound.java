package com.stella.equips.enchant;

import com.stella.equips.StellaEquips;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Soulbound: an enchanted item stays with its owner through death. When the owner dies, the items are
 * held back from the drop onto a persistent stash on the player, which moves to the respawned player
 * and is given back. The stash is saved with the player, so a server restart
 * between death and respawn loses nothing. With keepInventory on, vanilla keeps everything anyway.
 */
public final class Soulbound {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "utility/soulbound");
	public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, ID);
	/** The items held back from a death, on the player, until the respawn gives them back. */
	public static final AttachmentType<List<ItemStack>> STASH = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "soulbound_stash"),
		builder -> builder.persistent(ItemStack.CODEC.listOf())
	);

	private Soulbound() {
	}

	public static void register() {
		// The stash moves across on its own: Fabric's copy-on-death left it behind in 2 of 11 measured runs.
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			List<ItemStack> stash = oldPlayer.getAttached(STASH);
			if (!alive && stash != null && !stash.isEmpty()) {
				newPlayer.setAttached(STASH, List.copyOf(stash));
				oldPlayer.removeAttached(STASH);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> giveBack(newPlayer));
	}

	public static boolean isSoulbound(final ItemStack stack) {
		return !stack.isEmpty() && EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream().anyMatch(holder -> holder.is(KEY));
	}

	/** Called at the head of the player's equipment drop: moves every soulbound item onto the stash. */
	public static void holdBack(final Player player, final ServerLevel level) {
		if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}

		List<ItemStack> stash = new ArrayList<>(player.getAttachedOrElse(STASH, List.of()));
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isSoulbound(stack)) {
				stash.add(stack.copy());
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		// Worn and held gear, in case a slot sits outside the inventory's own range.
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);
			if (isSoulbound(stack)) {
				stash.add(stack.copy());
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}

		if (!stash.isEmpty()) {
			player.setAttached(STASH, stash);
			StellaEquips.LOGGER.info("[BEHAVIOR] test=soulbound HELD player={} items={}", player.getUUID(), stash.size());
		}
	}

	/** Gives the respawned player back what its death held back; anything that does not fit drops at its feet. */
	static void giveBack(final ServerPlayer player) {
		List<ItemStack> stash = player.getAttached(STASH);
		if (stash == null || stash.isEmpty()) {
			return;
		}

		player.removeAttached(STASH);
		for (ItemStack stack : stash) {
			ItemStack copy = stack.copy();
			if (!player.getInventory().add(copy)) {
				player.spawnAtLocation(player.level(), copy);
			}
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=soulbound RETURNED player={} items={}", player.getUUID(), stash.size());
	}
}
