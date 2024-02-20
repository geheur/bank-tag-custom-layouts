package com.banktaglayouts;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;
import static net.runelite.api.ItemID.*;

@Value
public class PrioritySlot
{
	String name;
	int serialId;
	int[] itemIds;
	boolean showPlaceholders;
	boolean useInAutoLayout;
	boolean useInAutoLayoutOnlyForDowngrades;

	public static final List<PrioritySlot> DEFAULT_PRIORITY_SLOTS = new ArrayList<>();
	static {
		/* jewellery false true false */
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("glory (lowest charge)", 0, new int[]{AMULET_OF_GLORY1, AMULET_OF_GLORY2, AMULET_OF_GLORY3, AMULET_OF_GLORY4, AMULET_OF_GLORY5, AMULET_OF_GLORY6, AMULET_OF_GLORY}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("duel ring (lowest charge)", 1, new int[]{RING_OF_DUELING1, RING_OF_DUELING2, RING_OF_DUELING3, RING_OF_DUELING4, RING_OF_DUELING5, RING_OF_DUELING6, RING_OF_DUELING7, RING_OF_DUELING8}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("games neck (lowest charge)", 2, new int[]{GAMES_NECKLACE1, GAMES_NECKLACE2, GAMES_NECKLACE3, GAMES_NECKLACE4, GAMES_NECKLACE5, GAMES_NECKLACE6, GAMES_NECKLACE7, GAMES_NECKLACE8}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("digiste pendant (lowest charge)", 3, new int[]{DIGSITE_PENDANT_1, DIGSITE_PENDANT_2, DIGSITE_PENDANT_3, DIGSITE_PENDANT_4, DIGSITE_PENDANT_5}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("burning amulet (lowest charge)", 4, new int[]{BURNING_AMULET1, BURNING_AMULET2, BURNING_AMULET3, BURNING_AMULET4, BURNING_AMULET5}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("combat bracelet (lowest charge)", 5, new int[]{COMBAT_BRACELET1, COMBAT_BRACELET2, COMBAT_BRACELET3, COMBAT_BRACELET4, COMBAT_BRACELET5, COMBAT_BRACELET6}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("skills necklace (lowest charge)", 6, new int[]{SKILLS_NECKLACE1, SKILLS_NECKLACE2, SKILLS_NECKLACE3, SKILLS_NECKLACE4, SKILLS_NECKLACE5, SKILLS_NECKLACE6}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("row (lowest charge)", 7, new int[]{RING_OF_WEALTH_1, RING_OF_WEALTH_2, RING_OF_WEALTH_3, RING_OF_WEALTH_4, RING_OF_WEALTH_5}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("necklace of passage (lowest charge)", 8, new int[]{NECKLACE_OF_PASSAGE1, NECKLACE_OF_PASSAGE2, NECKLACE_OF_PASSAGE3, NECKLACE_OF_PASSAGE4, NECKLACE_OF_PASSAGE5}, false, true, false));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("slayer ring (lowest charge)", 9, new int[]{SLAYER_RING_1, SLAYER_RING_2, SLAYER_RING_3, SLAYER_RING_4, SLAYER_RING_5, SLAYER_RING_6, SLAYER_RING_7, SLAYER_RING_8}, false, true, false));
		/* cosmetics true true true */
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{TWISTED_ANCESTRAL_HAT, ANCESTRAL_HAT}, true, true, true));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{TWISTED_ANCESTRAL_ROBE_TOP, ANCESTRAL_ROBE_TOP}, true, true, true));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{TWISTED_ANCESTRAL_ROBE_BOTTOM, ANCESTRAL_ROBE_BOTTOM}, true, true, true));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{SANGUINE_TORVA_FULL_HELM, TORVA_FULL_HELM}, true, true, true));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{SANGUINE_TORVA_PLATEBODY, TORVA_PLATEBODY}, true, true, true));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{SANGUINE_TORVA_PLATELEGS, TORVA_PLATELEGS}, true, true, true));
		// TODO do we need to have adowngrade system that remembers the original item in case someone wanted to use a cheaper version for a setup, e.g. for wilderness?
		/* downgrades true false false */
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{SANGUINE_TORVA_FULL_HELM, TORVA_FULL_HELM, NEITIZNOT_FACEGUARD, HELM_OF_NEITIZNOT}, true, true, true));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{SANGUINE_TORVA_PLATEBODY, TORVA_PLATEBODY, BANDOS_CHESTPLATE, FIGHTER_TORSO}, true, true, true));
		DEFAULT_PRIORITY_SLOTS.add(new PrioritySlot("", 0, new int[]{SANGUINE_TORVA_PLATELEGS, TORVA_PLATELEGS, BANDOS_TASSETS, OBSIDIAN_PLATELEGS}, true, true, true));
	}

	public static PrioritySlot get(int index)
	{
		return DEFAULT_PRIORITY_SLOTS.get(index);
	}

	public int containsItemId(int itemId) {
		for (int i = 0; i < itemIds.length; i++)
		{
			if (itemIds[i] == itemId) {
				return i;
			}
		}
		return -1;
	}
}
