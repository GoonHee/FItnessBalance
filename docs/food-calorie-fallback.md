# Food calorie fallback

Source-reported nonnegative finite energy takes priority, including zero. If it is absent, a complete nonnegative finite protein/carbohydrate/fat tuple can supply approximate reference energy using 4P + 4C + 9F. Scaling still requires an explicit matching per-100 g/ml basis. Source reference values and catalogue documents are not overwritten. Fibre, alcohol and polyol contributions are not included in the approximation.

Calculated estimates are labelled in details, history and the diary editor. Log snapshots include calculationMode=macro_estimate and energyMethod=atwater_4_4_9. Manual and source entries use energyMethod=user_entered and source_reported respectively. Existing snapshots remain unchanged.

Incomplete nutrition or an unknown non-barcode reference basis opens the existing manual form. No calorie value is prefilled or inferred. Users must supply calories for ONE selected serving from a label or another reliable source. Servings multiply that value. Other nutrients remain unavailable in manual mode. OFF package-basis confirmation remains available for eligible barcode records.

## Dataset finding

The uploaded export has 1770 foods: Current 151 energy values/83 missing, 1997 814/219 missing, Industry 503/0 missing. None of the 302 missing-energy records has all three total macros in either its JSON or provided raw CSV. This fallback therefore does not recover those missing energies automatically. All 1267 Current/1997 reference bases remain unknown in that export. No uploaded dataset or live Firestore document was modified by this change.

## Device checks

- Open an incomplete MyFCD food: manual form is visible and asks for a value; Log remains disabled until valid input. 80 kcal and 2 servings logs 160 kcal and no invented macros.
- Open source energy with unknown basis: do not assume its energy is per serving; manual form requires independent serving information.
- Open a known-basis record with source calories: existing automatic calculation remains available.
- On a test record with known basis, no energy, P=10, C=20, F=5, check 165 kcal at 100 units and 99 kcal at 30 units x 2 servings. The estimate label must appear in preview, history and editor.
- Missing any macro must prevent estimation. Recorded zero energy must remain zero. No g/ml conversion is assumed.
- Confirm barcode basis confirmation, diary edit/remove and daily totals still work.

No migration or new Firestore rules are needed. UI and live Firebase acceptance require a device; local tests cover calculation policy.
