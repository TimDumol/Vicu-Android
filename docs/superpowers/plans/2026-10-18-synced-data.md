# Synced data release: implementation plan

**Goal:** ship the release that `docs/synced-data-architecture.md` section 11 decides: the
hidden-task fixes, a shared synced-document engine, the `vicu-prefs:v1` preferences carrier with
the Inbox and the task order, and the iOS storage fixes. Android, iOS and the desktop ship it
together. PR #34 (configurable task order, by Pegoku) is rebased onto it at the end.

**Read first:** `docs/synced-data-architecture.md` (the design; section 13 is the decision
record), `docs/hidden-tasks.md` (the hidden tasks today, with known gaps), `docs/settings.md`
(every setting and where it lives).

**Checked against `main` at eac3866 (2.1.0)** on 2026-10-18, by reading the code of both apps.
The docs were written against 530915a; none of the code they describe changed since. Two claims
were wrong and are corrected in the docs: mobile drops a reopened custom-list carrier just as the
desktop does (`CarrierFinder.find` keeps a remembered id only while it is done), and the task
refresh keeps custom-list carriers and archive parts out of Room (`TaskRefresher.merge`).

## Where things stand

| Item | State |
|---|---|
| Docs | `docs/settings.md`, `docs/hidden-tasks.md`, `docs/synced-data-architecture.md` added on branch `docs/synced-data`, corrected against `main`. The handoff note is not added; what is still open in it is in this plan |
| Branch `origin/ccr-d486115c-dtp9rz` | Holds the original docs. **Keep it until PR #34 is merged**: the owner's reply on the PR links to the docs on that branch |
| PR #34 | Open, conflicts with `main`. The owner's reply (2026-10-18) asks the author to wait for the engine, then rebase. The author offered to do the desktop side of the task order |
| Desktop repo | `../vicu` on this Mac, `main` at 2.0.1, clean. No synced-preferences or task-order work on any branch. The contract files are identical in both repos |
| Seeded server | `vicu-test-vikunja` (Vikunja 2.4.0) runs on `localhost:3456`, but neither checkout has the seeded credentials (`../vicu/scripts/ui-verify/.local/` is missing) |
| iOS gate (W4) | This Mac can run it (`./gradlew :shared:iosSimulatorArm64Test` and the `xcodebuild` build) |

## Implementation decisions (accepted 2026-10-18)

Found while checking the code; not in section 13 of the architecture doc. The owner accepted
each recommendation on 2026-10-18, and the plan below follows them.

| # | Question | Decision |
|---|---|---|
| N1 | Order of work inside the release | Extract the engine (phase 3) **before** the custom-list fixes that live in it (tombstone pruning, duplicate deletion), so each fix is written once and covers custom lists and preferences. The fixes that do not touch the engine (hiding, owner, routines) go first. Section 11 lists the release's contents; this only orders the commits |
| N2 | The Inbox's one-time move (6.5) | Every device has an Inbox, so the first device to update writes its choice and every other device switches to it when it updates. Accept it, say so in the release notes, and show a one-time message on a device whose Inbox changed ("Inbox now syncs: using X") |
| N3 | An account switch leaves the old session's values (`settings.md` gap 11) | Fix in this release: on WIPE_ALL, clear the session values except the install id before storing the new ones. Small, and the release already touches `AccountSession` |
| N4 | "Discard unsynced changes" keeps unsynced custom-list changes (`settings.md` gap 9) | Fix in this release: the discard also drops both synced documents' unsynced state, since the preferences document joins the same count |
| N5 | A custom-list carrier is recognised by its title too (`hidden-tasks.md` gap 11) | Recognise every hidden task by its marker only (`HiddenTasks`). A task the user titles like a carrier stays theirs |
| N6 | Finding a reopened carrier that is not remembered (D8) | The `q` search drops `filter=done = true` (the marker check and the owner check decide). Depends on server check 0.1 |

Still open from section 13.4: **W1**, who does the desktop half (the desktop repo is on this
Mac now, so this session can).

---

## Phase 0: prerequisites

- [ ] **0.1 Server checks (W3).** Run the seed in the desktop repo (`node scripts/ui-verify/seed.mjs`,
  which resets the test user's projects on the shared test server, so no harness may run at the
  same time), then check against `localhost:3456`, as a small script in the scratchpad:
  - `GET tasks?q=vicu-custom-lists&filter=done = true` returns `created_by.id` on each task.
  - `GET tasks?q=vicu-prefs` (no `done` filter) finds a done and an open task whose descriptions
    hold `<!-- vicu-prefs:v1:... -->` (N6).
  - A task created over an API token has the token user as `created_by`.
  - `GET tasks` lists tasks of an archived project, and a `PATCH` there is refused (which status).
  - A second user who shares the first user's Inbox sees the first user's carriers in search
    (the leak, reproduced; `collab.mjs` has the two-user setup).
  Write the results into `docs/hidden-tasks.md` ("Finding them") and adjust phases 2 and 3 if a
  check fails.
- [ ] **0.2 Land the docs.** Merge `docs/synced-data` (the three docs, CLAUDE.md, this plan).

## Phase 1: contract (both repos, byte-identical)

`SharedContractIdentityTest` compares the files; run it with `--rerun`.

- [ ] **1.1 `docs/cross-app-semantics-v1.md`**, three new sections after section 9:
  - **10. Hidden tasks** (architecture 5.1 to 5.8): marker grammar and the hiding regex
    `<!--\s*vicu-[a-z0-9-]+:`, hiding by marker everywhere, shape and forced fields, ownership by
    `created_by`, placement and discovery (remembered ids whatever their state, N6), duplicates,
    revisions and 90-day tombstone pruning, versions (never write what cannot be read), unknown
    fields at every level, sizes.
  - **11. Synced preferences** (architecture 6.1, 6.2, 6.5): format, merge, null means default,
    defaults never written, unknown keys and values kept, per-project keys, the one-time move,
    and the **key registry**: `inbox_project_id` (project id, no default), `task_order` and
    `task_order.project.<id>` (the seven values, default `due_date_first`).
  - **12. Task order** (architecture 9, decisions T1 to T5): orders, sort rules (lowercased
    titles, parsed dates, missing dates last, ties by position then id), inheritance with cycles
    ended, drag rules, where it applies. This is the first time a sort is in the contract.
- [ ] **1.2 Vectors** in `test-fixtures/cross-app-semantics-v1.json`:
  - `hiddenTasks`: descriptions that are hidden (each known kind, an unknown kind, `routine:archive`,
    extra whitespace, marker inside other text) and that are not (`<!-- vicu -->`, `vicu-x:` without
    a comment, upper case kind, a title-only carrier).
  - `ownership`: carriers with `created_by` ids and the signed-in id, and which are used.
  - `syncedPrefs`: pairs of documents and their merge (newer revision wins, counter and device
    id tie-breaks, null entries, unknown keys kept, unknown values read as the default), and the
    one-time move (local value, carrier state, what is written).
  - `taskOrder`: task lists with each order's result, ties, a parent chain with a cycle, and the
    drag rule per order.
  - Tombstone pruning vectors go in `custom-list-sync-v1.json` (a document, a now, the result).
- [ ] **1.3 Fixture tests on mobile**, failing until the later phases: `HiddenTasksFixtureTest`,
  `SyncedPrefsFixtureTest`, `TaskOrderFixtureTest`, and the pruning case in the custom-list
  round-trip test. Mark them `@Ignore` with the phase that enables them, or add them in that
  phase.

## Phase 2: hidden-task fixes that do not need the engine

- [ ] **2.1 `HiddenTasks` (`util/HiddenTasks.kt`)**: the marker regex, `isHidden(description)`,
  `isOwn(task, userId)`, and the forced fields every write sends (done, null due date, no repeat,
  no reminders). `CustomListEnvelope.isAnyMetadataTask` delegates to it, then its callers move
  over (`TaskEntity.isMetadata` default, the duplicate checks in `TaskRepositoryImpl` and
  `SyncEngine`). `CarrierSpec.CUSTOM_LISTS.isCarrier` drops the title match (N5).
- [ ] **2.2 Room schema 5.** `MIGRATION_4_5` recomputes `isMetadata`: select the rows whose
  description `LIKE '%vicu-%'` and set the column with `HiddenTasks.isHidden` in Kotlin, so the
  backfill equals the rule instead of approximating it. Commit `shared/schemas/.../5.json`, register
  the migration in `KoinModules.kt`, and add `HiddenTasksMigrationTest` (sqlite-jdbc,
  `ExportedSchema.database(4)`) next to `TaskMetadataMigrationTest`.
- [ ] **2.3 Room keeps out every hidden task but routine carriers.** `TaskRefresher.merge` drops
  `HiddenTasks.isHidden && !RoutineEnvelope.hasCarrierMarker` instead of naming the custom-list and
  archive markers, so a `vicu-prefs` carrier never enters Room. `ProjectProgressSourceImpl`
  subtracts the preferences carrier's project too (once phase 4 adds its `CarrierSpec`).
- [ ] **2.4 Owner.** `AuthManager` exposes the user id as a `StateFlow<Long?>` (from
  `TokenStorage.getUserId`, backfilled by `ensureUserIdStored`). Then:
  - `CarrierFinder` takes the owner id and returns only own carriers; with no id yet it returns
    nothing and the sync reports Pending.
  - The routine reconcile in `TaskRefresher` keeps only own carriers, and `RoutineRepositoryImpl`
    filters by `TaskEntity.createdById` (already stored), so other users' routines, and their
    reminders, disappear.
  - `RoutineArchiveStore.fetchParts` keeps only own parts.
  - Test fakes gain `created_by`: `TaskListServer`, `FakeTaskServer`, and the private
    `CarrierServer`s.
- [ ] **2.5 Reopened carriers (D8).** `CarrierFinder` keeps a remembered id whatever its done
  state, and the search drops `done = true` (N6). The next write marks it done. Update the
  `CarrierFinderTest` case that asserts the opposite.
- [ ] **2.6 Routines keep unknown fields (D9).** `RoutinePayload`, `RoutineDefinition`,
  `RoutineSlot`, `RoutineOccurrenceRecord` and `RoutineArchivePart` get an unknown-fields holder
  through `UnknownFieldsSerializer` (`CustomListSync.kt`). `RoutineEnvelope.mergePayload` keeps the
  winning side's unknown fields at each level, top level included. Tests: a round trip with
  unknown fields at each level, and a merge.
- [ ] **2.7 Routines never overwrite what they cannot read (5.7).** A higher version is "update
  required", told apart from "needs repair" in `RoutinesScreen`. `TaskRepositoryImpl.sendUpdate`
  and the queued routine edit in `SyncEngine` no longer send the local patch when the server's
  payload does not parse: the action fails visibly (the failed-actions banner) and stays for
  retry after an update.

## Phase 3: the synced-document engine

The custom-list tests (`CustomListCarrierSyncTest`, `CustomListFirstSyncOrderTest`,
`CustomListRepositoryUnknownFieldsTest`, the `custom-list-sync-v1.json` round trip) must pass
unchanged after 3.1. They are the safety net for the refactor.

- [ ] **3.1 Extract, no behaviour change.** In `data/sync/`:
  - `SyncedDocumentCodec<Doc>`: parse, encode, normalize, merge, `pruneTombstones(doc, now)`,
    `same`, the version.
  - `SyncedDocumentStore`: the local copy (`device_id`, `document`, `dirty`, `carrier_task_id`,
    `last_synced_at`) in its own DataStore file, the shape of `custom_lists_sync_v1`.
  - `SyncedDocumentSync<Doc>`: the loop now in `CustomListRepositoryImpl.sync` (find, merge, write
    the lowest id when it differs, read back, status), with a `SyncedDocumentStatus` that
    `CustomListSyncStatus` maps to.
  - `CustomListRepositoryImpl` keeps its interface and uses the engine with a
    `CustomListCodec`. Wire it in `repositoryModule`; run `KoinGraphTest` and `IosKoinGraphTest`.
- [ ] **3.2 Small correctness fixes in the loop:** rethrow `CancellationException` (the current
  `catch (error: Exception)` swallows it); a malformed-carrier report survives a Pending or Idle
  result; a v1 marker whose JSON says a higher version is "update required", not malformed.
- [ ] **3.3 Tombstones (D6).** Prune tombstones older than 90 days (`TimeSource`) before
  comparing and writing. Enable the pruning vectors.
- [ ] **3.4 Duplicates (D7).** After a successful write and read-back, each other own carrier is
  fetched again and deleted when `same(prune(merge(kept, extra)), kept)`. Never a malformed or
  newer one. Tests: two carriers covered (one deleted), one not covered (kept), one of another
  user (untouched, never even read), a malformed extra (kept).
- [ ] **3.5 Unknown fields at the document level (5.8)** for custom lists: the document, its
  records and the list order keep unknown fields, like list values and filters already do.

## Phase 4: synced preferences and the Inbox

- [ ] **4.1 The preferences document.** `SyncedPrefsDocument` (`version`, `entries` of
  `{value: JsonElement?, revision}`, unknown fields kept) with its codec, and
  `CarrierSpec.PREFS` (`kind = "prefs"`, search `vicu-prefs`, title
  `Vicu preferences (sync metadata — do not delete)`). Enable `SyncedPrefsFixtureTest`.
- [ ] **4.2 `SyncedPreferences`** (`data/repository/`): typed keys with defaults, `Flow` readers
  from the local copy, setters that write the local copy, mark it unsynced and ask for a sync. A
  carrier is created only once an entry exists. The one-time move (6.5) runs after the first
  successful read of the carrier.
- [ ] **4.3 Wiring.** Store `synced_prefs` in `databaseModule`. Exclude it in `backup_rules.xml`
  and in both sections of `data_extraction_rules.xml`. `LocalDataWiper.wipeEverything` clears it,
  `unsyncedChangeCount` and `SessionCleanup` count it, the sign-out dialog names it, and the
  discard variant drops it (N4). `SyncEngine` runs it next to the custom-list sync (step 7 of
  `performSyncLocked`), with the same retry rule. Koin graph tests.
- [ ] **4.4 The Inbox (D1, 7.2).**
  - `inbox_project_id` becomes a synced entry. `AuthManager.inboxProjectId`,
    `getInboxProjectId` and `onInboxProjectSelected` read and write `SyncedPreferences`, so the
    Flow readers (ten view models) and the one-shot readers keep their call sites. `TaskWidgetStates.prepare`
    and `IosShareInbox` stop reading `TokenStorage` directly.
  - First start after the update: the `TokenStorage` value seeds the local copy, then the
    one-time move decides what is written (N2). `TokenStorage` stops holding it.
  - Setup (6.4): after sign-in, one preferences sync before the Inbox step. The synced Inbox,
    else Vikunja's `default_project_id` from `GET user` (read only), is preselected.
  - An Inbox that no longer exists or is not accessible (deleted, unshared) asks again, as
    today.
- [ ] **4.5 Account switch leftovers (N3).** On WIPE_ALL, `AccountSession` clears the session
  values except the install id before the new sign-in stores its own. Test in
  `SessionCleanupTest` or a new `AccountSessionTest`.

## Phase 5: the task order (PR #34)

The owner's reply asks the author to rebase once phases 1 to 4 are on `main`. The rebase, by
the author or by us, has to:

- [ ] Replace `TaskOrderPrefsStore` with a view of `SyncedPreferences` (keys `task_order` and
  `task_order.project.<id>`; "Default" writes `null`). Its own DataStore file, its `clear()` and
  its `LocalDataWiper` parameter go away: the synced document is cleared with the account.
- [ ] Move the `SettingsViewModel` binding to a spelled-out `viewModel { ... }` in
  `ViewModelModule.kt` (it already has 22 parameters, `viewModelOf`'s maximum).
- [ ] Move the widget ordering from `TaskWidgetWorker` into `TaskWidgetStates` (`Batch.query`,
  settings and `parentOf` read once in `prepare`), so iOS follows. Today the Inbox widget lists
  `created DESC` and the project widget `position ASC`; the PR description says both change.
- [ ] Sort with the contract rules (section 12): parse due and created dates instead of comparing
  strings, lowercase titles instead of `String.CASE_INSENSITIVE_ORDER`, ties by position then id.
  Enable `TaskOrderFixtureTest`.
- [ ] `InboxViewModel` sets the order and the tasks in one state update; the project screen
  builds the parent map once, not per section.

## Phase 6: iOS storage fixes (D13)

- [x] **6.1 Install id and session values out of the backup** (`settings.md` gap 1). The install
  id moves to the Keychain (this device only); the other `NSUserDefaults` session values move to
  a file in Application Support (excluded). On the first start of the new version, a device whose
  Keychain still holds its tokens moves its values; a device without them is a restored copy and
  gets a new install id, so it never deletes the original's backup token.
- [x] **6.2 Widget configurations across an account switch** (gap 3). WidgetKit keeps the
  configuration, so the snapshot catalog carries an account tag and a widget configured under
  another account shows "choose another" instead of the new account's project with the same id.
- [x] **6.3 The App Group** (gap 4): exclude `widgets/` and `share-inbox/` from backup, and empty
  `share-inbox/` in `wipeEverything` on iOS.
- [ ] Run the iOS gate after each common-code phase (W4), on this Mac.

## Desktop half (W1)

The same release in `../vicu`, in the same order, against the same vectors:

- Phase 2: widen both regex copies (`metadata-tasks.ts`, `custom-list-protocol.ts`) and apply them
  in `use-project-tasks.ts`, `use-project-sections.ts`, the project and section undo merges and
  Review; subtract archive parts and the preferences carrier from the sidebar's done count; the
  owner filter in `carrier-discovery.ts` (and before `removeDuplicateCarriers` deletes anything);
  keep reopened carriers; routines keep unknown top-level fields (`mergeRoutinePayload`) and in a
  logged occurrence (`statusRecord`), show "update required", and `writeRoutineCarrier` never
  writes over a carrier it cannot read.
- Phase 3 and 4: a generic document service next to `custom-list-service.ts`,
  `synced-preferences.ts`, `synced_prefs_sync` in `config.json` (in `ACCOUNT_SPECIFIC_KEYS` and
  `MAIN_OWNED_KEYS`), `inbox_project_id` read from it, setup reading it first.
- Phase 5: the task order setting, the menus, the sort (`lib/task-sort.ts` today does "due date,
  then position") and the drag rules. The PR #34 author offered to do this.

## Release

- [ ] The gate on each phase: `./gradlew test lint assembleDebug :shared:compileCommonMainKotlinMetadata`,
  `SharedContractIdentityTest --rerun`, the iOS gate, and the desktop's suites.
- [ ] By hand on the seeded server: two accounts sharing an Inbox (no lists or routines cross
  over, nothing of the other user is written or deleted); phone and desktop changing the task
  order and the Inbox; a carrier reopened, given a due date and deleted in the web UI; an older
  app version beside the new carrier (one done task in the Logbook and search, nothing else).
- [ ] Release notes (both apps): the Inbox and task order now sync and the first updated
  device's Inbox wins (N2); another user's custom lists and routines no longer appear in a shared
  project, and how to delete ones that were mixed in before (D12); older versions show the
  preferences carrier as one done task until they update.
- [ ] Bump `version.properties` and write `docs/releases/vX.Y.Z.md` (CLAUDE.md, CI / Releases).

## Later releases (not in this one)

The settings in architecture 13.2 marked "later release", one group at a time with the one-time
move; desktop routines offline; archive parts left behind by a deleted routine
(`hidden-tasks.md` gap 8); `settings.md` gaps 5, 6, 7 and 10.

## Order and parallel work

```
0.1 server checks ─┬─ 1 contract ─┬─ 2 fixes ────┬─ 4 prefs + Inbox ─ 5 task order ─ release
0.2 docs ──────────┘              └─ 3 engine ───┘
                    6 iOS storage (independent) ─────────────────────────────────┘
                    desktop half follows each phase against the same vectors
```

Phases 2 and 3 touch different files except `CarrierFinder` (owner filter in 2.4, reopened
carriers in 2.5); do those two steps before 3.4. Phase 6 shares nothing with the others.
