#!/usr/bin/env python3
"""Development content import. Requires pip install firebase-admin and external ADC.
Defaults to dry run. Never ship credentials in the repository or Android app.
"""
import argparse, json, os
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--project', required=True)
p.add_argument('--apply', action='store_true')
p.add_argument('--allow-remote-development-project', action='store_true')
a=p.parse_args()
docs=json.loads(Path(__file__).with_name('exercise-seed.json').read_text())
if not a.apply:
    print(f'Dry run: {len(docs)} deterministic documents for {a.project}. Existing documents will be preserved.')
    raise SystemExit()
if not os.getenv('FIRESTORE_EMULATOR_HOST') and not a.allow_remote_development_project:
    p.error('Use the emulator or explicitly select a remote DEVELOPMENT project.')
import firebase_admin
from firebase_admin import firestore
firebase_admin.initialize_app(options={'projectId':a.project})
db=firestore.client()
created=0
# Create-only operations make reruns safe and preserve any edited content.
from google.api_core.exceptions import AlreadyExists
for path,data in docs.items():
    try:
        db.document(path).create(data)
        created+=1
    except AlreadyExists:
        pass
print(f'Created {created} documents; preserved {len(docs)-created} existing documents.')
