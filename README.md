* * *

<div align="center">

📱 AppsInspector
============================

**An Android app for inspecting and managing installed applications**  
📦🔎⚙️💾🧭

![Projekt-Status](https://img.shields.io/badge/Status-Aktiv-brightgreen) ![License](https://img.shields.io/badge/License-NonCommercial-blue) ![Version](https://img.shields.io/badge/Version-1.0-orange)

[![Telegram Bot](https://img.shields.io/badge/Telegram-Bot-2AABEE?logo=telegram&logoColor=white)](https://t.me/darexsh_bot) [![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-yellow?logo=buy-me-a-coffee)](https://buymeacoffee.com/darexsh)  
<sub>Get release updates on Telegram.<br>If you want to support more apps, you can leave a small donation for a coffee.</sub>

</div>


* * *

✨ Authors
---------

| Name | GitHub | Role | Contact | Contributions |
| --- | --- | --- | --- | --- |
| **[Darexsh by Daniel Sichler](https://github.com/Darexsh)** | [Link](https://github.com/Darexsh?tab=repositories) | Android App Development 📱🛠️, UI/UX Design 🎨 | 📧 [E-Mail](mailto:sichler.daniel@gmail.com) | Concept, Feature Implementation, App Inventory & Detail Logic, Backup/Uninstall Flows, UI Design |

* * *

🚀 About the Project
==============

**AppsInspector** is an Android application designed to help you inspect installed apps on your device. It provides a searchable app inventory, user/system filtering, package-level details, backup actions, and quick access to app settings.

* * *

✨ Features
----------

* 🔎 **Search & Filter**: Search apps by name/package and filter by **User apps**, **System apps**, or **All apps**.

* 📋 **App Inventory**: Clean list with app icon, app name, and package name.

* 🧾 **App Details Screen**: Open detailed metadata including version, version code, target SDK, min SDK, first install date, data directory, and source directory.

* ⚙️ **Quick App Actions**: Open system app settings, create APK backup, or uninstall from each app row.

* 💾 **APK Backup**: Save APK files into `/storage/emulated/0/App_Backups` (visible at root storage level like DCIM/Download).

* ✅ **Backup Confirm Dialog**: Friendly confirmation dialog before each backup operation.

* 🧩 **Selection Mode (Batch)**: Long-press an app to enter selection mode, then run **batch backup** or **batch uninstall**.

* 🔄 **Live App List Refresh**: List updates automatically after installs, updates, and removals.

* 🌙 **Dark Mode (Default)**: App uses a dark, no-action-bar UI by default.

* ℹ️ **In-App About Dialog**: Includes version, description, and social/contact buttons.


* * *

📸 Screenshots
--------------

Screenshots can be added later in a `Screenshots/` folder and referenced here.

* * *

📥 Installation
---------------

1. **Build from source**:

    * Clone or download the repository from GitHub:

        ```bash
        git clone https://github.com/Darexsh/AppsInspector.git
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

    * Use search and filter controls in the header.

2. **Inspect Details**:

    * Tap an app row to open its details screen.

3. **Single App Actions**:

    * Tap the row action arrow to expand actions.

    * Use **App settings**, **Backup**, or **Uninstall**.

4. **Batch Mode**:

    * Long-press an app to enter selection mode.

    * Select multiple apps and run **Backup** or **Uninstall** from the batch bar.

5. **Grant File Access for Backups**:

    * If needed, accept the in-app prompt and allow file access in Android settings.


* * *

🔑 Permissions
--------------

* 📦 **Query Installed Apps** (`QUERY_ALL_PACKAGES`): Required to list installed apps.

* 💾 **File Access** (`MANAGE_EXTERNAL_STORAGE` on Android 11+): Required to save APK backups into `App_Backups` root folder.

* 🗑️ **Delete Packages** (`REQUEST_DELETE_PACKAGES`): Required to trigger uninstall requests.


* * *

⚙️ Technical Details
--------------------

* 📦 Built with **Java** and Android SDK components.

* 📱 Uses `PackageManager` to enumerate installed apps and collect metadata.

* 🔄 Uses `BroadcastReceiver` package events (`ADDED/REMOVED/CHANGED/REPLACED`) for live list refresh.

* 💾 APK backup copies from `applicationInfo.sourceDir` to external storage (`App_Backups`).

* 🧭 Navigation includes a dedicated app-details activity.

* 🎨 UI built with Material Components and custom dialogs/themes.


* * *

📜 License
----------

This project is licensed under the **Non-Commercial Software License (MIT-style) v1.0** and was developed as an educational project. You are free to use, modify, and distribute the code for **non-commercial purposes only**, and must credit the author:

**Copyright (c) 2025 Darexsh by Daniel Sichler**

Please include the following notice with any use or distribution:

> Developed by Daniel Sichler aka Darexsh. Licensed under the Non-Commercial Software License (MIT-style) v1.0. See `LICENSE` for details.

The full license is available in the [LICENSE](LICENSE) file.

* * *

<div align="center"> <sub>Created with ❤️ by Daniel Sichler</sub> </div>
