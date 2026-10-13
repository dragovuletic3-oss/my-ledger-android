# My Ledger for Android

A simple personal ledger app for recording income, expenses, money owed to you, and money you owe.

## Current features
- Overview totals for income, expenses, money owed to you, and money you owe
- Add dated entries with a description and optional person's name
- View and delete saved entries
- Local on-device storage
- Export a JSON backup using Android's share sheet

## Build an APK
Every push to `main` triggers the GitHub Actions workflow in `.github/workflows/android.yml`. When the workflow succeeds, open the repository's **Actions** tab, select the successful **Build My Ledger APK** run, and download the `my-ledger-debug-apk` artifact. Extract the ZIP to find `app-debug.apk`.

This is an initial debug build, not a signed Play Store release. The backup export is a plain JSON file; store it somewhere private. Local app data is not encrypted by this initial version.
