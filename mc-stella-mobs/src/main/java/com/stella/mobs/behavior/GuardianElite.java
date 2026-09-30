package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Guardian;

/**
 * O02 and O03: the elite guardian's movement and the forced elder's aura radius. The speed is one
 * guarded permanent modifier with a stable id, so a reload cannot stack it, and the elder's radius is
 * double the guardian's while staying under vanilla's own 50 block curse.
 */
public final class GuardianElite {
	/** The request's 0.40 on the guardian's own 0.5 base, which is 0.7 against a player's 0.1 walk. */
	public static final double SPEED_BONUS = 0.40;
	public static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "guardian_speed");
	/** O03: the elder's temple radius, double the guardian's 16. */
	public static final double ELDER_AURA_RADIUS = 32.0;

	private GuardianElite() {
	}

	/** Idempotent and elite gated: an ordinary guardian keeps its own 0.5, and a reload stacks nothing. */
	public static void speed(final Mob mob) {
		if (!(mob instanceof Guardian) || !Elite.isElite(mob)) {
			return;
		}

		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed != null && !speed.hasModifier(SPEED_ID)) {
			speed.addPermanentModifier(
				new AttributeModifier(SPEED_ID, SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
			);
		}
	}
}
