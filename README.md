# Mission-Critical B2G E-Invoicing: Peppol AS4, Dynamic SML/SMP Discovery & ISO Schematron Compliance

[![SAP Integration Suite](https://img.shields.io/badge/SAP%20Integration%20Suite-Cloud%20Integration-0A85EA?style=for-the-badge&logo=sap&logoColor=white)](https://www.sap.com)
[![B2G Standard](https://img.shields.io/badge/Standard-EN16931%20%7C%20X--Rechnung%203.0-darkgreen?style=for-the-badge)](https://ec.europa.eu)
[![Peppol Network](https://img.shields.io/badge/Network-Peppol%20BIS%20Billing%203.0-blue?style=for-the-badge)](https://peppol.org)
[![eDelivery Protocol](https://img.shields.io/badge/Protocol-AS4%20ebMS3%20%7C%20WS--Security-red?style=for-the-badge)](https://oasis-open.org)
[![Clean Core](https://img.shields.io/badge/Clean%20Core-Zero%20ERP%20Modification-success?style=for-the-badge&logo=sap&logoColor=white)](https://www.sap.com)

---

## Executive Summary

European Union member states mandate structured electronic invoicing (B2G/B2B) under Directive 2014/55/EU and national mandates (e.g., German E-Rechnungsverordnung / Growth Opportunities Act). Transmitting non-compliant payloads or relying on static point-to-point URLs triggers immediate tax authority rejections, financial penalties, and delayed cash flows.

This architecture delivers a **Production-Grade Global B2G Compliance Engine**:
1. **Canonical Transformation:** Ingests S/4HANA Cloud Billing records via OData V4 and transforms them into strict **CEN/EN16931 UBL 2.1** XML using precision BigDecimals for zero rounding variance.
2. **Pre-flight Schematron SVRL Audit:** Validates business rules, Leitweg-IDs, and mandatory tax group equations before network exit. Non-compliant documents are isolated into a quarantined audit queue.
3. **Dynamic Discovery (SML & SMP):** Resolves public sector recipient metadata dynamically via European Commission **Service Metadata Locator (SML)** DNS hashing (MD5/Base32) and **Service Metadata Publisher (SMP)** REST endpoints, querying live X.509 certificates and target AS4 Access Points.
4. **AS4 ebMS3 & WS-Security Packaging:** Encapsulates legal payloads into OASIS ebMS3 envelopes with SHA-256 digital signatures, enforcing Non-Repudiation of Receipt (NRR).

---

## Architectural Topology

```
[SAP S/4HANA Cloud (Billing / DRC)]
          │
          │ OData V4 Billing Ingestion (/b2g/peppol/invoice/submit)
          ▼
[SAP Integration Suite: IF_B2G_Peppol_Dynamic_Compliance_Engine]
  │
  ├── 1. Context Initialization (CM_Init_B2G_Context):
  │    └─ Injects correlation headers, compliance profile & raw audit state
  │
  ├── 2. Canonicalization Engine (Transform_To_EN16931_UBL.groovy):
  │    ├─ Generates Oasis UBL 2.1 compliant XML
  │    ├─ Injects CustomizationID: urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0
  │    └─ BigDecimal line-item & tax category calculation (zero rounding error)
  │
  ├── 3. Pre-Flight Schematron Audit (Validate_Schematron_EN16931.groovy):
  │    ├─ Evaluates CEN/EN16931 & German national rules (BR-DE-01, BR-DE-15, BR-CO-15)
  │    └─ Emits Schematron Validation Report Language (SVRL) discrepancy report
  │
  ├── 4. Tax Audit Router (Router_Tax_Audit):
  │    ├─ [Tax Rules Violated] ──► CM_Reject_Tax_Violation (HTTP 422 + SVRL Report) ──► End 1
  │    └─ [Audit Passed: ValidationPassed = 'true'] ──► Continue
  │
  ├── 5. Dynamic Peppol Discovery Engine (Calculate_Peppol_SMP_Lookup.groovy):
  │    ├─ Normalizes participant: iso6523-actorid-upis::9930:<vat>
  │    ├─ Computes SML lookup: b-<md5hash>.iso6523-actorid-upis.edelivery.tech.ec.europa.eu
  │    └─ Resolves authorized SMP URL, dynamic AS4 endpoint & receiver X.509 certificate
  │
  ├── 6. Secure Transport Packaging (Package_AS4_ebMS3_Envelope):
  │    ├─ Injects OASIS ebMS3 CollaborationInfo, Action & Service headers
  │    └─ Enforces WS-Security packaging for AS4 Peppol Access Point handoff
  │
  └── 7. Success Acknowledgement (CM_Peppol_Success_Ack) ──► End 2
       └─ Returns compliance audit record, SML resolution traces & message tracking ID
```

---

## Technical Specifications

| Architectural Dimension | Legacy EDI / Static AS2 | Dynamic Peppol B2G Engine (AS4 + SML/SMP) |
| :--- | :--- | :--- |
| **Routing Protocol** | Hardcoded partner URLs & manual IP whitelisting | Dynamic European SML/SMP metadata lookup via DNS/REST |
| **Payload Standard** | Proprietary EDIFACT / IDoc | Strict CEN/EN16931 & national profiles (X-Rechnung 3.0) |
| **Tax Validation** | Post-processing ERP validation (late error detection) | In-flight ISO Schematron SVRL pre-flight audit |
| **Security Layer** | Point-to-point TLS / PGP | OASIS AS4 ebMS3, WS-Security, Mutual TLS & X.509 Signatures |
| **Receipt Tracking** | Classical MDN / manual verification | Cryptographic Non-Repudiation of Receipt (NRR) storage |
| **Audit Compliance** | Fragmented server logs | Immutable WORM-compliant audit envelope with correlation ID |

---

## Target B2G Response Envelope (HTTP 200 OK)

```json
{
  "b2gComplianceStatus": "DISPATCHED_TO_PEPPOL_NETWORK",
  "invoiceMetadata": {
    "invoiceNumber": "INV-2026-DE-99410",
    "buyerReference": "04011000-12345-67",
    "totalAmountEUR": 37128.00,
    "complianceStandard": "PEPPOL_BIS_BILLING_3_0_EN16931"
  },
  "dynamicDiscovery": {
    "smlLookupDomain": "b-4a8b7921c5f3...iso6523-actorid-upis.edelivery.tech.ec.europa.eu",
    "resolvedSMPService": "[https://smp.9930-de987654321.peppol-directory.eu](https://smp.9930-de987654321.peppol-directory.eu)",
    "targetAS4AccessPoint": "[https://smp.9930-de987654321.peppol-directory.eu/as4/inbound/billing/v3](https://smp.9930-de987654321.peppol-directory.eu/as4/inbound/billing/v3)",
    "recipientKeyIdentity": "CN=Peppol-AP-Public-9930-DE987654321, O=European Public Sector, C=DE"
  },
  "ebMS3Packaging": {
    "protocol": "AS4 / OASIS ebMS3 Profile",
    "messageTrackingId": "AS4-MSG-AF_x9910_20261107_9841",
    "nonRepudiationReceiptRequired": true,
    "legalArchivalStatus": "COMMITTED_TO_WORM_AUDIT_STORE"
  },
  "timestamp": "2026-11-07T21:10:00Z"
}
```

---

## Repository Structure

```
├── README.md                                             # Enterprise architecture & operations runbook
├── ADR-002-Peppol-Dynamic-Discovery-And-Schematron.md    # Architecture Decision Record
├── IF_B2G_Peppol_Dynamic_Compliance_Engine.zip           # Production iFlow bundle export
├── cpi/
│   └── scripts/
│       ├── Transform_To_EN16931_UBL.groovy               # Canonical UBL 2.1 XML builder
│       ├── Validate_Schematron_EN16931.groovy            # ISO Schematron SVRL pre-flight rules engine
│       ├── Calculate_Peppol_SMP_Lookup.groovy            # Dynamic SML DNS & SMP metadata resolver
│       └── Format_B2G_Tax_Violation_DLQ.groovy           # Tax discrepancy incident handler
├── tests/
│   ├── sample_valid_xrechnung_invoice.json               # Compliant B2G test invoice
│   └── sample_invalid_tax_discrepancy.json               # Discrepancy test triggering SVRL rejection
└── .gitignore                                            # Git exclusion rules
```

---

## Verification & Deployment Runbook

### 1. Inbound Endpoint Verification
Verify runtime status in **Monitor** ──► **Manage Integration Content**:
```text
https://<tenant-runtime>.cfapps.<region>[.hana.ondemand.com/http/b2g/peppol/invoice/submit](https://.hana.ondemand.com/http/b2g/peppol/invoice/submit)
```

### 2. Dispatched cURL Test (Happy Path)
Execute an authorized POST request using compliant invoice data:

```bash
curl -X POST \
  'https://<tenant-runtime>.cfapps.<region>[.hana.ondemand.com/http/b2g/peppol/invoice/submit](https://.hana.ondemand.com/http/b2g/peppol/invoice/submit)' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <BTP_OAUTH_TOKEN>' \
  --data-binary '@tests/sample_valid_xrechnung_invoice.json'
```

### 3. Monitoring Trace
* Inspect execution in **Monitor Message Processing**.
* Verify properties `ValidationPassed=true`, `Total_Payable_EUR`, and `Dynamic_AS4_Endpoint`.
* Confirm HTTP 200 response contains complete Peppol SML resolution trace.

---

## Author

**Željko Popov**  
*Integration Architect | SAP Integration Suite & Clean Core Specialist*  
[GitHub Repositories](https://github.com/neurozeljko)
