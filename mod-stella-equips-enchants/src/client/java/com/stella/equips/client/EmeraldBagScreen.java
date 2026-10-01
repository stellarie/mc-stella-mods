package com.stella.equips.client;

import com.stella.equips.bag.EmeraldBag;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Shows the emeralds in the held bag and in the inventory, with deposit and withdraw buttons. */
@Environment(EnvType.CLIENT)
public final class EmeraldBagScreen extends Screen {
	private static final int WIDTH = 220;
	private static final int HEIGHT = 134;
	private static final int[] AMOUNTS = {1, 16, 64, EmeraldBag.ALL};
	private static final int BUTTON_WIDTH = 46;
	private static final int BUTTON_HEIGHT = 20;
	private static final int TEXT = 0xFF404040;
	private final InteractionHand hand;
	private int left;
	private int top;

	public EmeraldBagScreen(final InteractionHand hand) {
		super(Component.translatable("item.mod-stella-equips-enchants.emerald_bag"));
		this.hand = hand;
	}

	public static void register() {
		EmeraldBag.setClientOpener(hand -> Minecraft.getInstance().gui.setScreen(new EmeraldBagScreen(hand)));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (EmeraldBag.isBag(stack)) {
				lines.add(Component.translatable("item.mod-stella-equips-enchants.emerald_bag.count", EmeraldBag.stored(stack))
					.withStyle(ChatFormatting.GREEN));
			}
		});
	}

	@Override
	protected void init() {
		this.left = (this.width - WIDTH) / 2;
		this.top = (this.height - HEIGHT) / 2;
		for (int i = 0; i < AMOUNTS.length; i++) {
			int amount = AMOUNTS[i];
			Component label = Component.literal(amount == EmeraldBag.ALL ? "All" : Integer.toString(amount));
			int x = this.left + 10 + i * (BUTTON_WIDTH + 6);
			this.addRenderableWidget(Button.builder(label, button -> this.send(true, amount))
				.bounds(x, this.top + 44, BUTTON_WIDTH, BUTTON_HEIGHT).build());
			this.addRenderableWidget(Button.builder(label, button -> this.send(false, amount))
				.bounds(x, this.top + 82, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}
		this.addAutoBox();
	}

	private void addAutoBox() {
		ItemStack bag = this.minecraft.player.getItemInHand(this.hand);
		this.addRenderableWidget(Checkbox.builder(Component.translatable("screen.mod-stella-equips-enchants.emerald_bag.auto"), this.font)
			.pos(this.left + 10, this.top + 108)
			.selected(EmeraldBag.isAuto(bag))
			.onValueChange((box, value) -> ClientPlayNetworking.send(new EmeraldBag.AutoRequest(this.hand == InteractionHand.OFF_HAND, value)))
			.build());
	}

	private void send(final boolean deposit, final int amount) {
		ClientPlayNetworking.send(new EmeraldBag.Request(this.hand == InteractionHand.OFF_HAND, deposit, amount));
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, 0xFFC6C6C6);
		graphics.outline(this.left, this.top, WIDTH, HEIGHT, 0xFF373737);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		ItemStack bag = this.minecraft.player.getItemInHand(this.hand);
		if (!EmeraldBag.isBag(bag)) {
			this.onClose();
			return;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int inventory = EmeraldBag.loose(this.minecraft.player.getInventory());
		graphics.text(this.font, this.title, this.left + 10, this.top + 8, TEXT, false);
		graphics.text(this.font, Component.translatable("screen.mod-stella-equips-enchants.emerald_bag.stored", EmeraldBag.stored(bag)),
			this.left + 10, this.top + 22, TEXT, false);
		graphics.text(this.font, Component.translatable("screen.mod-stella-equips-enchants.emerald_bag.carried", inventory),
			this.left + 120, this.top + 22, TEXT, false);
		graphics.text(this.font, Component.translatable("screen.mod-stella-equips-enchants.emerald_bag.deposit"),
			this.left + 10, this.top + 34, TEXT, false);
		graphics.text(this.font, Component.translatable("screen.mod-stella-equips-enchants.emerald_bag.withdraw"),
			this.left + 10, this.top + 72, TEXT, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
