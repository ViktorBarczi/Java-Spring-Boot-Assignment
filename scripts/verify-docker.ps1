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

function Get-AppUrl([int]$Index) {
    $address = Invoke-Compose port --index $Index app 8081
    return "http://$($address.Trim())"
}

function Assert-BookExists([string]$Url, [string]$Isbn) {
    $books = Invoke-RestMethod "$Url/api/books" -TimeoutSec 15
    if (-not ($books | Where-Object { $_.isbn -eq $Isbn })) {
        throw "Demo book was not returned by $Url"
    }
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

Write-Host 'Starting two application instances and PostgreSQL...'
Invoke-Compose up -d --no-build --scale app=2 --wait --wait-timeout 180
$firstUrl = Get-AppUrl 1
$secondUrl = Get-AppUrl 2

$login = Invoke-RestMethod -Method Post -Uri "$firstUrl/auth/login" `
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

# A token from instance 1 must also authorize writes to instance 2.
$created = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$secondUrl/api/books" `
    -Headers $headers -ContentType 'application/json' -Body $book -TimeoutSec 15
if ([int]$created.StatusCode -ne 201) { throw 'Book creation did not return HTTP 201.' }
Assert-BookExists $firstUrl $isbn
Assert-BookExists $secondUrl $isbn
Write-Host 'Login, authenticated write and shared reads passed.'

Write-Host 'Removing and recreating containers while retaining the volume...'
Invoke-Compose down
Invoke-Compose up -d --no-build --scale app=2 --wait --wait-timeout 180
$firstUrl = Get-AppUrl 1
$secondUrl = Get-AppUrl 2
Assert-BookExists $firstUrl $isbn
Assert-BookExists $secondUrl $isbn
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
    firstUrl = $firstUrl
    secondUrl = $secondUrl
    demoBookTitle = $title
    demoBookIsbn = $isbn
} | ConvertTo-Json | Set-Content -Encoding UTF8 target/docker-verification.json
Invoke-Compose ps
Write-Host "All local checks passed. APIs: $firstUrl and $secondUrl"
