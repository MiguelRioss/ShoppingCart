package services.shipping.fedex

import org.openpdf.text.Document
import org.openpdf.text.Paragraph
import org.openpdf.text.PageSize
import org.openpdf.text.pdf.PdfWriter
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentContact
import domain.shipment.ShipmentDimensions
import domain.shipment.ShipmentPackage
import domain.shipment.ShipmentRateRequest
import domain.shipment.ShipmentRequest
import domain.shipment.ShipmentWeight
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.stream.Stream
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import services.shipping.AccountRoutingShippingProvider

/** Makes real FedEx Sandbox shipments. Run only via :services:fedexSandboxShipmentTest. */
class FedExSandboxShipmentIntegrationTest {
    private data class Destination(val countryCode: String, val address: ShipmentAddress)

    private val destinations = listOf(
        Destination("IT", ShipmentAddress("Via Roma 1", city = "Milano", postalCode = "20121", countryCode = "IT")),
        Destination("ES", ShipmentAddress("Calle de Alcala 1", city = "Madrid", postalCode = "28014", countryCode = "ES")),
        Destination("FR", ShipmentAddress("1 Rue de Rivoli", city = "Paris", postalCode = "75001", countryCode = "FR")),
        Destination("DE", ShipmentAddress("Friedrichstrasse 1", city = "Berlin", postalCode = "10117", countryCode = "DE")),
        Destination("NL", ShipmentAddress("Dam 1", city = "Amsterdam", postalCode = "1012 JS", countryCode = "NL")),
        Destination("BE", ShipmentAddress("Rue de la Loi 1", city = "Brussels", postalCode = "1000", countryCode = "BE")),
        Destination("IE", ShipmentAddress("O'Connell Street 1", city = "Dublin", postalCode = "D01 F5P2", countryCode = "IE")),
        Destination("SE", ShipmentAddress("Drottninggatan 1", city = "Stockholm", postalCode = "111 51", countryCode = "SE")),
        Destination("GB", ShipmentAddress("10 Downing Street", city = "London", postalCode = "SW1A 2AA", countryCode = "GB")),
        Destination("US", ShipmentAddress("1 Test Street", city = "New York", stateOrProvince = "NY", postalCode = "10001", countryCode = "US")),
        Destination("CA", ShipmentAddress("1 Test Street", city = "Toronto", stateOrProvince = "ON", postalCode = "M5H 2N2", countryCode = "CA")),
        Destination("CH", ShipmentAddress("Bahnhofstrasse 1", city = "Zurich", postalCode = "8001", countryCode = "CH")),
        Destination("NO", ShipmentAddress("Karl Johans gate 1", city = "Oslo", postalCode = "0154", countryCode = "NO")),
        Destination("AU", ShipmentAddress("George Street 1", city = "Sydney", stateOrProvince = "NSW", postalCode = "2000", countryCode = "AU")),
        Destination("JP", ShipmentAddress("1 Chome-1-1 Marunouchi", city = "Tokyo", stateOrProvince = "Tokyo", postalCode = "100-0005", countryCode = "JP")),
        Destination("AE", ShipmentAddress("Sheikh Zayed Road 1", city = "Dubai", postalCode = "00000", countryCode = "AE"))
    )

    @TestFactory
    fun `cheapest available service creates a sandbox AWB and PDF for every destination`(): Stream<DynamicTest> {
        check(System.getenv("FEDEX_MODE") == "SANDBOX") {
            "This integration test must run with FEDEX_MODE=SANDBOX"
        }
        val provider = AccountRoutingShippingProvider()
        return destinations.stream().map { destination ->
            DynamicTest.dynamicTest("${destination.countryCode}: create shipment using cheapest available rate") {
                runCatching { createAndSaveLabel(provider, destination) }
                    .onFailure { error -> createErrorReport(destination, error) }
            }
        }
    }

    private fun createAndSaveLabel(provider: AccountRoutingShippingProvider, destination: Destination) {
        val from = ShipmentAddress(
            addressLine1 = "Rua da Vidoeira 1",
            city = "Albergaria dos Doze",
            postalCode = "3100-097",
            countryCode = "PT"
        )
        val packages = listOf(
            ShipmentPackage(
                description = "Ceramic tile sample",
                countryOfManufacture = "PT",
                quantity = 1,
                quantityUnits = "PCS",
                weight = ShipmentWeight(1.0, "KG"),
                dimensions = ShipmentDimensions(30, 30, 20, "CM"),
                customsValue = 10.0,
                customsCurrency = "EUR"
            )
        )
        val quotes = try {
            provider.getQuotes(ShipmentRateRequest(from, destination.address, packages, "EUR"))
        } catch (error: error.FedExServiceException) {
            throw IllegalStateException(
                "FedEx Sandbox rejected ${destination.countryCode} rate request: " +
                    "status=${error.statusCode}, details=${error.description}", error
            )
        }
        val cheapestQuote = quotes.minByOrNull { it.amount }
            ?: error("FedEx Sandbox returned no available rates for PT-to-${destination.countryCode}")
        println("${destination.countryCode}: cheapest=${cheapestQuote.serviceType}, ${cheapestQuote.amount} ${cheapestQuote.currency}")

        val request = ShipmentRequest(
            from = from,
            shipperContact = ShipmentContact("Sandbox Shipper", "+351915980458", "FedEx Sandbox Test"),
            to = destination.address,
            recipientContact = ShipmentContact("Sandbox Recipient", "+351912345678"),
            packages = packages,
            serviceType = cheapestQuote.serviceType
        )
        val shipment = try {
            provider.prepareShipping(request, nextBusinessDay())
        } catch (error: error.FedExServiceException) {
            throw IllegalStateException(
                "FedEx Sandbox rejected ${destination.countryCode} shipment: " +
                    "status=${error.statusCode}, details=${error.description}", error
            )
        }
        assertTrue(shipment.masterTrackingNumber.isNotBlank(), "${destination.countryCode}: FedEx returned no AWB")

        val documents = shipment.documents + shipment.packages.flatMap { it.documents }
        val label = documents.firstOrNull { document ->
            val bytes = document.content
            bytes != null && bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-"
        } ?: error(
            "${destination.countryCode}: FedEx created AWB ${shipment.masterTrackingNumber} but returned no PDF. " +
                "Documents: ${documents.map { "type=${it.type}, format=${it.format}, bytes=${it.content?.size ?: 0}, hasUrl=${!it.url.isNullOrBlank()}" }}"
        )
        val pdf = requireNotNull(label.content)
        val outputDirectory = Path.of("src/test/kotlin/services/shipping/fedex/output")
        Files.createDirectories(outputDirectory)
        val outputFile = outputDirectory.resolve(
            "fedex-sandbox-${destination.countryCode}-${shipment.masterTrackingNumber}.pdf"
        )
        Files.write(outputFile, pdf)
        assertTrue(Files.isRegularFile(outputFile) && Files.size(outputFile) > 5,
            "${destination.countryCode}: PDF label was not saved")
        println("${destination.countryCode}: AWB=${shipment.masterTrackingNumber}; PDF=${outputFile.toAbsolutePath()}")
    }

    private fun nextBusinessDay(): LocalDate =
        generateSequence(LocalDate.now().plusDays(1)) { it.plusDays(1) }
            .first { it.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }

    private fun createErrorReport(destination: Destination, error: Throwable) {
        val outputDirectory = Path.of("src/test/kotlin/services/shipping/fedex/output")
        Files.createDirectories(outputDirectory)
        val outputFile = outputDirectory.resolve("fedex-sandbox-${destination.countryCode}-ERROR.pdf")
        val document = Document(PageSize.A4)
        Files.newOutputStream(outputFile).use { output ->
            PdfWriter.getInstance(document, output)
            document.open()
            document.add(Paragraph("FedEx Sandbox shipment test failed"))
            document.add(Paragraph("Destination country: ${destination.countryCode}"))
            document.add(Paragraph("Destination: ${destination.address.city}, ${destination.address.postalCode}"))
            document.add(Paragraph("Date: ${LocalDate.now()}"))
            document.add(Paragraph("Error: ${error.message ?: error.javaClass.simpleName}"))
            document.close()
        }
        assertTrue(Files.isRegularFile(outputFile) && Files.size(outputFile) > 5,
            "${destination.countryCode}: error report PDF was not saved")
        println("${destination.countryCode}: no AWB; error PDF=${outputFile.toAbsolutePath()}")
    }
}
