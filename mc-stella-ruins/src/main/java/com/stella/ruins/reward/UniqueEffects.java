package com.stella.ruins.reward;

import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Unique effects and their dispatch. An effect reaches a hook through Fabric events only. The dispatcher reads
 * both hands and the four armor slots of the entity, for a unique component or an affix that names an effect.
 * One effect fires once per event, however many items carry it.
 */
public final class UniqueEffects {
	/** Marks an arrow that has fired its hooks, so a chunk reload does not fire them again. */
	public static final String FIRED_TAG = "mod-stella-ruins.arrow_fired";
	private static final int PERIOD = 20;
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private static final Map<String, EffectHook> EFFECTS = new HashMap<>();
	private static final Set<String> WARNED = new HashSet<>();
	// A hook that deals damage must not start the same hooks again.
	private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);

	private UniqueEffects() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
			if (blocked || taken <= 0.0F) {
				return;
			}
			dispatch(entity, hook -> hook.onHurt(entity, source, taken));
			if (source.getEntity() instanceof LivingEntity attacker && attacker != entity) {
				dispatch(attacker, hook -> hook.onAttack(attacker, entity, source, taken));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof LivingEntity killer && killer != entity) {
				dispatch(killer, hook -> hook.onKill(killer, entity));
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof LivingEntity owner && !arrow.entityTags().contains(FIRED_TAG)) {
				if (!effectIdsOf(owner).isEmpty()) {
					arrow.addTag(FIRED_TAG);
					dispatch(owner, hook -> hook.onArrowFired(owner, arrow));
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % PERIOD != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				dispatch(player, hook -> hook.onTick(player));
			}
		});
	}

	public static void define(final String effectId, final EffectHook hook) {
		if (!Identifier.isValidPath(effectId)) {
			throw new IllegalArgumentException("Bad effect id: " + effectId);
		}
		if (EFFECTS.putIfAbsent(effectId, hook) != null) {
			throw new IllegalStateException("Effect already defined: " + effectId);
		}
	}

	public static boolean isDefined(final String effectId) {
		return EFFECTS.containsKey(effectId);
	}

	/** The effect ids that the entity holds or wears, each once, in slot order. */
	public static Set<String> effectIdsOf(final LivingEntity entity) {
		Set<String> ids = new LinkedHashSet<>();
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (stack.isEmpty() || !countsInSlot(stack, slot)) {
				continue;
			}
			String unique = RuinUniques.effectOf(stack);
			if (unique != null) {
				ids.add(unique);
			}
			String affixId = RuinAffixes.affixOf(stack);
			if (affixId != null) {
				RuinAffixes.get(affixId).map(affix -> affix.spec().effectId()).ifPresent(ids::add);
			}
		}
		return ids;
	}

	private static boolean countsInSlot(final ItemStack stack, final EquipmentSlot slot) {
		if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			return true;
		}
		// A helmet in a hand is carried, not worn.
		var equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
		return equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	private static void dispatch(final LivingEntity entity, final Consumer<EffectHook> call) {
		if (RUNNING.get()) {
			return;
		}
		List<EffectHook> hooks = new ArrayList<>();
		for (String id : effectIdsOf(entity)) {
			EffectHook hook = EFFECTS.get(id);
			if (hook == null) {
				if (WARNED.add(id)) {
					StellaRuins.LOGGER.warn("An item names the unknown unique effect {}", id);
				}
				continue;
			}
			hooks.add(hook);
		}
		if (hooks.isEmpty()) {
			return;
		}
		RUNNING.set(true);
		try {
			for (EffectHook hook : hooks) {
				try {
					call.accept(hook);
				} catch (RuntimeException e) {
					StellaRuins.LOGGER.error("A unique effect hook failed", e);
				}
			}
		} finally {
			RUNNING.set(false);
		}
	}
}
