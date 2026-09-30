package com.stella.rpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.stella.rpg.RpgCurve;
import com.stella.rpg.RpgDice;
import com.stella.rpg.Stat;
import com.stella.rpg.StatBonuses;
import com.stella.rpg.StatPoints;
import com.stella.rpg.StatSheet;
import com.stella.rpg.net.AllocatePayload;
import com.stella.rpg.net.RespecPayload;
import com.stella.rpg.net.StatsSyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.attributes.Attributes;

@Environment(EnvType.CLIENT)
public final class StatsScreen extends Screen {
	private static final int PANEL_WIDTH = 176;
	private static final int PANEL_HEIGHT = 166;
	private static final int ROWS_Y = 22;
	private static final int ROW_STEP = 16;
	private static final int LEFT_X = 8;
	private static final int ROW_WIDTH = 64;
	private static final int PLUS_X = 74;
	private static final int PLUS_WIDTH = 14;
	private static final int RIGHT_X = 96;
	private static final int POINTS_Y = 112;
	private static final int MESSAGE_Y = 126;
	private static final int RESPEC_Y = 140;
	private static final long CONFIRM_MILLIS = 5000L;
	private static final int SHIFT_AMOUNT = 10;
	private static final int TEXT = 0xFF404040;
	private static final int ERROR = 0xFFAA0000;

	private final Button[] plus = new Button[Stat.values().length];
	private Button respec;
	private int leftPos;
	private int topPos;
	private boolean placed;
	private long confirmUntil;

	public StatsScreen(final int leftPos, final int topPos) {
		super(Component.translatable("screen.mod-stella-rpg.stats"));
		this.leftPos = leftPos;
		this.topPos = topPos;
	}

	@Override
	protected void init() {
		if (this.placed) {
			this.leftPos = (this.width - PANEL_WIDTH) / 2;
			this.topPos = (this.height - PANEL_HEIGHT) / 2;
		}
		this.placed = true;
		for (Stat stat : Stat.values()) {
			int row = stat.ordinal();
			this.plus[row] = this.addRenderableWidget(Button.builder(Component.literal("+"), button -> this.spend(stat))
				.bounds(this.leftPos + PLUS_X, this.topPos + ROWS_Y + row * ROW_STEP - 2, PLUS_WIDTH, 12)
				.tooltip(Tooltip.create(Component.translatable("screen.mod-stella-rpg.plus_help", SHIFT_AMOUNT)))
				.build());
		}
		this.respec = this.addRenderableWidget(Button.builder(this.respecLabel(), button -> this.respecClicked())
			.bounds(this.leftPos + LEFT_X, this.topPos + RESPEC_Y, PANEL_WIDTH - 2 * LEFT_X, 20)
			.build());
	}

	@Override
	public void extractBackground(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick
	) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int right = this.leftPos + PANEL_WIDTH;
		int bottom = this.topPos + PANEL_HEIGHT;
		graphics.fill(this.leftPos, this.topPos, right, bottom, 0xFFC6C6C6);
		graphics.fill(this.leftPos, this.topPos, right, this.topPos + 2, 0xFFFFFFFF);
		graphics.fill(this.leftPos, this.topPos, this.leftPos + 2, bottom, 0xFFFFFFFF);
		graphics.fill(this.leftPos, bottom - 2, right, bottom, 0xFF555555);
		graphics.fill(right - 2, this.topPos, right, bottom, 0xFF555555);
		graphics.outline(this.leftPos - 1, this.topPos - 1, PANEL_WIDTH + 2, PANEL_HEIGHT + 2, 0xFF000000);
	}

	@Override
	public void extractRenderState(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick
	) {
		StatsSyncPayload data = ClientStats.get();
		if (data == null) {
			this.onClose();
			return;
		}
		this.updateWidgets(data);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		StatsTabs.draw(graphics, this.font, this.leftPos, this.topPos, StatsTabs.STATS, mouseX, mouseY);
		graphics.text(this.font, this.title, this.leftPos + LEFT_X, this.topPos + 8, TEXT, false);
		this.drawRows(graphics, data);
		this.drawDerived(graphics, data);
		graphics.text(
			this.font,
			Component.translatable("screen.mod-stella-rpg.points", data.unspent()),
			this.leftPos + LEFT_X,
			this.topPos + POINTS_Y,
			TEXT,
			false
		);
		StatPoints.Result failure = ClientStats.failure();
		if (failure != StatPoints.Result.OK) {
			graphics.text(
				this.font,
				Component.translatable("screen.mod-stella-rpg.result." + failure.name().toLowerCase(Locale.ROOT)),
				this.leftPos + LEFT_X,
				this.topPos + MESSAGE_Y,
				ERROR,
				false
			);
		}
		this.drawStatTooltip(graphics, data, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (event.button() == 0) {
			int tab = StatsTabs.hit(this.leftPos, this.topPos, event.x(), event.y());
			if (tab == StatsTabs.INVENTORY) {
				StatsTabs.openInventory(this.minecraft);
				return true;
			}
			if (tab == StatsTabs.STATS) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (this.minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void updateWidgets(final StatsSyncPayload data) {
		for (Stat stat : Stat.values()) {
			this.plus[stat.ordinal()].active = this.amountFor(data, stat, 1) > 0;
		}
		if (this.confirmUntil != 0L && Util.getMillis() >= this.confirmUntil) {
			this.confirmUntil = 0L;
		}
		this.respec.setMessage(this.respecLabel());
	}

	private int amountFor(final StatsSyncPayload data, final Stat stat, final int wanted) {
		int room = StatSheet.CEILING - data.base()[stat.ordinal()];
		return Math.max(0, Math.min(wanted, Math.min(data.unspent(), room)));
	}

	private void spend(final Stat stat) {
		StatsSyncPayload data = ClientStats.get();
		if (data == null) {
			return;
		}
		int amount = this.amountFor(data, stat, shiftHeld() ? SHIFT_AMOUNT : 1);
		if (amount > 0) {
			ClientPlayNetworking.send(new AllocatePayload(stat.ordinal(), amount));
		}
	}

	private void respecClicked() {
		if (this.confirmUntil != 0L && Util.getMillis() < this.confirmUntil) {
			this.confirmUntil = 0L;
			ClientPlayNetworking.send(new RespecPayload());
		} else {
			this.confirmUntil = Util.getMillis() + CONFIRM_MILLIS;
		}
	}

	private static boolean shiftHeld() {
		return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
	}

	private Component respecLabel() {
		return this.confirmUntil != 0L
			? Component.translatable("screen.mod-stella-rpg.respec_confirm")
			: Component.translatable("screen.mod-stella-rpg.respec", StatPoints.RESPEC_EMERALDS);
	}

	private static int effective(final StatsSyncPayload data, final Stat stat) {
		int total = data.base()[stat.ordinal()] + data.bonus()[stat.ordinal()];
		return Math.max(0, Math.min(StatBonuses.EFFECTIVE_CEILING, total));
	}

	private void drawRows(final GuiGraphicsExtractor graphics, final StatsSyncPayload data) {
		for (Stat stat : Stat.values()) {
			int bonus = data.bonus()[stat.ordinal()];
			MutableComponent bonusText = Component.literal(Integer.toString(bonus));
			if (bonus > 0) {
				bonusText.withStyle(ChatFormatting.DARK_GREEN);
			}
			Component row = Component.translatable(
				"screen.mod-stella-rpg.row",
				Component.translatable("screen.mod-stella-rpg.stat." + stat.key()),
				data.base()[stat.ordinal()],
				bonusText
			);
			graphics.text(this.font, row, this.leftPos + LEFT_X, this.topPos + ROWS_Y + stat.ordinal() * ROW_STEP, TEXT, false);
		}
	}

	private void drawDerived(final GuiGraphicsExtractor graphics, final StatsSyncPayload data) {
		double attack = this.minecraft.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		double speed = this.minecraft.player.getAttributeValue(Attributes.ATTACK_SPEED);
		List<Component> lines = List.of(
			Component.translatable(
				"screen.mod-stella-rpg.atk",
				number(attack),
				RpgDice.of(effective(data, Stat.STR)).toString()
			),
			Component.translatable(
				"screen.mod-stella-rpg.matk",
				percent(RpgCurve.spellPowerMultiplier(effective(data, Stat.INT)) - 1.0)
			),
			Component.translatable(
				"screen.mod-stella-rpg.def",
				percent(1.0 - RpgCurve.damageTakenMultiplier(effective(data, Stat.VIT))),
				data.flatDefense()
			),
			Component.translatable("screen.mod-stella-rpg.crit", percent(RpgCurve.critChance(effective(data, Stat.LUK)))),
			Component.translatable("screen.mod-stella-rpg.aspd", String.format(Locale.ROOT, "%.2f", speed))
		);
		for (int line = 0; line < lines.size(); line++) {
			graphics.text(this.font, lines.get(line), this.leftPos + RIGHT_X, this.topPos + ROWS_Y + line * ROW_STEP, TEXT, false);
		}
	}

	private void drawStatTooltip(final GuiGraphicsExtractor graphics, final StatsSyncPayload data, final int mouseX, final int mouseY) {
		for (Stat stat : Stat.values()) {
			int top = this.topPos + ROWS_Y + stat.ordinal() * ROW_STEP - 3;
			boolean over = mouseX >= this.leftPos + LEFT_X && mouseX < this.leftPos + LEFT_X + ROW_WIDTH
				&& mouseY >= top && mouseY < top + ROW_STEP;
			if (over) {
				graphics.setComponentTooltipForNextFrame(this.font, effectLines(data, stat), mouseX, mouseY);
				return;
			}
		}
	}

	private static List<Component> effectLines(final StatsSyncPayload data, final Stat stat) {
		int value = effective(data, stat);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("screen.mod-stella-rpg.name." + stat.key()).withStyle(ChatFormatting.GOLD));
		switch (stat) {
			case STR -> lines.add(Component.translatable("screen.mod-stella-rpg.tip.melee_dice", RpgDice.of(value).toString()));
			case AGI -> {
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.attack_speed", percent(RpgCurve.attackSpeedBonus(value))));
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.bow_draw", RpgCurve.fullDrawTicks(value)));
			}
			case VIT -> lines.add(Component.translatable(
				"screen.mod-stella-rpg.tip.damage_taken", percent(1.0 - RpgCurve.damageTakenMultiplier(value))));
			case DEX -> lines.add(Component.translatable("screen.mod-stella-rpg.tip.ranged_dice", RpgDice.of(value).toString()));
			case INT -> {
				lines.add(Component.translatable(
					"screen.mod-stella-rpg.tip.spell_power", percent(RpgCurve.spellPowerMultiplier(value) - 1.0)));
				lines.add(Component.translatable(
					"screen.mod-stella-rpg.tip.cooldown", percent(1.0 - RpgCurve.cooldownMultiplier(value))));
			}
			case LUK -> {
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.crit_chance", percent(RpgCurve.critChance(value))));
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.crit_damage", number(RpgCurve.critMultiplier())));
			}
		}
		return lines;
	}

	private static String percent(final double fraction) {
		return trim(Math.round(fraction * 1000.0) / 10.0);
	}

	private static String number(final double value) {
		return trim(Math.round(value * 10.0) / 10.0);
	}

	private static String trim(final double value) {
		String text = String.format(Locale.ROOT, "%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}
}
