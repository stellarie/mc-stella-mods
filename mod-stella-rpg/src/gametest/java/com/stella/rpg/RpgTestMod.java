package com.stella.rpg;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class RpgTestMod implements ModInitializer {
	public static final Identifier TEST_BOOK_ID = Identifier.fromNamespaceAndPath("mod-stella-rpg-test-mod-stella-rpg", "test_spellbook");
	public static final int TEST_BOOK_COOLDOWN = 40;
	public static Item testBook;

	/** A spellbook stand-in with a cooldown. */
	private static final class TestBook extends Item {
		private TestBook(final Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.FAIL;
			}
			player.getCooldowns().addCooldown(stack, TEST_BOOK_COOLDOWN);
			return InteractionResult.SUCCESS;
		}
	}

	/** A stand-in for a food effect, so the stat lookup by id is tested with the food mod absent. */
	private static final class StandIn extends MobEffect {
		private StandIn() {
			super(MobEffectCategory.BENEFICIAL, 0x55AA55);
		}
	}

	@Override
	public void onInitialize() {
		for (String name : new String[] {"surefooted", "seasoned_str"}) {
			Registry.register(BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath("mod-stella-food", name), new StandIn());
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TEST_BOOK_ID);
		testBook = Registry.register(BuiltInRegistries.ITEM, key, new TestBook(new Item.Properties().setId(key)));
	}
}
