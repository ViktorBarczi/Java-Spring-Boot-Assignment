# Runs the assignment checks against the project's local Compose stack.
# Recreates its containers to check persistence; keeps the database volume and demo book.
param([switch]$SkipBuildAndTests)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($args -join ' ')"
    }
}

function Invoke-Compose {
    Invoke-Docker compose -p library @args
}

function Get-ProxyUrl {
    $address = Invoke-Compose port nginx 80
    return "http://$($address.Trim())"
}

function Assert-BookExists([string]$Url, [string]$Isbn) {
    $books = Invoke-RestMethod "$Url/api/books" -TimeoutSec 15
    if (-not ($books | Where-Object { $_.isbn -eq $Isbn })) {
        throw "Demo book was not returned by $Url"
    }
}

function Assert-LoadBalancing([string]$Url, [string]$Isbn, [int]$ExpectedBackends, [switch]$WaitForDns) {
    # Allow Docker DNS and Nginx to pick up the current set of replicas.
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $backends = @()
        $settled = $true
        foreach ($request in 1..12) {
            try {
                $response = Invoke-WebRequest -UseBasicParsing -Uri "$Url/api/books" -TimeoutSec 15
            } catch {
                $status = [int]$_.Exception.Response.StatusCode
                if (-not $WaitForDns -or $status -notin @(502, 503, 504)) { throw }
                Write-Host "Waiting for Docker DNS to settle after scaling (HTTP $status)."
                $settled = $false
                break
            }
            $books = $response.Content | ConvertFrom-Json
            if (-not ($books | Where-Object { $_.isbn -eq $Isbn })) {
                throw 'The book was not returned through Nginx.'
            }
            $backend = [string]$response.Headers['X-Upstream-Addr']
            if ([string]::IsNullOrWhiteSpace($backend)) {
                throw 'Nginx did not identify the upstream application.'
            }
            # A retry can list more than one address; count only the successful backend.
            $backends += ($backend -split ',')[-1].Trim()
        }
        $uniqueBackends = @($backends | Sort-Object -Unique)
        if ($settled -and $uniqueBackends.Count -eq $ExpectedBackends) {
            Write-Host "Nginx routed requests to: $($uniqueBackends -join ', ')"
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Expected $ExpectedBackends backends, observed: $($uniqueBackends -join ', ')"
}

Invoke-Compose config --quiet
$javaTestsStatus = 'not run (skipped)'
if (-not $SkipBuildAndTests) {
    Write-Host 'Building application and running the existing Java tests...'
    Invoke-Compose build
    Invoke-Docker build --target build -t pims-build-check:local .
    Invoke-Docker run --rm pims-build-check:local mvn --batch-mode --no-transfer-progress test
    $javaTestsStatus = 'passed'
}

Write-Host 'Starting Nginx, two application instances and PostgreSQL...'
Invoke-Compose up -d --no-build --scale app=2 --wait --wait-timeout 180
$baseUrl = Get-ProxyUrl
Invoke-Compose exec -T nginx nginx -t

$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/login" `
    -ContentType 'application/json' -Body '{"username":"Viktor","password":"pass"}' -TimeoutSec 15
if (-not $login.token) { throw 'Login returned no token.' }
$headers = @{ Authorization = "Bearer $($login.token)" }

# Generate a valid, fresh ISBN-13 so this verification can be repeated.
$isbnPrefix = '978' + (Get-Random -Minimum 100000000 -Maximum 999999999)
$checksum = 0
for ($i = 0; $i -lt 12; $i++) {
    $weight = if ($i % 2 -eq 0) { 1 } else { 3 }
    $checksum += [int]::Parse([string]$isbnPrefix[$i]) * $weight
}
$isbn = $isbnPrefix + ((10 - $checksum % 10) % 10)
$title = 'Docker verification ' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$book = @{
    title = $title
    author = 'Viktor Barczi'
    isbn = $isbn
    publishedYear = 2026
} | ConvertTo-Json

# Nginx must forward the JSON body and Bearer token to the app.
$created = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$baseUrl/api/books" `
    -Headers $headers -ContentType 'application/json' -Body $book -TimeoutSec 15
if ([int]$created.StatusCode -ne 201) { throw 'Book creation did not return HTTP 201.' }
Assert-LoadBalancing $baseUrl $isbn 2
Write-Host 'Login, authenticated write and reads through both backends passed.'

Write-Host 'Checking dynamic discovery when scaling from two to three instances...'
$proxyId = Invoke-Compose ps -q nginx
try {
    Invoke-Compose up -d --no-build --scale app=3 --wait --wait-timeout 180
    Assert-LoadBalancing $baseUrl $isbn 3 -WaitForDns
    if ((Invoke-Compose ps -q nginx) -ne $proxyId) {
        throw 'Nginx was recreated instead of discovering the new instance.'
    }
} finally {
    Invoke-Compose up -d --no-build --scale app=2 --wait --wait-timeout 180
}
Assert-LoadBalancing $baseUrl $isbn 2 -WaitForDns
if ((Invoke-Compose ps -q nginx) -ne $proxyId) {
    throw 'Nginx was recreated during scale-down.'
}

Write-Host 'Removing and recreating containers while retaining the volume...'
Invoke-Compose down
Invoke-Compose up -d --no-build --scale app=2 --wait --wait-timeout 180
$baseUrl = Get-ProxyUrl
Assert-LoadBalancing $baseUrl $isbn 2
Write-Host 'Volume persistence passed.'

# These two app containers are launched manually against the same PostgreSQL service.
$manualNames = @()
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$imageRef = if ($env:APP_IMAGE) { $env:APP_IMAGE } else { 'viktorbarczi/pims_zadanie:1.0' }
try {
    foreach ($index in 1..2) {
        $name = "pims-manual-check-$suffix-$index"
        Invoke-Docker run -d --name $name --network library_default `
            -p 127.0.0.1::8081 $imageRef
        $manualNames += $name
    }
    foreach ($name in $manualNames) {
        $deadline = (Get-Date).AddSeconds(180)
        do {
            $health = Invoke-Docker inspect --format '{{.State.Health.Status}}' $name
            if ($health.Trim() -eq 'healthy') { break }
            if ((Get-Date) -gt $deadline) {
                Invoke-Docker logs --tail 80 $name
                throw "Container $name did not become healthy."
            }
            Start-Sleep -Seconds 2
        } while ($true)
        $address = Invoke-Docker port $name 8081/tcp
        Assert-BookExists "http://$($address.Trim())" $isbn
    }
    Write-Host 'Two manually launched application instances passed.'
} finally {
    if ($manualNames.Count -gt 0) {
        Invoke-Docker rm --force @manualNames
    }
}

New-Item -ItemType Directory -Path target -Force | Out-Null
@{
    checkedAt = (Get-Date).ToString('o')
    image = $imageRef
    javaTests = $javaTestsStatus
    loginAndCreate = 'passed'
    composeInstances = 2
    manualInstances = 2
    persistenceAfterRecreation = 'passed'
    loadBalancing = 'passed'
    dynamicScaling = 'passed (2 -> 3 -> 2 without restarting Nginx)'
    baseUrl = $baseUrl
    demoBookTitle = $title
    demoBookIsbn = $isbn
} | ConvertTo-Json | Set-Content -Encoding UTF8 target/docker-verification.json
Invoke-Compose ps
Write-Host "All local checks passed. Load-balanced API: $baseUrl"
