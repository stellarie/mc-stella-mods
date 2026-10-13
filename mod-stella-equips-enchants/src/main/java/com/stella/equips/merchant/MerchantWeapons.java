package com.stella.equips.merchant;

import com.stella.equips.StellaEquips;
import com.stella.equips.mixin.AbstractArrowAccessor;
import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineStation;
import com.stella.equips.tooltip.DescriptionLines;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDispatch;
import com.stella.equips.unique.UniqueTargets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The six netherite-tier weapons of the ghost merchant: Soulreaver (sword), Gravecleaver (axe), Wraithstring (bow),
 * Hexbolt (crossbow), Wraithpike (spear) and the Archmage's Staff. Each carries fixed rolls from creation, refines
 * with netherite like any weapon, and grows its effect at +7, +10, +12 and +15. The catalog is {@link #DEFS}; every
 * number of an effect is a constant below.
 *
 * <p>The fixed rolls are default components under the component types of mod-stella-rpg, looked up by id when the
 * components are bound, so the mod loads and works without it. The staff's spell power lives in mod-stella-spells,
 * which finds the staff by the item tag {@code mod-stella-equips-enchants:mage_staffs}.
 */
public final class MerchantWeapons {
	/** The refine levels at which the effect grows. */
	public static final int[] MILESTONES = {Refine.KEEN_LEVEL, Refine.SEARING_LEVEL, Refine.RENDING_LEVEL, Refine.ASCENDED_LEVEL};
	public static final int ENCHANTABILITY = 10;
	public static final String RPG_NAMESPACE = "mod-stella-rpg";
	public static final String HEXBOLT_TAG = "mod-stella-equips-enchants.hexbolt";

	/** Soulreaver: the chance in percent that a hit looses a slash, the slash's share of the hit, and the milestone steps. */
	public static final double SLASH_PERCENT = 15.0;
	public static final double SLASH_SHARE = 0.50;
	public static final double SWORD_DAMAGE_STEP = 0.05;
	public static final double LIFESTEAL_STEP = 0.02;
	/** Gravecleaver: the share of the hit that foes near the target take, how near, and the milestone step. */
	public static final double CLEAVE_BASE = 0.50;
	public static final double CLEAVE_STEP = 0.10;
	public static final double CLEAVE_RADIUS = 3.0;
	/** Wraithstring: the draw time against vanilla, the extra pierce, the arrow damage, and the milestone step. */
	public static final double BOW_DRAW_TIME = 0.75;
	public static final int BOW_PIERCE = 2;
	public static final double BOW_DAMAGE_BASE = 0.20;
	public static final double BOW_DAMAGE_STEP = 0.05;
	public static final int VANILLA_DRAW_TICKS = 20;
	/** Hexbolt: the bolts of a shot, the angle between two of them, the burst radius and its step, the burst's share of a bolt. */
	public static final int BOLTS = 3;
	public static final float FAN_DEGREES = 10.0F;
	public static final double BURST_RADIUS = 1.5;
	public static final double BURST_RADIUS_STEP = 0.5;
	public static final double BURST_SHARE = 0.50;
	public static final double BOLT_DAMAGE_STEP = 0.05;
	/** Wraithpike: the extra reach in blocks, the milestone steps, and the Wither of a charge hit. */
	public static final float REACH_BONUS = 1.0F;
	public static final float REACH_STEP = 0.25F;
	public static final double SPEAR_DAMAGE_STEP = 0.05;
	public static final int WITHER_TICKS = 60;
	public static final int WITHER_AMPLIFIER = 1;
	private static final float VANILLA_MIN_REACH = 2.0F;
	private static final float VANILLA_MAX_REACH = 4.5F;
	private static final float VANILLA_MAX_CREATIVE_REACH = 6.5F;
	private static final float REACH_HITBOX_MARGIN = 0.125F;
	private static final float REACH_MOB_FACTOR = 0.5F;
	private static final double STAFF_ATTACK_BONUS = 3.0;
	private static final double STAFF_ATTACK_SPEED = -2.8;

	private static final Map<String, Integer> NETHERITE_STATS_SWORD = Map.of("str", 10, "agi", 10, "luk", 10);

	/** The weapons. Stats are plain integers; a percent affix is in tenths of a percent, a flat affix in K units. */
	private static final List<MerchantDef> DEFS = List.of(
		new MerchantDef("soulreaver", "Soulreaver", MerchantKind.SWORD, NETHERITE_STATS_SWORD,
			Map.of("damage_flat", 40, "damage_pct", 120, "armor_pen", 120),
			List.of("Hits have a 15% chance to loose a soul-fire slash for +50% of the hit"),
			"At +7, +10, +12 and +15: +5% damage and +2% lifesteal each"),
		new MerchantDef("gravecleaver", "Gravecleaver", MerchantKind.AXE, Map.of("str", 10, "vit", 10, "dex", 10),
			Map.of("damage_flat", 40, "damage_pct", 120),
			List.of("Hits cleave foes within 3 blocks of the target for 50% of the hit"),
			"At +7, +10, +12 and +15: +10% cleave each"),
		new MerchantDef("wraithstring", "Wraithstring", MerchantKind.BOW, Map.of("dex", 10, "agi", 10, "luk", 10),
			Map.of("damage_pct", 120, "armor_pen", 120),
			List.of("Draws 25% faster", "Arrows deal +20% damage and pass through 2 foes"),
			"At +7, +10, +12 and +15: +5% arrow damage each, and +1 pierce at +15"),
		new MerchantDef("hexbolt", "Hexbolt", MerchantKind.CROSSBOW, Map.of("dex", 10, "str", 10, "luk", 10),
			Map.of("damage_pct", 120, "armor_pen", 120),
			List.of("Looses 3 bolts a shot", "Each bolt bursts on impact for 1.5 blocks, hurting bodies and breaking no block"),
			"At +7, +10, +12 and +15: +5% damage each, and +0.5 burst radius at +12 and +15"),
		new MerchantDef("wraithpike", "Wraithpike", MerchantKind.SPEAR, Map.of("str", 10, "agi", 10, "vit", 10),
			Map.of("damage_flat", 40, "armor_pen", 120),
			List.of("Reaches 1 block further than a netherite spear", "A charge hit gives Wither II for 3 seconds"),
			"At +7, +10, +12 and +15: +5% damage and +0.25 reach each"),
		new MerchantDef("archmage_staff", "Archmage's Staff", MerchantKind.STAFF, Map.of("int", 10, "dex", 10, "luk", 10),
			Map.of("matk_flat", 40, "matk_pct", 120, "magic_pen", 120),
			List.of("While held, spells deal +25% damage and rest 20% shorter"),
			"At +7, +10, +12 and +15: +10% spell damage and -5% cooldown each")
	);

	/** Decides the slash roll of a hit. A test replaces it. */
	public interface Roller {
		boolean succeeds(LivingEntity holder, RandomSource random, double chancePercent);
	}

	/** Told of each burst, so a test reads what the weapon produced. */
	public interface BurstListener {
		void burst(AbstractArrow bolt, Vec3 at, List<LivingEntity> hurt);
	}

	private static final Roller FAIR = (holder, random, chance) -> RefineStation.fairRoll(random, chance);
	private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
	private static final Map<String, MerchantDef> BY_ID = new LinkedHashMap<>();
	private static volatile Roller roller = FAIR;
	private static volatile @Nullable BurstListener burstListener;

	private MerchantWeapons() {
	}

	/** Registers the items and the hit rules. Called once from the mod initializer, before the item registry freezes. */
	public static void register() {
		for (MerchantDef def : DEFS) {
			Item item = Registry.register(BuiltInRegistries.ITEM, id(def), create(def));
			ITEMS.put(def.id(), item);
			BY_ID.put(def.id(), def);
		}
		// The components bind after every mod has registered, so a sibling's component type exists by then.
		DefaultItemComponentEvents.MODIFY.register(context -> {
			for (MerchantDef def : DEFS) {
				context.modify(ITEMS.get(def.id()), builder -> {
					builder.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("item." + StellaEquips.MOD_ID + "." + def.id(), def.name()));
					fixRolls(builder, def, MerchantWeapons::rpgComponent);
				});
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MerchantWeapons::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(MerchantWeapons::afterDeath);
	}

	public static void setRoller(final @Nullable Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static void setBurstListener(final @Nullable BurstListener listener) {
		burstListener = listener;
	}

	public static List<MerchantDef> all() {
		return Collections.unmodifiableList(DEFS);
	}

	public static @Nullable MerchantDef byId(final String id) {
		return BY_ID.get(id);
	}

	public static Item item(final MerchantDef def) {
		return ITEMS.get(def.id());
	}

	public static Identifier id(final MerchantDef def) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, def.id());
	}

	public static @Nullable MerchantDef def(final ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof MerchantWeapon weapon ? weapon.def() : null;
	}

	private static Item create(final MerchantDef def) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(def));
		Item.Properties properties = new Item.Properties().setId(key);
		return switch (def.kind()) {
			case SWORD -> new MerchantItem(finish(properties.sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)), def);
			case AXE -> new MerchantItem(finish(properties.axe(ToolMaterial.NETHERITE, 5.0F, -3.0F)), def);
			// The numbers of the vanilla netherite spear, then one block more reach.
			case SPEAR -> new MerchantItem(finish(properties.spear(ToolMaterial.NETHERITE, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F)
				.component(DataComponents.ATTACK_RANGE, reach(0))), def);
			case BOW -> new MerchantBowItem(finish(properties.durability(ToolMaterial.NETHERITE.durability()).repairable(Items.NETHERITE_INGOT)
				.enchantable(ENCHANTABILITY)), def);
			case CROSSBOW -> new MerchantCrossbowItem(finish(properties.stacksTo(1).durability(ToolMaterial.NETHERITE.durability())
				.repairable(Items.NETHERITE_INGOT).enchantable(ENCHANTABILITY).component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)), def);
			case STAFF -> new MerchantItem(finish(properties.durability(ToolMaterial.NETHERITE.durability()).repairable(Items.NETHERITE_INGOT)
				.enchantable(ENCHANTABILITY).attributes(staffAttributes()).component(DataComponents.WEAPON, new Weapon(1))), def);
		};
	}

	private static Item.Properties finish(final Item.Properties properties) {
		return properties.rarity(Rarity.EPIC).fireResistant();
	}

	// A staff swings like a stick with a head: enough attack damage to count as a weapon for refine, imbue and the weapon slot.
	private static ItemAttributeModifiers staffAttributes() {
		return ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, STAFF_ATTACK_BONUS, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, STAFF_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.build();
	}

	// ---- fixed rolls ----

	/** The component type of mod-stella-rpg under a path, or null when that mod is not installed. */
	private static @Nullable DataComponentType<?> rpgComponent(final String path) {
		return BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.fromNamespaceAndPath(RPG_NAMESPACE, path));
	}

	/** Puts the weapon's fixed rolls on a component builder. A missing component type leaves the builder as it was. */
	@SuppressWarnings("unchecked")
	public static void fixRolls(final DataComponentMap.Builder builder, final MerchantDef def, final Function<String, DataComponentType<?>> types) {
		DataComponentType<Map<String, Integer>> stats = (DataComponentType<Map<String, Integer>>) types.apply("stat_rolls");
		DataComponentType<Map<String, Integer>> affixes = (DataComponentType<Map<String, Integer>>) types.apply("affix_rolls");
		if (stats == null || affixes == null) {
			return;
		}
		builder.set(stats, Map.copyOf(def.stats()));
		builder.set(affixes, Map.copyOf(def.affixes()));
	}

	// ---- milestones ----

	/** How many milestones a refine level has reached, 0 to 4. */
	public static int milestonesAt(final int level) {
		int reached = 0;
		for (int milestone : MILESTONES) {
			if (level >= milestone) {
				reached++;
			}
		}
		return reached;
	}

	public static int milestones(final ItemStack stack) {
		return milestonesAt(Refine.level(stack));
	}

	/** The share of the hit a Soulreaver adds to its damage. */
	public static double swordDamage(final ItemStack stack) {
		return SWORD_DAMAGE_STEP * milestones(stack);
	}

	/** The share of the damage a Soulreaver hit heals its wielder. */
	public static double lifesteal(final ItemStack stack) {
		return LIFESTEAL_STEP * milestones(stack);
	}

	/** The share of the hit that foes around the target of a Gravecleaver take. */
	public static double cleave(final ItemStack stack) {
		return CLEAVE_BASE + CLEAVE_STEP * milestones(stack);
	}

	public static double bowDamageScale(final ItemStack stack) {
		return 1.0 + BOW_DAMAGE_BASE + BOW_DAMAGE_STEP * milestones(stack);
	}

	/** The foes a Wraithstring arrow passes through: two, and one more at +15. */
	public static int bowPierce(final ItemStack stack) {
		return BOW_PIERCE + (milestones(stack) >= MILESTONES.length ? 1 : 0);
	}

	public static double boltDamageScale(final ItemStack stack) {
		return 1.0 + BOLT_DAMAGE_STEP * milestones(stack);
	}

	/** The burst radius of a Hexbolt bolt: 1.5, and 0.5 more at +12 and again at +15. */
	public static double burstRadius(final ItemStack stack) {
		return BURST_RADIUS + BURST_RADIUS_STEP * Math.max(0, milestones(stack) - 2);
	}

	public static double spearDamage(final ItemStack stack) {
		return SPEAR_DAMAGE_STEP * milestones(stack);
	}

	/** The reach of a Wraithpike after a number of milestones: one block over the vanilla netherite spear, and a quarter more each. */
	public static AttackRange reach(final int milestones) {
		float bonus = REACH_BONUS + REACH_STEP * milestones;
		return new AttackRange(VANILLA_MIN_REACH, VANILLA_MAX_REACH + bonus, VANILLA_MIN_REACH, VANILLA_MAX_CREATIVE_REACH + bonus,
			REACH_HITBOX_MARGIN, REACH_MOB_FACTOR);
	}

	/** Called when a stack's refine level is written, to bring the parts that follow the level in line with it. */
	public static void refined(final ItemStack stack, final int level) {
		MerchantDef def = def(stack);
		if (def != null && def.kind() == MerchantKind.SPEAR) {
			stack.set(DataComponents.ATTACK_RANGE, reach(milestonesAt(level)));
		}
	}

	// ---- bow and crossbow ----

	public static int drawTicks() {
		return (int) Math.round(VANILLA_DRAW_TICKS * BOW_DRAW_TIME);
	}

	/** The power of a Wraithstring released after some ticks of draw: the vanilla curve, stretched over the bow's own full draw. */
	public static float bowPower(final ItemStack weapon, final int ticks) {
		float drawn = ticks / (float) drawTicks();
		return Math.min(1.0F, (drawn * drawn + drawn * 2.0F) / 3.0F);
	}

	/** Sets the damage, the pierce and the mark of an arrow that a merchant bow or crossbow looses. */
	static void shape(final Projectile projectile, final ItemStack weapon) {
		MerchantDef def = def(weapon);
		if (def == null || !(projectile instanceof AbstractArrow arrow)) {
			return;
		}
		double base = baseDamage(arrow);
		if (def.kind() == MerchantKind.BOW) {
			arrow.setBaseDamage(base * bowDamageScale(weapon));
			((AbstractArrowAccessor) arrow).stella$setPierceLevel((byte) (arrow.getPierceLevel() + bowPierce(weapon)));
		} else if (def.kind() == MerchantKind.CROSSBOW) {
			arrow.setBaseDamage(base * boltDamageScale(weapon));
			arrow.addTag(HEXBOLT_TAG);
		}
	}

	private static double baseDamage(final AbstractArrow arrow) {
		return ((AbstractArrowAccessor) arrow).stella$baseDamage();
	}

	/** The number of projectiles a crossbow holds loaded. */
	public static int boltCount(final ItemStack crossbow) {
		ChargedProjectiles charged = crossbow.get(DataComponents.CHARGED_PROJECTILES);
		return charged == null ? 0 : charged.size();
	}

	/** Turns one loaded arrow into three bolts. The extra two cost nothing and leave nothing to pick up. */
	static void loadBolts(final ItemStack crossbow) {
		if (def(crossbow) == null || def(crossbow).kind() != MerchantKind.CROSSBOW) {
			return;
		}
		ChargedProjectiles charged = crossbow.get(DataComponents.CHARGED_PROJECTILES);
		if (charged == null || charged.size() != 1) {
			return;
		}
		ItemStack arrow = charged.itemCopies().findFirst().orElse(ItemStack.EMPTY);
		if (!(arrow.getItem() instanceof ArrowItem)) {
			return;
		}
		List<ItemStack> bolts = new ArrayList<>();
		bolts.add(arrow);
		for (int extra = 1; extra < BOLTS; extra++) {
			ItemStack bolt = arrow.copyWithCount(1);
			bolt.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
			bolts.add(bolt);
		}
		crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(bolts));
	}

	/** The yaw of a bolt in the fan, in degrees: the middle one flies straight. */
	public static float fanAngle(final int index) {
		return (index - (BOLTS - 1) / 2.0F) * FAN_DEGREES;
	}

	/** A bolt strikes something. Hurts every foe within the burst radius of the point, never the entity the bolt struck. */
	public static void impact(final AbstractArrow bolt, final Vec3 at, final @Nullable Entity struck) {
		if (!(bolt.level() instanceof ServerLevel level) || !bolt.entityTags().contains(HEXBOLT_TAG) || !(bolt.getOwner() instanceof ServerPlayer owner)) {
			return;
		}
		ItemStack weapon = bolt.getWeaponItem();
		MerchantDef def = weapon == null ? null : def(weapon);
		if (def == null || def.kind() != MerchantKind.CROSSBOW) {
			return;
		}
		double radius = burstRadius(weapon);
		float amount = (float) (baseDamage(bolt) * bolt.getDeltaMovement().length() * BURST_SHARE);
		AABB box = new AABB(at, at).inflate(radius);
		double reach = radius * radius;
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, box,
			entity -> entity != struck && UniqueTargets.isFoe(owner, entity) && entity.getBoundingBox().distanceToSqr(at) <= reach);
		DamageSource source = level.damageSources().arrow(bolt, owner);
		List<LivingEntity> hurt = new ArrayList<>();
		for (LivingEntity foe : foes) {
			foe.damageCooldownTime = 0;
			if (foe.hurtServer(level, source, amount)) {
				hurt.add(foe);
			}
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 24, radius * 0.4, radius * 0.4, radius * 0.4, 0.03);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
		BurstListener listener = burstListener;
		if (listener != null) {
			listener.burst(bolt, at, List.copyOf(hurt));
		}
	}

	// ---- melee ----

	/** The damage a melee hit carries before armor, after the held weapon has changed it. */
	public static float outgoing(final LivingEntity victim, final DamageSource source, final float amount) {
		ServerPlayer holder = meleeHolder(victim, source);
		if (amount <= 0.0F || holder == null) {
			return amount;
		}
		ItemStack weapon = holder.getMainHandItem();
		MerchantDef def = def(weapon);
		if (def == null) {
			return amount;
		}
		return switch (def.kind()) {
			case SWORD -> (float) (amount * (1.0 + swordDamage(weapon)));
			case SPEAR -> (float) (amount * (1.0 + spearDamage(weapon)));
			default -> amount;
		};
	}

	/** The player whose weapon hit, for a plain melee or spear blow; null for every other source, extra hits included. */
	private static @Nullable ServerPlayer meleeHolder(final LivingEntity victim, final DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer holder && holder != victim && source.getDirectEntity() == holder
			&& source.is(DamageTypeTags.IS_PLAYER_ATTACK)) {
			return holder;
		}
		return null;
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (!blocked) {
			afterHit(victim, source, damage);
		}
	}

	// The damage event does not fire for a hit that kills, so the death event runs the same hooks.
	private static void afterDeath(final LivingEntity victim, final DamageSource source) {
		afterHit(victim, source, UniqueDispatch.taken(victim, 0.0F));
	}

	private static void afterHit(final LivingEntity victim, final DamageSource source, final float dealt) {
		ServerPlayer holder = meleeHolder(victim, source);
		if (dealt <= 0.0F || holder == null || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack weapon = holder.getMainHandItem();
		MerchantDef def = def(weapon);
		if (def == null) {
			return;
		}
		switch (def.kind()) {
			case SWORD -> slash(level, holder, victim, weapon, dealt);
			case AXE -> RefineEffects.withoutProcs(
				() -> UniqueDamage.area(level, victim.position(), CLEAVE_RADIUS, holder, dealt * (float) cleave(weapon), false, victim));
			case SPEAR -> {
				if (holder.isUsingItem() && holder.getUseItem().is(weapon.getItem()) && victim.isAlive()) {
					victim.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_AMPLIFIER), holder);
				}
			}
			default -> {
			}
		}
	}

	private static void slash(final ServerLevel level, final ServerPlayer holder, final LivingEntity victim, final ItemStack weapon, final float dealt) {
		double lifesteal = lifesteal(weapon);
		if (lifesteal > 0.0 && holder.isAlive()) {
			holder.heal((float) (dealt * lifesteal));
		}
		if (!roller.succeeds(holder, holder.getRandom(), SLASH_PERCENT)) {
			return;
		}
		// Soul fire ignores armor, so the slash is the whole share of the hit and not a share of what armor left.
		RefineEffects.withoutProcs(() -> UniqueDamage.deal(level, victim, holder, dealt * (float) SLASH_SHARE, true));
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(), victim.getY(0.5), victim.getZ(), 16, 0.3, 0.4, 0.3, 0.04);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.6), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.5F);
	}

	// ---- tooltip ----

	/** The tooltip lines of a stack: the Requires and Desc block, the milestone bonuses, and what the refine level gives now. */
	public static List<Component> tooltip(final ItemStack stack) {
		MerchantDef def = def(stack);
		if (def == null) {
			return List.of();
		}
		List<Component> lines = new ArrayList<>();
		lines.add(DescriptionLines.requires(styled(def.kind().requirement(), ChatFormatting.WHITE)));
		lines.add(DescriptionLines.desc(styled(String.join(". ", def.effect()) + ".", ChatFormatting.GOLD)));
		lines.add(styled(def.milestones(), ChatFormatting.GRAY));
		int reached = milestones(stack);
		if (reached > 0) {
			lines.add(styled("Now: " + now(def, stack), ChatFormatting.GREEN));
		}
		return lines;
	}

	private static Component styled(final String text, final ChatFormatting color) {
		return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
	}

	private static String now(final MerchantDef def, final ItemStack stack) {
		int reached = milestones(stack);
		return switch (def.kind()) {
			case SWORD -> "+" + percent(swordDamage(stack)) + " damage, " + percent(lifesteal(stack)) + " lifesteal";
			case AXE -> "cleave " + percent(cleave(stack));
			case BOW -> "+" + percent(bowDamageScale(stack) - 1.0) + " arrow damage, pierce " + bowPierce(stack);
			case CROSSBOW -> "+" + percent(boltDamageScale(stack) - 1.0) + " damage, burst radius " + trim(burstRadius(stack));
			case SPEAR -> "+" + percent(spearDamage(stack)) + " damage, +" + trim(REACH_BONUS + REACH_STEP * reached) + " reach";
			case STAFF -> "+" + percent(0.25 + 0.10 * reached) + " spell damage, -" + percent(0.20 + 0.05 * reached) + " cooldown";
		};
	}

	private static String percent(final double share) {
		return trim(share * 100.0) + "%";
	}

	private static String trim(final double value) {
		double rounded = Math.round(value * 100.0) / 100.0;
		return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.format(Locale.ROOT, "%s", rounded);
	}
}
