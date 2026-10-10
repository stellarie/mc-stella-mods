package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * What an encounter Wither leaves. Vanilla drops one nether star. A higher level adds more by chance, and five
 * enchanted books come with two or three enchantments each. The books come from the equips mod when it is present,
 * and from the registry when it is not.
 */
public final class WitherLoot {
	/** The object share the equips mod publishes: a {@code Function<Object[], ItemStack>} of {level, random, count}. */
	public static final String BOOK_SHARE = "mod-stella-equips-enchants:enchanted-book";
	private static final int SMALL_BOOK = 2;
	private static final int LARGE_BOOK = 3;

	private WitherLoot() {
	}

	/** The mean number of extra stars for a level: a base, a step per level above the base level, and a ceiling. */
	public static double mu(final int level, final WitherConfig config) {
		double above = Math.max(0, level - config.getInt(WitherConfig.LEVEL_BASE));
		return Math.min(config.get(WitherConfig.STARS_MAX), config.get(WitherConfig.STARS_BASE) + config.get(WitherConfig.STARS_PER_LEVEL) * above);
	}

	/** The whole part of the mean, plus one more with the chance of its fraction. */
	public static int extras(final int level, final RandomSource random, final WitherConfig config) {
		double mean = mu(level, config);
		int whole = (int) Math.floor(mean);
		return whole + (random.nextDouble() < mean - whole ? 1 : 0);
	}

	/** Two enchantments, or three with the configured chance. */
	public static int bookSize(final RandomSource random, final WitherConfig config) {
		return random.nextDouble() < config.get(WitherConfig.BOOKS_THREE_CHANCE) ? LARGE_BOOK : SMALL_BOOK;
	}

	/** Drops the extra stars and the books where the Wither died. Vanilla drops its own star. */
	public static void drop(final ServerLevel level, final WitherBoss wither, final RandomSource random) {
		WitherConfig config = WitherConfigStore.get();
		int extra = extras(ThreatState.level(wither), random, config);
		int books = config.getInt(WitherConfig.BOOKS_COUNT);
		if (extra > 0) {
			spawn(level, wither, new ItemStack(Items.NETHER_STAR, extra));
		}
		int dropped = 0;
		for (int i = 0; i < books; i++) {
			ItemStack book = book(level, random, bookSize(random, config));
			if (!book.isEmpty()) {
				spawn(level, wither, book);
				dropped++;
			}
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherLoot DROP uuid={} level={} extraStars={} books={}", wither.getUUID(), ThreatState.level(wither), extra, dropped);
	}

	private static void spawn(final ServerLevel level, final WitherBoss wither, final ItemStack stack) {
		ItemEntity item = new ItemEntity(level, wither.getX(), wither.getY() + 1.0, wither.getZ(), stack);
		item.setExtendedLifetime();
		level.addFreshEntity(item);
	}

	/** A book with `count` enchantments: the equips share when it answers with a book, the registry otherwise. */
	@SuppressWarnings("unchecked")
	public static ItemStack book(final ServerLevel level, final RandomSource random, final int count) {
		Object share = FabricLoader.getInstance().getObjectShare().get(BOOK_SHARE);
		if (share instanceof Function<?, ?> function) {
			try {
				Object answer = ((Function<Object[], ItemStack>) function).apply(new Object[] {level, random, count});
				if (answer instanceof ItemStack stack && stack.is(Items.ENCHANTED_BOOK)) {
					return stack;
				}
			} catch (RuntimeException failure) {
				StellaMobs.LOGGER.warn("The enchanted-book share failed; the Wither builds its own book: {}", failure.toString());
			}
		}
		return fallback(level, random, count);
	}

	/** Every enchantment in the registry that is not a curse and not a test fixture, distinct and compatible. */
	public static ItemStack fallback(final ServerLevel level, final RandomSource random, final int count) {
		List<Holder<Enchantment>> pool = new ArrayList<>();
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(enchantment -> {
			boolean fixture = enchantment.unwrapKey().map(key -> key.identifier().getNamespace().contains("-test-")).orElse(false);
			if (!enchantment.is(EnchantmentTags.CURSE) && !fixture) {
				pool.add(enchantment);
			}
		});
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		List<Holder<Enchantment>> granted = new ArrayList<>();
		while (granted.size() < count && !pool.isEmpty()) {
			Holder<Enchantment> pick = pool.remove(random.nextInt(pool.size()));
			if (granted.stream().allMatch(other -> Enchantment.areCompatible(other, pick))) {
				granted.add(pick);
				book.enchant(pick, Mth.nextInt(random, pick.value().getMinLevel(), pick.value().getMaxLevel()));
			}
		}
		return granted.isEmpty() ? ItemStack.EMPTY : book;
	}
}
