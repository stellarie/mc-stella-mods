package com.stella.end;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

/**
 * Blaze Soul, an imbue of a weapon. Its holder carries a small soul fire aura that, every {@link #PERIOD} ticks,
 * damages one random hostile mob within {@link #RANGE} blocks for the attack value of the holder. The aura keeps its
 * own clock, which a test moves on with {@link #advance}.
 */
public final class BlazeSoul {
	public static final String KEY = "stella_end_blaze_soul";
	public static final int PERIOD = 40;
	public static final double RANGE = 6.0;
	private static final String LORE = "Blaze Soul";
	/** The aura shows its flames this often. */
	private static final int SHOW_PERIOD = 5;
	/** Records keep this many entries; the oldest go first. */
	private static final int KEPT = 256;

	/** One strike of the aura: the tick it fell on, who held the weapon, who was hit, the damage asked and the health taken. */
	public record Hit(long tick, UUID holder, UUID victim, float damage, float dealt) {
	}

	private static final Map<UUID, Long> NEXT = new HashMap<>();
	private static final List<Hit> HITS = new ArrayList<>();
	private static long ticks;

	private BlazeSoul() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BlazeSoul::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	public static boolean has(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(KEY, false);
	}

	/** Whether the item can take the imbue: a melee weapon or a bow. */
	public static boolean eligible(final ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof ProjectileWeaponItem || stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)));
	}

	/** A copy of the item with the imbue and its lore line. */
	public static ItemStack imbued(final ItemStack stack) {
		ItemStack copy = stack.copy();
		CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> tag.putBoolean(KEY, true));
		List<Component> lines = new ArrayList<>(copy.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		lines.add(Component.literal(LORE).withStyle(style -> style.withColor(ChatFormatting.GOLD).withItalic(false)));
		copy.set(DataComponents.LORE, new ItemLore(lines));
		return copy;
	}

	public static float damage(final LivingEntity holder) {
		return (float) holder.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	public static List<Hit> hits() {
		return List.copyOf(HITS);
	}

	/** Moves the aura clock on, as a test skips time. */
	public static void advance(final long skipped) {
		ticks += skipped;
	}

	public static void reset() {
		NEXT.clear();
		HITS.clear();
		ticks = 0L;
	}

	private static boolean holds(final LivingEntity holder) {
		return has(holder.getMainHandItem()) || has(holder.getOffhandItem());
	}

	private static void tick(final MinecraftServer server) {
		ticks++;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !holds(player)) {
				NEXT.remove(player.getUUID());
				continue;
			}
			long due = NEXT.computeIfAbsent(player.getUUID(), id -> ticks + PERIOD);
			if (ticks % SHOW_PERIOD == 0) {
				player.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.5, 0.05, 0.5, 0.01);
			}
			if (ticks >= due) {
				NEXT.put(player.getUUID(), ticks + PERIOD);
				strike(player);
			}
		}
	}

	/** Damages one random hostile mob within range for the attack of the holder, and records it. Only a hostile mob is chosen. */
	private static void strike(final ServerPlayer holder) {
		ServerLevel level = holder.level();
		List<LivingEntity> hostile = level.getEntitiesOfClass(LivingEntity.class, holder.getBoundingBox().inflate(RANGE),
			entity -> entity instanceof Enemy && entity != holder && entity.isAlive() && entity.distanceTo(holder) <= RANGE);
		if (hostile.isEmpty()) {
			return;
		}
		LivingEntity victim = hostile.get(level.getRandom().nextInt(hostile.size()));
		float damage = damage(holder);
		float before = victim.getHealth();
		victim.hurtServer(level, level.damageSources().indirectMagic(holder, holder), damage);
		float dealt = before - victim.getHealth();
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
		HITS.add(new Hit(ticks, holder.getUUID(), victim.getUUID(), damage, dealt));
		if (HITS.size() > KEPT) {
			HITS.remove(0);
		}
		BehaviorLog.info("[END] blazeSoul holder={} victim={} damage={} dealt={}", holder.getName().getString(), victim.getType().getDescriptionId(), damage, dealt);
	}
}
