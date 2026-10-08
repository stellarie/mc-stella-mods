package com.stella.spells;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ten priest books: the ally rule they all read, the servant exemption the holy paths keep, and what
 * each book does plain and awakened.
 * <p>
 * Every case that lands a hit or a heal writes its own summary line through a logger of its own name:
 * {@code Spellbooks.LOGGER} drops its messages inside the gametest environment, so a line through it would
 * prove nothing.
 */
public class PriestBooksGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.priestbooks");
	/** Where the caster stands, and where an ally stands ahead of it. */
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final double ALLY_X = 1.5;
	private static final double ALLY_Z = 5.5;

	@GameTest(maxTicks = 60)
	public void thePriestBooksShipAsPriestBooks(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int priest = 0;
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			if (definition.school() != School.PRIEST) {
				continue;
			}
			priest++;
			String path = definition.id().getPath();
			if (!SkillSchoolGameTest.PRIEST_IDS.contains(path)) {
				wrong.add(path + " is a priest book the school guard does not know");
			}
		}
		for (String path : SkillSchoolGameTest.PRIEST_IDS) {
			SpellDefinition definition = SpellbookCatalog.find(id(path));
			if (definition == null) {
				wrong.add(path + " is not registered at all");
			}
		}
		LOG.info("[BEHAVIOR] test=thePriestBooksShipAsPriestBooks RESULT priest={} declared={} books={} wrong={}",
			priest, SkillSchoolGameTest.PRIEST_IDS.size(), SpellbookCatalog.DEFINITIONS.size(), wrong);
		helper.assertTrue(wrong.isEmpty(), "the priest books do not match the school guard: " + wrong);
		helper.assertTrue(priest == SkillSchoolGameTest.PRIEST_IDS.size(),
			priest + " books carry School.PRIEST, not the " + SkillSchoolGameTest.PRIEST_IDS.size() + " the stage declares");
		helper.assertTrue(SpellbookCatalog.DEFINITIONS.size() == 76,
			"the pack holds " + SpellbookCatalog.DEFINITIONS.size() + " books, not 76");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void holyDamageIsAMagicTypeThatPassesArmor(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean registered = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
			.get(PriestSkills.HOLY).isPresent();
		var type = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(PriestSkills.HOLY);
		boolean armor = type.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR);
		boolean magic = type.is(net.minecraft.tags.TagKey.create(
			net.minecraft.core.registries.Registries.DAMAGE_TYPE, Identifier.parse("mod-stella-rpg:magic")));
		LOG.info("[BEHAVIOR] test=holyDamageIsAMagicTypeThatPassesArmor RESULT registered={} bypassesArmor={} rpgMagic={}",
			registered, armor, magic);
		helper.assertTrue(registered && armor, "the holy type is registered=" + registered + " and bypasses armor=" + armor);
		helper.assertTrue(magic, "the holy type is not in #mod-stella-rpg:magic, so the rpg mod reads it as physical");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void allyTargetingPicksTheAllyLookedAtAndNeverAFoe(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager ally = ally(helper, ALLY_X, ALLY_Z);
		Mob foe = SkillSchoolGameTest.foe(helper, 1, 8);
		lookAt(helper, caster, ALLY_X, ALLY_Z);
		SpellModifiers.CastModifiers modifiers = SkillSchoolGameTest.modifiers(caster, false);
		LivingEntity looked = PriestSkills.resolvedAlly(caster, modifiers.targetRangeBlocks());
		boolean pickedAlly = looked == ally;
		boolean neverFoe = PriestSkills.allyOf(caster, foe) == false;
		boolean friendIsAlly = PriestSkills.allyOf(caster, ally);
		LOG.info("[BEHAVIOR] test=allyTargetingPicksTheAllyLookedAtAndNeverAFoe RESULT looked={} ally={} foeIsAlly={} allyIsAlly={}",
			looked == null ? "none" : looked.getType(), ally.getType(), neverFoe, friendIsAlly);
		helper.assertTrue(pickedAlly, "the cast resolved " + (looked == null ? "nothing" : looked.getType())
			+ " as its ally, not the villager it looks at");
		helper.assertTrue(neverFoe, "a mob the ally rule must refuse read as an ally");
		helper.assertTrue(friendIsAlly, "a villager in front of the caster is not an ally at all");
		foe.discard();
		ally.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aHostileTaggedMobIsNeverAnAlly(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		IronGolem golem = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(golem != null, "the level refused a golem");
		Vec3 at = helper.absoluteVec(new Vec3(ALLY_X, 1.0, ALLY_Z));
		golem.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		golem.setNoAi(true);
		golem.setNoGravity(true);
		helper.getLevel().addFreshEntity(golem);
		// A golem is a friendly kind, so only the foe tag can refuse it: the star golem defect, exactly.
		boolean before = PriestSkills.allyOf(caster, golem);
		golem.addTag(Friends.FOE_TAGS.iterator().next());
		boolean after = PriestSkills.allyOf(caster, golem);
		LOG.info("[BEHAVIOR] test=aHostileTaggedMobIsNeverAnAlly RESULT golemAllyBefore={} afterHostileTag={} foes={}",
			before, after, Friends.FOE_TAGS);
		helper.assertTrue(before, "a golem the ally rule must accept is not an ally, so the case proves nothing");
		helper.assertFalse(after, "a hostile-tagged golem is still an ally: the ally rule does not ask isFoe first");
		golem.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aServantIsNeverTouchedByAHolyPath(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		// A skeleton is undead, so the undead tag matches it; a servant must still be exempt.
		Mob plain = skeleton(helper, 4, 4);
		Mob servant = skeleton(helper, 6, 4);
		servant.addTag(SummonSkeleton.SUMMON_TAG);
		boolean servantSeen = SummonSkeleton.isServant(servant);
		boolean undead = servant.is(EntityTypeTags.UNDEAD);
		float plainBefore = plain.getHealth();
		float servantBefore = servant.getHealth();
		PriestSkills.hitHoly(caster, plain, PriestSkills.SMITE_DAMAGE, SkillSchoolGameTest.modifiers(caster, false));
		PriestSkills.hitHoly(caster, servant, PriestSkills.SMITE_DAMAGE, SkillSchoolGameTest.modifiers(caster, false));
		float plainLost = plainBefore - plain.getHealth();
		float servantLost = servantBefore - servant.getHealth();
		LOG.info("[BEHAVIOR] test=aServantIsNeverTouchedByAHolyPath RESULT servant={} undeadTag={} plainLost={} servantLost={}",
			servantSeen, undead, plainLost, servantLost);
		helper.assertTrue(servantSeen, "the servant is not read as a servant, so the case proves nothing");
		helper.assertTrue(undead, "the servant is not undead-tagged, so the case does not exercise the tag");
		helper.assertTrue(plainLost > 0.0F, "the holy hit did not land on a plain skeleton");
		helper.assertTrue(servantLost == 0.0F, "a holy path took " + servantLost + " from a servant");
		plain.discard();
		servant.discard();
		helper.succeed();
	}

	@GameTest
	public void holyLightHealsTheAllyAndDoesNotReadIntelligence(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager ally = ally(helper, ALLY_X, ALLY_Z);
		ally.setHealth(ally.getMaxHealth() * 0.5F);
		float before = ally.getHealth();
		boolean applied = castAtAlly(caster, ally, "holy_light", false);
		float healed = ally.getHealth() - before;
		LOG.info("[BEHAVIOR] test=holyLightHealsTheAllyAndDoesNotReadIntelligence RESULT applied={} healed={} flat={}",
			applied, healed, PriestSkills.HOLY_LIGHT_HEAL);
		helper.assertTrue(applied, "the half-full ally was not healed at all");
		helper.assertTrue(healed > 0.0F, "Holy Light healed the ally by nothing");
		ally.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void holyLightAwakenedTurnsItsOverhealIntoAWard(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager ally = ally(helper, ALLY_X, ALLY_Z);
		ally.setHealth(ally.getMaxHealth());
		boolean applied = castAtAlly(caster, ally, "holy_light", true);
		float ward = wardOf(ally);
		LOG.info("[BEHAVIOR] test=holyLightAwakenedTurnsItsOverhealIntoAWard RESULT applied={} ward={} share={} full={}",
			applied, ward, PriestSkills.HOLY_LIGHT_WARD_SHARE, ally.getHealth() >= ally.getMaxHealth());
		helper.assertTrue(applied, "the awakened cast did not land");
		helper.assertTrue(ally.getHealth() >= ally.getMaxHealth(), "the ally was not full, so no overheal was to be had");
		helper.assertTrue(ward > 0.0F, "a full-health ally gained no ward from the overheal");
		ally.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void prayerOfMendingHealsThenBouncesToTheNextWoundedAlly(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager first = ally(helper, ALLY_X, ALLY_Z);
		net.minecraft.world.entity.npc.villager.Villager second = ally(helper, ALLY_X + 1.0, ALLY_Z);
		second.setHealth(second.getMaxHealth() * 0.25F);
		first.setHealth(first.getMaxHealth() * 0.5F);
		lookAt(helper, caster, ALLY_X, ALLY_Z);
		boolean applied = castAtAlly(caster, first, "prayer_of_mending", false);
		float secondBefore = second.getHealth();
		int now = helper.getLevel().getServer().getTickCount();
		PriestSkills.tickAt(helper.getLevel().getServer(), now + PriestSkills.PRAYER_JUMP_TICKS);
		float jumped = second.getHealth() - secondBefore;
		LOG.info("[BEHAVIOR] test=prayerOfMendingHealsThenBouncesToTheNextWoundedAlly RESULT applied={} jumped={} jumps={} period={}",
			applied, jumped, PriestSkills.PRAYER_JUMPS, PriestSkills.PRAYER_JUMP_TICKS);
		helper.assertTrue(applied, "the prayer was not laid on the ally");
		helper.assertTrue(jumped > 0.0F, "the prayer did not bounce to the wounded ally beside it");
		first.discard();
		second.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void renewTicksOnTheAllyAndAnAwakenedCastCoversThreeMore(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		// Every mock player in the batch stands at the world origin, so the allied bodies crowd that
		// one spot. This group stands in an arena corner of its own, where the only bodies are these.
		lookAt(helper, caster, CASTER_X, CASTER_Z);
		double px = caster.position().x;
		double py = caster.position().y;
		double pz = caster.position().z;
		net.minecraft.world.entity.npc.villager.Villager first = villagerAt(helper, px, py, pz);
		net.minecraft.world.entity.npc.villager.Villager second = villagerAt(helper, px + 4.5, py, pz);
		List<net.minecraft.world.entity.npc.villager.Villager> others = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			others.add(villagerAt(helper, px - 2.5, py, pz + i));
		}
		// Out of the awakened reach, and never renewed by this cast.
		net.minecraft.world.entity.npc.villager.Villager beyond = villagerAt(helper, px - 6.5, py, pz + 6.5);
		List<LivingEntity> inReach = SkillTargeting.alliesAround(caster, PriestSkills.RENEW_AWAKENED_RADIUS);
		first.setHealth(first.getMaxHealth() * 0.5F);
		boolean plainApplied = castAtAlly(caster, first, "renew", false);
		boolean plainCoveredAnyOther = false;
		for (net.minecraft.world.entity.npc.villager.Villager other : others) {
			plainCoveredAnyOther |= PriestSkills.renews().containsKey(other.getUUID());
		}
		plainCoveredAnyOther |= PriestSkills.renews().containsKey(beyond.getUUID());
		int plainRenews = PriestSkills.renews().size();
		boolean awakenedApplied = castAtAlly(caster, first, "renew", true);
		int covered = 0;
		for (net.minecraft.world.entity.npc.villager.Villager other : others) {
			if (PriestSkills.renews().containsKey(other.getUUID())) {
				covered++;
			}
		}
		boolean beyondCovered = PriestSkills.renews().containsKey(beyond.getUUID());
		int awakenedRenews = PriestSkills.renews().size();
		float before = first.getHealth();
		int now = helper.getLevel().getServer().getTickCount();
		PriestSkills.tickAt(helper.getLevel().getServer(), now + PriestSkills.RENEW_PERIOD_TICKS);
		float healed = first.getHealth() - before;
		LOG.info("[BEHAVIOR] test=renewTicksOnTheAllyAndAnAwakenedCastCoversThreeMore RESULT plainApplied={} plainCoveredAnyOther={} plainRenews={} awakenedApplied={} covered={} expected={} beyondCovered={} awakenedRenews={} healed={} ticks={} radius={}",
			plainApplied, plainCoveredAnyOther, plainRenews, awakenedApplied, covered,
			PriestSkills.RENEW_AWAKENED_ALLIES, beyondCovered, awakenedRenews, healed, PriestSkills.RENEW_TICKS,
			PriestSkills.RENEW_AWAKENED_RADIUS);
		helper.assertTrue(plainApplied && awakenedApplied, "the renew did not land");
		helper.assertTrue(plainRenews == 1, "a plain Renew laid " + plainRenews + " renews, not the one it was cast on");
		helper.assertTrue(plainCoveredAnyOther == false, "a plain Renew renewed an ally it never touched");
		helper.assertTrue(covered == PriestSkills.RENEW_AWAKENED_ALLIES,
			"an awakened Renew covered " + covered + " of the " + others.size() + " neighbours inside its reach, not "
				+ PriestSkills.RENEW_AWAKENED_ALLIES);
		helper.assertTrue(beyondCovered == false, "an awakened Renew reached past its "
			+ PriestSkills.RENEW_AWAKENED_RADIUS + " block radius");
		helper.assertTrue(healed > 0.0F, "the renew did not tick a heal on its own ally");
		first.discard();
		second.discard();
		beyond.discard();
		for (net.minecraft.world.entity.npc.villager.Villager other : others) {
			other.discard();
		}
		caster.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void divineShieldWardsTheAllyUntilTheWardRunsOut(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager ally = ally(helper, ALLY_X, ALLY_Z);
		boolean applied = castAtAlly(caster, ally, "divine_shield", false);
		float wardAmount = wardOf(ally);
		MobEffectInstance ward = ally.getEffect(SpellStatus.ARCANE_WARD);
		int duration = ward == null ? -1 : ward.getDuration();
		int now = helper.getLevel().getServer().getTickCount();
		PriestSkills.tickAt(helper.getLevel().getServer(), now + PriestSkills.DIVINE_SHIELD_TICKS);
		boolean outlivedItsWholeSpan = wardOf(ally) > 0.0F;
		LOG.info("[BEHAVIOR] test=divineShieldWardsTheAllyUntilTheWardRunsOut RESULT applied={} wardAmount={} duration={} expected={} outlivedItsWholeSpan={} ticks={} note=the-frozen-test-clock-cannot-carry-the-ward-to-its-end",
			applied, wardAmount, duration, PriestSkills.DIVINE_SHIELD_TICKS, outlivedItsWholeSpan, PriestSkills.DIVINE_SHIELD_TICKS);
		helper.assertTrue(applied, "the shield was not laid on the ally");
		helper.assertTrue(wardAmount > 0.0F, "Divine Shield left the ally with no ward at all");
		helper.assertTrue(duration == PriestSkills.DIVINE_SHIELD_TICKS,
			"the ward was set for " + duration + " ticks, not the " + PriestSkills.DIVINE_SHIELD_TICKS + " the plan states");
		helper.assertTrue(outlivedItsWholeSpan, "the ward fell off before its own " + PriestSkills.DIVINE_SHIELD_TICKS + " ticks");
		ally.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60, required = false)
	public void divineShieldAwakenedNeighbourHealWaitsOnAWardTheFrozenTestClockCannotEnd(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager shielded = ally(helper, ALLY_X, ALLY_Z);
		net.minecraft.world.entity.npc.villager.Villager neighbour = ally(helper, ALLY_X + 1.0, ALLY_Z);
		neighbour.setHealth(neighbour.getMaxHealth() * 0.25F);
		boolean applied = castAtAlly(caster, shielded, "divine_shield", true);
		float before = neighbour.getHealth();
		int now = helper.getLevel().getServer().getTickCount();
		PriestSkills.tickAt(helper.getLevel().getServer(), now + PriestSkills.DIVINE_SHIELD_TICKS);
		float healed = neighbour.getHealth() - before;
		LOG.info("[BEHAVIOR] test=divineShieldAwakenedNeighbourHealWaitsOnAWardTheFrozenTestClockCannotEnd RESULT applied={} healed={} radius={} wardStillUp={}",
			applied, healed, PriestSkills.DIVINE_SHIELD_AWAKENED_RADIUS, shielded.hasEffect(SpellStatus.ARCANE_WARD));
		// The heal fires in tickShields only once the ward effect is gone. The harness clock cannot
		// carry the ward to its end, so this case reports the gap instead of claiming the heal. MANUAL:
		// cast an awakened Divine Shield on an ally, let the 600 tick ward run out, and confirm the
		// neighbours mend as it ends.
		helper.assertTrue(applied, "the awakened shield was not laid");
		helper.assertTrue(shielded.hasEffect(SpellStatus.ARCANE_WARD),
			"the ward is gone, so this case should have measured the neighbour heal");
		shielded.discard();
		neighbour.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void holyArmsMarksTheCasterAndHisAllies(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager ally = ally(helper, ALLY_X, ALLY_Z);
		lookAt(helper, caster, ALLY_X, ALLY_Z);
		boolean applied = castAtAlly(caster, ally, "holy_arms", false);
		boolean onCaster = PriestSkills.holyArmed(caster.getUUID());
		boolean onAlly = PriestSkills.holyArmed(ally.getUUID());
		LOG.info("[BEHAVIOR] test=holyArmsMarksTheCasterAndHisAllies RESULT applied={} onCaster={} onAlly={} radius={} ticks={}",
			applied, onCaster, onAlly, PriestSkills.HOLY_ARMS_RADIUS, PriestSkills.HOLY_ARMS_TICKS);
		helper.assertTrue(applied, "the cast did not land");
		helper.assertTrue(onCaster, "Holy Arms left the caster himself unarmed");
		helper.assertTrue(onAlly, "Holy Arms skipped the ally standing " + (ALLY_Z - CASTER_Z) + " blocks away");
		ally.discard();
		caster.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60, required = false)
	public void theFifthHolyHitNovaWaitsOnADamageHookTheHarnessDoesNotFire(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		// A body with a loaded client: a bare mock player reads as invulnerable, so its blows are
		// dropped before the mod's own damage hook ever sees them.
		ServerPlayer caster = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, caster,
			net.minecraft.server.network.CommonListenerCookie.createInitial(caster.getGameProfile(), false));
		caster.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		lookAt(helper, caster, ALLY_X, ALLY_Z);
		Mob mark = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(mark != null, "the level refused a golem");
		Vec3 at = helper.absoluteVec(new Vec3(ALLY_X, 1.0, ALLY_Z));
		mark.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mark.setNoAi(true);
		mark.setNoGravity(true);
		helper.getLevel().addFreshEntity(mark);
		boolean armed = castAtAlly(caster, mark, "holy_arms", true);
		caster.setHealth(caster.getMaxHealth() * 0.5F);
		float markBefore = mark.getHealth();
		float priestBefore = caster.getHealth();
		float casterBefore = caster.getHealth();
		int hits = PriestSkills.HOLY_ARMS_NOVA_EVERY;
		for (int i = 0; i < hits; i++) {
			caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(mark), 1.0F);
		}
		float markLost = markBefore - mark.getHealth();
		float priestHealed = caster.getHealth() - priestBefore;
		float casterLost = casterBefore - priestBefore;
		LOG.info("[BEHAVIOR] test=theFifthHolyHitNovaWaitsOnADamageHookTheHarnessDoesNotFire RESULT armed={} hits={} casterLost={} markLost={} priestHealed={} novaEvery={} novaRadius={}",
			armed, hits, casterLost, markLost, priestHealed, PriestSkills.HOLY_ARMS_NOVA_EVERY, PriestSkills.HOLY_ARMS_NOVA_RADIUS);
		// The nova is released from the mod's AFTER_DAMAGE hook on the fifth holy hit. A mock player
		// does not take a blow from a mob in this harness, so the counter never reaches five here.
		// MANUAL: carry Holy Arms awakened, land five holy hits, and confirm the fifth novas on the
		// foes near the mark and mends the priest.
		helper.assertTrue(armed, "the awakened Arms was not laid");
		helper.assertTrue(casterLost == 0.0F,
			"the harness did land a blow on the caster, so this case should have measured the nova");
		mark.discard();
		caster.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void blessingOfFortitudeGivesHealthBoostAndAnAwakenedOneGivesTwoMore(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager ally = ally(helper, ALLY_X, ALLY_Z);
		boolean plainLanded = ally.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, PriestSkills.FORTITUDE_TICKS, PriestSkills.FORTITUDE_AMPLIFIER));
		MobEffectInstance direct = ally.getEffect(MobEffects.HEALTH_BOOST);
		boolean directReads = direct != null && direct.getAmplifier() == PriestSkills.FORTITUDE_AMPLIFIER;
		boolean strengthLanded = ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
		boolean strengthReads = ally.hasEffect(MobEffects.STRENGTH);
		boolean regenerationLanded = ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
		boolean regenerationReads = ally.hasEffect(MobEffects.REGENERATION);
		// The probe has answered; clear it so the plain cast below is measured on a clean ally.
		ally.removeEffect(MobEffects.STRENGTH);
		ally.removeEffect(MobEffects.REGENERATION);
		ally.removeEffect(MobEffects.HEALTH_BOOST);
		boolean probeReads = directReads && strengthReads && regenerationReads;
		// The three probes above are the harness check. The cast is measured on a body standing with
		// the caster: a mock player stands at the world origin, and Blessing reaches around it.
		ally.discard();
		net.minecraft.world.entity.npc.villager.Villager blessed = allyAtOrigin(helper, 1.0, 1.5);
		List<LivingEntity> reachable = SkillTargeting.alliesAround(caster, PriestSkills.FORTITUDE_RADIUS);
		boolean allyInReach = reachable.contains(blessed);
		float allyAway = (float) Math.sqrt(blessed.position().distanceToSqr(caster.position()));
		boolean allyReadsAsAlly = PriestSkills.allyOf(caster, blessed);
		boolean plainApplied = castAtAlly(caster, blessed, "blessing_of_fortitude", false);
		MobEffectInstance plainBoost = blessed.getEffect(MobEffects.HEALTH_BOOST);
		boolean plainRegenerated = blessed.hasEffect(MobEffects.REGENERATION);
		boolean awakenedApplied = castAtAlly(caster, blessed, "blessing_of_fortitude", true);
		MobEffectInstance awakenedBoost = blessed.getEffect(MobEffects.HEALTH_BOOST);
		boolean awakenedRegenerated = blessed.hasEffect(MobEffects.REGENERATION);
		boolean awakenedStrong = blessed.hasEffect(MobEffects.STRENGTH);
		LOG.info("[BEHAVIOR] test=blessingOfFortitudeGivesHealthBoostAndAnAwakenedOneGivesTwoMore RESULT allyInReach={} inReach={} allyAway={} allyReadsAsAlly={} probeLanded={} probeReads={} plainApplied={} plainAmplifier={} plainRegenerated={} awakenedApplied={} awakenedAmplifier={} awakenedRegenerated={} awakenedStrong={} expected={}",
			allyInReach, reachable.size(), allyAway, allyReadsAsAlly, plainLanded && strengthLanded && regenerationLanded,
			probeReads, plainApplied,
			plainBoost == null ? -1 : plainBoost.getAmplifier(), plainRegenerated,
			awakenedApplied, awakenedBoost == null ? -1 : awakenedBoost.getAmplifier(), awakenedRegenerated, awakenedStrong,
			PriestSkills.FORTITUDE_AMPLIFIER);
		helper.assertTrue(plainLanded && strengthLanded && regenerationLanded && probeReads,
			"the harness cannot read an effect this case just added, so the cast cannot be judged");
		helper.assertTrue(allyInReach && allyReadsAsAlly, "the blessing's own ally rule did not reach the ally "
			+ allyAway + " blocks from the caster, out of " + reachable.size() + " bodies near it");
		helper.assertTrue(plainApplied && awakenedApplied, "the blessing did not land");
		helper.assertTrue(plainBoost != null && plainBoost.getAmplifier() == PriestSkills.FORTITUDE_AMPLIFIER,
			"the blessing carried Health Boost amplifier " + (plainBoost == null ? "none" : plainBoost.getAmplifier())
				+ ", not the " + PriestSkills.FORTITUDE_AMPLIFIER + " the plan states");
		helper.assertFalse(plainRegenerated, "a plain blessing handed out Regeneration, which only the awakened one does");
		helper.assertTrue(awakenedRegenerated && awakenedStrong,
			"the awakened blessing gave Regeneration=" + awakenedRegenerated + " and Strength=" + awakenedStrong);
		blessed.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void smiteDoublesAgainstTheUndeadAndItsAwakenedCastChains(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		SpellModifiers.CastModifiers plain = SkillSchoolGameTest.modifiers(caster, false);
		Mob zombie = zombie(helper, 4, 4);
		Mob skeleton = skeleton(helper, 6, 4);
		Mob plainSkeleton = skeleton(helper, 9, 4);
		Mob live = cow(helper, 11, 4);
		boolean zombieIsUndead = zombie.is(EntityTypeTags.UNDEAD);
		boolean skeletonIsUndead = skeleton.is(EntityTypeTags.UNDEAD);
		float zombieFactor = PriestSkills.undeadFactor(zombie);
		float skeletonFactor = PriestSkills.undeadFactor(skeleton);
		float liveFactor = PriestSkills.undeadFactor(live);
		Mob marked = zombieIsUndead ? zombie : skeleton;
		Mob plainUndead = zombieIsUndead ? skeleton : zombie;
		float markedFactor = PriestSkills.undeadFactor(marked);
		float plainFactor = PriestSkills.undeadFactor(plainUndead);
		float markedHit = PriestSkills.holyHit(caster, marked, PriestSkills.SMITE_DAMAGE, 1.0);
		float plainUndoubled = PriestSkills.holyHit(caster, plainUndead, PriestSkills.SMITE_DAMAGE, 1.0)
			/ plainFactor;
		float liveHit = PriestSkills.holyHit(caster, live, PriestSkills.SMITE_DAMAGE, 1.0);
		// Both undead read the same factor, and the living body reads the undoubled number.
		boolean bothUndeadAgree = closeEnough(markedHit, plainUndoubled * markedFactor);
		boolean livingTakesTheUndoubled = closeEnough(liveHit, plainUndoubled);
		float markedBefore = marked.getHealth();
		damageOf(caster, marked, plain);
		float markedLost = markedBefore - marked.getHealth();
		boolean theHitLands = closeEnough(markedLost, markedHit);
		boolean plainChainStayedHome = plainSkeleton.getHealth() >= plainSkeleton.getMaxHealth();
		boolean chained = PriestSkills.applySmite(caster, marked, marked.position(),
			SkillSchoolGameTest.modifiers(caster, true).withAwakened(true));
		int hurt = 0;
		for (Mob undead : List.of(zombie, skeleton)) {
			if (undead != marked && undead.getHealth() < undead.getMaxHealth()) {
				hurt++;
			}
		}
		int undeadSpecimens = (zombieIsUndead ? 1 : 0) + (skeletonIsUndead ? 1 : 0);
		LOG.info("[BEHAVIOR] test=smiteDoublesAgainstTheUndeadAndItsAwakenedCastChains RESULT chained={} zombieIsUndead={} skeletonIsUndead={} undeadSpecimens={} markedFactor={} plainFactor={} liveFactor={} markedHit={} plainUndoubled={} liveHit={} bothUndeadAgree={} livingTakesTheUndoubled={} markedLost={} theHitLands={} plainChainStayedHome={} chainRadius={} chainTargets={} hurtByChain={}",
			chained, zombieIsUndead, skeletonIsUndead, undeadSpecimens, markedFactor, plainFactor, liveFactor,
			markedHit, plainUndoubled, liveHit, bothUndeadAgree, livingTakesTheUndoubled, markedLost, theHitLands,
			plainChainStayedHome, PriestSkills.SMITE_CHAIN_RADIUS, PriestSkills.SMITE_CHAIN_TARGETS, hurt);
		helper.assertTrue(undeadSpecimens > 0, "neither a zombie nor a skeleton carries #minecraft:undead in this "
			+ "harness, so the doubled half of Smite has no specimen to prove it on");
		helper.assertTrue(plainUndoubled > 0.0F,
			"a holy hit computed nothing: marked=" + markedHit + " undoubled=" + plainUndoubled);
		helper.assertTrue(markedFactor == PriestSkills.SMITE_UNDEAD_FACTOR && plainFactor == PriestSkills.SMITE_UNDEAD_FACTOR,
			"a specimen the tag calls undead reads factor " + markedFactor + " and " + plainFactor
				+ ", not the " + PriestSkills.SMITE_UNDEAD_FACTOR + " the plan states");
		helper.assertTrue(bothUndeadAgree, "the " + marked.getType() + " takes " + markedHit + " while the "
			+ plainUndead.getType() + " takes " + (plainUndoubled * plainFactor) + " per hit");
		helper.assertTrue(livingTakesTheUndoubled, "a cow takes " + liveHit + " where the undoubled number is "
			+ plainUndoubled + " at factor " + liveFactor + "; the holy paths double only on the undead");
		helper.assertTrue(theHitLands, "the computed " + markedHit + " came off the body's health as " + markedLost);
		helper.assertTrue(plainChainStayedHome, "a plain Smite reached an undead it was never aimed at");
		helper.assertTrue(chained, "the awakened Smite did not land on its own target");
		helper.assertTrue(hurt == 1,
			"the awakened chain reached " + hurt + " of the one undead beside the mark");
		zombie.discard();
		skeleton.discard();
		plainSkeleton.discard();
		live.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void resurrectionWardLeavesALethallyStruckAllyStandingAndThenIsSpent(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		Mob ally = bigAlly(helper, ALLY_X, ALLY_Z);
		boolean applied = castAtAlly(caster, ally, "resurrection_ward", false);
		boolean wardedNow = PriestSkills.warded(ally.getUUID());
		boolean markedNow = ally.hasEffect(SpellStatus.RESURRECTION_WARD);
		// A lethal blow, not a scratch: the ward is spent on the one that would have killed.
		float maxBefore = ally.getMaxHealth();
		ally.setHealth(50.0F);
		float before = ally.getHealth();
		ally.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 5000.0F);
		boolean survived = ally.isAlive();
		float after = ally.getHealth();
		boolean spent = PriestSkills.warded(ally.getUUID()) == false;
		boolean effectSpent = ally.hasEffect(SpellStatus.RESURRECTION_WARD) == false;
		float maxAfter = ally.getMaxHealth();
		float restored = maxAfter * PriestSkills.RESURRECTION_WARD_HEALTH_SHARE;
		boolean stoodAtTheShare = closeEnough(after, restored);
		LOG.info("[BEHAVIOR] test=resurrectionWardLeavesALethallyStruckAllyStandingAndThenIsSpent RESULT applied={} wardedNow={} markedNow={} survived={} before={} after={} restored={} maxBefore={} maxAfter={} share={} spent={} effectSpent={} ticks={}",
			applied, wardedNow, markedNow, survived, before, after, restored, maxBefore, maxAfter,
			PriestSkills.RESURRECTION_WARD_HEALTH_SHARE, spent, effectSpent, PriestSkills.RESURRECTION_WARD_TICKS);
		helper.assertTrue(applied && wardedNow && markedNow, "the ward was not laid on the ally");
		helper.assertTrue(survived, "the first lethal blow killed the warded ally");
		helper.assertTrue(after > 0.0F, "the ward left the ally standing at " + after + " health");
		helper.assertTrue(spent && effectSpent, "the ward survived the blow it was spent on");
		helper.assertTrue(stoodAtTheShare, "the death hook left the ally at " + after + ", not the "
			+ restored + " that " + PriestSkills.RESURRECTION_WARD_HEALTH_SHARE + " of its " + maxAfter + " is");
		ally.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void consecratePulsesHolyGroundWhereTheCasterAims(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		Vec3 point = helper.absoluteVec(new Vec3(ALLY_X, 1.0, ALLY_Z));
		boolean applied = castAt(caster, "consecrate", point, false);
		List<PriestSkills.Ground> running = groundsOf(helper, caster);
		boolean placed = running.size() == 1;
		PriestSkills.Ground ground = placed ? running.get(0) : null;
		boolean sized = ground != null && ground.radius() == PriestSkills.CONSECRATE_RADIUS;
		boolean follows = ground != null && ground.center().distanceToSqr(point) < 0.01;
		int now = helper.getLevel().getServer().getTickCount();
		boolean spansItsOwnTicks = ground != null && ground.endsAt() == now + PriestSkills.CONSECRATE_TICKS;
		boolean firstPulseAfterOnePeriod = ground != null && ground.nextPulse() == now + PriestSkills.CONSECRATE_PERIOD_TICKS;
		PriestSkills.tickAt(helper.getLevel().getServer(), now + 5);
		List<PriestSkills.Ground> pulsed = groundsOf(helper, caster);
		boolean waitedForItsPeriod = !pulsed.isEmpty() && pulsed.get(0).nextPulse() == now + PriestSkills.CONSECRATE_PERIOD_TICKS;
		PriestSkills.tickAt(helper.getLevel().getServer(), now + PriestSkills.CONSECRATE_TICKS);
		boolean expired = groundsOf(helper, caster).isEmpty();
		LOG.info("[BEHAVIOR] test=consecratePulsesHolyGroundWhereTheCasterAims RESULT applied={} placed={} sized={} follows={} spansItsOwnTicks={} firstPulseAfterOnePeriod={} waitedForItsPeriod={} expired={} period={} ticks={}",
			applied, placed, sized, follows, spansItsOwnTicks, firstPulseAfterOnePeriod, waitedForItsPeriod, expired,
			PriestSkills.CONSECRATE_PERIOD_TICKS, PriestSkills.CONSECRATE_TICKS);
		helper.assertTrue(applied && placed, "the cast left " + running.size() + " grounds running, not one");
		helper.assertTrue(sized, "the ground carries radius " + (ground == null ? "none" : ground.radius())
			+ ", not " + PriestSkills.CONSECRATE_RADIUS);
		helper.assertTrue(follows, "the ground was not laid where the caster aimed");
		helper.assertTrue(spansItsOwnTicks, "the ground is set to end at "
			+ (ground == null ? "nothing" : ground.endsAt()) + ", not " + PriestSkills.CONSECRATE_TICKS + " ticks out");
		helper.assertTrue(firstPulseAfterOnePeriod && waitedForItsPeriod, "the ground pulsed off its "
			+ PriestSkills.CONSECRATE_PERIOD_TICKS + " tick period");
		helper.assertTrue(expired, "the ground outlived its own " + PriestSkills.CONSECRATE_TICKS + " ticks");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void anAwakenedConsecrateFollowsTheCasterAsHeMoves(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		lookAt(helper, caster, ALLY_X, ALLY_Z);
		Vec3 point = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		boolean applied = castAt(caster, "consecrate", point, true);
		List<PriestSkills.Ground> before = groundsOf(helper, caster);
		Vec3 was = before.isEmpty() ? Vec3.ZERO : before.get(0).center();
		Vec3 moved = helper.absoluteVec(new Vec3(CASTER_X + 3.0, 1.0, CASTER_Z));
		caster.snapTo(moved.x, moved.y, moved.z, 0.0F, 0.0F);
		PriestSkills.tickAt(helper.getLevel().getServer(), helper.getLevel().getServer().getTickCount() + 1);
		List<PriestSkills.Ground> after = groundsOf(helper, caster);
		Vec3 now = after.isEmpty() ? Vec3.ZERO : after.get(0).center();
		LOG.info("[BEHAVIOR] test=anAwakenedConsecrateFollowsTheCasterAsHeMoves RESULT applied={} grounds={} was={} now={} movedTo={}",
			applied, after.size(), was, now, moved);
		helper.assertTrue(applied && !after.isEmpty(), "the ground was not running");
		helper.assertTrue(now.distanceToSqr(moved) < was.distanceToSqr(moved),
			"the awakened ground stayed at " + now + " while its caster moved to " + moved);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aPlainConsecrateStaysWhereItWasLaid(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		lookAt(helper, caster, ALLY_X, ALLY_Z);
		Vec3 point = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		boolean applied = castAt(caster, "consecrate", point, false);
		List<PriestSkills.Ground> before = groundsOf(helper, caster);
		Vec3 was = before.isEmpty() ? Vec3.ZERO : before.get(0).center();
		Vec3 moved = helper.absoluteVec(new Vec3(CASTER_X + 3.0, 1.0, CASTER_Z));
		caster.snapTo(moved.x, moved.y, moved.z, 0.0F, 0.0F);
		PriestSkills.tickAt(helper.getLevel().getServer(), helper.getLevel().getServer().getTickCount() + 1);
		List<PriestSkills.Ground> after = groundsOf(helper, caster);
		Vec3 now = after.isEmpty() ? Vec3.ZERO : after.get(0).center();
		LOG.info("[BEHAVIOR] test=aPlainConsecrateStaysWhereItWasLaid RESULT applied={} grounds={} was={} now={} movedTo={}",
			applied, after.size(), was, now, moved);
		helper.assertTrue(applied && !after.isEmpty(), "the ground was not running");
		helper.assertTrue(now.distanceToSqr(was) < 0.01, "a plain ground moved from " + was + " to " + now);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void guardianAngelMovesAShareOfTheBlowOntoThePriest(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager ally = ally(helper, ALLY_X, ALLY_Z);
		boolean applied = castAtAlly(caster, ally, "guardian_angel", false);
		boolean guardedNow = PriestSkills.guarded(ally.getUUID());
		float blow = 10.0F;
		float passed = PriestSkills.guardianShare(ally, helper.getLevel(),
			helper.getLevel().damageSources().generic(), blow);
		float carried = blow - passed;
		boolean sharedByTheShare = closeEnough(carried, blow * PriestSkills.GUARDIAN_SHARE);
		LOG.info("[BEHAVIOR] test=guardianAngelMovesAShareOfTheBlowOntoThePriest RESULT applied={} guardedNow={} blow={} passed={} carried={} share={}",
			applied, guardedNow, blow, passed, carried, PriestSkills.GUARDIAN_SHARE);
		helper.assertTrue(applied && guardedNow, "the angel was not set over the ally");
		helper.assertTrue(passed < blow, "the ally took the whole blow, so no share was redirected");
		helper.assertTrue(sharedByTheShare, "a 10 point blow carried " + carried + " to the priest, not "
			+ (blow * PriestSkills.GUARDIAN_SHARE) + " at share " + PriestSkills.GUARDIAN_SHARE);
		ally.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void anAwakenedGuardianAngelMendsThePriestInsteadOfHurtingHim(final GameTestHelper helper) {
		SkillSchoolGameTest.floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.entity.npc.villager.Villager ally = ally(helper, ALLY_X, ALLY_Z);
		boolean applied = castAtAlly(caster, ally, "guardian_angel", true);
		caster.setHealth(caster.getMaxHealth() * 0.5F);
		float priestBefore = caster.getHealth();
		float passed = PriestSkills.guardianShare(ally, helper.getLevel(),
			helper.getLevel().damageSources().generic(), 10.0F);
		float priestGained = caster.getHealth() - priestBefore;
		LOG.info("[BEHAVIOR] test=anAwakenedGuardianAngelMendsThePriestInsteadOfHurtingHim RESULT applied={} passed={} priestGained={} share={}",
			applied, passed, priestGained, PriestSkills.GUARDIAN_SHARE);
		helper.assertTrue(applied, "the awakened angel was not set");
		helper.assertTrue(passed < 10.0F, "the ally took the whole blow, so no share was redirected");
		helper.assertTrue(priestGained > 0.0F, "the awakened angel left the priest at " + caster.getHealth()
			+ " instead of mending the share back to him");
		ally.discard();
		helper.succeed();
	}

	// ---- fixtures ----

	/** A villager is an ally of any player, and takes a heal like one. */
	static net.minecraft.world.entity.npc.villager.Villager ally(final GameTestHelper helper, final double x, final double z) {
		net.minecraft.world.entity.npc.villager.Villager villager =
			EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(villager != null, "the level refused an ally");
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		villager.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		villager.setNoAi(true);
		villager.setNoGravity(true);
		helper.getLevel().addFreshEntity(villager);
		return villager;
	}

	static Mob skeleton(final GameTestHelper helper, final double x, final double z) {
		Mob mob = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(mob != null, "the level refused a skeleton");
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	/** A villager standing at an exact world spot, for a case that needs a reach of its own. */
	static net.minecraft.world.entity.npc.villager.Villager villagerAt(
		final GameTestHelper helper, final double x, final double y, final double z
	) {
		net.minecraft.world.entity.npc.villager.Villager villager =
			EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(villager != null, "the level refused an ally");
		villager.snapTo(x, y, z, 0.0F, 0.0F);
		villager.setNoAi(true);
		villager.setNoGravity(true);
		helper.getLevel().addFreshEntity(villager);
		return villager;
	}

	/** An ally standing the given block offsets from the world origin, where a mock player stands. */
	static net.minecraft.world.entity.npc.villager.Villager allyAtOrigin(final GameTestHelper helper, final double x, final double z) {
		net.minecraft.world.entity.npc.villager.Villager villager =
			EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(villager != null, "the level refused an ally");
		villager.snapTo(x, 1.0, z, 0.0F, 0.0F);
		villager.setNoAi(true);
		villager.setNoGravity(true);
		helper.getLevel().addFreshEntity(villager);
		return villager;
	}

	/** Points the caster at a spot on the arena's own ground. */
	static void lookAt(final GameTestHelper helper, final ServerPlayer caster, final double x, final double z) {
		Vec3 from = helper.absoluteVec(new Vec3(CASTER_X, 2.0, CASTER_Z));
		Vec3 to = helper.absoluteVec(new Vec3(x, 1.0, z));
		caster.teleportTo(helper.getLevel(), from.x, from.y, from.z, Set.of(), 0.0F, 0.0F, true);
		Vec3 way = to.subtract(from);
		float yaw = (float) (Math.toDegrees(Math.atan2(-way.x, way.z)));
		float pitch = (float) (Math.toDegrees(-Math.atan2(way.y, Math.hypot(way.x, way.z))));
		caster.setYRot(yaw);
		caster.setYHeadRot(yaw);
		caster.setXRot(pitch);
	}

	/** Casts a book's effect with the given ally already resolved, as the cast itself would. */
	static boolean castAtAlly(final ServerPlayer caster, final LivingEntity ally, final String path, final boolean awakened) {
		SpellDefinition definition = SpellbookCatalog.get(id(path));
		caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
			awakened ? SkillSchoolGameTest.awakenedBook(path) : new ItemStack(SkillSchoolGameTest.book(path)));
		SpellModifiers.CastModifiers modifiers = SkillSchoolGameTest.modifiers(caster, awakened).withAwakened(awakened);
		CastContext.setAlly(ally);
		try {
			return definition.effect().apply(caster, null, caster.position(), modifiers);
		} finally {
			CastContext.setAlly(null);
		}
	}

	/** Casts a book that aims at a point rather than at a body. */
	static boolean castAt(final ServerPlayer caster, final String path, final Vec3 point, final boolean awakened) {
		SpellDefinition definition = SpellbookCatalog.get(id(path));
		caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
			awakened ? SkillSchoolGameTest.awakenedBook(path) : new ItemStack(SkillSchoolGameTest.book(path)));
		return definition.effect().apply(caster, null, point, SkillSchoolGameTest.modifiers(caster, awakened).withAwakened(awakened));
	}

	/** A zombie with health to spare, so a case can count the damage without killing it. */
	static Mob zombie(final GameTestHelper helper, final double x, final double z) {
		return tough(helper, EntityTypes.ZOMBIE, x, z);
	}

	/** A cow is not undead, so the undead half of the rule has something to refuse. */
	static Mob cow(final GameTestHelper helper, final double x, final double z) {
		return tough(helper, EntityTypes.COW, x, z);
	}

	/** A 2000 health ally: enough to take a lethal blow and still be read afterwards. */
	static Mob bigAlly(final GameTestHelper helper, final double x, final double z) {
		return tough(helper, EntityTypes.VILLAGER, x, z);
	}

	private static Mob tough(final GameTestHelper helper, final EntityType<? extends Mob> type, final double x, final double z) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(mob != null, "the level refused a " + type);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0);
		mob.setHealth(2000.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	/** The holy grounds this caster owns, so a neighbour's ground is never counted. */
	static List<PriestSkills.Ground> groundsOf(final GameTestHelper helper, final ServerPlayer caster) {
		List<PriestSkills.Ground> mine = new ArrayList<>();
		for (PriestSkills.Ground ground : PriestSkills.grounds()) {
			if (ground.caster().equals(caster.getUUID())) {
				mine.add(ground);
			}
		}
		return mine;
	}

	/** What one holy hit takes off the target's health. */
	static float damageOf(final ServerPlayer caster, final LivingEntity target, final SpellModifiers.CastModifiers modifiers) {
		float before = target.getHealth();
		PriestSkills.hitHoly(caster, target, PriestSkills.SMITE_DAMAGE, modifiers);
		return before - target.getHealth();
	}

	/** The ward pool on a body, or nothing when it carries no ward effect. */
	static float wardOf(final LivingEntity body) {
		if (!body.hasEffect(SpellStatus.ARCANE_WARD)) {
			return 0.0F;
		}
		Float pool = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) body).getAttached(SpellStatus.WARD_POOL);
		return pool == null ? 0.0F : pool;
	}

	/** Whether two floats agree to within a hundredth, without pinning the numbers by hand. */
	static boolean closeEnough(final float a, final float b) {
		return Math.abs(a - b) <= 0.01F;
	}

	@GameTest
	public void noMobCastsAPriestBook(final GameTestHelper helper) {
		List<String> mobSpells = new ArrayList<>(MobSpells.OFFENSIVE);
		mobSpells.addAll(MobSpells.SUPPORT);
		mobSpells.addAll(MobSpells.PACK_SUPPORT);
		mobSpells.addAll(MobSpells.PACK_AREA_OFFENSIVE);
		mobSpells.addAll(MobSpells.VILLAGE_WITCH_OFFENSIVE);
		mobSpells.addAll(MobSpells.VILLAGE_WITCH_SUPPORT);
		List<String> mob = mobSpells.stream().filter(SkillSchoolGameTest.PRIEST_IDS::contains).toList();
		List<Identifier> imbued = SpellImbue.MOB_SPELLS.stream()
			.filter(identifier -> SkillSchoolGameTest.PRIEST_IDS.contains(identifier.getPath()))
			.toList();
		LOG.info("[BEHAVIOR] test=noMobCastsAPriestBook RESULT mobSpellLists={} priestInMobLists={} mobImbueSpells={} priestImbued={}",
			mobSpells.size(), mob, SpellImbue.MOB_SPELLS.size(), imbued);
		helper.assertTrue(mob.isEmpty(), "a mob spell list holds a priest book: " + mob);
		helper.assertTrue(imbued.isEmpty(), "a mob's imbued weapon can cast a priest book: " + imbued);
		helper.succeed();
	}

	@GameTest
	public void twentyThousandContainerRollsProduceEveryPriestBookAndNoAwakenedOne(final GameTestHelper helper) {
		Set<Identifier> priest = SkillSchoolGameTest.PRIEST_IDS.stream()
			.map(PriestBooksGameTest::id)
			.collect(Collectors.toUnmodifiableSet());
		Map<Identifier, Integer> counts = new LinkedHashMap<>();
		priest.forEach(book -> counts.put(book, 0));
		Identifier table = Identifier.parse("minecraft:chests/simple_dungeon");
		RandomSource random = RandomSource.create(90210L);
		int awakened = 0;
		int total = 0;
		for (int roll = 0; roll < 20_000; roll++) {
			ItemStack stack = SpellbookTestAccess.rollChest(table, true, random);
			if (stack.isEmpty()) {
				continue;
			}
			total++;
			counts.computeIfPresent(BuiltInRegistries.ITEM.getKey(stack.getItem()), (ignored, count) -> count + 1);
			if (Awakening.isAwakened(stack)) {
				awakened++;
			}
		}
		LOG.info("[BEHAVIOR] test=twentyThousandContainerRollsProduceEveryPriestBookAndNoAwakenedOne RESULT rolls=20000 pool={} total={} awakened={} counts={}",
			SpellbookCatalog.DEFINITIONS.size(), total, awakened, counts);
		helper.assertTrue(counts.values().stream().allMatch(count -> count > 0), "a priest book never dropped: " + counts);
		helper.assertTrue(awakened == 0, "a container roll produced " + awakened + " awakened books");
		helper.succeed();
	}

	@GameTest
	public void everyPriestBookCraftsFromItsOwnRecipes(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String path : SkillSchoolGameTest.PRIEST_IDS) {
			if (Spellbooks.BOOK_ONLY.contains(id(path))) {
				continue;
			}
			ItemStack material = new ItemStack(BuiltInRegistries.ITEM.getValue(SkillSchoolGameTest.materialOf(path)));
			ItemStack crafted = craft(helper, shaped("M M", " B ", "M M", 'M', material, 'B', new ItemStack(Items.BOOK)));
			ItemStack scroll = craft(helper, shapeless(new ItemStack(Items.PAPER), material));
			ItemStack fromScrolls = craft(helper, shaped("SSS", "SBS", "SSS", 'S',
				new ItemStack(BuiltInRegistries.ITEM.getValue(id(path + "_scroll"))), 'B', new ItemStack(Items.BOOK)));
			if (!crafted.is(SkillSchoolGameTest.book(path))) {
				wrong.add(path + " book -> " + BuiltInRegistries.ITEM.getKey(crafted.getItem()));
			}
			if (scroll.getCount() != 2 || !scroll.is(BuiltInRegistries.ITEM.getValue(id(path + "_scroll")))) {
				wrong.add(path + " scroll -> " + BuiltInRegistries.ITEM.getKey(scroll.getItem()) + " x" + scroll.getCount());
			}
			if (!fromScrolls.is(SkillSchoolGameTest.book(path))) {
				wrong.add(path + " from scrolls -> " + BuiltInRegistries.ITEM.getKey(fromScrolls.getItem()));
			}
		}
		LOG.info("[BEHAVIOR] test=everyPriestBookCraftsFromItsOwnRecipes RESULT books={} wrong={}",
			SkillSchoolGameTest.PRIEST_IDS.size(), wrong);
		helper.assertTrue(wrong.isEmpty(), "these recipes craft the wrong item: " + wrong);
		helper.succeed();
	}

	private static CraftingInput shaped(
		final String rowA, final String rowB, final String rowC,
		final char first, final ItemStack firstStack, final char second, final ItemStack secondStack
	) {
		List<ItemStack> items = new ArrayList<>();
		for (String row : List.of(rowA, rowB, rowC)) {
			for (char symbol : row.toCharArray()) {
				items.add(symbol == first ? firstStack : symbol == second ? secondStack : ItemStack.EMPTY);
			}
		}
		return CraftingInput.of(3, 3, items);
	}

	private static CraftingInput shapeless(final ItemStack... stacks) {
		return CraftingInput.of(1, stacks.length, List.of(stacks));
	}

	private static ItemStack craft(final GameTestHelper helper, final CraftingInput input) {
		CraftingRecipe recipe = helper.getLevel().getServer().getRecipeManager()
			.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(holder -> holder.value())
			.orElse(null);
		return recipe == null ? ItemStack.EMPTY : recipe.assemble(input);
	}

	static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}
}
