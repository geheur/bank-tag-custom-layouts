package com.banktaglayouts.combo;

import com.banktaglayouts.BankTagLayoutsPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

/**
 * Resolves a combo group's "winner": from the group's ordered member list ({@link ComboGroup#members},
 * highest priority first), the highest-priority item the player currently has in the bank. If none are
 * in the bank, the top-priority member is returned as a "goal" (renders as a ghost), using the panel's
 * chosen display variant.
 */
@Slf4j
@RequiredArgsConstructor
public class ComboResolver
{
	private final BankTagLayoutsPlugin plugin;

	// A base id -> actual bank item id snapshot, rebuilt lazily and reused until the bank changes. resolveWinner
	// runs per cell across every host tab on each bank refresh (plus reconcile/sync/render the same tick), so
	// without this each call would rescan the whole bank container. Invalidated from onItemContainerChanged(BANK).
	// Client-thread only (every reader canonicalizes, which asserts the client thread).
	private Map<Integer, Integer> bankByBaseCache;
	// canonicalize(id) -> actual bank item id, VARIANT-level (distinguishes recolors / charge states that share a
	// base). Drives per-member variant priority. Built and invalidated alongside bankByBaseCache.
	private Map<Integer, Integer> bankByVariantCache;

	/** Drops the cached bank snapshots. Call when the BANK container changes so the next resolve rescans. */
	public void invalidateBankCache()
	{
		bankByBaseCache = null;
		bankByVariantCache = null;
	}

	/**
	 * The item id a combo "smart cell" should display: the highest-priority member present IN THE BANK
	 * (returned as the real bank item id so it renders solid), else the top-priority member as a ghost
	 * goal. Returns {@code -1} if the group is unknown or has no members.
	 */
	public int resolveWinner(String name)
	{
		// READ-ONLY: only reads members / variantOrder / displayIconId, so the shared cached snapshot is safe.
		ComboGroup group = ComboStore.cachedGet(plugin.configManager, plugin.gson, name);
		if (group == null || group.members.isEmpty())
		{
			return -1;
		}

		Map<Integer, Integer> bankByBase = bankItemsByBase();
		Map<Integer, Integer> bankByVariant = bankItemsByVariant();
		for (Integer base : group.members)
		{
			// If the user gave this member a variant priority order, the highest-priority OWNED variant wins
			// (variant-level match, so a specific recolor/version is preferred when several are held).
			List<Integer> order = group.variantOrder == null ? null : group.variantOrder.get(base);
			if (order != null)
			{
				for (Integer variant : order)
				{
					Integer actual = bankByVariant.get(plugin.itemManager.canonicalize(variant));
					if (actual != null)
					{
						return actual;
					}
				}
			}
			// Default / fallback: any owned variant of this member counts. Canonicalize the member too, so a
			// member stored as a stat-duplicate still matches the bank (which is keyed by the canonical base).
			Integer actual = bankByBase.get(ItemIndex.canonicalBase(base));
			if (actual != null)
			{
				return actual;
			}
		}
		// None in the bank → ghost: the combo's favorite icon (or the top member's chosen variant).
		// Guard against a corrupt/imported variantOrder mapping a base to 0/negative: displayIconId() can
		// then be non-positive, which callers' (winner > 0) checks would mishandle (a negative id could be
		// pinned). Collapse any non-positive result to the documented -1 "unknown/no members" sentinel.
		int ghost = group.displayIconId();
		return ghost > 0 ? ghost : -1;
	}

	/** Whether a real (non-placeholder, qty&gt;0) copy of the given item is currently in the bank. */
	public boolean isOwnedReal(int itemId)
	{
		Map<Integer, Integer> byBase = bankItemsByBase(); // also ensures the stat-duplicate merge map is built
		int base = ItemIndex.comboBaseOf(plugin.itemManager, itemId); // shared base definition (see ItemIndex.comboBaseOf)
		return byBase.containsKey(base);
	}

	/**
	 * A group's member base ids in priority order (the manual list order), canonicalized to their
	 * stat-duplicate roots so callers comparing tab/bank item bases line up. Used for membership/scrub
	 * comparison sets — NOT for variant display (which keys off the raw stored base).
	 */
	public List<Integer> orderedMemberBases(String name)
	{
		// READ-ONLY: only reads group.members (copied into a fresh list below), so the cached snapshot is safe.
		ComboGroup group = ComboStore.cachedGet(plugin.configManager, plugin.gson, name);
		if (group == null)
		{
			return new ArrayList<>();
		}
		List<Integer> bases = new ArrayList<>(group.members.size());
		for (int base : group.members)
		{
			bases.add(ItemIndex.canonicalBase(base));
		}
		return bases;
	}

	/** Maps base id → an actual item id currently in the bank (excludes empty slots and placeholders). */
	private Map<Integer, Integer> bankItemsByBase()
	{
		ensureBankSnapshot();
		return bankByBaseCache != null ? bankByBaseCache : new HashMap<>();
	}

	/** Maps {@code canonicalize(id)} → an actual bank item id, distinguishing variants that share a base. */
	private Map<Integer, Integer> bankItemsByVariant()
	{
		ensureBankSnapshot();
		return bankByVariantCache != null ? bankByVariantCache : new HashMap<>();
	}

	/** Builds both bank snapshots (base-level and variant-level) once, until the next {@link #invalidateBankCache}. */
	private void ensureBankSnapshot()
	{
		if (bankByBaseCache != null)
		{
			return;
		}
		// Ensure the stat-duplicate merge map exists so cosmetic recolors (e.g. Sanguine Torva) key to the
		// same base as their member; no-op once built. Safe here — the resolver is client-thread only.
		ItemIndex.build(plugin.client, plugin.itemManager);
		ItemContainer bank = plugin.client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			// The bank just isn't loaded yet (no ItemContainerChanged to invalidate on) → don't cache; retry later.
			return;
		}
		Map<Integer, Integer> byBase = new HashMap<>();
		Map<Integer, Integer> byVariant = new HashMap<>();
		for (Item item : bank.getItems())
		{
			int id = item.getId();
			// A placeholder (count 0) means you don't actually own it → fall through to the next member.
			if (id < 0 || item.getQuantity() <= 0 || plugin.isPlaceholder(id))
			{
				continue;
			}
			int canon = plugin.itemManager.canonicalize(id);
			byVariant.putIfAbsent(canon, id); // variant-level key stays raw-canonical (distinguishes recolors/charges)
			// Base-level key uses the shared definition so ownership lines up with the plugin's scrub. These items
			// are already non-placeholder (filtered above), so this equals statBaseOfCanon(canon) here.
			byBase.putIfAbsent(ItemIndex.comboBaseOf(plugin.itemManager, id), id);
		}
		bankByVariantCache = byVariant;
		bankByBaseCache = byBase;
	}
}
