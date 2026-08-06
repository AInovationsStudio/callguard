# CallGuard release-candidate design

## Purpose

This document defines the work required to move CallGuard from an early debug
preview toward an F-Droid-ready release candidate. The goal is a dependable,
privacy-preserving utility whose failure mode preserves ordinary phone
behavior. This pass does not submit the application to F-Droid or add signing
credentials to the repository.

## Scope

### Runtime safety

1. Contact data availability must not prevent ordinary number rules from being
   evaluated. When contact matching is unavailable, contact matchers are
   treated as non-matches. The configured fallback is used only when no
   ordinary rule matches and the caller cannot be resolved through the
   configured contact path.
2. Screening bootstrap must publish a coherent initial snapshot before its
   observers begin replacing state. A call that arrives before initialization
   completes continues to use the explicit fail-open `ALLOW` behavior; a
   completed bootstrap must not be followed by a stale `loaded=false` state.
3. Unexpected exceptions in the screening callback must fail open with
   `ALLOW`. The configured unknown-number action remains reserved for
   unavailable or unparseable caller identity.
4. Revoking contacts permission must not leave contact matching silently
   enabled without a repair path. The preference and UI state must make the
   disabled condition explicit.

### UI and user trust

1. Align action labels and terminology across the rule list, wizard, Settings,
   and documentation.
2. Correct contacts messaging so it describes CallGuard's fallback behavior,
   rather than attributing the behavior to Android.
3. Show a granted-state contacts message instead of an always-on missing
   permission warning.
4. Apply the same API-level support rule for `Silence` in Settings that the
   wizard already uses.
5. Support Android system back on Settings and the rule wizard.
6. Add accessibility semantics for the add-rule action and stateful switches.
7. Replace raw preview identifiers with human-readable rule information.
8. Add a small About section to Settings containing the app version, offline
   privacy statement, Apache-2.0 license, and public project link.

### Release and documentation

1. Restore and verify the screenshots referenced by the README, or remove any
   reference that cannot be produced by the checked-in capture workflow.
2. Add a public `NOTICE` file covering bundled third-party attribution
   requirements.
3. Add a containerized unsigned release build path. It must clean stale
   outputs, assemble the release variant, and verify the application ID,
   version, manifest permissions, and resulting artifact path.
4. Add F-Droid metadata suitable for a release candidate without embedding
   signing keys or claiming that a submission has been made.
5. Correct README statements that overstate dependency-lock coverage or imply
   that the default CI job runs emulator instrumentation.
6. Preserve the existing offline, no-account, no-network screening contract.

## Explicitly out of scope

- Submitting to F-Droid or any app store.
- Storing signing keys, credentials, or personal infrastructure details.
- Adding analytics, crash-reporting SDKs, network access, or SMS support.
- Changing the documented default rule semantics or making screening
  fail-closed during initialization.
- Raising the install floor solely to simplify API-level handling.
- Rebranding the application or adding a second persistent wordmark.

## Implementation boundaries

- Keep screening resolution Android-free where practical so fallback and
  precedence behavior remains unit-testable.
- Keep disk, contacts, and regex compilation off the screening callback path.
- Prefer small shared copy constants or resource strings over duplicating
  equivalent user-facing labels.
- Make release verification fail closed: a stale or incorrectly identified APK
  must not be presented as the current build.
- Do not weaken an existing test or manifest audit to accommodate an
  implementation.

## Verification

The release candidate is not complete until the following are green:

1. Formatting, Android lint, unit tests, manifest audit, and dependency
   verification in the pinned container.
2. New unit tests for contact-unavailable precedence, bootstrap ordering,
   permission revocation, and callback fail-open behavior.
3. Instrumentation tests for Settings copy/state, system back, accessibility
   semantics, About/version display, and unsupported `Silence`.
4. Screenshot capture with screen-identity assertions and all README images
   present.
5. Clean unsigned release build with APK identity and manifest verification.

## Release posture

The resulting artifact is an auditable release candidate, not a store
submission. F-Droid review, signing, final version tagging, changelog
publication, and external distribution remain separate release-owner steps.
