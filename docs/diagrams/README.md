# FitnessBalance database diagrams

[Editable draw.io file](FitnessBalance_Tidy_ERD.drawio) · [PDF preview](FitnessBalance_Tidy_ERD.pdf)

To download, open the file on GitHub and use **Download raw file** (the down-arrow icon at the top right of the file panel). For the editable diagram, you can also download through the project's Git checkout.

After `git pull origin master`, the file is at `docs/diagrams/FitnessBalance_Tidy_ERD.drawio`. In https://app.diagrams.net choose **File → Open From → Device** and select it.

The editable file has six pages: database structure overview, food, exercise, gamification, wearable confirmation/rewards, and weight history. All boxes and connectors are editable. The overview includes all 31 depicted document/embedded structures; focused pages show key relationships and selected fields, not every provenance link. The full data dictionary provides the detailed fields.

This is a logical Firestore model derived from repository write paths at the f21247e feature baseline, with the b73a265 adapter-only delta inspected. It is not an export of live database records. Private collections inherit ownership through users/{uid}; embedded maps and arrays are not extra collections. Relationships describe logical references rather than SQL foreign-key enforcement. Health Connect caches remain on-device and are not represented as independent cloud health collections.
