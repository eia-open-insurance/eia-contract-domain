# Live smoke for the Contract vertical against a real PostgreSQL instance.
#
#   create -> layers/participants -> required terms -> BOUND -> cession
#   -> in force -> commutation -> CLOSED
#
# Start the two services first (each uses the eia_contract database):
#   java -jar eia-contract-expert/build/libs/eia-contract-expert-0.1.0-SNAPSHOT.jar
#   java -jar eia-contract-domain/build/libs/eia-contract-domain-0.1.0-SNAPSHOT.jar
#
# Then run:  pwsh -File scripts/live-smoke.ps1
param(
    [string]$BaseUrl = "http://localhost:8086",
    [string]$Tenant = "11111111-1111-7111-8111-111111111111",
    [string]$Approver = "66666666-6666-7666-8666-666666666666",
    [string]$Cedent = "33333333-3333-7333-8333-333333333333",
    [string]$Reinsurer = "44444444-4444-7444-8444-444444444444",
    [string]$Follower = "aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa"
)

$ErrorActionPreference = "Stop"
$headers = @{ "X-EIA-Tenant" = $Tenant }
$script:failures = 0
$script:checks = 0

function Send {
    param([string]$Method, [string]$Path, $Body)
    $params = @{
        Method             = $Method
        Uri                = "$BaseUrl$Path"
        Headers            = $headers
        SkipHttpErrorCheck = $true
        StatusCodeVariable = 'statusCode'
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }
    $response = Invoke-RestMethod @params
    return [pscustomobject]@{ Status = $statusCode; Body = $response }
}

function Check {
    param([string]$Name, [bool]$Passed, [string]$Detail = "")
    $script:checks++
    if ($Passed) {
        Write-Host ("PASS {0}" -f $Name)
    } else {
        $script:failures++
        Write-Host ("FAIL {0} {1}" -f $Name, $Detail) -ForegroundColor Red
    }
}

function Expect {
    param([string]$Name, $Result, [int]$Status)
    $code = if ($Result.Body -and $Result.Body.code) { $Result.Body.code } else { "" }
    Check $Name ($Result.Status -eq $Status) ("expected HTTP $Status, got $($Result.Status) $code")
}

$suffix = Get-Date -Format "HHmmss"
$contractNumber = "XOL-LIVE-$suffix"
Write-Host "Contract vertical live smoke against $BaseUrl (contract $contractNumber)"

# ---------------------------------------------------------------- contract
$created = Send "POST" "/api/v1/contracts" @{
    contractNumber  = $contractNumber
    contractType    = "EXCESS_OF_LOSS"
    cedentPartyId   = $Cedent
    reinsurerPartyId = $Reinsurer
    effectiveDate   = "2026-01-01"
    expirationDate  = "2026-12-31"
    currency        = "USD"
}
Expect "create draft contract" $created 201
$id = $created.Body.id

$quoted = Send "PATCH" "/api/v1/contracts/$id/status" @{ status = "QUOTED"; expectedVersion = $created.Body.version }
Expect "quote contract" $quoted 200

$placed = Send "PATCH" "/api/v1/contracts/$id/status" @{ status = "PLACED"; expectedVersion = $quoted.Body.version }
Expect "place contract" $placed 200

# ------------------------------------------------------------------ layers
$layer1 = Send "POST" "/api/v1/contracts/$id/layers" @{
    layerNumber = 1; layerType = "EXCESS_OF_LOSS"; attachmentAmount = "0"
    limitAmount = "1000000"; limitBasis = "PER_OCCURRENCE"
}
Expect "record layer 1" $layer1 201

$layer2 = Send "POST" "/api/v1/contracts/$id/layers" @{
    layerNumber = 2; layerType = "EXCESS_OF_LOSS"; attachmentAmount = "1000000"
    limitAmount = "2000000"; limitBasis = "PER_OCCURRENCE"
}
Expect "record adjacent layer 2" $layer2 201

$overlap = Send "POST" "/api/v1/contracts/$id/layers" @{
    layerNumber = 3; layerType = "EXCESS_OF_LOSS"; attachmentAmount = "500000"
    limitAmount = "1000000"; limitBasis = "PER_OCCURRENCE"
}
Expect "overlapping layer is rejected (CTR-02)" $overlap 409
Check "overlap code is LAYER_OVERLAP" ($overlap.Body.code -eq "LAYER_OVERLAP") $overlap.Body.code

# ------------------------------------------------------------ participants
$p1 = Send "POST" "/api/v1/contracts/$id/participants" @{
    layerId = $layer1.Body.id; partyId = $Reinsurer; participantRole = "REINSURER"; sharePct = "60"
}
Expect "record layer 1 lead share 60%" $p1 201

$p2 = Send "POST" "/api/v1/contracts/$id/participants" @{
    layerId = $layer1.Body.id; partyId = $Follower; participantRole = "FOLLOW"; sharePct = "40"
}
Expect "record layer 1 follow share 40%" $p2 201

$p3 = Send "POST" "/api/v1/contracts/$id/participants" @{
    layerId = $layer2.Body.id; partyId = $Reinsurer; participantRole = "REINSURER"; sharePct = "100"
}
Expect "record layer 2 share 100%" $p3 201

$overShare = Send "POST" "/api/v1/contracts/$id/participants" @{
    layerId = $layer1.Body.id; partyId = "bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb"
    participantRole = "REINSURER"; sharePct = "10"
}
Expect "layer above 100% is rejected (CTR-01)" $overShare 409
Check "share code is SHARES_EXCEED_100" ($overShare.Body.code -eq "SHARES_EXCEED_100") $overShare.Body.code

# --------------------------------------------------------- bind preconditions
$earlyBind = Send "POST" "/api/v1/contracts/$id/bind" @{ approvedBy = $Approver; expectedVersion = $placed.Body.version }
Expect "bind without required terms is rejected" $earlyBind 409
Check "bind code is BIND_PRECONDITION_FAILED" ($earlyBind.Body.code -eq "BIND_PRECONDITION_FAILED") $earlyBind.Body.code

$term7 = Send "POST" "/api/v1/contracts/$id/terms" @{
    termCode = "GOVERNING_LAW"; termValue = "New York"; sourceClause = "GOVERNING_LAW"
}
Expect "record GOVERNING_LAW" $term7 201
$term8 = Send "POST" "/api/v1/contracts/$id/terms" @{
    termCode = "REINSTATEMENT_PROVISION"; termValue = "One free reinstatement"; sourceClause = "REINSTATEMENT"
}
Expect "record REINSTATEMENT_PROVISION" $term8 201
$term9 = Send "POST" "/api/v1/contracts/$id/terms" @{
    termCode = "ULTIMATE_NET_LOSS"; termValue = "Net loss after recoveries"; sourceClause = "ULTIMATE_NET_LOSS"
}
Expect "record ULTIMATE_NET_LOSS" $term9 201

$security = Send "POST" "/api/v1/contracts/$id/security" @{
    securityType = "LETTER_OF_CREDIT"; requiredAmount = "5000000"; heldAmount = "5000000"
    providerPartyId = $Reinsurer; expiryDate = "2027-03-31"
}
Expect "record letter of credit" $security 201

$overCollateral = Send "POST" "/api/v1/contracts/$id/security" @{
    securityType = "TRUST_FUND"; requiredAmount = "1000000"; heldAmount = "1500000"
}
Expect "collateral above obligation without approval is rejected" $overCollateral 409
Check "collateral code is COLLATERAL_EXCEEDS_OBLIGATION" (
    $overCollateral.Body.code -eq "COLLATERAL_EXCEEDS_OBLIGATION") $overCollateral.Body.code

$eligibility = Send "GET" "/api/v1/contracts/$id/bind-eligibility"
Expect "read bind eligibility" $eligibility 200
Check "contract is bindable" ($eligibility.Body.bindable -eq $true) ($eligibility.Body | ConvertTo-Json -Depth 5 -Compress)

$bound = Send "POST" "/api/v1/contracts/$id/bind" @{
    approvedBy = $Approver; expectedVersion = $placed.Body.version
}
Expect "bind contract" $bound 200
Check "contract status is BOUND" ($bound.Body.contract.status -eq "BOUND") $bound.Body.contract.status

# ----------------------------------------------------------------- cession
$cession = Send "POST" "/api/v1/contracts/$id/cessions" @{
    layerId = $layer1.Body.id; sourceType = "CLAIM"; claimRef = "CLM-LIVE-$suffix"
    policyRef = "POL-LIVE-$suffix"; cessionType = "QUOTA_SHARE"; cessionPct = "40"
    cededPremiumAmount = "400000"; cededLossAmount = "200000"
}
Expect "record cession within capacity" $cession 201
Check "cession status is RECORDED" ($cession.Body.status -eq "RECORDED") $cession.Body.status
Check "cession number is generated" ($cession.Body.cessionNumber -like "CES-2026-*") $cession.Body.cessionNumber

$overCession = Send "POST" "/api/v1/contracts/$id/cessions" @{
    layerId = $layer1.Body.id; sourceType = "CLAIM"; claimRef = "CLM-LIVE-OVER-$suffix"
    policyRef = "POL-LIVE-$suffix"; cessionType = "QUOTA_SHARE"; cessionPct = "40"
    cededLossAmount = "900000"
}
Expect "cession beyond layer capacity is rejected (CTR-04)" $overCession 409
Check "cession code is CESSION_EXCEEDS_CONTRACT_LIMIT" (
    $overCession.Body.code -eq "CESSION_EXCEEDS_CONTRACT_LIMIT") $overCession.Body.code

$cessionList = Send "GET" "/api/v1/contracts/$id/cessions"
Expect "list cessions" $cessionList 200
Check "one cession is stored" ($cessionList.Body.Count -eq 1) ("count=" + $cessionList.Body.Count)

# ------------------------------------------------------ in force + commutation
$signed = Send "PATCH" "/api/v1/contracts/$id/status" @{ status = "SIGNED"; expectedVersion = $bound.Body.contract.version }
Expect "sign contract" $signed 200
$inForce = Send "PATCH" "/api/v1/contracts/$id/status" @{ status = "IN_FORCE"; expectedVersion = $signed.Body.version }
Expect "put contract in force" $inForce 200

$commutation = Send "POST" "/api/v1/contracts/$id/commutations" @{
    commutationType = "FULL"; effectiveDate = "2026-12-31"; valuationDate = "2026-11-30"
    agreedAmount = "2650000"; savingsAmount = "230000"; basis = "NEGOTIATED"
    releaseDocumentId = "77777777-7777-7777-8777-777777777777"
}
Expect "propose FULL commutation" $commutation 201

$executeEarly = Send "POST" "/api/v1/contracts/$id/commutations/$($commutation.Body.id)/execute" @{}
Expect "execute before approval is rejected" $executeEarly 409
Check "execution code is COMMUTATION_NOT_APPROVED" (
    $executeEarly.Body.code -eq "COMMUTATION_NOT_APPROVED") $executeEarly.Body.code

$approved = Send "POST" "/api/v1/contracts/$id/commutations/$($commutation.Body.id)/approve" @{ approvedBy = $Approver }
Expect "approve commutation" $approved 200

$executed = Send "POST" "/api/v1/contracts/$id/commutations/$($commutation.Body.id)/execute" @{
    releaseDocumentId = "77777777-7777-7777-8777-777777777777"; releaseDate = "2026-12-31"
}
Expect "execute commutation" $executed 200
Check "FULL commutation closes the contract" ($executed.Body.contract.status -eq "CLOSED") $executed.Body.contract.status
Check "commutation status is EXECUTED" ($executed.Body.commutation.status -eq "EXECUTED") $executed.Body.commutation.status
Check "security was released" ($executed.Body.releasedSecurities -ge 1) ("released=" + $executed.Body.releasedSecurities)

Write-Host ""
Write-Host ("{0} checks, {1} failure(s)" -f $script:checks, $script:failures)
if ($script:failures -gt 0) {
    exit 1
}
Write-Host "Contract live smoke passed."
