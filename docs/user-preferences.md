# Goals and preferences

Open **Profile → Goals and preferences**. Save and reopen the screen to verify persistence. The Java activity uses Firebase Authentication's current UID and a merge transaction on users/{uid}; it cannot save until existing settings are loaded from the server. Save requires network. Other profile fields, XP, achievements, food logs and exercise logs are not overwritten. Edits survive screen recreation for the same user.

## Stored choices

| Field | Meaning |
|---|---|
| preferredWorkoutMinutes | Whole minutes, 1–180; uses the existing duration preference input |
| preferredWorkoutDays | ISO weekday numbers Monday=1 through Sunday=7; empty means no fixed schedule |
| equipment | none, chair, mat, dumbbells, resistance_band; none is exclusive |
| weeklyWorkoutTarget | Session count, 1–7, used by weekly Momentum |
| dailyCalorieGoal | Optional user-entered kcal target; blank clears the field |
| dailyProteinGoalG | Optional grams of protein; blank clears the field |
| dailyCarbohydrateGoalG | Optional grams of carbohydrate; blank clears the field |
| dailyFatGoalG | Optional grams of fat; blank clears the field |
| dietaryPreferences | Optional halal, vegetarian, vegan, avoid_pork, avoid_alcohol |
| nutritionGoalSource | user_entered or not_set |
| preferencesUpdatedAt | Server timestamp |

New registrations no longer silently receive dailyCalorieGoal=2000. Existing goals remain available for review and can be cleared explicitly; an older goal without source metadata is marked as potentially a default. The form makes no automatic calorie prescription. Nutrition input ceilings reject mistakes and are not recommended intake ranges. Calorie and macro targets are independent; the UI does not imply that they are balanced. Zero is allowed for a macro goal but not a calorie goal.

Existing recommendation code reads workout minutes, days and equipment the next time Exercise is opened. Nonselected days can trigger gentle recovery. Preference matching operates within unlocked available content; a 20-minute preference does not generate new 20-minute sessions. The current seeded catalogue uses only none/chair, although future content may use the other equipment options. Weekly target changes adjust displayed Momentum without granting XP just for changing settings; existing completion transactions handle subsequent rewards.

Dietary and nutrition choices are used by the reviewed-food suggestion module; see FOOD_RECOMMENDATIONS.md. They do not filter ordinary search, prove halal status, certify ingredients or prevent allergen exposure. Existing diet/BMI profile features remain separate. This change does not integrate Health Connect or implement physiological goal adaptation, dashboard goal visualisations.

No new collections, catalogue seed or Firestore rules deployment is required with the repository's owner-scoped profile rules.

## Android Studio acceptance checks

1. Pull latest master on a clean branch, sync Gradle, assemble and run.
2. Profile → Goals and preferences: set duration 20, Monday/Wednesday/Friday, a stable chair and target 4; save, leave and reopen. All choices should persist.
3. Reopen Exercise. Duration is a preference, equipment restricts applicable choices, and an unselected day may recommend recovery. Existing discomfort priority still applies.
4. Select No equipment: other equipment choices clear. Select a chair: No equipment clears. Leaving every day unchecked removes the fixed schedule.
5. Set explicit calorie/protein/carbohydrate/fat goals and dietary preferences. Save and reopen. Clear a goal and save; the Firestore field should be removed, not set to zero.
6. Verify profile name, weight, XP, completed sessions, daily calories and historical food entries are unchanged.
7. Try blank/invalid duration, target 0/8 or negative/out-of-range nutrition values: do not save.
8. Rotate with unsaved edits: edits should remain. Disconnect: load/save must report failure and never claim success. Reconnect and retry.
9. A new account should have no calorie goal until one is chosen. An older 2000 goal remains visible for explicit review.
10. Sign out/switch accounts: settings must never be saved to another user's profile.

Local unit tests verify bounds, optional targets, exclusive equipment and mapping to the existing recommendation preferences. UI, live Firebase and device behaviour require these acceptance checks.
