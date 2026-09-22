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
	public static final int KEEP_DISTANCE_PRIORITY = 0;
	public static final int SHIELD_PRIORITY = 1;
	public static final int RETREAT_PRIORITY = 2;
	public static final int DEFEND_PRIORITY = 3;
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
		RoleSpec spec = GearConfigStore.get().roleFor(entityType);
		state.setRoleSpec(spec != null ? spec : RoleSpec.DEFAULT);

		if (!RETREAT_EXEMPT.contains(mob.getType())) {
			mob.getGoalSelector().addGoal(RETREAT_PRIORITY, new RetreatGoal<>(monster));
		}

		mob.getGoalSelector().addGoal(DEFEND_PRIORITY, new DefendAllyGoal<>(monster));

		if (state.getRole() != CombatRole.MELEE) {
			if (supports(mob, state.getRole())) {
				mob.getGoalSelector().addGoal(KEEP_DISTANCE_PRIORITY, new KeepDistanceGoal<>(monster));
			} else {
				StellaMobs.LOGGER.warn(
					"Role {} cannot be performed by {}, which holds no bow and no crossbow and casts no spell; no distance goal was added",
					state.getRole(),
					entityType
				);
			}
		}

		if (mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			mob.getGoalSelector().addGoal(SHIELD_PRIORITY, new ShieldUseGoal<>(monster));
		}
	}

	private static boolean supports(final Mob mob, final CombatRole role) {
		return switch (role) {
			case RANGED -> isRangedWeapon(mob.getMainHandItem()) || isRangedWeapon(mob.getOffhandItem());
			case MAGIC -> mob instanceof SpellcasterIllager;
			case MELEE -> true;
		};
	}

	private static boolean isRangedWeapon(final ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
	}
}
