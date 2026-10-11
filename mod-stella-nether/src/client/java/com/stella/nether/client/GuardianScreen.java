package com.stella.nether.client;

import com.stella.nether.GuardianWindow;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Blaze Guardian's raid window: a button for each raid, showing its level, and a Cancel button. A raid that
 * runs is joined and one that does not is hosted. Closing the window any other way cancels it.
 */
public final class GuardianScreen extends Screen {
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 6;

	private final List<GuardianWindow.Row> rows;
	/** Set once a pick or a cancel has been sent, so closing never sends a second. */
	private boolean sent;

	public GuardianScreen(final GuardianWindow.Open open) {
		super(Component.translatable("screen.mod-stella-nether.guardian"));
		this.rows = open.rows();
	}

	@Override
	protected void init() {
		int count = this.rows.size() + 1;
		int top = this.height / 2 - (count * (BUTTON_HEIGHT + GAP)) / 2;
		int left = this.width / 2 - BUTTON_WIDTH / 2;
		this.addRenderableWidget(new StringWidget(left, top - 24, BUTTON_WIDTH, 12, this.title, this.font));
		for (int index = 0; index < this.rows.size(); index++) {
			GuardianWindow.Row row = this.rows.get(index);
			String key = "screen.mod-stella-nether." + (row.running() ? "join." : "host.") + row.raidMode().name().toLowerCase(Locale.ROOT);
			int picked = index;
			this.addRenderableWidget(Button.builder(Component.translatable(key, row.level()), pressed -> this.pick(picked))
				.bounds(left, top + index * (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable(key + ".hover", row.level())))
				.build());
		}
		this.addRenderableWidget(Button.builder(Component.translatable("screen.mod-stella-nether.cancel"), pressed -> this.onClose())
			.bounds(left, top + this.rows.size() * (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
			.build());
	}

	private void pick(final int option) {
		this.sent = true;
		ClientPlayNetworking.send(new GuardianWindow.Chosen(option));
		this.onClose();
	}

	/** Closed without a pick, by Cancel, Escape or otherwise: the server forgets the window and nothing else happens. */
	@Override
	public void onClose() {
		if (!this.sent) {
			this.sent = true;
			ClientPlayNetworking.send(new GuardianWindow.Cancelled());
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
