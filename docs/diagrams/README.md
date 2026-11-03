# FitnessBalance database diagrams

## Complete ERD — redrawn on one page

[Editable complete diagram](FitnessBalance_Complete_ERD_Redrawn.drawio) · [Vector PDF](FitnessBalance_Complete_ERD_Redrawn.pdf) · [PNG preview](FitnessBalance_Complete_ERD_Redrawn.png) · [SVG](FitnessBalance_Complete_ERD_Redrawn.svg)

This redraw keeps all 31 structures and 53 ownership, embedding, reference and summary/index relationships together on one page. Related entities occupy aligned food, exercise and progress/reward regions. Connectors use separate attachment points and right-angle routes that avoid entity boxes. Pale UID ownership links stay in the background; reference and derived links use distinct styles. The editable drawing retains native entity shapes and connectors. Selected key fields are shown; the complete field dictionary remains separate. Use a large landscape page or zoomable PDF for this complete model.

[Editable draw.io file](FitnessBalance_Tidy_ERD.drawio) · [PDF preview](FitnessBalance_Tidy_ERD.pdf)

To download, open the file on GitHub and use **Download raw file** (the down-arrow icon at the top right of the file panel). For the editable diagram, you can also download through the project's Git checkout.

After `git pull origin master`, the file is at `docs/diagrams/FitnessBalance_Tidy_ERD.drawio`. In https://app.diagrams.net choose **File → Open From → Device** and select it.

The editable file has six pages: database structure overview, food, exercise, gamification, wearable confirmation/rewards, and weight history. All boxes and connectors are editable. The overview includes all 31 depicted document/embedded structures; focused pages show key relationships and selected fields, not every provenance link. The full data dictionary provides the detailed fields.

This is a logical Firestore model derived from repository write paths at the f21247e feature baseline, with the b73a265 adapter-only delta inspected. It is not an export of live database records. Private collections inherit ownership through users/{uid}; embedded maps and arrays are not extra collections. Relationships describe logical references rather than SQL foreign-key enforcement. Health Connect caches remain on-device and are not represented as independent cloud health collections.
