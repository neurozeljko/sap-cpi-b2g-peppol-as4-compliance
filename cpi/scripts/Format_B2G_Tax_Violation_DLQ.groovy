import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant
import java.util.UUID

def Message processData(Message message) {
    def properties = message.getProperties()
    def ex = properties.get("CamelExceptionCaught")
    
    String rawError = ex ? ex.getMessage() : "B2G Peppol AS4 Packaging or SML Resolution Failure"
    String rawPayload = properties.get("Original_Raw_Payload") ? properties.get("Original_Raw_Payload").toString() : "{}"
    String corrId = (properties.get("CorrelationID") ?: UUID.randomUUID().toString()).toString()
    String invId = (properties.get("Invoice_ID") ?: "UNKNOWN_DOC").toString()

    def dlqEnvelope = [
        "B2G_Incident_Audit": [
            "SeverityLevel"      : "CRITICAL_TAX_COMPLIANCE_BREACH",
            "Timestamp"          : Instant.now().toString(),
            "CorrelationId"      : corrId,
            "FailedDocument"     : invId,
            "TargetNetwork"      : "PEPPOL_B2G_EUROPE"
        ],
        "Diagnostics": [
            "RootCause"          : rawError,
            "MitigationAction"   : "Verify participant certificate validity in BTP Truststore or check S/4HANA DRC billing structure"
        ],
        "QuarantinedPayload"     : rawPayload
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(dlqEnvelope)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-Audit-Status", "QUARANTINED_TO_TAX_AUDIT_LOG")
    
    return message
}
