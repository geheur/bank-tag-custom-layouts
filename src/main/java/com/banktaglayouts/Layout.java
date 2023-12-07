package com.banktaglayouts;

import java.util.Set;
import lombok.EqualsAndHashCode;
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
import net.runelite.api.EquipmentInventorySlot;
import static net.runelite.api.EquipmentInventorySlot.*;

@Slf4j
@EqualsAndHashCode
public class Layout {

    // Maps indexes to items.
    private Map<Integer, LayoutSlot> layoutMap = new HashMap<>();

    @RequiredArgsConstructor
    public static final class LayoutSlot {
    	public final int itemId;
    	private final int prioritySlotIndex;
    	private final String autoLayoutSlot;
    	private static final int AL_EQUIPMENT_HEAD = 0 + HEAD.getSlotIdx();
		private static final int AL_EQUIPMENT_CAPE = 0 + CAPE.getSlotIdx();
		private static final int AL_EQUIPMENT_AMULET = 0 + AMULET.getSlotIdx();
		private static final int AL_EQUIPMENT_WEAPON = 0 + WEAPON.getSlotIdx();
		private static final int AL_EQUIPMENT_BODY = 0 + BODY.getSlotIdx();
		private static final int AL_EQUIPMENT_SHIELD = 0 + SHIELD.getSlotIdx();
		private static final int AL_EQUIPMENT_LEGS = 0 + LEGS.getSlotIdx();
		private static final int AL_EQUIPMENT_GLOVES = 0 + GLOVES.getSlotIdx();
		private static final int AL_EQUIPMENT_BOOTS = 0 + BOOTS.getSlotIdx();
		private static final int AL_EQUIPMENT_RING = 0 + RING.getSlotIdx();
		private static final int AL_EQUIPMENT_AMMO = 0 + AMMO.getSlotIdx(); // 13
		private static final int AL_INVENTORY_0 = 20 + 1;
		private static final int AL_ZIGZAG_EQUIPMENT_ROOT = 100;
		private static final int AL_ZIGZAG_EQUIPMENT = 101;
		private static final int AL_ZIGZAG_INVENTORY_ROOT = 102;
		private static final int AL_ZIGZAG_INVENTORY = 103;
		private static final int AL_RUNE_POUCH_1 = 150;
		private static final int AL_RUNE_POUCH_2 = 151;
		private static final int AL_RUNE_POUCH_3 = 152;
		private static final int AL_RUNE_POUCH_4 = 153;
		public boolean isItem() {
    		return itemId != -1;
		}
		public boolean isPrioritySlot() {
			return prioritySlotIndex != -1;
		}
		public PrioritySlot getPrioritySlot() {
    		return PrioritySlot.get(prioritySlotIndex);
		}
		@Override
		public String toString() {
    		if (itemId != -1) {
				return "" + itemId;
			} else {
    			return "p" + prioritySlotIndex;
			}
		}
		public static LayoutSlot fromString(String s) {
			boolean isPrioritySlot = s.charAt(0) == 'p';
			if (isPrioritySlot) {
				s = s.substring(1);
			}
			String autoLayoutSlot = null;
			for (int i = 0; i < s.toCharArray().length; i++)
			{
				char c = s.charAt(i);
				if (!((c >= '0' && c <= '9') || c == '-')) {
					autoLayoutSlot = s.substring(i);
					s = s.substring(0, i);
				}
			}
			if (isPrioritySlot) {
				return new LayoutSlot(-1, Integer.parseInt(s), autoLayoutSlot);
			} else {
				return new LayoutSlot(Integer.parseInt(s), -1, autoLayoutSlot);
			}
		}

		public boolean isSame(LayoutSlot other)
		{
			return this.itemId == other.itemId && this.prioritySlotIndex == other.prioritySlotIndex;
		}
	}

	public static Layout fromString(String layoutString) {
		return fromString(layoutString, false);
    }

    public static Layout fromString(String layoutString, boolean ignoreNfe) {
        Layout layout = Layout.emptyLayout();
        if (layoutString.isEmpty()) return layout;
        for (String s1 : layoutString.split(",")) {
            String[] split = s1.split(":");
            try {
                LayoutSlot layoutSlot = LayoutSlot.fromString(split[0]);
                int index = Integer.parseInt(split[1]);
                if (index >= 0) {
                    layout.putSlot(layoutSlot, index);
                } else {
                    log.debug("Removed item " + layoutSlot + " due to it having a negative index (" + index + ")");
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
        for (Map.Entry<Integer, LayoutSlot> entry : allPairs()) {
            sb.append(entry.getValue() + ":" + entry.getKey() + ",");
        }
        if (sb.length() > 0) {
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb.toString();
    }

	public void putSlot(LayoutSlot layoutSlot, int index)
	{
		layoutMap.put(index, layoutSlot);
	}

	public void putItem(int itemId, int index) {
        if (itemId <= 0) {
            layoutMap.remove(index);
            return;
        }
        layoutMap.put(index, new LayoutSlot(itemId, -1, null));
    }

    /** returns -1 if there is no item there. */
    public LayoutSlot getItemAtIndex(int index) {
        return layoutMap.getOrDefault(index, null);
    }

    public Iterator<Map.Entry<Integer, LayoutSlot>> allPairsIterator() {
        return layoutMap.entrySet().iterator();
    }

    /**
     * Finds the index for the EXACT itemId. Does not factor in placeholders or variation items. For duplicated items,
     * it returns one of the indexes where the itemId can be found.
     * If there's no index for this itemId, then it returns -1.
     */
    public Integer getIndexForSlot(LayoutSlot slot) {
        return allPairs().stream()
                .filter(e -> e.getValue().isSame(slot))
                .map(e -> e.getKey())
                .findAny().orElse(-1);
    }

	/**
	 * Finds the index for the EXACT itemId. Does not factor in placeholders or variation items. For duplicated items,
	 * it returns one of the indexes where the itemId can be found.
	 * If there's no index for this itemId, then it returns -1.
	 */
	public Integer getIndexForSlot(int itemId) {
		return allPairs().stream()
			.filter(e -> e.getValue().isItem() && e.getValue().itemId == itemId)
			.map(e -> e.getKey())
			.findAny().orElse(-1);
	}

	public Collection<LayoutSlot> getUniqueLayoutSlots() {
		return new HashSet<>(layoutMap.values());
    }

    public Collection<Integer> getAllUsedIndexes() {
        return layoutMap.keySet();
    }

    public Collection<Map.Entry<Integer, LayoutSlot>> allPairs() {
        return layoutMap.entrySet();
    }

    public Collection<LayoutSlot> allSlots() {
    	return layoutMap.values();
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
     * @param itemId the dragged item widget's item id.
     */
    public void moveItem(int draggedItemIndex, int targetIndex, int itemId) {
        LayoutSlot draggedSlot = getItemAtIndex(draggedItemIndex);

		// Modifying a layout should use the real item there, NOT the item id stored in the layout (which can be
		// different due to how variant items are assigned indexes), because the item the user sees themselves
		// moving is the item id in the widget, not the item id in the layout. Therefore, the duplicates must be
		// updated to use that id as well.
		if (itemId != -1 && draggedSlot.isItem() && draggedSlot.itemId != itemId) {
			List<Integer> indexesForItem = getIndexesForItem(draggedSlot.itemId);
			draggedSlot = new LayoutSlot(itemId, -1, null);
			for (Integer i : indexesForItem) {
				putItem(itemId, i);
			}
		}

        LayoutSlot targetSlot = getItemAtIndex(targetIndex);

        clearIndex(draggedItemIndex);
        clearIndex(targetIndex);
        putSlot(draggedSlot, targetIndex);
        if (targetSlot != null) {
            putSlot(targetSlot, draggedItemIndex);
        }
    }

    public boolean isEmpty()
    {
        return layoutMap.isEmpty();
    }

    public int countItemsWithId(int itemId)
    {
        int count = 0;
        for (Map.Entry<Integer, LayoutSlot> pair : allPairs())
        {
            if (pair.getValue().itemId == itemId) {
                count++;
            }
        }
        return count;
    }

    // TODO create test.
    public void duplicateItem(int index, int itemId)
    {
        LayoutSlot layoutSlot = getItemAtIndex(index);

		// Modifying a layout should always use the real item there, NOT the item id stored in the layout (which can
		// be different due to how variant items are assigned indexes).
		// Therefore, the duplicates must be updated to use that id as well.
		if (itemId != -1 && layoutSlot.isItem() && layoutSlot.itemId != itemId) {
			List<Integer> indexesForItem = getIndexesForItem(layoutSlot.itemId);
			layoutSlot = new LayoutSlot(itemId, -1, null);
			for (Integer i : indexesForItem) {
				putSlot(layoutSlot, i);
			}
		}

		int newIndex = getFirstEmptyIndex(index);
		putSlot(layoutSlot, newIndex);
    }

	/**
	 * Finds the indexes for the EXACT itemId. Does not factor in placeholders or variation items.
	 * If there're no indexes for this itemId, then it returns an empty list.
	 */
	private List<Integer> getIndexesForItem(int itemId)
	{
		return allPairs().stream()
			.filter(e -> e.getValue().isItem() && e.getValue().itemId == itemId)
			.map(e -> e.getKey())
			.collect(Collectors.toList());
	}
}
