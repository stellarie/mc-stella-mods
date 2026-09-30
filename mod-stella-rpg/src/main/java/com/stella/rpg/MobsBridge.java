package com.stella.rpg;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Mob;

/**
 * Threat and combat role of a mob, reached by name when {@code mod-stella-mobs} is installed.
 * Without it every mob has threat 0 and role melee, so every mob stat is the minimum.
 */
public final class MobsBridge {
	public interface Facts {
		int threat(Mob mob);

		String role(Mob mob);
	}

	private static final Facts NONE = new Facts() {
		@Override
		public int threat(final Mob mob) {
			return 0;
		}

		@Override
		public String role(final Mob mob) {
			return MobStats.MELEE;
		}
	};

	private static volatile Facts facts;

	private MobsBridge() {
	}

	static void setFacts(final Facts stand) {
		facts = stand;
	}

	public static int threat(final Mob mob) {
		return facts().threat(mob);
	}

	public static String role(final Mob mob) {
		return facts().role(mob);
	}

	private static Facts facts() {
		Facts found = facts;
		if (found == null) {
			found = find();
			facts = found;
		}
		return found;
	}

	private static Facts find() {
		try {
			Class<?> mobs = Class.forName("com.stella.mobs.RpgFacts", true, MobsBridge.class.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle threat = lookup.findStatic(mobs, "threat", MethodType.methodType(int.class, Mob.class));
			MethodHandle role = lookup.findStatic(mobs, "role", MethodType.methodType(String.class, Mob.class));
			return new Facts() {
				@Override
				public int threat(final Mob mob) {
					try {
						return (int) threat.invokeExact(mob);
					} catch (Throwable e) {
						return 0;
					}
				}

				@Override
				public String role(final Mob mob) {
					try {
						return (String) role.invokeExact(mob);
					} catch (Throwable e) {
						return MobStats.MELEE;
					}
				}
			};
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}
}
