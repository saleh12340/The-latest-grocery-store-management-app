# سجل تنفيذ مشروع بقالة العزي

آخر تحديث: 2026-11-12

## المستودع والهوية
- Repository: saleh12340/The-latest-grocery-store-management-app
- Branch: main
- Package / applicationId: com.saleh.alazzi.grocery
- Android Native / Kotlin / Jetpack Compose / Room / SQLite
- Minimum SDK: 26
- Compile / Target SDK: 36
- Java toolchain: 17
- Gradle: 9.5.0
- AGP: 9.3.1
- Kotlin / Compose compiler plugin: 2.4.21
- KSP: 2.3.10
- Room: 2.8.5
- Compose BOM: 2026.09.00

## ما تم وضعه في المصدر
- Android entry point, RTL Compose host, MVVM ViewModel and navigation.
- Room database version 1 with 21 entities and DAOs.
- Customer, supplier, item, sales and purchase invoice storage.
- Atomic sale/purchase posting with Room transactions.
- Operation ID idempotency for invoices and payment postings.
- Exact integer money math and thousandth-unit quantity math.
- Customer receipts and supplier payments with cash ledger posting.
- Weighted-average purchase cost and per-sale cost snapshots.
- Daily sales/purchase/count/gross-profit SQL queries.
- Dashboard, customer/supplier/item screens, invoice entry and account statements.
- Independent receipt renderer for RTL text, ESC/POS raster encoding, paired Bluetooth printer client.
- PNG/PDF export and Android Sharesheet / WhatsApp intents.
- Unit tests for money arithmetic and instrumentation tests for transaction rollback/idempotency and raster encoding.
- GitHub Actions workflow for unit tests, instrumentation-test compilation, debug APK and artifact upload.

## Important accounting/storage decisions
- Amounts are stored as Long whole Yemeni rials; no Float/Double for monetary arithmetic.
- Quantity is stored in thousandths.
- Customer positive balance means amount owed by customer.
- Supplier positive balance means amount owed to supplier.
- Sales and purchases, stock, ledger and cash effects are committed in a single database transaction.
- No destructive Room migration fallback is configured.
- Operation IDs must be reused when retrying the same form submission.

## Verification state
- GitHub Actions has run repeatedly. Early runs failed first in Android SDK setup and then because the Kotlin Android plugin is prohibited by AGP 9 built-in Kotlin. Both causes have been corrected in later commits.
- The latest commit after those corrections must be checked in Actions before claiming build success.
- No APK is declared successful until an Actions run completes all test/build/upload steps and the artifact is verified.
- No physical Bluetooth printer has been tested.

## Known incomplete work
1. Complete build verification and fix compiler / Room KSP issues reported by CI.
2. Complete inventory adjustment, customer/supplier manual ledger operations, refunds/returns with reverse posting and cancellation audit.
3. Finish expenses, transfers, notes CRUD, settings and user PIN authentication/roles.
4. Finish JSON export/import backup with schema version and pre-restore backup.
5. Add printer discovery / runtime permission UX and connect actual invoice records to thermal print actions.
6. Add invoice history/detail/void/return UI and PDF/image open/viewer UX.
7. Expand reports with date filters, cost of goods sold, returns, expenses and net profit.
8. Add Room migration tests and UI tests; run instrumentation tests on emulator.
9. Add full Arabic validation, accessibility, screen-size and RTL UI review.

## Next exact task
Inspect the newest GitHub Actions run for branch main. Resolve any Gradle/KSP/Room/Kotlin errors from logs before expanding additional features. Do not start a second repository or reset history.
