# Collections-based logical ERD

This is the preferred ERD for the report convention in which each collection or subcollection is represented by an entity type and its document fields are represented by attributes.

[Editable drawing](FitnessBalance_Collections_ERD.drawio) · [PDF](FitnessBalance_Collections_ERD.pdf) · [PNG](FitnessBalance_Collections_ERD.png) · [SVG](FitnessBalance_Collections_ERD.svg)

The single-page diagram contains 28 collection/subcollection entity types and 50 logical ownership, shared-identifier and reference associations. The previous 31-structure figure additionally expanded three embedded structures; those are now correctly retained as fields: foods.nutrition, foodLogs.foodItems and exerciseLogs.exercises. They are not separate collection entities. Other maps and arrays, including recipe ingredients and reusable meal items, also remain attributes.

Document identifiers are labelled [ID]. No redundant stored UID field is invented in user subcollection documents. Solid pale lines show ownership/path nesting; dashed blue lines show logical identifier associations. A common date is interpreted together with the same parent UID. An entry-level identifier can be shown using a dotted attribute path such as foodItems[].foodId. These identifiers refer to elements inside a stored array, not an additional entity.

At dated-document level, a food record can appear in multiple logs and one log can contain multiple foods. Catalogue, barcode, saved-meal and recipe associations to foodLogs are consequently shown as optional many-to-many logical relationships through their embedded entry identifiers. This is distinct from treating each food entry as an independent collection entity. A wearable confirmation receipt can identify an entry in a dated exercise log; multiple receipts can relate to one dated log, and the receipt may survive deletion of an entry.

Quest and achievement recordId fields can identify either a guided workout record or a wearable reward. Those are alternative event sources, not two compulsory parents. Firestore does not enforce SQL foreign keys or cascading deletion. Optional historical references can outlive their source documents. Supporting local health caches and model state are outside this cloud ERD. The figure shows selected key fields; the database dictionary provides the detailed field list.

The figure is based on the repository-defined application schema at the f21247e feature baseline, not a live Firestore export. Later inspected changes were UI/documentation changes. Open the drawing in diagrams.net to edit every entity and connector.

Suggested report wording:

“The logical ERD represents Firestore collections and subcollections as entities and document fields as attributes. Associations are established through parent–subcollection paths, shared document identifiers and reference IDs stored in documents or embedded records. Embedded maps and arrays remain attributes of their containing entities. The relationships describe the application's logical data model rather than relational foreign-key constraints enforced by Firestore.”
