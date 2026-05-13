# Data Retention Policy

Date: 2026-05-13

## Principles

- Retain only what is necessary for the declared purpose.
- Remove or anonymize data when the retention basis expires.
- Keep security logs long enough to investigate abuse and incidents.

## Current Technical Retention Baseline

| Data Category | Current Technical Control | Target Policy |
|---|---|---|
| Access tokens | Short-lived JWTs | 15 minutes maximum |
| Refresh tokens | Stored server-side and scheduled for cleanup | 7 days by default, purge expired tokens daily |
| Login / security audit events | Structured audit logging | Follow organization log-retention schedule |
| Privacy acknowledgements | Stored on account record | Keep while account is active and as required for auditability |
| Deletion requests | Stored on account record | Keep until fulfilled and then archive according to legal policy |

## Required Business Decisions

The following still need business/legal validation outside the code:
- retention duration for `Situation` records
- retention duration for uploaded/imported source files
- archival vs anonymization rules for closed fiscal years
- retention duration for audit logs and incident artifacts
