package com.stella.tower;

import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Runes: an element, and a floor that sets their strength on the tower's own curve. A weapon's rune
 * multiplies its hits, the added part elemental; an armor piece's rune turns tower mobs' blows, and more
 * those of its own element.
 */
public final class TowerRunes {
	public static final int EMERALDS = 64;
	public static final double WEAPON_MAX = 100.0;
	public static final double ARMOR_MAX = 20.0;
	public static final double TIER_EXPONENT = 0.9;
	public static final int ARMOR_PIECES = 4;
	public static final double OPPOSED = 1.5;
	public static final double SAME = 0.5;
	public static final double OWN_ELEMENT_BASE = 0.05;
	public static final double OWN_ELEMENT_CAP = 0.80;
	public static final double DROP_CHANCE = 0.005;

	static final EquipmentSlot[] ARMOR_SLOTS ={EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** The spells mod's books, which take runes too; empty when that mod is absent. */
	static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> STAVES = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("mod-stella-tower", "staves"));
	static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> SPELLBOOKS = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse("mod-stella-spells:spellbooks"));

	/** What an item takes a rune as. */
	public enum Slot { WEAPON, ARMOR, BOOK }

	private TowerRunes() {
	}

	public static void register() {
		// Runed gear names its rune.
		net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry.addLast(TowerItems.RUNED, runes -> (context, lines, flag, components) -> {
			for (RuneData rune : runes) {
				DescriptionLines.add(lines, Component.translatable("item.mod-stella-tower.runed", Component.literal(rune.element().label()).withStyle(rune.element().color()), rune.floor())
					.withStyle(ChatFormatting.BLUE));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			TowerMobs.State state = TowerMobs.stateOf(entity);
			// A ghost leaves its own rune, not one of the eight.
			if (state == null || entity instanceof Ghostly || !(entity.level() instanceof ServerLevel level)
				|| entity.getLastHurtByPlayer() == null || !level.getGameRules().get(GameRules.MOB_DROPS)) {
				return;
			}
			double chance = PINNED_DROP.getOrDefault(state.floor(), dropChanceOf(entity));
			if (dropRoll(entity) < chance) {
				TowerElement element = TowerElement.values()[entity.getRandom().nextInt(TowerElement.values().length)];
				entity.spawnAtLocation(level, rune(element, state.floor()));
				StellaTower.LOGGER.debug("[TOWER] a {} rune of floor {} dropped", element, state.floor());
			}
		});
	}

	private static final Map<Integer, Double> PINNED_DROP = new java.util.concurrent.ConcurrentHashMap<>();

	private static volatile @Nullable Double pinnedRoll;

	/** The test seam: pins the roll a drop is made against. */
	public static void pinDropRoll(final @Nullable Double roll) {
		pinnedRoll = roll;
	}

	private static double dropRoll(final LivingEntity mob) {
		Double pin = pinnedRoll;
		return pin != null ? pin : mob.getRandom().nextDouble();
	}

	/** The test seam: pins, by the mob's floor, the chance a slain tower mob leaves a rune. */
	public static void pinDropChance(final int floor, final @Nullable Double chance) {
		if (chance == null) {
			PINNED_DROP.remove(floor);
		} else {
			PINNED_DROP.put(floor, chance);
		}
	}

	/** The chance this tower mob, when slain, leaves a rune: a shaman's own, else its rank's. */
	public static double dropChanceOf(final LivingEntity mob) {
		TowerMobs.State state = TowerMobs.stateOf(mob);
		if (TowerShamans.isShaman(mob)) {
			return TowerShamans.RUNE_CHANCE;
		}
		return state == null ? 0.0 : dropChance(state.rank());
	}

	/** The chance a slain tower mob of any rank leaves a rune. */
	public static double dropChance(final TowerRank rank) {
		return DROP_CHANCE;
	}

	/** A rune's power for its percentages: its floor to 100, then slower, and no further past 1000. */
	public static double power(final int floor) {
		int r = Math.max(0, Math.min(floor, (int) TowerCurve.END));
		return r <= 100 ? r : 100.0 + 10.0 * Math.sqrt(r - 100.0);
	}

	/** A rune's power as the piece of gear it is set into carries it: half as much again on void gear. */
	public static double power(final ItemStack gear, final RuneData rune) {
		return VoidGear.power(gear, power(rune.floor()));
	}

	public static double weaponMultiplier(final int floor) {
		return Math.pow(1.0 + (WEAPON_MAX - 1.0) * TowerCurve.s(floor), TIER_EXPONENT);
	}

	/** One armor piece's share: four pieces together divide by the armor tier. */
	public static double armorDivisor(final int floor) {
		return Math.pow(1.0 + (ARMOR_MAX - 1.0) * TowerCurve.s(floor), TIER_EXPONENT / ARMOR_PIECES);
	}

	public static ItemStack rune(final TowerElement element, final int floor) {
		ItemStack stack = new ItemStack(TowerItems.RUNE);
		stack.set(TowerItems.RUNE_DATA, new RuneData(element, floor));
		return stack;
	}

	/** What a piece of gear takes a rune as, or null if it takes none. */
	public static @Nullable Slot slotOf(final ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !stack.is(Items.ELYTRA)) {
			return Slot.ARMOR;
		}
		if (stack.is(SPELLBOOKS)) {
			return Slot.BOOK;
		}
		if (stack.has(DataComponents.WEAPON) || stack.is(STAVES) || stack.getItem() instanceof ProjectileWeaponItem) {
			return Slot.WEAPON;
		}
		return null;
	}

	/**
	 * The gear with the rune set into it; empty if the gear takes none, or a weapon already holds this
	 * element at an equal or higher floor. A weapon keeps its other runes; other gear's rune is replaced.
	 */
	public static ItemStack apply(final ItemStack gear, final RuneData rune) {
		// One piece at a time: a rune set into a stack would rune every piece of it.
		Slot slot = slotOf(gear);
		if (slot == null || gear.getCount() != 1) {
			return ItemStack.EMPTY;
		}
		java.util.List<RuneData> held = new java.util.ArrayList<>();
		if (slot == Slot.WEAPON) {
			for (RuneData old : runesOf(gear)) {
				if (old.element() != rune.element()) {
					held.add(old);
				} else if (old.floor() >= rune.floor()) {
					return ItemStack.EMPTY;
				}
			}
		}
		held.add(rune);
		ItemStack runed = gear.copy();
		runed.set(TowerItems.RUNED, java.util.List.copyOf(held));
		return runed;
	}

	/** Every rune the gear holds. */
	public static java.util.List<RuneData> runesOf(final ItemStack gear) {
		java.util.List<RuneData> runes = gear.get(TowerItems.RUNED);
		return runes == null ? java.util.List.of() : runes;
	}

	/** The gear's first rune, the only one on armor and books. */
	public static @Nullable RuneData runeOf(final ItemStack gear) {
		java.util.List<RuneData> runes = runesOf(gear);
		return runes.isEmpty() ? null : runes.get(0);
	}

	/**
	 * A hit once the attacker's weapon rune has had its say: multiplied by the rune's tier, the added part
	 * elemental, dealt half again to the rune's opposite and half to its own element.
	 */
	public static float weapon(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!struckWith(source)) {
			return amount;
		}
		ItemStack weapon = weaponOf(source);
		if (slotOf(weapon) != Slot.WEAPON) {
			return amount;
		}
		TowerMobs.State state = TowerMobs.stateOf(victim);
		double added = 0.0;
		float result = amount;
		for (RuneData rune : runesOf(weapon)) {
			double part = amount * (weaponMultiplier(rune.floor()) - 1.0);
			if (state != null && state.element() == rune.element().opposite()) {
				part *= OPPOSED;
			} else if (state != null && state.element() == rune.element() && !VoidGear.isVoid(weapon)) {
				// Void weapons strike their own element in full.
				part *= SAME;
			}
			added += part;
			result = RuneEffects.modify(victim, source, result, rune);
		}
		return (float) (result + added);
	}

	private static final int RPG_WEAPON_SLOT = 35;

	/** The weapon behind a blow. A spell hit has none, so a held staff stands in for it. */
	public static ItemStack weaponOf(final DamageSource source) {
		ItemStack weapon = source.getWeaponItem();
		// The held item stands in as the weapon, so a spell cast from a book reports the book.
		if ((weapon == null || slotOf(weapon) != Slot.WEAPON) && source.getEntity() instanceof Player player) {
			if (player.getMainHandItem().is(STAVES)) {
				return player.getMainHandItem();
			}
			// A spell is cast from a held book, so the staff sits in the RPG weapon slot.
			ItemStack slotted = player.getInventory().getItem(RPG_WEAPON_SLOT);
			if (slotted.is(STAVES)) {
				return slotted;
			}
		}
		return weapon;
	}

	/** A blow a player struck with the weapon in hand, not one their thorns or their armor returned. */
	public static boolean struckWith(final DamageSource source) {
		return source.getEntity() instanceof Player && weaponOf(source) != null && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)
			&& !source.is(RuneEffects.BURST);
	}

	/** A tower mob's blow, of its element, once the victim's armor runes have turned it. */
	public static float armor(final LivingEntity victim, final TowerElement element, final float amount) {
		double divisor = 1.0;
		double own = 0.0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack piece = victim.getItemBySlot(slot);
			RuneData rune = slotOf(piece) == Slot.ARMOR ? runeOf(piece) : null;
			if (rune != null) {
				divisor *= armorDivisor(rune.floor());
				if (rune.element() == element) {
					own += (OWN_ELEMENT_BASE + power(piece, rune) / 1000.0) * (VoidGear.isVoid(piece) ? VoidGear.OWN_ELEMENT_FACTOR : 1.0);
				}
			}
		}
		return (float) (amount / divisor * (1.0 - Math.min(OWN_ELEMENT_CAP, own)));
	}

	/** The rune's tier and each effect with its number for the stats, as the full armor set carries it. */
	public static void describe(final RuneData rune, final java.util.function.ToIntFunction<RuneStats.Stat> stats, final Consumer<Component> lines) {
		lines.accept(floorLine(rune));
		lines.accept(weaponLine(rune));
		lines.accept(weaponEffect(rune, stats));
		lines.accept(armorLine(rune));
		lines.accept(armorEffect(rune, stats));
	}

	/** The body of a rune's Desc line: its weapon effect, then its armor effect, with the numbers for the stats. */
	public static Component effects(final RuneData rune, final java.util.function.ToIntFunction<RuneStats.Stat> stats) {
		return weaponEffect(rune, stats).append(" ").append(armorEffect(rune, stats));
	}

	/** The lines that follow a rune's Desc line: its floor, and what it multiplies on a weapon and on armor. */
	public static void numbers(final RuneData rune, final Consumer<Component> lines) {
		lines.accept(floorLine(rune));
		lines.accept(weaponLine(rune));
		lines.accept(armorLine(rune));
	}

	private static net.minecraft.network.chat.MutableComponent floorLine(final RuneData rune) {
		return Component.translatable("item.mod-stella-tower.rune.floor", rune.floor()).withStyle(ChatFormatting.GRAY);
	}

	private static net.minecraft.network.chat.MutableComponent weaponLine(final RuneData rune) {
		return Component.translatable("item.mod-stella-tower.rune.weapon", String.format(java.util.Locale.ROOT, "%.1f", weaponMultiplier(rune.floor())),
			String.format(java.util.Locale.ROOT, "%.1f", OPPOSED),
			Component.literal(rune.element().opposite().label()).withStyle(rune.element().opposite().color()),
			String.format(java.util.Locale.ROOT, "%.1f", SAME)).withStyle(ChatFormatting.BLUE);
	}

	private static net.minecraft.network.chat.MutableComponent weaponEffect(final RuneData rune, final java.util.function.ToIntFunction<RuneStats.Stat> stats) {
		return Component.translatable("item.mod-stella-tower.rune.weapon." + rune.element().getSerializedName(), weaponNumber(rune.element(), stats)).withStyle(ChatFormatting.AQUA);
	}

	private static net.minecraft.network.chat.MutableComponent armorLine(final RuneData rune) {
		return Component.translatable("item.mod-stella-tower.rune.armor", String.format(java.util.Locale.ROOT, "%.2f", armorDivisor(rune.floor())),
			String.format(java.util.Locale.ROOT, "%.0f", (OWN_ELEMENT_BASE + power(rune.floor()) / 1000.0) * 100.0),
			String.format(java.util.Locale.ROOT, "%.0f", OWN_ELEMENT_CAP * 100.0)).withStyle(ChatFormatting.BLUE);
	}

	private static net.minecraft.network.chat.MutableComponent armorEffect(final RuneData rune, final java.util.function.ToIntFunction<RuneStats.Stat> stats) {
		return Component.translatable("item.mod-stella-tower.rune.armor." + rune.element().getSerializedName(), armorNumber(rune.element(), stats)).withStyle(ChatFormatting.AQUA);
	}

	/** The number a weapon effect shows, in percent. */
	public static String weaponNumber(final TowerElement element, final java.util.function.ToIntFunction<RuneStats.Stat> stats) {
		return RuneValues.show(switch (element) {
			case FIRE -> RuneValues.fireCombust(stats.applyAsInt(RuneStats.Stat.STR));
			case ICE -> RuneValues.iceBonus(stats.applyAsInt(RuneStats.Stat.INT));
			case WATER -> RuneValues.waterSoak(stats.applyAsInt(RuneStats.Stat.LUK));
			case LIGHTNING -> RuneValues.lightningChain(stats.applyAsInt(RuneStats.Stat.DEX));
			case WIND -> RuneValues.windAirborne(stats.applyAsInt(RuneStats.Stat.AGI));
			case VOID -> RuneValues.voidShredCap(stats.applyAsInt(RuneStats.Stat.INT));
			case HOLY -> RuneValues.holyHeal(stats.applyAsInt(RuneStats.Stat.VIT));
			case DARK -> RuneValues.darkLowHealth(stats.applyAsInt(RuneStats.Stat.STR));
		});
	}

	/** The number an armor effect shows for a full set: percent, or seconds for Dark. */
	public static String armorNumber(final TowerElement element, final java.util.function.ToIntFunction<RuneStats.Stat> stats) {
		return RuneValues.show(switch (element) {
			case FIRE -> RuneValues.fireThorns(stats.applyAsInt(RuneStats.Stat.VIT), 1.0);
			case ICE -> RuneValues.iceFreeze(stats.applyAsInt(RuneStats.Stat.AGI), 1.0);
			case WATER -> RuneValues.waterRegen(stats.applyAsInt(RuneStats.Stat.VIT), 1.0);
			case LIGHTNING -> RuneValues.lightningCharge(stats.applyAsInt(RuneStats.Stat.DEX), 1.0);
			case WIND -> RuneValues.windDodge(stats.applyAsInt(RuneStats.Stat.AGI), 1.0);
			case VOID -> RuneValues.voidResist(stats.applyAsInt(RuneStats.Stat.INT), 1.0);
			case HOLY -> RuneValues.holyUndead(stats.applyAsInt(RuneStats.Stat.VIT), 1.0);
			case DARK -> RuneValues.darkWeakSeconds(stats.applyAsInt(RuneStats.Stat.LUK), 1.0);
		});
	}
}
