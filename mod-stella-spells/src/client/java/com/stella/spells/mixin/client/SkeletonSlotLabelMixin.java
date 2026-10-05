package com.stella.spells.mixin.client;

import com.stella.spells.SkeletonMenu;
import com.stella.spells.SummonSkeleton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Names every equipment slot of a summoned skeleton's screen, so a player sees where its gear goes. */
@Mixin(AbstractContainerScreen.class)
public abstract class SkeletonSlotLabelMixin {
	private static final String[] LABELS = {"Head", "Chest", "Legs", "Feet", "Hand", "Off"};

	@Shadow
	protected int leftPos;

	@Shadow
	protected int topPos;

	@Inject(method = "extractSlots", at = @At("TAIL"))
	private void stellaSpells$gearLabels(final GuiGraphicsExtractor extractor, final int mouseX, final int mouseY,
		final CallbackInfo callback) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (!(screen.getMenu() instanceof SkeletonMenu menu)) {
			return;
		}
		var font = Minecraft.getInstance().font;
		for (int index = 0; index < LABELS.length; index++) {
			slotLabel(extractor, font, menu.slots.get(index), LABELS[index], 0xFFFFD24A);
		}
		String mode = SkeletonMenu.mode(menu.skeleton()) == SummonSkeleton.Mode.MIRROR ? "Mirror" : "Search";
		slotLabel(extractor, font, menu.slots.get(SkeletonMenu.MODE_SLOT), mode, 0xFFFFFFFF);
	}

	private void slotLabel(
		final GuiGraphicsExtractor extractor, final net.minecraft.client.gui.Font font,
		final Slot slot, final String label, final int color
	) {
		int x = this.leftPos + slot.x;
		int y = this.topPos + slot.y;
		extractor.fill(x, y - 9, x + font.width(label) + 1, y - 1, 0x90000000);
		extractor.text(font, label, x + 1, y - 8, color);
	}
}
