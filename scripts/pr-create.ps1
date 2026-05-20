param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("main", "develop")]
    [string]$Base = "develop",

    [Parameter(Mandatory = $false)]
    [switch]$Draft
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error "GitHub CLI(gh)가 설치되어 있지 않습니다."
}

$auth = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "gh 인증이 필요합니다. 먼저 'gh auth login'을 실행하세요."
}

$args = @("pr", "create", "--fill-verbose", "--base", $Base)
if ($Draft) {
    $args += "--draft"
}

Write-Host "Creating PR with auto-filled title/body (base=$Base)..."
gh @args
