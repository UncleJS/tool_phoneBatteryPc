# Battery Widget

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)
[![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/15)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Glance](https://img.shields.io/badge/Jetpack-Glance-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/develop/ui/compose/glance)
[![Podman](https://img.shields.io/badge/Podman-892CA0?logo=podman&logoColor=white)](https://podman.io)

A small Android home-screen widget that shows your phone's battery level, charging state,
temperature and the time of the last update. It is written in Kotlin with Jetpack Glance and
Compose Material3, targets Android 15+ (minSdk 35, targetSdk 36) and uses the package
`io.github.unclejs.batterywidget`. The APK is built entirely inside rootless Podman containers
managed with Quadlet, so nothing is installed on the host.

## Table of Contents

- [Features](#features)
- [How updates work](#how-updates-work)
- [Requirements](#requirements)
- [Build](#build)
  - [Quick start](#quick-start)
  - [What happens during a build](#what-happens-during-a-build)
  - [Quadlet units](#quadlet-units)
- [Install on your phone](#install-on-your-phone)
- [Using the widget](#using-the-widget)
- [Scripts](#scripts)
- [Project layout](#project-layout)
- [License](#license)

## Features

- **Battery percentage** in large text.
- **Charging indicator** coloured by charging speed, plus the power source (USB, AC, Wireless
  or Dock) and the charging power in watts when the phone reports it.
  "Not charging" is white on the dark widget (dark grey on the light one).

  | Label | Charging power | Dark widget | Light widget |
  |-------|----------------|-------------|--------------|
  | Not charging | on battery | white | dark grey |
  | Plugged in, not charging | charge paused / limit reached | blue grey | blue grey |
  | ⚡ Slow charging | < 7.5 W | orange | dark orange |
  | ⚡ Normal charging | 7.5–15 W | yellow | dark yellow |
  | ⚡ Fast charging | 15–30 W | light blue | blue |
  | ⚡ Super fast charging | 30–60 W | violet | purple |
  | ⚡ Ultra fast charging | ≥ 60 W | pink | magenta |
  | ⚡ Charging | phone reports no power figures | pale yellow | ochre |
  | Full | 100 %, plugged in | green | green |

  The speed uses the higher of two figures: the charger's rated power (the same value the lock
  screen uses for "Charging rapidly") and the power the battery is actually drawing (current ×
  voltage). Using the higher one keeps a fast charger "fast" near 100 %, when the actual draw drops.
  The watts shown are the actual draw. The app's main screen has the same colour legend.
- **Time until full** while charging, e.g. `Full in 1 h 23 min`, in the charging colour. It uses
  Android's own estimate (the one the lock screen shows). If the phone doesn't provide one, the app
  estimates it from the charge counter and current and marks it with `~` (`Full in ~45 min`); that
  estimate tends to be optimistic because charging slows near 100 %. The figure is worked out when
  the widget updates, so between updates it can be up to 15 minutes old (less with Live updates).
- **Refresh button** (↻) in the widget's top-right corner (can be hidden per widget) and in the
  app's top bar. Both re-read the battery and update all widgets immediately.
- **Battery temperature** in °C.
- **Last update time** in your local time zone, formatted `yyyy-MM-dd HH:mm:ss`.
- **Per-widget configuration screen**, shown when you place a widget and again via
  long-press → reconfigure (Android 12+):
  - percentage text size slider, 12–120 sp (default 48); if the widget is too small for the chosen size, the number shrinks to fit (resize the widget to get the full size)
  - detail text size slider, 8–32 sp (default 12)
  - toggles to show or hide charging state, time until full, temperature, last update time and the refresh button
  - live preview of the widget while you adjust it
- **Multiple widgets**: each widget keeps its own settings.
- **Dark theme by default**, with a light/dark toggle in the app's top bar.

[↑ Back to TOC](#table-of-contents)

## How updates work

Android does not let an app receive battery-change broadcasts in the background unless one of its
components is running. The widget is therefore refreshed in these ways:

| Trigger | How |
|---------|-----|
| Every 15 minutes | Periodic WorkManager job (15 minutes is the minimum interval Android allows). |
| Plugging in | A WorkManager job with a charging constraint runs shortly after the phone starts charging. |
| Tapping the widget or its ↻ button | Refreshes all widgets immediately. |
| ↻ in the app's top bar | Re-reads the battery and refreshes all widgets immediately. |
| **Live updates** (optional) | A switch in the app starts a foreground service, shown as a persistent low-priority notification. It updates the widget on every battery change and restarts after a reboot. |

[↑ Back to TOC](#table-of-contents)

## Requirements

- A Linux host with **rootless Podman 5.x** and a **systemd user session**
  (`systemctl --user` must work).
- A phone running **Android 15 or later**.

Nothing is installed on the host. The Android SDK, JDK and Gradle live in the builder image.

[↑ Back to TOC](#table-of-contents)

## Build

### Quick start

```bash
scripts/install.sh   # check prerequisites, build the builder image, install Quadlet units
scripts/start.sh     # build the APK in a container and export it
```

The APK ends up at `dist/BatteryLevelApp.apk`.

After changing the code, rebuild the image and the APK with:

```bash
scripts/start.sh --rebuild
# or
scripts/build.sh && scripts/start.sh
```

[↑ Back to TOC](#table-of-contents)

### What happens during a build

1. The Quadlet `.build` unit builds the image `localhost/phonebattery-builder` from the
   `Containerfile`. The source is copied into the image with `COPY`; there are no bind mounts.
2. The oneshot service `phonebattery-apk.service` runs the unit tests and `assembleDebug`
   inside that image.
3. Build outputs are written to the `phonebattery-dist` named volume and then exported to
   `dist/` in the repo.
4. The debug signing key is kept in the `phonebattery-keystore` named volume, so every new build
   is signed with the same key and installs over the previous version on the phone.
5. The Gradle cache lives in the `phonebattery-gradle` named volume, which makes rebuilds faster.

[↑ Back to TOC](#table-of-contents)

### Quadlet units

The unit files live in `quadlet/`:

| Unit | Purpose |
|------|---------|
| `phonebattery-builder.build` | Builds the `localhost/phonebattery-builder` image. |
| `phonebattery-apk.container` | Oneshot container that runs the tests and builds the APK. |
| `phonebattery-gradle.volume` | Gradle cache. |
| `phonebattery-keystore.volume` | Debug signing key. |
| `phonebattery-dist.volume` | Build outputs (APK). |

`scripts/install.sh` copies them to `~/.config/containers/systemd/` (filling in the build
context path for the `.build` unit) and runs `systemctl --user daemon-reload`.

Check the build service and its logs with:

```bash
systemctl --user status phonebattery-apk.service
journalctl --user -u phonebattery-apk.service
```

[↑ Back to TOC](#table-of-contents)

## Install on your phone

1. Copy `dist/BatteryLevelApp.apk` to the phone (USB file transfer, cloud storage, etc.).
2. Allow **Install unknown apps** for the file manager or app you will open the APK with.
3. Open the APK on the phone and confirm the installation.
4. The first time you turn on **Live updates**, Android asks for permission to show
   notifications.

If the signing key has changed (for example after `scripts/uninstall.sh --purge`, which deletes
the keystore volume), the new APK will not install over the old one. Uninstall the old app first.

[↑ Back to TOC](#table-of-contents)

## Using the widget

1. Long-press an empty spot on the home screen → **Widgets** → **Battery Widget**, or tap the
   **Add widget** button in the app.
2. The configuration screen opens. Adjust text sizes and choose which details to show, check
   the live preview, then save.
3. To change the settings later, long-press the widget and choose reconfigure (Android 12+).
4. Tap the ↻ button (or anywhere on the widget) to refresh it immediately.

[↑ Back to TOC](#table-of-contents)

## Scripts

All scripts live in `scripts/`, only call `podman` and `systemctl --user`, work from any
directory, are safe to re-run, and support `-h`/`--help`.

| Script | Does |
|--------|------|
| `common.sh` | Shared names (image, service, volumes, paths), sourced by the other scripts. |
| `install.sh [--no-build]` | Checks prerequisites (rootless Podman, systemd user session), builds the builder image (skipped with `--no-build`), installs the Quadlet units to `~/.config/containers/systemd/` and runs `daemon-reload`. |
| `build.sh` | Builds or rebuilds the builder image. |
| `start.sh [--rebuild]` | Starts `phonebattery-apk.service` to build the APK and exports it to `dist/`. `--rebuild` rebuilds the image first. |
| `stop.sh` | Stops the services. |
| `restart.sh` | Runs `stop.sh`, then `start.sh`. |
| `uninstall.sh [--purge] [--remove-quadlets]` | Removes what the repo created (see below). |

By default `uninstall.sh` stops the services, resets failed units, and removes the containers,
the builder image, its intermediate and dangling layers, build cache, and base images pulled for
the build (unless another container still uses them). It **keeps** the named volumes (Gradle
cache, keystore, dist) and the installed Quadlet units, and says so.

- `--purge` also removes the volumes and their data, including the debug signing key.
- `--remove-quadlets` also removes the units from `~/.config/containers/systemd/` and runs
  `daemon-reload`.

`scripts/uninstall.sh --purge --remove-quadlets` leaves nothing behind except the repo checkout.
At the end it checks Podman and systemd for leftovers, reports anything it finds, and exits
non-zero if something remains.

[↑ Back to TOC](#table-of-contents)

## Project layout

```text
.
├── app/src/main/java/io/github/unclejs/batterywidget/
│   ├── battery/
│   │   ├── BatteryReader.kt        # reads level, charging state, source, temperature, power
│   │   ├── BatterySnapshot.kt      # reading + ChargeSpeed tiers
│   │   ├── ChargeSpeed.kt          # charging-speed classification (watts → tier)
│   │   ├── ChargeTime.kt           # time-until-full rules and fallback estimate
│   │   └── Formatters.kt           # text formatting (%, °C, yyyy-MM-dd HH:mm:ss)
│   ├── widget/
│   │   ├── BatteryWidget.kt        # Glance widget UI
│   │   ├── BatteryWidgetReceiver.kt
│   │   ├── RefreshAction.kt        # tap / ↻ refresh
│   │   ├── WidgetConfig.kt         # per-widget settings model
│   │   ├── WidgetPalette.kt        # dark/light colours, charging-speed colours
│   │   └── WidgetUpdater.kt
│   ├── work/
│   │   ├── BatteryUpdateWorker.kt  # WorkManager job
│   │   └── Scheduler.kt            # periodic and on-charging jobs
│   ├── live/
│   │   ├── LiveBatteryService.kt   # optional foreground service
│   │   ├── LiveThrottle.kt         # limits how often live mode redraws
│   │   └── BootReceiver.kt         # restarts live updates after reboot
│   ├── config/
│   │   └── WidgetConfigActivity.kt # per-widget settings with live preview
│   ├── settings/
│   │   └── AppSettings.kt
│   ├── ui/
│   │   ├── BatteryState.kt         # live battery state for the app screens
│   │   └── Theme.kt                # dark/light Material3 theme
│   └── MainActivity.kt
├── Containerfile                   # builder image (SDK, JDK, Gradle)
├── container/
│   └── build-apk.sh                # runs tests + assembleDebug inside the container
├── quadlet/                        # Quadlet units (.build, .container, .volume)
└── scripts/                        # lifecycle scripts
```

[↑ Back to TOC](#table-of-contents)

## License

Copyright (C) 2026 UncleJS. This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.

See [LICENSE](LICENSE) for the full text.

[↑ Back to TOC](#table-of-contents)

---

© 2026 UncleJS — Licensed under [AGPL-3.0-or-later](https://www.gnu.org/licenses/agpl-3.0.txt)
