package inventorysetupz;

import java.util.HashMap;
import java.util.Map;
import static net.runelite.api.ItemID.*;
import static net.runelite.api.ItemID.AVERNIC_DEFENDER;
import static net.runelite.api.ItemID.GHOMMALS_AVERNIC_DEFENDER_5;
import static net.runelite.api.ItemID.GHOMMALS_AVERNIC_DEFENDER_5_L;
import static net.runelite.api.ItemID.GHOMMALS_AVERNIC_DEFENDER_6;
import static net.runelite.api.ItemID.GHOMMALS_AVERNIC_DEFENDER_6_L;
import static net.runelite.api.ItemID.GHRAZI_RAPIER;
import static net.runelite.api.ItemID.HOLY_GHRAZI_RAPIER;
import static net.runelite.api.ItemID.SANGUINESTI_STAFF;
import net.runelite.client.game.ItemVariationMapping;

public class InventorySetupsVariationMapping
{
	private static final Map<Integer, Integer> mappings;

	public InventorySetupsVariationMapping()
	{
	}

	public static int map(final Integer id)
	{
		int mappedId = ItemVariationMapping.map(id);

		// if the mapped ID is equal to the original id
		// this means there was no mapping for this id. Try the extra custom mappings
		if (mappedId == id)
		{
			mappedId = mappings.getOrDefault(id, id);
		}

		return mappedId;
	}

	static
	{
		mappings = new HashMap<>();

		// Granite Cannonball -> Cannonball
		mappings.put(GRANITE_CANNONBALL, CANNONBALL);

		// Smith Gloves (i) act as ice gloves
		mappings.put(SMITHS_GLOVES_I, ICE_GLOVES);

		// Divine rune pouch -> Rune Pouch
		mappings.put(DIVINE_RUNE_POUCH, RUNE_POUCH);

		// Make god capes the same
		final int itemIDGodCape = 1000000001;
		mappings.put(SARADOMIN_CAPE, itemIDGodCape);
		mappings.put(GUTHIX_CAPE, itemIDGodCape);
		mappings.put(ZAMORAK_CAPE, itemIDGodCape);
		final int itemIDImbuedGodCape = 1000000002;
		mappings.put(IMBUED_SARADOMIN_CAPE, itemIDImbuedGodCape);
		mappings.put(IMBUED_GUTHIX_CAPE, itemIDImbuedGodCape);
		mappings.put(IMBUED_ZAMORAK_CAPE, itemIDImbuedGodCape);
		final int itemIDGodMaxCape = 1000000003;
		mappings.put(SARADOMIN_MAX_CAPE, itemIDGodMaxCape);
		mappings.put(GUTHIX_MAX_CAPE, itemIDGodMaxCape);
		mappings.put(ZAMORAK_MAX_CAPE, itemIDGodMaxCape);
		final int itemIDImbuedGodMaxCape = 1000000004;
		mappings.put(IMBUED_SARADOMIN_MAX_CAPE, itemIDImbuedGodMaxCape);
		mappings.put(IMBUED_GUTHIX_MAX_CAPE, itemIDImbuedGodMaxCape);
		mappings.put(IMBUED_ZAMORAK_MAX_CAPE, itemIDImbuedGodMaxCape);

		// Make god d'hides the same
		final int itemIDGodCoif = 1000000005;
		mappings.put(ANCIENT_COIF, itemIDGodCoif);
		mappings.put(ARMADYL_COIF, itemIDGodCoif);
		mappings.put(BANDOS_COIF, itemIDGodCoif);
		mappings.put(GUTHIX_COIF, itemIDGodCoif);
		mappings.put(SARADOMIN_COIF, itemIDGodCoif);
		mappings.put(ZAMORAK_COIF, itemIDGodCoif);

		final int itemIDGodDhideBody = 1000000006;
		mappings.put(ANCIENT_DHIDE_BODY, itemIDGodDhideBody);
		mappings.put(ARMADYL_DHIDE_BODY, itemIDGodDhideBody);
		mappings.put(BANDOS_DHIDE_BODY, itemIDGodDhideBody);
		mappings.put(GUTHIX_DHIDE_BODY, itemIDGodDhideBody);
		mappings.put(SARADOMIN_DHIDE_BODY, itemIDGodDhideBody);
		mappings.put(ZAMORAK_DHIDE_BODY, itemIDGodDhideBody);

		final int itemIDGodChaps = 1000000007;
		mappings.put(ANCIENT_CHAPS, itemIDGodChaps);
		mappings.put(ARMADYL_CHAPS, itemIDGodChaps);
		mappings.put(BANDOS_CHAPS, itemIDGodChaps);
		mappings.put(GUTHIX_CHAPS, itemIDGodChaps);
		mappings.put(SARADOMIN_CHAPS, itemIDGodChaps);
		mappings.put(ZAMORAK_CHAPS, itemIDGodChaps);

		final int itemIDGodBracers = 1000000008;
		mappings.put(ANCIENT_BRACERS, itemIDGodBracers);
		mappings.put(ARMADYL_BRACERS, itemIDGodBracers);
		mappings.put(BANDOS_BRACERS, itemIDGodBracers);
		mappings.put(GUTHIX_BRACERS, itemIDGodBracers);
		mappings.put(SARADOMIN_BRACERS, itemIDGodBracers);
		mappings.put(ZAMORAK_BRACERS, itemIDGodBracers);

		final int itemIDGodDhideBoots = 1000000009;
		mappings.put(ANCIENT_DHIDE_BOOTS, itemIDGodDhideBoots);
		mappings.put(ARMADYL_DHIDE_BOOTS, itemIDGodDhideBoots);
		mappings.put(BANDOS_DHIDE_BOOTS, itemIDGodDhideBoots);
		mappings.put(GUTHIX_DHIDE_BOOTS, itemIDGodDhideBoots);
		mappings.put(SARADOMIN_DHIDE_BOOTS, itemIDGodDhideBoots);
		mappings.put(ZAMORAK_DHIDE_BOOTS, itemIDGodDhideBoots);

		final int itemIDGodDhideShield = 1000000010;
		mappings.put(ANCIENT_DHIDE_SHIELD, itemIDGodDhideShield);
		mappings.put(ARMADYL_DHIDE_SHIELD, itemIDGodDhideShield);
		mappings.put(BANDOS_DHIDE_SHIELD, itemIDGodDhideShield);
		mappings.put(GUTHIX_DHIDE_SHIELD, itemIDGodDhideShield);
		mappings.put(SARADOMIN_DHIDE_SHIELD, itemIDGodDhideShield);
		mappings.put(ZAMORAK_DHIDE_SHIELD, itemIDGodDhideShield);

		// Twisted Ancestral -> Regular Ancestral
		mappings.put(TWISTED_ANCESTRAL_HAT, ANCESTRAL_HAT);
		mappings.put(TWISTED_ANCESTRAL_ROBE_BOTTOM, ANCESTRAL_ROBE_BOTTOM);
		mappings.put(TWISTED_ANCESTRAL_ROBE_TOP, ANCESTRAL_ROBE_TOP);

		// Golden Prospectors -> Regular Prospectors
		mappings.put(GOLDEN_PROSPECTOR_BOOTS, PROSPECTOR_BOOTS);
		mappings.put(GOLDEN_PROSPECTOR_HELMET, PROSPECTOR_HELMET);
		mappings.put(GOLDEN_PROSPECTOR_JACKET, PROSPECTOR_JACKET);
		mappings.put(GOLDEN_PROSPECTOR_LEGS, PROSPECTOR_LEGS);

		// Spirit Anglers -> Regular Anglers
		mappings.put(SPIRIT_ANGLER_BOOTS, ANGLER_BOOTS);
		mappings.put(SPIRIT_ANGLER_HEADBAND, ANGLER_HAT);
		mappings.put(SPIRIT_ANGLER_TOP, ANGLER_TOP);
		mappings.put(SPIRIT_ANGLER_WADERS, ANGLER_WADERS);

		// ToB ornament kits -> base version
		mappings.put(SANGUINE_SCYTHE_OF_VITUR, SCYTHE_OF_VITUR);
		mappings.put(HOLY_SCYTHE_OF_VITUR, SCYTHE_OF_VITUR);
		mappings.put(HOLY_SANGUINESTI_STAFF, SANGUINESTI_STAFF);
		mappings.put(HOLY_GHRAZI_RAPIER, GHRAZI_RAPIER);

		mappings.put(GHOMMALS_AVERNIC_DEFENDER_5, AVERNIC_DEFENDER);
		mappings.put(GHOMMALS_AVERNIC_DEFENDER_5_L, AVERNIC_DEFENDER);
		mappings.put(GHOMMALS_AVERNIC_DEFENDER_6, AVERNIC_DEFENDER);
		mappings.put(GHOMMALS_AVERNIC_DEFENDER_6_L, AVERNIC_DEFENDER);
	}

}
