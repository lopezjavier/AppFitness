// Maestro runScript (host side): resets or restores the seeded local test user's onboarding state.
// It signs in as the user over REST (GoTrue password grant) and calls PostgREST as that user, so
// RLS applies and no secret key is involved.
//
// Env (passed by the flow; the key is NOT committed):
//   SUPABASE_LOCAL_PUBLISHABLE_KEY  value printed by `npx supabase status` (PUBLISHABLE_KEY)
//   MODE                            "reset" | "restore"
//
// reset:   onboarding_completed=false, biometric fields null, no body_measurements rows.
// restore: seed values (true, male, 1999-06-15, 178, moderate) and the seed 74.20 kg manual row.

var BASE = "http://localhost:54321";
var EMAIL = "test@appfitness.local";
var PASSWORD = "appfitness-local-test"; // local-only, documented in planning/08 section 3.2
var USER_ID = "00000000-0000-4000-8000-000000000001";

var key = typeof SUPABASE_LOCAL_PUBLISHABLE_KEY === "undefined" ? "" : SUPABASE_LOCAL_PUBLISHABLE_KEY;
if (!key) {
  throw new Error(
    "SUPABASE_LOCAL_PUBLISHABLE_KEY is missing: run maestro with " +
      "-e SUPABASE_LOCAL_PUBLISHABLE_KEY=<PUBLISHABLE_KEY from `npx supabase status`>"
  );
}
var mode = typeof MODE === "undefined" ? "" : MODE;
if (mode !== "reset" && mode !== "restore") {
  throw new Error("MODE must be 'reset' or 'restore', got '" + mode + "'");
}

function call(method, path, headers, body) {
  var res = http.request(BASE + path, {
    method: method,
    headers: headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(method + " " + path + " failed: " + res.status + " " + res.body);
  }
  return res;
}

var login = call(
  "POST",
  "/auth/v1/token?grant_type=password",
  { apikey: key, "Content-Type": "application/json" },
  { email: EMAIL, password: PASSWORD }
);
var token = json(login.body).access_token;
var auth = {
  apikey: key,
  Authorization: "Bearer " + token,
  "Content-Type": "application/json",
  Prefer: "return=minimal",
};

var profile =
  mode === "reset"
    ? { onboarding_completed: false, sex: null, birth_date: null, height_cm: null, activity_level: null }
    : {
        onboarding_completed: true,
        sex: "male",
        birth_date: "1999-06-15",
        height_cm: 178,
        activity_level: "moderate",
      };

call("PATCH", "/rest/v1/profiles?id=eq." + USER_ID, auth, profile);
call("DELETE", "/rest/v1/body_measurements?user_id=eq." + USER_ID, auth);
if (mode === "restore") {
  call("POST", "/rest/v1/body_measurements", auth, { weight_kg: 74.2, source: "manual" });
}
console.log("test user state: " + mode + " done");
