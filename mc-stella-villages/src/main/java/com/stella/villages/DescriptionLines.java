package com.stella.villages;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Splits screen text into lines of at most {@link #MAX_CHARS} characters, as the other Stella mods do for a tooltip.
 * A line breaks at a space. A word longer than the limit keeps a line of its own. A line break in the text starts a
 * new paragraph.
 */
public final class DescriptionLines {
	public static final int MAX_CHARS = 24;

	private DescriptionLines() {
	}

	public static List<String> wrap(final String text) {
		List<String> lines = new ArrayList<>();
		for (String paragraph : text.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split(" ")) {
				if (word.isEmpty()) {
					continue;
				}
				if (line.length() > 0 && line.length() + 1 + word.length() > MAX_CHARS) {
					lines.add(line.toString());
					line.setLength(0);
				}
				line.append(line.length() > 0 ? " " : "").append(word);
			}
			lines.add(line.toString());
		}
		return lines;
	}

	/** The wrapped lines of the text, each one a component with these formats and no italic. */
	public static List<Component> lines(final String text, final ChatFormatting... formats) {
		List<Component> out = new ArrayList<>();
		for (String line : wrap(text)) {
			MutableComponent component = Component.literal(line).withStyle(formats);
			out.add(component.withStyle(style -> style.withItalic(false)));
		}
		return out;
	}
}
