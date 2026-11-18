$ErrorActionPreference = 'Stop'
$values = @{}
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2].Trim('"', "'") }
}
$clientId = $values['FEDEX_PRODUCTION_PT_CLIENT_ID']
$clientSecret = $values['FEDEX_PRODUCTION_PT_CLIENT_SECRET']
if (-not $clientId -or -not $clientSecret -or -not $values['FEDEX_PRODUCTION_PT_ACCOUNT_NUMBER']) {
    throw 'PT FedEx production account number and PT-specific client credentials are required.'
}
$token = Invoke-RestMethod -Method Post -Uri 'https://apis.fedex.com/oauth/token' -ContentType 'application/x-www-form-urlencoded' -Body @{
    grant_type = 'client_credentials'; client_id = $clientId; client_secret = $clientSecret
}
$destinations = Get-Content outputs/fedex-ddp-matrix.json -Raw | ConvertFrom-Json |
    Where-Object { $_.country -notin @('PT', 'IT', 'ES', 'FR', 'DE', 'NL', 'BE', 'IE', 'SE') }
$details = foreach ($destination in $destinations) {
    $recipient = @{ countryCode = $destination.country }
    if ($destination.country -eq 'CA') { $recipient.stateOrProvinceCode = 'ON' }
    $body = @{
        shipper = @{ countryCode = 'PT' }
        recipient = $recipient
        serviceType = $destination.service
        shippingCost = @{ amount = $destination.transportEUR; currencyCode = 'EUR' }
        commodities = @(@{
            description = 'Handmade glazed ceramic tiles'; quantity = 4
            unitPrice = @{ amount = 90; currencyCode = 'EUR' }
            countryOfManufacture = 'PT'
        })
    } | ConvertTo-Json -Depth 8
    try {
        $result = Invoke-RestMethod -Method Post -Uri 'https://apis.fedex.com/dutytax/v1/estimate' -Headers @{
            Authorization = "Bearer $($token.access_token)"; 'X-locale' = 'en_US'
        } -ContentType 'application/json' -Body $body
        $item = $result.output.edtDetails[0]
        [pscustomobject]@{
            country = $destination.country; status = 'OK'; currency = $item.summary.currency
            duties = $item.summary.totalDuties; taxes = $item.summary.totalTaxes
            fees = $item.summary.totalFees; shipping = $item.summary.totalShippingCosts
            landed = $item.summary.totalLandedCost
            dutyLines = $item.duties; taxLines = $item.taxes; feeLines = $item.fees
        }
    } catch {
        [pscustomobject]@{ country = $destination.country; status = 'ERROR'; reason = $_.Exception.Message }
    }
}
$details | ConvertTo-Json -Depth 9 | Set-Content outputs/fedex-duty-details.json -Encoding utf8
$details | Select-Object country,status,duties,taxes,fees,shipping,landed | Format-Table -AutoSize
