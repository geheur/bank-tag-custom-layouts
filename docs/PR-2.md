# Code Review — PR #2 "Combo (smart cell) tag feature — Version 1.0"

Source: `HydraTal/bank-tag-layouts-combo` PR #2 (`upstream-main` ← `Version1.0`, +3143/−131, 19 files).
Review: 9 finder angles → dedup → direct verification against PR-head code and RuneLite 1.12.28 sources.

> **Line numbers are from the PR head (`Version1.0`).** They will not line up exactly
> with `ComboTags` (or later) branches — treat them as approximate anchors and re-locate by symbol.

**Refuted on inspection (no action):** case-sensitivity of tab/group names (RuneLite's
`buildSearchFilterBankTag` uses `customTags.get(tag)` with the raw case-preserved name —
`TagManager.java:517` — and the plugin registers with the same raw `hostTag`); `colon`/`comma`
split in `ComboSlots` (`ComboGroup.bracket()` strips `[];:,`); unbounded `firstEmpty…` loops
(sparse layout always yields a gap); menu `insertAt == size` (RuneLite's `createMenuEntry(idx)`
treats `idx == length` as append).

---

## Correctness

- [ ] **1. Data race on RuneLite's `customTags` HashMap (plugin disable vs. bank change)** — most severe
  - `shutDown()` runs on the **EDT** (PluginManager.stopPlugin asserts EDT, `:469`) and calls
    `unregisterComboBankTags()` *directly* (`BankTagLayoutsPlugin.java:386` → `tagManager.unregisterTag`
    → `customTags.remove`). `registerComboBankTags()` runs on the **client thread**
    (`BankTagLayoutsPlugin.java:765` in `onItemContainerChanged` → `customTags.put`). RuneLite's
    `customTags` is a plain `HashMap` (`TagManager.java:50`) with no synchronization.
  - **Failure:** disabling the plugin while a BANK `ItemContainerChanged` is mid-processing →
    concurrent `put`/`remove` on a non-synchronized HashMap → corruption / CME / resize spin. The
    plugin's own maps are concurrent but can't protect RuneLite's internal map.
  - **Fix:** wrap the `shutDown` unregister in `clientThread.invoke(...)`, as `startUp` already does for register.

- [ ] **2. Renaming a combo-host CORE tab blanks its cells until the next bank change**
  - `handlePotentialTagRename` → `migrateComboDataForTab` (`BankTagLayoutsPlugin.java:1102`) re-registers
    a fresh `ComboBankTag` but **never calls `maintainComboCoreTab`**. A new `ComboBankTag` starts with
    empty `ownedWinners` (`ComboBankTag.java:22`), and `contains()` gates tab membership.
  - **Failure:** user renames an open combo-host CORE tab; RuneLite reopens it (`reloadActiveTab`) so the
    filter rebuilds correctly, but with empty `ownedWinners` the winners are filtered out → cells render
    blank until a deposit/withdraw/search fires `onItemContainerChanged`.
  - **Fix:** call `maintainComboCoreTab(newTag)` after migrating.

- [ ] **3. Editing a combo in the panel doesn't refresh an already-open bank**
  - `onConfigChanged` for the `combos` key only nulls `comboColorCache` (`BankTagLayoutsPlugin.java:403`);
    it never re-runs `maintainAllComboCoreTabs`/`layoutBank`, and the panel's edit handlers (`moveCombo`,
    variant pick, `upsert`) only call `ComboStore`.
  - **Failure:** with the bank open, reordering members or changing the display variant shows the **old**
    winner/ghost until the next bank interaction.
  - **Fix:** trigger a maintain + relayout from the `combos` config change (on the client thread).

- [ ] **4. `comboMembersUntagged` one-shot guard is never cleared on tab delete/rename**
  - Guard added per host tag (`BankTagLayoutsPlugin.java:1288`) but `removeComboDataForTab` (`:1082`)
    and `migrateComboDataForTab` (`:1102`) don't remove it.
  - **Failure:** delete a combo tab and recreate one with the same name in the same session, then add a
    combo whose members were individually tagged → `untagComboMembersOnce` early-returns, the stray member
    tags survive, and RuneLite floats a non-winner member into the tab.
  - **Fix:** remove the host tag from `comboMembersUntagged` in `removeComboDataForTab` / `migrateComboDataForTab`.

- [ ] **5. Bank container null at startup → winners briefly resolve as ghosts**
  - `bankItemsByBase` returns an empty (uncached) map when `getItemContainer(BANK)` is null
    (`ComboResolver.java:95`); `resolveWinner` falls through to the ghost branch and `isOwnedReal` returns false.
  - **Failure:** `startUp`'s `maintainAllComboCoreTabs()` (`BankTagLayoutsPlugin.java:354`) runs when logged
    in but before the bank is opened, so an inactive core tab can get ghost winners pinned / empty
    `ownedWinners` and stay that way until that tab's next bank event. Mostly self-heals.
  - **Fix:** skip maintain when the bank container is null (or defer until first BANK event).

- [ ] **6. `ItemIndex.build` dereferences `getItemComposition(id)` with no null/try guard**
  - `ItemIndex.java:64` calls `itemManager.getItemComposition(id).getName()` for every id `0..getItemCount()`.
  - **Failure:** a null composition NPEs; `build` is invoked from `ComboPanel`'s `clientThread.invokeLater`
    with no catch, so `entries` is never assigned and item search is dead for the rest of the session.
  - **Fix:** null-guard the composition / wrap the build in try-catch.

- [ ] **7. Ghost winner can be non-positive from a corrupt `variants` map** (edge)
  - `resolveWinner` returns `selectedVariant(group, members.get(0))` for the ghost case
    (`ComboResolver.java:62`) with no `> 0` guard.
  - **Failure:** an imported/edited `variants` entry mapping a base to `0`/negative is returned as the
    "winner" and flows into `winner > 0` checks (silently dropped at 0, or a negative id pinned).

## Performance (hot paths: per-frame render, per bank build, per menu entry)

- [ ] **8. `FakeItemOverlay.render` re-parses + re-allocates every frame**
  - `render:48` calls `getComboCellGroups` which reads the `comboslots` config and builds a fresh `HashMap`
    (`BankTagLayoutsPlugin.java:1437`) on every paint; `FakeItemOverlay.java:131` news up a `java.awt.Color`
    per cell per frame (`getComboColor:1520`).
  - **Fix:** cache the cell map + Colors, invalidate on config/tab change.

- [ ] **9. `resolveWinner` re-parses the whole combos JSON on every call**
  - `ComboStore.get` does a full `gson.fromJson` + `normalize()` of every group (`ComboResolver.java:46`),
    called per cell across maintain/reconcile/sync/menu/render. `maintainComboCoreTab` parses twice per slot
    — `orderedMemberBases` then `resolveWinner` (`BankTagLayoutsPlugin.java:1156-1157`).
  - **Fix:** cache the parsed group list per bank build.

- [ ] **10. `comboHostTags()` scans all config keys twice per bank change; menu recolor resolves per entry**
  - `onItemContainerChanged` calls `registerComboBankTags()` then `maintainAllComboCoreTabs()`
    (`BankTagLayoutsPlugin.java:765-766`) — each iterates the whole config keyspace. `colorComboItemName`
    runs per `MenuEntryAdded` and resolves winners (`:2045`).
  - **Fix:** compute the host-tag list / cell group once per event.

## Cleanup

- [ ] **11. Two divergent "base id of an item" definitions**
  - `comboBaseOf` = `map(canonicalize(getNonPlaceholderId(id)))` (`BankTagLayoutsPlugin.java:1010`);
    `ComboResolver.isOwnedReal`/`bankItemsByBase` = `map(canonicalize(id))` **without** `getNonPlaceholderId`
    (`ComboResolver.java:68`, `:109`).
  - **Risk:** if the two disagree for some id, the scrub and the ownership/winner check operate on different
    bases. Use one shared canonical-base helper everywhere.

- [ ] **12. Duplicated `index:int` CSV codec**
  - `readComboWinnerMap`/`writeComboWinnerMap` (`BankTagLayoutsPlugin.java:1322`) hand-roll the same
    `Text.fromCSV`/`split(":")`/`parseInt` parse-serialize pair that `ComboSlots` already implements.
  - **Fix:** factor a shared codec so a format fix lands in one place.

---

**Priority:** #1 (the `customTags` data race) before merge — genuine corruption hazard on RuneLite's
shared map, one-line `clientThread.invoke` wrap in `shutDown`. #2–#4 are real but self-healing or niche.
