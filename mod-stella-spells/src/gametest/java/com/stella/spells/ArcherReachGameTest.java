package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every archer skill reaches 24 blocks and no further, and a cast plays the draw of the bow or crossbow in the
 * caster's hand without firing it. The reach is read through a full cast, so the targeting rule is what is proved.
 * The arena is closed by a ring of barriers, so a case that aims past it opens the ceiling for its length.
 */
public class ArcherReachGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.archerreach");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final int WEAPON_SLOT = 35;
	private static final int PACK_SLOT = 20;
	/** Facing +Z is yaw 0 and facing +X is yaw -90, so one caster can aim at two lanes. */
	private static final float FACING_Z = 0.0F;
	private static final float FACING_X = -90.0F;
	/**
	 * The reach is read along a steep upward look, so the ray leaves through the opened ceiling and never meets a
	 * neighbouring test's blocks or mobs, which a level ray at this distance does.
	 */
	private static final float LOOKING_UP = -60.0F;
	/** The two distances the skills are held to: a target 30 blocks away is refused and one 20 blocks away is hit. */
	private static final double PAST_REACH = 30.0;
	private static final double INSIDE_REACH = 20.0;

	@GameTest(maxTicks = 100)
	public void anArcherSkillRefusesAHostilePastTwentyFourBlocksAndFiresAtTwenty(final GameTestHelper helper) {
		helper.assertTrue(INSIDE_REACH < ArcherSkills.SKILL_RANGE_BLOCKS && ArcherSkills.SKILL_RANGE_BLOCKS < PAST_REACH
			&& ArcherSkills.SKILL_RANGE_BLOCKS == 24, "the shipped reach is " + ArcherSkills.SKILL_RANGE_BLOCKS + ", not 24");
		ServerPlayer caster = archer(helper);
		SpellbookItem book = SkillSchoolGameTest.book("rapid_fire");
		Mob far = floatingFoe(helper, along(caster, FACING_Z, PAST_REACH));
		Mob near = floatingFoe(helper, along(caster, FACING_X, INSIDE_REACH));
		List<BlockPos> ceiling = ArenaCeiling.open(helper);
		Findable.then(helper, List.of(far, near), () -> {
			try {
				caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
				caster.setXRot(LOOKING_UP);
				caster.setYRot(FACING_Z);
				SpellCasting.CastResult past = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
				float farLost = 1000.0F - far.getHealth();
				SkillSchoolGameTest.clearCooldown(caster);
				caster.setYRot(FACING_X);
				SpellCasting.CastResult inside = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
				float nearLost = 1000.0F - near.getHealth();
				LOG.info("[BEHAVIOR] test=anArcherSkillRefusesAHostilePastTwentyFourBlocksAndFiresAtTwenty RESULT past={} at={} inside={} at={} farLost={} nearLost={}",
					past, PAST_REACH, inside, INSIDE_REACH, farLost, nearLost);
				helper.assertTrue(past == SpellCasting.CastResult.NO_TARGET, "a hostile " + PAST_REACH + " blocks away read " + past);
				helper.assertTrue(farLost == 0.0F, "the hostile past the reach was hit");
				helper.assertTrue(inside == SpellCasting.CastResult.CAST, "a hostile " + INSIDE_REACH + " blocks away read " + inside);
				helper.assertTrue(nearLost > 0.0F, "the hostile inside the reach took nothing");
			} finally {
				far.discard();
				near.discard();
				ArenaCeiling.close(helper, ceiling);
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void anArcherSkillRefusesBlockPastTwentyFourBlocksAndFiresAtTwenty(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		SpellbookItem book = SkillSchoolGameTest.book("ankle_snare");
		ServerLevel level = helper.getLevel();
		BlockPos farBlock = BlockPos.containing(along(caster, FACING_Z, PAST_REACH));
		BlockPos nearBlock = BlockPos.containing(along(caster, FACING_X, INSIDE_REACH));
		List<BlockPos> ceiling = ArenaCeiling.open(helper);
		List<ChunkPos> forced = new ArrayList<>();
		for (BlockPos block : List.of(farBlock, nearBlock)) {
			ChunkPos chunk = ChunkPos.containing(block);
			if (!forced.contains(chunk) && !level.getForceLoadedChunks().contains(chunk.pack())) {
				level.setChunkForced(chunk.x(), chunk.z(), true);
				forced.add(chunk);
			}
			level.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
		}
		try {
			caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
			caster.setXRot(LOOKING_UP);
			caster.setYRot(FACING_Z);
			SpellCasting.CastResult past = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
			SkillSchoolGameTest.clearCooldown(caster);
			caster.setYRot(FACING_X);
			SpellCasting.CastResult inside = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
			int traps = ArcherSkills.ankleSnares(caster);
			LOG.info("[BEHAVIOR] test=anArcherSkillRefusesBlockPastTwentyFourBlocksAndFiresAtTwenty RESULT past={} inside={} traps={}", past, inside, traps);
			helper.assertTrue(past == SpellCasting.CastResult.NO_TARGET, "a block " + PAST_REACH + " blocks away read " + past);
			helper.assertTrue(inside == SpellCasting.CastResult.CAST, "a block " + INSIDE_REACH + " blocks away read " + inside);
			helper.assertTrue(traps == 1, "the trap count after both casts is " + traps);
		} finally {
			ArcherSkills.clearFor(caster);
			level.setBlock(farBlock, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(nearBlock, Blocks.AIR.defaultBlockState(), 3);
			ArenaCeiling.close(helper, ceiling);
			for (ChunkPos chunk : forced) {
				level.setChunkForced(chunk.x(), chunk.z(), false);
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void anArcherCastDrawsTheHeldBowAndFiresNothing(final GameTestHelper helper) {
		drawCase(helper, Items.BOW, "anArcherCastDrawsTheHeldBowAndFiresNothing");
	}

	@GameTest(maxTicks = 60)
	public void anArcherCastDrawsTheHeldCrossbowAndFiresNothing(final GameTestHelper helper) {
		drawCase(helper, Items.CROSSBOW, "anArcherCastDrawsTheHeldCrossbowAndFiresNothing");
	}

	@GameTest(maxTicks = 60)
	public void aMageSpellAndAnArcherCastWithNoWeaponInHandDrawNothing(final GameTestHelper helper) {
		ServerPlayer mage = archer(helper);
		mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		mage.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(SkillSchoolGameTest.book("quicksilver")));
		SpellCasting.CastResult mageResult = SpellCasting.cast(mage, SkillSchoolGameTest.book("quicksilver"), InteractionHand.OFF_HAND);
		ServerPlayer slotOnly = archer(helper);
		slotOnly.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SkillSchoolGameTest.book("eagle_eye")));
		SpellCasting.CastResult slotResult = SpellCasting.cast(slotOnly, SkillSchoolGameTest.book("eagle_eye"), InteractionHand.MAIN_HAND);
		int[] drawn = {0, 0};
		helper.onEachTick(() -> {
			drawn[0] += mage.isUsingItem() ? 1 : 0;
			drawn[1] += slotOnly.isUsingItem() ? 1 : 0;
		});
		helper.runAfterDelay(ArcherDraw.DRAW_TICKS + 8L, () -> {
			LOG.info("[BEHAVIOR] test=aMageSpellAndAnArcherCastWithNoWeaponInHandDrawNothing RESULT mage={} drawn={} slotOnly={} drawn={}",
				mageResult, drawn[0], slotResult, drawn[1]);
			helper.assertTrue(mageResult == SpellCasting.CastResult.CAST && slotResult == SpellCasting.CastResult.CAST, "a control cast never landed");
			helper.assertTrue(drawn[0] == 0, "a mage spell drew the bow for " + drawn[0] + " ticks");
			helper.assertTrue(drawn[1] == 0, "a bow in the weapon slot only was drawn for " + drawn[1] + " ticks");
			ArcherSkills.clearFor(slotOnly);
			helper.succeed();
		});
	}

	private static void drawCase(final GameTestHelper helper, final Item weapon, final String test) {
		ServerPlayer caster = archer(helper);
		caster.getInventory().setItem(PACK_SLOT, new ItemStack(Items.ARROW, 32));
		ItemStack held = new ItemStack(weapon);
		caster.setItemInHand(InteractionHand.MAIN_HAND, held);
		SpellbookItem book = SkillSchoolGameTest.book("eagle_eye");
		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(book));
		SpellCasting.CastResult result = SpellCasting.cast(caster, book, InteractionHand.OFF_HAND);
		int[] drawn = {0};
		boolean[] released = {false};
		helper.onEachTick(() -> {
			if (caster.isUsingItem() && caster.getUseItem().is(weapon) && caster.getUsedItemHand() == InteractionHand.MAIN_HAND) {
				drawn[0]++;
				// A release here would loose an arrow from a bow; the cast must hold the draw against it.
				if (weapon == Items.BOW && !released[0] && drawn[0] >= 3) {
					released[0] = true;
					caster.releaseUsingItem();
				}
			}
		});
		helper.runAfterDelay(ArcherDraw.DRAW_TICKS + 10L, () -> {
			int arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, caster.getBoundingBox().inflate(64.0),
				arrow -> arrow.getOwner() == caster).size();
			int ammo = caster.getInventory().getItem(PACK_SLOT).getCount();
			LOG.info("[BEHAVIOR] test={} RESULT result={} drawnTicks={} wanted={} released={} arrows={} ammo={} stillUsing={} damage={} charged={}",
				test, result, drawn[0], ArcherDraw.DRAW_TICKS, released[0], arrows, ammo, caster.isUsingItem(), held.getDamageValue(), CrossbowItem.isCharged(held));
			helper.assertTrue(result == SpellCasting.CastResult.CAST, "the control cast read " + result);
			helper.assertTrue(Math.abs(drawn[0] - ArcherDraw.DRAW_TICKS) <= 1,
				"the " + weapon + " was drawn for " + drawn[0] + " ticks, not " + ArcherDraw.DRAW_TICKS);
			helper.assertTrue(weapon != Items.BOW || released[0], "the case never tried a release against the draw");
			helper.assertTrue(arrows == 0, "the draw loosed " + arrows + " arrows");
			helper.assertTrue(ammo == 32, "the draw spent ammunition: " + ammo + " of 32 left");
			helper.assertTrue(held.getDamageValue() == 0, "the draw wore the weapon");
			helper.assertFalse(CrossbowItem.isCharged(held), "the draw loaded the crossbow");
			helper.assertFalse(caster.isUsingItem(), "the draw never ended");
			ArcherSkills.clearFor(caster);
			helper.succeed();
		});
	}

	// The point this far along the caster's look at the given yaw, steeply upward.
	private static Vec3 along(final ServerPlayer caster, final float yaw, final double distance) {
		return caster.getEyePosition().add(ServerPlayer.calculateViewVector(LOOKING_UP, yaw).scale(distance));
	}

	// A hostile floating with its middle on the point, so the look ray runs through it.
	private static Mob floatingFoe(final GameTestHelper helper, final Vec3 centre) {
		Mob foe = FighterBooksGameTest.foe(helper, 0.0, 0.0);
		foe.snapTo(centre.x, centre.y - foe.getBbHeight() / 2.0, centre.z, 180.0F, 0.0F);
		return foe;
	}

	/** A survival caster with a bow in the weapon slot, so the school's gate lets the cast through. */
	private static ServerPlayer archer(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.removeAllEffects();
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		Vec3 at = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(FACING_Z);
		caster.setXRot(0.0F);
		return caster;
	}

	/** The first-person pull and the third-person pose are drawn by a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theDrawLooksRightInFirstAndThirdPersonRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] hold a bow, then a crossbow, and cast any archer skill. Your arm pulls the weapon for about a third of a second and nothing is fired. A second player sees the same pose. This harness has no client.");
	}
}
