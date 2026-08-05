#!/usr/bin/env node
/**
 * Creates the missing `inviteCodes/{code}` lookup documents for households that predate
 * the invite-code change.
 *
 * Why: joining used to query `households` for a matching inviteCode, which required every
 * household to be readable by any signed-in user. The security rules now close that, and
 * clients resolve `inviteCodes/{code}` instead — but only households created after the
 * change publish that document, so existing ones cannot be joined until backfilled.
 *
 * The apps also self-heal (a member opening the household publishes its missing lookup
 * document), so this script is for doing it immediately and in bulk rather than waiting
 * for each household to be opened.
 *
 * DRY RUN BY DEFAULT. Pass --apply to actually write.
 *
 *   node backfill-invite-codes.js                 # report only
 *   node backfill-invite-codes.js --apply         # create the missing documents
 *
 * Auth (either one):
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccount.json
 *   or run somewhere with Application Default Credentials for the project.
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

const PROJECT_ID = process.env.FIRESTORE_PROJECT || 'expense-tracker-8005e';
const APPLY = process.argv.includes('--apply');

initializeApp({ projectId: PROJECT_ID });
const db = getFirestore();

(async () => {
  console.log(`project: ${PROJECT_ID}`);
  console.log(APPLY ? 'mode:    APPLY (writing)' : 'mode:    DRY RUN (no writes)');
  console.log('');

  const households = await db.collection('households').get();
  console.log(`households: ${households.size}`);

  const toCreate = [];
  const conflicts = [];
  const missingCode = [];
  const alreadyOk = [];
  const nameDrift = [];

  // Detect two households sharing a code before touching anything — the lookup is keyed
  // by code, so one would silently shadow the other.
  const byCode = new Map();
  for (const doc of households.docs) {
    const code = doc.get('inviteCode');
    if (!code) {
      missingCode.push(doc.id);
      continue;
    }
    if (!byCode.has(code)) byCode.set(code, []);
    byCode.get(code).push(doc);
  }

  for (const [code, docs] of byCode) {
    if (docs.length > 1) {
      conflicts.push({ code, householdIds: docs.map((d) => d.id) });
      continue;
    }
    const household = docs[0];
    const lookupRef = db.collection('inviteCodes').doc(code);
    const existing = await lookupRef.get();

    if (!existing.exists) {
      toCreate.push({ code, householdId: household.id, householdName: household.get('name') || '' });
    } else if (existing.get('householdId') !== household.id) {
      // Never repoint an existing lookup — that would hand one household's code to another.
      conflicts.push({
        code,
        householdIds: [household.id],
        note: `lookup already points at ${existing.get('householdId')}`,
      });
    } else if (existing.get('householdName') !== (household.get('name') || '')) {
      nameDrift.push({ code, householdId: household.id, householdName: household.get('name') || '' });
    } else {
      alreadyOk.push(code);
    }
  }

  console.log(`already correct:        ${alreadyOk.length}`);
  console.log(`to create:              ${toCreate.length}`);
  console.log(`name needs refreshing:  ${nameDrift.length}`);
  console.log(`no inviteCode field:    ${missingCode.length}`);
  console.log(`conflicts (skipped):    ${conflicts.length}`);

  if (toCreate.length) {
    console.log('\nwould create:');
    for (const item of toCreate) {
      console.log(`   ${item.code} -> ${item.householdId} (${item.householdName})`);
    }
  }
  if (nameDrift.length) {
    console.log('\nwould refresh householdName:');
    for (const item of nameDrift) console.log(`   ${item.code} -> ${item.householdName}`);
  }
  if (missingCode.length) {
    console.log('\nhouseholds with no inviteCode (cannot be joined by code at all):');
    for (const id of missingCode) console.log(`   ${id}`);
  }
  if (conflicts.length) {
    console.log('\nCONFLICTS — resolve by hand, nothing written for these:');
    for (const c of conflicts) {
      console.log(`   code ${c.code}: ${c.householdIds.join(', ')}${c.note ? ` — ${c.note}` : ''}`);
    }
  }

  if (!APPLY) {
    if (toCreate.length || nameDrift.length) {
      console.log('\nRe-run with --apply to write these.');
    }
    process.exit(0);
  }

  let written = 0;
  let batch = db.batch();
  let inBatch = 0;
  for (const item of [...toCreate, ...nameDrift]) {
    batch.set(
      db.collection('inviteCodes').doc(item.code),
      { householdId: item.householdId, householdName: item.householdName },
      { merge: true }
    );
    inBatch++;
    if (inBatch === 400) {
      await batch.commit();
      written += inBatch;
      batch = db.batch();
      inBatch = 0;
    }
  }
  if (inBatch > 0) {
    await batch.commit();
    written += inBatch;
  }

  console.log(`\ndocuments written:      ${written}`);
  process.exit(0);
})().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
