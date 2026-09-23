package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.gear.GearRoller;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.gear.TierRoller;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The shared elite marker: one permanent scale modifier, one durable flag with the rolled tier, and
 * one health multiplier. The roll runs at entity load rather than in the behavior installer, because
 * that installer returns before anything that is not a Monster, and a ghast is not one.
 */
public final class Elite {
	public static final double SCALE_BONUS = 0.2;
	public static final double HEALTH_MULTIPLIER = 2.0;
	public static final Identifier SCALE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "elite_scale");
	/** The elite kit roll: diamond is four times as likely as netherite, per the tier weights. */
	public static final List<GearTier> KIT_TIERS = List.of(GearTier.DIAMOND, GearTier.NETHERITE);
	public static final float KIT_DROP_CHANCE = 0.10F;
	private static final Set<EntityType<?>> BOSS_TYPES = Set.of(
		EntityTypes.WARDEN, EntityTypes.WITHER, EntityTypes.ELDER_GUARDIAN, EntityTypes.ENDER_DRAGON
	);

	private Elite() {
	}

	public static boolean canBeElite(final Mob mob) {
		return mob instanceof Enemy && !BOSS_TYPES.contains(mob.getType());
	}

	public static boolean roll(final RandomSource random, final double chance) {
		return chance > 0.0 && random.nextFloat() < (float) chance;
	}

	public static GearTier pickTier(final RandomSource random) {
		return TierRoller.pick(KIT_TIERS, TierRoller.totalWeight(KIT_TIERS), random);
	}

	/**
	 * Marks a mob elite exactly once: the guarded scale marker, the durable flag with its tier, and
	 * the health derived from the base it already carries. Returns true when this call did the marking.
	 */
	public static boolean mark(final Mob mob, final GearTier tier, final double healthMultiplier) {
		if (EliteState.isElite(mob)) {
			return false;
		}

		AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
		if (scale != null && !scale.hasModifier(SCALE_ID)) {
			scale.addPermanentModifier(
				new AttributeModifier(SCALE_ID, SCALE_BONUS, AttributeModifier.Operation.ADD_VALUE)
			);
		}

		EliteState.markElite(mob, tier);
		// R21: an elite zombie brings reinforcements, and the mark is the moment it becomes one.
		BehaviorInstaller.installReinforcements(mob);

		// R22: the elite skeleton's kit goes on here, before the installer runs, so the shield it writes
		// is in the off hand when the installer decides whether to add ShieldUseGoal. A boss is skipped:
		// its sword would replace the boss kit's bow and close the fan.
		if (mob.getType() == net.minecraft.world.entity.EntityTypes.SKELETON
			&& !EliteState.isSummoned(mob)
			&& !EliteState.isBoss(mob)
			&& !SkeletonEliteKit.applied(mob)) {
			SkeletonEliteKit.apply(mob);
		}

		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			// The bare base times the multiplier. R12's area scaling rides its own modifier, so it is
			// deliberately not folded in here: an elite's doubled health is the documented number.
			double boosted = health.getBaseValue() * healthMultiplier;
			health.setBaseValue(boosted);
			mob.setHealth((float) boosted);
		}

		return true;
	}

	public static boolean isElite(final Mob mob) {
		return EliteState.isElite(mob);
	}

	public static GearTier kitTier(final Mob mob) {
		return EliteState.get(mob).eliteTier().orElse(GearTier.LEATHER);
	}

	/** The escort tier: one explicit grade below the elite's, never the ordinal predecessor gold. */
	public static GearTier escortTier(final GearTier eliteTier) {
		return eliteTier == GearTier.NETHERITE ? GearTier.DIAMOND : GearTier.IRON;
	}

	/**
	 * The spawn-time package: the chance roll, the marker, the kit, and the escort squad queued for the
	 * end of the tick. Runs for a fresh spawn only, so a summon cannot roll itself elite and recurse.
	 */
	public static void rollAtSpawn(final Mob mob, final ServerLevel level, final double chance, final boolean applyKit) {
		if (EliteState.isSummoned(mob) || isElite(mob) || !canBeElite(mob) || !roll(mob.getRandom(), chance)) {
			return;
		}

		GearTier tier = pickTier(mob.getRandom());
		mark(mob, tier, HEALTH_MULTIPLIER);
		EliteState.setPearlsLeft(mob, PearlThrowGoal.PEARL_COUNT);

		// The kit gate is the same usability gate as the ordinary roll: a mob that cannot equip and use
		// gear takes only the marker, the health and the ability. This is what keeps an elite ghast bare.
		if (applyKit && GearRoller.canRoll(mob)) {
			GearRoller.rollSet(
				mob, tier, KIT_DROP_CHANCE, mob.getRandom(), level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			);
		}

		EscortSquad.queue(mob, tier);

		// R23: an elite spider's sword skeleton is guaranteed rather than a chance roll, and it rides the
		// shipped seating path, so the create-and-flag recipe is the one riders already use.
		if (mob.getType() == EntityTypes.SPIDER) {
			RiderSquad.queueArmed(mob, EntityTypes.SKELETON);
		}
	}
}
