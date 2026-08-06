#!/usr/bin/env node
/**
 * Normalises Firestore time fields from Firestore Timestamp -> epoch millis (Number).
 *
 * Why: the Android app and the KMP shared domain model treat date/createdAt/
 * updatedAt/targetDate as epoch millis. Older iOS builds wrote them as Firestore
 * Timestamps, which crashed Android ("Field 'date' is not a java.lang.Number")
 * and made Firestore's order(by:'date') split results into separate type groups.
 * Both apps now READ either form, so this migration is about normalising stored
 * data — it is safe to run after the app fix is deployed.
 *
 * DRY RUN BY DEFAULT. Pass --apply to actually write.
 *
 *   node migrate-timestamps.js                 # report only, writes nothing
 *   node migrate-timestamps.js --apply         # perform the migration
 *
 * Auth (either one):
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccount.json
 *   or run somewhere with Application Default Credentials for the project.
 */

// Modular API — firebase-admin v13 no longer exposes admin.firestore().
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

const PROJECT_ID = process.env.FIRESTORE_PROJECT || 'expense-tracker-8005e';
const APPLY = process.argv.includes('--apply');

// Fields that hold a point in time. Anything else (e.g. category `color`) is left alone.
const TIME_FIELDS = ['date', 'createdAt', 'updatedAt', 'targetDate'];

// Subcollections under households/{id}
const SUBCOLLECTIONS = ['expenses', 'categories', 'assets', 'liabilities', 'budgets', 'savingsGoals'];

initializeApp({ projectId: PROJECT_ID });
const db = getFirestore();

let scanned = 0;
let needing = 0;
let written = 0;
const perCollection = {};
const samples = [];

function conversions(data) {
  // Returns a patch of {field: millis} for any TIME_FIELDS stored as a Timestamp.
  const patch = {};
  for (const f of TIME_FIELDS) {
    const v = data[f];
    if (v && typeof v.toMillis === 'function') {
      patch[f] = v.toMillis();
    }
  }
  return patch;
}

async function migrateCollection(ref, label) {
  const snap = await ref.get();
  if (snap.empty) return;

  let batch = db.batch();
  let inBatch = 0;

  for (const doc of snap.docs) {
    scanned++;
    const patch = conversions(doc.data());
    const fields = Object.keys(patch);
    if (fields.length === 0) continue;

    needing++;
    perCollection[label] = (perCollection[label] || 0) + 1;
    if (samples.length < 8) {
      samples.push(`${label}/${doc.id}  ${fields.map((f) => `${f}=${patch[f]}`).join(' ')}`);
    }

    if (APPLY) {
      batch.update(doc.ref, patch);
      inBatch++;
      if (inBatch === 400) {
        await batch.commit();
        written += inBatch;
        batch = db.batch();
        inBatch = 0;
      }
    }
  }

  if (APPLY && inBatch > 0) {
    await batch.commit();
    written += inBatch;
  }
}

(async () => {
  console.log(`project: ${PROJECT_ID}`);
  console.log(APPLY ? 'mode:    APPLY (writing)' : 'mode:    DRY RUN (no writes)');
  console.log('');

  await migrateCollection(db.collection('users'), 'users');

  const households = await db.collection('households').get();
  console.log(`households: ${households.size}`);
  await migrateCollection(db.collection('households'), 'households');

  for (const h of households.docs) {
    for (const sub of SUBCOLLECTIONS) {
      await migrateCollection(db.collection('households').doc(h.id).collection(sub), `households/*/${sub}`);
    }
  }

  console.log('');
  console.log(`documents scanned:            ${scanned}`);
  console.log(`documents with Timestamps:    ${needing}`);
  for (const [k, v] of Object.entries(perCollection)) console.log(`   ${k}: ${v}`);
  if (samples.length) {
    console.log('');
    console.log('samples:');
    for (const s of samples) console.log(`   ${s}`);
  }
  if (APPLY) {
    console.log('');
    console.log(`documents updated:            ${written}`);
  } else if (needing > 0) {
    console.log('');
    console.log('Re-run with --apply to migrate these documents.');
  }
  process.exit(0);
})().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
