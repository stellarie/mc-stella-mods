package com.stella.equips.heart;

import com.mojang.serialization.Codec;
import com.stella.equips.StellaEquips;
import com.stella.equips.mixin.LivingEntityHurtAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * The slime heart. Blue and red slimes drop it rarely, and the anvil puts one into an armor piece. Each piece with a
 * heart delays {@link #SHARE_PER_PIECE} of an attack: the wearer takes the rest at once and the delayed part in
 * {@link #DRAIN_STEPS} equal parts, one every {@link #DRAIN_INTERVAL} ticks. The delayed part is measured after all
 * mitigation, so each part lands exactly, through its own damage type.
 */
public final class SlimeHeart {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "slime_heart");
	public static final float SHARE_PER_PIECE = 0.125F;
	public static final int DRAIN_STEPS = 5;
	public static final int DRAIN_INTERVAL = 20;
	public static final float DROP_CHANCE = 0.0001F;
	public static final Set<Identifier> DROPPING_TABLES = Set.of(
		Identifier.parse("mod-stella-mobs:entities/blue_slime"), Identifier.parse("mod-stella-mobs:entities/red_slime"));
	public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, ID);
	private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	/** True on an armor piece that holds a heart. */
	public static final DataComponentType<Boolean> INSERTED = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE, ID,
		DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build()
	);
	public static final Item ITEM = Registry.register(
		BuiltInRegistries.ITEM, ID, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID)))
	);
	private static final List<Drain> DRAINS = new ArrayList<>();
	/** The exact part a delayed hit lands, read by the damage hook while that hit runs. */
	private static final ThreadLocal<Float> SLICE = new ThreadLocal<>();

	private record Drain(ResourceKey<Level> dimension, UUID victim, float part, int[] left, long[] next) {
	}

	private SlimeHeart() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SlimeHeart::tick);
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (DROPPING_TABLES.contains(key.identifier())) {
				builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(ITEM))
					.when(Holder.direct(LootItemRandomChanceCondition.randomChance(DROP_CHANCE).build())));
			}
		});
	}

	public static boolean holdsHeart(final ItemStack piece) {
		return piece.getOrDefault(INSERTED, false);
	}

	/** The share of an attack this entity delays: 12.5% for each worn piece with a heart. */
	public static float share(final LivingEntity entity) {
		int pieces = 0;
		for (EquipmentSlot slot : ARMOR) {
			if (holdsHeart(entity.getItemBySlot(slot))) {
				pieces++;
			}
		}
		return SHARE_PER_PIECE * pieces;
	}

	/** An attack: a hit with a living attacker, not thorns, not a delayed part, and not one that ignores invulnerability. */
	public static boolean isAttack(final DamageSource source) {
		return source.getEntity() instanceof LivingEntity && !source.is(DamageTypes.THORNS) && !source.is(DAMAGE_TYPE)
			&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/** The damage that lands now. A delayed part returns its exact size; an attack on a heart wearer queues its delayed share. */
	public static float adjust(final LivingEntity victim, final DamageSource source, final float damage) {
		Float slice = SLICE.get();
		if (slice != null && source.is(DAMAGE_TYPE)) {
			return slice;
		}
		if (damage <= 0.0F || !(victim.level() instanceof ServerLevel level) || !isAttack(source)) {
			return damage;
		}
		float share = share(victim);
		if (share <= 0.0F) {
			return damage;
		}
		float delayed = damage * share;
		DRAINS.add(new Drain(level.dimension(), victim.getUUID(), delayed / DRAIN_STEPS,
			new int[] {DRAIN_STEPS}, new long[] {level.getServer().getTickCount() + DRAIN_INTERVAL}));
		return damage - delayed;
	}

	private static void tick(final MinecraftServer server) {
		if (DRAINS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Drain drain : List.copyOf(DRAINS)) {
			if (now < drain.next()[0]) {
				continue;
			}
			ServerLevel level = server.getLevel(drain.dimension());
			Entity found = level == null ? null : level.getEntity(drain.victim());
			if (!(found instanceof LivingEntity victim) || !victim.isAlive()) {
				DRAINS.remove(drain);
				continue;
			}
			land(level, victim, drain.part());
			drain.left()[0]--;
			drain.next()[0] = now + DRAIN_INTERVAL;
			if (drain.left()[0] <= 0) {
				DRAINS.remove(drain);
			}
		}
	}

	/** Lands one part and keeps the victim's hurt cooldown as it was, so a part never shields the next real hit. */
	private static void land(final ServerLevel level, final LivingEntity victim, final float part) {
		LivingEntityHurtAccessor hurt = (LivingEntityHurtAccessor) victim;
		int cooldown = victim.damageCooldownTime;
		float lastHurt = hurt.stellaEquips$getLastHurt();
		SLICE.set(part);
		try {
			victim.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE)), part);
		} finally {
			SLICE.remove();
			victim.damageCooldownTime = cooldown;
			hurt.stellaEquips$setLastHurt(lastHurt);
		}
	}
}
