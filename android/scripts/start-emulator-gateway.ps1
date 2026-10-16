$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$executable = Join-Path $projectRoot 'bin\mgy-management.exe'
$dataDirectory = Join-Path $projectRoot 'android\design\.verification\management-gateway'
$authStore = Join-Path $dataDirectory 'auth_store.json'
$healthUrl = 'http://127.0.0.1:58901/healthz'

if (!(Test-Path -LiteralPath $executable -PathType Leaf)) {
    throw "Gateway executable missing: $executable"
}
if (!(Test-Path -LiteralPath $authStore -PathType Leaf)) {
    throw "Existing pairing store missing; refusing to create a different identity: $authStore"
}

$listener = Get-NetTCPConnection -LocalPort 58901 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    foreach ($ownerId in ($listener.OwningProcess | Select-Object -Unique)) {
        $owner = Get-Process -Id $ownerId
        if (![string]::Equals($owner.Path, $executable, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Port 58901 belongs to another process (PID $ownerId); nothing was stopped."
        }
    }
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 5
    if ($health.status -ne 'ok') { throw 'The existing gateway failed its health check.' }
    [pscustomobject]@{ Port = 58901; PID = $listener[0].OwningProcess; Started = $false; Health = 'ok' }
    return
}

$logDirectory = Join-Path $projectRoot '.verification\emulator-gateway'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$logPrefix = Join-Path $logDirectory (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
$overrides = @{
    MULTIGRAVITY_DATA_DIR = $dataDirectory
    AUTH_STORE_PATH = $authStore
    CF_TUNNEL_ENABLED = 'false'
    AUTH_DISABLED = 'false'
}
$previous = @{}
try {
    foreach ($name in $overrides.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $overrides[$name], 'Process')
    }
    $gateway = Start-Process -FilePath $executable -ArgumentList '-host', '127.0.0.1', '-port', '58901', '-qr=false' `
        -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput "$logPrefix.stdout.log" -RedirectStandardError "$logPrefix.stderr.log"
}
finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}

for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $gateway.Refresh()
    if ($gateway.HasExited) { throw "Gateway exited. Check $logPrefix.stderr.log" }
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 1
        if ($health.status -eq 'ok') {
            [pscustomobject]@{ Port = 58901; PID = $gateway.Id; Started = $true; Health = 'ok'; Logs = $logDirectory }
            return
        }
    }
    catch { $health = $null }
    Start-Sleep -Milliseconds 250
}
throw "Gateway PID $($gateway.Id) started but health is not ready. Check $logPrefix.stderr.log"
