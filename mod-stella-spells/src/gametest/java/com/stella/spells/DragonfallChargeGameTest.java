package com.stella.spells;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dragonfall drops a burning fire charge twice the size of its model on the spot. The charge is an item display,
 * which any client draws, and it is gone once it has landed. The damage is Dragonfall's own and stays instant.
 */
public class DragonfallChargeGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.dragonfallcharge");
	/** Looking down this far from the eye puts the aimed floor point about five blocks ahead, inside the arena. */
	private static final float PITCH = 25.0F;

	@GameTest(maxTicks = 80)
	public void dragonfallDropsAFireChargeOfTwiceTheModelSizeThatFallsAndLands(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.removeAllEffects();
		caster.setXRot(PITCH);
		SpellbookItem book = SkillSchoolGameTest.book("dragonfall");
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		AABB arena = helper.getBounds().inflate(4.0);
		int before = helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, arena).size();
		SpellCasting.CastResult result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		helper.runAfterDelay(3L, () -> {
			List<Display.ItemDisplay> charges = helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, arena,
				display -> display.getItemStack().is(Items.FIRE_CHARGE));
			helper.assertTrue(result == SpellCasting.CastResult.CAST, "the cast read " + result);
			helper.assertTrue(charges.size() == 1, charges.size() + " fire charges are falling, not one (" + before + " displays before)");
			Display.ItemDisplay charge = charges.get(0);
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
			charge.saveWithoutId(out);
			CompoundTag tag = out.buildResult();
			CompoundTag transformation = tag.getCompoundOrEmpty("transformation");
			List<Float> scale = transformation.getListOrEmpty("scale").stream().map(entry -> entry.asFloat().orElse(0.0F)).toList();
			int duration = tag.getIntOr("interpolation_duration", -1);
			List<Float> lift = transformation.getListOrEmpty("translation").stream().map(entry -> entry.asFloat().orElse(-1.0F)).toList();
			LOG.info("[BEHAVIOR] test=dragonfallDropsAFireChargeOfTwiceTheModelSizeThatFallsAndLands RESULT scale={} lift={} duration={} y={} fallBlocks={}",
				scale, lift, duration, charge.getY(), DragonCharge.FALL_BLOCKS);
			helper.assertTrue(scale.size() == 3 && scale.stream().allMatch(axis -> axis == 2.0F), "the charge is drawn at scale " + scale + ", not twice the model");
			helper.assertTrue(DragonCharge.SCALE == 2.0F, "the shipped scale is " + DragonCharge.SCALE);
			helper.assertTrue(duration == DragonCharge.FALL_TICKS, "the fall is interpolated over " + duration + " ticks, not " + DragonCharge.FALL_TICKS);
			helper.assertTrue(lift.size() == 3 && Math.abs(lift.get(1)) < 0.5F, "the charge is not told to land: its offset is " + lift);
			helper.runAfterDelay(DragonCharge.FALL_TICKS + 6L, () -> {
				LOG.info("[BEHAVIOR] test=dragonfallDropsAFireChargeOfTwiceTheModelSizeThatFallsAndLands RESULT removed={}", charge.isRemoved());
				helper.assertTrue(charge.isRemoved(), "the charge stayed in the world after landing");
				helper.succeed();
			});
		});
	}

	/** How the charge burns and how large it reads on screen is seen on a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theChargeBurnsAndLooksHugeOnAClientRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] cast Dragonfall at a spot 10 blocks away. A huge fire charge, twice the model size, trailing flames and smoke, falls onto the spot. This harness has no client.");
	}
}
