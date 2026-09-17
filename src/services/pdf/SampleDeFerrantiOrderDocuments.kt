package services.pdf

object SampleDeFerrantiOrderDocuments {

    fun create(
        logoPath: String = "src/main/resources/deferranti-logo.png"
    ): DeFerrantiOrderDocuments =
        DeFerrantiOrderDocuments(
            branding =
                DocumentBranding(
                    logoPath = logoPath,
                    subtitle = "TEMPLATE EXAMPLE - NOT VALID FOR LIVE USE"
                ),
            references =
                DocumentReferences(
                    purchaseOrderNumber = "[SYSTEM PO NUMBER]",
                    purchaseOrderDate = "[SYSTEM DATE]",
                    customerOrderNumber = "[WEB ORDER NUMBER]",
                    requiredDespatchDate = "[CONFIRMED DATE]",
                    packingListNumber = "[SYSTEM PACKING LIST NUMBER]",
                    packingDate = "[FULFILMENT DATE]",
                    deliveryNoteNumber = "[SYSTEM DELIVERY NOTE NUMBER]",
                    deliveryDate = "[FEDEX DELIVERY DATE]",
                    invoiceNumber = "[INVOICE NUMBER]"
                ),
            parties =
                DocumentParties(
                    purchaser = deFerranti(),
                    supplier = mondiconexus("Rua da Vidoeira No. 1", "3100-097 Albergaria dos Doze, Portugal"),
                    shipFrom = mondiconexus("Avenida dos Bombeiros Voluntarios No. 36", "3450-122 Mortagua, Portugal"),
                    deliverTo =
                        DocumentParty(
                            name = "[CHECKOUT LEGAL NAME]",
                            addressLines = listOf("[CHECKOUT DELIVERY ADDRESS]"),
                            contactName = "[CHECKOUT CONTACT]",
                            phone = "[CHECKOUT TELEPHONE]",
                            email = "[CHECKOUT EMAIL]",
                            customerType = "[INDIVIDUAL / BUSINESS]"
                        ),
                    importerOfRecord = deFerranti(eoriOnly = true),
                    seller = deFerranti()
                ),
            goods = listOf(
                DocumentGoodsLine(
                    productName = "The Blues - Azure Tide MC52 - handmade glazed ceramic tiles",
                    specificationLines = listOf(
                        "Size 11 x 11 x 0.9 cm. Glaze variations as hand poured."
                    ),
                    area = "9.5 m2",
                    boxes = "19 boxes",
                    tiles = "760 tiles",
                    unitPrice = "EUR 100.00",
                    lineTotal = "EUR 950.00",
                    deFerrantiSku = "DEF-MON001-BLUES-MC52",
                    supplierProductId = "MC52",
                    hsCode = "6907 23 00",
                    originCountry = "Portugal",
                    netWeight = "[NET]",
                    grossWeight = "160 kg",
                    packageDescription = "1 pallet",
                    standardisedProductId = "NO"
                )
            ),
            totals =
                DocumentTotals(
                    supplierSubtotal = "EUR 950.00",
                    vatTreatment = "[SUPPLIER TAX TREATMENT]",
                    purchaseOrderTotal = "[CALCULATED TOTAL]"
                ),
            packing =
                PackingDetails(
                    packageType = "Pallet",
                    packageCount = "1 of 1",
                    dimensions = "80 x 60 x 80 cm",
                    grossWeight = "160 kg",
                    contents = "19 boxes / 760 MC52 tiles / 9.5 m2",
                    packingCost = "EUR 20",
                    handlingLines = listOf(
                        "Fragile handmade ceramic tiles",
                        "Keep pallet upright and dry",
                        "Do not stack unless the confirmed packing specification permits",
                        "Check pallet wrap, corners and labels before collection"
                    )
                ),
            shipment =
                ShipmentDetails(
                    courier = "FedEx",
                    service = "[FEDEX API RESPONSE]",
                    trackingNumber = "[TRACKING NUMBER]",
                    awb = "[FEDEX API RESPONSE]",
                    purpose = "Commercial sale",
                    incoterm = "DDP, Incoterms 2020",
                    deliveryTerms = "DDP - Incoterms 2020"
                ),
            checks =
                DocumentChecks(
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
                    packedByLines = listOf(
                        "Name: __________________________",
                        "Signature: _______________________",
                        "Date and time: ___________________"
                    ),
                    finalCheckLines = listOf(
                        "19 boxes confirmed: [YES / NO]",
                        "760 tiles confirmed: [YES / NO]",
                        "Pallet measurements confirmed: [YES / NO]",
                        "Gross weight confirmed: [YES / NO]",
                        "FedEx label attached: [YES / NO]"
                    ),
                    deliveryConditionLines = listOf(
                        "Please inspect the external pallet and packaging before acceptance.",
                        "Pallet received intact: [YES / NO]",
                        "Visible damage: [YES / NO]",
                        "Number of packages received: [NUMBER]",
                        "Photographs taken if damaged: [YES / NO]"
                    ),
                    deliveryExceptionLines = listOf(
                        "________________________________________________",
                        "________________________________________________",
                        "________________________________________________",
                        "If damaged or incomplete, record the exception with the FedEx driver",
                        "and notify De Ferranti promptly."
                    ),
                    receivedByLines = listOf(
                        "Name: _____________________________________",
                        "Company, if applicable: ______________________",
                        "Signature: __________________________________",
                        "Date: ______________________________________",
                        "Time: ______________________________________"
                    ),
                    courierReceiptLines = listOf(
                        "Driver name or ID: ___________________________",
                        "FedEx delivery reference: _____________________",
                        "Signature, if required: ________________________",
                        "Date and time: _______________________________"
                    )
                ),
            notes =
                DocumentNotes(
                    requiredDocumentLines = listOf(
                        "Commercial invoice",
                        "Packing list",
                        "FedEx label and AWB",
                        "Proof of despatch",
                        "Origin evidence where preferential treatment is claimed"
                    ),
                    b2bB2cLines = listOf(
                        "One document format applies to both.",
                        "For B2B, VAT number is stored on the commercial invoice.",
                        "The packing list contains no prices.",
                        "De Ferranti remains importer of record under DDP."
                    ),
                    deliveryImportantLines = listOf(
                        "This delivery note confirms receipt only.",
                        "It does not amend the sales contract or confirm concealed goods are free from damage.",
                        "Prices are intentionally omitted.",
                        "De Ferranti Limited remains importer of record under DDP."
                    ),
                    documentMatchLines = listOf(
                        "Order: [WEB ORDER NUMBER]",
                        "Commercial invoice: [INVOICE NUMBER]",
                        "Packing list: [PACKING LIST NUMBER]",
                        "Purchase order: [PO NUMBER]",
                        "FedEx tracking: [TRACKING NUMBER]"
                    ),
                    purchaseOrderControlNote =
                        "CONTROL NOTE: This purchase order is produced from the product backend and checkout order. " +
                                "Bracketed fields must be resolved before release. Supplier VAT treatment must be confirmed " +
                                "by De Ferranti's accountant and must not be inferred by the shipping API.",
                    packingListControlNote =
                        "CONTROL NOTE: The final packing list must match the commercial invoice, FedEx commodity data, " +
                                "AWB and physical shipment. The net product weight remains a required backend value and " +
                                "must not be replaced by the 160 kg gross packed weight.",
                    deliveryNoteControlNote =
                        "CONTROL NOTE: The generated delivery note must match the checkout order, packing list, " +
                                "commercial invoice, FedEx shipment and physical delivery. Bracketed fields must be resolved before issue."
                )
        )

    private fun deFerranti(
        eoriOnly: Boolean = false
    ): DocumentParty =
        DocumentParty(
            name = "De Ferranti Limited",
            addressLines = listOf(
                "South Park Studios, Suite 10",
                "88 Peterborough Road",
                "London SW6 3HH, United Kingdom"
            ),
            vatNumber = if (eoriOnly) null else "GB947801596",
            eoriNumber = "GB EORI: [VERIFIED NUMBER]"
        )

    private fun mondiconexus(
        line1: String,
        line2: String
    ): DocumentParty =
        DocumentParty(
            name = "Mondiconexus, Unipessoal, Lda",
            addressLines = listOf(line1, line2),
            eoriNumber = "PT517666448"
        )
}
