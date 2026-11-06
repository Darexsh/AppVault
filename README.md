* * *

<div align="center">

📱 AppVault
============================

**An Android app for inspecting and managing installed applications**  
📦🔎⚙️💾🧭

![Projekt-Status](https://img.shields.io/badge/Status-Aktiv-brightgreen) ![License](https://img.shields.io/badge/License-GPLv3-blue) ![Version](https://img.shields.io/badge/Version-1.0.1-orange)

[![Telegram Bot](https://img.shields.io/badge/Telegram-Bot-2AABEE?logo=telegram&logoColor=white)](https://t.me/darexsh_bot) [![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-yellow?logo=buy-me-a-coffee)](https://buymeacoffee.com/darexsh)  
<sub>Get release updates on Telegram.<br>If you want to support more apps, you can leave a small donation for a coffee.</sub>

</div>


* * *

✨ Authors
---------

| Name | GitHub | Role | Contact | Contributions |
| --- | --- | --- | --- | --- |
| **[Darexsh by Daniel Sichler](https://github.com/Darexsh)** | [Link](https://github.com/Darexsh?tab=repositories) | Android App Development 📱🛠️, UI/UX Design 🎨 | 📧 [E-Mail](mailto:sichler.daniel@gmail.com) | Concept, Feature Implementation, App Inventory & Detail Logic, Backup/Install/Uninstall Flows, UI Design |

* * *

🚀 About the Project
==============

**AppVault** is an Android application for inspecting, backing up, and managing installed apps. It provides a searchable app inventory, user/system filtering, sorting, package-level details, APK backup and installation, and quick access to app settings.

* * *

✨ Features
----------

* 🔎 **Search, Filter & Sort**: Search apps by name/package, filter by **User apps**, **System apps**, or **All apps**, and sort by **Name**, **Install date**, **Update date**, or **App size**.

* 📋 **App Inventory**: Clean list with app icon, app name, and package name.

* ✨ **Animated Splash and UI**: A 2.4-second branded splash screen, with motion in the app catalog, backup card, and storage tray.

* ⚡ **Responsive Loading**: Installed apps are scanned in the background, icons load as rows become visible, and a loading message appears while the catalog is prepared.

* 🧾 **App Details Screen**: Open detailed metadata including version, version code, target SDK, min SDK, first install date, **app size**, data directory, source directory, and requested app permissions.

* ⚙️ **Quick App Actions**: Open system app settings, create APK backup, or uninstall from each app row.

* 💾 **APK Backup**: Save backups in `/storage/emulated/0/App_Backups`. Regular apps are saved as `.apk` files; split apps are saved as one `.apks` archive containing the base APK and its splits.

* 📲 **Install Backups**: Use **Install backup apps** below the filter and sort controls to choose an `.apk`, APKMirror `.apkm`, APKPure `.xapk`, or AppVault `.apks` backup. The picker opens in `App_Backups`, and Android handles the install confirmation. XAPK APK splits are installed; bundled OBB game data is not copied.

* ✅ **Backup Confirm Dialog**: Friendly confirmation dialog before each backup operation.

* 🧩 **Selection Mode**: Long-press an app to enter selection mode, then run **backup** or **uninstall** for selected apps.

* 🪟 **Selected-App Confirm Dialogs**: Styled confirmation dialogs for selected-app backup/uninstall with selected app names shown.

* 🔄 **Live App List Refresh**: List updates automatically after installs, updates, and removals.

* 🔐 **Permissions Viewer**: Shows requested permissions and their status with human-readable English and German labels (**Allowed** / **Not allowed**).

* 🌐 **Language Switcher**: Change app language from the App Info dialog using **System default**, **English**, or **Deutsch**.

* 🌙 **Dark Mode (Default)**: App uses a dark, no-action-bar UI by default.

* ℹ️ **In-App About Dialog**: Includes version, description, and social/contact buttons.


* * *

📸 Screenshots
--------------

<table>
  <tr>
    <td align="center"><b>Home Screen</b><br><img src="Screenshots/Home.png" width="200" height="450"></td>
    <td align="center"><b>Details</b><br><img src="Screenshots/Details.png" width="200" height="450"></td>
    <td align="center"><b>Selection Mode</b><br><img src="Screenshots/Selection_mode.png" width="200" height="450"></td>
  </tr>
</table>

<table>
  <tr>
    <td align="center"><b>Backup Dialog</b><br><img src="Screenshots/Backup_selection.png" width="200" height="450"></td>
    <td align="center"><b>Uninstall Dialog</b><br><img src="Screenshots/Uninstall_selection.png" width="200" height="450"></td>
    <td align="center"><b>About</b><br><img src="Screenshots/About.png" width="200" height="450"></td>
  </tr>
</table>

* * *

📥 Installation
---------------

1. **Build from source**:

    * Clone or download the repository from GitHub:

        ```bash
        git clone https://github.com/Darexsh/AppVault.git
        ```

    * Open the project in **Android Studio**.

    * Sync Gradle and build the project.

    * Run the app on an Android device or emulator (Android 8+ recommended).

2. **Install via APK release**:

    * Download the APK from the GitHub Releases page.

    * 🔒 Enable installation from unknown sources if prompted (required on Android 8+).

    * 📂 Open the APK on your device and follow the installation steps.


* * *

📝 Usage
--------

1. **Browse Apps**:

    * Open the app to view installed applications.

    * Use search, filter, and sort controls in the header.

2. **Inspect Details**:

    * Tap an app row to open its details screen.

3. **Single App Actions**:

    * Tap the row action arrow to expand actions.

    * Use **App settings**, **Backup**, or **Uninstall**.

4. **Install a Backup**:

    * Tap **Install backup apps** below the filter and sort controls.

    * Choose an `.apk` file, an APKMirror `.apkm`, APKPure `.xapk`, or AppVault `.apks` archive from `App_Backups`.

    * If prompted, allow AppVault to install apps in Android settings, then tap the button again.

    * Confirm the installation in Android's package installer.

    * XAPK archives install their APK files. Any bundled OBB game data is not copied.

5. **Selection Mode**:

    * Long-press an app to enter selection mode.

    * Select multiple apps and run **Backup** or **Uninstall** from the action bar.

    * Review selected app names in the confirmation dialog before continuing.

6. **Grant File Access for Backups**:

    * If needed, accept the in-app prompt and allow file access in Android settings.

7. **Change App Language**:

    * Open the App Info dialog (`i` button in header).

    * Tap **Language** and choose **English** or **Deutsch**.


* * *

🔑 Permissions
--------------

* 📦 **Query Installed Apps** (`QUERY_ALL_PACKAGES`): Required to list installed apps.

* 💾 **File Access** (`MANAGE_EXTERNAL_STORAGE` on Android 11+): Required to save APK backups into `App_Backups` root folder.

* 📲 **Install Packages** (`REQUEST_INSTALL_PACKAGES`): Lets AppVault request installs through Android's package installer. The user must allow AppVault as an install source and confirm each installation.

* 🗑️ **Delete Packages** (`REQUEST_DELETE_PACKAGES`): Required to trigger uninstall requests.


* * *

⚙️ Technical Details
--------------------

* 📦 Built with **Java** and Android SDK components.

* ✨ The home screen is preceded by a 2.4-second animated AppVault splash screen.

* 📱 Uses `PackageManager` to enumerate installed apps and collect metadata.

* 🔄 Uses `BroadcastReceiver` package events (`ADDED/REMOVED/CHANGED/REPLACED`) for live list refresh.

* 💾 APK backups are copied from installed APK source files into `App_Backups`; split apps are packed into one `.apks` ZIP archive.

* 📲 Backup installation streams `.apk` files or the base and split APKs from APKMirror `.apkm`, APKPure `.xapk`, and AppVault `.apks` archives into an Android `PackageInstaller` session. XAPK OBB data is not copied. Android shows its own confirmation screen; split APKs are not merged into a universal APK.

* 🧭 Navigation includes a dedicated app-details activity.

* 📏 Details page app size is shown as installed APK footprint (base + split APK files when present).

* 🔐 Permission details use curated human-readable English and German names for common Android permissions, along with the grant state.

* 🌐 Language behavior: **System default** is the default mode. If the system language is not supported, the app falls back to English.

* 🎨 UI built with Material Components and custom dialogs/themes.


* * *

🔒 Privacy
----------

* Privacy Policy: [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

* * *

📜 License
----------

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

This license applies to the updater application code in this repository. Upstream patch and app projects remain under their own licenses and notice terms.

**Copyright (c) 2025 Darexsh by Daniel Sichler**

You are free to use, study, modify, and redistribute this software under the terms of GPL-3.0.

Please include the following notice when redistributing this project:

> **Developer:** Darexsh by Daniel Sichler. Licensed under GNU GPL v3.0. See `LICENSE` for details.

The full license is available in the [LICENSE](LICENSE) file.

* * *

<div align="center"> <sub>Created with ❤️ by Daniel Sichler</sub> </div>
