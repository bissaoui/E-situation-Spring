# Business Continuity and Disaster Recovery

Date: 2026-05-13

## Continuity Goals

- maintain access to critical authentication and situation-consultation functions
- recover from accidental data corruption, credential compromise, or hosting outage
- preserve audit evidence needed for compliance and incident handling

## Minimum Recovery Controls

- database backups with tested restore procedure
- documented secret-rotation process
- ability to revoke sessions and refresh tokens after compromise
- alternate deployment path for backend restoration
- documented contact list for owners and responders

## Current Repo Support

- short-lived JWT access tokens
- server-side refresh-token rotation and revocation
- audit logging for sensitive account and document actions
- security configuration and classification docs tracked in source control

## Still Required Outside the Repo

- verified backup schedule
- restore drills
- hosting failover procedure
- communications runbook
- named owners and escalation tree
