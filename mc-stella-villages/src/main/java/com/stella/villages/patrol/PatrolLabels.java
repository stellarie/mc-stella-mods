package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

/** A patrol member wears its role over its head: green while the village is calm, red while a player provokes it. */
public final class PatrolLabels {
	// Literal, because the village mod ships no language file and a vanilla client must read it too.
	public static final String HEAD_LABEL = "Duke";
	public static final String MEMBER_LABEL = "Guard";
	public static final ChatFormatting CALM = ChatFormatting.GREEN;
	public static final ChatFormatting PROVOKED = ChatFormatting.RED;

	private PatrolLabels() {
	}

	public static Component label(final PatrolState.Role role, final boolean provoked) {
		String text = role == PatrolState.Role.HEAD ? HEAD_LABEL : role == PatrolState.Role.VILLAGE_WITCH ? VillageWitchKit.LABEL : MEMBER_LABEL;
		return Component.literal(text).withStyle(provoked ? PROVOKED : CALM);
	}

	/** Puts the label on a member, and logs only when it changes. */
	static void apply(final Mob member, final boolean provoked) {
		PatrolState.Role role = PatrolState.role(member);
		if (role == null) {
			return;
		}

		Component wanted = label(role, provoked);
		if (wanted.equals(member.getCustomName()) && member.isCustomNameVisible()) {
			return;
		}

		member.setCustomName(wanted);
		member.setCustomNameVisible(true);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=patrol_label entity={} role={} provoked={}",
			McStellaVillages.MOD_ID, member.getUUID(), role, provoked
		);
	}
}
