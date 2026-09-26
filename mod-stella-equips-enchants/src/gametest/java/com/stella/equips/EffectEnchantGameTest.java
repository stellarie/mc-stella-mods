package com.stella.equips;

import com.stella.equips.power.PowerConfigStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

public class EffectEnchantGameTest {
	private static final String MOD_ID = StellaEquips.MOD_ID;
	private static final float SPAWN_Y = 4.0F;
	private static final int NEGATION_SAMPLES = 400;
	private static final int YIELD_BREAKS = 60;
	private static final int FUNCTION_WAIT_TICKS = 60;
	private static final int SHOT_WAIT_TICKS = 25;
	/** Cloudstep grants Jump Boost 3, which is 0.3 of jump power, about two blocks. */
	private static final int CLOUDSTEP_JUMP_AMPLIFIER = 2;
	private static final double CLOUDSTEP_JUMP_POWER = 0.1 * (CLOUDSTEP_JUMP_AMPLIFIER + 1);
	private static final int CLOUDSTEP_EFFECT_TICKS = 40;
	private static final int CLOUDSTEP_GATE_TICKS = 20;
	private static final double GRAVITY = 0.08;
	private static final double VANILLA_JUMP_POWER = 0.42;
	private static final double CLOUDSTEP_EXPECTED_EXTRA =
		(VANILLA_JUMP_POWER + CLOUDSTEP_JUMP_POWER) * (VANILLA_JUMP_POWER + CLOUDSTEP_JUMP_POWER) / (2.0 * GRAVITY)
			- VANILLA_JUMP_POWER * VANILLA_JUMP_POWER / (2.0 * GRAVITY);
	private static final double CLOUDSTEP_MINIMUM_EXTRA_HEIGHT = 1.5;
	private static final int JUMP_SAMPLE_TICKS = 40;
	/** Veinminer breaks the mined block and one neighbour each way along the plane axis. */
	private static final int VEINMINER_BLOCKS_PER_SWING = 3;

	private static final List<String> NEW_PATHS = List.of(
		"weapon/chill", "weapon/frostbite", "weapon/pyre", "weapon/venom", "weapon/ashfall", "weapon/weightless",
		"weapon/void_brand", "weapon/meteor", "weapon/impale", "weapon/undertow",
		"armor/ender_ward", "armor/ember_guard", "armor/rime_guard", "armor/venom_guard", "armor/dread_aura",
		"armor/cloudstep", "armor/deflection",
		"crossbow/railgun",
		"tool/ore_mender", "tool/veinminer", "tool/enchanted_yield"
	);
	private static final Set<String> CUT_PATHS = Set.of(
		"weapon/cleave", "weapon/returning", "armor/featherfall", "armor/asbestos", "armor/warded", "bow/bodkin",
		"tool/anglers_fortune", "crossbow/quickdraw", "armor/deepdiver", "weapon/arachnids_bane", "bow/rangers_mark",
		"weapon/slam"
	);

	@GameTest
	public void catalogueHoldsFiftyThreeEnchantmentsAndTheCutIdsAreAbsent(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		List<String> catalogue = new ArrayList<>();
		for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
			Identifier id = holder.key().identifier();
			if (MOD_ID.equals(id.getNamespace()) && !"test_marker".equals(id.getPath())) {
				catalogue.add(id.getPath());
			}
		}

		List<String> present = new ArrayList<>();
		for (String cut : CUT_PATHS) {
			if (catalogue.contains(cut)) {
				present.add(cut);
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=catalogueCount RESULT catalogue={} cutPresent={} expected=72",
			catalogue.size(), present.size()
		);
		helper.assertTrue(
			catalogue.size() == 72,
			"the catalogue holds " + catalogue.size() + " enchantments instead of 72"
		);
		helper.assertTrue(present.isEmpty(), "a cut enchantment is still registered: " + present);
		helper.succeed();
	}

	@GameTest
	public void everyNewEnchantmentReachesTheTableAndTheLootPool(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Set<Identifier> table = tagValues(helper, "in_enchanting_table");
		Set<Identifier> loot = tagValues(helper, "on_random_loot");
		List<String> missing = new ArrayList<>();
		int checked = 0;
		for (String path : NEW_PATHS) {
			Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, path);
			helper.assertTrue(
				registry.containsKey(id),
				"the new enchantment " + id + " is not in the registry, so no tag can hold it"
			);
			if (!table.contains(id) || !loot.contains(id)) {
				missing.add(path + "(table=" + table.contains(id) + ",loot=" + loot.contains(id) + ")");
			}

			checked++;
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=newReach RESULT checked={} tableValues={} lootValues={} missing={}",
			checked, table.size(), loot.size(), missing.size()
		);
		helper.assertTrue(missing.isEmpty(), "these new enchantments reach nothing: " + missing);
		helper.succeed();
	}

	@GameTest
	public void chillSlowsTheVictimAndDeepensWithLevel(final GameTestHelper helper) {
		int atOne = slownessAmplifierAfterHit(helper, 1);
		int atFive = slownessAmplifierAfterHit(helper, 5);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=chill RESULT amplifierAtLevel1={} amplifierAtLevel5={}", atOne, atFive
		);
		helper.assertTrue(atOne >= 0, "chill at level 1 applied no slowness");
		helper.assertTrue(atFive > atOne, "slowness did not deepen: " + atOne + " then " + atFive);
		helper.succeed();
	}

	@GameTest
	public void frostbiteFreezesTheVictimThroughDamage(final GameTestHelper helper) {
		float lost = healthLostToHit(helper, "weapon/frostbite", 5);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=frostbite RESULT victimLost={}", lost);
		helper.assertTrue(lost > 0.0F, "the frozen victim lost no health");
		helper.succeed();
	}

	@GameTest
	public void pyreSetsTheVictimAlight(final GameTestHelper helper) {
		Mob victim = victim(helper);
		hit(helper, "weapon/pyre", 1, victim);
		boolean burning = victim.isOnFire();
		int fireTicks = victim.getRemainingFireTicks();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=pyre RESULT onFire={} fireTicks={}", burning, fireTicks
		);
		helper.assertTrue(burning, "the victim of Pyre is not alight, fire ticks " + fireTicks);
		helper.succeed();
	}

	@GameTest
	public void venomPoisonsTheVictimAndLastsLongerWithLevel(final GameTestHelper helper) {
		int atOne = poisonDurationAfterHit(helper, 1);
		int atFive = poisonDurationAfterHit(helper, 5);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=venom RESULT poisonTicksAtLevel1={} poisonTicksAtLevel5={}", atOne, atFive
		);
		helper.assertTrue(atOne > 0, "venom at level 1 applied no poison");
		helper.assertTrue(atFive > atOne, "poison did not last longer: " + atOne + " then " + atFive);
		helper.succeed();
	}

	@GameTest
	public void ashfallBurnsAndFreezesInOneHit(final GameTestHelper helper) {
		Mob victim = victim(helper);
		float before = victim.getHealth();
		hit(helper, "weapon/ashfall", 5, victim);
		boolean burning = victim.isOnFire();
		float lost = before - victim.getHealth();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=ashfall RESULT onFire={} victimLost={}", burning, lost
		);
		helper.assertTrue(burning, "Ashfall left the victim unburnt");
		helper.assertTrue(lost > 0.0F, "Ashfall landed no freeze damage on the same victim");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void weightlessLiftsTheVictim(final GameTestHelper helper) {
		Mob victim = victim(helper);
		hit(helper, "weapon/weightless", 5, victim);
		boolean levitating = victim.hasEffect(MobEffects.LEVITATION);
		float startY = (float) victim.getY();
		helper.runAfterDelay(20, () -> {
			float lifted = (float) (victim.getY() - startY);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=weightless RESULT levitating={} lifted={}", levitating, lifted
			);
			helper.assertTrue(levitating, "Weightless applied no levitation");
			helper.assertTrue(lifted > 0.0F, "the victim did not rise: " + lifted);
			helper.succeed();
		});
	}

	@GameTest
	public void deflectionNegatesMoreHitsAtLevelFiveThanAtLevelOne(final GameTestHelper helper) {
		int atOne = negatedHits(helper, 1);
		int atFive = negatedHits(helper, 5);
		double rateOne = (double) atOne / NEGATION_SAMPLES;
		double rateFive = (double) atFive / NEGATION_SAMPLES;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=deflection RESULT samples={} negatedAtLevel1={} negatedAtLevel5={} rateOne={} rateFive={}",
			NEGATION_SAMPLES, atOne, atFive, rateOne, rateFive
		);
		helper.assertTrue(atOne > 0, "Deflection negated nothing at level 1");
		helper.assertTrue(
			atFive > atOne,
			"level 5 negated " + atFive + " hits and level 1 negated " + atOne + ", so the chance is not level driven"
		);
		helper.succeed();
	}

	@GameTest
	public void enderWardLeavesTheWearerUnharmedByAPearl(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer bare = mockPlayer(helper, GameType.SURVIVAL);
		ServerPlayer warded = mockPlayer(helper, GameType.SURVIVAL);
		warded.setItemSlot(
			EquipmentSlot.CHEST, enchanted(helper, Items.DIAMOND_CHESTPLATE, "armor/ender_ward", 1)
		);
		float bareLost = damage(level, bare, 5.0F);
		float wardedLost = damage(level, warded, 5.0F);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=enderWard RESULT bareLost={} wardedLost={}", bareLost, wardedLost
		);
		helper.assertTrue(bareLost > 0.0F, "the bare wearer took no pearl damage, so the test proves nothing");
		helper.assertTrue(wardedLost == 0.0F, "the warded wearer lost " + wardedLost + " health to a pearl");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void pyreShowsTheHitOnTheAttackersClient(final GameTestHelper helper) {
		ServerPlayer attacker = mockPlayerInLevel(helper);
		Mob victim = victim(helper);
		attacker.setPos(victim.getX(), victim.getY(), victim.getZ() + 1.0);
		attacker.setItemInHand(
			InteractionHand.MAIN_HAND, enchanted(helper, Items.DIAMOND_SWORD, "weapon/pyre", 1)
		);
		PacketRecorder recorder = PacketRecorder.of(attacker);
		attacker.attack(victim);
		int flames = particlesOn(recorder, ParticleTypes.FLAME);
		int sounds = soundsOn(recorder);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=pyreVisual RESULT flamePackets={} soundPackets={} victimBurning={}",
			flames, sounds, victim.isOnFire()
		);
		helper.assertTrue(victim.isOnFire(), "Pyre lit nothing, so the flash would prove nothing");
		helper.assertTrue(flames > 0, "the Pyre hit showed the attacker no flame particle");
		helper.assertTrue(sounds > 0, "the Pyre hit played the attacker no sound");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void meteorBlastReachesAClientAsAnEmitter(final GameTestHelper helper) {
		ServerPlayer smasher = mockPlayerInLevel(helper);
		ServerPlayer watcher = mockPlayerInLevel(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(1, (int) SPAWN_Y, 1));
		smasher.setPos(origin.getX() + 0.5, origin.getY() + 8.0, origin.getZ() + 0.5);
		watcher.setPos(origin.getX() + 2.5, origin.getY(), origin.getZ() + 0.5);
		PacketRecorder recorder = PacketRecorder.of(watcher);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 1.5F, SPAWN_Y, 1.0F).withNoFreeWill().spawn();
		smasher.setDeltaMovement(0.0, -1.5, 0.0);
		smasher.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.MACE, "weapon/meteor", 3));
		smasher.fallDistance = 6.0;
		smasher.attack(target);
		int emitters = explodePacketsOn(recorder, ParticleTypes.EXPLOSION_EMITTER);
		int debris = recorder.countMatching(
			packet -> packet instanceof ClientboundExplodePacket sent && !sent.blockParticles().isEmpty()
		);
		int sounds = soundsOn(recorder);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=meteorVisual RESULT emitterPackets={} debrisPackets={} soundPackets={} "
				+ "particles={} blockInteraction=none",
			emitters, debris, sounds, particleTypesOn(recorder)
		);
		helper.assertTrue(
			emitters > 0,
			"the Meteor blast sent " + emitters + " explosion_emitter particles, so it still reads as a poof"
		);
		helper.assertTrue(debris > 0, "the Meteor blast carried no block particles for its debris");
		helper.assertTrue(sounds > 0, "the Meteor blast played no sound");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void dreadAuraSparklesWhileItIsWorn(final GameTestHelper helper) {
		ServerPlayer wearer = mockPlayerInLevel(helper);
		wearer.setItemSlot(
			EquipmentSlot.CHEST, enchanted(helper, Items.DIAMOND_CHESTPLATE, "armor/dread_aura", 3)
		);
		wearer.setPos(helper.absoluteVec(new Vec3(2.5, SPAWN_Y, 2.5)));
		PacketRecorder recorder = PacketRecorder.of(wearer);
		helper.runAfterDelay(30, () -> {
			int souls = particlesOn(recorder, ParticleTypes.SOUL);
			EnchantmentHelper.tickEffects(helper.getLevel(), wearer);
			int manualSouls = particlesOn(recorder, ParticleTypes.SOUL);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=dreadAuraVisual RESULT soulPackets={} drivenSouls={} tickCount={} "
					+ "particles={} packets={}",
				souls, manualSouls, wearer.tickCount, particleTypesOn(recorder), recorder.count()
			);
			helper.assertTrue(
				manualSouls > 0,
				"the worn Dread Aura showed no aura particle, driven souls " + manualSouls
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void deflectionShowsTheHitItNegates(final GameTestHelper helper) {
		ServerPlayer wearer = mockPlayerInLevel(helper);
		wearer.setGameMode(GameType.SURVIVAL);
		wearer.setItemSlot(
			EquipmentSlot.CHEST, enchanted(helper, Items.DIAMOND_CHESTPLATE, "armor/deflection", 5)
		);
		wearer.setPos(helper.absoluteVec(new Vec3(2.5, SPAWN_Y, 2.5)));
		PacketRecorder recorder = PacketRecorder.of(wearer);
		int negated = 0;
		int hits = 0;
		while (hits < NEGATION_SAMPLES && negated == 0) {
			hits++;
			wearer.setHealth(wearer.getMaxHealth());
			wearer.setInvulnerableTime(0);
			if (!wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 5.0F)) {
				negated++;
			}
		}

		int sparks = particlesOn(recorder, ParticleTypes.ENCHANTED_HIT);
		int sounds = soundsOn(recorder);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=deflectionVisual RESULT hits={} negated={} sparkPackets={} soundPackets={} particles={}",
			hits, negated, sparks, sounds, particleTypesOn(recorder)
		);
		helper.assertTrue(negated > 0, "no hit was negated in " + hits + " tries");
		helper.assertTrue(sparks > 0, "the negated hit showed the wearer no particle");
		helper.assertTrue(sounds > 0, "the negated hit played the wearer no sound");
		helper.succeed();
	}

	private static int particlesOn(final PacketRecorder recorder, final ParticleOptions particle) {
		return recorder.countMatching(
			packet -> packet instanceof ClientboundLevelParticlesPacket sent && sent.particle() == particle
		);
	}

	/** The client renders a blast from the explode packet, not from a level particle packet. */
	private static int explodePacketsOn(final PacketRecorder recorder, final ParticleOptions particle) {
		return recorder.countMatching(
			packet -> packet instanceof ClientboundExplodePacket sent && sent.explosionParticle() == particle
		);
	}

	private static int soundsOn(final PacketRecorder recorder) {
		return recorder.countMatching(packet -> packet instanceof ClientboundSoundPacket);
	}

	@GameTest
	public void emberGuardBurnsTheAttackerThatHitTheWearer(final GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper, GameType.SURVIVAL);
		ServerPlayer wearer = armoured(helper, "armor/ember_guard", 3);
		attack(helper, attacker, wearer);
		boolean burning = attacker.isOnFire();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=emberGuard RESULT attackerOnFire={} fireTicks={}",
			burning, attacker.getRemainingFireTicks()
		);
		helper.assertTrue(burning, "the attacker was not set alight by Ember Guard");
		helper.succeed();
	}

	@GameTest
	public void rimeGuardFreezesTheAttackerThatHitTheWearer(final GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper, GameType.SURVIVAL);
		ServerPlayer wearer = armoured(helper, "armor/rime_guard", 3);
		float before = attacker.getHealth();
		attack(helper, attacker, wearer);
		float lost = before - attacker.getHealth();
		StellaEquips.LOGGER.info("[BEHAVIOR] test=rimeGuard RESULT attackerLost={}", lost);
		helper.assertTrue(lost > 0.0F, "the attacker took no freeze damage from Rime Guard");
		helper.succeed();
	}

	@GameTest
	public void venomGuardPoisonsTheAttackerThatHitTheWearer(final GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper, GameType.SURVIVAL);
		ServerPlayer wearer = armoured(helper, "armor/venom_guard", 3);
		attack(helper, attacker, wearer);
		boolean poisoned = attacker.hasEffect(MobEffects.POISON);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=venomGuard RESULT attackerPoisoned={}", poisoned);
		helper.assertTrue(poisoned, "the attacker was not poisoned by Venom Guard");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void dreadAuraSlowsAnEnemyFacingAwayAndNotOneFacingTheWearer(final GameTestHelper helper) {
		ServerPlayer wearer = mockPlayerInLevel(helper);
		wearer.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, Items.DIAMOND_CHESTPLATE, "armor/dread_aura", 3));
		BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
		Mob away = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(3, 2, 1));
		Mob facing = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 3));
		wearer.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
		away.setPos(origin.getX() + 2.5, origin.getY(), origin.getZ() + 0.5);
		away.setYRot(-90.0F);
		away.setYHeadRot(-90.0F);
		facing.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 2.5);
		facing.setYRot(180.0F);
		facing.setYHeadRot(180.0F);
		helper.runAfterDelay(FUNCTION_WAIT_TICKS, () -> {
			int hooked = score(helper, wearer, "ench_cd_dread");
			away.setYRot(-90.0F);
			away.setYHeadRot(-90.0F);
			facing.setYRot(180.0F);
			facing.setYHeadRot(180.0F);
			rearmCooldown(helper, wearer, "ench_cd_dread", 20);
			for (int pulse = 0; pulse < 3; pulse++) {
				EnchantmentHelper.tickEffects(helper.getLevel(), wearer);
			}

			boolean awaySlowed = away.hasEffect(MobEffects.SLOWNESS);
			boolean facingSlowed = facing.hasEffect(MobEffects.SLOWNESS);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=dreadAura RESULT facingAwaySlowed={} facingWearerSlowed={} "
					+ "tickCount={} hookCooldown={} manualCooldown={} wornLevel={} tickEntries={} tagHits={}",
				awaySlowed, facingSlowed, wearer.tickCount, hooked, score(helper, wearer, "ench_cd_dread"),
				EnchantmentHelper.getEnchantmentLevel(holder(helper, "armor/dread_aura"), wearer),
				enchantment(helper, "armor/dread_aura").getEffects(EnchantmentEffectComponents.TICK).size(),
				scratchScore(helper, "#dread_hits")
			);
			helper.assertTrue(awaySlowed, "the enemy facing away from the wearer was not slowed");
			helper.assertTrue(!facingSlowed, "the enemy facing the wearer was slowed as well");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void cloudstepRaisesTheJumpWhileSneakingAndNotWhileStanding(final GameTestHelper helper) {
		ServerPlayer wearer = mockPlayerInLevel(helper);
		wearer.setItemSlot(EquipmentSlot.FEET, enchanted(helper, Items.DIAMOND_BOOTS, "armor/cloudstep", 3));
		BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
		wearer.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
		helper.runAfterDelay(FUNCTION_WAIT_TICKS, () -> {
			boolean standingJump = wearer.hasEffect(MobEffects.JUMP_BOOST);
			boolean standingGravity = wearer.hasEffect(MobEffects.SLOW_FALLING);
			wearer.setShiftKeyDown(true);
			wearer.setPose(Pose.CROUCHING);
			helper.runAfterDelay(FUNCTION_WAIT_TICKS, () -> {
				int hooked = score(helper, wearer, "ench_cd_cloud");
				wearer.setShiftKeyDown(true);
				wearer.setPose(Pose.CROUCHING);
				rearmCooldown(helper, wearer, "ench_cd_cloud", CLOUDSTEP_GATE_TICKS);
				EnchantmentHelper.tickEffects(helper.getLevel(), wearer);

				boolean sneakingJump = wearer.hasEffect(MobEffects.JUMP_BOOST);
				boolean sneakingGravity = wearer.hasEffect(MobEffects.SLOW_FALLING);
				int jumpAmplifier = sneakingJump ? wearer.getEffect(MobEffects.JUMP_BOOST).getAmplifier() : -1;
				int gravityDuration = sneakingGravity ? wearer.getEffect(MobEffects.SLOW_FALLING).getDuration() : 0;
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=cloudstep RESULT jumpStanding={} gravityStanding={} jumpSneaking={} "
						+ "gravitySneaking={} jumpAmplifier={} gravityDuration={} tickCount={} hookCooldown={} "
						+ "sneaking={} inFunctionPredicate={} tickEntries={} mentionsLevitation={}",
					standingJump, standingGravity, sneakingJump, sneakingGravity, jumpAmplifier, gravityDuration,
					wearer.tickCount, hooked, wearer.isShiftKeyDown(),
					scratchScore(helper, "#cloud_pred"),
					enchantment(helper, "armor/cloudstep").getEffects(EnchantmentEffectComponents.TICK).size(),
					resource("data/mod-stella-equips-enchants/function/cloudstep.mcfunction").contains("levitation")
				);
				helper.assertTrue(!standingJump, "the standing wearer carried the jump effect");
				helper.assertTrue(!standingGravity, "the standing wearer carried the gravity effect");
				helper.assertTrue(sneakingJump, "the sneaking wearer carried no jump effect");
				helper.assertTrue(sneakingGravity, "the sneaking wearer carried no gravity effect");
				helper.assertTrue(
					jumpAmplifier == CLOUDSTEP_JUMP_AMPLIFIER,
					"the jump effect was amplifier " + jumpAmplifier + " instead of " + CLOUDSTEP_JUMP_AMPLIFIER
				);
				helper.assertTrue(
					!resource("data/mod-stella-equips-enchants/function/cloudstep.mcfunction").contains("levitation"),
					"the cloudstep function still mentions levitation"
				);
				helper.succeed();
			});
		});
	}

	/** The shipped function must name the two new effects and no longer grant levitation. */
	@GameTest
	public void cloudstepFunctionGrantsJumpAndGravityWithoutLevitation(final GameTestHelper helper) {
		String text = resource("data/mod-stella-equips-enchants/function/cloudstep.mcfunction");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=cloudstepFunction RESULT hasJumpBoost={} hasSlowFalling={} hasLevitation={} "
				+ "effectTicks={} amplifier={} bytes={}",
			text.contains("jump_boost"), text.contains("slow_falling"), text.contains("levitation"),
			CLOUDSTEP_EFFECT_TICKS, CLOUDSTEP_JUMP_AMPLIFIER, text.length()
		);
		helper.assertTrue(text.contains("jump_boost"), "the function grants no jump boost");
		helper.assertTrue(text.contains("slow_falling"), "the function grants no slow falling");
		helper.assertTrue(!text.contains("levitation"), "the function still grants levitation");
		helper.assertTrue(
			text.contains("effect give @s minecraft:jump_boost " + CLOUDSTEP_EFFECT_TICKS + " "
				+ CLOUDSTEP_JUMP_AMPLIFIER + " true"),
			"the jump effect is not " + CLOUDSTEP_EFFECT_TICKS + " ticks at amplifier " + CLOUDSTEP_JUMP_AMPLIFIER
		);
		helper.succeed();
	}

	private static String resource(final String path) {
		try (InputStream stream = EffectEnchantGameTest.class.getClassLoader().getResourceAsStream(path)) {
			return stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		}
	}
	@GameTest
	public void voidBrandDamageIgnoresArmour(final GameTestHelper helper) {
		float plainArmoured = healthLostToPlainHit(helper, 20.0);
		float voidArmoured = healthLostToHit(helper, "weapon/void_brand", 5, 20.0);
		float plainBare = healthLostToPlainHit(helper, 0.0);
		float voidBare = healthLostToHit(helper, "weapon/void_brand", 5, 0.0);
		float extraArmoured = voidArmoured - plainArmoured;
		float extraBare = voidBare - plainBare;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=voidBrand RESULT armouredExtra={} bareExtra={} plainArmoured={} plainBare={}",
			extraArmoured, extraBare, plainArmoured, plainBare
		);
		helper.assertTrue(extraArmoured > 0.0F, "Void Brand added no damage to an armoured target");
		helper.assertTrue(
			Math.abs(extraArmoured - extraBare) < 0.01F,
			"the armour changed the bonus: " + extraArmoured + " armoured against " + extraBare + " bare"
		);
		helper.succeed();
	}

	@GameTest
	public void railgunChargesSlowlyAndPierces(final GameTestHelper helper) {
		ServerPlayer shooter = mockPlayer(helper, GameType.SURVIVAL);
		ItemStack plain = new ItemStack(Items.CROSSBOW);
		int plainTicks = CrossbowItem.getChargeDuration(plain, shooter);
		int railgunTicks = 0;
		for (int level = 1; level <= 3; level++) {
			railgunTicks = CrossbowItem.getChargeDuration(
				enchanted(helper, Items.CROSSBOW, "crossbow/railgun", level), shooter
			);
		}

		int plainPierce = EnchantmentHelper.getPiercingCount(
			helper.getLevel(), plain, new ItemStack(Items.ARROW)
		);
		int railgunPierce = EnchantmentHelper.getPiercingCount(
			helper.getLevel(),
			enchanted(helper, Items.CROSSBOW, "crossbow/railgun", 3),
			new ItemStack(Items.ARROW)
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=railgunCharge RESULT plainTicks={} railgunTicksAtLevel3={} plainPierce={} railgunPierce={}",
			plainTicks, railgunTicks, plainPierce, railgunPierce
		);
		helper.assertTrue(
			railgunTicks > plainTicks, "the railgun charges in " + railgunTicks + " against a plain " + plainTicks
		);
		helper.assertTrue(railgunPierce > plainPierce, "the railgun pierced no more than a plain crossbow");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void railgunBoltEndsInABlastThatBreaksNothing(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer shooter = mockPlayer(helper, GameType.SURVIVAL);
		BlockPos origin = helper.absolutePos(new BlockPos(0, (int) SPAWN_Y, 0));
		shooter.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
		shooter.setYRot(0.0F);
		shooter.setXRot(0.0F);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(x, (int) SPAWN_Y, 3, Blocks.STONE);
			helper.setBlock(x, (int) SPAWN_Y + 1, 3, Blocks.STONE);
		}

		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 1.5F, SPAWN_Y, 2.0F).withNoFreeWill().spawn();
		float targetBefore = target.getHealth();
		ItemStack crossbow = enchanted(helper, Items.CROSSBOW, "crossbow/railgun", 3);
		crossbow.set(
			net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
			ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW)))
		);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
		((CrossbowItem) Items.CROSSBOW).performShooting(level, shooter, InteractionHand.MAIN_HAND, crossbow, 1.0F, 0.0F, null);
		BlockPos wall = BlockPos.containing(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.0, SPAWN_Y + 0.5, 3.5)));
		helper.runAfterDelay(SHOT_WAIT_TICKS, () -> {
			boolean intact = level.getBlockState(wall).is(Blocks.STONE);
			float targetLost = targetBefore - target.getHealth();
			List<AbstractArrow> arrows = arrowsOf(level, shooter);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=railgunBlast RESULT wallIntact={} arrows={} targetLost={}",
				intact, arrows.size(), targetLost
			);
			helper.assertTrue(!arrows.isEmpty(), "the railgun fired nothing");
			helper.assertTrue(intact, "the railgun blast broke the block it hit");
			helper.assertTrue(targetLost > 0.0F, "the railgun blast hurt nobody near the impact");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void veinminerBreaksAThreeBlockPlaneAlongTheLookingDirection(final GameTestHelper helper) {
		Map<String, String> planes = new LinkedHashMap<>();
		planes.put("south yaw 0", planeOf(helper, 0.0F, 0.0F, "x"));
		planes.put("west yaw 90", planeOf(helper, 90.0F, 0.0F, "z"));
		planes.put("north yaw 180", planeOf(helper, 180.0F, 0.0F, "x"));
		planes.put("east yaw 270 as -90", planeOf(helper, -90.0F, 0.0F, "z"));
		planes.put("down pitch 90", planeOf(helper, 0.0F, 90.0F, "x"));
		planes.put("up pitch -90", planeOf(helper, 0.0F, -90.0F, "x"));
		Enchantment veinminer = enchantment(helper, "tool/veinminer");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=veinminerPlane RESULT blocksPerSwing={} broken={} planes={} maxLevel={} "
				+ "anvilCost={} weight={}",
			VEINMINER_BLOCKS_PER_SWING, planes.size(), planes, veinminer.getMaxLevel(),
			veinminer.getAnvilCost(), veinminer.getWeight()
		);
		helper.assertTrue(
			veinminer.getMaxLevel() == 1,
			"veinminer reports " + veinminer.getMaxLevel() + " levels instead of one"
		);

		for (Map.Entry<String, String> plane : planes.entrySet()) {
			helper.assertTrue(
				plane.getValue().equals("x") || plane.getValue().equals("z"),
				"looking " + plane.getKey() + " broke " + plane.getValue()
			);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void veinminerGuardsAgainstItsOwnSecondPulse(final GameTestHelper helper) {
		ServerPlayer miner = mockPlayer(helper, GameType.SURVIVAL);
		BlockPos centre = helper.absolutePos(new BlockPos(3, (int) SPAWN_Y, 3));
		List<BlockPos> field = oreField(helper, centre);
		hold(miner, enchanted(helper, Items.DIAMOND_PICKAXE, "tool/veinminer", 1));
		miner.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
		miner.setYRot(0.0F);
		rearmCooldown(helper, miner, "ench_cd_vein", 20);
		runCommand(helper, miner, "function mod-stella-equips-enchants:veinminer");
		int afterFirst = brokenIn(helper, field).size();
		int cooldownAfterFirst = score(helper, miner, "ench_cd_vein");
		runCommand(helper, miner, "function mod-stella-equips-enchants:veinminer");
		int afterSecond = brokenIn(helper, field).size();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=veinminerGuard RESULT afterFirstPulse={} afterSecondPulse={} "
				+ "cooldownAfterFirst={} fieldSize={} expectedPerSwing={}",
			afterFirst, afterSecond, cooldownAfterFirst, field.size(), VEINMINER_BLOCKS_PER_SWING
		);
		helper.assertTrue(
			afterFirst == VEINMINER_BLOCKS_PER_SWING,
			"one pulse broke " + afterFirst + " blocks instead of " + VEINMINER_BLOCKS_PER_SWING
		);
		helper.assertTrue(
			afterSecond == VEINMINER_BLOCKS_PER_SWING,
			"the second pulse broke more: " + afterSecond + " blocks, so the guard let it re-enter"
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void oreMenderHealsHalfAHeartOnEveryOreBreak(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer miner = mockPlayer(helper, GameType.SURVIVAL);
		List<Block> ores = oreBlocks(level);
		helper.assertTrue(!ores.isEmpty(), "minecraft:ores has no entries");
		float maxHealth = miner.getMaxHealth();
		int expectedBreaks = ores.size() + 1;
		float startingHealth = maxHealth - expectedBreaks;
		helper.assertTrue(startingHealth > 0.0F, "the player has no health headroom for all ore breaks");
		miner.setHealth(startingHealth);
		hold(miner, enchanted(helper, Items.DIAMOND_PICKAXE, "tool/ore_mender", 1));
		BlockPos target = helper.absolutePos(new BlockPos(2, (int) SPAWN_Y, 2));
		miner.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
		List<String> failures = new ArrayList<>();
		int completed = 0;
		float totalHealed = 0.0F;
		for (Block ore : ores) {
			level.setBlockAndUpdate(target, ore.defaultBlockState());
			float before = miner.getHealth();
			boolean broken = miner.gameMode.destroyBlock(target);
			float after = miner.getHealth();
			float healed = after - before;
			totalHealed += healed;
			if (broken) {
				completed++;
			}
			if (!broken || !level.getBlockState(target).isAir() || Math.abs(healed - 1.0F) > 0.001F) {
				failures.add(BuiltInRegistries.BLOCK.getKey(ore) + " broken=" + broken + " healed=" + healed);
			}
		}

		level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
		float beforeRepeat = miner.getHealth();
		boolean repeatedBreak = miner.gameMode.destroyBlock(target);
		float repeatHeal = miner.getHealth() - beforeRepeat;
		completed += repeatedBreak ? 1 : 0;
		totalHealed += repeatHeal;
		if (!repeatedBreak || !level.getBlockState(target).isAir() || Math.abs(repeatHeal - 1.0F) > 0.001F) {
			failures.add("immediate repeated diamond ore break healed=" + repeatHeal);
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=oreMenderHealsHalfAHeart RESULT oreTypes={} completed={} expectedBreaks={} "
				+ "totalHealed={} healPerBreak=1.0 failures={}",
				ores.size(), completed, expectedBreaks, totalHealed, failures
		);
		helper.assertTrue(failures.isEmpty(), "Ore Mender did not heal one point per ore break: " + failures);
		helper.assertTrue(completed == expectedBreaks, "only " + completed + " of " + expectedBreaks + " breaks completed");
		helper.assertTrue(
			Math.abs(miner.getHealth() - maxHealth) < 0.001F,
			"the player ended at " + miner.getHealth() + " instead of " + maxHealth
		);
		helper.succeed();
	}

	@GameTest
	public void oreMenderRejectsStoneAndUnenchantedOre(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer miner = mockPlayer(helper, GameType.SURVIVAL);
		float startingHealth = miner.getMaxHealth() / 2.0F;
		miner.setHealth(startingHealth);
		BlockPos target = helper.absolutePos(new BlockPos(2, (int) SPAWN_Y, 2));
		miner.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
		hold(miner, enchanted(helper, Items.DIAMOND_PICKAXE, "tool/ore_mender", 1));

		level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
		boolean stoneDestroyed = miner.gameMode.destroyBlock(target);
		float healthAfterStone = miner.getHealth();
		boolean stoneAir = level.getBlockState(target).isAir();

		hold(miner, new ItemStack(Items.DIAMOND_PICKAXE));
		level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
		boolean unenchantedOreDestroyed = miner.gameMode.destroyBlock(target);
		float healthAfterUnenchantedOre = miner.getHealth();
		boolean oreAir = level.getBlockState(target).isAir();

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=oreMenderRejections RESULT stoneDestroyed={} stoneAir={} stoneHeal={} "
				+ "unenchantedOreDestroyed={} oreAir={} unenchantedOreHeal={}",
			stoneDestroyed, stoneAir, healthAfterStone - startingHealth,
			unenchantedOreDestroyed, oreAir, healthAfterUnenchantedOre - healthAfterStone
		);
		helper.assertTrue(stoneDestroyed && stoneAir, "the plain stone control did not break");
		helper.assertTrue(
			healthAfterStone == startingHealth,
			"stone healed the player by " + (healthAfterStone - startingHealth)
		);
		helper.assertTrue(unenchantedOreDestroyed && oreAir, "the unenchanted ore control did not break");
		helper.assertTrue(
			healthAfterUnenchantedOre == startingHealth,
			"an unenchanted pickaxe healed the player by " + (healthAfterUnenchantedOre - startingHealth)
		);
		helper.succeed();
	}

	@GameTest
	public void oreMenderDoesNotOverheal(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer miner = mockPlayer(helper, GameType.SURVIVAL);
		float maxHealth = miner.getMaxHealth();
		float startingHealth = maxHealth - 0.5F;
		miner.setHealth(startingHealth);
		BlockPos target = helper.absolutePos(new BlockPos(2, (int) SPAWN_Y, 2));
		miner.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
		hold(miner, enchanted(helper, Items.DIAMOND_PICKAXE, "tool/ore_mender", 1));
		level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
		boolean destroyed = miner.gameMode.destroyBlock(target);
		float healthAfter = miner.getHealth();
		boolean oreAir = level.getBlockState(target).isAir();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=oreMenderNoOverheal RESULT destroyed={} oreAir={} before={} after={} max={}",
			destroyed, oreAir, startingHealth, healthAfter, maxHealth
		);
		helper.assertTrue(destroyed && oreAir, "the diamond ore did not break");
		helper.assertTrue(healthAfter >= startingHealth, "the ore break reduced health");
		helper.assertTrue(healthAfter <= maxHealth, "Ore Mender raised health above its maximum");
		helper.assertTrue(healthAfter - startingHealth <= 1.0F, "Ore Mender added more than one health point");
		helper.succeed();
	}
	@GameTest(maxTicks = 200)
	public void enchantedYieldPaysMoreExperienceAtAHigherLevel(final GameTestHelper helper) {
		int atOne = experienceFromBreaks(helper, 1);
		int atFive = experienceFromBreaks(helper, 5);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=enchantedYield RESULT breaks={} experienceAtLevel1={} experienceAtLevel5={}",
			YIELD_BREAKS, atOne, atFive
		);
		helper.assertTrue(atOne > 0, "Enchanted Yield granted nothing at level 1 over " + YIELD_BREAKS + " breaks");
		helper.assertTrue(atFive > atOne, "a higher level paid no more: " + atOne + " then " + atFive);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void prospectorRevealRadiusScalesWithLevel(final GameTestHelper helper) {
		Enchantment prospector = enchantment(helper, "tool/prospector");
		String body;
		try (InputStream stream = EffectEnchantGameTest.class.getResourceAsStream(
			"/data/mod-stella-equips-enchants/function/prospector.mcfunction"
		)) {
			body = stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			body = null;
		}

		helper.assertTrue(body != null, "the prospector function is not on the classpath");
		if (body == null) {
			return;
		}

		int branches = 0;
		for (int level = 1; level <= 5; level++) {
			if (body.contains("matches " + level + " run particle")) {
				branches++;
			}
		}

		ServerPlayer miner = mockPlayer(helper, GameType.SURVIVAL);
		BlockPos block = helper.absolutePos(new BlockPos(2, (int) SPAWN_Y, 2));
		helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
		miner.setPos(block.getX() + 0.5, block.getY(), block.getZ() + 0.5);
		hold(miner, enchanted(helper, Items.DIAMOND_PICKAXE, "tool/prospector", 5));
		rearmCooldown(helper, miner, "ench_cd_prospect", 40);
		int before = score(helper, miner, "ench_cd_prospect");
		breakBlock(miner, block, helper.getLevel());
		int after = score(helper, miner, "ench_cd_prospect");
		int readLevel = scratchScore(helper, "#prospect_level");

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=prospectorRadius RESULT maxLevel={} levelBranches={} expectedMaxLevel=5 "
				+ "cooldownBefore={} cooldownAfter={} readLevel={}",
			prospector.getMaxLevel(), branches, before, after, readLevel
		);
		helper.assertTrue(prospector.getMaxLevel() == 5, "Prospector still caps at " + prospector.getMaxLevel());
		helper.assertTrue(branches >= 4, "the reveal radius holds " + branches + " level branches instead of 5");
		helper.assertTrue(readLevel == 5, "the function read level " + readLevel + " from a level 5 pickaxe");
		helper.assertTrue(
			after == before + 40,
			"the Prospector pulse never ran on a block break: the cooldown went " + before + " to " + after
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void meteorHurlsNearbyEnemiesOnAFallingSmash(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer smasher = mockPlayer(helper, GameType.SURVIVAL);
		BlockPos origin = helper.absolutePos(new BlockPos(1, (int) SPAWN_Y, 1));
		smasher.setPos(origin.getX() + 0.5, origin.getY() + 8.0, origin.getZ() + 0.5);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 1.5F, SPAWN_Y, 1.0F).withNoFreeWill().spawn();
		Mob bystander = helper.spawnMob(EntityTypes.ZOMBIE, 2.1F, SPAWN_Y, 1.0F).withNoFreeWill().spawn();
		float before = bystander.getHealth();
		double startDistance = bystander.distanceToSqr(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
		smasher.setDeltaMovement(0.0, -1.5, 0.0);
		ItemStack mace = enchanted(helper, Items.MACE, "weapon/meteor", 3);
		smasher.setItemInHand(InteractionHand.MAIN_HAND, mace);
		smasher.fallDistance = 6.0;
		smasher.attack(target);
		helper.runAfterDelay(5, () -> {
			float lost = before - bystander.getHealth();
			double moved = bystander.distanceToSqr(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5) - startDistance;
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=meteor RESULT bystanderLost={} bystanderPushedSqr={} targetAlive={}",
				lost, moved, target.isAlive()
			);
			helper.assertTrue(lost > 0.0F, "the falling smash hurt no nearby enemy");
			helper.assertTrue(moved > 0.0, "the smash threw nobody outward");
			helper.assertTrue(!target.isAlive(), "the falling smash did not take out the target beneath it");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void undertowDragsTheThrowerTowardTheTarget(final GameTestHelper helper) {
		ServerPlayer thrower = mockPlayer(helper, GameType.SURVIVAL);
		BlockPos origin = helper.absolutePos(new BlockPos(1, (int) SPAWN_Y, 1));
		thrower.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
		thrower.setYRot(0.0F);
		Mob victim = helper.spawnMob(EntityTypes.ZOMBIE, 4.5F, SPAWN_Y, 4.0F).withNoFreeWill().spawn();
		ItemStack trident = enchanted(helper, Items.TRIDENT, "weapon/undertow", 3);
		thrower.setItemInHand(InteractionHand.MAIN_HAND, trident);
		thrower.setDeltaMovement(0.0, 0.0, 0.0);
		thrower.attack(victim);
		double pullX = thrower.getDeltaMovement().x;
		double pullY = thrower.getDeltaMovement().y;
		double pullZ = thrower.getDeltaMovement().z;
		double towardVictim = victim.getZ() - thrower.getZ();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=undertow RESULT pullX={} pullY={} pullZ={} towardVictim={}",
			pullX, pullY, pullZ, towardVictim
		);
		helper.assertTrue(
			Math.signum(pullZ) == Math.signum(towardVictim) && pullZ != 0.0,
			"Undertow pushed the thrower " + pullZ + " on z while the target is at " + towardVictim
		);
		helper.assertTrue(pullY == 0.0, "the pull left the ground: y=" + pullY);
		helper.succeed();
	}

	@GameTest
	public void impaleAddsDamageAndASlowToASpearHit(final GameTestHelper helper) {
		Mob plainVictim = victim(helper);
		ServerPlayer plainSpear = mockPlayer(helper, GameType.SURVIVAL);
		plainSpear.setPos(plainVictim.getX(), plainVictim.getY(), plainVictim.getZ() + 1.0);
		hold(plainSpear, new ItemStack(Items.IRON_SPEAR));
		float plainBefore = plainVictim.getHealth();
		plainSpear.attack(plainVictim);
		float plainLost = plainBefore - plainVictim.getHealth();

		Mob impaled = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 2, 4));
		impaled.setInvulnerableTime(0);
		ServerPlayer impaler = mockPlayer(helper, GameType.SURVIVAL);
		impaler.setPos(impaled.getX(), impaled.getY(), impaled.getZ() + 1.0);
		hold(impaler, enchanted(helper, Items.IRON_SPEAR, "weapon/impale", 3));
		float impaledBefore = impaled.getHealth();
		impaler.attack(impaled);
		float impaledLost = impaledBefore - impaled.getHealth();
		boolean slowed = impaled.hasEffect(MobEffects.SLOWNESS);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=impale RESULT plainLost={} impaledLost={} slowed={}",
			plainLost, impaledLost, slowed
		);
		helper.assertTrue(
			impaledLost > plainLost,
			"Impale added nothing: plain " + plainLost + " against impaled " + impaledLost
		);
		helper.assertTrue(slowed, "Impale landed no slow on the victim");
		helper.succeed();
	}

	private static int slownessAmplifierAfterHit(final GameTestHelper helper, final int level) {
		Mob victim = victim(helper);
		hit(helper, "weapon/chill", level, victim);
		return victim.hasEffect(MobEffects.SLOWNESS)
			? victim.getEffect(MobEffects.SLOWNESS).getAmplifier()
			: -1;
	}

	private static int poisonDurationAfterHit(final GameTestHelper helper, final int level) {
		Mob victim = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(2, 2, 2));
		victim.setInvulnerableTime(0);
		hit(helper, "weapon/venom", level, victim);
		return victim.hasEffect(MobEffects.POISON) ? victim.getEffect(MobEffects.POISON).getDuration() : 0;
	}

	private static float healthLostToHit(final GameTestHelper helper, final String path, final int level) {
		return healthLostToHit(helper, path, level, 0.0);
	}

	private static float healthLostToHit(
		final GameTestHelper helper, final String path, final int level, final double armour
	) {
		Mob victim = victim(helper, armour);
		float before = victim.getHealth();
		hit(helper, path, level, victim);
		return before - victim.getHealth();
	}

	private static float healthLostToPlainHit(final GameTestHelper helper, final double armour) {
		Mob victim = victim(helper, armour);
		float before = victim.getHealth();
		hit(helper, null, 1, victim);
		return before - victim.getHealth();
	}

	private static int negatedHits(final GameTestHelper helper, final int level) {
		ServerLevel level0 = helper.getLevel();
		ServerPlayer wearer = mockPlayer(helper, GameType.SURVIVAL);
		wearer.setItemSlot(
			EquipmentSlot.CHEST, enchanted(helper, Items.DIAMOND_CHESTPLATE, "armor/deflection", level)
		);
		int negated = 0;
		int landed = 0;
		for (int hit = 0; hit < NEGATION_SAMPLES; hit++) {
			wearer.setHealth(wearer.getMaxHealth());
			wearer.setInvulnerableTime(0);
			float before = wearer.getHealth();
			if (wearer.hurtServer(level0, level0.damageSources().magic(), 5.0F)) {
				landed++;
			}

			if (wearer.getHealth() == before) {
				negated++;
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=deflectionSample RESULT level={} samples={} landed={} rank={}",
			level, NEGATION_SAMPLES, landed,
			EnchantmentHelper.getEnchantmentLevel(holder(helper, "armor/deflection"), wearer)
		);
		return negated;
	}

	private static float damage(final ServerLevel level, final ServerPlayer wearer, final float amount) {
		wearer.setHealth(wearer.getMaxHealth());
		wearer.setInvulnerableTime(0);
		float before = wearer.getHealth();
		wearer.hurtServer(level, level.damageSources().enderPearl(), amount);
		return before - wearer.getHealth();
	}

	/** The axis the three broken blocks spread on, or the count when it is not three. */
	private static String planeOf(
		final GameTestHelper helper, final float yaw, final float pitch, final String expected
	) {
		ServerPlayer miner = mockPlayer(helper, GameType.SURVIVAL);
		BlockPos centre = helper.absolutePos(new BlockPos(3, (int) SPAWN_Y, 3));
		List<BlockPos> field = oreField(helper, centre);
		hold(miner, enchanted(helper, Items.DIAMOND_PICKAXE, "tool/veinminer", 1));
		miner.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
		miner.setYRot(yaw);
		miner.setXRot(pitch);
		rearmCooldown(helper, miner, "ench_cd_vein", 20);
		breakBlock(miner, centre, helper.getLevel());
		List<BlockPos> broken = brokenIn(helper, field);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=veinminerSwing RESULT yaw={} pitch={} expectedAxis={} brokenCount={} broken={}",
			yaw, pitch, expected, broken.size(), coordinates(centre, broken)
		);

		if (broken.size() != VEINMINER_BLOCKS_PER_SWING) {
			return broken.size() + " blocks";
		}

		Set<String> axes = new LinkedHashSet<>();

		for (BlockPos pos : broken) {
			BlockPos offset = pos.subtract(centre);
			axes.add(Math.abs(offset.getX()) + Math.abs(offset.getY()) + Math.abs(offset.getZ()) == 0
				? "centre"
				: offset.getX() != 0 ? "x" : offset.getZ() != 0 ? "z" : "y");
		}

		axes.remove("centre");
		return axes.size() == 1 && axes.contains(expected) ? expected : axes.toString();
	}

	private static List<Block> oreBlocks(final ServerLevel level) {
		Registry<Block> registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
		TagKey<Block> ores = TagKey.create(
			Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "ores")
		);
		List<Block> blocks = new ArrayList<>();
		registry.getTagOrEmpty(ores).forEach(holder -> blocks.add(holder.value()));
		blocks.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
		return blocks;
	}

	private static List<BlockPos> oreField(final GameTestHelper helper, final BlockPos centre) {
		List<BlockPos> field = new ArrayList<>();

		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos pos = centre.offset(dx, dy, dz);
					helper.getLevel().setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState());
					field.add(pos);
				}
			}
		}

		return field;
	}

	private static List<BlockPos> brokenIn(final GameTestHelper helper, final List<BlockPos> field) {
		List<BlockPos> broken = new ArrayList<>();

		for (BlockPos pos : field) {
			if (!helper.getLevel().getBlockState(pos).is(Blocks.DIAMOND_ORE)) {
				broken.add(pos);
			}
		}

		return broken;
	}

	private static String coordinates(final BlockPos centre, final List<BlockPos> broken) {
		List<String> offsets = new ArrayList<>();

		for (BlockPos pos : broken) {
			BlockPos offset = pos.subtract(centre);
			offsets.add(offset.getX() + "," + offset.getY() + "," + offset.getZ());
		}

		java.util.Collections.sort(offsets);
		return offsets.toString();
	}

	private static int experienceFromBreaks(final GameTestHelper helper, final int level) {
		ServerPlayer miner = mockPlayer(helper, GameType.SURVIVAL);
		BlockPos centre = helper.absolutePos(new BlockPos(3, (int) SPAWN_Y, 3));
		miner.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
		hold(miner, enchanted(helper, Items.DIAMOND_PICKAXE, "tool/enchanted_yield", level));
		runCommand(helper, miner, "experience set @s 0 points");
		int zero = miner.totalExperience;
		for (int broken = 0; broken < YIELD_BREAKS; broken++) {
			helper.getLevel().setBlockAndUpdate(centre, Blocks.STONE.defaultBlockState());
			rearmCooldown(helper, miner, "ench_cd_yield", 20);
			breakBlock(miner, centre, helper.getLevel());
		}

		return miner.totalExperience - zero;
	}

	/** Move the guard back so the next pulse runs, whatever the clock reads. */
	private static void rearmCooldown(
		final GameTestHelper helper, final ServerPlayer player, final String objective, final int ticks
	) {
		runCommand(helper, player, "scoreboard players operation @s " + objective + " = #clock ench_clock");
		runCommand(helper, player, "scoreboard players remove @s " + objective + " " + ticks);
	}

	/** Which NBT path holds a held tool, so a function can read its level. */
	private static void probeHeldItemPaths(final GameTestHelper helper, final ServerPlayer player) {
		runCommand(helper, player,
			"execute store result score #probe_slot ench_scratch run data get entity @s SelectedItemSlot");
		runCommand(helper, player,
			"execute store result score #probe_inv ench_scratch run data get entity @s Inventory[{Slot:0b}].count");
		runCommand(helper, player,
			"execute store result score #probe_equip ench_scratch run data get entity @s equipment.mainhand.count");
		runCommand(helper, player,
			"execute store result score #probe_sel ench_scratch run data get entity @s SelectedItem.count");
		runCommand(helper, player,
			"execute if data entity @s Inventory[{Slot:0b}].components "
				+ "run scoreboard players set #probe_comp ench_scratch 1");
		runCommand(helper, player,
			"execute if data entity @s Inventory[{Slot:0b}].components.\"minecraft:enchantments\" "
				+ "run scoreboard players set #probe_ench ench_scratch 1");
		runCommand(helper, player,
			"execute if data entity @s Inventory[{Slot:0b}].components.\"minecraft:enchantments\".levels "
				+ "run scoreboard players set #probe_lvls ench_scratch 1");		runCommand(helper, player,
			"execute if data entity @s Inventory[{Slot:0b}].components.\"minecraft:enchantments\"."
				+ "\"mod-stella-equips-enchants:tool/veinminer\" "
				+ "run scoreboard players set #probe_id ench_scratch 1");
		runCommand(helper, player,
			"execute store result score #probe_lvl ench_scratch run data get entity @s "
				+ "Inventory[{Slot:0b}].components.\"minecraft:enchantments\"."
				+ "\"mod-stella-equips-enchants:tool/veinminer\"");
	}

	private static int score(final GameTestHelper helper, final ServerPlayer player, final String objective) {
		var scoreboard = helper.getLevel().getServer().getScoreboard();
		var holder = scoreboard.getObjective(objective);
		if (holder == null) {
			return 0;
		}

		return scoreboard.getOrCreatePlayerScore(player, holder).get();
	}

	/** A fake player score, written by the functions in `ench_scratch`. */
	private static int scratchScore(final GameTestHelper helper, final String name) {
		var scoreboard = helper.getLevel().getServer().getScoreboard();
		var objective = scoreboard.getObjective("ench_scratch");
		if (objective == null) {
			return 0;
		}

		return scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name), objective).get();
	}

	private static void runCommand(final GameTestHelper helper, final LivingEntity entity, final String command) {
		var server = helper.getLevel().getServer();
		var source = server.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withEntity(entity)
			.withPosition(entity.position())
			.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER)
			.withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}

	private static void attack(final GameTestHelper helper, final ServerPlayer attacker, final ServerPlayer wearer) {
		attacker.setItemInHand(
			InteractionHand.MAIN_HAND, enchanted(helper, Items.DIAMOND_SWORD, "weapon/chill", 1)
		);
		attacker.attack(wearer);
	}

	private static ServerPlayer armoured(final GameTestHelper helper, final String path, final int level) {
		ServerPlayer wearer = mockPlayer(helper, GameType.SURVIVAL);
		wearer.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, Items.DIAMOND_CHESTPLATE, path, level));
		return wearer;
	}

	/**
	 * A mock player with a connection. `ServerPlayer.isInvulnerableTo` reads
	 * `connection.hasClientLoaded`, so a damaged mock player without one throws.
	 */
	private static ServerPlayer mockPlayer(final GameTestHelper helper, final GameType type) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(type);
		PacketRecorder.attach(player);
		return player;
	}

	private static ServerPlayer mockPlayerInLevel(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PacketRecorder.attach(player);
		return player;
	}

	/**
	 * A player holds the tool in the hand slot the hook reads and in the inventory
	 * the level lookup reads, because `ItemStack` NBT has only the inventory.
	 */
	private static ItemStack hold(final ServerPlayer player, final ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.getInventory().setSelectedItem(stack);
		return stack;
	}

	/** The real break path: `destroyBlock` never calls `EnchantmentHelper.onHitBlock`. */
	private static void breakBlock(final ServerPlayer miner, final BlockPos pos, final ServerLevel level) {
		miner.gameMode.handleBlockBreakAction(
			pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 0
		);
	}

	private static String particleTypesOn(final PacketRecorder recorder) {
		Map<String, Integer> seen = new TreeMap<>();
		for (Packet<?> packet : recorder.snapshot()) {
			if (packet instanceof ClientboundLevelParticlesPacket sent) {
				seen.merge(String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(sent.particle().getType())), 1, Integer::sum);
			}
		}

		return seen.toString();
	}

	private static void hit(
		final GameTestHelper helper, final String path, final int level, final Mob victim
	) {
		ServerPlayer attacker = mockPlayer(helper, GameType.SURVIVAL);
		attacker.setPos(victim.getX(), victim.getY(), victim.getZ() + 1.0);
		ItemStack weapon = path == null
			? new ItemStack(Items.DIAMOND_SWORD)
			: enchanted(helper, Items.DIAMOND_SWORD, path, level);
		attacker.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		attacker.attack(victim);
	}

	private static Mob victim(final GameTestHelper helper) {
		return victim(helper, 0.0);
	}

	private static Mob victim(final GameTestHelper helper, final double armour) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(armour);
		zombie.setInvulnerableTime(0);
		return zombie;
	}

	private static List<AbstractArrow> arrowsOf(final ServerLevel level, final net.minecraft.world.entity.Entity owner) {
		List<AbstractArrow> arrows = new ArrayList<>();
		for (net.minecraft.world.entity.Entity entity : level.getEntitiesOfClass(
			net.minecraft.world.entity.Entity.class,
			owner.getBoundingBox().inflate(32.0)
		)) {
			if (entity instanceof AbstractArrow arrow && owner.equals(arrow.getOwner())) {
				arrows.add(arrow);
			}
		}

		return arrows;
	}

	private static Set<Identifier> tagValues(final GameTestHelper helper, final String tag) {
		Registry<Enchantment> registry = registry(helper);
		var key = net.minecraft.tags.TagKey.create(
			Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("minecraft", tag)
		);
		Set<Identifier> values = new java.util.TreeSet<>();
		registry.get(key).ifPresent(holders -> {
			for (Holder<Enchantment> holder : holders) {
				holder.unwrapKey().ifPresent(holderKey -> values.add(holderKey.identifier()));
			}
		});
		return values;
	}

	private static Registry<Enchantment> registry(final GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static Enchantment enchantment(final GameTestHelper helper, final String path) {
		return registry(helper).getValue(Identifier.fromNamespaceAndPath(MOD_ID, path));
	}

	private static Holder<Enchantment> holder(final GameTestHelper helper, final String path) {
		return registry(helper).getOrThrow(
			ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MOD_ID, path))
		);
	}

	private static ItemStack enchanted(
		final GameTestHelper helper, final Item item, final String path, final int level
	) {
		Registry<Enchantment> registry = registry(helper);
		Holder<Enchantment> holder = registry.getOrThrow(
			ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MOD_ID, path))
		);
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(holder, level);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}
}
