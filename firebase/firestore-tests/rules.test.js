// Firestore security-rules tests. Run against the local emulator (free tier):
//   firebase emulators:exec --only firestore "npm --prefix firebase/firestore-tests test"
//
// Each test asserts a single rule branch in ../../firestore.rules so a future
// rules change that loosens access fails loudly here.

const { test, before, after, beforeEach } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  query,
  where,
} = require('firebase/firestore');

const PROJECT_ID = 'demo-proofnest';
const LANDLORD = 'landlord-uid';
const TENANT = 'tenant-uid';
const STRANGER = 'stranger-uid';

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: '127.0.0.1',
      port: 8080,
      rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8'),
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    // Roles consulted by isLandlordUser()/isTenantUser().
    await setDoc(doc(db, 'publicProfiles', LANDLORD), { role: 'LANDLORD' });
    await setDoc(doc(db, 'publicProfiles', TENANT), { role: 'TENANT' });
    // Occupied-ish active property: both parties are members.
    await setDoc(doc(db, 'properties', 'prop-active'), {
      id: 'prop-active',
      landlordId: LANDLORD,
      tenantId: TENANT,
      status: 'ACTIVE',
      inviteCode: 'ABC123',
      moveInInspectionSubmittedAtMillis: 1,
      moveOutInspectionSubmittedAtMillis: null,
    });
    // Vacant, still joinable by invite code (no tenant yet).
    await setDoc(doc(db, 'properties', 'prop-vacant'), {
      id: 'prop-vacant',
      landlordId: LANDLORD,
      tenantId: null,
      status: 'PENDING',
      inviteCode: 'JOIN99',
    });
    await setDoc(doc(db, 'inviteCodes', 'EXISTING1'), {
      landlordId: LANDLORD,
      propertyId: 'prop-vacant',
    });
    await setDoc(doc(db, 'notifications', 'notif-tenant'), {
      recipientUid: TENANT,
      title: 'Hello',
      body: 'x',
      read: false,
    });
    // Dispute raised by the tenant; the landlord is the non-raiser resolver.
    await setDoc(doc(db, 'disputes', 'disp-1'), {
      propertyId: 'prop-active',
      raisedByUid: TENANT,
      status: 'OPEN',
      itemId: 'item-1',
      resolutionNote: '',
      resolvedAtMillis: null,
    });
  });
});

const asLandlord = () => testEnv.authenticatedContext(LANDLORD).firestore();
const asTenant = () => testEnv.authenticatedContext(TENANT).firestore();
const asStranger = () => testEnv.authenticatedContext(STRANGER).firestore();
const asUnauth = () => testEnv.unauthenticatedContext().firestore();

// ---- inviteCodes ---------------------------------------------------------

test('inviteCodes: a signed-in user can GET a code by id', async () => {
  await assertSucceeds(getDoc(doc(asTenant(), 'inviteCodes', 'EXISTING1')));
});

test('inviteCodes: an unauthenticated user cannot GET a code', async () => {
  await assertFails(getDoc(doc(asUnauth(), 'inviteCodes', 'EXISTING1')));
});

test('inviteCodes: listing the whole collection is denied (no enumeration)', async () => {
  await assertFails(getDocs(collection(asTenant(), 'inviteCodes')));
});

test('inviteCodes: a landlord can create a new code they own', async () => {
  await assertSucceeds(
    setDoc(doc(asLandlord(), 'inviteCodes', 'NEWCODE1'), {
      landlordId: LANDLORD,
      propertyId: 'prop-vacant',
    }),
  );
});

test('inviteCodes: cannot create a code claiming another landlord', async () => {
  await assertFails(
    setDoc(doc(asTenant(), 'inviteCodes', 'NEWCODE2'), {
      landlordId: LANDLORD,
      propertyId: 'prop-vacant',
    }),
  );
});

test('inviteCodes: a non-owner cannot update an existing code', async () => {
  await assertFails(
    updateDoc(doc(asStranger(), 'inviteCodes', 'EXISTING1'), { propertyId: 'hijack' }),
  );
});

test('inviteCodes: the owner can delete their code', async () => {
  await assertSucceeds(deleteDoc(doc(asLandlord(), 'inviteCodes', 'EXISTING1')));
});

// ---- properties (get vs list) -------------------------------------------

test('properties: the landlord member can GET their property', async () => {
  await assertSucceeds(getDoc(doc(asLandlord(), 'properties', 'prop-active')));
});

test('properties: the tenant member can GET their property', async () => {
  await assertSucceeds(getDoc(doc(asTenant(), 'properties', 'prop-active')));
});

test('properties: a stranger cannot GET an active property', async () => {
  await assertFails(getDoc(doc(asStranger(), 'properties', 'prop-active')));
});

test('properties: any signed-in user can GET a joinable vacant property', async () => {
  await assertSucceeds(getDoc(doc(asStranger(), 'properties', 'prop-vacant')));
});

test('properties: an unauthenticated user cannot GET a vacant property', async () => {
  await assertFails(getDoc(doc(asUnauth(), 'properties', 'prop-vacant')));
});

test('properties: a landlord can LIST their own properties (filtered query)', async () => {
  const q = query(collection(asLandlord(), 'properties'), where('landlordId', '==', LANDLORD));
  await assertSucceeds(getDocs(q));
});

test('properties: a tenant can LIST their own properties (filtered query)', async () => {
  const q = query(collection(asTenant(), 'properties'), where('tenantId', '==', TENANT));
  await assertSucceeds(getDocs(q));
});

test('properties: an unfiltered LIST is denied', async () => {
  await assertFails(getDocs(collection(asLandlord(), 'properties')));
});

test('properties: cannot LIST another landlord\'s properties', async () => {
  const q = query(collection(asTenant(), 'properties'), where('landlordId', '==', STRANGER));
  await assertFails(getDocs(q));
});

// ---- notifications -------------------------------------------------------

test('notifications: the recipient can read their notification', async () => {
  await assertSucceeds(getDoc(doc(asTenant(), 'notifications', 'notif-tenant')));
});

test('notifications: a non-recipient cannot read it', async () => {
  await assertFails(getDoc(doc(asLandlord(), 'notifications', 'notif-tenant')));
});

test('notifications: the recipient can flip only the read flag', async () => {
  await assertSucceeds(updateDoc(doc(asTenant(), 'notifications', 'notif-tenant'), { read: true }));
});

test('notifications: the recipient cannot edit any other field', async () => {
  await assertFails(updateDoc(doc(asTenant(), 'notifications', 'notif-tenant'), { title: 'changed' }));
});

test('notifications: a property member can create a notification for the other member', async () => {
  await assertSucceeds(
    setDoc(doc(asLandlord(), 'notifications', 'n-new'), {
      id: 'n-new',
      recipientUid: TENANT,
      type: 'TENANT_JOINED',
      title: 'Hello',
      body: 'world',
      propertyId: 'prop-active',
      createdAtMillis: 1,
      read: false,
    }),
  );
});

test('notifications: a stranger cannot create notifications', async () => {
  await assertFails(
    setDoc(doc(asStranger(), 'notifications', 'n-new2'), {
      id: 'n-new2',
      recipientUid: TENANT,
      type: 'TENANT_JOINED',
      title: 'Hello',
      body: 'world',
      propertyId: 'prop-active',
      createdAtMillis: 1,
      read: false,
    }),
  );
});

test('notifications: clients cannot delete notifications', async () => {
  await assertFails(deleteDoc(doc(asTenant(), 'notifications', 'notif-tenant')));
});

// ---- disputes ------------------------------------------------------------

test('disputes: a non-raiser member can resolve with the 3 allowed fields', async () => {
  await assertSucceeds(
    updateDoc(doc(asLandlord(), 'disputes', 'disp-1'), {
      status: 'RESOLVED',
      resolutionNote: 'agreed',
      resolvedAtMillis: 123,
    }),
  );
});

test('disputes: resolving while touching an extra field is denied', async () => {
  await assertFails(
    updateDoc(doc(asLandlord(), 'disputes', 'disp-1'), {
      status: 'RESOLVED',
      resolutionNote: 'agreed',
      resolvedAtMillis: 123,
      extra: 'sneaky',
    }),
  );
});

test('disputes: the raiser cannot resolve their own dispute', async () => {
  await assertFails(
    updateDoc(doc(asTenant(), 'disputes', 'disp-1'), {
      status: 'RESOLVED',
      resolutionNote: 'self',
      resolvedAtMillis: 123,
    }),
  );
});

test('disputes: a stranger cannot read a dispute', async () => {
  await assertFails(getDoc(doc(asStranger(), 'disputes', 'disp-1')));
});

test('disputes: clients cannot delete a dispute', async () => {
  await assertFails(deleteDoc(doc(asLandlord(), 'disputes', 'disp-1')));
});
