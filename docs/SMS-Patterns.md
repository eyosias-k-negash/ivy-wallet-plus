# Writing SMS patterns

This guide is for writing the patterns that turn a bank's or wallet's SMS into transactions, and for sharing them with other people as a config file. The [README](../README.md#transactions-from-bank-sms) covers setting up an account.

Patterns are Java regular expressions. Every pattern runs case-insensitive, with `DOTALL` (`.` also matches new lines) and `MULTILINE` (`^` and `$` match at each line).

## Named groups

Name the parts of the SMS you want, and the app reads them by name, wherever they are in the message:

```
on (?<day>\d{2})/(?<month>\d{2})/(?<year>\d{4})
```

The order of the groups doesn't matter, so `(?<year>\d{4})-(?<month>\d{2})-(?<day>\d{2})` works too.

| Name | Read from | What it holds |
|---|---|---|
| `amount` | Amount, master | The transaction amount. Commas are removed, so `1,250.00` is 1250. |
| `balance` | Balance, master | The balance after the transaction. It's added to the description as `(Bal: …)` and [checked against the app](../README.md#out-of-sync-warnings). |
| `description` | Description, master | Text for the description. Every `description` group that takes part, from every match, goes on its own line. |
| `income`, `expense` | Master | If the group takes part in the match, it sets the transaction type. |
| `day` | Date/time, master | 1 to 31. |
| `month` | Date/time, master | 1 to 12, or an English month name: `Jan`, `Sept` or `January`. |
| `year` | Date/time, master | 2 or 4 digits. Without it, the year the SMS arrived is used. |
| `hour` | Date/time, master | 0 to 23, or 1 to 12 with `ampm`. Without it, the time is midnight. |
| `minute`, `second` | Date/time, master | Without them, 0. |
| `ampm` | Date/time, master | Text starting with `a` or `p`, like `AM`, `pm` or `a.m.` |

"Read from" is where the parser looks for the name. In the **Amount Extraction** pattern, for example, only `amount` counts. The tester flags a name that's misspelled or in a pattern that doesn't read it.

Names ignore case: `Day` is the same as `day`.

### Where each value comes from

1. The value's own pattern, like **Date/Time Extraction** for the date.
2. If that pattern is empty, doesn't match or gives no value, the group with the same name in the master pattern.
3. If neither gives one: no amount means no transaction, no date means the time the SMS arrived, and no type means keywords decide. Words like "credited" or "received" make it income, anything else an expense.

For the type, the **Income** and **Expense Identification** patterns come first. If either matches anywhere in the message, it decides.

### Patterns without names

A pattern with no named groups works as it always has, by group position:

- Amount and balance: group 1.
- Date/time: day, month and year, optionally followed by hour, minute and second.
- Description: every group.
- Master: group 1 is the amount. Nothing else is read from it.

Once a pattern has a named group, only names count in it. Unnamed groups are then ignored, so you can use them freely, like `(debited|credited) with (?<amount>[\d,.]+)`.

## Dates written two ways

Some senders write `20/11/2026` in one message and `2026-11-21` in another. Java doesn't allow the same group name twice in a pattern, so add digits to the names in the second layout. The parser treats `day2` as `day`, `month2` as `month`, and so on:

```
on (?:(?<day>\d{2})/(?<month>\d{2})/(?<year>\d{4})|(?<year2>\d{4})-(?<month2>\d{2})-(?<day2>\d{2})) (?<hour>\d{2}):(?<minute>\d{2}):(?<second>\d{2})
```

Only one of the two layouts takes part in a match, and the parser uses the groups that did. If several groups for the same value take part, the first one is used. The description is the exception: it uses them all.

A date that doesn't exist, like month 13 or 31 November, is rejected instead of rolled over, and the transaction gets the time the SMS arrived. The tester says why, and points out when the day and month look swapped.

## A full config

**IMPORT CONFIG**, in the account's SMS settings, loads a JSON file with these keys. Any key can be left out. In JSON, every backslash is written twice, so `\d` becomes `\\d`.

Say Example Bank sends these:

> Dear Customer, your account 1\*\*\*\*89 was debited with USD 1,250.00 on 20/11/2026 at 09:15:00 for Payment to Corner Shop. Available balance: USD 8,400.50.
>
> Dear Customer, your account 1\*\*\*\*89 was credited with USD 40.00 on 2026-11-21 at 18:30:05 from Jane Doe. Available balance: USD 8,440.50.

With **Use Advanced Multi-Regex**, each value has its own pattern:

```json
{
  "useMultiRegex": true,
  "masterMatch": "^Dear Customer, your account \\d\\*+\\d+ was (?:debited|credited)",
  "amountRegex": "(?:debited|credited) with USD (?<amount>[\\d,.]+)",
  "incomeRegex": "credited",
  "expenseRegex": "debited",
  "dateTimeRegex": "on (?:(?<day>\\d{2})/(?<month>\\d{2})/(?<year>\\d{4})|(?<year2>\\d{4})-(?<month2>\\d{2})-(?<day2>\\d{2})) at (?<hour>\\d{2}):(?<minute>\\d{2}):(?<second>\\d{2})",
  "descriptionRegex": "(?<description>(?:for|from) [^.]+)\\.",
  "balanceRegex": "balance: USD (?<balance>[\\d,.]+)\\."
}
```

Without it, one pattern can name everything:

```json
{
  "useMultiRegex": false,
  "masterMatch": "^Dear Customer, your account \\d\\*+\\d+ was (?:(?<expense>debited)|(?<income>credited)) with USD (?<amount>[\\d,.]+) on (?:(?<day>\\d{2})/(?<month>\\d{2})/(?<year>\\d{4})|(?<year2>\\d{4})-(?<month2>\\d{2})-(?<day2>\\d{2})) at (?<hour>\\d{2}):(?<minute>\\d{2}):(?<second>\\d{2}) (?<description>(?:for|from) [^.]+)\\..*?balance: USD (?<balance>[\\d,.]+)\\."
}
```

The single pattern is harder to read, and if any part fails to match, nothing is read. Separate patterns fail one at a time, and the tester shows which.

## Tips

- **Test on the phone.** Tap **TEST WITH AN SMS** and pick a real message. Copying an SMS to a computer often loses no-break spaces and other invisible characters, so a pattern can work there and fail on the phone. The tester marks these characters. `\s` doesn't match a no-break space; `[\s ]` does.
- **Stop text before a word** with `(?:(?! on ).)*`, which takes everything up to " on ".
- **Write `.` as `\.`** when you mean a full stop. A bare `.` matches any character.
- **Amounts with a decimal comma**, like `1.250,00`, aren't read correctly yet, because commas are removed.
- **Before sharing a config,** take out anything personal. Replace your name with `\w+` and account digits with a pattern like `\d\*+\d+`, so the config works for anyone with that bank.
