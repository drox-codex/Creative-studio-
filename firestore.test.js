const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write profiles, projects, or AI history", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set({
        userId: ALICE_UID,
        name: "Unauthorized",
        projectType: "DESIGN",
        canvasWidth: 1080,
        canvasHeight: 1080,
        backgroundColorHex: "#090A0C",
        createdAt: past,
        updatedAt: past,
      })
  );
});

test("User profile create, read, partial update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Creative",
      email: "alice@example.com",
      preferredTheme: "obsidian",
      preferredExportQuality: 95,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Partial update allowed fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      brandName: "Monolith Studio",
      brandPrimaryHex: "#090A0C",
      brandAccentHex: "#C4C8CE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read or write Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Projects CRUD, schema validation, @DocumentId exclusion, and strict owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Valid Kotlin-mirrored create payload (no 'id' field)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_poster_1")
      .set({
        userId: ALICE_UID,
        name: "Modern Poster",
        projectType: "DESIGN",
        canvasWidth: 1080,
        canvasHeight: 1350,
        backgroundColorHex: "#090A0C",
        adjustments: { brightness: 0, contrast: 1.0 },
        layers: [
          {
            id: "layer_1",
            type: "TEXT",
            text: "DREAM BIGGER",
            x: 0.5,
            y: 0.4,
          },
        ],
        clips: [],
        createdAt: past,
        updatedAt: past,
      })
  );

  // Reject payload containing '@DocumentId' 'id' field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_bad_id")
      .set({
        id: "proj_bad_id",
        userId: ALICE_UID,
        name: "Bad Project",
        projectType: "PHOTO",
        canvasWidth: 1080,
        canvasHeight: 1080,
        backgroundColorHex: "#090A0C",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Reject invalid projectType
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_bad_type")
      .set({
        userId: ALICE_UID,
        name: "Bad Type",
        projectType: "AUDIO_GEN",
        canvasWidth: 1080,
        canvasHeight: 1080,
        backgroundColorHex: "#090A0C",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Partial update on mutable fields succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_poster_1")
      .update({
        name: "Modern Poster v2",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject changing immutable projectType on update
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_poster_1")
      .update({
        projectType: "VIDEO",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Alice can list her own projects (mirrors Kotlin query)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .get()
  );

  // Bob cannot list, get, update, or delete Alice's projects
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_poster_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_poster_1")
      .delete()
  );

  // Alice can delete her own project
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_poster_1")
      .delete()
  );
});

test("AI history is append-only for owner and isolated across accounts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ai_history")
      .doc("entry_1")
      .set({
        userId: ALICE_UID,
        projectId: "proj_poster_1",
        prompt: "Make the text metallic silver",
        response: "Updated selected text fill to #C4C8CE.",
        appliedAction: "UPDATE_SELECTED_COLOR",
        createdAt: past,
      })
  );

  // Updates forbidden on audit history
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ai_history")
      .doc("entry_1")
      .update({ prompt: "Tampered" })
  );

  // Cross-user read denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("ai_history").get()
  );
});
