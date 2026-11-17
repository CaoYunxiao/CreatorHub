$ErrorActionPreference = 'Stop'

$pageUrl = 'http://127.0.0.1:8080/app/index.html'
$backend = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
$build = Join-Path $backend 'target'
$jar = Join-Path $build 'backend-0.0.1-SNAPSHOT.jar'
$java = 'E:\JDK\bin\java.exe'
$startupLock = [System.Threading.Mutex]::new($false, 'Local\CreatorHub-8080-Launcher')
$ownsLock = $false
$javaProcess = $null
$started = $false

function Test-CreatorHubPage {
    try {
        $request = [System.Net.HttpWebRequest]::Create($pageUrl)
        $request.Proxy = $null
        $request.AllowAutoRedirect = $false
        $request.Timeout = 1200
        $request.ReadWriteTimeout = 1200
        $response = $request.GetResponse()
        try {
            if ([int]$response.StatusCode -ne 200 -or $response.ContentType -notlike 'text/html*') {
                return $false
            }
            $reader = [System.IO.StreamReader]::new($response.GetResponseStream())
            try {
                $buffer = [char[]]::new(4096)
                $length = $reader.ReadBlock($buffer, 0, $buffer.Length)
                $html = [string]::new($buffer, 0, $length)
                return $html.Contains('<title>CreatorHub') -and $html.Contains('id="works-view"')
            }
            finally { $reader.Dispose() }
        }
        finally { $response.Dispose() }
    }
    catch [System.Net.WebException] {
        if ($_.Exception.Response) { $_.Exception.Response.Dispose() }
        return $false
    }
    catch [System.IO.IOException] { return $false }
}

function Test-PortAvailable {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 8080)
    try {
        $listener.ExclusiveAddressUse = $true
        $listener.Start()
        return $true
    }
    catch [System.Net.Sockets.SocketException] { return $false }
    finally { $listener.Stop() }
}

function Open-CreatorHubPage {
    Write-Host "CreatorHub 页面  $pageUrl"
    try { Start-Process $pageUrl }
    catch { Write-Warning '浏览器未自动打开，请复制上面的地址。' }
}

try {
    try { $ownsLock = $startupLock.WaitOne(0) }
    catch [System.Threading.AbandonedMutexException] { $ownsLock = $true }
    if (Test-CreatorHubPage) {
        Open-CreatorHubPage
        return
    }
    if (-not $ownsLock) {
        throw '另一个启动窗口正在启动 CreatorHub，请等它打开浏览器。'
    }
    if (-not (Test-PortAvailable)) {
        throw '8080 端口已被占用，且未识别到 CreatorHub 页面。请检查该端口后重试。'
    }
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
        throw "找不到 E 盘 JDK  $java"
    }
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "找不到后端 JAR  $jar。请按 backend/README.md 先打包。"
    }

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $java
    $startInfo.WorkingDirectory = $backend
    # 将 Java 临时文件和 Windows NIO 套接字放在项目构建目录。
    $startInfo.Arguments = '"-Djava.io.tmpdir=' + $build + '" "-Djdk.net.unixdomain.tmpdir=' + $build +
        '" -jar "' + $jar + '" --server.address=127.0.0.1 --server.port=8080 --spring.sql.init.mode=never'
    $startInfo.UseShellExecute = $false
    $startInfo.EnvironmentVariables['CREATORHUB_DB_URL'] = 'jdbc:mysql://localhost:3306/creatorhub'
    $startInfo.EnvironmentVariables['CREATORHUB_DB_USERNAME'] = 'creatorhub_app'
    $javaProcess = [System.Diagnostics.Process]::new()
    $javaProcess.StartInfo = $startInfo

    $secret = Read-Host '输入 creatorhub_app 的 MySQL 密码' -AsSecureString
    try {
        if ($secret.Length -eq 0) { throw '请输入应用账户密码。' }
        $startInfo.EnvironmentVariables['CREATORHUB_DB_PASSWORD'] =
            [System.Net.NetworkCredential]::new('', $secret).Password
        $started = $javaProcess.Start()
        if (-not $started) { throw '未能启动 Java。' }
    }
    finally {
        $startInfo.EnvironmentVariables.Remove('CREATORHUB_DB_PASSWORD')
        $secret.Dispose()
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        if ($javaProcess.HasExited) {
            throw "后端启动失败（Java 退出码 $($javaProcess.ExitCode)），请查看上面的启动日志。"
        }
        $ready = Test-CreatorHubPage
        if (-not $ready) { Start-Sleep -Milliseconds 250 }
    } until ($ready -or [DateTime]::UtcNow -ge $deadline)
    if (-not $ready -or $javaProcess.HasExited) {
        throw '未在 45 秒内确认 CreatorHub 页面就绪，请查看启动日志。'
    }

    $startupLock.ReleaseMutex()
    $ownsLock = $false
    Open-CreatorHubPage
    Write-Host '保持此窗口打开。停止 CreatorHub 时请按 Ctrl+C。'
    while (-not $javaProcess.WaitForExit(500)) {}
    if ($javaProcess.ExitCode -notin @(0, 130, -1073741510)) {
        throw "后端已退出（Java 退出码 $($javaProcess.ExitCode)），请查看上面的日志。"
    }
}
catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
finally {
    if ($ownsLock) { $startupLock.ReleaseMutex() }
    $startupLock.Dispose()
    if ($javaProcess) {
        try {
            if ($started -and -not $javaProcess.WaitForExit(5000)) {
                $javaProcess.Kill()
                $javaProcess.WaitForExit()
            }
        }
        finally { $javaProcess.Dispose() }
    }
}
