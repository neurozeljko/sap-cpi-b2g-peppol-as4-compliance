import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlSlurper
import groovy.json.JsonOutput
import java.math.BigDecimal

def Message processData(Message message) {
    String xmlBody = message.getBody(String.class)
    def invoice = new XmlSlurper(false, false).parseText(xmlBody)

    def violations = []

    String customization = invoice.CustomizationID.text()
    if (!customization.contains("urn:cen.eu:en16931:2017")) {
        violations.add([
            "RuleID"    : "BR-DE-01",
            "Severity"  : "FATAL",
            "Context"   : "/Invoice/cbc:CustomizationID",
            "Message"   : "Invoice does not specify a compliant EN16931 / X-Rechnung standard identifier"
        ])
    }

    String buyerRef = invoice.BuyerReference.text()
    if (!buyerRef || buyerRef.length() < 5) {
        violations.add([
            "RuleID"    : "BR-DE-15",
            "Severity"  : "FATAL",
            "Context"   : "/Invoice/cbc:BuyerReference",
            "Message"   : "Mandatory public sector Leitweg-ID / BuyerReference missing or malformed"
        ])
    }

    try {
        BigDecimal taxExclusive = new BigDecimal(invoice.LegalMonetaryTotal.TaxExclusiveAmount.text())
        BigDecimal taxAmount = new BigDecimal(invoice.TaxTotal.TaxAmount.text())
        BigDecimal taxInclusive = new BigDecimal(invoice.LegalMonetaryTotal.TaxInclusiveAmount.text())

        if (taxExclusive.add(taxAmount).compareTo(taxInclusive) != 0) {
            violations.add([
                "RuleID"    : "BR-CO-15",
                "Severity"  : "FATAL",
                "Context"   : "/Invoice/cac:LegalMonetaryTotal",
                "Message"   : "Invoice monetary totals are mathematically inconsistent (TaxExclusive + TaxTotal != TaxInclusive)"
            ])
        }
    } catch (Exception ex) {
        violations.add([
            "RuleID"    : "BR-CO-01",
            "Severity"  : "FATAL",
            "Context"   : "/Invoice/cac:LegalMonetaryTotal",
            "Message"   : "Monetary total elements cannot be parsed as valid numeric amounts"
        ])
    }

    String supplierEndpoint = invoice.AccountingSupplierParty.Party.EndpointID.text()
    String customerEndpoint = invoice.AccountingCustomerParty.Party.EndpointID.text()
    if (!supplierEndpoint || !customerEndpoint) {
        violations.add([
            "RuleID"    : "BR-PEPPOL-01",
            "Severity"  : "FATAL",
            "Context"   : "/Invoice/cac:AccountingSupplierParty/Party/EndpointID",
            "Message"   : "Peppol electronic address (EndpointID) missing for supplier or recipient"
        ])
    }

    boolean isPassed = violations.isEmpty()

    def svrlReport = [
        "SchematronAuditReport": [
            "ValidationStandard" : "CEN/EN16931_Schematron_v3.0",
            "ValidationStatus"   : isPassed ? "COMPLIANT" : "REJECTED_TAX_VIOLATION",
            "TotalViolations"    : violations.size(),
            "EvaluatedDocument"  : invoice.ID.text(),
            "AuditTimestamp"     : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            "Discrepancies"      : violations
        ]
    ]

    message.setProperty("ValidationPassed", isPassed.toString())
    message.setProperty("SVRL_Violations_Count", violations.size())
    message.setProperty("SVRL_Audit_Report", JsonOutput.toJson(svrlReport))

    if (!isPassed) {
        message.setHeader("X-Tax-Compliance-Status", "FAILED")
    } else {
        message.setHeader("X-Tax-Compliance-Status", "PASSED_EN16931")
    }

    return message
}
