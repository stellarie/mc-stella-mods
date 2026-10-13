package com.stella.equips.unique;

import com.stella.equips.refine.Refine;
import com.stella.equips.tooltip.DescriptionLines;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * The tooltip lines of a unique, in order: the Requires line, the Desc line with the base effect, the job line, the
 * source line, then one line for each of +7, +10, +12 and +15. A tier line is gold at or above its refine level and dark
 * gray below it. The job line is information only: any player can use any unique.
 */
public final class UniqueTooltip {
	private static final int[] TIERS = {Uniques.PLUS_7, Uniques.PLUS_10, Uniques.PLUS_12, Uniques.PLUS_15};

	private UniqueTooltip() {
	}

	/** The lines of a unique stack, or an empty list for any other stack. */
	public static List<Component> lines(final ItemStack stack) {
		UniqueDef def = Uniques.def(stack);
		if (def == null) {
			return List.of();
		}
		int refine = Refine.level(stack);
		String[] texts = {def.plus7(), def.plus10(), def.plus12(), def.plus15()};
		List<Component> lines = new ArrayList<>(8);
		lines.add(DescriptionLines.requires(line(def.kind().requirement(), ChatFormatting.WHITE)));
		lines.add(DescriptionLines.desc(line(def.base(), ChatFormatting.GOLD)));
		lines.add(line("Job: " + def.job().title(), ChatFormatting.AQUA));
		lines.add(sources(def));
		for (int tier = 0; tier < TIERS.length; tier++) {
			lines.add(line("+" + TIERS[tier] + ": " + texts[tier], refine >= TIERS[tier] ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	public static void append(final ItemStack stack, final Consumer<Component> builder) {
		for (Component line : lines(stack)) {
			DescriptionLines.add(builder, line);
		}
	}

	private static MutableComponent line(final String text, final ChatFormatting color) {
		return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
	}

	private static MutableComponent sources(final UniqueDef def) {
		MutableComponent line = Component.literal("Drops from: ").withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
		for (int index = 0; index < def.sources().size(); index++) {
			if (index > 0) {
				line.append(", ");
			}
			line.append(sourceName(def.sources().get(index)));
		}
		return line;
	}

	/** The mob name of a source: the entity name when the type is loaded, else its id as a title. */
	static Component sourceName(final Identifier source) {
		if (source.equals(UniqueDrops.SOVEREIGN_SOURCE)) {
			return Component.literal("Blaze Sovereign");
		}
		return BuiltInRegistries.ENTITY_TYPE.getOptional(source).<Component>map(EntityType::getDescription).orElseGet(() -> Component.literal(title(source.getPath())));
	}

	private static String title(final String path) {
		StringBuilder title = new StringBuilder();
		for (String word : path.split("_")) {
			if (!word.isEmpty()) {
				if (title.length() > 0) {
					title.append(' ');
				}
				title.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
			}
		}
		return title.toString();
	}
}
