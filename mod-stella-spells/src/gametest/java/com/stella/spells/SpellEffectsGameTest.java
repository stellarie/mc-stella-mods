package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class SpellEffectsGameTest {
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final Identifier FROSTBIND = Identifier.parse("mod-stella-spells:frostbind");
	private static final Identifier BRIAR_SNARE = Identifier.parse("mod-stella-spells:briar_snare");
	private static final Identifier GALE_STEP = Identifier.parse("mod-stella-spells:gale_step");
	private static final Identifier MENDING_WORD = Identifier.parse("mod-stella-spells:mending_word");
	private static final Identifier STONEGUARD = Identifier.parse("mod-stella-spells:stoneguard");
	private static final Identifier STATIC_LASH = Identifier.parse("mod-stella-spells:static_lash");
	private static final Identifier TIDAL_PUSH = Identifier.parse("mod-stella-spells:tidal_push");
	private static final Identifier OWLS_GAZE = Identifier.parse("mod-stella-spells:owls_gaze");
	private static final Identifier SLIMEBOUND = Identifier.parse("mod-stella-spells:slimebound");
	private static final Identifier WITHER_SEAL = Identifier.parse("mod-stella-spells:wither_seal");
	private static final Identifier PHOENIX_MANTLE = Identifier.parse("mod-stella-spells:phoenix_mantle");
	private static final Identifier LIFE_BLOOM = Identifier.parse("mod-stella-spells:life_bloom");
	private static final Identifier AEGIS_OF_THE_DEEP = Identifier.parse("mod-stella-spells:aegis_of_the_deep");
	private static final Identifier WARDENS_ROAR = Identifier.parse("mod-stella-spells:wardens_roar");
	private static final Identifier CHAINSTORM = Identifier.parse("mod-stella-spells:chainstorm");
	private static final Identifier TIME_LOCK = Identifier.parse("mod-stella-spells:time_lock");
	private static final Identifier VOID_WELL = Identifier.parse("mod-stella-spells:void_well");
	private static final Identifier DRAGONFALL = Identifier.parse("mod-stella-spells:dragonfall");
	private static final Identifier RIFTWALK = Identifier.parse("mod-stella-spells:riftwalk");
	private static final List<CooldownExpectation> EXPECTED_COOLDOWNS = List.of(
		new CooldownExpectation(SPARK_SHOT, 60),
		new CooldownExpectation(FROSTBIND, 60),
		new CooldownExpectation(GALE_STEP, 60),
		new CooldownExpectation(MENDING_WORD, 140),
		new CooldownExpectation(STONEGUARD, 160),
		new CooldownExpectation(STATIC_LASH, 120),
		new CooldownExpectation(BRIAR_SNARE, 120),
		new CooldownExpectation(TIDAL_PUSH, 100),
		new CooldownExpectation(OWLS_GAZE, 200),
		new CooldownExpectation(SLIMEBOUND, 100),
		new CooldownExpectation(CHAINSTORM, 300),
		new CooldownExpectation(RIFTWALK, 200),
		new CooldownExpectation(PHOENIX_MANTLE, 300),
		new CooldownExpectation(LIFE_BLOOM, 340),
		new CooldownExpectation(VOID_WELL, 340),
		new CooldownExpectation(WARDENS_ROAR, 300),
		new CooldownExpectation(AEGIS_OF_THE_DEEP, 400),
		new CooldownExpectation(WITHER_SEAL, 360),
		new CooldownExpectation(TIME_LOCK, 400),
		new CooldownExpectation(DRAGONFALL, 500),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:ice_lance"), 100),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:soul_siphon"), 180),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:venom_dart"), 100),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:sapping_hex"), 240),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:blinding_flash"), 140),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:ember_brand"), 120),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:quicksilver"), 300),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:tide_lungs"), 300),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:berserk"), 400),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:haste_rune"), 300),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:shadow_veil"), 400),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:sanctuary"), 400),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:purify"), 300),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:frost_nova"), 300),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:earthspike"), 240),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:plague_cloud"), 300),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:flame_ring"), 400),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:gravity"), 240),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:bulwark_hymn"), 400),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:tailwind"), 300),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:battle_hymn"), 400),
		new CooldownExpectation(Identifier.parse("mod-stella-spells:summon_skeleton"), SummonSkeleton.COOLDOWN_TICKS)
	);

	@GameTest(maxTicks = 100)
	public void sparkShotHitsTargetAtSixteenBlocks(final GameTestHelper helper) {
		SpellTestLog.track(helper, "sparkShotHitsTargetAtSixteenBlocks");
		helper.setTime(18_000L);
		double range = SpellbookTestAccess.targetRangeBlocks();
		helper.assertTrue(range == 16.0, "production target range changed from 16 blocks: " + range);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		clearTargetRayBarriers(helper);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		Vec3 targetPosition = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5 + range));
		target.setPos(targetPosition.x, targetPosition.y, targetPosition.z);
		float healthBefore = target.getHealth();
		SpellbookItem book = spellbook(helper, SPARK_SHOT, "Spark Shot");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		Findable.then(helper, List.of(target), () -> {
			var castResult = book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			Vec3 rayStart = player.getEyePosition();
			Vec3 rayEnd = rayStart.add(player.getLookAngle().scale(range));
			var blockHit = helper.getLevel().clip(
				new net.minecraft.world.level.ClipContext(
					rayStart, rayEnd, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, player
				)
			);
			var entityHit = target.getBoundingBox().inflate(target.getPickRadius()).clip(rayStart, rayEnd);
			helper.assertTrue(
				target.getHealth() == healthBefore - 3.0f && player.getCooldowns().isOnCooldown(stack),
				"Spark Shot did not hit a hostile centered at the production 16-block boundary: cast=" + castResult
					+ " player=" + player.position() + " eye=" + rayStart + " look=" + player.getLookAngle()
					+ " target=" + target.position() + " targetBox=" + target.getBoundingBox()
					+ " rayEnd=" + rayEnd + " entityHit=" + entityHit
					+ " blockHit=" + blockHit.getType() + "/" + blockHit.getLocation()
					+ " blockPos=" + blockHit.getBlockPos()
					+ " blockState=" + helper.getLevel().getBlockState(blockHit.getBlockPos())
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void sparkShotBeyondSixteenBlocksHasNoEffectOrCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "sparkShotBeyondSixteenBlocksHasNoEffectOrCooldown");
		helper.setTime(18_000L);
		double range = SpellbookTestAccess.targetRangeBlocks();
		helper.assertTrue(range == 16.0, "production target range changed from 16 blocks: " + range);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		clearTargetRayBarriers(helper);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		Vec3 targetPosition = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5 + range + 0.5));
		target.setPos(targetPosition.x, targetPosition.y, targetPosition.z);
		float healthBefore = target.getHealth();
		SpellbookItem book = spellbook(helper, SPARK_SHOT, "Spark Shot");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		Findable.then(helper, List.of(target), () -> {
			book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(target.getHealth() == healthBefore, "Spark Shot hit a hostile beyond 16 blocks");
			helper.assertFalse(player.getCooldowns().isOnCooldown(stack), "an out-of-range cast started a cooldown");
			helper.succeed();
		});
	}

	@GameTest
	public void sparkShotHitsNearestHostileOnClearRay(final GameTestHelper helper) {
		SpellTestLog.track(helper, "sparkShotHitsNearestHostileOnClearRay");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		clearTargetRayBarriers(helper);
		Mob nearer = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		Mob farther = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 10).withNoFreeWill().spawn();
		float nearHealth = nearer.getHealth();
		float farHealth = farther.getHealth();
		SpellbookItem book = spellbook(helper, SPARK_SHOT, "Spark Shot");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(
			nearer.getHealth() == nearHealth - 3.0f && farther.getHealth() == farHealth
				&& player.getCooldowns().isOnCooldown(stack),
			"Spark Shot did not select the nearest hostile on the clear ray"
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void frostbindAppliesSlownessTwoWithoutDamage(final GameTestHelper helper) {
		SpellTestLog.track(helper, "frostbindAppliesSlownessTwoWithoutDamage");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		float healthBefore = target.getHealth();
		SpellbookItem book = frostbind(helper);
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance effect = target.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(
			effect != null && effect.getAmplifier() == 1 && effect.getDuration() >= 80,
			"Frostbind did not apply Slowness II for four seconds"
		);
		helper.assertTrue(target.getHealth() == healthBefore, "Frostbind damaged its target");
		helper.assertTrue(player.getCooldowns().isOnCooldown(stack), "Frostbind did not start its cooldown");
		helper.succeed();
	}

	@GameTest
	public void frostbindDoesNotAffectBlockedTargetOrStartCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "frostbindDoesNotAffectBlockedTargetOrStartCooldown");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		helper.setBlock(2, 2, 4, Blocks.STONE);
		SpellbookItem book = frostbind(helper);
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(target.getEffect(MobEffects.SLOWNESS) == null, "Frostbind affected a target behind a block");
		helper.assertFalse(player.getCooldowns().isOnCooldown(stack), "a blocked Frostbind cast started a cooldown");
		helper.succeed();
	}

	@GameTest
	public void distinctSpellbooksCastBeforeEitherCooldownExpiresAndMatchTheCatalog(final GameTestHelper helper) {
		SpellTestLog.track(helper, "distinctSpellbooksCastBeforeEitherCooldownExpiresAndMatchTheCatalog");
		List<String> mismatches = new ArrayList<>();
		// The pin list freezes the cooldown of every book it names, so a rebalance is deliberate. It is not a
		// copy of the catalogue: a book added later is covered by the floor below until someone pins it.
		for (CooldownExpectation expected : EXPECTED_COOLDOWNS) {
			var definition = SpellbookCatalog.DEFINITIONS.stream()
				.filter(candidate -> candidate.id().equals(expected.id()))
				.findFirst();
			if (definition.isEmpty() || definition.orElseThrow().baseCooldownTicks() != expected.ticks()) {
				mismatches.add(
					"cooldown mismatch for " + expected.id() + ": expected=" + expected.ticks()
						+ " actual=" + definition.map(SpellDefinition::baseCooldownTicks).orElse(-1)
				);
			}
		}
		// Every book the catalogue ships, pinned or not, keeps the twenty-tick floor a cast can rest on.
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			if (definition.baseCooldownTicks() < 20) {
				mismatches.add("cooldown below the floor for " + definition.id() + ": " + definition.baseCooldownTicks());
			}
		}
		helper.assertTrue(mismatches.isEmpty(), String.join("; ", mismatches));

		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		SpellbookItem sparkBook = spellbook(helper, SPARK_SHOT, "Spark Shot");
		ItemStack sparkStack = sparkBook.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, sparkStack);
		sparkBook.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

		SpellbookItem witherBook = spellbook(helper, WITHER_SEAL, "Wither Seal");
		ItemStack witherStack = witherBook.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, witherStack);
		witherBook.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

		int sparkTicks = SpellbookCatalog.get(SPARK_SHOT).baseCooldownTicks();
		int witherTicks = SpellbookCatalog.get(WITHER_SEAL).baseCooldownTicks();
		MobEffectInstance wither = target.getEffect(MobEffects.WITHER);
		helper.assertTrue(
			target.getHealth() == 17.0f && wither != null && wither.getAmplifier() == 1
				&& player.getCooldowns().isOnCooldown(sparkStack)
				&& player.getCooldowns().isOnCooldown(witherStack)
				&& sparkStack.getCount() == 1 && witherStack.getCount() == 1,
			"the second spell did not cast while preserving separate book cooldowns"
		);

		for (int tick = 1; tick < sparkTicks; tick++) {
			player.doTick();
		}
		helper.assertTrue(
			player.getCooldowns().isOnCooldown(sparkStack) && player.getCooldowns().isOnCooldown(witherStack),
			"a cooldown expired before its production duration"
		);
		player.doTick();
		helper.assertFalse(player.getCooldowns().isOnCooldown(sparkStack), "Spark Shot exceeded its production cooldown");
		helper.assertTrue(player.getCooldowns().isOnCooldown(witherStack), "Wither Seal expired with Spark Shot");
		for (int tick = sparkTicks; tick < witherTicks - 1; tick++) {
			player.doTick();
		}
		helper.assertTrue(player.getCooldowns().isOnCooldown(witherStack), "Wither Seal expired before its production duration");
		player.doTick();
		helper.assertFalse(player.getCooldowns().isOnCooldown(witherStack), "Wither Seal exceeded its production cooldown");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void briarSnareAppliesSlownessFourWithoutDamage(final GameTestHelper helper) {
		SpellTestLog.track(helper, "briarSnareAppliesSlownessFourWithoutDamage");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		float healthBefore = target.getHealth();
		SpellbookItem book = spellbook(helper, BRIAR_SNARE, "Briar Snare");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance effect = target.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(
			effect != null && effect.getAmplifier() == 3 && effect.getDuration() >= 80,
			"Briar Snare did not apply Slowness IV for four seconds"
		);
		helper.assertTrue(target.getHealth() == healthBefore, "Briar Snare damaged its target");
		helper.assertTrue(player.getCooldowns().isOnCooldown(stack), "Briar Snare did not start its cooldown");
		helper.succeed();
	}

	@GameTest
	public void briarSnareDoesNotAffectBlockedTargetOrStartCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "briarSnareDoesNotAffectBlockedTargetOrStartCooldown");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		helper.setBlock(2, 2, 4, Blocks.STONE);
		SpellbookItem book = spellbook(helper, BRIAR_SNARE, "Briar Snare");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(target.getEffect(MobEffects.SLOWNESS) == null, "Briar Snare affected a target behind a block");
		helper.assertFalse(player.getCooldowns().isOnCooldown(stack), "a blocked Briar Snare cast started a cooldown");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void galeStepMovesCasterAndGrantsSlowFallingWithoutTarget(final GameTestHelper helper) {
		SpellTestLog.track(helper, "galeStepMovesCasterAndGrantsSlowFallingWithoutTarget");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		SpellbookItem book = galeStep(helper);
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		Vec3 look = player.getLookAngle();
		Vec3 velocity = player.getDeltaMovement();
		MobEffectInstance slowFalling = player.getEffect(MobEffects.SLOW_FALLING);
		double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		helper.assertTrue(
			Math.abs(horizontalSpeed - 1.2) < 0.001
				&& Math.abs(velocity.y - 0.45) < 0.001
				&& velocity.x * look.x + velocity.z * look.z > 1.19
				&& slowFalling != null && slowFalling.getAmplifier() == 0 && slowFalling.getDuration() >= 40,
			"Gale Step did not move forward and grant two seconds of Slow Falling: velocity=" + velocity
				+ " slowFalling=" + slowFalling
		);
		helper.succeed();
	}

	@GameTest
	public void mendingWordRestoresFourHealthWithoutTarget(final GameTestHelper helper) {
		SpellTestLog.track(helper, "mendingWordRestoresFourHealthWithoutTarget");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		float healthBefore = player.getHealth();
		player.setHealth(healthBefore - 6.0f);
		SpellbookItem book = mendingWord(helper);
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.getHealth() == healthBefore - 2.0f, "Mending Word did not restore four health");
		helper.assertTrue(player.getCooldowns().isOnCooldown(stack), "Mending Word did not start its cooldown");
		helper.succeed();
	}

	@GameTest
	public void stoneguardGrantsResistanceOneWithoutTarget(final GameTestHelper helper) {
		SpellTestLog.track(helper, "stoneguardGrantsResistanceOneWithoutTarget");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		SpellbookItem book = stoneguard(helper);
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
		helper.assertTrue(
			resistance != null && resistance.getAmplifier() == 0 && resistance.getDuration() >= 80,
			"Stoneguard did not grant Resistance I for four seconds"
		);
		helper.assertTrue(player.getCooldowns().isOnCooldown(stack), "Stoneguard did not start its cooldown");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void staticLashHitsPrimaryAndTwoNearestHostilesByEntityId(final GameTestHelper helper) {
		SpellTestLog.track(helper, "staticLashHitsPrimaryAndTwoNearestHostilesByEntityId");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob primary = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		Mob firstTie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 7).withNoFreeWill().spawn();
		Mob secondTie = helper.spawnMob(EntityTypes.ZOMBIE, 3, 1, 7).withNoFreeWill().spawn();
		Mob fartherTie = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 8).withNoFreeWill().spawn();
		List<Mob> candidates = List.of(firstTie, secondTie, fartherTie);
		List<Mob> expectedExtras = new ArrayList<>(candidates);
		expectedExtras.sort(
			Comparator.comparingDouble((Mob mob) -> mob.position().distanceToSqr(primary.position()))
				.thenComparingInt(Mob::getId)
		);
		List<Mob> selectedExtras = expectedExtras.subList(0, 2);
		SpellbookItem book = spellbook(helper, STATIC_LASH, "Static Lash");
		player.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		boolean correctTargets = primary.getHealth() == 17.0f && candidates.stream().allMatch(
			mob -> mob.getHealth() == (selectedExtras.contains(mob) ? 17.0f : 20.0f)
		);
		helper.assertTrue(
			correctTargets,
			"Static Lash did not damage the primary and the two nearest extras by entity ID"
		);
		helper.succeed();
	}

	@GameTest
	public void staticLashDoesNotAffectBlockedTargetOrStartCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "staticLashDoesNotAffectBlockedTargetOrStartCooldown");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		float healthBefore = target.getHealth();
		helper.setBlock(2, 2, 4, Blocks.STONE);
		SpellbookItem book = spellbook(helper, STATIC_LASH, "Static Lash");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(target.getHealth() == healthBefore, "Static Lash affected a target behind a block");
		helper.assertFalse(player.getCooldowns().isOnCooldown(stack), "a blocked Static Lash cast started a cooldown");
		helper.succeed();
	}

	@GameTest
	public void tidalPushMovesAimedHostileAwayFromCaster(final GameTestHelper helper) {
		SpellTestLog.track(helper, "tidalPushMovesAimedHostileAwayFromCaster");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		float healthBefore = target.getHealth();
		Vec3 away = target.position().subtract(player.position());
		Vec3 horizontalAway = new Vec3(away.x, 0.0, away.z).normalize();
		SpellbookItem book = spellbook(helper, TIDAL_PUSH, "Tidal Push");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		Vec3 velocity = target.getDeltaMovement();
		double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		helper.assertTrue(
			Math.abs(horizontalSpeed - 1.2) < 0.001
				&& Math.abs(velocity.y - 0.2) < 0.001
				&& velocity.x * horizontalAway.x + velocity.z * horizontalAway.z > 1.19
				&& target.getHealth() == healthBefore
				&& player.getCooldowns().isOnCooldown(stack),
			"Tidal Push did not push the target away without damage: velocity=" + velocity
		);
		helper.succeed();
	}

	@GameTest
	public void tidalPushDoesNotMoveBlockedTargetOrStartCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "tidalPushDoesNotMoveBlockedTargetOrStartCooldown");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		helper.setBlock(2, 2, 4, Blocks.STONE);
		SpellbookItem book = spellbook(helper, TIDAL_PUSH, "Tidal Push");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Tidal Push moved a target behind a block");
		helper.assertFalse(player.getCooldowns().isOnCooldown(stack), "a blocked Tidal Push cast started a cooldown");
		helper.succeed();
	}

	@GameTest
	public void owlsGazeGrantsNightVisionWithoutTarget(final GameTestHelper helper) {
		SpellTestLog.track(helper, "owlsGazeGrantsNightVisionWithoutTarget");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		SpellbookItem book = spellbook(helper, OWLS_GAZE, "Owl's Gaze");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
		helper.assertTrue(
			nightVision != null && nightVision.getAmplifier() == 0 && nightVision.getDuration() >= 900,
			"Owl's Gaze did not grant Night Vision for 45 seconds"
		);
		helper.assertTrue(player.getCooldowns().isOnCooldown(stack), "Owl's Gaze did not start its cooldown");
		helper.succeed();
	}

	@GameTest
	public void slimeboundGrantsJumpBoostAndSlowFallingWithoutTarget(final GameTestHelper helper) {
		SpellTestLog.track(helper, "slimeboundGrantsJumpBoostAndSlowFallingWithoutTarget");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		SpellbookItem book = spellbook(helper, SLIMEBOUND, "Slimebound");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP_BOOST);
		MobEffectInstance slowFalling = player.getEffect(MobEffects.SLOW_FALLING);
		helper.assertTrue(
			jumpBoost != null && jumpBoost.getAmplifier() == 1 && jumpBoost.getDuration() >= 160
				&& slowFalling != null && slowFalling.getAmplifier() == 0 && slowFalling.getDuration() >= 160,
			"Slimebound did not grant Jump Boost II and Slow Falling for eight seconds"
		);
		helper.assertTrue(player.getCooldowns().isOnCooldown(stack), "Slimebound did not start its cooldown");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void witherSealAppliesWitherTwoForFiveSeconds(final GameTestHelper helper) {
		SpellTestLog.track(helper, "witherSealAppliesWitherTwoForFiveSeconds");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		SpellbookItem book = spellbook(helper, WITHER_SEAL, "Wither Seal");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance wither = target.getEffect(MobEffects.WITHER);
		helper.assertTrue(
			wither != null && wither.getAmplifier() == 1 && wither.getDuration() >= 100,
			"Wither Seal did not apply Wither II for five seconds"
		);
		helper.assertTrue(player.getCooldowns().isOnCooldown(stack), "Wither Seal did not start its cooldown");
		helper.succeed();
	}

	@GameTest
	public void witherSealDoesNotAffectBlockedTargetOrStartCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "witherSealDoesNotAffectBlockedTargetOrStartCooldown");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		helper.setBlock(2, 2, 4, Blocks.STONE);
		SpellbookItem book = spellbook(helper, WITHER_SEAL, "Wither Seal");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(target.getEffect(MobEffects.WITHER) == null, "Wither Seal affected a target behind a block");
		helper.assertFalse(player.getCooldowns().isOnCooldown(stack), "a blocked Wither Seal cast started a cooldown");
		helper.succeed();
	}

	@GameTest
	public void phoenixMantleGrantsFireResistanceAndRestoresFourHealth(final GameTestHelper helper) {
		SpellTestLog.track(helper, "phoenixMantleGrantsFireResistanceAndRestoresFourHealth");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		player.setHealth(12.0f);
		SpellbookItem book = spellbook(helper, PHOENIX_MANTLE, "Phoenix Mantle");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance fireResistance = player.getEffect(MobEffects.FIRE_RESISTANCE);
		helper.assertTrue(
			fireResistance != null && fireResistance.getAmplifier() == 0 && fireResistance.getDuration() >= 600
				&& player.getHealth() == 16.0f && player.getCooldowns().isOnCooldown(stack),
			"Phoenix Mantle did not grant Fire Resistance for 30 seconds and restore four health"
		);
		helper.succeed();
	}

	@GameTest
	public void lifeBloomRestoresEightHealthAndGrantsRegenerationOne(final GameTestHelper helper) {
		SpellTestLog.track(helper, "lifeBloomRestoresEightHealthAndGrantsRegenerationOne");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		player.setHealth(8.0f);
		SpellbookItem book = spellbook(helper, LIFE_BLOOM, "Life Bloom");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(
			regeneration != null && regeneration.getAmplifier() == 0 && regeneration.getDuration() >= 100
				&& player.getHealth() == 16.0f && player.getCooldowns().isOnCooldown(stack),
			"Life Bloom did not restore eight health and grant Regeneration I for five seconds"
		);
		helper.succeed();
	}

	@GameTest
	public void aegisOfTheDeepGrantsEightAbsorptionAndResistanceOne(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aegisOfTheDeepGrantsEightAbsorptionAndResistanceOne");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		SpellbookItem book = spellbook(helper, AEGIS_OF_THE_DEEP, "Aegis of the Deep");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance absorption = player.getEffect(MobEffects.ABSORPTION);
		MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
		helper.assertTrue(
			absorption != null && absorption.getAmplifier() == 1 && absorption.getDuration() >= 80
				&& resistance != null && resistance.getAmplifier() == 0 && resistance.getDuration() >= 80
				&& player.getAbsorptionAmount() == 8.0f && player.getCooldowns().isOnCooldown(stack),
			"Aegis of the Deep did not grant eight Absorption health and Resistance I for four seconds"
		);
		helper.succeed();
	}

	@GameTest
	public void wardensRoarDamagesAndKnocksBackHostilesWithinFiveBlocks(final GameTestHelper helper) {
		SpellTestLog.track(helper, "wardensRoarDamagesAndKnocksBackHostilesWithinFiveBlocks");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob nearby = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 5).withNoFreeWill().spawn();
		Mob distant = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 10).withNoFreeWill().spawn();
		Vec3 away = nearby.position().subtract(player.position());
		Vec3 horizontalAway = new Vec3(away.x, 0.0, away.z).normalize();
		SpellbookItem book = spellbook(helper, WARDENS_ROAR, "Warden's Roar");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		Vec3 velocity = nearby.getDeltaMovement();
		double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		helper.assertTrue(
			nearby.getHealth() == 14.0f && distant.getHealth() == 20.0f
				&& Math.abs(horizontalSpeed - 1.0) < 0.001 && Math.abs(velocity.y - 0.4) < 0.001
				&& velocity.x * horizontalAway.x + velocity.z * horizontalAway.z > 0.99
				&& player.getCooldowns().isOnCooldown(stack),
			"Warden's Roar did not damage and repel only hostiles within five blocks"
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void timeLockSlowsHostilesWithinFiveBlocksOfAimedPoint(final GameTestHelper helper) {
		SpellTestLog.track(helper, "timeLockSlowsHostilesWithinFiveBlocksOfAimedPoint");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob nearby = helper.spawnMob(EntityTypes.ZOMBIE, 5, 1, 5).withNoFreeWill().spawn();
		Mob distant = helper.spawnMob(EntityTypes.ZOMBIE, 10, 1, 5).withNoFreeWill().spawn();
		helper.setBlock(2, 2, 5, Blocks.STONE);
		SpellbookItem book = spellbook(helper, TIME_LOCK, "Time Lock");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance nearbySlowness = nearby.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(
			nearbySlowness != null && nearbySlowness.getAmplifier() == 1 && nearbySlowness.getDuration() >= 100
				&& distant.getEffect(MobEffects.SLOWNESS) == null && player.getCooldowns().isOnCooldown(stack),
			"Time Lock did not apply Slowness II only within five blocks of the aimed point"
		);
		helper.succeed();
	}

	@GameTest
	public void timeLockWithoutBlockHitDoesNotApplyEffectsOrStartCooldown(final GameTestHelper helper) {
		SpellTestLog.track(helper, "timeLockWithoutBlockHitDoesNotApplyEffectsOrStartCooldown");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		player.setXRot(-90.0f);
		for (int y = 2; y <= 20; y++) {
			helper.setBlock(2, y, 2, Blocks.AIR);
		}
		Mob nearby = helper.spawnMob(EntityTypes.ZOMBIE, 5, 1, 5).withNoFreeWill().spawn();
		SpellbookItem book = spellbook(helper, TIME_LOCK, "Time Lock");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(nearby.getEffect(MobEffects.SLOWNESS) == null, "Time Lock affected hostiles without a block hit");
		helper.assertFalse(player.getCooldowns().isOnCooldown(stack), "a Time Lock cast without a block hit started cooldown");
		helper.succeed();
	}

	@GameTest
	public void voidWellPullsHostilesTowardAimedPointAndSlowsThem(final GameTestHelper helper) {
		SpellTestLog.track(helper, "voidWellPullsHostilesTowardAimedPointAndSlowsThem");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob nearby = helper.spawnMob(EntityTypes.ZOMBIE, 5, 1, 5).withNoFreeWill().spawn();
		Mob distant = helper.spawnMob(EntityTypes.ZOMBIE, 10, 1, 5).withNoFreeWill().spawn();
		float healthBefore = nearby.getHealth();
		helper.setBlock(2, 2, 5, Blocks.STONE);
		SpellbookItem book = spellbook(helper, VOID_WELL, "Void Well");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		Vec3 velocity = nearby.getDeltaMovement();
		double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		MobEffectInstance slowness = nearby.getEffect(MobEffects.SLOWNESS);
		Vec3 aimedPoint = helper.absoluteVec(new Vec3(2.5, 0.0, 5.0));
		Vec3 towardPoint = new Vec3(
			aimedPoint.x - nearby.position().x, 0.0, aimedPoint.z - nearby.position().z
		).normalize();
		helper.assertTrue(
			Math.abs(horizontalSpeed - 0.8) < 0.001 && Math.abs(velocity.y - 0.1) < 0.001
				&& velocity.x * towardPoint.x + velocity.z * towardPoint.z > 0.79
				&& slowness != null && slowness.getAmplifier() == 1 && slowness.getDuration() >= 60
				&& nearby.getHealth() == healthBefore && distant.getDeltaMovement().equals(Vec3.ZERO)
				&& distant.getEffect(MobEffects.SLOWNESS) == null && player.getCooldowns().isOnCooldown(stack),
			"Void Well did not pull and slow hostiles within five blocks of the aimed point: velocity=" + velocity
				+ " position=" + nearby.position() + " slowness=" + slowness
				+ " distantVelocity=" + distant.getDeltaMovement()
				+ " distantSlowness=" + distant.getEffect(MobEffects.SLOWNESS)
				+ " healthBefore=" + healthBefore + " healthAfter=" + nearby.getHealth()
				+ " cooldown=" + player.getCooldowns().isOnCooldown(stack)
		);
		helper.succeed();
	}

	@GameTest
	public void dragonfallDealsMagicDamageWithinThreeBlocksOfAimedPoint(final GameTestHelper helper) {
		SpellTestLog.track(helper, "dragonfallDealsMagicDamageWithinThreeBlocksOfAimedPoint");
		helper.setTime(18_000L);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		Mob nearby = helper.spawnMob(EntityTypes.ZOMBIE, 4, 1, 5).withNoFreeWill().spawn();
		Mob distant = helper.spawnMob(EntityTypes.ZOMBIE, 8, 1, 5).withNoFreeWill().spawn();
		helper.setBlock(2, 2, 5, Blocks.STONE);
		SpellbookItem book = spellbook(helper, DRAGONFALL, "Dragonfall");
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		// The hit lands with the fire charge; the case lands the charge now.
		DragonCharge.landNow(player);
		helper.assertTrue(
			nearby.getHealth() == 10.0f && distant.getHealth() == 20.0f
				&& player.getCooldowns().isOnCooldown(stack),
			"Dragonfall did not deal ten magic damage only within three blocks of the aimed point"
		);
		helper.succeed();
	}

	private static SpellbookItem spellbook(
		final GameTestHelper helper, final Identifier id, final String name
	) {
		Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item instanceof SpellbookItem, name + " item is not registered");
		return (SpellbookItem) item;
	}

	private static void clearTargetRayBarriers(final GameTestHelper helper) {
		helper.setBlock(2, 2, 8, Blocks.AIR);
		helper.setBlock(2, 2, 16, Blocks.AIR);
	}

	private static SpellbookItem stoneguard(final GameTestHelper helper) {
		Item item = BuiltInRegistries.ITEM.get(STONEGUARD).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item instanceof SpellbookItem, "Stoneguard item is not registered");
		return (SpellbookItem) item;
	}

	private static SpellbookItem mendingWord(final GameTestHelper helper) {
		Item item = BuiltInRegistries.ITEM.get(MENDING_WORD).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item instanceof SpellbookItem, "Mending Word item is not registered");
		return (SpellbookItem) item;
	}

	private static SpellbookItem galeStep(final GameTestHelper helper) {
		Item item = BuiltInRegistries.ITEM.get(GALE_STEP).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item instanceof SpellbookItem, "Gale Step item is not registered");
		return (SpellbookItem) item;
	}

	private static SpellbookItem frostbind(final GameTestHelper helper) {
		Item item = BuiltInRegistries.ITEM.get(FROSTBIND).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item instanceof SpellbookItem, "Frostbind item is not registered");
		return (SpellbookItem) item;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer addPlayer(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.setPos(position.x, position.y, position.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		return player;
	}

	private record CooldownExpectation(Identifier id, int ticks) {
	}
}
