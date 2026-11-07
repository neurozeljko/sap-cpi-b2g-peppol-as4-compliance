# ADR-002: Dynamic Peppol SML/SMP Discovery and Pre-Flight Schematron SVRL Validation

## Status
Accepted

## Context
European e-invoicing compliance forbids static AS4 endpoint configurations due to periodic Access Point migrations and rotating recipient certificates. Invalid submissions to public sector authorities incur substantial tax compliance penalties.

## Decision
1. **Canonical Schema:** Map inbound S/4HANA Billing OData documents directly to CEN/EN16931 UBL 2.1 XML specifications.
2. **Pre-flight Schematron SVRL Engine:** Execute local Groovy-based business rule and tax balance audits before external dispatch. Quarantining non-compliant payloads with HTTP 422 SVRL diagnostics prevents external transmission of invalid invoices.
3. **Dynamic Discovery:** Compute recipient hash fingerprints against the European Commission Peppol SML DNS root to resolve live SMP URLs, dynamic AS4 inbound endpoints, and X.509 certificates.
4. **AS4 ebMS3 & WS-Security:** Encapsulate payloads inside signed ebMS3 envelopes and record transaction correlation IDs for Non-Repudiation of Receipt (NRR) audits.
