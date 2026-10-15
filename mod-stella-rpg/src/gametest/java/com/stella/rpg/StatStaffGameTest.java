package com.stella.rpg;

import com.stella.rpg.job.Job;
import com.stella.rpg.job.JobItems;
import com.stella.rpg.job.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Stat milestones, the one-time job change bonus, and staff MATK. */
public final class StatStaffGameTest {
	private static final double EPSILON = 1e-9;

	@GameTest
	public void nineStrengthReachesThreeMilestones(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.STR, 9);
		int str = StatBonuses.effective(player, Stat.STR);
		int reached = StatMilestones.reached(str);
		StatMilestones.setBonusPerStep(null);
		double status;
		try {
			status = RpgCombat.statusAtk(str) * RpgScale.k();
		} finally {
			StatMilestones.setBonusPerStep(0.0);
		}
		RpgTestSupport.result("nineStrengthReachesThreeMilestones", "str=" + str + " reached=" + reached + " statusAtk=" + status);
		helper.assertTrue(str == 9 && reached == 3, "STR " + str + " reached " + reached + " milestones");
		helper.assertTrue(Math.abs(status - str * (1.0 + 3 * StatMilestones.BONUS_PER_STEP)) < EPSILON, "status ATK " + status);
		helper.assertTrue(StatMilestones.reached(8) == 2 && StatMilestones.reached(0) == 0, "milestone steps drifted");
		helper.succeed();
	}

	@GameTest
	public void aJobChangeAddsThreeStatsOncePerJob(final GameTestHelper helper) {
		ServerPlayer changed = RpgTestSupport.player(helper);
		ServerPlayer plain = RpgTestSupport.player(helper);
		changed.experienceLevel = Jobs.MIN_LEVEL;
		plain.experienceLevel = Jobs.MIN_LEVEL;
		helper.assertTrue(Jobs.choose(changed, Job.MAGE), "the job change was refused");
		plain.setAttached(Jobs.ATTACHMENT, new Jobs.State(Job.MAGE, List.of()));
		for (Stat stat : Stat.values()) {
			int gain = StatBonuses.effective(changed, stat) - StatBonuses.effective(plain, stat);
			int expected = Job.MAGE.bonusStats().contains(stat) ? Jobs.CHANGE_BONUS : 0;
			helper.assertTrue(gain == expected, stat + " gained " + gain + ", want " + expected);
		}
		int before = StatBonuses.effective(changed, Stat.INT);
		Jobs.State state = changed.getAttachedOrElse(Jobs.ATTACHMENT, Jobs.State.EMPTY);
		changed.setAttached(Jobs.ATTACHMENT, new Jobs.State(Job.NONE, state.trials(), state.bonusJobs()));
		helper.assertTrue(Jobs.choose(changed, Job.MAGE), "the second change was refused");
		int after = StatBonuses.effective(changed, Stat.INT);
		RpgTestSupport.result("aJobChangeAddsThreeStatsOncePerJob", "bonus=" + Job.MAGE.bonusStats() + " intBefore=" + before + " intAfter=" + after);
		helper.assertTrue(after == before, "a second change to the same job moved INT from " + before + " to " + after);
		helper.succeed();
	}

	@GameTest
	public void theMageStaffGivesMatkThatRefineRaises(final GameTestHelper helper) {
		ServerPlayer mage = RpgTestSupport.player(helper);
		mage.getInventory().clearContent();
		mage.experienceLevel = Jobs.MIN_LEVEL;
		helper.assertTrue(Jobs.choose(mage, Job.MAGE) && Jobs.grantItems(mage), "the mage got no kit");
		helper.assertTrue(mage.getInventory().contains(stack -> stack.is(JobItems.MAGE_STAFF)), "the mage got no staff");
		double bare = RpgSpells.matkFlat(mage);
		ItemStack staff = new ItemStack(JobItems.MAGE_STAFF);
		mage.setItemSlot(EquipmentSlot.MAINHAND, staff);
		double unrefined = RpgSpells.matkFlat(mage);
		double[] byLevel = new double[16];
		for (int level = 0; level <= 15; level++) {
			int refine = level;
			CustomData.update(DataComponents.CUSTOM_DATA, staff, tag -> tag.putInt("stella_refine", refine));
			byLevel[level] = RpgSpells.matkFlat(mage);
		}
		RpgTestSupport.result("theMageStaffGivesMatkThatRefineRaises", "bare=" + bare + " plus0=" + unrefined + " plus6=" + byLevel[6]
			+ " plus7=" + byLevel[7] + " plus15=" + byLevel[15]);
		helper.assertTrue(bare == 0.0 && Math.abs(unrefined - JobItems.STAFF_MATK) < EPSILON, "staff MATK " + unrefined);
		for (int level = 1; level <= 15; level++) {
			helper.assertTrue(byLevel[level] > byLevel[level - 1], "refine +" + level + " did not raise MATK");
		}
		double plainStep = byLevel[6] - byLevel[5];
		helper.assertTrue(byLevel[7] - byLevel[6] > plainStep, "the +7 milestone added no extra MATK");
		for (int milestone : StaffMatk.MILESTONES) {
			helper.assertTrue(byLevel[milestone] - byLevel[milestone - 1] > plainStep, "no jump at +" + milestone);
		}
		helper.succeed();
	}

	@GameTest
	public void aStaffEffectGrowsAtRefineMilestones(final GameTestHelper helper) {
		ServerPlayer mage = RpgTestSupport.player(helper);
		ItemStack staff = new ItemStack(Items.STICK);
		staff.set(DataComponents.CUSTOM_DATA, StaffMatk.profile(40, StaffMatk.CDR, 5, 4));
		mage.setItemSlot(EquipmentSlot.MAINHAND, staff);
		double bare = RpgSpells.cooldown(mage);
		CustomData.update(DataComponents.CUSTOM_DATA, staff, tag -> tag.putInt("stella_refine", 10));
		double refined = RpgSpells.cooldown(mage);
		mage.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		double none = RpgSpells.cooldown(mage);
		RpgTestSupport.result("aStaffEffectGrowsAtRefineMilestones", "none=" + none + " plus0=" + bare + " plus10=" + refined);
		helper.assertTrue(bare < none && refined < bare, "cooldown " + none + " then " + bare + " then " + refined);
		helper.succeed();
	}

	@GameTest
	public void staffTooltipShowsTheMatkASpellUses(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack staff = new ItemStack(Items.STICK);
		staff.set(DataComponents.CUSTOM_DATA, StaffMatk.profile(10, StaffMatk.CRIT, 5, 5));
		CustomData.update(DataComponents.CUSTOM_DATA, staff, tag -> tag.putInt("stella_refine", 7));
		player.setItemSlot(EquipmentSlot.MAINHAND, staff);
		String line = com.stella.rpg.roll.BonusTooltip.lines(staff).get(0).getString();
		String want = "+" + AttackPreview.number(StaffMatk.heldMatk(player)) + " Matk";
		RpgTestSupport.result("staffTooltipShowsTheMatkASpellUses", "line=" + line + " want=" + want);
		helper.assertTrue(line.equals(want), "tooltip " + line + " vs " + want);
		helper.assertTrue(Math.abs(RpgSpells.matkFlat(player) - StaffMatk.heldMatk(player)) < EPSILON, "spell flat MATK drifted");
		helper.succeed();
	}
}
