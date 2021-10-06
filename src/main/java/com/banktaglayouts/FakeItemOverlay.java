package com.banktaglayouts;

import com.banktaglayouts.BankTagLayoutsPlugin.BankSlot;
import static com.banktaglayouts.BankTagLayoutsPlugin.BankSlot.Type.DUPLICATE_ITEM;
import static com.banktaglayouts.BankTagLayoutsPlugin.BankSlot.Type.LAYOUT_PLACEHOLDER;
import com.banktaglayouts.Layout.BankItem;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
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
public class FakeItemOverlay extends Overlay {
    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private TooltipManager tooltipManager;

    @Inject
    private BankTagLayoutsPlugin plugin;

    @Inject
    private BankTagLayoutsConfig config;

    FakeItemOverlay()
    {
    	setLayer(OverlayLayer.MANUAL);
        drawAfterLayer(WidgetInfo.BANK_ITEM_CONTAINER);
        setPosition(OverlayPosition.DYNAMIC);
    }

    private Tooltip tooltip = null;

    @Override
    public Dimension render(Graphics2D graphics)
    {
        BankTagLayoutsPlugin.LayoutableThing currentLayoutableThing = plugin.getCurrentLayoutableThing();
        if (currentLayoutableThing == null) return null;

        Layout layout = plugin.getBankOrder(currentLayoutableThing);
        if (layout == null) return null;

        if (config.showLayoutPlaceholders() && log.isDebugEnabled()) updateTooltip(layout);

        Widget bankItemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        if (bankItemContainer == null || bankItemContainer.isHidden()) return null;
        int scrollY = bankItemContainer.getScrollY();
        Point canvasLocation = bankItemContainer.getCanvasLocation();
        Rectangle bankItemArea = new Rectangle(canvasLocation.getX() + 51 - 6, canvasLocation.getY(), bankItemContainer.getWidth() - 51 + 6, bankItemContainer.getHeight());

        graphics.clip(bankItemArea);

		Set<Integer> idsToMarkUnstackableItemCount = plugin.getBankSlotContents().entrySet().stream()
			.filter(e -> e.getValue().getBankItem().getUnstackableIndex() == 1)
			.map(e -> e.getValue().getBankItem().getItemId())
			.collect(Collectors.toSet());

		for (Map.Entry<Integer, BankSlot> entry : plugin.getBankSlotContents().entrySet())
		{
			int index = entry.getKey();
			BankSlot bankSlot = entry.getValue();
			if (bankSlot.getType() == LAYOUT_PLACEHOLDER && !config.showLayoutPlaceholders()) continue;

			int dragDeltaX = 0;
			int dragDeltaY = 0;
			if (index == plugin.draggedItemIndex && plugin.antiDrag.mayDrag()) {
				dragDeltaX = client.getMouseCanvasPosition().getX() - plugin.dragStartX;
				dragDeltaY = client.getMouseCanvasPosition().getY() - plugin.dragStartY;
				dragDeltaY += bankItemContainer.getScrollY() - plugin.dragStartScroll;
			}
			int itemId = bankSlot.getBankItem().getItemId();

			int x = BankTagLayoutsPlugin.getXForIndex(index) + canvasLocation.getX() + dragDeltaX;
			int y = BankTagLayoutsPlugin.getYForIndex(index) + canvasLocation.getY() - scrollY + dragDeltaY;
			if (y + BankTagLayoutsPlugin.BANK_ITEM_HEIGHT > bankItemArea.getMinY() && y < bankItemArea.getMaxY())
			{
				if (bankSlot.getType() == LAYOUT_PLACEHOLDER)
				{
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
					BufferedImage image = itemManager.getImage(itemId, 1000, false);
					graphics.drawImage(image, x, y, image.getWidth(), image.getHeight(), null);
					BufferedImage outline = itemManager.getItemOutline(itemId, 1000, Color.GRAY);
					graphics.drawImage(outline, x, y, null);
				} else if (bankSlot.getType() == DUPLICATE_ITEM) {
					int quantity = bankSlot.getQuantity();
					if (quantity == 0) {
						// placeholder.
						graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
					} else {
						graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
					}
					boolean showQuantity = itemManager.getItemComposition(itemId).isStackable() || quantity != 1;
					BufferedImage image = itemManager.getImage(itemId, quantity, showQuantity);
					graphics.drawImage(image, x, y, image.getWidth(), image.getHeight(), null);

					if (log.isDebugEnabled()) {
						graphics.setColor(Color.PINK);
						graphics.drawString("Dup", x, y + 33);
					}
				}

				if (plugin.showUnstackableItemIndexes() && idsToMarkUnstackableItemCount.contains(itemId)) {
					String text = "#" + (bankSlot.getBankItem().getUnstackableIndex() + 1);
					int stringLength = graphics.getFontMetrics().stringWidth(text);
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
					graphics.setColor(Color.GRAY);
					graphics.drawString(text, x + BankTagLayoutsPlugin.BANK_ITEM_WIDTH - stringLength, y + 33);
				}
			}
		}

        return null;
    }

    private void updateTooltip(Layout layout) {
        tooltipManager.getTooltips().remove(tooltip);
        tooltip = null;

        int index = plugin.getIndexForMousePosition(true);
		if (!log.isDebugEnabled()) return;

		if (index != -1) {
            BankItem bankItem = layout.getItemAtIndex(index);
			if (bankItem != null && tooltip == null) {
                String tooltipString = ColorUtil.wrapWithColorTag(plugin.itemName(bankItem), BankTagLayoutsPlugin.itemTooltipColor);
                if (log.isDebugEnabled())
                    tooltipString += " (" +
						bankItem.getItemId() +
						(plugin.isPlaceholder(bankItem.getItemId()) ? ", ph" : "") +
						(", #" + bankItem.getUnstackableIndex()) +
					")";
                tooltip = new Tooltip(tooltipString);
                tooltipManager.add(tooltip);
            }
        }
    }
}
