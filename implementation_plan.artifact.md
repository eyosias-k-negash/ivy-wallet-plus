# Implementation Plan - Account Balance at Day Feature

Add a feature to show the historical balance of an account for a specific day by clicking on the daily net cash flow. The balance is calculated backwards from the current balance.

## Proposed Changes

### [Legacy UI] [temp/legacy-code](file:///C:/Users/User/StudioProjects/ivy-wallet/temp/legacy-code)

#### [MODIFY] [HistoryDateDivider.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/temp/legacy-code/src/main/java/com/ivy/legacy/ui/component/transaction/HistoryDateDivider.kt)
- Add `onClick: () -> Unit` parameter to `HistoryDateDivider`.
- Wrap the cash flow display in a clickable modifier.

#### [MODIFY] [Transactions.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/temp/legacy-code/src/main/java/com/ivy/legacy/ui/component/transaction/Transactions.kt)
- Add `onDayClick: (LocalDate) -> Unit` to `transactions` and `historySection`.
- Pass the callback down to `HistoryDateDivider`.

#### [NEW] [DayBalanceModal.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/temp/legacy-code/src/main/java/com/ivy/legacy/legacy/ui/theme/modal/DayBalanceModal.kt)
- Implement `DayBalanceModal` to show the calculated historical balance.

### [Feature Transactions] [feature/transactions](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/transactions)

#### [MODIFY] [TransactionsEvent.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/transactions/src/main/java/com/ivy/transactions/TransactionsEvent.kt)
- Add `OnDayClick(date: LocalDate)` event.
- Add `OnDayBalanceDismiss` event.

#### [MODIFY] [TransactionsState.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/transactions/src/main/java/com/ivy/transactions/TransactionsState.kt)
- Add `dayBalance: Double? = null` and `dayBalanceDate: LocalDate? = null`.

#### [MODIFY] [TransactionsViewModel.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/transactions/src/main/java/com/ivy/transactions/TransactionsViewModel.kt)
- Handle `OnDayClick`:
    - Calculate `SumCashFlow(date + 1, now)` using `CalcAccBalanceAct`.
    - `historicalBalance = currentBalance - SumCashFlow`.
    - Update state.
- Handle `OnDayBalanceDismiss`:
    - Reset `dayBalance` and `dayBalanceDate`.

#### [MODIFY] [TransactionsScreen.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/transactions/src/main/java/com/ivy/transactions/TransactionsScreen.kt)
- Pass `onDayClick` to `transactions`.
- Display `DayBalanceModal` when `dayBalance` is present.

## Verification Plan

### Manual Verification
1.  Navigate to a specific account's transaction history.
2.  Click on the daily net cash flow for any day.
3.  Verify that a modal appears showing the balance for that day.
4.  Compare the shown balance with a manual calculation (Current Balance - Net change from that day until now).
5.  Dismiss the modal and verify it closes.
