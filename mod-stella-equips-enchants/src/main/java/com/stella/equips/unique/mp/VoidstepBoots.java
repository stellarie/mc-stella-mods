package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;

/** Voidstep Boots evade a hit and empower the next spell at +15. */
public final class VoidstepBoots {
	public static final String ID = "voidstep_boots";

	public record Tuning(double baseChance, double plus7Chance, int range, int baseCooldownTicks,
		int plus12CooldownTicks, double plus15Bonus) {
	}

	public static final Tuning TUNING = new Tuning(0.15, 0.20, 8, 400, 240, 0.50);
	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
	private static final int[] HEIGHTS = {0, -1, -2, 1, 2};
	private static final Map<UUID, Long> READY_AT = new HashMap<>();
	private static final Map<UUID, Boolean> NEXT_SPELL = new HashMap<>();

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float incoming(final UniqueContext c, final DamageSource source, final float amount) {
			if (amount <= 0.0F || c.holder().level().getServer().getTickCount() < READY_AT.getOrDefault(c.holder().getUUID(), 0L)
				|| !MpRolls.chance(ID, c.holder(), chanceAt(c.refine())) || !escape(c.holder())) {
				return amount;
			}
			READY_AT.put(c.holder().getUUID(), (long) c.holder().level().getServer().getTickCount() + cooldownAt(c.refine()));
			if (c.atLeast(Uniques.PLUS_15)) {
				NEXT_SPELL.put(c.holder().getUUID(), true);
			}
			return c.atLeast(Uniques.PLUS_10) ? 0.0F : amount;
		}

		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && NEXT_SPELL.remove(c.holder().getUUID()) != null
				? (float) (amount * (1.0 + TUNING.plus15Bonus())) : amount;
		}
	};

	private VoidstepBoots() {
	}

	private static boolean escape(final ServerPlayer holder) {
		ServerLevel level = holder.level();
		Vec3 from = holder.position();
		for (int radius = TUNING.range() - 1; radius >= 1; radius--) {
			for (int[] direction : DIRECTIONS) {
				for (int dy : HEIGHTS) {
					int dx = direction[0] * radius;
					int dz = direction[1] * radius;
					if (dx * dx + dz * dz + dy * dy > TUNING.range() * TUNING.range()) {
						continue;
					}
					BlockPos feet = BlockPos.containing(from.x + dx, from.y + dy, from.z + dz);
					if (!level.isLoaded(feet) || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
						continue;
					}
					Vec3 to = Vec3.atBottomCenterOf(feet);
					if (from.distanceToSqr(to) > TUNING.range() * TUNING.range()
						|| !level.noCollision(holder, holder.getBoundingBox().move(to.subtract(from)))) {
						continue;
					}
					if (holder.teleportTo(level, to.x, to.y, to.z, Set.of(), holder.getYRot(), holder.getXRot(), true)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static double chanceAt(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Chance() : TUNING.baseChance();
	}

	public static int cooldownAt(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.plus12CooldownTicks() : TUNING.baseCooldownTicks();
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY_AT.clear();
			NEXT_SPELL.clear();
		});
		Uniques.register(new UniqueDef(ID, "Voidstep Boots", UniqueJob.MAGE, UniqueKind.BOOTS, List.of(Identifier.parse("minecraft:enderman")),
			"When hit, " + MpKit.percent(TUNING.baseChance()) + " chance to teleport up to " + TUNING.range() + " blocks away. Cooldown " + MpKit.seconds(TUNING.baseCooldownTicks()),
			MpKit.percent(TUNING.plus7Chance()),
			"The triggering hit deals no damage",
			"Cooldown " + MpKit.seconds(TUNING.plus12CooldownTicks()),
			"After a teleport, your next spell hit deals +" + MpKit.percent(TUNING.plus15Bonus()),
			EquipmentAssets.NETHERITE, EFFECT));
	}
}
