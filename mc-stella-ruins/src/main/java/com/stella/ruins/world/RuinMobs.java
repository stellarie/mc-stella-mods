package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/** What happens to themed mobs after they spawn: abilities, deaths, drops and the unique roll. Fabric events carry all of it. */
public final class RuinMobs {
	/** Ticks between two ability ticks. */
	public static final int ABILITY_TICKS = 5;

	private static final Map<UUID, Mob> WATCHED = new ConcurrentHashMap<>();
	private static int counter;

	private RuinMobs() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && isRuinMob(mob)) {
				WATCHED.put(mob.getUUID(), mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> WATCHED.remove(entity.getUUID()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(RuinMobs::onDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(RuinMobs::onDeath);
		ServerTickEvents.END_SERVER_TICK.register(server -> tickAbilities());
	}

	public static boolean isRuinMob(final Entity entity) {
		return entity.entityTags().contains(RuinMobKind.TAG_MOB);
	}

	/** The tag of a mob a boss or a trap called in. It rolls no kind table and no unique. */
	public static final String TAG_SUMMONED = "mod-stella-ruins.summoned";

	/** Marks a mob as called in by another mob or by a repeatable trigger. */
	public static void markSummoned(final Mob mob) {
		mob.addTag(TAG_SUMMONED);
	}

	/** True for a foe: an Enemy by vanilla class, or a ruin mob that carries the published hostile mark. */
	public static boolean isFoe(final Entity entity) {
		return entity instanceof Enemy || entity.entityTags().contains(RuinMobKind.TAG_HOSTILE);
	}

	/** The tag a mob carries for the core of the run that spawned it. */
	public static final String TAG_CORE_PREFIX = "mod-stella-ruins.core.";

	/** The core tag of a run, so a mob can say which core called it. */
	public static String coreTag(final int x, final int y, final int z) {
		return TAG_CORE_PREFIX + x + "_" + y + "_" + z;
	}

	/** True when two mobs came from the same run. A mob of one ruin never answers another. */
	public static boolean sameCore(final Entity one, final Entity other) {
		for (String tag : one.entityTags()) {
			if (tag.startsWith(TAG_CORE_PREFIX) && other.entityTags().contains(tag)) {
				return true;
			}
		}
		return false;
	}

	/** The kind a mob belongs to, or null for any other entity. */
	public static @Nullable RuinMobKind kindOf(final Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(RuinMobKind.TAG_KIND_PREFIX)) {
				return RuinMobKind.get(tag.substring(RuinMobKind.TAG_KIND_PREFIX.length()));
			}
		}
		return null;
	}

	/** One unique roll, at the configured chance. */
	public static boolean rollUnique(final RandomSource random) {
		return random.nextDouble() < RuinConfig.get().uniqueDropChance();
	}

	private static void tickAbilities() {
		if (++counter % ABILITY_TICKS != 0) {
			return;
		}
		for (Mob mob : new ArrayList<>(WATCHED.values())) {
			if (mob.isRemoved() || !mob.isAlive()) {
				WATCHED.remove(mob.getUUID());
				continue;
			}
			RuinMobKind kind = kindOf(mob);
			if (kind != null && kind.ability() != null && mob.level() instanceof ServerLevel level) {
				try {
					kind.ability().tick(mob, level);
				} catch (RuntimeException e) {
					StellaRuins.LOGGER.warn("[RUINS] ability of kind {} failed: {}", kind.id(), e.toString());
				}
			}
		}
	}

	private static void onDamage(final LivingEntity target, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked) {
		heardHit(source, target);
	}

	/** An ability hears every hit of its mob, a killing blow too. AFTER_DAMAGE does not fire for a death, so AFTER_DEATH covers it. */
	private static void heardHit(final DamageSource source, final LivingEntity target) {
		if (source.getEntity() instanceof Mob attacker && isRuinMob(attacker)) {
			RuinMobKind kind = kindOf(attacker);
			if (kind != null && kind.ability() != null) {
				try {
					kind.ability().onHurtTarget(attacker, target);
				} catch (RuntimeException e) {
					StellaRuins.LOGGER.warn("[RUINS] ability of kind {} failed: {}", kind.id(), e.toString());
				}
			}
		}
	}

	private static void onDeath(final LivingEntity entity, final DamageSource source) {
		heardHit(source, entity);
		if (!(entity instanceof Mob mob) || !isRuinMob(mob) || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer player && !mob.entityTags().contains(TAG_SUMMONED)) {
			dropExtras(level, mob, source, player);
		}
		RuinRuns.onMobDeath(level, mob);
	}

	/** A player's kill rolls the kind's table and the site's unique. Any other death rolls nothing extra. */
	private static void dropExtras(final ServerLevel level, final Mob mob, final DamageSource source, final ServerPlayer player) {
		RuinMobKind kind = kindOf(mob);
		if (kind != null && kind.drops() != null) {
			LootTable table = level.getServer().reloadableRegistries().getLootTable(kind.drops());
			LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.THIS_ENTITY, mob)
				.withParameter(LootContextParams.ORIGIN, mob.position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
				.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
				.withLuck(player.getLuck())
				.create(LootContextParamSets.ENTITY);
			for (ItemStack stack : table.getRandomItems(params)) {
				mob.spawnAtLocation(level, stack);
			}
		}
		RuinData data = RuinData.get(level);
		BlockPos core = data.coreOf(mob.getUUID());
		RuinData.Entry entry = core == null ? null : data.entry(core);
		RuinSite site = entry == null ? null : RuinSites.get(entry.site());
		if (site != null && site.hasUnique() && rollUnique(level.getRandom())) {
			ItemStack unique = site.newUnique();
			mob.spawnAtLocation(level, unique);
			StellaRuins.LOGGER.debug("[RUINS] unique {} dropped by kind {} at {}", unique.getItem(), kind == null ? "?" : kind.id(), mob.blockPosition().toShortString());
		}
	}
}
