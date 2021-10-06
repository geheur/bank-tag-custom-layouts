package com.banktaglayouts;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.banktaglayouts.BankTagLayoutsPlugin.BankItemAndWidget;
import com.banktaglayouts.BankTagLayoutsPlugin.BankSlot;
import com.banktaglayouts.Layout.BankItem;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.TabInterface;
import net.runelite.client.ui.overlay.OverlayManager;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class BankLayoutTest
{
    @Mock
    @Bind
    private Client client;

    @Mock
    @Bind
    private ConfigManager configManager;

    @Mock
    @Bind
    private ItemManager itemManager;

    @Mock
    @Bind
    private OverlayManager overlayManager;

    @Mock
    @Bind
    private BankTagLayoutsConfig config;

    @Mock
    @Bind
    private FakeItemOverlay fakeItemOverlay;

    @Mock
    @Bind
    private MouseManager mouseManager;

    @Mock
    @Bind
    private KeyManager keyManager;

    @Mock
    @Bind
    private SpriteManager spriteManager;

    @Mock
    @Bind
    private ClientThread clientThread;

    @Mock
    @Bind
    private TabInterface tabInterface;

    @Mock
    @Bind
    private TagManager tagManager;

    @Mock
    @Bind
    private BankSearch bankSearch;

    @Mock
    @Bind
    private ChatboxPanelManager chatboxPanelManager;

    // TODO uncomment when Adam's spritemanager fix is available.
    @Mock
    @Bind
    private RuneLiteConfig runeliteConfig;

    @Inject
    private BankTagLayoutsPlugin plugin;

    // TODO import/export tests.

    @Before
    public void before()
    {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.DEBUG);
        Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
        plugin = Mockito.spy(plugin);
		Mockito.when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> {
			ItemComposition itemComposition = generateItemComposition(invocation.getArgument(0));
			return itemComposition.getPlaceholderTemplateId() != -1 ?
				itemComposition.getPlaceholderId() :
				itemComposition.getId();
		});
		Mockito.when(itemManager.getItemComposition(anyInt())).thenAnswer(invocation -> generateItemComposition(invocation.getArgument(0)));
    }

    Layout currentLayout;

    private static final Map<Integer, Item> itemsById = new HashMap<>();

    private static final Item MAGIC_LOGS = new Item("Magic logs", 1513, 13982, -1);
    private static final Item MAGIC_LOGS_PH = new Item("Magic logs ph", 13982, 1513, -1, true);

	private static final Item RUNE_PLATEBODY = new Item("rune platebody", 1127, 14106, -1);
	private static final Item RUNE_PLATEBODY_PH = new Item("rune platebody ph", 14106, 1127, -1);

	private static final Item GAMES_NECKLACE_1_PH = new Item("Games necklace (1) ph", 21460, 3867, -1, true);
    private static final Item GAMES_NECKLACE_8_PH = new Item("Games necklace (8) ph", 16362, 3853, -1, true);
    private static final Item GAMES_NECKLACE_8 = new Item("Games necklace (8)", 3867, 16362, 3867);
    private static final Item GAMES_NECKLACE_7 = new Item("Games necklace (7)", 3865, -1, 3867);
    private static final Item GAMES_NECKLACE_6 = new Item("Games necklace (6)", 3863, -1, 3867);
    private static final Item GAMES_NECKLACE_5 = new Item("Games necklace (5)", 3861, -1, 3867);
    private static final Item GAMES_NECKLACE_4 = new Item("Games necklace (4)", 3859, -1, 3867);
    private static final Item GAMES_NECKLACE_3 = new Item("Games necklace (3)", 3857, -1, 3867);
    private static final Item GAMES_NECKLACE_2 = new Item("Games necklace (2)", 3855, -1, 3867);
    private static final Item GAMES_NECKLACE_1 = new Item("Games necklace (1)", 3853, 21460, 3867);

    private static final Item STRANGE_LOCKPICK_FULL = new Item("Strange lockpick (full)", 24740, 24742, 24738);
    private static final Item STRANGE_LOCKPICK_FULL_PH = new Item("Strange lockpick (full) ph", 24742, 24740, -1, true);
    private static final Item STRANGE_LOCKPICK_USED = new Item("Strange lockpick (used)", 24738, 24739, 24738);
    private static final Item STRANGE_LOCKPICK_USED_PH = new Item("Strange lockpick (used) ph", 24739, 24738, -1, true);

	private static final Item SERP_HELM = new Item("Serp helm", 	12931, 15317, 12929);
	private static final Item SERP_HELM_PH = new Item("Serp helm (ph)", 15317, 12931, -1, true);
	private static final Item MAGMA_SERP_HELM = new Item("Magma helm", 13199, 15319, 12929);
	private static final Item MAGMA_SERP_HELM_PH = new Item("Magma helm (ph)", 15319, 13199, -1, true);

	private static boolean isPlaceholder(int itemId)
	{
		Item item = itemsById.get(itemId);
		return item.placeholder;
	}

    /*
    Things to test both variant and non-variant.
        Moving (including updating the duplicates).
        Adding new item.
        Duplicating (including updating the duplicates with the currently present item).
        Removing duplicates.
    variant items only:
     */

    @Test
    public void testAddNewItemVariant() {
        currentLayout = generateLayout(
        );
        List<BankTagLayoutsPlugin.BankItemAndWidget> bankItems = generateBankItems(
                GAMES_NECKLACE_8
        );

        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
        LaidOutBank expectedLaidOutBank = createLaidOutBank(
                0, realItem(GAMES_NECKLACE_8)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);
    }

	@Test
    public void testAddNewItemNonVariant() {
        currentLayout = generateLayout(
        );
        List<BankTagLayoutsPlugin.BankItemAndWidget> bankItems = generateBankItems(
                MAGIC_LOGS
        );

        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
        LaidOutBank expectedLaidOutBank = createLaidOutBank(
                0, realItem(MAGIC_LOGS)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);
    }

    @Test
    public void testMoveItems() {
		List<BankItem> layoutItems = Arrays.asList(new BankItem(GAMES_NECKLACE_8.id), new BankItem(GAMES_NECKLACE_8_PH.id), new BankItem(MAGIC_LOGS.id), new BankItem(GAMES_NECKLACE_8.id, 1), new BankItem(GAMES_NECKLACE_8_PH.id, 1), new BankItem(MAGIC_LOGS.id, 1));
		List<BankItem> realItems = Arrays.asList(new BankItem(GAMES_NECKLACE_8.id), new BankItem(GAMES_NECKLACE_8_PH.id), new BankItem(MAGIC_LOGS.id), new BankItem(GAMES_NECKLACE_8.id, 1), new BankItem(GAMES_NECKLACE_8_PH.id, 1), new BankItem(MAGIC_LOGS.id, 1));
		List<BankSlot.Type> targetTypes = Arrays.asList(BankSlot.Type.DUPLICATE_ITEM, BankSlot.Type.LAYOUT_PLACEHOLDER, BankSlot.Type.REAL_ITEM, null);
		for (BankItem targetLayoutItem : realItems)
		{
			for (BankSlot.Type targetType : targetTypes)
			{
				for (int i = 0; i < layoutItems.size(); i++)
				{
					BankItem layoutItem = layoutItems.get(i);
					BankItem realItem = realItems.get(i);
					if (targetLayoutItem.equals(layoutItem) || targetLayoutItem.equals(realItem)) continue;
					if (targetLayoutItem.getUnstackableIndex() == 1 && layoutItem.getUnstackableIndex() == 1) {
						System.out.println("hello");
					}
					testMoveItem(layoutItem, layoutItem, targetLayoutItem, targetType);
					testMoveItem(layoutItem, realItem, targetLayoutItem, targetType);
					testMoveItem(realItem, layoutItem, targetLayoutItem, targetType);
					testMoveItem(realItem, realItem, targetLayoutItem, targetType);
				}
			}
		}
	}

    /*
    targetItem only matters if targetType is real or layout placeholder.

    BankSlot.Type is misused here as an argument.
    null means swap with empty spot
    duplicate means swap with the item's own duplicate
    layout placeholder means swap with a layout placeholder.
    real means swap with a different real item.
     */
    private void testMoveItem(BankItem layoutItem, BankItem realItem, BankItem targetLayoutItem, BankSlot.Type targetType)
    {
    	System.out.println("testMoveItem " + targetType + ": " + layoutItem + " (" + realItem + "), " + targetLayoutItem);
        /*
        indexes:
        1 - moved item
        2 - location of empty slot to move to
        4 - dupe of 1
        6 - location of other item to move to
        7 - dupe of 6
         */
        boolean isLayoutPlaceholder = realItem == null;
        int targetIndex = targetType == null ? 2 : targetType == BankSlot.Type.DUPLICATE_ITEM ? 4 : 6;

        currentLayout = generateLayout(
                layoutItem, 1,
                layoutItem, 4,
                targetLayoutItem, 6,
                targetLayoutItem, 7
        );
        List<BankTagLayoutsPlugin.BankItemAndWidget> bankItems = new ArrayList<>();
        if (!isLayoutPlaceholder)
        {
            bankItems.add(createBankItemWidget(realItem));
        }
        if (targetType != BankSlot.Type.LAYOUT_PLACEHOLDER)
        {
            bankItems.add(createBankItemWidget(targetLayoutItem));
        }

        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
        LaidOutBank expectedLaidOutBank = createLaidOutBank(
                1, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : realItem(realItem),
                4, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : duplicateItem(realItem),
                6, targetType == BankSlot.Type.LAYOUT_PLACEHOLDER ? BankSlot.layoutPlaceholder(targetLayoutItem) : realItem(targetLayoutItem),
                7, targetType == BankSlot.Type.LAYOUT_PLACEHOLDER ? BankSlot.layoutPlaceholder(targetLayoutItem) : duplicateItem(targetLayoutItem)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);

        currentLayout.moveItem(1, targetIndex, realItem != null ? realItem : layoutItem);

        laidOutBank = layOutBank(currentLayout, bankItems);
        expectedLaidOutBank = createLaidOutBank();
        if (targetType == null) {
			expectedLaidOutBank.add(2, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : realItem(realItem));
			expectedLaidOutBank.add(4, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : duplicateItem(realItem));
			expectedLaidOutBank.add(6, realItem(targetLayoutItem));
			expectedLaidOutBank.add(7, duplicateItem(targetLayoutItem));
			if (realItem != null)
			{
				checkCurrentLayout(2, realItem);
				checkCurrentLayout(4, realItem);
			}
		} else if (targetType == BankSlot.Type.DUPLICATE_ITEM) {
			expectedLaidOutBank.add(1, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : realItem(realItem));
			expectedLaidOutBank.add(4, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : duplicateItem(realItem));
			expectedLaidOutBank.add(6, realItem(targetLayoutItem));
			expectedLaidOutBank.add(7, duplicateItem(targetLayoutItem));
			if (realItem != null)
			{
				checkCurrentLayout(1, realItem);
				checkCurrentLayout(4, realItem);
			}
		} else if (targetType == BankSlot.Type.LAYOUT_PLACEHOLDER) {
			expectedLaidOutBank.add(4, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : realItem(realItem));
			expectedLaidOutBank.add(6, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : duplicateItem(realItem));
			expectedLaidOutBank.add(1, BankSlot.layoutPlaceholder(targetLayoutItem));
			expectedLaidOutBank.add(7, BankSlot.layoutPlaceholder(targetLayoutItem));
			if (realItem != null)
			{
				checkCurrentLayout(6, realItem);
				checkCurrentLayout(4, realItem);
			}
		} else if (targetType == BankSlot.Type.REAL_ITEM) {
			expectedLaidOutBank.add(4, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : realItem(realItem));
			expectedLaidOutBank.add(6, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(layoutItem) : duplicateItem(realItem));
			expectedLaidOutBank.add(1, realItem(targetLayoutItem));
			expectedLaidOutBank.add(7, duplicateItem(targetLayoutItem));
			if (realItem != null)
			{
				checkCurrentLayout(6, realItem);
				checkCurrentLayout(4, realItem);
			}
		}
		System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);
    }

	@Test
    public void testDuplicateItems()
	{
		List<BankItem> layoutItems = Arrays.asList(new BankItem(GAMES_NECKLACE_8.id), new BankItem(GAMES_NECKLACE_8_PH.id), new BankItem(MAGIC_LOGS.id));
		List<BankItem> realItems = Arrays.asList(new BankItem(GAMES_NECKLACE_8.id), new BankItem(GAMES_NECKLACE_8_PH.id), new BankItem(MAGIC_LOGS.id));
		for (BankItem targetLayoutItem : realItems)
		{
			for (int i = 0; i < layoutItems.size(); i++)
			{
				// TODO aren't these always the same?
				BankItem layoutItem = layoutItems.get(i);
				BankItem realItem = realItems.get(i);
				if (targetLayoutItem.equals(layoutItem) || targetLayoutItem.equals(realItem)) continue;
				System.out.println("test duplicate item " + layoutItem + " " + realItem);
				System.out.println("1");
				testDuplicateItem(layoutItem, layoutItem);
				System.out.println("2");
				testDuplicateItem(layoutItem, realItem);
				System.out.println("3");
				testDuplicateItem(realItem, layoutItem);
				System.out.println("4");
				testDuplicateItem(realItem, realItem);
			}
		}

		/*
		List<BankItem> layoutItems = Arrays.asList(new BankItem(GAMES_NECKLACE_8.id), new BankItem(GAMES_NECKLACE_8_PH.id), new BankItem(MAGIC_LOGS.id));
		List<BankItem> realItems = Arrays.asList(new BankItem(GAMES_NECKLACE_8.id), new BankItem(GAMES_NECKLACE_8_PH.id), new BankItem(MAGIC_LOGS.id));
		for (BankItem targetLayoutItem : realItems)
		{
			for (BankItem layoutItem : layoutItems)
			{
				if (targetLayoutItem.equals(layoutItem)) continue;
				testDuplicateItem(layoutItem, layoutItem);
				testDuplicateItem(layoutItem, targetLayoutItem);
				testDuplicateItem(targetLayoutItem, layoutItem);
				testDuplicateItem(targetLayoutItem, targetLayoutItem);
			}
		}
		 */
	}

	private void testDuplicateItem(BankItem layoutItem, BankItem realItem)
	{
		boolean isLayoutPlaceholder = realItem == null;
		if (realItem == null) realItem = layoutItem;
		currentLayout = generateLayout(
			layoutItem, 1,
			layoutItem, 2
		);
		List<BankItemAndWidget> bankItems = isLayoutPlaceholder
			? Collections.emptyList()
			: generateBankItems(itemsById.get(realItem.getItemId()));

		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(realItem) : realItem(realItem),
			2, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(realItem) : duplicateItem(realItem)
		);
		System.out.println("real bank: " + laidOutBank);
		System.out.println("expected bank: " + expectedLaidOutBank);
		assertEquals(expectedLaidOutBank, laidOutBank);

		if (isLayoutPlaceholder) {
			currentLayout.duplicateItem(1);
		} else {
			currentLayout.duplicateItem(1, realItem);
		}

		laidOutBank = layOutBank(currentLayout, bankItems);
		expectedLaidOutBank = createLaidOutBank(
			1, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(realItem) : realItem(realItem),
			2, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(realItem) : duplicateItem(realItem),
			3, isLayoutPlaceholder ? BankSlot.layoutPlaceholder(realItem) : duplicateItem(realItem)
		);
		assertEquals(expectedLaidOutBank, laidOutBank);

		checkCurrentLayout(1, realItem);
		checkCurrentLayout(2, realItem);
		checkCurrentLayout(3, realItem);
	}

	@Test
	public void testRemoveDuplicateItems()
	{
		List<Item> layoutItems = Arrays.asList(GAMES_NECKLACE_8, GAMES_NECKLACE_8_PH, MAGIC_LOGS);
		List<Item> realItems = Arrays.asList(GAMES_NECKLACE_8, GAMES_NECKLACE_8_PH, MAGIC_LOGS);
		for (Item targetLayoutItem : realItems)
		{
			for (int i = 0; i < layoutItems.size(); i++)
			{
				Item layoutItem = layoutItems.get(i);
				Item realItem = realItems.get(i);
				if (targetLayoutItem.equals(layoutItem) || targetLayoutItem.equals(realItem)) continue;
				testRemoveDuplicateItem(layoutItem, layoutItem);
				testRemoveDuplicateItem(layoutItem, realItem);
				testRemoveDuplicateItem(realItem, layoutItem);
				testRemoveDuplicateItem(realItem, realItem);
			}
		}
	}

	private void testRemoveDuplicateItem(Item layoutItem, Item realItem)
	{
		boolean isLayoutPlaceholder = realItem == null;
		if (realItem == null) realItem = layoutItem;
		currentLayout = generateLayout(
			layoutItem, 1,
			layoutItem, 2,
			layoutItem, 3
		);
		List<BankItemAndWidget> bankItems = isLayoutPlaceholder
			? Collections.emptyList()
			: generateBankItems(realItem);

		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, isLayoutPlaceholder ? layoutPlaceholder(realItem) : realItem(realItem),
			2, isLayoutPlaceholder ? layoutPlaceholder(realItem) : duplicateItem(realItem),
			3, isLayoutPlaceholder ? layoutPlaceholder(realItem) : duplicateItem(realItem)
		);
		System.out.println("real bank: " + laidOutBank);
		System.out.println("expected bank: " + expectedLaidOutBank);
		assertEquals(expectedLaidOutBank, laidOutBank);

		currentLayout.clearIndex(2);

		laidOutBank = layOutBank(currentLayout, bankItems);
		expectedLaidOutBank = createLaidOutBank(
			1, isLayoutPlaceholder ? layoutPlaceholder(realItem) : realItem(realItem),
//			2, isLayoutPlaceholder ? layoutPlaceholder(realItem) : duplicateItem(realItem),
			3, isLayoutPlaceholder ? layoutPlaceholder(realItem) : duplicateItem(realItem)
		);
		System.out.println("real bank: " + laidOutBank);
		System.out.println("expected bank: " + expectedLaidOutBank);
		assertEquals(expectedLaidOutBank, laidOutBank);

		checkCurrentLayout(1, realItem);
//		checkLayout(2, realItem);
		checkCurrentLayout(3, realItem);
	}

	@Test
    public void testDuplicatingLayoutPlaceholder() {
        currentLayout = generateLayout(
                GAMES_NECKLACE_5, 4
        );
        List<BankItemAndWidget> bankItems = generateBankItems(
        );

        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
        LaidOutBank expectedLaidOutBank = createLaidOutBank(
                4, layoutPlaceholder(GAMES_NECKLACE_5)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);

        System.out.println("layout before: " + currentLayout);
        currentLayout.duplicateItem(4);
        System.out.println("layout after: " + currentLayout);

        laidOutBank = layOutBank(currentLayout, bankItems);
        expectedLaidOutBank = createLaidOutBank(
                4, layoutPlaceholder(GAMES_NECKLACE_5),
                5, layoutPlaceholder(GAMES_NECKLACE_5)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);
    }

    // TODO might a test like this still hold value?
//    @Test
//    public void testThatItemsThatShowUpMoreThanOnceInTheBankDoNotTakeUpPlaceholderSlots() {
//        currentLayout = generateLayout(
//                new LayoutItem(STRANGE_LOCKPICK_USED, 4),
//                new LayoutItem(STRANGE_LOCKPICK_USED_PH, 7)
//        );
//        List<BankItemAndWidget> bankItems = Arrays.asList(
//                createBankItemWidget(STRANGE_LOCKPICK_USED),
//                createBankItemWidget(STRANGE_LOCKPICK_USED)
//        );
//
//        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
//        LaidOutBank expectedLaidOutBank = createLaidOutBank(
//                4, BankSlot.realItem(STRANGE_LOCKPICK_USED),
//                7, BankSlot.layoutPlaceholder(STRANGE_LOCKPICK_USED_PH)
//        );
//        System.out.println("real bank: " + laidOutBank);
//        System.out.println("expected bank: " + expectedLaidOutBank);
//        assertEquals(expectedLaidOutBank, laidOutBank);
//    }
//
//    @Test
//    public void testThatItemsThatShowUpInMultipleRealBankSlotsOnlyAppearOnceInLaidOutBankTab() {
//        currentLayout = generateLayout(
//                new LayoutItem(STRANGE_LOCKPICK_USED, 4),
//                new LayoutItem(STRANGE_LOCKPICK_FULL, 7)
//        );
//        List<BankItemAndWidget> bankItems = Arrays.asList(
//                createBankItemWidget(STRANGE_LOCKPICK_USED),
//                createBankItemWidget(STRANGE_LOCKPICK_FULL),
//                createBankItemWidget(STRANGE_LOCKPICK_FULL),
//                createBankItemWidget(STRANGE_LOCKPICK_USED)
//        );
//
//        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
//        LaidOutBank expectedLaidOutBank = createLaidOutBank(
//                4, BankSlot.realItem(STRANGE_LOCKPICK_USED),
//                7, BankSlot.realItem(STRANGE_LOCKPICK_FULL)
//        );
//        System.out.println("real bank: " + laidOutBank);
//        System.out.println("expected bank: " + expectedLaidOutBank);
//        assertEquals(expectedLaidOutBank, laidOutBank);
//    }
//
    // If I move a games neck (5) that is at a layout spot that is something else like games neck (1), then all games
    // neck (1) parts of the layout should be updated to be games neck (5), but the item it's dragged on to should
    // continue to use the original id in the layout.
    @Test
    public void testMoveDuplicateItemUpdatesActualItemId() {
        currentLayout = generateLayout(
                GAMES_NECKLACE_5, 4,
                GAMES_NECKLACE_5, 5,

                GAMES_NECKLACE_1, 6,
                GAMES_NECKLACE_1, 7
        );
        List<BankItemAndWidget> bankItems = generateBankItems(
                GAMES_NECKLACE_6,
                GAMES_NECKLACE_2
        );

        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
        LaidOutBank expectedLaidOutBank = createLaidOutBank(
                4, realItem(GAMES_NECKLACE_6),
                5, duplicateItem(GAMES_NECKLACE_6),

                6, realItem(GAMES_NECKLACE_2),
                7, duplicateItem(GAMES_NECKLACE_2)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);

        System.out.println("layout before: " + currentLayout);
        currentLayout.moveItem(4, 6, new BankItem(GAMES_NECKLACE_6.id));
        System.out.println("layout after: " + currentLayout);

        laidOutBank = layOutBank(currentLayout, bankItems);
        expectedLaidOutBank = createLaidOutBank(
                4, realItem(GAMES_NECKLACE_2),
                5, realItem(GAMES_NECKLACE_6),

                6, duplicateItem(GAMES_NECKLACE_6),
                7, duplicateItem(GAMES_NECKLACE_2)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);

        checkCurrentLayout(4, GAMES_NECKLACE_1);
        checkCurrentLayout(5, GAMES_NECKLACE_6);
        checkCurrentLayout(6, GAMES_NECKLACE_6);
        checkCurrentLayout(7, GAMES_NECKLACE_1);
    }

    // movement tests should always leave empty spots in the early slots of the bank, so that you don't get false
    // negatives from an item being moved to index 0.
    @Test
    public void testMoveVariantItemOntoLayoutPlaceholderOfSameBaseVariant() {
        currentLayout = generateLayout(
                GAMES_NECKLACE_5, 4,
                GAMES_NECKLACE_1, 5
        );
        List<BankItemAndWidget> bankItems = generateBankItems(
                GAMES_NECKLACE_5
        );

        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
        LaidOutBank expectedLaidOutBank = createLaidOutBank(
                4, realItem(GAMES_NECKLACE_5),
                5, layoutPlaceholder(GAMES_NECKLACE_1)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);

        System.out.println("layout before: " + currentLayout);
        currentLayout.moveItem(4, 5, new BankItem(GAMES_NECKLACE_5.id));
        System.out.println("layout after: " + currentLayout);

        laidOutBank = layOutBank(currentLayout, bankItems);
        expectedLaidOutBank = createLaidOutBank(
                5, realItem(GAMES_NECKLACE_5),
                4, layoutPlaceholder(GAMES_NECKLACE_1)
        );
        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);
        assertEquals(expectedLaidOutBank, laidOutBank);
    }

    @Test
    public void testLayout() {
        currentLayout = generateLayout(
                GAMES_NECKLACE_8, 1,
                GAMES_NECKLACE_1, 8,
                GAMES_NECKLACE_1_PH, 9
        );
        List<BankItemAndWidget> bankItems = generateBankItems(
                GAMES_NECKLACE_8,
                GAMES_NECKLACE_1_PH
        );

        LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);

        Map<Integer, BankSlot> expectedBank = new HashMap<>();
        expectedBank.put(1, realItem(GAMES_NECKLACE_8));
        expectedBank.put(8, layoutPlaceholder(GAMES_NECKLACE_1));
        expectedBank.put(9, realItem(GAMES_NECKLACE_1_PH));
        LaidOutBank expectedLaidOutBank = new LaidOutBank(expectedBank);

        System.out.println("real bank: " + laidOutBank);
        System.out.println("expected bank: " + expectedLaidOutBank);

        assertEquals(expectedLaidOutBank, laidOutBank);
    }

	private LaidOutBank createLaidOutBank(Object... arr)
    {
        Map<Integer, BankSlot> map = new HashMap<>();
        assert arr.length % 2 == 0;
        for (int i = 0; i < arr.length; i += 2)
        {
            int index = (int) arr[i];
            BankSlot bankSlot = (BankSlot) arr[i + 1];
            map.put(index, bankSlot);
        }
        return new LaidOutBank(map);
    }

//    @Value
//    public static class BankSlot {
//        enum Type {
//            REAL_ITEM, DUPLICATE_ITEM, LAYOUT_PLACEHOLDER
//        }
//
//        Type type;
//		BankItem bankItem;
//        int quantity;
//
//        public static BankSlot realItem(Item item) {
//            int quantity = item.placeholder ? 0 : 1;
//            return realItem(item, quantity);
//        }
//
//		public static BankSlot realItem(Item item, int quantity) {
//			return new BankSlot(Type.REAL_ITEM, new BankItem(item.id), quantity);
//        }
//
//		public static BankSlot realItem(Widget widget) {
//            return new BankSlot(Type.REAL_ITEM, new BankItem(widget.getItemId()), widget.getItemQuantity());
//        }
//
//		public static BankSlot realItem(BankItem bankItem) {
//			int quantity = isPlaceholder(bankItem.getItemId()) ? 0 : 1;
//			return new BankSlot(Type.REAL_ITEM, bankItem, quantity);
//		}
//
//		// TODO what if I have a 1-dose ppot as a fake with duplicates - will a 4-dose fill all the spots of that?
//
//        // TODO what if i create a duplicate when the wrong item is in a fake item? e.g. 1-dose ppot is in layout but there is a 4-dose appearing there, and I then duplicate that?
//
//        public static BankSlot duplicateItem(Item item) {
//            return duplicateItem(item, item.placeholder ? 0 : 1);
//        }
//
//		public static BankSlot duplicateItem(Item item, int quantity) {
//			return duplicateItem(new BankItem(item.id), quantity);
//		}
//
//		public static BankSlot duplicateItem(BankItem bankItem) {
//			return duplicateItem(bankItem, isPlaceholder(bankItem.getItemId()) ? 0 : 1);
//		}
//
//		public static BankSlot duplicateItem(BankItem bankItem, int quantity) {
//			return new BankSlot(Type.DUPLICATE_ITEM, bankItem, quantity);
//		}
//
//		public static BankSlot layoutPlaceholder(BankItem bankItem) {
//            return new BankSlot(Type.LAYOUT_PLACEHOLDER, bankItem, -1);
//        }
//
//        public static BankSlot layoutPlaceholder(Item item) {
//            return layoutPlaceholder(new BankItem(item.id));
//        }
//
//        @Override
//        public String toString()
//        {
//            Item item = itemsById.get(bankItem.getItemId());
////            System.out.println("tostring: " + id + " " + item + " " + type);
//            String typeString = type == Type.LAYOUT_PLACEHOLDER ? " lph" : type == Type.REAL_ITEM ? "" : " dup";
//            return "{" +
//                    item.name + " " + bankItem.getUnstackableIndex() + typeString +
//                    (type == Type.REAL_ITEM ? " (" + quantity + ")" : "") +
//                    '}';
//        }
//    }
//
	private BankTagLayoutsPlugin.BankItemAndWidget createBankItemWidget(BankItem bankItem)
	{
		return new BankTagLayoutsPlugin.BankItemAndWidget(bankItem, tempName(bankItem.getItemId(), isPlaceholder(bankItem.getItemId()) ? 0 : 1));
	}

	private BankTagLayoutsPlugin.BankItemAndWidget createBankItemWidget(Item item)
    {
        return new BankTagLayoutsPlugin.BankItemAndWidget(new BankItem(item.id), tempName(item.id, item.placeholder ? 0 : 1));
    }

	private BankTagLayoutsPlugin.BankItemAndWidget createBankItemWidgetUnstackableDuplicate(Item item, int n)
	{
		return new BankTagLayoutsPlugin.BankItemAndWidget(new BankItem(item.id, n), tempName(item.id, item.placeholder ? 0 : 1));
	}

	private BankTagLayoutsPlugin.BankItemAndWidget createBankItemWidgetWithQuantity(Item item, int quantity)
    {
		return new BankTagLayoutsPlugin.BankItemAndWidget(new BankItem(item.id), tempName(item.id, quantity));
    }

	private Widget tempName(int itemId, int quantity)
	{
		Widget widget = Mockito.mock(Widget.class);
//		Mockito.when(widget.getItemId()).thenReturn(itemId);
		Mockito.when(widget.getItemQuantity()).thenReturn(quantity);
		return widget;
	}

	private LaidOutBank layOutBank(Layout layout, List<BankItemAndWidget> bankItems)
    {
        return new LaidOutBank(plugin.generateBankSlots(layout, bankItems, false));
    }

    private static class Item {
        final String name;
        final int id;
        final int placeholderId;
        final boolean placeholder;
        final int variantClass;

        public Item(String name, int id, int placeholderId, int variantClass) {
            this(name, id, placeholderId, variantClass, false);
        }

        public Item(String name, int id, int placeholderId, int variantClass, boolean placeholder) {
            this.name = name;
            this.id = id;
            this.placeholderId = placeholderId;
            this.variantClass = variantClass;
            this.placeholder = placeholder;
            itemsById.put(this.id, this);
        }
    }

	private Layout generateLayout(Object... objects) {
        Layout layout = new Layout();
		for (int i = 0; i < objects.length; i += 2)
		{
			if (objects[i] instanceof BankItem)
			{
				layout.putItem((Integer) objects[i + 1], (BankItem) objects[i]);
			} else {
				layout.putItem((Integer) objects[i + 1], new BankItem(((Item) objects[i]).id));
			}
		}
        return layout;
    }

	private void checkCurrentLayout(int index, BankItem bankItem)
	{
		checkLayout(currentLayout, index, bankItem);
	}

	private void checkCurrentLayout(int index, Item item)
    {
        checkLayout(currentLayout, index, item);
    }

	private void checkLayout(Layout layout, int index, BankItem bankItem)
	{
//		checkLayout(layout, index, bankItem.getItemId());
		assertEquals(bankItem, layout.getItemAtIndex(index));
	}

	private void checkLayout(Layout layout, int index, Item item)
	{
		checkLayout(layout, index, new BankItem(item.id));
	}

//	private void checkLayout(Layout layout, int index, int itemId)
//	{
//		assertEquals(itemId, layout.getItemAtIndex(index));
//	}
//
	private static class LaidOutBank {
        final Map<Integer, BankSlot> map;

        public LaidOutBank(Map<Integer, BankSlot> map) {
            this.map = map;
        }

        @Override
        public String toString()
        {
            String result = "\n";
            for (Map.Entry<Integer, BankSlot> entry : map.entrySet())
            {
                result += entry.getKey() + ": " + entry.getValue().toString() + "\n";
            }
            return result;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LaidOutBank that = (LaidOutBank) o;
            return Objects.equals(map, that.map);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(map);
        }

        public void add(int index, BankSlot bankSlot)
		{
    		map.put(index, bankSlot);
		}
    }

    private ItemComposition generateItemComposition(int itemId)
    {
        Item item = itemsById.get(itemId);
        if (itemId == -1) {
        	new Exception().printStackTrace(System.out);
		}
        System.out.println("item for " + itemId + " is " + item);
        ItemComposition mock = Mockito.mock(ItemComposition.class);
        Mockito.when(mock.getName()).thenReturn(item.name);
        Mockito.when(mock.getPlaceholderTemplateId()).thenReturn(item.placeholder ? 14401 : -1);
        Mockito.when(mock.getPlaceholderId()).thenReturn(item.placeholderId);
		Mockito.when(mock.getId()).thenReturn(item.id);
		return mock;
    }

    @Test
	public void testLayoutGeneratorWithDuplicateItems() {
		LayoutGenerator layoutGenerator = new LayoutGenerator(plugin);
		Layout layout = layoutGenerator.basicLayout(Arrays.asList(GAMES_NECKLACE_8.id, RUNE_PLATEBODY.id), Arrays.asList(GAMES_NECKLACE_8.id, MAGIC_LOGS.id, MAGIC_LOGS.id), Layout.emptyLayout(), 28);
		checkLayout(layout, 0, GAMES_NECKLACE_8);
		checkLayout(layout, 8, RUNE_PLATEBODY);
		checkLayout(layout, 16, GAMES_NECKLACE_8);
		checkLayout(layout, 24, MAGIC_LOGS);
		checkLayout(layout, 17, MAGIC_LOGS);
	}

	@Test
	public void testLayoutGeneratorWithDuplicateLimit() {
		LayoutGenerator layoutGenerator = new LayoutGenerator(plugin);
		Layout layout = layoutGenerator.basicLayout(Collections.emptyList(), Arrays.asList(
			GAMES_NECKLACE_8.id,
			MAGIC_LOGS.id,
			MAGIC_LOGS.id,
			GAMES_NECKLACE_8.id,
			GAMES_NECKLACE_8.id,
			GAMES_NECKLACE_8.id,
			MAGIC_LOGS.id,
			MAGIC_LOGS.id,
			MAGIC_LOGS.id,
			MAGIC_LOGS.id,
			GAMES_NECKLACE_8.id), Layout.emptyLayout(), 3);
		checkLayout(layout, 0, GAMES_NECKLACE_8);
		checkLayout(layout, 8, MAGIC_LOGS);
		checkLayout(layout, 1, MAGIC_LOGS);
		checkLayout(layout, 9, GAMES_NECKLACE_8);
		checkLayout(layout, 2, GAMES_NECKLACE_8);
		checkLayout(layout, 10, GAMES_NECKLACE_8);
		checkLayout(layout, 3, MAGIC_LOGS);
		checkLayout(layout, 11, GAMES_NECKLACE_8);
		assertEquals(8, layout.allPairs().size());
	}

	/**
	 * https://github.com/geheur/bank-tag-custom-layouts/issues/32
	 */
	@Test
	public void testLayoutGeneratorWithMoreItemsThanDuplicateLimitAsTheFirstItemsInTheInventory() {
		LayoutGenerator layoutGenerator = new LayoutGenerator(plugin);
		Layout layout = layoutGenerator.basicLayout(Collections.emptyList(), Arrays.asList(
			MAGIC_LOGS.id,
			MAGIC_LOGS.id,
			MAGIC_LOGS.id,
			MAGIC_LOGS.id
			), Layout.emptyLayout(), 3);
		checkLayout(layout, 0, MAGIC_LOGS);
		assertEquals(1, layout.allPairs().size());
	}

	@Test
	public void testLayoutGeneratorWithEmptyInventory() {
		LayoutGenerator layoutGenerator = new LayoutGenerator(plugin);

		Layout layout = layoutGenerator.basicLayout(Collections.emptyList(), Arrays.asList(), Layout.emptyLayout(), 3);
		assertEquals(0, layout.allPairs().size());

		layout = layoutGenerator.basicLayout(Collections.emptyList(), Arrays.asList(-1), Layout.emptyLayout(), 3);
		assertEquals(0, layout.allPairs().size());

		layout = layoutGenerator.basicLayout(Collections.emptyList(), Collections.nCopies(28, -1), Layout.emptyLayout(), 3);
		assertEquals(0, layout.allPairs().size());
	}

	@Test
	public void testLayoutGeneratorDoesntDeleteDuplicateItems() {
		LayoutGenerator layoutGenerator = new LayoutGenerator(plugin);
		Layout initialLayout = generateLayout(
			MAGIC_LOGS, 0,
			MAGIC_LOGS, 8,
			MAGIC_LOGS, 1,

			MAGIC_LOGS, 9,
			MAGIC_LOGS, 2
		);
		Layout layout = layoutGenerator.basicLayout(Collections.emptyList(), Arrays.asList(
			MAGIC_LOGS.id,
			MAGIC_LOGS.id,
			MAGIC_LOGS.id
		), initialLayout, 3);
		checkLayout(layout, 0, MAGIC_LOGS);
		checkLayout(layout, 8, MAGIC_LOGS);
		checkLayout(layout, 1, MAGIC_LOGS);
		checkLayout(layout, 9, MAGIC_LOGS);
		checkLayout(layout, 2, MAGIC_LOGS);
		assertEquals(5, layout.allPairs().size());
	}

	@Test
	public void testLayoutGeneratorWorksWithVariantItems() {
		LayoutGenerator layoutGenerator = new LayoutGenerator(plugin);
		Layout initialLayout = generateLayout(
			GAMES_NECKLACE_8, 0,
			GAMES_NECKLACE_7, 8
		);
		Layout layout = layoutGenerator.basicLayout(Collections.emptyList(), Arrays.asList(
			GAMES_NECKLACE_8.id,
			GAMES_NECKLACE_6.id
		), initialLayout, 3);
		checkLayout(layout, 0, GAMES_NECKLACE_8);
		checkLayout(layout, 8, GAMES_NECKLACE_6);
		System.out.println("layout: " + layout);
		assertEquals(2, layout.allPairs().size());
	}

	@Test
	public void testUnstackableItemsInitialLayoutGeneration() {
		currentLayout = generateLayout(
		);
		List<BankItemAndWidget> bankItems = generateBankItems(
			MAGIC_LOGS,
			MAGIC_LOGS
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			0, realItem(MAGIC_LOGS),
			1, realItem(new BankItem(MAGIC_LOGS.id, 1))
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableItemsPlaceholder() {
		currentLayout = generateLayout(
			MAGIC_LOGS, 1,
			MAGIC_LOGS_PH, 2
		);
		List<BankItemAndWidget> bankItems = generateBankItems(
			MAGIC_LOGS,
			MAGIC_LOGS
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(MAGIC_LOGS),
			2, realItem(new BankItem(MAGIC_LOGS.id, 1))
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableItemsPlaceholder2() {
		currentLayout = generateLayout(
			MAGIC_LOGS_PH, 1,
			MAGIC_LOGS, 2
		);
		List<BankItemAndWidget> bankItems = generateBankItems(
			MAGIC_LOGS,
			MAGIC_LOGS
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(new BankItem(MAGIC_LOGS.id, 1)),
			2, realItem(MAGIC_LOGS)
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableVariantItemsPlaceholder() {
		currentLayout = generateLayout(
			SERP_HELM, 1,
			SERP_HELM_PH, 2
		);
		List<BankItemAndWidget> bankItems = generateBankItems(
			SERP_HELM,
			SERP_HELM
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(SERP_HELM),
			2, realItem(new BankItem(SERP_HELM.id, 1))
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableVariantItemsPlaceholder2() {
		currentLayout = generateLayout(
			SERP_HELM_PH, 1,
			SERP_HELM, 2
		);
		List<BankItemAndWidget> bankItems = generateBankItems(
			SERP_HELM,
			SERP_HELM
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(new BankItem(SERP_HELM.id, 1)),
			2, realItem(SERP_HELM)
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableIntoDuplicatePlaceholders() {
		currentLayout = generateLayout(
			MAGIC_LOGS_PH, 1,
			MAGIC_LOGS_PH, 2
		);
		List<BankItemAndWidget> bankItems = generateBankItems(
			MAGIC_LOGS,
			MAGIC_LOGS
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			0, realItem(new BankItem(MAGIC_LOGS.id, 1)),
			1, realItem(new BankItem(MAGIC_LOGS.id, 0)),
			2, duplicateItem(new BankItem(MAGIC_LOGS.id, 0))
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableItemsVariant() {
		currentLayout = generateLayout(
			SERP_HELM, 1,
			new BankItem(SERP_HELM.id, 1), 2,
			MAGMA_SERP_HELM, 3,
			new BankItem(MAGMA_SERP_HELM.id, 1), 4
		);
		// TODO what if it's one serp one magma (check both orderings).
		List<BankItemAndWidget> bankItems = generateBankItems(
			SERP_HELM,
			SERP_HELM_PH,
			MAGMA_SERP_HELM,
			MAGMA_SERP_HELM
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(SERP_HELM),
			2, realItem(SERP_HELM_PH),
			3, realItem(MAGMA_SERP_HELM),
			4, realItem(new BankItem(MAGMA_SERP_HELM.id, 1))
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableItemsPlaceholderVariant() {
		currentLayout = generateLayout(
			SERP_HELM, 1,
			SERP_HELM_PH, 2
		);
		// TODO what if it's one serp one magma (check both orderings).
		List<BankItemAndWidget> bankItems = generateBankItems(
			MAGMA_SERP_HELM,
			MAGMA_SERP_HELM
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(MAGMA_SERP_HELM),
			2, realItem(new BankItem(MAGMA_SERP_HELM.id, 1))
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableItemsPlaceholderVariant2() {
		currentLayout = generateLayout(
			SERP_HELM, 1,
			SERP_HELM_PH, 2
		);
		// TODO what if it's one serp one magma (check both orderings).
		List<BankItemAndWidget> bankItems = generateBankItems(
			SERP_HELM,
			MAGMA_SERP_HELM
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(SERP_HELM),
			2, realItem(MAGMA_SERP_HELM)
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableItemsPlaceholderVariant3() {
		currentLayout = generateLayout(
			SERP_HELM, 1,
			SERP_HELM_PH, 2
		);
		// TODO what if it's one serp one magma (check both orderings).
		List<BankItemAndWidget> bankItems = generateBankItems(
			MAGMA_SERP_HELM,
			SERP_HELM
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(SERP_HELM),
			2, realItem(MAGMA_SERP_HELM)
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	@Test
	public void testUnstackableItemsPlaceholderVariant4() {
		currentLayout = generateLayout(
			SERP_HELM, 1,
			SERP_HELM_PH, 2
		);
		// TODO what if it's one serp one magma (check both orderings).
		List<BankItemAndWidget> bankItems = generateBankItems(
			SERP_HELM,
			SERP_HELM
		);
		LaidOutBank laidOutBank = layOutBank(currentLayout, bankItems);
		LaidOutBank expectedLaidOutBank = createLaidOutBank(
			1, realItem(SERP_HELM),
			2, realItem(new BankItem(SERP_HELM.id, 1))
		);
		assertEquals(expectedLaidOutBank, laidOutBank);
	}

	private List<BankItemAndWidget> generateBankItems(Item... items)
	{
		List<BankItemAndWidget> bankItems = new ArrayList<>();
		Map<Item, Integer> counts = new HashMap<>();
		for (Item item : items)
		{
			int count = counts.getOrDefault(item, 0);
			bankItems.add(createBankItemWidgetUnstackableDuplicate(item, count));
			counts.put(item, ++count);
		}
		return bankItems;
	}

	private BankSlot realItem(Item item)
	{
		return realItem(new BankItem(item.id));
	}

	private BankSlot realItem(BankItem bankItem)
	{
		return BankSlot.realItem(new BankItemAndWidget(bankItem, null));
	}

	private BankSlot realItem(Item item, int quantity)
	{
		// we don't use the quantity field in the test.
		return realItem(item);
	}

	private BankSlot duplicateItem(BankItem bankItem)
	{
		return BankSlot.duplicateItem(bankItem, isPlaceholder(bankItem.getItemId()) ? 0 : 1);
	}

	private BankSlot duplicateItem(Item item)
	{
		return duplicateItem(new BankItem(item.id));
	}

	private BankSlot layoutPlaceholder(Item item)
	{
		return BankSlot.layoutPlaceholder(new BankItem(item.id));
	}
}
