package com.banktaglayouts;

import com.banktaglayouts.BtlMenuSwapper.WithdrawMode;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

@Slf4j
public class MenuSwapperOverlay extends Overlay
{
	@Inject
	private Client client;

	@Inject
	private BankTagLayoutsPlugin plugin;

	@Inject
	private BtlMenuSwapper swapper;

	MenuSwapperOverlay()
	{
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		BankTagLayoutsPlugin.LayoutableThing currentLayoutableThing = plugin.getCurrentLayoutableThing();
		if (currentLayoutableThing == null) return null;

		if (client.isMenuOpen() && swapper.isWithdrawMenu()) {
			for (int i = 0; i < client.getMenuEntries().length; i++)
			{
				WithdrawMode withdrawMode = WithdrawMode.fromMenuEntry(client.getMenuEntries()[i], swapper.getJagexBankSwapVarbit());
				if (withdrawMode == null) continue;

				Rectangle menuOptionExtraOptionClickbox = swapper.getExtraOptionMenuEntryClickbox(i);
				if (menuOptionExtraOptionClickbox.contains(new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY()))) {
					graphics.setColor(Color.YELLOW);
				} else {
					graphics.setColor(Color.WHITE);
				}
				int boxSize = 8;
				int topLeftX = (int) menuOptionExtraOptionClickbox.getX() + 5;
				int topLeftY = (int) menuOptionExtraOptionClickbox.getY() + 3;
				if (currentLayoutableThing.getLayout().getWithdrawMode(swapper.getMenuBankIndex()) == withdrawMode) {
					graphics.fillRect(topLeftX, topLeftY, boxSize, boxSize);
				} else {
					graphics.drawRect(topLeftX, topLeftY, boxSize, boxSize);
				}
			}
		}

		return null;
	}
}
