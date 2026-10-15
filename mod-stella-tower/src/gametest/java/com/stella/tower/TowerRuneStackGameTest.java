package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

/** A weapon holds one rune per element: a higher floor replaces, an equal or lower one changes nothing. */
public class TowerRuneStackGameTest {
	@GameTest
	public void aWeaponHoldsOneRunePerElement(final GameTestHelper helper) {
		ItemStack sword = TowerRunes.apply(new ItemStack(Items.DIAMOND_SWORD), new RuneData(TowerElement.FIRE, 50));
		sword = TowerRunes.apply(sword, new RuneData(TowerElement.WATER, 40));
		List<RuneData> held = TowerRunes.runesOf(sword);
		boolean lower = TowerRunes.apply(sword, new RuneData(TowerElement.FIRE, 30)).isEmpty();
		boolean equal = TowerRunes.apply(sword, new RuneData(TowerElement.FIRE, 50)).isEmpty();
		ItemStack raised = TowerRunes.apply(sword, new RuneData(TowerElement.FIRE, 80));
		List<RuneData> after = TowerRunes.runesOf(raised);
		List<String> lines = DescriptionSupport.text(sword.getTooltipLines(
			Item.TooltipContext.of(helper.getLevel().registryAccess()), null, TooltipFlag.NORMAL));
		long runeLines = lines.stream().filter(line -> line.contains(TowerElement.FIRE.label()) && line.contains("50")
			|| line.contains(TowerElement.WATER.label()) && line.contains("40")).count();

		RuneStats.pin((entity, stat) -> 50);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		try {
			TowerTestSupport.vulnerable(player);
			player.setItemInHand(InteractionHand.MAIN_HAND, sword);
			Zombie target = TowerTestSupport.unrolled(EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND));
			target.snapTo(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
			target.setNoAi(true);
			target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
			target.setHealth(500.0F);
			helper.getLevel().addFreshEntity(target);
			target.damageCooldownTime = 0;
			target.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 10.0F);
			int heat = RuneEffects.heatOf(target);
			boolean wet = RuneEffects.isWet(target);
			target.discard();
			TowerTestSupport.result("runeStack", "held=" + held + " lower=" + lower + " equal=" + equal + " after=" + after + " lines=" + runeLines + " heat=" + heat + " wet=" + wet);
			helper.assertTrue(held.size() == 2, "the weapon holds " + held);
			helper.assertTrue(lower && equal, "a rune of no higher floor changed the weapon");
			helper.assertTrue(after.size() == 2 && after.contains(new RuneData(TowerElement.FIRE, 80)) && after.contains(new RuneData(TowerElement.WATER, 40)),
				"a higher floor left " + after);
			helper.assertTrue(runeLines == 2, "the tooltip showed " + runeLines + " rune lines");
			helper.assertTrue(heat == 1 && wet, "a hit built heat " + heat + ", wet " + wet);
		} finally {
			RuneStats.pin(null);
			TowerTestSupport.leave(helper, player);
		}
		helper.succeed();
	}
}
