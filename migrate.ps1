$env:PATH = 'C:\Program Files\nodejs;' + $env:PATH
Set-Location 'C:\Users\CHEPI\Desktop\SE PROJECT\server'

Write-Host "--- Generating Prisma client ---"
& 'C:\Program Files\nodejs\npx.cmd' prisma generate
Write-Host "Generate exit code: $LASTEXITCODE"

Write-Host "`n--- Running DB migration ---"
& 'C:\Program Files\nodejs\npx.cmd' prisma migrate dev --name init
Write-Host "Migrate exit code: $LASTEXITCODE"

Write-Host "`n--- Seeding DB ---"
& 'C:\Program Files\nodejs\node.exe' prisma/seed.js
Write-Host "Seed exit code: $LASTEXITCODE"

Write-Host "`nDB setup complete!"
