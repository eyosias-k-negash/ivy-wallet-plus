# SMS-to-Transaction Implementation Plan

This plan outlines the steps to automatically pre-fill the "Add Transaction" screen when an SMS is received from a configured bank contact on a specific SIM.

## User Review Required

> [!IMPORTANT]
> **Permissions**: The app will require `RECEIVE_SMS`, `READ_SMS`, and `READ_PHONE_STATE` permissions. Users will need to grant these for the feature to work.
> **SIM Mapping**: We will use `Subscription ID` for SIM identification. On some devices, these IDs might change if SIMs are swapped.

## Proposed Changes

### 1. Data Model & Database
We need to store SMS mapping configuration within the `Account` entity.

#### [MODIFY] [AccountEntity.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/shared/data/core/src/main/java/com/ivy/data/db/entity/AccountEntity.kt)
- Add `smsSenderPhone: String?`
- Add `smsSubscriptionId: Int?`
- Add `autoTagId: UUID?`

#### [MODIFY] [Account.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/shared/data/model/src/main/kotlin/com/ivy/data/model/Account.kt)
- Add corresponding fields to the domain model.

#### [MODIFY] [AccountMapper.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/shared/data/core/src/main/java/com/ivy/data/repository/mapper/AccountMapper.kt)
- Update mapping logic to include new fields.

#### [MODIFY] [IvyRoomDatabase.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/shared/data/core/src/main/java/com/ivy/data/db/IvyRoomDatabase.kt)
- Add a Room migration to add the new columns to the `accounts` table.

---

### 2. Navigation & UI Integration
Enhance the navigation system to allow passing pre-filled transaction data.

#### [MODIFY] [Screens.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/shared/ui/navigation/src/main/java/com/ivy/navigation/Screens.kt)
- Update `EditTransactionScreen` data class to include:
    - `amount: Double?`
    - `title: String?`
    - `dateTime: Long?` (epoch millis)
    - `tagIds: List<UUID>`

#### [MODIFY] [EditTransactionViewModel.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/edit-transaction/src/main/java/com/ivy/transaction/EditTransactionViewModel.kt)
- Update `start()` method to apply pre-filled values from the `EditTransactionScreen` object if `initialTransactionId` is null (new transaction mode).

#### [MODIFY] [RootViewModel.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/app/src/main/java/com/ivy/wallet/RootViewModel.kt)
- Update `handleSpecialStart()` to parse additional extras from the Intent (amount, title, date, account, tags) and pass them to `EditTransactionScreen`.

---

### 3. SMS Processing Logic
Create the core logic for receiving and parsing SMS.

#### [NEW] `SmsReceiver.kt`
- A `BroadcastReceiver` that listens for `android.provider.Telephony.SMS_RECEIVED`.
- Logic:
    1. Extract sender and subscription ID.
    2. Query `AccountRepository` for a matching account.
    3. If found, parse the SMS body for an amount (using a regex).
    4. Generate the title: `AUTO <ACC_NAME> TXN <TXN_HASH>`.
    5. Build an Intent with extras and launch `RootActivity`.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/User/StudioProjects/ivy-wallet/app/src/main/AndroidManifest.xml)
- Add required permissions.
- Register `SmsReceiver`.

## Verification Plan

### Automated Tests
- Unit tests for SMS parsing logic (extracting amount and hash).
- Unit tests for `AccountMapper` updates.

### Manual Verification
- Send a mock SMS via ADB to verify the `SmsReceiver` triggers correctly:
  ```bash
  adb shell am broadcast -a android.provider.Telephony.SMS_RECEIVED --es pdus "..." --ei subscription 1
  ```
- Verify that the "Add Transaction" screen opens with correctly pre-filled fields.
- Test with different SIM slot IDs.
