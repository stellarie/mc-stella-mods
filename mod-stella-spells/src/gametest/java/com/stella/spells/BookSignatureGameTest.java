package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The +15 signature of a book. Four books are cast for real and their signature is read in the world. Then every
 * one of the 83 runs its signature in one arena, and a snapshot of the arena must differ afterwards.
 */
public class BookSignatureGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.booksignature");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final double SLACK = 0.05;
	private static final int ECHO_WAIT_TICKS = 50;

	@GameTest(maxTicks = 40)
	public void sparkShotAtFifteenArcsToASecondFoeForHalfTheHitAndBurnsIt(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob target = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 3.0);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X + 3.0, CASTER_Z + 3.0);
		Mob far = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 10.0);
		SpellCasting.CastResult result = BookRefineGameTest.cast(caster, BookRefineGameTest.refined("spark_shot", 15));
		float targetLost = 1000.0F - target.getHealth();
		float nearLost = 1000.0F - near.getHealth();
		LOG.info("[BEHAVIOR] test=sparkShotAtFifteenArcsToASecondFoeForHalfTheHitAndBurnsIt RESULT result={} targetLost={} nearLost={} nearFire={} farLost={}",
			result, targetLost, nearLost, near.getRemainingFireTicks(), 1000.0F - far.getHealth());
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "the cast read " + result);
		helper.assertTrue(targetLost > 0.0F && Math.abs(nearLost / targetLost - 0.5) < SLACK, "the arc hit for " + nearLost + " against " + targetLost);
		helper.assertTrue(near.getRemainingFireTicks() >= 60, "the arced foe burns for " + near.getRemainingFireTicks() + " ticks");
		helper.assertTrue(far.getHealth() == 1000.0F, "a foe past the arc took damage");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void mendingWordAtFifteenHealsAgainForHalfTwoSecondsLater(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.setHealth(100.0F);
		BookRefineGameTest.cast(caster, BookRefineGameTest.refined("mending_word", 15));
		float first = caster.getHealth() - 100.0F;
		helper.runAfterDelay(ECHO_WAIT_TICKS, () -> {
			float second = caster.getHealth() - 100.0F - first;
			LOG.info("[BEHAVIOR] test=mendingWordAtFifteenHealsAgainForHalfTwoSecondsLater RESULT first={} second={}", first, second);
			helper.assertTrue(first > 0.0F, "the cast healed nothing at once");
			helper.assertTrue(Math.abs(second / first - 0.5) < SLACK, "the echo healed " + second + " after a first heal of " + first);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void warCryAtFifteenRalliesAnAllyWithStrengthAndAbsorption(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		IronGolem ally = golem(helper, CASTER_X + 3.0, CASTER_Z + 1.0);
		SpellCasting.CastResult result = BookRefineGameTest.cast(caster, BookRefineGameTest.refined("war_cry", 15));
		MobEffectInstance guard = caster.getEffect(SpellStatus.REFINED_GUARD);
		LOG.info("[BEHAVIOR] test=warCryAtFifteenRalliesAnAllyWithStrengthAndAbsorption RESULT result={} allyEffects={} casterEffects={} guard={}",
			result, ally.getActiveEffects(), caster.getActiveEffects(), guard);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "the cast read " + result);
		helper.assertTrue(ally.hasEffect(MobEffects.STRENGTH) && ally.hasEffect(MobEffects.ABSORPTION), "the ally was not rallied: " + ally.getActiveEffects());
		helper.assertTrue(caster.hasEffect(MobEffects.STRENGTH) && caster.hasEffect(MobEffects.ABSORPTION), "the caster was not rallied");
		helper.assertTrue(guard != null && guard.getAmplifier() == BookRefine.MAX_LEVEL - 1, "the +15 guard reads " + guard);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void holyLightAtFifteenOverflowsAThirdOfTheHealToAnAllyNearby(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		IronGolem target = golem(helper, CASTER_X, CASTER_Z + 3.0);
		IronGolem near = golem(helper, CASTER_X + 3.0, CASTER_Z + 3.0);
		target.setHealth(50.0F);
		near.setHealth(50.0F);
		SpellCasting.CastResult result = BookRefineGameTest.cast(caster, BookRefineGameTest.refined("holy_light", 15));
		float healed = target.getHealth() - 50.0F;
		float overflow = near.getHealth() - 50.0F;
		LOG.info("[BEHAVIOR] test=holyLightAtFifteenOverflowsAThirdOfTheHealToAnAllyNearby RESULT result={} healed={} overflow={}", result, healed, overflow);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "the cast read " + result);
		helper.assertTrue(healed > 0.0F && Math.abs(overflow / healed - 1.0 / 3.0) < SLACK, "the nearby ally healed " + overflow + " after " + healed);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void everyBookSignatureChangesTheArenaWhenItRuns(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		ServerLevel level = helper.getLevel();
		List<String> unchanged = new ArrayList<>();
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			String path = definition.id().getPath();
			// A zombie shrugs off Poison, so the second foe is a creeper; the target sits low for Coup de Grace.
			Mob target = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 3.0);
			target.setHealth(250.0F);
			Mob second = creeper(helper, CASTER_X + 1.5, CASTER_Z + 3.5);
			Mob third = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 6.0);
			IronGolem ally = golem(helper, CASTER_X + 3.0, CASTER_Z + 1.0);
			ally.setHealth(50.0F);
			IronGolem neighbour = golem(helper, CASTER_X + 5.0, CASTER_Z + 1.0);
			neighbour.setHealth(50.0F);
			List<Mob> foes = List.of(target, second, third);
			List<Float> healths = foes.stream().map(Mob::getHealth).toList();
			caster.removeAllEffects();
			caster.setHealth(500.0F);
			caster.setPos(helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z)));
			// A landing book turns the caster to its target, so every book starts facing the foes again.
			caster.setYRot(0.0F);
			caster.setXRot(0.0F);
			caster.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
			ItemStack book = BookRefineGameTest.refined(path, BookRefine.MAX_LEVEL);
			caster.setItemInHand(InteractionHand.MAIN_HAND, book);
			if (definition.school() == School.ARCHER) {
				SkillSchoolGameTest.supplyArrowsIfGripped(caster);
			}
			SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(book, ItemStack.EMPTY, ItemStack.EMPTY);
			Vec3 departure = caster.position();
			Mob aimed = definition.targetMode() == TargetMode.HOSTILE ? target : null;
			Vec3 point = switch (definition.targetMode()) {
				case BLOCK -> target.position();
				case SAFE_TELEPORT -> departure.add(3.0, 0.0, 0.0);
				default -> departure;
			};
			LivingEntity helped = definition.targetMode() == TargetMode.ALLY ? ally : null;
			CastContext.setAlly(helped);
			try {
				definition.effect().apply(caster, aimed, point, modifiers);
			} finally {
				CastContext.setAlly(null);
			}
			List<Mob> victims = new ArrayList<>();
			for (int index = 0; index < foes.size(); index++) {
				if (foes.get(index).getHealth() < healths.get(index)) {
					victims.add(foes.get(index));
				}
			}
			String before = snapshot(level, caster);
			RefineCast cast = new RefineCast(caster, definition, aimed, definition.targetMode() == TargetMode.HOSTILE ? null : point, helped, victims, departure, modifiers);
			boolean ran = BookRefine.runSignature(cast);
			BookRefine.flushLater(level.getServer());
			String after = snapshot(level, caster);
			if (!ran || before.equals(after)) {
				unchanged.add(path + (ran ? "" : " (no signature)"));
			}
			discardAround(level, caster);
		}
		// The caster leaves, so no aura or zone a staged cast started keeps ticking beside the other cases.
		level.getServer().getPlayerList().remove(caster);
		LOG.info("[BEHAVIOR] test=everyBookSignatureChangesTheArenaWhenItRuns RESULT books={} unchanged={}", SpellbookCatalog.DEFINITIONS.size(), unchanged);
		helper.assertTrue(unchanged.isEmpty(), "these signatures left the arena as it was: " + unchanged);
		helper.succeed();
	}

	/** A foe that is neither undead nor immune to Poison, held in place like the zombies. */
	private static Mob creeper(final GameTestHelper helper, final double x, final double z) {
		Mob mob = EntityTypes.CREEPER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a creeper");
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		mob.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		mob.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static IronGolem golem(final GameTestHelper helper, final double x, final double z) {
		IronGolem golem = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(golem != null, "the level refused a golem");
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		golem.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		golem.setNoAi(true);
		golem.setNoGravity(true);
		golem.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		helper.getLevel().addFreshEntity(golem);
		return golem;
	}

	private static void discardAround(final ServerLevel level, final ServerPlayer caster) {
		for (LivingEntity body : bodies(level, caster)) {
			if (body != caster) {
				body.discard();
			}
		}
		ArcherSkills.clearFor(caster);
	}

	private static List<LivingEntity> bodies(final ServerLevel level, final ServerPlayer caster) {
		AABB box = caster.getBoundingBox().inflate(40.0);
		List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive));
		found.sort(Comparator.comparing(body -> body.getUUID().toString()));
		return found;
	}

	/** Everything a signature can move on a body near the caster, and the caster's arrows and armed shot. */
	private static String snapshot(final ServerLevel level, final ServerPlayer caster) {
		StringBuilder out = new StringBuilder();
		for (LivingEntity body : bodies(level, caster)) {
			out.append(BuiltInRegistries.ENTITY_TYPE.getKey(body.getType())).append(body == caster ? "*" : "")
				.append(" hp=").append(body.getHealth()).append(" abs=").append(body.getAbsorptionAmount())
				.append(" fire=").append(body.getRemainingFireTicks()).append(" frozen=").append(body.getTicksFrozen())
				.append(" at=").append(Math.round(body.getX() * 100)).append(',').append(Math.round(body.getY() * 100)).append(',').append(Math.round(body.getZ() * 100))
				.append(" v=").append(Math.round(body.getDeltaMovement().x * 100)).append(',').append(Math.round(body.getDeltaMovement().y * 100))
				.append(',').append(Math.round(body.getDeltaMovement().z * 100)).append(" fx=");
			body.getActiveEffects().stream()
				.sorted(Comparator.comparing((MobEffectInstance effect) -> effect.getEffect().getRegisteredName()))
				.forEach(effect -> out.append(effect.getEffect().getRegisteredName()).append(':').append(effect.getAmplifier()).append(':').append(effect.getDuration()).append(';'));
			out.append('\n');
		}
		int arrows = 0;
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			arrows += stack.is(ItemTags.ARROWS) ? stack.getCount() : 0;
		}
		out.append("arrows=").append(arrows).append(" shotOwed=").append(ArcherSkills.shotOwed(caster))
			.append(" shotAwakened=").append(ArcherSkills.owedShotAwakened(caster));
		return out.toString();
	}
}
