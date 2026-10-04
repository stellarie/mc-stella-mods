package com.stella.rpg;

import com.stella.rpg.roll.WeaponRolls;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
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
 * from it, so attack, use, dice, and other mods need no redirect. In quick bar mode a hotkey asks the server for an
 * action on its slot, and the weapon stays in hand. Food and potions are eaten or drunk at once.
 */
public final class WeaponSlot {
	/** The last index, so pickups fill it last. */
	public static final int SLOT = 35;
	/** The hotkey index of the ready-weapon key. */
	public static final int READY = -1;
	/** Above the shield slot at (77, 62), in quick bar mode. */
	public static final int MENU_X = 77;
	public static final int MENU_Y = 44;

	public enum Result {
		EQUIPPED,
		CAST,
		USED,
		EATING,
		NO_EFFECT,
		ARMOR,
		READIED,
		REFUSED
	}

	/** Players eating from a hotkey, by the hotkey index. */
	private static final Map<UUID, Integer> EATING = new ConcurrentHashMap<>();

	private WeaponSlot() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (UUID id : EATING.keySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player == null) {
					EATING.remove(id);
				} else if (!player.isUsingItem()) {
					endEating(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> endEating(handler.getPlayer()));
		// A fresh or older save may hold a hotbar slot.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (QuickbarMode.isOn(player) && !isSlot(player.getInventory().getSelectedSlot())) {
				select(player, SLOT);
			}
		});
	}

	public static boolean isSlot(final int slot) {
		return slot == SLOT;
	}

	/** A weapon, a tool that attacks (pickaxe, axe, shovel, hoe), or a bow. Shears are used, not held. */
	public static boolean isEquipment(final ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.has(DataComponents.WEAPON) || stack.getItem() instanceof ProjectileWeaponItem) {
			return true;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return modifiers.modifiers().stream()
			.anyMatch(entry -> entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND));
	}

	public static Result hotkey(final ServerPlayer player, final int index, final boolean down) {
		if (down) {
			return press(player, index);
		}
		release(player, index);
		return Result.NO_EFFECT;
	}

	public static Result press(final ServerPlayer player, final int index) {
		if (player.isSpectator() || !QuickbarMode.isOn(player)) {
			return Result.REFUSED;
		}
		Integer eating = EATING.get(player.getUUID());
		if (eating != null && eating == index) {
			return Result.EATING;
		}
		endEating(player);
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
		if (stack.isEmpty()) {
			return Result.NO_EFFECT;
		}
		if (stack.has(DataComponents.CONSUMABLE)) {
			return startEating(player, index);
		}
		return useAtTarget(player, index);
	}

	/** A key-up ends the eating window that key opened. */
	public static void release(final ServerPlayer player, final int index) {
		Integer eating = EATING.get(player.getUUID());
		if (eating != null && eating == index) {
			endEating(player);
		}
	}

	public static boolean isEating(final ServerPlayer player) {
		return EATING.containsKey(player.getUUID());
	}

	/** Stops an unfinished use and puts the weapon back in hand. */
	public static void endEating(final ServerPlayer player) {
		if (EATING.remove(player.getUUID()) == null) {
			return;
		}
		if (player.isUsingItem()) {
			player.stopUsingItem();
		}
		if (QuickbarMode.isOn(player)) {
			select(player, SLOT);
		}
	}

	private static Result startEating(final ServerPlayer player, final int index) {
		if (player.isUsingItem()) {
			return Result.REFUSED;
		}
		select(player, index);
		InteractionResult result = player.gameMode.useItem(player, player.level(), player.getInventory().getSelectedItem(), InteractionHand.MAIN_HAND);
		if (!player.isUsingItem()) {
			// Not hungry, or instant: weapon back now.
			select(player, SLOT);
			return result.consumesAction() ? Result.USED : Result.NO_EFFECT;
		}
		// Food and potions finish at once in quick bar mode.
		ItemStack used = player.getUseItem();
		ItemStack left = used.finishUsingItem(player.level(), player);
		if (left != used) {
			player.setItemInHand(InteractionHand.MAIN_HAND, left);
		}
		player.stopUsingItem();
		select(player, SLOT);
		return Result.USED;
	}

	/** Vanilla order: entity, then block, then air. */
	private static Result useAtTarget(final ServerPlayer player, final int index) {
		if (player.isUsingItem()) {
			return Result.REFUSED;
		}
		Inventory inventory = player.getInventory();
		int previous = inventory.getSelectedSlot();
		InteractionResult result = InteractionResult.PASS;
		inventory.setSelectedSlot(index);
		try {
			HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
			EntityHitResult entityHit = targetEntity(player, hit);
			if (entityHit != null) {
				Entity entity = entityHit.getEntity();
				Vec3 location = entityHit.getLocation().subtract(entity.getX(), entity.getY(), entity.getZ());
				result = player.interactOn(entity, InteractionHand.MAIN_HAND, location);
			}
			if (!result.consumesAction() && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block
				&& player.level().mayInteract(player, block.getBlockPos())) {
				result = player.gameMode.useItemOn(player, player.level(), inventory.getSelectedItem(), InteractionHand.MAIN_HAND, block);
			}
			if (!result.consumesAction()) {
				result = player.gameMode.useItem(player, player.level(), inventory.getSelectedItem(), InteractionHand.MAIN_HAND);
			}
			if (player.isUsingItem()) {
				// A held use cannot outlive the swap back.
				player.stopUsingItem();
			}
			if (result.consumesAction()) {
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
		} finally {
			inventory.setSelectedSlot(previous);
		}
		return result.consumesAction() ? Result.USED : Result.NO_EFFECT;
	}

	/** The entity the vanilla client would pick. */
	private static EntityHitResult targetEntity(final ServerPlayer player, final HitResult blockHit) {
		double range = player.entityInteractionRange();
		Vec3 from = player.getEyePosition();
		double nearest = range * range;
		if (blockHit.getType() != HitResult.Type.MISS) {
			nearest = Math.min(nearest, blockHit.getLocation().distanceToSqr(from));
		}
		Vec3 view = player.getViewVector(1.0F);
		Vec3 to = from.add(view.scale(range));
		AABB box = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, box, EntitySelector.CAN_BE_PICKED, nearest);
		return hit != null && hit.getLocation().distanceToSqr(from) < nearest ? hit : null;
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

	public static void select(final ServerPlayer player, final int slot) {
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
