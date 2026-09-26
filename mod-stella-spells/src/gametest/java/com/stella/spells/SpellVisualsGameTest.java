package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
		List<SpellbookItem> books = books(TargetMode.SELF);
		// Gale Step launches the caster, so it goes last.
		books.sort(Comparator.comparing((SpellbookItem book) -> book.definition().id().getPath().equals("gale_step")));
		for (SpellbookItem book : books) {
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
		for (int z = 3; z <= 5; z++) {
			helper.setBlock(2, 0, z, Blocks.STONE);
		}
		helper.setBlock(2, 1, 3, Blocks.STONE);
		helper.setBlock(2, 2, 3, Blocks.STONE);
		List<SpellbookItem> books = books(TargetMode.SAFE_TELEPORT);
		for (SpellbookItem book : books) {
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
		LOG.info("[SPELLTEST] visual spell={} particle={} at={} shapes={}", book.definition().id(), BuiltInRegistries.PARTICLE_TYPE.getKey(visual.primary().getType()), where, played.size());
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
