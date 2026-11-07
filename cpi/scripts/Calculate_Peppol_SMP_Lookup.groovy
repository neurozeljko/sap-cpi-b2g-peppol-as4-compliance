import com.sap.gateway.ip.core.customdev.util.Message
import java.security.MessageDigest

def Message processData(Message message) {
    String recipientPeppolId = (message.getProperty("Recipient_Peppol_ID") ?: "9930:DE987654321").toString().trim()
    String standardId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0::2.1"
    
    String scheme = "iso6523-actorid-upis"
    String normalizedParticipant = "${scheme}::${recipientPeppolId}".toLowerCase()

    MessageDigest md5Digest = MessageDigest.getInstance("MD5")
    byte[] md5Hash = md5Digest.digest(normalizedParticipant.getBytes("UTF-8"))
    
    String smlHostZone = "edelivery.tech.ec.europa.eu"
    String b32Hash = md5Hash.encodeHex().toString().take(32)
    String smlDomain = "b-${b32Hash}.${scheme}.${smlHostZone}"

    String resolvedSMPUrl = "https://smp.${recipientPeppolId.replace(':', '-')}.peppol-directory.eu"
    String dynamicAS4Endpoint = "${resolvedSMPUrl}/as4/inbound/billing/v3"
    String recipientCertificateCN = "CN=Peppol-AP-Public-${recipientPeppolId.replace(':', '-')}, O=European Public Sector, C=DE"

    message.setProperty("Peppol_SML_Domain", smlDomain)
    message.setProperty("Resolved_SMP_URL", resolvedSMPUrl)
    message.setProperty("Dynamic_AS4_Endpoint", dynamicAS4Endpoint)
    message.setProperty("Recipient_Certificate_CN", recipientCertificateCN)
    message.setProperty("Peppol_Document_ID", standardId)

    message.setHeader("X-Peppol-SML-DNS", smlDomain)
    message.setHeader("X-Peppol-Target-Endpoint", dynamicAS4Endpoint)

    return message
}
