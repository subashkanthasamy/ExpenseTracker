#!/usr/bin/env node
/**
 * Writes `ownerUid` and `roles` onto households that predate the permission model.
 *
 * Why: the rules now decide "who may delete this household / manage its members / write its
 * shared config" from `ownerUid`, and "who may write expenses" from `roles`. Households
 * created before that have neither field.
 *
 * The rules and the clients both tolerate the gap — they fall back to `memberUids[0]` as the
 * owner and treat a member absent from `roles` as a plain member — so nobody is locked out
 * while this is pending. But the fallback is positional: if the creator later left the
 * household, index 0 is now somebody else, and that person silently inherits ownership. This
 * script pins the answer down before that can happen.
 *
 * Why memberUids[0] is the creator: the create rule admits exactly one initial member, and
 * every join appends with arrayUnion. So index 0 is whoever created it — unless they left,
 * which is exactly the case this script cannot resolve on its own and reports instead.
 *
 * DRY RUN BY DEFAULT. Pass --apply to actually write.
 *
 *   node backfill-household-roles.js                 # report only
 *   node backfill-household-roles.js --apply         # write ownerUid + roles
 *
 * Run this BEFORE `firebase deploy --only firestore:rules`.
 *
 * Auth (either one):
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccount.json
 *   or run somewhere with Application Default Credentials for the project.
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

const PROJECT_ID = process.env.FIRESTORE_PROJECT || 'expense-tracker-8005e';
const APPLY = process.argv.includes('--apply');
const ROLE_MEMBER = 'member';

initializeApp({ projectId: PROJECT_ID });
const db = getFirestore();

(async () => {
  console.log(`project: ${PROJECT_ID}`);
  console.log(APPLY ? 'mode:    APPLY (writing)' : 'mode:    DRY RUN (no writes)');
  console.log('');

  const households = await db.collection('households').get();
  console.log(`households: ${households.size}`);

  const toWrite = [];
  const alreadyOk = [];
  const empty = [];
  const partial = [];

  for (const doc of households.docs) {
    const memberUids = doc.get('memberUids') || [];
    const ownerUid = doc.get('ownerUid');
    const roles = doc.get('roles');

    if (memberUids.length === 0) {
      // Cannot infer an owner. Left alone deliberately: writing a wrong owner is worse than
      // leaving a household that no one can administer, which is recoverable by hand.
      empty.push(doc.id);
      continue;
    }

    if (ownerUid && roles) {
      alreadyOk.push(doc.id);
      continue;
    }

    // An owner already recorded wins over the positional guess.
    const owner = ownerUid || memberUids[0];
    // Everyone who is not the owner becomes a plain member — this preserves exactly what
    // they could do before, since the old rules gave every member the same powers.
    const nextRoles = {};
    for (const uid of memberUids) {
      if (uid !== owner) nextRoles[uid] = ROLE_MEMBER;
    }

    if (ownerUid && !roles) partial.push(doc.id);

    toWrite.push({
      id: doc.id,
      name: doc.get('name') || '(unnamed)',
      owner,
      inferredOwner: !ownerUid,
      memberCount: memberUids.length,
      roles: nextRoles,
    });
  }

  for (const h of toWrite) {
    const how = h.inferredOwner ? 'inferred from memberUids[0]' : 'already set';
    console.log(
      `  ${h.id}  "${h.name}"  owner=${h.owner} (${how})  members=${h.memberCount}`
    );
  }

  console.log('');
  console.log(`to write:    ${toWrite.length}`);
  console.log(`already ok:  ${alreadyOk.length}`);
  if (partial.length) console.log(`had ownerUid but no roles: ${partial.length}`);
  if (empty.length) {
    console.log(`SKIPPED (no members, owner not inferable): ${empty.length}`);
    for (const id of empty) console.log(`  ${id}`);
  }

  if (!APPLY) {
    console.log('');
    console.log('Dry run. Re-run with --apply to write.');
    return;
  }

  // Batched: 500 is the Firestore limit per commit.
  let batch = db.batch();
  let pending = 0;
  let written = 0;
  for (const h of toWrite) {
    batch.update(db.collection('households').doc(h.id), {
      ownerUid: h.owner,
      roles: h.roles,
    });
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
  console.log('Now deploy the rules:  firebase deploy --only firestore:rules');
})().catch((err) => {
  // The default credential failure arrives as a bare stack trace from deep inside
  // google-auth-library, which says nothing about what to actually do about it.
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
    console.error('       node tools/backfill-household-roles.js');
    console.error('');
    console.error('That key is a full-access credential for the project. Do not commit it.');
    console.error('');
    console.error('Alternatively, with the gcloud CLI installed:');
    console.error('     gcloud auth application-default login');
    process.exit(1);
  }
  console.error(err);
  process.exit(1);
});
