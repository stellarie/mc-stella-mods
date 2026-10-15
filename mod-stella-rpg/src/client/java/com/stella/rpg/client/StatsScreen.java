package com.stella.rpg.client;


import com.mojang.blaze3d.platform.InputConstants;
import com.stella.rpg.AttackPreview;
import com.stella.rpg.DefPreview;
import com.stella.rpg.PendingStats;
import com.stella.rpg.RpgConfig;
import com.stella.rpg.RpgConfigStore;
import com.stella.rpg.DrawSpeed;
import com.stella.rpg.RpgCurve;
import com.stella.rpg.RpgDice;
import com.stella.rpg.RpgSpells;
import com.stella.rpg.Stat;
import com.stella.rpg.StatBonuses;
import com.stella.rpg.StatPoints;
import com.stella.rpg.StatSheet;
import com.stella.rpg.net.AllocateBatchPayload;
import com.stella.rpg.net.RespecPayload;
import com.stella.rpg.net.StatsSyncPayload;
import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

@Environment(EnvType.CLIENT)
public final class StatsScreen extends Screen {
	private static final int PANEL_WIDTH = 256;
	private static final int PANEL_HEIGHT = 200;
	private static final int MARGIN = 8;
	private static final int ROWS_Y = 24;
	private static final int ROW_STEP = 22;
	private static final int ROW_TEXT_WIDTH = 100;
	private static final int STEP_BUTTON = 14;
	private static final int MINUS_X = 112;
	private static final int PLUS_X = 128;
	private static final int RIGHT_X = 152;
	private static final int RIGHT_Y = 27;
	private static final int RIGHT_STEP = 13;
	/** Atk, Matk, M.Res, M.Pen, A.Pen, Def, Crit, Aspd. */
	private static final int DERIVED_LINES = 8;
	private static final int POINTS_GAP = 3;
	private static final int MESSAGE_Y = 154;
	private static final int MESSAGE_WIDTH = 124;
	private static final int ACTION_WIDTH = 110;
	private static final int ACTION_HEIGHT = 20;
	private static final int ADD_Y = 152;
	private static final int RESET_Y = 174;
	private static final long CONFIRM_MILLIS = 5000L;
	private static final int SHIFT_AMOUNT = 10;
	private static final int TEXT = 0xFF404040;
	private static final int ERROR = 0xFFAA0000;

	private final Button[] minus = new Button[Stat.values().length];
	private final Button[] plus = new Button[Stat.values().length];
	private final PendingStats pending = new PendingStats();
	private Button add;
	private Button reset;
	private int leftPos;
	private int topPos;
	private long confirmUntil;
	private StatsSyncPayload seen;
	private boolean sent;

	public StatsScreen() {
		super(Component.translatable("screen.mod-stella-rpg.stats"));
	}

	@Override
	protected void init() {
		this.leftPos = (this.width - PANEL_WIDTH) / 2;
		this.topPos = (this.height - PANEL_HEIGHT) / 2;
		for (Stat stat : Stat.values()) {
			int y = this.topPos + ROWS_Y + stat.ordinal() * ROW_STEP;
			this.minus[stat.ordinal()] = this.addRenderableWidget(Button.builder(Component.literal("-"), button -> this.remove(stat))
				.bounds(this.leftPos + MINUS_X, y, STEP_BUTTON, STEP_BUTTON)
				.tooltip(Tooltip.create(Component.translatable("screen.mod-stella-rpg.minus_help", SHIFT_AMOUNT)))
				.build());
			this.plus[stat.ordinal()] = this.addRenderableWidget(Button.builder(Component.literal("+"), button -> this.spend(stat))
				.bounds(this.leftPos + PLUS_X, y, STEP_BUTTON, STEP_BUTTON)
				.tooltip(Tooltip.create(Component.translatable("screen.mod-stella-rpg.plus_help", SHIFT_AMOUNT)))
				.build());
		}
		int actionX = this.leftPos + PANEL_WIDTH - MARGIN - ACTION_WIDTH;
		this.add = this.addRenderableWidget(Button.builder(Component.translatable("screen.mod-stella-rpg.add"), button -> this.addClicked())
			.bounds(actionX, this.topPos + ADD_Y, ACTION_WIDTH, ACTION_HEIGHT)
			.build());
		this.reset = this.addRenderableWidget(Button.builder(this.resetLabel(), button -> this.resetClicked())
			.bounds(actionX, this.topPos + RESET_Y, ACTION_WIDTH, ACTION_HEIGHT)
			.build());
	}

	@Override
	public void removed() {
		this.pending.clear();
		super.removed();
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
		this.syncPending(data);
		this.updateWidgets(data);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		StatsTabs.draw(graphics, this.font, this.leftPos, this.topPos, StatsTabs.STATS, mouseX, mouseY);
		graphics.text(this.font, this.title, this.leftPos + MARGIN, this.topPos + 8, TEXT, false);
		this.drawRows(graphics, data);
		this.drawDerived(graphics, data);
		graphics.text(
			this.font,
			Component.translatable("screen.mod-stella-rpg.points", data.unspent() - this.pending.total()),
			this.leftPos + RIGHT_X,
			this.topPos + RIGHT_Y + DERIVED_LINES * RIGHT_STEP + POINTS_GAP,
			TEXT,
			false
		);
		StatPoints.Result failure = ClientStats.failure();
		if (failure != StatPoints.Result.OK) {
			graphics.textWithWordWrap(
				this.font,
				Component.translatable("screen.mod-stella-rpg.result." + failure.name().toLowerCase(Locale.ROOT)),
				this.leftPos + MARGIN,
				this.topPos + MESSAGE_Y,
				MESSAGE_WIDTH,
				ERROR
			);
		}
		this.drawStatTooltip(graphics, data, mouseX, mouseY);
	}

	int left() {
		return this.leftPos;
	}

	int top() {
		return this.topPos;
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (this.minecraft.options.keyInventory.matches(event)) {
			StatsTabs.openInventory(this.minecraft);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void syncPending(final StatsSyncPayload data) {
		if (data != this.seen) {
			this.seen = data;
			this.sent = false;
			if (data.resultCode() == StatPoints.Result.OK) {
				this.pending.clear();
			}
		}
		this.pending.clamp(data.unspent(), data.base());
	}

	private void updateWidgets(final StatsSyncPayload data) {
		int free = data.unspent() - this.pending.total();
		for (Stat stat : Stat.values()) {
			int row = stat.ordinal();
			this.plus[row].active = free > 0 && data.base()[row] + this.pending.get(stat) < StatSheet.CEILING;
			this.minus[row].active = this.pending.get(stat) > 0;
		}
		this.add.active = this.pending.total() > 0 && !this.sent;
		if (this.confirmUntil != 0L && Util.getMillis() >= this.confirmUntil) {
			this.confirmUntil = 0L;
		}
		this.reset.setMessage(this.resetLabel());
	}

	private void spend(final Stat stat) {
		StatsSyncPayload data = ClientStats.get();
		if (data != null) {
			this.pending.plus(stat, step(), data.unspent(), data.base()[stat.ordinal()]);
		}
	}

	private void remove(final Stat stat) {
		this.pending.minus(stat, step());
	}

	private void addClicked() {
		if (this.pending.total() > 0 && !this.sent) {
			this.sent = true;
			ClientPlayNetworking.send(new AllocateBatchPayload(this.pending.toArray()));
		}
	}

	private void resetClicked() {
		if (this.confirmUntil != 0L && Util.getMillis() < this.confirmUntil) {
			this.confirmUntil = 0L;
			ClientPlayNetworking.send(new RespecPayload());
		} else {
			this.confirmUntil = Util.getMillis() + CONFIRM_MILLIS;
		}
	}

	private static int step() {
		return shiftHeld() ? SHIFT_AMOUNT : 1;
	}

	private static boolean shiftHeld() {
		return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
	}

	private Component resetLabel() {
		return this.confirmUntil != 0L
			? Component.translatable("screen.mod-stella-rpg.reset_confirm")
			: Component.translatable("screen.mod-stella-rpg.reset", StatPoints.RESPEC_EMERALDS);
	}

	private int effective(final StatsSyncPayload data, final Stat stat) {
		return this.pending.effective(stat, data.base()[stat.ordinal()], data.bonus()[stat.ordinal()]);
	}

	private void drawRows(final GuiGraphicsExtractor graphics, final StatsSyncPayload data) {
		for (Stat stat : Stat.values()) {
			int bonus = data.bonus()[stat.ordinal()];
			MutableComponent bonusText = Component.literal(Integer.toString(bonus));
			if (bonus > 0) {
				bonusText.withStyle(ChatFormatting.DARK_GREEN);
			}
			int staged = this.pending.get(stat);
			Component stagedText = staged > 0
				? Component.translatable("screen.mod-stella-rpg.pending", staged).withStyle(ChatFormatting.DARK_BLUE)
				: Component.empty();
			Component row = Component.translatable(
				"screen.mod-stella-rpg.row",
				Component.translatable("screen.mod-stella-rpg.stat." + stat.key()),
				data.base()[stat.ordinal()],
				stagedText,
				bonusText
			);
			int y = this.topPos + ROWS_Y + stat.ordinal() * ROW_STEP + 3;
			graphics.text(this.font, row, this.leftPos + MARGIN, y, TEXT, false);
		}
	}

	private void drawDerived(final GuiGraphicsExtractor graphics, final StatsSyncPayload data) {
		double[] attack = AttackPreview.heldRanged(this.minecraft.player)
			? AttackPreview.ranged(this.minecraft.player, this.effective(data, Stat.DEX))
			: AttackPreview.range(this.minecraft.player, this.effective(data, Stat.STR));
		List<Component> lines = List.of(
			Component.translatable(
				"screen.mod-stella-rpg.atk",
				AttackPreview.format(attack)
			),
			Component.translatable(
				"screen.mod-stella-rpg.matk",
				AttackPreview.number(this.matk(this.effective(data, Stat.INT)))
			),
			Component.translatable(
				"screen.mod-stella-rpg.mres",
				percent(this.resistance(this.effective(data, Stat.INT), this.effective(data, Stat.VIT)))
			),
			Component.translatable(
				"screen.mod-stella-rpg.mpen",
				percent(Affixes.total(this.minecraft.player, Affix.MAGIC_PEN))
			),
			Component.translatable(
				"screen.mod-stella-rpg.apen",
				percent(this.armorPenetration())
			),
			Component.translatable(
				"screen.mod-stella-rpg.def",
				percent(DefPreview.combined(
					this.minecraft.player.getAttributeValue(Attributes.ARMOR),
					1.0 - RpgCurve.damageTakenMultiplier(this.effective(data, Stat.VIT))
				)),
				data.flatDefense()
			),
			Component.translatable(
				"screen.mod-stella-rpg.crit",
				percent(RpgCurve.critChance(this.effective(data, Stat.LUK)))
			),
			Component.translatable("screen.mod-stella-rpg.aspd", String.format(Locale.ROOT, "%.2f", this.previewSpeed(data)))
		);
		for (int line = 0; line < lines.size(); line++) {
			graphics.text(this.font, lines.get(line), this.leftPos + RIGHT_X, this.topPos + RIGHT_Y + line * RIGHT_STEP, TEXT, false);
		}
	}

	private double matk(final int intel) {
		double flat = RpgSpells.matkFromInt(intel) + RpgSpells.matkFlat(this.minecraft.player);
		return flat * (1.0 + RpgSpells.matkPercent(this.minecraft.player));
	}

	private double armorPenetration() {
		RpgConfig.MagicRules rules = RpgConfigStore.current().magic();
		return Math.min(rules.armorPenCap(), rules.armorPenBase() + Affixes.total(this.minecraft.player, Affix.ARMOR_PEN));
	}

	private double resistance(final int intel, final int vit) {
		return RpgSpells.resistance(intel, vit, Affixes.total(this.minecraft.player, Affix.MAGIC_RES_PCT));
	}

	private double previewSpeed(final StatsSyncPayload data) {
		AttributeInstance instance = this.minecraft.player.getAttribute(Attributes.ATTACK_SPEED);
		int agi = Stat.AGI.ordinal();
		int current = Math.max(0, Math.min(StatBonuses.EFFECTIVE_CEILING, data.base()[agi] + data.bonus()[agi]));
		double delta = RpgCurve.attackSpeedBonus(this.effective(data, Stat.AGI)) - RpgCurve.attackSpeedBonus(current);
		return instance.getValue() + instance.getBaseValue() * delta;
	}

	private void drawStatTooltip(final GuiGraphicsExtractor graphics, final StatsSyncPayload data, final int mouseX, final int mouseY) {
		for (Stat stat : Stat.values()) {
			int top = this.topPos + ROWS_Y + stat.ordinal() * ROW_STEP;
			boolean over = mouseX >= this.leftPos + MARGIN && mouseX < this.leftPos + MARGIN + ROW_TEXT_WIDTH
				&& mouseY >= top && mouseY < top + STEP_BUTTON;
			if (over) {
				graphics.setComponentTooltipForNextFrame(this.font, this.effectLines(data, stat), mouseX, mouseY);
				return;
			}
		}
	}

	private List<Component> effectLines(final StatsSyncPayload data, final Stat stat) {
		int value = this.effective(data, stat);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("screen.mod-stella-rpg.name." + stat.key()).withStyle(ChatFormatting.GOLD));
		switch (stat) {
			case STR -> lines.add(Component.translatable("screen.mod-stella-rpg.tip.melee_dice", RpgDice.of(value).toString()));
			case AGI -> {
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.attack_speed", percent(RpgCurve.attackSpeedBonus(value))));
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.bow_draw",
					DrawSpeed.fullDrawTicks(value, this.effective(data, Stat.DEX))));
			}
			case VIT -> lines.add(Component.translatable(
				"screen.mod-stella-rpg.tip.damage_taken", percent(1.0 - RpgCurve.damageTakenMultiplier(value))));
			case DEX -> {
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.ranged_dice", RpgDice.of(value).toString()));
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.bow_draw",
					DrawSpeed.fullDrawTicks(this.effective(data, Stat.AGI), value)));
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.cooldown",
					percent(1.0 - RpgCurve.cooldownMultiplier(value + this.effective(data, Stat.INT)))));
			}
			case INT -> {
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.matk", AttackPreview.number(RpgSpells.matkFromInt(value))));
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.mres", percent(this.resistance(value, this.effective(data, Stat.VIT)))));
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.cooldown",
					percent(1.0 - RpgCurve.cooldownMultiplier(value + this.effective(data, Stat.DEX)))));
			}
			case LUK -> {
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.crit_chance", percent(RpgCurve.critChance(value))));
				lines.add(Component.translatable("screen.mod-stella-rpg.tip.crit_damage", AttackPreview.number(RpgCurve.critMultiplier())));
			}
		}
		return lines;
	}

	private static String percent(final double fraction) {
		return AttackPreview.number(fraction * 100.0);
	}
}
