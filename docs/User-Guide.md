# Ivy Wallet Plus user guide

This guide explains the features Ivy Wallet Plus adds to Ivy Wallet and how to use them. Everything else works as it does in Ivy Wallet.

- [Transactions](#transactions): [attachments](#attachments), [locations](#locations), [changing a transaction's type](#changing-a-transactions-type), [tag picker](#tag-picker)
- [Transactions from bank SMS](#transactions-from-bank-sms): [setup](#set-it-up), [patterns](#patterns), [testing](#test-your-patterns), [what happens](#what-happens-when-an-sms-arrives), [fees](#fees), [permissions](#permissions), [out-of-sync warnings](#out-of-sync-warnings)
- [Accounts](#accounts): [descriptions](#account-descriptions), [balance on any day](#account-balance-on-any-day)
- [Planned payments and budgets](#planned-payments-and-budgets): [overdue alerts](#overdue-planned-payment-alerts), [planned payments in budgets](#planned-payments-in-budgets)
- [Home and reports](#home-and-reports): [tags left out of income and expense](#tags-left-out-of-income-and-expense), [reports filter](#reports-filter)
- [Notifications](#notifications)
- [Ivy Wallet+ name and logo](#ivy-wallet-name-and-logo)

## Transactions

### Attachments

Attach a photo or a voice note, such as a receipt, an invoice or a reminder of what a payment was for, to any expense, income or transfer.

- **Attach:** on the transaction screen, tap **Add attachment**, then choose **Take photo** (camera), **Audio note** (your phone's voice recorder) or **Choose from gallery** (Android photo picker).
- **View or play:** once something is attached, the button reads **Image attached** or **Audio note attached**. Tap it to open the file in your image viewer or audio player.
- **Remove:** long-press the button and confirm. The file is deleted too. Each transaction holds one attachment, so remove the current one before adding another.
- **Spot:** transactions with an attachment show a paperclip next to the amount in transaction lists.
- **Free up space:** **Settings → Disk usage** shows how much space images and audio notes take up. **Purge** next to either type deletes all of its files and removes them from their transactions, after you confirm. The transactions themselves stay.

**How it's stored.** The app copies the file into its private storage (`files/attachments/`), so it stays available if you move or delete the original. The transaction keeps the copy's path in the `attachmentUrl` database column. Ivy Wallet's database already had that column but never filled it, so attachments need no migration. When you edit an existing transaction, attaching or removing a file saves right away. On a new transaction, it's saved when you save the transaction.

**Limitations.**

- **Audio note** opens a recorder app through Android's "record sound" request. Not every phone has a recorder that supports it. If yours doesn't, the app says "No voice recorder app found" and nothing is attached.
- Backups don't include attachment files. A backup keeps each transaction's file path, so after restoring on another device the paperclip still shows but tapping it says "File not found".
- Deleting a transaction leaves its attachment file in app storage. **Disk usage** counts these files too, and purging their type deletes them.
- Purged files can't be brought back, since backups don't include them.

### Locations

- **Add:** tap **Add location**, below the date and time on the transaction screen, to save where you are. If needed, the app asks for location permission and to turn location on. It saves coordinates, not a place name.
- **View:** once a location is saved, the row reads **Location attached**. Tap it to open the spot in your maps app.
- **Remove:** long-press **Location attached** and confirm.
- **Spot:** transactions with a location show a pin next to the amount in transaction lists.
- **From SMS:** transactions recorded from a bank SMS get the phone's location automatically, unless **Location** is off in the account's Capture menu. Location must be on, and it's skipped if no fix is found within a few seconds.

### Changing a transaction's type

On the transaction screen, tap the type button at the top (**Income**, **Expense** or **Transfer**) and pick another type. Ivy Wallet showed no such button on transfers, so a transfer couldn't be changed back. The title, date, category, description, tags, attachment and location stay as they are. The account moves to where the new type needs it:

| From | To | Accounts |
|---|---|---|
| Income | Transfer | The account becomes the destination. Pick the account the money came from. |
| Expense | Transfer | The account stays the source. Pick the destination. |
| Transfer | Income | The destination becomes the account, with the amount it received. |
| Transfer | Expense | The source stays the account, with the amount it sent. |

Until you pick the missing account, the app asks for it and the transfer can't be saved, so an existing transaction keeps its old type. If you switch back before picking one, the original account comes back. When an income becomes a transfer, the destination keeps receiving the income's amount. If the source is in another currency, the amount sent is worked out from it at the current exchange rate. Changing either account or the rate on the exchange-rate card works it out again. Once you type the amount sent yourself, the transfer works like any other: the amount sent stays and the amount received follows the rate.

A loan's own transaction can't become a transfer, and a loan record's type still can't be changed.

### Tag picker

The tag picker on the transaction screen uses compact chips in a scrollable area of limited height, so the search field and **Done** stay in view however many tags you have. The Reports tag filter uses the same picker.

## Transactions from bank SMS

Ivy Wallet Plus can read your bank's SMS alerts and record the transactions for you.

### Set it up

Edit an account and turn on **Auto Listen to SMS**. The settings come in three parts. Each part shows what can be filled in for you. Tap its header (**MANUAL** or **DETAILS**) to also show the fields you fill in yourself, and again to hide them.

1. **Profile** says which SMS belong to the account.
   - **SAMPLE SMS** lets you pick an SMS from your bank, and fills in its sender and the SIM it came in on.
   - **Bank phone no. / contact**: tap **PICK** to choose a contact, or open **MANUAL** to type the sender's name or number. An SMS counts when its sender, or the sender's contact name, contains this text. Left empty, it matches every sender.
   - **Contact SIM no.**: optionally choose a SIM so only messages that arrive on it count. The list shows each SIM as `carrier (ID): phone number · SIM name`. The name is the one you gave the SIM in Android's settings, if any. The number only appears when Android knows it, and it's then checked as well. To type the receiving phone number and SIM slot yourself, choose **Manual Entry** or open **MANUAL**.
2. **Capture menu** picks what a transaction gets besides its amount and type: **Location**, **Date**, **Fee**, **Description** and **Balance & rebalance**. All but **Fee** are on by default. **DETAILS** explains each one. Date, fee, description and balance come from their own patterns or from named groups in the master pattern. A **NO REGEX** tag shows when neither can give a ticked value. See [Locations](#locations) and [Out-of-sync warnings](#out-of-sync-warnings).
3. **SMS structure** holds the patterns. **IMPORT CONFIG** loads them all from a JSON file with any of these keys: `masterMatch`, `useMultiRegex`, `incomeRegex`, `expenseRegex`, `amountRegex`, `dateTimeRegex`, `descriptionRegex`, `balanceRegex` and `feeRegex`.
   - **Simple** has the **Master regex**, always shown. A message must match it, and its first capture group, or a group named `amount`, is read as the amount. If you leave it empty, a generic pattern is used, but you'll usually need one that fits your bank's wording.
   - **MANUAL** reveals **Use advanced multi-regex**, which turns on the separate patterns below. **HIDE** and **SHOW** fold them away. A pattern whose box is off in the Capture menu is tagged **OFF IN CAPTURE MENU**.
   - **TEST WITH AN SMS**, at the bottom, tries the patterns on a real SMS.

### Patterns

| Pattern | What it does |
|---|---|
| Income / Expense identification | If either matches anywhere in the message, it sets the transaction type. |
| Amount | The `amount` group is the amount. Commas are stripped. |
| Date/time | Groups named `day`, `month` (a number or an English name like `Jan`), `year` (2 or 4 digits), `hour`, `minute`, `second` and `ampm`, in any order. Only day and month are required. Used when **Date** is on. |
| Description | Every `description` group from every match, one per line. Used when **Description** is on. |
| Balance | The `balance` group is added to the description as `(Bal: …)`, and checked against the account's balance. Used when **Balance & rebalance** is on. See [Out-of-sync warnings](#out-of-sync-warnings). |
| Fee | Every `fee` group from every match is added up, so a service charge and its VAT become one fee. Used when **Fee** is on. See [Fees](#fees). |

**Named groups** like `(?<day>\d{2})` let the values come in any order, so one pattern can read dates written as `20/11/2026` in one SMS and `2026-11-21` in the next. The master pattern can name values too. They're used when a value's own pattern is empty or finds nothing, and in single-regex mode. Patterns without named groups still work by position: group 1 is the amount or balance, the date groups are day, month, year, hour, minute and second, and the description and fee take every group. [Writing SMS patterns](SMS-Patterns.md) lists every name, with examples and tips for sharing a config.

Without a type match, words like "credited", "received", "deposited" or "added" make the transaction income, and anything else makes it an expense. Without a date match, or with **Date** off, the transaction is dated when the SMS arrives. An empty pattern field is ignored, and so is an invalid pattern whose box is off in the Capture menu.

Every pattern runs case-insensitive, with `DOTALL` (`.` also matches new lines) and `MULTILINE` (`^` and `$` match at each line). A long SMS that arrives in several parts is read as one message.

### Test your patterns

Tap **TEST WITH AN SMS**, at the bottom of **SMS structure**, and pick any SMS from your inbox. The tester runs the same parser as the SMS listener, with the values in the form, including edits you haven't saved. It shows:

- whether the sender and SIM checks pass,
- what each pattern matched and every capture group, and whether the Capture menu leaves it out,
- the transaction that would be created, with its fee, or why none would be.

New lines, no-break spaces and other invisible characters show as orange markers. They're often lost when you copy an SMS to a computer, which is why a pattern can work there but fail on the phone. The picked SMS stays selected, so you can edit a pattern and test again. The tester doesn't check the receiving phone number, or whether another account with a matching sender would get the SMS first.

**Add an SMS by hand.** When the picked SMS gives a transaction, tap **Add transaction** at the bottom of the tester, for example for an SMS that arrived before you turned on listening. It opens a new transaction for this account, filled in with the type, amount, date, title, description and `AUTO-` tag the listener would use. Nothing is saved until you tap **Add** there. It doesn't include the fee, which you add as its own expense. The account has to be saved first, because opening the transaction closes the account form.

### What happens when an SMS arrives

When a matching SMS arrives, the app saves the transaction to that account right away, titled `AUTO IN` or `AUTO OUT` plus the account name and a short code made from the message. It tags it `AUTO-<account name>`, a tag created when you save an account with SMS listening on. A "Transaction created" notification then lets you open the transaction to check or edit it. With **Location** on, the transaction also gets the phone's location when the SMS arrived, if one is found within a few seconds.

**Reviewing SMS transactions.** The "Transaction created" notification stays until you've reviewed the transaction:

- Tapping it opens the transaction and the notification stays, so backing out without saving keeps it. **Clear all** skips it.
- It goes away when you tap **Save** on the transaction's screen, delete the transaction there, or swipe the notification away.
- After a reboot, an app update or a force stop, which clear all notifications, the app posts the ones still waiting for review again, without sound. It also does this whenever you open the app.
- Android shows at most about 50 notifications per app. Transactions recorded before this feature was added aren't tracked.

### Fees

With **Fee** on and a fee found, a second expense is saved in your Fees category, titled `AUTO FEE` plus the account name and the same short code. It has the same date, location and tag as the transaction, and its own notification. Leave **Fee** off if your bank's amount already includes the fee, or it's counted twice.

**Fees and Rebalance categories.** The app uses the category you already have with that name, even in another case or spacing, or singular instead of plural (`Fee` for Fees). Only when there's none does it create one, the first time it's needed.

### Permissions

**SAMPLE SMS** and the tester ask for SMS and Contacts access when you open them. **SAMPLE SMS** and the SIM list also ask for Phone access. Otherwise the app doesn't ask for these itself, so allow them in Android's settings under **Apps → Ivy Wallet+ → Permissions**:

- **SMS** is required.
- **Phone** lets the SIM list show your SIMs and their phone numbers.
- **Contacts** lets the app match senders by contact name.
- **Location** set to **Allow all the time** lets the Capture menu's **Location** work while the app is closed. Ticking **Location**, or tapping **ALLOW LOCATION ACCESS** below it, asks for this. With **Allow only while using the app**, SMS transactions get a location only when the app is open.

### Out-of-sync warnings

When a pattern reads the balance from the SMS and **Balance & rebalance** is on in the Capture menu, every SMS transaction (and its fee) is followed by a balance check. If the balance in the SMS differs from the account's balance in the app by 10 or more, in the account's currency, an "Out of sync" notification shows both balances. Smaller gaps don't trigger it. Usually a transaction is missing, doubled or has the wrong amount. From the notification, you can:

- **Tap it** to open Add transaction with the account, your Rebalance category (reused if you have one, otherwise created) and the difference already filled in, as income if the app shows too little or an expense if it shows too much. The difference is worked out again when you tap, so it accounts for changes you made in the meantime, like correcting the SMS transaction. Whatever is left is filled in, even if it's now under 10. If nothing is left to fix, a message says so.
- **Fix manually** to open the account's transactions and find the problem yourself.
- **Dismiss** it.

Each account has at most one warning. The next SMS for the account replaces it, or removes it once the balances are less than 10 apart. Warnings use their own channel, **Account out of sync**, which you can turn off in Android's notification settings for the app. Unlike "Transaction created" notifications, they don't come back after a restart.

## Accounts

### Account descriptions

Each account can have a description, for example what the account is for or who shares it. Type it under the account's name when you create or edit an account. It shows under the name on the Accounts tab, cut to two lines, and in full at the top of the account's screen. Clearing the text removes it. Descriptions are kept in backups.

### Account balance on any day

Open an account's transaction list and tap a day's date header. That day and every newer day then show the account's end-of-day balance (`Bal: …`) instead of the day's cash flow. Tap the same day again to switch back.

## Planned payments and budgets

### Overdue planned payment alerts

Every morning at 9:00, the app checks for planned payments that are past their due date and still unpaid. Each one gets its own notification with the amount, the account and how many days late it is. They're bundled together and alert once for the whole bundle. Tap one to open that payment, where **Pay** or **Get** settles it. Tap the bundle to open Home and its Overdue section.

- **Repeats:** the alerts come back each morning until the payment is paid or skipped, even if you swiped them away.
- **Clears itself:** paying or skipping a payment anywhere in the app removes its notification right away. Opening the app also clears notifications for payments you edited or deleted.
- **Priority:** the notifications use their own high-importance channel, **Overdue planned payments**, and pop up on screen. To let them through Do Not Disturb, add Ivy Wallet+ to Do Not Disturb's allowed apps, or allow reminders there.
- **Off switch:** turn them off with **Settings → Show notifications** in the app, which also stops the daily transaction reminder, or turn off just this channel in Android's notification settings for the app.

### Planned payments in budgets

On the Budgets screen, each budget's bar also counts the month's planned expenses that are still unpaid, including overdue ones. What you've spent is the solid fill. Planned payments follow it as a lighter fill, so the empty part is what's free to spend. The bar takes its color from spent plus planned, so it turns orange or red before the payments land. The amount reads **Free to spend**, or **Planned exceeds budget by** when the planned payments would take you over. Once a payment is paid, it moves from the lighter fill to the solid one. Budgets with no planned payments look the same as before.

## Home and reports

### Tags left out of income and expense

Some transactions aren't really income or spending, like moving money between your own accounts or paying for something you'll be paid back for. Tag them, then go to **Settings → Advanced features → Income and expense**, tap **Exclude tags from income and expense** and tick those tags. Transactions with any ticked tag no longer count toward the Home screen's **Income** and **Expenses** for the period, each day's total in the Home history, or the balance widget's income and expense. They're still listed in the history and still count toward account balances. Other screens, such as Reports and an account's or category's transactions, keep counting them. The excluded tags are saved on the phone, not in backups.

### Reports filter

In the reports filter, accounts and categories show as compact chips that wrap onto several lines, in a scrollable area of limited height, instead of a single row you scroll sideways.

## Notifications

Each kind of notification has its own channel, so you can turn any of them off in Android's notification settings for the app.

| Channel | What it's for | More |
|---|---|---|
| **SMS Transactions** | A transaction or fee was recorded from a bank SMS. Stays until you review it. | [What happens when an SMS arrives](#what-happens-when-an-sms-arrives) |
| **Account out of sync** | A bank SMS reported a balance 10 or more away from the app's. | [Out-of-sync warnings](#out-of-sync-warnings) |
| **Overdue planned payments** | Every morning at 9:00, for each unpaid planned payment past its due date. | [Overdue planned payment alerts](#overdue-planned-payment-alerts) |
| **Transaction reminder** | Ivy Wallet's reminder at 20:00 on days you haven't recorded a transaction. | |

**Settings → Show notifications** in the app turns off the overdue alerts and the transaction reminder.

## Ivy Wallet+ name and logo

The app is called **Ivy Wallet+** and has a new gradient logo. Its package name is `com.ivy.walletplus`, with `.debug` added for debug and demo builds. So it installs alongside the original Ivy Wallet instead of replacing it, and your Ivy Wallet data doesn't move over by itself. To bring it across, export a backup in Ivy Wallet and import it in Ivy Wallet+.

Debug builds are called **Ivy Wallet+ Debug** and have a grey icon, so they're easy to tell apart from a regular install.
