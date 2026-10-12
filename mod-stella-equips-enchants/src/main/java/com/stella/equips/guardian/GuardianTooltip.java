package com.stella.equips.guardian;

import com.stella.equips.refine.Refine;
import com.stella.equips.tooltip.DescriptionLines;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * The tooltip lines of the guardian gear. Each item states its effect, then the bonus that refining adds.
 * A refine line is gold at or above its level and dark gray below it.
 */
public final class GuardianTooltip {
	private static final int PARRY_LEVEL = Refine.MILESTONE_FIRST;

	private GuardianTooltip() {
	}

	/** The lines of a guardian item stack, or an empty list for any other stack. */
	public static List<Component> lines(final ItemStack stack) {
		if (BoneShield.is(stack)) {
			return shield(Refine.level(stack));
		}
		if (SkeletonCrown.is(stack)) {
			return crown(Refine.level(stack));
		}
		return List.of();
	}

	public static void append(final ItemStack stack, final Consumer<Component> builder) {
		for (Component line : lines(stack)) {
			DescriptionLines.add(builder, line);
		}
	}

	private static List<Component> shield(final int refine) {
		List<Component> lines = new ArrayList<>();
		lines.add(line("Each blow it blocks adds a stack of +" + percent(BoneShield.BONUS_PER_STACK) + " melee damage, up to " + BoneShield.MAX_STACKS + ".",
			ChatFormatting.GRAY));
		lines.add(line("Your next melee hit spends every stack and shoves the mobs in front of you.", ChatFormatting.GRAY));
		// Once refined to the level, the Parry line of the refine lore says the same with the real share.
		if (refine < PARRY_LEVEL) {
			lines.add(line("+" + PARRY_LEVEL + ": Parry sends part of a blocked melee hit back at the attacker.", ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	private static List<Component> crown(final int refine) {
		List<Component> lines = new ArrayList<>();
		lines.add(line("Arrows you shoot deal +" + percent(SkeletonCrown.BASE_BONUS) + " damage.", ChatFormatting.GRAY));
		for (int tier : SkeletonCrown.TIERS) {
			lines.add(line("+" + tier + ": arrows deal +" + percent(SkeletonCrown.bonusAt(tier)) + " damage.",
				refine >= tier ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	private static String percent(final double share) {
		return Math.round(share * 100.0) + "%";
	}

	private static MutableComponent line(final String text, final ChatFormatting color) {
		return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
	}
}
