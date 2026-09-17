package services.pdf

object TestDeFerrantiOrderDocuments {

    fun create(
        logoPath: String = "src/main/resources/deferranti-logo.png"
    ): DeFerrantiOrderDocuments =

        DeFerrantiOrderDocuments(

            // ---------------------------------------------------------
            // BRANDING
            // ---------------------------------------------------------

            branding =
                DocumentBranding(
                    logoPath = logoPath,
                    subtitle = "TEST ORDER - NOT VALID FOR LIVE USE"
                ),

            // ---------------------------------------------------------
            // DOCUMENT REFERENCES
            // ---------------------------------------------------------

            references =
                DocumentReferences(
                    purchaseOrderNumber = "PO-2026-000001",
                    purchaseOrderDate = "17/09/2026",

                    customerOrderNumber = "DF-2026-000001",
                    requiredDespatchDate = "24/09/2026",

                    packingListNumber = "PL-2026-000001",
                    packingDate = "24/09/2026",

                    deliveryNoteNumber = "DN-2026-000001",
                    deliveryDate = "28/09/2026",

                    invoiceNumber = "INV-2026-000001"
                ),

            // ---------------------------------------------------------
            // PARTIES
            // ---------------------------------------------------------

            parties =
                DocumentParties(

                    purchaser =
                        DocumentParty(
                            name = "De Ferranti Limited",
                            addressLines =
                                listOf(
                                    "South Park Studios, Suite 10",
                                    "88 Peterborough Road",
                                    "London SW6 3HH",
                                    "United Kingdom"
                                ),
                            vatNumber = "GB947801596",
                            eoriNumber = "TEST-GB-EORI"
                        ),

                    supplier =
                        DocumentParty(
                            name = "Mondiconexus, Unipessoal, Lda",
                            addressLines =
                                listOf(
                                    "Rua da Vidoeira No. 1",
                                    "3100-097 Albergaria dos Doze",
                                    "Portugal"
                                ),
                            eoriNumber = "PT517666448"
                        ),

                    shipFrom =
                        DocumentParty(
                            name = "Mondiconexus, Unipessoal, Lda",
                            addressLines =
                                listOf(
                                    "Avenida dos Bombeiros Voluntarios No. 36",
                                    "3450-122 Mortagua",
                                    "Portugal"
                                ),
                            eoriNumber = "PT517666448"
                        ),

                    /*
                     * Fake checkout customer.
                     *
                     * Later this comes directly from the checkout.
                     */
                    deliverTo =
                        DocumentParty(
                            name = "John Smith",
                            addressLines =
                                listOf(
                                    "24 Test Street",
                                    "London",
                                    "SW1A 1AA",
                                    "United Kingdom"
                                ),
                            contactName = "John Smith",
                            phone = "+44 7700 900000",
                            email = "test-customer@example.com",
                            customerType = "Individual"
                        ),

                    importerOfRecord =
                        DocumentParty(
                            name = "De Ferranti Limited",
                            addressLines =
                                listOf(
                                    "South Park Studios, Suite 10",
                                    "88 Peterborough Road",
                                    "London SW6 3HH",
                                    "United Kingdom"
                                ),
                            eoriNumber = "TEST-GB-EORI"
                        ),

                    seller =
                        DocumentParty(
                            name = "De Ferranti Limited",
                            addressLines =
                                listOf(
                                    "South Park Studios, Suite 10",
                                    "88 Peterborough Road",
                                    "London SW6 3HH",
                                    "United Kingdom"
                                ),
                            vatNumber = "GB947801596",
                            eoriNumber = "TEST-GB-EORI"
                        )
                ),

            // ---------------------------------------------------------
            // GOODS
            // ---------------------------------------------------------

            goods =
                listOf(

                    DocumentGoodsLine(

                        productName =
                            "The Blues - Azure Tide MC52 - handmade glazed ceramic tiles",

                        specificationLines =
                            listOf(
                                "Size 11 x 11 x 0.9 cm.",
                                "Glaze variations as hand poured.",
                                "Handmade glazed ceramic tiles."
                            ),

                        area = "9.5 m2",

                        boxes = "19 boxes",

                        tiles = "760 tiles",

                        /*
                         * Supplier purchase price.
                         *
                         * This is NOT necessarily the customer retail price.
                         */
                        unitPrice = "EUR 100.00",

                        lineTotal = "EUR 950.00",

                        unitPriceLabel = "per m2",

                        deFerrantiSku =
                            "DEF-MON001-BLUES-MC52",

                        supplierProductId =
                            "MC52",

                        hsCode =
                            "6907 23 00",

                        originCountry =
                            "Portugal",

                        /*
                         * Still needs to eventually come from the
                         * real product / packing data.
                         */
                        netWeight =
                            "150 kg",

                        grossWeight =
                            "160 kg",

                        packageDescription =
                            "1 pallet",

                        standardisedProductId =
                            "NO"
                    )
                ),

            // ---------------------------------------------------------
            // PURCHASE ORDER TOTALS
            // ---------------------------------------------------------

            totals =
                DocumentTotals(

                    supplierSubtotal =
                        "EUR 950.00",

                    /*
                     * We should NOT invent the supplier VAT treatment.
                     * This eventually needs to come from your accounting rules.
                     */
                    vatTreatment =
                        "VAT treatment to be confirmed",

                    purchaseOrderTotal =
                        "EUR 950.00"
                ),

            // ---------------------------------------------------------
            // PACKING
            // ---------------------------------------------------------

            packing =
                PackingDetails(

                    packageType =
                        "Pallet",

                    packageCount =
                        "1 of 1",

                    dimensions =
                        "80 x 60 x 80 cm",

                    grossWeight =
                        "160 kg",

                    contents =
                        "19 boxes / 760 MC52 tiles / 9.5 m2",

                    packingCost =
                        "EUR 20.00",

                    handlingLines =
                        listOf(
                            "Fragile handmade ceramic tiles",
                            "Keep pallet upright and dry",
                            "Do not stack unless confirmed packing specification permits",
                            "Check pallet wrap, corners and labels before FedEx collection"
                        )
                ),

            // ---------------------------------------------------------
            // FEDEX / SHIPMENT
            // ---------------------------------------------------------

            shipment =
                ShipmentDetails(

                    courier =
                        "FedEx",

                    /*
                     * Fake values for PDF testing.
                     *
                     * Later these come directly from FedEx.
                     */
                    service =
                        "FedEx International Priority",

                    trackingNumber =
                        "TEST123456789",

                    awb =
                        "TEST-AWB-123456789",

                    purpose =
                        "Commercial sale",

                    incoterm =
                        "DDP, Incoterms 2020",

                    deliveryTerms =
                        "DDP - Incoterms 2020"
                ),

            // ---------------------------------------------------------
            // CHECK BOXES / SIGNATURE SECTIONS
            // ---------------------------------------------------------

            checks =
                DocumentChecks(

                    supplierConfirmationLines =
                        listOf(
                            "Quantity confirmed: YES / EXCEPTION",
                            "Packing confirmed: YES / EXCEPTION",
                            "Despatch date: __________________",
                            "Confirmed by: __________________",
                            "Date: __________________"
                        ),

                    authorisationLines =
                        listOf(
                            "Authorised by: __________________",
                            "Date: __________________",
                            "Notes: __________________________"
                        ),

                    packedByLines =
                        listOf(
                            "Name: __________________________",
                            "Signature: _____________________",
                            "Date and time: _________________"
                        ),

                    finalCheckLines =
                        listOf(
                            "19 boxes confirmed: YES / NO",
                            "760 tiles confirmed: YES / NO",
                            "Pallet measurements confirmed: YES / NO",
                            "Gross weight confirmed: YES / NO",
                            "FedEx label attached: YES / NO"
                        ),

                    deliveryConditionLines =
                        listOf(
                            "Please inspect the external pallet before acceptance.",
                            "Pallet received intact: YES / NO",
                            "Visible damage: YES / NO",
                            "Number of packages received: ______",
                            "Photographs taken if damaged: YES / NO"
                        ),

                    deliveryExceptionLines =
                        listOf(
                            "________________________________________",
                            "________________________________________",
                            "________________________________________",
                            "Record any visible damage with the FedEx driver."
                        ),

                    receivedByLines =
                        listOf(
                            "Name: __________________________",
                            "Company: _______________________",
                            "Signature: _____________________",
                            "Date: __________________________",
                            "Time: __________________________"
                        ),

                    courierReceiptLines =
                        listOf(
                            "Driver name / ID: ______________",
                            "FedEx reference: ________________",
                            "Signature: _____________________",
                            "Date and time: _________________"
                        )
                ),

            // ---------------------------------------------------------
            // DOCUMENT NOTES
            // ---------------------------------------------------------

            notes =
                DocumentNotes(

                    requiredDocumentLines =
                        listOf(
                            "Commercial invoice",
                            "Packing list",
                            "FedEx label and AWB",
                            "Proof of despatch",
                            "Origin evidence where required"
                        ),

                    b2bB2cLines =
                        listOf(
                            "One packing-list format applies to both.",
                            "For B2B, VAT information is held on the commercial invoice.",
                            "The packing list contains no prices.",
                            "De Ferranti remains importer of record under DDP."
                        ),

                    deliveryImportantLines =
                        listOf(
                            "This delivery note confirms receipt only.",
                            "It does not confirm concealed goods are free from damage.",
                            "Prices are intentionally omitted.",
                            "De Ferranti Limited remains importer of record under DDP."
                        ),

                    documentMatchLines =
                        listOf(
                            "Order: DF-2026-000001",
                            "Commercial invoice: INV-2026-000001",
                            "Packing list: PL-2026-000001",
                            "Purchase order: PO-2026-000001",
                            "FedEx tracking: TEST123456789"
                        ),

                    purchaseOrderControlNote =
                        "TEST DOCUMENT. Purchase order generated from test checkout data. " +
                                "Supplier VAT treatment must be confirmed before live use.",

                    packingListControlNote =
                        "TEST DOCUMENT. Packing list must match the commercial invoice, " +
                                "FedEx shipment and physical shipment.",

                    deliveryNoteControlNote =
                        "TEST DOCUMENT. Delivery note must match the checkout order, " +
                                "packing list, commercial invoice and physical delivery."
                )
        )
}