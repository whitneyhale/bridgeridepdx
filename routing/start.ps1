# Starts the local routing engine (GraphHopper) for Bridge Ride PDX.
# First run downloads the engine (about 47 MB) and the Portland map (about 73 MB).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$jar = "graphhopper-web-11.1.jar"
$pbf = "Portland.osm.pbf"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Java not found. Installing Java 21 with winget..."
    winget install --id EclipseAdoptium.Temurin.21.JRE -e --accept-package-agreements --accept-source-agreements
    Write-Host ""
    Write-Host "Java installed. Close this window, then run start.bat again."
    exit
}

if (-not (Test-Path $jar)) {
    Write-Host "Downloading routing engine..."
    curl.exe -L -f -o "$jar.part" "https://github.com/graphhopper/graphhopper/releases/download/11.1/graphhopper-web-11.1.jar"
    if ($LASTEXITCODE -ne 0) { throw "Engine download failed." }
    Move-Item "$jar.part" $jar
}

if (-not (Test-Path $pbf)) {
    Write-Host "Downloading Portland map..."
    curl.exe -L -f -o "$pbf.part" "https://download.bbbike.org/osm/bbbike/Portland/Portland.osm.pbf"
    if ($LASTEXITCODE -ne 0) { throw "Map download failed." }
    Move-Item "$pbf.part" $pbf
}

Write-Host ""
Write-Host "Starting. First run builds the route graph (about a minute)."
Write-Host "When you see 'Started Server', open http://localhost:8989/maps/ in your browser."
Write-Host "Close this window to stop."
Write-Host ""
java -Xmx2g -jar $jar server config.yml
