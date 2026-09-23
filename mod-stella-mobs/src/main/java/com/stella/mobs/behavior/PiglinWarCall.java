package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearRoller;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.piglin.Piglin;

/**
 * R24 item 2, the piglin war call: an elite piglin that takes damage from a player or a mob calls ten to
 * fifteen piglins once, each in a rolled diamond or netherite kit, and one in a hundred of them arrives
 * as an elite piglin brute instead. The flag is written before the first summon, so a re-entrant damage
 * event during the call cannot start a second one, and it is durable, so a reload cannot rearm it.
 */
public final class PiglinWarCall {
	public static final int MIN_SUMMONS = 10;
	public static final int MAX_SUMMONS = 15;
	public static final double SCATTER = 3.0;
	/**
	 * The identities this caller's one call produced, so a clause reads against the set the ability wrote
	 * rather than against a box. A neighbouring test's summoned piglins sit inside any box, and a despawn
	 * would shrink a count, while a recorded set can neither gain a stranger nor lose a member.
	 */
	private static final AttachmentType<List<UUID>> BAND = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "war_call_band"), ArrayList::new
	);

	private PiglinWarCall() {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the war call band attachment {}", BAND.identifier());
	}

	/** The identities the call sent, in arrival order, as a copy the caller cannot have changed. */
	public static List<UUID> bandOf(final Mob caller) {
		return List.copyOf(((AttachmentTarget) caller).getAttachedOrCreate(BAND));
	}

	public static int bandSize(final Mob caller) {
		return bandOf(caller).size();
	}

	public static void afterDamage(
		final LivingEntity entity,
		final DamageSource source,
		final float baseDamage,
		final float damageTaken,
		final boolean blocked
	) {
		if (!(entity instanceof Piglin caller) || !(caller.level() instanceof ServerLevel level)) {
			return;
		}

		if (!Elite.isElite(caller) || EliteState.isSummoned(caller) || EliteState.isWarCallUsed(caller)) {
			return;
		}

		Entity attacker = source.getEntity();
		if (attacker == null || attacker == caller || !(attacker instanceof Player || attacker instanceof Mob)) {
			return;
		}

		// Before the summons: a damage event raised by the call itself cannot double-call.
		EliteState.setWarCallUsed(caller, true);
		int called = summon(level, caller, caller.getRandom());
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=piglinWarCall t={} CALL called={} recorded={} calledBy={}",
			level.getGameTime(), called, bandSize(caller), attacker.getType()
		);
	}

	private static int summon(final ServerLevel level, final Mob caller, final RandomSource random) {
		int count = MIN_SUMMONS + random.nextInt(MAX_SUMMONS - MIN_SUMMONS + 1);
		double bruteChance = GearConfigStore.get()
			.rollFor(BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.PIGLIN))
			.warCallBruteChance();

		for (int i = 0; i < count; i++) {
			summonOne(level, caller, bruteChance, random);
		}

		return count;
	}

	@SuppressWarnings("unchecked")
	private static void summonOne(
		final ServerLevel level, final Mob caller, final double bruteChance, final RandomSource random
	) {
		boolean brute = random.nextFloat() < (float) bruteChance;
		EntityType<? extends Mob> type = brute ? EntityTypes.PIGLIN_BRUTE : EntityTypes.PIGLIN;
		Mob companion = type.create(level, EntitySpawnReason.EVENT);
		if (companion == null) {
			return;
		}

		companion.snapTo(
			caller.getX() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			caller.getY(),
			caller.getZ() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			random.nextFloat() * 360.0F,
			0.0F
		);
		companion.finalizeSpawn(
			level, level.getCurrentDifficultyAt(companion.blockPosition()), EntitySpawnReason.EVENT, null
		);
		companion.setBaby(false);
		// All three writes must land before the add: ENTITY_LOAD fires inside addFreshEntity.
		EliteState.setSummoned(companion, true);
		ReactiveState.get(companion).setGearApplied(true);
		GearTier tier = Elite.pickTier(random);

		if (brute) {
			Elite.mark(companion, tier, Elite.HEALTH_MULTIPLIER);
		}

		GearRoller.rollSet(
			companion, tier, Elite.KIT_DROP_CHANCE, random,
			level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
		);

		if (level.addFreshEntity(companion)) {
			((AttachmentTarget) caller).getAttachedOrCreate(BAND).add(companion.getUUID());
		}
	}
}
