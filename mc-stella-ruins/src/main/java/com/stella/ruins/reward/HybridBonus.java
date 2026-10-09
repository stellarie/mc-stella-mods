package com.stella.ruins.reward;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * A bonus that needs pieces of two or more sets at once, such as 2 of one set and 2 of another. It holds while
 * every requirement holds, and drops when one piece leaves.
 */
public record HybridBonus(String id, Map<String, Integer> requires, List<AttributeBonus> attributes, @Nullable PeriodicEffect every20Ticks) {
	public HybridBonus {
		requires = Map.copyOf(requires);
		attributes = List.copyOf(attributes);
		if (requires.size() < 2) {
			throw new IllegalArgumentException("A hybrid bonus needs pieces of at least two sets: " + id);
		}
		int total = 0;
		for (int count : requires.values()) {
			if (count < 1 || count > 4) {
				throw new IllegalArgumentException("A hybrid requirement needs 1 to 4 pieces, got " + count);
			}
			total += count;
		}
		// Four slots hold four pieces, so a bonus that needs more can never hold.
		if (total > 4) {
			throw new IllegalArgumentException("A hybrid bonus needs " + total + " pieces, and a body has four slots: " + id);
		}
		Set<Holder<Attribute>> seen = new HashSet<>();
		for (AttributeBonus bonus : attributes) {
			if (!seen.add(bonus.attribute())) {
				throw new IllegalArgumentException("A hybrid bonus lists an attribute twice: " + bonus.attribute().getRegisteredName());
			}
		}
	}
}
