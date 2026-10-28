#!/usr/bin/env python3
"""Create-only development import. Dry run by default; never embed credentials."""
import argparse,json,os
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--project',required=True)
p.add_argument('--apply',action='store_true')
p.add_argument('--allow-remote-development-project',action='store_true')
a=p.parse_args()
docs=json.loads(Path(__file__).with_name('exercise-seed.json').read_text())
if not a.apply:
    print(f'Dry run: {len(docs)} documents for {a.project}; existing records will be preserved.')
    raise SystemExit()
if not os.getenv('FIRESTORE_EMULATOR_HOST') and not a.allow_remote_development_project:
    p.error('Use a local emulator or explicitly allow a remote DEVELOPMENT project.')
import firebase_admin
from firebase_admin import firestore
from google.api_core.exceptions import AlreadyExists
firebase_admin.initialize_app(options={'projectId':a.project})
db=firestore.client()
created=0
for path,data in docs.items():
    try:
        db.document(path).create(data)
        created+=1
    except AlreadyExists:
        pass
print(f'Created {created} documents; preserved {len(docs)-created} existing documents.')
