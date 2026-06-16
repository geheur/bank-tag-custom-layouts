package com.banktaglayouts.combo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A combo gear group: a named, ordered list of member base item ids plus display/ranking settings.
 * Persisted as JSON by {@link ComboStore}. The {@link #name} is the canonical id, bracketed
 * (e.g. {@code [Melee Helm]}); the member list order IS the priority order.
 */
public class ComboGroup
{
	public static final int DEFAULT_COLOR = 0xFFFF00;

	public String name = "";
	public List<Integer> members = new ArrayList<>(); // base item ids, highest priority first (manual order)
	public int color = DEFAULT_COLOR;                   // RGB
	public String category = "";                         // optional category/group name this combo belongs to
	// base id -> that member's variant ids in priority order. The TOP entry is the variant this combo "serves"
	// (shown in the panel/ghost/icon); during resolution the highest-priority OWNED variant wins (lets you
	// prefer a specific recolor/version when you hold several). Empty = default order (functional item first).
	public Map<Integer, List<Integer>> variantOrder = new HashMap<>();
	public int icon = 0;                                 // favorite display item id (0 = use the top member)

	public ComboGroup()
	{
	}

	public ComboGroup(String name)
	{
		this.name = name;
	}

	/**
	 * The item id to display as this combo's icon (side-panel row + the bank "nothing owned" ghost): the
	 * favorite {@link #icon} if set and still belongs to a member, otherwise the top member's chosen variant,
	 * or {@code -1} when the combo is empty.
	 */
	public int displayIconId()
	{
		if (icon != 0 && members.contains(ItemIndex.statBaseOfCanon(icon)))
		{
			return icon;
		}
		if (members.isEmpty())
		{
			return -1;
		}
		return servedVariant(members.get(0));
	}

	/**
	 * The variant of {@code base} this combo "serves" (the version shown in the panel, group icon, and ghost):
	 * the top of the member's stored priority order if it has one, else the first of its default variant options
	 * — which lists the functional item ahead of a variation-mapped "(damaged)" base. Static item lookups only,
	 * so it is safe off the client thread.
	 */
	public int servedVariant(int base)
	{
		List<Integer> order = variantOrder == null ? null : variantOrder.get(base);
		if (order != null && !order.isEmpty())
		{
			return order.get(0);
		}
		List<Integer> opts = ItemIndex.variants(base); // equippable variants in default order
		return opts.isEmpty() ? base : opts.get(0);
	}

	/** The member base that owns the favorite icon, or {@code -1} if there's no (valid) favorite. */
	public int favoriteOwner()
	{
		if (icon == 0)
		{
			return -1;
		}
		int owner = ItemIndex.statBaseOfCanon(icon);
		return members.contains(owner) ? owner : -1;
	}

	/** Ensures collections/fields are non-null after Gson deserialization. */
	public void normalize()
	{
		if (name == null)
		{
			name = "";
		}
		if (members == null)
		{
			members = new ArrayList<>();
		}
		if (variantOrder == null)
		{
			variantOrder = new HashMap<>();
		}
		if (category == null)
		{
			category = "";
		}
	}

	/** Wraps a user-entered name in brackets and strips characters that would break our CSV/JSON keys. */
	public static String bracket(String raw)
	{
		String s = raw == null ? "" : raw.replaceAll("[\\[\\]:,]", "").trim();
		return "[" + s + "]";
	}
}
