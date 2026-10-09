package com.stella.equips.imbue;

import com.stella.equips.StellaEquips;
import com.stella.equips.refine.Refine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The nether star imbue. The anvil adds one imbue to a weapon or an armor piece for each star.
 * The count lives in the stack's custom data under {@link #KEY}, so mod-stella-rpg and this mod need no class link.
 * Each imbue also asks the rpg share {@link #GRANT_SHARE} for {@link #STAT_PER_IMBUE} on all six stats.
 * Without that share the imbue still works and skips the grant.
 */
public final class NetherImbue {
	public static final String KEY = "stella_nether_star";
	public static final String GRANT_SHARE = "mod-stella-rpg:grant-bonus";
	public static final int STAT_PER_IMBUE = 10;
	/** The emerald price of the next imbue is this much for each imbue the piece will then hold. */
	public static final int PRICE_STEP = 16;
	public static final List<String> STATS = List.of("str", "agi", "vit", "dex", "int", "luk");
	private static final String LORE_PREFIX = "Nether Star x";

	private NetherImbue() {
	}

	/** How many imbues the stack holds. */
	public static int count(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : Math.max(0, data.copyTag().getIntOr(KEY, 0));
	}

	/** The emerald price of the next imbue on a piece that holds {@code count} imbues. */
	public static int price(final int count) {
		return PRICE_STEP * (count + 1);
	}

	/** Armor, a weapon, or a bow. A shield, a book and a plain item are refused. */
	public static boolean eligible(final ItemStack stack) {
		return !stack.isEmpty() && (Refine.isArmor(stack) || Refine.isWeapon(stack));
	}

	/** A copy of the piece with one more imbue, its lore line, and the stat grant when the rpg share exists. */
	public static ItemStack imbued(final ItemStack input) {
		ItemStack result = input.copy();
		int count = count(input) + 1;
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putInt(KEY, count));
		result.set(DataComponents.LORE, withLine(result, count));
		grant(result);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static void grant(final ItemStack stack) {
		Object share = FabricLoader.getInstance().getObjectShare().get(GRANT_SHARE);
		if (!(share instanceof Function<?, ?> function)) {
			return;
		}
		Map<String, Integer> delta = new LinkedHashMap<>();
		for (String stat : STATS) {
			delta.put(stat, STAT_PER_IMBUE);
		}
		try {
			((Function<Object[], Boolean>) function).apply(new Object[] {stack, delta});
		} catch (RuntimeException failure) {
			StellaEquips.LOGGER.warn("The stat grant share {} failed: {}", GRANT_SHARE, failure.toString());
		}
	}

	// Foreign lines stay, the refine lines among them. The imbue line goes last.
	private static ItemLore withLine(final ItemStack stack, final int count) {
		List<Component> lines = new ArrayList<>();
		for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			if (!line.getString().startsWith(LORE_PREFIX)) {
				lines.add(line);
			}
		}
		lines.add(Component.literal(LORE_PREFIX + count).withStyle(style -> style.withColor(ChatFormatting.DARK_PURPLE).withItalic(false)));
		return new ItemLore(lines);
	}
}
