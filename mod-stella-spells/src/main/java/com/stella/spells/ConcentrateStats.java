package com.stella.spells;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Concentrate's two halves, handed to the RPG mod's own seams: a flat 25 Dexterity through its stat bonuses,
 * and half again on both halves of a critical roll through its critical bonuses. Both classes are found by
 * name, so this mod still loads and behaves as it does today when the RPG mod is absent: the lookups fail
 * once, quietly, and every question below answers zero.
 */
public final class ConcentrateStats {
	/** The flat Dexterity Concentrate grants while a bow or a crossbow is held. */
	public static final double CONCENTRATE_DEXTERITY = 25.0;
	/** The share Concentrate adds to the critical chance and to the critical damage, each on its own seam. */
	public static final double CONCENTRATE_CRITICAL_HALF = 0.50;

	private static final String STAT_BONUSES_CLASS = "com.stella.rpg.StatBonuses";
	private static final String STAT_CLASS = "com.stella.rpg.Stat";
	private static final String CRIT_BONUSES_CLASS = "com.stella.rpg.CritBonuses";
	private static final String DEX_KEY = "dex";

	/** The RPG mod's DEX constant, handed to that stat provider on every read; null without the RPG mod. */
	private static final Object DEX_STAT = dexStat();
	/** The stat provider the RPG mod holds, or null; the proxy answers through {@link #answerStat}. */
	private static final Object STAT_PROVIDER = provider(STAT_BONUSES_CLASS, ConcentrateStats::answerStat);
	/** The critical provider the RPG mod holds, or null; the proxy answers through {@link #answerCritical}. */
	private static final Object CRITICAL_PROVIDER = provider(CRIT_BONUSES_CLASS, ConcentrateStats::answerCritical);

	private ConcentrateStats() {
	}

	/** Registers both halves once, at init, in the shape the other bridges already use. */
	static void register() {
		offering(STAT_BONUSES_CLASS, STAT_PROVIDER);
		offering(CRIT_BONUSES_CLASS, CRITICAL_PROVIDER);
	}

	/** True when a caster is worth Concentrate's numbers: the effect is on and a bow or crossbow is held. */
	public static boolean worth(final LivingEntity caster) {
		return caster instanceof ServerPlayer player && player.hasEffect(SpellStatus.CONCENTRATE)
			&& ArcherSkills.hasBowOrCrossbow(player);
	}

	/** The Dexterity the caster's own seam grants, or 0.0 when this mod runs without the RPG mod. */
	public static double dexterityBonus(final LivingEntity caster) {
		if (DEX_STAT == null || STAT_PROVIDER == null || !worth(caster)) {
			return 0.0;
		}
		Object answer = calling(STAT_BONUSES_CLASS, "Provider", "bonus", STAT_PROVIDER,
			new Class<?>[] {LivingEntity.class, DEX_STAT.getClass()}, caster, DEX_STAT);
		return answer instanceof Integer value ? value.doubleValue() : 0.0;
	}

	/** The critical chance the caster's own seam grants, or 0.0 when this mod runs without the RPG mod. */
	public static double criticalChanceBonus(final LivingEntity caster) {
		return criticalHalf(caster, "chance");
	}

	/** The critical damage the caster's own seam grants, or 0.0 when this mod runs without the RPG mod. */
	public static double criticalDamageBonus(final LivingEntity caster) {
		return criticalHalf(caster, "damage");
	}

	// Both halves arrive in one Bonus object, so each read asks the provider once and takes its own accessor.
	private static double criticalHalf(final LivingEntity caster, final String accessor) {
		if (CRITICAL_PROVIDER == null || !worth(caster)) {
			return 0.0;
		}
		Object bonus = calling(CRIT_BONUSES_CLASS, "Provider", "bonus", CRITICAL_PROVIDER,
			new Class<?>[] {LivingEntity.class}, caster);
		if (bonus == null) {
			return 0.0;
		}
		Object value = calling(CRIT_BONUSES_CLASS, "Bonus", accessor, bonus, new Class<?>[0]);
		return value instanceof Double number ? number : 0.0;
	}

	// The stat answer: the asked number for a concentrating archer asked about DEX, and nothing otherwise.
	private static Object answerStat(final Method method, final Object[] arguments) {
		if (!method.getName().equals("bonus") || arguments.length != 2 || arguments[1] != DEX_STAT) {
			return 0;
		}
		return arguments[0] instanceof LivingEntity caster && worth(caster) ? (int) CONCENTRATE_DEXTERITY : 0;
	}

	// The critical answer: both halves for a concentrating archer, and an empty bonus otherwise.
	private static Object answerCritical(final Method method, final Object[] arguments) {
		if (!method.getName().equals("bonus") || arguments.length != 1) {
			return null;
		}
		boolean worth = arguments[0] instanceof LivingEntity caster && worth(caster);
		return bonus(worth ? CONCENTRATE_CRITICAL_HALF : 0.0, worth ? CONCENTRATE_CRITICAL_HALF : 0.0);
	}

	// The RPG mod's own Bonus record, built by its canonical constructor; null when it is not there.
	private static Object bonus(final double chance, final double damage) {
		try {
			Class<?> type = Class.forName(CRIT_BONUSES_CLASS + "$Bonus", true, ConcentrateStats.class.getClassLoader());
			return type.getConstructor(double.class, double.class).newInstance(chance, damage);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return null;
		}
	}

	// One provider, implemented as a dynamic proxy so the RPG mod receives the very interface it declares.
	private static Object provider(final String ownerName, final Answer answer) {
		Class<?> owner = loading(ownerName);
		Class<?> provider = loading(ownerName + "$Provider");
		if (owner == null || provider == null) {
			return null;
		}
		return Proxy.newProxyInstance(provider.getClassLoader(), new Class<?>[] {provider},
			(proxy, method, arguments) -> answering(answer, proxy, method, arguments));
	}

	private static Object answering(final Answer answer, final Object proxy, final Method method, final Object[] arguments) {
		if (method.getDeclaringClass() == Object.class) {
			return switch (method.getName()) {
				case "toString" -> "concentrate stats provider";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == arguments[0];
				default -> null;
			};
		}
		try {
			return answer.to(method, arguments == null ? new Object[0] : arguments);
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	// Hands one provider to the RPG mod's own register, once; absent classes and absent methods are silent.
	private static void offering(final String ownerName, final Object provider) {
		if (provider == null) {
			return;
		}
		Class<?> owner = loading(ownerName);
		if (owner == null) {
			return;
		}
		for (Method candidate : owner.getMethods()) {
			if (!candidate.getName().equals("register") || candidate.getParameterCount() != 1) {
				continue;
			}
			try {
				candidate.invoke(null, provider);
				Spellbooks.LOGGER.info("[SPELL] event=CONCENTRATE_SEAM class={}", ownerName);
				return;
			} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
				Spellbooks.LOGGER.info("[SPELL] event=CONCENTRATE_SEAM_ABSENT class={} reason={}", ownerName, e.getClass().getSimpleName());
				return;
			}
		}
	}

	// Calls one method on the RPG mod's own class, the declaring type named so the proxy is never asked for it.
	private static Object calling(
		final String ownerName, final String nested, final String method,
		final Object target, final Class<?>[] types, final Object... arguments
	) {
		Class<?> holder = loading(ownerName + (nested.isEmpty() ? "" : "$" + nested));
		if (holder == null) {
			return null;
		}
		try {
			return holder.getMethod(method, types).invoke(target, arguments);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return null;
		}
	}

	private static Class<?> loading(final String className) {
		try {
			return Class.forName(className, false, ConcentrateStats.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError | RuntimeException e) {
			return null;
		}
	}

	private static Object dexStat() {
		Class<?> stat = loading(STAT_CLASS);
		if (stat == null) {
			return null;
		}
		for (Object value : stat.getEnumConstants()) {
			if (((Enum<?>) value).name().equalsIgnoreCase(DEX_KEY)) {
				return value;
			}
		}
		return null;
	}

	/** What one provider call answers, given the interface method and its arguments. */
	@FunctionalInterface
	private interface Answer {
		Object to(Method method, Object[] arguments);
	}
}
