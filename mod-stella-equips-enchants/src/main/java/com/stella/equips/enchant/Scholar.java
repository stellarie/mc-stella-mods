package com.stella.equips.enchant;

import com.stella.equips.StellaEquips;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Each worn or held piece with Scholar adds 15% to the experience from an orb. */
public final class Scholar {
	public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "utility/scholar"));
	public static final double BONUS_PER_PIECE = 0.15;

	private Scholar() {
	}

	public static int pieces(final Player player) {
		Holder<Enchantment> holder = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(KEY).orElse(null);
		if (holder == null) {
			return 0;
		}
		int pieces = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			boolean worn = slot.getType() == EquipmentSlot.Type.HAND || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
			if (worn && EnchantmentHelper.getItemEnchantmentLevel(holder, player.getItemBySlot(slot)) > 0) {
				pieces++;
			}
		}
		return pieces;
	}

	/** The orb's experience after Scholar; the fraction rounds up by chance. */
	public static int scale(final Player player, final int amount) {
		int pieces = pieces(player);
		if (amount <= 0 || pieces == 0) {
			return amount;
		}
		double scaled = amount * (1.0 + BONUS_PER_PIECE * pieces);
		int whole = (int) Math.floor(scaled);
		return whole + (player.getRandom().nextDouble() < scaled - whole ? 1 : 0);
	}
}
