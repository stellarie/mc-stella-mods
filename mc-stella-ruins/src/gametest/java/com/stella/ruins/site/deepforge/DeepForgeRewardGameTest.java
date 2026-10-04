package com.stella.ruins.site.deepforge;

import com.stella.ruins.world.RuinClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 8, 10 and 11: the lit forge, the Deepsteel recipes and stats, and the Forgeheart Hammer. */
public class DeepForgeRewardGameTest {
	static final int TICKS = 12000;
	static final long SEED = 0xF0E6E900L;

	static int inPack(final ServerPlayer player, final Item item) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	@GameTest(maxTicks = TICKS)
	public void anUnlitForgeRefusesAndALitForgeTurnsOneChunkAndOneCoalIntoOneIngot(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED, arena -> {
			ServerLevel level = arena.level();
			BlockPos forge = arena.markers("forge").get(0);
			ServerPlayer player = arena.join(arena.standAt(0, 4));
			helper.assertTrue(!level.getBlockState(forge).getValue(DeepForgeBlock.LIT), "the forge stands lit before the trial");

			ItemStack chunks = new ItemStack(DeepForgeItems.ORE_CHUNK, 4);
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COAL, 4));
			ForgeTestSupport.use(level, player, forge, chunks);
			helper.assertTrue(inPack(player, DeepForgeItems.INGOT) == 0 && chunks.getCount() == 4 && player.getItemInHand(InteractionHand.OFF_HAND).getCount() == 4, "a cold forge took a chunk or gave an ingot");

			level.setBlock(forge, level.getBlockState(forge).setValue(DeepForgeBlock.LIT, true), 3);
			ForgeTestSupport.use(level, player, forge, chunks);
			helper.assertTrue(inPack(player, DeepForgeItems.INGOT) == 1, "a lit forge gave " + inPack(player, DeepForgeItems.INGOT) + " ingots, not 1");
			helper.assertTrue(chunks.getCount() == 3 && player.getItemInHand(InteractionHand.OFF_HAND).getCount() == 3, "the forge spent " + (4 - chunks.getCount()) + " chunks and " + (4 - player.getItemInHand(InteractionHand.OFF_HAND).getCount()) + " coal");

			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			player.getInventory().add(new ItemStack(Items.CHARCOAL, 2));
			ForgeTestSupport.use(level, player, forge, chunks);
			helper.assertTrue(inPack(player, DeepForgeItems.INGOT) == 2 && inPack(player, Items.CHARCOAL) == 1, "charcoal from the pack did not work: ingots " + inPack(player, DeepForgeItems.INGOT) + " charcoal " + inPack(player, Items.CHARCOAL));

			player.getInventory().clearContent();
			chunks = new ItemStack(DeepForgeItems.ORE_CHUNK, 2);
			ForgeTestSupport.use(level, player, forge, chunks);
			helper.assertTrue(inPack(player, DeepForgeItems.INGOT) == 0 && chunks.getCount() == 2, "the forge ran without fuel");

			ItemStack other = new ItemStack(Items.IRON_ORE, 2);
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COAL, 2));
			ForgeTestSupport.use(level, player, forge, other);
			helper.assertTrue(inPack(player, DeepForgeItems.INGOT) == 0 && other.getCount() == 2, "the forge smelted something that is not a Deepsteel chunk");
			ForgeTestSupport.result("forgeSmelts", "ingots=2 chunksLeft=" + 3);
		});
	}

	private static ItemStack craft(final GameTestHelper helper, final int w, final int h, final ItemStack... cells) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(w, h, List.of(cells));
		Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		return found.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	@GameTest
	public void everyDeepsteelRecipeCraftsItsItemAndOnlyFromDeepsteel(final GameTestHelper helper) {
		ItemStack i = new ItemStack(DeepForgeItems.INGOT);
		ItemStack s = new ItemStack(Items.STICK);
		ItemStack e = ItemStack.EMPTY;
		helper.assertTrue(craft(helper, 1, 3, i, i, s).is(DeepForgeItems.SWORD), "the sword recipe gave " + craft(helper, 1, 3, i, i, s));
		helper.assertTrue(craft(helper, 2, 3, i, i, i, s, e, s).is(DeepForgeItems.AXE), "the axe recipe failed");
		helper.assertTrue(craft(helper, 3, 3, i, i, i, e, s, e, e, s, e).is(DeepForgeItems.PICKAXE), "the pickaxe recipe failed");
		helper.assertTrue(craft(helper, 3, 2, i, i, i, i, e, i).is(DeepForgeItems.HELMET), "the helmet recipe failed");
		helper.assertTrue(craft(helper, 3, 3, i, e, i, i, i, i, i, i, i).is(DeepForgeItems.CHESTPLATE), "the chestplate recipe failed");
		helper.assertTrue(craft(helper, 3, 3, i, i, i, i, e, i, i, e, i).is(DeepForgeItems.LEGGINGS), "the leggings recipe failed");
		helper.assertTrue(craft(helper, 3, 2, i, e, i, i, e, i).is(DeepForgeItems.BOOTS), "the boots recipe failed");
		ItemStack iron = new ItemStack(Items.IRON_INGOT);
		ItemStack made = craft(helper, 1, 3, iron, iron, s);
		helper.assertTrue(!made.is(DeepForgeItems.SWORD), "iron ingots made a Deepsteel sword");
		ForgeTestSupport.result("deepsteelRecipes", "seven recipes craft; iron sword control gave " + made.getItem());
		helper.succeed();
	}

	@GameTest
	public void deepsteelStatsSitBetweenDiamondAndNetheriteAndTheIngotRepairsIt(final GameTestHelper helper) {
		ToolMaterial tool = com.stella.ruins.reward.RuinGear.toolMaterial(DeepForgeItems.DEEPSTEEL);
		ArmorMaterial armor = com.stella.ruins.reward.RuinGear.armorMaterial(DeepForgeItems.DEEPSTEEL);
		helper.assertTrue(tool.durability() > ToolMaterial.DIAMOND.durability() && tool.durability() < ToolMaterial.NETHERITE.durability(), "tool durability " + tool.durability());
		helper.assertTrue(tool.attackDamageBonus() > ToolMaterial.DIAMOND.attackDamageBonus() && tool.attackDamageBonus() < ToolMaterial.NETHERITE.attackDamageBonus(), "attack bonus " + tool.attackDamageBonus());
		helper.assertTrue(tool.speed() > ToolMaterial.DIAMOND.speed() && tool.speed() < ToolMaterial.NETHERITE.speed(), "mining speed " + tool.speed());
		helper.assertTrue(armor.durability() > ArmorMaterials.DIAMOND.durability() && armor.durability() < ArmorMaterials.NETHERITE.durability(), "armor durability factor " + armor.durability());
		helper.assertTrue(armor.toughness() == 2.5F && armor.toughness() > ArmorMaterials.DIAMOND.toughness() && armor.toughness() < ArmorMaterials.NETHERITE.toughness(), "toughness " + armor.toughness());
		helper.assertTrue(armor.knockbackResistance() == 0.05F && armor.knockbackResistance() > ArmorMaterials.DIAMOND.knockbackResistance() && armor.knockbackResistance() < ArmorMaterials.NETHERITE.knockbackResistance(), "knockback resistance " + armor.knockbackResistance());
		helper.assertTrue(armor.defense().get(ArmorType.HELMET) == 3 && armor.defense().get(ArmorType.CHESTPLATE) == 8
			&& armor.defense().get(ArmorType.LEGGINGS) == 6 && armor.defense().get(ArmorType.BOOTS) == 3, "armor values " + armor.defense());
		helper.assertTrue(tool.durability() == 1800, "tool durability is " + tool.durability() + ", not 1800");
		for (Item item : new Item[] {DeepForgeItems.SWORD, DeepForgeItems.AXE, DeepForgeItems.PICKAXE, DeepForgeItems.HAMMER}) {
			helper.assertTrue(new ItemStack(item).get(DataComponents.MAX_DAMAGE) == 1800, new ItemStack(item) + " has " + new ItemStack(item).get(DataComponents.MAX_DAMAGE) + " durability");
		}
		helper.assertTrue(new ItemStack(DeepForgeItems.INGOT).is(DeepForgeItems.DEEPSTEEL_REPAIR), "the ingot does not repair Deepsteel");
		helper.assertTrue(!new ItemStack(Items.IRON_INGOT).is(DeepForgeItems.DEEPSTEEL_REPAIR), "an iron ingot repairs Deepsteel");
		ForgeTestSupport.result("deepsteelStats", "durability=" + tool.durability() + " speed=" + tool.speed() + " armorFactor=" + armor.durability() + " toughness=" + armor.toughness());
		helper.succeed();
	}

	static Husk husk(final ServerLevel level, final Vec3 at) {
		return DeepForgeMobGameTest.target(level, at);
	}

	static void hit(final ServerLevel level, final ServerPlayer player, final net.minecraft.world.entity.LivingEntity victim) {
		victim.damageCooldownTime = 0;
		float before = victim.getHealth();
		boolean hurt = victim.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
		ForgeTestSupport.result("hammerHit", "hurt=" + hurt + " took=" + (before - victim.getHealth()) + " fire=" + victim.getRemainingFireTicks());
	}

	@GameTest(maxTicks = TICKS)
	public void theForgeheartHammerBurstsFireOnEveryThirdHitOnOneTargetWithinEightyTicks(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 1, arena -> {
			ServerLevel level = arena.level();
			AtomicLong clock = new AtomicLong(5_000L);
			RuinClock.Source old = RuinClock.replace(l -> clock.get());
			DeepForgeSite.FORGEHEART.reset();
			try {
				ServerPlayer player = arena.join(DeepForgeMobGameTest.spot(arena, -6));
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DeepForgeItems.HAMMER));
				Husk a = husk(level, DeepForgeMobGameTest.spot(arena, 0));
				Husk near = husk(level, DeepForgeMobGameTest.spot(arena, 2));
				Husk far = husk(level, DeepForgeMobGameTest.spot(arena, 7));
				Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.EVENT);
				pig.snapTo(DeepForgeMobGameTest.spot(arena, 0).add(0.0, 0.0, 1.5));
				level.addFreshEntity(pig);
				float pigBefore = pig.getHealth();
				float full = a.getHealth();

				hit(level, player, a);
				float blow = full - a.getHealth();
				helper.assertTrue(blow > 0.0F, "a blow did no damage, so the damage checks below prove nothing");
				hit(level, player, a);
				helper.assertTrue(!a.isOnFire() && near.getHealth() == full, "a burst came before the third hit");
				hit(level, player, a);
				float aHealth = a.getHealth();
				helper.assertTrue(Math.abs((full - aHealth) - (3.0F * blow + ForgeheartEffect.BURST_DAMAGE)) < 0.01F, "the target took " + (full - aHealth) + " damage from three blows of " + blow + " and a burst");
				helper.assertTrue(a.isOnFire() && near.isOnFire(), "the burst did not ignite the target and its neighbour");
				helper.assertTrue(Math.abs((full - near.getHealth()) - ForgeheartEffect.BURST_DAMAGE) < 0.01F, "the neighbour took " + (full - near.getHealth()) + " damage");
				helper.assertTrue(far.getHealth() == full && !far.isOnFire(), "a mob outside the burst radius was hurt");
				helper.assertTrue(pig.getHealth() == pigBefore && !pig.isOnFire(), "the burst hurt a mob that is not hostile");
				a.clearFire();
				near.clearFire();

				// A fourth and fifth blow start a fresh count.
				hit(level, player, a);
				hit(level, player, a);
				helper.assertTrue(!a.isOnFire(), "the blows after a burst burst again at once");
				hit(level, player, a);
				helper.assertTrue(a.isOnFire(), "the sixth blow did not burst");
				a.clearFire();
				near.clearFire();

				// Blows spread over more than eighty ticks do not burst.
				DeepForgeSite.FORGEHEART.reset();
				hit(level, player, a);
				clock.addAndGet(50);
				hit(level, player, a);
				clock.addAndGet(ForgeheartEffect.WINDOW_TICKS - 49);
				hit(level, player, a);
				helper.assertTrue(!a.isOnFire(), "a third blow after " + (ForgeheartEffect.WINDOW_TICKS + 1) + " ticks burst");
				hit(level, player, a);
				hit(level, player, a);
				helper.assertTrue(a.isOnFire(), "two more blows inside a new window did not burst");
				a.clearFire();

				// A blow on another target restarts the count.
				DeepForgeSite.FORGEHEART.reset();
				hit(level, player, a);
				hit(level, player, a);
				hit(level, player, far);
				helper.assertTrue(!a.isOnFire() && !far.isOnFire(), "a blow on a second target burst");
				ForgeTestSupport.result("hammerBursts", "blow=" + blow + " burst=" + ForgeheartEffect.BURST_DAMAGE + " radius=" + ForgeheartEffect.BURST_RADIUS + " window=" + ForgeheartEffect.WINDOW_TICKS);
				List<net.minecraft.world.entity.Entity> gone = new ArrayList<>(List.of(a, near, far, pig));
				gone.forEach(net.minecraft.world.entity.Entity::discard);
			} finally {
				RuinClock.replace(old);
				DeepForgeSite.FORGEHEART.reset();
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theHammerDoesNothingInAnotherHandThanTheWielders(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 2, arena -> {
			ServerLevel level = arena.level();
			DeepForgeSite.FORGEHEART.reset();
			ServerPlayer player = arena.join(DeepForgeMobGameTest.spot(arena, -6));
			Husk a = husk(level, DeepForgeMobGameTest.spot(arena, 0));
			for (int i = 0; i < 3; i++) {
				hit(level, player, a);
			}
			helper.assertTrue(!a.isOnFire(), "a player with no hammer set a mob on fire");
			ForgeTestSupport.result("hammerNeedsHammer", "fire=" + a.isOnFire());
			a.discard();
		});
	}
}
