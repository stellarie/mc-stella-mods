package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.RoleSpec;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;

/**
 * The combat modes of a servant that another mod drives, such as the summoned skeleton. The mod that owns the servant
 * calls the function under {@link #SHARE_KEY} with the role its held weapon asks for, and needs no class of this mod.
 *
 * <p>A servant keeps {@link StellaMobs#NO_BEHAVIOR_TAG}, so the hostile install skips it. This class adds the
 * distance goals of the role by hand, and the servant's own melee goal keeps closing in for the MELEE role. The goals
 * read the servant's {@code LivingEntity} target, so the servant never turns on a player.
 */
public final class ServantRoles {
	/** The ObjectShare key of a {@code BiConsumer<Mob, String>}: the servant, then MELEE, RANGED or MAGIC. */
	public static final String SHARE_KEY = "mod-stella-mobs:servant-role";
	/** Above the servant's own shoot goal at rank 2, because a tie never preempts. */
	public static final int KEEP_DISTANCE_PRIORITY = 1;
	public static final int STANDOFF_PRIORITY = BehaviorInstaller.STANDOFF_PRIORITY;

	private ServantRoles() {
	}

	public static void register() {
		FabricLoader.getInstance().getObjectShare().put(SHARE_KEY, (BiConsumer<Mob, String>) ServantRoles::apply);
	}

	/**
	 * Replaces the role goals of the servant with those of the named role.
	 * Call it outside the goal selector's tick, because it adds and removes goals.
	 */
	public static void apply(final Mob mob, final String roleName) {
		CombatRole role = CombatRole.byName(roleName);
		if (role == null || !(mob instanceof PathfinderMob pathfinder)) {
			return;
		}

		mob.getGoalSelector().removeAllGoals(
			goal -> goal instanceof KeepDistanceGoal<?> || goal instanceof HoldStandoffGoal<?>
		);
		ReactiveState state = ReactiveState.get(mob);
		state.setServant(true);
		state.setRoleSpec(RoleSpec.of(role));
		if (role != CombatRole.MELEE) {
			mob.getGoalSelector().addGoal(KEEP_DISTANCE_PRIORITY, new KeepDistanceGoal<>(pathfinder));
			mob.getGoalSelector().addGoal(STANDOFF_PRIORITY, new HoldStandoffGoal<>(pathfinder));
		}
	}
}
