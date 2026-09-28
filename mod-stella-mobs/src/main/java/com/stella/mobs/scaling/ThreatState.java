package com.stella.mobs.scaling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.config.GearConfigStore;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A spawn area's level and one durable Gaussian roll. Rank changes never reroll the mob. */
public record ThreatState(int areaLevel, int variation) {
	public static final int MAX_VARIATION = 10;
	public static final double VARIATION_STDDEV = 5.0;
	public static final int NETHER_MINIMUM = 20;
	public static final int END_MINIMUM = 50;
	public static final int ELITE_BONUS = 5;
	public static final int BOSS_BONUS = 10;
	public static final Codec<ThreatState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("area_level").forGetter(ThreatState::areaLevel),
		Codec.INT.fieldOf("variation").forGetter(ThreatState::variation)
	).apply(instance, ThreatState::new));
	public static final AttachmentType<ThreatState> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "threat_state"), CODEC
	);

	public ThreatState {
		areaLevel = Math.max(0, areaLevel);
		variation = Mth.clamp(variation, -MAX_VARIATION, MAX_VARIATION);
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered threat state attachment {}", TYPE.identifier());
	}

	public static @Nullable ThreatState get(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(TYPE);
	}

	public static ThreatState getOrRoll(final Mob mob) {
		ThreatState state = get(mob);
		if (state != null) {
			return state;
		}
		state = new ThreatState(SpawnScaling.areaLevel(mob), rollVariation(mob.getRandom()));
		((AttachmentTarget) mob).setAttached(TYPE, state);
		return state;
	}

	public static int rollVariation(final RandomSource random) {
		return Mth.clamp((int) Math.round(random.nextGaussian() * VARIATION_STDDEV),
			-MAX_VARIATION, MAX_VARIATION);
	}

	public static int level(final Mob mob) {
		ThreatState state = getOrRoll(mob);
		var dimension = mob.level().dimension();
		int dimensionMinimum = dimension.equals(Level.NETHER) ? NETHER_MINIMUM
			: dimension.equals(Level.END) ? END_MINIMUM : 0;
		int areaBase = Math.max(dimensionMinimum, state.areaLevel());
		int ordinary = Math.max(dimensionMinimum, areaBase + state.variation());
		int level;
		if (EliteState.isBoss(mob)) {
			double configured = GearConfigStore.get().rollFor(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))
				.bossLevelBonus();
			long bonus = Math.max(0L, Math.round(BOSS_BONUS * configured));
			level = bonus >= Integer.MAX_VALUE - ordinary ? Integer.MAX_VALUE : ordinary + (int) bonus;
		} else {
			level = ordinary + (EliteState.isElite(mob) ? ELITE_BONUS : 0);
		}
		if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
			&& !mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG)) {
			WorldThreatPeak.observe(serverLevel, level);
		}
		return level;
	}
}
