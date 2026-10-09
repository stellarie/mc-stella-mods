package com.stella.rpg.client;

import com.stella.rpg.AttackPreview;
import com.stella.rpg.Stat;
import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.BonusTooltip;
import com.stella.rpg.roll.WeaponRolls;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Environment(EnvType.CLIENT)
public final class WeaponRollTooltip {
	private WeaponRollTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!BonusTooltip.hasBonus(stack)) {
				return;
			}
			Map<String, Integer> rolls = stack.getOrDefault(WeaponRolls.STAT_ROLLS, Map.of());
			Map<String, Integer> affixes = stack.get(WeaponRolls.AFFIX_ROLLS);
			if (affixes != null) {
				for (Affix affix : Affix.values()) {
					Integer stored = affixes.get(affix.key());
					if (stored != null) {
						MutableComponent line = Component.translatable("tooltip.mod-stella-rpg.affix." + affix.key(),
							AttackPreview.number(affix.shown(stored)));
						String suffix = BonusTooltip.kindSuffix(stack, affix);
						if (suffix != null) {
							line.append(Component.translatable("tooltip.mod-stella-rpg.kind." + suffix));
						}
						lines.add(line.withStyle(ChatFormatting.GREEN));
					}
				}
			}
			for (Stat stat : Stat.values()) {
				Integer value = rolls.get(stat.key());
				if (value != null) {
					lines.add(Component.translatable("tooltip.mod-stella-rpg.roll", value,
						Component.translatable("tooltip.mod-stella-rpg.stat." + stat.key())).withStyle(ChatFormatting.GREEN));
				}
			}
		});
	}
}
