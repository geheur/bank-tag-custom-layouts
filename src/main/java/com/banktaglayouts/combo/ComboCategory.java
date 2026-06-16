package com.banktaglayouts.combo;

/**
 * A collapsible category in the side panel that groups combo tags (e.g. "Melee"). Combos reference it
 * by name via {@link ComboGroup#category}. Persisted as JSON by {@link ComboStore}.
 */
public class ComboCategory
{
	public String name = "";
	public boolean collapsed = false;
	public int color = ComboGroup.DEFAULT_COLOR; // group color; new combos created into this group inherit it

	public ComboCategory()
	{
	}

	public ComboCategory(String name)
	{
		this.name = name;
	}
}
