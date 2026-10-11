# SMS Transaction Configuration Enhancements

I have implemented several enhancements to the SMS transaction listening feature to make it easier to configure and more robust, especially for multi-SIM devices.

## Key Changes

### 1. New "Receiving Phone Number" Field
- **Database Update**: Added `smsReceiverPhone` to `AccountEntity` (Migration 132 -> 133).
- **Domain Model**: Updated `Account` and `CreateAccountData` to include the receiving phone number.
- **UI**: Added a new field in the Account Modal for "Receiving Phone Number (SIM)".

### 2. Intelligent SIM Picker
- **Automatic Retrieval**: When selecting a SIM card from the dropdown, the app now automatically fetches and populates the **Receiving Phone Number** from the SIM card.
- **Enhanced Dropdown**: The SIM selection dropdown now displays:
    - **Display Name** (e.g., "Google Fi")
    - **Subscription ID**
    - **Telecom Carrier Name**
    - **Phone Number** (if available)

### 3. Flexible Configuration Modes
- **Auto Selection**: The default mode uses a guided dropdown to pick a SIM slot.
- **Manual Mode**: If permissions are missing or the user prefers manual entry, they can switch to "Manual Entry" mode to type in the Subscription ID and Receiving Phone Number themselves.
- **Toggleable UI**: Easy switching between Auto and Manual modes with a single click.

### 4. Technical Fixes
- **Correct Mapping**: Fixed a logic error where the SIM's phone number was incorrectly being mapped to the *Sender* (Bank) field. It is now correctly mapped to the *Receiver* field.
- **Permission Safety**: Added `@SuppressLint("MissingPermission")` and proper try-catch blocks for all telephony API calls to ensure stability.

### 5. Contact Name Identification
- **Permission Added**: Added `READ_CONTACTS` permission to resolve phone numbers to contact names.
- **"PICK" Contact Button**: Added a button next to the "Sender Name or Number" field that opens the system contact picker.
- **Smart Matching**: The SMS listener now resolves the incoming sender's phone number to a contact name (e.g., "Chase Bank") and matches it against the name you've entered. This allows you to group multiple bank numbers/short-codes under a single contact name.
- **Case-Insensitive Matching**: Matching is now case-insensitive for better reliability.

### 6. Multi-Layer Safety Verification
- **Strict SIM Slot Matching**: If a SIM Slot (Subscription ID) is configured for an account, the app now enforces a strict match. SMS received on a different SIM slot will be ignored for that specific account.
- **Receiver Phone Verification**: Added a secondary validation layer. If a "Receiving Phone Number" is saved, the app cross-references it with the actual phone number of the SIM slot that received the message. If they don't match (e.g., if SIM cards were swapped), the transaction will not be auto-processed.
- **Robust Phone Formatting**: Verification is done using partial matching to account for different international formats (e.g., with or without the `+` prefix).

### 7. Automatic Transaction Type Detection
- **Smart Keyword Analysis**: The SMS listener now scans the message body for keywords to determine if the transaction is an **Income** or **Expense**.
    - **Income Keywords**: "credited", "received", "deposited", "added".
    - **Expense Keywords**: "debited", "sent", "withdrawn", "spent", "paid", "purchased".
- **Dynamic Prefixing**: Auto-created transaction titles now include an "IN" or "OUT" prefix based on the detected type (e.g., `AUTO IN Chase 4A2B`).
- **Safety Default**: If no keyword is matched, it defaults to **Expense** to ensure you don't miss any spending.

### 8. Advanced Multi-Regex Parsing
- **New Feature**: Added a "Use Advanced Multi-Regex" option for more complex SMS formats.
- **Granular Control**: Instead of one regex for everything, you can now define separate regexes for:
    1. **Master Match**: To identify if the SMS contains transaction data (requires only a partial match).
    2. **Income/Expense Identification**: Specific regexes to detect the transaction type.
    3. **Amount Extraction**: Regex to pick out the exact amount (from the 1st capturing group).
    4. **Date/Time Extraction**: Regex to parse the transaction timestamp (supporting 3 or 6 groups).
    5. **Description Extraction**: Joins all capturing groups from this regex with new lines for the title.
    6. **Balance Extraction**: Regex to identify the remaining account balance (from the 1st capturing group).
- **Database Migration**: Upgraded database to version **134** to support these new fields.
- **Improved Logic**: `SmsReceiver` now intelligently switches between the simple single-regex mode and the advanced multi-regex mode based on your account settings.

## How to test
1. Open any **Account** or create a new one.
2. Enable **"Auto Listen to SMS"**.
3. Toggle **"Use Advanced Multi-Regex"**.
4. Define your specific regexes for Amount, Description, etc.
5. (Simulated) When an SMS arrives, the app will run each regex to extract the most accurate information possible for the new transaction.
