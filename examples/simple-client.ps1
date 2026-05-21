Write-Output "AIMS Sample Client"
[Console]::Out.Flush()

while (($line = [Console]::In.ReadLine()) -ne $null) {
    if ($line -eq "#end") {
        break
    }
}

$actions = @(
    "Move(S)",
    "Pull(N,W)",
    "Push(S,S)"
)

foreach ($action in $actions) {
    Write-Output $action
    [Console]::Out.Flush()
    $response = [Console]::In.ReadLine()
    if ($null -eq $response) {
        break
    }
}
