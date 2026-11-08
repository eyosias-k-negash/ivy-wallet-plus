# Replace horizontal scrolling with wrapped layout in Reports Filter

The user wants to replace the horizontal scrolling selection for accounts and categories in the reports filter configuration screen with a wrapped layout that allows vertical scrolling. This will make it easier to see and select multiple items at once.

## Proposed Changes

### Reports Feature

#### [MODIFY] [FilterOverlay.kt](file:///C:/Users/User/StudioProjects/ivy-wallet/feature/reports/src/main/java/com/ivy/reports/FilterOverlay.kt)

- Replace `LazyRow` in `AccountsFilter` with `WrapContentRow`.
- Replace `LazyRow` in `CategoriesFilter` with `WrapContentRow`.
- Adjust padding to maintain consistency with the rest of the screen.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to the Reports screen.
- Open the filter overlay.
- Verify that Accounts and Categories selection now wrap to multiple lines instead of scrolling horizontally.
- Verify that selecting/deselecting items works as expected.
- Verify that the layout looks good on different screen sizes.
