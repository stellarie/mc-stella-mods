package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RPG duration table. Every expected value is a literal from the task table, never the shipped constant,
 * so a wrong constant fails the test.
 */
public class RpgDurationsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.rpgdur");
	private static final int HALF_HOUR = 36_000;

	private static final class Stub implements RpgBridge.Stats {
		private final double multiplier;

		Stub(final double multiplier) {
			this.multiplier = multiplier;
		}

		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double durationMultiplier(final LivingEntity caster) {
			return this.multiplier;
		}
	}

	/** A 30-minute buff must read in minutes, not as 1800 seconds. */
	@GameTest
	public void tooltipDurationsUseMinutes(final GameTestHelper helper) {
		String buff = SpellTooltip.duration(SpellNumbers.BERSERK.duration().ticks());
		String zone = SpellTooltip.duration(SpellNumbers.SANCTUARY.duration().ticks());
		helper.assertTrue("30 minutes".equals(buff), "a 30-minute buff reads " + buff);
		helper.assertTrue("30 seconds".equals(zone), "a 30-second zone reads " + zone);
		helper.assertTrue("1 minute".equals(SpellTooltip.duration(1200)), "1200 ticks read " + SpellTooltip.duration(1200));
		helper.assertTrue("1 second".equals(SpellTooltip.duration(20)), "20 ticks read " + SpellTooltip.duration(20));
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void playerDurations(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		try {
			RpgBridge.setStats(new Stub(1.0));
			ServerPlayer caster = aimedCaster(helper);
			Mob zombie = zombie(helper, 2, 5);

			hostile(helper, caster, zombie, wrong, "frostbind", MobEffects.SLOWNESS, 160);
			hostile(helper, caster, zombie, wrong, "briar_snare", MobEffects.SLOWNESS, 160);
			hostile(helper, caster, zombie, wrong, "wither_seal", MobEffects.WITHER, 300);
			Mob pig = pig(helper, 3, 5);
			apply(caster, "venom_dart", pig, null);
			expect(wrong, "venom_dart", pig.getEffect(MobEffects.POISON), 300);
			hostile(helper, caster, zombie, wrong, "blinding_flash", MobEffects.BLINDNESS, 300);
			expect(wrong, "blinding_flash glow", zombie.getEffect(MobEffects.GLOWING), 600);

			zombie.clearFire();
			use(helper, caster, "ember_brand");
			expectInt(wrong, "ember_brand burn", zombie.getRemainingFireTicks(), 600);
			zombie.clearFire();
			use(helper, caster, "spark_shot");
			expectInt(wrong, "spark_shot burn", zombie.getRemainingFireTicks(), 120);

			zombie.setTicksFrozen(0);
			use(helper, caster, "ice_lance");
			expectInt(wrong, "ice_lance freeze", zombie.getTicksFrozen(), zombie.getTicksRequiredToFreeze() + 2 * 280);

			zombie.removeAllEffects();
			use(helper, caster, "time_lock");
			expect(wrong, "time_lock", zombie.getEffect(MobEffects.SLOWNESS), 200);
			zombie.removeAllEffects();
			use(helper, caster, "void_well");
			expect(wrong, "void_well", zombie.getEffect(MobEffects.SLOWNESS), 120);
			zombie.removeAllEffects();
			use(helper, caster, "gravity");
			expect(wrong, "gravity", zombie.getEffect(MobEffects.SLOWNESS), 320);
			zombie.removeAllEffects();
			use(helper, caster, "frost_nova");
			expect(wrong, "frost_nova", zombie.getEffect(MobEffects.SLOWNESS), 120);

			zombie.removeAllEffects();
			int foes = MoreSpells.hostiles(helper.getLevel(), caster.position(), 6.0).size();
			use(helper, caster, "sapping_hex");
			expect(wrong, "sapping_hex weakness", zombie.getEffect(MobEffects.WEAKNESS), Math.min(900, 180 * foes));
			expect(wrong, "sapping_hex strength", caster.getEffect(MobEffects.STRENGTH), Math.min(900, 180 * foes));

			caster.removeAllEffects();
			self(helper, caster, wrong, "stoneguard", MobEffects.RESISTANCE, HALF_HOUR);
			self(helper, caster, wrong, "owls_gaze", MobEffects.NIGHT_VISION, HALF_HOUR);
			self(helper, caster, wrong, "slimebound", MobEffects.JUMP_BOOST, HALF_HOUR);
			self(helper, caster, wrong, "phoenix_mantle", MobEffects.FIRE_RESISTANCE, HALF_HOUR);
			self(helper, caster, wrong, "aegis_of_the_deep", MobEffects.ABSORPTION, HALF_HOUR);
			self(helper, caster, wrong, "quicksilver", MobEffects.SPEED, HALF_HOUR);
			self(helper, caster, wrong, "tide_lungs", MobEffects.WATER_BREATHING, HALF_HOUR);
			expect(wrong, "tide_lungs grace", caster.getEffect(MobEffects.DOLPHINS_GRACE), HALF_HOUR);
			self(helper, caster, wrong, "berserk", MobEffects.STRENGTH, HALF_HOUR);
			self(helper, caster, wrong, "haste_rune", MobEffects.HASTE, HALF_HOUR);
			self(helper, caster, wrong, "shadow_veil", MobEffects.INVISIBILITY, HALF_HOUR);
			self(helper, caster, wrong, "bulwark_hymn", MobEffects.ABSORPTION, HALF_HOUR);
			self(helper, caster, wrong, "tailwind", MobEffects.SPEED, HALF_HOUR);
			self(helper, caster, wrong, "battle_hymn", MobEffects.STRENGTH, HALF_HOUR);
			self(helper, caster, wrong, "life_bloom", MobEffects.REGENERATION, 300);
			expectInt(wrong, "shadow_veil break stop", MoreSpells.VEIL_BREAK_SLOW_TICKS, 40);

			caster.removeAllEffects();
			Vec3 spot = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 8)));
			use(helper, caster, "plague_cloud");
			AreaEffectCloud cloud = helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(3.0),
				candidate -> candidate.getOwner() == caster).stream().findFirst().orElse(null);
			if (cloud == null) {
				wrong.add("plague_cloud left no cloud");
			} else {
				expectInt(wrong, "plague_cloud cloud", cloud.getDuration(), 300);
				cloud.discard();
			}
			LOG.info("[SPELLTEST] class=RpgDurationsGameTest test=playerDurations RESULT wrong={}", wrong);
			helper.assertTrue(wrong.isEmpty(), "wrong durations: " + wrong);
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void buffsIgnoreInt(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		try {
			RpgBridge.setStats(new Stub(2.0));
			ServerPlayer caster = aimedCaster(helper);
			self(helper, caster, wrong, "berserk", MobEffects.STRENGTH, HALF_HOUR);
			self(helper, caster, wrong, "battle_hymn", MobEffects.STRENGTH, HALF_HOUR);
			self(helper, caster, wrong, "tide_lungs", MobEffects.WATER_BREATHING, HALF_HOUR);
			expect(wrong, "tide_lungs grace", caster.getEffect(MobEffects.DOLPHINS_GRACE), HALF_HOUR);
			Mob pig = pig(helper, 3, 5);
			apply(caster, "venom_dart", pig, null);
			expect(wrong, "venom_dart", pig.getEffect(MobEffects.POISON), 600);
			LOG.info("[SPELLTEST] class=RpgDurationsGameTest test=buffsIgnoreInt RESULT wrong={}", wrong);
			helper.assertTrue(wrong.isEmpty(), "wrong durations at multiplier 2.0: " + wrong);
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void eliteHalvesControl(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		try {
			RpgBridge.setStats(new Stub(1.0));
			ServerPlayer caster = aimedCaster(helper);
			Mob plain = zombie(helper, 2, 5);
			Mob elite = zombie(helper, 3, 5);
			Mob elitePig = pig(helper, 4, 5);
			elitePig.getAttribute(Attributes.SCALE).addPermanentModifier(new AttributeModifier(
				Identifier.fromNamespaceAndPath("mod-stella-mobs", "elite_scale"), 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			elite.getAttribute(Attributes.SCALE).addPermanentModifier(new AttributeModifier(
				Identifier.fromNamespaceAndPath("mod-stella-mobs", "elite_scale"), 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

			apply(caster, "frostbind", plain, null);
			apply(caster, "frostbind", elite, null);
			expect(wrong, "frostbind plain", plain.getEffect(MobEffects.SLOWNESS), 160);
			expect(wrong, "frostbind elite", elite.getEffect(MobEffects.SLOWNESS), 80);

			plain.removeAllEffects();
			elite.removeAllEffects();
			apply(caster, "frost_nova", null, null);
			expect(wrong, "frost_nova plain", plain.getEffect(MobEffects.SLOWNESS), 120);
			expect(wrong, "frost_nova elite", elite.getEffect(MobEffects.SLOWNESS), 60);

			plain.removeAllEffects();
			elite.removeAllEffects();
			apply(caster, "venom_dart", elitePig, null);
			expect(wrong, "venom_dart elite keeps full length", elitePig.getEffect(MobEffects.POISON), 300);
			LOG.info("[SPELLTEST] class=RpgDurationsGameTest test=eliteHalvesControl RESULT wrong={}", wrong);
			helper.assertTrue(wrong.isEmpty(), "elite durations: " + wrong);
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void zones(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		try {
			RpgBridge.setStats(new Stub(1.0));
			ServerPlayer caster = aimedCaster(helper);
			int now = helper.getLevel().getServer().getTickCount();
			use(helper, caster, "sanctuary");
			List<SpellTimers.Zone> zones = SpellTimers.zonesOf(caster.getUUID());
			expectInt(wrong, "sanctuary zones", zones.size(), 1);
			if (zones.size() == 1) {
				expectInt(wrong, "sanctuary length", zones.get(0).endsAt() - now, 600);
				expectInt(wrong, "sanctuary radius", (int) Math.round(zones.get(0).radius() * 10.0), 60);
			}
			use(helper, caster, "flame_ring");
			SpellTimers.Aura aura = SpellTimers.flameAuraOf(caster.getUUID());
			if (aura == null) {
				wrong.add("flame_ring left no aura");
			} else {
				expectInt(wrong, "flame_ring length", aura.endsAt() - now, 6000);
				expectInt(wrong, "flame_ring radius", (int) Math.round(aura.radius() * 10.0), 50);
				expectInt(wrong, "flame_ring burn", aura.burnTicks(), 180);
			}
			LOG.info("[SPELLTEST] class=RpgDurationsGameTest test=zones RESULT wrong={}", wrong);
			helper.assertTrue(wrong.isEmpty(), "zone numbers: " + wrong);
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void flameRingRecast(final GameTestHelper helper) {
		try {
			ServerPlayer caster = aimedCaster(helper);
			int now = helper.getLevel().getServer().getTickCount();
			RpgBridge.setStats(new Stub(1.0));
			apply(caster, "flame_ring", null, null);
			SpellTimers.Aura first = SpellTimers.flameAuraOf(caster.getUUID());
			helper.assertTrue(first != null, "the first cast left no aura");
			RpgBridge.setStats(new Stub(0.5));
			apply(caster, "flame_ring", null, null);
			SpellTimers.Aura second = SpellTimers.flameAuraOf(caster.getUUID());
			LOG.info("[SPELLTEST] class=RpgDurationsGameTest test=flameRingRecast RESULT first={} second={}", first == null ? -1 : first.endsAt() - now, second == null ? -1 : second.endsAt() - now);
			helper.assertTrue(second != null && second.endsAt() - now == 3000, "the recast left " + second);
			helper.assertTrue(first != second, "the recast kept the first aura");
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void mobKeepsOldDurations(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		floor(helper);
		net.minecraft.world.entity.monster.Witch witch = helper.spawnMob(EntityTypes.WITCH, 2, 1, 2).withNoFreeWill().spawn();
		Mob ally = zombie(helper, 3, 2);
		Mob foe = zombie(helper, 2, 4);
		witch.setTarget(foe);

		MobSpells.support(witch, "berserk");
		expect(wrong, "witch berserk ally", ally.getEffect(MobEffects.STRENGTH), 200);
		ally.removeAllEffects();
		MobSpells.support(witch, "battle_hymn");
		expect(wrong, "witch battle_hymn ally", ally.getEffect(MobEffects.STRENGTH), 400);
		ally.removeAllEffects();
		MobSpells.support(witch, "quicksilver");
		expect(wrong, "witch quicksilver ally", ally.getEffect(MobEffects.SPEED), 400);
		ally.removeAllEffects();
		MobSpells.support(witch, "tailwind");
		expect(wrong, "witch tailwind ally", ally.getEffect(MobEffects.SPEED), 600);

		Mob pig = pig(helper, 4, 3);
		MobSpells.castAt(witch, "venom_dart", pig);
		expect(wrong, "witch venom_dart", pig.getEffect(MobEffects.POISON), 100);
		MobSpells.castAt(witch, "gravity", foe);
		expect(wrong, "witch gravity", foe.getEffect(MobEffects.SLOWNESS), 160);
		foe.removeAllEffects();
		MobSpells.castAt(witch, "frost_nova", foe);
		expect(wrong, "witch frost_nova", foe.getEffect(MobEffects.SLOWNESS), 60);
		foe.removeAllEffects();
		witch.removeAllEffects();
		MobSpells.castAt(witch, "sapping_hex", foe);
		expect(wrong, "witch sapping_hex", foe.getEffect(MobEffects.WEAKNESS), 60);
		foe.clearFire();
		MobSpells.castAt(witch, "ember_brand", foe);
		expectInt(wrong, "witch ember_brand", foe.getRemainingFireTicks(), 200);

		Mob distant = zombie(helper, 2, 7);
		witch.setTarget(distant);
		distant.clearFire();
		MobSpells.castAt(witch, "flame_ring", distant);
		expectInt(wrong, "witch flame_ring reach (radius 3)", distant.getRemainingFireTicks(), 0);

		LOG.info("[SPELLTEST] class=RpgDurationsGameTest test=mobKeepsOldDurations RESULT wrong={}", wrong);
		helper.assertTrue(wrong.isEmpty(), "mob numbers: " + wrong);
		helper.succeed();
	}

	private static void hostile(
		final GameTestHelper helper, final ServerPlayer caster, final Mob target, final List<String> wrong,
		final String spell, final Holder<MobEffect> effect, final int expected
	) {
		target.removeAllEffects();
		use(helper, caster, spell);
		expect(wrong, spell, target.getEffect(effect), expected);
	}

	private static void self(
		final GameTestHelper helper, final ServerPlayer caster, final List<String> wrong,
		final String spell, final Holder<MobEffect> effect, final int expected
	) {
		caster.removeEffect(effect);
		use(helper, caster, spell);
		expect(wrong, spell, caster.getEffect(effect), expected);
	}

	private static void expect(final List<String> wrong, final String label, final MobEffectInstance applied, final int expected) {
		if (applied == null || applied.getDuration() != expected) {
			wrong.add(label + "=" + (applied == null ? "none" : applied.getDuration()) + " want " + expected);
		}
	}

	private static void expectInt(final List<String> wrong, final String label, final int actual, final int expected) {
		if (actual != expected) {
			wrong.add(label + "=" + actual + " want " + expected);
		}
	}

	private static void use(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		InteractionResult result = book.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, spell + " did not cast: " + result);
	}

	// The cast's own effect without the book's cooldown, for a second cast of one spell.
	private static void apply(final ServerPlayer caster, final String spell, final Mob target, final Vec3 point) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		ItemStack stack = book.getDefaultInstance();
		SpellModifiers.CastModifiers modifiers = RpgBridge.grow(caster, spell,
			SpellModifiers.resolve(stack, ItemStack.EMPTY, ItemStack.EMPTY));
		book.definition().effect().apply(caster, target, point, modifiers);
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

	private static ServerPlayer aimedCaster(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		helper.setBlock(2, 1, 8, Blocks.STONE);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 8))));
		return player;
	}

	private static Mob pig(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.PIG, x, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.setHealth(1000.0F);
		return mob;
	}

	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(1000.0F);
		return mob;
	}
}
