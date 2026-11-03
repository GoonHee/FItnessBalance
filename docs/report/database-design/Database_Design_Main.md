# 3.4.3 Database Design

FitnessBalance uses Cloud Firestore as its principal cloud database. Shared catalogue collections store food, exercise and reward definitions, while private logs, preferences and progress are organised beneath the authenticated user's profile document. Document-oriented modelling supports grouping related information around application access patterns (Sadalage & Fowler, 2012). In FitnessBalance, the Firebase Authentication UID identifies the users document and determines ownership of its private subcollections. Passwords are managed by Firebase Authentication and are not stored in Firestore.

This section summarises the main data structures and the reasons for their organisation. The complete field dictionaries, including data types, optional fields, embedded records and supporting receipts, are provided in Appendix A. The simplified ERD in Section 3.4.4 illustrates the core relationships; it does not imply that the supporting collections have been removed.

## 3.4.3.1 User Information and Preferences

The users collection stores personal profile information, explicit nutrition goals and workout preferences. Preference fields support the implemented food and workout recommendation rules, while protected progress fields provide the user's accumulated XP and level. Some fields are created only after preferences are configured or an eligible workout is recorded. Table 3.3 groups the main fields; Appendix A.1 provides the detailed dictionary.

Table 3.3: User information and preference groups

| Data group | Main fields | Purpose |
| --- | --- | --- |
| Profile | fullName, email, weight, height, fitnessLevel | Stores personal information and the current profile measurements. |
| Nutrition goals | dailyCalorieGoal, dailyProteinGoalG, dailyCarbohydrateGoalG, dailyFatGoalG | Stores explicitly selected calorie and macronutrient targets. |
| Workout preferences | preferredWorkoutMinutes, preferredWorkoutDays, weeklyWorkoutTarget, equipment | Supports workout scheduling, eligibility and duration preferences. |
| Food preferences | dietaryPreferences, preferredFoodIds, dislikedFoodIds | Supports filtering and ranking of eligible food suggestions. |
| Progress summary | totalXp, level, achievementIds, metGoalWeeks | Provides accumulated application progress and achieved target weeks. |

## 3.4.3.2 Food and Nutrition Data

The foods collection contains the Malaysian food catalogue imported into Firestore. Each definition preserves available source nutrition, its reference basis and provenance. Food consumption is stored separately in dated foodLogs documents as embedded foodItems snapshots. These snapshots retain the selected portion and the available nutrition at logging time, so later catalogue changes do not silently rewrite historical consumption.

Saved meals and recipes reuse ingredient or portion snapshots, while personal servings provide user-defined labels tied to measurable amounts. Private barcode products and personal nutrition overrides are stored under the user's account rather than modifying the shared catalogue. Missing values remain distinguishable from zero, and aggregate totals retain coverage information. Table 3.4 summarises the structures; Appendix A.2–A.5 and A.15–A.19 provide their detailed dictionaries.

Table 3.4: Food and nutrition data structures

| Collection / subcollection | Main fields | Purpose |
| --- | --- | --- |
| foods | food_name, nutrition_basis, nutrition, source_database | Stores shared food definitions and source reference nutrition. |
| foodLogs | date, foodItems, totalCalories, nutrientTotals, nutrientCoverage | Stores dated consumption snapshots and known nutrient totals. |
| savedMeals | name, items, revision | Stores reusable groups of food portions. |
| recipes | name, items, yieldServings, revision | Stores ingredient snapshots and recipe yield. |
| personalServings | foodKey, name, amount, unit | Defines a private serving label with a measurable amount. |
| barcodeFoods | barcode, food_name, nutrition, label_nutrition | Stores user-entered private product definitions. |
| foodNutrition | originalFoodId, nutrition, source_kind, revision | Stores private label-based or approximate alternative nutrition. |

## 3.4.3.3 Exercise Data

Exercise content is separated from user activity. Shared exercises define reusable movements and instructions. workoutPaths contain lessons, and each lesson contains ordered workoutExercises assignments referencing the exercise definitions. This allows the same exercise to be used in different lessons without duplicating its instructional content.

A guided workout creates a workoutSessions start record and, upon completion, an immutable workoutRecords result using the same session identifier. The result preserves completion, duration, estimated calories, feedback and awarded progress. Manual activities and explicitly confirmed wearable sessions use the separate dated exerciseLogs structure. Table 3.5 summarises these records; Appendix A.7–A.14 and A.22 provide the field dictionaries.

Table 3.5: Exercise catalogue and activity structures

| Collection / subcollection | Main fields | Purpose |
| --- | --- | --- |
| exercises | name, instructions, targetType, equipment, metValue | Defines reusable exercises and estimation inputs. |
| workoutPaths | name, description, active | Organises guided workout content into paths. |
| lessons | sequence, difficulty, estimatedMinutes, baseXp, prerequisiteLessonId | Defines lesson information and progression eligibility. |
| workoutExercises | exerciseId, sequence, targetType, targetValue | Assigns ordered exercise targets within a lesson. |
| workoutSessions | pathId, lessonId, plannedExercises, startedAt | Preserves the guided workout's initial context. |
| workoutRecords | lessonId, completionPercentage, durationSeconds, feedback fields, xpEarned | Stores immutable guided outcomes and reward results. |
| exerciseLogs | date, exercises, totalCaloriesBurned | Stores dated manual and confirmed wearable activity entries. |
| pathProgress | completionCounts, bestPercent, bestStars | Tracks per-user attempts and best lesson results. |

## 3.4.3.4 Progress and Gamification Data

dailyData stores dated food and exercise summaries for the dashboard and progress views. This controlled denormalisation reduces the need to reconstruct every summary from its source records during each read, but requires coordinated updates to keep duplicated values consistent (Kleppmann, 2017). Food and exercise operations therefore update their relevant summary fields without replacing unrelated totals.

Dated weight measurements are stored in weightLogs, with weightHistory maintaining a private index for history operations. Gamification uses exerciseWeeks for shared weekly progress, questProgress for dated quest completions, and unlockedAchievements for earned achievement evidence. Shared quests and achievements provide definition metadata. The design tracks weekly targets and Momentum rather than consecutive-day streaks. Table 3.6 summarises these structures; Appendix A.6 and A.20–A.27 contain their detailed dictionaries.

Table 3.6: Progress and gamification structures

| Collection / subcollection | Main fields | Purpose |
| --- | --- | --- |
| dailyData | caloriesConsumed, caloriesBurned, nutrientTotals, exerciseEntryCount | Provides dated dashboard summaries and completeness information. |
| weightLogs | day, weightKg, revision | Stores manual weight measurements by date. |
| weightHistory | entries, updatedAt | Maintains the private weight-history index. |
| exerciseWeeks | completed, target, categories, bonusAwarded | Stores shared weekly workout progress and bonus status. |
| quests | name, target, bonusXp | Stores shared quest definition metadata. |
| questProgress | day, questId, recordId, xpEarned | Records immutable daily quest completions. |
| achievements | name, description, active | Stores shared achievement display metadata. |
| unlockedAchievements | recordId, unlockedAt | Records the evidence for each earned achievement. |

## 3.4.3.5 Health Data and Data Integrity

Health Connect synchronisation uses private, account-specific local caches rather than automatically creating cloud health-record collections. Summaries and a rolling wearable-session cache remain on the device until the user explicitly confirms a wearable workout. Confirmation saves the selected activity metadata, elapsed duration and available active calories into exerciseLogs. Qualifying sessions can also create separate reward evidence. Raw health samples and the raw energy-interval list are not uploaded by this workflow.

Supporting receipts prevent a repeated meal insertion or wearable confirmation from creating duplicate contributions. Wearable award and date-credit records additionally prevent repeated XP and weekly credit. Firestore transactions apply their writes atomically and can retry when concurrent changes affect their reads (Google, n.d.). FitnessBalance combines transactions, stable identifiers and revision checks to coordinate related log and progress changes. Table 3.7 summarises the cloud support records; their fields and the local-storage inventory are provided in Appendix A.28–A.32.

Firestore security rules restrict private records to the owner identified by the parent UID and protect shared catalogue data from ordinary client writes. Stored identifiers express logical associations rather than SQL foreign-key constraints. Supporting reward receipts are retained under the implemented policy even if a confirmed activity is later removed from calorie and duration totals. Appendix A.33 summarises the logical collection relationships.

Table 3.7: Supporting confirmation and consistency records

| Supporting subcollection | Main fields | Purpose |
| --- | --- | --- |
| savedMealLogs | date, mealId, entryCount, createdAt | Prevents repeated insertion of the same reusable-meal operation. |
| wearableWorkoutLinks | date, entryId, createdAt | Identifies a wearable session already confirmed into the activity log. |
| wearableWorkoutRewards | entryId, day, week, xpEarned, ruleVersion | Preserves the immutable result of an eligible wearable award. |
| wearableRewardDays | day, week, awardId | Limits wearable base XP and weekly credit to the first qualifying award on that date. |