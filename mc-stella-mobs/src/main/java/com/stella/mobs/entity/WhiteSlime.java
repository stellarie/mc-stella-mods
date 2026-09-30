package com.stella.mobs.entity;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.EliteState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;

/**
 * The white slime: always elite, fast, and hard hitting. It keeps {@link #ESCORT_COUNT} aggressive blue
 * escorts, resummons each one that dies, and refreshes Strength and Speed on itself and on the escorts.
 * The escorts it made are recorded by identity, so a count never depends on a box query.
 */
public final class WhiteSlime extends StellaSlime {
	public static final int ESCORT_COUNT = 5;
	/** Strength IV and Speed IV: the amplifier is the level minus one. */
	public static final int BUFF_AMPLIFIER = 3;
	public static final int BUFF_DURATION_TICKS = 100;
	public static final int BUFF_INTERVAL_TICKS = 20;
	public static final int SUMMON_INTERVAL_TICKS = 20;
	public static final double BASE_HEALTH = 20.0;
	public static final double ATTACK_DAMAGE = 6.0;
	public static final double MOVEMENT_SPEED = 0.6;
	private static final double SCATTER = 2.0;
	// The record persists, so a reload keeps the escorts this slime already made.
	private static final AttachmentType<List<UUID>> ESCORTS = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "white_slime_escorts"),
		builder -> builder.persistent(UUIDUtil.CODEC.listOf())
	);

	public WhiteSlime(final EntityType<? extends Slime> type, final Level level) {
		super(type, level);
	}

	static void register() {
		StellaMobs.LOGGER.debug("Registered the white slime escort record {}", ESCORTS.identifier());
	}

	@Override
	protected void applyStats() {
		setBase(Attributes.MAX_HEALTH, BASE_HEALTH);
		setBase(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
		setBase(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);

		if (this.tickCount % SUMMON_INTERVAL_TICKS == 0) {
			summonMissing(level);
		}
		if (this.tickCount % BUFF_INTERVAL_TICKS == 0) {
			refreshBuffs(level);
		}
	}

	/** The recorded escort identities. A test compares this record with what it observes. */
	public List<UUID> recordedEscorts() {
		return ((AttachmentTarget) this).getAttachedOrElse(ESCORTS, List.of());
	}

	/** The recorded escorts that are alive in the level. */
	public List<BlueSlime> livingEscorts(final ServerLevel level) {
		List<BlueSlime> alive = new ArrayList<>();
		for (UUID id : recordedEscorts()) {
			if (level.getEntity(id) instanceof BlueSlime escort && escort.isAlive() && !escort.isRemoved()) {
				alive.add(escort);
			}
		}
		return alive;
	}

	private void summonMissing(final ServerLevel level) {
		List<UUID> kept = new ArrayList<>();
		for (BlueSlime escort : livingEscorts(level)) {
			kept.add(escort.getUUID());
		}

		int alive = kept.size();
		for (int i = alive; i < ESCORT_COUNT; i++) {
			BlueSlime escort = summonOne(level);
			if (escort != null) {
				kept.add(escort.getUUID());
			}
		}

		if (!kept.equals(recordedEscorts())) {
			((AttachmentTarget) this).setAttached(ESCORTS, List.copyOf(kept));
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=whiteSlime SUMMON slime={} aliveBefore={} recorded={}", getUUID(), alive, kept.size()
			);
		}
	}

	private BlueSlime summonOne(final ServerLevel level) {
		BlueSlime escort = StellaEntities.BLUE_SLIME.create(level, EntitySpawnReason.EVENT);
		if (escort == null) {
			return null;
		}

		RandomSource random = escort.getRandom();
		escort.snapTo(
			getX() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			getY(),
			getZ() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			random.nextFloat() * 360.0F,
			0.0F
		);
		escort.finalizeSpawn(level, level.getCurrentDifficultyAt(escort.blockPosition()), EntitySpawnReason.EVENT, null);
		// The flag must exist before the add: the load hook fires inside addFreshEntity and reads it.
		EliteState.setSummoned(escort, true);
		return level.addFreshEntity(escort) ? escort : null;
	}

	private void refreshBuffs(final ServerLevel level) {
		buff(this);
		for (BlueSlime escort : livingEscorts(level)) {
			buff(escort);
		}
	}

	private void buff(final LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BUFF_DURATION_TICKS, BUFF_AMPLIFIER, true, false), this);
		target.addEffect(new MobEffectInstance(MobEffects.SPEED, BUFF_DURATION_TICKS, BUFF_AMPLIFIER, true, false), this);
	}
}
