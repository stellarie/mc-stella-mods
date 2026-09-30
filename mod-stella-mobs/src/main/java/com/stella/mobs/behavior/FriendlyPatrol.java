package com.stella.mobs.behavior;

import com.stella.mobs.entity.BlueSlime;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.mixin.MobTargetSelectorAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.Pillager;

/**
 * A wild pillager pair is a village patrol on its rounds: it wears the "Village Patrol" label, never takes
 * a player, a villager or a golem as its target, and hunts the monsters around it. Golems leave it alone,
 * villagers do not fear it, and a player may sleep beside it. It still answers whoever strikes it.
 */
public final class FriendlyPatrol {
	public static final String TAG = "mod-stella-mobs.friendly_patrol";
	/** The village mod's own patrol tag, so the two patrols never fight each other. */
	public static final String VILLAGE_PATROL_TAG = "mc-stella-villages.patrol";
	public static final String LABEL = "Village Patrol";
	public static final String VILLAGE_WITCH_TAG = "mc-stella-villages.village_witch";
	public static final ChatFormatting LABEL_COLOR = ChatFormatting.GREEN;
	private static final int HUNT_INTERVAL = 10;

	private FriendlyPatrol() {
	}

	/** Goals are never saved, so a friendly pillager gets its own each time it enters a level. */
	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Pillager pillager && isFriendly(pillager)) {
				installTargets(pillager);
			}
		});
	}

	public static boolean isFriendly(final Entity entity) {
		return entity != null && entity.entityTags().contains(TAG);
	}

	/** A friend of the village by any mod's tag: a wild patrol pair, a village patrol member, the village witch. */
	public static boolean isVillageFriend(final Entity entity) {
		return entity != null && (entity.entityTags().contains(TAG) || entity.entityTags().contains(VILLAGE_PATROL_TAG)
			|| entity.entityTags().contains(VILLAGE_WITCH_TAG));
	}

	/** Marks a pillager before it joins the level; its goals follow on its load. */
	public static void befriend(final Pillager pillager) {
		pillager.addTag(TAG);
		// A friend never joins a raid, where nothing but the player could end it.
		pillager.setCanJoinRaid(false);
		// A friendly patrol takes none of the spawn rolls, so no elite aura or kit ever turns it on a player.
		pillager.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		pillager.setCustomName(Component.literal(LABEL).withStyle(LABEL_COLOR));
		pillager.setCustomNameVisible(true);
	}

	private static void installTargets(final Pillager pillager) {
		GoalSelector targets = ((MobTargetSelectorAccessor) pillager).stellaMobs$targetSelector();
		targets.removeAllGoals(goal -> true);
		targets.addGoal(1, new HurtByTargetGoal(pillager));
		targets.addGoal(2, new NearestAttackableTargetGoal<>(pillager, Mob.class, HUNT_INTERVAL, true, false, FriendlyPatrol::isQuarry));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=friendlyPatrol INSTALL pillager={}", pillager.getUUID());
	}

	// A monster, but never a creeper that would blow up beside it, another friendly patrol, or a village's own patrol.
	private static boolean isQuarry(final net.minecraft.world.entity.LivingEntity target, final ServerLevel level) {
		return target instanceof Enemy && !(target instanceof Creeper) && !isFriendly(target)
			&& !target.entityTags().contains(VILLAGE_PATROL_TAG) && !BlueSlime.isHarmless(target);
	}
}
