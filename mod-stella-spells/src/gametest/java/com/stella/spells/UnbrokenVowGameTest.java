package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Unbroken Vow: how much wear a point of durability covers, how the two stats move it, and that the
 * sum carries no cap. The wear rule is read from the shipped constant and from the pinned stats, and the
 * two lengths are the literals of the approved table, so a wrong constant fails the case.
 */
public class UnbrokenVowGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.vow");
	/** The approved lengths: half an hour for the vow, twenty seconds for the prayer. */
	private static final int HALF_HOUR = 36_000;

	/** The two stats the vow reads, pinned so the case does not need the RPG mod. */
	private record Stats(double vit, double intel) implements UnbrokenVow.StatSource {
		@Override
		public double stat(final LivingEntity holder, final String key) {
			return switch (key) {
				case "vit" -> this.vit;
				case "int" -> this.intel;
				default -> 0.0;
			};
		}
	}

	/** One point of durability covers one point of wear per point of Vitality and Intelligence together. */
	@GameTest
	public void aWearOfOneLevelPlusOneSpendsOneDurability(final GameTestHelper helper) {
		ServerPlayer holder = holder(helper);
		int wear = 40;
		int level;
		int spent;
		UnbrokenVow.setStatSource(new Stats(12.0, 8.0));
		try {
			level = UnbrokenVow.level(holder);
			spent = wear(helper, holder, wear);
		} finally {
			UnbrokenVow.setStatSource(null);
		}
		int expected = wear / (level + 1);
		LOG.info("[BEHAVIOR] test=vowWear RESULT level={} wear={} spent={} expected={}", level, wear, spent, expected);
		helper.assertTrue(expected > 0, "the pinned stats absorb the whole wear, so the case proves nothing: level=" + level);
		helper.assertTrue(spent == expected, "a wear of " + wear + " at level " + level + " cost " + spent + " durability, not " + expected);
		helper.succeed();
	}

	/** Both stats move the vow: the same wear costs less at higher Vitality and Intelligence. */
	@GameTest
	public void theVowFollowsBothStats(final GameTestHelper helper) {
		int wear = 100;
		int low = wornBy(helper, new Stats(5.0, 5.0), wear);
		int high = wornBy(helper, new Stats(50.0, 50.0), wear);
		LOG.info("[BEHAVIOR] test=vowStats RESULT wear={} low={} high={}", wear, low, high);
		helper.assertTrue(low > 0, "the low pair absorbed the whole wear, so the case proves nothing");
		helper.assertTrue(high < low, "the high pair spent " + high + " durability against " + low + ", so the vow does not follow the stats");
		helper.succeed();
	}

	/** No cap: the level is the whole sum of the two stats, and the wear it covers keeps growing with it. */
	@GameTest
	public void theVowCarriesNoCap(final GameTestHelper helper) {
		ServerPlayer holder = holder(helper);
		double vit = 1_000.0;
		double intel = 1_000.0;
		UnbrokenVow.setStatSource(new Stats(vit, intel));
		int level;
		int first;
		int second;
		try {
			level = UnbrokenVow.level(holder);
			first = wear(helper, holder, 100);
			second = wear(helper, holder, 5_000);
		} finally {
			UnbrokenVow.setStatSource(null);
		}
		int expectedLevel = (int) (UnbrokenVow.WEAR_PER_STAT_POINT * (vit + intel));
		LOG.info("[BEHAVIOR] test=vowCap RESULT level={} expectedLevel={} first={} second={}", level, expectedLevel, first, second);
		helper.assertTrue(level == expectedLevel, "the level stopped at " + level + " against the whole sum of " + expectedLevel);
		helper.assertTrue(second > first, "the vow absorbed five thousand wear at the same cost as a hundred: " + second + " against " + first);
		helper.succeed();
	}

	/** The book grants the vow for the approved half hour, and only while the effect holds does wear fall. */
	@GameTest
	public void theBookGrantsTheVowForHalfAnHour(final GameTestHelper helper) {
		ServerPlayer holder = holder(helper);
		use(helper, holder, "unbroken_vow");
		MobEffectInstance vow = holder.getEffect(SpellStatus.UNBROKEN_VOW);
		int duration = vow == null ? -1 : vow.getDuration();
		LOG.info("[BEHAVIOR] test=vowDuration RESULT duration={} want={} shipped={}", duration, HALF_HOUR, UnbrokenVow.VOW_TICKS);
		helper.assertTrue(duration == HALF_HOUR && UnbrokenVow.VOW_TICKS == HALF_HOUR,
			"the vow lasts " + duration + " ticks with the shipped constant at " + UnbrokenVow.VOW_TICKS + ", not " + HALF_HOUR);
		holder.removeEffect(SpellStatus.UNBROKEN_VOW);
		int bare = wear(helper, holder, 40);
		LOG.info("[BEHAVIOR] test=vowWithoutEffect RESULT spent={} wear={}", bare, 40);
		helper.assertTrue(bare == 40, "without the vow a wear of 40 cost " + bare + " durability");
		helper.succeed();
	}

	private static int wornBy(final GameTestHelper helper, final Stats stats, final int wear) {
		ServerPlayer holder = holder(helper);
		UnbrokenVow.setStatSource(stats);
		try {
			holder.addEffect(new MobEffectInstance(SpellStatus.UNBROKEN_VOW, HALF_HOUR, 0));
			return wear(helper, holder, wear);
		} finally {
			UnbrokenVow.setStatSource(null);
		}
	}

	/** Wears the held sword this many points, and reads what the durability paid. */
	private static int wear(final GameTestHelper helper, final ServerPlayer holder, final int points) {
		if (!holder.hasEffect(SpellStatus.UNBROKEN_VOW)) {
			holder.addEffect(new MobEffectInstance(SpellStatus.UNBROKEN_VOW, HALF_HOUR, 0));
		}
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		holder.setItemSlot(EquipmentSlot.MAINHAND, sword);
		for (int i = 0; i < points; i++) {
			sword.hurtAndBreak(1, holder, EquipmentSlot.MAINHAND);
		}
		return sword.getDamageValue();
	}

	private static ServerPlayer holder(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	private static void use(final GameTestHelper helper, final ServerPlayer caster, final String spell) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		InteractionResult result = book.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, spell + " did not cast: " + result);
	}

	private static void floor(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}
}
