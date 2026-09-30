package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.gear.GearRoller;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/**
 * The escort squad: at most two ordinary mobs of the elite's own kind, one grade below its kit, queued at spawn
 * and added on the level tick boundary. Every escort is flagged summoned before it enters the level, so
 * the load hook cannot roll it elite and the squad cannot recurse.
 */
public final class EscortSquad {
	/** The entourage cap: a squad request only fills the places its living escorts leave free. */
	public static final int ESCORT_COUNT = 2;
	public static final float DROP_CHANCE = 0.02F;
	private static final double SCATTER = 1.0;
	private static final int FOLLOW_PRIORITY = 5;
	private static final double FOLLOW_SPEED = 1.0;
	private static final float FOLLOW_STOP_DISTANCE = 6.0F;
	private static final float FOLLOW_AREA_SIZE = 16.0F;
	private static final AttachmentType<List<Pending>> QUEUE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "escort_queue"), ArrayList::new
	);
	// The identities this elite's squads added, so the cap counts what the ability produced.
	private static final AttachmentType<List<UUID>> ESCORTS = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "escorts"), ArrayList::new
	);

	private EscortSquad() {
	}

	public record Pending(Mob elite, GearTier tier) {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the escort attachments {} {}", QUEUE.identifier(), ESCORTS.identifier());
	}

	public static void queue(final Mob elite, final GearTier tier) {
		if (elite.level() instanceof ServerLevel level) {
			((AttachmentTarget) level).getAttachedOrCreate(QUEUE).add(new Pending(elite, tier));
		}
	}

	/** Adds every squad queued on the previous tick, once the level's own entity pass has finished. */
	public static void drain(final ServerLevel level) {
		List<Pending> pending = ((AttachmentTarget) level).getAttached(QUEUE);
		if (pending == null || pending.isEmpty()) {
			return;
		}

		List<Pending> batch = List.copyOf(pending);
		pending.clear();

		for (Pending item : batch) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=escortDrain RESULT elite={} uuid={} alive={}",
				item.elite().getType(), item.elite().getUUID(), item.elite().isAlive()
			);
			if (item.elite().isAlive() && !item.elite().isRemoved()) {
				spawnSquad(level, item.elite(), item.tier());
			}
		}
	}

	private static void spawnSquad(final ServerLevel level, final Mob elite, final GearTier eliteTier) {
		// B05: a boss takes one companion and no escort squad, so a boss never queues a squad here.
		if (EliteState.isBoss(elite)) {
			return;
		}

		GearTier tier = Elite.escortTier(eliteTier);
		int alive = escortsOf(level, elite).size();

		for (int i = alive; i < ESCORT_COUNT; i++) {
			spawnOne(level, elite, tier);
		}

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=escortCap RESULT elite={} aliveBefore={} added={} cap={}",
			elite.getUUID(), alive, Math.max(0, ESCORT_COUNT - alive), ESCORT_COUNT
		);
	}

	/** The living escorts this elite's squads added, read from the record rather than a box. */
	public static List<Mob> escortsOf(final ServerLevel level, final Mob elite) {
		List<Mob> alive = new ArrayList<>();

		for (UUID id : ((AttachmentTarget) elite).getAttachedOrCreate(ESCORTS)) {
			if (level.getEntity(id) instanceof Mob escort && escort.isAlive() && !escort.isRemoved()) {
				alive.add(escort);
			}
		}

		return alive;
	}

	@SuppressWarnings("unchecked")
	private static void spawnOne(final ServerLevel level, final Mob elite, final GearTier tier) {
		EntityType<? extends Mob> type = (EntityType<? extends Mob>) elite.getType();
		Mob escort = type.create(level, EntitySpawnReason.EVENT);
		if (escort == null) {
			return;
		}

		RandomSource random = escort.getRandom();
		escort.snapTo(
			elite.getX() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			elite.getY(),
			elite.getZ() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			random.nextFloat() * 360.0F,
			0.0F
		);
		escort.finalizeSpawn(
			level, level.getCurrentDifficultyAt(escort.blockPosition()), EntitySpawnReason.EVENT, null
		);
		escort.setBaby(false);
		// Both flags must exist before the add: ENTITY_LOAD fires inside addFreshEntity.
		EliteState.setSummoned(escort, true);
		ReactiveState.get(escort).setGearApplied(true);
		GearRoller.rollSet(
			escort, tier, DROP_CHANCE, random, level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
		);
		escort.getGoalSelector().removeAllGoals(goal -> goal instanceof WaterAvoidingRandomStrollGoal);
		escort.getGoalSelector().addGoal(
			FOLLOW_PRIORITY, new FollowAllyGoal(escort, elite, FOLLOW_SPEED, FOLLOW_STOP_DISTANCE, FOLLOW_AREA_SIZE)
		);
		if (level.addFreshEntity(escort)) {
			((AttachmentTarget) elite).getAttachedOrCreate(ESCORTS).add(escort.getUUID());
		}
	}
}
