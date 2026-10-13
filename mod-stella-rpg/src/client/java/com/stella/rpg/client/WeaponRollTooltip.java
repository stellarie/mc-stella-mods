package com.stella.rpg.client;

import com.stella.rpg.roll.BonusTooltip;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

@Environment(EnvType.CLIENT)
public final class WeaponRollTooltip {
	private WeaponRollTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> lines.addAll(BonusTooltip.lines(stack)));
	}
}
