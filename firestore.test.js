const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-jarvisai-05a726fe-014d-4f15-a931-073a0ef3bfcd";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date(Date.now() - 1000);
}

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

function aliceDb() {
  return testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
}

function bobDb() {
  return testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
}

function unauthDb() {
  return testEnv.unauthenticatedContext().firestore(DATABASE_ID);
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const db = unauthDb();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("forex_watchlist").get());
});

test("Owner can create and update their own UserProfile", async () => {
  const db = aliceDb();
  const ts = nowTimestamp();
  await assertSucceeds(
    db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      preferredLanguage: "en",
      speechRate: 1.0,
      voiceResponseEnabled: true,
      confirmSensitiveActions: true,
      memoryOptIn: true,
      wakePhraseEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    db.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "bn",
      updatedAt: nowTimestamp(),
    })
  );
});

test("Shadow update with ghost field fails on UserProfile", async () => {
  const db = aliceDb();
  const ts = nowTimestamp();
  await assertFails(
    db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      preferredLanguage: "en",
      speechRate: 1.0,
      voiceResponseEnabled: true,
      confirmSensitiveActions: true,
      memoryOptIn: true,
      wakePhraseEnabled: true,
      isAdmin: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Cross-user read and write are denied", async () => {
  const aDb = aliceDb();
  await assertSucceeds(
    aDb.collection("users").doc(ALICE_UID).collection("conversations").doc("msg1").set({
      userId: ALICE_UID,
      sessionId: "sess1",
      role: "user",
      content: "Hello Jarvis",
      language: "en",
      source: "voice",
      createdAt: nowTimestamp(),
    })
  );

  const bDb = bobDb();
  await assertFails(bDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(bDb.collection("users").doc(ALICE_UID).collection("conversations").doc("msg1").get());
  await assertFails(
    bDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem1").set({
      userId: ALICE_UID,
      title: "Hijack",
      content: "Bad",
      category: "preference",
      approved: true,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );
});

test("Owner can manage StudioProject, ResearchBrief, ForexWatchItem, and UserMemoryItem", async () => {
  const db = aliceDb();
  const ts = nowTimestamp();
  await assertSucceeds(
    db.collection("users").doc(ALICE_UID).collection("studio_projects").doc("proj1").set({
      userId: ALICE_UID,
      title: "Portfolio",
      prompt: "Build portfolio",
      framework: "html_css_js",
      htmlCode: "<h1>Hi</h1>",
      cssCode: "body{}",
      jsCode: "console.log(1);",
      lastDiagnostic: "OK",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    db.collection("users").doc(ALICE_UID).collection("research_briefs").doc("brief1").set({
      userId: ALICE_UID,
      query: "ECB rate decision",
      summary: "Summary text",
      verificationNotes: "Verified via official ECB release.",
      sourceTitles: ["ECB Press Release"],
      sourceUrls: ["https://www.ecb.europa.eu"],
      createdAt: ts,
    })
  );
  await assertSucceeds(
    db.collection("users").doc(ALICE_UID).collection("forex_watchlist").doc("EUR_USD").set({
      userId: ALICE_UID,
      baseCurrency: "EUR",
      quoteCurrency: "USD",
      targetAlertRate: 1.1,
      alertDirection: "above",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    db.collection("users").doc(ALICE_UID).collection("memories").doc("mem1").set({
      userId: ALICE_UID,
      title: "Morning Forex Brief",
      content: "Check EUR/USD at 9am",
      category: "shortcut",
      approved: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
