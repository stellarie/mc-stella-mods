package com.stella.mobs.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.mobs.StellaMobs;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * The durable half of an encounter Wither: whether its charge-up is over, the height it stood at then, the health its
 * attribute could not hold, which rages it has spent, the rage it is in, and the minions it summoned.
 * A Wither with no state is a legacy one and stays vanilla. Every field is optional in the codec, so a save written
 * before a later field existed still decodes.
 */
public record WitherState(
	boolean activated,
	double anchorY,
	double healthDivisor,
	boolean rageAUsed,
	boolean rageBUsed,
	int rageKind,
	int rageTicksLeft,
	List<UUID> minions
) {
	public static final int RAGE_NONE = 0;
	public static final int RAGE_BARRAGE = 1;
	public static final int RAGE_RAIN = 2;
	public static final Codec<WitherState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("activated", false).forGetter(WitherState::activated),
		Codec.DOUBLE.optionalFieldOf("anchor_y", 0.0).forGetter(WitherState::anchorY),
		Codec.DOUBLE.optionalFieldOf("health_divisor", 1.0).forGetter(WitherState::healthDivisor),
		Codec.BOOL.optionalFieldOf("rage_a_used", false).forGetter(WitherState::rageAUsed),
		Codec.BOOL.optionalFieldOf("rage_b_used", false).forGetter(WitherState::rageBUsed),
		Codec.INT.optionalFieldOf("rage_kind", RAGE_NONE).forGetter(WitherState::rageKind),
		Codec.INT.optionalFieldOf("rage_ticks_left", 0).forGetter(WitherState::rageTicksLeft),
		UUIDUtil.CODEC.listOf().optionalFieldOf("minions", List.of()).forGetter(WitherState::minions)
	).apply(instance, WitherState::new));
	public static final AttachmentType<WitherState> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "wither_state"), CODEC
	);

	public WitherState {
		minions = List.copyOf(minions);
	}

	public static WitherState fresh() {
		return new WitherState(false, 0.0, 1.0, false, false, RAGE_NONE, 0, List.of());
	}

	public WitherState withActivated(final double anchor) {
		return new WitherState(true, anchor, this.healthDivisor, this.rageAUsed, this.rageBUsed, this.rageKind, this.rageTicksLeft, this.minions);
	}

	public WitherState withHealthDivisor(final double divisor) {
		return new WitherState(this.activated, this.anchorY, divisor, this.rageAUsed, this.rageBUsed, this.rageKind, this.rageTicksLeft, this.minions);
	}

	public WitherState withRage(final boolean aUsed, final boolean bUsed, final int kind, final int ticksLeft) {
		return new WitherState(this.activated, this.anchorY, this.healthDivisor, aUsed, bUsed, kind, ticksLeft, this.minions);
	}

	public WitherState withMinions(final List<UUID> recorded) {
		return new WitherState(this.activated, this.anchorY, this.healthDivisor, this.rageAUsed, this.rageBUsed, this.rageKind, this.rageTicksLeft, recorded);
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the wither state attachment {}", TYPE.identifier());
	}

	/** The state, or null for a legacy Wither. It never creates one. */
	public static @Nullable WitherState find(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(TYPE);
	}

	/** Read by the rpg mod through a method handle, so it must stay public static and create nothing. */
	public static boolean hasWitherFlag(final Mob mob) {
		return find(mob) != null;
	}

	public static void set(final Mob mob, final WitherState state) {
		((AttachmentTarget) mob).setAttached(TYPE, state);
	}
}
