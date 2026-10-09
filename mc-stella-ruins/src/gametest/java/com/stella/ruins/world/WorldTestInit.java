package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Registers the test-only content: sites, a mob kind and an exclusive item. Nothing here ships in the jar. The
 * sites differ in their objective, claim and place, and share one layout.
 */
public final class WorldTestInit implements ModInitializer {
	static final String NAMESPACE = "mod-stella-ruins-test";
	static final ResourceKey<LootTable> VAULT_LOOT = table("vault_loot");
	static final ResourceKey<LootTable> CHEST_LOOT = table("chest_loot");
	static final ResourceKey<LootTable> HUSK_DROPS = table("husk_drops");

	/** Counts the ticks the test kind's ability ran, and the hits it landed. */
	static final AtomicInteger ABILITY_TICKS = new AtomicInteger();
	/** Hits the test kind ability heard, by attacker, so tests running side by side never count each other. */
	static final java.util.Map<java.util.UUID, AtomicInteger> ABILITY_HITS = new java.util.concurrent.ConcurrentHashMap<>();

	/** Fabric damage and death events seen by victim, so a test can show a hit raised one. */
	static final java.util.Map<java.util.UUID, AtomicInteger> DAMAGE_EVENTS = new java.util.concurrent.ConcurrentHashMap<>();
	static final java.util.Map<java.util.UUID, AtomicInteger> DEATH_EVENTS = new java.util.concurrent.ConcurrentHashMap<>();

	static int count(final java.util.Map<java.util.UUID, AtomicInteger> events, final java.util.UUID id) {
		AtomicInteger n = events.get(id);
		return n == null ? 0 : n.get();
	}

	static int hitsOf(final Mob mob) {
		AtomicInteger hits = ABILITY_HITS.get(mob.getUUID());
		return hits == null ? 0 : hits.get();
	}

	static RuinMobKind husk;
	static Item relic;
	static RuinSite surface;
	static RuinSite reset;
	static RuinSite waves;
	static RuinSite offer;
	static RuinSite keys;
	static RuinSite sequence;
	static RuinSite large;
	static RuinSite cave;
	static RuinSite findable;
	static RuinSite trophy;
	static RuinSite leader;

	static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(NAMESPACE, path));
	}

	/** Chunks round a test player that tick entities. Each arena holds a player, so a wide ring costs the whole suite. */
	static final int SIMULATION_DISTANCE = 4;

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> DAMAGE_EVENTS.computeIfAbsent(entity.getUUID(), id -> new AtomicInteger()).incrementAndGet());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> DEATH_EVENTS.computeIfAbsent(entity.getUUID(), id -> new AtomicInteger()).incrementAndGet());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			server.getPlayerList().setSimulationDistance(SIMULATION_DISTANCE);
			server.getPlayerList().setViewDistance(SIMULATION_DISTANCE);
			// Trophies and leads add stacks to a vault, so the suite pins them off and a test that needs one turns it on.
			RuinConfig.replace(RuinConfig.get().withTrophy(0.0, 0.0).withLeadChance(0.0));
		});
		ResourceKey<Item> relicKey = ResourceKey.create(Registries.ITEM, StellaRuins.id("test_relic"));
		relic = Registry.register(BuiltInRegistries.ITEM, relicKey, new Item(new Item.Properties().setId(relicKey)));

		husk = RuinMobKind.builder("test_husk")
			.base(EntityTypes.HUSK)
			.name("mob.mod-stella-ruins-test.husk")
			.scale(1.25F)
			.health(2.0)
			.damage(1.5)
			.speed(1.0)
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.IRON_HELMET))
			.effect(MobEffects.FIRE_RESISTANCE, 0)
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					ABILITY_TICKS.incrementAndGet();
				}

				@Override
				public void onHurtTarget(final Mob mob, final LivingEntity target) {
					ABILITY_HITS.computeIfAbsent(mob.getUUID(), id -> new AtomicInteger()).incrementAndGet();
				}
			})
			.drops(HUSK_DROPS)
			.register();

		Roster three = Roster.builder().add(husk, 3).build();
		Roster two = Roster.builder().add(husk, 2).build();
		Roster one = Roster.builder().add(husk, 1).build();
		SiteLayout yard = TestLayouts.yard(24, 10, 24, CHEST_LOOT, false);

		surface = base("test_surface", yard).placement(Placement.surface(3)).roster(three).claim(Claim.once())
			.objective(Objectives::clear).unique(() -> new ItemStack(Items.NETHER_STAR)).clueWeight(3).register();
		reset = base("test_reset", yard).placement(Placement.surface(3)).roster(two).claim(Claim.resetAfterDays(1))
			.objective(Objectives::clear).register();
		waves = base("test_waves", yard).placement(Placement.surface(3))
			.objective(() -> Objectives.waves(WavePlan.of(two, one))).register();
		offer = base("test_offer", yard).placement(Placement.surface(3))
			.objective(() -> Objectives.offer(stack -> stack.is(Items.DIAMOND))).register();
		keys = base("test_keys", yard).placement(Placement.surface(3))
			.objective(() -> Objectives.keys(Items.GOLD_INGOT, 3)).register();
		sequence = base("test_sequence", yard).placement(Placement.surface(3))
			.objective(() -> Objectives.sequence(Objectives.offer(stack -> stack.is(Items.DIAMOND)), Objectives.waves(WavePlan.of(one)))).register();
		large = RuinSite.builder("test_large").size(SiteSize.LARGE).placement(Placement.surface(3)).bounds(64, 48, 64)
			.layout(TestLayouts.yard(64, 48, 64, CHEST_LOOT, true)).objective(Objectives::clear).vaultLoot(VAULT_LOOT).register();
		cave = RuinSite.builder("test_cave").placement(Placement.cave(-40, 20, 8)).bounds(20, 12, 20)
			.layout(TestLayouts.yard(20, 12, 20, CHEST_LOOT, false)).objective(Objectives::clear).vaultLoot(VAULT_LOOT).clueWeight(1).register();
		trophy = base("test_trophy", yard).placement(Placement.surface(3)).roster(one).claim(Claim.resetAfterDays(1))
			.objective(Objectives::clear).unique(() -> new ItemStack(Items.NETHER_STAR)).register();
		leader = base("test_leader", yard).placement(Placement.surface(3)).roster(one).claim(Claim.resetAfterDays(1))
			.objective(Objectives::clear).leads("test_findable").register();
		findable = base("test_findable", yard).placement(Placement.surface(3)).roster(one).objective(Objectives::clear).register();
	}

	private static RuinSite.Builder base(final String id, final SiteLayout layout) {
		return RuinSite.builder(id).size(SiteSize.SMALL).bounds(24, 10, 24).layout(layout).vaultLoot(VAULT_LOOT);
	}
}
