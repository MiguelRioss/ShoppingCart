package services.pdf

/**
 * Produces a visibly marked purchase-order template for layout previews and manual testing.
 *
 * Bracketed values are intentional placeholders and the result must never be released as a
 * live supplier order.
 */
object SamplePurchaseOrderPdfRequest {

    /** Creates the sample request, optionally using a different logo file. */
    fun create(
        logoPath: String = "src/main/resources/deferranti-logo.png"
    ): PurchaseOrderPdfRequest =
        PurchaseOrderPdfRequest(
            logoPath = logoPath,
            subtitle = "TEMPLATE EXAMPLE - NOT VALID FOR LIVE USE",
            purchaseOrderNumber = "[SYSTEM PO NUMBER]",
            purchaseOrderDate = "[SYSTEM DATE]",
            customerOrderNumber = "[WEB ORDER NUMBER]",
            requiredDespatchDate = "[CONFIRMED DATE]",
            purchaser =
                PurchaseOrderParty(
                    name = "De Ferranti Limited",
                    lines = listOf(
                        "South Park Studios, Suite 10",
                        "88 Peterborough Road",
                        "London SW6 3HH, United Kingdom",
                        "VAT: GB947801596",
                        "GB EORI: [VERIFIED NUMBER]"
                    )
                ),
            supplier =
                PurchaseOrderParty(
                    name = "Mondiconexus, Unipessoal, Lda",
                    lines = listOf(
                        "Rua da Vidoeira No. 1",
                        "3100-097 Albergaria dos Doze, Portugal",
                        "PT EORI: PT517666448",
                        "Supplier product ID: MC52"
                    )
                ),
            delivery =
                PurchaseOrderParty(
                    name = "[CHECKOUT CONSIGNEE]",
                    lines = listOf(
                        "[CHECKOUT DELIVERY ADDRESS]",
                        "Contact: [CHECKOUT CONTACT]",
                        "T: [CHECKOUT TELEPHONE]",
                        "E: [CHECKOUT EMAIL]"
                    )
                ),
            lines = listOf(
                PurchaseOrderLine(
                    productName =
                        "The Blues - Azure Tide MC52 - handmade glazed ceramic tiles",
                    specificationLines = listOf(
                        "Size 11 x 11 x 0.9 cm. Glaze variations as hand poured."
                    ),
                    area = "9.5 m2",
                    boxes = "19 boxes",
                    tiles = "760 tiles",
                    unitPrice = "EUR 100.00",
                    lineTotal = "EUR 950.00",
                    deFerrantiSku = "DEF-MON001-BLUES-MC52",
                    supplierProductId = "MC52"
                )
            ),
            totals =
                PurchaseOrderTotals(
                    subtotal = "EUR 950.00",
                    vat = "[SUPPLIER TAX TREATMENT]",
                    total = "[CALCULATED TOTAL]"
                ),
            packingLines = listOf(
                "Prepare 19 boxes on one pallet.",
                "Expected packed dimensions: 80 x 60 x 80 cm.",
                "Expected gross packed weight: 160 kg.",
                "Packaging cost stored for checkout: EUR 20.",
                "Do not invoice packaging twice."
            ),
            directShipmentInstructionLines = listOf(
                "Ship from Mondiconexus directly to the checkout consignee.",
                "Incoterm: DDP, Incoterms 2020.",
                "De Ferranti Limited is importer of record.",
                "No import charges are to be collected from the consignee."
            ),
            requiredDocumentLines = listOf(
                "Commercial invoice",
                "Packing list",
                "FedEx label and AWB",
                "Proof of despatch",
                "Origin evidence where preferential treatment is claimed"
            ),
            supplierConfirmationLines = listOf(
                "Quantity confirmed: [YES / EXCEPTION]",
                "Packing confirmed: [YES / EXCEPTION]",
                "Despatch date: [DATE]",
                "Confirmed by: [NAME]",
                "Date: [DATE]"
            ),
            authorisationLines = listOf(
                "Authorised by: [SYSTEM USER / NAME]",
                "Date: [DATE]",
                "Notes: [ORDER NOTES]"
            ),
            controlNote =
                "CONTROL NOTE: This purchase order is produced from the product backend and checkout order. " +
                        "Bracketed fields must be resolved before release. " +
                        "Supplier VAT treatment must be confirmed by De Ferranti's accountant " +
                        "and must not be inferred by the shipping API."
        )
}
