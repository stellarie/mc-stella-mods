package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.CombatRole;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.Items;

/**
 * How far a ranged unit shoots, and the follow range that lets it see a target there. A unit whose follow range
 * is shorter than its band never targets a player at the far edge, because the target goals drop a target past
 * that range.
 *
 * <p>A ranged unit is a mob with a ranged or magic role, a mob that holds a crossbow, and a witch. A crossbow
 * piglin is a crossbow holder.
 */
public final class RangedReach {
	public static final double RANGE = 24.0;
	// Evoker fangs reach only 20 blocks.
	public static final double MAGIC_RANGE = 20.0;
	public static final double FOLLOW_RANGE = 32.0;
	private static final Identifier FOLLOW_RANGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "ranged_reach");

	private RangedReach() {
	}

	public static boolean isRangedUnit(final Mob mob) {
		return ReactiveState.get(mob).getRole() != CombatRole.MELEE
			|| CrossbowCadenceGoal.holdsCrossbow(mob)
			|| mob instanceof Witch;
	}

	/** A piglin whose Brain shoots a crossbow from its main hand: the case vanilla's attack range hook reads. */
	public static boolean isCrossbowPiglin(final Mob mob) {
		return mob instanceof AbstractPiglin && mob.getMainHandItem().is(Items.CROSSBOW);
	}

	/**
	 * Widens the follow range of a ranged unit to `FOLLOW_RANGE`, once. The burst widens a skeleton the same way under
	 * its own id. Both read the value and not the base, so whichever runs second finds 32 and adds nothing.
	 */
	public static void install(final Mob mob) {
		if (!isRangedUnit(mob)) {
			return;
		}

		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null && range.getModifier(FOLLOW_RANGE_ID) == null && range.getValue() < FOLLOW_RANGE) {
			range.addPermanentModifier(
				new AttributeModifier(FOLLOW_RANGE_ID, FOLLOW_RANGE - range.getValue(), AttributeModifier.Operation.ADD_VALUE)
			);
		}
	}
}
