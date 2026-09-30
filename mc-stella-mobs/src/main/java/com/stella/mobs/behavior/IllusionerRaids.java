package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.raid.Raid;
import org.jspecify.annotations.Nullable;

/**
 * M9: vanilla never puts an Illusioner in a raid, so a player could not meet one. From wave three on,
 * each wave has this chance to bring one along, joined through the raid's own call so it counts, buffs
 * and ends with the wave like any other raider.
 */
public final class IllusionerRaids {
	public static final int FIRST_WAVE = 3;
	public static final float JOIN_CHANCE = 0.25F;

	private IllusionerRaids() {
	}

	public static boolean shouldJoin(final int wave, final RandomSource random) {
		return wave >= FIRST_WAVE && random.nextFloat() < JOIN_CHANCE;
	}

	/** Called by the raid mixin once a wave has spawned. */
	public static void afterWave(final Raid raid, final ServerLevel level, final BlockPos pos) {
		int wave = raid.getGroupsSpawned();
		if (shouldJoin(wave, level.getRandom())) {
			join(raid, level, wave, pos);
		}
	}

	public static @Nullable Illusioner join(final Raid raid, final ServerLevel level, final int wave, final BlockPos pos) {
		Illusioner illusioner = EntityTypes.ILLUSIONER.create(level, EntitySpawnReason.EVENT);
		if (illusioner == null) {
			return null;
		}

		raid.joinRaid(level, wave, illusioner, pos, false);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=illusionerKit RAID wave={} joined={}", wave, level.getEntity(illusioner.getUUID()) == illusioner);
		return illusioner;
	}
}
