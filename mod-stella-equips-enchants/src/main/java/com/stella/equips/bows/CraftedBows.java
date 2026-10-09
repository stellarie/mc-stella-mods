package com.stella.equips.bows;

import com.stella.equips.StellaEquips;
import com.stella.equips.mixin.AbstractArrowAccessor;
import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineStation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The crafted bows and crossbows, after the Ragnarok Online archer line. Each has its own draw or charge time, arrow
 * damage and durability, one signature effect, and the refine milestones that grow that effect. The catalog is
 * {@link #DEFS}; every number of a milestone lives in {@link #TABLE}.
 */
public final class CraftedBows {
	public static final int VANILLA_DRAW_TICKS = 20;
	public static final int VANILLA_CHARGE_TICKS = 25;
	/** The chance in percent that a Hunter Bow keeps the arrow, before its milestones. */
	public static final double KEEP_BASE_CHANCE = 20.0;
	/** The share of the damage dealt that a Rudra Bow heals, before its milestones. */
	public static final double HEAL_BASE_SHARE = 0.05;
	/** The knockback level of a Siege Arbalest bolt: the push a Punch enchantment of this level gives. */
	public static final double KNOCKBACK_LEVEL = 2.0;
	private static final double KNOCKBACK_PER_LEVEL = 0.6;
	public static final int ENCHANTABILITY = 10;
	/** The refine levels at which the signature grows. */
	public static final int[] MILESTONES = {Refine.KEEN_LEVEL, Refine.SEARING_LEVEL, Refine.RENDING_LEVEL, Refine.ASCENDED_LEVEL};
	/**
	 * The signature bonus at each milestone. Draw, charge and damage are shares, keep and heal are percentage points,
	 * and pierce is extra foes.
	 */
	private static final Map<Signature, double[]> TABLE = Map.of(
		Signature.DRAW, new double[] {0.05, 0.10, 0.15, 0.20},
		Signature.CHARGE, new double[] {0.05, 0.10, 0.15, 0.20},
		Signature.DAMAGE, new double[] {0.05, 0.10, 0.15, 0.25},
		Signature.KEEP, new double[] {5, 10, 15, 20},
		Signature.HEAL, new double[] {1, 2, 3, 5},
		Signature.PIERCE, new double[] {0, 1, 1, 2}
	);

	private static final List<CraftedBowDef> DEFS = List.of(
		new CraftedBowDef("hunter_bow", "Hunter Bow", false, 448, 0.95, 1.00, 1.0, Signature.KEEP, 0, false, Items.LEATHER),
		new CraftedBowDef("composite_bow", "Composite Bow", false, 576, 0.85, 1.10, 1.0, Signature.DRAW, 0, false, Items.IRON_INGOT),
		new CraftedBowDef("gakkung", "Gakkung", false, 400, 0.65, 0.90, 1.0, Signature.DRAW, 0, false, Items.GOLD_INGOT),
		new CraftedBowDef("great_bow", "Great Bow", false, 1200, 1.25, 1.40, 1.3, Signature.DAMAGE, 0, false, Items.DIAMOND),
		new CraftedBowDef("rudra_bow", "Rudra Bow", false, 2400, 1.15, 1.55, 1.0, Signature.HEAL, 0, false, Items.NETHERITE_INGOT),
		new CraftedBowDef("arbalest", "Arbalest", true, 700, 1.00, 1.30, 1.0, Signature.DAMAGE, 0, false, Items.IRON_INGOT),
		new CraftedBowDef("repeater", "Repeater", true, 560, 0.55, 0.85, 1.0, Signature.CHARGE, 0, false, Items.COPPER_INGOT),
		new CraftedBowDef("ballista", "Ballista", true, 1300, 1.50, 1.70, 1.0, Signature.PIERCE, 2, false, Items.DIAMOND),
		new CraftedBowDef("siege_arbalest", "Siege Arbalest", true, 2600, 1.35, 1.85, 1.0, Signature.PIERCE, 3, true, Items.NETHERITE_INGOT)
	);

	/** Decides the keep-the-arrow roll of a shot. A test replaces it. */
	public interface Roller {
		boolean succeeds(LivingEntity holder, RandomSource random, double chancePercent);
	}

	private static final Roller FAIR = (holder, random, chance) -> RefineStation.fairRoll(random, chance);
	private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
	private static final Map<String, CraftedBowDef> BY_ID = new LinkedHashMap<>();
	private static volatile Roller roller = FAIR;

	private CraftedBows() {
	}

	/** Registers the items and the hit rules. Called once from the mod initializer, before the item registry freezes. */
	public static void register() {
		for (CraftedBowDef def : DEFS) {
			Item item = Registry.register(BuiltInRegistries.ITEM, id(def), create(def));
			ITEMS.put(def.id(), item);
			BY_ID.put(def.id(), def);
		}
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CraftedBows::afterDamage);
	}

	public static void setRoller(final @Nullable Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static List<CraftedBowDef> all() {
		return Collections.unmodifiableList(DEFS);
	}

	public static @Nullable CraftedBowDef byId(final String id) {
		return BY_ID.get(id);
	}

	public static Item item(final CraftedBowDef def) {
		return ITEMS.get(def.id());
	}

	public static Identifier id(final CraftedBowDef def) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, def.id());
	}

	public static @Nullable CraftedBowDef def(final ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof CraftedWeapon weapon ? weapon.def() : null;
	}

	private static Item create(final CraftedBowDef def) {
		Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(def))).durability(def.durability())
			.repairable(def.repair()).enchantable(ENCHANTABILITY)
			.rarity(def.repair() == Items.NETHERITE_INGOT ? Rarity.RARE : Rarity.UNCOMMON);
		if (def.crossbow()) {
			return new CraftedCrossbowItem(properties.stacksTo(1).component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY), def);
		}
		return new CraftedBowItem(properties, def);
	}

	/** The index of the highest milestone a refine level has reached, or -1 below the first. */
	private static int tier(final int level) {
		int tier = -1;
		for (int index = 0; index < MILESTONES.length; index++) {
			if (level >= MILESTONES[index]) {
				tier = index;
			}
		}
		return tier;
	}

	/** The signature bonus of a stack at its refine level, in the unit of its table row; 0 below +7. */
	public static double signature(final ItemStack stack) {
		CraftedBowDef def = def(stack);
		int tier = def == null ? -1 : tier(Refine.level(stack));
		return tier < 0 ? 0.0 : TABLE.get(def.signature())[tier];
	}

	private static double bonus(final ItemStack stack, final Signature signature) {
		CraftedBowDef def = def(stack);
		return def != null && def.signature() == signature ? signature(stack) : 0.0;
	}

	/** A vanilla draw or charge time, scaled by the weapon's own time and its draw or charge milestone. */
	public static int scaledTicks(final ItemStack stack, final int vanillaTicks) {
		CraftedBowDef def = def(stack);
		if (def == null) {
			return vanillaTicks;
		}
		double cut = bonus(stack, Signature.DRAW) + bonus(stack, Signature.CHARGE);
		return Math.max(1, (int) Math.round(vanillaTicks * def.time() * (1.0 - cut)));
	}

	/** The ticks a full draw or a full charge takes, before any Quick Charge. */
	public static int drawTicks(final ItemStack stack) {
		CraftedBowDef def = def(stack);
		return scaledTicks(stack, def != null && def.crossbow() ? VANILLA_CHARGE_TICKS : VANILLA_DRAW_TICKS);
	}

	/** The power of a bow released after some ticks of draw: the vanilla curve, stretched over the bow's own full draw. */
	public static float power(final ItemStack stack, final int ticks) {
		float drawn = ticks / (float) drawTicks(stack);
		return Math.min(1.0F, (drawn * drawn + drawn * 2.0F) / 3.0F);
	}

	public static double keepChance(final ItemStack stack) {
		return Math.min(100.0, KEEP_BASE_CHANCE + bonus(stack, Signature.KEEP));
	}

	/** The arrow damage scale against a vanilla arrow, with the damage milestone. */
	public static double damageScale(final ItemStack stack) {
		CraftedBowDef def = def(stack);
		return def == null ? 1.0 : def.damage() * (1.0 + bonus(stack, Signature.DAMAGE));
	}

	public static double healShare(final ItemStack stack) {
		CraftedBowDef def = def(stack);
		return def == null || def.signature() != Signature.HEAL ? 0.0 : HEAL_BASE_SHARE + signature(stack) / 100.0;
	}

	/** The extra foes an arrow of the weapon passes through. */
	public static int pierce(final ItemStack stack) {
		CraftedBowDef def = def(stack);
		return def == null ? 0 : def.basePierce() + (int) bonus(stack, Signature.PIERCE);
	}

	static boolean rollKeep(final LivingEntity holder, final ItemStack weapon) {
		CraftedBowDef def = def(weapon);
		return def != null && def.signature() == Signature.KEEP && roller.succeeds(holder, holder.getRandom(), keepChance(weapon));
	}

	/**
	 * Sets the damage and pierce of an arrow that a crafted weapon looses. The damage divides by the arrow speed, so
	 * the hit deals the catalog ratio: speed times base damage is what the game multiplies at the hit.
	 */
	static void shape(final Projectile projectile, final ItemStack weapon) {
		CraftedBowDef def = def(weapon);
		if (def == null || !(projectile instanceof AbstractArrow arrow)) {
			return;
		}
		arrow.setBaseDamage(RefineEffects.baseDamage(arrow) * damageScale(weapon) / def.speed());
		int pierce = pierce(weapon);
		if (pierce > 0) {
			((AbstractArrowAccessor) arrow).stella$setPierceLevel((byte) (arrow.getPierceLevel() + pierce));
		}
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (blocked || damage <= 0.0F || !(source.getDirectEntity() instanceof AbstractArrow arrow)) {
			return;
		}
		ItemStack weapon = arrow.getWeaponItem();
		CraftedBowDef def = weapon == null ? null : def(weapon);
		// The refine Volley arrow carries the weapon too, but one shot counts one hit.
		if (def == null || arrow.entityTags().contains(RefineEffects.VOLLEY_TAG)) {
			return;
		}
		if (def.signature() == Signature.HEAL && source.getEntity() instanceof LivingEntity shooter && shooter != victim && shooter.isAlive()) {
			shooter.heal((float) (damage * healShare(weapon)));
		}
		if (def.knockback()) {
			// The same push AbstractArrow gives for Punch, which a crafted bolt has no enchantment for.
			double resistance = Math.max(0.0, 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			Vec3 push = arrow.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize().scale(KNOCKBACK_LEVEL * KNOCKBACK_PER_LEVEL * resistance);
			if (push.lengthSqr() > 0.0) {
				victim.push(push.x, 0.1, push.z);
			}
		}
	}

	/** The line a tooltip shows for the signature effect at the stack's refine level. */
	public static String effectLine(final ItemStack stack) {
		CraftedBowDef def = def(stack);
		if (def == null) {
			return "";
		}
		double bonus = signature(stack);
		return switch (def.signature()) {
			case KEEP -> "Keeps the arrow: " + trim(keepChance(stack)) + "% chance";
			case DRAW -> "Draw time: " + drawTicks(stack) + " ticks" + cut(bonus);
			case CHARGE -> "Charge time: " + drawTicks(stack) + " ticks" + cut(bonus);
			case DAMAGE -> "Arrow damage: x" + String.format(Locale.ROOT, "%.2f", damageScale(stack));
			case HEAL -> "Heals " + trim(healShare(stack) * 100.0) + "% of the damage dealt";
			case PIERCE -> "Pierces " + pierce(stack) + " foes" + (def.knockback() ? " and knocks back" : "");
		};
	}

	public static Component effectText(final ItemStack stack) {
		return Component.literal(effectLine(stack)).withStyle(style -> style.withColor(ChatFormatting.GOLD).withItalic(false));
	}

	private static String cut(final double share) {
		return share > 0.0 ? " (-" + trim(share * 100.0) + "%)" : "";
	}

	private static String trim(final double value) {
		double rounded = Math.round(value * 10.0) / 10.0;
		return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
	}
}
