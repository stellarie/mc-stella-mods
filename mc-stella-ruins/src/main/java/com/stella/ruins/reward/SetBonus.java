package com.stella.ruins.reward;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jspecify.annotations.Nullable;

/** What a set gives at a count of worn pieces. A larger count keeps every smaller bonus. */
public record SetBonus(int pieces, List<AttributeBonus> attributes, @Nullable PeriodicEffect every20Ticks) {
	public SetBonus {
		if (pieces < 1 || pieces > 4) {
			throw new IllegalArgumentException("A set bonus needs 1 to 4 pieces, got " + pieces);
		}
		attributes = List.copyOf(attributes);
		// One modifier id covers a whole bonus, so a repeated attribute would overwrite itself.
		Set<Holder<Attribute>> seen = new HashSet<>();
		for (AttributeBonus bonus : attributes) {
			if (!seen.add(bonus.attribute())) {
				throw new IllegalArgumentException("A set bonus lists an attribute twice: " + bonus.attribute().getRegisteredName());
			}
		}
	}

	public SetBonus(final int pieces, final List<AttributeBonus> attributes) {
		this(pieces, attributes, null);
	}
}
