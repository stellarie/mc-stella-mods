package com.stella.tower;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * The tower's way to the spells mod, when it is installed: the spells a mob can cast and the casting of
 * them, reached by name so this mod builds and runs without it.
 */
public final class TowerSpells {
	/** What the tower needs of a spell caster. */
	public interface Caster {
		boolean available();

		/** Spells a mob can strike a foe with. */
		List<String> offensive();

		/** Spells a mob can aid its side with. */
		List<String> support();

		int cooldownOf(String spell);

		boolean castAt(Mob caster, String spell, LivingEntity target);

		boolean support(Mob caster, String spell);
	}

	/** The caster of a game without the spells mod: it offers nothing. */
	public static final Caster ABSENT = new Caster() {
		@Override
		public boolean available() {
			return false;
		}

		@Override
		public List<String> offensive() {
			return List.of();
		}

		@Override
		public List<String> support() {
			return List.of();
		}

		@Override
		public int cooldownOf(final String spell) {
			return 0;
		}

		@Override
		public boolean castAt(final Mob caster, final String spell, final LivingEntity target) {
			return false;
		}

		@Override
		public boolean support(final Mob caster, final String spell) {
			return false;
		}
	};

	private static final java.util.Map<Integer, Caster> INSTALLED = new java.util.concurrent.ConcurrentHashMap<>();
	private static @Nullable Caster found;

	private TowerSpells() {
	}

	/** The caster in use on a floor: a test's own if one is installed for it, else the spells mod's, else none. */
	public static Caster get(final int floor) {
		Caster own = INSTALLED.get(floor);
		if (own != null) {
			return own;
		}
		if (found == null) {
			found = find();
		}
		return found;
	}

	/** The test seam: a caster to use on a floor in place of the spells mod; null restores the real one. */
	public static void install(final int floor, final @Nullable Caster caster) {
		if (caster == null) {
			INSTALLED.remove(floor);
		} else {
			INSTALLED.put(floor, caster);
		}
	}

	private static Caster find() {
		try {
			Class<?> spells = Class.forName("com.stella.spells.MobSpells", true, TowerSpells.class.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			List<String> offensive = strings(spells, "OFFENSIVE");
			List<String> support = strings(spells, "SUPPORT");
			MethodHandle cooldown = lookup.findStatic(spells, "cooldownOf", MethodType.methodType(int.class, String.class));
			MethodHandle cast = lookup.findStatic(spells, "castAt", MethodType.methodType(boolean.class, Mob.class, String.class, LivingEntity.class));
			MethodHandle aid = lookup.findStatic(spells, "support", MethodType.methodType(boolean.class, Mob.class, String.class));
			return new Caster() {
				@Override
				public boolean available() {
					return !offensive.isEmpty() && !support.isEmpty();
				}

				@Override
				public List<String> offensive() {
					return offensive;
				}

				@Override
				public List<String> support() {
					return support;
				}

				@Override
				public int cooldownOf(final String spell) {
					try {
						return (int) cooldown.invokeExact(spell);
					} catch (Throwable e) {
						return 200;
					}
				}

				@Override
				public boolean castAt(final Mob caster, final String spell, final LivingEntity target) {
					try {
						return (boolean) cast.invokeExact(caster, spell, target);
					} catch (Throwable e) {
						StellaTower.LOGGER.warn("[TOWER] the spells mod would not cast {}", spell, e);
						return false;
					}
				}

				@Override
				public boolean support(final Mob caster, final String spell) {
					try {
						return (boolean) aid.invokeExact(caster, spell);
					} catch (Throwable e) {
						StellaTower.LOGGER.warn("[TOWER] the spells mod would not cast {}", spell, e);
						return false;
					}
				}
			};
		} catch (ReflectiveOperationException | LinkageError e) {
			return ABSENT;
		}
	}

	@SuppressWarnings("unchecked")
	private static List<String> strings(final Class<?> owner, final String field) throws ReflectiveOperationException {
		return List.copyOf((List<String>) owner.getField(field).get(null));
	}
}
