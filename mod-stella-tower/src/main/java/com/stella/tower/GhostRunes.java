package com.stella.tower;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * A ghost rune set into gear. A chestplate phases its wearer through mobs, slowing the hostile ones it
 * passes through, and evades three blows in ten of any kind. A weapon's blows meet half the target's armor
 * and pass shields.
 */
public final class GhostRunes {
	public static final double EVASION = 0.30;
	public static final double ARMOR_PIERCE = 0.50;
	public static final int SLOW_TICKS = 60;
	public static final int SLOW_AMPLIFIER = 1;
	public static final int SLOW_RENEW_TICKS = 20;

	private GhostRunes() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !evades(entity, source));
		ServerTickEvents.END_SERVER_TICK.register(GhostRunes::haunt);
		ItemComponentTooltipProviderRegistry.addLast(TowerItems.GHOSTED, mark -> (context, lines, flag, components) -> {
			Equippable equippable = components.get(DataComponents.EQUIPPABLE);
			boolean chest = equippable != null && equippable.slot() == EquipmentSlot.CHEST;
			lines.accept(Component.translatable(chest ? "item.mod-stella-tower.ghosted.chest" : "item.mod-stella-tower.ghosted.weapon").withStyle(ChatFormatting.DARK_AQUA));
		});
	}

	/** Whether the gear takes a ghost rune: a chestplate or a weapon, one piece at a time. */
	public static boolean takes(final ItemStack gear) {
		// One piece at a time, and a piece that carries one already takes no second.
		if (gear.getCount() != 1 || isGhosted(gear)) {
			return false;
		}
		Equippable equippable = gear.get(DataComponents.EQUIPPABLE);
		boolean chest = equippable != null && equippable.slot() == EquipmentSlot.CHEST && !gear.is(Items.ELYTRA);
		return chest || TowerRunes.slotOf(gear) == TowerRunes.Slot.WEAPON;
	}

	/** The gear with a ghost rune set into it; empty if it takes none. */
	public static ItemStack apply(final ItemStack gear) {
		if (!takes(gear)) {
			return ItemStack.EMPTY;
		}
		ItemStack ghosted = gear.copy();
		ghosted.set(TowerItems.GHOSTED, net.minecraft.util.Unit.INSTANCE);
		return ghosted;
	}

	public static boolean isGhosted(final ItemStack gear) {
		return gear.has(TowerItems.GHOSTED);
	}

	/** A player wearing a ghost-runed chestplate. */
	public static boolean phased(final LivingEntity wearer) {
		return wearer instanceof Player && isGhosted(wearer.getItemBySlot(EquipmentSlot.CHEST));
	}

	/** Whether two entities pass through each other: a phased player and a mob. */
	public static boolean passThrough(final Entity a, final Entity b) {
		return a instanceof LivingEntity one && phased(one) && b instanceof Mob
			|| b instanceof LivingEntity other && phased(other) && a instanceof Mob;
	}

	/**
	 * Whether a phased wearer evades this blow, three times in ten. A blow nothing can turn, the void's or a
	 * command's, is not evaded.
	 */
	public static boolean evades(final LivingEntity victim, final DamageSource source) {
		if (!phased(victim) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || victim.getRandom().nextDouble() >= EVASION) {
			return false;
		}
		if (victim.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1.0, victim.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
		}
		return true;
	}

	/** A blow a player struck with a ghost-runed weapon. */
	public static boolean ghostBlow(final DamageSource source) {
		return TowerRunes.struckWith(source) && isGhosted(source.getWeaponItem());
	}

	/** Every hostile mob a phased player stands within is slowed. */
	private static void haunt(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !phased(player)) {
				continue;
			}
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox(), mob -> mob instanceof Enemy && mob.isAlive())) {
				// Renewed once a second, not every tick, so a mob is not sent a fresh effect each tick.
				MobEffectInstance held = mob.getEffect(MobEffects.SLOWNESS);
				if (held == null || held.getDuration() <= SLOW_TICKS - SLOW_RENEW_TICKS) {
					mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER), player);
				}
			}
		}
	}
}
