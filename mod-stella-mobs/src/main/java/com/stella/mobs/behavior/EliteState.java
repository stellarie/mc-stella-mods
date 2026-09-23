package com.stella.mobs.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.gear.GearTier;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * The durable half of an elite's identity: whether the mob was summoned, whether its one war call is
 * spent, and whether it is an elite plus the tier its kit rolled. Registered persistent, so a chunk
 * unload and reload cannot turn a summon into a fresh elite roll or give a warband a second call.
 * Timing state stays in {@link ReactiveState}, where losing a cooldown is harmless.
 */
public record EliteState(boolean summoned, boolean warCallUsed, boolean elite, Optional<GearTier> eliteTier) {
	public static final Codec<EliteState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("summoned", false).forGetter(EliteState::summoned),
		Codec.BOOL.optionalFieldOf("war_call_used", false).forGetter(EliteState::warCallUsed),
		Codec.BOOL.optionalFieldOf("elite", false).forGetter(EliteState::elite),
		GearTier.CODEC.optionalFieldOf("elite_tier").forGetter(EliteState::eliteTier)
	).apply(instance, EliteState::new));
	public static final AttachmentType<EliteState> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "elite_state"), CODEC
	);

	public EliteState() {
		this(false, false, false, Optional.empty());
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the elite state attachment {}", TYPE.identifier());
	}

	public static EliteState get(final Mob mob) {
		return ((AttachmentTarget) mob).getAttachedOrCreate(TYPE, EliteState::new);
	}

	public static boolean isElite(final Mob mob) {
		return get(mob).elite();
	}

	public static boolean isSummoned(final Mob mob) {
		return get(mob).summoned();
	}

	public static void setSummoned(final Mob mob, final boolean summoned) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withSummoned(summoned));
	}

	public static boolean isWarCallUsed(final Mob mob) {
		return get(mob).warCallUsed();
	}

	public static void setWarCallUsed(final Mob mob, final boolean warCallUsed) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withWarCallUsed(warCallUsed));
	}

	public static void markElite(final Mob mob, final GearTier tier) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).asElite(tier));
	}

	public EliteState withSummoned(final boolean value) {
		return new EliteState(value, this.warCallUsed, this.elite, this.eliteTier);
	}

	public EliteState withWarCallUsed(final boolean value) {
		return new EliteState(this.summoned, value, this.elite, this.eliteTier);
	}

	public EliteState asElite(final GearTier tier) {
		return new EliteState(this.summoned, this.warCallUsed, true, Optional.of(tier));
	}
}
