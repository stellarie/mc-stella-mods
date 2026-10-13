package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dragonfall drops a burning fire charge twice the size of its model on the spot. The charge is an item display,
 * which any client draws, and it is gone once it has landed. The spell's one hit lands on the tick the charge does.
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
		// A hostile stands where the look ray meets the floor, before the cast, so a hit at cast time would find it.
		Vec3 eye = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		double floor = helper.absoluteVec(new Vec3(0.0, 1.0, 0.0)).y;
		Vec3 aimed = eye.add(look.scale((eye.y - floor) / -look.y));
		Mob foe = FighterBooksGameTest.foe(helper, 0.0, 0.0);
		foe.snapTo(aimed.x, floor, aimed.z, 180.0F, 0.0F);
		float full = foe.getHealth();
		SpellCasting.CastResult result = SpellCasting.cast(caster, book, InteractionHand.MAIN_HAND);
		List<Display.ItemDisplay> charges = helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, arena,
			display -> display.getItemStack().is(Items.FIRE_CHARGE));
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "the cast read " + result);
		helper.assertTrue(charges.size() == 1, charges.size() + " fire charges are falling, not one (" + before + " displays before)");
		Display.ItemDisplay charge = charges.get(0);
		List<Long> drops = new ArrayList<>();
		List<Float> amounts = new ArrayList<>();
		long[] removedAt = {-1L};
		float[] last = {full};
		helper.onEachTick(() -> {
			float now = foe.getHealth();
			if (now < last[0]) {
				drops.add(helper.getTick());
				amounts.add(last[0] - now);
			}
			last[0] = now;
			if (removedAt[0] < 0L && charge.isRemoved()) {
				removedAt[0] = helper.getTick();
			}
		});
		helper.runAfterDelay(3L, () -> {
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
			helper.assertTrue(foe.getHealth() == full, "the spell hit before its charge landed: the hostile lost " + (full - foe.getHealth()));
		});
		helper.runAfterDelay(DragonCharge.FALL_TICKS + 8L, () -> {
			LOG.info("[BEHAVIOR] test=dragonfallDropsAFireChargeOfTwiceTheModelSizeThatFallsAndLands RESULT removedAt={} hitsAt={} amounts={} fallTicks={}",
				removedAt[0], drops, amounts, DragonCharge.FALL_TICKS);
			helper.assertTrue(charge.isRemoved() && removedAt[0] >= 0L, "the charge stayed in the world after landing");
			helper.assertTrue(drops.size() == 1, "the spell hit " + drops.size() + " times, not once: " + drops);
			helper.assertTrue(Math.abs(amounts.get(0) - SpellNumbers.DRAGONFALL.damage()) < 0.001F,
				"the hit dealt " + amounts.get(0) + ", not " + SpellNumbers.DRAGONFALL.damage());
			helper.assertTrue(Math.abs((full - foe.getHealth()) - SpellNumbers.DRAGONFALL.damage()) < 0.001F,
				"the hostile lost " + (full - foe.getHealth()) + " in all, not one hit of " + SpellNumbers.DRAGONFALL.damage());
			helper.assertTrue(drops.get(0) >= DragonCharge.FALL_TICKS, "the hit came on tick " + drops.get(0) + ", before the charge had fallen " + DragonCharge.FALL_TICKS);
			helper.assertTrue(drops.get(0).longValue() == removedAt[0], "the hit came on tick " + drops.get(0) + " and the charge landed on tick " + removedAt[0]);
			foe.discard();
			helper.succeed();
		});
	}

	/** How the charge burns and how large it reads on screen is seen on a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theChargeBurnsAndLooksHugeOnAClientRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] cast Dragonfall at a spot 10 blocks away. A huge fire charge, twice the model size, trailing flames and smoke, falls onto the spot. This harness has no client.");
	}
}
