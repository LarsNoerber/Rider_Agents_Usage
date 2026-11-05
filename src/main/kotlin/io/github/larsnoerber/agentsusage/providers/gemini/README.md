# Android Studio Gemini integration

The optional loaded plugin is `com.google.tools.ij.aiplugin` (verified in the installed Gemini plugin descriptor).
The reader selects `AiCodeQuotaService` for Consumer, `UserQuotaService` for Business and `BaicodeQuotaService` for
Enterprise, using only the active `GeminiUserInfoProvider` state's `getUserType()` enum. It never reads the
authorized-user or identity fields. Old APIs without category metadata retain the original `UserQuotaService` path.
It reflectively calls the selected service's `Companion.getInstance().getQuotaStatus().getValue()` and reads each
`ModelQuotaState` model ID, remaining fraction and reset instant. It does not retrieve credentials, make requests or
call Gemini's refresh methods. Gemini controls when its own state changes. A missing plugin, empty list, invalid
fraction or incompatible internal API never becomes a fabricated percentage. An empty list cannot establish whether
the user is signed out. A reset time that cannot be parsed is omitted. Failed reads retain prior model values and
publish an error; those values may be stale. The public tier name is read from the same user-info state. A Consumer
with an explicit `isPaidTier() == false` is labelled Free, matching Gemini's own `isFreeTier` implementation;
missing metadata is never used to infer Free. Authorized-user objects and credentials are never read.

`GeminiUsageService.getInstance()` exposes `current`, `refresh()`, `addListener` and `removeListener` through
`UsageSource<GeminiUsage>`; listeners are delivered on the EDT. `isAvailable()` means only that the optional plugin
is loaded, not that quota or authentication is available. `GeminiUsage.models` contains all reported models with
remaining fractions, rounded remaining percentages and optional reset times in Unix seconds; `error` and `problem`
describe unavailable or incompatible reads. The standard polling interval, provider ID `gemini` and the externally
added `settings.state.showGemini` selection gate are used.

The status widget defaults to every reported model's name and remaining percentage, individually colored by quota;
settings can select individual model IDs or hide all status models without stopping provider reads or hiding the
overview.
`geminiStatusAllModels` includes newly reported models; `geminiStatusModels` preserves explicit model choices.
`GeminiStatusModelSelectionPanel` subscribes only while shown and unsubscribes on hiding or disposal.
its tooltip and the overview show all reported models. Model labels use a separate full-width row above compact
bars; tooltips preserve the original IDs. Gemini defaults to selected in Android Studio when the persisted field
is absent, and explicit saved choices are honored. Settings explain when the optional plugin is unavailable.
Measurement time is unknown because the API supplies no observation timestamp. Successful reads are labelled local
reads, never fresh quota measurements. The Gemini plugin remains optional. The user confirmed live operation in
Android Studio and Rider; Free, Business and Enterprise behavior has not been independently checked with each tier.
