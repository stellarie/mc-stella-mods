package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueFields;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniqueScheduler;
import com.stella.equips.unique.Uniques;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;

/** Endflame Robe leaves a breath field after a spell hit. */
public final class EndflameRobe {
	public static final String ID = "endflame_robe";

	public record Tuning(double baseChance, double plus7Chance, double baseRadius, double plus12Radius,
		int baseTicks, int plus10Ticks, double perSecondShare, double plus15Bonus) {
	}

	public static final Tuning TUNING = new Tuning(0.20, 0.30, 2.0, 3.0, 80, 120, 0.05, 0.25);

	private record Mark(ServerLevel level, Vec3 center, double radius, long expires) {
	}

	private static final Map<UUID, List<Mark>> FIELDS = new HashMap<>();

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && inside(c.holder(), target)
				? (float) (amount * (1.0 + TUNING.plus15Bonus())) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float dealt, final boolean killed) {
			if (!MpRolls.chance(ID, c.holder(), chanceAt(c.refine()))) {
				return;
			}
			ServerPlayer holder = c.holder();
			Vec3 center = target.position();
			double radius = radiusAt(c.refine());
			int ticks = ticksAt(c.refine());
			UniqueFields.breath(holder.level(), center, radius, ticks, (float) (dealt * TUNING.perSecondShare()), holder);
			Mark mark = new Mark(holder.level(), center, radius, holder.level().getServer().getTickCount() + ticks);
			FIELDS.computeIfAbsent(holder.getUUID(), key -> new ArrayList<>()).add(mark);
			UniqueScheduler.after(holder.level().getServer(), ticks, () -> {
				List<Mark> marks = FIELDS.get(holder.getUUID());
				if (marks != null) {
					marks.remove(mark);
					if (marks.isEmpty()) {
						FIELDS.remove(holder.getUUID());
					}
				}
			});
		}
	};

	private EndflameRobe() {
	}

	private static boolean inside(final ServerPlayer holder, final LivingEntity target) {
		if (target.level() != holder.level()) {
			return false;
		}
		List<Mark> marks = FIELDS.get(holder.getUUID());
		if (marks == null) {
			return false;
		}
		long now = holder.level().getServer().getTickCount();
		marks.removeIf(mark -> mark.expires() <= now || mark.level() != holder.level());
		if (marks.isEmpty()) {
			FIELDS.remove(holder.getUUID());
			return false;
		}
		return marks.stream().anyMatch(mark -> target.getBoundingBox().distanceToSqr(mark.center()) <= mark.radius() * mark.radius());
	}

	public static double chanceAt(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Chance() : TUNING.baseChance();
	}

	public static double radiusAt(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.plus12Radius() : TUNING.baseRadius();
	}

	public static int ticksAt(final int refine) {
		return refine >= Uniques.PLUS_10 ? TUNING.plus10Ticks() : TUNING.baseTicks();
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIELDS.clear());
		Uniques.register(new UniqueDef(ID, "Endflame Robe", UniqueJob.MAGE, UniqueKind.CHESTPLATE,
			List.of(Identifier.parse("minecraft:ender_dragon")),
			MpKit.percent(TUNING.baseChance()) + " chance a spell hit leaves a breath field: radius " + (int) TUNING.baseRadius() + ", "
				+ MpKit.seconds(TUNING.baseTicks()) + ", " + MpKit.percent(TUNING.perSecondShare()) + " of the hit each second",
			MpKit.percent(TUNING.plus7Chance()),
			"The field lasts " + MpKit.seconds(TUNING.plus10Ticks()),
			"Radius " + (int) TUNING.plus12Radius(),
			"Spell hits on a target inside a field deal +" + MpKit.percent(TUNING.plus15Bonus()),
			EquipmentAssets.NETHERITE, EFFECT));
	}
}
