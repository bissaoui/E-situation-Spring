# Incident Response Plan

Date: 2026-05-13

## Severity Triggers

- suspected unauthorized access to personal or financial data
- repeated abnormal login or refresh-token abuse
- document exfiltration or suspicious mass export
- compromise of signing keys, secrets, or privileged accounts

## Immediate Actions

1. Contain the event.
2. Preserve logs and evidence.
3. Revoke affected sessions and rotate secrets if needed.
4. Assess impacted data categories and classifications.
5. Escalate to the application owner and security lead.

## DGSSI / ma-CERT Alignment

- For a major security incident impacting a public entity or OIV context, notify `ma-CERT (DGSSI)` within 48 hours.
- Preserve the incident timeline and evidence for the post-incident report due within 1 month.

## Technical Sources of Evidence

- application audit logs
- authentication and refresh-token events
- access logs at reverse proxy / hosting layer
- database audit extracts when available
- deployment and configuration change history
