package services.shipping.fedex.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import services.shipping.fedex.FedExConfiguration

class FedExAuthClientTest {

    @Test
    fun `accessToken returns token from successful response`() {
        val httpClient = RecordingFedExHttpClient(
            response = FedExHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "access_token": "test-access-token",
                      "token_type": "bearer",
                      "expires_in": 3600
                    }
                """.trimIndent()
            )
        )

        val client = FedExAuthClient(
            configuration = testConfiguration(),
            httpClient = httpClient
        )

        val token = client.accessToken()

        assertEquals(
            "test-access-token",
            token
        )
    }

    @Test
    fun `accessToken calls FedEx oauth endpoint`() {
        val httpClient = RecordingFedExHttpClient(
            response = successfulResponse()
        )

        val client = FedExAuthClient(
            configuration = testConfiguration(),
            httpClient = httpClient
        )

        client.accessToken()

        assertEquals(
            "https://apis-sandbox.fedex.com/oauth/token",
            httpClient.lastUrl
        )
    }

    @Test
    fun `accessToken sends client credentials form body`() {
        val httpClient = RecordingFedExHttpClient(
            response = successfulResponse()
        )

        val client = FedExAuthClient(
            configuration = testConfiguration(),
            httpClient = httpClient
        )

        client.accessToken()

        val body = requireNotNull(
            httpClient.lastBody
        )

        assertTrue(
            body.contains(
                "grant_type=client_credentials"
            )
        )

        assertTrue(
            body.contains(
                "client_id=test-client-id"
            )
        )

        assertTrue(
            body.contains(
                "client_secret=test-client-secret"
            )
        )
    }

    @Test
    fun `accessToken url encodes credentials`() {
        val httpClient = RecordingFedExHttpClient(
            response = successfulResponse()
        )

        val configuration = FedExConfiguration(
            baseUrl = "https://apis-sandbox.fedex.com",
            clientId = "client id",
            clientSecret = "secret+value",
            accountNumber = "123456789"
        )

        val client = FedExAuthClient(
            configuration = configuration,
            httpClient = httpClient
        )

        client.accessToken()

        val body = requireNotNull(
            httpClient.lastBody
        )

        assertTrue(
            body.contains(
                "client_id=client+id"
            )
        )

        assertTrue(
            body.contains(
                "client_secret=secret%2Bvalue"
            )
        )
    }

    @Test
    fun `accessToken fails when FedEx returns non successful response`() {
        val httpClient = RecordingFedExHttpClient(
            response = FedExHttpResponse(
                statusCode = 401,
                body = """
                    {
                      "errors": [
                        {
                          "code": "UNAUTHORIZED",
                          "message": "Invalid credentials"
                        }
                      ]
                    }
                """.trimIndent()
            )
        )

        val client = FedExAuthClient(
            configuration = testConfiguration(),
            httpClient = httpClient
        )

        val exception = assertThrows(
            IllegalStateException::class.java
        ) {
            client.accessToken()
        }

        assertTrue(
            exception.message
                ?.contains("401") == true
        )
    }

    @Test
    fun `accessToken fails when access token is missing`() {
        val httpClient = RecordingFedExHttpClient(
            response = FedExHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "token_type": "bearer",
                      "expires_in": 3600
                    }
                """.trimIndent()
            )
        )

        val client = FedExAuthClient(
            configuration = testConfiguration(),
            httpClient = httpClient
        )

        val exception = assertThrows(
            IllegalStateException::class.java
        ) {
            client.accessToken()
        }

        assertTrue(
            exception.message
                ?.contains(
                    "did not contain access_token"
                ) == true
        )
    }

    private fun testConfiguration() =
        FedExConfiguration(
            baseUrl =
                "https://apis-sandbox.fedex.com",
            clientId =
                "test-client-id",
            clientSecret =
                "test-client-secret",
            accountNumber =
                "123456789"
        )

    private fun successfulResponse() =
        FedExHttpResponse(
            statusCode = 200,
            body = """
                {
                  "access_token": "test-access-token",
                  "token_type": "bearer",
                  "expires_in": 3600
                }
            """.trimIndent()
        )

    private class RecordingFedExHttpClient(
        private val response:
        FedExHttpResponse
    ) : FedExHttpClient {

        var lastUrl: String? = null
            private set

        var lastBody: String? = null
            private set

        override fun postForm(
            url: String,
            body: String
        ): FedExHttpResponse {
            lastUrl = url
            lastBody = body

            return response
        }
    }
}