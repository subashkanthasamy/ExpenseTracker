#!/usr/bin/env node
/**
 * Writes `scope: "shared"` onto expenses that predate expense visibility.
 *
 * Why this is not optional: the new read rule tests `resource.data.scope` **literally** —
 *
 *     allow read: if isManagerOf(...) || resource.data.scope == 'shared'
 *                                     || resource.data.addedBy == uid()
 *
 * A row with no `scope` therefore satisfies none of those branches for a member, so it becomes
 * unreadable to them. It does not error; it simply stops appearing. Deploying the rules before
 * running this hides existing expenses from every non-manager in the household.
 *
 * The rule is written literally rather than as `resource.data.get('scope', 'shared')` because
 * Firestore's query prover is conservative about defaults: with a `.get()` it may refuse to
 * accept `where scope == 'shared'` as provably safe, and an unproven query is rejected outright
 * — which breaks the whole list rather than one row.
 *
 * DRY RUN BY DEFAULT. Pass --apply to actually write.
 *
 *   node backfill-expense-scope.js                 # report only
 *   node backfill-expense-scope.js --apply         # write scope: "shared"
 *
 * Run this BEFORE publishing the rules, and AFTER shipping a client that writes `scope`.
 *
 * Auth (either one):
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccount.json
 *   or run somewhere with Application Default Credentials for the project.
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

const PROJECT_ID = process.env.FIRESTORE_PROJECT || 'expense-tracker-8005e';
const APPLY = process.argv.includes('--apply');
const SHARED = 'shared';

initializeApp({ projectId: PROJECT_ID });
const db = getFirestore();

(async () => {
  console.log(`project: ${PROJECT_ID}`);
  console.log(APPLY ? 'mode:    APPLY (writing)' : 'mode:    DRY RUN (no writes)');
  console.log('');

  const households = await db.collection('households').get();
  console.log(`households: ${households.size}`);

  let toWrite = [];
  let alreadyOk = 0;
  let personal = 0;

  for (const household of households.docs) {
    const expenses = await household.ref.collection('expenses').get();
    let missing = 0;

    for (const doc of expenses.docs) {
      const scope = doc.get('scope');
      if (scope === SHARED) {
        alreadyOk += 1;
      } else if (scope === 'personal') {
        // Already deliberately private. Never rewrite this to shared — that would expose it.
        personal += 1;
      } else {
        missing += 1;
        toWrite.push(doc.ref);
      }
    }

    console.log(
      `  ${household.id}  "${household.get('name') || '(unnamed)'}"  ` +
        `expenses=${expenses.size}  missing scope=${missing}`
    );
  }

  console.log('');
  console.log(`to write:        ${toWrite.length}`);
  console.log(`already shared:  ${alreadyOk}`);
  console.log(`already personal: ${personal}  (left untouched)`);

  if (!APPLY) {
    console.log('');
    console.log('Dry run. Re-run with --apply to write.');
    console.log('Publish the rules only AFTER this reports "to write: 0".');
    return;
  }

  // Batched: 500 is the Firestore limit per commit.
  let batch = db.batch();
  let pending = 0;
  let written = 0;
  for (const ref of toWrite) {
    batch.update(ref, { scope: SHARED });
    pending += 1;
    if (pending === 450) {
      await batch.commit();
      written += pending;
      batch = db.batch();
      pending = 0;
    }
  }
  if (pending > 0) {
    await batch.commit();
    written += pending;
  }

  console.log('');
  console.log(`written: ${written}`);
  console.log('Now publish the rules (Console -> Firestore -> Rules -> Publish).');
})().catch((err) => {
  const message = String(err && err.message);
  if (
    message.includes('Could not load the default credentials') ||
    message.includes('NO_ADC_FOUND')
  ) {
    console.error('');
    console.error('No credentials. This script talks to Firestore as an administrator, so it');
    console.error('needs a service account — being signed in to the app is not enough.');
    console.error('');
    console.error('  1. Firebase Console -> Project Settings -> Service accounts');
    console.error('  2. "Generate new private key", save the JSON OUTSIDE this repo');
    console.error('  3. Re-run:');
    console.error('');
    console.error('     GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json \\');
    console.error('       node tools/backfill-expense-scope.js');
    console.error('');
    console.error('That key is a full-access credential for the project. Do not commit it.');
    process.exit(1);
  }
  console.error(err);
  process.exit(1);
});
