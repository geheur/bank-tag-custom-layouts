package com.banktaglayouts.combo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.plugins.banktags.BankTag;

/**
 * Membership filter for a CORE/built-in layout tab that hosts combo cells.
 *
 * <p>RuneLite combines this with the tab's real tag via OR (see {@code BankTagsPlugin.buildSearchFilterBankTag}),
 * so it can only ADD items to the tab, never hide tagged ones. We exploit that: combo member items are NOT
 * individually tagged, and this tag makes only the currently-resolved OWNED winners pass the bank filter so
 * they render as real, withdrawable widgets at their cells. Non-winner members and leftover placeholders are
 * never in the set, so RuneLite never pulls them into the tab and never auto-floats them.
 *
 * <p>The winner set is recomputed live from the bank by {@code maintainComboCoreTab} on every bank change and
 * swapped in here; {@link #contains(int)} runs in the bank-build filter, so it must be O(1).
 */
public final class ComboBankTag implements BankTag
{
	private volatile Set<Integer> ownedWinners = Collections.emptySet();

	/** Replaces the set of owned-winner item ids that should pass the tab's bank filter. */
	public void setOwnedWinners(Set<Integer> winners)
	{
		this.ownedWinners = winners == null ? Collections.emptySet() : new HashSet<>(winners);
	}

	@Override
	public boolean contains(int itemId)
	{
		return ownedWinners.contains(itemId);
	}
}
