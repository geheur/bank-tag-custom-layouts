package com.banktaglayouts.combo;

import com.banktaglayouts.BankTagLayoutsPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * Shared codec for the combo config values stored as a CSV of {@code index:value} rows — the combo-cell
 * slots ({@code comboslots_<tab>}, see {@link ComboSlots}) and the hub winner map
 * ({@code combowinners_<tab>}). Both used to hand-roll the same {@link Text#fromCSV}/{@code split(":")}/
 * {@code parseInt} parse-serialize pair; centralizing it here means a format change lands in one place.
 * All values live under {@link BankTagLayoutsPlugin#CONFIG_GROUP}.
 */
public final class ComboCsv
{
	private ComboCsv()
	{
	}

	/**
	 * Reads a config CSV value, splitting each entry on {@code :} and invoking {@code parser} with the parsed
	 * int index and the full parts array. Entries with fewer than two parts or a non-integer index are skipped,
	 * as is a {@code null} parser result (so the parser can reject a malformed value). Order is preserved.
	 */
	public static <T> List<T> read(ConfigManager configManager, String key, BiFunction<Integer, String[], T> parser)
	{
		List<T> out = new ArrayList<>();
		String csv = configManager.getConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, key);
		if (csv == null || csv.isEmpty())
		{
			return out;
		}
		for (String entry : Text.fromCSV(csv))
		{
			String[] parts = entry.split(":");
			if (parts.length < 2)
			{
				continue;
			}
			int index;
			try
			{
				index = Integer.parseInt(parts[0].trim());
			}
			catch (NumberFormatException ignored)
			{
				continue; // malformed index → skip
			}
			T value = parser.apply(index, parts);
			if (value != null)
			{
				out.add(value);
			}
		}
		return out;
	}

	/** Writes pre-formatted {@code index:value} rows to a config CSV value; unsets the key when empty. */
	public static void write(ConfigManager configManager, String key, List<String> rows)
	{
		if (rows.isEmpty())
		{
			configManager.unsetConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, key);
			return;
		}
		configManager.setConfiguration(BankTagLayoutsPlugin.CONFIG_GROUP, key, Text.toCSV(rows));
	}

	/** Reads an {@code index:int} CSV value into a map (rows with a non-integer value are skipped). */
	public static Map<Integer, Integer> readIntMap(ConfigManager configManager, String key)
	{
		Map<Integer, Integer> map = new HashMap<>();
		for (int[] kv : read(configManager, key, (index, parts) ->
		{
			try
			{
				return new int[]{index, Integer.parseInt(parts[1].trim())};
			}
			catch (NumberFormatException ignored)
			{
				return null; // malformed value → skip
			}
		}))
		{
			map.put(kv[0], kv[1]);
		}
		return map;
	}

	/** Writes a map of {@code index -> int} as an {@code index:value} CSV value; unsets the key when empty. */
	public static void writeIntMap(ConfigManager configManager, String key, Map<Integer, Integer> map)
	{
		List<String> rows = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : map.entrySet())
		{
			rows.add(e.getKey() + ":" + e.getValue());
		}
		write(configManager, key, rows);
	}
}
