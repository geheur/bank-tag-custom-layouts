package com.banktaglayouts;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

public class BankTagLayoutsToolsOverlay extends Overlay
{
	@Inject private BankTagLayoutsConfig config;
	@Inject private TooltipManager tooltipManager;
	@Inject private BankTagLayoutsToolsPlugin toolsPlugin;
	@Inject private BankTagLayoutsPlugin plugin;

	private Tooltip tooltip = null;

	BankTagLayoutsToolsOverlay() {
		drawAfterLayer(ComponentID.BANK_ITEM_CONTAINER);
		setLayer(OverlayLayer.MANUAL);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		BankTagLayoutsPlugin.LayoutableThing currentLayoutableThing = plugin.getCurrentLayoutableThing();
		if (currentLayoutableThing == null) return null;

		Layout layout = plugin.getBankOrder(currentLayoutableThing);
		if (layout == null) return null;

		if (config.showLayoutPlaceholders()) updateTooltip(layout);

		return null;
	}

	private void updateTooltip(Layout layout) {
		tooltipManager.getTooltips().remove(tooltip);
		tooltip = null;

		int index = plugin.getIndexForMousePosition(true);
		if (plugin.fakeItems.stream().noneMatch(fakeItem -> fakeItem.index == index)) return;

		if (index != -1) {
			int itemIdForTooltip = layout.getItemAtIndex(index);
			if (itemIdForTooltip != -1 && tooltip == null) {
				String tooltipString = ColorUtil.wrapWithColorTag(plugin.itemName(itemIdForTooltip), BankTagLayoutsPlugin.itemTooltipColor);
				tooltipString += " (" + itemIdForTooltip + (plugin.isPlaceholder(itemIdForTooltip) ? ", ph" : "") + ")";
				tooltip = new Tooltip(tooltipString);
				tooltipManager.add(tooltip);
			}
		}
	}
}
