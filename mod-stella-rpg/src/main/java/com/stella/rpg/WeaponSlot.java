package com.stella.rpg;

import com.stella.rpg.roll.WeaponRolls;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The weapon slot is inventory index {@link #SLOT}. While {@code selected} is that index, vanilla reads the main hand
 * from it, so attack, use, dice, and other mods need no redirect. A hotkey press asks the server for one of four actions.
 */
public final class WeaponSlot {
	/** The last index, so pickups fill it last. */
	public static final int SLOT = 35;
	/** The hotkey index of the ready-weapon key. */
	public static final int READY = -1;
	/** Above the shield slot at (77, 62). */
	public static final int MENU_X = 77;
	public static final int MENU_Y = 44;

	public enum Result {
		EQUIPPED,
		CAST,
		NO_EFFECT,
		ARMOR,
		SELECTED,
		READIED,
		REFUSED
	}

	private WeaponSlot() {
	}

	public static boolean isSlot(final int slot) {
		return slot == SLOT;
	}

	/** Anything that attacks or mines, or shoots. */
	public static boolean isEquipment(final ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON) || stack.getItem() instanceof ProjectileWeaponItem) {
			return true;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return modifiers.modifiers().stream()
			.anyMatch(entry -> entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND));
	}

	public static Result press(final ServerPlayer player, final int index) {
		if (player.isSpectator()) {
			return Result.REFUSED;
		}
		if (index == READY) {
			select(player, SLOT);
			return Result.READIED;
		}
		if (!Inventory.isHotbarSlot(index)) {
			return Result.REFUSED;
		}
		Inventory inventory = player.getInventory();
		ItemStack stack = inventory.getItem(index);
		if (stack.is(WeaponRolls.SPELLBOOKS)) {
			return cast(player, index);
		}
		if (isEquipment(stack)) {
			// A two-slot swap never loses an item.
			inventory.setItem(index, inventory.getItem(SLOT));
			inventory.setItem(SLOT, stack);
			select(player, SLOT);
			return Result.EQUIPPED;
		}
		EquipmentSlot armor = armorSlot(player, stack);
		if (armor != null) {
			return equipArmor(player, index, stack, armor);
		}
		select(player, index);
		return Result.SELECTED;
	}

	// A head block such as a pumpkin is placed.
	private static EquipmentSlot armorSlot(final ServerPlayer player, final ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof BlockItem) {
			return null;
		}
		EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
	}

	private static Result equipArmor(final ServerPlayer player, final int index, final ItemStack stack, final EquipmentSlot slot) {
		ItemStack worn = player.getItemBySlot(slot);
		if (!player.isCreative() && EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
			return Result.REFUSED;
		}
		player.getInventory().setItem(index, worn);
		player.setItemSlot(slot, stack);
		return Result.ARMOR;
	}

	/** The spell's own rules and cooldown apply. */
	private static Result cast(final ServerPlayer player, final int index) {
		if (player.isUsingItem()) {
			return Result.REFUSED;
		}
		Inventory inventory = player.getInventory();
		int previous = inventory.getSelectedSlot();
		boolean used;
		inventory.setSelectedSlot(index);
		try {
			ItemStack book = inventory.getSelectedItem();
			InteractionResult result = player.gameMode.useItem(player, player.level(), book, InteractionHand.MAIN_HAND);
			used = result.consumesAction();
			if (player.isUsingItem()) {
				// A held use would end on the swap back anyway.
				player.stopUsingItem();
			}
			if (used) {
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
		} finally {
			inventory.setSelectedSlot(previous);
		}
		return used ? Result.CAST : Result.NO_EFFECT;
	}

	private static void select(final ServerPlayer player, final int slot) {
		Inventory inventory = player.getInventory();
		if (inventory.getSelectedSlot() != slot && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
			player.stopUsingItem();
		}
		inventory.setSelectedSlot(slot);
		if (player.connection != null) {
			player.connection.send(new ClientboundSetHeldSlotPacket(slot));
		}
	}
}
