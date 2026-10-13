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
	/** The style insertion on every line of a Requires and Desc block. A reader of the lore finds the block by it. */
	public static final String BLOCK_MARK = "stella:desc";
	public static final String REQUIRES_LABEL = "Requires: ";
	public static final String DESC_LABEL = "Desc: ";
	/** A block shows no more lines than this after the split. */
	public static final int MAX_BLOCK_LINES = 8;

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

	/** A Requires line and a Desc line, each split at the width. The label is gray, the requirement white, the description gray. */
	public static List<Component> block(final String requires, final String desc) {
		List<Component> out = new ArrayList<>(labeled(REQUIRES_LABEL, requires, ChatFormatting.WHITE));
		out.addAll(labeled(DESC_LABEL, desc, ChatFormatting.GRAY));
		return out;
	}

	/** True when the first character of the line carries the block mark. */
	public static boolean isBlockLine(final Component line) {
		String[] found = new String[1];
		line.visit((style, piece) -> {
			if (piece.isEmpty()) {
				return java.util.Optional.empty();
			}
			found[0] = style.getInsertion();
			return java.util.Optional.of(Boolean.TRUE);
		}, net.minecraft.network.chat.Style.EMPTY);
		return BLOCK_MARK.equals(found[0]);
	}

	private static List<Component> labeled(final String label, final String value, final ChatFormatting valueColor) {
		List<Component> out = new ArrayList<>();
		boolean first = true;
		for (String line : wrap(label + value)) {
			MutableComponent component = Component.empty().withStyle(style -> style.withItalic(false).withInsertion(BLOCK_MARK));
			String rest = line;
			if (first) {
				String head = line.startsWith(label) ? label : label.trim();
				component.append(Component.literal(head).withStyle(ChatFormatting.GRAY));
				rest = line.substring(head.length());
				first = false;
			}
			component.append(Component.literal(rest).withStyle(valueColor));
			out.add(component);
		}
		return out;
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
