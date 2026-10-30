import importlib.util
from pathlib import Path
import tempfile
import csv
import unittest

spec = importlib.util.spec_from_file_location("catalogue", Path(__file__).parents[1] / "food_recommendation_catalogue.py")
catalogue = importlib.util.module_from_spec(spec)
spec.loader.exec_module(catalogue)

class CatalogueTests(unittest.TestCase):
    def setUp(self):
        self.food = dict(food_key="food_1", food_name="Example", source_database="MyFCD Industry",
                         nutrition_basis="per_100g", nutrition={"energy_kcal":200})
        self.foods = {"food_1":self.food}
        self.row = dict(include="yes", food_key="food_1", nutrition_basis="per_100g", meal_types="lunch|dinner",
                        serving_label="50 g", serving_amount="50", serving_unit="g", dietary_tags="",
                        dietary_evidence="{}", reviewer_id="review-1", review_notes="Portion reviewed against source")
    def patch(self):
        return catalogue.patches(self.foods,[self.row],"2026-10-30T00:00:00Z")["food_1"]
    def test_metadata_only(self):
        p=self.patch()
        self.assertTrue(p["recommendation_ready"])
        self.assertNotIn("nutrition",p)
        self.assertNotIn("nutrition_basis",p)
        self.assertEqual(50,p["serving_options"][0]["amount"])
    def test_unknown_basis_rejected(self):
        self.food["nutrition_basis"]="unknown"
        with self.assertRaises(ValueError): self.patch()
    def test_unit_conversion_not_guessed(self):
        self.row["serving_unit"]="ml"
        with self.assertRaises(ValueError): self.patch()
    def test_dietary_evidence_required(self):
        self.row["dietary_tags"]="halal"
        with self.assertRaises(ValueError): self.patch()
        self.row["dietary_evidence"]='{"halal":"reviewed certification source"}'
        self.assertEqual("reviewed",self.patch()["dietary_tags_status"])
    def test_template_defaults_to_no_without_guessing_portions_or_tags(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/"review.csv"
            self.assertEqual(1,catalogue.template(self.foods,path))
            with path.open(encoding="utf-8-sig") as stream: row=next(csv.DictReader(stream))
            self.assertEqual("no",row["include"])
            self.assertEqual("",row["serving_amount"])
            self.assertEqual("",row["dietary_tags"])
    def test_live_source_must_match_reviewed_export(self):
        live=dict(self.food);live["nutrition"]={"energy_kcal":250}
        with self.assertRaises(ValueError):catalogue.check_live(self.food,live)
    def test_remove_only_disables_recommendations(self):
        self.row["include"]="remove"
        self.assertEqual({"recommendation_ready":False},self.patch())
    def test_no_review_is_no_write(self):
        self.row["include"]="no"
        self.assertEqual({},catalogue.patches(self.foods,[self.row],"now"))
    def test_review_identity_and_notes_required(self):
        self.row["reviewer_id"]=""
        with self.assertRaises(ValueError):self.patch()

if __name__=="__main__":unittest.main()
