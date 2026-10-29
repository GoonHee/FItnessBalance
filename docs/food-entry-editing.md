# Editing logged food

Implemented independently on top of master 273b1fd. No reference-project code or assets were copied.

Food log entries now have an **Edit logged food** button. The editor changes serving amount, number of servings and meal and previews nutrition before saving. Manual entries also expose calories per serving; their other nutrients remain unavailable.

Database entries scale their historical nutrient snapshot by new total amount / original total amount. Source, original logging time, reference unit, entry ID and verification metadata are preserved. This avoids replacing historical nutrition with current catalogue values. Grams and millilitres cannot be interchanged. Older records without the required snapshot must be removed and logged again.

FoodLogRepository.update replaces only the matching entry and writes recalculated foodLogs totals and dailyData totals in one transaction, preserving unrelated exercise data. The original snapshot must still match at commit time: concurrent changes or removal cause an error rather than overwriting newer data or recreating a deleted entry. Transactions require a connection; failures keep the editor open. After an uncertain acknowledgement, close and reopen to inspect the latest log before retrying.

## Android Studio checks

1. Sign in and log a database food at 100 g, one serving.
2. Open Food log, choose Edit logged food, change to 50 g and two servings. Calories/nutrients should match the original total. Save: entry count stays unchanged.
3. Change to 50 g and one serving: calories and available nutrients halve, while unavailable nutrients remain unavailable. Check daily totals and dashboard after returning.
4. Edit a manual entry: change calories per serving and servings, confirm the product is multiplied correctly and no macros are invented.
5. Change meal, save, reopen, and confirm values remain.
6. Cancel an edit: no document should change. Repeat on another selected date; today should be unchanged.
7. While an editor is open, remove or change that entry on another device. Saving the stale editor must fail; reopen to see current data.
8. Disconnect and attempt save: show failure without a duplicate entry. Reconnect, reopen and retry.
9. Confirm another user's log remains inaccessible with existing owner-only rules.

These device/production checks are not executed by the local JVM suite. No new Firestore collection or rule change is required.
