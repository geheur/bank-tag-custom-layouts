/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Kamiel
 * Copyright (c) 2019, Rami <https://github.com/Rami-J>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.banktaglayouts;

import com.banktaglayouts.BankTagLayoutsPlugin.LayoutableThing;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

public class BtlMenuSwapper
{
	private static final int MENU_HEADER_HEIGHT = 19;
	private static final int MENU_OPTION_HEIGHT = 15;

	private static final int EXTRA_OPTION_WIDTH = 20;

	@Inject
	private Client client;

	@Inject
	private BankTagLayoutsPlugin plugin;

	@Inject
	private MenuSwapperOverlay menuSwapperOverlay;

	@Inject
	private OverlayManager overlayManager;

	public void startUp()
	{
		overlayManager.add(menuSwapperOverlay);
	}

	public void shutDown()
	{
		overlayManager.remove(menuSwapperOverlay);
	}

	@Getter
	@RequiredArgsConstructor
	public enum WithdrawMode
	{
		WITHDRAW_1("Withdraw-1", MenuAction.CC_OP, 2, "1"),
		WITHDRAW_5("Withdraw-5", MenuAction.CC_OP, 3, "5"),
		WITHDRAW_10("Withdraw-10", MenuAction.CC_OP, 4, "10"),
		WITHDRAW_X("Withdraw-X", MenuAction.CC_OP, 5, "x"),
		WITHDRAW_SET_X("Withdraw-Set-X", MenuAction.CC_OP_LOW_PRIORITY, 6, "c"),
		WITHDRAW_ALL("Withdraw-All", MenuAction.CC_OP_LOW_PRIORITY, 7, "a"),
		WITHDRAW_ALL_BUT_1("Withdraw-All-But-1", MenuAction.CC_OP_LOW_PRIORITY, 8, "b"),
		WITHDRAW_PLACEHOLDER("Placeholder", MenuAction.CC_OP_LOW_PRIORITY, 9, "p"),
		;

		private final String name;
		private final MenuAction menuAction;
		private final int identifier;
		final String saveString;

		// Jagex's left-click swap works by inserting a menu entry with identifier 1 at the top of the menu. This DOES NOT REMOVE the normal menu entry for that quantity.
		// This does not happen with left-click quantity "1" but that entry remains in the array to make the indexes line up.
		private static final WithdrawMode[] JAGEX_BANK_LEFT_CLICK_SWAP_MODE = {
			WithdrawMode.WITHDRAW_1,
			WithdrawMode.WITHDRAW_5,
			WithdrawMode.WITHDRAW_10,
			WithdrawMode.WITHDRAW_X,
			WithdrawMode.WITHDRAW_ALL
		};

		public static WithdrawMode fromMenuEntry(MenuEntry entry, int jagexBankLeftClickVarbit)
		{
			if (entry.getIdentifier() == 1) {
				return JAGEX_BANK_LEFT_CLICK_SWAP_MODE[jagexBankLeftClickVarbit];
			}

			for (WithdrawMode withdrawMode : values())
			{
				if (withdrawMode.getIdentifier() == entry.getIdentifier()) {
					return withdrawMode;
				}
			}
			return null;
		}

		public static WithdrawMode fromSaveString(String saveString)
		{
			for (WithdrawMode withdrawMode : values())
			{
				if (withdrawMode.saveString.equals(saveString)) {
					return withdrawMode;
				}
			}
			return null;
		}
	}

	/**
	 * If true, and the menu is open, then it is a bank withdraw menu.
	 */
	@Getter
	private boolean isWithdrawMenu = false;
	/** Only contains valid data when isWithdrawMenu == true and client.isMenuOpen() */
	@Getter
	private int menuBankIndex = 0;
	/** Only contains valid data when isWithdrawMenu == true and client.isMenuOpen() */
	@Getter
	private int jagexBankSwapVarbit = 0;

	@Subscribe
	public void onMenuOpened(MenuOpened e)
	{
		isWithdrawMenu = false;
		if (plugin.getCurrentLayoutableThing() == null) return;
		if (!client.isKeyPressed(KeyCode.KC_SHIFT)) return;
		MenuEntry[] menuEntries = e.getMenuEntries();
		for (MenuEntry menuEntry : menuEntries)
		{
			if (menuEntry.getOption().startsWith("Withdraw")) {
				isWithdrawMenu = true;
				menuBankIndex = plugin.getIndexForMousePosition();
				// This value may be wrong if the menu is opened soon after the varbit has changed value, because the change of the varbit doesn't actually take effect until the next game tick. I have decided I do not care about this edge case, so I have not made any accommodations for this quirk.
				jagexBankSwapVarbit = client.getVarbitValue(6590);
				break;
			}
		}

		if (!isWithdrawMenu) return;

		Font runescapeFont = FontManager.getRunescapeBoldFont();
		FontRenderContext fontRenderContext = new FontRenderContext(null, false, false);
		double maxWidth = 0;
		for (MenuEntry menuEntry : menuEntries)
		{
			double width = runescapeFont.getStringBounds(Text.removeTags(menuEntry.getOption()) + " " + Text.removeTags(menuEntry.getTarget()), fontRenderContext).getWidth();
			if (width > maxWidth) maxWidth = width;
		}

		double cancelWidth = runescapeFont.getStringBounds(Text.removeTags(menuEntries[0].getOption()) + " " + Text.removeTags(menuEntries[0].getTarget()), fontRenderContext).getWidth();
		double spaceWidth = runescapeFont.getStringBounds(" ", fontRenderContext).getWidth();
		int spacesCount = (int) ((maxWidth + EXTRA_OPTION_WIDTH - cancelWidth) / spaceWidth);
		byte[] padding = new byte[spacesCount];
		Arrays.fill(padding, (byte) ' ');
		menuEntries[0].setOption(menuEntries[0].getOption() + new String(padding));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (client.isMenuOpen() && isWithdrawMenu) {
			Layout layout = plugin.getCurrentLayoutableThing().getLayout();
			MenuEntry extraOptionMenuEntry = getExtraOptionMenuEntry(client.getMouseCanvasPosition());
			if (extraOptionMenuEntry == e.getMenuEntry()) {
				WithdrawMode selectedMode = WithdrawMode.fromMenuEntry(extraOptionMenuEntry, jagexBankSwapVarbit);
				WithdrawMode existingMode = layout.getWithdrawMode(menuBankIndex);
				if (selectedMode == existingMode) {
					layout.setWithdrawMode(menuBankIndex, null);
				} else {
					layout.setWithdrawMode(menuBankIndex, selectedMode);
				}

				e.consume();
			}
		}
	}

	public MenuEntry getExtraOptionMenuEntry(Point mouseCanvasPosition)
	{
		if (mouseCanvasPosition.getX() < client.getMenuX() + client.getMenuWidth() - EXTRA_OPTION_WIDTH || mouseCanvasPosition.getX() > client.getMenuX() + client.getMenuWidth()) return null;

		// this index is from the top, so it is reversed from what client.getMenuEntries() uses.
		int index = (mouseCanvasPosition.getY() - client.getMenuY() - MENU_HEADER_HEIGHT) / MENU_OPTION_HEIGHT;
		if (index < 0 || index >= client.getMenuEntries().length) return null;
		index = client.getMenuEntries().length - 1 - index; // reverse index.
		return client.getMenuEntries()[index];
	}

	public Rectangle getExtraOptionMenuEntryClickbox(int index)
	{
		index = client.getMenuEntries().length - 1 - index;
		return new Rectangle(client.getMenuX() + client.getMenuWidth() - EXTRA_OPTION_WIDTH, client.getMenuY() + MENU_HEADER_HEIGHT + index * MENU_OPTION_HEIGHT, EXTRA_OPTION_WIDTH, MENU_OPTION_HEIGHT);
	}

	// TODO real bank swaps.
	//      variation items.
	//      How to show whether an existing swap is general or specific?
	//      Check that it works on all types of storage.
	//      Maybe even make it work for the seed vault?
	// TODO deposit swaps.
	// batch 2
	// TODO autolayout.
	//      show quantity in preview.
	//      deposit swaps - equip swap.
	// TODO reset all custom quantities?
	// TODO allow quantity swapping on placeholders and layout placeholders.
	private WithdrawMode getWithdrawMode(int index)
	{
		LayoutableThing layoutableThing = plugin.getCurrentLayoutableThing();
		if (layoutableThing != null) {
			WithdrawMode layoutWithdrawMode = layoutableThing.getLayout().getWithdrawMode(index);
			if (layoutWithdrawMode != null) return layoutWithdrawMode;
		}
		return null;
	}

	@Subscribe(priority = 1) // These swaps should be overrideable so do them first.
	public void onClientTick(ClientTick clientTick)
	{
		// The menu is not rebuilt when it is open, so don't swap or else it will
		// repeatedly swap entries
		if (client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen() || plugin.getCurrentLayoutableThing() == null)
		{
			return;
		}

		WithdrawMode withdrawMode = getWithdrawMode(plugin.getIndexForMousePosition());
		if (withdrawMode == null) return;

		for (MenuEntry entry : client.getMenuEntries())
		{
			swapBank(entry, entry.getType(), withdrawMode);
		}
	}

	private void swapBank(MenuEntry menuEntry, MenuAction type, WithdrawMode withdrawMode)
	{
		if (type != MenuAction.CC_OP && type != MenuAction.CC_OP_LOW_PRIORITY)
		{
			return;
		}

		// Deposit- op 1 is the current withdraw amount 1/5/10/x
		if (type == MenuAction.CC_OP && menuEntry.getIdentifier() == 1
			&& menuEntry.getOption().startsWith("Withdraw"))
		{
			final MenuAction action = withdrawMode.getMenuAction();
			final int opId = withdrawMode.getIdentifier();
			bankModeSwap(action, opId);
		}
	}

	private void bankModeSwap(MenuAction entryType, int entryIdentifier)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();

		for (int i = menuEntries.length - 1; i >= 0; --i)
		{
			MenuEntry entry = menuEntries[i];

			if (entry.getType() == entryType && entry.getIdentifier() == entryIdentifier)
			{
				// Raise the priority of the op so it doesn't get sorted later
				entry.setType(MenuAction.CC_OP);

				menuEntries[i] = menuEntries[menuEntries.length - 1];
				menuEntries[menuEntries.length - 1] = entry;

				client.setMenuEntries(menuEntries);
				break;
			}
		}
	}
}
