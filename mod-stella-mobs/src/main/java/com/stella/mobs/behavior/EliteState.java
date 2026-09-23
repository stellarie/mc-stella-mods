package com.stella.mobs.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.gear.GearTier;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * The durable half of an elite's identity: whether the mob was summoned, whether it is an elite plus the
 * tier its kit rolled, how many ender pearls its budget has left, whether it is a boss, whether its
 * one-shot storm is spent, whether the brood mother has given birth, whether the evoker has returned
 * once, and whether the piglin's one war call is spent. Registered persistent, so a chunk unload and
 * reload cannot turn a summon into a fresh elite roll, refill a spent pearl budget, re-arm a storm,
 * birth a second brood, return an evoker twice, or give a warband a second call. Every field is an
 * optional codec field, so a save written before a later field existed still decodes.
 */
public record EliteState(
	boolean summoned,
	boolean elite,
	Optional<GearTier> eliteTier,
	int pearlsLeft,
	boolean boss,
	boolean stormCast,
	boolean broodBorn,
	boolean secondPhaseUsed,
	boolean warCallUsed,
	List<String> traits
) {
	public static final Codec<EliteState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("summoned", false).forGetter(EliteState::summoned),
		Codec.BOOL.optionalFieldOf("elite", false).forGetter(EliteState::elite),
		GearTier.CODEC.optionalFieldOf("elite_tier").forGetter(EliteState::eliteTier),
		Codec.INT.optionalFieldOf("pearls_left", 0).forGetter(EliteState::pearlsLeft),
		Codec.BOOL.optionalFieldOf("boss", false).forGetter(EliteState::boss),
		Codec.BOOL.optionalFieldOf("storm_cast", false).forGetter(EliteState::stormCast),
		Codec.BOOL.optionalFieldOf("brood_born", false).forGetter(EliteState::broodBorn),
		Codec.BOOL.optionalFieldOf("second_phase_used", false).forGetter(EliteState::secondPhaseUsed),
		Codec.BOOL.optionalFieldOf("war_call_used", false).forGetter(EliteState::warCallUsed),
		Codec.STRING.listOf().optionalFieldOf("traits", List.of()).forGetter(EliteState::traits)
	).apply(instance, EliteState::new));
	public static final AttachmentType<EliteState> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "elite_state"), CODEC
	);

	public EliteState() {
		this(false, false, Optional.empty(), 0, false, false, false, false, false, List.of());
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

	public static void markElite(final Mob mob, final GearTier tier) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).asElite(tier));
	}

	/** B01 to B05: the boss flag is durable, so a reloaded boss is still a boss. */
	public static boolean isBoss(final Mob mob) {
		return get(mob).boss();
	}

	/**
	 * B1: the same flag, read without creating a state. The load path asks this of every monster,
	 * and a created state would be written into the save of a mob that is not a boss.
	 */
	public static boolean hasBossFlag(final Mob mob) {
		EliteState state = ((AttachmentTarget) mob).getAttached(TYPE);
		return state != null && state.boss();
	}

	public static void setBoss(final Mob mob, final boolean boss) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withBoss(boss));
	}

	/** R25: the pearl budget's reader and writer. The budget is durable, so a reload cannot refill it. */
	public static int pearlsLeft(final Mob mob) {
		return get(mob).pearlsLeft();
	}

	public static void setPearlsLeft(final Mob mob, final int pearlsLeft) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withPearlsLeft(Math.max(0, pearlsLeft)));
	}

	/**
	 * Slice B: the one-shot storm flag. Deliberately not haru's shared {@code bossAbilitySpent},
	 * because a boss evoker is also an elite: it has to be able to cast the storm at half health and
	 * still return once at lethal damage, and one flag would silently cancel whichever came second.
	 */
	public static boolean isStormCast(final Mob mob) {
		return get(mob).stormCast();
	}

	public static void setStormCast(final Mob mob, final boolean stormCast) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withStormCast(stormCast));
	}

	/** The brood mother's one birth. Its own field, because a spider has no other once-only ability. */
	public static boolean isBroodBorn(final Mob mob) {
		return get(mob).broodBorn();
	}

	public static void setBroodBorn(final Mob mob, final boolean broodBorn) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withBroodBorn(broodBorn));
	}

	/** The evoker's one return: its own field, because the storm is the other once-only ability it owns. */
	public static boolean isSecondPhaseUsed(final Mob mob) {
		return get(mob).secondPhaseUsed();
	}

	public static void setSecondPhaseUsed(final Mob mob, final boolean secondPhaseUsed) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withSecondPhaseUsed(secondPhaseUsed));
	}

	/** The elite piglin's one war call. Durable, so a reloaded warband cannot call a second time. */
	public static boolean isWarCallUsed(final Mob mob) {
		return get(mob).warCallUsed();
	}

	public static void setWarCallUsed(final Mob mob, final boolean warCallUsed) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withWarCallUsed(warCallUsed));
	}

	/** The location traits taken at spawn, durable so a reload cannot lose one or roll a second. */
	public static List<String> traits(final Mob mob) {
		return get(mob).traits();
	}

	public static void setTraits(final Mob mob, final List<String> traits) {
		((AttachmentTarget) mob).setAttached(TYPE, get(mob).withTraits(traits));
	}

	/** Every late field is threaded through every wither, so one missed copy would drop a flag silently. */
	private EliteState with(final boolean summoned, final boolean elite, final Optional<GearTier> eliteTier,
		final int pearlsLeft, final boolean boss, final boolean stormCast, final boolean broodBorn,
		final boolean secondPhaseUsed, final boolean warCallUsed, final List<String> traits
	) {
		return new EliteState(
			summoned, elite, eliteTier, pearlsLeft, boss, stormCast, broodBorn, secondPhaseUsed, warCallUsed, traits
		);
	}

	public EliteState withSummoned(final boolean value) {
		return with(
			value, this.elite, this.eliteTier, this.pearlsLeft, this.boss, this.stormCast, this.broodBorn,
			this.secondPhaseUsed, this.warCallUsed, this.traits
		);
	}

	public EliteState asElite(final GearTier tier) {
		return with(
			this.summoned, true, Optional.of(tier), this.pearlsLeft, this.boss, this.stormCast, this.broodBorn,
			this.secondPhaseUsed, this.warCallUsed, this.traits
		);
	}

	public EliteState withPearlsLeft(final int value) {
		return with(
			this.summoned, this.elite, this.eliteTier, value, this.boss, this.stormCast, this.broodBorn,
			this.secondPhaseUsed, this.warCallUsed, this.traits
		);
	}

	public EliteState withBoss(final boolean value) {
		return with(
			this.summoned, this.elite, this.eliteTier, this.pearlsLeft, value, this.stormCast, this.broodBorn,
			this.secondPhaseUsed, this.warCallUsed, this.traits
		);
	}

	public EliteState withStormCast(final boolean value) {
		return with(
			this.summoned, this.elite, this.eliteTier, this.pearlsLeft, this.boss, value, this.broodBorn,
			this.secondPhaseUsed, this.warCallUsed, this.traits
		);
	}

	public EliteState withBroodBorn(final boolean value) {
		return with(
			this.summoned, this.elite, this.eliteTier, this.pearlsLeft, this.boss, this.stormCast, value,
			this.secondPhaseUsed, this.warCallUsed, this.traits
		);
	}

	public EliteState withSecondPhaseUsed(final boolean value) {
		return with(
			this.summoned, this.elite, this.eliteTier, this.pearlsLeft, this.boss, this.stormCast, this.broodBorn,
			value, this.warCallUsed, this.traits
		);
	}

	public EliteState withWarCallUsed(final boolean value) {
		return with(
			this.summoned, this.elite, this.eliteTier, this.pearlsLeft, this.boss, this.stormCast, this.broodBorn,
			this.secondPhaseUsed, value, this.traits
		);
	}

	public EliteState withTraits(final List<String> value) {
		return with(
			this.summoned, this.elite, this.eliteTier, this.pearlsLeft, this.boss, this.stormCast, this.broodBorn,
			this.secondPhaseUsed, this.warCallUsed, value
		);
	}
}
