# Transactions Migration Plan

## Current State
- The app uses separate collections for expenses and incomes:
  - `shared_expenses` for expenses
  - `shared_incomes` for incomes
- The `Transaction` interface is used as a common model for UI display
- Firestore rules include a rule for a `transactions` collection, but it's not being used

## Target State
- Use a single `transactions` collection for both expenses and incomes
- Add a `type` field to distinguish between expense and income transactions
- Maintain backward compatibility during the transition

## Migration Steps

### 1. Create a Unified Transaction Model
- Extend the existing `Transaction` interface or create a new concrete class
- Include a `type` field to distinguish between "EXPENSE" and "INCOME"
- Ensure all necessary fields from both Expense and Income are included

### 2. Create a Unified TransactionRepository
- Create a new repository that uses the `transactions` collection
- Implement CRUD operations for both expenses and incomes
- Add methods to query by transaction type

### 3. Data Migration
- Create a migration utility to:
  - Read all existing data from `shared_expenses` and `shared_incomes`
  - Convert to the new format with appropriate type
  - Write to the `transactions` collection
  - Set a migration flag when complete

### 4. Update Dependent Code
- Update FinanceViewModel to use the new repository
- Modify UI code to work with the unified model
- Update any other code that directly uses ExpenseRepository or IncomeRepository

### 5. Testing Plan
- Test data migration process
- Verify all CRUD operations with the new repository
- Ensure UI displays both types of transactions correctly
- Confirm filtering and reporting features work with the new structure

### 6. Cleanup
- Once migration is complete and stable:
  - Remove old repositories
  - Remove migration code
  - Update Firestore rules to remove unused collections

## Implementation Timeline
1. Create models and repository (1-2 days)
2. Implement migration utility (1 day)
3. Update dependent code (1-2 days)
4. Testing (1-2 days)
5. Cleanup (1 day)

## Risks and Mitigation
- **Data Loss**: Backup all data before migration
- **Backward Compatibility**: Maintain old repositories during transition
- **Performance**: Monitor migration process for large datasets 