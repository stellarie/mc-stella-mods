package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import com.stella.equips.mixin.AbstractArrowAccessor;
import com.stella.equips.refine.Refine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/** The Raider's Repeater. */
public final class RaiderRepeater {
	public static final String ID = "raider_repeater";
	public record Tuning(double chance, double chancePlus7, double chancePlus12, int piercePlus, double sprintBonus) {}
	public static final Tuning TUNING = new Tuning(0.25, 0.35, 0.50, 1, 0.30);
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
			if (c.holder().isSprinting()) {
				arrow.addTag(Af.SPRINT_TAG);
			}
			if (c.atLeast(Uniques.PLUS_10)) {
				((AbstractArrowAccessor) arrow).stella$setPierceLevel((byte) Math.min(Byte.MAX_VALUE,
					arrow.getPierceLevel() + TUNING.piercePlus()));
			}
		}

		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && arrow != null && arrow.entityTags().contains(Af.SPRINT_TAG)
				? amount * (1 + (float) TUNING.sprintBonus()) : amount;
		}
	};

	public static boolean retains(final ServerPlayer holder, final ItemStack weapon) {
		return AfChance.roll(holder, ID, Af.tier(Refine.level(weapon), TUNING.chance(), TUNING.chancePlus7(), TUNING.chancePlus12()));
	}
	private RaiderRepeater() {}
	public static void register() {
		Uniques.register(new UniqueDef(ID, "Raider's Repeater", UniqueJob.ARCHER, UniqueKind.CROSSBOW,
			List.of(Identifier.parse("minecraft:pillager")),
			"25% chance a shot leaves the crossbow loaded", "35%", "Bolts pierce 1 more mob", "50%", "Shots while sprinting deal +30%", null, EFFECT));
	}
}
