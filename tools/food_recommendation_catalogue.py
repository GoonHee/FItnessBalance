"""Prepare reviewed recommendation metadata. No source nutrition is invented or overwritten.

Template: python tools/food_recommendation_catalogue.py template --foods foods.json --out review.csv
Preview:  python tools/food_recommendation_catalogue.py upload --foods foods.json --review review.csv --out patches.json
Apply:    same upload command plus --apply --project PROJECT_ID --credentials LOCAL_KEY_PATH
"""
import argparse
import csv
import json
import math
from datetime import datetime, timezone
from pathlib import Path

MEALS = {"breakfast", "lunch", "dinner", "snacks"}
DIETS = {"halal", "vegetarian", "vegan", "avoid_pork", "avoid_alcohol"}
UNITS = {"per_100g": "g", "per_100ml": "ml"}
COLUMNS = ["include", "food_key", "food_name", "source_database", "nutrition_basis",
           "reference_energy_kcal", "meal_types", "serving_label", "serving_amount", "serving_unit",
           "dietary_tags", "dietary_evidence", "reviewer_id", "review_notes", "source_url"]

def number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and value >= 0

def energy(food):
    nutrition = food.get("nutrition") or {}
    value = nutrition.get("energy_kcal")
    if number(value):
        return value
    values = [nutrition.get(key) for key in ("protein_g", "carbohydrate_g", "fat_g")]
    if all(number(value) for value in values):
        estimate = 4 * values[0] + 4 * values[1] + 9 * values[2]
        return estimate if math.isfinite(estimate) else None
    return None

def load_foods(path):
    foods = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    if not isinstance(foods, list):
        raise ValueError("Expected a food array")
    result = {}
    for food in foods:
        key = food["food_key"]
        if not isinstance(key, str) or not key or "/" in key or key in result:
            raise ValueError("Invalid or duplicate food key")
        result[key] = food
    return result

def template(foods, path):
    rows = []
    for key, food in foods.items():
        unit = UNITS.get(food.get("nutrition_basis"))
        kcal = energy(food)
        if not unit or kcal is None or kcal <= 0:
            continue
        rows.append(dict(include="no", food_key=key, food_name=food.get("food_name", ""),
                         source_database=food.get("source_database", ""), nutrition_basis=food["nutrition_basis"],
                         reference_energy_kcal=kcal, meal_types="", serving_label="", serving_amount="",
                         serving_unit=unit, dietary_tags="", dietary_evidence="{}", reviewer_id="",
                         review_notes="", source_url=food.get("source_url", "")))
    with Path(path).open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)

def patches(foods, rows, timestamp):
    result = {}
    seen = set()
    for row in rows:
        key = row.get("food_key", "").strip()
        if key not in foods or key in seen:
            raise ValueError(f"Unknown/duplicate food key: {key}")
        seen.add(key)
        action = row.get("include", "no").strip().lower()
        if action in ("", "no", "false"):
            continue
        if action == "remove":
            result[key] = {"recommendation_ready": False}
            continue
        if action not in ("yes", "true"):
            raise ValueError(f"Unknown include action: {key}")
        food = foods[key]
        unit = UNITS.get(food.get("nutrition_basis"))
        kcal = energy(food)
        if not unit or kcal is None or kcal <= 0 or food.get("basis_confirmation_required"):
            raise ValueError(f"Known reference basis and positive calories required: {key}")
        if row.get("nutrition_basis") != food["nutrition_basis"] or row.get("serving_unit") != unit:
            raise ValueError(f"Do not change the source basis or convert g/ml here: {key}")
        meals = list(dict.fromkeys(part.strip().lower() for part in row.get("meal_types", "").split("|") if part.strip()))
        tags = list(dict.fromkeys(part.strip().lower() for part in row.get("dietary_tags", "").split("|") if part.strip()))
        amount = float(row.get("serving_amount", ""))
        label = row.get("serving_label", "").strip()
        reviewer = row.get("reviewer_id", "").strip()
        notes = row.get("review_notes", "").strip()
        if not meals or not set(meals) <= MEALS or not set(tags) <= DIETS:
            raise ValueError(f"Invalid meal types or dietary tags: {key}")
        if not math.isfinite(amount) or not 0 < amount <= 10000 or not label or len(label)>120 or not reviewer or len(reviewer)>100 or not notes or len(notes)>2000:
            raise ValueError(f"Explicit portion, reviewer ID and review notes required: {key}")
        evidence = json.loads(row.get("dietary_evidence", "{}") or "{}")
        if not isinstance(evidence, dict) or any(not isinstance(evidence.get(tag), str) or not evidence[tag].strip() or len(evidence[tag])>2048 for tag in tags):
            raise ValueError(f"Evidence is required for every dietary tag; never infer from food names: {key}")
        result[key] = dict(recommendation_ready=True, meal_types=meals,
                           serving_options=[dict(label=label, amount=amount, unit=unit)],
                           dietary_tags=tags, dietary_tags_status="reviewed" if tags else "unknown",
                           dietary_evidence={tag: evidence[tag] for tag in tags},
                           recommendation_reviewer_id=reviewer, recommendation_review_notes=notes,
                           recommendation_reviewed_at=timestamp, recommendation_metadata_version=1)
    if len(result) > 200 or sum(p.get("recommendation_ready") is True for p in result.values()) > 100:
        raise ValueError("Keep the reviewed active pool at most 100 foods; at most 200 updates per import")
    return result

def check_live(food, live):
    for field in ("food_key", "source_database", "nutrition_basis", "nutrition"):
        if live.get(field) != food.get(field):
            raise ValueError(f"Live data differs from reviewed export ({field}): {food['food_key']}")

def upload(args, foods):
    with Path(args.review).open(encoding="utf-8-sig", newline="") as stream:
        changes = patches(foods, list(csv.DictReader(stream)), datetime.now(timezone.utc).isoformat())
    Path(args.out).write_text(json.dumps(changes, ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8")
    print(f"Validated metadata patches: {len(changes)}. Preview: {args.out}")
    if not args.apply:
        print("Preview only. Firestore was not accessed or changed.")
        return
    if not args.project or not args.credentials:
        raise ValueError("--project and a LOCAL --credentials path are required to apply")
    import firebase_admin
    from firebase_admin import credentials, firestore
    app = firebase_admin.initialize_app(credentials.Certificate(args.credentials), name="food-recommendation-review")
    if app.project_id != args.project:
        raise ValueError("Firebase project does not match --project")
    db = firestore.client(app=app)
    current = {doc.id for doc in db.collection("foods").where("recommendation_ready", "==", True).stream()}
    enabled = {key for key, patch in changes.items() if patch["recommendation_ready"]}
    removed = set(changes) - enabled
    if len((current - removed) | enabled) > 100:
        raise ValueError("The resulting active recommendation catalogue would exceed 100 foods")
    snapshots = []
    for key in changes:
        document = db.collection("foods").document(key).get()
        if not document.exists:
            raise ValueError(f"Missing live document: {key}")
        if changes[key]["recommendation_ready"]:
            check_live(foods[key], document.to_dict())
        snapshots.append(document)
    backup = []
    for document in snapshots:
        data = document.to_dict()
        backup.append(dict(food_key=document.id, fields={field: dict(present=field in data, value=data.get(field)) for field in changes[document.id]}))
    backup_path = Path(f"food_recommendation_backup_{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')}.json")
    def encode(value):
        if isinstance(value, datetime):
            return {"__type": "timestamp", "value": value.isoformat()}
        raise TypeError("Unsupported backup type")
    with backup_path.open("x", encoding="utf-8") as stream:
        json.dump(dict(project_id=args.project, documents=backup), stream, ensure_ascii=False, indent=2, default=encode, allow_nan=False)
    if not snapshots:
        print("No updates selected.")
        return
    batch = db.batch()
    for document in snapshots:
        batch.update(document.reference, changes[document.id], option=db.write_option(last_update_time=document.update_time))
    batch.commit()
    for key, patch in changes.items():
        actual = db.collection("foods").document(key).get().to_dict()
        if any(actual.get(field) != value for field, value in patch.items()):
            raise RuntimeError(f"Post-update verification failed: {key}")
    print(f"Updated and checked {len(changes)} documents. Backup: {backup_path}")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("template", "upload"))
    parser.add_argument("--foods", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--review")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--project")
    parser.add_argument("--credentials")
    args = parser.parse_args()
    if Path(args.out).resolve() == Path(args.foods).resolve() or (args.review and Path(args.out).resolve() == Path(args.review).resolve()):
        parser.error("Output must not overwrite your food export or review CSV")
    foods = load_foods(args.foods)
    if args.command == "template":
        print(f"Template rows: {template(foods, args.out)}. All default to include=no; review before enabling.")
    else:
        if not args.review:
            parser.error("--review is required for upload")
        upload(args, foods)

if __name__ == "__main__":
    main()
