package com.stella.spells.mixin;

import com.stella.spells.SpellEnchantmentTags;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class SpellAnvilCostMixin {
	@Accessor("cost")
	protected abstract DataSlot modStellaSpells$costSlot();

	@Inject(method = "createResult", at = @At("RETURN"))
	private void modStellaSpells$makeSpellEnchantmentsFree(final CallbackInfo ci) {
		if (this.modStellaSpells$isSpellOnlyApplication()) {
			this.modStellaSpells$costSlot().set(0);
		}
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void modStellaSpells$allowFreeSpellPickup(
		final Player player,
		final boolean hasItem,
		final CallbackInfoReturnable<Boolean> cir
	) {
		if (this.modStellaSpells$isSpellOnlyApplication()) {
			cir.setReturnValue(hasItem && this.modStellaSpells$costSlot().get() == 0);
		}
	}

	@Unique
	private boolean modStellaSpells$isSpellOnlyApplication() {
		if (FabricLoader.getInstance().isModLoaded("mod-stella-equips-enchants")) {
			return false;
		}

		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack input = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		ItemStack addition = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		if (input.isEmpty() || !addition.is(Items.ENCHANTED_BOOK) || result.isEmpty()) {
			return false;
		}
		if (!ItemStack.matches(
			modStellaSpells$withoutEnchantments(input), modStellaSpells$withoutEnchantments(result)
		)) {
			return false;
		}

		ItemEnchantments before = EnchantmentHelper.getEnchantmentsForCrafting(input);
		ItemEnchantments after = EnchantmentHelper.getEnchantmentsForCrafting(result);
		Set<Holder<Enchantment>> keys = new HashSet<>(before.keySet());
		keys.addAll(after.keySet());
		boolean appliedSpellEnchantment = false;
		for (Holder<Enchantment> enchantment : keys) {
			int oldLevel = before.getLevel(enchantment);
			int newLevel = after.getLevel(enchantment);
			if (newLevel == oldLevel) {
				continue;
			}
			if (newLevel < oldLevel || !enchantment.is(SpellEnchantmentTags.SPELL_ENCHANTMENTS)) {
				return false;
			}
			appliedSpellEnchantment = true;
		}

		return appliedSpellEnchantment;
	}

	@Unique
	private static ItemStack modStellaSpells$withoutEnchantments(final ItemStack stack) {
		ItemStack result = stack.copy();
		result.remove(DataComponents.ENCHANTMENTS);
		result.remove(DataComponents.STORED_ENCHANTMENTS);
		result.remove(DataComponents.REPAIR_COST);
		return result;
	}
}
