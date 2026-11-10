<#
.SYNOPSIS
  공개 지식 조각(JSON)을 보안길잡이 서버에 일괄 등록한다 (POST /api/knowledge-snippets, ADMIN).

.DESCRIPTION
  - 비밀번호는 실행 중에 입력받는다(스크립트·명령줄·기록에 남기지 않는다).
  - 이미 같은 출처(제목·판본·위치)가 있으면 건너뛴다 → 여러 번 실행해도 중복되지 않는다.
  - -DeactivateSynthetic 을 주면 제목에 "(합성 예시)" 가 있는 기존 조각을 비활성화한다.
  - -DryRun 을 주면 서버를 바꾸지 않고 등록될 건수만 보여준다.

.EXAMPLE
  .\scripts\load-knowledge-snippets.ps1 -Username admin
  .\scripts\load-knowledge-snippets.ps1 -Username admin -DeactivateSynthetic
  .\scripts\load-knowledge-snippets.ps1 -Username admin -BaseUrl http://localhost:8080 -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Username,
    [string]$BaseUrl = 'http://localhost:8080',
    [string[]]$Files = @(
        (Join-Path $PSScriptRoot '..\docs\knowledge\knowledge_snippets_nis_guideline.json'),
        (Join-Path $PSScriptRoot '..\docs\knowledge\knowledge_snippets_public.json')
    ),
    [switch]$DeactivateSynthetic,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$utf8 = New-Object System.Text.UTF8Encoding($false)
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Get-CsrfToken {
    $cookie = $session.Cookies.GetCookies($BaseUrl) | Where-Object { $_.Name -eq 'XSRF-TOKEN' } | Select-Object -First 1
    if (-not $cookie) { throw 'XSRF-TOKEN 쿠키를 받지 못했습니다. 서버 주소를 확인하세요.' }
    return $cookie.Value
}

# 응답을 항상 UTF-8 로 해석한다(Windows PowerShell 5.1 은 charset 이 없으면 한글이 깨진다).
function Invoke-Api {
    param([string]$Method, [string]$Path, $Body)
    $req = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        WebSession      = $session
        UseBasicParsing = $true
        Headers         = @{ Accept = 'application/json'; 'X-Requested-With' = 'XMLHttpRequest' }
    }
    if ($Method -ne 'GET') {
        $req.Headers['X-XSRF-TOKEN'] = Get-CsrfToken
        if ($null -ne $Body) {
            $req.ContentType = 'application/json; charset=utf-8'
            $req.Body = $utf8.GetBytes((ConvertTo-Json -InputObject $Body -Depth 6 -Compress))
        }
    }
    $response = Invoke-WebRequest @req
    $text = $utf8.GetString($response.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return ConvertFrom-Json -InputObject $text
}

function Get-ErrorText($err) {
    if ($err.ErrorDetails -and $err.ErrorDetails.Message) { return $err.ErrorDetails.Message }
    try {
        $stream = $err.Exception.Response.GetResponseStream()
        return (New-Object System.IO.StreamReader($stream, $utf8)).ReadToEnd()
    } catch { return $err.Exception.Message }
}

function Get-Key($s) { return "$($s.sourceTitle)|$($s.sourceVersion)|$($s.sourceLocator)" }

# 1) CSRF 쿠키 받기 (로그인 전이므로 401 은 정상)
try { Invoke-Api GET '/api/auth/me' | Out-Null } catch { }

# 2) 로그인 — 비밀번호는 입력받아 메모리에서만 쓴다
$secure = Read-Host -Prompt "$Username 비밀번호" -AsSecureString
$plain = (New-Object System.Net.NetworkCredential('', $secure)).Password
try {
    $me = Invoke-Api POST '/api/auth/login' @{ username = $Username; password = $plain }
} catch {
    throw "로그인 실패: $(Get-ErrorText $_)"
} finally {
    $plain = $null
}
if ($me.role -ne 'ADMIN') { throw "기관 관리자(ADMIN) 계정이 필요합니다. 현재 역할: $($me.role)" }
Invoke-Api GET '/api/auth/me' | Out-Null   # 로그인 뒤 바뀐 CSRF 쿠키 갱신
Write-Host "로그인: $($me.displayName) ($($me.role))"

# 3) 기존 조각 목록
$listed = Invoke-Api GET '/api/knowledge-snippets'
$existing = @($listed | Where-Object { $_ })
$known = @{}
foreach ($s in $existing) { $known[(Get-Key $s)] = $true }
Write-Host "기존 조각 $($existing.Count)건"

# 4) 합성 예시 비활성화(선택)
if ($DeactivateSynthetic) {
    $targets = @($existing | Where-Object { $_.active -and $_.sourceTitle -like '*(합성 예시)*' })
    Write-Host "합성 예시 비활성화 대상 $($targets.Count)건"
    foreach ($s in $targets) {
        if ($DryRun) { continue }
        $body = @{
            sourceTitle = $s.sourceTitle; sourceVersion = $s.sourceVersion; sourceLocator = $s.sourceLocator
            fieldKeys = @($s.fieldKeys); docTypes = @($s.docTypes); body = $s.body
            active = $false; expectedRevision = $s.revision
        }
        try { Invoke-Api PUT "/api/knowledge-snippets/$($s.knowledgeSnippetId)" $body | Out-Null }
        catch { Write-Warning "비활성화 실패 #$($s.knowledgeSnippetId): $(Get-ErrorText $_)" }
    }
}

# 5) 등록
$created = 0; $skipped = 0; $failed = 0
foreach ($file in $Files) {
    $path = (Resolve-Path $file).Path
    $parsed = ConvertFrom-Json -InputObject ([System.IO.File]::ReadAllText($path, $utf8))
    $items = @($parsed | Where-Object { $_ })
    Write-Host "`n$([System.IO.Path]::GetFileName($path)) — $($items.Count)건"
    foreach ($item in $items) {
        $key = Get-Key $item
        if ($known.ContainsKey($key)) { $skipped++; continue }
        if ($DryRun) { $created++; $known[$key] = $true; continue }
        $body = @{
            sourceTitle = $item.sourceTitle; sourceVersion = $item.sourceVersion; sourceLocator = $item.sourceLocator
            fieldKeys = @($item.fieldKeys); docTypes = @($item.docTypes); body = $item.body; active = $true
        }
        try {
            Invoke-Api POST '/api/knowledge-snippets' $body | Out-Null
            $created++; $known[$key] = $true
        } catch {
            $failed++
            Write-Warning "실패 [$($item.sourceLocator)]: $(Get-ErrorText $_)"
        }
    }
}

$label = if ($DryRun) { '등록 예정' } else { '등록' }
Write-Host "`n$label $created건, 건너뜀(이미 있음) $skipped건, 실패 $failed건"

# 6) 로그아웃
try { Invoke-Api POST '/api/auth/logout' | Out-Null } catch { }
if ($failed -gt 0) { exit 1 }
