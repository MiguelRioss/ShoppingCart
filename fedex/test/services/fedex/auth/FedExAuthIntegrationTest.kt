package services.shipping.fedex.auth

import io.github.cdimascio.dotenv.dotenv
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import services.shipping.fedex.FedExConfiguration

class FedExAuthIntegrationTest {

    @Test
    fun `real FedEx sandbox authentication returns access token`() {
        val dotenv = dotenv {
            directory = "./"
            filename = ".env"
        }

        println(
            "FedEx client ID suffix: " +
                    requireNotNull(
                        dotenv["FEDEX_CLIENT_ID"]
                    ).takeLast(6)
        )

        println(
            "FedEx account: " +
                    requireNotNull(
                        dotenv["FEDEX_ACCOUNT_NUMBER"]
                    )
        )

        val configuration = FedExConfiguration(
            baseUrl = "https://apis-sandbox.fedex.com",
            clientId = requireNotNull(
                dotenv["FEDEX_CLIENT_ID"]
            ) {
                "FEDEX_CLIENT_ID is missing from .env"
            },
            clientSecret = requireNotNull(
                dotenv["FEDEX_CLIENT_SECRET"]
            ) {
                "FEDEX_CLIENT_SECRET is missing from .env"
            },
            accountNumber = requireNotNull(
                dotenv["FEDEX_ACCOUNT_NUMBER"]
            ) {
                "FEDEX_ACCOUNT_NUMBER is missing from .env"
            }
        )

        val client = FedExAuthClient(
            configuration = configuration,
            httpClient = JavaFedExHttpClient()
        )

        val token = client.accessToken()

        assertTrue(token.isNotBlank())

        println("FedEx authentication successful")
        println("Token received: ${token.take(12)}...")
    }
}
