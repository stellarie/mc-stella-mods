package com.stella.equips.imbue;

import com.stella.equips.StellaEquips;
import com.stella.equips.refine.Refine;
import com.stella.equips.unique.UniqueDamage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The nether star imbue. The anvil adds one imbue to a weapon or an armor piece for each star.
 * The count lives in the stack's custom data under {@link #KEY}, so mod-stella-rpg and this mod need no class link.
 * Each imbue also asks the rpg share {@link #GRANT_SHARE} for {@link #STAT_PER_IMBUE} on all six stats.
 * Without that share the imbue still works and skips the grant.
 */
public final class NetherImbue {
	public static final String KEY = "stella_nether_star";
	public static final String GRANT_SHARE = "mod-stella-rpg:grant-bonus";
	public static final int STAT_PER_IMBUE = 10;
	/** The emerald price of the next imbue is this much for each imbue the piece will then hold. */
	public static final int PRICE_STEP = 16;
	public static final int WITHER_TICKS = 100;
	/** Amplifier 1 is Wither II. */
	public static final int WITHER_AMPLIFIER = 1;
	/** A hit on a withered target deals this many times its damage. The imbue count does not change it. */
	public static final float BONUS_MULTIPLIER = 3.0F;
	/** How often a worn imbue cures a Wither that was already on the wearer, in ticks. */
	public static final int CURE_INTERVAL = 10;
	private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	public static final List<String> STATS = List.of("str", "agi", "vit", "dex", "int", "luk");
	private static final String LORE_PREFIX = "Nether Star x";

	private NetherImbue() {
	}

	/** How many imbues the stack holds. */
	public static int count(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : Math.max(0, data.copyTag().getIntOr(KEY, 0));
	}

	/** The emerald price of the next imbue on a piece that holds {@code count} imbues. */
	public static int price(final int count) {
		return PRICE_STEP * (count + 1);
	}

	/** Armor, a weapon, or a bow. A shield, a book and a plain item are refused. */
	public static boolean eligible(final ItemStack stack) {
		return !stack.isEmpty() && (Refine.isArmor(stack) || Refine.isWeapon(stack));
	}

	/** A copy of the piece with one more imbue, its lore line, and the stat grant when the rpg share exists. */
	public static ItemStack imbued(final ItemStack input) {
		ItemStack result = input.copy();
		int count = count(input) + 1;
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putInt(KEY, count));
		result.set(DataComponents.LORE, withLine(result, count));
		grant(result);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static void grant(final ItemStack stack) {
		Object share = FabricLoader.getInstance().getObjectShare().get(GRANT_SHARE);
		if (!(share instanceof Function<?, ?> function)) {
			return;
		}
		Map<String, Integer> delta = new LinkedHashMap<>();
		for (String stat : STATS) {
			delta.put(stat, STAT_PER_IMBUE);
		}
		try {
			((Function<Object[], Boolean>) function).apply(new Object[] {stack, delta});
		} catch (RuntimeException failure) {
			StellaEquips.LOGGER.warn("The stat grant share {} failed: {}", GRANT_SHARE, failure.toString());
		}
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(NetherImbue::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(NetherImbue::tick);
	}

	/** True when any worn armor piece holds an imbue. */
	public static boolean wearsImbue(final LivingEntity entity) {
		for (EquipmentSlot slot : ARMOR) {
			if (count(entity.getItemBySlot(slot)) > 0) {
				return true;
			}
		}
		return false;
	}

	/** Armor with an imbue refuses the Wither effect and every other effect lands as usual. */
	public static boolean blocksEffect(final LivingEntity entity, final MobEffectInstance effect) {
		return effect.is(MobEffects.WITHER) && wearsImbue(entity);
	}

	/** Removes a Wither that was on the entity before it put on imbued armor. */
	public static void cure(final LivingEntity entity) {
		if (entity.hasEffect(MobEffects.WITHER) && wearsImbue(entity)) {
			entity.removeEffect(MobEffects.WITHER);
		}
	}

	/**
	 * The weapon of a melee or an arrow hit, or empty for any other damage.
	 * Unique damage, thorns and spells name the attacker as the direct entity too, so they are listed out.
	 */
	public static ItemStack weaponOf(final DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity attacker) || source.is(UniqueDamage.UNIQUE) || source.is(UniqueDamage.UNIQUE_PIERCE)
			|| source.is(DamageTypes.THORNS) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)) {
			return ItemStack.EMPTY;
		}
		Entity direct = source.getDirectEntity();
		if (direct != attacker && !(direct instanceof AbstractArrow)) {
			return ItemStack.EMPTY;
		}
		ItemStack weapon = source.getWeaponItem();
		return weapon == null ? ItemStack.EMPTY : weapon;
	}

	/** The hit's damage before blocking and armor: times {@link #BONUS_MULTIPLIER} for an imbued weapon on a withered victim. */
	public static float outgoing(final LivingEntity victim, final DamageSource source, final float amount) {
		if (amount <= 0.0F || !victim.hasEffect(MobEffects.WITHER) || count(weaponOf(source)) <= 0 || source.getEntity() == victim) {
			return amount;
		}
		return amount * BONUS_MULTIPLIER;
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (blocked || !victim.isAlive() || victim.level().isClientSide() || source.getEntity() == victim || count(weaponOf(source)) <= 0) {
			return;
		}
		// The source is the attacker, and canBeAffected still refuses the effect for Wither-proof victims.
		victim.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_AMPLIFIER), source.getEntity());
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % CURE_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			cure(player);
		}
	}

	// Foreign lines stay, the refine lines among them. The imbue line goes last.
	private static ItemLore withLine(final ItemStack stack, final int count) {
		List<Component> lines = new ArrayList<>();
		for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			if (!line.getString().startsWith(LORE_PREFIX)) {
				lines.add(line);
			}
		}
		lines.add(Component.literal(LORE_PREFIX + count).withStyle(style -> style.withColor(ChatFormatting.DARK_PURPLE).withItalic(false)));
		return new ItemLore(lines);
	}
}
