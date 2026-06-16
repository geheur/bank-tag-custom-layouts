package com.banktaglayouts.combo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemVariationMapping;

/**
 * A searchable, variant-collapsed index of every named item — including untradeables that
 * {@link ItemManager#search} (GE-price backed) misses.
 *
 * <p>Each entry is a single BASE item: placeholders/notes are canonicalized and charged/ornamented
 * variants fold to their base via {@link ItemVariationMapping}, so e.g. all serpentine-helm states
 * (and an oathplate + ornament kit) collapse to one entry.
 *
 * <p>On top of that, cosmetic recolors that {@link ItemVariationMapping} misses are folded two ways:
 * (1) by NAME, when a recolor's name ends with an equippable item's full name (e.g. "Sanguine torva
 * full helm" → "Torva full helm"); and (2) for recolor SETS with no bare base item, by grouping items
 * that share a trailing type AND an identical stat block — a 1-word type for ARMOR ("Saradomin coif" /
 * "Armadyl coif" → "coif") but a 2-word type for WEAPONS (so the four godswords, which share "godsword"
 * + identical base stats but differ in special attack, never collapse). Both are guarded by stats so
 * distinct items are left alone — we never merge on raw stats alone, because stat-clones can differ in
 * passive effects the item API doesn't expose (amulet of glory / avarice / the damned). The fold is
 * exposed via {@link #canonicalBase(int)} so combo ownership treats a recolor as its base item.
 *
 * <p>{@link #build} MUST run on the client thread (it uses {@code getItemComposition}/
 * {@code canonicalize}); the result is cached after the first build.
 */
@Slf4j
public final class ItemIndex
{
	/** An entry whose item isn't equippable. */
	public static final int NOT_EQUIPPABLE = -1;
	/** Search filter: match every item (no slot restriction). */
	public static final int SLOT_ANY = -2;
	/** Search filter: match any equippable item, regardless of which slot. */
	public static final int SLOT_EQUIPPABLE = -3;

	/** One base item: its id, display name, the icon to show, and equipment slot ({@link #NOT_EQUIPPABLE} if not gear). */
	public static final class Entry
	{
		public final int id;
		public final String name;
		// Icon to display for this entry: the first EQUIPPABLE variant, so a DT2 ring shows its current model
		// rather than the non-equippable lowest-id base it collapses onto. Equals id for ordinary items.
		public final int displayId;
		public final int equipSlot; // EquipmentInventorySlot.getSlotIdx(), or NOT_EQUIPPABLE

		Entry(int id, String name, int displayId, int equipSlot)
		{
			this.id = id;
			this.name = name;
			this.displayId = displayId;
			this.equipSlot = equipSlot;
		}
	}

	private static volatile List<Entry> entries;
	private static volatile Map<Integer, Integer> mergeToCanonical; // folded base id -> canonical (lowest-id) base id
	private static volatile Map<Integer, List<Integer>> foldedMembers; // canonical base id -> folded recolor ids
	private static volatile Map<Integer, Integer> slotByBase; // equippable base id -> EquipmentInventorySlot.getSlotIdx()
	private static volatile Map<Integer, Integer> statBaseClass; // canon id -> stat-class representative base
	private static volatile Map<Integer, List<Integer>> displayVariants; // base -> its equippable, same-stat variation members

	private ItemIndex()
	{
	}

	public static boolean isReady()
	{
		return entries != null;
	}

	/** Builds the index once (no-op if already built). MUST be called on the client thread. */
	public static void build(Client client, ItemManager itemManager)
	{
		if (entries != null)
		{
			return;
		}

		try
		{
			// Partition every variation group into stat-classes: items with identical equipment stats become one base,
			// so same-stat versions stay one item (a corrupted bow + its charge states, degrade states) while a
			// stat-changing imbue (a DK ring (i)) splits into its own base. Each item maps to its class's representative
			// (lowest id); the cosmetic-recolor fold (canonicalBase) is layered on afterwards.
			Map<Integer, Integer> statBaseMap = new HashMap<>(); // canon id -> its stat-class representative
			Map<Integer, List<Integer>> displayVars = new HashMap<>(); // rep -> the equippable members of its class
			Set<Integer> bases = new HashSet<>();
			Set<Integer> seenGroups = new HashSet<>();
			int count = client.getItemCount();
			for (int id = 0; id < count; id++)
			{
				ItemComposition comp = itemManager.getItemComposition(id);
				if (comp == null || isBlank(comp.getName()))
				{
					continue;
				}
				int canon = itemManager.canonicalize(id);
				int group = ItemVariationMapping.map(canon);
				if (seenGroups.add(group))
				{
					partitionStatClasses(itemManager, group, statBaseMap, displayVars, bases);
				}
				if (!statBaseMap.containsKey(canon)) // safety net: every item gets a base
				{
					statBaseMap.put(canon, canon);
					bases.add(canon);
				}
			}

			List<Entry> list = new ArrayList<>(bases.size());
			Map<Integer, ItemEquipmentStats> eqByBase = new HashMap<>(); // equippable bases -> stats, for the fold guard
			Map<Integer, Integer> slots = new HashMap<>(); // equippable bases -> slot, for member-slot auto-detect
			for (int base : bases)
			{
				ItemComposition comp = itemManager.getItemComposition(base);
				String name = comp == null ? null : comp.getName();
				if (isBlank(name))
				{
					continue;
				}
				ItemStats stats = itemManager.getItemStats(base);
				ItemEquipmentStats eq = stats != null && stats.isEquipable() ? stats.getEquipment() : null;
				list.add(new Entry(base, name, base, eq != null ? eq.getSlot() : NOT_EQUIPPABLE));
				if (eq != null)
				{
					eqByBase.put(base, eq);
					slots.put(base, eq.getSlot());
				}
			}

			// Fold cosmetic recolors into their base item by NAME: a recolor's name ENDS WITH an equippable item's
			// full name (strip leading word(s): "sanguine torva full helm" -> "torva full helm" -> fold into it).
			// We deliberately do NOT merge on raw stats alone: distinct items can share an identical stat block yet
			// differ in passive/secondary effects the item API does not expose (amulet of glory vs avarice vs the
			// damned), so those must stay separate. The shared "Amulet of ..." prefix is harmless here — the stripped
			// remainder ("avarice") must itself be a complete equippable item, which sibling amulets never are.
			// Index each base by its lowercased name AND by that name with a trailing "(...)" qualifier removed, so a
			// recolor can fold onto a base whose canonical (variation-mapped) name carries a state suffix: the Torva
			// full-helm group's base item is named "Torva full helm (damaged)", and "Sanguine torva full helm" must
			// still resolve to it via the bare "torva full helm". The exact name is registered first, so a real bare
			// base (if one exists) wins the bare key.
			Map<String, Entry> byName = new HashMap<>();
			for (Entry e : list)
			{
				String lower = e.name.toLowerCase(Locale.ROOT);
				byName.putIfAbsent(lower, e);
				String bare = stripTrailingQualifier(lower);
				if (!bare.equals(lower))
				{
					byName.putIfAbsent(bare, e);
				}
			}
			Map<Integer, Integer> merge = new HashMap<>();
			for (Entry e : list)
			{
				String lower = e.name.toLowerCase(Locale.ROOT);
				for (int sp = lower.indexOf(' '); sp >= 0; sp = lower.indexOf(' ', sp + 1))
				{
					Entry root = byName.get(lower.substring(sp + 1)); // remainder after stripping leading word(s)
					if (root == null || root.id == e.id || root.equipSlot == NOT_EQUIPPABLE)
					{
						continue;
					}
					// Guard: only fold when stats are compatible — the recolor either has no stats of its own (a
					// pure cosmetic the stats db doesn't know, e.g. Sanguine Torva) or matches the root exactly. A
					// same-name-stem item with DIFFERENT stats is a real, distinct item and is left alone.
					ItemEquipmentStats es = eqByBase.get(e.id);
					if (es == null || es.equals(eqByBase.get(root.id)))
					{
						merge.put(e.id, root.id);
						break;
					}
				}
			}

			// Group same-family recolor SETS that have no bare base item to fold into (e.g. god d'hide boots):
			// equippable items that share a trailing type AND an identical stat block collapse to their lowest-id
			// member. ARMOR collapses on a SINGLE-word type ("Saradomin coif" + "Armadyl coif" -> "coif"); the
			// WEAPON slot requires a TWO-word type, because a 1-word weapon type can't be told apart from
			// special-attack stat-clones (the four godswords all share "godsword" + identical base stats). The
			// identical-stats guard additionally keeps glory/avarice/the damned apart (different last word) and
			// rune vs dragon "full helm" apart (same suffix, different stats).
			final int weaponSlot = EquipmentInventorySlot.WEAPON.getSlotIdx();
			Map<String, Map<ItemEquipmentStats, List<Entry>>> bySuffix = new HashMap<>();
			for (Entry e : list)
			{
				if (merge.containsKey(e.id))
				{
					continue; // already folded into a real base item above
				}
				ItemEquipmentStats eq = eqByBase.get(e.id);
				if (eq == null)
				{
					continue;
				}
				String suffix = e.equipSlot == weaponSlot ? trailingTwoWords(e.name) : trailingWord(e.name);
				if (suffix == null)
				{
					continue;
				}
				bySuffix.computeIfAbsent(suffix, k -> new HashMap<>())
					.computeIfAbsent(eq, k -> new ArrayList<>()).add(e);
			}
			for (Map<ItemEquipmentStats, List<Entry>> byStats : bySuffix.values())
			{
				for (List<Entry> bucket : byStats.values())
				{
					if (bucket.size() < 2)
					{
						continue;
					}
					int canonical = bucket.get(0).id;
					for (Entry e : bucket)
					{
						canonical = Math.min(canonical, e.id); // lowest id = the original (usually the base color)
					}
					for (Entry e : bucket)
					{
						if (e.id != canonical)
						{
							merge.put(e.id, canonical);
						}
					}
				}
			}

			// Resolve chains so every folded id points at its ultimate canonical, then invert for variant display.
			Map<Integer, List<Integer>> folded = new HashMap<>();
			for (Integer k : new ArrayList<>(merge.keySet()))
			{
				int root = merge.get(k);
				Set<Integer> seen = new HashSet<>();
				while (merge.containsKey(root) && seen.add(root))
				{
					root = merge.get(root);
				}
				merge.put(k, root);
				folded.computeIfAbsent(root, x -> new ArrayList<>()).add(k);
			}
			// NOTE: folded variants stay in the search list (every d'hide colour is individually searchable);
			// the collapse happens at ADD time, where the picked variant is filed under its canonical base.
			list.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
			mergeToCanonical = merge; // publish before entries so canonicalBase is ready once isReady() is true
			foldedMembers = folded;
			slotByBase = slots;
			statBaseClass = statBaseMap;
			displayVariants = displayVars;
			entries = list; // publish LAST: isReady() flips true only once every other map is set
		}
		catch (Throwable t)
		{
			// Leave entries null so isReady() stays false and a later build() call retries cleanly (the
			// early-return guard at the top no-ops only once entries is published). All fields are local
			// until the publish block above, so a failure here cannot leave a half-built state.
			log.warn("ItemIndex build failed; will retry on next invocation", t);
		}
	}

	/**
	 * Partitions one variation group into stat-classes (members with identical equipment stats), assigning every
	 * member to its class's representative (lowest id). Equippable classes also record their members as the base's
	 * selectable variants. Each representative becomes a base, so same-stat versions stay one item while a
	 * different-stats imbue splits off. Groups are tiny, so the pairwise stats compare needs no hashing.
	 */
	private static void partitionStatClasses(ItemManager itemManager, int group,
		Map<Integer, Integer> statBaseMap, Map<Integer, List<Integer>> displayVars, Set<Integer> bases)
	{
		Collection<Integer> raw = ItemVariationMapping.getVariations(group);
		LinkedHashSet<Integer> members = new LinkedHashSet<>();
		if (raw.isEmpty())
		{
			members.add(group);
		}
		else
		{
			for (int m : raw)
			{
				members.add(itemManager.canonicalize(m));
			}
		}
		List<ItemEquipmentStats> classStats = new ArrayList<>(); // parallel lists; null entry = non-equippable class
		List<Integer> classRep = new ArrayList<>();
		List<List<Integer>> classMembers = new ArrayList<>();
		for (int mc : members)
		{
			ItemStats s = itemManager.getItemStats(mc);
			ItemEquipmentStats e = s != null && s.isEquipable() ? s.getEquipment() : null;
			int idx = -1;
			for (int i = 0; i < classStats.size(); i++)
			{
				ItemEquipmentStats cs = classStats.get(i);
				if (cs == null ? e == null : cs.equals(e))
				{
					idx = i;
					break;
				}
			}
			if (idx < 0)
			{
				classStats.add(e);
				classRep.add(mc);
				classMembers.add(new ArrayList<>());
				idx = classStats.size() - 1;
			}
			else if (mc < classRep.get(idx))
			{
				classRep.set(idx, mc);
			}
			classMembers.get(idx).add(mc);
		}
		for (int i = 0; i < classStats.size(); i++)
		{
			int rep = classRep.get(i);
			bases.add(rep);
			for (int mc : classMembers.get(i))
			{
				statBaseMap.put(mc, rep);
			}
			if (classStats.get(i) != null) // equippable class → its members are the selectable variants
			{
				displayVars.put(rep, new ArrayList<>(classMembers.get(i)));
			}
		}
	}

	/** Equipment slot index ({@code EquipmentInventorySlot.getSlotIdx()}) for a base, or {@link #NOT_EQUIPPABLE}. */
	public static int slotOf(int baseId)
	{
		Map<Integer, Integer> m = slotByBase;
		return m == null ? NOT_EQUIPPABLE : m.getOrDefault(baseId, NOT_EQUIPPABLE);
	}

	/**
	 * The stats-aware combo base of an already-canonicalized item id: items with identical equipment stats in a
	 * variation group share one base, but a stat-changing imbue is its own base — then the cosmetic-recolor fold
	 * is applied. Used for combo membership AND bank ownership so an imbued item resolves to its own member, not
	 * the plain version. Falls back to the plain variation map before the index is built.
	 */
	public static int statBaseOfCanon(int canon)
	{
		Map<Integer, Integer> m = statBaseClass;
		int base = m != null && m.containsKey(canon) ? m.get(canon) : ItemVariationMapping.map(canon);
		return canonicalBase(base);
	}

	/** {@code getPlaceholderTemplateId()} of a bank placeholder (the layout-placeholder model id). */
	private static final int PLACEHOLDER_TEMPLATE_ID = 14401;

	/**
	 * The stats-aware combo base of a RAW item id (a bank item, a layout {@code int[]} slot, or a menu item id):
	 * a placeholder is first resolved to the real item it stands for, then canonicalized and mapped to its
	 * stat-class base + cosmetic-recolor root via {@link #statBaseOfCanon}. This is the SINGLE definition of
	 * "an item's combo base" shared by the layout scrub/membership checks (plugin) and bank-ownership/winner
	 * checks ({@link ComboResolver}), so the two can never disagree on whether an id belongs to a member.
	 * Client thread (uses {@code getItemComposition} / {@code canonicalize}).
	 */
	public static int comboBaseOf(ItemManager itemManager, int itemId)
	{
		ItemComposition comp = itemManager.getItemComposition(itemId);
		int nonPlaceholder = (comp != null && comp.getPlaceholderTemplateId() == PLACEHOLDER_TEMPLATE_ID)
			? comp.getPlaceholderId() : itemId;
		return statBaseOfCanon(itemManager.canonicalize(nonPlaceholder));
	}

	/**
	 * A base's selectable display variants in default order: its equippable, same-stat variation members plus any
	 * folded cosmetic recolors. Different-stats imbues are their own base, so they never appear here; a DT2 ring's
	 * non-equippable base model is hidden too. Falls back to the raw variation set before the index is built.
	 */
	public static List<Integer> variants(int base)
	{
		Map<Integer, List<Integer>> dv = displayVariants;
		if (dv == null)
		{
			LinkedHashSet<Integer> raw = new LinkedHashSet<>(ItemVariationMapping.getVariations(base));
			raw.add(base);
			raw.addAll(mergedVariants(base));
			return new ArrayList<>(raw);
		}
		LinkedHashSet<Integer> ids = new LinkedHashSet<>(dv.getOrDefault(base, Collections.emptyList()));
		ids.addAll(mergedVariants(base));
		if (ids.isEmpty())
		{
			ids.add(base);
		}
		return new ArrayList<>(ids);
	}

	/**
	 * The canonical base for a (already {@link ItemVariationMapping}-mapped) base id: its stat-duplicate
	 * root if it was folded, else the id unchanged. Safe (identity) before the index is built.
	 */
	public static int canonicalBase(int baseId)
	{
		Map<Integer, Integer> m = mergeToCanonical;
		return m == null ? baseId : m.getOrDefault(baseId, baseId);
	}

	/**
	 * Item ids folded into the given canonical base (stat-duplicate / recolor cosmetics) — the extra
	 * selectable display "variants" for that base (each is still individually searchable; the collapse
	 * happens at add time). Empty before the index is built or for an unmerged base.
	 */
	public static List<Integer> mergedVariants(int canonicalBaseId)
	{
		Map<Integer, List<Integer>> m = foldedMembers;
		List<Integer> v = m == null ? null : m.get(canonicalBaseId);
		return v == null ? Collections.emptyList() : v;
	}

	/**
	 * The last two space-separated words of a name, lowercased (e.g. "Armadyl d'hide boots" → "d'hide boots"),
	 * or {@code null} when the name has fewer than two words. The weapon-slot family suffix (stricter, so
	 * special-attack stat-clones don't collapse).
	 */
	private static String trailingTwoWords(String name)
	{
		int last = name.lastIndexOf(' ');
		if (last <= 0)
		{
			return null;
		}
		int prev = name.lastIndexOf(' ', last - 1);
		return name.substring(prev + 1).toLowerCase(Locale.ROOT); // prev == -1 (two-word name) → whole name
	}

	/** The last space-separated word of a name, lowercased (e.g. "Saradomin coif" → "coif"). The armor family suffix. */
	private static String trailingWord(String name)
	{
		return name.substring(name.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT); // no space → whole (one-word) name
	}

	/** A name with a single trailing parenthetical qualifier removed (e.g. "torva full helm (damaged)" → "torva full helm"). */
	private static String stripTrailingQualifier(String lowerName)
	{
		int open = lowerName.lastIndexOf(" (");
		return open > 0 && lowerName.endsWith(")") ? lowerName.substring(0, open) : lowerName;
	}

	/**
	 * Substring search by name (prefix matches first), up to {@code limit} results, restricted to
	 * {@code slotFilter}: a specific {@code EquipmentInventorySlot.getSlotIdx()}, {@link #SLOT_EQUIPPABLE}
	 * (any gear), or {@link #SLOT_ANY} (no restriction).
	 */
	public static List<Entry> search(String query, int limit, int slotFilter)
	{
		List<Entry> all = entries;
		if (all == null || isBlank(query))
		{
			return Collections.emptyList();
		}

		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Entry> prefix = new ArrayList<>();
		List<Entry> contains = new ArrayList<>();
		for (Entry e : all)
		{
			if (!matchesSlot(e, slotFilter))
			{
				continue;
			}
			String lower = e.name.toLowerCase(Locale.ROOT);
			if (lower.startsWith(q))
			{
				prefix.add(e);
			}
			else if (lower.contains(q))
			{
				contains.add(e);
			}
		}
		prefix.addAll(contains);
		return prefix.size() > limit ? new ArrayList<>(prefix.subList(0, limit)) : prefix;
	}

	private static boolean matchesSlot(Entry e, int slotFilter)
	{
		switch (slotFilter)
		{
			case SLOT_ANY:
				return true;
			case SLOT_EQUIPPABLE:
				return e.equipSlot != NOT_EQUIPPABLE;
			default:
				return e.equipSlot == slotFilter;
		}
	}

	private static boolean isBlank(String s)
	{
		return s == null || s.isEmpty() || "null".equalsIgnoreCase(s);
	}
}
