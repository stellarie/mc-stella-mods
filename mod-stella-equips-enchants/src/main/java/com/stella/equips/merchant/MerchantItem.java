package com.stella.equips.merchant;

import com.stella.equips.tooltip.DescriptionLines;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A merchant sword, axe, spear or staff. The effect lives in {@link MerchantWeapons}; the item adds its tooltip. */
public class MerchantItem extends Item implements MerchantWeapon {
	private final MerchantDef def;

	public MerchantItem(final Item.Properties properties, final MerchantDef def) {
		super(properties);
		this.def = def;
	}

	@Override
	public MerchantDef def() {
		return this.def;
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder,
		final TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		MerchantWeapons.tooltip(stack).forEach(line -> DescriptionLines.add(builder, line));
	}
}
