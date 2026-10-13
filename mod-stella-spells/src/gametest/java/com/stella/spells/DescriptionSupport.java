package com.stella.spells;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Reads the tooltip of a stack the way a client does, with the shipped English text installed. */
final class DescriptionSupport {
	static final int LIMIT = 24;
	private static final String LANG_PATH = "/assets/mod-stella-spells/lang/en_us.json";

	private DescriptionSupport() {
	}

	/**
	 * The text lines under the name of a stack, as the client builds them for the viewer: the item tooltip, then the
	 * mod's own decoration. The vanilla attribute lines are hidden, because they are not descriptions.
	 */
	static List<Component> lines(final GameTestHelper helper, final @Nullable Player viewer, final ItemStack stack) {
		ItemStack shown = stack.copy();
		shown.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
		List<Component> all = withShippedText(() -> {
			List<Component> tooltip = shown.getTooltipLines(Item.TooltipContext.of(helper.getLevel().registryAccess()), viewer, TooltipFlag.NORMAL);
			SpellTooltip.decorate(viewer, shown, tooltip);
			return tooltip;
		});
		return all.subList(1, all.size());
	}

	/** The rendered text of the lines. The shipped text is installed while the lines resolve. */
	static List<String> text(final List<Component> lines) {
		return withShippedText(() -> lines.stream().map(Component::getString).toList());
	}

	/** The translatable text of a line: the line itself, or the value after the Requires or Desc label. */
	static TranslatableContents translatable(final Component line) {
		if (line.getContents() instanceof TranslatableContents contents) {
			return contents;
		}
		for (Component part : line.getSiblings()) {
			if (part.getContents() instanceof TranslatableContents contents) {
				return contents;
			}
		}
		throw new IllegalStateException("no translatable text in " + line);
	}

	static List<String> tooLong(final List<String> lines) {
		return tooLong("", lines);
	}

	/** The lines over the limit, each named with the owner it came from. */
	static List<String> tooLong(final String owner, final List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String line : lines) {
			if (line.length() > LIMIT) {
				out.add(owner + " " + line.length() + ": " + line);
			}
		}
		return out;
	}

	/**
	 * What is wrong with the Requires and Desc blocks in these lines. A block starts at a Requires line, holds a Desc
	 * line after at most three Requires lines, and shows at most {@link DescriptionLines#MAX_BLOCK_LINES} lines.
	 */
	static List<String> blockProblems(final String owner, final List<Component> lines) {
		List<String> texts = text(lines);
		List<String> problems = new ArrayList<>();
		List<String> run = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			if (!DescriptionLines.isBlockLine(lines.get(i))) {
				checkBlock(owner, run, problems);
				run = new ArrayList<>();
				continue;
			}
			if (texts.get(i).startsWith("Requires:")) {
				checkBlock(owner, run, problems);
				run = new ArrayList<>();
			}
			run.add(texts.get(i));
		}
		checkBlock(owner, run, problems);
		return problems;
	}

	/** True when at least one line belongs to a Requires and Desc block. */
	static boolean hasBlock(final List<Component> lines) {
		return lines.stream().anyMatch(DescriptionLines::isBlockLine);
	}

	private static void checkBlock(final String owner, final List<String> block, final List<String> problems) {
		if (block.isEmpty()) {
			return;
		}
		int desc = -1;
		for (int i = 0; i < block.size() && desc < 0; i++) {
			if (block.get(i).startsWith("Desc:")) {
				desc = i;
			}
		}
		if (!block.get(0).startsWith("Requires:")) {
			problems.add(owner + " block starts with: " + block);
		} else if (desc < 1) {
			problems.add(owner + " has no Desc line after Requires: " + block);
		} else if (desc > 3) {
			problems.add(owner + " Requires runs over 3 lines: " + block);
		}
		if (block.size() > DescriptionLines.MAX_BLOCK_LINES) {
			problems.add(owner + " block of " + block.size() + " lines: " + block);
		}
	}

	static <T> T withShippedText(final Supplier<T> action) {
		Language previous = Language.getInstance();
		try {
			Language.inject(shipped());
			return action.get();
		} finally {
			Language.inject(previous);
		}
	}

	private static Language shipped() {
		Map<String, String> entries = new HashMap<>();
		try (InputStream stream = DescriptionSupport.class.getResourceAsStream(LANG_PATH)) {
			if (stream == null) {
				throw new IllegalStateException("missing resource " + LANG_PATH);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				entries.put(entry.getKey(), entry.getValue().getAsString());
			}
		} catch (IOException error) {
			throw new IllegalStateException("could not read " + LANG_PATH, error);
		}
		Language vanilla = Language.DEFAULT_INSTANCE;
		return new Language() {
			@Override
			public String getOrDefault(final String id, final String fallback) {
				String ours = entries.get(id);
				return ours != null ? ours : vanilla.getOrDefault(id, fallback);
			}

			@Override
			public boolean has(final String id) {
				return entries.containsKey(id) || vanilla.has(id);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return vanilla.isDefaultRightToLeft();
			}

			@Override
			public FormattedCharSequence getVisualOrder(final FormattedText text) {
				return vanilla.getVisualOrder(text);
			}
		};
	}
}
