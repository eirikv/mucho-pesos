# Mucho Pesos

**Mucho Pesos** is a serverless Android personal finance and partner bill-splitting app built with **Jetpack Compose + Material 3**, using a **shared Google Sheet** as its sole database and sync engine.

## Features

- **Serverless Shared Google Sheets Backend**: Two partners share a single formatted spreadsheet (`Config` + strictly append-only `Ledger` tabs)—no central server or third-party database.
- **Exact Financial Math**: All amounts, split shares, percentages, and net balances use `java.math.BigDecimal` with explicit currency scale and deterministic rounding (`RoundingMode.HALF_UP`).
- **Four Split Modes + Settle Up**: Equal (50/50), Exact Amounts, Percentages, Full (100%), and one-tap Settle Up reimbursements.
- **Append-Only Edit & Delete**: Deleting appends a `VOID` tombstone row; editing atomically appends both a `VOID` row and a replacement `EXPENSE` row in a single `values:append` request so the spreadsheet audit history stays immutable.
- **Monthly Statistics Tab**: Track overall monthly totals, interactive 6-month spending trends, each partner's monthly share and paid totals, and monthly breakdowns by specific category or category group (excluding settlements from spending totals).
- **Pixel 10 / Pixel 10 Pro Material 3 Design**: Dynamic Color (`dynamicLightColorScheme` / `dynamicDarkColorScheme`), full edge-to-edge gesture insets, tabular financial numerals, pull-to-refresh, grouped household categories (6 groups, 21 categories), and predictive back navigation.
- **Built-in Demo Mode**: Test every flow end-to-end against a local simulated append-only sheet before configuring Google Cloud credentials.

## Spreadsheet Schema (`Ledger` Tab)

The `Ledger` tab is strictly append-only (`A1:P`), with one row per event:

| Column | Description | Example |
| --- | --- | --- |
| `tx_id` | Unique UUID v4 identifier for the ledger row. | `550e8400-e29b-41d4-a716-446655440000` |
| `record_type` | Event type: `EXPENSE`, `SETTLEMENT`, or `VOID` (tombstone cancelling a prior row). | `EXPENSE` |
| `ref_tx_id` | Original `tx_id` referenced by a `VOID` tombstone or an edited replacement `EXPENSE`; empty for new entries. | `""` *(or `550e8400-...` on `VOID`/edit)* |
| `date` | Transaction date in ISO-8601 format (`YYYY-MM-DD`). | `2026-11-20` |
| `created_at` | UTC creation timestamp in ISO-8601 format. | `2026-11-20T18:30:00Z` |
| `created_by` | Email address of the signed-in user who appended the row. | `alex@example.com` |
| `description` | Short transaction title, `"Settlement payment"`, or void reason. | `Weekly Groceries` |
| `category_group` | Top-level category group (`FOOD`, `HOUSING`, `TRANSPORT`, `UTILITIES`, `ENTERTAINMENT`, `OTHER`, or `SETTLEMENT`; empty for `VOID`). | `FOOD` |
| `category` | Specific category display name within the group (e.g. `Groceries`, `Electricity & Gas`, `Settlement`; empty for `VOID`). | `Groceries` |
| `amount` | Total transaction amount as a plain decimal string scaled to the ledger's home currency (`0.00` for `VOID`). | `120.00` |
| `paid_by` | Partner who paid the expense or sent the settlement: `PARTNER_A` or `PARTNER_B` (mapped in the `Config` tab). | `PARTNER_A` |
| `split_type` | Split mode used for the row: `EQUAL`, `EXACT`, `PERCENTAGE`, `FULL`, or `SETTLEMENT`. | `PERCENTAGE` |
| `partner_a_share` | Partner A's share of `amount` as a plain decimal string (`0.00` for `SETTLEMENT` and `VOID`). | `72.00` |
| `partner_b_share` | Partner B's share of `amount` as a plain decimal string (`partner_a_share + partner_b_share = amount`; `0.00` for `SETTLEMENT` and `VOID`). | `48.00` |
| `split_meta` | Extra split parameters: `"A%:B%"` for `PERCENTAGE` (e.g. `60:40`), owing partner role (`PARTNER_A`/`PARTNER_B`) for `FULL`, empty otherwise. | `60:40` |
| `notes` | Optional free-text notes attached to the transaction. | `Weekly produce and pantry staples` |

## Tech Stack

- **Target**: Pixel 10 / Pixel 10 Pro (`minSdk = 36`, `compileSdk = 37`, `targetSdk = 37`)
- **Toolchain**: Gradle `9.7.1`, Android Gradle Plugin `9.5.1` (built-in Kotlin), Kotlin `2.4.20`, JDK 21 (bytecode target JVM 17)
- **UI**: Jetpack Compose BOM `2026.09.00`, Material 3, Navigation Compose, Lifecycle Compose
- **Auth**: Android Credential Manager (`GetGoogleIdOption`) + Google Play Services `Identity.getAuthorizationClient`
- **Data**: Retrofit 3, OkHttp 5, Kotlinx Serialization JSON, DataStore Preferences, Coroutines

## Getting Started

1. **Clone & open** the project in Android Studio (or build from CLI with JDK 21 and Android SDK 37).
2. **Demo Mode (zero setup)**: Run the app and tap **Try Demo Mode** on the Sign-In screen.
3. **Real Google Sheets Sync**: Follow [GOOGLE_CLOUD_SETUP.md](GOOGLE_CLOUD_SETUP.md) to enable the Google Sheets & Drive APIs, create OAuth clients, and set `GOOGLE_WEB_CLIENT_ID` in `local.properties`.

## Build & Test Commands

```bash
# Run all JVM unit tests
./gradlew testDebugUnitTest

# Build the debug APK
./gradlew assembleDebug
```
