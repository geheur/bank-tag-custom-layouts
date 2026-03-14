package com.banktaglayouts;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import static net.runelite.client.plugins.banktags.BankTagsPlugin.*;

/**
 * Class I made when I changed this plugin from using reflection to access private parts of Bank Tags.
 */
public class UsedToBeReflection {

    static final String ITEM_KEY_PREFIX = "item_";

    @Inject private Client client;
	@Inject private ConfigManager configManager;
	@Inject private ItemManager itemManager;

	String getIcon(final String tag)
	{
		return configManager.getConfiguration(CONFIG_GROUP, TAG_ICON_PREFIX + Text.standardize(tag));
	}

	void setIcon(final String tag, final String icon)
    {
        configManager.setConfiguration(CONFIG_GROUP, TAG_ICON_PREFIX + Text.standardize(tag), icon);
    }

    boolean findTag(int itemId, String bankTagName) {
        Collection<String> tags = getTags(itemId, false);
        tags.addAll(getTags(itemId, true));
        return tags.stream().anyMatch(tag -> tag.startsWith(Text.standardize(bankTagName)));
    }

    Collection<String> getTags(int itemId, boolean variation)
    {
        return new LinkedHashSet<>(Text.fromCSV(getTagString(itemId, variation).toLowerCase()));
    }

    String getTagString(int itemId, boolean variation)
    {
        itemId = getItemId(itemId, variation);

        String config = configManager.getConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
        if (config == null)
        {
            return "";
        }

        return config;
    }

    private int getItemId(int itemId, boolean variation)
    {
        itemId = Math.abs(itemId);
        itemId = itemManager.canonicalize(itemId);

        if (variation)
        {
            itemId = ItemVariationMapping.map(itemId) * -1;
        }

        return itemId;
    }

    public void saveNewTab(String newTabName) {
		String configuration = configManager.getConfiguration(CONFIG_GROUP, TAG_TABS_CONFIG);
		if (configuration == null) configuration = "";
		List<String> tabs = new ArrayList<>(Text.fromCSV(configuration));
        tabs.add(newTabName);
        String tags = Text.toCSV(tabs);
        configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, TAG_TABS_CONFIG, tags);
    }

    private boolean reEnableTabs = false;
	public void loadTab(String name) {
		configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, "useTabs", false);
		reEnableTabs = true;
	}

	@Subscribe(priority = -1f) // run after TabInterface.
	public void onScriptPreFired(ScriptPreFired event) {
		if (event.getScriptId() == ScriptID.BANKMAIN_INIT && reEnableTabs) {
			configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, "useTabs", true);
			reEnableTabs = false;
		}
	}

    public void openTag(String tag) {
    }

    public void scrollTab(int i) {
    }
}
