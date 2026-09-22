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
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
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
	private static final Set<EntityType<?>> RETREAT_EXEMPT = Set.of(
		EntityTypes.WARDEN, EntityTypes.WITHER, EntityTypes.ELDER_GUARDIAN, EntityTypes.RAVAGER
	);

	private BehaviorInstaller() {
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

		if (mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			mob.getGoalSelector().addGoal(SHIELD_PRIORITY, new ShieldUseGoal<>(monster));
		}
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

		return isRangedWeapon(mainHand) || isRangedWeapon(mob.getOffhandItem())
			? RoleSpec.of(CombatRole.RANGED)
			: RoleSpec.of(CombatRole.MELEE);
	}

	private static boolean supports(final Mob mob, final CombatRole role) {
		return switch (role) {
			case RANGED -> isRangedWeapon(mob.getMainHandItem()) || isRangedWeapon(mob.getOffhandItem());
			case MAGIC -> mob instanceof SpellcasterIllager;
			case MELEE -> true;
		};
	}

	private static boolean isRangedWeapon(final ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT);
	}
}
