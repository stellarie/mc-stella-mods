package com.stella.mobs;

import com.stella.mobs.behavior.WitherLoot;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/**
 * An encounter Wither leaves vanilla's one star plus more by level, and five enchanted books with two or three
 * enchantments each. The books come from the equips share when it answers, and from the registry when it does not.
 */
public final class WitherLootGameTest {
	private static final int DRAWS = 10_000;
	private static final long SEED = 31415L;
	private static final double MEAN_TOLERANCE = 0.03;
	private static final int BOOK_DRAWS = 300;
	private static final List<ItemStack> DROPPED = new CopyOnWriteArrayList<>();
	private static final List<Vec3> WHERE = new CopyOnWriteArrayList<>();
	private static boolean watching;

	private static synchronized void watchItems() {
		if (watching) {
			return;
		}
		watching = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				DROPPED.add(item.getItem().copy());
				WHERE.add(item.position());
			}
		});
	}

	@GameTest
	public void theMeanExtraStarsFollowTheLevel(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		int[] levels = {100, 120, 160, 200, 300, 500, 640, 1000};
		double[] wanted = {0.25, 0.5, 1.0, 1.5, 2.75, 5.25, 7.0, 7.0};
		StringBuilder log = new StringBuilder();
		double previous = -1.0;
		for (int i = 0; i < levels.length; i++) {
			double mu = WitherLoot.mu(levels[i], config);
			helper.assertTrue(Math.abs(mu - wanted[i]) < 1.0E-9, "level " + levels[i] + " has mean " + mu + ", want " + wanted[i]);
			helper.assertTrue(mu >= previous, "the mean fell at level " + levels[i]);
			previous = mu;
			RandomSource random = RandomSource.create(SEED + levels[i]);
			long total = 0;
			for (int draw = 0; draw < DRAWS; draw++) {
				int extras = WitherLoot.extras(levels[i], random, config);
				helper.assertTrue(extras >= Math.floor(mu) && extras <= Math.floor(mu) + 1, "level " + levels[i] + " gave " + extras + " extra stars");
				total += extras;
			}
			double mean = total / (double) DRAWS;
			log.append(levels[i]).append(":mu=").append(mu).append(",mean=").append(mean).append(' ');
			helper.assertTrue(Math.abs(mean - mu) <= MEAN_TOLERANCE * Math.max(1.0, mu), "level " + levels[i] + " drew a mean of " + mean + ", want " + mu);
		}
		helper.assertTrue(WitherLoot.mu(50, config) == 0.25, "a level under the base level has a mean of " + WitherLoot.mu(50, config));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherStars RESULT {}", log);
		helper.succeed();
	}

	@GameTest
	public void booksHoldTwoOrThree(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		RandomSource random = RandomSource.create(SEED);
		int threes = 0;
		for (int i = 0; i < DRAWS; i++) {
			int size = WitherLoot.bookSize(random, config);
			helper.assertTrue(size == 2 || size == 3, "a book of " + size + " enchantments");
			threes += size == 3 ? 1 : 0;
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherBookSize RESULT threes={} of={}", threes, DRAWS);
		helper.assertTrue(Math.abs(threes / (double) DRAWS - 0.5) < 0.03, "three-enchantment books came " + threes + " of " + DRAWS);
		helper.succeed();
	}

	private static List<String> faults(final ItemStack book, final int count) {
		List<String> faults = new ArrayList<>();
		ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (!book.is(Items.ENCHANTED_BOOK)) {
			faults.add("not an enchanted book");
		}
		if (stored.size() != count) {
			faults.add("holds " + stored.size() + ", want " + count);
		}
		List<Holder<Enchantment>> held = new ArrayList<>(stored.keySet());
		for (Holder<Enchantment> enchantment : held) {
			int level = stored.getLevel(enchantment);
			if (enchantment.is(EnchantmentTags.CURSE)) {
				faults.add("a curse " + enchantment.getRegisteredName());
			}
			if (level < enchantment.value().getMinLevel() || level > enchantment.value().getMaxLevel()) {
				faults.add(enchantment.getRegisteredName() + " at level " + level);
			}
			for (Holder<Enchantment> other : held) {
				if (other != enchantment && !Enchantment.areCompatible(enchantment, other)) {
					faults.add(enchantment.getRegisteredName() + " clashes with " + other.getRegisteredName());
				}
			}
		}
		return faults;
	}

	/** The share is global, so the whole body runs inside one tick and puts back what it found. */
	@GameTest
	public void booksComeFromTheShareOrTheFallback(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Object original = FabricLoader.getInstance().getObjectShare().remove(WitherLoot.BOOK_SHARE);
		try {
			RandomSource random = RandomSource.create(SEED);
			for (int draw = 0; draw < BOOK_DRAWS; draw++) {
				int count = 2 + draw % 2;
				List<String> faults = faults(WitherLoot.book(level, random, count), count);
				helper.assertTrue(faults.isEmpty(), "a fallback book of " + count + ": " + faults);
			}
			List<Object[]> calls = new ArrayList<>();
			ItemStack marker = new ItemStack(Items.ENCHANTED_BOOK);
			marker.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("from the share"));
			Function<Object[], ItemStack> standIn = args -> {
				calls.add(args);
				return marker.copy();
			};
			FabricLoader.getInstance().getObjectShare().put(WitherLoot.BOOK_SHARE, standIn);
			ItemStack shared = WitherLoot.book(level, random, 3);
			boolean argsRight = calls.size() == 1 && calls.get(0).length == 3 && calls.get(0)[0] == level
				&& calls.get(0)[1] == random && Integer.valueOf(3).equals(calls.get(0)[2]);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherBooks RESULT shareCalls={} argsRight={} fromShare={}", calls.size(), argsRight, shared.has(DataComponents.CUSTOM_NAME));
			helper.assertTrue(argsRight, "the share was not asked for {level, random, 3}");
			helper.assertTrue(shared.has(DataComponents.CUSTOM_NAME), "the share's book was not used");

			FabricLoader.getInstance().getObjectShare().put(WitherLoot.BOOK_SHARE, (Function<Object[], ItemStack>) args -> ItemStack.EMPTY);
			helper.assertTrue(faults(WitherLoot.book(level, random, 2), 2).isEmpty(), "an empty answer left no book");
			FabricLoader.getInstance().getObjectShare().put(WitherLoot.BOOK_SHARE, (Function<Object[], ItemStack>) args -> {
				throw new IllegalStateException("share failure");
			});
			helper.assertTrue(faults(WitherLoot.book(level, random, 3), 3).isEmpty(), "a failing share left no book");
			FabricLoader.getInstance().getObjectShare().put(WitherLoot.BOOK_SHARE, (Function<Object[], ItemStack>) args -> new ItemStack(Items.STICK));
			helper.assertTrue(faults(WitherLoot.book(level, random, 3), 3).isEmpty(), "a share that answered a stick left no book");
		} finally {
			FabricLoader.getInstance().getObjectShare().remove(WitherLoot.BOOK_SHARE);
			if (original != null) {
				FabricLoader.getInstance().getObjectShare().put(WitherLoot.BOOK_SHARE, original);
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 1100, padding = WideArena.PADDING)
	public void aKillDropsTheStarsAndFiveBooks(final GameTestHelper helper) {
		watchItems();
		WideArena arena = WideArena.build(helper);
		WitherBoss[] wither = new WitherBoss[1];
		ServerPlayer[] player = new ServerPlayer[1];
		long[] killedAt = {-1};
		int[] mark = {0};
		Vec3[] diedAt = new Vec3[1];
		helper.runBeforeTestEnd(arena::finish);
		helper.onEachTick(() -> {
			if (wither[0] == null) {
				return;
			}
			WideArena.keepValid(player[0]);
			ServerLevel level = helper.getLevel();
			if (killedAt[0] < 0) {
				killedAt[0] = helper.getTick();
				mark[0] = DROPPED.size();
				diedAt[0] = wither[0].position();
				wither[0].hurtServer(level, level.damageSources().genericKill(), 1.0E9F);
				return;
			}
			if (helper.getTick() - killedAt[0] < 10) {
				return;
			}
			int mu = (int) Math.floor(WitherLoot.mu(ThreatState.level(wither[0]), WitherConfigStore.get()));
			int stars = 0;
			int books = 0;
			List<String> faults = new ArrayList<>();
			for (int i = mark[0]; i < DROPPED.size(); i++) {
				if (WHERE.get(i).distanceToSqr(diedAt[0]) > 36.0) {
					continue;
				}
				ItemStack stack = DROPPED.get(i);
				if (stack.is(Items.NETHER_STAR)) {
					stars += stack.getCount();
				} else if (stack.is(Items.ENCHANTED_BOOK)) {
					books++;
					int size = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).size();
					if (size != 2 && size != 3) {
						faults.add("a book of " + size);
					}
				}
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherKillDrops RESULT level={} stars={} books={} faults={}", ThreatState.level(wither[0]), stars, books, faults);
			helper.assertTrue(books == WitherConfigStore.get().getInt(WitherConfig.BOOKS_COUNT), "the Wither left " + books + " books");
			helper.assertTrue(faults.isEmpty(), "bad books: " + faults);
			helper.assertTrue(stars >= 1 && stars <= 1 + mu + 1, "the Wither left " + stars + " stars at level " + ThreatState.level(wither[0]));
			helper.succeed();
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			wither[0] = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
		});
	}
}
