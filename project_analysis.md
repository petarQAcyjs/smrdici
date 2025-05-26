# Finances specifications:

## Front end:

### Finances screen layout:
App header (just like other screens, with profile icon enabled).A Time period navigation bar with clickable TextView showing the currently selected date range "Meseec 01.03.2025 – 31.03.2025" or "June" or "2025" (depends on what time period is selected). Left and right arrows for navigation between previous or future time period.When current time period is NOT displayed, a clickable TextView ("Vratite se na trenutni period") styled as a hyperlink, triggering a setOnClickListener to reset the date range (Vratite se na trenutni - has different suffix based on selected time period - month, day, week or in case of a custom period - period).A tabbed navigation bar, with two tabs (Expenses and Income tabs).A CardView which says what Account data is displayed in the list below and not clickable Section which shows current balance on the selected account.A list of expenses (or Incomes) is displayed with the Sort Filter Dropdown at the top right. Each list item is a custom ViewHolder containing:
A TextView for the category (e.g., "HEALTH", "DM", "MICA").
A TextView for the description or note
A TextView for the date the transaction was created
A TextView for the Account (For testing purposes, informational - TO BE REMOVED WHEN DONE)
A TextView for the amount (e.g., "4060 EUR", "3704 EUR", "3072 EUR") with currency formatting.
Two ImageButton icons on the right: a pencil for editing and a red trash bin for deletion.
Floating button for creation of transaction.

## Back End:

App header:
Clicking the back arrow in the header leads user back to Home screen.Clicking the profile icon leads user to the Profile screen (Settings)
Time period navigation:
When clicked, a drop-down menu is displayed with options: Year, Month, Week, Day or Custom period.
By selecting any option, user chooses which time period filter will be applied to the list of transactions (income or expenses) below.
Clicking left or right arrows user navigates back and forward between selected time period ranges (eg. if the day is selected option, clicking the left arrow applies time period filter to transactions list for the previous day - same goes for other time periods).
Clicking on a hyperlink, user resets filter to show current of whatever time period is selected.
The default time period for initial opening of finances screen is set in Finances settings screen.
Tabbed navigation bar:
Clicking on each of the options, user chooses which type of transactions is displayed in the list below (Income or Expenses).
A card view:
By clicking on Account name, the dropdown menu is displayed to allow user to choose for which account the data in the list below is displayed (options are each of configured account that are configured in Finances settings screen or the combination of all accounts). Selecting any option applies filter to the data in the list bellow to show data from that account only.
Not clickable Current balance section shows the current balance of selected account in currency that is selected for that exact account. What account uses which currency is configurable in Finances settings. If All is selected, the total amount of all existing accounts should be displayed in EUR.
Calculation for all should be done in a way to convert current balance from all accounts to EUR, add them together and show it here as information.
A list of transactions:
Clicking on the Sort filter dropdown, the dropdown menu is displayed with options to sort data by Date (rising or dropping) or Amount (rising or dropping) which applies the filter to the data below.
By clicking the edit button, transaction edit dialogue is displayed and user can change any of the transaction details (Category, Description, Date, Account and Amount). By confirming edit the Transaction detais are updated in both local memory (and cache) as well as in DataBase (Firestore).
By clicking the Delete button, the transaction is being deleted both localy and from DB but user can click the Undo if the transaction is deleted by mistake.
Clicking the Floating button, the Create transaction (income or expense) dialogue is displayed with same fields as the Edit dialogue. By confirming creation, the transaction is being saved both locally and in DB.
Editing, deleting or adding new transaction updates the current balance accordingly.

# Completed Tasks
- Fixed package declarations in finance screen files from 'budget' to 'finance'
- Updated imports in NavGraph.kt to use the correct packages
- Renamed BudgetSettings to FinanceSettings in Screen.kt
- Updated route from "budget_settings" to "finance_settings"
- Fixed references in ProfileScreen.kt
- Renamed BudgetSettingsViewModel to FinanceSettingsViewModel
- Updated SettingsRepository keys with migration code
- Enhanced TransactionRepository usage in Add/Edit screens
- Updated RepositoryManager with finance-specific methods
- Added backward compatibility for deprecated methods

# Updated Implementation Checklist

## Main Finance Screen (FinanceScreen.kt)
- [ ] Implement time period navigation
  - [ ] Create date range selector component
  - [ ] Add navigation arrows for period switching
  - [ ] Add "Return to current period" functionality
  - [ ] Implement period state management
- [ ] Add transaction type tabs
  - [ ] Expenses tab
  - [ ] Income tab
  - [ ] Tab switching logic
- [ ] Create account selection card
  - [ ] Account dropdown implementation
  - [ ] Current balance display
  - [ ] Multi-currency balance calculation
  - [ ] Total balance in EUR calculation

## Transaction List
- [ ] Create TransactionList composable
  - [ ] Implement sort/filter functionality
  - [ ] Create transaction item layout
- [ ] Add transaction filtering by:
  - [ ] Selected time period
  - [ ] Selected account
  - [ ] Transaction type (Income/Expense)
- [ ] Implement list item actions
  - [ ] Connect edit functionality to existing EditScreens
  - [ ] Implement delete with undo functionality
  - [ ] Update balance calculations on actions

## Data Layer Enhancements
- [x] Add time period filtering to repositories
- [ ] Implement account balance tracking
- [x] Add currency conversion functionality (Implemented in CurrencyConverter.kt)
- [x] Create caching mechanism for offline support
- [x] Add transaction sync mechanism

## State Management
- [ ] Create FinanceViewModel for main screen
  - [ ] Time period state
  - [ ] Selected account state
  - [ ] Current balance state
  - [ ] Transaction list state
- [ ] Implement proper error handling
- [ ] Add loading states
- [ ] Handle offline mode

## UI Polish
- [ ] Add proper loading indicators
- [ ] Implement error messages
- [ ] Add animations for:
  - [ ] Tab switching
  - [ ] List item actions
  - [ ] Period navigation
- [ ] Optimize list performance
- [ ] Implement pull-to-refresh

## Settings Integration
- [ ] Connect to existing account settings
- [ ] Add default period preference
- [ ] Implement currency preferences
- [ ] Add account order configuration

# Implementation Checklist

## Setup and Infrastructure
- [x] Create necessary project structure
- [x] Set up dependency management
- [x] Configure Firebase/Firestore integration
- [x] Set up proper state management solution
- [x] Implement currency conversion utility
- [x] Set up proper navigation system

## UI Components
### App Header
- [x] Implement app header with back arrow
- [x] Add profile icon with navigation
- [x] Style header according to app theme

### Time Period Navigation
- [ ] Create time period selector dropdown
  - [ ] Year option
  - [ ] Month option
  - [ ] Week option
  - [ ] Day option
  - [ ] Custom period option
- [ ] Implement navigation arrows
- [ ] Add "Return to current period" link
- [ ] Create date range display
- [ ] Implement period navigation logic

### Transaction Type Tabs
- [ ] Create tabbed navigation
- [ ] Implement Expenses tab
- [ ] Implement Income tab
- [ ] Add tab switching logic

### Account Selection
- [ ] Create account selection card
- [ ] Implement account dropdown
- [ ] Add current balance display
- [ ] Implement multi-currency balance calculation
- [ ] Add EUR conversion for total balance

### Transaction List
- [ ] Create transaction list component
- [ ] Implement sort/filter dropdown
  - [ ] Date sorting (ascending/descending)
  - [ ] Amount sorting (ascending/descending)
- [ ] Create transaction item layout
  - [ ] Category display
  - [ ] Description/note field
  - [ ] Date display
  - [ ] Account info (temporary)
  - [ ] Amount with currency
  - [ ] Edit button
  - [ ] Delete button
- [x] Implement edit functionality
  - [x] Edit dialog
  - [x] Form validation
  - [x] Update in local state
  - [x] Update in Firestore
- [ ] Implement delete functionality
  - [ ] Delete confirmation
  - [ ] Undo functionality
  - [ ] Remove from local state
  - [ ] Remove from Firestore

### Transaction Creation
- [x] Create floating action button
- [x] Implement transaction creation dialog
  - [x] Category selection
  - [x] Description input
  - [x] Date selection
  - [x] Account selection
  - [x] Amount input
  - [x] Form validation
- [x] Add local state update
- [x] Add Firestore update

## Data Management
- [x] Implement local caching
- [x] Set up Firestore listeners
- [x] Create data models
- [ ] Implement error handling
- [ ] Add loading states
- [x] Set up offline support

## Settings
- [x] Create finance settings screen
- [x] Implement account configuration
- [x] Add currency settings
- [x] Set default time period option

## Testing
- [ ] Unit tests for business logic
- [ ] Integration tests for data flow
- [ ] UI tests for critical paths
- [ ] Performance testing
- [ ] Edge case testing

## Polish
- [ ] Add proper error messages
- [ ] Implement loading indicators
- [ ] Add animations
- [ ] Optimize performance
- [ ] Add proper documentation

## Summary of Missing Features:
1. Multi-currency balance calculation integration
2. Sort/filter functionality for transactions
3. Undo functionality for transaction deletion
4. Account balance tracking

# Finances Feature Issues and Solutions

## Current Issues

### Package Naming Inconsistencies
- [x] Files in `ui/screens/finance` directory have incorrect package declarations (`budget` instead of `finance`)
- [x] Import conflicts in NavGraph.kt due to incorrect package declarations

### Navigation Route Inconsistencies
- [x] Screen.kt still has `BudgetSettings` route instead of "FinanceSettings"
- [x] NavGraph.kt references `BudgetSettingsScreen` and `BudgetSettingsViewModel`

### Repository Layer Issues
- [x] TransactionRepository exists but is underutilized
- [ ] ExpenseRepository and IncomeRepository have significant code duplication
- [x] RepositoryManager has outdated `getExpenseRepositoryForBudget()` methods

### Settings Layer Issues
- [x] SettingsRepository uses budget-specific keys (e.g., `KEY_PERIOD = "budget_period"`)
- [x] BudgetSettingsViewModel class should be renamed to FinanceSettingsViewModel

### Database Structure Issues
- [ ] Firestore indexes still reference "budgets" collection
- [ ] No clear migration path from old budget collections to new finance collections

### Incomplete Feature Implementation
- [ ] Sort/filter functionality for transactions is missing
- [ ] Undo functionality for transaction deletion is not implemented
- [ ] Time period navigation is partially implemented but lacks custom period dialog
- [ ] Account balance tracking needs refinement

## Implementation Plan

### Phase 1: Package and Navigation Fixes ✅
- [x] Fix package declarations in all finance screen files
  - [x] AddExpenseScreen.kt
  - [x] AddIncomeScreen.kt
  - [x] EditExpenseScreen.kt
  - [x] EditIncomeScreen.kt
  - [x] ExpenseViewModel.kt
  - [x] IncomeViewModel.kt
- [x] Update Screen.kt (rename BudgetSettings to FinanceSettings)
- [x] Update NavGraph.kt for consistent navigation
- [x] Update all references to BudgetSettings in ProfileScreen.kt

### Phase 2: Repository Layer Refactoring ✅
- [x] Update SettingsRepository keys with migration code
  - [x] Rename KEY_PERIOD from "budget_period" to "finance_period"
  - [x] Add migration code to preserve existing settings
- [x] Rename BudgetSettingsViewModel to FinanceSettingsViewModel
- [x] Update RepositoryManager with finance-specific methods
  - [x] Replace getExpenseRepositoryForBudget() with getExpenseRepositoryForFinance()
  - [x] Replace getIncomeRepositoryForBudget() with getIncomeRepositoryForFinance()
  - [x] Replace getAccountRepositoryForBudget() with getAccountRepositoryForFinance()

### Phase 3: Enhance TransactionRepository
- [x] Add methods to consolidate common functionality
- [ ] Implement transaction filtering by time period
- [ ] Add support for multi-currency calculations

### Phase 4: Feature Implementation
- [ ] Add sort/filter functionality to FinanceViewModel
  - [ ] Implement SortOption enum (DATE_ASC, DATE_DESC, AMOUNT_ASC, AMOUNT_DESC)
  - [ ] Add sorting methods
- [ ] Implement undo functionality for transaction deletion
  - [ ] Create DeletedTransaction data class
  - [ ] Add undoDelete method
- [ ] Create custom period dialog for time period selection
  - [ ] Implement DatePicker composable
  - [ ] Add validation logic

### Phase 5: Testing and Polishing
- [ ] Test all navigation paths
- [ ] Verify account balance tracking works correctly
- [ ] Ensure multi-currency calculations are accurate
- [ ] Add proper loading indicators and error handling
- [ ] Update Firestore indexes if needed

## Next Steps
1. Implement transaction filtering by time period in TransactionRepository
2. Add support for multi-currency calculations
3. Implement sort/filter functionality in FinanceViewModel
4. Add undo functionality for transaction deletion
5. Create a custom period dialog for time period selection