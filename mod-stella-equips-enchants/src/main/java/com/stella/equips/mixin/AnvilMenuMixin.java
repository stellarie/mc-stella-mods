package com.stella.equips.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stella.equips.anvil.AnvilCost;
import com.stella.equips.anvil.EmeraldPurse;
import com.stella.equips.imbue.NetherImbue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Accessor("cost")
	protected abstract DataSlot stellaEquips$costSlot();

	@Accessor("repairItemCountCost")
	protected abstract void stellaEquips$setRepairCount(int count);

	/** Armor and a slime heart make the armor with the heart, free, using one heart. */
	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void stellaEquips$insertHeart(final CallbackInfo ci) {
		ItemStack addition = this.stellaEquips$addition();
		if (!addition.is(com.stella.equips.heart.SlimeHeart.ITEM)) {
			return;
		}
		ItemStack input = this.stellaEquips$input();
		ItemStack result = ItemStack.EMPTY;
		if (com.stella.equips.refine.Refine.isArmor(input) && !com.stella.equips.heart.SlimeHeart.holdsHeart(input)) {
			result = input.copy();
			result.set(com.stella.equips.heart.SlimeHeart.INSERTED, true);
		}
		((AnvilMenu) (Object) this).getSlot(AnvilMenu.RESULT_SLOT).set(result);
		this.stellaEquips$costSlot().set(0);
		this.stellaEquips$setRepairCount(1);
		((AnvilMenu) (Object) this).broadcastChanges();
		ci.cancel();
	}

	/** A weapon or armor piece and a nether star make the piece with one more imbue, for emeralds, using one star. */
	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void stellaEquips$imbueStar(final CallbackInfo ci) {
		if (!this.stellaEquips$addition().is(Items.NETHER_STAR)) {
			return;
		}
		ItemStack input = this.stellaEquips$input();
		boolean imbuable = NetherImbue.eligible(input);
		ItemStack result = imbuable ? NetherImbue.imbued(input) : ItemStack.EMPTY;
		((AnvilMenu) (Object) this).getSlot(AnvilMenu.RESULT_SLOT).set(result);
		// The cancel skips the RETURN price writer, so this branch writes the emerald price itself.
		this.stellaEquips$costSlot().set(imbuable ? NetherImbue.price(NetherImbue.count(input)) : 0);
		this.stellaEquips$setRepairCount(1);
		((AnvilMenu) (Object) this).broadcastChanges();
		ci.cancel();
	}

	/** The Too Expensive gate compares the emerald price, never the level price. */
	@ModifyExpressionValue(
		method = "createResult",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;get()I", ordinal = 1)
	)
	private int stellaEquips$priceAtTheGate(final int original) {
		return AnvilCost.price(EmeraldPurse.offeredEnchantments(this.stellaEquips$input(), this.stellaEquips$addition()));
	}

	/** One field, and the display, the pickup check and the charge all read it. */
	@Inject(method = "createResult", at = @At("RETURN"))
	private void stellaEquips$writeThePrice(final CallbackInfo ci) {
		this.stellaEquips$costSlot().set(
			AnvilCost.price(EmeraldPurse.appliedEnchantments(this.stellaEquips$input(), this.stellaEquips$result()))
		);
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void stellaEquips$mayPickupOnEmeralds(
		final Player player,
		final boolean hasItem,
		final CallbackInfoReturnable<Boolean> cir
	) {
		int price = this.stellaEquips$costSlot().get();
		cir.setReturnValue(
			hasItem && (player.hasInfiniteMaterials() || EmeraldPurse.count(player) >= price)
		);
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void stellaEquips$spendEmeralds(final Player player, final ItemStack carried, final CallbackInfo ci) {
		EmeraldPurse.spend(player, this.stellaEquips$costSlot().get());
	}

	@ModifyArg(
		method = "onTake",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V"),
		index = 0
	)
	private int stellaEquips$spendNoLevels(final int levels) {
		return 0;
	}

	@Unique
	private ItemStack stellaEquips$input() {
		return ((AnvilMenu) (Object) this).getSlot(AnvilMenu.INPUT_SLOT).getItem();
	}

	@Unique
	private ItemStack stellaEquips$addition() {
		return ((AnvilMenu) (Object) this).getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
	}

	@Unique
	private ItemStack stellaEquips$result() {
		return ((AnvilMenu) (Object) this).getSlot(AnvilMenu.RESULT_SLOT).getItem();
	}
}
