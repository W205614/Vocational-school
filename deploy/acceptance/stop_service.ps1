param([Parameter(Mandatory=$true)][ValidatePattern('^[a-z]+$')][string]$Service)
$ErrorActionPreference='Stop'
$metadata=Join-Path $PSScriptRoot ('.local/'+$Service+'-pid.json')
if(Test-Path -LiteralPath $metadata){
 $record=Get-Content -LiteralPath $metadata -Raw|ConvertFrom-Json
 $process=Get-CimInstance Win32_Process -Filter ('ProcessId = '+[int]$record.pid)
 if($process){
  $expected='@'+[IO.Path]::GetFullPath((Join-Path $PSScriptRoot ('.local/'+$Service+'-java.args')))
  if($process.Name -ne 'java.exe' -or !$process.CommandLine.Contains($expected)){Write-Output ('Recorded PID changed; skipped isolated '+$Service); exit 0}
  Stop-Process -Id $process.ProcessId
  Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
  Write-Output ('Stopped isolated '+$Service)
 }
}
