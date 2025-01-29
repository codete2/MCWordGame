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
    # ����汾���Ǳ�������� plugin.yml ��ȡ
    if ($currentVersion -eq '$newVersion') {
        $ymlContent = Get-Content "src/main/resources/plugin.yml" -Raw
        if ($ymlContent -match "version: '(.*?)'") {
            $currentVersion = $matches[1]
        }
    }
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

# ���� pom.xml �汾��
Write-Host "`n���� pom.xml �汾��..." -ForegroundColor Green
$pomContent = Get-Content "pom.xml" -Raw
$lines = $pomContent -split "`n"
$updated = $false

# ֻ���µ�һ���ҵ��İ汾�ţ���Ŀ�汾�ţ�
for ($i = 0; $i -lt $lines.Count; $i++) {
    if (-not $updated -and $lines[$i] -match '<version>.*</version>') {
        $lines[$i] = $lines[$i] -replace '<version>.*</version>', "<version>$newVersion</version>"
        $updated = $true
    }
}

$pomContent = $lines -join "`n"
$pomContent | Set-Content "pom.xml" -Encoding UTF8

# ���� plugin.yml
Write-Host "`n���� plugin.yml �汾��..." -ForegroundColor Green
$ymlContent = Get-Content "src/main/resources/plugin.yml" -Encoding UTF8
$ymlContent = $ymlContent -replace "version: '.*?'", "version: '$newVersion'"
$ymlContent = $ymlContent -replace "api-version: '.*?'", "api-version: '$newVersion'"
$ymlContent | Set-Content "src/main/resources/plugin.yml" -Encoding UTF8

# ���Զ�ȡ����� Token ���û���
$tokenPath = ".github_token"
$usernamePath = ".github_username"

if ((Test-Path $tokenPath) -and (Test-Path $usernamePath)) {
    $githubTokenText = Get-Content $tokenPath
    $githubUsername = Get-Content $usernamePath
    Write-Host "�Ѷ�ȡ����� GitHub ����" -ForegroundColor Green
} else {
    # ��ȡ GitHub �û���������
    $githubUsername = Read-Host "��������� GitHub �û���"
    $githubToken = Read-Host "��������� GitHub Token (�� https://github.com/settings/tokens ��ȡ��֮��ᱣ�治���ظ�����)" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($githubToken)
    $githubTokenText = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
    
    # ��������
    $githubTokenText | Set-Content $tokenPath
    $githubUsername | Set-Content $usernamePath
    Write-Host "GitHub �����ѱ��棬�´�������������" -ForegroundColor Green
}

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

# ���͵� GitHub
Write-Host "`n���͵� GitHub..." -ForegroundColor Green
$remoteUrl = "https://${githubUsername}:${githubTokenText}@github.com/${githubUsername}/MCWordGame.git"
git remote set-url origin $remoteUrl
git push -u origin main --force
git push origin --tags --force

# ���� Release
Write-Host "`n���� Release..." -ForegroundColor Green
$jarFile = Get-ChildItem "target/MCWordGame-*.jar" | Select-Object -First 1
if ($jarFile) {
    $releaseData = @{
        tag_name = "v$newVersion"
        name = "Version $newVersion"
        body = "MCWordGame Plugin Version $newVersion"
        draft = $false
        prerelease = $false
    } | ConvertTo-Json

    try {
        # ���� Release
        $release = Invoke-RestMethod `
            -Uri "https://api.github.com/repos/$githubUsername/MCWordGame/releases" `
            -Method Post `
            -Headers @{
                Authorization = "token $githubTokenText"
                Accept = "application/vnd.github.v3+json"
            } `
            -Body $releaseData `
            -ContentType "application/json"

        # �ϴ������õ� jar �ļ�
        $uploadUrl = $release.upload_url -replace "{.*}$", ""
        $fileName = $jarFile.Name
        
        Invoke-RestMethod `
            -Uri "${uploadUrl}?name=${fileName}" `
            -Method Post `
            -Headers @{
                Authorization = "token $githubTokenText"
                Accept = "application/vnd.github.v3+json"
            } `
            -InFile $jarFile.FullName `
            -ContentType "application/java-archive"

        Write-Host "Release �����ɹ���" -ForegroundColor Green
    } catch {
        Write-Host "���� Release ʧ�ܣ�" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
} else {
    Write-Host "δ�ҵ������õ� jar �ļ���" -ForegroundColor Red
}

Write-Host "`n=== ������ɣ�===" -ForegroundColor Green
Write-Host "�汾�Ѹ�����: $newVersion" -ForegroundColor Cyan
Write-Host "��Ĳ���Ѿ��ϴ�����https://github.com/$githubUsername/MCWordGame" -ForegroundColor Yellow
Write-Host "�����õĲ���� target Ŀ¼��" -ForegroundColor Yellow
Write-Host ""

pause 