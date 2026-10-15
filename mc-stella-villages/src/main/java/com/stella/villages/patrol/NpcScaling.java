package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;

/**
 * The village's friendly NPCs (guards, dukes, mages, the trainer and iron golems) level with the nearest player. The
 * mobs mod owns the level and the scaling, through its friendly-level share. This class gives each NPC its base
 * health, and asks the share again each second so the NPC keeps pace as the player grows. With no mobs mod, nothing
 * scales.
 */
public final class NpcScaling {
	public static final String SHARE_KEY = "mod-stella-mobs:friendly-level";
	public static final double BASE_HEALTH = 10000.0;
	public static final int REFRESH_TICKS = 20;
	/** Marks a golem that took the base health once. */
	public static final String GOLEM_TAG = "mc-stella-villages.npc_golem";
	private static final Map<UUID, Mob> TRACKED = new ConcurrentHashMap<>();

	private NpcScaling() {
	}

	public static void register() {
		// Before the mobs mod's own load listener, so its elite roll sees the golem already claimed.
		Identifier early = Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "npc_scaling_early");
		ServerEntityEvents.ENTITY_LOAD.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ServerEntityEvents.ENTITY_LOAD.register(early, (entity, level) -> onLoad(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> TRACKED.remove(entity.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_TICKS == 0) {
				refreshAll();
			}
		});
	}

	public static void setBaseHealth(final Mob mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(BASE_HEALTH);
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static void onLoad(final Entity entity) {
		if (!(entity instanceof Mob mob)) {
			return;
		}
		if (mob instanceof IronGolem && !mob.entityTags().contains(GOLEM_TAG)) {
			// A tagged golem belongs to another mod's kit, such as a nemesis companion.
			if (mob.entityTags().contains(McStellaVillages.NO_SPAWN_ROLL_TAG)) {
				return;
			}
			mob.addTag(GOLEM_TAG);
			setBaseHealth(mob);
		}
		if (mob.entityTags().contains(GOLEM_TAG) || PatrolState.isMarked(mob)) {
			TRACKED.put(mob.getUUID(), mob);
			refresh(mob);
		}
	}

	private static void refreshAll() {
		for (Mob mob : List.copyOf(TRACKED.values())) {
			if (mob.isRemoved() || !mob.isAlive()) {
				TRACKED.remove(mob.getUUID(), mob);
			} else {
				refresh(mob);
			}
		}
	}

	private static void refresh(final Mob mob) {
		if (FabricLoader.getInstance().getObjectShare().get(SHARE_KEY) instanceof Consumer<?> share) {
			try {
				@SuppressWarnings("unchecked")
				Consumer<Mob> levels = (Consumer<Mob>) share;
				levels.accept(mob);
			} catch (RuntimeException error) {
				McStellaVillages.LOGGER.warn("The friendly level share failed for {}: {}", mob.getUUID(), error.toString());
			}
		}
	}
}
