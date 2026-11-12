const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function validVersePayload(uid, serverTimestamp) {
  return {
    userId: uid,
    sourceType: "QURAN",
    reference: "Surah Ash-Sharh 94:5",
    arabicText: "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا",
    translation: "So, surely with hardship comes ease.",
    sourceUrl: "https://quran.com/94/5",
    tafsirUrl: "https://quran.com/94:5/tafsirs/en-tafisr-ibn-kathir",
    reflectionNote: "A reminder of hope.",
    createdAt: serverTimestamp,
    updatedAt: serverTimestamp,
  };
}

function validDhikrLogPayload(uid, serverTimestamp) {
  return {
    userId: uid,
    dateKey: "2026-11-12",
    salawatCount: 33,
    tasbihCount: 99,
    dailyTarget: 100,
    completedPrayers: ["Fajr", "Dhuhr"],
    createdAt: serverTimestamp,
    updatedAt: serverTimestamp,
  };
}

function validDhikrProfilePayload(uid, serverTimestamp) {
  return {
    userId: uid,
    lifetimeSalawat: 330,
    currentStreakDays: 3,
    lastActiveDate: "2026-11-12",
    unlockedMilestones: ["first_33", "hundred_club"],
    createdAt: serverTimestamp,
    updatedAt: serverTimestamp,
  };
}

test("Unauthenticated user cannot read or write saved_verses", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("saved_verses").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("saved_verses").doc("v1").set(
      validVersePayload(ALICE_UID, new Date())
    )
  );
});

test("Alice can create, read, update note, and delete her own saved verse", async () => {
  const aliceCtx = testEnv.authenticatedContext(ALICE_UID);
  const aliceDb = aliceCtx.firestore();
  const ts = new Date();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("saved_verses").doc("quran_94_5");

  await assertSucceeds(docRef.set(validVersePayload(ALICE_UID, ts)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("saved_verses").get());
  await assertSucceeds(docRef.update({ reflectionNote: "Updated reflection", updatedAt: ts }));
  await assertSucceeds(docRef.delete());
});

test("Bob cannot read, create, update, or delete Alice's saved verses", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("saved_verses").doc("v1").set(
      validVersePayload(ALICE_UID, new Date())
    );
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("saved_verses").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("saved_verses").doc("v1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("saved_verses").doc("v2").set(
      validVersePayload(BOB_UID, new Date())
    )
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("saved_verses").doc("v1").delete());
});

test("Shadow update with extra ghost field or invalid sourceType fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_verses").doc("bad1").set({
      ...validVersePayload(ALICE_UID, ts),
      isAdmin: true,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_verses").doc("bad2").set({
      ...validVersePayload(ALICE_UID, ts),
      sourceType: "INVALID",
    })
  );
});

test("Alice can create and update her dhikr_logs and dhikr_profile, Bob cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date();

  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("dhikr_logs").doc("2026-11-12");
  await assertSucceeds(logRef.set(validDhikrLogPayload(ALICE_UID, ts)));
  await assertSucceeds(logRef.update({ salawatCount: 100, completedPrayers: ["Fajr", "Dhuhr", "Asr"], updatedAt: ts }));

  const profileRef = aliceDb.collection("users").doc(ALICE_UID).collection("dhikr_profile").doc("summary");
  await assertSucceeds(profileRef.set(validDhikrProfilePayload(ALICE_UID, ts)));
  await assertSucceeds(profileRef.update({ lifetimeSalawat: 400, updatedAt: ts }));

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("dhikr_logs").doc("2026-11-12").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("dhikr_profile").doc("summary").get());
});
