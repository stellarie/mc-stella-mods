package com.stella.end;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * Kit helpers for castle mobs. A wither skeleton uses them now, and any later mob that wears enchanted netherite reuses
 * them. The random pool holds every enchantment of the registry that supports the item, vanilla and modpack alike, so
 * this class names no sibling mod. The equips mod is reached by identifier only.
 */
public final class MobGear {
	/** Random enchantments on each armour piece and on the sword of a castle wither skeleton. */
	public static final int SKELETON_ENCHANTS = 3;
	/** The level of the equips enchantment on the sword, one past the ceiling of waveform. */
	public static final int SPECIAL_LEVEL = 4;
	public static final int THORNS_LEVEL = 4;
	/** The sword takes exactly one of these when the equips mod is present. A missing one is skipped. */
	public static final List<Identifier> SPECIAL_SWORD = List.of(
		Identifier.parse("mod-stella-equips-enchants:weapon/venom"),
		Identifier.parse("mod-stella-equips-enchants:weapon/chill"),
		Identifier.parse("mod-stella-equips-enchants:weapon/waveform")
	);

	private MobGear() {
	}

	public static @Nullable Holder<Enchantment> find(final Registry<Enchantment> registry, final Identifier id) {
		return registry.get(id).orElse(null);
	}

	/** Every enchantment that supports the item, curses left out: a curse works against the wearer. */
	public static List<Holder<Enchantment>> pool(final ItemStack stack, final Registry<Enchantment> registry, final Collection<Identifier> excluded) {
		List<Holder<Enchantment>> found = new ArrayList<>();
		registry.listElements().forEach(holder -> {
			boolean skipped = holder.unwrapKey().map(key -> excluded.contains(key.identifier())).orElse(false);
			if (!skipped && holder.value().canEnchant(stack) && !holder.is(EnchantmentTags.CURSE)) {
				found.add(holder);
			}
		});
		return found;
	}

	/**
	 * Adds {@code count} different enchantments from the pool to the item, none of which excludes another, each at a
	 * level from its lowest to its highest. The set is never short while the pool holds that many compatible entries.
	 * Returns the number added.
	 */
	public static int enchantRandom(final ItemStack stack, final int count, final RandomSource random, final Registry<Enchantment> registry, final Collection<Identifier> excluded) {
		List<Holder<Enchantment>> pool = pool(stack, registry, excluded);
		Util.shuffle(pool, random);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		int added = 0;
		for (Holder<Enchantment> candidate : pool) {
			if (added >= count) {
				break;
			}
			boolean fresh = !mutable.keySet().contains(candidate);
			boolean compatible = mutable.keySet().stream().allMatch(held -> Enchantment.areCompatible(held, candidate));
			if (fresh && compatible) {
				mutable.set(candidate, Mth.nextInt(random, candidate.value().getMinLevel(), candidate.value().getMaxLevel()));
				added++;
			}
		}
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return added;
	}

	/** Sets one enchantment at any level, past its ceiling too: the component takes the number as it is. */
	public static void setLevel(final ItemStack stack, final Holder<Enchantment> enchantment, final int level) {
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		mutable.set(enchantment, level);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
	}

	/**
	 * Picks one of the candidates that the registry holds, puts it on the item at {@link #SPECIAL_LEVEL}, and returns
	 * its identifier. Returns null when the registry holds none, which is the case without the equips mod.
	 */
	public static @Nullable Identifier addSpecial(final ItemStack sword, final RandomSource random, final Registry<Enchantment> registry, final List<Identifier> candidates) {
		List<Identifier> present = new ArrayList<>();
		for (Identifier id : candidates) {
			if (find(registry, id) != null) {
				present.add(id);
			}
		}
		if (present.isEmpty()) {
			return null;
		}
		Identifier chosen = present.get(random.nextInt(present.size()));
		setLevel(sword, find(registry, chosen), SPECIAL_LEVEL);
		return chosen;
	}

	/** A sword with the random enchantments, and one special one when the registry holds any. The special three stay out of the random pool. */
	public static ItemStack skeletonSword(final RandomSource random, final Registry<Enchantment> registry) {
		return skeletonSword(random, registry, SPECIAL_SWORD);
	}

	/** As {@link #skeletonSword(RandomSource, Registry)}, with the special candidates named by the caller. */
	public static ItemStack skeletonSword(final RandomSource random, final Registry<Enchantment> registry, final List<Identifier> specials) {
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		addSpecial(sword, random, registry, specials);
		enchantRandom(sword, SKELETON_ENCHANTS, random, registry, specials);
		return sword;
	}

	/** The off-hand shield: thorns past its ceiling. */
	public static ItemStack thornsShield(final Registry<Enchantment> registry) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		Holder<Enchantment> thorns = find(registry, Identifier.withDefaultNamespace("thorns"));
		if (thorns != null) {
			setLevel(shield, thorns, THORNS_LEVEL);
		}
		return shield;
	}

	/** Full netherite armour with {@code enchants} random enchantments on each piece; none of it drops. */
	public static void netheriteArmor(final Mob mob, final int enchants, final RandomSource random, final Registry<Enchantment> registry) {
		wear(mob, EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET), enchants, random, registry);
		wear(mob, EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE), enchants, random, registry);
		wear(mob, EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS), enchants, random, registry);
		wear(mob, EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS), enchants, random, registry);
	}

	private static void wear(final Mob mob, final EquipmentSlot slot, final ItemStack piece, final int enchants, final RandomSource random, final Registry<Enchantment> registry) {
		if (enchants > 0) {
			enchantRandom(piece, enchants, random, registry, List.of());
		}
		mob.setItemSlot(slot, piece);
		mob.setDropChance(slot, 0.0F);
	}
}
