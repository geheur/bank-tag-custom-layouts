package com.banktaglayouts;

import com.banktaglayouts.BankTagLayoutsConfig.WhichPlugin;
import static com.banktaglayouts.BankTagLayoutsConfig.WhichPlugin.BOTH;
import static com.banktaglayouts.BankTagLayoutsConfig.WhichPlugin.CORE;
import static com.banktaglayouts.BankTagLayoutsConfig.WhichPlugin.HUB;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.Point;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.Varbits;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.MenuShouldLeftClick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetDrag;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.AutoLayout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.banktags.tabs.TabInterface;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Bank Tag Layouts",
	description = "Right click a bank tag tabs and click \"Enable layout\", select the tag tab, then drag items in the tag to reposition them.",
	tags = {"bank", "tag", "layout"}
)
@PluginDependency(BankTagsPlugin.class)
public class BankTagLayoutsPlugin extends Plugin implements MouseListener, KeyListener
{
	public static final IntPredicate FILTERED_CHARS = c -> "</>:".indexOf(c) == -1;

	public static final Color itemTooltipColor = new Color(0xFF9040);

	public static final String CONFIG_GROUP = "banktaglayouts";
	public static final String LAYOUT_CONFIG_KEY_PREFIX = "layout_";
	public static final String INVENTORY_SETUPS_LAYOUT_CONFIG_KEY_PREFIX = "inventory_setups_layout_";
	public static final String BANK_TAG_STRING_PREFIX = "banktaglayoutsplugin:";
	public static final String LAYOUT_EXPLICITLY_DISABLED = "DISABLED";

	public static final String ENABLE_LAYOUT = "Enable layout";
	public static final String DISABLE_LAYOUT = "Delete layout";
	public static final String IMPORT_LAYOUT = "Import tag tab with layout";
	public static final String EXPORT_LAYOUT = "Export tag tab with layout";
	public static final String REMOVE_FROM_LAYOUT_MENU_OPTION = "Remove-layout";
	public static final String PREVIEW_AUTO_LAYOUT = "Preview auto layout";
	public static final String DUPLICATE_ITEM = "Duplicate-item";
	public static final String REMOVE_DUPLICATE_ITEM = "Remove-duplicate-item";
	public static final String REPLACE_WITH_COMBO = "Replace with combo";
	public static final String REMOVE_COMBO = "Remove Layout";
	public static final String REPLACE_COMBO_WITH_ITEM = "Set Placeholder";
	public static final String OPEN_COMBO = "Edit";

	public static final int BANK_ITEM_WIDTH = 36;
	public static final int BANK_ITEM_HEIGHT = 32;
	public static final int ROW_HEIGHT = 36;
	public static final int COLUMN_WIDTH = 48;

	@Inject public Client client;
	@Inject public OverlayManager overlayManager;
	@Inject public MouseManager mouseManager;
	@Inject public KeyManager keyManager;
	@Inject public SpriteManager spriteManager;
	@Inject public ItemManager itemManager;
	@Inject public ConfigManager configManager;
	@Inject public ClientThread clientThread;
	@Inject public TabInterface tabInterface;
	@Inject public TagManager tagManager;
	@Inject public BankTagsService bankTagsService;
	@Inject public FakeItemOverlay fakeItemOverlay;
	@Inject public BankSearch bankSearch;
	@Inject public ChatboxPanelManager chatboxPanelManager;
	@Inject public BankTagLayoutsConfig config;
	@Inject public Gson gson;
	@Inject public UsedToBeReflection copyPaste;
	@Inject public LayoutManager layoutManager;
	@Inject public EventBus eventBus;
	@Inject public net.runelite.client.ui.ClientToolbar clientToolbar;
	@Inject public net.runelite.client.ui.components.colorpicker.ColorPickerManager colorPickerManager;

	// The current indexes for where each widget should appear in the custom bank layout. Should be ignored if there is not tab active.
	private final Map<Integer, Widget> indexToWidget = new HashMap<>();

	private Widget showLayoutPreviewButton = null;
	private Widget applyLayoutPreviewButton = null;
	private Widget cancelLayoutPreviewButton = null;

	private LayoutableThing lastLayoutable = null;
	private int lastHeight = Integer.MAX_VALUE;

	final AntiDragPluginUtil antiDrag = new AntiDragPluginUtil(this);
	private final LayoutGenerator layoutGenerator = new LayoutGenerator(this);
	private final com.banktaglayouts.combo.ComboResolver comboResolver = new com.banktaglayouts.combo.ComboResolver(this);

	// CORE-layout combo tabs: one registered BankTag per host tag, supplying live owned-winner membership so
	// RuneLite renders the cells from our resolution (no per-winner tagging, no auto-float). See maintainComboCoreTab.
	// Concurrent: put/get/remove on the client thread (onItemContainerChanged) vs iterate+clear on the EDT
	// (shutDown -> unregisterComboBankTags). A plain HashMap here can CME or corrupt under that interleaving.
	private final Map<String, com.banktaglayouts.combo.ComboBankTag> comboBankTags = new ConcurrentHashMap<>();

	// Cached combo group name -> ARGB color, so the overlay (per box, per frame) and the right-click menu don't
	// re-parse the whole combos JSON every call. Rebuilt lazily; nulled when the "combos" config changes.
	// volatile: built/read on the client thread but invalidated from onConfigChanged (which may be the EDT).
	private volatile Map<String, Integer> comboColorCache;

	// Parallel to comboColorCache: memoized java.awt.Color instances (so the overlay doesn't new up a Color per
	// box per frame). Invalidated wherever comboColorCache is nulled. volatile for the same reason.
	private volatile Map<String, java.awt.Color> comboColorObjCache;

	// Cached per host tag (LayoutableThing.name) cell index -> combo group map returned by getComboCellGroups,
	// so FakeItemOverlay.render doesn't re-read the comboslots config + rebuild a HashMap every paint. The whole
	// cache is cleared whenever combo slots change (every ComboSlots.write in this file, the "combos" config key,
	// and tab rename/delete). Callers only READ the returned map. volatile: read on the client thread (render),
	// cleared from onConfigChanged (possibly the EDT).
	private volatile Map<String, Map<Integer, String>> comboCellGroupsCache = new ConcurrentHashMap<>();

	// Per host tag, the cell index -> winner id map we last pinned into its CORE layout while it was INACTIVE.
	// Lets maintainComboCoreTab skip the loadLayout + saveLayout for an inactive tab whose winners haven't
	// changed since (nothing edits an unopened tab's layout). Dropped while the tab is active; cleared on
	// startup and when a tab's combo data is removed/renamed. Concurrent for the same reason as comboBankTags:
	// get/put/remove on the client thread (maintainComboCoreTab) vs clear on the EDT (startUp).
	private final Map<String, Map<Integer, Integer>> lastCorePinnedWinners = new ConcurrentHashMap<>();

	// Negative priority so the combo panel sorts ABOVE core panels (the Configuration wrench is priority 0).
	private static final int COMBO_PANEL_PRIORITY = -100;
	private com.banktaglayouts.combo.ComboPanel comboPanel;
	private net.runelite.client.ui.NavigationButton comboNavButton;

	private void updateButton() {
		Widget parent = client.getWidget(ComponentID.BANK_CONTENT_CONTAINER);
		if (parent == null) return;

		boolean found = false;
		if (showLayoutPreviewButton != null) {
			for (Widget dynamicChild : parent.getDynamicChildren())
			{
				if (dynamicChild == showLayoutPreviewButton) {
					found = true;
					break;
				}
			}
		}
		if (!found || showLayoutPreviewButton == null) {
			showLayoutPreviewButton = parent.createChild(-1, WidgetType.GRAPHIC);

			showLayoutPreviewButton.setOriginalHeight(18);
			showLayoutPreviewButton.setOriginalWidth(18);
			showLayoutPreviewButton.setYPositionMode(WidgetPositionMode.ABSOLUTE_BOTTOM);
			showLayoutPreviewButton.setOriginalX(434);
			showLayoutPreviewButton.setOriginalY(45);
			showLayoutPreviewButton.setSpriteId(Sprites.AUTO_LAYOUT.getSpriteId());

			showLayoutPreviewButton.setOnOpListener((JavaScriptCallback) (e) -> showLayoutPreview());
			showLayoutPreviewButton.setHasListener(true);
			showLayoutPreviewButton.revalidate();
			showLayoutPreviewButton.setAction(0, PREVIEW_AUTO_LAYOUT);

			applyLayoutPreviewButton = parent.createChild(-1, WidgetType.GRAPHIC);

			applyLayoutPreviewButton.setOriginalHeight(18);
			applyLayoutPreviewButton.setOriginalWidth(18);
			applyLayoutPreviewButton.setYPositionMode(WidgetPositionMode.ABSOLUTE_BOTTOM);
			applyLayoutPreviewButton.setOriginalX(434 - 30);
			applyLayoutPreviewButton.setOriginalY(45);
			applyLayoutPreviewButton.setSpriteId(Sprites.APPLY_PREVIEW.getSpriteId());
			applyLayoutPreviewButton.setNoClickThrough(true);

			applyLayoutPreviewButton.setOnOpListener((JavaScriptCallback) (e) -> applyLayoutPreview());
			applyLayoutPreviewButton.setHasListener(true);
			applyLayoutPreviewButton.revalidate();
			applyLayoutPreviewButton.setAction(0, "Use this layout");

			cancelLayoutPreviewButton = parent.createChild(-1, WidgetType.GRAPHIC);

			cancelLayoutPreviewButton.setOriginalHeight(18);
			cancelLayoutPreviewButton.setOriginalWidth(18);
			cancelLayoutPreviewButton.setYPositionMode(WidgetPositionMode.ABSOLUTE_BOTTOM);
			cancelLayoutPreviewButton.setOriginalX(434);
			cancelLayoutPreviewButton.setOriginalY(45);
			cancelLayoutPreviewButton.setSpriteId(Sprites.CANCEL_PREVIEW.getSpriteId());
			cancelLayoutPreviewButton.setNoClickThrough(true);

			cancelLayoutPreviewButton.setOnOpListener((JavaScriptCallback) (e) -> cancelLayoutPreview());
			cancelLayoutPreviewButton.setHasListener(true);
			cancelLayoutPreviewButton.revalidate();
			cancelLayoutPreviewButton.setAction(0, "Cancel preview");
		}

		hideLayoutPreviewButtons(!isShowingPreview());
		boolean show =
			getCurrentLayoutableThing() != null &&
			config.showAutoLayoutButton() &&
			(config.whichPlugin() != CORE && !isVanillaLayoutEnabled(getCurrentLayoutableThing()) || hasLayoutEnabled(getCurrentLayoutableThing())) &&
			!isShowingPreview();
		showLayoutPreviewButton.setHidden(!show);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANK) {
			showLayoutPreviewButton = null; // when the bank widget is unloaded or loaded (not sure which) the button is removed from it somehow. So, set it to null so that it will be regenerated.
			registerListeners();
		}
	}

	int checkInventorySetup = 0;

	@Subscribe
	public void onWidgetClosed(WidgetClosed widgetClosed) {
		if (widgetClosed.getGroupId() == InterfaceID.BANK) {
			checkInventorySetup = client.getGameCycle();
			unregisterListeners();
		}
	}

	private boolean registered = false;

	private void registerListeners() {
		if (registered) return;
		Widget widget = client.getWidget(ComponentID.BANK_CONTAINER);
		if (widget == null || widget.isHidden()) return;

		mouseManager.registerMouseListener(this);
		keyManager.registerKeyListener(antiDrag);
		keyManager.registerKeyListener(this);
		registered = true;
	}

	private void unregisterListeners() {
		mouseManager.unregisterMouseListener(this);
		keyManager.unregisterKeyListener(antiDrag);
		keyManager.unregisterKeyListener(this);
		addItemKeybind = addRowKeybind = removeRowKeybind = false;
		draggedItemIndex = -1;
		antiDrag.reset();
		registered = false;
	}

	@Override
	protected void startUp()
	{
		eventBus.register(copyPaste);
		layoutManager.unregisterAutoLayout("Zigzag");
		layoutManager.registerAutoLayout(this, "Zigzag", new AutoLayout()
		{
			@Override
			public net.runelite.client.plugins.banktags.tabs.Layout generateLayout(net.runelite.client.plugins.banktags.tabs.Layout currentLayout)
			{
				List<Integer> equippedGear = getEquippedGear();
				List<Integer> inventory = getInventory();
				if (equippedGear.stream().noneMatch(id -> id > 0) && inventory.stream().noneMatch(id -> id > 0)) {
					chatMessage("This feature uses your equipped items and inventory to automatically create a bank tag layout, but you don't have any items equipped or in your inventory.");
					return null;
				}

//				System.out.println("=============layout:");
				Layout l = new Layout();
				for (int i = 0; i < currentLayout.getLayout().length; i++)
				{
					int itemId = currentLayout.getLayout()[i];
					if (itemId == -1) continue;
//					System.out.println(itemNameWithId(itemId) + " " + i);
					l.putItem(itemId, i);
				}
//				System.out.println("=============item widgets:");
				Widget widget = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
				for (Widget dynamicChild : widget.getDynamicChildren())
				{
					if (dynamicChild.getItemId() == -1) continue;
//					System.out.println(itemNameWithId(dynamicChild.getItemId()));
				}

				Layout previewLayout = layoutGenerator.basicBankTagLayout(equippedGear, inventory, config.autoLayoutIncludeRunePouchRunes() ? getRunePouchRunes() : Collections.emptyList(), Collections.emptyList(), l, getAutoLayoutDuplicateLimit(), config.autoLayoutStyle());
				net.runelite.client.plugins.banktags.tabs.Layout l2 = new net.runelite.client.plugins.banktags.tabs.Layout(currentLayout.getTag());
				for (Map.Entry<Integer, Integer> pair : previewLayout.allPairs())
				{
					l2.setItemAtPos(pair.getValue(), pair.getKey());
				}
//				System.out.println("==========================here");
				for (int i = 0; i < l2.getLayout().length; i++)
				{
					int itemId = l2.getLayout()[i];
					if (itemId == -1) continue;
//					System.out.println(itemNameWithId(itemId) + " " + i);
				}
				return l2;
			}
		});
		lastProfile = configManager.getProfile();

		overlayManager.add(fakeItemOverlay);
		spriteManager.addSpriteOverrides(Sprites.values());

		comboPanel = new com.banktaglayouts.combo.ComboPanel(this);
		comboNavButton = net.runelite.client.ui.NavigationButton.builder()
			.tooltip("Combo Tags")
			.icon(net.runelite.client.util.ImageUtil.loadImageResource(getClass(), "/com/banktaglayouts/auto_layout.png"))
			.priority(COMBO_PANEL_PRIORITY)
			.panel(comboPanel)
			.build();
		clientToolbar.addNavigation(comboNavButton);
		comboTagSyncInProgress = false;
		comboMembersUntagged.clear();
		lastCorePinnedWinners.clear();
		clientThread.invokeLater(() -> registerComboBankTags());

		clientThread.invokeLater(() -> {
			registerListeners();
			if (client.getGameState() == GameState.LOGGED_IN) {
				showLayoutPreviewButton = null;
				maintainAllComboCoreTabs();
				// If a combo-host CORE tab is already open, reopen it so the just-registered ComboBankTag is
				// captured into the active filter (membership is OR'd in only at openTag time).
				recaptureActiveComboCoreTab();
				updateButton();
				bankSearch.layoutBank();
			}
		});
	}

	/** Reopens the active tab if it is a combo-host CORE tab, so a newly-registered ComboBankTag takes effect. */
	private void recaptureActiveComboCoreTab() {
		String active = bankTagsService.getActiveTag();
		if (active != null && comboBankTags.containsKey(active)
			&& isVanillaLayoutEnabled(LayoutableThing.bankTag(active))) {
			bankTagsService.openBankTag(active, net.runelite.client.plugins.banktags.BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		}
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(copyPaste);
		overlayManager.remove(fakeItemOverlay);
		spriteManager.removeSpriteOverrides(Sprites.values());
		unregisterListeners();

		if (comboNavButton != null) {
			clientToolbar.removeNavigation(comboNavButton);
		}

		// Run on the client thread (mirroring startUp's registerComboBankTags) — tag (un)registration mutates
		// RuneLite's unsynchronized customTags HashMap, which the client-thread registerComboBankTags also
		// writes to; doing the remove on the EDT can corrupt/CME it. Independent of game state.
		clientThread.invokeLater(this::unregisterComboBankTags);

		clientThread.invokeLater(() -> {
			if (client.getGameState() == GameState.LOGGED_IN) {
				indexToWidget.clear();
				cancelLayoutPreview();
				if (showLayoutPreviewButton != null) showLayoutPreviewButton.setHidden(true);

				bankSearch.layoutBank();
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup())) {
			if (com.banktaglayouts.combo.ComboStore.CONFIG_KEY.equals(event.getKey())) {
				comboColorCache = null; // a group's color (or set of groups) changed → rebuild on next read
				comboColorObjCache = null; // drop memoized Color instances alongside the ARGB map
				invalidateComboCellGroupsCache(); // a member/variant change can alter the resolved winner shown
				// Editing a combo in the panel (reorder members, change variant, etc.) can change a cell's winner
				// or ghost. Re-resolve + relayout on the client thread (onConfigChanged may be on the EDT) so an
				// already-open bank reflects it now instead of at the next bank interaction. Safe if no bank open.
				clientThread.invokeLater(() -> {
					if (client.getGameState() == GameState.LOGGED_IN) {
						maintainAllComboCoreTabs();
						bankSearch.layoutBank();
					}
				});
			}
			if ("comboReplaceOnList".equals(event.getKey())) {
				if (comboPanel != null) {
					SwingUtilities.invokeLater(comboPanel::rebuild);
				}
			}
			if ("layoutEnabledByDefault".equals(event.getKey())) {
				clientThread.invokeLater(() -> applyCustomBankTagItemPositions());
			} else if ("showAutoLayoutButton".equals(event.getKey())) {
				clientThread.invokeLater(this::updateButton);
			} else if ("whichPlugin".equals(event.getKey()) || "convertAll".equals(event.getKey())) {
				if (SwingUtilities.isEventDispatchThread() && config.convertAll() && config.whichPlugin() != BOTH) {
					boolean toCore = config.whichPlugin() == CORE;
					clientThread.invoke(() -> convertAllLayouts(toCore));
				}
			} else if ("useWithInventorySetups".equals(event.getKey())) {
				clientThread.invokeLater(bankSearch::layoutBank);
			}
		} else if (BankTagsPlugin.CONFIG_GROUP.equals(event.getGroup()) && BankTagsPlugin.TAG_TABS_CONFIG.equals(event.getKey())) {
			handlePotentialTagRename(event);
		}
	}

	private void convertAllLayouts(boolean toCore) {
		int converted = 0;
		for (TagTab tab : tabManager.getTabs()) {
			if (toCore && hasLayoutEnabled(LayoutableThing.bankTag(tab.getTag()))) {
				convertTagToCore(tab.getTag());
				converted++;
			} else if (!toCore && hasRuneliteLayout(tab.getTag())) {
				convertTagToHub(tab.getTag());
				converted++;
			}
			if (tab.getTag().equals(tabInterface.getActiveTag())) {
				bankTagsService.openBankTag(tabInterface.getActiveTag(), BankTagsService.OPTION_ALLOW_MODIFICATIONS);
			}
		}
		chatMessage("Converted " + converted + " tag tabs from " + (toCore ? "hub to built-in." : "built-in to hub."));
	}

	public void convertTagToHub(String tag) {
		net.runelite.client.plugins.banktags.tabs.Layout layout = layoutManager.loadLayout(tag);
		Layout hubLayout = new Layout();
		int index = 0;
		for (int i : layout.getLayout()) {
			if (i >= 0) hubLayout.putItem(i, index);
			index++;
		}
		saveLayout(LayoutableThing.bankTag(tag), hubLayout);
		layoutManager.removeLayout(tag);
	}

	public void convertTagToCore(String tag) {
		Layout layout = getBankOrderNonPreview(LayoutableThing.bankTag(tag));
		net.runelite.client.plugins.banktags.tabs.Layout coreLayout = new net.runelite.client.plugins.banktags.tabs.Layout(tag);
		for (Map.Entry<Integer, Integer> pair : layout.allPairs()) {
			coreLayout.setItemAtPos(getNonPlaceholderId(pair.getValue()), pair.getKey());
		}
		layoutManager.saveLayout(coreLayout);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		GameState gameState = gameStateChanged.getGameState();
		if (gameState == GameState.LOGGED_IN) {
			checkVersionUpgrade();
		}
	}

	private void onVersionUpgraded(VersionNumber previousVersion, VersionNumber newVersion) {
		if (previousVersion.compareTo(new VersionNumber(1, 4, 10)) < 0)
		{
			if (config.updateMessages())
			{
				clientThread.invokeLater(() -> {
					chatMessage(ColorUtil.wrapWithColorTag("Bank Tag Layouts ", Color.RED) + "new version: " + "1.4.10");
					chatMessage(" - " + "New Auto-layout mode \"Presets\" shows your gear and inventory in a prettier way. You can switch to it in the plugin's config.");
				});
			}
		}
		if (previousVersion.compareTo(new VersionNumber(1, 4, 11)) < 0)
		{
			String prefix = CONFIG_GROUP + "." + INVENTORY_SETUPS_LAYOUT_CONFIG_KEY_PREFIX;
			for (String key : configManager.getConfigurationKeys(prefix))
			{
				String inventorySetupName = key.substring(prefix.length());
				String layoutString = configManager.getConfiguration(CONFIG_GROUP, INVENTORY_SETUPS_LAYOUT_CONFIG_KEY_PREFIX + inventorySetupName);
				String escapedKey = LayoutableThing.inventorySetup(inventorySetupName).configKey();
				configManager.setConfiguration(CONFIG_GROUP, escapedKey, layoutString);
			}
		}
		if (previousVersion.compareTo(new VersionNumber(1, 5, 0)) < 0) {
			if (config.updateMessages() && config.whichPlugin() != WhichPlugin.CORE) {
				clientThread.invokeLater(() -> {
					chatMessage(ColorUtil.wrapWithColorTag("Bank Tag Layouts ", Color.RED) + "new version: " + "1.5.0");
					chatMessage(" - " + "Adds \"Add item\" and \"Insert empty row\" options when right-clicking in empty space.");
				});
			}
		}
	}

	void checkVersionUpgrade() {
		try (InputStream is = BankTagLayoutsPlugin.class.getResourceAsStream("/version.txt"))
		{
			Properties props = new Properties();

			try
			{
				props.load(is);
			}
			catch (IOException e)
			{
				log.error("unable to load version number", e);
				return;
			}

			VersionNumber buildVersion = new VersionNumber(props.getProperty("version"));
			String previousVersionString = configManager.getConfiguration(CONFIG_GROUP, "version");
			// This is a best guess - they could have had the plugin installed previously but if they don't have any layouts set they probably don't use it.
			boolean assumeFreshInstall =
				previousVersionString == null
				&& configManager.getConfigurationKeys(CONFIG_GROUP + "." + LAYOUT_CONFIG_KEY_PREFIX).size() == 0
				&& configManager.getConfigurationKeys(CONFIG_GROUP + "." + INVENTORY_SETUPS_LAYOUT_CONFIG_KEY_PREFIX).size() == 0;
			VersionNumber previousVersion = new VersionNumber(previousVersionString);
			if (buildVersion.compareTo(previousVersion) > 0 && !assumeFreshInstall)
			{
				onVersionUpgraded(previousVersion, buildVersion);
			}
			configManager.setConfiguration(CONFIG_GROUP, "version", buildVersion);
		}
		catch (IOException e) {
			log.error("unable to close version file.", e);
		}
	}

	private ConfigProfile lastProfile = null;

	@Subscribe
	public void onProfileChanged(ProfileChanged e) {
		lastProfile = configManager.getProfile();
	}

	private void handlePotentialTagRename(ConfigChanged event) {
		// Profile changes can look like tag renames sometimes, but we do not want to modify the config in that case
		// because it can cause people to lose their data.
		// The order is 1) configManager.getProfile() changes 2) ConfigChanged events 3) ProfileChanged event
		if (lastProfile.getId() != configManager.getProfile().getId()) {
			return;
		}

		String oldValue = event.getOldValue();
		String newValue = event.getNewValue();
		Set<String> oldTags = new HashSet<>(Text.fromCSV(oldValue == null ? "" : oldValue));
		Set<String> newTags = new HashSet<>(Text.fromCSV(newValue == null ? "" : newValue));
		// Compute the diff between the two lists.
		Iterator<String> iter = oldTags.iterator();
		while (iter.hasNext()) {
			String oldTag = iter.next();
			if (newTags.remove(oldTag)) {
				iter.remove();
			}
		}

		// A single tag tab deleted with nothing renamed in: drop the combo data keyed to it. RuneLite deletes
		// tabs one at a time, so an empty newTags with MORE than one removed tag is a bulk/transient rewrite
		// (e.g. an import or a clear that slipped past the profile guard) — not real deletions — and must NOT
		// purge anything. Combo group definitions are global, so only the per-tab cells/winner-map/tag go.
		if (newTags.isEmpty()) {
			if (oldTags.size() == 1) {
				removeComboDataForTab(oldTags.iterator().next());
			}
			return;
		}

		// Check if it's a rename or something else.
		if (oldTags.size() != 1 || newTags.size() != 1) return;

		String oldTagName = oldTags.iterator().next();
		String newName = newTags.iterator().next();
		LayoutableThing oldName = LayoutableThing.bankTag(oldTagName);

		Layout oldLayout = getBankOrderNonPreview(oldName);
		if (oldLayout != null) {
			saveLayout(LayoutableThing.bankTag(newName), oldLayout);
			configManager.unsetConfiguration(CONFIG_GROUP, oldName.configKey());
		}
		// Carry the tab's combo cells over to the new name too, else they orphan under the dead tag.
		migrateComboDataForTab(oldTagName, newName);
	}

	@Provides
	BankTagLayoutsConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankTagLayoutsConfig.class);
	}

	private void applyLayoutPreview() {
		if (previewLayoutable.isBankTab()) {
			for (Integer itemId : previewLayout.getAllUsedItemIds()) {
				if (!copyPaste.findTag(itemId, previewLayoutable.name)) {
					log.debug("adding item " + itemNameWithId(itemId) + " to tag");
					tagManager.addTag(itemId, previewLayoutable.name, false);
				}
			}
		}

		saveLayoutNonPreview(previewLayoutable, previewLayout);

		cancelLayoutPreview();
		bankSearch.layoutBank();
	}

	private void hideLayoutPreviewButtons(boolean hide) {
		if (applyLayoutPreviewButton != null) applyLayoutPreviewButton.setHidden(hide);
		if (cancelLayoutPreviewButton != null) cancelLayoutPreviewButton.setHidden(hide);
		if (showLayoutPreviewButton != null && config.showAutoLayoutButton() && getCurrentLayoutableThing() != null) showLayoutPreviewButton.setHidden(!hide);
	}

	private void cancelLayoutPreview() {
		previewLayout = null;
		previewLayoutable = null;

		hideLayoutPreviewButtons(true);

		applyCustomBankTagItemPositions();
	}

	/** null indicates that there should not be a preview shown. */
	private Layout previewLayout = null;
	private LayoutableThing previewLayoutable = null;

	private void showLayoutPreview() {

		if (isShowingPreview()) return;
		LayoutableThing currentLayoutableThing = getCurrentLayoutableThing();
		if (currentLayoutableThing == null) {
			chatMessage("Select a tag tab before using this feature.");
			return;
		} else if (isVanillaLayoutEnabled(currentLayoutableThing)) {
			chatErrorMessage("This tag is using the new non-plugin-hub version of bank tag layouts. Disable it before using the plugin-hub version, by right-clicking the tag tab and selecting \"Disable layout\".");
			return;
		} else {
			// TODO allow creation of new tab.
		}

		if (currentLayoutableThing.isBankTab()) {
			List<Integer> equippedGear = getEquippedGear();
			List<Integer> inventory = getInventory();
			if (equippedGear.stream().noneMatch(id -> id > 0) && inventory.stream().noneMatch(id -> id > 0)) {
				chatMessage("This feature uses your equipped items and inventory to automatically create a bank tag layout, but you don't have any items equipped or in your inventory.");
				return;
			}

			hideLayoutPreviewButtons(false);

			Layout currentLayout = getBankOrderNonPreview(currentLayoutableThing);
			if (currentLayout == null) currentLayout = Layout.emptyLayout();

			previewLayout = layoutGenerator.basicBankTagLayout(equippedGear, inventory, config.autoLayoutIncludeRunePouchRunes() ? getRunePouchRunes() : Collections.emptyList(), Collections.emptyList(), currentLayout, getAutoLayoutDuplicateLimit(), config.autoLayoutStyle());
		} else {
			throw new UnsupportedOperationException();
		}

		hideLayoutPreviewButtons(false);

		previewLayoutable = currentLayoutableThing;

		applyCustomBankTagItemPositions();
	}

	private int getAutoLayoutDuplicateLimit() {
		return !config.autoLayoutDuplicatesEnabled() ? 0 : config.autoLayoutDuplicateLimit();
	}

	private List<Integer> getEquippedGear() {
		ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
		if (container == null) return Collections.emptyList();
		return Arrays.stream(container.getItems()).map(Item::getId).collect(Collectors.toList());
	}

	/**
	 * empty spaces before an item are always -1, empty spaces after an item may be -1 or may not be included in the
	 * list at all.
	 */
	private List<Integer> getInventory() {
		ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
		if (container == null) return Collections.emptyList();
		return Arrays.stream(container.getItems()).map(w -> w.getId()).collect(Collectors.toList());
	}

	private boolean isShowingPreview() {
		return previewLayout != null;
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick) {
		if (checkInventorySetup + 1 == client.getGameCycle()) {
			updateInventorySetupShown();
		}

		Widget widget = client.getWidget(ComponentID.BANK_CONTAINER);
		if (widget == null || widget.isHidden()) {
			return;
		}

		if (!client.isMenuOpen()) {
			addTabInterfaceMenuEntries();
		}

		sawMenuEntryAddedThisClientTick = false;
	}

	private static final int[] AMOUNT_VARBITS = {Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4};
	private static final int[] RUNE_VARBITS = {Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3, Varbits.RUNE_POUCH_RUNE4};
	private List<Integer> getRunePouchRunes() {
		List<Integer> runes = new ArrayList<>(AMOUNT_VARBITS.length);
		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < AMOUNT_VARBITS.length; i++)
		{
			int amount = client.getVarbitValue(AMOUNT_VARBITS[i]);
			if (amount <= 0) {
				continue;
			}
			int runeId = client.getVarbitValue(RUNE_VARBITS[i]);
			int runeItemId = runepouchEnum.getIntValue(runeId);
			runes.add(runeItemId);
		}
		return runes;
	}

	private String inventorySetup = null;
	private void updateInventorySetupShown() {
		if (client.getVarbitValue(Varbits.CURRENT_BANK_TAB) == 15 /* potion storage */) {
			inventorySetup = null;
			return;
		}

		Widget bankTitleBar = client.getWidget(ComponentID.BANK_TITLE_BAR);
		String newSetup = null;
		if (bankTitleBar != null)
		{
			String bankTitle = bankTitleBar.getText();
			Matcher matcher = Pattern.compile("Inventory Setup <col=ff0000>(?<setup>.*) - (?<subfilter>.*)</col>.*").matcher(bankTitle);
			if (matcher.matches())
			{
				newSetup = matcher.group("setup");
			}
		}

		inventorySetup = newSetup;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		// The bank changed → combo winners may have rotated. Refresh each CORE host tab's membership + pinned
		// winners NOW, before the bank rebuilds, so RuneLite renders the new winners in place (and never
		// auto-floats a stale one). Hub tabs are untouched (maintainComboCoreTab no-ops on them).
		if (event.getContainerId() == InventoryID.BANK.getId()) {
			comboResolver.invalidateBankCache(); // bank changed → drop the cached base→id snapshot before resolving
			// Scan the config keyspace for host tags ONCE per bank change, then share the list with both passes.
			List<String> hostTags = comboHostTags();
			registerComboBankTags(hostTags);
			maintainAllComboCoreTabs(hostTags);
		}
	}

	@Subscribe(priority = -1f) // "Bank Tags" plugin also sets the scroll bar height; run after it. We also need to run after "Inventory Setups" to get the bank title it sets.
	public void onScriptPreFired(ScriptPreFired event) {
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING) {
			return;
		}

		updateInventorySetupShown();

		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (layoutable == null) {
			return;
		}

		Layout layout = getBankOrder(layoutable);
		if (layout == null) {
			return;
		}

		int maxIndex = layout.getAllUsedIndexes().stream().max(Integer::compare).orElse(0);
		int height = getYForIndex(maxIndex) + BANK_ITEM_HEIGHT;

		// This is prior to bankmain_finishbuilding running, so the arguments are still on the stack. Overwrite
		// argument int13 (9 from the end) which is the height passed to if_setscrollsize
		client.getIntStack()[client.getIntStackSize() - 9] = height;
	}

	@Subscribe(priority = -1f) // I want to run after the Bank Tags plugin does, since it will interfere with the layout-ing if hiding tab separators is enabled.
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.BANKMAIN_BUILD) {
			LayoutableThing layoutable = getCurrentLayoutableThing();
			if (layoutable == null || !layoutable.equals(lastLayoutable) || isVanillaLayoutEnabled(layoutable)) {
				cancelLayoutPreview();
			}

			// CORE tabs: if RuneLite moved a combo winner (e.g. the user dragged it), make the cell's index
			// follow it so the box stays on the item instead of snapping back on the next maintain.
			if (layoutable != null && layoutable.isBankTab() && isVanillaLayoutEnabled(layoutable)) {
				reconcileComboCoreCells(layoutable.name);
			}

			// Keep each combo cell's OWNED winner tagged (so it's a withdrawable tab member), deferred to the
			// next tick since mutating tags during the build crashes the client. This frame renders as-is.
			syncComboWinnerTags(layoutable);
			applyCustomBankTagItemPositions(false, false);

			lastLayoutable = layoutable;

			// invokelater is required for when you open the bank with a tag tab already open.
			clientThread.invokeLater(this::updateButton);
		}
	}

	private void importLayout() {
		final String clipboardData;
		try {
			clipboardData = Toolkit
					.getDefaultToolkit()
					.getSystemClipboard()
					.getData(DataFlavor.stringFlavor)
					.toString()
					.trim();
		} catch (UnsupportedFlavorException | IOException e) {
			chatErrorMessage("import failed:", " couldn't get an import string from the clipboard");
			return;
		}

		if (!clipboardData.startsWith(BANK_TAG_STRING_PREFIX)) {
			// TODO try to import the tag as a normal tag?.
			if (Pattern.compile("[^,]+,\\d+(,[\\d-]+)*").matcher(clipboardData).matches() || clipboardData.startsWith("banktag")) {
				chatErrorMessage("import failed:", " This looks like a regular bank tag, try using \"Import tag tab\" instead of \"" + IMPORT_LAYOUT + "\".");
			} else {
				chatErrorMessage("import failed:", " Invalid format. layout-ed tag data starts with \"" + BANK_TAG_STRING_PREFIX + "\"; did you copy the wrong thing?");
			}
			return;
		}

		String[] split = clipboardData.split(",banktag:");
		if (split.length != 2) {
			chatErrorMessage("import failed:", " invalid format. layout string doesn't include regular bank tag data (It should say \"banktag:\" somewhere in the import string). Maybe you didn't copy the whole thing?");
			return;
		}

		String prefixRemoved = split[0].substring(BANK_TAG_STRING_PREFIX.length());

		String name;
		String layoutString;
		int firstCommaIndex = prefixRemoved.indexOf(",");
		if (firstCommaIndex == -1) { // There are no items in this layout.
			name = prefixRemoved;
			layoutString = "";
		} else {
			name = prefixRemoved.substring(0, firstCommaIndex);
			layoutString = prefixRemoved.substring(name.length() + 1);
		}

		name = validateTagName(name);
		if (name == null) return; // it was invalid.

		Layout layout;
		try {
			layout = Layout.fromString(layoutString);
		} catch (NumberFormatException e) {
			chatErrorMessage("import failed:", " something in the layout data is not a number");
			return;
		}
		String tagString = split[1];

		log.debug("import string: {}, {}, {}", name, layoutString, split[1]);

		// If the tag has no items in it, it will not trigger the overwrite warning. This is not intuitive, but I don't care enough to fix it.
		if (!tagManager.getItemsForTag(name).isEmpty()) {
			String finalName = name;
			chatboxPanelManager.openTextMenuInput("Tag tab with same name (" + name + ") already exists.")
					.option("Keep both, renaming imported tab", () -> {
						clientThread.invokeLater(() -> { // If the option is selected by a key, this will not be on the client thread.
							String newName = generateUniqueName(finalName);
							if (newName == null) {
								chatErrorMessage("import failed:", " couldn't find a unique name. do you literally have 100 similarly named tags???????????");
								return;
							}
							importLayout(newName, layout, tagString);
						});
					})
					.option("Overwrite existing tab", () -> {
						clientThread.invokeLater(() -> { // If the option is selected by a key, this will not be on the client thread.
							importLayout(finalName, layout, tagString);
						});
					})
					.option("Cancel", Runnables::doNothing)
					.build();
		} else {
			importLayout(name, layout, tagString);
		}
	}

	private String generateUniqueName(String name) {
		for (int i = 2; i < 100; i++) {
			String newName = "(" + i + ") " + name;
			if (tagManager.getItemsForTag(newName).isEmpty()) {
				return newName;
			}
		}
		return null;
	}

	private void importLayout(String name, Layout layout, String tagString) {
		boolean successful = importBankTag(name, tagString);
		if (!successful) return;

		layoutManager.removeLayout(name); // disable vanilla layout
		saveLayout(LayoutableThing.bankTag(name), layout);

		chatMessage("Imported layout-ed tag tab \"" + name + "\"");

		applyCustomBankTagItemPositions();
	}

	void saveLayout(LayoutableThing layoutable, Layout layout) {
		if (isShowingPreview()) {
			previewLayout = layout;
			return;
		}

		saveLayoutNonPreview(layoutable, layout);
	}

	private void saveLayoutNonPreview(LayoutableThing layoutable, Layout layout) {
		configManager.setConfiguration(CONFIG_GROUP, layoutable.configKey(), layout.toString());
	}

	private String validateTagName(String name) {
		StringBuilder sb = new StringBuilder();
		for (char c : name.toCharArray()) {
			if (FILTERED_CHARS.test(c)) {
				sb.append(c);
			}
		}

		if (sb.length() == 0) {
			chatErrorMessage("import failed:", " tag name does not contain any valid characters.");
			return null;
		}

		return sb.toString().toLowerCase();
	}

	// TODO what is the purpose of the return value.
	private boolean importBankTag(String name, String tagString) {
		log.debug("importing tag data. " + tagString);
		final Iterator<String> dataIter = Text.fromCSV(tagString).iterator();
		dataIter.next(); // skip name.

		final String icon = dataIter.next();

		copyPaste.setIcon(name, icon);

		tagManager.removeTag(name);
		while (dataIter.hasNext()) {
			int itemId = Integer.parseInt(dataIter.next());
			tagManager.addTag(itemId, name, itemId < 0);
		}

		copyPaste.saveNewTab(name);
		copyPaste.loadTab(name);

		return true;
	}

	@Inject TabManager tabManager;

	private void enableLayout(LayoutableThing layoutable) {
		TagTab tagTab = tabManager.find(layoutable.name);
		net.runelite.client.plugins.banktags.tabs.Layout layout = layoutManager.loadLayout(layoutable.name);
		if (layout != null) {
			chatErrorMessage("This tag is using the new non-plugin-hub version of bank tag layouts. Disable it before enabling the plugin-hub version, by right-clicking the tag tab and selecting \"Disable layout\".");
			return;
		}
		saveLayout(layoutable, Layout.emptyLayout());
		if (layoutable.equals(getCurrentLayoutableThing())) {
			applyCustomBankTagItemPositions();
		}
	}

	private void disableLayout(String bankTagName) {
		chatboxPanelManager.openTextMenuInput("Delete layout for " + bankTagName + "?")
				.option("Yes", () ->
						clientThread.invoke(() ->
						{
							configManager.setConfiguration(CONFIG_GROUP, LAYOUT_CONFIG_KEY_PREFIX + bankTagName, LAYOUT_EXPLICITLY_DISABLED);
							if (tabInterface.getActiveTag() != null && bankTagName.equals(tabInterface.getActiveTag())) {
								bankSearch.layoutBank();
							}
						})
				)
				.option("No", Runnables::doNothing)
				.build();
	}


	/**
	 * Stats-aware combo base id for an item: placeholders/noted/same-stat variants and cosmetic recolors collapse
	 * to one base, but a stat-changing variation (an imbued DK ring) is its OWN base — see
	 * {@link com.banktaglayouts.combo.ItemIndex#statBaseOfCanon}. Used for membership AND bank ownership so an
	 * imbued item resolves to its own member. Client thread.
	 */
	private int comboBaseOf(int itemId) {
		return com.banktaglayouts.combo.ItemIndex.comboBaseOf(itemManager, itemId);
	}

	/** A combo group's member base ids in priority order (the manual list order). */
	public List<Integer> orderedComboMembers(String comboGroup) {
		return comboResolver.orderedMemberBases(comboGroup);
	}

	// ======================================================================================================
	// CORE (built-in) layout combo cells.
	//
	// On a tab that uses RuneLite's built-in bank-tags layout, RuneLite (LayoutManager.layout) owns rendering
	// and runs DURING the bank build, before any of this plugin's hooks. It auto-appends any in-tab item not
	// pinned in the layout int[] to the first empty slot and PERSISTS it — so rotating a combo winner used to
	// leave a stray floated placeholder. We avoid that by being the single source of truth RuneLite reads:
	//   * membership: a per-tab ComboBankTag (OR'd into the tab filter) makes ONLY owned winners pass, so
	//     non-winner members / leftover placeholders never enter the tab and can't be auto-floated;
	//   * position: we pin each cell's winner into the core layout int[] (and scrub stray member copies) on
	//     every bank change — before the build — mutating the LIVE active Layout object so it takes effect
	//     this frame. An owned winner renders as a real widget; a ghost (unowned) renders as RuneLite's own
	//     faded "layout placeholder". No per-winner tagging, so none of the old tag-sync race.
	// ======================================================================================================

	/** Tags currently hosting at least one combo cell (from the {@code comboslots_<tag>} config keys). */
	private List<String> comboHostTags() {
		List<String> tags = new ArrayList<>();
		String prefix = CONFIG_GROUP + "." + com.banktaglayouts.combo.ComboSlots.CONFIG_KEY_PREFIX;
		for (String key : configManager.getConfigurationKeys(CONFIG_GROUP)) {
			if (key.startsWith(prefix)) {
				tags.add(key.substring(prefix.length()));
			}
		}
		return tags;
	}

	/** Ensures every combo-host tab has a registered {@link com.banktaglayouts.combo.ComboBankTag}. */
	private void registerComboBankTags() {
		registerComboBankTags(comboHostTags());
	}

	/** As {@link #registerComboBankTags()} but with a precomputed host-tag list (avoids re-scanning the config). */
	private void registerComboBankTags(List<String> hostTags) {
		for (String hostTag : hostTags) {
			ensureComboBankTagRegistered(hostTag);
		}
	}

	/**
	 * Registers a {@link com.banktaglayouts.combo.ComboBankTag} for the host tag if it has none yet; returns
	 * true if it was NEWLY registered. A new registration must be captured into an already-open tab's filter
	 * via {@link #recaptureActiveComboCoreTab()} (the filter is built once at openTag); for an existing
	 * registration we just mutate the same tag instance, so a relayout is enough.
	 */
	private boolean ensureComboBankTagRegistered(String hostTag) {
		if (comboBankTags.containsKey(hostTag)) {
			return false;
		}
		com.banktaglayouts.combo.ComboBankTag t = new com.banktaglayouts.combo.ComboBankTag();
		comboBankTags.put(hostTag, t);
		tagManager.registerTag(hostTag, t);
		return true;
	}

	private void unregisterComboBankTags() {
		for (String hostTag : comboBankTags.keySet()) {
			tagManager.unregisterTag(hostTag);
		}
		comboBankTags.clear();
	}

	/**
	 * Drops every per-tab combo trace of a deleted host tab: its cells ({@code comboslots_}), its hub
	 * winner map ({@code combowinners_}), and its registered {@link com.banktaglayouts.combo.ComboBankTag}.
	 * Combo GROUP definitions are global (shared across tabs), so they are deliberately left untouched.
	 * Client thread (touches TagManager).
	 */
	private void removeComboDataForTab(String hostTag) {
		boolean hadCells = !com.banktaglayouts.combo.ComboSlots.read(configManager, hostTag).isEmpty();
		com.banktaglayouts.combo.ComboSlots.write(configManager, hostTag, Collections.emptyList());
		invalidateComboCellGroupsCache();
		writeComboWinnerMap(hostTag, new HashMap<>());
		lastCorePinnedWinners.remove(hostTag);
		comboMembersUntagged.remove(hostTag); // recreating a tab with this name should re-run untag cleanup
		if (comboBankTags.remove(hostTag) != null) {
			tagManager.unregisterTag(hostTag);
		}
		if (hadCells) {
			log.debug("removed combo cells for deleted tab '{}'", hostTag);
		}
	}

	/**
	 * Moves a renamed host tab's per-tab combo data — its cells ({@code comboslots_}), hub winner map
	 * ({@code combowinners_}), and registered {@link com.banktaglayouts.combo.ComboBankTag} — from the old
	 * tag name to the new one. Without this the data orphans under the dead tag: the renamed tab shows no
	 * cells and {@link #comboHostTags()} keeps returning the dead name forever. Global combo GROUP
	 * definitions are shared and left untouched. No-op when the old tab had no combo data. Client thread.
	 */
	private void migrateComboDataForTab(String oldTag, String newTag) {
		if (oldTag.equalsIgnoreCase(newTag)) {
			return;
		}
		List<com.banktaglayouts.combo.ComboSlots.Slot> slots = com.banktaglayouts.combo.ComboSlots.read(configManager, oldTag);
		Map<Integer, Integer> winners = readComboWinnerMap(oldTag);
		boolean wasHost = comboBankTags.containsKey(oldTag);
		if (slots.isEmpty() && winners.isEmpty() && !wasHost) {
			return; // nothing combo-related on the old tab
		}
		// Write under the new name, then clear the old keys (both writes unset when empty).
		com.banktaglayouts.combo.ComboSlots.write(configManager, newTag, slots);
		writeComboWinnerMap(newTag, winners);
		com.banktaglayouts.combo.ComboSlots.write(configManager, oldTag, Collections.emptyList());
		writeComboWinnerMap(oldTag, new HashMap<>());
		invalidateComboCellGroupsCache();
		lastCorePinnedWinners.remove(oldTag);
		comboMembersUntagged.remove(oldTag); // the new name re-runs untag cleanup as needed
		// Re-register the ComboBankTag under the new name (drop the old registration first).
		if (comboBankTags.remove(oldTag) != null) {
			tagManager.unregisterTag(oldTag);
		}
		if (!slots.isEmpty()) {
			ensureComboBankTagRegistered(newTag);
			// A fresh ComboBankTag starts with empty ownedWinners; populate its membership + pin its winners now
			// so the renamed tab's cells render immediately instead of blanking until the next bank change.
			maintainComboCoreTab(newTag);
		}
		log.debug("migrated combo data on rename '{}' -> '{}'", oldTag, newTag);
	}

	/** The Layout object to mutate for a host tag: the LIVE active one (if that tab is open) else the saved copy. */
	private net.runelite.client.plugins.banktags.tabs.Layout coreLayoutFor(String hostTag) {
		boolean active = hostTag.equalsIgnoreCase(bankTagsService.getActiveTag());
		net.runelite.client.plugins.banktags.tabs.Layout live = active ? bankTagsService.getActiveLayout() : null;
		return live != null ? live : layoutManager.loadLayout(hostTag);
	}

	private void maintainAllComboCoreTabs() {
		maintainAllComboCoreTabs(comboHostTags());
	}

	/** As {@link #maintainAllComboCoreTabs()} but with a precomputed host-tag list (avoids re-scanning the config). */
	private void maintainAllComboCoreTabs(List<String> hostTags) {
		// Skip while the bank container hasn't loaded yet (e.g. the startUp deferral fires before the first bank
		// build): with a null BANK container every winner resolves as a ghost, so we'd pin ghosts / clear
		// ownedWinners on inactive tabs. The real first BANK onItemContainerChanged runs maintain properly.
		if (client.getItemContainer(InventoryID.BANK) == null) {
			return;
		}
		for (String hostTag : hostTags) {
			maintainComboCoreTab(hostTag);
		}
	}

	/**
	 * Makes RuneLite render the combo cells of a CORE-layout host tab from our live resolution: refreshes the
	 * tab's owned-winner membership, pins each cell's winner into the core layout, and scrubs stray member
	 * copies (legacy floats). No-op for hub tabs. Client thread.
	 */
	private void maintainComboCoreTab(String hostTag) {
		if (!isVanillaLayoutEnabled(LayoutableThing.bankTag(hostTag))) {
			return; // hub tabs are rendered by applyCustomBankTagItemPositions, not RuneLite.
		}
		List<com.banktaglayouts.combo.ComboSlots.Slot> slots = com.banktaglayouts.combo.ComboSlots.read(configManager, hostTag);

		Map<Integer, Integer> cellToWinner = new HashMap<>(); // cell index -> winner id (owned id or ghost)
		Set<Integer> ownedWinners = new HashSet<>();
		Set<Integer> memberBases = new HashSet<>();
		for (com.banktaglayouts.combo.ComboSlots.Slot s : slots) {
			memberBases.addAll(comboResolver.orderedMemberBases(s.getGroup()));
			int winner = comboResolver.resolveWinner(s.getGroup());
			if (winner > 0) {
				cellToWinner.put(s.getIndex(), winner);
				if (comboResolver.isOwnedReal(winner)) {
					ownedWinners.add(winner);
				}
			}
		}

		com.banktaglayouts.combo.ComboBankTag bankTag = comboBankTags.get(hostTag);
		if (bankTag != null) {
			bankTag.setOwnedWinners(ownedWinners);
		}
		untagComboMembersOnce(hostTag, memberBases);

		boolean active = hostTag.equalsIgnoreCase(bankTagsService.getActiveTag());
		net.runelite.client.plugins.banktags.tabs.Layout live = active ? bankTagsService.getActiveLayout() : null;
		if (live != null) {
			// Active tab: mutate the LIVE layout object RuneLite is rendering (config-only edits don't take
			// effect until reopen). We don't persist here — RuneLite owns saving the active layout — so a stale
			// snapshot must never short-circuit a later inactive load.
			lastCorePinnedWinners.remove(hostTag);
			applyComboCellsToCore(live, cellToWinner, memberBases);
			return;
		}
		// Inactive tab: operate on the SAVED layout copy. Nothing edits an unopened tab's layout, so when the
		// resolved winners match what we last pinned there's nothing to re-correct — skip the load + save churn.
		if (cellToWinner.equals(lastCorePinnedWinners.get(hostTag))) {
			return;
		}
		net.runelite.client.plugins.banktags.tabs.Layout core = layoutManager.loadLayout(hostTag);
		if (core == null) {
			return;
		}
		if (applyComboCellsToCore(core, cellToWinner, memberBases)) {
			layoutManager.saveLayout(core);
		}
		lastCorePinnedWinners.put(hostTag, new HashMap<>(cellToWinner));
	}

	/**
	 * Pins each cell's winner at its position and removes any combo-member item sitting OUTSIDE its cell. Such
	 * an item is always a stray (a rotated-out winner RuneLite auto-floated, or a leftover layout copy) — combo
	 * members are never individually tagged in the core model ({@link #untagComboMembersOnce}), and a deliberately
	 * promoted item belongs to a group with no cell, so its base isn't in {@code memberBases}. Client thread.
	 */
	private boolean applyComboCellsToCore(net.runelite.client.plugins.banktags.tabs.Layout core,
			Map<Integer, Integer> cellToWinner, Set<Integer> memberBases) {
		boolean changed = false;
		int[] l = core.getLayout();
		for (int pos = 0; pos < l.length; pos++) {
			int id = l[pos];
			if (id > 0 && !cellToWinner.containsKey(pos) && memberBases.contains(comboBaseOf(id))) {
				core.removeItemAtPos(pos);
				changed = true;
			}
		}
		for (Map.Entry<Integer, Integer> e : cellToWinner.entrySet()) {
			if (core.getItemAtPos(e.getKey()) != e.getValue()) {
				core.setItemAtPos(e.getValue(), e.getKey());
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Makes each combo cell's stored index FOLLOW its winner after RuneLite moves it (a drag in a core tab is
	 * handled by RuneLite's own {@code dragCompleteHandler}, which swaps the live layout but knows nothing about
	 * our cells). Run after the build: if a cell's winner is no longer at the cell's index but sits elsewhere in
	 * the layout, move the cell's index there so the box and item stay together (and the next maintain pins it
	 * in place instead of snapping it back). Client thread. Returns whether any cell moved.
	 */
	private boolean reconcileComboCoreCells(String hostTag) {
		if (!isVanillaLayoutEnabled(LayoutableThing.bankTag(hostTag))) {
			return false;
		}
		List<com.banktaglayouts.combo.ComboSlots.Slot> slots = com.banktaglayouts.combo.ComboSlots.read(configManager, hostTag);
		if (slots.isEmpty()) {
			return false;
		}
		net.runelite.client.plugins.banktags.tabs.Layout core = coreLayoutFor(hostTag);
		if (core == null) {
			return false;
		}
		int[] l = core.getLayout();
		boolean changed = false;
		Set<Integer> claimed = new HashSet<>();
		for (com.banktaglayouts.combo.ComboSlots.Slot s : slots) {
			int winner = comboResolver.resolveWinner(s.getGroup());
			if (winner <= 0) {
				continue;
			}
			int idx = s.getIndex();
			if (idx >= 0 && idx < l.length && l[idx] == winner) {
				claimed.add(idx);
				continue; // still at its cell — nothing moved
			}
			int found = -1;
			for (int pos = 0; pos < l.length; pos++) {
				if (l[pos] == winner && !claimed.contains(pos)) {
					found = pos;
					break;
				}
			}
			if (found >= 0 && found != idx) {
				s.setIndex(found);
				claimed.add(found);
				changed = true;
			}
		}
		if (changed) {
			com.banktaglayouts.combo.ComboSlots.write(configManager, hostTag, slots);
			invalidateComboCellGroupsCache();
		}
		return changed;
	}

	// Host tags whose combo-member items have already been untagged this session.
	// Concurrent set: add on the client thread (untagComboMembersOnce) vs clear on the EDT (startUp).
	private final Set<String> comboMembersUntagged = ConcurrentHashMap.newKeySet();

	/**
	 * One-shot-per-session cleanup: in the core model a combo cell's membership comes entirely from the
	 * {@link com.banktaglayouts.combo.ComboBankTag}, so NO member of the tab's combos should be individually
	 * tagged into the host tab. A stray tag makes RuneLite pull the item in and float it once its cell's winner
	 * rotates or the cell moves — and untagging only the recorded winner ids (position-keyed, often stale) misses
	 * members that rotated. So this untags EVERY currently-tagged member of the tab's combos (robust to position,
	 * by item id) and drops the now-unused winner map. Deferred a tick — tag mutation during the build crashes
	 * the client. Idempotent: once the tags are gone from config, later sessions find nothing to do.
	 */
	private void untagComboMembersOnce(String hostTag, Set<Integer> memberBases) {
		if (!comboMembersUntagged.add(hostTag)) {
			return;
		}
		List<Integer> toUntag = new ArrayList<>();
		if (!memberBases.isEmpty()) {
			for (int id : tagManager.getItemsForTag(Text.standardize(hostTag))) {
				if (memberBases.contains(comboBaseOf(Math.abs(id)))) {
					toUntag.add(id);
				}
			}
		}
		Map<Integer, Integer> winnerMap = readComboWinnerMap(hostTag);
		if (toUntag.isEmpty() && winnerMap.isEmpty()) {
			return;
		}
		clientThread.invokeLater(() -> {
			for (int id : toUntag) {
				copyPaste.removeTag(id, hostTag); // removes both the non-variation and variation tag forms
			}
			if (!winnerMap.isEmpty()) {
				writeComboWinnerMap(hostTag, new HashMap<>());
			}
			bankSearch.layoutBank();
		});
	}

	// ---- combo winner display + tags ----
	// Per-tab map of {combo cell index → the winner item id currently shown there}. The RENDER reads this
	// stored map (not a fresh live resolve) so the shown item is always consistent with the tag state — that
	// prevents a 1-frame ghost flash (and the old winner floating to the top-left) while the deferred tag
	// sync catches up. Owned winners are tagged (so the cell is a withdrawable tab member); not-owned winners
	// stay untagged and draw as a faded fake item. comboslots stays index:group.
	public static final String COMBO_WINNER_TAGS_PREFIX = "combowinners_";

	private Map<Integer, Integer> readComboWinnerMap(String hostTag) {
		return com.banktaglayouts.combo.ComboCsv.readIntMap(configManager, COMBO_WINNER_TAGS_PREFIX + hostTag);
	}

	private void writeComboWinnerMap(String hostTag, Map<Integer, Integer> map) {
		com.banktaglayouts.combo.ComboCsv.writeIntMap(configManager, COMBO_WINNER_TAGS_PREFIX + hostTag, map);
	}

	/** What each combo cell SHOULD display right now: cell index → live-resolved winner (owned item or ghost). */
	private Map<Integer, Integer> computeDesiredComboWinnerMap(LayoutableThing layoutable) {
		Map<Integer, Integer> map = new HashMap<>();
		for (com.banktaglayouts.combo.ComboSlots.Slot slot : com.banktaglayouts.combo.ComboSlots.read(configManager, layoutable.name)) {
			int w = comboResolver.resolveWinner(slot.getGroup());
			if (w > 0) {
				map.put(slot.getIndex(), w);
			}
		}
		return map;
	}

	private boolean comboTagSyncInProgress = false;

	/**
	 * Cheap per-build check: if the displayed-winner map differs from what each cell should show, schedule the
	 * tag/untag + map update for the next tick (mutating tags DURING the bank build crashes the client).
	 */
	private void syncComboWinnerTags(LayoutableThing layoutable) {
		if (comboTagSyncInProgress || layoutable == null || !layoutable.isBankTab()) {
			return;
		}
		// CORE tabs don't use the winner-map/tagging machinery — RuneLite renders their cells from the layout
		// we maintain in maintainComboCoreTab (driven off onItemContainerChanged). Nothing to sync here.
		if (isVanillaLayoutEnabled(layoutable)) {
			return;
		}
		List<com.banktaglayouts.combo.ComboSlots.Slot> slots =
			com.banktaglayouts.combo.ComboSlots.read(configManager, layoutable.name);
		Map<Integer, Integer> stored = readComboWinnerMap(layoutable.name);
		if (slots.isEmpty() && stored.isEmpty()) {
			return;
		}
		if (computeDesiredComboWinnerMap(layoutable).equals(stored)) {
			return;
		}
		comboTagSyncInProgress = true;
		clientThread.invokeLater(() -> {
			try {
				doComboWinnerTagSync(layoutable);
			}
			finally {
				comboTagSyncInProgress = false;
			}
		});
	}

	/** Applies the deferred winner update: tag the now-shown owned winners, untag the ones that left, store the map. */
	private void doComboWinnerTagSync(LayoutableThing layoutable) {
		String hostTag = layoutable.name;
		Map<Integer, Integer> desired = computeDesiredComboWinnerMap(layoutable);
		Map<Integer, Integer> stored = readComboWinnerMap(hostTag);
		if (desired.equals(stored)) {
			return;
		}
		// Write the map FIRST: the render is driven by it, and a tag change below can trigger a relayout
		// mid-sync. If the map still showed the old winner then, the freshly-tagged new winner wouldn't be
		// injected at its cell and would get auto-placed at the first empty slot (and persisted).
		writeComboWinnerMap(hostTag, desired);
		Set<Integer> desiredWinners = new HashSet<>(desired.values());
		Set<Integer> storedWinners = new HashSet<>(stored.values());
		// Untag winners no longer shown by any cell.
		for (int id : storedWinners) {
			if (!desiredWinners.contains(id)) {
				tagManager.removeTag(id, hostTag);
				copyPaste.removeTag(id, hostTag);
			}
		}
		// Tag currently-shown OWNED winners; ensure a now-ghost winner is untagged.
		for (int id : desiredWinners) {
			if (comboResolver.isOwnedReal(id)) {
				tagManager.addTag(id, hostTag, false);
			}
			else {
				tagManager.removeTag(id, hostTag);
				copyPaste.removeTag(id, hostTag);
			}
		}
		// CORE tabs never reach here (syncComboWinnerTags returns early for them); this path is hub-only.
		bankSearch.layoutBank();
	}

	/**
	 * For the given host tab, maps each combo cell's layout index → its combo group name. Cached per host tag
	 * (this is a render hot path); the cache is cleared by {@link #invalidateComboCellGroupsCache()} on every
	 * combo-slot mutation. The returned map is the shared cached instance — callers must only READ it.
	 */
	public Map<Integer, String> getComboCellGroups(LayoutableThing layoutable) {
		if (layoutable == null || !layoutable.isBankTab()) {
			return Collections.emptyMap();
		}
		Map<String, Map<Integer, String>> cache = comboCellGroupsCache;
		Map<Integer, String> cached = cache.get(layoutable.name);
		if (cached != null) {
			return cached;
		}
		Map<Integer, String> result = new HashMap<>();
		for (com.banktaglayouts.combo.ComboSlots.Slot slot : com.banktaglayouts.combo.ComboSlots.read(configManager, layoutable.name)) {
			result.put(slot.getIndex(), slot.getGroup());
		}
		cache.put(layoutable.name, result);
		return result;
	}

	/** Drops the cached {@link #getComboCellGroups} maps. Called on every combo-slot mutation in this file. */
	private void invalidateComboCellGroupsCache() {
		comboCellGroupsCache.clear();
	}

	/** The combo group whose current (live-resolved) winner equals the given item id in the host tab, or null. */
	public String getComboGroupForItem(LayoutableThing layoutable, int itemId) {
		if (layoutable == null || !layoutable.isBankTab() || itemId <= 0) {
			return null;
		}
		for (com.banktaglayouts.combo.ComboSlots.Slot slot : com.banktaglayouts.combo.ComboSlots.read(configManager, layoutable.name)) {
			if (comboResolver.resolveWinner(slot.getGroup()) == itemId) {
				return slot.getGroup();
			}
		}
		return null;
	}

	/** First grid index that's empty in the layout AND not already occupied by another combo cell. */
	private int firstEmptyIndexAvoidingCombos(Layout layout, List<com.banktaglayouts.combo.ComboSlots.Slot> comboSlots) {
		Set<Integer> comboIndices = new HashSet<>();
		for (com.banktaglayouts.combo.ComboSlots.Slot s : comboSlots) {
			comboIndices.add(s.getIndex());
		}
		for (int i = 0; ; i++) {
			if (layout.getItemAtIndex(i) <= 0 && !comboIndices.contains(i)) {
				return i;
			}
		}
	}

	/** First position empty in the CORE layout AND not occupied by another combo cell. */
	private int firstEmptyCorePos(net.runelite.client.plugins.banktags.tabs.Layout core, List<com.banktaglayouts.combo.ComboSlots.Slot> comboSlots) {
		Set<Integer> comboIndices = new HashSet<>();
		for (com.banktaglayouts.combo.ComboSlots.Slot s : comboSlots) {
			comboIndices.add(s.getIndex());
		}
		int[] l = core != null ? core.getLayout() : new int[0];
		for (int i = 0; ; i++) {
			boolean empty = i >= l.length || l[i] <= 0;
			if (empty && !comboIndices.contains(i)) {
				return i;
			}
		}
	}

	/**
	 * HUB tabs only: tags + records a combo cell's current owned winner immediately (safe — callers run off the
	 * bank build) so the cell resolves to the real item at once instead of waiting on the deferred sync. (CORE
	 * tabs go through maintainComboCoreTab instead.)
	 */
	private void placeComboWinnerNow(String hostTag, int cellIndex, String comboGroup) {
		int winner = comboResolver.resolveWinner(comboGroup);
		Map<Integer, Integer> winnerMap = readComboWinnerMap(hostTag);
		if (winner > 0) {
			winnerMap.put(cellIndex, winner);
			if (comboResolver.isOwnedReal(winner)) {
				tagManager.addTag(winner, hostTag, false);
			}
		}
		else {
			winnerMap.remove(cellIndex);
		}
		writeComboWinnerMap(hostTag, winnerMap);
	}

	/** The single per-group color (used for both the box and the item name), from the group's JSON. */
	public java.awt.Color getComboColor(String comboGroup) {
		Map<String, Integer> colors = comboColorCache;
		if (colors == null) {
			colors = new HashMap<>();
			for (com.banktaglayouts.combo.ComboGroup g : com.banktaglayouts.combo.ComboStore.all(configManager, gson)) {
				colors.put(g.name, g.color);
			}
			comboColorCache = colors;
			comboColorObjCache = new ConcurrentHashMap<>(); // rebuilt alongside comboColorCache
		}
		// Memoize the Color instance so the overlay (per box, per frame) doesn't allocate a new one each paint.
		Map<String, java.awt.Color> objCache = comboColorObjCache;
		java.awt.Color cached = objCache.get(comboGroup);
		if (cached != null) {
			return cached;
		}
		Integer c = colors.get(comboGroup);
		java.awt.Color color = new java.awt.Color(c != null ? c : 0xFFFF00);
		objCache.put(comboGroup, color);
		return color;
	}

	/** Embeds a combo group as a smart cell in the currently-open bank tag tab. Called from the side panel. */
	public void addComboToOpenTab(String comboGroup) {
		clientThread.invokeLater(() -> {
			String hostTag = tabInterface.getActiveTag();
			if (hostTag == null || hostTag.isEmpty() || hostTag.equals("tagtabs") || hostTag.startsWith("_invsetup_")) {
				chatMessage("Open a bank tag tab first, then add the combo to it.");
				return;
			}

			LayoutableThing layoutable = LayoutableThing.bankTag(hostTag);
			boolean core = isVanillaLayoutEnabled(layoutable);
			boolean wasHost = comboBankTags.containsKey(hostTag);
			Layout layout = core ? null : getBankOrderNonPreview(layoutable);
			if (!core && layout == null) {
				layout = Layout.emptyLayout();
			}

			// First behave like "Replace in tab": consolidate the combo if it's already (even loosely) present.
			// Only when it isn't in the tab at all (ABSENT) do we fall through and add a fresh cell.
			ComboReplaceResult result = replaceComboIntoTab(hostTag, layout, comboGroup);
			if (result != ComboReplaceResult.ABSENT) {
				if (core) {
					if (!wasHost) {
						recaptureActiveComboCoreTab(); // capture the new ComboBankTag into the open tab's filter
					}
				}
				else {
					saveLayoutNonPreview(layoutable, layout);
				}
				bankSearch.layoutBank();
				chatMessage((result == ComboReplaceResult.ADDED ? "Replaced" : "Cleaned up") + " combo \"" + comboGroup + "\" in tab \"" + hostTag + "\".");
				return;
			}

			// Not in the tab → add a new smart cell at the first empty slot.
			List<com.banktaglayouts.combo.ComboSlots.Slot> slots = com.banktaglayouts.combo.ComboSlots.read(configManager, hostTag);
			int index;
			if (core) {
				index = firstEmptyCorePos(coreLayoutFor(hostTag), slots);
			}
			else {
				index = firstEmptyIndexAvoidingCombos(layout, slots);
				saveLayoutNonPreview(layoutable, layout); // ensure the tab's hub layout is enabled so the cell renders
			}
			slots.add(new com.banktaglayouts.combo.ComboSlots.Slot(index, comboGroup));
			com.banktaglayouts.combo.ComboSlots.write(configManager, hostTag, slots);
			invalidateComboCellGroupsCache();
			if (core) {
				ensureComboBankTagRegistered(hostTag); // this tab is now a combo host — give it a ComboBankTag
				maintainComboCoreTab(hostTag);         // membership + pin the new cell's winner
				if (!wasHost) {
					recaptureActiveComboCoreTab(); // capture the new ComboBankTag into the open tab's filter
				}
			}
			else {
				placeComboWinnerNow(hostTag, index, comboGroup);
			}
			bankSearch.layoutBank();
			chatMessage("Added combo \"" + comboGroup + "\" to tab \"" + hostTag + "\".");
		});
	}

	private void applyCustomBankTagItemPositions() {
		applyCustomBankTagItemPositions(true, false);
	}

	private void applyCustomBankTagItemPositions(boolean setScroll, boolean doNotScrollDown) {
		fakeItems.clear();

		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (layoutable == null) {
			return;
		}

		log.debug("applyCustomBankTagItemPositions: " + layoutable);

		indexToWidget.clear();

		Layout layout = getBankOrder(layoutable);
		if (layout == null) {
			return; // layout not enabled.
		}

		List<Widget> bankItems = Arrays.stream(client.getWidget(ComponentID.BANK_ITEM_CONTAINER).getDynamicChildren())
				.filter(bankItem -> !bankItem.isHidden() && bankItem.getItemId() >= 0)
				.collect(Collectors.toList());

		if (!isShowingPreview()) { // I don't want to clean layout items when displaying a preview. This could result in some layout placeholders being auto-removed due to not being in the tab.
			cleanItemsNotInBankTag(layout, layoutable);
		}

		// COMBO CELLS: inject each cell's live winner into the IN-MEMORY layout at the cell index (never
		// persisted — stripped before saveLayout below). For an OWNED winner this tells assignItemPositions
		// to place the real (tagged, via syncComboWinnerTags) item widget there → native Withdraw etc. For a
		// not-owned winner there's no widget, so it becomes the faded ghost FakeItem.
		List<Integer> injectedComboIndices = new ArrayList<>();
		if (!isShowingPreview() && layoutable.isBankTab()) {
			// Use the STORED display winner per cell (kept in lockstep with the tags by the deferred sync) so
			// the render never shows an untagged/ghost winner mid-transition. Fresh cells with no stored entry
			// fall back to a live resolve until the next sync populates the map.
			Map<Integer, Integer> winnerMap = readComboWinnerMap(layoutable.name);
			for (Map.Entry<Integer, String> cell : getComboCellGroups(layoutable).entrySet()) {
				int index = cell.getKey();
				int stored = winnerMap.getOrDefault(index, -1);
				// Only resolve live (a bank lookup) when there's no stored winner to use.
				int winner = stored > 0 ? stored : comboResolver.resolveWinner(cell.getValue());
				int occupant = layout.getItemAtIndex(index);
				if (winner > 0 && occupant <= 0) {
					layout.putItem(winner, index);
					injectedComboIndices.add(index);
				}
			}
		}

		indexToWidget.putAll(assignItemPositions(layout, bankItems));
		moveDuplicateItem();
		updateFakeItems(layout);

		for (Widget bankItem : bankItems) {
			bankItem.setOnDragCompleteListener((JavaScriptCallback) (ev) -> customBankTagOrderInsert(layoutable, ev.getSource()));
		}

		setItemPositions(indexToWidget);

		// Necessary as applyCustomBankTagItemPositions can be called after an item's layout position is changed. This doesn't fire BANKMAIN_BUILD, so our PreScriptFired subscriber doesn't change the scrollbar height, and the item's movement can change the height of the layout if it is moved below the last row or if it is the last item in the layout and is alone on its own row and was moved upwards.
		int maxIndex = layout.getAllUsedIndexes().stream().max(Integer::compare).orElse(0);
		int height = getYForIndex(maxIndex) + BANK_ITEM_HEIGHT + 8;
		if (setScroll && layoutable.equals(lastLayoutable) && height != lastHeight)
		{
			resizeBankContainerScrollbar(height, lastHeight, doNotScrollDown);
		}
		lastHeight = height;

		// Strip the transient combo-winner entries so the combo cell is NEVER persisted into layout_.
		for (int idx : injectedComboIndices) {
			layout.clearIndex(idx);
		}
		saveLayout(layoutable, layout);
		log.debug("saved tag " + layoutable);
	}

	/**
	 * Generates a map of widgets to the bank indexes where they should show up in the laid-out tag. Does not update fake items.
	 */
	Map<Integer, Widget> assignItemPositions(Layout layout, List<Widget> bankItems)
	{
		Map<Integer, Widget> indexToWidget = new HashMap<>();
		assignVariantItemPositions(layout, bankItems, indexToWidget);
		// TODO check if the existance of this method is just a performance boost.
		assignNonVariantItemPositions(layout, bankItems, indexToWidget);
		return indexToWidget;
	}

	private void updateFakeItems(Layout layout)
	{
		fakeItems = calculateFakeItems(layout, indexToWidget);
	}

	// TODO this is n^2. There are multiple places I think where I do such an operation, so doing something about this would be nice.
	Set<FakeItem> calculateFakeItems(Layout layout, Map<Integer, Widget> indexToWidget)
	{
		Set<FakeItem> fakeItems = new HashSet<>();
		for (Map.Entry<Integer, Integer> entry : layout.allPairs()) {
			Integer index = entry.getKey();
			if (indexToWidget.containsKey(index)) continue;

			int itemId = entry.getValue();
			Optional<Widget> any = layout.allPairs().stream()
					.filter(e -> e.getValue() == itemId)
					.map(e -> indexToWidget.get(e.getKey()))
					.filter(widget -> widget != null)
					.findAny();

			boolean isLayoutPlaceholder = !any.isPresent();
			int quantity = any.isPresent() ? any.get().getItemQuantity() : -1;
			int fakeItemItemId = any.isPresent() ? any.get().getItemId() : itemId;
//			fakeItems.add(new FakeItem(index, getNonPlaceholderId(fakeItemItemId), isLayoutPlaceholder, quantity));
			fakeItems.add(new FakeItem(index, fakeItemItemId, isLayoutPlaceholder, quantity));
		}
		return fakeItems;
	}

	LayoutableThing getCurrentLayoutableThing() {
		String activeTag = tabInterface.getActiveTag();

		// This completely disables Inventory Setups integration.
		// Inventory Setups now uses core Bank Tags.
		if (activeTag != null && activeTag.startsWith("_invsetup_")) return null;

		boolean isBankTag = activeTag != null && !activeTag.equals("tagtabs");
		if (!isBankTag && !(inventorySetup != null && config.useWithInventorySetups())) {
			return null;
		}
		String name = isBankTag ? activeTag : inventorySetup;
		return new LayoutableThing(name, isBankTag);
	}

	private boolean tutorialMessageShown = false;
	private boolean tutorialMessage() {
		if (!config.tutorialMessage()) return false;

		for (String key : configManager.getConfigurationKeys(CONFIG_GROUP)) {
			if (key.startsWith(CONFIG_GROUP + "." + LAYOUT_CONFIG_KEY_PREFIX)) { // They probably already know what to do if they have a key like this set.
				return false;
			}
		}

		if (!tutorialMessageShown) {
			tutorialMessageShown = true;
			chatMessage("If you want to use Bank Tag Layouts, enable it for the tab by right clicking the tag tab and clicking \"Enable layout\".");
			chatMessage("To disable this message, to go the Bank Tag Layouts config and disable \"Layout enable tutorial message\".");
			return true;
		}
		return false;
	}

	private void assignNonVariantItemPositions(Layout layout, List<Widget> bankItems, Map<Integer, Widget> indexToWidget) {
		for (Widget bankItem : bankItems) {
			int itemId = bankItem.getItemId();

			int nonPlaceholderId = getNonPlaceholderId(itemId);

			if (!itemShouldBeTreatedAsHavingVariants(nonPlaceholderId)) {
//				log.debug("\tassigning position for " + itemName(itemId) + itemId + ": ");

				Integer indexForItem = layout.getIndexForItem(itemId);
				if (indexForItem == -1) {
					// swap the item with its placeholder (or vice versa) and try again.
					int otherItemId = switchPlaceholderId(itemId);
					indexForItem = layout.getIndexForItem(otherItemId);
				}

				if (indexForItem == -1) {
					// The item is not in the layout.
					indexForItem = layout.getFirstEmptyIndex();
					layout.putItem(itemId, indexForItem);
				}
				indexToWidget.put(indexForItem, bankItem);
			}
		}
	}

	public Set<FakeItem> fakeItems = new HashSet<>();

	@Override
	public MouseEvent mouseClicked(MouseEvent mouseEvent) {
		return mouseEvent;
	}

	public volatile int draggedItemIndex = -1; // Used for fake items only, not real items.
	public int dragStartX = 0;
	public int dragStartY = 0;
	public int dragStartScroll = 0;

	// Mouse position + scroll at the last left-button press, in canvas space. Used by FakeItemOverlay to make a
	// combo box follow a RuneLite-dragged combo cell in CORE tabs (where RuneLite, not this plugin, owns the drag).
	public int comboDragPressX = 0;
	public int comboDragPressY = 0;
	public int comboDragPressScroll = 0;

	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent) {
		if (mouseEvent.getButton() == MouseEvent.BUTTON1) {
			comboDragPressX = mouseEvent.getX();
			comboDragPressY = mouseEvent.getY();
			Widget items = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
			comboDragPressScroll = items != null ? items.getScrollY() : 0;
		}
		return mouseEvent;
	}
	@Override public MouseEvent mouseReleased(MouseEvent mouseEvent) {
		if (mouseEvent.getButton() != MouseEvent.BUTTON1) return mouseEvent;
		clientThread.invokeLater(() -> {
			if (draggedItemIndex == -1) return;

			int draggedOnIndex = getMouseIndexNoLowerLimit();
			if (draggedOnIndex != -1 && antiDrag.mayDrag()) {
				customBankTagOrderInsert(getCurrentLayoutableThing(), draggedItemIndex, draggedOnIndex);
			}
			antiDrag.endDrag();
			draggedItemIndex = -1;
		});
		return mouseEvent;
	}
	@Override public MouseEvent mouseEntered(MouseEvent mouseEvent) { return mouseEvent; }
	@Override public MouseEvent mouseExited(MouseEvent mouseEvent) { return mouseEvent; }
	@Override public MouseEvent mouseDragged(MouseEvent mouseEvent) { return mouseEvent; }
	@Override public MouseEvent mouseMoved(MouseEvent mouseEvent) { return mouseEvent; }

	boolean addItemKeybind = false;
	boolean addRowKeybind = false;
	boolean removeRowKeybind = false;
	@Override
	public void keyPressed(KeyEvent e) {
		if (config.addItemKeybind().matches(e)) addItemKeybind = true;
		if (config.addRowBelowKeybind().matches(e)) addRowKeybind = true;
		if (config.removeRowKeybind().matches(e)) removeRowKeybind = true;
	}

	@Override public void keyReleased(KeyEvent e) {
		if (config.addItemKeybind().matches(e)) addItemKeybind = false;
		if (config.addRowBelowKeybind().matches(e)) addRowKeybind = false;
		if (config.removeRowKeybind().matches(e)) removeRowKeybind = false;
	}

	@Override public void keyTyped(KeyEvent e) { }

	@Data
	public static class FakeItem {
		public final int index;
		public final int itemId;
		public final boolean layoutPlaceholder;
		public final int quantity;
	}

	/**
	 * Used to run code in onMenuEntryAdded only once per client tick. Client tick events occur after MenuEntryAdded,
	 * so this flag is set in MenuEntryAdded and reset in ClientTick.
	 */
	boolean sawMenuEntryAddedThisClientTick = false;
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded menuEntryAdded)
	{
		if (!bankOpenButNotOnOptionsMenu()) return;

		if (!sawMenuEntryAddedThisClientTick) {
			sawMenuEntryAddedThisClientTick = true;

			// If you move the items when you're dragging an item over its duplicates, undesirable behavior occurs.
			if (client.getDraggedWidget() == null)
			{
				boolean movedItemWidget = moveDuplicateItem();
				if (movedItemWidget)
				{
					updateFakeItems(getBankOrder(getCurrentLayoutableThing()));
					setItemPositions(indexToWidget);
				}
			}
		}

		addFakeItemMenuEntries(menuEntryAdded);
		addDuplicateItemMenuEntries(menuEntryAdded);
		colorComboItemName(menuEntryAdded);
	}

	/**
	 * For a combo "smart cell" under the cursor: drop RuneLite's per-item tag/layout entries (they would untag,
	 * unlayout, duplicate or edit-tags the surfaced item — all wrong for a cell) and add the combo actions:
	 * remove the cell, or replace the cell with the item it currently shows. The combo actions sit just under
	 * the Withdraw options so Withdraw stays the default left-click.
	 *
	 * <p>Done in {@link MenuOpened} rather than {@link MenuEntryAdded} because RuneLite's own entries are created
	 * (by TabInterface/BankTagsPlugin reacting to the game's Examine entry) without firing MenuEntryAdded — so
	 * they're only reliably present, and reliably recolorable, once the whole menu is open.
	 */
	private boolean customizeComboCellMenu(MenuOpened event) {
		Widget bankContainer = client.getWidget(ComponentID.BANK_CONTAINER);
		if (bankContainer == null || bankContainer.isHidden()) {
			return false;
		}
		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (layoutable == null || !layoutable.isBankTab()) {
			return false;
		}
		boolean core = isVanillaLayoutEnabled(layoutable);
		if (!core && !hasLayoutEnabled(layoutable)) {
			return false; // hub cells need the hub layout on; core cells are rendered by RuneLite
		}
		int index = getMouseIndex();
		if (index == -1) {
			return false;
		}
		String group = getComboCellGroups(layoutable).get(index);
		if (group == null) {
			return false; // not a combo cell
		}

		// A hub ghost (unowned) builds its own placeholder-style menu in addComboGhostMenuEntries — leave it.
		if (!core) {
			int ghostWinner = comboResolver.resolveWinner(group);
			if (ghostWinner > 0 && !comboResolver.isOwnedReal(ghostWinner)) {
				return true;
			}
		}

		// Drop RuneLite's per-item entries that would act on the surfaced item rather than the cell:
		// "Remove-tag (<tag>)", "Edit-tags", "Duplicate-item" (all layouts) and the core ghost's "Remove-layout".
		MenuEntry[] entries = client.getMenuEntries();
		List<MenuEntry> kept = new ArrayList<>(entries.length);
		for (MenuEntry e : entries) {
			String opt = e.getOption();
			if (opt != null && (opt.startsWith("Remove-tag")
				|| opt.startsWith("Edit-tags")
				|| opt.startsWith("Duplicate-item")
				|| opt.equals(OPEN_COMBO) // the hover-relabeled placeholder entry; re-added cleanly below
				|| (core && opt.startsWith(REMOVE_FROM_LAYOUT_MENU_OPTION)))) {
				continue;
			}
			kept.add(e);
		}

		// Insert the combo actions just BELOW the Withdraw block (index 0 is the bottom "Cancel" entry, so the
		// lowest-indexed "Withdraw" entry is the bottom of that block). Falls back to the top if there's no
		// Withdraw entry (e.g. a ghost cell). Order ends up Withdraw…, Remove combo, Replace combo, Examine….
		int insertAt = kept.size();
		for (int i = 0; i < kept.size(); i++) {
			String opt = kept.get(i).getOption();
			if (opt != null && opt.startsWith("Withdraw")) {
				insertAt = i;
				break;
			}
		}
		client.setMenuEntries(kept.toArray(new MenuEntry[0]));

		int at = insertAt;
		int winner = comboResolver.resolveWinner(group);
		if (winner > 0) {
			client.createMenuEntry(at++)
				.setOption(REPLACE_COMBO_WITH_ITEM)
				.setType(MenuAction.RUNELITE_OVERLAY)
				.setTarget(ColorUtil.wrapWithColorTag(itemName(winner), itemTooltipColor))
				.setParam0(index);
		}
		client.createMenuEntry(at++)
			.setOption(REMOVE_COMBO)
			.setType(MenuAction.RUNELITE_OVERLAY)
			.setTarget(ColorUtil.wrapWithColorTag(comboDisplayName(group), itemTooltipColor))
			.setParam0(index);
		// "Edit <name>" on top (above Withdraw is avoided — for owned cells this lands below it).
		client.createMenuEntry(at)
			.setOption(OPEN_COMBO)
			.setType(MenuAction.RUNELITE_OVERLAY)
			.setTarget(ColorUtil.wrapWithColorTag(comboDisplayName(group), itemTooltipColor))
			.setParam0(index)
			.onClick(me -> openComboInPanel(group));

		// Recolor the cell's winner entries (Withdraw/Examine/…) to the group color over the WHOLE open menu —
		// colorComboItemName runs per MenuEntryAdded and can miss the top (default) entry; doing it here covers it.
		if (config.comboColorName() && winner > 0) {
			java.awt.Color color = getComboColor(group);
			for (MenuEntry e : client.getMenuEntries()) {
				if (e.getItemId() == winner) {
					String n = Text.removeTags(e.getTarget());
					if (!n.isEmpty()) {
						e.setTarget(ColorUtil.wrapWithColorTag(n, color));
					}
				}
			}
		}
		return true;
	}

	/** A combo group's display name with the storage brackets stripped (e.g. "[MeleeLegs]" → "MeleeLegs"). */
	private static String comboDisplayName(String group) {
		return (group.length() >= 2 && group.startsWith("[") && group.endsWith("]"))
			? group.substring(1, group.length() - 1) : group;
	}

	/** Removes the combo cell at the given layout index from the currently-open tab. */
	private void removeComboCell(int index) {
		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (layoutable == null || !layoutable.isBankTab()) {
			return;
		}
		List<com.banktaglayouts.combo.ComboSlots.Slot> slots =
			com.banktaglayouts.combo.ComboSlots.read(configManager, layoutable.name);
		if (slots.removeIf(s -> s.getIndex() == index)) {
			com.banktaglayouts.combo.ComboSlots.write(configManager, layoutable.name, slots);
			invalidateComboCellGroupsCache();
			if (isVanillaLayoutEnabled(layoutable)) {
				// Clear the removed cell's pinned winner from the core layout, then refresh the rest.
				net.runelite.client.plugins.banktags.tabs.Layout core = coreLayoutFor(layoutable.name);
				if (core != null) {
					core.removeItemAtPos(index);
					layoutManager.saveLayout(core);
				}
				maintainComboCoreTab(layoutable.name);
			}
			bankSearch.layoutBank();
		}
	}

	/** Removes the combo cell and tags the item it currently shows into the tab at the cell's spot. */
	private void replaceComboCellWithItem(int index) {
		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (layoutable == null || !layoutable.isBankTab()) {
			return;
		}
		List<com.banktaglayouts.combo.ComboSlots.Slot> slots =
			com.banktaglayouts.combo.ComboSlots.read(configManager, layoutable.name);
		com.banktaglayouts.combo.ComboSlots.Slot cell = slots.stream()
			.filter(s -> s.getIndex() == index).findFirst().orElse(null);
		if (cell == null) {
			return;
		}
		int winner = comboResolver.resolveWinner(cell.getGroup());
		boolean core = isVanillaLayoutEnabled(layoutable);
		slots.removeIf(s -> s.getIndex() == index);
		com.banktaglayouts.combo.ComboSlots.write(configManager, layoutable.name, slots);
		invalidateComboCellGroupsCache();
		if (winner > 0) {
			// Promote the winner to a normal tagged+laid-out item, and drop the cell's entry from the winner
			// map so the sync won't untag it now that there's no cell.
			Map<Integer, Integer> winnerMap = readComboWinnerMap(layoutable.name);
			if (winnerMap.remove(index) != null) {
				writeComboWinnerMap(layoutable.name, winnerMap);
			}
			tagManager.addTag(winner, layoutable.name, false);
			if (core) {
				// Pin it in the core layout where the cell was. It's now individually tagged, so the maintain
				// scrub (which only removes UNtagged member floats) leaves it alone.
				net.runelite.client.plugins.banktags.tabs.Layout coreLayout = coreLayoutFor(layoutable.name);
				if (coreLayout != null) {
					coreLayout.setItemAtPos(winner, index);
					layoutManager.saveLayout(coreLayout);
				}
			}
			else {
				Layout layout = getBankOrderNonPreview(layoutable);
				if (layout == null) {
					layout = Layout.emptyLayout();
				}
				layout.putItem(winner, index);
				saveLayoutNonPreview(layoutable, layout);
			}
		}
		if (core) {
			maintainComboCoreTab(layoutable.name);
		}
		bankSearch.layoutBank();
	}

	/** Recolors a combo smart cell's item name (hover/menu target) using the group's per-tag color. */
	private void colorComboItemName(MenuEntryAdded event) {
		if (!config.comboColorName()) {
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		int itemId = entry.getItemId();
		String group = getComboGroupForItem(getCurrentLayoutableThing(), itemId);
		if (group == null) {
			return;
		}
		String name = Text.removeTags(entry.getTarget());
		if (!name.isEmpty()) {
			entry.setTarget(ColorUtil.wrapWithColorTag(name, getComboColor(group)));
		}
	}

	private boolean bankOpenButNotOnOptionsMenu() {
		Widget widget = client.getWidget(ComponentID.BANK_CONTAINER);
		if (widget == null || widget.isHidden()) return false;
		widget = client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.MENU_CONTAINER);
		return (widget == null || widget.isHidden());
	}

	@Subscribe
	public void onMenuOpened(MenuOpened e) {
		if (removeThisMenuEntry != null && removeThisMenuEntryStale == client.getGameCycle()) {
			try {
				client.getMenu().removeMenuEntry(removeThisMenuEntry);
			} catch (IllegalArgumentException ex) { /* do nothing lol */ }
			removeThisMenuEntry = null;
		}

		// Combo: if the cursor is over a combo cell, replace its menu with the combo actions and stop.
		if (customizeComboCellMenu(e)) return;

		if (!bankOpenButNotOnOptionsMenu()) return;
		Layout layout = getCurrentBankOrder();
		if (layout == null) return;
		if (config.shiftModifierForExtraBankItemOptions() && !client.isKeyPressed(KeyCode.KC_SHIFT)) return;
		for (MenuEntry menuEntry : client.getMenu().getMenuEntries()) {
			if (menuEntry.getWidget() != null) return;
		}

		int index = getMouseIndex();
		if (config.showRowMenuEntries() && (index == -1 || layout.getItemAtIndex(index) == -1)) {
			insertRow(layout);
			addRemoveRow(layout);
		}

		int paddedIndex = getMouseIndexPadded();
		if (config.showAddItemEntries() && paddedIndex != -1 && layout.getItemAtIndex(paddedIndex) == -1) {
			client.getMenu().createMenuEntry(-1).setOption("Add item").onClick(me -> addItem(paddedIndex));
		}
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort e) {
		// Combo: relabel a core-tab ghost cell's closed-menu hover entry to "Edit <name>".
		relabelComboGhostHover();

		if (!bankOpenButNotOnOptionsMenu()) return;
		Layout layout = getCurrentBankOrder();
		if (layout == null) return;

		if (addItemKeybind) {
			int paddedIndex = getMouseIndexPadded();
			if (paddedIndex != -1 && layout.getItemAtIndex(paddedIndex) == -1) {
				client.getMenu().createMenuEntry(-1).setOption("Add item").onClick(me -> addItem(paddedIndex));
				return;
			}
		}

		if (removeRowKeybind) {
			boolean added = addRemoveRow(layout);
			if (added) return;
		}

		if (addRowKeybind) {
			boolean added = insertRow(layout);
			if (added) return;
		}
	}

	@Inject ChatboxItemSearch chatboxItemSearch;
	private void addItem(int index) {
		chatboxItemSearch
			.tooltipText("Add to layout")
			.onItemSelected((itemId) ->
			{
				int finalId = itemManager.canonicalize(itemId);

				String tag = bankTagsService.getActiveTag();
				tagManager.addTag(finalId, tag, true);
				LayoutableThing layoutable = getCurrentLayoutableThing();
				if (isVanillaLayoutEnabled(layoutable)) return;
				Layout layout = getBankOrderNonPreview(layoutable);
				if (layout == null) return;

				layout.putItem(finalId, layout.getFirstEmptyIndex(index - 1));
				saveLayout(layoutable, layout);

				bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
			})
			.build();
	}

	private boolean insertRow(Layout layout) {
		int row = getMouseRow();
		if (row == -1) return false;
		if (layout.isEmpty()) return false;
		if (row >= layout.rowCount()) return false;

		client.getMenu().createMenuEntry(-1)
			.setOption("Insert empty row")
			.onClick(me -> addEmptyRow(row));
		return true;
	}

	private boolean addRemoveRow(Layout layout) {
		int row = getMouseRow();
		if (row == -1) return false;
		if (layout.isEmpty()) return false;
		if (row >= layout.rowCount() - 1) return false;
		if (!layout.isRowEmpty(row)) return false;

		client.getMenu().createMenuEntry(-1)
			.setOption("Remove row")
			.onClick(me -> removeEmptyRow(row));
		return true;
	}

	private void addEmptyRow(int row)
	{
		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (layoutable == null) return;

		Layout layout = getBankOrder(layoutable);
		if (layout == null) return;

		layout.insertBlankRow(row);
		saveLayout(layoutable, layout);

		applyCustomBankTagItemPositions(true, true);
	}

	private void removeEmptyRow(int row)
	{
		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (layoutable == null) return;

		Layout layout = getBankOrder(layoutable);
		if (layout == null) return;

		if (!layout.isRowEmpty(row)) return;

		layout.removeBlankRow(row);
		saveLayout(layoutable, layout);

		applyCustomBankTagItemPositions();
	}

	private void addTabInterfaceMenuEntries()
	{
		for (MenuEntry menuEntry : client.getMenuEntries()) {
			if (WidgetUtil.componentToInterface(menuEntry.getParam1()) == InterfaceID.BANK) {
				if ("View tag tab".equals(menuEntry.getOption())) {
					String bankTagName = Text.removeTags(menuEntry.getTarget()).replace("\u00a0"," ");
					if (bankTagName.length() > 0) {
						addTagTabMenuEntries(bankTagName);
					}
					return;
				} else if ("New tag tab".equals(menuEntry.getOption()) && config.whichPlugin() != CORE) {
					if (!config.showAutoLayoutButton()) {
						addEntry("", PREVIEW_AUTO_LAYOUT);
					}
					addEntry("", IMPORT_LAYOUT);
					return;
				}
			}
		}
	}

	private void addTagTabMenuEntries(String bankTagName) {
		WhichPlugin mode = config.whichPlugin();
		boolean isHub = hasLayoutEnabled(LayoutableThing.bankTag(bankTagName));
		if (!isHub && mode == CORE) return;

		MenuEntry[] menuEntries = client.getMenuEntries();
		int index = -1;
		for (int i = 0; i < menuEntries.length; i++) {
			String option = menuEntries[i].getOption();
			if ("Enable layout".equals(option) || "Disable layout".equals(option)) {
				index = i;
				break;
			}
		}
		if (index == -1) return; // shouldn't happen.

		boolean isCore = hasRuneliteLayout(bankTagName);
		boolean noLayout = !isHub && !isCore;

		boolean addBoth = mode == BOTH || (mode == CORE && isHub) || (mode == HUB && isCore);
		MenuEntry coreEnableLayoutEntry = menuEntries[index];
		if (addBoth || mode == CORE) { // add core enable/disable option
			if (isHub) {
				client.getMenu().createMenuEntry(index)
					.setOption("Convert to built-in layout")
					.setTarget(ColorUtil.wrapWithColorTag(bankTagName, itemTooltipColor))
					.setType(MenuAction.CC_OP)
					.onClick(me -> {
						convertTagToCore(bankTagName);
						bankTagsService.openBankTag(bankTagName, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
					});
				client.getMenu().removeMenuEntry(coreEnableLayoutEntry);
			} else {
				menuEntries[index].setOption(menuEntries[index].getOption() + " (built-in)");
			}
		} else {
			client.getMenu().removeMenuEntry(coreEnableLayoutEntry);
			index--;
		}
		if (addBoth || mode == HUB) { // add hub enable/disable option
			if (isCore) {
				client.getMenu().createMenuEntry(index)
					.setOption("Convert to hub layout")
					.setTarget(ColorUtil.wrapWithColorTag(bankTagName, itemTooltipColor))
					.setType(MenuAction.CC_OP)
					.onClick(me -> {
						convertTagToHub(bankTagName);
						bankTagsService.openBankTag(bankTagName, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
					});
			} else {
				String suffix = mode != HUB ? " (hub)" : "";
				client.getMenu().createMenuEntry(!addBoth ? 1 : index)
					.setOption((isHub ? DISABLE_LAYOUT : ENABLE_LAYOUT) + suffix)
					.setTarget(ColorUtil.wrapWithColorTag(bankTagName, itemTooltipColor))
					.setType(!addBoth ? MenuAction.RUNELITE : MenuAction.CC_OP)
					.onClick(me -> {
						if (isHub) {
							disableLayout(bankTagName);
						} else {
							enableLayout(LayoutableThing.bankTag(bankTagName));
						}
					});
			}
		}

		if (isHub) {
			client.createMenuEntry(!addBoth ? 2 : 1)
				.setOption(EXPORT_LAYOUT)
				.setTarget(ColorUtil.wrapWithColorTag(bankTagName, itemTooltipColor))
				.setType(MenuAction.RUNELITE);
		}
	}

	private void addEntry(String menuTarget, String menuOption) {
		client.createMenuEntry(1)
			.setOption(menuOption)
			.setTarget(ColorUtil.wrapWithColorTag(menuTarget, itemTooltipColor))
			.setType(MenuAction.RUNELITE);
	}

	/**
	 * Makes sure there is a real item under the mouse cursor if the mouse is over or near a duplicated item.
	 * @return true if an item widget was moved, false otherwise. If true, fake items should be updated and
	 * setItemPositions should be called, since this method does not do that.
	 */
	private boolean moveDuplicateItem()
	{
		if (getCurrentLayoutableThing() == null)
		{
			return false;
		}

		int mousePositionIndex = getMouseIndexPadded();
		Layout layout = getBankOrder(getCurrentLayoutableThing());
		if (layout == null) return false;
		int itemId = layout.getItemAtIndex(mousePositionIndex);

		if (itemId == -1)
		{
			return false;
		}

		int count = 0;
		List<Integer> indexes = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : layout.allPairs())
		{
			if (entry.getValue() == itemId) {
				count++;
				indexes.add(entry.getKey());
			}
		}
		if (count > 1) {
			for (Integer index : indexes)
			{
				if (indexToWidget.containsKey(index) && index != mousePositionIndex) {
					Widget widget = indexToWidget.get(index);
					indexToWidget.remove(index);
					indexToWidget.put(mousePositionIndex, widget);
					return true;
				}
			}
		}
		return false;
	}

	private void addDuplicateItemMenuEntries(MenuEntryAdded menuEntryAdded)
	{
		if (config.shiftModifierForExtraBankItemOptions() && !client.isKeyPressed(KeyCode.KC_SHIFT)) return;

		Layout layout = getCurrentBankOrder();
		if (layout == null) return;
		int index = getMouseIndex();
		if (index == -1) return;
		int itemIdAtIndex = layout.getItemAtIndex(index);

		if (itemIdAtIndex == -1) return;

		boolean isRealItem = indexToWidget.containsKey(index);
		if (!menuEntryAdded.getOption().equals("Examine") && isRealItem) return;

		boolean isLayoutPlaceholder = fakeItems.stream()
				.filter(fakeItem -> fakeItem.getIndex() == index && fakeItem.isLayoutPlaceholder()).findAny().isPresent();
		if (isLayoutPlaceholder) return;

		int itemCount = layout.countItemsWithId(itemIdAtIndex);
		if (itemCount > 1) {
			client.createMenuEntry(-1)
					.setOption(REMOVE_DUPLICATE_ITEM)
					.setTarget(ColorUtil.wrapWithColorTag(itemName(itemIdAtIndex), itemTooltipColor))
					.setType(MenuAction.RUNELITE_OVERLAY)
					.setParam0(index);
		}

		// An unowned combo ghost behaves like a bank placeholder (see addComboGhostMenuEntries); otherwise the
		// normal "Duplicate-item" default applies.
		LayoutableThing layoutable = getCurrentLayoutableThing();
		String comboGhostGroup = layoutable != null && layoutable.isBankTab() && !comboResolver.isOwnedReal(itemIdAtIndex)
				? getComboCellGroups(layoutable).get(index) : null;
		if (comboGhostGroup != null) {
			addComboGhostMenuEntries(index, comboGhostGroup);
		} else {
			client.createMenuEntry(-1)
					.setOption(DUPLICATE_ITEM)
					.setTarget(ColorUtil.wrapWithColorTag(itemName(itemIdAtIndex), itemTooltipColor))
					.setType(MenuAction.RUNELITE_OVERLAY)
					.setParam0(index);
		}

		if (!isRealItem) return; // layout placeholders already have "remove-layout" menu option which does the same thing as remove-duplicate-item.
	}

	private int removeThisMenuEntryStale = -1;
	private MenuEntry removeThisMenuEntry = null;

	/** Opens the Combo Tags side panel and shows the given combo's editor (the ghost's "Edit" action). */
	private void openComboInPanel(String comboGroup) {
		SwingUtilities.invokeLater(() -> {
			clientToolbar.openPanel(comboNavButton);
			comboPanel.openCombo(comboGroup);
		});
	}

	/**
	 * Builds an unowned combo ghost's menu like a bank placeholder: every entry is DEPRIORITIZED, so a
	 * left-click opens the menu instead of acting (RuneLite/the client turns the left-click into a right-click
	 * for low-priority defaults). The top entry (the hover text) is "Edit <name>", which opens this combo's
	 * editor in the side panel — a non-destructive default, so even if the client doesn't force the menu open,
	 * an accidental left-click is harmless. Below it: Set Placeholder / Remove Layout (handled in
	 * onMenuOptionClicked). onMenuOpened skips hub ghosts so these aren't duplicated.
	 */
	private void addComboGhostMenuEntries(int index, String group) {
		client.createMenuEntry(-1)
				.setOption(REMOVE_COMBO)
				.setTarget(ColorUtil.wrapWithColorTag(comboDisplayName(group), itemTooltipColor))
				.setType(MenuAction.RUNELITE_OVERLAY)
				.setParam0(index)
				.setDeprioritized(true);
		int winner = comboResolver.resolveWinner(group);
		if (winner > 0) {
			client.createMenuEntry(-1)
					.setOption(REPLACE_COMBO_WITH_ITEM)
					.setTarget(ColorUtil.wrapWithColorTag(itemName(winner), itemTooltipColor))
					.setType(MenuAction.RUNELITE_OVERLAY)
					.setParam0(index)
					.setDeprioritized(true);
		}
		// Top entry (the hover text): "Edit <name>". Deprioritized so a left-click opens the menu;
		// selecting it opens this combo's editor in the side panel.
		client.createMenuEntry(-1)
				.setOption(OPEN_COMBO)
				.setTarget(ColorUtil.wrapWithColorTag(comboDisplayName(group), itemTooltipColor))
				.setType(MenuAction.RUNELITE_OVERLAY)
				.setParam0(index)
				.setDeprioritized(true)
				.onClick(me -> openComboInPanel(group));
	}

	/**
	 * CORE tabs only: an unowned combo ghost is RuneLite's own placeholder, whose hover defaults to
	 * "Duplicate-item". While the menu is closed (i.e. for the hover), relabel that entry to "Edit <name>" in
	 * the group color. It stays deprioritized, so a left-click still opens the menu; once open, onMenuOpened
	 * owns the entries (it strips this and re-adds the full set). Called from onPostMenuSort.
	 */
	private void relabelComboGhostHover() {
		if (client.isMenuOpen()) {
			return;
		}
		Widget bankContainer = client.getWidget(ComponentID.BANK_CONTAINER);
		if (bankContainer == null || bankContainer.isHidden()) {
			return;
		}
		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (layoutable == null || !layoutable.isBankTab() || !isVanillaLayoutEnabled(layoutable)) {
			return; // core tabs only; hub ghosts build their own placeholder menu
		}
		int index = getMouseIndex();
		if (index == -1) {
			return;
		}
		String group = getComboCellGroups(layoutable).get(index);
		if (group == null) {
			return;
		}
		int winner = comboResolver.resolveWinner(group);
		if (winner <= 0 || comboResolver.isOwnedReal(winner)) {
			return; // only the unowned ghost
		}
		for (MenuEntry e : client.getMenuEntries()) {
			String opt = e.getOption();
			if (opt != null && opt.startsWith("Duplicate-item")) {
				e.setOption(OPEN_COMBO)
					.setTarget(ColorUtil.wrapWithColorTag(comboDisplayName(group), itemTooltipColor))
					.onClick(me -> openComboInPanel(group));
				break;
			}
		}
	}

	private void addFakeItemMenuEntries(MenuEntryAdded menuEntryAdded) {
		if (!menuEntryAdded.getOption().equalsIgnoreCase("cancel")) return;

		LayoutableThing currentLayoutableThing = getCurrentLayoutableThing();
		if (!config.showLayoutPlaceholders() || !hasLayoutEnabled(currentLayoutableThing)) {
			return;
		}
		Layout layout = getBankOrder(currentLayoutableThing);

		int index = getMouseIndex();
		if (index == -1) return;
		int itemIdAtIndex = layout.getItemAtIndex(index);

		// Combo ghost cells are plain placeholders — no "Remove-layout" entry (so they have no hover text).
		if (itemIdAtIndex != -1 && !indexToWidget.containsKey(index)
				&& getComboCellGroups(currentLayoutableThing).get(index) == null) {
			client.getMenu().createMenuEntry(-1)
					.setOption(REMOVE_FROM_LAYOUT_MENU_OPTION)
					.setType(MenuAction.RUNELITE)
					.setTarget(ColorUtil.wrapWithColorTag(itemName(itemIdAtIndex), itemTooltipColor))
					.setParam0(index);
			client.createMenuEntry(-1)
				.setOption(DUPLICATE_ITEM)
				.setTarget(ColorUtil.wrapWithColorTag(itemName(itemIdAtIndex), itemTooltipColor))
				.setType(MenuAction.RUNELITE)
				.setParam0(index);
			// This is a dummy option to start off the drag. It should be removed in onMenuOpened.
			removeThisMenuEntry = client.getMenu().createMenuEntry(-1)
				.setType(MenuAction.RUNELITE)
				.setTarget(ColorUtil.wrapWithColorTag(itemName(itemIdAtIndex), itemTooltipColor))
				.onClick(me -> {
					if (client.isMenuOpen()) return;
					FakeItem fakeItem = fakeItems.stream().filter(fake -> fake.index == index).findAny().orElse(null);
					if (fakeItem != null) {
						Point mcp = client.getMouseCanvasPosition();
						draggedItemIndex = fakeItem.index;
						dragStartX = mcp.getX();
						dragStartY = mcp.getY();
						dragStartScroll = client.getWidget(ComponentID.BANK_ITEM_CONTAINER).getScrollY();
						antiDrag.startDrag();
					}
				});
			removeThisMenuEntryStale = client.getGameCycle();
		}
	}

	int getMouseIndex() {
		return getIndexForMousePosition(false, false, false);
	}

	int getMouseIndexNoLowerLimit() {
		return getIndexForMousePosition(true, true, false);
	}

	int getMouseIndexPadded() {
		return getIndexForMousePosition(true, false, false);
	}

	int getMouseRow() {
		return getIndexForMousePosition(false, false, true);
	}

	int getIndexForMousePosition(boolean padClickboxes, boolean expandBelowWindow, boolean getRow) {
		Widget bankItemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
		if (bankItemContainer == null) return -1;
		Point mouseCanvasPosition = client.getMouseCanvasPosition();

		int mouseX = mouseCanvasPosition.getX();
		int mouseY = mouseCanvasPosition.getY();
		Rectangle bankBounds = bankItemContainer.getBounds();

		if (
				expandBelowWindow && (mouseX < bankBounds.getMinX() || mouseX > bankBounds.getMaxX() || mouseY < bankBounds.getMinY())
						|| !expandBelowWindow && !bankBounds.contains(new java.awt.Point(mouseX, mouseY))) {
			return -1;
		}

		Point canvasLocation = bankItemContainer.getCanvasLocation();
		int relativeY = mouseY - canvasLocation.getY() + bankItemContainer.getScrollY() + 2;
		int row = relativeY / ROW_HEIGHT;
		if (getRow) return row;
		int relativeX = mouseX - canvasLocation.getX() - 51 + 6;
		int col = relativeX / COLUMN_WIDTH;
		int index = row * 8 + col;
		if (row < 0 || col < 0 || col > 7 || index < 0) return -1;
		if (!padClickboxes) {
			int xDistanceIntoItem = relativeX % COLUMN_WIDTH;
			int yDistanceIntoItem = relativeY % ROW_HEIGHT;
			if (xDistanceIntoItem < 6 || xDistanceIntoItem >= 42 || yDistanceIntoItem < 2 || yDistanceIntoItem >= 34) {
				return -1;
			}
		}
		return index;
	}

	// TODO do I actually want to remove variant items from the tag? What if I'm just removing one of the layout items, and do not actually want to remove it from the tag? That seems very reasonable.
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		Widget widget = client.getWidget(ComponentID.BANK_CONTAINER);
		if (widget == null || widget.isHidden()) {
			return;
		}

		if (!(event.getMenuAction() == MenuAction.RUNELITE_OVERLAY || event.getMenuAction() == MenuAction.RUNELITE)) return;

		String menuTarget = Text.removeTags(event.getMenuTarget()).replace("\u00a0"," ");

		// If this is on a real item, then the bank tags plugin will remove it from the tag, and this plugin only needs
		// to remove it from the layout. If this is on a fake item, this plugin must do both (unless the "Remove-layout"
		// option was clicked, then the tags are not touched).
		String menuOption = event.getMenuOption();
		boolean consume = true;
		if (menuOption.startsWith(REMOVE_FROM_LAYOUT_MENU_OPTION)) {
			removeFromLayout(event.getParam0());
		} else if (EXPORT_LAYOUT.equals(menuOption)) {
			exportLayout(menuTarget);
		} else if (IMPORT_LAYOUT.equals(menuOption)) {
			importLayout();
		} else if (PREVIEW_AUTO_LAYOUT.equals(menuOption)) {
			showLayoutPreview();
		} else if (DUPLICATE_ITEM.equals(menuOption)) {
			duplicateItem(event.getParam0());
		} else if (REMOVE_DUPLICATE_ITEM.equals(menuOption)) {
			removeFromLayout(event.getParam0());
		} else if (REMOVE_COMBO.equals(menuOption)) {
			removeComboCell(event.getParam0());
		} else if (REPLACE_COMBO_WITH_ITEM.equals(menuOption)) {
			replaceComboCellWithItem(event.getParam0());
		} else {
			consume = false;
		}
		if (consume) event.consume();
	}

	/**
	 * Replaces a combo group's loose members in the currently-open tag tab with a single combo smart cell:
	 * untags every member of the group from the host tab and places the cell at the top-left-most position
	 * those members occupied (or the first empty slot if none were present). Called from the side panel.
	 */
	public void replaceComboInOpenTab(String comboGroup) {
		clientThread.invokeLater(() -> {
			String hostTag = tabInterface.getActiveTag();
			if (hostTag == null || hostTag.isEmpty() || hostTag.equals("tagtabs") || hostTag.startsWith("_invsetup_")) {
				chatMessage("Open a bank tag tab first, then replace the combo into it.");
				return;
			}
			LayoutableThing layoutable = LayoutableThing.bankTag(hostTag);
			boolean core = isVanillaLayoutEnabled(layoutable);
			boolean wasHost = comboBankTags.containsKey(hostTag);
			Layout layout = core ? null : getBankOrderNonPreview(layoutable);
			if (!core && layout == null) {
				layout = Layout.emptyLayout();
			}
			ComboReplaceResult result = replaceComboIntoTab(hostTag, layout, comboGroup);
			if (result == ComboReplaceResult.ABSENT) {
				chatMessage("Combo \"" + comboGroup + "\" isn't in tab \"" + hostTag + "\" — nothing to replace.");
				return; // no-op: don't relayout or (for a new host) recapture
			}
			if (core) {
				if (!wasHost) {
					recaptureActiveComboCoreTab(); // only needed to capture a brand-new ComboBankTag
				}
			}
			else {
				saveLayoutNonPreview(layoutable, layout);
			}
			bankSearch.layoutBank(); // triggers a build → the render shows the live winner
			chatMessage((result == ComboReplaceResult.ADDED ? "Replaced" : "Cleaned up") + " combo \"" + comboGroup + "\" in tab \"" + hostTag + "\".");
		});
	}

	/** Replaces EVERY combo filed under the given category into the open tab, in one client-thread pass. */
	public void replaceCategoryInOpenTab(String category) {
		clientThread.invokeLater(() -> {
			String hostTag = tabInterface.getActiveTag();
			if (hostTag == null || hostTag.isEmpty() || hostTag.equals("tagtabs") || hostTag.startsWith("_invsetup_")) {
				chatMessage("Open a bank tag tab first, then replace the group into it.");
				return;
			}
			List<String> groups = new ArrayList<>();
			for (com.banktaglayouts.combo.ComboGroup g : com.banktaglayouts.combo.ComboStore.all(configManager, gson)) {
				if (category.equals(g.category)) {
					groups.add(g.name);
				}
			}
			if (groups.isEmpty()) {
				chatMessage("No combos in group \"" + category + "\".");
				return;
			}
			LayoutableThing layoutable = LayoutableThing.bankTag(hostTag);
			boolean core = isVanillaLayoutEnabled(layoutable);
			boolean wasHost = comboBankTags.containsKey(hostTag);
			Layout layout = core ? null : getBankOrderNonPreview(layoutable);
			if (!core && layout == null) {
				layout = Layout.emptyLayout();
			}
			int replaced = 0;
			for (String comboGroup : groups) {
				if (replaceComboIntoTab(hostTag, layout, comboGroup) != ComboReplaceResult.ABSENT) {
					replaced++; // shares the one layout; one relayout below
				}
			}
			if (replaced == 0) {
				chatMessage("No combos from group \"" + category + "\" are in tab \"" + hostTag + "\" — nothing to replace.");
				return;
			}
			if (core) {
				if (!wasHost) {
					recaptureActiveComboCoreTab(); // only needed to capture a brand-new ComboBankTag
				}
			}
			else {
				saveLayoutNonPreview(layoutable, layout);
			}
			bankSearch.layoutBank();
			chatMessage("Replaced group \"" + category + "\" (" + replaced + " of " + groups.size() + " combos) in tab \"" + hostTag + "\".");
		});
	}

	/** Outcome of {@link #replaceComboIntoTab}: a cell was newly placed, an existing one refreshed, or the combo wasn't in the tab. */
	private enum ComboReplaceResult { ADDED, REFRESHED, ABSENT }

	/**
	 * Core of "replace combo in tab": consolidates the group's loose member items already in the tab into a
	 * single smart cell — untags them and (for a new cell) places it at the top-left-most member slot. It does
	 * NOT add a combo that isn't present: if no member is in the tab and there's no existing cell, it's a no-op.
	 * Mutates the passed {@code layout} and the persisted comboslots; does NOT save the layout or relayout (the
	 * caller does that once). Client thread.
	 */
	private ComboReplaceResult replaceComboIntoTab(String hostTag, Layout hubLayout, String comboGroup) {
		boolean core = isVanillaLayoutEnabled(LayoutableThing.bankTag(hostTag));
		Set<Integer> memberBases = new HashSet<>(comboResolver.orderedMemberBases(comboGroup));
		List<com.banktaglayouts.combo.ComboSlots.Slot> slots = com.banktaglayouts.combo.ComboSlots.read(configManager, hostTag);
		com.banktaglayouts.combo.ComboSlots.Slot existing = slots.stream()
			.filter(s -> s.getGroup().equals(comboGroup)).findFirst().orElse(null);

		// Sweep the group's member items out of whichever layout the tab uses, recording the top-left slot.
		int topLeft = Integer.MAX_VALUE;
		if (core) {
			// Read the LIVE layout for the top-left member slot; the actual removal of the (about-to-be-untagged)
			// members is left to maintainComboCoreTab's scrub so everything happens on the one live object — a
			// separate loadLayout/saveLayout copy here would diverge from what RuneLite is rendering.
			net.runelite.client.plugins.banktags.tabs.Layout coreLayout = coreLayoutFor(hostTag);
			if (coreLayout != null) {
				int[] l = coreLayout.getLayout();
				for (int pos = 0; pos < l.length; pos++) {
					if (l[pos] > 0 && memberBases.contains(comboBaseOf(l[pos])) && pos < topLeft) {
						topLeft = pos;
					}
				}
			}
		}
		else {
			List<Integer> indicesToClear = new ArrayList<>();
			for (Map.Entry<Integer, Integer> e : new ArrayList<>(hubLayout.allPairs())) {
				if (memberBases.contains(comboBaseOf(e.getValue()))) {
					indicesToClear.add(e.getKey());
					if (e.getKey() < topLeft) {
						topLeft = e.getKey();
					}
				}
			}
			for (int idx : indicesToClear) {
				hubLayout.clearIndex(idx);
			}
		}

		// Untag every member item from the host tab (both the non-variation and variation tag forms), noting
		// whether the combo was present in the tab at all (a member sitting in the layout or tagged).
		boolean memberPresent = topLeft != Integer.MAX_VALUE;
		// getItemsForTag matches case-sensitively against lowercased tags and returns NEGATIVE ids for the
		// variation tag form — so standardize the tag and abs the id before mapping to a base (mirrors
		// untagComboMembersOnce). Without this, mixed-case tabs find no members and variation-tagged members
		// feed a negative id into getItemComposition.
		for (Integer item : tagManager.getItemsForTag(Text.standardize(hostTag))) {
			if (memberBases.contains(comboBaseOf(Math.abs(item)))) {
				copyPaste.removeTag(item, hostTag);
				memberPresent = true;
			}
		}

		int cellIndex;
		if (existing == null) {
			// "Replace" only consolidates a combo that's already in the tab — never adds one that isn't.
			if (!memberPresent) {
				return ComboReplaceResult.ABSENT;
			}
			if (topLeft == Integer.MAX_VALUE) {
				topLeft = core ? firstEmptyCorePos(coreLayoutFor(hostTag), slots)
					: firstEmptyIndexAvoidingCombos(hubLayout, slots);
			}
			slots.add(new com.banktaglayouts.combo.ComboSlots.Slot(topLeft, comboGroup));
			com.banktaglayouts.combo.ComboSlots.write(configManager, hostTag, slots);
			invalidateComboCellGroupsCache();
			cellIndex = topLeft;
		}
		else {
			cellIndex = existing.getIndex();
		}

		if (core) {
			ensureComboBankTagRegistered(hostTag); // the caller recaptures iff this tab was a new host
			maintainComboCoreTab(hostTag);         // membership + pin the cell's winner (mutates the live layout)
		}
		else {
			// Tag + position the cell's winner NOW (instant) — see placeComboWinnerNow.
			placeComboWinnerNow(hostTag, cellIndex, comboGroup);
		}
		return existing == null ? ComboReplaceResult.ADDED : ComboReplaceResult.REFRESHED;
	}

	private void removeFromLayout(int index)
	{
		LayoutableThing layoutable = getCurrentLayoutableThing();
		Layout layout = getBankOrder(layoutable);
		layout.clearIndex(index);
		saveLayout(layoutable, layout);

		applyCustomBankTagItemPositions();
	}

	@VisibleForTesting
	void duplicateItem(int clickedItemIndex)
	{
		LayoutableThing layoutable = getCurrentLayoutableThing();
		Layout layout = getBankOrder(layoutable);

		layout.duplicateItem(clickedItemIndex, getIdForIndexInRealBank(clickedItemIndex));
		saveLayout(layoutable, layout);

		applyCustomBankTagItemPositions();
	}

	// TODO consider using tagManager.getItemsForTag(bankTagName) because unlike findTag it is api.
	private void cleanItemsNotInBankTag(Layout layout, LayoutableThing layoutable) {
		Predicate<Integer> containsId;
		if (layoutable.isBankTab()) {
			// Exact match: startsWith wrongly treats e.g. an item tagged "newtoa2" as part of tab "newtoa",
			// which kept rotated-out combo items in the layout as ghost artifacts.
			containsId = id -> copyPaste.findTagExact(id, layoutable.name);
		}
		else
		{
			throw new UnsupportedOperationException();
		}

		Iterator<Map.Entry<Integer, Integer>> iter = layout.allPairsIterator();
		while (iter.hasNext()) {
			int itemId = iter.next().getValue();

			if (!containsId.test(itemId))
			{
				log.debug("removing " + itemNameWithId(itemId) + " because it is no longer in the thing");
				iter.remove();
			}
		}
	}

	// TODO this logic needs looking at re: barrows items.
	private void assignVariantItemPositions(Layout layout, List<Widget> bankItems, Map<Integer, Widget> indexToWidget) {
		// Remove duplicate item id widgets.
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		bankItems = new ArrayList<>(bankItems.stream().filter(widget -> seen.add(widget.getItemId())).collect(Collectors.toList()));

		Multimap<Integer, Widget> variantItemsInBank = LinkedListMultimap.create(); // key is the variant base id; the list contains the item widgets that go in this variant base id;
		for (Widget bankItem : bankItems) {
			int nonPlaceholderId = getNonPlaceholderId(bankItem.getItemId());
			if (itemShouldBeTreatedAsHavingVariants(nonPlaceholderId)) {
				int variationBaseId = getVariationBaseId(nonPlaceholderId);
				variantItemsInBank.put(variationBaseId, bankItem);
			}
		}

		Multimap<Integer, Integer> variantItemsInLayout = LinkedListMultimap.create(); // key is the variant base id; the list contains the item ids;
		for (Map.Entry<Integer, Integer> pair : layout.allPairs()) {
			int nonPlaceholderId = getNonPlaceholderId(pair.getValue());
			if (itemShouldBeTreatedAsHavingVariants(nonPlaceholderId)) {
				int variationBaseId = getVariationBaseId(nonPlaceholderId);
				variantItemsInLayout.put(variationBaseId, pair.getValue());
			}
		}

		for (Integer variationBaseId : variantItemsInBank.keySet()) {
			List<Widget> notYetPositionedWidgets = new ArrayList<>(variantItemsInBank.get(variationBaseId));

			// first, figure out if there is a perfect match.
			assignitemstrashname(indexToWidget, variantItemsInLayout, variationBaseId, notYetPositionedWidgets, (itemIdsInLayoutForVariant, itemId) -> itemIdsInLayoutForVariant.contains(itemId) ? layout.getIndexForItem(itemId) : -1, "pass 1 (exact itemid match)");

			// check matches of placeholders or placeholders matching items.
			assignitemstrashname(indexToWidget, variantItemsInLayout, variationBaseId, notYetPositionedWidgets, (itemIdsInLayoutForVariant, itemId) -> {
				itemId = switchPlaceholderId(itemId);
				return itemIdsInLayoutForVariant.contains(itemId) ? layout.getIndexForItem(itemId) : -1;
			}, "pass 2 (placeholder match)");

			// match any variant item.
			assignitemstrashname(indexToWidget, variantItemsInLayout, variationBaseId, notYetPositionedWidgets, (itemIdsInLayoutForVariant, itemId) -> {
				for (Integer id : itemIdsInLayoutForVariant) {
					int index = layout.getIndexForItem(id);
					if (!indexToWidget.containsKey(index)) {
						return index;
					}
				}
				return -1;
			}, "pass 3 (variant item match)");

			if (!notYetPositionedWidgets.isEmpty()) {
				for (Widget notYetPositionedWidget : notYetPositionedWidgets) {
					int itemId = notYetPositionedWidget.getItemId();
					int layoutIndex = layout.getIndexForItem(itemId);
					if (layoutIndex != -1) continue; // Prevents an issue where items with the same id that take up multiple bank slots, e.g. items that have their charges stored on the item, can be added into two slots during this stage.
					int index = layout.getFirstEmptyIndex();
					layout.putItem(itemId, index);
					log.debug("item " + itemNameWithId(itemId) + " assigned on pass 4 (assign to empty spot) to index " + index);
					indexToWidget.put(index, notYetPositionedWidget);
				}
			}
		}
	}

	private int getVariationBaseId(int nonPlaceholderId)
	{
		int runeliteBaseId = ItemVariationMapping.map(nonPlaceholderId);
		if (runeliteBaseId == 713) {
			ItemComposition itemComposition = itemManager.getItemComposition(nonPlaceholderId);
			int iconId = itemComposition.getInventoryModel();
			if (iconId == 37162) { // beginner
				return nonPlaceholderId; // All share the same id.
			}
			else if (iconId == 37202) { // easy
				return 2677; // Lowest id of this clue type.
			}
			else if (iconId == 37152) { // medium
				return 2801; // Lowest id of this clue type.
			}
			else if (iconId == 37181) { // hard
				return 2722; // Lowest id of this clue type.
			}
			else if (iconId == 37167) { // elite
				return 12073; // Lowest id of this clue type.
			}
			else if (iconId == 37183) { // master
				return nonPlaceholderId; // All share the same id.
			}
			// this is either a (likely unobtainable) pink skirt or a sote quest item. I don't care how either of these items are handled.
		}
		return runeliteBaseId;
	}

	@FunctionalInterface
	private interface functionalinterfacetrashname {
		int getIndex(Collection<Integer> itemIds, int itemId);
	}

	private void assignitemstrashname(Map<Integer, Widget> indexToWidget, Multimap<Integer, Integer> variantItemsInLayout, Integer variationBaseId, List<Widget> notYetPositionedWidgets, functionalinterfacetrashname getIndex, String debugDescription)
	{
		Iterator<Widget> iter = notYetPositionedWidgets.iterator();
		while (iter.hasNext()) {
			Widget widget = iter.next();
			int itemId = widget.getItemId();

			Collection<Integer> itemIds = variantItemsInLayout.get(variationBaseId);
			if (itemIds == null) continue; // this could happen because I removed all the widgets at this key.

			int index = getIndex.getIndex(itemIds, itemId);

			if (index != -1 && !indexToWidget.containsKey(index)) {
				log.debug("item " + itemNameWithId(itemId) + " assigned on " + debugDescription + " to index " + index);
				indexToWidget.put(index, widget);
				iter.remove();
			}
		}
	}

	/**
	 */
	private boolean itemHasVariants(int nonPlaceholderItemId) {
		return ItemVariationMapping.getVariations(ItemVariationMapping.map(nonPlaceholderItemId)).size() > 1;
	}

	/**
	 * Whether this item should be treated as having variants for the purpose of custom bank layouts.
	 * If true, this means that the item should occupy the next available position in the custom layout which matches either its own id or any of its variants.
	 * This includes placeholders for the item.
	 * This does mean that the order that items appear in in the normal bank has an impact on the custom layout. Not something you'd expect from this feature, lol.
	 */
	boolean itemShouldBeTreatedAsHavingVariants(int nonPlaceholderItemId) {
		return itemHasVariants(nonPlaceholderItemId);
	}

	private boolean layoutEnabledByDefault() {
		return config.layoutEnabledByDefault() && config.whichPlugin() != CORE;
	}

	public boolean hasLayoutEnabled(LayoutableThing layoutable) {
		if (layoutable == null) return false;
		if (isShowingPreview()) return true;
		TagTab tagTab = tabManager.find(layoutable.name);
		if (layoutable.isBankTab && tagTab != null && hasRuneliteLayout(layoutable.name)) return false;

		String configuration = configManager.getConfiguration(CONFIG_GROUP, layoutable.configKey());
		if (LAYOUT_EXPLICITLY_DISABLED.equals(configuration)) return false;
		return configuration != null || (layoutable.isBankTab() && layoutEnabledByDefault()) || (layoutable.isInventorySetup() && config.useWithInventorySetups());
	}

	public boolean hasVanillaOrHubLayoutEnabled(LayoutableThing layoutable) {
		if (layoutable == null) return false;
		if (isShowingPreview()) return true;
		TagTab tagTab = tabManager.find(layoutable.name);
		if (tagTab != null && hasRuneliteLayout(layoutable.name)) return true;

		String configuration = configManager.getConfiguration(CONFIG_GROUP, layoutable.configKey());
		if (LAYOUT_EXPLICITLY_DISABLED.equals(configuration)) return false;
		return configuration != null || (layoutable.isBankTab() && layoutEnabledByDefault()) || (layoutable.isInventorySetup() && config.useWithInventorySetups());
	}

	public boolean isVanillaLayoutEnabled(LayoutableThing layoutable) {
		if (layoutable == null) return false;
		TagTab tagTab = tabManager.find(layoutable.name);
		return tagTab != null && hasRuneliteLayout(layoutable.name);
	}

	public boolean hasRuneliteLayout(String tag) {
		return this.configManager.getConfiguration("banktags", "layout_" + Text.standardize(tag)) != null;
	}

	/**
	 * the bank order which is actually used.
	 */
	Layout getBankOrder(LayoutableThing layoutable) {
		if (isShowingPreview()) {
			return previewLayout;
		}

		if (isVanillaLayoutEnabled(layoutable)) return null;
		return getBankOrderNonPreview(layoutable);
	}

	Layout getCurrentBankOrder() {
		LayoutableThing layoutable = getCurrentLayoutableThing();
		if (!hasLayoutEnabled(layoutable)) return null;
		if (isShowingPreview()) {
			return previewLayout;
		}

		if (isVanillaLayoutEnabled(layoutable)) return null;
		return getBankOrderNonPreview(layoutable);
	}

	/**
	 * unlike getBankOrder, this will not return a preview layout when one is currently being show.
	 */
	private Layout getBankOrderNonPreview(LayoutableThing layoutable) {
		String configuration = configManager.getConfiguration(CONFIG_GROUP, layoutable.configKey());
		if (LAYOUT_EXPLICITLY_DISABLED.equals(configuration)) return null;
		if (configuration == null) {
			if (layoutable.isBankTab() && !layoutEnabledByDefault() || layoutable.isInventorySetup() && !config.useWithInventorySetups()) {
				return null;
			} else if (layoutable.isInventorySetup()) {
				throw new UnsupportedOperationException();
			}

			configuration = "";
		}
		return Layout.fromString(configuration, true);
	}

	private void exportLayout(String tagName) {
		String exportString = BANK_TAG_STRING_PREFIX + tagName;
		String layout = getBankOrder(LayoutableThing.bankTag(tagName)).toString();
		if (!layout.isEmpty()) {
			exportString += ",";
		}
		exportString += layout;

		List<String> tabNames = Text.fromCSV(MoreObjects.firstNonNull(configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG), ""));
		if (!tabNames.contains(tagName)) {
			chatErrorMessage("Couldn't export layout-ed tag tab - tag tab doesn't see to exist?");
		}

		List<String> data = new ArrayList<>();
		data.add(tagName);
		String tagTabIconItemId = copyPaste.getIcon(tagName);
		if (tagTabIconItemId == null) {
			tagTabIconItemId = "" + ItemID.SPADE;
		}
		data.add(tagTabIconItemId);

		for (Integer item : tagManager.getItemsForTag(tagName)) {
			data.add(String.valueOf(item));
		}

		exportString += ",banktag:" + Text.toCSV(data);

		putInClipboard(exportString);
		chatMessage("Copied layout-ed tag \"" + tagName + "\" to clipboard");
	}

	private void putInClipboard(String exportString) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(exportString), null);
	}

	private MessageNode chatErrorMessage(String message) {
		return chatMessage(ColorUtil.wrapWithColorTag(message, Color.RED));
	}

	private MessageNode chatErrorMessage(String redMessage, String regularMessage) {
		return chatMessage(ColorUtil.wrapWithColorTag(redMessage, Color.RED) + regularMessage);
	}

	private MessageNode chatMessage(String message) {
		return client.addChatMessage(ChatMessageType.GAMEMESSAGE, "bla", message, "bla");
	}

	static int getXForIndex(int index) {
		return (index % 8) * COLUMN_WIDTH + 51;
	}

	static int getYForIndex(int index) {
		return (index / 8) * BANK_ITEM_WIDTH;
	}

	private void setItemPositions(Map<Integer, Widget> indexToWidget) {
		Widget container = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
		// Hide all widgets not in indexToWidget.
		outer_loop:
		for (Widget child : container.getDynamicChildren()) {
			if (child.isHidden()) continue;
			for (Map.Entry<Integer, Widget> integerWidgetEntry : indexToWidget.entrySet()) {
				if (integerWidgetEntry.getValue().equals(child)) {
					continue outer_loop;
				}
			}

			child.setHidden(true);
			child.revalidate();
		}

		for (Map.Entry<Integer, Widget> entry : indexToWidget.entrySet()) {
			Widget widget = entry.getValue();
			int index = entry.getKey();

			widget.setOriginalX(getXForIndex(index));
			widget.setOriginalY(getYForIndex(index));
			widget.revalidate();
		}
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	static final class LayoutableThing {
		public final String name;
		/** false means it's an inventory setup. */
		public final boolean isBankTab;

		public static LayoutableThing bankTag(String tagName) {
			return new LayoutableThing(tagName, true);
		}

		public static LayoutableThing inventorySetup(String inventorySetupName) {
			return new LayoutableThing(inventorySetupName, false);
		}

		@Override
		public String toString() {
			return name + " " + (isBankTab ? "(bank tab)" : "(inventory setup)");
		}

		public String configKey() {
			if (isBankTab) {
				return LAYOUT_CONFIG_KEY_PREFIX + name;
			} else {
				return INVENTORY_SETUPS_LAYOUT_CONFIG_KEY_PREFIX + escapeColonCharactersInInventorySetupName(name);
			}
		}

		static String escapeColonCharactersInInventorySetupName(String s)
		{
			return s.replaceAll("&", "&amp;").replaceAll(":", "&#58;");
		}

		public boolean isBankTab() {
			return isBankTab;
		}

		public boolean isInventorySetup() {
			return !isBankTab;
		}
	}

	private void resizeBankContainerScrollbar(int height, int lastHeight, boolean doNotScrollDown) {
		Widget container = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);

		container.setScrollHeight(height); // This change requires the script below to run to take effect.

		int itemContainerScroll = (!doNotScrollDown && height > lastHeight) ? height : container.getScrollY();

		clientThread.invokeLater(() ->
				client.runScript(ScriptID.UPDATE_SCROLLBAR,
						ComponentID.BANK_SCROLLBAR,
						ComponentID.BANK_ITEM_CONTAINER,
						itemContainerScroll)
		);
	}

	public String itemName(Integer itemId) {
		return (itemId == null) ? "null" : itemManager.getItemComposition(itemId).getName();
	}

	public String itemNameWithId(Integer itemId) {
		return ((itemId == null) ? "null" : itemManager.getItemComposition(itemId).getName()) + " (" + itemId + (isPlaceholder(itemId) ? ",ph" : "") + ")";
	}

	private int getPlaceholderId(int id) {
		ItemComposition itemComposition = itemManager.getItemComposition(id);
		return (itemComposition.getPlaceholderTemplateId() == 14401) ? id : itemComposition.getPlaceholderId();
	}

	int getNonPlaceholderId(int id) {
		ItemComposition itemComposition = itemManager.getItemComposition(id);
		return (itemComposition.getPlaceholderTemplateId() == 14401) ? itemComposition.getPlaceholderId() : id;
	}

	int switchPlaceholderId(int id) {
		ItemComposition itemComposition = itemManager.getItemComposition(id);
		return itemComposition.getPlaceholderId();
	}

	public boolean isPlaceholder(int id) {
		ItemComposition itemComposition = itemManager.getItemComposition(id);
		return itemComposition.getPlaceholderTemplateId() == 14401;
	}

	private void customBankTagOrderInsert(LayoutableThing layoutable, Widget draggedItem) {
		int draggedOnItemIndex = getMouseIndexNoLowerLimit();
		if (draggedOnItemIndex == -1) return;

		int draggedItemIndex = -1;
		for (Map.Entry<Integer, Widget> entry : indexToWidget.entrySet()) {
			if (entry.getValue().equals(draggedItem)) {
				draggedItemIndex = entry.getKey();
			}
		}

		customBankTagOrderInsert(layoutable, draggedItemIndex, draggedOnItemIndex);
	}

	private void customBankTagOrderInsert(LayoutableThing layoutable, int draggedItemIndex, int draggedOnItemIndex) {
		Layout layout = getBankOrder(layoutable);
		if (layout == null) return;
		if (draggedItemIndex == draggedOnItemIndex) return;

		// Combo cells aren't persisted in the layout, so dragging them (or dragging an item onto one) must
		// move the combo SLOT index instead of touching the layout. Handles both ends being a combo cell.
		if (layoutable.isBankTab() && moveComboCellOnDrag(layoutable, layout, draggedItemIndex, draggedOnItemIndex)) {
			return;
		}

		// Currently I'm just spilling the variant items out in bank order, so I don't care exactly what item id was there - although if I ever decide to change this, this section will become much more complicated, since if I drag a (2) charge onto a regular item, but there was supposed to be a (3) charge there then I have to move the (2) but also deal with where the (2)'s saved position is... At least that's how it'll go if I decide to handle jewellery that way.

		Integer currentDraggedItemId = getIdForIndexInRealBank(draggedItemIndex);

		layout.moveItem(draggedItemIndex, draggedOnItemIndex, currentDraggedItemId);

		saveLayout(layoutable, layout);

		applyCustomBankTagItemPositions();
	}

	/**
	 * Handles a drag where one (or both) end is a combo cell, by moving the combo SLOT index (and swapping
	 * whatever was at the destination). Returns true if it handled the drag (combo involved), false otherwise.
	 */
	private boolean moveComboCellOnDrag(LayoutableThing layoutable, Layout layout, int draggedItemIndex, int draggedOnItemIndex) {
		List<com.banktaglayouts.combo.ComboSlots.Slot> slots =
			com.banktaglayouts.combo.ComboSlots.read(configManager, layoutable.name);
		com.banktaglayouts.combo.ComboSlots.Slot draggedCell = slots.stream()
			.filter(s -> s.getIndex() == draggedItemIndex).findFirst().orElse(null);
		com.banktaglayouts.combo.ComboSlots.Slot targetCell = slots.stream()
			.filter(s -> s.getIndex() == draggedOnItemIndex).findFirst().orElse(null);
		if (draggedCell == null && targetCell == null) {
			return false; // no combo cell involved — let the normal layout move run
		}

		boolean layoutChanged = false;
		if (draggedCell != null && targetCell != null) {
			// Swap two combo cells.
			draggedCell.setIndex(draggedOnItemIndex);
			targetCell.setIndex(draggedItemIndex);
		} else if (draggedCell != null) {
			// Combo dragged onto an empty slot or a real item — swap with the real item if present.
			int targetItem = layout.getItemAtIndex(draggedOnItemIndex);
			if (targetItem > 0) {
				layout.clearIndex(draggedOnItemIndex);
				layout.putItem(targetItem, draggedItemIndex);
				layoutChanged = true;
			}
			draggedCell.setIndex(draggedOnItemIndex);
		} else {
			// A real item dragged onto a combo cell — swap their positions.
			int draggedItem = layout.getItemAtIndex(draggedItemIndex);
			if (draggedItem > 0) {
				layout.clearIndex(draggedItemIndex);
				layout.putItem(draggedItem, draggedOnItemIndex);
				layoutChanged = true;
			}
			targetCell.setIndex(draggedItemIndex);
		}

		// The winner map is keyed by cell index, so move its entries to follow the swapped indices — otherwise
		// each cell would render the OTHER cell's winner after the swap.
		Map<Integer, Integer> winnerMap = readComboWinnerMap(layoutable.name);
		Integer draggedWinner = winnerMap.remove(draggedItemIndex);
		Integer targetWinner = winnerMap.remove(draggedOnItemIndex);
		if (draggedWinner != null) {
			winnerMap.put(draggedOnItemIndex, draggedWinner);
		}
		if (targetWinner != null) {
			winnerMap.put(draggedItemIndex, targetWinner);
		}
		writeComboWinnerMap(layoutable.name, winnerMap);

		com.banktaglayouts.combo.ComboSlots.write(configManager, layoutable.name, slots);
		invalidateComboCellGroupsCache();
		if (layoutChanged) {
			saveLayout(layoutable, layout);
		}
		applyCustomBankTagItemPositions();
		return true;
	}

	private Integer getIdForIndexInRealBank(int index) {
		if (index == -1) return -1;
		Widget widget = indexToWidget.get(index);
		if (widget == null) return -1;
		return widget.getItemId();
	}

	// Disable reordering your real bank while any tag tab is active, as if the Bank Tags Plugin's "Prevent tag tab item dragging" was enabled.
	@Subscribe(priority = -1f) // run after bank tags, otherwise you can't drag items into other tabs while a tab is open.
	public void onWidgetDrag(WidgetDrag event) {
		Widget widget = client.getWidget(ComponentID.BANK_CONTAINER);
		if (widget == null || widget.isHidden()) {
			return;
		}

		Widget draggedWidget = client.getDraggedWidget();

		// Returning early or nulling the drag release listener has no effect. Hence, we need to
		// null the draggedOnWidget instead.
		if (draggedWidget.getId() == ComponentID.BANK_ITEM_CONTAINER && hasLayoutEnabled(getCurrentLayoutableThing())) {
			client.setDraggedOnWidget(null);
		}
	}

	@Subscribe
	public void onFocusChanged(FocusChanged focusChanged)
	{
		antiDrag.focusChanged(focusChanged);
	}

	@Subscribe
	public void onMenuShouldLeftClick(MenuShouldLeftClick event)
	{
		Widget widget = client.getWidget(ComponentID.BANK_CONTAINER);
		if (widget == null || widget.isHidden()) {
			return;
		}

		MenuEntry[] menuEntries = client.getMenuEntries();
		for (MenuEntry entry : menuEntries)
		{
			// checking the type is kinda hacky because really both preview auto layout entries should have the runelite id... but it works.
			if (entry.getOption().equals(PREVIEW_AUTO_LAYOUT) && entry.getType() != MenuAction.RUNELITE)
			{
				event.setForceRightClick(true);
				return;
			}
		}
	}
}
