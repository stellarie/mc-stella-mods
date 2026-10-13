package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Clue books: tattered notes left in chests by the spellbinders who came before. Each one tells how to bind
 * one spell's book and pen its scroll, with the material by name, what the spell does, and a good deal of
 * gossip. One in fifty is instead about the Scroll of Return.
 */
public final class ClueBooks {
	/** The share of vanilla chests that hold a clue book. */
	public static final double CLUE_CHEST_CHANCE = 0.30;
	/** Custom data key naming what a clue book teaches: a spell id, or the return scroll's id. */
	public static final String CLUE_KEY = "stella_clue";
	/** Tattered: the book cannot be copied, as befits a note found at the bottom of a chest. */
	private static final int TATTERED = 3;

	private record Author(String name, String opener) {
	}

	private static final List<Author> AUTHORS = List.of(
		new Author("Archmage Pip", "Dear reader, if you found this in a chest, the chest is yours now, and so is this secret. I, Archmage Pip, have bound %s eleven times and set my own eyebrows alight only twice. Here is how."),
		new Author("Grandma Wisteria", "Sit down, dearie. Nobody listens to Grandma until they need %s, and now you need it. Elbows off the crafting table."),
		new Author("A Tired Librarian", "OVERDUE since before the Nether was hot. Fine owed: one (1) emerald. Contents: the true binding of %s. Return to the stacks after memorising. You won't."),
		new Author("Sir Clanksworth", "In my day we had no %s. We had a stick. Then a creeper took the stick. So I learned %s, and so shall you, squire."),
		new Author("The Cat (dictated)", "Mrrp. My human keeps making %s instead of feeding me. I watched. I will tell you how, for one (1) fish. The fish is due now."),
		new Author("Some Witch, Probably", "Hehehe. You opened a stranger's chest and read a stranger's notes. Bold. I like bold. %s is yours, if you can find what it eats."),
		new Author("Nemo the Nervous", "Please do not tell the Guild of Binders I wrote this down. %s is supposed to be a secret. Oh no. Oh no, I'm still writing."),
		new Author("A Villager", "Hmm. Hmmm. HRMM. (Translated from the Villager: this is %s. Bring emeralds. Hmm.)")
	);

	private static final List<String> POSTSCRIPTS = List.of(
		"P.S. Do not imbue it into a fish. We tried. The fish was not grateful.",
		"P.S. If this page smells of smoke, that is unrelated.",
		"P.S. There are more notes like this in other chests. Collect them all, or don't. I am a book, not your mother.",
		"P.S. Tide Lungs will not go into a sword. It says it is a breath, not a blow, and it is very dramatic about it.",
		"P.S. A Scroll of Return smells faintly of feathers and homesickness.",
		"P.S. The wolves have been working out lately. Be nice to them.",
		"P.S. A second imbue costs five times the first. The anvil is greedy and it knows it.",
		"P.S. If a village head waves a glowing axe at you, you have probably annoyed the village."
	);

	private ClueBooks() {
	}

	/** Rolls a clue book on its own draw: a random spell's, or now and then the Scroll of Return's. */
	public static ItemStack roll(final RandomSource random) {
		int pick = random.nextInt(SpellbookCatalog.DEFINITIONS.size() + 1);
		return pick < SpellbookCatalog.DEFINITIONS.size()
			? forSpell(SpellbookCatalog.DEFINITIONS.get(pick), random)
			: forReturnScroll(random);
	}

	public static ItemStack forSpell(final SpellDefinition spell, final RandomSource random) {
		Identifier id = spell.id();
		String name = displayName(id);
		Component material = Component.translatable(spell.ingredient().getDescriptionId());
		boolean doubled = Spellbooks.SCROLLS_WITH_DOUBLE_MATERIAL.contains(id);
		List<Component> pages = new ArrayList<>();
		Author author = AUTHORS.get(random.nextInt(AUTHORS.size()));
		pages.add(Component.literal(author.opener().formatted(name, name)));
		pages.add(Component.literal("How to bind the book:\nset a Book in the middle of a crafting grid and one ")
			.append(material).append(" in each of the four corners.\n\nHow to pen scrolls:\none Paper and ")
			.append(doubled ? Component.literal("two ").append(material) : Component.literal("one ").append(material))
			.append(" make two scrolls. A scroll crumbles when it is read."));
		pages.add(Component.literal("What " + name + " does:\n").append(Component.translatable("item." + id.getNamespace() + "." + id.getPath() + ".desc")));
		pages.add(Component.literal(POSTSCRIPTS.get(random.nextInt(POSTSCRIPTS.size())) + "\n\n- " + author.name()));
		return book("Notes on " + name, author.name(), pages, id);
	}

	public static ItemStack forReturnScroll(final RandomSource random) {
		Author author = AUTHORS.get(random.nextInt(AUTHORS.size()));
		List<Component> pages = new ArrayList<>();
		pages.add(Component.literal(author.opener().formatted("the Scroll of Return", "the Scroll of Return")));
		pages.add(Component.literal("How to pen it:\none ").append(Component.translatable(Items.PAPER.getDescriptionId()))
			.append(", one ").append(Component.translatable(Items.ENDER_PEARL.getDescriptionId()))
			.append(" and one ").append(Component.translatable(Items.FEATHER.getDescriptionId()))
			.append(", anywhere in a crafting grid, make two."));
		pages.add(Component.literal("What it does:\n").append(Component.translatable("item." + Spellbooks.MOD_ID + ".return_scroll.desc")));
		pages.add(Component.literal("P.S. It will not take you home if home is the Nether. Nobody's home is the Nether.\n\n- " + author.name()));
		return book("Notes on Returning Home", author.name(), pages, Spellbooks.SCROLL_OF_RETURN_ID);
	}

	/** What a clue book teaches, or null for any other item. */
	public static Identifier teaches(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getString(CLUE_KEY).map(Identifier::tryParse).orElse(null);
	}

	private static ItemStack book(final String title, final String author, final List<Component> pages, final Identifier teaches) {
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> filtered = pages.stream().map(Filterable::passThrough).toList();
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
			Filterable.passThrough(title.length() > WrittenBookContent.TITLE_MAX_LENGTH ? title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH) : title),
			author, TATTERED, filtered, true
		));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(CLUE_KEY, teaches.toString()));
		return stack;
	}

	// "ice_lance" reads "Ice Lance": the server holds no mod language file, and a book title is plain text.
	static String displayName(final Identifier id) {
		String special = switch (id.getPath()) {
			case "wardens_roar" -> "Warden's Roar";
			case "owls_gaze" -> "Owl's Gaze";
			default -> null;
		};
		if (special != null) {
			return special;
		}
		StringBuilder name = new StringBuilder();
		for (String word : id.getPath().split("_")) {
			if (!name.isEmpty()) {
				name.append(' ');
			}
			boolean small = !name.isEmpty() && (word.equals("of") || word.equals("the"));
			name.append(small ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
		}
		return name.toString();
	}
}
