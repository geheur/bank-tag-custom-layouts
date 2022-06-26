package com.banktaglayouts;

import com.banktaglayouts.BtlMenuSwapper.WithdrawMode;
import java.util.Set;
import lombok.AllArgsConstructor;
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

	@AllArgsConstructor
	static final class IndexData {
		public final int itemId; // not the exact itemid that will appear in this spot, due to placeholders/variation items.
		public final WithdrawMode withdrawMode; // null indicates no swap.
	}

    // Key is the index into the layout.
    private final Map<Integer, IndexData> layoutMap = new HashMap<>();

    public static Layout fromString(String layoutString) {
        return fromString(layoutString, false);
    }

	// TODO create test.
    public static Layout fromString(String layoutString, boolean ignoreNfe) {
        Layout layout = Layout.emptyLayout();
        if (layoutString.isEmpty()) return layout;
        for (String s1 : layoutString.split(",")) {
            String[] split = s1.split(":");
            try {
                int itemId = Integer.parseInt(split[0]);
                int index = Integer.parseInt(split[1]);
                WithdrawMode withdrawMode = null;
                for (int i = 2; i < split.length; i++) {
                	String extraArg = split[i];
                	if (extraArg.startsWith("q")) {
                		extraArg = extraArg.substring(1);
                		withdrawMode = WithdrawMode.fromSaveString(extraArg);
                		if (withdrawMode == null) log.warn("found quantity string \"" + extraArg + "\" that is not valid");
					}
				}
                if (index >= 0) {
                    layout.putItem(itemId, index, withdrawMode);
                } else {
                    log.debug("Removed item " + itemId + " due to it having a negative index (" + index + ")");
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
	// TODO create test.
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Integer, IndexData> e : allPairs()) {
			IndexData indexData = e.getValue();
			sb.append(indexData.itemId + ":" + e.getKey());
            if (indexData.withdrawMode != null) {
            	sb.append(":q" + indexData.withdrawMode.saveString);
			}
            sb.append(",");
        }

        // remove trialing comma.
        if (sb.length() > 0) {
            sb.delete(sb.length() - 1, sb.length());
        }

        return sb.toString();
    }

    public void putItem(int itemId, int index) {
    	putItem(itemId, index, null);
    }

	public void putItem(int itemId, int index, WithdrawMode withdrawMode) {
		if (itemId <= 0) {
			layoutMap.remove(index);
			return;
		}
		layoutMap.put(index, new IndexData(itemId, withdrawMode));
	}

	/** returns -1 if there is no item there. */
    public int getItemAtIndex(int index) {
		IndexData indexData = layoutMap.get(index);
		return indexData != null ? indexData.itemId : -1;
    }

	public WithdrawMode getWithdrawMode(int index)
	{
		IndexData indexData = layoutMap.get(index);
		return indexData != null ? indexData.withdrawMode : null;
	}

	public void setWithdrawMode(int index, WithdrawMode withdrawMode)
	{
		IndexData indexData = layoutMap.get(index);
		assert indexData != null;
		if (indexData != null) layoutMap.put(index, new IndexData(indexData.itemId, withdrawMode));
	}

    public Iterator<Map.Entry<Integer, IndexData>> allPairsIterator() {
        return layoutMap.entrySet().iterator();
    }

    /**
     * Finds the index for the EXACT itemId. Does not factor in placeholders or variation items. For duplicated items,
     * it returns one of the indexes where the itemId can be found.
     * If there's no index for this itemId, then it returns -1.
     */
    public Integer getIndexForItem(int itemId) {
        return allPairs().stream()
                .filter(e -> e.getValue().itemId == itemId)
                .map(e -> e.getKey())
                .findAny().orElse(-1);
    }

    /**
     * Finds the indexes for the EXACT itemId. Does not factor in placeholders or variation items.
     * If there're no indexes for this itemId, then it returns an empty list.
     */
    public List<Integer> getIndexesForItem(int itemId)
    {
        return allPairs().stream()
                .filter(e -> e.getValue().itemId == itemId)
                .map(e -> e.getKey())
                .collect(Collectors.toList());
    }

	/**
	 * Counts the indexes with the EXACT itemId. Does not factor in placeholders or variation items.
	 */
	public int countItemsWithId(int itemId)
	{
		return (int) allPairs().stream()
			.filter(e -> e.getValue().itemId == itemId)
			.count();
	}

	/**
	 * Whether this EXACT itemId has duplicates. Does not factor in placeholders or variation items.
	 */
	public boolean itemHasDuplicates(int itemId)
	{
		boolean foundOne = false;
		for (Map.Entry<Integer, IndexData> e : allPairs())
		{
			if (e.getValue().itemId == itemId) {
				if (foundOne) return true;
				foundOne = true;
			}
		}
		return false;
	}

	public Set<Integer> getAllUsedItemIds() {
        return layoutMap.values().stream().map(indexData -> indexData.itemId).collect(Collectors.toSet());
    }

    public Collection<Integer> getAllUsedIndexes() {
        return layoutMap.keySet();
    }

    public Collection<Map.Entry<Integer, IndexData>> allPairs() {
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
     * @param draggedItemId the dragged item widget's item id.
     */
	// TODO create test.
    public void moveItem(int draggedItemIndex, int targetIndex, int draggedItemId) {
		IndexData draggedItem = layoutMap.get(draggedItemIndex);
        if (draggedItemId != -1 && draggedItemId != draggedItem.itemId) { // dragging a real item.
			int layoutItemId = draggedItem.itemId;
        	draggedItem = new IndexData(draggedItemId, draggedItem.withdrawMode);
			// Modifying a layout should use the real item there, NOT the item id stored in the layout (which can be
			// different due to how variant items are assigned indexes), because the item the user sees themselves
			// moving is the item id in the widget, not the item id in the layout. Therefore, the duplicates must be
			// updated to use that id as well.
			for (Integer index : getIndexesForItem(layoutItemId)) {
				layoutMap.put(index, draggedItem);
			}
		}

		IndexData targetItem = layoutMap.get(targetIndex);

        clearIndex(draggedItemIndex);
        clearIndex(targetIndex);
        layoutMap.put(targetIndex, draggedItem);
        if (targetItem != null) {
            layoutMap.put(draggedItemIndex, targetItem);
        }
    }

    public boolean isEmpty()
    {
        return layoutMap.isEmpty();
    }

	// TODO create test.
	public void duplicateItem(int clickedItemIndex, int itemIdAtIndex)
    {
		int duplicatedItemIndex = getFirstEmptyIndex(clickedItemIndex);

		IndexData itemToDuplicate = layoutMap.get(clickedItemIndex);
		if (itemIdAtIndex != -1 && itemIdAtIndex != itemToDuplicate.itemId) { // dragging a real item.
			int layoutItemId = itemToDuplicate.itemId;
			itemToDuplicate = new IndexData(itemIdAtIndex, itemToDuplicate.withdrawMode);
			// Modifying a layout should always use the real item there, NOT the item id stored in the layout (which can
			// be different due to how variant items are assigned indexes).
			// Therefore, the duplicates must be updated to use that id as well.
			for (Integer index : getIndexesForItem(layoutItemId)) {
				layoutMap.put(index, itemToDuplicate);
			}
		}

        layoutMap.put(duplicatedItemIndex, itemToDuplicate);
    }
}
