# Simplified core ERD for the main report

[Editable drawing](FitnessBalance_Simplified_Core_ERD.drawio) · [PDF](FitnessBalance_Simplified_Core_ERD.pdf) · [PNG](FitnessBalance_Simplified_Core_ERD.png) · [SVG](FitnessBalance_Simplified_Core_ERD.svg)

This intentionally simplified logical ERD shows 11 core collection/subcollection entity types and nine principal relationships. It is not a replacement for the complete database dictionary or [complete collections ERD](FitnessBalance_Collections_ERD.md).

The shared catalogue row contains foods, workoutPaths, lessons, workoutExercises and exercises. The private region contains the users profile plus foodLogs, dailyData, exerciseLogs, workoutRecords and exerciseWeeks. All private subcollections belong to the parent users/{uid}; the group label expresses ownership without repeated long user-to-every-record lines. The users profile is shown as the parent identity, not as an unrelated private subcollection.

Document IDs are marked [ID]. Other shown fields are selected attributes. Map and array fields remain embedded. Source foods relate to dated foodLogs through foodItems[].foodId, giving an optional many-to-many document-level association. A path contains lessons; a lesson contains exercise assignments referencing a shared exercise. Guided records identify the lesson by both pathId and lessonId and their event week by localWeek. Gold dotted lines indicate dated summary dependencies, not foreign-key constraints.

The simplified view omits reusable meals/recipes, personal servings, private product definitions, weight history/indexes, session-start records, detailed path progress, quests, achievements and wearable confirmation/reward receipts. These omitted modules and their provenance/operation links remain documented in the detailed ERDs. Their omission improves readability; it does not imply that they do not exist in FitnessBalance.

Suggested report explanation:

“Figure 3.X presents a simplified logical ERD of FitnessBalance's core food, exercise and progress records. Shared catalogue collections are separated from the user document and its private subcollections. Document fields are attributes, and the illustrated associations use parent paths or stored identifiers. User ownership is represented through grouping to avoid repeated connectors. Embedded food and activity records remain array attributes, while dailyData provides derived summaries. Supporting collections are presented in the complete ERD and data dictionary.”

Download the .drawio file with GitHub's Download raw file button, then open it in diagrams.net using File → Open From → Device. Every entity and connector remains editable.
