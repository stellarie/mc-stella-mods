package com.stella.spells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.Nullable;

/**
 * Refining a spell or skill book. Two identical books in an anvil make the book one level higher, up to +15,
 * and the level lives in the stack's custom data under the key the equips mod always used.
 * <p>
 * Every level adds 2 percent to the book's main stat: its damage, else its heal, else its resistance, else the
 * caster's MATK for a few seconds after a cast. The milestones add to that: +7 shortens the cooldown, +10
 * adds an effect for the book's kind, +12 cuts the cost, and +15 gives the book its own signature.
 */
public final class BookRefine {
	public static final String LEVEL_KEY = "stella_refine";
	public static final int MAX_LEVEL = 15;
	/** What each level adds to the main stat. */
	public static final double STAT_PER_LEVEL = 0.02;
	public static final int COOLDOWN_LEVEL = 7;
	public static final int STRETCH_LEVEL = 10;
	public static final int COST_LEVEL = 12;
	public static final int SIGNATURE_LEVEL = 15;
	public static final double COOLDOWN_CUT = 0.10;
	public static final double COST_CUT = 0.15;
	/** How much longer the effects of a resistance or utility cast last from +10. */
	public static final double STRETCH = 1.25;
	/** How long a cast leaves the MATK buff: ten seconds. */
	public static final int FOCUS_TICKS = 200;
	/** The ObjectShare key of the MATK a caster holds from a cast, as a {@code ToDoubleFunction<Object>} of the entity. */
	public static final String MATK_SHARE = "mod-stella-spells:matk-bonus";
	private static final String OLD_LORE_PREFIX = "Refine +";
	private static final double WATCH_RADIUS = 24.0;
	private static final int FIRE_TICKS = 80;
	private static final int SLOW_TICKS = 60;
	private static final int RIDER_TICKS = 600;

	/** The number of a book a refine level scales. */
	public enum Stat {
		DAMAGE, HEAL, RESISTANCE, MATK
	}

	/** The four vanilla Resistance books. */
	private static final Set<String> RESISTANCE_BOOKS = Set.of("stoneguard", "aegis_of_the_deep", "war_cry", "iron_skin");
	/** Grappling Hook still carries a damage number from the fan it replaced, but its cast deals none. */
	private static final Set<String> NO_DAMAGE_BOOKS = Set.of("multishot");
	private static final Map<Identifier, Stat> STATS = new ConcurrentHashMap<>();
	/** The refine level of the last cast of each book, by caster, for the buffs that act after the cast. */
	private static final Map<UUID, Map<String, Integer>> CAST_LEVELS = new ConcurrentHashMap<>();
	/** A fire or a slow a damage cast owes to the next foe its caster hurts. */
	private static final Map<UUID, Rider> RIDERS = new ConcurrentHashMap<>();
	private static final List<Later> LATER = new CopyOnWriteArrayList<>();

	private record Rider(School school, int expiresAt) {
	}

	private record Later(int due, ServerPlayer owner, Runnable action) {
	}

	/** What a cast saw before its effect ran: the effects on each body near the caster, and the clouds already there. */
	record Pending(int level, Stat stat, Map<UUID, Map<Holder<MobEffect>, Integer>> before, Set<UUID> clouds, int resistanceBefore) {
	}

	private BookRefine() {
	}

	public static void register() {
		FabricLoader.getInstance().getObjectShare().put(MATK_SHARE, (ToDoubleFunction<Object>) BookRefine::matkBonus);
		ServerTickEvents.END_SERVER_TICK.register(BookRefine::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BookRefine::afterDamage);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CAST_LEVELS.clear();
			RIDERS.clear();
			LATER.clear();
		});
	}

	// ---- the level ----

	/** The refine level of a book stack; 0 for anything that is not a spell or skill book. */
	public static int level(final ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof SpellbookItem)) {
			return 0;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : Math.max(0, Math.min(MAX_LEVEL, data.copyTag().getIntOr(LEVEL_KEY, 0)));
	}

	/** A copy of the book at this level, with the glint of a refined book and no stale Refine line from the equips station. */
	public static ItemStack withLevel(final ItemStack book, final int level) {
		ItemStack out = book.copy();
		int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
		if (clamped == 0 && level(book) == 0) {
			return out;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> tag.putInt(LEVEL_KEY, clamped));
		if (clamped > 0) {
			out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		ItemLore lore = out.get(DataComponents.LORE);
		if (lore != null && lore.lines().stream().anyMatch(BookRefine::isOldRefineLine)) {
			out.set(DataComponents.LORE, new ItemLore(lore.lines().stream().filter(line -> !isOldRefineLine(line)).toList()));
		}
		return out;
	}

	/** True for the plain lore line the equips station once wrote on a refined book; this mod's own line is a translation. */
	static boolean isOldRefineLine(final Component line) {
		return !(line.getContents() instanceof TranslatableContents) && line.getString().startsWith(OLD_LORE_PREFIX);
	}

	/** The refined book two identical books make in the anvil, or empty when they cannot. The right book's level never matters. */
	public static ItemStack combine(final ItemStack left, final ItemStack right) {
		if (!(left.getItem() instanceof SpellbookItem first) || !(right.getItem() instanceof SpellbookItem second)
			|| !first.definition().id().equals(second.definition().id())) {
			return ItemStack.EMPTY;
		}
		int level = level(left);
		return level >= MAX_LEVEL ? ItemStack.EMPTY : withLevel(left, level + 1);
	}

	/** What the next step costs: the level it reaches. */
	public static int anvilCost(final ItemStack left) {
		return level(left) + 1;
	}

	// ---- the main stat ----

	/** The main stat of a book: its damage, else its heal, else its resistance, else MATK. */
	public static Stat statOf(final SpellDefinition definition) {
		return STATS.computeIfAbsent(definition.id(), id -> classify(definition));
	}

	private static Stat classify(final SpellDefinition definition) {
		String path = definition.id().getPath();
		SpellNumbers numbers = SpellNumbers.find(definition.id());
		if (numbers != null && numbers.damage() > 0.0F && !NO_DAMAGE_BOOKS.contains(path)) {
			return Stat.DAMAGE;
		}
		if (numbers != null && (numbers.heal() > 0.0F || numbers.allyHeal() > 0.0F)) {
			return Stat.HEAL;
		}
		return RESISTANCE_BOOKS.contains(path) ? Stat.RESISTANCE : Stat.MATK;
	}

	/** The factor a level puts on the main stat: 1 plus 2 percent a level. */
	public static double statFactor(final int level) {
		return 1.0 + STAT_PER_LEVEL * Math.max(0, level);
	}

	/** The factor a cast's power takes: the stat factor for a damage or heal book, else 1. */
	public static double powerFactor(final @Nullable SpellDefinition definition, final int level) {
		if (definition == null || level <= 0) {
			return 1.0;
		}
		Stat stat = statOf(definition);
		return stat == Stat.DAMAGE || stat == Stat.HEAL ? statFactor(level) : 1.0;
	}

	/**
	 * The cooldown after the milestones. +7 cuts a tenth. +12 cuts the cost by 15 percent: a book that spends
	 * arrows takes it there, and every other book has nothing to spend, so it takes it as rest.
	 */
	public static int cooldown(final int ticks, final @Nullable SpellDefinition definition, final int level) {
		if (definition == null || level < COOLDOWN_LEVEL) {
			return ticks;
		}
		double factor = 1.0 - COOLDOWN_CUT;
		if (level >= COST_LEVEL && !ArcherSkills.AMMO_BOOKS.contains(definition.id().getPath())) {
			factor *= 1.0 - COST_CUT;
		}
		return Math.max(20, (int) Math.round(ticks * factor));
	}

	/** The arrows a book spends once the +12 cut is counted; never fewer than one. */
	public static int arrowCost(final int base, final int level) {
		return level >= COST_LEVEL ? Math.max(1, (int) Math.round(base * (1.0 - COST_CUT))) : base;
	}

	/** The MATK a caster holds from a refined utility cast, as a fraction; 0 without the buff. */
	public static double matkBonus(final Object entity) {
		if (!(entity instanceof LivingEntity living)) {
			return 0.0;
		}
		MobEffectInstance focus = living.getEffect(SpellStatus.REFINED_FOCUS);
		return focus == null ? 0.0 : STAT_PER_LEVEL * (focus.getAmplifier() + 1);
	}

	/** The damage a hit does once the victim's refined guard has taken its share. */
	static float guard(final LivingEntity victim, final DamageSource source, final float amount) {
		MobEffectInstance guard = victim.getEffect(SpellStatus.REFINED_GUARD);
		if (guard == null || source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
			return amount;
		}
		return (float) (amount * (1.0 - STAT_PER_LEVEL * (guard.getAmplifier() + 1)));
	}

	// ---- what a cast leaves behind ----

	/** The refine level the caster's last cast of this book had; 0 for a plain book, a scroll or no cast. */
	static int castLevel(final UUID caster, final String path) {
		Map<String, Integer> levels = CAST_LEVELS.get(caster);
		return levels == null ? 0 : levels.getOrDefault(path, 0);
	}

	/** Notes the level a cast carries, a plain cast included, so a later buff never reads an older book's level. */
	static void remember(final ServerPlayer caster, final String path, final int level) {
		CAST_LEVELS.computeIfAbsent(caster.getUUID(), id -> new ConcurrentHashMap<>()).put(path, level);
	}

	/** Reads the scene before a cast's effect runs; null for a book with no level. */
	static @Nullable Pending begin(final ServerPlayer caster, final SpellDefinition definition, final ItemStack stack) {
		int level = level(stack);
		remember(caster, definition.id().getPath(), level);
		if (level <= 0) {
			return null;
		}
		Stat stat = statOf(definition);
		Map<UUID, Map<Holder<MobEffect>, Integer>> before = new HashMap<>();
		Set<UUID> clouds = new java.util.HashSet<>();
		boolean stretches = level >= STRETCH_LEVEL && (stat == Stat.MATK || stat == Stat.RESISTANCE);
		if (stretches) {
			ServerLevel world = caster.level();
			for (LivingEntity body : world.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(WATCH_RADIUS), LivingEntity::isAlive)) {
				Map<Holder<MobEffect>, Integer> durations = new HashMap<>();
				for (MobEffectInstance effect : body.getActiveEffects()) {
					durations.put(effect.getEffect(), effect.getDuration());
				}
				before.put(body.getUUID(), durations);
			}
			for (AreaEffectCloud cloud : world.getEntitiesOfClass(AreaEffectCloud.class, caster.getBoundingBox().inflate(WATCH_RADIUS))) {
				clouds.add(cloud.getUUID());
			}
		}
		MobEffectInstance resistance = caster.getEffect(MobEffects.RESISTANCE);
		return new Pending(level, stat, before, clouds, resistance == null ? -1 : resistance.getDuration());
	}

	/** Adds what the level brings once the cast has landed. The cast's hits do not count toward its book's awakening. */
	static void finish(final @Nullable Pending pending, final RefineCast cast) {
		if (pending == null) {
			return;
		}
		CastHits.pause();
		try {
			ServerPlayer caster = cast.caster();
			int level = pending.level();
			switch (pending.stat()) {
				case MATK -> {
					stretch(pending, cast);
					grantFocus(caster, level);
				}
				case RESISTANCE -> {
					stretch(pending, cast);
					grantGuard(pending, caster);
				}
				case DAMAGE -> {
					if (level >= STRETCH_LEVEL) {
						strike(cast);
					}
				}
				case HEAL -> {
					if (level >= STRETCH_LEVEL) {
						cleanse(cast);
					}
				}
			}
			if (level >= SIGNATURE_LEVEL) {
				runSignature(cast);
			}
		} finally {
			CastHits.resume();
		}
	}

	private static void grantFocus(final ServerPlayer caster, final int level) {
		int ticks = level >= STRETCH_LEVEL ? (int) Math.round(FOCUS_TICKS * STRETCH) : FOCUS_TICKS;
		caster.addEffect(new MobEffectInstance(SpellStatus.REFINED_FOCUS, ticks, level - 1, false, true, true));
	}

	private static void grantGuard(final Pending pending, final ServerPlayer caster) {
		MobEffectInstance resistance = caster.getEffect(MobEffects.RESISTANCE);
		if (resistance == null || resistance.getDuration() <= pending.resistanceBefore()) {
			return;
		}
		caster.addEffect(new MobEffectInstance(SpellStatus.REFINED_GUARD, resistance.getDuration(), pending.level() - 1, false, true, true));
	}

	/** +10 on a resistance or utility book: every effect the cast applied lasts a quarter longer. */
	private static void stretch(final Pending pending, final RefineCast cast) {
		if (pending.level() < STRETCH_LEVEL) {
			return;
		}
		ServerLevel world = cast.caster().level();
		for (Map.Entry<UUID, Map<Holder<MobEffect>, Integer>> entry : pending.before().entrySet()) {
			if (!(world.getEntity(entry.getKey()) instanceof LivingEntity body) || !body.isAlive()) {
				continue;
			}
			for (MobEffectInstance effect : List.copyOf(body.getActiveEffects())) {
				Integer was = entry.getValue().get(effect.getEffect());
				if (effect.isInfiniteDuration() || was != null && effect.getDuration() <= was) {
					continue;
				}
				body.addEffect(new MobEffectInstance(effect.getEffect(), (int) Math.round(effect.getDuration() * STRETCH),
					effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon()));
			}
		}
		for (AreaEffectCloud cloud : world.getEntitiesOfClass(AreaEffectCloud.class, cast.caster().getBoundingBox().inflate(WATCH_RADIUS))) {
			if (!pending.clouds().contains(cloud.getUUID()) && cloud.getOwner() == cast.caster()) {
				cloud.setDuration((int) Math.round(cloud.getDuration() * STRETCH));
			}
		}
	}

	/** +10 on a damage book: the foes it struck burn (mage, priest) or slow (fighter, archer). A cast that struck none leaves a rider. */
	private static void strike(final RefineCast cast) {
		School school = cast.definition().school();
		List<Mob> struck = new ArrayList<>(cast.victims());
		if (cast.target() != null && !struck.contains(cast.target())) {
			struck.add(cast.target());
		}
		if (struck.isEmpty()) {
			int now = cast.caster().level().getServer().getTickCount();
			RIDERS.put(cast.caster().getUUID(), new Rider(school, now + RIDER_TICKS));
			return;
		}
		for (Mob foe : struck) {
			setback(foe, school, cast.caster());
		}
	}

	private static void setback(final LivingEntity foe, final School school, final ServerPlayer caster) {
		if (!foe.isAlive()) {
			return;
		}
		if (school == School.MAGE || school == School.PRIEST) {
			foe.igniteForTicks(FIRE_TICKS);
		} else {
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1), caster);
		}
	}

	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked
	) {
		if (damage <= 0.0F || !(source.getEntity() instanceof ServerPlayer caster) || !(victim instanceof Mob foe) || !Friends.isFoe(foe)) {
			return;
		}
		Rider rider = RIDERS.get(caster.getUUID());
		if (rider == null) {
			return;
		}
		RIDERS.remove(caster.getUUID());
		if (caster.level().getServer().getTickCount() <= rider.expiresAt()) {
			setback(foe, rider.school(), caster);
		}
	}

	/** +10 on a heal book: one harmful effect leaves each body the cast healed. */
	private static void cleanse(final RefineCast cast) {
		LivingEntity healed = cast.definition().targetMode() == TargetMode.ALLY && cast.ally() != null ? cast.ally() : cast.caster();
		for (MobEffectInstance effect : healed.getActiveEffects()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				healed.removeEffect(effect.getEffect());
				return;
			}
		}
	}

	// ---- signatures ----

	/** True when the book has a +15 signature. */
	public static boolean hasSignature(final SpellDefinition definition) {
		return BookSignatures.of(definition.id().getPath()) != null;
	}

	/** Runs the cast's book's +15 signature; false when it has none. */
	static boolean runSignature(final RefineCast cast) {
		BookSignatures.Signature signature = BookSignatures.of(cast.definition().id().getPath());
		if (signature == null) {
			return false;
		}
		signature.action().run(cast);
		Spellbooks.LOGGER.info("[SPELL] event=SIGNATURE caster={} book={}", cast.caster().getUUID(), cast.definition().id());
		return true;
	}

	/** Runs an action after a delay on the server's own tick counter. */
	static void later(final ServerPlayer owner, final int ticks, final Runnable action) {
		LATER.add(new Later(owner.level().getServer().getTickCount() + ticks, owner, action));
	}

	/** Runs every waiting action now; a test seam. */
	static void flushLater(final MinecraftServer server) {
		List<Later> waiting = List.copyOf(LATER);
		LATER.clear();
		for (Later later : waiting) {
			run(later);
		}
	}

	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		RIDERS.values().removeIf(rider -> now > rider.expiresAt());
		for (Later later : LATER) {
			if (now >= later.due()) {
				LATER.remove(later);
				run(later);
			}
		}
	}

	private static void run(final Later later) {
		if (later.owner().isAlive() && !later.owner().isRemoved()) {
			try {
				later.action().run();
			} catch (RuntimeException error) {
				Spellbooks.LOGGER.warn("A refine signature failed after its delay", error);
			}
		}
	}

	// ---- the tooltip ----

	/** The tooltip lines of a refined book, whole: the level, the bonus and each unlocked milestone. */
	static List<Component> tooltip(final ItemStack stack) {
		int level = level(stack);
		if (level <= 0) {
			return List.of();
		}
		SpellDefinition definition = ((SpellbookItem) stack.getItem()).definition();
		String prefix = "spellbook." + Spellbooks.MOD_ID + ".refine";
		Stat stat = statOf(definition);
		List<Component> lines = new ArrayList<>();
		lines.add(Spellbooks.line(Component.translatable(prefix, level), ChatFormatting.AQUA));
		int percent = (int) Math.round(STAT_PER_LEVEL * level * 100.0);
		lines.add(Spellbooks.line(switch (stat) {
			case DAMAGE -> Component.translatable(prefix + ".damage", percent);
			case HEAL -> Component.translatable(prefix + ".heal", percent);
			case RESISTANCE -> Component.translatable(prefix + ".resistance", percent);
			case MATK -> Component.translatable(prefix + ".matk", percent, level >= STRETCH_LEVEL ? "12.5" : "10");
		}, ChatFormatting.GREEN));
		if (level >= COOLDOWN_LEVEL) {
			lines.add(Spellbooks.line(Component.translatable(prefix + ".m7"), ChatFormatting.LIGHT_PURPLE));
		}
		if (level >= STRETCH_LEVEL) {
			String key = switch (stat) {
				case DAMAGE -> definition.school() == School.MAGE || definition.school() == School.PRIEST ? ".m10.fire" : ".m10.slow";
				case HEAL -> ".m10.heal";
				case RESISTANCE, MATK -> ".m10.longer";
			};
			lines.add(Spellbooks.line(Component.translatable(prefix + key), ChatFormatting.LIGHT_PURPLE));
		}
		if (level >= COST_LEVEL) {
			boolean arrows = ArcherSkills.AMMO_BOOKS.contains(definition.id().getPath());
			lines.add(Spellbooks.line(Component.translatable(prefix + (arrows ? ".m12.arrows" : ".m12.cooldown")), ChatFormatting.LIGHT_PURPLE));
		}
		if (level >= SIGNATURE_LEVEL && hasSignature(definition)) {
			String path = definition.id().getPath();
			lines.add(Spellbooks.line(Component.translatable(prefix + ".m15",
				Component.translatable("spellbook." + Spellbooks.MOD_ID + ".sig." + path),
				Component.translatable("spellbook." + Spellbooks.MOD_ID + ".sig." + path + ".text")), ChatFormatting.GOLD));
		}
		return lines;
	}
}
