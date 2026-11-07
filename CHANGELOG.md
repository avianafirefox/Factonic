# Changelog

## 1.0.0 (versionCode 5)

The first full release. Prototype saves (1.2 and earlier) load as they are.

### Google Play
- Targets Android 16 (API 36), which Play requires for all app updates since August 31, 2026. Toolchain moved to Android Gradle Plugin 8.13.1, Gradle 8.14.3 and Kotlin 2.2.21.
- Declared as a game (`android:appCategory="game"`), so Android 16 keeps it in landscape on tablets and foldables.
- New `Release AAB (Google Play)` workflow builds the signed App Bundle with the upload key from GitHub secrets, checks the signature, and attaches the R8 mapping file.

### New
- **Tunnel Belts** (research: Tunnel Belts). Run a belt under up to five tiles so production lines can cross. Build an entrance, then an exit further along facing the same way; the ghost shows the range and the link it will make. Rotating one end reverses the whole tunnel, and an unpaired end raises an alert.
- **Belt Splitters** (research: Belt Splitting). Belts pointing into a splitter are its inputs; every other belt beside it is an output, served in turn. That makes it a splitter, a merger or a balancer. Give it a filter and it sorts: the filtered item leaves through the front, everything else through the sides.
- **Research queue.** Queue any number of technologies. Queuing a locked one queues its missing prerequisites first, and labs move straight on to the next one. Researching something else now puts the interrupted technology back at the front of the queue, so no progress is lost.
- **Production statistics.** Items per minute over the last 1 or 10 minutes, with a sparkline per item, plus play time, factory size, power and rockets. Tap the Sol clock or open Menu → Statistics.
- **Alerts.** Depleted drills, generators out of fuel, machines without a recipe and unpaired tunnels get a blinking badge and appear in a ⚠ chip; tap it to fly to each in turn. Newly placed machines get a short grace period.
- **Objective pointer.** When an objective needs a resource, an arrow at the edge of the screen points to the nearest deposit and shows its distance. Tap it to fly there.
- **Ambient soundtrack**, composed and synthesised on the device the first time the game runs.
- **Settings** (in the menu and on the title screen): music and volume, sound effects and volume, vibration, objective pointer, control hints, keep screen on.
- **Swipe to remove**: with the Remove tool, swipe across buildings to pick up a whole row.
- **Pipette copies settings**: long-pressing an assembler or splitter copies its recipe or filter to the copies you place.
- Placement pulse, smooth camera flights (home button, alerts, objective pointer) and new sounds for alerts and completed objectives.
- The recipe panel shows how many you can craft right now and has a MAX button.

### Fixed
- Autosaving every minute cancelled the hand-crafting queue and refunded it. The queue is now saved with the game and keeps running.
- Returning to the game from the background while the menu was open unpaused the simulation behind the menu.
- The Android back button closed the game outright. It now closes the top panel, the selection or the tool, then opens the menu.
- "New planet" on the title screen replaced an existing save without asking. It now asks for a second tap.
- Every new game started two more terrain threads that were never stopped.

### Saves
- Saves are written in the background (no hitch every minute), fsynced and swapped in atomically. The previous save is kept as a backup and used automatically if the main file is damaged; an unreadable save is kept aside instead of being overwritten.
- New save format (version 5) that also stores the craft queue, research queue, hotbar and play time. Each building is stored as its own block, so future versions can skip anything they don't recognise.

### Under the hood
- Fewer allocations in the simulation's hot paths (power grid, drill output).
- Sound effects respect a volume setting; music and effects can be turned off separately.
- `BuildConfig` is enabled so the version shown in the game always matches the build.
- New simulation tests cover tunnels, splitters, sorting, the research queue, alerts, statistics, the v5 save round-trip and loading prototype (v4) saves. New Robolectric screenshot tests cover logistics, statistics, settings and the research queue.
- Release signing uses a private key from environment variables (or GitHub secrets in the release workflow); private keys are git-ignored.
- GitHub Actions workflow that builds a versioned debug APK and then runs the simulation tests.
