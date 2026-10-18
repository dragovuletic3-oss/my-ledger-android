# My Ledger for Android

A simple personal ledger app for recording income, expenses, money owed to you, and money you owe.

## Version 1.2
- The same lending and repayment parser handles typed descriptions and voice results.
- “I gave Dan $300” followed by “Dan gave me back $200” leaves $100 owed to you and records no income.
- Tap a person's balance to see their history and add entries using their microphone.
- The bottom Total bar shows amounts owed to you minus amounts you owe.
- Records includes a confirmation step to clear all trial entries.
- Automated checks cover transaction direction, per-person balances, totals, cents, spoken amounts, and microphone session state. Physical device testing is still required.

An older test APK may have a different signing key. If Android rejects the update, export any records you need before uninstalling the old test app and installing 1.2. Uninstalling removes local records. This workflow caches the test signing key for subsequent builds; a production release still needs a durable release signing setup.

## Current features
- Overview totals for income, expenses, money owed to you, and money you owe
- Add dated entries with a description and optional person's name
- View and delete saved entries
- Local on-device storage
- Export a JSON backup using Android's share sheet

## Build an APK
Every push to `main` triggers the GitHub Actions workflow in `.github/workflows/android.yml`. When the workflow succeeds, open the repository's **Actions** tab, select the successful **Build My Ledger APK** run, and download the `my-ledger-debug-apk` artifact. Extract the ZIP to find `app-debug.apk`.

This is an initial debug build, not a signed Play Store release. The backup export is a plain JSON file; store it somewhere private. Local app data is not encrypted by this initial version.
