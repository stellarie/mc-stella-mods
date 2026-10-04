package com.stella.ruins.world;

import com.mojang.serialization.Codec;
import com.stella.ruins.StellaRuins;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A clue to a site, found in ordinary chests. Using it turns it into a map to the nearest site of its kind; the
 * tooltip shows the site's riddle. A clue is not an exclusive item.
 */
public final class RuinClueItem extends Item {
	public static final Identifier ID = StellaRuins.id("ruin_clue");
	public static final DataComponentType<String> CLUE_SITE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		StellaRuins.id("clue_site"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build()
	);
	public static final RuinClueItem ITEM = Registry.register(
		BuiltInRegistries.ITEM, ID, new RuinClueItem(new Item.Properties().stacksTo(16).setId(ResourceKey.create(Registries.ITEM, ID))));

	private RuinClueItem(final Properties properties) {
		super(properties);
	}

	/** Loads the class, which registers the item and its component. */
	public static void register() {
	}

	public static ItemStack of(final String siteId) {
		ItemStack stack = new ItemStack(ITEM);
		stack.set(CLUE_SITE, siteId);
		return stack;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		String siteId = stack.get(CLUE_SITE);
		if (siteId == null) {
			return InteractionResult.PASS;
		}
		long start = System.nanoTime();
		Optional<BlockPos> target = RuinClues.locate(server, siteId, player.blockPosition());
		long millis = (System.nanoTime() - start) / 1_000_000L;
		StellaRuins.LOGGER.debug("[RUINS] clue site={} search took {} ms found={}", siteId, millis, target.map(BlockPos::toShortString).orElse("none"));
		if (target.isEmpty()) {
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.clue_nothing", "The clue leads nowhere you can reach."));
			return InteractionResult.CONSUME;
		}
		ItemStack map = RuinClues.mapTo(server, siteId, target.get());
		player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, map));
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendHoverText(
		final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> consumer, final TooltipFlag flag
	) {
		String siteId = stack.get(CLUE_SITE);
		if (siteId != null) {
			consumer.accept(Component.translatableWithFallback("clue.mod-stella-ruins." + siteId, "The way is hidden."));
		}
	}
}
