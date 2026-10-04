package com.stella.equips.unique;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/**
 * The drop roller of the uniques and of the Slime Heart. A kill with a credited player rolls each entry of the
 * victim's sources on its own. The chance is {@code BASE_CHANCE x rank x (1 + LOOTING_STEP x Looting + share bonus)},
 * capped at 1. Other mods add to the share bonus through the {@code drop-rate} object share.
 */
public final class UniqueDrops {
	public static final double BASE_CHANCE = 0.001;
	public static final double LOOTING_STEP = 0.5;
	/** A {@code Consumer<ToDoubleBiFunction<ServerPlayer, LivingEntity>>}: each source added returns an additive bonus. */
	public static final String DROP_RATE_SHARE = "mod-stella-equips-enchants:drop-rate";
	/** The source id of the Blaze that the nether sovereign share accepts. */
	public static final Identifier SOVEREIGN_SOURCE = Identifier.parse(UniqueRank.SOVEREIGN_SHARE);

	/** One droppable item: its key, the source ids it drops from, its base chance and a fresh stack. */
	public record Entry(String key, List<Identifier> sources, double baseChance, Supplier<ItemStack> stack) {
	}

	/** Decides a roll. The fair roller passes when a random number falls under the chance. */
	public interface Roller {
		boolean succeeds(String key, LivingEntity victim, ServerPlayer killer, double chance);
	}

	private static final Roller FAIR = (key, victim, killer, chance) -> victim.getRandom().nextDouble() < chance;
	private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
	private static final List<ToDoubleBiFunction<ServerPlayer, LivingEntity>> BONUSES = new CopyOnWriteArrayList<>();
	private static volatile Roller roller = FAIR;

	private UniqueDrops() {
	}

	public static void register() {
		Consumer<ToDoubleBiFunction<ServerPlayer, LivingEntity>> adder = BONUSES::add;
		FabricLoader.getInstance().getObjectShare().put(DROP_RATE_SHARE, adder);
		ServerLivingEntityEvents.AFTER_DEATH.register(UniqueDrops::onDeath);
	}

	public static void add(final Entry entry) {
		if (entry(entry.key()) != null) {
			throw new IllegalArgumentException("duplicate drop entry " + entry.key());
		}
		ENTRIES.add(entry);
	}

	public static List<Entry> entries() {
		return List.copyOf(ENTRIES);
	}

	public static @Nullable Entry entry(final String key) {
		for (Entry entry : ENTRIES) {
			if (entry.key().equals(key)) {
				return entry;
			}
		}
		return null;
	}

	/** A test seam. Null restores the fair roller. */
	public static void setRoller(final @Nullable Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static double chance(final double base, final int rank, final int looting, final double shareBonus) {
		if (rank <= 0) {
			return 0.0;
		}
		double multiplier = Math.max(0.0, 1.0 + LOOTING_STEP * looting + shareBonus);
		return Math.min(1.0, base * rank * multiplier);
	}

	/** The sum of every drop-rate source for this kill. A source that fails or returns a non-number counts as zero. */
	public static double shareBonus(final ServerPlayer killer, final LivingEntity victim) {
		double total = 0.0;
		for (ToDoubleBiFunction<ServerPlayer, LivingEntity> source : BONUSES) {
			try {
				double bonus = source.applyAsDouble(killer, victim);
				if (Double.isFinite(bonus)) {
					total += bonus;
				}
			} catch (RuntimeException failure) {
				// A broken source of another mod must not stop a drop.
			}
		}
		return total;
	}

	/** Looting from the weapon of the kill, else from the main hand of the killer. */
	public static int looting(final ServerPlayer killer, final DamageSource source) {
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null || weapon.isEmpty()) {
			weapon = killer.getMainHandItem();
		}
		Holder<Enchantment> looting = killer.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
		return Math.max(0, EnchantmentHelper.getItemEnchantmentLevel(looting, weapon));
	}

	/** The source ids of a victim: its entity type, and the Sovereign source when the nether share accepts it. */
	public static List<Identifier> sourcesOf(final LivingEntity victim) {
		List<Identifier> sources = new ArrayList<>(2);
		sources.add(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
		if (UniqueRank.sovereign(victim)) {
			sources.add(SOVEREIGN_SOURCE);
		}
		return sources;
	}

	public static void onDeath(final LivingEntity victim, final DamageSource source) {
		if (victim instanceof Player || !(victim.level() instanceof ServerLevel) || !(victim.getLastHurtByPlayer() instanceof ServerPlayer killer)) {
			return;
		}
		Set<Entry> matched = new LinkedHashSet<>();
		List<Identifier> sources = sourcesOf(victim);
		for (Entry entry : ENTRIES) {
			if (entry.sources().stream().anyMatch(sources::contains)) {
				matched.add(entry);
			}
		}
		roll(victim, killer, source, matched);
	}

	/** Rolls the entries of one source id for a kill. Returns the items that dropped. */
	public static List<ItemEntity> rollSource(final LivingEntity victim, final ServerPlayer killer, final DamageSource source, final Identifier sourceId) {
		Set<Entry> matched = new LinkedHashSet<>();
		for (Entry entry : ENTRIES) {
			if (entry.sources().contains(sourceId)) {
				matched.add(entry);
			}
		}
		return roll(victim, killer, source, matched);
	}

	private static List<ItemEntity> roll(final LivingEntity victim, final ServerPlayer killer, final DamageSource source, final Set<Entry> entries) {
		List<ItemEntity> dropped = new ArrayList<>();
		if (entries.isEmpty() || !(victim.level() instanceof ServerLevel level)) {
			return dropped;
		}
		int rank = UniqueRank.of(victim);
		if (rank <= 0) {
			return dropped;
		}
		int looting = looting(killer, source);
		double bonus = shareBonus(killer, victim);
		for (Entry entry : entries) {
			double chance = chance(entry.baseChance(), rank, looting, bonus);
			if (roller.succeeds(entry.key(), victim, killer, chance)) {
				ItemStack stack = entry.stack().get();
				ItemEntity item = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), stack);
				level.addFreshEntity(item);
				level.getServer().getPlayerList().broadcastSystemMessage(announcement(killer, stack), false);
				dropped.add(item);
			}
		}
		return dropped;
	}

	/** The chat line sent to every player: the finder, and the item name in the color of rarity EPIC. */
	public static Component announcement(final ServerPlayer player, final ItemStack stack) {
		return Component.empty()
			.append(player.getDisplayName())
			.append(" found ")
			.append(stack.getHoverName().copy().withStyle(Rarity.EPIC.color()))
			.append("!");
	}
}
