package com.banktaglayouts;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
	public static void main(String[] args) throws Exception
	{
		System.setProperty("runelite.pluginhub.version", "1.8.25.2");
		ExternalPluginManager.loadBuiltin(BankTagLayoutsPlugin.class);
		RuneLite.main(args);
	}
}