import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder
import java.math.BigDecimal
import java.math.RoundingMode

def Message processData(Message message) {
    String body = message.getBody(String.class)
    def jsonSlurper = new JsonSlurper()
    def inv = jsonSlurper.parseText(body)

    String invoiceId = (inv.BillingDocument ?: inv.InvoiceID ?: "INV-2026-0001").toString().trim()
    String issueDate = (inv.IssueDate ?: new Date().format("yyyy-MM-dd")).toString().trim()
    String currency = (inv.Currency ?: "EUR").toString().trim().toUpperCase()
    
    String sellerId = (inv.Seller?.PeppolID ?: "9930:DE123456789").toString().trim()
    String buyerId = (inv.Buyer?.PeppolID ?: "9930:DE987654321").toString().trim()
    String buyerRef = (inv.BuyerReference ?: inv.LeitwegID ?: "04011000-12345-67").toString().trim()

    BigDecimal lineExtensionAmount = BigDecimal.ZERO
    BigDecimal taxExclusiveAmount = BigDecimal.ZERO
    BigDecimal taxInclusiveAmount = BigDecimal.ZERO
    BigDecimal taxTotalAmount = BigDecimal.ZERO

    def items = inv.InvoiceLines instanceof List ? inv.InvoiceLines : [inv.InvoiceLines]

    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    xml.'Invoice'(
        'xmlns': 'urn:oasis:names:specification:ubl:schema:xsd:Invoice-2',
        'xmlns:cac': 'urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2',
        'xmlns:cbc': 'urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2'
    ) {
        'cbc:CustomizationID'('urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0')
        'cbc:ProfileID'('urn:fdc:peppol.eu:2017:poacc:billing:01:1.0')
        'cbc:ID'(invoiceId)
        'cbc:IssueDate'(issueDate)
        'cbc:InvoiceTypeCode'('380')
        'cbc:DocumentCurrencyCode'(currency)
        'cbc:BuyerReference'(buyerRef)

        'cac:AccountingSupplierParty' {
            'cac:Party' {
                'cbc:EndpointID'('schemeID': '0088', sellerId)
                'cac:PartyName' {
                    'cbc:Name'(inv.Seller?.Name ?: "Global Enterprise Solutions SE")
                }
                'cac:PostalAddress' {
                    'cbc:StreetName'(inv.Seller?.Street ?: "Willy-Brandt-Strasse 1")
                    'cbc:CityName'(inv.Seller?.City ?: "Berlin")
                    'cbc:PostalZone'(inv.Seller?.PostalCode ?: "10557")
                    'cac:Country' {
                        'cbc:IdentificationCode'(inv.Seller?.CountryCode ?: "DE")
                    }
                }
                'cac:PartyTaxScheme' {
                    'cbc:CompanyID'(inv.Seller?.VATNumber ?: "DE123456789")
                    'cac:TaxScheme' {
                        'cbc:ID'('VAT')
                    }
                }
                'cac:PartyLegalEntity' {
                    'cbc:RegistrationName'(inv.Seller?.LegalName ?: "Global Enterprise Solutions SE")
                    'cbc:CompanyID'(sellerId)
                }
            }
        }

        'cac:AccountingCustomerParty' {
            'cac:Party' {
                'cbc:EndpointID'('schemeID': '0088', buyerId)
                'cac:PartyName' {
                    'cbc:Name'(inv.Buyer?.Name ?: "Bundesministerium fuer Digitales")
                }
                'cac:PostalAddress' {
                    'cbc:StreetName'(inv.Buyer?.Street ?: "Invalidenstrasse 44")
                    'cbc:CityName'(inv.Buyer?.City ?: "Berlin")
                    'cbc:PostalZone'(inv.Buyer?.PostalCode ?: "10115")
                    'cac:Country' {
                        'cbc:IdentificationCode'(inv.Buyer?.CountryCode ?: "DE")
                    }
                }
                'cac:PartyTaxScheme' {
                    'cbc:CompanyID'(inv.Buyer?.VATNumber ?: "DE987654321")
                    'cac:TaxScheme' {
                        'cbc:ID'('VAT')
                    }
                }
            }
        }

        'cac:PaymentMeans' {
            'cbc:PaymentMeansCode'('58')
            'cac:PayeeFinancialAccount' {
                'cbc:ID'(inv.Payment?.IBAN ?: "DE89370400440532013000")
            }
        }

        items.eachWithIndex { item, index ->
            BigDecimal qty = new BigDecimal((item.Quantity ?: 1.0).toString()).setScale(2, RoundingMode.HALF_UP)
            BigDecimal unitPrice = new BigDecimal((item.UnitPrice ?: 0.0).toString()).setScale(2, RoundingMode.HALF_UP)
            BigDecimal lineTotal = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)
            BigDecimal vatRate = new BigDecimal((item.TaxRate ?: 19.0).toString())

            lineExtensionAmount = lineExtensionAmount.add(lineTotal)
            BigDecimal vatAmount = lineTotal.multiply(vatRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
            taxTotalAmount = taxTotalAmount.add(vatAmount)

            'cac:InvoiceLine' {
                'cbc:ID'(index + 1)
                'cbc:InvoicedQuantity'('unitCode': (item.UnitOfMeasure ?: 'C62'), qty.toString())
                'cbc:LineExtensionAmount'('currencyID': currency, lineTotal.toString())
                'cac:Item' {
                    'cbc:Name'(item.Description ?: "Enterprise Cloud Consulting Service")
                    'cac:ClassifiedTaxCategory' {
                        'cbc:ID'('S')
                        'cbc:Percent'(vatRate.toString())
                        'cac:TaxScheme' {
                            'cbc:ID'('VAT')
                        }
                    }
                }
                'cac:Price' {
                    'cbc:PriceAmount'('currencyID': currency, unitPrice.toString())
                }
            }
        }

        taxExclusiveAmount = lineExtensionAmount
        taxInclusiveAmount = taxExclusiveAmount.add(taxTotalAmount)

        'cac:TaxTotal' {
            'cbc:TaxAmount'('currencyID': currency, taxTotalAmount.toString())
            'cac:TaxSubtotal' {
                'cbc:TaxableAmount'('currencyID': currency, taxExclusiveAmount.toString())
                'cbc:TaxAmount'('currencyID': currency, taxTotalAmount.toString())
                'cac:TaxCategory' {
                    'cbc:ID'('S')
                    'cbc:Percent'('19.00')
                    'cac:TaxScheme' {
                        'cbc:ID'('VAT')
                    }
                }
            }
        }

        'cac:LegalMonetaryTotal' {
            'cbc:LineExtensionAmount'('currencyID': currency, lineExtensionAmount.toString())
            'cbc:TaxExclusiveAmount'('currencyID': currency, taxExclusiveAmount.toString())
            'cbc:TaxInclusiveAmount'('currencyID': currency, taxInclusiveAmount.toString())
            'cbc:PayableAmount'('currencyID': currency, taxInclusiveAmount.toString())
        }
    }

    message.setProperty("Invoice_ID", invoiceId)
    message.setProperty("Recipient_Peppol_ID", buyerId)
    message.setProperty("Seller_Peppol_ID", sellerId)
    message.setProperty("Total_Payable_EUR", taxInclusiveAmount.doubleValue())
    message.setProperty("Buyer_Reference", buyerRef)

    message.setBody(writer.toString())
    message.setHeader("Content-Type", "application/xml")

    return message
}
