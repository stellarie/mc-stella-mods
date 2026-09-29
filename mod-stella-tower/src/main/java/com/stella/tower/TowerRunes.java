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
	public static final double MOB_CHANCE = 0.002;
	public static final double ELITE_CHANCE = 0.01;
	public static final double BOSS_CHANCE = 0.05;

	static final EquipmentSlot[] ARMOR_SLOTS ={EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** The spells mod's books, which take runes too; empty when that mod is absent. */
	static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> SPELLBOOKS = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse("mod-stella-spells:spellbooks"));

	/** What an item takes a rune as. */
	public enum Slot { WEAPON, ARMOR, BOOK }

	private TowerRunes() {
	}

	public static void register() {
		// Runed gear names its rune.
		net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry.addLast(TowerItems.RUNED, rune -> (context, lines, flag, components) ->
			lines.accept(Component.translatable("item.mod-stella-tower.runed", Component.literal(rune.element().label()).withStyle(rune.element().color()), rune.floor())
				.withStyle(ChatFormatting.BLUE)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			TowerMobs.State state = TowerMobs.stateOf(entity);
			// A ghost leaves its own rune, not one of the eight.
			if (state == null || entity instanceof TowerGhost || !(entity.level() instanceof ServerLevel level)
				|| entity.getLastHurtByPlayer() == null || !level.getGameRules().get(GameRules.MOB_DROPS)) {
				return;
			}
			double chance = PINNED_DROP.getOrDefault(state.floor(), dropChanceOf(entity));
			if (entity.getRandom().nextDouble() < chance) {
				TowerElement element = TowerElement.values()[entity.getRandom().nextInt(TowerElement.values().length)];
				entity.spawnAtLocation(level, rune(element, state.floor()));
				StellaTower.LOGGER.debug("[TOWER] a {} rune of floor {} dropped", element, state.floor());
			}
		});
	}

	private static final Map<Integer, Double> PINNED_DROP = new java.util.concurrent.ConcurrentHashMap<>();

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

	/** The chance a slain tower mob of this rank leaves a rune. */
	public static double dropChance(final TowerRank rank) {
		return switch (rank) {
			case MOB -> MOB_CHANCE;
			case ELITE -> ELITE_CHANCE;
			case BOSS -> BOSS_CHANCE;
		};
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
		if (stack.has(DataComponents.WEAPON) || stack.getItem() instanceof ProjectileWeaponItem) {
			return Slot.WEAPON;
		}
		return null;
	}

	/** The gear with the rune set into it, replacing any before; empty if the gear takes none. */
	public static ItemStack apply(final ItemStack gear, final RuneData rune) {
		// One piece at a time: a rune set into a stack would rune every piece of it.
		if (slotOf(gear) == null || gear.getCount() != 1) {
			return ItemStack.EMPTY;
		}
		ItemStack runed = gear.copy();
		runed.set(TowerItems.RUNED, rune);
		return runed;
	}

	public static @Nullable RuneData runeOf(final ItemStack gear) {
		return gear.get(TowerItems.RUNED);
	}

	/**
	 * A hit once the attacker's weapon rune has had its say: multiplied by the rune's tier, the added part
	 * elemental, dealt half again to the rune's opposite and half to its own element.
	 */
	public static float weapon(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!struckWith(source)) {
			return amount;
		}
		ItemStack weapon = source.getWeaponItem();
		RuneData rune = slotOf(weapon) == Slot.WEAPON ? runeOf(weapon) : null;
		if (rune == null) {
			return amount;
		}
		double added = amount * (weaponMultiplier(rune.floor()) - 1.0);
		TowerMobs.State state = TowerMobs.stateOf(victim);
		if (state != null && state.element() == rune.element().opposite()) {
			added *= OPPOSED;
		} else if (state != null && state.element() == rune.element() && !VoidGear.isVoid(weapon)) {
			// Void weapons strike their own element in full.
			added *= SAME;
		}
		return RuneEffects.modify(victim, source, (float) (amount + added), rune, power(weapon, rune));
	}

	/** A blow a player struck with the weapon in hand, not one their thorns or their armor returned. */
	public static boolean struckWith(final DamageSource source) {
		return source.getEntity() instanceof Player && source.getWeaponItem() != null && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS);
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

	public static void describe(final RuneData rune, final Consumer<Component> lines) {
		lines.accept(Component.translatable("item.mod-stella-tower.rune.floor", rune.floor()).withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable("item.mod-stella-tower.rune.weapon", String.format(java.util.Locale.ROOT, "%.1f", weaponMultiplier(rune.floor())),
			Component.literal(rune.element().opposite().label()).withStyle(rune.element().opposite().color())).withStyle(ChatFormatting.BLUE));
		lines.accept(Component.translatable("item.mod-stella-tower.rune.armor", String.format(java.util.Locale.ROOT, "%.2f", armorDivisor(rune.floor())),
			String.format(java.util.Locale.ROOT, "%.0f", (OWN_ELEMENT_BASE + power(rune.floor()) / 1000.0) * 100.0)).withStyle(ChatFormatting.BLUE));
	}
}
