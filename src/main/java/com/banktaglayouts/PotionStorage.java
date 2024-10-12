package com.banktaglayouts;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
// This is borrowed from the PotionStorage class in the banktags plugin defined here:
// https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/banktags/tabs/PotionStorage.java
// I made a few modifications to make it work with this plugin
public class PotionStorage {
    static final int COMPONENTS_PER_POTION = 5;
    private final Client client;
    private Potion[] potions;
    boolean cachePotions;
    private Set<Integer> potionStoreVars;

    @Setter
    private PotionStorageCallback onPotionStorageUpdated;

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (cachePotions) {
            log.debug("Rebuilding potions");
            cachePotions = false;
            rebuildPotions();

            Widget w = client.getWidget(ComponentID.BANK_POTIONSTORE_CONTENT);

            if (w != null && potionStoreVars == null) {
                int[] trigger = w.getVarTransmitTrigger();
                potionStoreVars = new HashSet<>();
                Arrays.stream(trigger).forEach(potionStoreVars::add);
            }
            if(onPotionStorageUpdated != null){
                onPotionStorageUpdated.run();
            }
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged) {
        boolean isPotionStoreVar = potionStoreVars != null && potionStoreVars.contains(varbitChanged.getVarpId());
        boolean isPotionStoreTabBeingSelected = varbitChanged.getVarbitId() == Varbits.CURRENT_BANK_TAB && varbitChanged.getValue() == 15;
        if (isPotionStoreVar || isPotionStoreTabBeingSelected) {
            cachePotions = true;
        }
    }

    private void rebuildPotions() {
        EnumComposition potionStorePotions = client.getEnum(EnumID.POTIONSTORE_POTIONS);
        potions = new Potion[potionStorePotions.size()];
        int potionsIdx = 0;
        for (int potionEnumId : potionStorePotions.getIntVals()) {
            EnumComposition potionEnum = client.getEnum(potionEnumId);
            client.runScript(ScriptID.POTIONSTORE_DOSES, potionEnumId);
            int doses = client.getIntStack()[0];
            client.runScript(ScriptID.POTIONSTORE_WITHDRAW_DOSES, potionEnumId);
            int withdrawDoses = client.getIntStack()[0];

            if (doses > 0 && withdrawDoses > 0) {
                Potion p = new Potion();
                p.potionEnum = potionEnum;
                p.itemId = potionEnum.getIntValue(withdrawDoses);
                p.doses = doses;
                p.withdrawDoses = withdrawDoses;
                potions[potionsIdx] = p;
            }

            ++potionsIdx;
        }
    }

    public int count(int itemId) {
        if (potions == null) {
            return 0;
        }

        for (Potion potion : potions) {
            if (potion != null && potion.itemId == itemId) {
                return potion.doses / potion.withdrawDoses;
            }
        }
        return 0;
    }

    public boolean containsAndHasAny(int itemId){
        if (potions == null) {
            return false;
        }

        for (Potion potion : potions) {
            if (potion != null && potion.itemId == itemId) {
                return potion.doses >= potion.withdrawDoses;
            }
        }
        return false;
    }

    public int find(int itemId) {
        if (potions == null) {
            return -1;
        }

        int potionIdx = 0;
        for (Potion potion : potions) {
            ++potionIdx;
            if (potion != null && potion.itemId == itemId) {
                return potionIdx - 1;
            }
        }
        return -1;
    }

    void prepareWidgets()
    {
        // if the potion store hasn't been opened yet, the client components won't have been made yet.
        // they need to exist for the click to work correctly.
        Widget potStoreContent = client.getWidget(ComponentID.BANK_POTIONSTORE_CONTENT);
        if (potStoreContent.getChildren() == null)
        {
            int childIdx = 0;
            for (int i = 0; i < potions.length; ++i)
            {
                for (int j = 0; j < COMPONENTS_PER_POTION; ++j)
                {
                    potStoreContent.createChild(childIdx++, WidgetType.GRAPHIC);
                }
            }
        }
    }
}