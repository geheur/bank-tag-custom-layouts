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
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

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
        // layout is null for CORE-layout tabs (RuneLite renders those). We still draw combo boxes there —
        // the combo cell index IS the core layout position — so only bail if there's nothing to draw.
        java.util.Map<Integer, String> comboCellGroups = plugin.getComboCellGroups(currentLayoutableThing);
        if (layout == null && comboCellGroups.isEmpty()) return null;

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

		// Background combo highlight draws BEHIND the fake items (so a ghost item shows on top of its tint
		// rather than under a mask). Outline/Dot/Underline are drawn on top, after the items.
		drawComboHighlights(graphics, true, comboCellGroups, canvasLocation, yOffset, scrollY, bankItemArea);

		for (BankTagLayoutsPlugin.FakeItem fakeItem : plugin.fakeItems) {
			// A combo cell's unowned ghost is a plain placeholder, NOT a layout placeholder: it always draws
			// (independent of the layout-placeholder toggle) and uses the standard faded look below.
			boolean comboGhost = fakeItem.isLayoutPlaceholder() && comboCellGroups.containsKey(fakeItem.index);
			if (fakeItem.isLayoutPlaceholder() && !comboGhost && !config.showLayoutPlaceholders()) continue;

			int dragDeltaX = 0;
			int dragDeltaY = 0;
			if (fakeItem.index == plugin.draggedItemIndex && plugin.antiDrag.mayDrag()) {
				dragDeltaX = client.getMouseCanvasPosition().getX() - plugin.dragStartX;
				dragDeltaY = client.getMouseCanvasPosition().getY() - plugin.dragStartY;
				dragDeltaY += bankItemContainer.getScrollY() - plugin.dragStartScroll;
			}
			int fakeItemId = fakeItem.getItemId();

			int x = BankTagLayoutsPlugin.getXForIndex(fakeItem.index) + canvasLocation.getX() + dragDeltaX;
			int y = BankTagLayoutsPlugin.getYForIndex(fakeItem.index) + yOffset - scrollY + dragDeltaY;
			if (y + BankTagLayoutsPlugin.BANK_ITEM_HEIGHT > bankItemArea.getMinY() && y < bankItemArea.getMaxY())
			{
				if (comboGhost)
				{
					// Plain faded placeholder (like a standard bank placeholder): no dim gray outline.
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
					BufferedImage image = itemManager.getImage(fakeItemId, 1, false);
					graphics.drawImage(image, x, y, image.getWidth(), image.getHeight(), null);
				} else if (fakeItem.isLayoutPlaceholder())
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

		// Foreground combo highlights (Outline/Dot/Underline) draw ON TOP of the items, here.
		drawComboHighlights(graphics, false, comboCellGroups, canvasLocation, yOffset, scrollY, bankItemArea);

        return null;
    }

    /**
     * Draws the combo "smart cell" highlights for the current style. The cell index is the grid position in
     * either layout format, so getComboCellGroups gives the positions. {@code backgroundPass} selects which
     * styles render: the Background fill runs in the pre-pass (BEFORE the fake items, so a ghost item isn't
     * masked by its own tint); Outline/Dot/Underline run in the post-pass (on top of the items).
     */
    private void drawComboHighlights(Graphics2D graphics, boolean backgroundPass,
        java.util.Map<Integer, String> comboCellGroups, Point canvasLocation, int yOffset, int scrollY,
        Rectangle bankItemArea)
    {
        BankTagLayoutsConfig.ComboHighlight style = config.comboHighlightStyle();
        if (style == BankTagLayoutsConfig.ComboHighlight.NONE
            || backgroundPass != (style == BankTagLayoutsConfig.ComboHighlight.BACKGROUND))
        {
            return;
        }

        // When RuneLite is dragging the cell's item widget (CORE tabs), offset the highlight by the same
        // amount so it follows the item under the cursor instead of staying behind at the cell's grid slot.
        Widget draggedWidget = client.getDraggedWidget();
        int draggedIndex = (draggedWidget != null && draggedWidget.getId() == ComponentID.BANK_ITEM_CONTAINER)
            ? draggedWidget.getIndex() : -1;

        for (java.util.Map.Entry<Integer, String> cell : comboCellGroups.entrySet())
        {
            int index = cell.getKey();
            int dragDeltaX = 0;
            int dragDeltaY = 0;
            if (index == draggedIndex)
            {
                dragDeltaX = client.getMouseCanvasPosition().getX() - plugin.comboDragPressX;
                dragDeltaY = client.getMouseCanvasPosition().getY() - plugin.comboDragPressY;
                dragDeltaY += scrollY - plugin.comboDragPressScroll;
            }
            int x = BankTagLayoutsPlugin.getXForIndex(index) + canvasLocation.getX() + dragDeltaX;
            int y = BankTagLayoutsPlugin.getYForIndex(index) + yOffset - scrollY + dragDeltaY;
            if (y + BankTagLayoutsPlugin.BANK_ITEM_HEIGHT > bankItemArea.getMinY() && y < bankItemArea.getMaxY())
            {
                drawComboHighlight(graphics, style, plugin.getComboColor(cell.getValue()), x, y, bankItemArea);
            }
        }
    }

    /** Draws one combo cell's highlight in the configured style, at the cell's top-left item position (x, y). */
    private void drawComboHighlight(Graphics2D graphics, BankTagLayoutsConfig.ComboHighlight style, Color color,
        int x, int y, Rectangle clip)
    {
        int left = x - 2;
        int top = Math.max(y - 2, (int) clip.getMinY()); // clamp the top so a partially-scrolled cell isn't off-screen
        int bottom = y + BankTagLayoutsPlugin.BANK_ITEM_HEIGHT;
        int width = BankTagLayoutsPlugin.BANK_ITEM_WIDTH;
        switch (style)
        {
            case OUTLINE:
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                graphics.setColor(color);
                graphics.drawRect(left, top, width, bottom - top);
                break;
            case DOT:
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                graphics.setColor(color);
                int dot = 7;
                graphics.fillOval(left, top, dot, dot); // top-left corner
                break;
            case UNDERLINE:
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                graphics.setColor(color);
                int lineWidth = (int) (width * 0.8); // middle 80% of the cell
                graphics.fillRect(left + (width - lineWidth) / 2, bottom - 2, lineWidth, 2); // double thickness
                break;
            case BACKGROUND:
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
                graphics.setColor(color);
                graphics.fillRect(left, top, width, bottom - top);
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                break;
            default:
                break;
        }
    }
}
