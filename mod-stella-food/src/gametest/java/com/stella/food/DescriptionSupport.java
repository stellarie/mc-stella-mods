package com.stella.food;

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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Reads the tooltip of a stack the way a client does, with the shipped English text installed. */
final class DescriptionSupport {
	static final int LIMIT = 24;
	private static final String LANG_PATH = "/assets/mod-stella-food/lang/en_us.json";

	private DescriptionSupport() {
	}

	/** The text lines under the name of a stack. The vanilla attribute lines are hidden, because they are not descriptions. */
	static List<Component> lines(final GameTestHelper helper, final ItemStack stack) {
		ItemStack shown = stack.copy();
		shown.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
		List<Component> all = withShippedText(() -> shown.getTooltipLines(
			Item.TooltipContext.of(helper.getLevel().registryAccess()), null, TooltipFlag.NORMAL));
		return all.subList(1, all.size());
	}

	/** The rendered text of the lines. The shipped text is installed while the lines resolve. */
	static List<String> text(final List<Component> lines) {
		return withShippedText(() -> lines.stream().map(Component::getString).toList());
	}

	static List<String> tooLong(final List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String line : lines) {
			if (line.length() > LIMIT) {
				out.add(line.length() + ": " + line);
			}
		}
		return out;
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
