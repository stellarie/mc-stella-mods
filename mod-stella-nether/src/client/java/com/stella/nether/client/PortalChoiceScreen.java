package com.stella.nether.client;

import com.stella.nether.PortalWindow;
import com.stella.nether.RaidMode;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The portal's choice: three options stacked. It stays open in the portal until a click, which sends the player on
 * at once, or a cancel, which steps them back out.
 */
public final class PortalChoiceScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 6;

	private final @Nullable RaidMode running;
	/** Set once a choice or a cancel has been sent, so closing never sends a second. */
	private boolean chosen;

	public PortalChoiceScreen(final PortalWindow.Open open) {
		super(Component.translatable("screen.mod-stella-nether.portal"));
		this.running = open.runningMode();
	}

	@Override
	protected void init() {
		int count = PortalWindow.Option.values().length;
		int top = this.height / 2 - (count * (BUTTON_HEIGHT + GAP)) / 2;
		int left = this.width / 2 - BUTTON_WIDTH / 2;
		StringWidget title = new StringWidget(left, top - 24, BUTTON_WIDTH, 12, this.title, this.font);
		this.addRenderableWidget(title);
		for (PortalWindow.Option option : PortalWindow.Option.values()) {
			String key = key(option);
			Button button = Button.builder(Component.translatable("screen." + key), pressed -> this.choose(option))
				.bounds(left, top + option.ordinal() * (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("screen." + key + ".hover")))
				.build();
			button.active = option.open(this.running);
			this.addRenderableWidget(button);
		}
	}

	/** The option's label key, below {@code screen.}: to host, to join, or closed while a raid of the other mode runs. */
	private String key(final PortalWindow.Option option) {
		if (option == PortalWindow.Option.NORMAL_NETHER) {
			return "mod-stella-nether.normal";
		}
		String mode = option.mode().name().toLowerCase(Locale.ROOT);
		if (this.running == null) {
			return "mod-stella-nether.host." + mode;
		}
		return option.open(this.running) ? "mod-stella-nether.join." + mode : "mod-stella-nether.closed." + mode;
	}

	private void choose(final PortalWindow.Option option) {
		this.chosen = true;
		ClientPlayNetworking.send(new PortalWindow.Chosen(option.ordinal()));
		this.onClose();
	}

	/** Closed without a choice, by Escape or otherwise: the server steps the player back out of the portal. */
	@Override
	public void onClose() {
		if (!this.chosen) {
			this.chosen = true;
			ClientPlayNetworking.send(new PortalWindow.Cancelled());
		}
		super.onClose();
	}

	/** The window is opened from inside a portal, where the client closes any screen that does not allow it. */
	@Override
	public boolean isAllowedInPortal() {
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
