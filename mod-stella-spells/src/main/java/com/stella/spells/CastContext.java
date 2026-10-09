package com.stella.spells;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The ally a cast resolved for an {@link TargetMode#ALLY} book, handed to the effect without widening
 * {@link SpellEffect}, whose target is a {@code Mob}. A cast sets it for the effect it is about to run and
 * clears it after, so a nested cast never reads another's ally.
 */
public final class CastContext {
	private static final ThreadLocal<LivingEntity> ALLY = new ThreadLocal<>();
	private static final ThreadLocal<LivingEntity> IMBUED_VICTIM = new ThreadLocal<>();

	private CastContext() {
	}

	/** The ally this cast resolved: the one the caster looks at, else the caster itself. */
	public static @Nullable LivingEntity ally() {
		return ALLY.get();
	}

	/** The victim of an imbued cast running now, or null for a book cast. An imbued cast needs no bow in hand and no ammunition. */
	public static @Nullable LivingEntity imbuedVictim() {
		return IMBUED_VICTIM.get();
	}

	static void setImbuedVictim(final @Nullable LivingEntity victim) {
		if (victim == null) {
			IMBUED_VICTIM.remove();
		} else {
			IMBUED_VICTIM.set(victim);
		}
	}

	static void setAlly(final @Nullable LivingEntity ally) {
		if (ally == null) {
			ALLY.remove();
		} else {
			ALLY.set(ally);
		}
	}
}
