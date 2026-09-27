package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The twenty spells of the second catalogue, each proved by what it does to a mob or to its caster. */
public class NewSpellsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.newspells");
	private static final Map<UUID, List<Cloud>> CLOUDS = new ConcurrentHashMap<>();

	private record Cloud(AreaEffectCloud cloud, MobEffectInstance effect) {
	}

	static {
		MoreSpells.setRecorder((caster, cloud, effect) -> CLOUDS.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(new Cloud(cloud, effect)));
	}

	// Hostile spells: the mob straight ahead of the caster.

	@GameTest
	public void iceLanceHurtsAndFreezes(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob mob = zombie(helper, 2, 5);
		cast(helper, caster, "ice_lance");
		helper.assertTrue(taken(mob) == MoreSpells.ICE_LANCE_DAMAGE, "Ice Lance dealt " + taken(mob));
		helper.assertTrue(mob.getTicksFrozen() >= MoreSpells.ICE_LANCE_FREEZE_TICKS && mob.isFullyFrozen(), "the mob is not frozen: " + mob.getTicksFrozen());
		result("iceLanceHurtsAndFreezes", "damage=" + taken(mob) + " frozen=" + mob.getTicksFrozen());
		helper.succeed();
	}

	// Outside powder snow a mob thaws two ticks for every tick, so the freeze must hold for its whole stated time.
	@GameTest(maxTicks = 200)
	public void iceLanceKeepsTheMobFrozenForItsWholeDuration(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob mob = zombie(helper, 2, 5);
		cast(helper, caster, "ice_lance");
		helper.runAfterDelay(MoreSpells.ICE_LANCE_FREEZE_TICKS - 2, () -> {
			boolean frozen = mob.isFullyFrozen();
			result("iceLanceKeepsTheMobFrozenForItsWholeDuration", "after=" + (MoreSpells.ICE_LANCE_FREEZE_TICKS - 2) + " frozen=" + frozen + " ticksFrozen=" + mob.getTicksFrozen());
			helper.assertTrue(frozen, "the mob thawed before " + MoreSpells.ICE_LANCE_FREEZE_TICKS + " ticks: " + mob.getTicksFrozen() + " frozen ticks left");
			helper.succeed();
		});
	}

	@GameTest
	public void soulSiphonHealsTheCasterByWhatItTakes(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.setHealth(10.0F);
		Mob mob = zombie(helper, 2, 5);
		cast(helper, caster, "soul_siphon");
		helper.assertTrue(taken(mob) == MoreSpells.SOUL_SIPHON_DAMAGE && caster.getHealth() == 10.0F + MoreSpells.SOUL_SIPHON_DAMAGE, "Soul Siphon took " + taken(mob) + " and left the caster at " + caster.getHealth());
		result("soulSiphonHealsTheCasterByWhatItTakes", "taken=" + taken(mob) + " casterHealth=" + caster.getHealth());
		helper.succeed();
	}

	// A vindicator, because an undead mob shrugs off Poison.
	@GameTest
	public void venomDartStingsAndPoisons(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob mob = helper.spawnMob(EntityTypes.VINDICATOR, 2, 1, 5).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		cast(helper, caster, "venom_dart");
		MobEffectInstance poison = mob.getEffect(MobEffects.POISON);
		helper.assertTrue(taken(mob) == MoreSpells.VENOM_DAMAGE, "Venom Dart dealt " + taken(mob));
		helper.assertTrue(poison != null && poison.getAmplifier() == MoreSpells.VENOM_AMPLIFIER && poison.getDuration() == MoreSpells.VENOM_TICKS, "Venom Dart left " + poison);
		result("venomDartStingsAndPoisons", "damage=" + taken(mob) + " poison=" + poison);
		helper.succeed();
	}

	@GameTest
	public void sappingHexWeakens(final GameTestHelper helper) {
		assertMobEffect(helper, "sappingHexWeakens", "sapping_hex", MobEffects.WEAKNESS, MoreSpells.HEX_AMPLIFIER, MoreSpells.HEX_TICKS);
	}

	@GameTest
	public void skyhookLifts(final GameTestHelper helper) {
		assertMobEffect(helper, "skyhookLifts", "skyhook", MobEffects.LEVITATION, MoreSpells.SKYHOOK_AMPLIFIER, MoreSpells.SKYHOOK_TICKS);
	}

	@GameTest
	public void blindingFlashBlindsAndMarks(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob mob = zombie(helper, 2, 5);
		cast(helper, caster, "blinding_flash");
		MobEffectInstance blind = mob.getEffect(MobEffects.BLINDNESS);
		MobEffectInstance glow = mob.getEffect(MobEffects.GLOWING);
		helper.assertTrue(blind != null && blind.getDuration() == MoreSpells.FLASH_BLIND_TICKS && glow != null && glow.getDuration() == MoreSpells.FLASH_GLOW_TICKS, "the flash left " + blind + " and " + glow);
		result("blindingFlashBlindsAndMarks", "blind=" + blind.getDuration() + " glow=" + glow.getDuration());
		helper.succeed();
	}

	@GameTest
	public void smiteStrikes(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob mob = zombie(helper, 2, 5);
		cast(helper, caster, "smite");
		helper.assertTrue(taken(mob) == MoreSpells.SMITE_DAMAGE, "Smite dealt " + taken(mob));
		result("smiteStrikes", "damage=" + taken(mob));
		helper.succeed();
	}

	@GameTest
	public void emberBrandBurns(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob mob = zombie(helper, 2, 5);
		cast(helper, caster, "ember_brand");
		helper.assertTrue(taken(mob) == MoreSpells.EMBER_DAMAGE && mob.getRemainingFireTicks() >= MoreSpells.EMBER_BURN_TICKS - 1, "Ember Brand dealt " + taken(mob) + " and set " + mob.getRemainingFireTicks() + " fire ticks");
		result("emberBrandBurns", "damage=" + taken(mob) + " fire=" + mob.getRemainingFireTicks());
		helper.succeed();
	}

	// Self spells: the caster.

	@GameTest
	public void quicksilverHastensTheCaster(final GameTestHelper helper) {
		assertCasterEffect(helper, "quicksilverHastensTheCaster", "quicksilver", MobEffects.SPEED, MoreSpells.QUICKSILVER_AMPLIFIER, MoreSpells.QUICKSILVER_TICKS);
	}

	@GameTest
	public void tideLungsLetsTheCasterBreatheAndSwim(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		cast(helper, caster, "tide_lungs");
		MobEffectInstance breath = caster.getEffect(MobEffects.WATER_BREATHING);
		MobEffectInstance grace = caster.getEffect(MobEffects.DOLPHINS_GRACE);
		helper.assertTrue(breath != null && breath.getDuration() == MoreSpells.TIDE_BREATH_TICKS && grace != null && grace.getDuration() == MoreSpells.TIDE_GRACE_TICKS, "Tide Lungs left " + breath + " and " + grace);
		result("tideLungsLetsTheCasterBreatheAndSwim", "breath=" + breath.getDuration() + " grace=" + grace.getDuration());
		helper.succeed();
	}

	@GameTest
	public void berserkStrengthens(final GameTestHelper helper) {
		assertCasterEffect(helper, "berserkStrengthens", "berserk", MobEffects.STRENGTH, MoreSpells.BERSERK_AMPLIFIER, MoreSpells.BERSERK_TICKS);
	}

	@GameTest
	public void hasteRuneQuickensDigging(final GameTestHelper helper) {
		assertCasterEffect(helper, "hasteRuneQuickensDigging", "haste_rune", MobEffects.HASTE, MoreSpells.HASTE_AMPLIFIER, MoreSpells.HASTE_TICKS);
	}

	@GameTest
	public void shadowVeilHidesTheCaster(final GameTestHelper helper) {
		assertCasterEffect(helper, "shadowVeilHidesTheCaster", "shadow_veil", MobEffects.INVISIBILITY, 0, MoreSpells.VEIL_TICKS);
	}

	@GameTest
	public void sanctuaryHealsTheCasterAndNearbyPlayersOnly(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.setHealth(10.0F);
		ServerPlayer near = player(helper, new Vec3(4.5, 1.0, 1.5));
		near.setHealth(10.0F);
		ServerPlayer far = player(helper, new Vec3(2.5, 1.0, 1.5 + MoreSpells.SANCTUARY_RADIUS + 2.0));
		far.setHealth(10.0F);
		cast(helper, caster, "sanctuary");
		helper.assertTrue(caster.getHealth() == 10.0F + MoreSpells.SANCTUARY_SELF_HEAL, "the caster healed to " + caster.getHealth());
		helper.assertTrue(near.getHealth() == 10.0F + MoreSpells.SANCTUARY_ALLY_HEAL, "the near player healed to " + near.getHealth());
		helper.assertTrue(far.getHealth() == 10.0F, "a player outside the sanctuary healed to " + far.getHealth());
		result("sanctuaryHealsTheCasterAndNearbyPlayersOnly", "caster=" + caster.getHealth() + " near=" + near.getHealth() + " far=" + far.getHealth());
		helper.succeed();
	}

	@GameTest
	public void purifyClearsOnlyHarmfulEffects(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
		caster.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
		cast(helper, caster, "purify");
		helper.assertTrue(!caster.hasEffect(MobEffects.POISON) && caster.hasEffect(MobEffects.SPEED), "Purify left " + caster.getActiveEffects());
		result("purifyClearsOnlyHarmfulEffects", "poison=false speed=true");
		helper.succeed();
	}

	@GameTest
	public void purifyWithNothingToClearDoesNotCast(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		InteractionResult result = use(helper, caster, "purify");
		helper.assertTrue(result != InteractionResult.SUCCESS && !caster.getCooldowns().isOnCooldown(caster.getMainHandItem()), "an empty Purify cast: " + result);
		result("purifyWithNothingToClearDoesNotCast", "result=" + result);
		helper.succeed();
	}

	@GameTest
	public void frostNovaChillsHostilesAroundTheCaster(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		Mob near = zombie(helper, 4, 3);
		Mob far = zombie(helper, 2, 1 + (int) MoreSpells.FROST_NOVA_RADIUS + 3);
		cast(helper, caster, "frost_nova");
		MobEffectInstance slow = near.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(taken(near) == MoreSpells.FROST_NOVA_DAMAGE && slow != null && slow.getAmplifier() == MoreSpells.FROST_NOVA_SLOW_AMPLIFIER, "the near mob took " + taken(near) + " and " + slow);
		helper.assertTrue(taken(far) == 0.0F && !far.hasEffect(MobEffects.SLOWNESS), "the nova reached past its radius");
		result("frostNovaChillsHostilesAroundTheCaster", "near=" + taken(near) + " far=" + taken(far));
		helper.succeed();
	}

	// Block spells: the stone the caster looks at.

	@GameTest
	public void earthspikeThrowsHostilesUp(final GameTestHelper helper) {
		ServerPlayer caster = aimedCaster(helper);
		Mob near = zombie(helper, 3, 8);
		Mob far = zombie(helper, 2, 8 + (int) MoreSpells.EARTHSPIKE_RADIUS + 3);
		cast(helper, caster, "earthspike");
		helper.assertTrue(taken(near) == MoreSpells.EARTHSPIKE_DAMAGE && near.getDeltaMovement().y >= MoreSpells.EARTHSPIKE_LIFT - 1.0e-6, "the near mob took " + taken(near) + " and rose at " + near.getDeltaMovement().y);
		helper.assertTrue(taken(far) == 0.0F, "the spike reached past its radius");
		result("earthspikeThrowsHostilesUp", "near=" + taken(near) + " lift=" + near.getDeltaMovement().y);
		helper.succeed();
	}

	@GameTest
	public void plagueCloudLeavesPoisonAtTheSpot(final GameTestHelper helper) {
		assertCloud(helper, "plagueCloudLeavesPoisonAtTheSpot", "plague_cloud", MobEffects.POISON, MoreSpells.PLAGUE_RADIUS, MoreSpells.PLAGUE_CLOUD_TICKS);
	}

	@GameTest
	public void healingSpringLeavesRegenerationAtTheSpot(final GameTestHelper helper) {
		assertCloud(helper, "healingSpringLeavesRegenerationAtTheSpot", "healing_spring", MobEffects.REGENERATION, MoreSpells.SPRING_RADIUS, MoreSpells.SPRING_CLOUD_TICKS);
	}

	@GameTest
	public void flameRingSetsHostilesAlight(final GameTestHelper helper) {
		ServerPlayer caster = aimedCaster(helper);
		Mob near = zombie(helper, 3, 8);
		Mob far = zombie(helper, 2, 8 + (int) MoreSpells.FLAME_RING_RADIUS + 3);
		cast(helper, caster, "flame_ring");
		helper.assertTrue(taken(near) == MoreSpells.FLAME_RING_DAMAGE && near.getRemainingFireTicks() >= MoreSpells.FLAME_RING_BURN_TICKS - 1, "the near mob took " + taken(near) + " and " + near.getRemainingFireTicks() + " fire ticks");
		helper.assertTrue(taken(far) == 0.0F && far.getRemainingFireTicks() <= 0, "the ring reached past its radius");
		result("flameRingSetsHostilesAlight", "near=" + taken(near) + " fire=" + near.getRemainingFireTicks());
		helper.succeed();
	}

	private static void assertMobEffect(final GameTestHelper helper, final String test, final String spell, final Holder<MobEffect> effect, final int amplifier, final int ticks) {
		ServerPlayer caster = caster(helper);
		Mob mob = zombie(helper, 2, 5);
		cast(helper, caster, spell);
		MobEffectInstance applied = mob.getEffect(effect);
		helper.assertTrue(applied != null && applied.getAmplifier() == amplifier && applied.getDuration() == ticks, spell + " left " + applied);
		result(test, "effect=" + applied);
		helper.succeed();
	}

	private static void assertCasterEffect(final GameTestHelper helper, final String test, final String spell, final Holder<MobEffect> effect, final int amplifier, final int ticks) {
		ServerPlayer caster = caster(helper);
		cast(helper, caster, spell);
		MobEffectInstance applied = caster.getEffect(effect);
		helper.assertTrue(applied != null && applied.getAmplifier() == amplifier && applied.getDuration() == ticks, spell + " left " + applied);
		result(test, "effect=" + applied);
		helper.succeed();
	}

	private static void assertCloud(final GameTestHelper helper, final String test, final String spell, final Holder<MobEffect> effect, final float radius, final int ticks) {
		ServerPlayer caster = aimedCaster(helper);
		cast(helper, caster, spell);
		List<Cloud> clouds = CLOUDS.getOrDefault(caster.getUUID(), List.of());
		helper.assertTrue(clouds.size() == 1, spell + " left " + clouds.size() + " clouds");
		Cloud cloud = clouds.get(0);
		Vec3 spot = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 8)));
		helper.assertTrue(cloud.effect().getEffect().equals(effect) && cloud.cloud().getRadius() == radius && cloud.cloud().isAlive(), spell + " left " + cloud.effect() + " at radius " + cloud.cloud().getRadius());
		helper.assertTrue(cloud.cloud().position().distanceTo(spot) <= 1.5, "the cloud sits at " + helper.relativeVec(cloud.cloud().position()));
		result(test, "effect=" + cloud.effect().getEffect().getRegisteredName() + " radius=" + cloud.cloud().getRadius() + " ticks=" + ticks);
		helper.succeed();
	}

	private static void cast(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		InteractionResult result = use(helper, caster, spell);
		helper.assertTrue(result == InteractionResult.SUCCESS, spell + " did not cast: " + result);
	}

	private static InteractionResult use(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		return book.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
	}

	private static float taken(final LivingEntity entity) {
		return entity.getMaxHealth() - entity.getHealth();
	}

	private static void floor(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 16; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	private static ServerPlayer player(final GameTestHelper helper, final Vec3 local) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(local);
		player.setPos(at.x, at.y, at.z);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		return player;
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		floor(helper);
		return player(helper, new Vec3(2.5, 1.0, 1.5));
	}

	private static ServerPlayer aimedCaster(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		helper.setBlock(2, 1, 8, Blocks.STONE);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 8))));
		return caster;
	}

	// A durable zombie with no armour, so a spell's damage reads as exactly its number.
	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}

	private static void result(final String test, final String details) {
		LOG.info("[SPELLTEST] test={} RESULT {}", test, details);
	}
}
