package com.stella.spells;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The Unbroken Vow: for half an hour one point of an item's durability is spent once per {@link
 * #WEAR_PER_STAT_POINT} points of wear the item takes, for each point of the holder's Vitality and
 * Intelligence together, with no cap on the sum.
 * <p>
 * The vow carries the wear and never the item. The permanent form of this idea in the pack is refinement,
 * which raises an item's maximum durability; a raised maximum would outlive the status through a logout,
 * a death or a trade, and this status must not.
 */
public final class UnbrokenVow {
	/** The vow lasts half an hour, as the long self buffs of this mod do. */
	public static final int VOW_TICKS = 36_000;
	/** The points of wear that one point of Vitality and Intelligence together absorb. */
	public static final double WEAR_PER_STAT_POINT = 1.0;

	private static final String RPG_SKILLS_CLASS = "com.stella.rpg.RpgSkills";
	private static final Map<UUID, Integer> CARRIED = new WeakHashMap<>();
	private static volatile StatSource source = UnbrokenVow::rpgStat;
	private static volatile @Nullable Method statMethod;
	private static volatile boolean statResolved;

	/** Reads one stat of an entity. The harness pins this when the RPG mod is absent. */
	public interface StatSource {
		double stat(LivingEntity holder, String key);
	}

	private UnbrokenVow() {
	}

	/** Pins the stat reader for a harness. A null source returns to the RPG mod's own stats. */
	public static void setStatSource(final @Nullable StatSource newSource) {
		source = newSource == null ? UnbrokenVow::rpgStat : newSource;
	}

	/** The wear one point of durability covers: one per point of Vitality and Intelligence, uncapped. */
	public static int level(final LivingEntity holder) {
		double total = source.stat(holder, "vit") + source.stat(holder, "int");
		return (int) Math.floor(Math.max(0.0, WEAR_PER_STAT_POINT * total));
	}

	/** What the item pays of a wear of this size. The rest stays carried by the vow. */
	public static int spent(final ServerPlayer holder, final int amount) {
		if (amount <= 0 || !holder.hasEffect(SpellStatus.UNBROKEN_VOW)) {
			return amount;
		}
		int level = level(holder);
		if (level <= 0) {
			return amount;
		}
		synchronized (CARRIED) {
			int carried = CARRIED.getOrDefault(holder.getUUID(), 0) + amount;
			CARRIED.put(holder.getUUID(), carried % (level + 1));
			return carried / (level + 1);
		}
	}

	private static double rpgStat(final LivingEntity holder, final String key) {
		Method method = statMethod();
		if (method == null) {
			return 0.0;
		}
		try {
			return (double) method.invoke(null, holder, key);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0.0;
		}
	}

	private static @Nullable Method statMethod() {
		if (!statResolved) {
			synchronized (UnbrokenVow.class) {
				if (!statResolved) {
					try {
						statMethod = Class.forName(RPG_SKILLS_CLASS, true, UnbrokenVow.class.getClassLoader())
							.getMethod("stat", LivingEntity.class, String.class);
					} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
						statMethod = null;
					}
					statResolved = true;
				}
			}
		}
		return statMethod;
	}
}
