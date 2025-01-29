# ���ñ���ͱ���
$host.ui.RawUI.WindowTitle = "MCWordGame ����ű�"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ��������ʾ����
Clear-Host
Write-Host "�X�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�[" -ForegroundColor Cyan
Write-Host "�U     MCWordGame ����ű�        �U" -ForegroundColor Cyan
Write-Host "�^�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�a" -ForegroundColor Cyan
Write-Host ""

# ����Ҫ����
function Check-Command($cmdname) {
    return [bool](Get-Command -Name $cmdname -ErrorAction SilentlyContinue)
}

if (-not (Check-Command "git")) {
    Write-Host "����δ��װ Git��" -ForegroundColor Red
    Write-Host "��� https://git-scm.com/downloads ���ز���װ Git"
    pause
    exit 1
}

if (-not (Check-Command "mvn")) {
    Write-Host "����δ��װ Maven��" -ForegroundColor Red
    Write-Host "��� https://maven.apache.org/download.cgi ���ز���װ Maven"
    pause
    exit 1
}

# ��ȡ��ǰ�汾
Write-Host "`n��ȡ��ǰ�汾..." -ForegroundColor Green
$pomContent = Get-Content "pom.xml" -Raw
if ($pomContent -match '<version>(.*?)</version>') {
    $currentVersion = $matches[1]
    Write-Host "��ǰ�汾: $currentVersion" -ForegroundColor White
} else {
    Write-Host "�����޷��� pom.xml ��ȡ�汾�ţ�" -ForegroundColor Red
    pause
    exit 1
}

# �����汾��
$versionParts = $currentVersion -replace "-RELEASE", "" -split "\."
try {
    $major = [int]($versionParts[0])
    $minor = [int]($versionParts[1])
    $patch = [int]($versionParts[2])
} catch {
    Write-Host "���󣺰汾�Ÿ�ʽ����ȷ��" -ForegroundColor Red
    Write-Host "��ǰ��ȡ���İ汾��: $currentVersion" -ForegroundColor Yellow
    Write-Host "�����ĸ�ʽ: X.Y.Z-RELEASE (����: 1.0.0-RELEASE)" -ForegroundColor Yellow
    pause
    exit 1
}

# ѡ��汾��������
Write-Host "`nѡ��汾��������:" -ForegroundColor Cyan
Write-Host "1. ���汾���� (��ǰ: $major)" -ForegroundColor Yellow
Write-Host "2. �ΰ汾���� (��ǰ: $minor)" -ForegroundColor Yellow
Write-Host "3. �������� (��ǰ: $patch)" -ForegroundColor Yellow
$updateType = Read-Host "`n��ѡ�� (1-3)"

# ���°汾��
switch ($updateType) {
    "1" { 
        $major++
        $minor = 0
        $patch = 0
    }
    "2" { 
        $minor++
        $patch = 0
    }
    "3" { $patch++ }
    default {
        Write-Host "��Ч��ѡ��" -ForegroundColor Red
        pause
        exit 1
    }
}

$newVersion = "$major.$minor.$patch-RELEASE"
Write-Host "`n�°汾: $newVersion" -ForegroundColor Green

$confirm = Read-Host "ȷ�ϸ��°汾? (Y/N)"
if ($confirm -ne "Y") {
    Write-Host "��ȡ������" -ForegroundColor Red
    pause
    exit 1
}

# ���°汾��
Write-Host "`n���� pom.xml �汾��..." -ForegroundColor Green
mvn versions:set -DnewVersion=$newVersion -DgenerateBackupPoms=false

# ���� plugin.yml
Write-Host "`n���� plugin.yml �汾��..." -ForegroundColor Green
$content = Get-Content "src/main/resources/plugin.yml" -Encoding UTF8
$content = $content -replace "version: '.*'", "version: '$newVersion'"
$content | Set-Content "src/main/resources/plugin.yml" -Encoding UTF8

# ��ȡ GitHub �û���
$githubUsername = Read-Host "��������� GitHub �û���"

# Git ����
if (-not (Test-Path ".git")) {
    Write-Host "`n��ʼ�� Git �ֿ�..." -ForegroundColor Green
    git init

    Write-Host "`n���� Git �û���Ϣ..." -ForegroundColor Green
    $gitName = Read-Host "������������֣����� Git �ύ��"
    $gitEmail = Read-Host "������������䣨���� Git �ύ��"
    git config user.name $gitName
    git config user.email $gitEmail
}

# ���� GitHub Actions ����
if (-not (Test-Path ".github\workflows")) {
    New-Item -ItemType Directory -Path ".github\workflows" -Force | Out-Null
}

Write-Host "`n���� GitHub Actions ����..." -ForegroundColor Green
$workflowContent = @"
name: Build MCWordGame

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven
        
    - name: Build with Maven
      run: mvn -B package --file pom.xml
      
    - name: Upload artifact
      uses: actions/upload-artifact@v3
      with:
        name: MCWordGame
        path: target/MCWordGame-*.jar

    - name: Create Release
      if: startsWith(github.ref, 'refs/tags/')
      uses: softprops/action-gh-release@v1
      with:
        files: target/MCWordGame-*.jar
        generate_release_notes: true
"@
$workflowContent | Set-Content ".github\workflows\build.yml" -Encoding UTF8

# ������Ŀ
Write-Host "`n������Ŀ..." -ForegroundColor Green
mvn clean package
if ($LASTEXITCODE -ne 0) {
    Write-Host "����ʧ�ܣ�" -ForegroundColor Red
    pause
    exit 1
}

# Git ����
Write-Host "`n����ļ��� Git..." -ForegroundColor Green
git add .

Write-Host "`n�ύ����..." -ForegroundColor Green
git commit -m "Update MCWordGame plugin to version $newVersion"

Write-Host "`n�����汾��ǩ..." -ForegroundColor Green
git tag -a "v$newVersion" -m "Version $newVersion"

# ���Զ�ֿ̲�
$remoteExists = git remote -v | Select-String "origin"
if (-not $remoteExists) {
    Write-Host "`n���� GitHub �ֿ�..." -ForegroundColor Green
    $body = @{
        name = "MCWordGame"
        description = "Minecraft ���־�����Ϸ���"
        private = $false
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "https://api.github.com/user/repos" `
        -Method Post -Headers @{Authorization = "Basic $([Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$($githubUsername):$($githubToken)")))"} `
        -Body $body -ContentType "application/json"

    Write-Host "`n���Զ�ֿ̲�..." -ForegroundColor Green
    git remote add origin "https://github.com/$githubUsername/MCWordGame.git"
}

# ���͵� GitHub
Write-Host "`n���͵� GitHub..." -ForegroundColor Green
git push -u origin main --tags

Write-Host "`n=== ������ɣ�===" -ForegroundColor Green
Write-Host "�汾�Ѹ�����: $newVersion" -ForegroundColor Cyan
Write-Host "��Ĳ���Ѿ��ϴ�����https://github.com/$githubUsername/MCWordGame" -ForegroundColor Yellow
Write-Host "�����õĲ���� target Ŀ¼��" -ForegroundColor Yellow
Write-Host "GitHub Actions ���Զ���������������" -ForegroundColor Yellow
Write-Host ""

pause 