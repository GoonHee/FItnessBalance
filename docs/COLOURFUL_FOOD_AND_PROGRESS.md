# FitnessBalance food journal and Progress studio

## Update and scope

Pull master, sync Gradle and run. This is a presentation update; there are no new Firestore collections, rules, migrations, dependencies or nutrition calculations. Existing production rules still need to support the previously implemented features, but this update adds no rule requirements.

## Food journal

- Food Log now uses a Food journal heading, a teal daily-nutrition panel and clear purple/secondary action buttons.
- Search, barcode lookup, meal/date selection, recommendations and Meals and recipes keep their existing destinations and behavior.
- Diary entries are grouped visually under Breakfast, Lunch, Dinner, Snacks and Unassigned, with coloured portion cards and group counts. A sorted display copy is used; Firestore array order, entry IDs, snapshot values, edits and removal transactions are unchanged.
- Food Details uses matching panels and action styling. Unknown-calorie, manual-entry, private-source and approximate-substitution notices remain visible.
- The saved-meal/recipe library has an introductory banner, selected-section styling and themed cards. Its controls scroll with the library on small screens. Recipe editing, personal nutrition, barcode label entry and suggested-food buttons use the same visual language.
- Colours adapt for light and dark themes. Labels and entry counts identify groups without relying on colour alone. Disabled primary actions have distinct visual states.

## A distinct Progress studio

The previous Overview/Calories/Nutrients/Macros tab strip has been replaced by a two-row section selector below a FitnessBalance banner. Week controls and export are part of the scrollable page rather than a fixed tabbed layout.

1. **Week journal:** a week snapshot, calorie-availability panel, seven dated journal entries and a compact weight-history shortcut. This replaces the old stack of overview calorie/weight/macro mini-charts. Recorded days are not described as completed diet days, and older aggregate-only coverage remains unknown.
2. **Food energy:** horizontal daily energy rails, meal-colour keys, optional food-minus-logged-exercise mode, exact daily values and recorded-food contribution totals. Negative net values are plotted left of zero; unknown days have a dash, and real zero is a point on the zero axis. Current goal reference and incomplete-coverage gating are retained.
3. **Nutrient ledger:** individual nutrient panels with available totals, available-day averages, recorded-entry coverage and current confirmed goal references. Historical unknown coverage is identified explicitly.
4. **Macro balance:** horizontal daily gram charts and a labelled horizontal macro-energy contribution strip. The previous donut is removed. Percentages retain the existing 4/9/4 calculation and remain withheld for incomplete macro coverage.

The palette is purple, teal, coral, rose and gold rather than a series of blue stacked columns. Chart axes and gridlines use theme-aware colours. Exact values, coverage warnings and CSV exports remain available. The layout follows FitnessBalance's journal/workspace structure rather than the MyFitnessPal screenshot layout.

Weight history is titled **Weight journal**, with the matching banner, cards and purple measurement trace. Existing range filtering, kg entry/edit/delete, optional profile-weight updates and exports are unchanged. Its banner scrolls with content to avoid obscuring the chart/list on short screens.

## Number display

Progress display formatting now preserves finite signed values, such as a -200 kcal recorded net or -1.5 kg change. Previously the food-only nonnegative formatter could display these as unavailable. This changes formatting only; weights, exercise, calorie totals and CSV arithmetic are untouched. Missing/non-finite values still display as unavailable and real zero remains zero. Locale-specific decimal formatting is preserved.

## Validation and device acceptance

Debug assembly and lint validate resource/integration changes. The existing domain tests are run, with additional focused tests for signed progress formatting, real zero versus missing, non-finite input and locale formatting. No Android device or emulator UI session was attached in the cloud, so visual acceptance remains necessary.

On a phone check light/dark theme, large font, landscape, small screens, date navigation and all four studio sections. Verify a known-zero food, an unknown-calorie food, a partial week, a negative recorded net and a weight decrease. Confirm matching CSV values, retained source warnings, recipe/saved-meal logging, and the existing exercise celebrations. Food bars/strips describe recorded data, not an inferred complete intake or full physical energy expenditure.
