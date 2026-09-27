package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The mobs on the player's side grew to meet the hostiles. Each kind below carries a fraction more of its
 * own base health, speed and attack damage, as permanent modifiers, so a tamed wolf's larger base is raised
 * as well, and the bonus survives saves. Wolves get the most: they are the player's own pack.
 */
public final class FriendlyBuffs {
	public record Buff(double health, double speed, double damage) {
	}

	public static final Map<EntityType<?>, Buff> BUFFS = buffs();
	public static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "friendly_health");
	public static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "friendly_speed");
	public static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "friendly_damage");

	private FriendlyBuffs() {
	}

	private static Map<EntityType<?>, Buff> buffs() {
		Map<EntityType<?>, Buff> buffs = new LinkedHashMap<>();
		buffs.put(EntityTypes.WOLF, new Buff(1.0, 0.75, 1.5));
		buffs.put(EntityTypes.IRON_GOLEM, new Buff(0.5, 0.0, 0.5));
		buffs.put(EntityTypes.SNOW_GOLEM, new Buff(1.0, 0.0, 0.0));
		buffs.put(EntityTypes.CAT, new Buff(1.0, 0.3, 0.0));
		buffs.put(EntityTypes.FOX, new Buff(0.0, 0.3, 0.5));
		buffs.put(EntityTypes.HORSE, new Buff(0.5, 0.25, 0.0));
		buffs.put(EntityTypes.DONKEY, new Buff(0.5, 0.25, 0.0));
		buffs.put(EntityTypes.MULE, new Buff(0.5, 0.25, 0.0));
		buffs.put(EntityTypes.CAMEL, new Buff(0.5, 0.25, 0.0));
		buffs.put(EntityTypes.LLAMA, new Buff(1.0, 0.0, 0.0));
		buffs.put(EntityTypes.TRADER_LLAMA, new Buff(1.0, 0.0, 0.0));
		buffs.put(EntityTypes.PARROT, new Buff(1.0, 0.0, 0.0));
		buffs.put(EntityTypes.POLAR_BEAR, new Buff(0.5, 0.0, 0.5));
		return java.util.Collections.unmodifiableMap(buffs);
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				apply(mob);
			}
		});
	}

	/** Adds the kind's bonus once; a mob that gains more health here starts at full health. */
	public static void apply(final Mob mob) {
		Buff buff = BUFFS.get(mob.getType());
		if (buff == null) {
			return;
		}
		boolean healthAdded = add(mob, Attributes.MAX_HEALTH, HEALTH_ID, buff.health());
		add(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, buff.speed());
		add(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, buff.damage());
		if (healthAdded) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static boolean add(final Mob mob, final Holder<Attribute> attribute, final Identifier id, final double bonus) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (bonus == 0.0 || instance == null || instance.hasModifier(id)) {
			return false;
		}
		instance.addPermanentModifier(new AttributeModifier(id, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		return true;
	}
}
