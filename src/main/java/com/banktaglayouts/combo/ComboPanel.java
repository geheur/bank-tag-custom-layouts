package com.banktaglayouts.combo;

import com.banktaglayouts.BankTagLayoutsPlugin;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side-panel interface for combo gear groups. Groups are stored as JSON by {@link ComboStore}; this
 * panel is the editor. List view: create (name + rank stat) and manage groups. Detail view: ordered
 * member list (drag to reorder, variant pick, remove), item search to add, color, and tab-placement buttons.
 */
@Slf4j
public class ComboPanel extends PluginPanel
{
	private static final int MAX_SEARCH_RESULTS = 12;

	// Item-search slot filter: labels shown in the dropdown, paired with the ItemIndex filter value.
	private static final String[] SLOT_FILTER_LABELS = {
		"Equippable", "Any item", "Head", "Cape", "Amulet", "Weapon",
		"Body", "Shield", "Legs", "Gloves", "Boots", "Ring", "Ammo"
	};
	private static final int[] SLOT_FILTER_VALUES = {
		ItemIndex.SLOT_EQUIPPABLE, ItemIndex.SLOT_ANY,
		EquipmentInventorySlot.HEAD.getSlotIdx(),
		EquipmentInventorySlot.CAPE.getSlotIdx(),
		EquipmentInventorySlot.AMULET.getSlotIdx(),
		EquipmentInventorySlot.WEAPON.getSlotIdx(),
		EquipmentInventorySlot.BODY.getSlotIdx(),
		EquipmentInventorySlot.SHIELD.getSlotIdx(),
		EquipmentInventorySlot.LEGS.getSlotIdx(),
		EquipmentInventorySlot.GLOVES.getSlotIdx(),
		EquipmentInventorySlot.BOOTS.getSlotIdx(),
		EquipmentInventorySlot.RING.getSlotIdx(),
		EquipmentInventorySlot.AMMO.getSlotIdx()
	};

	private final BankTagLayoutsPlugin plugin;

	// List-view create-form widgets (persist across rebuilds).
	private final JTextField nameField = new JTextField();
	private final JTextField categoryField = new JTextField();
	private String createCategory = ""; // remembered "create in group" selection ("" = none)
	private JComboBox<String> createGroupBox; // live create-form group dropdown; read authoritatively on create

	// Detail-view state.
	private String selectedGroup = null; // null = list view
	private String searchQuery = "";
	private List<ItemIndex.Entry> searchResults = new ArrayList<>();
	private boolean indexBuilding = false;
	private int searchSlotFilter = ItemIndex.SLOT_EQUIPPABLE; // remembered "Add items" slot filter (default: gear only)
	private Integer expandedBase = null; // member base id whose variant list is expanded, or null

	// Persistent across rebuilds so typed-but-uncommitted text survives a non-search rebuild (drag reorder,
	// etc.). Re-parented into a fresh bar panel each rebuild; listeners are wired ONCE here to avoid stacking.
	private final JTextField searchField = new JTextField();

	public ComboPanel(BankTagLayoutsPlugin plugin)
	{
		this.plugin = plugin;
		searchField.addActionListener(e -> doSearch());
		rebuild();
	}

	/** Re-renders the whole panel for the current state (list view, or a group's detail view). */
	public void rebuild()
	{
		removeAll();
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		try
		{
			if (selectedGroup == null)
			{
				buildListView(content);
			}
			else
			{
				buildDetailView(content, group(selectedGroup));
			}
		}
		catch (Throwable ex)
		{
			log.warn("Combo panel failed to render", ex);
			content.removeAll();
			content.add(hint("Panel error: " + ex));
		}

		for (Component c : content.getComponents())
		{
			if (c instanceof javax.swing.JComponent)
			{
				((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
			}
		}

		add(content, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	private ComboGroup group(String name)
	{
		return ComboStore.get(plugin.configManager, plugin.gson, name);
	}

	private void save(ComboGroup g)
	{
		ComboStore.upsert(plugin.configManager, plugin.gson, g);
	}

	// ---------------------------------------------------------------- list view

	private void buildListView(JPanel content)
	{
		content.add(title("Combo Tags"));
		content.add(Box.createVerticalStrut(8));
		content.add(buildCreateForm());
		content.add(Box.createVerticalStrut(8));
		JSeparator divider = new JSeparator();
		divider.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		content.add(divider);
		content.add(Box.createVerticalStrut(8));
		content.add(buildCreateCategoryForm());
		content.add(Box.createVerticalStrut(10));

		List<ComboGroup> all = ComboStore.all(plugin.configManager, plugin.gson);
		if (all.isEmpty())
		{
			content.add(hint("No combo groups yet."));
			return;
		}

		// Categories (collapsible) with their member combos nested underneath, in saved order.
		for (ComboCategory cat : ComboStore.orderedCategories(plugin.configManager, plugin.gson))
		{
			content.add(buildCategoryHeader(cat));
			content.add(Box.createVerticalStrut(2));
			if (!cat.collapsed)
			{
				for (ComboGroup g : all)
				{
					if (cat.name.equals(g.category))
					{
						content.add(buildGroupRow(g.name, true));
						content.add(Box.createVerticalStrut(4));
					}
				}
			}
		}

		// Ungrouped combos, under their own collapsible header (no color/replace/delete — it isn't a real group).
		boolean anyUngrouped = all.stream().anyMatch(g -> g.category == null || g.category.isEmpty());
		if (anyUngrouped)
		{
			boolean collapsed = ComboStore.isUngroupedCollapsed(plugin.configManager);
			content.add(buildUngroupedHeader(collapsed));
			content.add(Box.createVerticalStrut(2));
			if (!collapsed)
			{
				for (ComboGroup g : all)
				{
					if (g.category == null || g.category.isEmpty())
					{
						content.add(buildGroupRow(g.name, true));
						content.add(Box.createVerticalStrut(4));
					}
				}
			}
		}
	}

	/** Header for the Ungrouped section: just a collapse toggle and label — no color/replace/delete actions. */
	private JPanel buildUngroupedHeader(boolean collapsed)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBorder(rowBorder());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JButton toggle = new JButton(collapsed ? "▸" : "▾");
		toggle.setToolTipText(collapsed ? "Expand" : "Collapse");
		toggle.addActionListener(e -> {
			ComboStore.setUngroupedCollapsed(plugin.configManager, !collapsed);
			rebuild();
		});
		row.add(toggle, BorderLayout.WEST);

		JLabel nameLbl = new JLabel("<html><b>Ungrouped</b></html>");
		nameLbl.setForeground(ColorScheme.BRAND_ORANGE);
		row.add(nameLbl, BorderLayout.CENTER);
		return row;
	}

	private JPanel buildCreateCategoryForm()
	{
		JPanel form = new JPanel(new BorderLayout(4, 0));
		form.add(categoryField, BorderLayout.CENTER);
		JButton add = new JButton("New group");
		add.setToolTipText("Create a collapsible category to organize combos");
		add.addActionListener(e -> {
			String n = categoryField.getText().trim();
			if (n.isEmpty() || ComboStore.category(plugin.configManager, plugin.gson, n) != null)
			{
				return;
			}
			ComboStore.upsertCategory(plugin.configManager, plugin.gson, new ComboCategory(n));
			categoryField.setText("");
			rebuild();
		});
		form.add(add, BorderLayout.EAST);
		return form;
	}

	private JPanel buildCategoryHeader(ComboCategory cat)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBorder(rowBorder());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JButton toggle = new JButton(cat.collapsed ? "▸" : "▾");
		toggle.setToolTipText(cat.collapsed ? "Expand" : "Collapse");
		toggle.addActionListener(e -> {
			cat.collapsed = !cat.collapsed;
			ComboStore.upsertCategory(plugin.configManager, plugin.gson, cat);
			rebuild();
		});
		row.add(toggle, BorderLayout.WEST);

		JLabel nameLbl = new JLabel("<html><b>" + htmlEscape(cat.name) + "</b></html>");
		nameLbl.setForeground(ColorScheme.BRAND_ORANGE);
		row.add(nameLbl, BorderLayout.CENTER);

		// Right-click the header to reorder groups (order is saved separately, so it only changes here).
		JPopupMenu menu = new JPopupMenu();
		JMenuItem up = new JMenuItem("Move up");
		up.addActionListener(e -> {
			ComboStore.moveCategory(plugin.configManager, plugin.gson, cat.name, -1);
			rebuild();
		});
		JMenuItem down = new JMenuItem("Move down");
		down.addActionListener(e -> {
			ComboStore.moveCategory(plugin.configManager, plugin.gson, cat.name, +1);
			rebuild();
		});
		JMenuItem delete = new JMenuItem("Delete");
		delete.addActionListener(e -> {
			if (confirmDelete("group \"" + cat.name + "\" (its combos become ungrouped)"))
			{
				ComboStore.deleteCategory(plugin.configManager, plugin.gson, cat.name);
				rebuild();
			}
		});
		menu.add(up);
		menu.add(down);
		menu.addSeparator();
		menu.add(delete);
		row.setComponentPopupMenu(menu);
		nameLbl.setComponentPopupMenu(menu);
		toggle.setComponentPopupMenu(menu);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton color = new JButton("🎨");
		color.setToolTipText("Set the color for every combo in this group");
		color.addActionListener(e -> pickCategoryColor(cat, color));
		actions.add(color);

		JButton replaceAll = new JButton("⇄");
		replaceAll.setForeground(new Color(cat.color)); // tint to reflect this group's color
		replaceAll.setToolTipText("Replace every combo in this group into the open bank tab");
		replaceAll.addActionListener(e -> plugin.replaceCategoryInOpenTab(cat.name));
		actions.add(replaceAll);

		row.add(actions, BorderLayout.EAST);
		return row;
	}

	private void pickCategoryColor(ComboCategory cat, Component anchor)
	{
		net.runelite.client.ui.components.colorpicker.RuneliteColorPicker picker =
			plugin.colorPickerManager.create(
				javax.swing.SwingUtilities.getWindowAncestor(this), new Color(cat.color), cat.name + " color", false);
		placePickerNear(picker, anchor);
		picker.setOnClose(chosen -> {
			// Remember the group color (new combos inherit it) and apply it to every current member.
			cat.color = chosen.getRGB();
			ComboStore.upsertCategory(plugin.configManager, plugin.gson, cat);
			// Recolor every member in one parse + one save (not an upsert-per-member, which re-parsed and
			// rewrote the whole combos JSON for each).
			List<ComboGroup> all = ComboStore.all(plugin.configManager, plugin.gson);
			boolean changed = false;
			for (ComboGroup g : all)
			{
				if (cat.name.equals(g.category))
				{
					g.color = chosen.getRGB();
					changed = true;
				}
			}
			if (changed)
			{
				ComboStore.save(plugin.configManager, plugin.gson, all);
			}
			rebuild();
		});
		picker.setVisible(true);
	}

	private JPanel buildCreateForm()
	{
		JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
		form.setBorder(boxBorder());

		// Row 1: "Name" label + a name box that fills the rest of the row.
		form.add(labelled("Name", nameField));

		// File the new combo into an existing group from the start (remembers the selection across creates).
		List<String> options = new ArrayList<>();
		options.add("(none)");
		for (ComboCategory c : ComboStore.categories(plugin.configManager, plugin.gson))
		{
			options.add(c.name);
		}
		JComboBox<String> groupBox = new JComboBox<>(options.toArray(new String[0]));
		groupBox.setSelectedItem(options.contains(createCategory) ? createCategory : "(none)");
		groupBox.addActionListener(e -> {
			String sel = (String) groupBox.getSelectedItem();
			createCategory = "(none)".equals(sel) ? "" : sel;
		});
		createGroupBox = groupBox; // onCreate reads the live selection from here (not just the listener-set field)

		// Row 2: group dropdown | Create button (two equal halves).
		JButton create = new JButton("Create");
		create.addActionListener(e -> onCreate());
		JPanel actionRow = new JPanel(new GridLayout(1, 2, 6, 0));
		actionRow.add(groupBox);
		actionRow.add(create);
		form.add(actionRow);
		return form;
	}

	private void onCreate()
	{
		String raw = nameField.getText().trim();
		if (raw.isEmpty())
		{
			return;
		}
		String name = ComboGroup.bracket(raw);
		if (group(name) != null)
		{
			nameField.setText(""); // clear the entry so the name isn't left behind on return
			openGroup(name); // already exists — just open it
			return;
		}
		// Read the dropdown's live selection (authoritative) rather than trusting the listener-set field,
		// which can be stale if the selection event hasn't been delivered before this click.
		if (createGroupBox != null)
		{
			String sel = (String) createGroupBox.getSelectedItem();
			createCategory = sel == null || "(none)".equals(sel) ? "" : sel;
		}
		ComboGroup g = new ComboGroup(name);
		if (!createCategory.isEmpty())
		{
			ComboCategory cat = ComboStore.category(plugin.configManager, plugin.gson, createCategory);
			if (cat != null)
			{
				g.category = cat.name;
				g.color = cat.color; // inherit the group's color on creation
			}
		}
		save(g);
		nameField.setText("");
		openGroup(name);
		// Re-apply the group's color on the next EDT cycle. Creating, saving, and rebuilding the detail view
		// in one event can leave the new combo showing the default color; deferring re-reads the saved group
		// and its category and forces the inherited color through. No-op once the color already matches.
		SwingUtilities.invokeLater(() -> inheritCategoryColor(name));
	}

	/** Forces a group's color to match its category's color (no-op if ungrouped or already matching). */
	private void inheritCategoryColor(String groupName)
	{
		ComboGroup g = group(groupName);
		if (g == null || g.category == null || g.category.isEmpty())
		{
			return;
		}
		ComboCategory cat = ComboStore.category(plugin.configManager, plugin.gson, g.category);
		if (cat == null || g.color == cat.color)
		{
			return;
		}
		g.color = cat.color;
		save(g);
		if (groupName.equals(selectedGroup))
		{
			rebuild();
		}
	}

	private JPanel buildGroupRow(String name, boolean indented)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(indented
			? BorderFactory.createCompoundBorder(new EmptyBorder(0, 12, 0, 0), rowBorder())
			: rowBorder());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

		ComboGroup g = group(name);
		int count = g != null ? g.members.size() : 0;
		int color = g != null ? g.color : ComboGroup.DEFAULT_COLOR;

		// The combo's icon (its favorite, or the top member by default), flush to the left.
		if (g != null && !g.members.isEmpty())
		{
			row.add(iconLabel(g.displayIconId()), BorderLayout.WEST);
		}

		// Center: plain name (no brackets — they waste horizontal space) plus the item count.
		JButton open = new JButton("<html><b>" + htmlEscape(stripBrackets(name)) + "</b><br>"
			+ count + (count == 1 ? " item" : " items") + "</html>");
		open.setHorizontalAlignment(SwingConstants.LEFT);
		open.setToolTipText("Manage items in this group");
		open.addActionListener(e -> openGroup(name));
		row.add(open, BorderLayout.CENTER);

		// Right-click to reorder within this section (order is the stored combos order) or delete (confirmed).
		JPopupMenu menu = new JPopupMenu();
		JMenuItem up = new JMenuItem("Move up");
		up.addActionListener(e -> {
			ComboStore.moveCombo(plugin.configManager, plugin.gson, name, -1);
			rebuild();
		});
		JMenuItem down = new JMenuItem("Move down");
		down.addActionListener(e -> {
			ComboStore.moveCombo(plugin.configManager, plugin.gson, name, +1);
			rebuild();
		});
		JMenuItem delete = new JMenuItem("Delete");
		delete.addActionListener(e -> {
			if (confirmDelete("combo \"" + stripBrackets(name) + "\""))
			{
				ComboStore.delete(plugin.configManager, plugin.gson, name);
				rebuild();
			}
		});
		menu.add(up);
		menu.add(down);
		menu.addSeparator();
		menu.add(delete);
		row.setComponentPopupMenu(menu);
		open.setComponentPopupMenu(menu);

		// "Replace in tab" button (when enabled in config), flush to the right, tinted with the group color.
		if (plugin.config.comboReplaceOnList())
		{
			JButton replace = new JButton("⇄");
			replace.setForeground(new Color(color));
			replace.setToolTipText("Replace this combo in the currently-open bank tab");
			replace.addActionListener(e -> plugin.replaceComboInOpenTab(name));
			row.add(replace, BorderLayout.EAST);
		}
		return row;
	}

	/** Opens a combo's editor in the panel (e.g. from the bank ghost's "Open Combo Tag"). EDT only. */
	public void openCombo(String name)
	{
		if (group(name) != null)
		{
			openGroup(name);
		}
	}

	private void openGroup(String name)
	{
		selectedGroup = name;
		searchQuery = "";
		searchField.setText(""); // keep the visible field in sync with the reset search state
		searchResults = new ArrayList<>();
		expandedBase = null;
		searchSlotFilter = detectSlotFilter(group(name)); // default the search to this combo's slot
		rebuild();
		if (!ItemIndex.isReady())
		{
			// Slot detection needs the item index; if it isn't built yet, build it off-thread then refine the
			// default (and re-render) — but only if we're still on this same combo.
			plugin.clientThread.invokeLater(() -> {
				ItemIndex.build(plugin.client, plugin.itemManager);
				SwingUtilities.invokeLater(() -> {
					if (name.equals(selectedGroup))
					{
						searchSlotFilter = detectSlotFilter(group(name));
						rebuild();
					}
				});
			});
		}
	}

	/**
	 * The slot the "Add items" search should default to for a combo: the shared equipment slot of its members
	 * (so a head-slot combo opens filtered to Head). Falls back to {@link ItemIndex#SLOT_EQUIPPABLE} for an
	 * empty combo, a mixed-slot combo, or before the index is built. Member-slot lookups are O(1).
	 */
	private int detectSlotFilter(ComboGroup g)
	{
		if (g == null || g.members.isEmpty() || !ItemIndex.isReady())
		{
			return ItemIndex.SLOT_EQUIPPABLE;
		}
		int slot = ItemIndex.NOT_EQUIPPABLE;
		for (int base : g.members)
		{
			int s = ItemIndex.slotOf(ItemIndex.canonicalBase(base));
			if (s == ItemIndex.NOT_EQUIPPABLE)
			{
				continue; // unknown/non-equippable member doesn't constrain the slot
			}
			if (slot == ItemIndex.NOT_EQUIPPABLE)
			{
				slot = s;
			}
			else if (slot != s)
			{
				return ItemIndex.SLOT_EQUIPPABLE; // members span multiple slots → don't narrow
			}
		}
		return slot == ItemIndex.NOT_EQUIPPABLE ? ItemIndex.SLOT_EQUIPPABLE : slot;
	}

	// -------------------------------------------------------------- detail view

	private void buildDetailView(JPanel content, ComboGroup g)
	{
		if (g == null)
		{
			// Group was deleted out from under us; fall back to the list.
			selectedGroup = null;
			buildListView(content);
			return;
		}
		String name = g.name;

		JButton back = new JButton("← Back");
		back.addActionListener(e -> {
			selectedGroup = null;
			rebuild();
		});
		content.add(back); // delete is via right-click (with confirmation) on the combo's row in the list
		content.add(Box.createVerticalStrut(8));

		content.add(title(stripBrackets(name)));
		content.add(Box.createVerticalStrut(4));

		// Group selector (a dropdown button) and the color picker share one line, 50/50, so they line up with
		// the Add/Replace buttons below.
		JComboBox<String> groupSelector = buildCategorySelector(g);
		JButton colorSwatch = buildColorSwatch(new Color(g.color), c -> {
			ComboGroup cur = group(name);
			if (cur != null)
			{
				cur.color = c.getRGB();
				save(cur);
			}
		});
		JPanel groupColorRow = new JPanel(new GridLayout(1, 2, 4, 0));
		groupColorRow.setOpaque(false);
		groupColorRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			Math.max(groupSelector.getPreferredSize().height, colorSwatch.getPreferredSize().height)));
		groupColorRow.add(groupSelector);
		groupColorRow.add(colorSwatch);
		content.add(groupColorRow);
		content.add(Box.createVerticalStrut(6));

		// Add / Replace share one line (short labels so they fit side by side).
		JButton addToTab = new JButton("Add to Tab");
		addToTab.setToolTipText("Embeds this combo as a smart cell in the bank tag tab you currently have open");
		addToTab.addActionListener(e -> plugin.addComboToOpenTab(name));

		// Always available in the editor, regardless of whether the list also shows the ⇄ button.
		JButton replaceInTab = new JButton("Replace in Tab");
		replaceInTab.setToolTipText("<html>Removes this combo's member items from the open tag tab and"
			+ "<br>puts the combo cell where the top-left-most one was.</html>");
		replaceInTab.addActionListener(e -> plugin.replaceComboInOpenTab(name));

		JPanel tabActions = new JPanel(new GridLayout(1, 2, 4, 0));
		tabActions.setOpaque(false);
		tabActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, addToTab.getPreferredSize().height));
		tabActions.add(addToTab);
		tabActions.add(replaceInTab);
		content.add(tabActions);
		content.add(Box.createVerticalStrut(10));

		content.add(sectionLabel("Members"));
		if (g.members.isEmpty())
		{
			content.add(hint("No items yet — search below to add."));
		}
		else
		{
			for (int i = 0; i < g.members.size(); i++)
			{
				int baseId = g.members.get(i);
				content.add(buildMemberRow(g, baseId));
				content.add(Box.createVerticalStrut(4));

				if (expandedBase != null && expandedBase == baseId)
				{
					int selected = selectedVariant(g, baseId);
					for (Integer variantId : orderedVariants(g, baseId))
					{
						content.add(buildVariantRow(name, baseId, variantId, variantId == selected));
						content.add(Box.createVerticalStrut(2));
					}
				}
			}
		}

		content.add(Box.createVerticalStrut(6));
		content.add(sectionLabel("Add items"));
		content.add(labelled("Slot", buildSlotFilter()));
		content.add(Box.createVerticalStrut(4));
		content.add(buildSearchBar());
		if (indexBuilding)
		{
			content.add(Box.createVerticalStrut(4));
			content.add(hint("Building item index (one-time)…"));
		}
		for (ItemIndex.Entry result : searchResults)
		{
			content.add(Box.createVerticalStrut(4));
			content.add(buildResultRow(name, result));
		}
	}

	/** Dropdown to file this combo under a category (or none). */
	private JComboBox<String> buildCategorySelector(ComboGroup g)
	{
		List<String> options = new ArrayList<>();
		options.add("(none)");
		for (ComboCategory c : ComboStore.categories(plugin.configManager, plugin.gson))
		{
			options.add(c.name);
		}
		JComboBox<String> box = new JComboBox<>(options.toArray(new String[0]));
		box.setSelectedItem(g.category == null || g.category.isEmpty() ? "(none)" : g.category);
		box.addActionListener(e -> {
			String sel = (String) box.getSelectedItem();
			ComboGroup cur = group(g.name);
			if (cur != null)
			{
				cur.category = "(none)".equals(sel) ? "" : sel;
				// Filing a combo into a group adopts that group's color (same semantics as creation and
				// the group color picker, which force-recolor every member). "(none)" leaves color as-is.
				if (!cur.category.isEmpty())
				{
					ComboCategory cat = ComboStore.category(plugin.configManager, plugin.gson, cur.category);
					if (cat != null)
					{
						cur.color = cat.color;
					}
				}
				save(cur);
				rebuild(); // refresh the detail view's color swatch to the inherited color
			}
		});
		return box;
	}

	/** Dropdown that restricts the item search to an equipment slot (or any equippable / any item). */
	private JComboBox<String> buildSlotFilter()
	{
		JComboBox<String> box = new JComboBox<>(SLOT_FILTER_LABELS);
		box.setToolTipText("Restrict the item search to an equipment slot");
		for (int i = 0; i < SLOT_FILTER_VALUES.length; i++)
		{
			if (SLOT_FILTER_VALUES[i] == searchSlotFilter)
			{
				box.setSelectedIndex(i); // set before wiring the listener so it doesn't fire on build
				break;
			}
		}
		box.addActionListener(e -> {
			int idx = box.getSelectedIndex();
			if (idx >= 0)
			{
				searchSlotFilter = SLOT_FILTER_VALUES[idx];
				doSearch(); // re-run the current query under the new slot
			}
		});
		return box;
	}

	private JPanel buildSearchBar()
	{
		// Re-parent the persistent field (adding it to a new container removes it from the old one), so its
		// typed text and caret survive this rebuild even when the rebuild was triggered by a non-search action.
		// Listeners are attached once at construction — do NOT re-add them here.
		JPanel bar = new JPanel(new BorderLayout(4, 0));
		JButton search = new JButton("Search");
		search.addActionListener(e -> doSearch());

		bar.add(searchField, BorderLayout.CENTER);
		bar.add(search, BorderLayout.EAST);
		return bar;
	}

	/** Runs the item search from the live persistent-field text. Wired to Enter (once) and the Search button. */
	private void doSearch()
	{
		searchQuery = searchField.getText().trim();
		if (searchQuery.isEmpty())
		{
			searchResults = new ArrayList<>();
			rebuild();
			return;
		}
		if (ItemIndex.isReady())
		{
			searchResults = ItemIndex.search(searchQuery, MAX_SEARCH_RESULTS, searchSlotFilter);
			rebuild();
		}
		else
		{
			// First search builds the full item index on the client thread (getItemComposition).
			indexBuilding = true;
			searchResults = new ArrayList<>();
			rebuild();
			int slotFilter = searchSlotFilter;
			plugin.clientThread.invokeLater(() -> {
				ItemIndex.build(plugin.client, plugin.itemManager);
				List<ItemIndex.Entry> results = ItemIndex.search(searchQuery, MAX_SEARCH_RESULTS, slotFilter);
				SwingUtilities.invokeLater(() -> {
					indexBuilding = false;
					searchResults = results;
					rebuild();
				});
			});
		}
	}

	private JPanel buildMemberRow(ComboGroup g, int baseId)
	{
		int displayId = selectedVariant(g, baseId);
		int variantCount = variantOptions(baseId).size();

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(rowBorder());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		row.setToolTipText("Drag to reorder (higher in the list = higher priority)");
		row.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		row.putClientProperty(MEMBER_BASE_ID, baseId);
		row.putClientProperty(DEFAULT_BORDER, rowBorder());
		attachReorderDrag(row, this::isMemberRow, gap -> applyReorder(g.name, baseId, gap));

		row.add(iconLabel(displayId), BorderLayout.WEST);
		row.add(nameLabel(displayId), BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// One control per row: a member WITH variants shows only the dropdown (the favorite is chosen per-variant
		// in the expanded rows); a singular member shows only its favorite star. Either way it sits flush-right.
		if (variantCount > 1)
		{
			boolean expanded = expandedBase != null && expandedBase == baseId;
			JButton toggle = new JButton(expanded ? "▾" : "▸");
			toggle.setToolTipText("Show " + variantCount + " variants");
			toggle.addActionListener(e -> {
				expandedBase = expanded ? null : baseId;
				rebuild();
			});
			buttons.add(toggle);
		}
		else
		{
			boolean memberIsFavorite = g.favoriteOwner() == baseId;
			JButton fav = new JButton(memberIsFavorite ? "★" : "☆");
			fav.setToolTipText(memberIsFavorite ? "This is the combo icon (click to unset)" : "Use as the combo icon");
			fav.addActionListener(e -> toggleFavorite(g.name, displayId));
			buttons.add(fav);
		}

		row.add(buttons, BorderLayout.EAST);

		// Remove is now a right-click action on the row (the old inline ✕ button is gone). The row's drag
		// handler only arms on the left button, so right-click is free for the popup.
		JPopupMenu menu = new JPopupMenu();
		JMenuItem remove = new JMenuItem("Remove from group");
		remove.addActionListener(e -> {
			ComboGroup cur = group(g.name);
			if (cur != null)
			{
				cur.members.remove((Integer) baseId);
				cur.variantOrder.remove(baseId);
				if (cur.icon != 0 && cur.favoriteOwner() == -1)
				{
					cur.icon = 0; // favorite's member is gone → back to the default icon
				}
				save(cur);
				rebuild();
			}
		});
		menu.add(remove);
		row.setComponentPopupMenu(menu);
		return row;
	}

	/** An indented row under an expanded member: one variant, draggable to set its resolution priority. */
	private JPanel buildVariantRow(String name, int baseId, int variantId, boolean selected)
	{
		javax.swing.border.Border border = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR), new EmptyBorder(3, 20, 3, 6));

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(border);
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		row.setToolTipText("Drag to reorder (higher = preferred when you own several of this item)");
		row.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		row.putClientProperty(VARIANT_OWNER, baseId);
		row.putClientProperty(DEFAULT_BORDER, border);
		attachReorderDrag(row, c -> isVariantRowOf(c, baseId), gap -> applyVariantReorder(name, baseId, variantId, gap));

		row.add(iconLabel(variantId), BorderLayout.WEST);

		JLabel nameLbl = nameLabel(variantId);
		if (selected)
		{
			nameLbl.setForeground(ColorScheme.BRAND_ORANGE);
		}
		row.add(nameLbl, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Favorite only (the old "use" button is gone — the served version is whichever variant is dragged to the
		// top of the order). Flush-right so it lines up with the member row's favorite.
		ComboGroup gFav = group(name);
		boolean isFavorite = gFav != null && gFav.icon == variantId;
		JButton fav = new JButton(isFavorite ? "★" : "☆");
		fav.setToolTipText(isFavorite ? "This is the combo icon (click to unset)" : "Use this version as the combo icon");
		fav.addActionListener(e -> toggleFavorite(name, variantId));
		buttons.add(fav);

		row.add(buttons, BorderLayout.EAST);
		return row;
	}

	private JPanel buildResultRow(String name, ItemIndex.Entry result)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(rowBorder());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		row.add(iconLabel(result.displayId), BorderLayout.WEST);
		row.add(new JLabel(result.name), BorderLayout.CENTER);

		JButton add = new JButton("+");
		add.setToolTipText("Add to group");
		add.addActionListener(e -> {
			ComboGroup cur = group(name);
			if (cur == null)
			{
				return;
			}
			// A recolor collapses onto its canonical base (so all variants are one member). Adding a specific
			// version serves it by putting it on top of that member's variant order; adding the bare base clears
			// any ordering so the default (functional item) is served.
			int canonical = ItemIndex.canonicalBase(result.id);
			if (!cur.members.contains(canonical))
			{
				cur.members.add(canonical);
			}
			if (result.id == canonical)
			{
				cur.variantOrder.remove(canonical);
			}
			else
			{
				List<Integer> order = new ArrayList<>(orderedVariants(cur, canonical));
				order.remove((Integer) result.id);
				order.add(0, result.id);
				cur.variantOrder.put(canonical, order);
			}
			save(cur);
			rebuild();
		});
		row.add(add, BorderLayout.EAST);
		return row;
	}

	// Client-property keys: mark a member row (value = base id), mark a variant row (value = owner base id),
	// and cache each row's normal border so the drag insertion indicator can restore it.
	private static final String MEMBER_BASE_ID = "comboMemberBaseId";
	private static final String VARIANT_OWNER = "comboVariantOwner";
	private static final String DEFAULT_BORDER = "comboDefaultBorder";

	private boolean isMemberRow(Component c)
	{
		return c instanceof JComponent && ((JComponent) c).getClientProperty(MEMBER_BASE_ID) != null;
	}

	private boolean isVariantRowOf(Component c, int owner)
	{
		return c instanceof JComponent
			&& Objects.equals(((JComponent) c).getClientProperty(VARIANT_OWNER), owner);
	}

	/**
	 * Makes a row draggable to reorder it among its siblings (rows matching {@code isSibling}); the new order is
	 * committed via {@code onDrop} on release. While dragging, an orange line marks where the row will land. Drives
	 * both member reorder (priority) and variant reorder. Replaces the old ▲▼ priority buttons.
	 */
	private void attachReorderDrag(JPanel row, Predicate<Component> isSibling, IntConsumer onDrop)
	{
		MouseAdapter ma = new MouseAdapter()
		{
			private boolean armed = false;
			private boolean moved = false;

			@Override
			public void mousePressed(MouseEvent e)
			{
				armed = SwingUtilities.isLeftMouseButton(e);
				moved = false;
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (!armed)
				{
					return;
				}
				moved = true;
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				showInsertionIndicator(row, isSibling, targetGap(row, isSibling, e));
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (!armed)
				{
					return;
				}
				armed = false;
				if (!moved)
				{
					return; // a plain click on the row body, not a drag
				}
				onDrop.accept(targetGap(row, isSibling, e)); // onDrop's rebuild() clears all drag visuals
			}
		};
		row.addMouseListener(ma);
		row.addMouseMotionListener(ma);
	}

	/** The insertion index (0..n) among the OTHER sibling rows for the cursor's current vertical position. */
	private int targetGap(JComponent draggedRow, Predicate<Component> isSibling, MouseEvent e)
	{
		Container parent = draggedRow.getParent();
		if (parent == null)
		{
			return 0;
		}
		Point p = SwingUtilities.convertPoint(draggedRow, e.getPoint(), parent);
		int gap = 0;
		for (Component c : parent.getComponents())
		{
			if (c == draggedRow || !isSibling.test(c))
			{
				continue;
			}
			if (p.y > c.getY() + c.getHeight() / 2)
			{
				gap++;
			}
		}
		return gap;
	}

	/** Draws an orange line at the drop position: top of the gap row, or bottom of the last row for end-insert. */
	private void showInsertionIndicator(JComponent draggedRow, Predicate<Component> isSibling, int gap)
	{
		Container parent = draggedRow.getParent();
		if (parent == null)
		{
			return;
		}
		List<JComponent> rows = new ArrayList<>();
		for (Component c : parent.getComponents())
		{
			if (c != draggedRow && isSibling.test(c))
			{
				rows.add((JComponent) c);
			}
		}
		for (JComponent r : rows)
		{
			r.setBorder(defaultBorder(r)); // reset the indicator from the previous drag position
		}
		if (rows.isEmpty())
		{
			return;
		}
		JComponent target = rows.get(Math.min(gap, rows.size() - 1));
		int top = gap < rows.size() ? 2 : 0;
		int bottom = gap < rows.size() ? 0 : 2;
		target.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(top, 0, bottom, 0, ColorScheme.BRAND_ORANGE), defaultBorder(target)));
		parent.repaint();
	}

	/** A row's normal (non-indicator) border, cached as a client property when the row is built. */
	private javax.swing.border.Border defaultBorder(JComponent row)
	{
		Object b = row.getClientProperty(DEFAULT_BORDER);
		return b instanceof javax.swing.border.Border ? (javax.swing.border.Border) b : rowBorder();
	}

	/** Moves the dragged member to the drop gap, persists the new priority order, and re-renders. */
	private void applyReorder(String name, int baseId, int gap)
	{
		ComboGroup cur = group(name);
		if (cur == null)
		{
			rebuild();
			return;
		}
		int from = cur.members.indexOf(baseId);
		if (from < 0)
		{
			rebuild();
			return;
		}
		Integer member = cur.members.remove(from);
		int to = Math.max(0, Math.min(gap, cur.members.size()));
		cur.members.add(to, member);
		if (to != from)
		{
			save(cur);
		}
		rebuild();
	}

	/** A member's variants in display order: the stored priority order (valid options first), else the default. */
	private List<Integer> orderedVariants(ComboGroup g, int baseId)
	{
		List<Integer> all = variantOptions(baseId);
		List<Integer> stored = g.variantOrder == null ? null : g.variantOrder.get(baseId);
		if (stored == null || stored.isEmpty())
		{
			return all;
		}
		List<Integer> result = new ArrayList<>(all.size());
		for (Integer v : stored)
		{
			if (all.contains(v))
			{
				result.add(v); // stored order, dropping any variants the game no longer reports
			}
		}
		for (Integer v : all)
		{
			if (!result.contains(v))
			{
				result.add(v); // append any newly-available variants not yet in the stored order
			}
		}
		return result;
	}

	/** Moves the dragged variant to the drop gap and persists this member's variant priority order. */
	private void applyVariantReorder(String name, int baseId, int variantId, int gap)
	{
		ComboGroup cur = group(name);
		if (cur == null)
		{
			rebuild();
			return;
		}
		List<Integer> order = new ArrayList<>(orderedVariants(cur, baseId));
		int from = order.indexOf(variantId);
		if (from < 0)
		{
			rebuild();
			return;
		}
		Integer v = order.remove(from);
		int to = Math.max(0, Math.min(gap, order.size()));
		order.add(to, v);
		cur.variantOrder.put(baseId, order);
		if (to != from)
		{
			save(cur);
		}
		rebuild();
	}

	/** The variant currently served (shown/highlighted) for a member: the top of its priority order. */
	private int selectedVariant(ComboGroup g, int baseId)
	{
		return g.servedVariant(baseId);
	}

	/** Modal yes/no confirmation before a destructive delete; returns true only if the user confirmed. */
	private boolean confirmDelete(String what)
	{
		int choice = JOptionPane.showConfirmDialog(this, "Delete " + what + "?", "Confirm delete",
			JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		return choice == JOptionPane.YES_OPTION;
	}

	/** Sets (or, if already favorited, clears) the combo's favorite display icon to a specific item id. */
	private void toggleFavorite(String name, int itemId)
	{
		ComboGroup cur = group(name);
		if (cur == null)
		{
			return;
		}
		cur.icon = cur.icon == itemId ? 0 : itemId;
		save(cur);
		rebuild();
	}

	/**
	 * Selectable display variants for a member: RuneLite's native variation group PLUS the stat-duplicate
	 * recolors the search folded into this base (e.g. Sanguine Torva under Torva full helm), restricted to
	 * equippable versions (so a DT2 ring's non-equippable old base model is hidden). The folded ids only appear
	 * once the item index has been built (i.e. after a search).
	 */
	private List<Integer> variantOptions(int baseId)
	{
		return ItemIndex.variants(baseId);
	}

	// --------------------------------------------------------------- icon/name + small widgets

	/** Item icon label (icon loads async; safe from the EDT). */
	private JLabel iconLabel(int itemId)
	{
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		plugin.itemManager.getImage(itemId).addTo(icon);
		return icon;
	}

	/** Item name label; resolved on the client thread (getItemComposition asserts it under -ea), then set on the EDT. */
	private JLabel nameLabel(int itemId)
	{
		JLabel label = new JLabel("…");
		plugin.clientThread.invokeLater(() -> {
			String resolved = plugin.itemManager.getItemComposition(itemId).getName();
			SwingUtilities.invokeLater(() -> label.setText(resolved));
		});
		return label;
	}

	private static String htmlEscape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Display form of a combo name with the surrounding [ ] stripped (the brackets are storage-only). */
	private static String stripBrackets(String name)
	{
		return (name.length() >= 2 && name.startsWith("[") && name.endsWith("]"))
			? name.substring(1, name.length() - 1) : name;
	}

	private JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		return label;
	}

	private JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(6, 0, 2, 0));
		return label;
	}

	private JLabel hint(String text)
	{
		JLabel label = new JLabel(text);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(8, 0, 0, 0));
		return label;
	}

	/** A color "Pick" button tinted with the current color; opens the picker (anchored to the button) on click. */
	private JButton buildColorSwatch(Color current, Consumer<Color> onPick)
	{
		JButton swatch = new JButton("Pick");
		swatch.setBackground(current);
		swatch.setOpaque(true);
		swatch.setToolTipText("Choose the color for this combo");
		swatch.addActionListener(e -> {
			net.runelite.client.ui.components.colorpicker.RuneliteColorPicker picker =
				plugin.colorPickerManager.create(
					javax.swing.SwingUtilities.getWindowAncestor(this), current, "Combo color", false);
			placePickerNear(picker, swatch);
			picker.setOnClose(chosen -> {
				onPick.accept(chosen);
				rebuild();
			});
			picker.setVisible(true);
		});
		return swatch;
	}

	/**
	 * Anchors the color picker's TOP-RIGHT corner to the clicked button (the color buttons sit on the panel's
	 * right edge), so it opens beside the button instead of off the client's top-left. Clamped to the screen.
	 */
	private void placePickerNear(net.runelite.client.ui.components.colorpicker.RuneliteColorPicker picker, Component anchor)
	{
		try
		{
			java.awt.Point loc = anchor.getLocationOnScreen();
			Dimension size = picker.getSize();
			int x = Math.max(0, loc.x + anchor.getWidth() - size.width);
			int y = Math.max(0, loc.y);
			picker.setLocation(x, y);
		}
		catch (java.awt.IllegalComponentStateException ignored)
		{
			// anchor not currently on screen — leave the picker's default location
		}
	}

	private JPanel labelled(String text, Component field)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		JLabel label = new JLabel(text);
		label.setPreferredSize(new Dimension(60, label.getPreferredSize().height));
		row.add(label, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		return row;
	}

	private javax.swing.border.Border boxBorder()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(8, 8, 8, 8));
	}

	private javax.swing.border.Border rowBorder()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR), new EmptyBorder(4, 6, 4, 6));
	}
}
