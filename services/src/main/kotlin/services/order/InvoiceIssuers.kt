package services.order

import services.pdf.documents.DocumentParty

internal object InvoiceIssuers {
    fun forAccount(account: String): DocumentParty = when (account) {
        "PT" -> DocumentParty(
            name = "DE FERRANTI, UNIPESSOAL LDA",
            addressLines = listOf(
                "Travessa Doutor Miranda do Vale 9-11",
                "São Pedro de Penaferrim",
                "2710-691 Sintra, Portugal"
            ),
            vatNumber = "518678237"
        )
        "UK" -> DocumentParty(
            name = "De Ferranti Limited",
            addressLines = listOf(
                "South Park Studios, Suite 10",
                "88 Peterborough Road",
                "London SW6 3HH, United Kingdom"
            ),
            vatNumber = "GB947801596"
        )
        else -> throw IllegalArgumentException("Unsupported invoice issuer account: $account")
    }
}
