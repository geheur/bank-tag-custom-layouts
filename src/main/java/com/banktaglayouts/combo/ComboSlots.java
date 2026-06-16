package com.banktaglayouts.combo;

import com.banktaglayouts.BankTagLayoutsPlugin;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.runelite.client.config.ConfigManager;

/**
 * Persistence for combo "smart cells" embedded in host bank tabs. Each host tab can have any number
 * of cells, each backed by a combo group; the cell holds that group's current winner as a real,
 * tagged item, kept in sync on render.
 *
 * <p>Stored under {@link BankTagLayoutsPlugin#CONFIG_GROUP} at {@code comboslots_<hostTag>} as a CSV
 * of {@code index:group} entries (combo group names contain no commas or colons). The displayed winner
 * is NOT stored — it is resolved live from the bank at render time and the real widget is surfaced (or a
 * ghost drawn), so nothing is tagged/untagged. Old {@code index:group:placedItem} rows are still read
 * (the trailing field is ignored).
 */
public final class ComboSlots
{
	public static final String CONFIG_KEY_PREFIX = "comboslots_";

	/** One embedded combo cell: where it sits in the host tab and which combo group it shows. */
	@Data
	@AllArgsConstructor
	public static final class Slot
	{
		private int index;            // layout index in the host tab
		private final String group;   // combo group name (e.g. "[MeleeLegs]")
	}

	private ComboSlots()
	{
	}

	public static List<Slot> read(ConfigManager configManager, String hostTag)
	{
		// New format: index:group. Old format: index:group:placedItem (placedItem ignored — parts[1] is the group).
		return ComboCsv.read(configManager, CONFIG_KEY_PREFIX + hostTag, (index, parts) -> new Slot(index, parts[1].trim()));
	}

	public static void write(ConfigManager configManager, String hostTag, List<Slot> slots)
	{
		List<String> rows = new ArrayList<>();
		for (Slot s : slots)
		{
			rows.add(s.getIndex() + ":" + s.getGroup());
		}
		ComboCsv.write(configManager, CONFIG_KEY_PREFIX + hostTag, rows);
	}
}
