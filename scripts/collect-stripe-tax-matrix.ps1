$ErrorActionPreference = 'Stop'
$values = @{}
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2].Trim('"', "'") }
}
$key = $values['STRIPE_PT_SECRET_KEY']
if (-not $key.StartsWith('sk_test_')) { throw 'Only a Stripe test key is allowed for this matrix' }
$destinations = Get-Content outputs/fedex-ddp-matrix.json -Raw | ConvertFrom-Json
$postcodes = @{ PT = '1000-001'; IT = '20121'; ES = '28001'; FR = '75001'; DE = '10115'; NL = '1012 AB'; BE = '1000'; IE = 'D02 X285'; SE = '111 22'; US = '10001'; CA = 'M5H 2N2'; CH = '8001'; NO = '0150'; AU = '2000'; JP = '100-0001'; AE = '00000' }
$rows = foreach ($destination in $destinations) {
    $body = @{
        currency = 'eur'
        'line_items[0][amount]' = '36000'
        'line_items[0][reference]' = 'product-9091'
        'line_items[0][tax_behavior]' = 'exclusive'
        'customer_details[address][country]' = $destination.country
        'customer_details[address][postal_code]' = $postcodes[$destination.country]
        'customer_details[address_source]' = 'shipping'
    }
    if ($destination.country -eq 'CA') { $body['customer_details[address][state]'] = 'ON' }
    if ($destination.status -eq 'OK') {
        $body['shipping_cost[amount]'] = [string][math]::Round([decimal]$destination.transportEUR * 100)
        $body['shipping_cost[tax_behavior]'] = 'exclusive'
    }
    try {
        $result = Invoke-RestMethod -Method Post -Uri 'https://api.stripe.com/v1/tax/calculations' -Headers @{ Authorization = "Bearer $key" } -Body $body
        $breakdown = @($result.tax_breakdown) | Where-Object { $_.amount -gt 0 } | Select-Object -First 1
        [pscustomobject]@{
            country = $destination.country
            status = 'OK'
            livemode = $result.livemode
            productTaxEUR = [math]::Round(($result.tax_amount_exclusive - $result.shipping_cost.amount_tax) / 100, 2)
            shippingTaxEUR = [math]::Round($result.shipping_cost.amount_tax / 100, 2)
            totalTaxEUR = [math]::Round($result.tax_amount_exclusive / 100, 2)
            taxCountry = if ($breakdown) { $breakdown.tax_rate_details.country } else { $result.tax_breakdown[0].tax_rate_details.country }
            taxReason = if ($breakdown) { $breakdown.taxability_reason } else { $result.tax_breakdown[0].taxability_reason }
            ratePercent = if ($breakdown) { $breakdown.tax_rate_details.percentage_decimal } else { $result.tax_breakdown[0].tax_rate_details.percentage_decimal }
        }
    } catch {
        [pscustomobject]@{ country = $destination.country; status = 'ERROR'; reason = $_.Exception.Message }
    }
}
$rows | ConvertTo-Json -Depth 6 | Set-Content outputs/stripe-tax-matrix.json -Encoding utf8
$rows | Select-Object country,status,totalTaxEUR,taxCountry,taxReason,ratePercent | Format-Table -AutoSize
