package com.stella.food.client;

import com.stella.food.CookingStation;
import com.stella.food.CookingStationMenu;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public final class CookingStationScreen extends AbstractContainerScreen<CookingStationMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace(
		"textures/gui/container/crafting_table.png"
	);
	private static final int LIST_WIDTH = 128;
	private static final int LIST_GAP = 4;
	private static final int ENTRY_HEIGHT = 17;

	public CookingStationScreen(
		final CookingStationMenu menu,
		final Inventory inventory,
		final Component title
	) {
		super(menu, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		int combinedWidth = LIST_WIDTH + LIST_GAP + this.imageWidth;
		this.leftPos = Math.max(
			LIST_WIDTH + LIST_GAP,
			(this.width - combinedWidth) / 2 + LIST_WIDTH + LIST_GAP
		);
		this.titleLabelX = 29;
		int listX = this.leftPos - LIST_WIDTH - LIST_GAP + 4;
		int listY = this.topPos + 17;
		for (int entry = 0; entry < 8; entry++) {
			int entryId = entry;
			Button.Builder builder = Button.builder(this.entryName(entry), ignored -> this.selectEntry(entryId))
				.bounds(listX, listY + entry * ENTRY_HEIGHT, LIST_WIDTH - 8, ENTRY_HEIGHT);
			if (entry < 6) {
				builder.tooltip(Tooltip.create(this.dishHelp(entry)));
			} else {
				builder.tooltip(Tooltip.create(Component.translatable(entry == 6
					? "screen.mod-stella-food.cook_help"
					: "screen.mod-stella-food.infuse_help")));
			}
			this.addRenderableWidget(builder.build());
		}
	}

	@Override
	public void extractBackground(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick
	) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			BACKGROUND,
			this.leftPos,
			this.topPos,
			0.0F,
			0.0F,
			this.imageWidth,
			this.imageHeight,
			256,
			256
		);
		int listLeft = this.leftPos - LIST_WIDTH - LIST_GAP;
		graphics.fill(listLeft, this.topPos, listLeft + LIST_WIDTH, this.topPos + this.imageHeight, 0xFF373737);
		graphics.fill(listLeft + 2, this.topPos + 2, listLeft + LIST_WIDTH - 2, this.topPos + 15, 0xFFC6C6C6);
		graphics.text(
			this.font,
			Component.translatable("screen.mod-stella-food.recipes"),
			listLeft + 6,
			this.topPos + 4,
			0xFF404040,
			false
		);
	}

	private Component entryName(final int entry) {
		if (entry < CookingStation.dishRecipes().size()) {
			return new ItemStack(CookingStation.dishRecipes().get(entry).output()).getHoverName();
		}
		return Component.translatable(entry == 6
			? "screen.mod-stella-food.cook_food"
			: "screen.mod-stella-food.infuse_dish");
	}

	private Component dishHelp(final int entry) {
		MutableComponent help = Component.empty();
		List<List<Item>> choices = CookingStation.dishRecipes().get(entry).choices();
		for (int ingredient = 0; ingredient < choices.size(); ingredient++) {
			if (ingredient > 0) {
				help.append(Component.literal("\n"));
			}
			List<Item> alternatives = choices.get(ingredient);
			for (int alternative = 0; alternative < alternatives.size(); alternative++) {
				if (alternative > 0) {
					help.append(Component.literal(" / "));
				}
				help.append(new ItemStack(alternatives.get(alternative)).getHoverName());
			}
		}
		return help;
	}

	private void selectEntry(final int entry) {
		if (this.menu.clickMenuButton(this.minecraft.player, entry)) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, entry);
		}
	}

	@Override
	protected boolean hasClickedOutside(
		final double mouseX,
		final double mouseY,
		final int left,
		final int top
	) {
		int listLeft = this.leftPos - LIST_WIDTH - LIST_GAP;
		boolean outsideList = mouseX < listLeft || mouseX >= this.leftPos - LIST_GAP
			|| mouseY < this.topPos || mouseY >= this.topPos + this.imageHeight;
		return outsideList && super.hasClickedOutside(mouseX, mouseY, left, top);
	}
}
