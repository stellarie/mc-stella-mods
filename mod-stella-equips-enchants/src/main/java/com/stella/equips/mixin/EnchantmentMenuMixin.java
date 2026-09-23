package com.stella.equips.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stella.equips.anvil.EmeraldSlot;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The table charges the offered level in emeralds. `costs` is the array the
 * client renders, so it holds the price. The vanilla roll power is kept aside,
 * because the roll still needs it, and the click path reads the price from the
 * array before the swap.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
	@Shadow
	@Final
	public int[] costs;

	@Shadow
	@Final
	public int[] levelClue;

	@Shadow
	@Final
	private Container enchantSlots;

	@Unique
	private final int[] stellaEquips$rollPowers = new int[3];

	@Unique
	private int stellaEquips$button;

	@Unique
	private int stellaEquips$price;

	@Unique
	private int stellaEquips$level;

	/** The currency slot is the emerald slot, at the position the lapis slot held. */
	@Inject(
		method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
		at = @At("RETURN")
	)
	private void stellaEquips$emeraldCurrencySlot(
		final int containerId,
		final Inventory inventory,
		final ContainerLevelAccess access,
		final CallbackInfo ci
	) {
		((EnchantmentMenu) (Object) this).slots.set(1, new EmeraldSlot(this.enchantSlots, 1, 35, 47));
	}

	/** The offered level becomes the price, so the rendered number is the charge. */
	@Inject(method = "slotsChanged", at = @At("RETURN"))
	private void stellaEquips$priceIsTheOfferedLevel(final Container container, final CallbackInfo ci) {
		for (int slot = 0; slot < this.costs.length; slot++) {
			this.stellaEquips$rollPowers[slot] = this.costs[slot];
			this.costs[slot] = Math.max(this.levelClue[slot], 0);
		}
	}

	@Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
	private void stellaEquips$openTheClick(
		final Player player,
		final int buttonId,
		final CallbackInfoReturnable<Boolean> cir
	) {
		int price = this.costs[buttonId];
		boolean canPay = player.hasInfiniteMaterials() || this.enchantSlots.getItem(1).getCount() >= price;
		if (price <= 0 || this.enchantSlots.getItem(0).isEmpty() || !canPay) {
			cir.setReturnValue(false);
			return;
		}

		this.stellaEquips$button = buttonId;
		this.stellaEquips$price = price;
		this.stellaEquips$level = player.experienceLevel;
		this.costs[buttonId] = this.stellaEquips$rollPowers[buttonId];
	}

	/** Every later reader in the click path pays the price, not the button number. */
	@ModifyVariable(method = "clickMenuButton", at = @At("STORE"), index = 5)
	private int stellaEquips$theCostIsThePrice(final int enchantmentCost) {
		return this.stellaEquips$price;
	}

	@Inject(method = "clickMenuButton", at = @At("RETURN"))
	private void stellaEquips$closeTheClick(
		final Player player,
		final int buttonId,
		final CallbackInfoReturnable<Boolean> cir
	) {
		for (int slot = 0; slot < this.costs.length; slot++) {
			this.costs[slot] = Math.max(this.levelClue[slot], 0);
		}

		player.experienceLevel = this.stellaEquips$level;
	}

	@ModifyExpressionValue(
		method = "clickMenuButton",
		at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I")
	)
	private int stellaEquips$noLevelRequirement(final int level) {
		return Integer.MAX_VALUE;
	}
}
