package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpellVisualsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.visuals");
	private static final double NEAR = 1.5;
	private static final Set<String> AREA_SPELLS = Set.of(
		"static_lash", "chainstorm", "void_well", "wardens_roar", "time_lock", "dragonfall",
		"sapping_hex", "sanctuary", "purify", "frost_nova", "earthspike", "plague_cloud",
		"flame_ring", "gravity", "bulwark_hymn", "tailwind", "battle_hymn"
	);

	static {
		SpellVisualLog.install();
	}

	@GameTest
	public void everySpellHasItsOwnVisual(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everySpellHasItsOwnVisual");
		Set<ParticleType<?>> primaries = new HashSet<>();
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			SpellVisuals.Visual visual = SpellVisuals.visual(definition.id());
			helper.assertTrue(visual != null, definition.id() + " has no visual");
			helper.assertTrue(visual.motif() != null, definition.id() + " has no named effect motif");
			helper.assertTrue(SpellVisuals.heldIconColor(definition.id()) >= 0, definition.id() + " has no held icon color");
			helper.assertTrue(primaries.add(visual.primary().getType()), definition.id() + " shares its primary particle with another spell");
		}
		LOG.info("[SPELLTEST] test=everySpellHasItsOwnVisual RESULT distinct={}", primaries.size());
		helper.succeed();
	}

	@GameTest
	public void everyHostileSpellPlaysItsVisualAtItsTarget(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everyHostileSpellPlaysItsVisualAtItsTarget");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper);
		helper.setBlock(2, 2, 8, Blocks.AIR);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		// Every hostile book lands on this one target, so it must outlast them all.
		target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
		target.setHealth(1000.0F);
		List<SpellbookItem> books = books(TargetMode.HOSTILE);
		// Tidal Push knocks the target off the aim line, so it goes last.
		books.sort(Comparator.comparing((SpellbookItem book) -> book.definition().id().getPath().equals("tidal_push")));
		for (SpellbookItem book : books) {
			Vec3 center = target.getBoundingBox().getCenter();
			cast(helper, player, book);
			assertPlayedNear(helper, player, book, center);
		}
		LOG.info("[SPELLTEST] test=everyHostileSpellPlaysItsVisualAtItsTarget RESULT spells={} visuals={}", books.size(), SpellVisualLog.played(player.getUUID()).size());
		helper.succeed();
	}

	@GameTest
	public void everySelfSpellPlaysItsVisualAroundTheCaster(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everySelfSpellPlaysItsVisualAroundTheCaster");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper);
		player.setHealth(4.0f);
		// Purify casts only with something to clear, and Sapping Hex only with a hostile close by.
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 600, 0));
		net.minecraft.world.entity.Mob hexed = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 4).withNoFreeWill().spawn();
		hexed.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
		hexed.setHealth(1000.0F);
		List<SpellbookItem> books = books(TargetMode.SELF);
		// Gale Step launches the caster, so it goes last.
		books.sort(Comparator.comparing((SpellbookItem book) -> book.definition().id().getPath().equals("gale_step")));
		for (SpellbookItem book : books) {
			// Each healing spell needs a wound to close.
			player.setHealth(4.0f);
			Vec3 center = player.position();
			cast(helper, player, book);
			assertPlayedNear(helper, player, book, center);
		}
		LOG.info("[SPELLTEST] test=everySelfSpellPlaysItsVisualAroundTheCaster RESULT spells={} visuals={}", books.size(), SpellVisualLog.played(player.getUUID()).size());
		helper.succeed();
	}

	@GameTest
	public void everyBlockSpellPlaysItsVisualAtTheAimedPoint(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everyBlockSpellPlaysItsVisualAtTheAimedPoint");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper);
		helper.setBlock(2, 2, 5, Blocks.STONE);
		Vec3 eye = player.getEyePosition();
		var hit = helper.getLevel().clip(new ClipContext(
			eye, eye.add(player.getLookAngle().scale(SpellCasting.RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
		));
		helper.assertTrue(hit.getType() == HitResult.Type.BLOCK, "the aimed block is not in the ray");
		// Chainstorm casts only with a hostile around the spot.
		helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 6).withNoFreeWill().spawn();
		List<SpellbookItem> books = books(TargetMode.BLOCK);
		for (SpellbookItem book : books) {
			cast(helper, player, book);
			assertPlayedNear(helper, player, book, hit.getLocation());
		}
		LOG.info("[SPELLTEST] test=everyBlockSpellPlaysItsVisualAtTheAimedPoint RESULT spells={} visuals={}", books.size(), SpellVisualLog.played(player.getUUID()).size());
		helper.succeed();
	}

	@GameTest
	public void riftwalkPlaysItsVisualAtDepartureAndArrival(final GameTestHelper helper) {
		SpellTestLog.track(helper, "riftwalkPlaysItsVisualAtDepartureAndArrival");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper);
		for (int z = 3; z <= 8; z++) {
			helper.setBlock(2, 0, z, Blocks.STONE);
		}
		List<SpellbookItem> books = books(TargetMode.SAFE_TELEPORT);
		for (SpellbookItem book : books) {
			player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 1.0, 7.5)));
			Vec3 departure = player.position();
			cast(helper, player, book);
			Vec3 arrival = player.position();
			helper.assertTrue(departure.distanceTo(arrival) > NEAR, book.definition().id() + " did not move far enough to tell its ends apart");
			assertPlayedNear(helper, player, book, departure);
			assertPlayedNear(helper, player, book, arrival);
		}
		LOG.info("[SPELLTEST] test=riftwalkPlaysItsVisualAtDepartureAndArrival RESULT spells={} visuals={}", books.size(), SpellVisualLog.played(player.getUUID()).size());
		helper.succeed();
	}

	@GameTest
	public void aFailedCastPlaysNoVisual(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aFailedCastPlaysNoVisual");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper);
		SpellbookItem book = books(TargetMode.HOSTILE).get(0);
		player.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		InteractionResult result = book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.PASS, "a cast with nothing to aim at did not fail: " + result);
		helper.assertTrue(SpellVisualLog.played(player.getUUID()).isEmpty(), "a failed cast played a visual");
		LOG.info("[SPELLTEST] test=aFailedCastPlaysNoVisual RESULT result={} visuals=0", result);
		helper.succeed();
	}

	@GameTest
	public void everyAreaSpellDrawsAnElementColoredMagicCircleAtItsEffectRadius(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everyAreaSpellDrawsAnElementColoredMagicCircleAtItsEffectRadius");
		Set<String> actualAreaSpells = Set.copyOf(SpellVisuals.areaSpells().stream().map(Identifier::getPath).toList());
		helper.assertTrue(actualAreaSpells.equals(AREA_SPELLS), "area visual registry differs from tested spells: " + actualAreaSpells);
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper);
		ServerPlayer farViewer = addPlayer(helper);
		Vec3 farPosition = helper.absoluteVec(new Vec3(42.5, 1.0, 2.5));
		farViewer.setPos(farPosition.x, farPosition.y, farPosition.z);
		helper.setBlock(2, 2, 5, Blocks.STONE);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 4).withNoFreeWill().spawn();
		target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
		target.setHealth(1000.0F);
		helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 6).withNoFreeWill().spawn();
		for (SpellbookItem book : Spellbooks.CATALOG) {
			String path = book.definition().id().getPath();
			if (!AREA_SPELLS.contains(path)) {
				continue;
			}
			Vec3 center;
			switch (book.definition().targetMode()) {
			case SELF -> {
				player.setHealth(4.0F);
				if (path.equals("purify")) {
					player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 600, 0));
				}
				center = player.position();
				}
				case HOSTILE -> {
					player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
					center = target.position();
				}
				case BLOCK -> {
					player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 2.5, 5.0)));
					center = helper.absoluteVec(new Vec3(2.5, 2.5, 5.0));
				}
				default -> throw new AssertionError("unexpected area target mode for " + path);
			}
			int before = SpellVisualLog.played(player.getUUID()).size();
			cast(helper, player, book);
			List<SpellVisualLog.Played> emitted = SpellVisualLog.played(player.getUUID()).subList(
				before, SpellVisualLog.played(player.getUUID()).size()
			);
			assertMagicCircle(helper, book.definition(), emitted, center, expectedRadius(path), expectedElementColor(path));
		}
		LOG.info("[SPELLTEST] test=everyAreaSpellDrawsAnElementColoredMagicCircleAtItsEffectRadius RESULT spells={}", AREA_SPELLS.size());
		helper.succeed();
	}

	@GameTest
	public void areaCircleEmitsASecondAnimationPhase(final GameTestHelper helper) {
		SpellTestLog.track(helper, "areaCircleEmitsASecondAnimationPhase");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper);
		SpellbookItem book = Spellbooks.CATALOG.stream()
			.filter(candidate -> candidate.definition().id().getPath().equals("frost_nova"))
			.findFirst().orElseThrow();
		int before = SpellVisualLog.played(player.getUUID()).size();
		cast(helper, player, book);
		int immediate = SpellVisualLog.played(player.getUUID()).size();
		helper.assertTrue(immediate > before, "the area cast emitted no immediate visual phase");
		int[] firstPhase = {-1};
		helper.runAfterDelay(3L, () -> {
			firstPhase[0] = SpellVisualLog.played(player.getUUID()).size();
			helper.assertTrue(firstPhase[0] > immediate, "the first delayed animation phase did not appear");
			helper.assertTrue(SpellVisualLog.played(player.getUUID()).subList(immediate, firstPhase[0]).stream()
			.anyMatch(entry -> entry.casterSaw() && entry.particles() > 0), "the caster could not see the first pulse");
		});
		helper.runAfterDelay(5L, () -> {
			int delayed = SpellVisualLog.played(player.getUUID()).size();
			helper.assertTrue(delayed > firstPhase[0], "the second delayed animation phase did not appear");
			helper.assertTrue(SpellVisualLog.played(player.getUUID()).subList(firstPhase[0], delayed).stream()
			.anyMatch(entry -> entry.casterSaw() && entry.particles() > 0), "the caster could not see the second pulse");
			LOG.info("[SPELLTEST] test=areaCircleEmitsASecondAnimationPhase RESULT immediate={} first={} second={}", immediate, firstPhase[0], delayed);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void heldSpellbookCircleFollowsFeetAndStopsAfterSwitch(final GameTestHelper helper) {
		SpellTestLog.track(helper, "heldSpellbookCircleFollowsFeetAndStopsAfterSwitch");
		ServerPlayer player = addPlayer(helper);
		SpellbookItem book = Spellbooks.SPARK_SHOT;
		SpellbookItem offhandBook = Spellbooks.CATALOG.get(1);
		player.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		Vec3 firstFeet = player.position();
		ServerPlayer farViewer = addPlayer(helper);
		farViewer.setPos(firstFeet.x + SpellVisuals.VIEW_RADIUS_BLOCKS + 32.0, firstFeet.y, firstFeet.z);
		int[] seen = {0};
		int[] totalAfterSwitch = {0};
		helper.runAfterDelay(11L, () -> {
			List<SpellVisualLog.Played> dust = heldDust(player, book);
			helper.assertTrue(!dust.isEmpty(), "a held spellbook emitted no foot circle");
			helper.assertTrue(dust.stream().anyMatch(entry -> atFootRing(entry.at(), firstFeet)
				&& entry.casterSaw() && dustRgb((DustParticleOptions) entry.particle()) == 0xF5D35E),
				"Spark Shot showed no visible lightning-colored circle at the holder's feet");
			seen[0] = dust.size();
			player.setItemInHand(InteractionHand.OFF_HAND, offhandBook.getDefaultInstance());
			player.setPos(firstFeet.x + 3.0, firstFeet.y, firstFeet.z);
		});
		helper.runAfterDelay(22L, () -> {
			List<SpellVisualLog.Played> dust = heldDust(player, book);
			helper.assertTrue(dust.size() > seen[0], "the held circle stopped before the book was deselected");
			helper.assertTrue(heldDust(player, offhandBook).isEmpty(), "the offhand color replaced a held main-hand book");
			helper.assertTrue(dust.subList(seen[0], dust.size()).stream()
				.anyMatch(entry -> atFootRing(entry.at(), player.position())), "the held circle did not follow the player");
			seen[0] = dust.size();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		helper.runAfterDelay(33L, () -> {
			List<SpellVisualLog.Played> offhandDust = heldDust(player, offhandBook);
			helper.assertTrue(!offhandDust.isEmpty(), "an offhand spellbook emitted no circle");
			helper.assertTrue(offhandDust.stream().anyMatch(entry -> atFootRing(entry.at(), player.position())
				&& dustRgb((DustParticleOptions) entry.particle()) == 0x74D7FF),
				"Frostbind showed no frost-colored offhand circle");
			helper.assertTrue(heldDust(player, book).size() == seen[0], "the old main-hand color kept emitting");
			int count = SpellVisualLog.played(player.getUUID()).size();
			int maxPulses = 33 / SpellVisuals.HELD_CIRCLE_INTERVAL_TICKS + 2;
			helper.assertTrue(count <= maxPulses * SpellVisuals.MAX_HELD_PARTICLES_PER_PULSE,
				"the held circle exceeded its 33-tick dust and glow budget: " + count);
			long glow = SpellVisualLog.played(player.getUUID()).stream()
				.filter(entry -> entry.particle().getType() == net.minecraft.core.particles.ParticleTypes.GLOW).count();
			helper.assertTrue(glow > 0 && glow <= maxPulses * SpellVisuals.HELD_GLOW_POINTS,
				"the held circle has an invalid glow count: " + glow);
			for (SpellVisualLog.Played entry : SpellVisualLog.played(player.getUUID())) {
				long nearby = helper.getLevel().players().stream()
					.filter(viewer -> viewer.position().distanceToSqr(entry.at()) <= SpellVisuals.VIEW_RADIUS_BLOCKS * SpellVisuals.VIEW_RADIUS_BLOCKS)
					.count();
				helper.assertTrue(entry.particles() <= nearby, "the held aura reached a distant viewer");
			}
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.MAIN_HAND, Spellbooks.SCROLLS.get(0).getDefaultInstance());
			totalAfterSwitch[0] = SpellVisualLog.played(player.getUUID()).size();
		});
		helper.runAfterDelay(44L, () -> {
			helper.assertTrue(SpellVisualLog.played(player.getUUID()).size() == totalAfterSwitch[0],
				"a held scroll emitted a spellbook circle");
			LOG.info("[SPELLTEST] test=heldSpellbookCircleFollowsFeetAndStopsAfterSwitch RESULT mainHand=true offHand=true bothHandsMainPriority=true colorChanged=true moved=true scroll=false stopped=true particles={}",
				totalAfterSwitch[0]);
			helper.succeed();
		});
	}

	@GameTest
	public void staticLashLinksOnlyItsActualChainVictim(final GameTestHelper helper) {
		SpellTestLog.track(helper, "staticLashLinksOnlyItsActualChainVictim");
		helper.setTime(18_000L);
		ServerPlayer caster = addPlayer(helper);
		Mob primary = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		Mob secondary = helper.spawnMob(EntityTypes.ZOMBIE, 3, 1, 7).withNoFreeWill().spawn();
		Mob outside = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 14).withNoFreeWill().spawn();
		SpellbookItem book = Spellbooks.CATALOG.stream()
			.filter(candidate -> candidate.definition().id().getPath().equals("static_lash"))
			.findFirst().orElseThrow();
		cast(helper, caster, book);
		Vec3 midpoint = primary.getBoundingBox().getCenter().add(secondary.getBoundingBox().getCenter()).scale(0.5);
		List<SpellVisualLog.Played> played = SpellVisualLog.played(caster.getUUID(), book.definition().id());
		boolean linked = played.stream().anyMatch(entry -> entry.particle() instanceof DustParticleOptions
			&& entry.at().distanceTo(midpoint) < 0.25 && entry.casterSaw());
		boolean outsideLinked = played.stream().anyMatch(entry -> entry.particle() instanceof DustParticleOptions
			&& entry.at().distanceTo(outside.getBoundingBox().getCenter()) < 0.5);
		helper.assertTrue(linked && !outsideLinked, "Static Lash did not link only its selected chain victim");
		LOG.info("[SPELLTEST] test=staticLashLinksOnlyItsActualChainVictim RESULT linked={} outsideLinked={}", linked, outsideLinked);
		helper.succeed();
	}

	@GameTest
	public void bulwarkHymnLinksTheAllyItBlessed(final GameTestHelper helper) {
		SpellTestLog.track(helper, "bulwarkHymnLinksTheAllyItBlessed");
		ServerPlayer caster = addPlayer(helper);
		ServerPlayer ally = addPlayer(helper);
		Vec3 allyFeet = caster.position().add(3.0, 0.0, 0.0);
		ally.setPos(allyFeet.x, allyFeet.y, allyFeet.z);
		SpellbookItem book = Spellbooks.CATALOG.stream()
			.filter(candidate -> candidate.definition().id().getPath().equals("bulwark_hymn"))
			.findFirst().orElseThrow();
		cast(helper, caster, book);
		Vec3 midpoint = caster.position().add(0.0, 1.0, 0.0).add(ally.position().add(0.0, 1.0, 0.0)).scale(0.5);
		boolean linked = SpellVisualLog.played(caster.getUUID(), book.definition().id()).stream()
			.anyMatch(entry -> entry.particle() instanceof DustParticleOptions && entry.at().distanceTo(midpoint) < 0.3 && entry.casterSaw());
		helper.assertTrue(linked, "Bulwark Hymn blessed an ally without a visible link");
		LOG.info("[SPELLTEST] test=bulwarkHymnLinksTheAllyItBlessed RESULT linked={}", linked);
		helper.succeed();
	}

	@GameTest
	public void bulwarkHymnBlessesTwentyAlliesWithBoundedVisualLinks(final GameTestHelper helper) {
		SpellTestLog.track(helper, "bulwarkHymnBlessesTwentyAlliesWithBoundedVisualLinks");
		ServerPlayer caster = addPlayer(helper);
		List<Mob> allies = new ArrayList<>();
		for (int z = 5; z <= 8; z++) {
			for (int x = 0; x < 5; x++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				allies.add(helper.spawnMob(EntityTypes.VILLAGER, x, 1, z).withNoFreeWill().spawn());
			}
		}
		SpellbookItem book = Spellbooks.CATALOG.stream()
			.filter(candidate -> candidate.definition().id().getPath().equals("bulwark_hymn"))
			.findFirst().orElseThrow();
		cast(helper, caster, book);
		long blessed = allies.stream().filter(ally -> ally.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)).count();
		helper.assertTrue(blessed == allies.size(), "Bulwark Hymn missed " + (allies.size() - blessed) + " allies");
		long links = SpellVisualLog.played(caster.getUUID(), book.definition().id()).stream()
			.filter(entry -> entry.particle() instanceof DustParticleOptions && entry.at().y > caster.position().y + 0.6)
			.count();
		helper.assertTrue(links > 0 && links <= SpellVisuals.MAX_EFFECT_LINK_RECIPIENTS * SpellVisuals.MAX_EFFECT_LINK_POINTS,
			"Bulwark Hymn emitted " + links + " link points for " + allies.size() + " allies");
		LOG.info("[SPELLTEST] test=bulwarkHymnBlessesTwentyAlliesWithBoundedVisualLinks RESULT allies={} blessed={} linkPoints={}", allies.size(), blessed, links);
		helper.succeed();
	}

	private static List<SpellVisualLog.Played> heldDust(final ServerPlayer player, final SpellbookItem book) {
		return SpellVisualLog.played(player.getUUID(), book.definition().id()).stream()
			.filter(entry -> entry.particle() instanceof DustParticleOptions)
			.toList();
	}

	private static boolean atFootRing(final Vec3 at, final Vec3 feet) {
		return Math.abs(horizontalDistance(at, feet) - SpellVisuals.HELD_CIRCLE_RADIUS) < 0.25
			&& Math.abs(at.y - feet.y) < 0.35;
	}

	private static void cast(final GameTestHelper helper, final ServerPlayer player, final SpellbookItem book) {
		player.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		InteractionResult result = book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, book.definition().id() + " did not cast: " + result);
	}

	private static void assertPlayedNear(final GameTestHelper helper, final ServerPlayer player, final SpellbookItem book, final Vec3 where) {
		SpellVisuals.Visual visual = SpellVisuals.visual(book.definition().id());
		helper.assertTrue(visual != null, book.definition().id() + " has no visual");
		List<SpellVisualLog.Played> played = SpellVisualLog.played(player.getUUID(), book.definition().id());
		boolean near = played.stream().anyMatch(entry -> entry.particle().getType() == visual.primary().getType()
			&& entry.casterSaw() && entry.particles() > 0 && entry.at().distanceTo(where) <= NEAR);
		helper.assertTrue(near, book.definition().id() + " played no visible " + visual.primary().getType() + " near " + where + ": " + played);
		long accentPoints = played.stream()
			.filter(entry -> entry.particle().getType() == visual.secondary().getType() && entry.casterSaw() && entry.particles() > 0)
			.map(SpellVisualLog.Played::at).distinct().count();
		helper.assertTrue(accentPoints >= SpellVisuals.ACCENT_POINTS,
			book.definition().id() + " has only " + accentPoints + " effect-specific accent points");
		String path = book.definition().id().getPath();
		if (path.equals("frostbind") || path.equals("chainstorm") || path.equals("battle_hymn")) {
			List<SpellVisualLog.Played> accents = played.stream()
				.filter(entry -> entry.particle().getType() == visual.secondary().getType() && entry.casterSaw())
				.toList();
			double lowest = accents.stream().mapToDouble(entry -> entry.at().y).min().orElse(where.y);
			double highest = accents.stream().mapToDouble(entry -> entry.at().y).max().orElse(where.y);
			helper.assertTrue(highest - lowest > 1.0, path + " has no visible vertical " + visual.motif() + " motion");
		}
		if (path.equals("soul_siphon")) {
			helper.assertTrue(played.stream().anyMatch(entry -> entry.particle().getType() == visual.secondary().getType()
				&& entry.at().distanceTo(player.position().add(0.0, 1.0, 0.0)) <= NEAR),
				"Soul Siphon has no soul tether reaching the caster");
		}
		LOG.info("[SPELLTEST] visual spell={} motif={} particle={} at={} shapes={} accentPoints={}", book.definition().id(),
			visual.motif(), BuiltInRegistries.PARTICLE_TYPE.getKey(visual.primary().getType()), where, played.size(), accentPoints);
	}

	private static void assertMagicCircle(
		final GameTestHelper helper, final SpellDefinition definition, final List<SpellVisualLog.Played> emitted,
		final Vec3 center, final double radius, final int elementColor
	) {
		List<SpellVisualLog.Played> dust = emitted.stream()
			.filter(entry -> entry.particle() instanceof DustParticleOptions && entry.casterSaw())
			.toList();
		helper.assertTrue(!dust.isEmpty(), definition.id() + " emitted no caster-visible magic-circle dust: " + emitted);
		helper.assertTrue(dust.stream().allMatch(entry -> dustRgb((DustParticleOptions) entry.particle()) == elementColor),
			definition.id() + " used a dust color unrelated to its element icon");
		double furthest = dust.stream().mapToDouble(entry -> horizontalDistance(entry.at(), center)).max().orElse(0.0);
		helper.assertTrue(Math.abs(furthest - radius) <= 0.35,
			definition.id() + " circle reached " + furthest + " instead of radius " + radius);
		long outer = dust.stream().filter(entry -> Math.abs(horizontalDistance(entry.at(), center) - radius) <= 0.35).count();
		helper.assertTrue(outer >= 8, definition.id() + " circle had only " + outer + " outer points");
		long bands = dust.stream().map(entry -> Math.round(horizontalDistance(entry.at(), center) * 10.0)).distinct().count();
		helper.assertTrue(bands >= 3, definition.id() + " circle had no inner ring or spokes: " + bands + " bands");
		int points = dust.size();
		helper.assertTrue(points <= SpellVisuals.MAX_MAGIC_CIRCLE_PARTICLES_PER_CAST,
			definition.id() + " exceeded the bounded circle point budget: " + points);
		for (SpellVisualLog.Played entry : dust) {
			long nearbyPlayers = helper.getLevel().players().stream()
				.filter(viewer -> viewer.position().distanceToSqr(entry.at()) <= SpellVisuals.VIEW_RADIUS_BLOCKS * SpellVisuals.VIEW_RADIUS_BLOCKS)
				.count();
			helper.assertTrue(entry.particles() <= nearbyPlayers,
				definition.id() + " sent a magic-circle particle beyond its viewer radius: " + entry);
		}
		long sent = dust.stream().mapToLong(SpellVisualLog.Played::particles).sum();
		long recipients = helper.getLevel().players().size();
		helper.assertTrue(sent <= SpellVisuals.MAX_MAGIC_CIRCLE_PARTICLES_PER_CAST * recipients,
			definition.id() + " exceeded its total magic-circle packet budget: " + sent);
	}

	private static double horizontalDistance(final Vec3 from, final Vec3 to) {
		return Math.sqrt(from.distanceToSqr(to) - (from.y - to.y) * (from.y - to.y));
	}

	private static int dustRgb(final DustParticleOptions dust) {
		var color = dust.getColor();
		return (Math.round(color.x() * 255.0F) << 16)
			| (Math.round(color.y() * 255.0F) << 8)
			| Math.round(color.z() * 255.0F);
	}

	private static double expectedRadius(final String path) {
		return switch (path) {
			case "static_lash" -> SpellCasting.STATIC_LASH_RADIUS;
			case "chainstorm" -> SpellStorms.RADIUS;
			case "void_well" -> SpellCasting.VOID_WELL_RADIUS;
			case "time_lock" -> SpellCasting.TIME_LOCK_RADIUS;
			case "wardens_roar" -> SpellCasting.WARDENS_ROAR_RADIUS;
			case "frost_nova" -> MoreSpells.FROST_NOVA_RADIUS;
			case "sanctuary" -> MoreSpells.SANCTUARY_RADIUS;
			case "sapping_hex" -> MoreSpells.HEX_RADIUS;
			case "purify" -> MoreSpells.PURIFY_RADIUS;
			case "dragonfall" -> SpellCasting.DRAGONFALL_RADIUS;
			case "earthspike" -> MoreSpells.EARTHSPIKE_RADIUS;
			case "plague_cloud" -> MoreSpells.PLAGUE_RADIUS;
			case "flame_ring" -> MoreSpells.FLAME_RING_RADIUS;
			case "gravity" -> MoreSpells.GRAVITY_RADIUS;
			case "bulwark_hymn", "tailwind", "battle_hymn" -> MoreSpells.SUPPORT_RADIUS;
			default -> throw new AssertionError("unknown area spell " + path);
		};
	}

	private static int expectedElementColor(final String path) {
		return switch (path) {
			case "static_lash" -> 0xF5D35E;
			case "chainstorm" -> 0xF5DC65;
			case "void_well" -> 0x7A4BA5;
			case "wardens_roar" -> 0x46E4C0;
			case "time_lock" -> 0xD16DE6;
			case "dragonfall", "flame_ring" -> 0xFF6A3D;
			case "sanctuary" -> 0x6ABCCB;
			case "sapping_hex" -> 0xA0D84B;
			case "purify" -> 0xF2F2B2;
			case "frost_nova" -> 0x74D7FF;
			case "earthspike" -> 0x9A704A;
			case "plague_cloud" -> 0xA0D84B;
			case "gravity" -> 0x7A4BA5;
			case "bulwark_hymn" -> 0x6ABCCB;
			case "tailwind" -> 0xB8E8FF;
			case "battle_hymn" -> 0xF5DC65;
			default -> throw new AssertionError("unknown area spell " + path);
		};
	}

	private static List<SpellbookItem> books(final TargetMode mode) {
		return new ArrayList<>(Spellbooks.CATALOG.stream().filter(book -> book.definition().targetMode() == mode).toList());
	}

	private static ServerPlayer addPlayer(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(position.x, position.y, position.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		return player;
	}
}
