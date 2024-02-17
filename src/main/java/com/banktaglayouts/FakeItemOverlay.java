package com.banktaglayouts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

@Slf4j
public class FakeItemOverlay extends Overlay {
    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private BankTagLayoutsPlugin plugin;

    @Inject
    private BankTagLayoutsConfig config;

    FakeItemOverlay()
    {
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

        Widget bankItemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
        if (bankItemContainer == null) return null;
		int scrollY = bankItemContainer.getScrollY();
        Point canvasLocation = bankItemContainer.getCanvasLocation();

		int yOffset = 0;
		Widget widget = bankItemContainer;
        while (widget.getParent() != null) {
			yOffset += widget.getRelativeY();
        	widget = widget.getParent();
		}

		Rectangle bankItemArea = new Rectangle(canvasLocation.getX() + 51 - 6, yOffset, bankItemContainer.getWidth() - 51 + 6, bankItemContainer.getHeight());

        graphics.clip(bankItemArea);

		for (BankTagLayoutsPlugin.FakeItem fakeItem : plugin.fakeItems) {
			if (fakeItem.isLayoutPlaceholder() && !config.showLayoutPlaceholders()) continue;

			int dragDeltaX = 0;
			int dragDeltaY = 0;
			if (fakeItem.index == plugin.draggedItemIndex && plugin.antiDrag.mayDrag()) {
				dragDeltaX = client.getMouseCanvasPosition().getX() - plugin.dragStartX;
				dragDeltaY = client.getMouseCanvasPosition().getY() - plugin.dragStartY;
				dragDeltaY += bankItemContainer.getScrollY() - plugin.dragStartScroll;
			}
			int fakeItemId = fakeItem.getSlot().itemId;

			int x = BankTagLayoutsPlugin.getXForIndex(fakeItem.index) + canvasLocation.getX() + dragDeltaX;
			int y = BankTagLayoutsPlugin.getYForIndex(fakeItem.index) + yOffset - scrollY + dragDeltaY;
			if (y + BankTagLayoutsPlugin.BANK_ITEM_HEIGHT > bankItemArea.getMinY() && y < bankItemArea.getMaxY())
			{
				if (fakeItem.isLayoutPlaceholder())
				{
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
					BufferedImage image = itemManager.getImage(fakeItemId, 1000, false);
					graphics.drawImage(image, x, y, image.getWidth(), image.getHeight(), null);
					BufferedImage outline = itemManager.getItemOutline(fakeItemId, 1000, Color.GRAY);
					graphics.drawImage(outline, x, y, null);
				} else {
					if (fakeItem.quantity == 0) {
						// placeholder.
						graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
					} else {
						graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
					}
					boolean showQuantity = itemManager.getItemComposition(fakeItemId).isStackable() || fakeItem.quantity != 1;
					BufferedImage image = itemManager.getImage(fakeItemId, fakeItem.quantity, showQuantity);
					graphics.drawImage(image, x, y, image.getWidth(), image.getHeight(), null);
				}
			}
		}

        return null;
    }
}
