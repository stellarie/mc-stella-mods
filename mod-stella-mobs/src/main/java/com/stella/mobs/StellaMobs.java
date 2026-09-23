package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.BossCompanion;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.BroodMother;
import com.stella.mobs.behavior.Decay;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.EliteKits;
import com.stella.mobs.behavior.EscortSquad;
import com.stella.mobs.behavior.EvokerStorm;
import com.stella.mobs.behavior.GolemElite;
import com.stella.mobs.behavior.Harpooner;
import com.stella.mobs.behavior.OceanSpecials;
import com.stella.mobs.behavior.PiglinSmash;
import com.stella.mobs.behavior.PiglinWarCall;
import com.stella.mobs.behavior.OceanBand;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.ReinforcementGoal;
import com.stella.mobs.behavior.RiderSquad;
import com.stella.mobs.behavior.SecondPhase;
import com.stella.mobs.behavior.SnowGolemCold;
import com.stella.mobs.behavior.Traits;
import com.stella.mobs.behavior.VoidDrag;
import com.stella.mobs.behavior.WitchPotions;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.Loadout;
import com.stella.mobs.gear.EnchantRoller;
import com.stella.mobs.gear.GearApplier;
import com.stella.mobs.gear.GearRoller;
import com.stella.mobs.scaling.SpawnScaling;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StellaMobs implements ModInitializer {
	public static final String MOD_ID = "mod-stella-mobs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ReactiveState.register();
		EliteState.register();
		EscortSquad.register();
		RiderSquad.register();
		BossCompanion.register();
		Bosses.register();
		ReinforcementGoal.register();
		EvokerStorm.register();
		PiglinWarCall.register();
		ServerEntityEvents.ENTITY_LOAD.register(StellaMobs::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(StellaMobs::onEntityUnload);
		ServerLivingEntityEvents.AFTER_DEATH.register(StellaMobs::onEntityDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(EvokerStorm::afterDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BroodMother::afterDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Decay::afterDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(VoidDrag::afterDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(GolemElite::afterDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PiglinSmash::afterDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PiglinWarCall::afterDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(EliteKits::strayFreeze);
		// T04: the windy trait shoves what this mob hits, read through the source so a windy victim cannot.
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Traits::afterDamage);
		// P2: an elite drowned's trident pulls what it hits, which Loyalty does not do.
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Harpooner::afterDamage);
		// T06: the end trait negates a hit itself, so it rides the allow hook rather than the after hook.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Traits::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(SecondPhase::allowDeath);
		ServerTickEvents.END_LEVEL_TICK.register(EscortSquad::drain);
		ServerTickEvents.END_LEVEL_TICK.register(RiderSquad::drain);
		ServerTickEvents.END_LEVEL_TICK.register(BossCompanion::drain);
		ServerTickEvents.END_LEVEL_TICK.register(OceanBand::drain);
		ServerTickEvents.END_LEVEL_TICK.register(EvokerStorm::tick);
		ServerTickEvents.END_LEVEL_TICK.register(Bosses::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> GearConfigStore.load());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				GearConfigStore.load();
				EnchantRoller.clearCache();
			}
		});
		LOGGER.info("{} initialized", MOD_ID);
	}

	static void onEntityLoad(final Entity entity, final ServerLevel level) {
		if (!(entity instanceof Mob mob)) {
			return;
		}

		boolean loadedFromDisk = entity instanceof EntityLoadData loadData && loadData.isLoadedFromDisk();

		if (!loadedFromDisk) {
			Identifier entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
			Loadout loadout = GearConfigStore.get().loadoutFor(entityType);
			ReactiveState state = ReactiveState.get(mob);

			if (loadout != null) {
				if (!state.isGearApplied()) {
					GearApplier.apply(mob, loadout, level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
					state.setGearApplied(true);
				}
			} else if (GearRoller.canRoll(mob) && !state.isGearApplied()) {
				GearRoller.roll(
					mob,
					GearConfigStore.get().rollFor(entityType),
					mob.getRandom(),
					level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				);
				state.setGearApplied(true);
			}

			// B01 and B02: the boss roll runs before the elite roll, which returns at its own guard.
			Bosses.rollAtSpawn(mob, level, GearConfigStore.get().rollFor(entityType).bossChance());

			Elite.rollAtSpawn(
				mob, level, GearConfigStore.get().rollFor(entityType).eliteChance(), loadout == null
			);

			// P3 and P4: a glow squid and a pufferfish are not Enemies, so their roll lives here.
			OceanSpecials.roll(mob, GearConfigStore.get().rollFor(entityType).eliteChance());

			// R23: an elite spider's rider is guaranteed by the elite path, so the chance roll skips it
			// and the mount cannot end up with two riders.
			if (RiderSquad.canMount(mob) && !EliteState.isSummoned(mob) && !Elite.isElite(mob)) {
				EntityType<? extends Mob> riderType = RiderSquad.RIDER_TYPES.get(mob.getType());
				double riderChance = GearConfigStore.get().riderFor(entityType).chance();
				if (riderType != null && riderChance > 0.0 && mob.getRandom().nextFloat() < (float) riderChance) {
					RiderSquad.queue(mob, riderType);
					LOGGER.info(
						"[BEHAVIOR] test=riders t={} RIDER mount={} chance={} rider={}",
						level.getGameTime(),
						entityType,
						riderChance,
						BuiltInRegistries.ENTITY_TYPE.getKey(riderType)
					);
				}
			}

			SpawnScaling.apply(mob);
			// T03 to T06: the location traits are taken here, once, from where the mob appeared.
			Traits.apply(mob);

			// O01: a fresh natural dolphin over an ocean may start a skeleton band.
			if (mob instanceof Dolphin
				&& entity instanceof EntityLoadData loadData
				&& loadData.spawnReason() == EntitySpawnReason.NATURAL) {
				OceanBand.trySpawn(level, (Dolphin) mob);
			}
		}

		// B1: a boss joins its level's bar watch list here, which covers both the roll above and a boss
		// read from disk, whose flag came back from the save.
		if (EliteState.hasBossFlag(mob)) {
			Bosses.watch(mob);
		}

		BehaviorInstaller.install(mob);
		// R24: a ghast is not a Monster, so the installer returns before this and the ghast path is its own.
		BehaviorInstaller.installGhastFireball(mob);
		// G01: an iron golem is not an Enemy, so it cannot roll elite and is forced here instead. The call
		// is idempotent and its modifiers are guarded, so a reload can neither mark nor stack it twice.
		GolemElite.force(mob);
		// G07: a snow golem is not an Enemy either, so its one in ten arrival rolls on the same per-type
		// chance key and marks through the shared marker.
		SnowGolemCold.rollElite(mob);
	}

	/** B1: a boss that leaves the level loses its bar, because the sweep can no longer reach it. */
	private static void onEntityUnload(final Entity entity, final ServerLevel level) {
		if (entity instanceof Mob mob && EliteState.hasBossFlag(mob)) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=bossBarWatch t={} REMOVE uuid={} cause=unload", level.getGameTime(), mob.getUUID()
			);
			Bosses.unwatch(mob);
		}
	}

	/** W10 and B1: a witch drops one of every potion it carries, and a boss loses its bar. */
	private static void onEntityDeath(final LivingEntity entity, final DamageSource source) {
		if (entity instanceof Mob mob && EliteState.hasBossFlag(mob)) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=bossBarWatch t={} REMOVE uuid={} cause=death source={}",
				entity.level().getGameTime(), mob.getUUID(), source.getMsgId()
			);
			Bosses.unwatch(mob);
		}

		if (!(entity instanceof Witch witch) || !(witch.level() instanceof ServerLevel level)) {
			return;
		}

		for (WitchPotions.Carried carried : WitchPotions.CARRIED) {
			witch.spawnAtLocation(level, WitchPotions.stack(carried));
		}
	}
}
