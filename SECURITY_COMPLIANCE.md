# Security Compliance Status Report

Date: 2026-05-14

Scope:
- Backend reviewed in this repository: `C:\Users\YassineBISSAOUI\Spring boot Project`
- Frontend/mobile reviewed in companion repository: `C:\E-Situation`

Method:
- Static code and configuration review, followed by implementation remediation passes on 2026-05-13 and 2026-05-14
- No legal certification, infrastructure-console review, live penetration test, malware scanning, SIEM review, or CNDP/DGSSI filing verification
- Findings below distinguish between `Implemented`, `Partial`, `Missing`, and `No evidence found`

## Executive Summary

Current status: not ready to claim compliance with CNDP / Law 09-08, DGSSI / Law 05-20, or an OWASP Top 10 production baseline.

Implemented in the remediation pass:
- Web `localStorage` session storage was removed from the Expo client. Web now uses in-memory session state, while native uses `SecureStore`.
- Backend access tokens were reduced to 15 minutes, with server-side refresh-token issuance, rotation, revocation, and cleanup.
- MFA/TOTP enrollment and verification flow was added for sensitive roles, including a setup-pending token path, QR-code enrollment, and authenticator-compatible SHA1 provisioning with legacy SHA256 verification fallback.
- Login lockout/backoff and a tighter auth-specific rate limit were added.
- Selected sensitive `Situation` string, amount, and date fields now use application-layer AES-GCM field encryption at rest, with startup schema repair and migration of legacy plaintext rows.
- Browser pages moved to external CSS/JS so a stricter CSP and other security headers can be enforced.
- Privacy notice exposure, account data export, deletion-request recording, privacy acknowledgement, audit logging, API versioning aliases, and data-classification/docs scaffolding were added.
- Legacy PostgreSQL auth-schema repair for `app_user` and `refresh_token` was added to reduce rollout failures while a formal migration framework is still recommended.
- Android cleartext traffic was disabled and the webpack dev wildcard relaxations were removed.

Highest-risk remaining gaps:
- Some query-critical `Situation` columns (`be`, `numeroOv`, `situation`, `projet`) still remain plaintext by design to preserve lookups and scoping; if they are classified as sensitive, a searchable-tokenization strategy is still needed.
- File uploads still do not have malware scanning.
- Web auth does not yet use `HttpOnly` cookies; it now avoids persistent browser storage but still relies on bearer tokens in JS memory.
- Consent banner / withdrawal UX for non-essential client-side collection is still not implemented.
- Cross-border hosting review, CNDP declarations/authorizations, SIEM/monitoring operations, and DGSSI-certified audit activities remain external obligations.

Controls already present:
- Passwords are hashed with bcrypt cost 12, not plaintext.
- JPA / repository patterns are used instead of raw SQL concatenation.
- Role and project-scope authorization exists for situation access.
- Backend input sanitization and bean validation are present.
- Selected sensitive `Situation` string, amount, and date fields are now encrypted at rest with application-layer AES-GCM, and legacy plaintext rows are migrated on startup.
- Native mobile uses Expo SecureStore instead of plain storage, and web no longer persists tokens in `localStorage`.
- Login UX uses a generic invalid-credentials message.
- MFA setup now supports QR-code enrollment with manual-secret fallback, and the backend accepts both new SHA1 and legacy SHA256 TOTP codes during the migration window.

## Highest Priority Findings

| Severity | Area | Finding | Evidence |
|---|---|---|---|
| Medium | Backend/Crypto | Sensitive `Situation` string, amount, and date fields are now encrypted at rest, but query-critical columns such as `be`, `numeroOv`, `situation`, and `projet` remain plaintext by design and a dedicated key-rotation procedure is still not evidenced. | `Situation` now encrypts selected string, amount, and date fields; lookup/scope columns remain plain for queries. |
| High | Backend/File Upload | Import flow now validates extension/MIME/size, but malware scanning is still not integrated. | `SituationImportService` validates upload metadata; no AV service is present. |
| High | Frontend/Auth | Web no longer uses `localStorage`, but browser auth still relies on bearer tokens in JS memory instead of `HttpOnly` cookies. | `C:\E-Situation\src\network\apiClient.ts` |
| Medium | Backend/OWASP A10 SSRF | Remote BE fetching is now allowlisted and HTTPS-only, but it still depends on remote host governance and DNS trust. | `RemoteBeUrlValidator`, `BeFileController` |
| Medium | CNDP | Privacy notice, acknowledgement, data export, and deletion-request flows were added, but full consent/banner and legal-basis governance remain incomplete. | Browser `privacy.html`, account APIs, mobile settings |
| Medium | Backend/API | API error leakage was reduced, but some browser-side flows still need the same level of generic handling. | `ApiExceptionHandler` updated; browser controllers remain partial |
| Medium | Backend/Monitoring | Audit logging now exists in-repo, but SIEM/anomaly detection operations are still not evidenced. | `AuditService` plus action hooks |

## Backend Audit Against Checklist

### Authentication and Authorization

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Strong password hashing with bcrypt cost >= 12 or argon2id | Implemented | bcrypt is configured with strength 12 in `SecurityConfig`. |
| MFA for admin and sensitive routes | Partial | TOTP-based MFA enrollment and verification were added for sensitive roles, including setup-pending handling, QR-code onboarding, and SHA1/SHA256 compatibility. Recovery-factor and organizational rollout still need completion. |
| JWT <= 15 min plus refresh-token rotation | Implemented | Access tokens now default to 900 seconds and refresh tokens are issued, rotated, revoked, and cleaned up server-side. |
| RBAC on protected endpoints | Partial | Stronger than basic RBAC in some areas: admin-only user APIs and project-scope access for situations. `src/main/java/com/example/situation/security/ProjetAccessService.java:20`, `:26`, `:38`; `src/main/java/com/example/situation/controller/SituationApiController.java:55`, `:74`, `:129` |
| Lock after 5 failed logins; backoff or CAPTCHA | Implemented | Failed-attempt tracking and timed lockouts were added. CAPTCHA is still not implemented, but exponential lock windows and auth rate limits are now present. |
| Rate-limit authentication endpoints (example 10/min/IP) | Implemented | Auth-specific rate limiting was added with a dedicated 10/minute default. |

### Input Validation and Injection Prevention

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Validate and sanitize all inputs server-side | Partial | `@Valid`, `@Validated`, model sanitization, and web input sanitization are present. Coverage is helpful but not exhaustive. `src/main/java/com/example/situation/controller/SituationApiController.java:32`, `:84`, `:95`; `src/main/java/com/example/situation/security/ModelSanitizer.java:16`; `src/main/java/com/example/situation/security/InputSanitizer.java:14`, `:15`, `:16` |
| Parameterized queries / ORM only | Implemented | Spring Data JPA / repository methods are used; no raw SQL concatenation found in reviewed code paths. |
| Prevent NoSQL / LDAP / XML / command injection | Partial | No NoSQL or LDAP layers are present. Command/XML injection controls are not explicitly relevant in reviewed code, but SSRF is present through `beUrl`. |
| Strip or escape HTML/JS before storage/display | Partial | Sanitizer strips tags; Thymeleaf escapes output by default. The sanitizer is regex-based and should not be treated as a full HTML security framework. `src/main/java/com/example/situation/security/InputSanitizer.java:15` |

### API Security

| Requirement | Status | Notes / Evidence |
|---|---|---|
| HTTPS only and HSTS | Partial | HSTS headers are now configured. Absolute HTTPS enforcement still depends on edge / platform configuration. |
| Security headers on every response | Implemented | CSP, frame denial, content-type hardening, referrer policy, permissions policy, and HSTS headers were added in security configuration. |
| CORS whitelist, never `*` in production | Partial | Production config does not use literal `*`, but relies on broad origin patterns including local IP wildcards. `src/main/java/com/example/situation/config/SecurityConfig.java:93`, `:96` |
| Versioned API (`/api/v1/`) | Implemented | Versioned aliases were added for auth, account, situations, users, and BE file APIs. |
| Hide internal error details in production | Partial | API exception responses were generalized, but some browser-side flows still need the same treatment. |

### Data Encryption and Storage

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Encrypt sensitive data at rest | Partial | Selected sensitive `Situation` string, amount, and date fields now use application-layer AES-GCM encryption with legacy-row migration; lookup/scoping fields such as `be`, `numeroOv`, `situation`, and `projet` remain plaintext by design. |
| Encrypt data in transit (TLS 1.2+/1.3) | Partial | Expected at hosting edge, but no code-level enforcement or transport policy is evidenced here. |
| Hash passwords only | Implemented | Passwords are hashed and plaintext seed-password logging was removed. |
| Field-level encryption for sensitive PII | Partial | `Situation` now uses field-level encryption on selected string, amount, and date fields such as beneficiary, expense-description, payment amounts, and payment dates; query-critical identifiers and scope columns remain plaintext by design. |
| Never log sensitive data | Partial | Plaintext temporary password logging was removed and BE logging was reduced, but a full sensitive-log review should still continue as new features are added. |

### Session Management

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Cryptographically random session IDs | Partial | JWT signing key generation uses secure random if not configured, but bearer-token sessions are not server-revocable. `src/main/java/com/example/situation/security/JwtService.java:77` |
| Cookies: `HttpOnly; Secure; SameSite=Strict` | Missing / not evidenced | API auth uses bearer tokens rather than hardened cookies. No explicit cookie hardening configuration found for browser sessions. |
| Invalidate sessions on logout | Partial | Refresh tokens are revoked on logout and access tokens are short-lived. Access JWTs remain stateless until expiry. |
| Session timeout <= 30 min for sensitive operations | Implemented | Access tokens now default to 15 minutes. |

### File Uploads

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Validate MIME type and extension | Implemented | Backend upload validation now checks extension, MIME type, and file size before parsing. |
| Scan uploaded files for malware | Missing | No AV scanning integration found. |
| Store uploads outside web root | Partial / not applicable | Import files are processed from request stream and not persisted by this code path, which avoids public storage but does not replace malware validation. |
| Rename files on upload | Not applicable to current import flow | Files are not stored by this code path. |

### Infrastructure and Dependencies

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Pin dependency versions and run dependency audit on every build | Partial | Backend direct dependencies are versioned in `pom.xml`, but no repo evidence of automated SCA (`dependency-check`, `npm audit`, `snyk`, `trivy`, etc.) was found. |
| No hardcoded secrets in VCS | Partial | Render env vars are externalized, and seed-password logging was removed. Seed credentials are now required by default unless explicitly relaxed for development. |
| Disable directory listing | No evidence found | No explicit web-server listing config evidenced in repo. |
| Remove debug endpoints / stack traces / admin tools from prod | Partial | Swagger/OpenAPI endpoints are publicly permitted in security config. `src/main/java/com/example/situation/config/SecurityConfig.java:42`, `:50` |
| Structured logging with audit trail for sensitive actions | Implemented | Audit logging was added for authentication, password change, user/situation mutation, document access, privacy acknowledgement, export, and deletion requests. |

## Frontend Audit Against Checklist

### XSS and Content Security

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Never use `dangerouslySetInnerHTML` / unsafe `innerHTML` with user data | Implemented in Expo client | No `dangerouslySetInnerHTML` found in `C:\E-Situation\src`. |
| Sanitize dynamic HTML with DOMPurify if needed | Not applicable / no evidence of dynamic HTML rendering | No dynamic HTML rendering flow found in Expo client. |
| Strict CSP, no `unsafe-inline`, no `unsafe-eval` | Implemented for browser surfaces | Inline browser styles/scripts were moved out and backend CSP/security headers were added. |
| Avoid sensitive storage in `localStorage` / `sessionStorage` | Implemented | Expo web no longer persists session tokens in `localStorage`; native uses SecureStore and web uses memory-only session state. |

### Forms and Data Handling

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Validate all form fields client-side and server-side | Implemented | Login/password/MFA flows now have stronger client and server validation, including 12-character password policy, OTP formatting, and QR-assisted MFA enrollment with manual fallback. |
| CSRF tokens on state-changing forms | Partial / architecture-dependent | Backend keeps CSRF enabled for browser pages but explicitly ignores `/api/**`. Mobile app uses bearer tokens rather than cookies, so CSRF is less relevant there, but no browser API CSRF layer exists. `src/main/java/com/example/situation/config/SecurityConfig.java:40` |
| Tell users what data is collected and why | Implemented | Privacy-purpose disclosure was added to login/settings/browser forms and a privacy page was added. |
| Visible privacy notice link on every data collection form | Implemented | Privacy notice links/actions were added to browser forms and the mobile login/settings flows. |

### Authentication UX

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Do not reveal whether an email/user exists | Implemented on login | Generic invalid-credentials message is used. `C:\E-Situation\src\navigation\AppNavigator.tsx:116`; `src/main/resources/templates/login.html:18` |
| Secure password inputs and no autocomplete for sensitive fields | Partial | React Native uses `secureTextEntry`, but there is no equivalent browser autocomplete hardening for the HTML templates. `C:\E-Situation\src\screens\LoginScreen.tsx:84` |
| Visible logout button from every authenticated page | Implemented | Drawer contains `Deconnexion`; browser page also exposes logout. `C:\E-Situation\src\navigation\AppNavigator.tsx:66`; `src/main/resources/templates/situations.html:41` |

### Third-Party Scripts and Dependencies

| Requirement | Status | Notes / Evidence |
|---|---|---|
| Trusted third-party scripts with SRI | No evidence found for script tags in Expo client | No explicit analytics/marketing script tags were found in the Expo client. |
| Audit dependencies for exfiltration risks | Missing / no evidence found | No dependency-audit automation evidenced. |
| No analytics/tracking without consent | Partial | No explicit analytics SDK was identified in `C:\E-Situation\package.json`, but there is also no consent layer if analytics are added later. |

### Frontend-Specific Additional Risks

| Severity | Finding | Evidence |
|---|---|---|
| Medium | Web auth still uses bearer tokens in JS memory instead of `HttpOnly` cookies | `C:\E-Situation\src\network\apiClient.ts` |
| Resolved | Android cleartext traffic was disabled | `C:\E-Situation\app.json` |
| Resolved | Webpack dev wildcard CORS/host relaxations were removed | `C:\E-Situation\webpack.config.js` |

## CNDP / Law 09-08 Gap Assessment

| Requirement | Status | Notes |
|---|---|---|
| Legal basis documented for each collection | Partial | Privacy notice and CNDP processing-register documentation were added, but formal legal review remains external. |
| Purpose limitation | Partial | Purpose disclosure now exists in privacy docs and browser/app notices. |
| Data minimization | Partial | Data fields appear business-driven, but no documented minimization review exists. |
| Accuracy, access, rectification, deletion mechanisms | Partial | Password change, profile export, privacy acknowledgement, and deletion-request flows now exist; broader profile rectification remains limited. |
| Retention limits enforced | Missing | No retention schedule, TTL, archival, anonymization, or purge job found. |
| CNDP declaration / authorization | No evidence found | This is organizational/legal, not evidenced in code. |
| Sensitive data authorization before storage | No evidence found | No technical classification or authorization gating exists. |
| Document processing operations | Partial | CNDP processing-register documentation and privacy page were added to the repo. |
| Right of access | Implemented | Account data export endpoint and mobile UI action were added. |
| Right of rectification | Partial | Admin CRUD and password change exist, but no user-facing personal-data rectification workflow. |
| Right of opposition | Missing | No opt-out/objection flow found. |
| Right of deletion | Partial | User-driven deletion-request recording was added; fulfillment workflow remains organizational. |
| "My Data" dashboard section | Partial | Mobile settings now expose privacy, export, and deletion-request actions. |
| Data stored in Morocco by default | Partial | Deployment config targets Frankfurt, EU. This may reduce cross-border risk versus US hosting, but it does not satisfy Morocco-only requirements for classified/public/OIV data. `render.yaml:6` |
| Cross-border transfer governance | No evidence found | No SCC/authorization/process docs in repo. |
| Consent banner for cookies/non-essential data | Missing | No consent UI found. |
| Consent records with timestamp/version and withdrawal | Partial | Privacy notice acknowledgement with timestamp/version was added. Withdrawal/consent banner UX remains incomplete. |

## DGSSI / Law 05-20 Gap Assessment

Assumption note:
- If this application is used by a Moroccan public entity, local authority, public enterprise, or OIV, DGSSI obligations become materially stronger. The repo does not contain enough organizational context to certify applicability boundaries.

| Requirement | Status | Notes |
|---|---|---|
| Data classification from day 1 | Implemented | Classification markers and supporting docs were added for core entities. |
| Access controls based on classification | Missing | Access control exists by role/project, not by formal classification level. |
| Intrusion detection / SIEM monitoring | No evidence found | No monitoring integration in repo. |
| Audit logs for classified/personal data access | Implemented | Audit logging was added for key account, document, and mutation events. |
| Anomalous access monitoring | Missing | No failed-login anomaly, export anomaly, or off-hours detection found. |
| Incident response plan | Implemented in repo / external execution pending | Incident-response documentation was added; operational execution remains external. |
| ma-CERT notification path within 48h | No evidence found | Operational/legal process not documented. |
| Post-incident reporting support | Missing | No incident log schema or workflow found. |
| BCP / DRP | Implemented in repo / external execution pending | Continuity and DRP documentation were added; backup/failover operations remain external. |
| DGSSI-certified security audit before production | No evidence found | Not evidenced in repo. |
| Annual penetration testing and pre-release testing | No evidence found | Not evidenced in repo. |
| DGSSI ASVF / audit checklist usage | No evidence found | Not evidenced in repo. |
| DGSSI-approved crypto algorithms | Partial | Application-layer AES-GCM field encryption is now present for selected string, amount, and date fields, but key-management hardening, explicit rotation procedures, and full algorithm/hosting approval remain incomplete. |
| TLS 1.3 and legacy protocol disablement | No evidence found | Usually enforced at ingress/load balancer; not evidenced in repo. |

## OWASP Top 10 Mapping

| OWASP Area | Status | Notes |
|---|---|---|
| Broken Access Control | Partial | Project-scope controls remain strong; classification-aware access policy is still a future enhancement. |
| Cryptographic Failures | Medium | Access-token lifetime, refresh rotation, MFA hardening, and at-rest encryption coverage were improved, but web cookie-hardening and formal key-rotation procedures remain incomplete. |
| Injection | Partial | SQL injection posture is good and SSRF was constrained with allowlisting, but file scanning and broader remote-governance concerns remain. |
| Insecure Design | Partial | MFA, user-rights flows, processing docs, and retention docs were improved; formal consent and legal process work remains. |
| Security Misconfiguration | Partial | Major headers, versioning, cleartext Android, and dev wildcard settings were fixed; Swagger exposure and hosting-edge controls still need review. |
| Vulnerable and Outdated Components | Unknown / partial | Versions are declared, but no SCA automation is evidenced. |
| Identification and Authentication Failures | Partial | MFA, short-lived access tokens, refresh rotation, and lockout were added; cookie-based web sessions are still not in place. |
| Software and Data Integrity Failures | Partial | No CI evidence of dependency scanning or signed release controls. |
| Logging and Monitoring Failures | Partial | Audit logging now exists in-repo, but SIEM/anomaly monitoring operations are still not evidenced. |
| SSRF | Partial | Remote `beUrl` fetching is now HTTPS-only and allowlisted, but still warrants governance and monitoring. |

## Recommended Remediation Order

### Immediate (0 to 14 days)

1. Integrate real malware scanning for imported/uploaded files.
2. Decide whether web auth should move from bearer tokens in memory to `HttpOnly` cookie sessions.
3. Add consent banner / withdrawal UX for non-essential browser-side collection where applicable.
4. Review Swagger/OpenAPI exposure and hosting-edge HTTPS/TLS enforcement in production.
5. Document `DATA_ENCRYPTION_KEY` rotation/re-encryption procedures and explicitly classify the remaining plaintext query/scoping columns.

### Near Term (15 to 45 days)

1. Expand profile rectification and rights-management UX beyond password change/export/deletion request.
2. Add CI-driven dependency/security scanning and signed release controls.
3. Extend audit feeds into SIEM/anomaly monitoring.
4. Add backup/restore drills and hosting failover validation.
5. Review browser controllers for the same generic-error discipline already applied to the APIs.
6. Replace the temporary startup auth-schema repair path with formal database migrations (`Flyway` or `Liquibase`) for future releases.

### Compliance and Governance (30 to 60 days)

1. Finalize legal-basis mapping and CNDP declaration / authorization work.
2. Approve retention schedules and implement purge/anonymization jobs for business data.
3. Confirm hosting-region acceptability for the actual legal/data-classification context.
4. Schedule formal penetration testing and DGSSI-aligned audit activity where applicable.
5. Operationalize incident response, BCP, DRP, and ma-CERT notification procedures.

## Suggested Definition of Done Before Production Claim

The project should not be described as CNDP/DGSSI compliant until, at minimum:
- browser auth no longer relies on JS-accessible bearer tokens; web sessions use hardened `HttpOnly; Secure; SameSite=Strict` cookies
- MFA, short-lived access tokens, refresh rotation, and revocation are in place
- SSRF path is removed or allowlisted
- security headers and HTTPS/HSTS posture are enforced
- sensitive fields are encrypted at rest and any remaining plaintext lookup columns are explicitly justified
- audit logging exists for sensitive actions
- privacy notice, consent handling, retention policy, and user-rights flows exist
- data-classification, incident response, and operational monitoring are documented and active
- a formal security assessment and legal/compliance review are completed

## Review Limitations

- This report is based on repository contents available on 2026-05-14.
- `NO_MATCH` means the control was not found in the reviewed code/config, not that it definitively does not exist in an external platform.
- Operational controls such as SIEM, WAF, backups, key custody, CNDP filings, and DGSSI-certified audits must be validated outside the source code.
