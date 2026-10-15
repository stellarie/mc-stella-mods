package com.stella.equips.merchant;

import com.stella.equips.StellaEquips;
import com.stella.equips.tooltip.DescriptionLines;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * The Arcane Bow, a netherite-tier bow that only the ghost merchant sells. It needs no arrow and uses none, and the arrow
 * it looses cannot be picked up. Every arrow hit of it deals one damage; the rule lives in mod-stella-rpg, which finds the
 * bow by the item tag {@code mod-stella-rpg:one_damage_weapons}. Its MATK profile is the one of {@link ArcaneShortsword}.
 */
public class ArcaneBow extends BowItem {
	public static final String ID = "arcane_bow";
	public static final Item ITEM = register();

	public ArcaneBow(final Item.Properties properties) {
		super(properties);
	}

	public static void init() {
		// Touching ITEM runs the static initializer once.
	}

	private static Item register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, ID));
		String lang = "item." + StellaEquips.MOD_ID + "." + ID;
		return Registry.register(BuiltInRegistries.ITEM, key, new ArcaneBow(new Item.Properties().setId(key)
			.durability(ToolMaterial.NETHERITE.durability()).repairable(Items.NETHERITE_INGOT).enchantable(MerchantWeapons.ENCHANTABILITY)
			.rarity(Rarity.EPIC).fireResistant()
			.component(DataComponents.CUSTOM_DATA, ArcaneShortsword.profile())
			.component(DataComponents.LORE, new ItemLore(List.of(
				DescriptionLines.requires(Component.translatable(lang + ".requires").withStyle(ChatFormatting.WHITE)),
				DescriptionLines.desc(Component.translatable(lang + ".desc").withStyle(ChatFormatting.GRAY)))))));
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean releaseUsing(final ItemStack weapon, final Level level, final LivingEntity entity, final int remaining) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		float power = BowItem.getPowerForTime(this.getUseDuration(weapon, entity) - remaining);
		if (power < 0.1F) {
			return false;
		}
		List<ItemStack> drawn = draw(weapon, new ItemStack(Items.ARROW), player);
		if (level instanceof ServerLevel server && !drawn.isEmpty()) {
			this.shoot(server, player, player.getUsedItemHand(), weapon, drawn, power * 3.0F, 1.0F, power == 1.0F, null);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
			1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
		player.awardStat(Stats.ITEM_USED.get(this));
		return true;
	}

	@Override
	protected Projectile createProjectile(final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit) {
		Projectile created = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		if (created instanceof AbstractArrow arrow) {
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		}
		return created;
	}
}
