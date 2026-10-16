package com.stella.end;

import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.phys.Vec3;

/** The ultimate gear: made at the smithing table from a netherite piece, a void shard and the dragon's spoils, and refinable. */
public class UltimateGearGameTest {
	/** The netherite piece each item is forged from. */
	static final Map<String, Item> BASE = Map.of(
		DraconicGear.BLADE, Items.NETHERITE_SWORD, DraconicGear.BOW, Items.NETHERITE_INGOT, DraconicGear.STAFF, Items.NETHERITE_SCRAP,
		DraconicGear.HELMET, Items.NETHERITE_HELMET, DraconicGear.CHESTPLATE, Items.NETHERITE_CHESTPLATE,
		DraconicGear.LEGGINGS, Items.NETHERITE_LEGGINGS, DraconicGear.BOOTS, Items.NETHERITE_BOOTS
	);

	static Item material(final String id) {
		return DraconicGear.WEAPON_IDS.contains(id) ? Items.DRAGON_EGG : DraconicGear.item(DraconicGear.DRAGON_SCALE);
	}

	static ItemStack stack(final String id) {
		return new ItemStack(DraconicGear.item(id));
	}

	/** What an attribute totals on a stack for one slot, from the player's base value. */
	static double total(final ItemStack stack, final Holder<Attribute> attribute, final double base) {
		double sum = base;
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.attribute().equals(attribute) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
				sum += entry.modifier().amount();
			}
		}
		return sum;
	}

	@GameTest(environment = "mod-stella-end-test:gear_recipes", maxTicks = 40)
	public void everyPieceHasASmithingRecipeThatNeedsItsShardsItsBaseAndItsFullCostOfMaterial(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		List<String> bad = new ArrayList<>();
		for (String id : DraconicGear.ALL) {
			int cost = DraconicGear.COST.get(id);
			ItemStack template = new ItemStack(DraconicGear.item(EndItems.SHARD_ID));
			ItemStack base = new ItemStack(BASE.get(id));
			Optional<RecipeHolder<SmithingRecipe>> full = end.recipeAccess().getRecipeFor(RecipeType.SMITHING,
				new SmithingRecipeInput(template, base, new ItemStack(material(id).asItem(), cost)), end);
			if (full.isEmpty() || !full.get().value().assemble(new SmithingRecipeInput(template, base, new ItemStack(material(id).asItem(), cost))).is(DraconicGear.item(id))) {
				bad.add(id + " has no recipe from " + cost + " of its material");
			}
			if (cost > 1 && end.recipeAccess().getRecipeFor(RecipeType.SMITHING,
				new SmithingRecipeInput(template, base, new ItemStack(material(id).asItem(), cost - 1)), end).isPresent()) {
				bad.add(id + " is made from " + (cost - 1) + " of its material, short of " + cost);
			}
			if (end.recipeAccess().getRecipeFor(RecipeType.SMITHING,
				new SmithingRecipeInput(new ItemStack(Items.AMETHYST_SHARD), base, new ItemStack(material(id).asItem(), cost)), end).isPresent()) {
				bad.add(id + " is made without a void shard");
			}
		}
		EndTestSupport.result("gearRecipes", "pieces=" + DraconicGear.ALL.size() + " bad=" + bad.size());
		helper.assertTrue(bad.isEmpty(), "recipe defects: " + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:gear_menu", maxTicks = 40)
	public void theSmithingTablePaysTheFullCostWhenTheHelmetIsTaken(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 1);
		SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		int cost = DraconicGear.COST.get(DraconicGear.HELMET);
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(DraconicGear.item(EndItems.SHARD_ID), 3));
		menu.getSlot(SmithingMenu.BASE_SLOT).set(new ItemStack(Items.NETHERITE_HELMET));
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(DraconicGear.item(DraconicGear.DRAGON_SCALE), cost + 2));
		menu.slotsChanged(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).container);
		ItemStack offered = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
		menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
		ItemStack carried = menu.getCarried().copy();
		int shards = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().getCount();
		int left = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount();
		boolean baseGone = menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty();
		EndTestSupport.result("gearMenu", "offered=" + offered + " carried=" + carried + " shards=" + shards + " scalesLeft=" + left + " baseGone=" + baseGone);
		EndTestSupport.fresh(helper);
		helper.assertTrue(offered.is(DraconicGear.item(DraconicGear.HELMET)), "the table offers " + offered);
		helper.assertTrue(carried.is(DraconicGear.item(DraconicGear.HELMET)), "the player took " + carried);
		helper.assertTrue(left == 2, "the table left " + left + " scales of " + (cost + 2) + ", for a cost of " + cost);
		helper.assertTrue(shards == 2 && baseGone, "the table kept a shard or the netherite helmet: " + shards + " shards");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:gear_stats", maxTicks = 60)
	public void theGearCarriesTheStatsOfThePlan(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 1);
		List<String> bad = new ArrayList<>();
		ItemStack blade = stack(DraconicGear.BLADE);
		double damage = total(blade, Attributes.ATTACK_DAMAGE, 1.0);
		double speed = total(blade, Attributes.ATTACK_SPEED, 4.0);
		if (Math.abs(damage - DraconicGear.BLADE_DAMAGE) > 1.0E-6 || Math.abs(speed - DraconicGear.BLADE_SPEED) > 1.0E-6) {
			bad.add("blade " + damage + " damage at " + speed + " speed");
		}
		for (int i = 0; i < DraconicGear.ARMOR.size(); i++) {
			ItemStack piece = stack(DraconicGear.ARMOR.get(i));
			EquipmentSlot slot = DraconicGear.slotOf(DraconicGear.ARMOR.get(i));
			var equippable = piece.get(DataComponents.EQUIPPABLE);
			if (equippable == null || equippable.slot() != slot) {
				bad.add(DraconicGear.ARMOR.get(i) + " is worn in " + (equippable == null ? "no slot" : equippable.slot()));
			}
			if (total(piece, Attributes.ARMOR, 0.0) != DraconicGear.ARMOR_POINTS[i] || total(piece, Attributes.ARMOR_TOUGHNESS, 0.0) != DraconicGear.ARMOR_TOUGHNESS
				|| Math.abs(total(piece, Attributes.KNOCKBACK_RESISTANCE, 0.0) - DraconicGear.ARMOR_KNOCKBACK) > 1.0E-6
				|| total(piece, Attributes.MAX_HEALTH, 0.0) != DraconicGear.ARMOR_HEALTH) {
				bad.add(DraconicGear.ARMOR.get(i) + " gives armor " + total(piece, Attributes.ARMOR, 0.0) + ", toughness " + total(piece, Attributes.ARMOR_TOUGHNESS, 0.0)
					+ ", knockback " + total(piece, Attributes.KNOCKBACK_RESISTANCE, 0.0) + ", health " + total(piece, Attributes.MAX_HEALTH, 0.0));
			}
		}
		ItemStack staff = stack(DraconicGear.STAFF);
		CustomData data = staff.get(DataComponents.CUSTOM_DATA);
		CompoundTag profile = data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("stella_staff");
		int matk = profile.getIntOr("matk", -1);
		int expected = (int) Math.round(DraconicGear.STAFF_BASE_MATK * (1.0 + DraconicGear.SPELL_POWER_BONUS));
		if (matk != expected || !Refine.isStaff(staff)) {
			bad.add("staff matk " + matk + ", expected " + expected + ", staff=" + Refine.isStaff(staff));
		}
		// The bow doubles its arrow.
		// A shot costs it no wear.
		ItemStack bow = stack(DraconicGear.BOW);
		player.getInventory().add(new ItemStack(Items.ARROW, 8));
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		int arrowsBefore = player.getInventory().countItem(Items.ARROW);
		double damagePerArrow = fire(player, bow, end);
		int shot = arrowsBefore - player.getInventory().countItem(Items.ARROW);
		ItemStack vanilla = new ItemStack(Items.BOW);
		player.setItemInHand(InteractionHand.MAIN_HAND, vanilla);
		double vanillaDamage = fire(player, vanilla, end);
		EndTestSupport.result("gearStats", "blade=" + damage + "/" + speed + " matk=" + matk + " arrow=" + damagePerArrow + " vanillaArrow=" + vanillaDamage + " shot=" + shot
			+ " bowWear=" + bow.getDamageValue() + " vanillaWear=" + vanilla.getDamageValue() + " bad=" + bad.size());
		int bowWear = bow.getDamageValue();
		int vanillaWear = vanilla.getDamageValue();
		EndTestSupport.fresh(helper);
		helper.assertTrue(bad.isEmpty(), "stat defects: " + bad);
		helper.assertTrue(shot == 1 && damagePerArrow > 0.0, "the bow shot " + shot + " arrows of damage " + damagePerArrow);
		helper.assertTrue(Math.abs(damagePerArrow / vanillaDamage - DraconicGear.ARROW_FACTOR) < 1.0E-6, "the arrow hits " + damagePerArrow / vanillaDamage + " times as hard as vanilla's");
		helper.assertTrue(vanillaWear == 1 && bowWear == 0, "a vanilla bow wore " + vanillaWear + " and the draconic bow wore " + bowWear);
		helper.succeed();
	}

	/** Draws a bow fully and lets go; returns the base damage of the arrow it fired, read from the arrow's saved form. */
	static double fire(final ServerPlayer player, final ItemStack bow, final ServerLevel end) {
		java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
		for (net.minecraft.world.entity.Entity entity : EndTestSupport.entities(end)) {
			before.add(entity.getUUID());
		}
		bow.getItem().releaseUsing(bow, end, player, bow.getItem().getUseDuration(bow, player) - 20);
		for (net.minecraft.world.entity.Entity entity : EndTestSupport.entities(end)) {
			if (!before.contains(entity.getUUID()) && entity instanceof AbstractArrow arrow) {
				var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, end.registryAccess());
				arrow.saveWithoutId(output);
				double base = output.buildResult().getDoubleOr("damage", -1.0);
				arrow.discard();
				return base;
			}
		}
		return -1.0;
	}

	@GameTest(environment = "mod-stella-end-test:gear_refine", maxTicks = 40)
	public void everyPieceAcceptsARefineUpToTheCapAndWeaponsAndArmorGainTheirBonus(final GameTestHelper helper) {
		List<String> bad = new ArrayList<>();
		StringBuilder factors = new StringBuilder();
		for (String id : DraconicGear.ALL) {
			ItemStack piece = stack(id);
			if (!Refine.canRefine(piece)) {
				bad.add(id + " cannot be refined");
				continue;
			}
			Refine.setLevel(piece, Refine.MAX_LEVEL);
			if (Refine.level(piece) != Refine.MAX_LEVEL) {
				bad.add(id + " reads refine " + Refine.level(piece));
			}
			boolean bonus = piece.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
				.anyMatch(entry -> entry.modifier().id().equals(Refine.DAMAGE_ID) || entry.modifier().id().getPath().startsWith("refine_armor_"));
			boolean expectsBonus = id.equals(DraconicGear.BLADE) || DraconicGear.ARMOR.contains(id);
			if (expectsBonus && !bonus) {
				bad.add(id + " gained no refine bonus");
			}
			if (Refine.isArmor(piece) != DraconicGear.ARMOR.contains(id)) {
				bad.add(id + " armor=" + Refine.isArmor(piece));
			}
			if (RefineTier.factor(piece) < RefineTier.BASELINE) {
				bad.add(id + " has tier factor " + RefineTier.factor(piece));
			}
			factors.append(' ').append(id).append('=').append(RefineTier.factor(piece));
		}
		EndTestSupport.result("gearRefine", "pieces=" + DraconicGear.ALL.size() + " bad=" + bad.size() + " tierFactors=" + factors);
		helper.assertTrue(bad.isEmpty(), "refine defects: " + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:gear_blade", maxTicks = 40)
	public void theBladeHitsMobsOfTheEndTwentyFivePercentHarderAndNothingElseDoes(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 1);
		Zombie inEnd = EndTestSupport.dummy(end, new Vec3(2.5, EndCastle.standY(1), 2.5));
		Zombie elsewhere = EntityTypesHolder.zombie(helper.getLevel());
		float[] lost = new float[4];
		// The same weapon in both places.
		// Its stats cancel, so only the place differs.
		player.setItemInHand(InteractionHand.MAIN_HAND, stack(DraconicGear.BLADE));
		lost[0] = blow(end, inEnd, player);
		lost[1] = blow(helper.getLevel(), elsewhere, player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
		lost[2] = blow(end, inEnd, player);
		lost[3] = blow(helper.getLevel(), elsewhere, player);
		EndTestSupport.result("gearBlade", "bladeEnd=" + lost[0] + " bladeElsewhere=" + lost[1] + " swordEnd=" + lost[2] + " swordElsewhere=" + lost[3]);
		elsewhere.discard();
		EndTestSupport.fresh(helper);
		helper.assertTrue(Math.abs(lost[0] / lost[1] - (1.0 + DraconicGear.END_BONUS)) < 0.1, "the blade did " + lost[0] / lost[1] + " times the damage in the End");
		helper.assertTrue(Math.abs(lost[2] / lost[3] - 1.0) < 0.1, "a plain sword did " + lost[2] / lost[3] + " times the damage in the End");
		helper.succeed();
	}

	/** Twelve blows of one hundred points struck as the player, summed: another mod rolls dice on damage, so one blow varies. */
	static float blow(final ServerLevel level, final Zombie victim, final ServerPlayer attacker) {
		float total = 0.0F;
		for (int i = 0; i < 12; i++) {
			victim.setHealth(victim.getMaxHealth());
			victim.damageCooldownTime = 0;
			float before = victim.getHealth();
			victim.hurtServer(level, level.damageSources().playerAttack(attacker), 100.0F);
			total += before - victim.getHealth();
		}
		return total;
	}

	/** A claimed zombie standing in a level other than the End. */
	static final class EntityTypesHolder {
		static Zombie zombie(final ServerLevel level) {
			Zombie zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWNER);
			LevelBridge.claim(zombie);
			zombie.setNoAi(true);
			zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0E6);
			zombie.setHealth(zombie.getMaxHealth());
			zombie.setPos(1.5, 100.0, 1.5);
			level.addFreshEntity(zombie);
			return zombie;
		}
	}

	@GameTest(environment = "mod-stella-end-test:gear_set", maxTicks = 40)
	public void allFourPiecesGiveImmunityToWitherAndSlownessAndThreeDoNot(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 1);
		for (String id : DraconicGear.ARMOR.subList(0, 3)) {
			player.setItemSlot(DraconicGear.slotOf(id), stack(id));
		}
		boolean witherThree = player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
		boolean slowThree = player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0));
		player.removeAllEffects();
		player.setItemSlot(DraconicGear.slotOf(DraconicGear.BOOTS), stack(DraconicGear.BOOTS));
		boolean witherFour = player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
		boolean slowFour = player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0));
		boolean weakFour = player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
		boolean worn = DraconicGear.wearsFullSet(player);
		EndTestSupport.result("gearSet", "three=" + witherThree + "/" + slowThree + " four=" + witherFour + "/" + slowFour + "/" + weakFour + " worn=" + worn);
		EndTestSupport.fresh(helper);
		helper.assertTrue(witherThree && slowThree, "three pieces already gave immunity");
		helper.assertTrue(worn, "the full set is not recognised");
		helper.assertTrue(!witherFour && !slowFour, "the full set took wither " + witherFour + " and slowness " + slowFour);
		helper.assertTrue(weakFour, "the full set also blocks weakness");
		helper.succeed();
	}
}
