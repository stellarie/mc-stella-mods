package com.stella.spells;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Spell Writer: writes scrolls of the spellbook held in the off hand. It writes nothing, and costs no
 * cooldown, when that hand holds no book or the book has no scroll.
 */
final class SpellWriter {
	static final int SCROLLS = SpellNumbers.SPELL_WRITER.targets();

	private SpellWriter() {
	}

	static boolean apply(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (!(caster.getOffhandItem().getItem() instanceof SpellbookItem book)) {
			caster.sendSystemMessage(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".needs_offhand_book"), true);
			return false;
		}
		Item scroll = BuiltInRegistries.ITEM.getOptional(Spellbooks.scrollId(book.definition().id())).orElse(null);
		if (scroll == null) {
			caster.sendSystemMessage(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".no_scroll"), true);
			return false;
		}
		int left = SCROLLS;
		while (left > 0) {
			ItemStack batch = new ItemStack(scroll, Math.min(left, scroll.getDefaultMaxStackSize()));
			left -= batch.getCount();
			if (!caster.getInventory().add(batch) && !batch.isEmpty()) {
				caster.spawnAtLocation((ServerLevel) caster.level(), batch);
			}
		}
		return true;
	}
}
