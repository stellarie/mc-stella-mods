package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RoleSpec;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BehaviorInstaller {
	// The retreat must beat every vanilla MOVE goal, and no monster claims MOVE at 0.
	public static final int RETREAT_PRIORITY = 0;
	// A tie never preempts, and the vanilla melee goals sit at priority 2 and above.
	public static final int SHIELD_PRIORITY = 1;
	public static final int KEEP_DISTANCE_PRIORITY = 2;
	public static final int STANDOFF_PRIORITY = 3;
	public static final int DEFEND_PRIORITY = 4;
	// R25: the pearl throw claims no flags, so it sits above the shared block and can tie nothing.
	public static final int PEARL_PRIORITY = 5;
	// The witch block starts above the shared block. Ranks 0 to 4 are retreat,
	// shield, distance, standoff, and ally defense, and the witch does not touch
	// them. A tie never preempts, so the witch goals claim no flags at all.
	public static final int WITCH_BLOCK_START = 5;
	public static final int WITCH_SELF_HEAL_PRIORITY = WITCH_BLOCK_START;
	public static final int WITCH_SUPPORT_PRIORITY = WITCH_BLOCK_START + 1;
	public static final int WITCH_OFFENSE_PRIORITY = WITCH_BLOCK_START + 2;
	// Rank 8 stays free. The aura sits at the top of the block, as planned.
	public static final int WITCH_AURA_PRIORITY = WITCH_BLOCK_START + 4;
	// A blaze has no hands and still attacks from range, so the item checks cannot speak for it.
	private static final Set<EntityType<?>> INNATE_RANGED = Set.of(EntityTypes.BLAZE);
	private static final Set<EntityType<?>> RETREAT_EXEMPT = Set.of(
		EntityTypes.WARDEN, EntityTypes.WITHER, EntityTypes.ELDER_GUARDIAN, EntityTypes.RAVAGER
	);

	private BehaviorInstaller() {
	}

	/**
	 * The one clause a boss promoted by hand owes the installer: an off hand that blocks needs its goal.
	 * A boss rolled by its own path is installed after the kit, so for it this is already done.
	 */
	public static void installShield(final Mob mob) {
		if (!(mob instanceof Monster monster) || !mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return;
		}

		if (hasShieldGoal(mob)) {
			return;
		}

		mob.getGoalSelector().addGoal(SHIELD_PRIORITY, new ShieldUseGoal<>(monster));
	}

	private static boolean hasShieldGoal(final Mob mob) {
		return mob.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof ShieldUseGoal);
	}

	/** R21: an elite zombie that is not a summon brings its own reinforcements. Idempotent. */
	public static void installReinforcements(final Mob mob) {
		if (!(mob instanceof Zombie zombie) || EliteState.isSummoned(mob) || !Elite.isElite(zombie)) {
			return;
		}

		if (mob.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof ReinforcementGoal)) {
			return;
		}

		mob.getGoalSelector().addGoal(ReinforcementGoal.PRIORITY, new ReinforcementGoal(zombie));
	}

	public static void install(final Mob mob) {
		if (!(mob instanceof Monster monster)) {
			return;
		}

		ReactiveState state = ReactiveState.get(mob);
		if (state.isInstalled()) {
			return;
		}

		state.setInstalled(true);

		Identifier entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		state.setRoleSpec(resolveRole(mob, state, entityType));

		if (!RETREAT_EXEMPT.contains(mob.getType())) {
			mob.getGoalSelector().addGoal(RETREAT_PRIORITY, new RetreatGoal<>(monster));
		}

		mob.getGoalSelector().addGoal(DEFEND_PRIORITY, new DefendAllyGoal<>(monster));

		if (state.getRole() != CombatRole.MELEE) {
			if (supports(mob, state.getRole())) {
				mob.getGoalSelector().addGoal(KEEP_DISTANCE_PRIORITY, new KeepDistanceGoal<>(monster));
				mob.getGoalSelector().addGoal(STANDOFF_PRIORITY, new HoldStandoffGoal<>(monster));
			} else {
				StellaMobs.LOGGER.warn(
					"Role {} cannot be performed by {}, which holds no bow, no crossbow, and no trident, and casts no spell; no distance goal was added",
					state.getRole(),
					entityType
				);
			}
		}

		installShield(mob);

		// R25: armed at install, and the goal's own guard reads the elite flag, which is already set
		// for a mob that rolled elite before this install runs.
		mob.getGoalSelector().addGoal(PEARL_PRIORITY, new PearlThrowGoal(mob));

		if (mob instanceof Witch witch) {
			installWitch(witch);
		}

		BossAbilities.install(mob);
	}

	/**
	 * The witch's four goals claim no flags, so they can neither starve nor be starved by vanilla's
	 * own witch goals, which hold MOVE and LOOK from rank 1 to 3.
	 */
	private static void installWitch(final Witch witch) {
		witch.getGoalSelector().addGoal(WITCH_SELF_HEAL_PRIORITY, new WitchSelfHealGoal<>(witch));
		witch.getGoalSelector().addGoal(WITCH_SUPPORT_PRIORITY, new WitchSupportHealGoal<>(witch));
		witch.getGoalSelector().addGoal(WITCH_OFFENSE_PRIORITY, new WitchOffensiveThrowGoal<>(witch));
		witch.getGoalSelector().addGoal(WITCH_AURA_PRIORITY, new WitchEliteAuraGoal<>(witch));
	}

	private static RoleSpec resolveRole(final Mob mob, final ReactiveState state, final Identifier entityType) {
		if (mob instanceof SpellcasterIllager) {
			return RoleSpec.of(CombatRole.MAGIC);
		}

		RoleSpec configured = GearConfigStore.get().roleFor(entityType);
		ItemStack mainHand = mob.getMainHandItem();

		if (state.isMainHandRolled() && !mainHand.isEmpty()) {
			CombatRole rolled = isRangedWeapon(mainHand) ? CombatRole.RANGED : CombatRole.MELEE;
			return configured != null && configured.role() == rolled ? configured.withRole(rolled) : RoleSpec.of(rolled);
		}

		if (configured != null) {
			return configured;
		}

		return isInnatelyRanged(mob) || isRangedWeapon(mainHand) || isRangedWeapon(mob.getOffhandItem())
			? RoleSpec.of(CombatRole.RANGED)
			: RoleSpec.of(CombatRole.MELEE);
	}

	private static boolean supports(final Mob mob, final CombatRole role) {
		return switch (role) {
			case RANGED -> isInnatelyRanged(mob)
				|| isRangedWeapon(mob.getMainHandItem())
				|| isRangedWeapon(mob.getOffhandItem());
			case MAGIC -> mob instanceof SpellcasterIllager;
			case MELEE -> true;
		};
	}

	private static boolean isInnatelyRanged(final Mob mob) {
		return INNATE_RANGED.contains(mob.getType());
	}

	private static boolean isRangedWeapon(final ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT);
	}
}
