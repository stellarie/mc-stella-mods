package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The spellbooks a trainer adds to a job's own item. They come from the spells mod by id, so this mod has no link
 * to it. Without the spells mod a job gets its own item only.
 */
public final class JobKits {
	public static final String SPELLS = "mod-stella-spells";
	/** The priest's heal book and buff book. */
	public static final Identifier HEAL_BOOK = spells("mending_word");
	public static final Identifier BUFF_BOOK = spells("stoneguard");
	public static final Identifier DOUBLECAST = spells("doublecast");

	/** Test seam: the item of a spells mod id, or empty. Null reads the item registry. */
	private static volatile @Nullable Function<Identifier, Optional<Item>> itemLookup;
	/** Test seam: the enchantment every book carries. Null means Doublecast. */
	private static volatile @Nullable Identifier bookEnchantment;

	private JobKits() {
	}

	private static Identifier spells(final String path) {
		return Identifier.fromNamespaceAndPath(SPELLS, path);
	}

	public static void setItemLookup(final @Nullable Function<Identifier, Optional<Item>> lookup) {
		itemLookup = lookup;
	}

	public static void setBookEnchantment(final @Nullable Identifier enchantment) {
		bookEnchantment = enchantment;
	}

	private static Optional<Item> item(final Identifier id) {
		Function<Identifier, Optional<Item>> lookup = itemLookup;
		return lookup != null ? lookup.apply(id) : BuiltInRegistries.ITEM.getOptional(id);
	}

	/** The books a job adds: a priest gets a heal and a buff. A mage gets none, because the rpg mod hands over the starter books. */
	public static List<ItemStack> books(final ServerLevel level, final String job, final RandomSource random) {
		List<ItemStack> books = new ArrayList<>();
		switch (job) {
			case "priest" -> {
				add(level, books, item(HEAL_BOOK).orElse(null));
				add(level, books, item(BUFF_BOOK).orElse(null));
			}
			default -> {
			}
		}
		return books;
	}

	private static void add(final ServerLevel level, final List<ItemStack> books, final @Nullable Item item) {
		if (item == null) {
			return;
		}
		ItemStack book = new ItemStack(item);
		Identifier wanted = bookEnchantment == null ? DOUBLECAST : bookEnchantment;
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.get(ResourceKey.create(Registries.ENCHANTMENT, wanted))
			.ifPresentOrElse(
				holder -> book.enchant(holder, 1),
				() -> McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=job_book_unenchanted enchantment={}", McStellaVillages.MOD_ID, wanted)
			);
		books.add(book);
	}

	/** Puts the books in the inventory, and at the feet of a player whose inventory is full. */
	public static void give(final ServerPlayer player, final List<ItemStack> books) {
		for (ItemStack book : books) {
			player.getInventory().add(book);
			if (!book.isEmpty()) {
				player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), book));
			}
		}
	}
}
