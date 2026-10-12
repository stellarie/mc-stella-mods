package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Splits a tooltip description into lines of at most {@link #MAX_CHARS} characters.
 * A line breaks at a space. A word longer than the limit keeps a line of its own.
 * Each character keeps its style, so a colored word stays colored after the split.
 */
public final class DescriptionLines {
	public static final int MAX_CHARS = 24;

	private DescriptionLines() {
	}

	/** Adds the wrapped lines of the text to the list. */
	public static void add(final List<Component> out, final Component text) {
		out.addAll(wrap(text));
	}

	public static List<Component> wrap(final Component text) {
		StringBuilder plain = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		text.visit((style, piece) -> {
			plain.append(piece);
			for (int i = 0; i < piece.length(); i++) {
				styles.add(style);
			}
			return Optional.empty();
		}, Style.EMPTY);
		List<Component> lines = new ArrayList<>();
		int offset = 0;
		for (String paragraph : plain.toString().split("\n", -1)) {
			wrapParagraph(paragraph, styles.subList(offset, offset + paragraph.length()), lines);
			offset += paragraph.length() + 1;
		}
		return lines;
	}

	private static void wrapParagraph(final String paragraph, final List<Style> styles, final List<Component> lines) {
		List<int[]> words = new ArrayList<>();
		int start = -1;
		for (int i = 0; i <= paragraph.length(); i++) {
			boolean space = i == paragraph.length() || paragraph.charAt(i) == ' ';
			if (!space && start < 0) {
				start = i;
			} else if (space && start >= 0) {
				words.add(new int[] {start, i});
				start = -1;
			}
		}
		if (words.isEmpty()) {
			lines.add(Component.empty());
			return;
		}
		List<int[]> line = new ArrayList<>();
		int length = 0;
		for (int[] word : words) {
			int size = word[1] - word[0];
			if (!line.isEmpty() && length + 1 + size > MAX_CHARS) {
				lines.add(build(paragraph, styles, line));
				line = new ArrayList<>();
				length = 0;
			}
			length += (line.isEmpty() ? 0 : 1) + size;
			line.add(word);
		}
		lines.add(build(paragraph, styles, line));
	}

	private static Component build(final String paragraph, final List<Style> styles, final List<int[]> words) {
		StringBuilder text = new StringBuilder();
		List<Style> lineStyles = new ArrayList<>();
		for (int w = 0; w < words.size(); w++) {
			int[] word = words.get(w);
			if (w > 0) {
				text.append(' ');
				lineStyles.add(styles.get(words.get(w - 1)[1]));
			}
			text.append(paragraph, word[0], word[1]);
			lineStyles.addAll(styles.subList(word[0], word[1]));
		}
		Style first = lineStyles.get(0);
		if (lineStyles.stream().allMatch(first::equals)) {
			return Component.literal(text.toString()).withStyle(first);
		}
		MutableComponent line = Component.empty();
		int from = 0;
		for (int i = 1; i <= text.length(); i++) {
			if (i == text.length() || !lineStyles.get(i).equals(lineStyles.get(from))) {
				line.append(Component.literal(text.substring(from, i)).withStyle(lineStyles.get(from)));
				from = i;
			}
		}
		return line;
	}
}
