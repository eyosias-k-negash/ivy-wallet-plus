# Add image attachments to transactions

This plan outlines the steps to add the ability to attach images to expense, income, and transfer entries.

## Proposed Changes

### Domain & Data Layer

#### [MODIFY] [Transaction.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/shared/data/model/src/main/kotlin/com/ivy/data/model/Transaction.kt)
- Add `attachmentUrl: String?` to `TransactionMetadata` data class.

#### [MODIFY] [TransactionMapper.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/shared/data/core/src/main/java/com/ivy/data/repository/mapper/TransactionMapper.kt)
- Update `toDomain` to extract `attachmentUrl` from `TransactionEntity` and put it into `TransactionMetadata`.
- Update `toEntity` to take `attachmentUrl` from `TransactionMetadata` and save it to `TransactionEntity`.

### Feature Layer (Edit Transaction)

#### [MODIFY] [EditTransactionViewState.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/edit-transaction/src/main/java/com/ivy/transaction/EditTransactionViewState.kt)
- Add `attachmentUrl: String?` to the state.

#### [MODIFY] [EditTransactionViewEvent.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/edit-transaction/src/main/java/com/ivy/transaction/EditTransactionViewEvent.kt)
- Add events for attaching an image (`OnAttachImage(uri: Uri)`) and removing an attachment (`OnRemoveAttachment`).

#### [MODIFY] [EditTransactionViewModel.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/edit-transaction/src/main/java/com/ivy/transaction/EditTransactionViewModel.kt)
- Handle the new events.
- Implement logic to copy the selected image from the provided URI to the app's internal storage (`files/attachments/`) to ensure persistence even if the original file is moved or deleted.
- Update the `loadedTransaction` with the new attachment URL.

#### [MODIFY] [EditTransactionScreen.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/edit-transaction/src/main/java/com/ivy/transaction/EditTransactionScreen.kt)
- Add a new UI section to display and manage attachments.
- Use `rememberLauncherForActivityResult` with `PickVisualMedia` for picking images from the gallery.
- Display a preview of the attached image using Coil's `AsyncImage`.

### Shared UI Layer

#### [MODIFY] [TransactionCard.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/temp/legacy-code/src/main/java/com/ivy/legacy/ui/component/transaction/TransactionCard.kt)
- Add a small attachment icon (using `ic_attachment`) if `transaction.attachmentUrl` is not null, providing a visual cue in the transaction list.

## Verification Plan

### Manual Verification
- Open the "Edit Transaction" screen for a new or existing transaction.
- Tap the "Attach Image" button and select an image from the gallery.
- Verify that the image preview appears in the screen.
- Save the transaction.
- Go back to the transaction list and verify that an attachment icon is shown for that transaction.
- Re-open the transaction and verify that the image is still there.
- Remove the attachment and verify it's gone after saving.
