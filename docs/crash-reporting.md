# Crash and ANR reporting

Sentry initializes only when `SENTRY_DSN` is supplied. Default PII, screenshots,
view hierarchy capture, interaction breadcrumbs, and performance tracing are
disabled. Java/Kotlin crash and ANR collection remain enabled.

Release mapping upload is enabled only when `SENTRY_AUTH_TOKEN`, `SENTRY_ORG`,
and `SENTRY_PROJECT` are present in the signed release workflow environment.
Configure those repository secrets together with `SENTRY_DSN`; builds without
them remain valid but do not send telemetry or upload mappings.

Before production enablement, verify a deliberately thrown internal test crash
in a non-production build and confirm its stack trace is symbolicated. Do not
ship a user-accessible crash trigger.
