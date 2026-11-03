# Appendix A: Database Data Dictionary

## A.1 users collection

The users collection stores account profile information, explicit nutrition and workout preferences, and the user's accumulated application progress. Its document identifier is the Firebase Authentication UID. Passwords are managed by Firebase Authentication and are not stored in the user document.

Table A.1: users collection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| uid (document ID) | string | false | Firebase Authentication UID used as the document path identifier; not a separate stored field. |
| fullName | string | false | Name supplied during registration and updated through the profile screen. |
| email | string | false | Registered email address copied into the profile; authentication identity remains managed by Firebase Authentication. |
| weight | number | false | Current profile body weight in kilograms. |
| height | number | true | Profile height in centimetres, supplied after registration. |
| fitnessLevel | string | false | Selected fitness level used by workout preferences. |
| createdAt | timestamp | false | Account profile creation time. |
| isVerified | boolean | false | Profile metadata initially set to false; Firebase Auth emailVerified is the authoritative verification state. |
| nutritionGoalSource | string | false | Indicates whether nutrition goals are not set or explicitly user-entered. |
| dailyCalorieGoal | number | true | Explicit daily energy target in kilocalories; absent until configured. |
| dailyProteinGoalG | number | true | Explicit daily protein target in grams. |
| dailyCarbohydrateGoalG | number | true | Explicit daily carbohydrate target in grams. |
| dailyFatGoalG | number | true | Explicit daily fat target in grams. |
| preferredExercises | array(string) | true | Activity preferences selected through the profile workflow. |
| preferredWorkoutMinutes | integer | true | Preferred workout duration in minutes. |
| preferredWorkoutDays | array(integer) | true | Chosen workout weekdays, encoded from 1 (Monday) to 7 (Sunday). |
| weeklyWorkoutTarget | integer | true | Weekly target bounded from one to seven workouts. |
| equipment | array(string) | true | Available equipment identifiers, or none. |
| dietaryPreferences | array(string) | true | Supported dietary preferences used to restrict eligible food suggestions. |
| preferredFoodIds | array(string) | true | Food identifiers marked as preferred for recommendation ranking. |
| dislikedFoodIds | array(string) | true | Food identifiers excluded by the user's recommendation preferences. |
| preferencesUpdatedAt | timestamp | true | Time of the latest preferences update. |
| totalXp | integer | true | Accumulated XP, created or updated by an eligible reward transaction. |
| level | integer | true | Application level derived from total XP. |
| lastRecordId | string | true | Most recent guided record or wearable award associated with an atomic progress update. |
| metGoalWeeks | array(string) | true | Distinct Monday-based week identifiers for which the weekly target bonus was earned. |
| achievementIds | array(string) | true | Identifiers of achievements recorded in the user's progress summary. |
| feedbackCount | integer | true | Count of saved guided-workout feedback submissions. |
| lastWorkoutAt | timestamp | true | Most recent qualifying workout time used by progress and inactivity logic. |
| lastDiscomfortAt | timestamp | true | Latest recorded discomfort feedback time. |
| lastDifficulty | string | true | Latest guided-workout difficulty feedback. |
| lastFeeling | string | true | Latest guided-workout feeling feedback. |
| timeZone | string | true | Time-zone identifier recorded by guided reward processing. |

Progress and preference fields may be absent on a newly registered account. Legacy weeklyCounts and weeklyCategories may still be read for compatibility; they are not the primary current week-document design. The recommendation engine can read fitnessGoal, but the current profile/preferences UI does not persist a named fitnessGoal selector, so it is not presented as a mandatory implemented profile field.

## A.2 foods collection

The foods collection contains the shared food catalogue imported from Malaysian food composition datasets. It separates source reference nutrition from the serving-adjusted snapshots saved in individual food logs. Additional reviewed metadata supports food recommendations without changing the original source nutrients.

Table A.2: foods collection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| foodKey (document ID) | string | false | Stable identifier of a shared catalogue document. |
| food_key | string | false | Imported stable food identifier corresponding to the document key. |
| food_id | string | true | Identifier assigned by the original source dataset. |
| food_name | string | false | Food name presented to the user. |
| food_name_lowercase | string | false | Normalised food name used by prefix search. |
| food_group | string | true | Source food-group code, where supplied. |
| source_database | string | false | Origin of the record, such as a MyFCD dataset. |
| nutrition_basis | string | false | Reference basis, such as per_100g, per_100ml or unknown. |
| portion_unit | string | true | Compatible portion measurement unit when established. |
| nutrition | map | false | Available reference nutrient values; individual nutrient keys can be absent. |
| additional_nutrients | map | true | Additional source nutrient measurements with their original names and units. |
| image_url | string | true | Image address when available; may be null. |
| source_url | string | true | Source-page URL, if retained in the imported record. |
| nutrition_verification | string | true | Provenance or verification description supplied by the data-processing workflow. |
| basis_confirmation_required | boolean | true | Indicates that a reference basis needs explicit confirmation before automatic scaling. |
| recommendation_ready | boolean | true | Explicit flag enabling consideration by the reviewed recommendation catalogue. |
| meal_types | array(string) | true | Reviewed meal categories for recommendation eligibility. |
| serving_options | array(map) | true | Reviewed portion options containing label, amount and unit. |
| dietary_tags | array(string) | true | Reviewed dietary classifications; unknown suitability is not inferred from the name. |
| dietary_tags_status | string | true | Review status associated with dietary classifications. |
| dietary_evidence | map | true | Evidence notes keyed by dietary classification. |
| recommendation_metadata_version | integer | true | Version of the reviewed recommendation metadata. |

Imported documents can contain further source-specific top-level nutrient or provenance fields. This table describes the core application contract rather than asserting that every live document has been inspected. Recommendation fields are conditional and are not automatically populated for all imported foods.

## A.3 nutrition map

The nutrition map stores the available reference values for the source food. The same canonical nutrient keys are used in portion-adjusted food-entry nutrients and aggregate nutrient maps, with different reference contexts.

Table A.3: nutrition map

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| energy_kcal | number | true | Energy in kilocalories for the stated reference basis. |
| protein_g | number | true | Protein in grams for the stated reference basis. |
| carbohydrate_g | number | true | Carbohydrate in grams for the stated reference basis. |
| fat_g | number | true | Fat in grams for the stated reference basis. |
| fiber_g | number | true | Dietary fibre in grams for the stated reference basis. |
| sodium_mg | number | true | Sodium in milligrams for the stated reference basis. |

This is an embedded map, not a collection. Missing values remain unavailable. A recorded numeric zero is different from a missing measurement. Additional source-specific nutrition keys may be retained. Energy estimation/provenance metadata is documented separately when present; this table does not certify nutrient accuracy.

## A.4 foodLogs subcollection

The foodLogs subcollection groups food-consumption snapshots by local date. Each document contains the food entries for that day and recalculated totals with completeness information.

Table A.4: foodLogs subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| date (document ID) | string | false | Date identifier in yyyy-MM-dd format. |
| date | string | false | Local log date stored in the document. |
| foodItems | array(map) | false | Food-entry snapshots logged for the selected date. |
| totalCalories | integer | false | Rounded sum of available energy values; not a complete intake estimate when entries lack calories. |
| nutrientTotals | map | false | Sums of available canonical nutrients from the day's entries. |
| nutrientCoverage | map | false | Number of entries contributing a known value to each nutrient total. |
| knownCalorieCount | integer | false | Number of entries with usable calorie information. |
| missingCalorieCount | integer | false | Number of entries without usable calorie information. |
| caloriesComplete | boolean | false | Indicates whether all logged entries have usable energy values. |
| timestamp | timestamp | false | Latest write time for the dated food log. |



## A.5 foodItems embedded records

A foodItems element preserves the food name, chosen serving and nutrition values as they were recorded. This snapshot design prevents later catalogue changes from silently altering historical consumption.

Table A.5: foodItems embedded records

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| entryId | string | false | Stable entry identifier used for editing, deletion and duplicate protection. |
| foodId | string | true | Source food identifier when one is available. |
| name | string | false | Food-name snapshot. |
| meal | string | false | Selected meal category: Breakfast, Lunch, Dinner or Snacks. |
| servingAmount | number | true | Amount in one serving, when specified quantitatively. |
| servings | number | true | Number of selected servings. |
| portionUnit | string | true | Portion unit such as g or ml. |
| totalAmount | number | true | Total quantity calculated from serving amount and count. |
| portionGrams | number | true | Total weight when the selected unit is grams. |
| portionMillilitres | number | true | Total volume when the selected unit is millilitres. |
| calories | number | true | Known/calculated calories for the consumed portion; absent or unavailable for unknown-energy entries. |
| nutrients | map | true | Available portion-adjusted nutrient values using canonical keys. |
| nutritionBasis | string | true | Reference basis used or retained when logging. |
| sourceDatabase | string | true | Source description copied into the historical entry. |
| barcode | string | true | Product barcode for barcode-based records. |
| sourceUrl | string | true | Source URL, where supplied. |
| sourceRetrievedAt | timestamp or string | true | Source retrieval metadata copied as supplied by the source record. |
| basisConfirmationSource | string | true | Describes source metadata, user-label entry or another supported reference confirmation. |
| calculationMode | string | true | Indicates the supported calculation path, such as database, manual or macro estimate. |
| energyMethod | string | true | Provenance of energy, such as source-reported, user-entered or Atwater estimate. |
| nutritionVerification | string | true | Describes whether values were user-entered, approximate or not independently verified. |
| manualCaloriesPerServing | number | true | Explicit calories entered for one serving in the manual workflow. |
| portionDescription | string | true | Descriptive portion text used by unknown-value logging. |
| nutritionStatus | string | true | Availability status for an incomplete entry. |
| savedMealId | string | true | Originating saved meal, where applicable. |
| savedMealName | string | true | Saved-meal name snapshot. |
| savedMealOperation | string | true | Operation identifier tying a multi-food insertion to its receipt. |
| recipeId | string | true | Originating recipe identifier. |
| recipeName | string | true | Recipe-name snapshot. |
| recipeRevision | integer | true | Recipe version used to generate the entry. |
| recipeYield | number | true | Yield of the recipe version used. |
| recipeServings | number | true | Number of recipe servings consumed. |
| personalNutritionKind | string | true | Kind of personal nutrition override applied. |
| personalNutritionRevision | integer | true | Version of the personal override used. |
| alternativeFoodId | string | true | Selected approximate alternative food, where applicable. |
| alternativeName | string | true | Alternative food-name snapshot. |
| loggedAt | timestamp | true | Timestamp added to reusable-meal/recipe insertions where recorded. |

These records are embedded in an array and are not separate top-level MealRecords documents. Optional provenance fields depend on the logging path; older entries can have fewer fields.

## A.6 dailyData subcollection

The dailyData subcollection provides dated dashboard summaries for food consumption and exercise. Food and exercise repositories update their respective fields using merges and transactions so that one module does not replace the other module’s totals.

Table A.6: dailyData subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| date (document ID) | string | false | Local date represented by the summary document. |
| caloriesConsumed | integer | true | Rounded known food-energy total for the date. |
| nutrientTotals | map | true | Available food nutrient totals. |
| nutrientCoverage | map | true | Known-value coverage for each food nutrient. |
| foodEntryCount | integer | true | Number of food entries included. |
| knownCalorieCount | integer | true | Food entries with usable calories. |
| missingCalorieCount | integer | true | Food entries with unavailable calories. |
| caloriesComplete | boolean | true | Whether food-energy coverage is complete. |
| manualCaloriesBurned | integer | true | Exercise-energy contribution from the legacy activity-log bucket, including confirmed wearable entries. |
| structuredCaloriesBurned | integer | true | Contribution from guided workout records. |
| caloriesBurned | integer | true | Combined known exercise calories after source-aware accounting. |
| exerciseActiveSeconds | integer | true | Combined counted exercise time; manual/wearable durations can represent elapsed time despite the historical field name. |
| exerciseEntryCount | integer | true | Number of counted exercise contributions. |
| exerciseTotalsVersion | integer | true | Version of the exercise aggregation representation. |
| exerciseCaloriesUnavailableCount | integer | true | Counted activity entries whose calories are unavailable. |
| structuredExerciseContributions | map | true | Guided contributions keyed by workout record ID, including any duplicate-suppression metadata. |
| lastGuidedCompletedAt | integer | true | Epoch milliseconds of the latest guided completion recorded by the transaction. |
| lastUpdated | timestamp | false | Latest summary update time. |

Food-only and exercise-only days can contain different subsets of these fields. The document ID supplies the date; current writers do not require a separate date field here. This summary is intentionally denormalised for read efficiency.

## A.7 exercises collection

The exercises collection stores reusable exercise definitions for guided workout lessons. It contains instructional content, targets and parameters used by the existing exercise calorie-estimation logic.

Table A.7: exercises collection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| exerciseId (document ID) | string | false | Identity of the shared exercise definition; model id is populated from the document ID. |
| name | string | false | Displayed exercise name. |
| category | string | false | Exercise category, such as cardio, strength or mobility. |
| difficulty | string | false | Difficulty classification. |
| description | string | false | Exercise description and relevant guidance. |
| instructions | array(string) | false | Ordered instructional steps. |
| targetType | string | false | Target mode, such as duration or repetitions. |
| defaultTarget | integer | false | Default target amount for the exercise. |
| targetUnit | string | false | Target measurement unit, such as seconds. |
| equipment | array(string) | false | Required equipment identifiers. |
| metValue | number | false | MET parameter used for estimated energy expenditure. |
| imageUrl | string | true | Optional illustration URL; current catalogue entries may use an empty string. |
| active | boolean | false | Whether the exercise is available as active catalogue content. |



## A.8 workoutPaths collection

The workoutPaths collection organises guided lessons into named paths. Lesson content is stored in nested lessons documents rather than embedded in the path document.

Table A.8: workoutPaths collection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| pathId (document ID) | string | false | Identity of the workout path. |
| name | string | false | Displayed path name. |
| description | string | false | Description of the path and its purpose. |
| active | boolean | false | Whether the path is active. |



## A.9 lessons subcollection

Each lessons document defines a guided workout lesson, its progression position and eligibility information. The lesson’s ordered exercise assignments are stored in a further subcollection.

Table A.9: lessons subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| lessonId (document ID) | string | false | Identity of the lesson within the path. |
| name | string | false | Displayed lesson name. |
| description | string | false | Lesson description. |
| stageName | string | false | Displayed stage label. |
| sequence | integer | false | Ordering value within the path. |
| difficulty | string | false | Lesson difficulty. |
| estimatedMinutes | integer | false | Estimated workout duration in minutes. |
| baseXp | integer | false | Base XP used by guided reward calculations. |
| prerequisiteLessonId | string | true | Preceding lesson identifier; can be absent, null or empty for an entry lesson. |
| active | boolean | false | Whether the lesson is active. |
| standalone | boolean | false | Whether the lesson is a standalone alternative, such as recovery. |
| category | string | false | Category used by recommendation and weekly variety logic. |
| equipment | array(string) | false | Required equipment for eligibility filtering. |



## A.10 workoutExercises subcollection

The workoutExercises subcollection associates reusable exercise definitions with an ordered lesson. Its item identifier denotes the assignment, while exerciseId identifies the shared exercise definition.

Table A.10: workoutExercises subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| itemId (document ID) | string | false | Identity of the exercise assignment within a lesson. |
| exerciseId | string | false | Identifier of the corresponding exercises document. |
| sequence | integer | false | Exercise order within the lesson. |
| targetType | string | false | Duration or repetition target mode. |
| targetValue | integer | false | Required target amount for this lesson item. |
| restAfterSeconds | integer | false | Rest interval following the item. |
| optional | boolean | false | Indicates whether the item is optional. |



## A.11 workoutSessions subcollection

The workoutSessions subcollection records the immutable start context of a guided workout. The same session identifier is subsequently used for the completed workout record, supporting repeat-safe completion.

Table A.11: workoutSessions subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| sessionId (document ID) | string | false | Stable identifier for one guided session. |
| pathId | string | false | Selected workout path. |
| lessonId | string | false | Selected lesson. |
| plannedExercises | integer | false | Planned number of exercise items. |
| startedAt | timestamp | false | Server timestamp establishing the session start. |



## A.12 workoutRecords subcollection

The workoutRecords subcollection stores immutable guided-workout outcomes, submitted feedback and the reward result committed with each completion. It does not contain fabricated guided results for wearable-only activities.

Table A.12: workoutRecords subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| sessionId (document ID) | string | false | Matches the original workoutSessions identifier. |
| pathId | string | false | Completed workout path. |
| lessonId | string | false | Completed lesson. |
| lessonName | string | false | Historical lesson-name snapshot. |
| startedAt | timestamp | false | Start timestamp copied from the session document. |
| completedAt | timestamp | false | Server timestamp of completion processing. |
| plannedExercises | integer | false | Planned exercise-item count. |
| completedExercises | integer | false | Number of completed exercise items. |
| skippedExercises | integer | false | Number of skipped exercise items. |
| completionPercentage | number | false | Completed-item percentage relative to planned items. |
| durationSeconds | integer | false | Recorded session duration. |
| activeDurationSeconds | integer | false | Recorded active exercise duration. |
| estimatedCaloriesBurned | number | false | Estimated guided-workout energy expenditure. |
| xpEarned | integer | false | XP awarded by this completion event. |
| starsEarned | integer | false | Guided-result star count. |
| difficultyFeedback | string | false | User-reported difficulty: too_easy, suitable or too_difficult. |
| feelingFeedback | string | false | User-reported condition after exercise. |
| discomfortReported | boolean | false | Whether feeling feedback indicates discomfort. |
| firstCompletion | boolean | false | Whether this is the first saved attempt for the lesson. |
| totalXpAfter | integer | false | Account XP after the transaction. |
| weeklyCompleted | integer | false | Shared weekly count after processing. |
| weeklyTarget | integer | false | Weekly target used by the completion. |
| unlocked | array(string) | false | New achievement or lesson identifiers reported by the result. |
| localDay | string | false | Local date assigned to the completion. |
| localWeek | string | false | Monday-based local week identifier. |
| timeZone | string | false | Time-zone identifier used for date assignment. |
| category | string | false | Lesson category snapshot. |



## A.13 exerciseLogs subcollection

The exerciseLogs subcollection stores dated manual activity entries and explicitly confirmed wearable sessions. Guided results remain in workoutRecords and are combined through dailyData accounting.

Table A.13: exerciseLogs subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| date (document ID) | string | false | Local date of the activity log. |
| date | string | false | Date copied into the current log document. |
| exercises | array(map) | false | Manual or confirmed wearable activity entries. |
| totalCaloriesBurned | integer | false | Rounded known calories from this activity-log bucket only. |
| timestamp | timestamp | false | Latest activity-log update time. |

Older aggregate-only documents may not contain an exercises array. The application preserves these through a legacy summary mapping rather than inventing exercise duration.

## A.14 exercises embedded activity records

An activity entry stores the amount and completion state of a manual activity or the provenance of a confirmed wearable session. Optional fields distinguish these two sources.

Table A.14: exercises embedded activity records

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| entryId | string | false | Stable activity-entry identifier. |
| name | string | false | Activity name. |
| duration | number | false | Duration in minutes; wearable entries explicitly use elapsed duration. |
| calories | number | true | Estimated/manual or available wearable active calories; unavailable wearable energy can be null. |
| completed | boolean | false | Whether the entry contributes to counted totals. |
| revision | integer | false | Entry revision assigned by current mutation logic. |
| lastMutationId | string | true | Identifier supporting retry-safe manual edits. |
| source | string | true | manual for current manual entries or health_connect for confirmed wearable entries; older entries may omit it. |
| activityType | string | true | Supported manual-activity code. |
| metValue | number | true | MET value used by the manual estimate. |
| weightKg | number | true | Profile-weight snapshot used by the manual estimate. |
| calorieMethod | string | true | Manual estimation method, currently met_estimate. |
| legacySummary | boolean | true | Marks an aggregate-only historical summary or false for current manual entries. |
| sourcePackage | string | true | Source application package for wearable data. |
| healthConnectId | string | true | Original Health Connect session identifier. |
| startedAtMillis | integer | true | Wearable session start in epoch milliseconds. |
| endedAtMillis | integer | true | Wearable session end in epoch milliseconds. |
| calorieSource | string | true | wearable_active or unavailable for the wearable workflow. |
| durationSource | string | true | wearable_elapsed when derived from the wearable session interval. |
| workoutStartedAtMillis | integer | true | Time at which the user prepared the matching workout in FitnessBalance. |
| localDay | string | true | Recorded local date for a wearable entry. |
| timeZone | string | true | Time zone used to assign the wearable entry date. |

Manual entries may include additional activity-specific estimation inputs. These embedded entries are not the same structure as the shared exercises catalogue.

## A.15 savedMeals subcollection

The savedMeals subcollection stores reusable groups of food-portion snapshots. Reuse creates new dated food-log entries while retaining the stored source and nutrition context.

Table A.15: savedMeals subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| mealId (document ID) | string | false | Identity of the reusable meal. |
| name | string | false | User-defined meal name. |
| items | array(map) | false | One to 25 food snapshots, using the supported portion/nutrient/provenance fields from food entries. |
| revision | integer | false | Revision used to detect conflicting updates. |
| createdAt | timestamp | false | Creation timestamp. |
| updatedAt | timestamp | false | Latest update timestamp. |



## A.16 recipes subcollection

The recipes subcollection stores ingredient snapshots and a recipe yield. Consumed recipe servings are calculated by scaling ingredient values relative to this yield.

Table A.16: recipes subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| recipeId (document ID) | string | false | Recipe identity. |
| name | string | false | User-defined recipe name. |
| items | array(map) | false | Ingredient portion and nutrition snapshots. |
| yieldServings | number | false | Number of servings produced by the recipe. |
| revision | integer | false | Recipe revision. |
| mutationId | string | false | Identifier of the recipe save operation. |
| createdAt | timestamp | false | Creation timestamp. |
| updatedAt | timestamp | false | Latest update timestamp. |



## A.17 personalServings subcollection

The personalServings subcollection stores user-defined portion labels and measurable amounts for catalogue foods or private barcode products. The foodKey field identifies the applicable food without requiring duplicate shared catalogue documents.

Table A.17: personalServings subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| servingId (document ID) | string | false | Personal-serving identity. |
| name | string | false | User-defined serving label. |
| foodKey | string | false | Namespaced identity, such as food:<foodId> or barcode:<barcode>. |
| amount | number | false | Amount represented by the serving. |
| unit | string | false | Supported measurement unit, g or ml. |
| revision | integer | false | Serving revision used by update checks. |
| createdAt | timestamp | false | Creation timestamp. |
| updatedAt | timestamp | false | Latest update timestamp. |



## A.18 barcodeFoods subcollection

The barcodeFoods subcollection stores private product definitions entered from package labels. Nutrients are normalised to a supported per-100-unit basis while retaining the original entered label information.

Table A.18: barcodeFoods subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| barcode (document ID) | string | false | Validated product barcode used as the document identifier. |
| barcode | string | false | Product barcode copied into the record. |
| food_key | string | false | Private product key, generated with a user_barcode_ prefix. |
| food_name | string | false | User-entered product name. |
| food_name_lowercase | string | false | Normalised product name. |
| source_database | string | false | Description identifying the user-entered private product source. |
| nutrition_basis | string | false | Normalised per_100g or per_100ml basis. |
| portion_unit | string | false | g or ml. |
| nutrition | map | false | Available normalised canonical nutrient values. |
| label_amount | number | false | Reference amount transcribed from the package. |
| label_unit | string | false | Unit of the package reference amount. |
| label_nutrition | map | false | Original entered nutrients for the package reference amount. |
| nutrition_verification | string | false | user_entered provenance label. |
| basis_confirmation_required | boolean | false | False for the explicitly entered private label definition. |

This documents the current UserBarcodeFood creation path. Shared external product lookup responses are not automatically equivalent to privately saved products.

## A.19 foodNutrition subcollection

The foodNutrition subcollection stores personal label-based nutrition or a user-confirmed approximate alternative for a shared food. These private overrides preserve the shared source record.

Table A.19: foodNutrition subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| foodId (document ID) | string | false | Identity of the original shared food to which the override applies. |
| originalFoodId | string | false | Original food identifier. |
| food_name | string | false | Original/displayed food name. |
| nutrition | map | false | Supported private reference nutrient values. |
| nutrition_basis | string | false | Supported per_100g or per_100ml reference basis. |
| source_kind | string | false | label or alternative. |
| source_database | string | false | Description of the private nutrition source. |
| source_note | string | false | User/source note describing the information or approximate substitution. |
| nutrition_verification | string | false | Provenance label, such as user_entered or approximate_substitution. |
| label_amount | number | true | Original label reference amount for label-based entries. |
| label_unit | string | true | Original label measurement unit. |
| label_nutrition | map | true | Original values transcribed from the label. |
| alternativeFoodId | string | true | Selected alternative food identifier. |
| alternativeName | string | true | Selected alternative food name. |
| revision | integer | false | Override version. |
| mutationId | string | false | Save-operation identifier. |
| updatedAt | timestamp | false | Latest update timestamp. |
| confirmedAt | timestamp | false | Timestamp of explicit confirmation. |



## A.20 weightLogs subcollection

The weightLogs subcollection stores one manual body-weight measurement per local date. Revision checks and an accompanying private index coordinate edits and removal.

Table A.20: weightLogs subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| date (document ID) | string | false | Measurement date in yyyy-MM-dd format. |
| day | string | false | Measurement date copied into the document. |
| weightKg | number | false | Recorded weight in kilograms. |
| revision | integer | false | Measurement version. |
| source | string | false | manual for this workflow. |
| mutationId | string | false | Identifier of the save operation. |
| updateProfile | boolean | false | Whether saving this measurement also updates current profile weight. |
| updatedAt | timestamp | false | Latest update timestamp. |



## A.21 weightHistory subcollection

The weightHistory subcollection maintains a private state index for weight-history operations. The entries map is updated atomically with the corresponding dated weight document.

Table A.21: weightHistory subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| state (document ID) | string | false | Fixed index-document identifier. |
| entries | map | false | Date-keyed index; each value contains weightKg and revision. |
| updatedAt | timestamp | false | Latest index update timestamp. |

This is a transaction-maintained index, not a second independent source of user-entered weight. The current implementation limits the index to 5,000 dates.

## A.22 pathProgress subcollection

The pathProgress subcollection maintains guided lesson progression for each user and path. It tracks attempts and the best recorded result per lesson, supporting unlocking without altering shared lesson definitions.

Table A.22: pathProgress subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| pathId (document ID) | string | false | Shared workout path to which the progress belongs. |
| completionCounts | map | false | Lesson-ID keys mapped to saved attempt counts. |
| bestPercent | map | false | Lesson-ID keys mapped to best completion percentages. |
| bestStars | map | false | Lesson-ID keys mapped to best star counts. |
| updatedAt | timestamp | false | Latest progress update timestamp. |



## A.23 exerciseWeeks subcollection

The exerciseWeeks subcollection stores shared weekly progress from eligible guided and confirmed wearable sessions. The document identifier is the Monday date of the corresponding local week.

Table A.23: exerciseWeeks subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| week (document ID) | string | false | Monday-based week identifier in yyyy-MM-dd format. |
| completed | integer | false | Shared qualifying workout count for the week. |
| categories | array(string) | false | Recorded qualifying workout categories used by variety achievements. |
| bonusAwarded | boolean | false | Whether the weekly target bonus has already been awarded. |
| target | integer | false | Target used by the latest update. |
| lastRecordId | string | false | Guided record or wearable award responsible for the latest atomic update. |
| updatedAt | timestamp | false | Latest weekly-progress update timestamp. |



## A.24 quests collection

The quests collection stores shared quest definitions. Completion evidence is stored separately in each user’s questProgress documents; editing a shared definition does not itself award progress.

Table A.24: quests collection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| questId (document ID) | string | false | Identity of the shared quest definition. |
| name | string | false | Displayed quest name. |
| target | integer | false | Target amount associated with the definition. |
| bonusXp | integer | false | Quest XP metadata. |
| active | boolean | false | Whether the definition is active in the seeded catalogue. |

Some current quest labels and eligibility rules are defined in application resources/services. The catalogue document is not the sole executable source of reward policy.

## A.25 questProgress subcollection

The questProgress subcollection records immutable daily quest completions. A date-plus-quest identifier allows guided and wearable reward paths to share the same daily completion receipt.

Table A.25: questProgress subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| date_questId (document ID) | string | false | Composite date and quest identifier. |
| day | string | false | Local date to which completion applies. |
| questId | string | false | Completed quest identifier. |
| completed | boolean | false | True for a committed completion. |
| xpEarned | integer | false | Quest-specific XP recorded for the completion. |
| recordId | string | false | Guided record or wearable award supporting the completion. |
| completedAt | timestamp | false | Server timestamp of progress creation. |



## A.26 achievements collection

The achievements collection stores shared achievement display metadata. Eligibility is evaluated by the application’s guided and wearable reward services and constrained by the relevant Firestore rules.

Table A.26: achievements collection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| achievementId (document ID) | string | false | Identity of the achievement definition. |
| name | string | false | Displayed achievement name. |
| description | string | false | Description of the achievement. |
| active | boolean | false | Whether the seeded definition is active. |



## A.27 unlockedAchievements subcollection

The unlockedAchievements subcollection stores the user’s immutable achievement-unlock evidence. Reusing the shared achievement identifier prevents the same achievement from being created repeatedly for that user.

Table A.27: unlockedAchievements subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| achievementId (document ID) | string | false | Identity of the shared achievement that was unlocked. |
| recordId | string | false | Guided record or wearable award that produced the unlock. |
| unlockedAt | timestamp | false | Server timestamp of the unlock. |



## A.28 savedMealLogs subcollection

The savedMealLogs subcollection stores operation receipts for inserting a saved meal or recipe into the food diary. A repeated operation identifier returns an existing result rather than appending its foods again.

Table A.28: savedMealLogs subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| operationId (document ID) | string | false | UUID identifying the reusable-food insertion operation. |
| date | string | false | Destination food-log date. |
| mealId | string | false | Source reusable meal identity; recipe reuse can use a recipe-prefixed identity. |
| entryCount | integer | false | Number of food entries inserted by the operation. |
| createdAt | timestamp | false | Server timestamp of the receipt. |



## A.29 wearableWorkoutLinks subcollection

The wearableWorkoutLinks subcollection stores immutable confirmation receipts for sessions explicitly saved from Health Connect. Its stable identifier is derived from the source package and original session ID.

Table A.29: wearableWorkoutLinks subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| entryId (document ID) | string | false | Stable wearable_ key followed by the SHA-256 digest of source/session identity. |
| date | string | false | Date of the corresponding confirmed exercise-log entry. |
| entryId | string | false | Stable activity identifier copied into the receipt. |
| createdAt | timestamp | false | Server timestamp of the confirmed insertion. |

Removing the activity entry does not remove this receipt. This prevents accidental re-import of the same session after deletion. Syncing a local wearable cache alone does not create this document.

## A.30 wearableWorkoutRewards subcollection

The wearableWorkoutRewards subcollection stores immutable, versioned reward outcomes for qualifying confirmed wearable sessions. It is separate from guided workoutRecords so that a wearable activity does not imply completion of a guided lesson.

Table A.30: wearableWorkoutRewards subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| entryId (document ID) | string | false | Stable identifier of the confirmed wearable activity. |
| entryId | string | false | Activity identifier copied into the award. |
| source | string | false | health_connect. |
| activity | string | false | Activity-name snapshot. |
| day | string | false | Workout date receiving credit. |
| week | string | false | Monday-based event week receiving credit. |
| category | string | false | Supported reward category assigned to the activity. |
| startedAtMillis | integer | false | Original session start in epoch milliseconds. |
| endedAtMillis | integer | false | Original session end in epoch milliseconds. |
| durationSeconds | integer | false | Elapsed duration used by reward eligibility. |
| baseXp | integer | false | Base wearable XP, capped by the date credit receipt. |
| questXp | integer | false | Previously unclaimed shared daily quest bonuses. |
| weeklyBonusXp | integer | false | Previously unclaimed shared weekly target bonus. |
| xpEarned | integer | false | Sum of the XP components for this event. |
| weeklyCompleted | integer | false | Shared week count after the award. |
| weeklyTarget | integer | false | Target used in the reward decision. |
| totalXpAfter | integer | false | Account XP after the award transaction. |
| newDayCredit | boolean | false | Whether this event received the date's wearable base-XP and weekly credit. |
| unlocked | array(string) | false | General achievement identifiers newly unlocked by this event. |
| ruleVersion | string | false | Version of the wearable reward policy. |
| createdAt | timestamp | false | Server timestamp of award creation. |

The current policy requires a recent confirmed session of at least five elapsed minutes. Calories do not determine XP. Display-only fields such as eligible or alreadyAwarded are transient results, not stored ledger fields.

## A.31 wearableRewardDays subcollection

The wearableRewardDays subcollection stores one immutable wearable credit receipt per workout date. It coordinates simultaneous devices and limits wearable base XP and weekly credit independently of how many sessions are imported.

Table A.31: wearableRewardDays subcollection

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| date (document ID) | string | false | Workout date receiving the wearable credit. |
| day | string | false | Date copied into the document. |
| week | string | false | Event week associated with that date. |
| awardId | string | false | Wearable award that first claimed the date credit. |
| createdAt | timestamp | false | Server timestamp of credit creation. |



## A.32 Local Device Storage

Table A.32: Local device storage structures

| Structure | Storage mechanism | Purpose |
| --- | --- | --- |
| Health summaries | HealthSummaryStore private no-backup JSON | Permission/freshness state and supported steps, sleep and heart-rate summaries. |
| Wearable sessions and pending preparation | WearableWorkoutStore private no-backup SQLite | Rolling imported-session cache, sync metadata and account-specific pending workout. |
| Guided workout draft | WorkoutDraftStore private SharedPreferences | Resume state before immutable guided completion is submitted. |
| Sleep recommendation context | HealthRecommendationStore local cache | Opt-in sleep comparison baseline and bounded explanation history. |
| Optional contextual-bandit state | BanditStore local state | Local decision context, model parameters and processed-outcome evidence. |
| Food recommendation explanation | Account-private on-device snapshot | Latest rule inputs and explanation context. |
| Celebration display receipts | RewardCelebrationStore private no-backup SQLite | Prevents repeated guided celebration display; does not authorise rewards. |

## A.33 Logical Collection Relationships

Table A.33: Logical collection relationships

| Relationship | Implementation | Cardinality / qualification |
| --- | --- | --- |
| Authentication identity → users | Firebase Authentication UID matches users document ID. | One identity can have one application profile. |
| users → private subcollections | Ownership is established by users/{uid}. | One user can own many dated or event records; some state documents are singleton. |
| workoutPaths → lessons | Parent/subcollection nesting. | One path contains multiple lessons. |
| lessons → workoutExercises | Parent/subcollection nesting. | One lesson contains multiple ordered assignments. |
| exercises → workoutExercises | exerciseId points to a reusable definition. | One exercise can be assigned to multiple lesson items. |
| workoutSessions → workoutRecords | The same session ID is reused for the result. | A started session has zero or one immutable completion. |
| Food definitions → foodLogs | foodItems stores source IDs and historical values. | Many-to-many at dated-document level through embedded snapshots. |
| savedMeals / recipes → foodLogs | New snapshots retain source and operation identifiers. | One reusable definition can supply multiple diary insertions. |
| Source logs → dailyData | Related source operations update the same user/date summary. | Derived summary dependency, not an enforced foreign key. |
| weightLogs → weightHistory | Dated record and index entry are updated together. | Multiple dates are represented in at most one private state index per user. |
| workoutPaths → pathProgress | Progress document ID identifies the shared path. | At most one progress document per user and path. |
| quests → questProgress | questId and date form the daily completion identity. | A user can have one completion per date and quest. |
| achievements → unlockedAchievements | The shared achievement ID is the private document ID. | At most one immutable unlock per user and achievement. |
| Confirmed wearable entry → wearableWorkoutLinks | Stable entry ID derives from source package and Health Connect ID. | One confirmation receipt per source session; it can outlive deletion of the log entry. |
| wearableWorkoutLinks → wearableWorkoutRewards | The stable confirmed entry ID identifies the award. | A qualifying confirmed session has at most one immutable award. |
| wearableWorkoutRewards → wearableRewardDays | awardId identifies the first qualifying wearable award for the date. | At most one date-credit receipt per user and workout date. |
| Guided / wearable award → shared progress | recordId can name either event source. | The sources share week, quest and profile progress without creating fictitious guided results. |