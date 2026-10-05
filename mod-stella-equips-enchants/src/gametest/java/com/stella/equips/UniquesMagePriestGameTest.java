package com.stella.equips;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueFields;
import com.stella.equips.unique.UniqueTargets;
import com.stella.equips.unique.Uniques;
import com.stella.equips.unique.mp.HexweaversHat;
import com.stella.equips.unique.mp.MagmaGreaves;
import com.stella.equips.unique.mp.MpKit;
import com.stella.equips.unique.mp.MpRolls;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.stella.equips.unique.mp.TidecallerVestments;
import com.stella.equips.unique.mp.ClericsMitre;
import com.stella.equips.unique.mp.HeartwoodBark;
import com.stella.equips.unique.mp.GelMantle;
import com.stella.equips.unique.mp.SpectralVestments;
import com.stella.equips.unique.mp.ConduitMantle;
import com.stella.equips.unique.mp.RevenantGreaves;
import com.stella.equips.unique.mp.RavagersBenediction;
import com.stella.equips.unique.mp.BoarhideVest;
import com.stella.equips.unique.mp.ThreeSkullReliquary;
import com.stella.equips.unique.mp.MilkgelHood;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Behavior checks for mage and priest uniques. */
public class UniquesMagePriestGameTest {
	private static final float HIT = 10.0F;
	private static final Map<UUID, String> FORCED = new ConcurrentHashMap<>();

	static {
		MpRolls.setRoller(new MpRolls.Roller() {
			@Override
			public boolean succeeds(final String unique, final ServerPlayer holder, final double chance) {
				return unique.equals(FORCED.get(holder.getUUID()));
			}

			@Override
			public double unit(final String unique, final ServerPlayer holder) {
				return 0.0;
			}
		});
	}

	private static void force(final UniqueContext c, final String id) {
		FORCED.put(c.holder().getUUID(), id);
	}

	private static UniqueContext context(final GameTestHelper helper, final String id, final int refine) {
		UniqueDef def = Uniques.byId(id);
		helper.assertTrue(def != null, "missing unique " + id);
		ServerPlayer holder = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		return new UniqueContext(holder, UniqueTestKit.stack(def, refine), refine);
	}

	private static UniqueContext connectedContext(final GameTestHelper helper, final String id, final int refine, final Vec3 at) {
		UniqueDef def = Uniques.byId(id);
		helper.assertTrue(def != null, "missing unique " + id);
		ServerPlayer holder = UniqueTestKit.connected(helper, at);
		return new UniqueContext(holder, UniqueTestKit.stack(def, refine), refine);
	}

	private static void leave(final UniqueContext c) {
		c.holder().level().getServer().getPlayerList().remove(c.holder());
	}

	private static LivingEntity foe(final GameTestHelper helper, final double x) {
		return foe(helper, x, 5.5);
	}

	private static LivingEntity foe(final GameTestHelper helper, final double x, final double z) {
		LivingEntity target = helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new Vec3(x, 1.0, z));
		target.addEffect(new MobEffectInstance(MobEffects.POISON, 40));
		helper.assertTrue(target.hasEffect(MobEffects.POISON), "the test foe rejects Poison");
		target.removeEffect(MobEffects.POISON);
		return target;
	}

	private static void hit(final GameTestHelper helper, final String id, final UniqueContext c, final LivingEntity target, final boolean killed) {
		Uniques.byId(id).effect().afterSpell(c, target, helper.getLevel().damageSources().indirectMagic(c.holder(), c.holder()), HIT, killed);
	}

	private static float damage(final GameTestHelper helper, final String id, final UniqueContext c, final LivingEntity target) {
		return Uniques.byId(id).effect().outgoingSpell(c, target, helper.getLevel().damageSources().indirectMagic(c.holder(), c.holder()), HIT);
	}

	private static void check(final GameTestHelper helper, final String name, final boolean passed, final String detail) {
		UniqueTestKit.result(name, detail);
		helper.assertTrue(passed, detail);
		helper.succeed();
	}

	@GameTest
	public void hexweaverBaseGivesOneHarmfulEffect(final GameTestHelper helper) {
		UniqueContext c = context(helper, "hexweaver_hat", 0);
		LivingEntity target = foe(helper, 2.5);
		hit(helper, "hexweaver_hat", c, target, false);
		int count = HexweaversHat.POOL.stream().mapToInt(effect -> target.hasEffect(effect) ? 1 : 0).sum();
		check(helper, "hexweaverBase", count == HexweaversHat.TUNING.baseEffects(), "effects=" + count);
	}

	@GameTest
	public void hexweaverPlus15DamagesAThreeEffectTarget(final GameTestHelper helper) {
		UniqueContext low = context(helper, "hexweaver_hat", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "hexweaver_hat", Uniques.PLUS_15);
		LivingEntity target = foe(helper, 2.5);
		for (var effect : List.of(MobEffects.POISON, MobEffects.SLOWNESS, MobEffects.WEAKNESS)) {
			target.addEffect(new MobEffectInstance(effect, 100));
		}
		float a = damage(helper, "hexweaver_hat", low, target);
		float b = damage(helper, "hexweaver_hat", high, target);
		check(helper, "hexweaverTier", a == HIT && b > a, "low=" + a + " high=" + b);
	}

	@GameTest
	public void blazingBaseIgnitesSpellTarget(final GameTestHelper helper) {
		UniqueContext c = context(helper, "blazing_mantle", 0);
		LivingEntity target = foe(helper, 2.5);
		hit(helper, "blazing_mantle", c, target, false);
		check(helper, "blazingBase", target.isOnFire(), "fire=" + target.getRemainingFireTicks());
	}

	@GameTest
	public void blazingPlus15StopsFireDamage(final GameTestHelper helper) {
		UniqueContext low = context(helper, "blazing_mantle", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "blazing_mantle", Uniques.PLUS_15);
		float a = Uniques.byId("blazing_mantle").effect().incoming(low, helper.getLevel().damageSources().onFire(), HIT);
		float b = Uniques.byId("blazing_mantle").effect().incoming(high, helper.getLevel().damageSources().onFire(), HIT);
		check(helper, "blazingTier", a == HIT && b == 0, "low=" + a + " high=" + b);
	}

	@GameTest
	public void sulfurBasePoisonsNearbyFoe(final GameTestHelper helper) {
		UniqueContext c = context(helper, "sulfur_crown", 0);
		LivingEntity target = foe(helper, 2.5);
		LivingEntity nearby = foe(helper, 3.5);
		LivingEntity bystander = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(2.5, 1.0, 4.5));
		hit(helper, "sulfur_crown", c, target, false);
		check(helper, "sulfurBase", nearby.hasEffect(MobEffects.POISON) && !target.hasEffect(MobEffects.POISON) && !bystander.hasEffect(MobEffects.POISON),
			"neighbor=" + nearby.hasEffect(MobEffects.POISON) + " target=" + target.hasEffect(MobEffects.POISON)
				+ " bystander=" + bystander.hasEffect(MobEffects.POISON));
	}

	@GameTest
	public void sulfurPlus15DamagesPoisonedTarget(final GameTestHelper helper) {
		UniqueContext low = context(helper, "sulfur_crown", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "sulfur_crown", Uniques.PLUS_15);
		LivingEntity target = foe(helper, 2.5);
		target.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
		float a = damage(helper, "sulfur_crown", low, target);
		float b = damage(helper, "sulfur_crown", high, target);
		check(helper, "sulfurTier", a == HIT && b > a, "low=" + a + " high=" + b);
	}

	@GameTest
	public void spectralBaseMakesTargetGlow(final GameTestHelper helper) {
		UniqueContext c = context(helper, "spectral_circlet", 0);
		LivingEntity target = foe(helper, 2.5);
		hit(helper, "spectral_circlet", c, target, false);
		check(helper, "spectralBase", target.hasEffect(MobEffects.GLOWING), "glowing=" + target.hasEffect(MobEffects.GLOWING));
	}

	@GameTest(maxTicks = 100)
	public void spectralPlus15EchoesOnlyOnGlowingTarget(final GameTestHelper helper) {
		UniqueContext low = context(helper, "spectral_circlet", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "spectral_circlet", Uniques.PLUS_15);
		LivingEntity lowTarget = foe(helper, 2.5);
		LivingEntity highTarget = foe(helper, 6.5);
		lowTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100));
		highTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100));
		hit(helper, "spectral_circlet", low, lowTarget, false);
		hit(helper, "spectral_circlet", high, highTarget, false);
		helper.runAfterDelay(25, () -> check(helper, "spectralTier", highTarget.getHealth() < lowTarget.getHealth(),
			"low=" + lowTarget.getHealth() + " high=" + highTarget.getHealth()));
	}

	@GameTest
	public void tarBaseSlowsSpellTarget(final GameTestHelper helper) {
		UniqueContext c = context(helper, "tar_shroud", 0);
		LivingEntity target = foe(helper, 2.5);
		hit(helper, "tar_shroud", c, target, false);
		check(helper, "tarBase", target.hasEffect(MobEffects.SLOWNESS), "slow=" + target.hasEffect(MobEffects.SLOWNESS));
	}

	@GameTest
	public void tarPlus15SpreadsSlownessOnKill(final GameTestHelper helper) {
		UniqueContext low = context(helper, "tar_shroud", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "tar_shroud", Uniques.PLUS_15);
		LivingEntity lowTarget = foe(helper, 1.5);
		LivingEntity highTarget = foe(helper, 8.5);
		LivingEntity lowNeighbor = foe(helper, 2.5);
		LivingEntity highNeighbor = foe(helper, 9.5);
		lowTarget.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100));
		highTarget.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100));
		hit(helper, "tar_shroud", low, lowTarget, true);
		hit(helper, "tar_shroud", high, highTarget, true);
		check(helper, "tarTier", !lowNeighbor.hasEffect(MobEffects.SLOWNESS) && highNeighbor.hasEffect(MobEffects.SLOWNESS),
			"low=" + lowNeighbor.hasEffect(MobEffects.SLOWNESS) + " high=" + highNeighbor.hasEffect(MobEffects.SLOWNESS));
	}

	@GameTest
	public void starfallBaseWithersSpellTarget(final GameTestHelper helper) {
		UniqueContext c = context(helper, "starfall_crown", 0);
		LivingEntity target = foe(helper, 2.5);
		hit(helper, "starfall_crown", c, target, false);
		check(helper, "starfallBase", target.hasEffect(MobEffects.WITHER), "wither=" + target.hasEffect(MobEffects.WITHER));
	}

	@GameTest
	public void starfallPlus15DamagesWitheredTarget(final GameTestHelper helper) {
		UniqueContext low = context(helper, "starfall_crown", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "starfall_crown", Uniques.PLUS_15);
		LivingEntity target = foe(helper, 2.5);
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100));
		float a = damage(helper, "starfall_crown", low, target);
		float b = damage(helper, "starfall_crown", high, target);
		check(helper, "starfallTier", a == HIT && b > a, "low=" + a + " high=" + b);
	}

	@GameTest(maxTicks = 100)
	public void fangcallerBaseRaisesFangsUnderTheTarget(final GameTestHelper helper) {
		UniqueContext c = context(helper, "fangcaller_robe", 0);
		force(c, "fangcaller_robe");
		LivingEntity target = foe(helper, 2.5);
		hit(helper, "fangcaller_robe", c, target, false);
		int fangs = helper.getLevel().getEntitiesOfClass(Entity.class, new AABB(target.position(), target.position()).inflate(2.0),
			entity -> entity.getType() == EntityTypes.EVOKER_FANGS).size();
		check(helper, "fangcallerBase", fangs > 0, "fangs=" + fangs);
	}

	@GameTest
	public void magmaBaseSplashesOneNearbyFoe(final GameTestHelper helper) {
		UniqueContext c = context(helper, "magma_greaves", 0);
		LivingEntity target = foe(helper, 2.5);
		LivingEntity neighbor = foe(helper, 3.5);
		float before = neighbor.getHealth();
		hit(helper, "magma_greaves", c, target, false);
		check(helper, "magmaBase", neighbor.getHealth() < before && target.getHealth() == target.getMaxHealth(),
			"neighborLost=" + (before - neighbor.getHealth()) + " targetLost=" + (target.getMaxHealth() - target.getHealth()));
	}

	@GameTest
	public void volatileBaseExplodesOnSpellKillWithoutBlocks(final GameTestHelper helper) {
		UniqueContext c = context(helper, "volatile_hood", 0);
		LivingEntity target = foe(helper, 2.5);
		LivingEntity neighbor = foe(helper, 3.5);
		BlockPos block = new BlockPos(3, 0, 5);
		helper.setBlock(block, Blocks.STONE);
		float before = neighbor.getHealth();
		hit(helper, "volatile_hood", c, target, true);
		check(helper, "volatileBase", neighbor.getHealth() < before && helper.getBlockState(block).is(Blocks.STONE),
			"neighborLost=" + (before - neighbor.getHealth()));
	}

	@GameTest
	public void splitGelBaseSplitsAHitToOneOtherFoe(final GameTestHelper helper) {
		UniqueContext c = context(helper, "split_gel_boots", 0);
		force(c, "split_gel_boots");
		LivingEntity target = foe(helper, 2.5);
		LivingEntity neighbor = foe(helper, 3.5);
		float before = neighbor.getHealth();
		hit(helper, "split_gel_boots", c, target, false);
		check(helper, "splitGelBase", neighbor.getHealth() < before && target.getHealth() == target.getMaxHealth(),
			"neighborLost=" + (before - neighbor.getHealth()));
	}

	@GameTest
	public void endflameBaseLeavesABreathField(final GameTestHelper helper) {
		UniqueContext c = context(helper, "endflame_robe", 0);
		force(c, "endflame_robe");
		LivingEntity target = foe(helper, 2.5);
		hit(helper, "endflame_robe", c, target, false);
		long fields = UniqueFields.active().stream().filter(field -> field.level() == helper.getLevel()
			&& field.center().distanceToSqr(target.position()) < 0.1).count();
		check(helper, "endflameBase", fields == 1, "ownedFields=" + fields);
	}

	@GameTest(maxTicks = 100)
	public void echoBaseLandsOneDelayedHit(final GameTestHelper helper) {
		UniqueContext c = context(helper, "echo_circlet", 0);
		LivingEntity target = foe(helper, 2.5);
		float before = target.getHealth();
		hit(helper, "echo_circlet", c, target, false);
		helper.runAfterDelay(25, () -> check(helper, "echoBase", target.getHealth() < before,
			"targetLost=" + (before - target.getHealth())));
	}

	@GameTest(maxTicks = 100)
	public void fangcallerPlus15FangsSlowTheTarget(final GameTestHelper helper) {
		UniqueContext low = context(helper, "fangcaller_robe", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "fangcaller_robe", Uniques.PLUS_15);
		force(low, "fangcaller_robe");
		force(high, "fangcaller_robe");
		LivingEntity lowTarget = foe(helper, 2.5, 2.5);
		LivingEntity highTarget = foe(helper, 2.5, 8.5);
		hit(helper, "fangcaller_robe", low, lowTarget, false);
		hit(helper, "fangcaller_robe", high, highTarget, false);
		helper.runAfterDelay(30, () -> {
			MobEffectInstance slow = highTarget.getEffect(MobEffects.SLOWNESS);
			check(helper, "fangcallerTier", lowTarget.getEffect(MobEffects.SLOWNESS) == null && slow != null && slow.getAmplifier() == 2,
				"lowSlow=" + lowTarget.getEffect(MobEffects.SLOWNESS) + " highSlow=" + slow);
		});
	}

	@GameTest
	public void magmaPlus15SplashesMoreThanPlus14(final GameTestHelper helper) {
		UniqueContext low = context(helper, "magma_greaves", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "magma_greaves", Uniques.PLUS_15);
		LivingEntity lowTarget = foe(helper, 2.5, 2.5);
		LivingEntity highTarget = foe(helper, 2.5, 8.5);
		LivingEntity lowNeighbor = foe(helper, 3.5, 2.5);
		LivingEntity highNeighbor = foe(helper, 3.5, 8.5);
		hit(helper, "magma_greaves", low, lowTarget, false);
		hit(helper, "magma_greaves", high, highTarget, false);
		float lowLost = lowNeighbor.getMaxHealth() - lowNeighbor.getHealth();
		float highLost = highNeighbor.getMaxHealth() - highNeighbor.getHealth();
		if (highLost == 0.0F) {
			Vec3 targetAtFailure = highTarget.position();
			Vec3 neighborAtFailure = highNeighbor.position();
			BlockPos neighborBlock = highNeighbor.blockPosition();
			boolean loadedChunk = helper.getLevel().getChunkSource().getChunkNow(neighborBlock.getX() >> 4, neighborBlock.getZ() >> 4) != null;
			boolean indexed = helper.getLevel().getEntity(highNeighbor.getUUID()) == highNeighbor;
			boolean candidate = MpKit.foesAround(helper.getLevel(), high.holder(), highTarget.position(),
				MagmaGreaves.radiusAt(high.refine()), highTarget).contains(highNeighbor);
			boolean foe = UniqueTargets.isFoe(high.holder(), highNeighbor);
			double boxDistance = highNeighbor.getBoundingBox().distanceToSqr(highTarget.position());
			boolean aliveBeforeControl = highNeighbor.isAlive();
			float controlBefore = highNeighbor.getHealth();
			boolean controlHit = UniqueDamage.deal(helper.getLevel(), highNeighbor, high.holder(),
				(float) (HIT * MagmaGreaves.shareAt(high.refine())), false);
			float controlAfter = highNeighbor.getHealth();
			StellaEquips.LOGGER.info("[DIAG] test=magmaTier originalHighLost=" + highLost + " target=" + highTarget.getUUID()
				+ "@" + targetAtFailure + " neighbor=" + highNeighbor.getUUID() + "@" + neighborAtFailure
				+ " aliveBefore=" + aliveBeforeControl + " aliveAfter=" + highNeighbor.isAlive()
				+ " loadedChunk=" + loadedChunk + " indexed=" + indexed
				+ " candidate=" + candidate + " foe=" + foe + " boxDistance=" + boxDistance
				+ " controlHit=" + controlHit + " controlBefore=" + controlBefore + " controlAfter=" + controlAfter);
		}
		check(helper, "magmaTier", lowLost > 0.0F && highLost > lowLost, "lowLost=" + lowLost + " highLost=" + highLost);
	}

	@GameTest
	public void volatilePlus15ChainsOnlyOneExplosion(final GameTestHelper helper) {
		UniqueContext low = context(helper, "volatile_hood", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "volatile_hood", Uniques.PLUS_15);
		LivingEntity lowTarget = foe(helper, 1.5, 0.5);
		LivingEntity highTarget = foe(helper, 1.5, 9.5);
		LivingEntity lowFirst = foe(helper, 4.5, 0.5);
		LivingEntity highFirst = foe(helper, 4.5, 9.5);
		LivingEntity lowThird = foe(helper, 8.5, 0.5);
		LivingEntity highThird = foe(helper, 8.5, 9.5);
		LivingEntity highFourth = foe(helper, 8.5, 5.5);
		lowFirst.setHealth(1.0F);
		highFirst.setHealth(1.0F);
		highThird.setHealth(1.0F);
		hit(helper, "volatile_hood", low, lowTarget, true);
		hit(helper, "volatile_hood", high, highTarget, true);
		check(helper, "volatileTier", lowThird.getHealth() == lowThird.getMaxHealth() && highThird.getHealth() < 1.0F
			&& highFourth.getHealth() == highFourth.getMaxHealth(),
			"lowThird=" + lowThird.getHealth() + " highThird=" + highThird.getHealth() + " highFourth=" + highFourth.getHealth());
	}

	@GameTest
	public void splitGelPlus15SplitsTwice(final GameTestHelper helper) {
		UniqueContext low = context(helper, "split_gel_boots", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "split_gel_boots", Uniques.PLUS_15);
		force(low, "split_gel_boots");
		force(high, "split_gel_boots");
		LivingEntity lowTarget = foe(helper, 1.5, 0.5);
		LivingEntity highTarget = foe(helper, 1.5, 9.5);
		LivingEntity lowFirst = foe(helper, 3.5, 0.5);
		LivingEntity highFirst = foe(helper, 3.5, 9.5);
		LivingEntity lowSecond = foe(helper, 8.5, 0.5);
		LivingEntity highSecond = foe(helper, 8.5, 9.5);
		LivingEntity highThird = foe(helper, 8.5, 4.5);
		hit(helper, "split_gel_boots", low, lowTarget, false);
		hit(helper, "split_gel_boots", high, highTarget, false);
		check(helper, "splitGelTier", lowFirst.getHealth() < lowFirst.getMaxHealth() && highFirst.getHealth() < highFirst.getMaxHealth()
			&& lowSecond.getHealth() == lowSecond.getMaxHealth() && highSecond.getHealth() < highSecond.getMaxHealth()
			&& highThird.getHealth() == highThird.getMaxHealth(),
			"lowSecond=" + lowSecond.getHealth() + " highSecond=" + highSecond.getHealth() + " highThird=" + highThird.getHealth());
	}

	@GameTest
	public void endflamePlus15BonusesAHitInsideAField(final GameTestHelper helper) {
		UniqueContext low = context(helper, "endflame_robe", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "endflame_robe", Uniques.PLUS_15);
		force(high, "endflame_robe");
		LivingEntity target = foe(helper, 2.5);
		hit(helper, "endflame_robe", high, target, false);
		float a = damage(helper, "endflame_robe", low, target);
		float b = damage(helper, "endflame_robe", high, target);
		check(helper, "endflameTier", a == HIT && b > a, "low=" + a + " high=" + b);
	}

	@GameTest(maxTicks = 100)
	public void echoPlus15HitsNearbyFoesOnEcho(final GameTestHelper helper) {
		UniqueContext low = context(helper, "echo_circlet", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "echo_circlet", Uniques.PLUS_15);
		LivingEntity lowTarget = foe(helper, 1.5, 2.5);
		LivingEntity highTarget = foe(helper, 1.5, 8.5);
		LivingEntity lowNeighbor = foe(helper, 2.5, 2.5);
		LivingEntity highNeighbor = foe(helper, 2.5, 8.5);
		hit(helper, "echo_circlet", low, lowTarget, false);
		hit(helper, "echo_circlet", high, highTarget, false);
		helper.runAfterDelay(25, () -> check(helper, "echoTier",
			lowNeighbor.getHealth() == lowNeighbor.getMaxHealth() && highNeighbor.getHealth() < highNeighbor.getMaxHealth(),
			"lowNeighbor=" + lowNeighbor.getHealth() + " highNeighbor=" + highNeighbor.getHealth()));
	}

	@GameTest
	public void aWornBlazingMantleIgnitesAnActualSpellHit(final GameTestHelper helper) {
		UniqueDef def = Uniques.byId("blazing_mantle");
		helper.assertTrue(def != null, "missing blazing_mantle");
		ServerPlayer holder = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		holder.setItemSlot(EquipmentSlot.CHEST, UniqueTestKit.stack(def, 0));
		LivingEntity target = foe(helper, 2.5);
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().indirectMagic(holder, holder), HIT);
		check(helper, "blazingConsumer", target.isOnFire(), "fire=" + target.getRemainingFireTicks());
	}

	@GameTest
	public void wornMagmaGreavesSplashAnActualSpellHit(final GameTestHelper helper) {
		UniqueDef def = Uniques.byId("magma_greaves");
		helper.assertTrue(def != null, "missing magma_greaves");
		ServerPlayer holder = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		holder.setItemSlot(EquipmentSlot.LEGS, UniqueTestKit.stack(def, 0));
		LivingEntity target = foe(helper, 2.5);
		LivingEntity neighbor = foe(helper, 3.5);
		float before = neighbor.getHealth();
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().indirectMagic(holder, holder), HIT);
		check(helper, "magmaConsumer", neighbor.getHealth() < before && target.getHealth() < target.getMaxHealth(),
			"neighborLost=" + (before - neighbor.getHealth()) + " targetLost=" + (target.getMaxHealth() - target.getHealth()));
	}

	@GameTest
	public void voidstepBaseTeleportsAfterAHit(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, "voidstep_boots", 0, new Vec3(5.5, 2.0, 5.5));
		try {
			force(c, "voidstep_boots");
			Vec3 before = c.holder().position();
			float taken = Uniques.byId("voidstep_boots").effect().incoming(c, helper.getLevel().damageSources().generic(), HIT);
			double moved = before.distanceToSqr(c.holder().position());
			check(helper, "voidstepBase", moved > 0.0 && moved <= 64.0 && taken == HIT, "movedSquared=" + moved + " taken=" + taken);
		} finally {
			leave(c);
		}
	}

	@GameTest
	public void voidstepPlus15BonusesOnlyTheNextSpell(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, "voidstep_boots", Uniques.PLUS_15 - 1, new Vec3(3.5, 2.0, 5.5));
		UniqueContext high = connectedContext(helper, "voidstep_boots", Uniques.PLUS_15, new Vec3(7.5, 2.0, 5.5));
		try {
			force(low, "voidstep_boots");
			force(high, "voidstep_boots");
			LivingEntity target = foe(helper, 2.5);
			Uniques.byId("voidstep_boots").effect().incoming(low, helper.getLevel().damageSources().generic(), HIT);
			Uniques.byId("voidstep_boots").effect().incoming(high, helper.getLevel().damageSources().generic(), HIT);
			float a = damage(helper, "voidstep_boots", low, target);
			float b = damage(helper, "voidstep_boots", high, target);
			float again = damage(helper, "voidstep_boots", high, target);
			check(helper, "voidstepTier", a == HIT && b > a && again == HIT, "low=" + a + " high=" + b + " next=" + again);
		} finally {
			leave(low);
			leave(high);
		}
	}

	@GameTest
	public void mirrorBaseIgnoresAPassingHit(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, "mirror_mask", 0, new Vec3(5.5, 2.0, 5.5));
		try {
			force(c, "mirror_mask");
			float taken = Uniques.byId("mirror_mask").effect().incoming(c, helper.getLevel().damageSources().generic(), HIT);
			check(helper, "mirrorBase", taken == 0.0F, "taken=" + taken);
		} finally {
			leave(c);
		}
	}

	@GameTest
	public void aWornMirrorMaskIgnoresAnActualIncomingHit(final GameTestHelper helper) {
		UniqueDef def = Uniques.byId("mirror_mask");
		helper.assertTrue(def != null, "missing mirror_mask");
		UniqueContext c = connectedContext(helper, "mirror_mask", 0, new Vec3(5.5, 2.0, 5.5));
		try {
			force(c, "mirror_mask");
			c.holder().setItemSlot(EquipmentSlot.HEAD, UniqueTestKit.stack(def, 0));
			LivingEntity attacker = foe(helper, 2.5);
			float before = c.holder().getHealth();
			c.holder().hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
			check(helper, "mirrorConsumer", c.holder().getHealth() == before, "health=" + c.holder().getHealth() + " before=" + before);
		} finally {
			leave(c);
		}
	}

	@GameTest
	public void mirrorPlus15BlindsOnlyAfterAnIgnoredHit(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, "mirror_mask", Uniques.PLUS_15 - 1, new Vec3(3.5, 2.0, 5.5));
		UniqueContext high = connectedContext(helper, "mirror_mask", Uniques.PLUS_15, new Vec3(7.5, 2.0, 5.5));
		try {
			force(low, "mirror_mask");
			force(high, "mirror_mask");
			LivingEntity lowAttacker = foe(helper, 2.5);
			LivingEntity highAttacker = foe(helper, 8.5);
			float lowTaken = Uniques.byId("mirror_mask").effect().incoming(low, helper.getLevel().damageSources().mobAttack(lowAttacker), HIT);
			float highTaken = Uniques.byId("mirror_mask").effect().incoming(high, helper.getLevel().damageSources().mobAttack(highAttacker), HIT);
			check(helper, "mirrorTier", lowTaken == 0.0F && highTaken == 0.0F
				&& !lowAttacker.hasEffect(MobEffects.BLINDNESS) && highAttacker.hasEffect(MobEffects.BLINDNESS),
				"lowTaken=" + lowTaken + " highTaken=" + highTaken + " lowBlind=" + lowAttacker.hasEffect(MobEffects.BLINDNESS)
					+ " highBlind=" + highAttacker.hasEffect(MobEffects.BLINDNESS));
		} finally {
			leave(low);
			leave(high);
		}
	}

	@GameTest
	public void ectoplasmBaseLetsAHitPassThrough(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, "ectoplasm_veil", 0, new Vec3(5.5, 2.0, 5.5));
		try {
			force(c, "ectoplasm_veil");
			float taken = Uniques.byId("ectoplasm_veil").effect().incoming(c, helper.getLevel().damageSources().generic(), HIT);
			check(helper, "ectoplasmBase", taken == 0.0F, "taken=" + taken);
		} finally {
			leave(c);
		}
	}

	@GameTest
	public void ectoplasmPlus15GrantsSpeedAndRegenerationAfterAPass(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, "ectoplasm_veil", Uniques.PLUS_15 - 1, new Vec3(3.5, 2.0, 5.5));
		UniqueContext high = connectedContext(helper, "ectoplasm_veil", Uniques.PLUS_15, new Vec3(7.5, 2.0, 5.5));
		try {
			force(low, "ectoplasm_veil");
			force(high, "ectoplasm_veil");
			Uniques.byId("ectoplasm_veil").effect().incoming(low, helper.getLevel().damageSources().generic(), HIT);
			Uniques.byId("ectoplasm_veil").effect().incoming(high, helper.getLevel().damageSources().generic(), HIT);
			boolean lowBuff = low.holder().hasEffect(MobEffects.SPEED) || low.holder().hasEffect(MobEffects.REGENERATION);
			boolean highBuff = high.holder().hasEffect(MobEffects.SPEED) && high.holder().hasEffect(MobEffects.REGENERATION);
			check(helper, "ectoplasmTier", !lowBuff && highBuff, "lowBuff=" + lowBuff + " highBuff=" + highBuff);
		} finally {
			leave(low);
			leave(high);
		}
	}

	@GameTest(maxTicks = 100)
	public void prismarineBaseBonusesSpellsAgainstWetTargets(final GameTestHelper helper) {
		UniqueContext c = context(helper, "prismarine_leggings", 0);
		helper.setBlock(new BlockPos(2, 1, 5), Blocks.WATER);
		LivingEntity wet = foe(helper, 2.5);
		LivingEntity dry = foe(helper, 6.5);
		helper.runAfterDelay(4, () -> {
			float a = damage(helper, "prismarine_leggings", c, dry);
			float b = damage(helper, "prismarine_leggings", c, wet);
			check(helper, "prismarineBase", wet.isInWaterOrRain() && a == HIT && b > a,
				"wet=" + wet.isInWaterOrRain() + " dry=" + a + " water=" + b);
		});
	}

	@GameTest
	public void prismarinePlus15GivesMiningFatigue(final GameTestHelper helper) {
		UniqueContext low = context(helper, "prismarine_leggings", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "prismarine_leggings", Uniques.PLUS_15);
		LivingEntity lowTarget = foe(helper, 2.5);
		LivingEntity highTarget = foe(helper, 7.5);
		hit(helper, "prismarine_leggings", low, lowTarget, false);
		hit(helper, "prismarine_leggings", high, highTarget, false);
		MobEffectInstance fatigue = highTarget.getEffect(MobEffects.MINING_FATIGUE);
		check(helper, "prismarineTier", lowTarget.getEffect(MobEffects.MINING_FATIGUE) == null && fatigue != null && fatigue.getAmplifier() == 2,
			"lowFatigue=" + lowTarget.getEffect(MobEffects.MINING_FATIGUE) + " highFatigue=" + fatigue);
	}

	@GameTest
	public void sovereignCrownBaseBurnsAndBonusesBurningTarget(final GameTestHelper helper) {
		UniqueContext c = context(helper, "sovereign_crown", 0);
		LivingEntity target = foe(helper, 2.5);
		float before = damage(helper, "sovereign_crown", c, target);
		hit(helper, "sovereign_crown", c, target, false);
		float burning = damage(helper, "sovereign_crown", c, target);
		check(helper, "sovereignBase", target.isOnFire() && before == HIT && burning > before,
			"fire=" + target.getRemainingFireTicks() + " before=" + before + " burning=" + burning);
	}

	@GameTest
	public void sovereignCrownPlus15BurstsOnBurningSpellKill(final GameTestHelper helper) {
		UniqueContext low = context(helper, "sovereign_crown", Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, "sovereign_crown", Uniques.PLUS_15);
		LivingEntity lowTarget = foe(helper, 2.5, 2.5);
		LivingEntity highTarget = foe(helper, 2.5, 8.5);
		LivingEntity lowNeighbor = foe(helper, 3.5, 2.5);
		LivingEntity highNeighbor = foe(helper, 3.5, 8.5);
		lowTarget.setRemainingFireTicks(100);
		highTarget.setRemainingFireTicks(100);
		hit(helper, "sovereign_crown", low, lowTarget, true);
		hit(helper, "sovereign_crown", high, highTarget, true);
		check(helper, "sovereignTier", lowNeighbor.getHealth() == lowNeighbor.getMaxHealth() && highNeighbor.getHealth() < highNeighbor.getMaxHealth(),
			"lowNeighbor=" + lowNeighbor.getHealth() + " highNeighbor=" + highNeighbor.getHealth());
	}

	@GameTest
	public void tidecallerPulseCleansesOnlyAtPlus15(final GameTestHelper helper) {
		UniqueContext below = connectedContext(helper, TidecallerVestments.ID, Uniques.PLUS_15 - 1, new Vec3(1.5, 2.0, 1.5));
		below.holder().addEffect(new MobEffectInstance(MobEffects.POISON, 100));
		TidecallerVestments.pulse(below);
		// Sampled before the higher pulse, which reaches this holder as an ally.
		boolean belowStays = below.holder().hasEffect(MobEffects.POISON);
		UniqueContext at = connectedContext(helper, TidecallerVestments.ID, Uniques.PLUS_15, new Vec3(5.5, 2.0, 1.5));
		at.holder().addEffect(new MobEffectInstance(MobEffects.POISON, 100));
		TidecallerVestments.pulse(at);
		boolean atCleared = !at.holder().hasEffect(MobEffects.POISON);
		check(helper, "tidecallerPulse", belowStays && atCleared, "belowStays=" + belowStays + " atCleared=" + atCleared);
	}

	@GameTest
	public void clericsMitreMitigatesUndeadBlows(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, ClericsMitre.ID, 0, new Vec3(1.5, 2.0, 1.5));
		LivingEntity undead = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(3.5, 2.0, 1.5));
		LivingEntity living = helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new Vec3(5.5, 2.0, 1.5));
		float fromUndead = Uniques.byId(ClericsMitre.ID).effect().incoming(c, helper.getLevel().damageSources().mobAttack(undead), 10.0F);
		float fromLiving = Uniques.byId(ClericsMitre.ID).effect().incoming(c, helper.getLevel().damageSources().mobAttack(living), 10.0F);
		float expected = 10.0F * (1.0F - (float) ClericsMitre.TUNING.reduce());
		check(helper, "clericsMitre", Math.abs(fromUndead - expected) < 0.001 && Math.abs(fromLiving - 10.0F) < 0.001,
			"undead=" + fromUndead + " living=" + fromLiving + " expected=" + expected);
	}

	@GameTest(maxTicks = 200)
	public void heartwoodBarkResistsAfterStandingStill(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, HeartwoodBark.ID, 0, new Vec3(1.5, 3.0, 1.5));
		helper.runAfterDelay(10, () -> Uniques.byId(HeartwoodBark.ID).effect().everySecond(c));
		helper.runAfterDelay(10 + HeartwoodBark.TUNING.baseTicks() + 5, () -> {
			Uniques.byId(HeartwoodBark.ID).effect().everySecond(c);
			check(helper, "heartwoodStill", c.holder().hasEffect(MobEffects.RESISTANCE),
				"still resistance: " + c.holder().getEffect(MobEffects.RESISTANCE));
		});
	}

	@GameTest
	public void gelMantleShieldsALowAlly(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, GelMantle.ID, 0, new Vec3(1.5, 2.0, 1.5));
		c.holder().setHealth(c.holder().getMaxHealth() * 0.2F);
		Uniques.byId(GelMantle.ID).effect().everySecond(c);
		float expected = (float) (c.holder().getMaxHealth() * GelMantle.TUNING.share());
		check(helper, "gelMantle", Math.abs(c.holder().getAbsorptionAmount() - expected) < 0.001,
			"absorption=" + c.holder().getAbsorptionAmount() + " expected=" + expected);
	}

	@GameTest
	public void spectralVestmentsVeilALowHolder(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, SpectralVestments.ID, 0, new Vec3(1.5, 2.0, 1.5));
		c.holder().setHealth(c.holder().getMaxHealth() * 0.1F);
		Uniques.byId(SpectralVestments.ID).effect().afterIncoming(c, helper.getLevel().damageSources().generic(), 5.0F);
		check(helper, "spectralVeil", c.holder().hasEffect(MobEffects.INVISIBILITY) && c.holder().hasEffect(MobEffects.RESISTANCE),
			"invisibility=" + c.holder().hasEffect(MobEffects.INVISIBILITY) + " resistance=" + c.holder().hasEffect(MobEffects.RESISTANCE));
	}

	@GameTest
	public void conduitMantleGivesConduitPower(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, ConduitMantle.ID, 0, new Vec3(1.5, 2.0, 1.5));
		Uniques.byId(ConduitMantle.ID).effect().everySecond(c);
		check(helper, "conduitPower", c.holder().hasEffect(MobEffects.CONDUIT_POWER),
			"conduit power: " + c.holder().getEffect(MobEffects.CONDUIT_POWER));
	}

	@GameTest
	public void revenantPulseSlowsOnlyTheUndead(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, RevenantGreaves.ID, 0, new Vec3(1.5, 2.0, 1.5));
		LivingEntity undead = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(4.5, 2.0, 1.5));
		LivingEntity living = helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new Vec3(7.5, 2.0, 1.5));
		RevenantGreaves.pulse(c);
		check(helper, "revenantPulse", undead.hasEffect(MobEffects.SLOWNESS) && !living.hasEffect(MobEffects.SLOWNESS),
			"undead=" + undead.getEffect(MobEffects.SLOWNESS) + " living=" + living.hasEffect(MobEffects.SLOWNESS));
	}

	@GameTest
	public void ravagersBenedictionHealsOnARealHit(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, RavagersBenediction.ID, 0, new Vec3(1.5, 2.0, 1.5));
		c.holder().setHealth(c.holder().getMaxHealth() * 0.5F);
		float before = c.holder().getHealth();
		LivingEntity target = foe(helper, 3.5, 1.5);
		Uniques.byId(RavagersBenediction.ID).effect().afterMelee(c, target,
			helper.getLevel().damageSources().playerAttack(c.holder()), 10.0F, false, false);
		float healed = c.holder().getHealth() - before;
		float expected = 10.0F * (float) RavagersBenediction.TUNING.healShare();
		check(helper, "ravagersHeal", Math.abs(healed - expected) < 0.001, "healed=" + healed + " expected=" + expected);
	}

	@GameTest
	public void boarhideVestHealsItsHolderOnAKill(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, BoarhideVest.ID, 0, new Vec3(1.5, 2.0, 1.5));
		c.holder().setHealth(c.holder().getMaxHealth() * 0.5F);
		float before = c.holder().getHealth();
		LivingEntity victim = foe(helper, 3.5, 1.5);
		Uniques.byId(BoarhideVest.ID).effect().onKill(c, victim, helper.getLevel().damageSources().playerAttack(c.holder()));
		float healed = c.holder().getHealth() - before;
		float expected = c.holder().getMaxHealth() * (float) BoarhideVest.TUNING.healShare();
		check(helper, "boarhideKill", Math.abs(healed - expected) < 0.001, "healed=" + healed + " expected=" + expected);
	}

	@GameTest
	public void threeSkullReliquaryWithersItsTarget(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, ThreeSkullReliquary.ID, 0, new Vec3(1.5, 2.0, 1.5));
		LivingEntity target = foe(helper, 3.5, 1.5);
		Uniques.byId(ThreeSkullReliquary.ID).effect().afterMelee(c, target,
			helper.getLevel().damageSources().playerAttack(c.holder()), 10.0F, false, false);
		MobEffectInstance wither = target.getEffect(MobEffects.WITHER);
		check(helper, "threeSkullWither", wither != null && wither.getDuration() == ThreeSkullReliquary.TUNING.witherTicks()
			&& wither.getAmplifier() == ThreeSkullReliquary.TUNING.witherAmplifier(), "wither=" + wither);
	}

	@GameTest
	public void milkgelHoodClearsOneHarmfulEffect(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, MilkgelHood.ID, 0, new Vec3(1.5, 2.0, 1.5));
		c.holder().addEffect(new MobEffectInstance(MobEffects.POISON, 100));
		Uniques.byId(MilkgelHood.ID).effect().everySecond(c);
		check(helper, "milkgelCleanse", !c.holder().hasEffect(MobEffects.POISON),
			"poison after the hood: " + c.holder().hasEffect(MobEffects.POISON));
	}
}
