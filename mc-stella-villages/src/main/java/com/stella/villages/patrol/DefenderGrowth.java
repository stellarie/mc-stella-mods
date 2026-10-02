package com.stella.villages.patrol;

import com.mojang.serialization.Codec;
import com.stella.mobs.entity.BlueSlime;
import com.stella.villages.McStellaVillages;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A village defender grows stronger with each kill of a mob or a player. One kill adds {@link #KILL_POINTS} to one
 * random stat. With the rpg mod present, its object share grows an rpg stat and returns the stat's key. Without it,
 * the defender gains a permanent modifier on one of four vanilla attributes, and a saved total per attribute keeps
 * the growth across a reload. A fellow defender and a friend of the village never count as a kill.
 */
public final class DefenderGrowth {
	// The share is a BiFunction<Mob, Double, String>.
	// It takes the mob and the points.
	// It returns the key of the stat it grew.
	public static final String RPG_SHARE_KEY = "mod-stella-rpg:mob-stat-grow";
	public static final double KILL_POINTS = 0.5;
	public static final List<Holder<Attribute>> ATTRIBUTES = List.of(
		Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS
	);
	private static final AttachmentType<Map<String, Double>> TOTALS = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "defender_growth"),
		Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
	);

	private DefenderGrowth() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(DefenderGrowth::onAfterDeath);
	}

	/**
	 * Points gained per attribute, by name.
	 * The rpg share keeps its own count.
	 */
	public static Map<String, Double> totals(final Mob defender) {
		return Map.copyOf(((AttachmentTarget) defender).getAttachedOrElse(TOTALS, Map.of()));
	}

	public static String key(final Holder<Attribute> attribute) {
		return attribute.unwrapKey().orElseThrow().identifier().getPath();
	}

	public static Identifier modifierId(final Holder<Attribute> attribute) {
		return Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "defender_growth_" + key(attribute));
	}

	/** Grows one stat. Returns its key. */
	public static @Nullable String grow(final Mob defender, final LivingEntity victim) {
		String stat = growThroughShare(defender);
		String via = "rpg";
		if (stat == null) {
			stat = growAttribute(defender);
			via = "attribute";
		}

		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=defender_grown defender={} role={} victim={} stat={} via={} amount={}",
			McStellaVillages.MOD_ID, defender.getUUID(), PatrolState.role(defender), victim.getType(), stat, via, KILL_POINTS
		);
		return stat;
	}

	private static void onAfterDeath(final LivingEntity victim, final DamageSource source) {
		if (source.getEntity() instanceof Mob defender && PatrolState.isMarked(defender) && counts(victim)) {
			grow(defender, victim);
		}
	}

	/**
	 * A foe counts.
	 * A fellow, a friend, or a harmless mob does not.
	 */
	static boolean counts(final LivingEntity victim) {
		return victim instanceof Player || victim instanceof Mob mob && !isFriend(mob);
	}

	private static boolean isFriend(final Mob mob) {
		return PatrolState.isMarked(mob)
			|| mob.entityTags().contains(PatrolDefendTargetGoal.FRIENDLY_PATROL_TAG)
			|| mob.entityTags().contains(BlueSlime.HARMLESS_TAG)
			|| mob instanceof AbstractGolem
			|| mob instanceof AbstractVillager;
	}

	@SuppressWarnings("unchecked")
	private static @Nullable String growThroughShare(final Mob defender) {
		if (!(FabricLoader.getInstance().getObjectShare().get(RPG_SHARE_KEY) instanceof BiFunction<?, ?, ?> share)) {
			return null;
		}

		try {
			return ((BiFunction<Mob, Double, ?>) share).apply(defender, KILL_POINTS) instanceof String stat ? stat : null;
		} catch (RuntimeException error) {
			McStellaVillages.LOGGER.warn("The rpg growth share failed for {}: {}", defender.getUUID(), error.toString());
			return null;
		}
	}

	private static @Nullable String growAttribute(final Mob defender) {
		Holder<Attribute> attribute = ATTRIBUTES.get(defender.getRandom().nextInt(ATTRIBUTES.size()));
		AttributeInstance instance = defender.getAttribute(attribute);
		if (instance == null) {
			return null;
		}

		String key = key(attribute);
		Map<String, Double> totals = new HashMap<>(totals(defender));
		double total = totals.merge(key, KILL_POINTS, Double::sum);
		((AttachmentTarget) defender).setAttached(TOTALS, Map.copyOf(totals));
		// One modifier per attribute, raised in place.
		// So growth never stacks.
		instance.addOrReplacePermanentModifier(new AttributeModifier(modifierId(attribute), total, AttributeModifier.Operation.ADD_VALUE));
		return key;
	}
}
