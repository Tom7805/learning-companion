# Tạo tài khoản quản trị viên duy nhất trên máy chủ (NCL-03-CN-002). Chạy một lần khi cài đặt:
#
#   .\scripts\create-admin.ps1 admin@example.com "Quản trị viên"
#
# Mật khẩu nhập ẩn (hoặc đặt sẵn biến LC_ADMIN_PASSWORD). Đã có quản trị viên thì câu lệnh từ chối và thoát mã 2.
param(
    [Parameter(Mandatory = $true)][string]$Email,
    [string]$Name = "Quản trị viên"
)
$ErrorActionPreference = "Stop"

if (-not $env:LC_ADMIN_PASSWORD) {
    $first = Read-Host "Mật khẩu quản trị viên (ít nhất 10 ký tự)" -AsSecureString
    $second = Read-Host "Nhập lại mật khẩu" -AsSecureString
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($first))
    $again = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($second))
    if ($plain -ne $again) {
        Write-Error "Hai lần nhập mật khẩu không khớp."
        exit 1
    }
    $env:LC_ADMIN_PASSWORD = $plain
}
$env:LC_ADMIN_NAME = $Name

$arguments = "--server.port=0 --create-admin --email=$Email"
if ($env:LC_APP_JAR) {
    java -jar $env:LC_APP_JAR --server.port=0 --create-admin "--email=$Email"
    exit $LASTEXITCODE
}
Push-Location (Join-Path $PSScriptRoot "..\backend")
try {
    .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.arguments=$arguments"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
