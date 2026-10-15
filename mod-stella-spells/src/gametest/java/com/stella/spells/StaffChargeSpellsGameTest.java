package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Full-charge imbue gating, Memorize, Soul Vulcan and friendly pass-through for the staff normal attack. */
public class StaffChargeSpellsGameTest {
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final Identifier FROSTBIND = Identifier.parse("mod-stella-spells:frostbind");
	private static final float HEALTH = 1000.0F;

	@GameTest(maxTicks = 200)
	public void onlyAFullyChargedStaffAttackTriggersImbuedSpells(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.setItemInHand(InteractionHand.MAIN_HAND, imbuedStaff(SPARK_SHOT));
		Mob zombie = foe(helper, 2, 6);
		List<Identifier> casts = record(caster);
		helper.runAfterDelay(5L, () -> {
			try {
				SpellImbue.setProcRoll(() -> 0.0);
				StaffBolt.fire(caster, 0.99F);
				int partial = casts.size();
				SpellImbue.clearCooldowns(caster.getUUID());
				zombie.damageCooldownTime = 0;
				StaffBolt.fire(caster, 1.0F);
				helper.assertTrue(partial == 0, "a charge of 0.99 triggered " + partial + " imbued spells");
				helper.assertTrue(casts.size() == 1, "a full charge triggered " + casts.size() + " imbued spells, not 1");
			} finally {
				SpellImbue.setProcRoll(() -> 0.0);
			}
			leave(helper, caster);
		});
	}

	@GameTest(maxTicks = 200)
	public void memorizeMakesTheNextTenFullAttacksCastEveryImbuedSpell(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		ItemStack staff = imbuedStaff(SPARK_SHOT, FROSTBIND);
		caster.setItemInHand(InteractionHand.MAIN_HAND, staff);
		Mob zombie = foe(helper, 2, 6);
		List<Identifier> casts = record(caster);
		helper.runAfterDelay(5L, () -> {
			try {
				SpellImbue.setLukSource((holder, key) -> 0.0);
				// 0.99 never beats the 20 percent staff chance, so only Memorize can cast.
				SpellImbue.setProcRoll(() -> 0.99);
				helper.assertTrue(cast("memorize", caster), "Memorize did not cast");
				helper.assertTrue(Memorize.left(caster.getUUID()) == Memorize.CHARGES, "Memorize holds " + Memorize.left(caster.getUUID()));
				StaffBolt.fire(caster, 0.5F);
				helper.assertTrue(casts.isEmpty() && Memorize.left(caster.getUUID()) == Memorize.CHARGES,
					"a half charge cast " + casts.size() + " or spent a Memorize charge");
				for (int swing = 1; swing <= Memorize.CHARGES; swing++) {
					SpellImbue.clearCooldowns(caster.getUUID());
					zombie.damageCooldownTime = 0;
					zombie.setHealth(HEALTH);
					zombie.removeAllEffects();
					StaffBolt.fire(caster, 1.0F);
					helper.assertTrue(casts.size() == 2 * swing, "full swing " + swing + " left " + casts.size() + " casts, not " + 2 * swing);
				}
				helper.assertTrue(Memorize.left(caster.getUUID()) == 0, "Memorize still holds " + Memorize.left(caster.getUUID()));
				zombie.damageCooldownTime = 0;
				StaffBolt.fire(caster, 1.0F);
				helper.assertTrue(casts.size() == 2 * Memorize.CHARGES, "an eleventh swing still cast: " + casts.size());
			} finally {
				SpellImbue.setProcRoll(() -> 0.0);
				SpellImbue.setLukSource(null);
			}
			leave(helper, caster);
		});
	}

	@GameTest(maxTicks = 300)
	public void soulVulcanFiresFiveHomingBoltsAtTheTargetAndFoesAroundIt(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		ItemStack staff = imbuedStaff(SPARK_SHOT);
		Mob first = foe(helper, 2, 6);
		Mob left = foe(helper, 0, 7);
		Mob right = foe(helper, 4, 7);
		List<Identifier> casts = record(caster);
		helper.runAfterDelay(5L, () -> {
			SpellImbue.setLukSource((holder, key) -> 0.0);
			SpellImbue.setProcRoll(() -> 0.0);
			caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			boolean bare = cast("soul_vulcan", caster);
			caster.setItemInHand(InteractionHand.MAIN_HAND, staff);
			boolean armed = cast("soul_vulcan", caster);
			helper.assertTrue(!bare && armed, "Soul Vulcan needs a staff: bare=" + bare + " armed=" + armed);
			// A half charge still fires the bolts and hurts, but triggers no imbued spell.
			StaffBolt.fire(caster, 0.5F);
			helper.runAfterDelay(40L, () -> {
				helper.assertTrue(casts.isEmpty(), "a half charge triggered " + casts.size() + " imbued spells");
				helper.assertTrue(left.getHealth() < HEALTH && right.getHealth() < HEALTH,
					"the bolts missed the foes beside the target: " + left.getHealth() + " " + right.getHealth());
				SpellImbue.clearCooldowns(caster.getUUID());
				StaffBolt.fire(caster, 1.0F);
				helper.runAfterDelay(40L, () -> {
					SpellImbue.setProcRoll(() -> 0.0);
					SpellImbue.setLukSource(null);
					// One main bolt and five extra ones, each triggering the imbued book.
					helper.assertTrue(casts.size() == 1 + SoulVulcan.EXTRA_BOLTS,
						"a full swing triggered " + casts.size() + " imbued spells, not " + (1 + SoulVulcan.EXTRA_BOLTS)
							+ " pending=" + SoulVulcan.pending() + " first=" + first.position() + " left=" + left.position() + " right=" + right.position()
							+ " hp=" + first.getHealth() + "/" + left.getHealth() + "/" + right.getHealth());
					leave(helper, caster);
				});
			});
		});
	}

	@GameTest(maxTicks = 100)
	public void staffBoltsPassThroughFriendliesAndAMeleeBlowOnOneLands(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_ROD));
		Mob villager = helper.spawnMob(EntityTypes.VILLAGER, 2, 1, 4).withNoFreeWill().spawn();
		villager.setNoGravity(true);
		Mob golem = helper.spawnMob(EntityTypes.IRON_GOLEM, 2, 1, 5).withNoFreeWill().spawn();
		golem.setNoGravity(true);
		Mob zombie = foe(helper, 2, 7);
		helper.runAfterDelay(5L, () -> {
			float villagerBefore = villager.getHealth();
			float golemBefore = golem.getHealth();
			LivingEntity struck = StaffBolt.fire(caster, 1.0F);
			helper.assertTrue(struck == zombie, "the bolt stopped at " + struck);
			helper.assertTrue(villager.getHealth() == villagerBefore && golem.getHealth() == golemBefore, "the bolt hurt a friendly unit");
			float zombieBefore = zombie.getHealth();
			InteractionResult melee = AttackEntityCallback.EVENT.invoker().interact(caster, helper.getLevel(), InteractionHand.MAIN_HAND, villager, null);
			helper.assertTrue(melee == InteractionResult.PASS, "a staff blow on a villager was " + melee + ", not a melee hit");
			helper.assertTrue(zombie.getHealth() == zombieBefore, "the blow on a villager also fired a bolt");
			leave(helper, caster);
		});
	}

	private static boolean cast(final String path, final ServerPlayer caster) {
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		return SpellbookCatalog.get(Identifier.parse("mod-stella-spells:" + path)).effect().apply(caster, null, caster.position(), modifiers);
	}

	private static List<Identifier> record(final ServerPlayer caster) {
		List<Identifier> casts = new ArrayList<>();
		SpellImbue.addRecorder((who, spell, victim) -> {
			if (who == caster) {
				casts.add(spell);
			}
		});
		return casts;
	}

	private static ItemStack imbuedStaff(final Identifier... spells) {
		ItemStack staff = new ItemStack(Items.BLAZE_ROD);
		staff.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(List.of(spells)));
		return staff;
	}

	private static Mob foe(final GameTestHelper helper, final int x, final int z) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, 1, z).withNoFreeWill().spawn();
		zombie.setNoGravity(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.setHealth(HEALTH);
		return zombie;
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		caster.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		return caster;
	}

	private static void leave(final GameTestHelper helper, final ServerPlayer caster) {
		helper.getLevel().getServer().getPlayerList().remove(caster);
		helper.succeed();
	}
}
