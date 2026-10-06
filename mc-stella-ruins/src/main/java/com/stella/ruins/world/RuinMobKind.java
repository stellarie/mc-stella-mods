package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A themed mob: a vanilla type with a name, gear, scale, attribute multipliers, effects and an ability. A kind adds
 * no entity type and no renderer. Every mob of a kind is persistent and carries the tags {@link #TAG_MOB} and
 * {@code mod-stella-ruins.kind.<id>}.
 * <p>
 * With the mobs mod present, the kind's mob also carries that mod's {@code no_spawn_roll} tag, so it never rolls an
 * elite, a boss or a nemesis promotion and keeps this kind's own health and gear. It also carries the
 * {@code self_scaled} tag, so its hits follow its level.
 */
public final class RuinMobKind {
	public static final String TAG_MOB = "mod-stella-ruins.mob";
	public static final String TAG_KIND_PREFIX = "mod-stella-ruins.kind.";
	/**
	 * The published mark of a ruin mob that is a foe. The spells mod reads this exact string, so a ruin mob that
	 * carries it is a spell target. An iron golem is not an Enemy, so without the tag no spell treats it as a foe.
	 */
	public static final String TAG_HOSTILE = "mod-stella-ruins.hostile";
	/** Owned by the mobs mod: a tagged mob takes none of its spawn rolls. */
	public static final String NO_SPAWN_ROLL_TAG = "mod-stella-mobs.no_spawn_roll";
	/** Owned by the mobs mod: with the tag above, the mob's hits still follow its level. */
	public static final String SELF_SCALED_TAG = "mod-stella-mobs.self_scaled";

	private static final Map<String, RuinMobKind> KINDS = new LinkedHashMap<>();
	private static final Identifier HEALTH_ID = StellaRuins.id("kind_health");
	private static final Identifier DAMAGE_ID = StellaRuins.id("kind_damage");
	private static final Identifier SPEED_ID = StellaRuins.id("kind_speed");

	/** An effect a mob carries without end. */
	public record Effect(Holder<MobEffect> effect, int amplifier) {
	}

	private final String id;
	private final EntityType<? extends Mob> base;
	private final String nameKey;
	private final float scale;
	private final double health;
	private final double damage;
	private final double speed;
	private final Map<EquipmentSlot, Supplier<ItemStack>> gear;
	private final List<Effect> effects;
	private final @Nullable MobAbility ability;
	private final @Nullable ResourceKey<LootTable> drops;
	private final boolean boss;
	private final boolean hostile;

	private RuinMobKind(final Builder b) {
		this.id = b.id;
		this.base = b.base;
		this.nameKey = b.nameKey;
		this.scale = b.scale;
		this.health = b.health;
		this.damage = b.damage;
		this.speed = b.speed;
		this.gear = new EnumMap<>(b.gear);
		this.effects = List.copyOf(b.effects);
		this.ability = b.ability;
		this.drops = b.drops;
		this.boss = b.boss;
		this.hostile = b.hostile;
	}

	public static Builder builder(final String id) {
		return new Builder(id);
	}

	public static synchronized @Nullable RuinMobKind get(final String id) {
		return KINDS.get(id);
	}

	public static synchronized Collection<RuinMobKind> all() {
		return List.copyOf(new ArrayList<>(KINDS.values()));
	}

	public String id() {
		return this.id;
	}

	public EntityType<? extends Mob> base() {
		return this.base;
	}

	public @Nullable MobAbility ability() {
		return this.ability;
	}

	public @Nullable ResourceKey<LootTable> drops() {
		return this.drops;
	}

	public boolean boss() {
		return this.boss;
	}

	public boolean hostile() {
		return this.hostile;
	}

	/**
	 * Builds the mob, adds it to the level and fixes its level with the mobs mod. Returns null when the base type
	 * will not build. The caller owns the roster record.
	 */
	public @Nullable Mob spawn(final ServerLevel level, final Vec3 at) {
		Entity created = this.base.create(level, EntitySpawnReason.EVENT);
		if (!(created instanceof Mob mob)) {
			return null;
		}
		mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.addTag(TAG_MOB);
		mob.addTag(TAG_KIND_PREFIX + this.id);
		mob.addTag(NO_SPAWN_ROLL_TAG);
		mob.addTag(SELF_SCALED_TAG);
		if (this.hostile) {
			mob.addTag(TAG_HOSTILE);
		}
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.setCustomName(Component.translatable(this.nameKey));
		mob.setCustomNameVisible(this.boss);
		applyAttributes(mob);
		this.gear.forEach((slot, stack) -> {
			mob.setItemSlot(slot, stack.get());
			mob.setDropChance(slot, 0.0F);
		});
		for (Effect effect : this.effects) {
			mob.addEffect(new MobEffectInstance(effect.effect(), MobEffectInstance.INFINITE_DURATION, effect.amplifier(), false, false));
		}
		level.addFreshEntityWithPassengers(mob);
		// Reading the level fixes it at this spot, before the first fight.
		RuinLevels.of(mob);
		return mob;
	}

	private void applyAttributes(final Mob mob) {
		AttributeInstance scaleAttribute = mob.getAttribute(Attributes.SCALE);
		if (scaleAttribute != null && this.scale != 1.0F) {
			scaleAttribute.setBaseValue(this.scale);
		}
		multiply(mob, Attributes.MAX_HEALTH, HEALTH_ID, this.health);
		multiply(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, this.damage);
		multiply(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, this.speed);
		mob.setHealth(mob.getMaxHealth());
	}

	private static void multiply(final Mob mob, final Holder<Attribute> attribute, final Identifier id, final double factor) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && factor != 1.0) {
			instance.addPermanentModifier(new AttributeModifier(id, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	public static final class Builder {
		private final String id;
		private @Nullable EntityType<? extends Mob> base;
		private String nameKey;
		private float scale = 1.0F;
		private double health = 1.0;
		private double damage = 1.0;
		private double speed = 1.0;
		private final Map<EquipmentSlot, Supplier<ItemStack>> gear = new EnumMap<>(EquipmentSlot.class);
		private final List<Effect> effects = new ArrayList<>();
		private @Nullable MobAbility ability;
		private @Nullable ResourceKey<LootTable> drops;
		private boolean boss;
		private boolean hostile;

		private Builder(final String id) {
			if (id == null || !id.matches("[a-z0-9_]+")) {
				throw new IllegalArgumentException("a mob kind id is lower case letters, digits and _: " + id);
			}
			this.id = id;
			this.nameKey = "mob.mod-stella-ruins." + id;
		}

		public Builder base(final EntityType<? extends Mob> type) {
			this.base = type;
			return this;
		}

		/** The lang key of the mob's name. It defaults to {@code mob.mod-stella-ruins.<id>}. */
		public Builder name(final String langKey) {
			this.nameKey = langKey;
			return this;
		}

		public Builder scale(final float value) {
			this.scale = value;
			return this;
		}

		/** A multiplier on the base health, 1.0 for none. */
		public Builder health(final double multiplier) {
			this.health = multiplier;
			return this;
		}

		public Builder damage(final double multiplier) {
			this.damage = multiplier;
			return this;
		}

		public Builder speed(final double multiplier) {
			this.speed = multiplier;
			return this;
		}

		/** Gear for a slot. It never drops: its drop chance is 0. */
		public Builder gear(final EquipmentSlot slot, final Supplier<ItemStack> stack) {
			this.gear.put(slot, stack);
			return this;
		}

		/** An effect with no end. */
		public Builder effect(final Holder<MobEffect> effect, final int amplifier) {
			this.effects.add(new Effect(effect, amplifier));
			return this;
		}

		public Builder ability(final MobAbility value) {
			this.ability = value;
			return this;
		}

		/** The table a player's kill rolls, on top of the base type's own drops. */
		public Builder drops(final ResourceKey<LootTable> table) {
			this.drops = table;
			return this;
		}

		public Builder boss(final boolean value) {
			this.boss = value;
			return this;
		}

		/** A foe: the mob carries {@link #TAG_HOSTILE}, so a spell that reads the tag treats it as a target. */
		public Builder hostile() {
			this.hostile = true;
			return this;
		}

		public RuinMobKind register() {
			if (this.base == null) {
				throw new IllegalStateException("mob kind " + this.id + " needs a base type");
			}
			RuinMobKind kind = new RuinMobKind(this);
			synchronized (RuinMobKind.class) {
				if (KINDS.putIfAbsent(kind.id, kind) != null) {
					throw new IllegalStateException("mob kind " + this.id + " is registered twice");
				}
			}
			return kind;
		}
	}
}
