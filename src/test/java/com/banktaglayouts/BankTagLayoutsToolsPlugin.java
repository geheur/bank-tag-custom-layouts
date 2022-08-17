package com.banktaglayouts;

import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Bank Tag Layouts [Tools]",
	description = "tools",
	tags = {"bank", "tag", "layout"}
)
@PluginDependency(BankTagLayoutsPlugin.class)
public class BankTagLayoutsToolsPlugin extends Plugin
{
	@Inject private OverlayManager overlayManager;
	@Inject private ConfigManager configManager;
	@Inject private Client client;
	@Inject private BankTagLayoutsPlugin plugin;
	@Inject private BankTagLayoutsToolsOverlay overlay;

	@Override
	public void startUp() {
		overlayManager.add(overlay);
	}

	@Override
	public void shutDown() {
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted) {
		if ("clearversion".equals(commandExecuted.getCommand())) {
			configManager.unsetConfiguration(plugin.CONFIG_GROUP, "version");
			System.out.println("cleared version number.");
		}
		if ("checkversion".equals(commandExecuted.getCommand())) {
			System.out.println("checking version number");
			plugin.checkVersionUpgrade();
		}

		if ("itemname".equals(commandExecuted.getCommand())) {
			String[] arguments = commandExecuted.getArguments();
			client.addChatMessage(ChatMessageType.PUBLICCHAT, "Item name of " + arguments[0], plugin.itemName(Integer.valueOf(arguments[0])), "bla");
		}
		if ("placeholder".equals(commandExecuted.getCommand())) {
			String[] arguments = commandExecuted.getArguments();
			int itemId = Integer.parseInt(arguments[0]);
			client.addChatMessage(ChatMessageType.PUBLICCHAT, "" + itemId, plugin.itemName(itemId) + " is a " + plugin.isPlaceholder(itemId) + " and it's reversed id is " + plugin.switchPlaceholderId(itemId) + " and again " + plugin.switchPlaceholderId(plugin.switchPlaceholderId(itemId)), "bla");
		}
	}
}
