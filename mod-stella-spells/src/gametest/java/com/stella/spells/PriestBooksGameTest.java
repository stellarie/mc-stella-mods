package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
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
	public void theTenPriestBooksShipAsPriestBooks(final GameTestHelper helper) {
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
		LOG.info("[BEHAVIOR] test=theTenPriestBooksShipAsPriestBooks RESULT priest={} declared={} books={} wrong={}",
			priest, SkillSchoolGameTest.PRIEST_IDS.size(), SpellbookCatalog.DEFINITIONS.size(), wrong);
		helper.assertTrue(wrong.isEmpty(), "the priest books do not match the school guard: " + wrong);
		helper.assertTrue(priest == SkillSchoolGameTest.PRIEST_IDS.size(),
			priest + " books carry School.PRIEST, not the " + SkillSchoolGameTest.PRIEST_IDS.size() + " the stage declares");
		helper.assertTrue(SpellbookCatalog.DEFINITIONS.size() == 72,
			"the pack holds " + SpellbookCatalog.DEFINITIONS.size() + " books, not 72");
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

	@GameTest(maxTicks = 60)
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

	static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}
}
