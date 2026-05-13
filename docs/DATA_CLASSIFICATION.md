# Data Classification

Date: 2026-05-13

## Levels

- `TRES_SECRET`: national-security or sovereign data. Morocco-only hosting and handling.
- `SECRET`: critical public-service or highly sensitive operational data. Morocco-only unless formally authorized.
- `CONFIDENTIEL`: sensitive internal or personal/business data with restricted access.
- `DIFFUSION_RESTREINTE`: internal non-public information with access controls.

## Current Application Mapping

| Object | Classification | Rationale |
|---|---|---|
| `AppUser` | `CONFIDENTIEL` | authentication data, roles, MFA metadata, privacy acknowledgements |
| `Situation` | `CONFIDENTIEL` | financial and administrative situation data, document references |
| `RefreshToken` | `CONFIDENTIEL` | session and access continuity metadata |

## Code Markers

The application now carries runtime entity markers using:
- `@DataClassification`
- `DataClassificationLevel`

These markers are present on the core entities so future access-control and audit extensions can enforce policy by classification.
