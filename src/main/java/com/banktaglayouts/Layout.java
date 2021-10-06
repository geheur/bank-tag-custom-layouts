package com.banktaglayouts;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class Layout {

    // Maps indexes to items.
    private final Map<Integer, BankItem> layoutMap = new HashMap<>();

	// TODO Hiding one of them? How to store?

	/**
	 * Represents an item in the bank. This is an itemId and an index to disambiguate between multiple of the same item.
	 */
	@RequiredArgsConstructor
	@EqualsAndHashCode
	@Getter
	public static class BankItem
	{
		private final int itemId;
		/** if -1, then this item does not have multiple unstackable versions of the items recorded in the layout. */
		// TODO should I store when there are no unstackable copies? I do need this info for knowing whether or not to write #1 or #2, and it makes serialization cleaner, but does it actually have to be stored?
		private final int unstackableIndex;

		public BankItem(int itemId) {
			this(itemId, 0);
		}

		public static BankItem fromString(String s)
		{
			int indexOfParentheses = s.indexOf("(");
			if (indexOfParentheses == -1) {
				return new BankItem(Integer.parseInt(s));
			}

			String itemIdString = s.substring(0, indexOfParentheses);
			String unstackableIndexString = s.substring(indexOfParentheses + 1, s.length() - 1);
			return new BankItem(Integer.parseInt(itemIdString), Integer.parseInt(unstackableIndexString));
		}

		@Override
		public String toString()
		{
			return unstackableIndex > 0 ? "" + itemId + "(" + getUnstackableIndex() + ")" : "" + itemId;
		}

		public int switchPlaceholderId(BankTagLayoutsPlugin bankTagLayoutsPlugin)
		{
			// you cannot have multiple of the same placeholder in the real bank, so use the item id.
			return bankTagLayoutsPlugin.switchPlaceholderId(itemId);
		}
	}

	// TODO serialize unstackable items data.
	public static Layout fromString(String layoutString) {
		return fromString(layoutString, false);
    }

    public static Layout fromString(String layoutString, boolean ignoreNfe) {
        Layout layout = Layout.emptyLayout();
        if (layoutString.isEmpty()) return layout;
        for (String s1 : layoutString.split(",")) {
            String[] split = s1.split(":");
            try {
                BankItem bankItem = BankItem.fromString(split[0]);
                int index = Integer.parseInt(split[1]);
                if (index >= 0) {
                    layout.putItem(index, bankItem);
                } else {
                    log.debug("Removed item " + bankItem.getItemId() + " due to it having a negative index (" + index + ")");
                }
            } catch (NumberFormatException e) {
                if (!ignoreNfe) throw e;
                log.debug("input string \"" + layoutString + "\"");
            }
        }
        return layout;
    }

    public static Layout emptyLayout() {
        return new Layout();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, BankItem> entry : allPairs()) {
            sb.append(entry.getValue() + ":" + entry.getKey() + ",");
        }
        if (sb.length() > 0) {
            sb.delete(sb.length() - 1, sb.length());
        }
        System.out.println("returning \"" + sb.toString() + "\"");
        return sb.toString();
    }

	public void putItem(int index, BankItem bankItem) {
		layoutMap.put(index, bankItem);
	}

	/** returns null if there is no item there. */
    public BankItem getItemAtIndex(int index) {
    	return layoutMap.get(index);
    }

    public Iterator<Map.Entry<Integer, BankItem>> allPairsIterator() {
        return layoutMap.entrySet().iterator();
    }

	public int getIndexForItem(BankItem bankItem) {
		return allPairs().stream()
			.filter(e -> e.getValue().equals(bankItem))
			.map(e -> e.getKey())
			.findAny().orElse(-1);
	}

	/**
	 * Finds the index for the EXACT itemId. Does not factor in placeholders or variation items. For duplicated items,
	 * it returns one of the indexes where the itemId can be found.
	 * If there's no index for this itemId, then it returns -1.
	 */
	public int getIndexForItemId(int itemId)
	{
		return allPairs().stream()
			.filter(e -> e.getValue().getItemId() == itemId)
			.map(e -> e.getKey())
			.findAny().orElse(-1);
	}

	/**
	 * Finds the indexes for the EXACT itemId. Does not factor in placeholders or variation items.
     * If there're no indexes for this itemId, then it returns an empty list.
     */
    private List<Integer> getIndexesForItem(BankItem bankItem)
    {
        return allPairs().stream()
			.filter(e -> e.getValue().equals(bankItem))
			.map(e -> e.getKey())
			.collect(Collectors.toList());
    }

    public Collection<BankItem> getAllUsedItemIds() {
        return new HashSet<>(layoutMap.values());
    }

    public Collection<Integer> getAllUsedIndexes() {
        return layoutMap.keySet();
    }

    public Collection<Map.Entry<Integer, BankItem>> allPairs() {
        return layoutMap.entrySet();
    }

    public int getFirstEmptyIndex() {
        return getFirstEmptyIndex(-1);
    }

    public int getFirstEmptyIndex(int afterThisIndex) {
        List<Integer> indexes = new ArrayList<>(getAllUsedIndexes());
        indexes.sort(Integer::compare);
        for (Integer integer : indexes) {
            if (integer < afterThisIndex) continue;

            if (integer - afterThisIndex > 1) {
                break;
            }
            afterThisIndex = integer;
        }
        return afterThisIndex + 1;
    }

    public void clearIndex(int index) {
        layoutMap.remove(index);
    }

    /**
     * @param draggedItemIndex dragged item's original index.
     * @param targetIndex target location's index.
     * @param draggedItem BankItem representing the dragged item.
     */
    public void moveItem(int draggedItemIndex, int targetIndex, BankItem draggedItem) {
    	System.out.println("moving " + draggedItemIndex + " " + targetIndex + " " + draggedItem);
    	assert draggedItem != null;

        BankItem draggedLayoutItem = getItemAtIndex(draggedItemIndex);
        if (draggedItem == null) { // dragging a layout placeholder, or bad input.
            draggedItem = draggedLayoutItem;
            assert draggedItem != null;
        } else if (!draggedLayoutItem.equals(draggedItem)) {
            // Modifying a layout should use the real item there, NOT the item id stored in the layout (which can be
            // different due to how variant items are assigned indexes), because the item the user sees themselves
            // moving is the item id in the widget, not the item id in the layout. Therefore, the duplicates must be
            // updated to use that id as well.
			System.out.println("here");
            for (Integer index : getIndexesForItem(draggedLayoutItem)) {
                putItem(index, draggedItem);
            }
        }

        BankItem targetItem = getItemAtIndex(targetIndex);

        System.out.println(targetItem + " " + draggedItem);
        clearIndex(draggedItemIndex);
        clearIndex(targetIndex);
        putItem(targetIndex, draggedItem);
        if (targetItem != null) {
            putItem(draggedItemIndex, targetItem);
        }
    }

    public boolean isEmpty()
    {
        return layoutMap.isEmpty();
    }

    public int countItems(BankItem bankItem)
    {
        int count = 0;
        for (Map.Entry<Integer, BankItem> pair : allPairs())
        {
            if (pair.getValue().equals(bankItem)) {
                count++;
            }
        }
        return count;
    }

	public void duplicateItem(int clickedItemIndex)
	{
		duplicateItem(clickedItemIndex, null);
	}

	// TODO create test.
    public void duplicateItem(int clickedItemIndex, BankItem itemAtIndex)
    {
        int duplicatedItemIndex = getFirstEmptyIndex(clickedItemIndex);

        BankItem layoutItem = getItemAtIndex(clickedItemIndex);
        if (itemAtIndex == null) {
        	itemAtIndex = layoutItem;
		} else if (!layoutItem.equals(itemAtIndex)) {
            // Modifying a layout should always use the real item there, NOT the item id stored in the layout (which can
            // be different due to how variant items are assigned indexes).
            // Therefore, the duplicates must be updated to use that id as well.
            List<Integer> indexesToChange = getIndexesForItem(layoutItem);
            for (Integer index : indexesToChange) {
                putItem(index, itemAtIndex);
            }
        }

        putItem(duplicatedItemIndex, itemAtIndex);
    }
}
