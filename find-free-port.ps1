$listener = $null

foreach ($port in 8080..8090) {
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $port)
        $listener.Start()
        $listener.Stop()
        Write-Output $port
        exit 0
    } catch {
        if ($listener) {
            $listener.Stop()
            $listener = $null
        }
    }
}

exit 1
