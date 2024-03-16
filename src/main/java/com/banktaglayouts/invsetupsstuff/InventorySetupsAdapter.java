/*
 * Copyright (c) 2019, dillydill123 <https://github.com/dillydill123>
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
package com.banktaglayouts.invsetupsstuff;

import com.banktaglayouts.BankTagLayoutsPlugin;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import inventorysetupz.InventorySetup;
import inventorysetupz.InventorySetupsItem;
import inventorysetupz.InventorySetupsVariationMapping;
import inventorysetupz.serialization.InventorySetupItemSerializable;
import inventorysetupz.serialization.InventorySetupItemSerializableTypeAdapter;
import inventorysetupz.serialization.InventorySetupSerializable;
import inventorysetupz.serialization.LongTypeAdapter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class InventorySetupsAdapter {

    public static final String CONFIG_GROUP = "inventorysetups";
	public static final String CONFIG_KEY_SETUPS_V3_PREFIX = "setupsV3_";
	public static final String CONFIG_KEY_SETUPS_ORDER_V3 = "setupsOrderV3_";

	private final BankTagLayoutsPlugin plugin;

	private Gson gson;

	public InventorySetupsAdapter(BankTagLayoutsPlugin plugin) {
		this.plugin = plugin;
	}

	// This method does not exist anywhere in inventory setups.
    public InventorySetup getInventorySetup(String name)
    {
		if (gson == null) this.gson = plugin.gson.newBuilder().registerTypeAdapter(long.class, new LongTypeAdapter()).registerTypeAdapter(InventorySetupItemSerializable.class, new InventorySetupItemSerializableTypeAdapter()).create();
		return loadSetupByName(name);
    }

	public boolean setupContainsItem(final InventorySetup setup, int itemID)
	{
		// So place holders will show up in the bank.
		itemID = plugin.itemManager.canonicalize(itemID);

		// Check if this item (inc. placeholder) is in the additional filtered items
		if (additionalFilteredItemsHasItem(itemID, setup.getAdditionalFilteredItems()))
		{
			return true;
		}

		// check the rune pouch to see if it has the item (runes in this case)
		if (setup.getRune_pouch() != null)
		{
			if (checkIfContainerContainsItem(itemID, setup.getRune_pouch()))
			{
				return true;
			}
		}

		// check the bolt pouch to see if it has the item (bolts in this case)
		if (setup.getBoltPouch() != null)
		{
			if (checkIfContainerContainsItem(itemID, setup.getBoltPouch()))
			{
				return true;
			}
		}

		return checkIfContainerContainsItem(itemID, setup.getInventory()) ||
			checkIfContainerContainsItem(itemID, setup.getEquipment());
	}

	private boolean additionalFilteredItemsHasItem(int itemId, final Map<Integer, InventorySetupsItem> additionalFilteredItems)
	{
		final int canonicalizedId = plugin.itemManager.canonicalize(itemId);
		for (final Integer additionalItemKey : additionalFilteredItems.keySet())
		{
			boolean isFuzzy = additionalFilteredItems.get(additionalItemKey).isFuzzy();
			int addItemId = getProcessedID(isFuzzy, additionalFilteredItems.get(additionalItemKey).getId());
			int finalItemId = getProcessedID(isFuzzy, canonicalizedId);
			if (addItemId == finalItemId)
			{
				return true;
			}
		}
		return false;
	}

	private boolean checkIfContainerContainsItem(int itemID, final List<InventorySetupsItem> setupContainer)
	{
		// So place holders will show up in the bank.
		itemID = plugin.itemManager.canonicalize(itemID);

		for (final InventorySetupsItem item : setupContainer)
		{
			// For equipped weight reducing items or noted items in the inventory
			int setupItemId = plugin.itemManager.canonicalize(item.getId());
			if (getProcessedID(item.isFuzzy(), itemID) == getProcessedID(item.isFuzzy(), setupItemId))
			{
				return true;
			}
		}

		return false;
	}

	private int getProcessedID(boolean isFuzzy, int itemId)
	{
		// use fuzzy mapping if needed
		if (isFuzzy)
		{
			return InventorySetupsVariationMapping.map(itemId);
		}

		return itemId;
	}

	private InventorySetup loadV3Setup(String configKey)
	{
		final String storedData = plugin.configManager.getConfiguration(CONFIG_GROUP, configKey);
		try
		{
			return InventorySetupSerializable.convertToInventorySetup(gson.fromJson(storedData, InventorySetupSerializable.class));
		}
		catch (Exception e)
		{
			log.error(String.format("Exception occurred while loading %s", configKey), e);
			throw e;
		}
	}

	private InventorySetup loadSetupByName(String name) {
		final String wholePrefix = ConfigManager.getWholeKey(CONFIG_GROUP, null, CONFIG_KEY_SETUPS_V3_PREFIX);
		final List<String> loadedSetupWholeKeys = plugin.configManager.getConfigurationKeys(wholePrefix);
		Set<String> loadedSetupKeys = loadedSetupWholeKeys.stream().map(
			key -> key.substring(wholePrefix.length() - CONFIG_KEY_SETUPS_V3_PREFIX.length())
		).collect(Collectors.toSet());

		Type setupsOrderType = new TypeToken<ArrayList<String>>()
		{

		}.getType();
		final String setupsOrderJson = plugin.configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_ORDER_V3);
		List<String> setupsOrder = gson.fromJson(setupsOrderJson, setupsOrderType);
		if (setupsOrder == null)
		{
			setupsOrder = new ArrayList<>();
		}

		List<InventorySetup> loadedSetups = new ArrayList<>();
		for (final String configHash : setupsOrder)
		{
			final String configKey = CONFIG_KEY_SETUPS_V3_PREFIX + configHash;
			if (loadedSetupKeys.remove(configKey))
			{ // Handles if hash is present only in configOrder.
				final InventorySetup setup = loadV3Setup(configKey);
				if (name.equals(setup.getName())) {
					return setup;
				}
//				loadedSetups.add(setup);
			}
		}
		for (final String configKey : loadedSetupKeys)
		{
			// Load any remaining setups not present in setupsOrder. Useful if updateConfig crashes midway.
			log.info("Loading setup that was missing from Order key: " + configKey);
			final InventorySetup setup = loadV3Setup(configKey);
			if (name.equals(setup.getName())) {
				return setup;
			}
//			loadedSetups.add(setup);
		}
		return null;
//		return loadedSetups;
	}

}
