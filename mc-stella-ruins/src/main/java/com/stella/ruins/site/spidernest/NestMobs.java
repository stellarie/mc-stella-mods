package com.stella.ruins.site.spidernest;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The three themed mobs of the nest, and the loot tables the nest owns. */
public final class NestMobs {
	public static final ResourceKey<LootTable> VAULT_LOOT = table("chests/spider_nest");
	public static final ResourceKey<LootTable> HATCHED_VAULT_LOOT = table("chests/spider_nest_hatched");
	public static final ResourceKey<LootTable> WEAVER_DROPS = table("entities/nest_weaver");
	public static final ResourceKey<LootTable> CRAWLER_DROPS = table("entities/brood_crawler");
	public static final ResourceKey<LootTable> MATRIARCH_DROPS = table("entities/nest_matriarch");

	/** Ticks between two webs of one Nest Weaver. */
	public static final int WEB_COOLDOWN = 100;
	/** Ticks between two broods of the Matriarch. */
	public static final int BROOD_TICKS = 200;
	public static final int BROOD_SIZE = 2;
	/** The most Brood Crawlers alive at once, her own broods and the nest's swarm together. */
	public static final int CRAWLER_CAP = 8;
	/** The Matriarch counts crawlers this far around her. */
	private static final double COUNT_RANGE = 64.0;

	public static RuinMobKind NEST_WEAVER;
	public static RuinMobKind BROOD_CRAWLER;
	public static RuinMobKind NEST_MATRIARCH;

	/** The entity tick of each Weaver's last web. A weak key lets a dead Weaver go. */
	private static final Map<Mob, Integer> LAST_WEB = java.util.Collections.synchronizedMap(new WeakHashMap<>());

	private NestMobs() {
	}

	private static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, path));
	}

	public static void register() {
		NEST_WEAVER = RuinMobKind.builder("nest_weaver")
			.base(EntityTypes.SPIDER)
			.scale(1.1F)
			.ability(new MobAbility() {
				@Override
				public void onHurtTarget(final Mob mob, final LivingEntity target) {
					web(mob, target);
				}
			})
			.drops(WEAVER_DROPS)
			.register();
		BROOD_CRAWLER = RuinMobKind.builder("brood_crawler")
			.base(EntityTypes.CAVE_SPIDER)
			.scale(0.8F)
			.drops(CRAWLER_DROPS)
			.register();
		NEST_MATRIARCH = RuinMobKind.builder("nest_matriarch")
			.base(EntityTypes.SPIDER)
			.scale(2.2F)
			.health(6.0)
			.boss(true)
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					if (mob.tickCount >= BROOD_TICKS && mob.tickCount % BROOD_TICKS < RuinMobs.ABILITY_TICKS) {
						brood(mob, level);
					}
				}
			})
			.drops(MATRIARCH_DROPS)
			.register();
	}

	/**
	 * Puts a cobweb at the target's feet, at most once every {@link #WEB_COOLDOWN} ticks for each Weaver. The cooldown
	 * runs only when a web was placed, so a hit on a target that stands in a block does not use it up.
	 */
	public static boolean web(final Mob weaver, final LivingEntity target) {
		Level level = target.level();
		BlockPos feet = target.blockPosition();
		synchronized (LAST_WEB) {
			Integer last = LAST_WEB.get(weaver);
			if (last != null && weaver.tickCount >= last && weaver.tickCount - last < WEB_COOLDOWN) {
				return false;
			}
			if (!level.getBlockState(feet).isAir()) {
				return false;
			}
			level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
			LAST_WEB.put(weaver, weaver.tickCount);
		}
		return true;
	}

	/** How many Brood Crawlers live near a spot. */
	public static int crawlersNear(final ServerLevel level, final Vec3 at) {
		AABB area = new AABB(at, at).inflate(COUNT_RANGE);
		return level.getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive() && RuinMobs.kindOf(mob) == BROOD_CRAWLER).size();
	}

	/** The Matriarch calls her brood: two Crawlers beside her, never past the cap. Returns how many came. */
	public static int brood(final Mob mother, final ServerLevel level) {
		int room = CRAWLER_CAP - crawlersNear(level, mother.position());
		int spawned = 0;
		RuinData data = RuinData.get(level);
		BlockPos core = data.coreOf(mother.getUUID());
		RuinData.Entry entry = core == null ? null : data.entry(core);
		for (int i = 0; i < Math.min(BROOD_SIZE, room); i++) {
			double angle = (i + level.getRandom().nextDouble()) * Math.PI;
			double radius = 2.0 * mother.getScale() / 2.2 + 1.2;
			Vec3 at = new Vec3(mother.getX() + Math.cos(angle) * radius, mother.getY(), mother.getZ() + Math.sin(angle) * radius);
			Mob crawler = BROOD_CRAWLER.spawn(level, at);
			if (crawler != null) {
				RuinMobs.markSummoned(crawler);
				spawned++;
				if (entry != null) {
					entry.addMob(crawler.getUUID());
				}
			}
		}
		return spawned;
	}
}
