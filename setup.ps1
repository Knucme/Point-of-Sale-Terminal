$env:PATH = 'C:\Program Files\nodejs;' + $env:PATH

Write-Host "Node: $(& 'C:\Program Files\nodejs\node.exe' --version)"
Write-Host "NPM: $(& 'C:\Program Files\nodejs\npm.cmd' --version)"

Write-Host "`n--- Installing server dependencies ---"
Set-Location 'C:\Users\CHEPI\Desktop\SE PROJECT\server'
& 'C:\Program Files\nodejs\npm.cmd' install
Write-Host "Server install exit code: $LASTEXITCODE"

Write-Host "`n--- Installing client dependencies ---"
Set-Location 'C:\Users\CHEPI\Desktop\SE PROJECT\client'
& 'C:\Program Files\nodejs\npm.cmd' install
Write-Host "Client install exit code: $LASTEXITCODE"

Write-Host "`nAll done!"
