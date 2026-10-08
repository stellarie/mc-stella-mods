package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What an archer is handed on joining the school: arrow rain, and one other archer book drawn from the
 * shipped catalogue. The draw is the caller's, so the same seed gives the same book; the grant is all or
 * nothing, so a pack with no room for both keeps what it has and nothing is dropped on the ground.
 */
public final class ArcherStarter {
	/** Every new archer is handed arrow rain. */
	public static final Identifier STARTER_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "arrow_rain");
	/** Arrow rain, and one other archer book. */
	public static final int STARTER_BOOKS = 2;

	private ArcherStarter() {
	}

	/** The same grant on the caller's own draw. */
	public static int grant(final ServerPlayer player, final RandomSource random, final Consumer<Identifier> onGranted) {
		List<Identifier> drawn = new ArrayList<>(STARTER_BOOKS);
		drawn.add(STARTER_ID);
		drawn.add(randomArcherBook(random));
		return give(player, drawn, onGranted);
	}

	/** A fresh draw for a caller with no source of its own. */
	public static int grant(final ServerPlayer player, final Consumer<Identifier> onGranted) {
		return grant(player, player.getRandom(), onGranted);
	}

	/** The same grant with no record of what it handed out. */
	public static int grant(final ServerPlayer player, final RandomSource random) {
		return grant(player, random, ignored -> {
		});
	}

	// The whole grant is spent or none of it is: every book has to find its own room beforehand.
	private static int give(final ServerPlayer player, final List<Identifier> drawn, final Consumer<Identifier> onGranted) {
		List<ItemStack> books = new ArrayList<>(drawn.size());
		for (Identifier id : drawn) {
			Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR);
			ItemStack book = item.asItem().getDefaultInstance();
			if (book.isEmpty()) {
				Spellbooks.LOGGER.info("[SPELL] event=ARCHER_STARTER_REFUSED player={} refused={} books={}",
					player.getUUID(), id, drawn);
				return 0;
			}
			books.add(book);
		}
		if (freeSlots(player) < books.size()) {
			Spellbooks.LOGGER.info("[SPELL] event=ARCHER_STARTER_REFUSED player={} free={} books={}",
				player.getUUID(), freeSlots(player), drawn);
			return 0;
		}
		for (int index = 0; index < books.size(); index++) {
			player.getInventory().add(books.get(index));
			onGranted.accept(drawn.get(index));
		}
		Spellbooks.LOGGER.info("[SPELL] event=ARCHER_STARTER player={} books={} granted={}",
			player.getUUID(), books.size(), drawn);
		return books.size();
	}

	// One empty slot per book: every book of this grant is a single item, so a slot each is what it needs.
	private static int freeSlots(final ServerPlayer player) {
		int free = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	// A book of the archer school other than arrow rain, drawn from the catalogue the mod ships.
	private static Identifier randomArcherBook(final RandomSource random) {
		List<Identifier> archer = SpellbookCatalog.DEFINITIONS.stream()
			.filter(definition -> definition.school() == School.ARCHER)
			.map(SpellDefinition::id)
			.filter(id -> !id.equals(STARTER_ID))
			.sorted()
			.toList();
		if (archer.isEmpty()) {
			return STARTER_ID;
		}
		return archer.get(random.nextInt(archer.size()));
	}
}
