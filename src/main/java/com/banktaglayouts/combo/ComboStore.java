package com.banktaglayouts.combo;

import com.banktaglayouts.BankTagLayoutsPlugin;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Loads/saves combo groups as a single JSON array under {@code banktaglayouts.combos}. One source of
 * truth for a group's members, order, rank stat, color, and variant picks.
 */
@Slf4j
public final class ComboStore
{
	public static final String CONFIG_KEY = "combos";
	public static final String CATEGORIES_KEY = "combo_categories";
	public static final String CATEGORY_ORDER_KEY = "combo_category_order";
	public static final String UNGROUPED_COLLAPSED_KEY = "combo_ungrouped_collapsed";
	private static final Type LIST_TYPE = new TypeToken<List<ComboGroup>>()
	{
	}.getType();
	private static final Type CAT_LIST_TYPE = new TypeToken<List<ComboCategory>>()
	{
	}.getType();
	private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>()
	{
	}.getType();

	// Parse cache for the hot READ-ONLY path (resolver: per cell, per bank build). Keyed on the EXACT JSON
	// config string; any save writes a new string, so the key mismatches and we transparently re-parse — no
	// external invalidation needed. Published together under a synchronized block. The cached list/objects are
	// SHARED and treated as read-only by callers; mutating callers must keep using all() (a fresh parse).
	private static volatile String cachedJson;
	private static volatile List<ComboGroup> cachedList;

	private ComboStore()
	{
	}

	public static List<ComboGroup> all(ConfigManager configManager, Gson gson)
	{
		String json = configManager.getConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, CONFIG_KEY);
		if (json == null || json.isEmpty())
		{
			return new ArrayList<>();
		}
		try
		{
			List<ComboGroup> list = gson.fromJson(json, LIST_TYPE);
			if (list == null)
			{
				return new ArrayList<>();
			}
			list.forEach(ComboGroup::normalize);
			return list;
		}
		catch (Exception ex)
		{
			log.warn("failed to parse combo groups json", ex);
			return new ArrayList<>();
		}
	}

	public static ComboGroup get(ConfigManager configManager, Gson gson, String name)
	{
		for (ComboGroup g : all(configManager, gson))
		{
			if (g.name.equals(name))
			{
				return g;
			}
		}
		return null;
	}

	/**
	 * A SHARED, READ-ONLY snapshot of all groups, reusing a cached parse while the JSON config string is
	 * unchanged (auto-invalidated on any save, which writes a new string). For the hot resolver path that
	 * re-reads the same config many times per bank build. Never mutate the returned list or its groups —
	 * mutation paths (upsert/moveCombo/delete/deleteCategory) must use {@link #all} for a fresh parse.
	 */
	public static synchronized List<ComboGroup> cachedAll(ConfigManager configManager, Gson gson)
	{
		String json = configManager.getConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, CONFIG_KEY);
		// Match all()'s no-op handling: null/empty json → empty list. Cache it too so empty configs don't re-key.
		String key = json == null ? "" : json;
		if (cachedList != null && key.equals(cachedJson))
		{
			return cachedList;
		}
		List<ComboGroup> parsed = all(configManager, gson); // reuse all()'s parse + normalize + failure handling
		// Publish key and list together (this method is synchronized; fields are volatile for off-thread readers).
		cachedJson = key;
		cachedList = parsed;
		return parsed;
	}

	/**
	 * READ-ONLY lookup of a single group by name from {@link #cachedAll}. The returned group is shared and
	 * must not be mutated; use {@link #get} + {@link #upsert} for edits.
	 */
	public static ComboGroup cachedGet(ConfigManager configManager, Gson gson, String name)
	{
		for (ComboGroup g : cachedAll(configManager, gson))
		{
			if (g.name.equals(name))
			{
				return g;
			}
		}
		return null;
	}

	public static List<String> names(ConfigManager configManager, Gson gson)
	{
		List<String> names = new ArrayList<>();
		for (ComboGroup g : all(configManager, gson))
		{
			names.add(g.name);
		}
		return names;
	}

	public static void save(ConfigManager configManager, Gson gson, List<ComboGroup> groups)
	{
		configManager.setConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, CONFIG_KEY, gson.toJson(groups, LIST_TYPE));
	}

	/** Inserts or replaces a group (matched by name), preserving its position so editing never reorders it. */
	public static void upsert(ConfigManager configManager, Gson gson, ComboGroup group)
	{
		List<ComboGroup> list = all(configManager, gson);
		int idx = -1;
		for (int i = 0; i < list.size(); i++)
		{
			if (list.get(i).name.equals(group.name))
			{
				idx = i;
				break;
			}
		}
		if (idx >= 0)
		{
			list.set(idx, group);
		}
		else
		{
			list.add(group);
		}
		save(configManager, gson, list);
	}

	/**
	 * Moves a combo up (delta -1) or down (delta +1) relative to the other combos in its OWN section
	 * (same category, or the ungrouped section). Other sections are unaffected.
	 */
	public static void moveCombo(ConfigManager configManager, Gson gson, String name, int delta)
	{
		List<ComboGroup> list = all(configManager, gson);
		int i = -1;
		for (int k = 0; k < list.size(); k++)
		{
			if (list.get(k).name.equals(name))
			{
				i = k;
				break;
			}
		}
		if (i < 0)
		{
			return;
		}
		String cat = norm(list.get(i).category);
		int step = delta < 0 ? -1 : 1;
		int j = -1;
		for (int k = i + step; k >= 0 && k < list.size(); k += step)
		{
			if (norm(list.get(k).category).equals(cat))
			{
				j = k; // nearest sibling in that direction
				break;
			}
		}
		if (j < 0)
		{
			return;
		}
		Collections.swap(list, i, j);
		save(configManager, gson, list);
	}

	private static String norm(String s)
	{
		return s == null ? "" : s;
	}

	public static void delete(ConfigManager configManager, Gson gson, String name)
	{
		List<ComboGroup> list = all(configManager, gson);
		if (list.removeIf(g -> g.name.equals(name)))
		{
			save(configManager, gson, list);
		}
	}

	// ---- categories ----

	public static List<ComboCategory> categories(ConfigManager configManager, Gson gson)
	{
		String json = configManager.getConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, CATEGORIES_KEY);
		if (json == null || json.isEmpty())
		{
			return new ArrayList<>();
		}
		try
		{
			List<ComboCategory> list = gson.fromJson(json, CAT_LIST_TYPE);
			return list != null ? list : new ArrayList<>();
		}
		catch (Exception ex)
		{
			log.warn("failed to parse combo categories json", ex);
			return new ArrayList<>();
		}
	}

	public static ComboCategory category(ConfigManager configManager, Gson gson, String name)
	{
		for (ComboCategory c : categories(configManager, gson))
		{
			if (c.name.equals(name))
			{
				return c;
			}
		}
		return null;
	}

	public static void saveCategories(ConfigManager configManager, Gson gson, List<ComboCategory> categories)
	{
		configManager.setConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, CATEGORIES_KEY, gson.toJson(categories, CAT_LIST_TYPE));
	}

	public static void upsertCategory(ConfigManager configManager, Gson gson, ComboCategory category)
	{
		List<ComboCategory> list = categories(configManager, gson);
		// Replace in place so editing (e.g. collapse toggle) never reshuffles the stored list.
		int idx = -1;
		for (int i = 0; i < list.size(); i++)
		{
			if (list.get(i).name.equals(category.name))
			{
				idx = i;
				break;
			}
		}
		if (idx >= 0)
		{
			list.set(idx, category);
		}
		else
		{
			list.add(category);
		}
		saveCategories(configManager, gson, list);
	}

	// ---- category display order (stored separately so editing a category never moves it) ----

	public static List<String> categoryOrder(ConfigManager configManager, Gson gson)
	{
		String json = configManager.getConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, CATEGORY_ORDER_KEY);
		if (json == null || json.isEmpty())
		{
			return new ArrayList<>();
		}
		try
		{
			List<String> list = gson.fromJson(json, STRING_LIST_TYPE);
			return list != null ? list : new ArrayList<>();
		}
		catch (Exception ex)
		{
			log.warn("failed to parse combo category order json", ex);
			return new ArrayList<>();
		}
	}

	public static void saveCategoryOrder(ConfigManager configManager, Gson gson, List<String> order)
	{
		configManager.setConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, CATEGORY_ORDER_KEY, gson.toJson(order, STRING_LIST_TYPE));
	}

	/**
	 * Categories sorted by the saved order; appends any newly-seen names and drops stale ones.
	 * Pure read: reconciles the order IN-MEMORY only and never persists (no ConfigManager write),
	 * so it is safe to call during an EDT panel rebuild without triggering a ConfigChanged feedback loop.
	 * The saved order is repaired by the explicit reorder paths instead.
	 */
	public static List<ComboCategory> orderedCategories(ConfigManager configManager, Gson gson)
	{
		List<ComboCategory> cats = categories(configManager, gson);
		List<String> order = categoryOrder(configManager, gson);
		for (ComboCategory c : cats)
		{
			if (!order.contains(c.name))
			{
				order.add(c.name);
			}
		}
		order.removeIf(n -> cats.stream().noneMatch(c -> c.name.equals(n)));
		cats.sort(Comparator.comparingInt(c -> order.indexOf(c.name)));
		return cats;
	}

	/** Moves a category up (delta -1) or down (delta +1) in the saved order. */
	public static void moveCategory(ConfigManager configManager, Gson gson, String name, int delta)
	{
		List<String> order = categoryOrder(configManager, gson);
		// Make sure every existing category is represented before swapping.
		for (ComboCategory c : categories(configManager, gson))
		{
			if (!order.contains(c.name))
			{
				order.add(c.name);
			}
		}
		int i = order.indexOf(name);
		int j = i + delta;
		if (i < 0 || j < 0 || j >= order.size())
		{
			return;
		}
		Collections.swap(order, i, j);
		saveCategoryOrder(configManager, gson, order);
	}

	// ---- ungrouped section (a pseudo-category: collapsible, but no color/order/delete) ----

	public static boolean isUngroupedCollapsed(ConfigManager configManager)
	{
		return "true".equals(configManager.getConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, UNGROUPED_COLLAPSED_KEY));
	}

	public static void setUngroupedCollapsed(ConfigManager configManager, boolean collapsed)
	{
		configManager.setConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, UNGROUPED_COLLAPSED_KEY, Boolean.toString(collapsed));
	}

	public static void deleteCategory(ConfigManager configManager, Gson gson, String name)
	{
		List<ComboCategory> list = categories(configManager, gson);
		if (list.removeIf(c -> c.name.equals(name)))
		{
			saveCategories(configManager, gson, list);
		}
		// Un-file any combos that were in this category.
		List<ComboGroup> combos = all(configManager, gson);
		boolean changed = false;
		for (ComboGroup g : combos)
		{
			if (name.equals(g.category))
			{
				g.category = "";
				changed = true;
			}
		}
		if (changed)
		{
			save(configManager, gson, combos);
		}
	}
}
