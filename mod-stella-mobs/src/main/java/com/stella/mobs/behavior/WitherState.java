package com.stella.mobs.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.mobs.StellaMobs;
import java.util.List;
import java.util.Optional;
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
 * attribute could not hold, whether its sphere has started and its small withers have come, the ticks to its next
 * sphere burst, the minions it summoned, and the small withers it summoned.
 * A small wither also names its parent. A Wither with no state is a legacy one and stays vanilla.
 * Every field is optional in the codec, so a save written before a later field existed still decodes.
 */
public record WitherState(
	boolean activated,
	double anchorY,
	double healthDivisor,
	boolean rageAUsed,
	boolean rageBUsed,
	int sphereTicks,
	List<UUID> minions,
	List<UUID> spawnlings,
	Optional<UUID> parent
) {
	public static final Codec<WitherState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("activated", false).forGetter(WitherState::activated),
		Codec.DOUBLE.optionalFieldOf("anchor_y", 0.0).forGetter(WitherState::anchorY),
		Codec.DOUBLE.optionalFieldOf("health_divisor", 1.0).forGetter(WitherState::healthDivisor),
		Codec.BOOL.optionalFieldOf("rage_a_used", false).forGetter(WitherState::rageAUsed),
		Codec.BOOL.optionalFieldOf("rage_b_used", false).forGetter(WitherState::rageBUsed),
		Codec.INT.optionalFieldOf("sphere_ticks", 0).forGetter(WitherState::sphereTicks),
		UUIDUtil.CODEC.listOf().optionalFieldOf("minions", List.of()).forGetter(WitherState::minions),
		UUIDUtil.CODEC.listOf().optionalFieldOf("spawnlings", List.of()).forGetter(WitherState::spawnlings),
		UUIDUtil.CODEC.optionalFieldOf("parent").forGetter(WitherState::parent)
	).apply(instance, WitherState::new));
	public static final AttachmentType<WitherState> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "wither_state"), CODEC
	);

	public WitherState {
		minions = List.copyOf(minions);
		spawnlings = List.copyOf(spawnlings);
	}

	public static WitherState fresh() {
		return new WitherState(false, 0.0, 1.0, false, false, 0, List.of(), List.of(), Optional.empty());
	}

	/** A small wither: active at once, with its parent's divisor, and with both triggers spent. */
	public static WitherState small(final double anchor, final double divisor, final UUID parent) {
		return new WitherState(true, anchor, divisor, true, true, 0, List.of(), List.of(), Optional.of(parent));
	}

	/** True for a small wither, which is the only kind that names a parent. */
	public boolean small() {
		return this.parent.isPresent();
	}

	public WitherState withActivated(final double anchor) {
		return new WitherState(true, anchor, this.healthDivisor, this.rageAUsed, this.rageBUsed, this.sphereTicks, this.minions, this.spawnlings, this.parent);
	}

	public WitherState withHealthDivisor(final double divisor) {
		return new WitherState(this.activated, this.anchorY, divisor, this.rageAUsed, this.rageBUsed, this.sphereTicks, this.minions, this.spawnlings, this.parent);
	}

	public WitherState withSphere(final boolean started, final int ticksToNext) {
		return new WitherState(this.activated, this.anchorY, this.healthDivisor, started, this.rageBUsed, ticksToNext, this.minions, this.spawnlings, this.parent);
	}

	public WitherState withSpawnlings(final List<UUID> recorded) {
		return new WitherState(this.activated, this.anchorY, this.healthDivisor, this.rageAUsed, true, this.sphereTicks, this.minions, recorded, this.parent);
	}

	public WitherState withMinions(final List<UUID> recorded) {
		return new WitherState(this.activated, this.anchorY, this.healthDivisor, this.rageAUsed, this.rageBUsed, this.sphereTicks, recorded, this.spawnlings, this.parent);
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
