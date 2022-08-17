package com.banktaglayouts;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Properties;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
	public static void main(String[] args) throws Exception
	{
		setWindowTitle("bank-tag-layouts (" + getCurrentGitBranch() + ") RL-" + RuneLiteProperties.getVersion());

		ExternalPluginManager.loadBuiltin(BankTagLayoutsPlugin.class, BankTagLayoutsToolsPlugin.class);
		RuneLite.main(args);
	}

	private static void setWindowTitle(String title) throws NoSuchFieldException, IllegalAccessException
	{
		Field propertiesField = RuneLiteProperties.class.getDeclaredField("properties");
		propertiesField.setAccessible(true);
		Properties properties = (Properties) propertiesField.get(null);
		properties.setProperty("runelite.title", title);
	}

	public static String getCurrentGitBranch() {
		try
		{
			Process process = Runtime.getRuntime().exec("git rev-parse --abbrev-ref HEAD");
			process.waitFor();

			BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream()));

			return reader.readLine();
		}catch (Exception e) {
			return "threw exception";
		}
	}
}