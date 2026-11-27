$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'Git is not installed. Install Git for Windows, then run this script again.'
}

$remote = 'https://github.com/anutk7653746-commits/app.git'

if (-not (Test-Path '.git')) {
    git init -b main
} else {
    git branch -M main
}

$remotes = @(git remote)
if ($remotes -contains 'origin') {
    git remote set-url origin $remote
} else {
    git remote add origin $remote
}

git add -A
$status = git status --porcelain
if ($status) {
    git commit -m 'Create Findora Android app'
} else {
    Write-Host 'No new changes to commit.'
}

git push -u origin main
Write-Host 'Uploaded Findora to GitHub. The APK workflow should now start in Actions.'
