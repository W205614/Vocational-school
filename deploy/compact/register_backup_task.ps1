param(
    [string]$HomeDirectory = $PSScriptRoot,
    [string]$Project = '',
    [int]$PortOffset = 0,
    [string]$PythonPath = (Get-Command python -ErrorAction Stop).Source
)
$ErrorActionPreference = 'Stop'
$resolvedHome = (Resolve-Path -LiteralPath $HomeDirectory).Path
$Project = & $PythonPath -c 'import sys; sys.path.insert(0,sys.argv[1]); from runtime_compose import resolve_project; print(resolve_project(sys.argv[2],sys.argv[3] or None))' $PSScriptRoot $resolvedHome $Project
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve deployment project identity' }
if ($Project -notmatch '^[a-z][a-z0-9-]{2,40}$') { throw 'Invalid project name' }
if (-not ((Test-Path -LiteralPath (Join-Path $resolvedHome '.local/compose.runtime.yaml')) -or (Test-Path -LiteralPath (Join-Path $resolvedHome 'compose.yaml')))) { throw 'Existing deployment required' }
if ((Get-TimeZone).Id -ne 'China Standard Time') { throw 'Task host timezone must be China Standard Time (Asia/Shanghai)' }
$taskName = 'Zhixuetang-Backup-' + $Project
$legacyTaskName = 'Tianji-Backup-' + $Project
if (Get-ScheduledTask -TaskName $legacyTaskName -ErrorAction SilentlyContinue) { $taskName = $legacyTaskName }
$resolvedPython = (Resolve-Path -LiteralPath $PythonPath).Path
$windowlessPython = Join-Path (Split-Path -Parent $resolvedPython) 'pythonw.exe'
if (-not (Test-Path -LiteralPath $windowlessPython -PathType Leaf)) {
    throw 'Scheduled backup requires pythonw.exe beside PythonPath to avoid opening terminal windows'
}
$driver = Join-Path $PSScriptRoot 'scheduled_backup.py'
$arguments = '"' + $driver + '" --home "' + $resolvedHome.TrimEnd('\') + '" --project "' + $Project + '" --port-offset ' + $PortOffset + ' --python "' + $resolvedPython + '"'
$action = New-ScheduledTaskAction -Execute $windowlessPython -Argument $arguments -WorkingDirectory $PSScriptRoot
$daily = New-ScheduledTaskTrigger -Daily -At '03:30'
# The process exits after each check; retries only catch up a missing full backup.
$repeat = New-ScheduledTaskTrigger -Once -At (Get-Date).Date.AddDays(1).AddHours(3).AddMinutes(30) -RepetitionInterval (New-TimeSpan -Minutes 15)
$daily.Repetition = $repeat.Repetition
$daily.Repetition.Duration = 'P1D'
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 4) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $daily -Settings $settings -Principal $principal -Description '03:30 Asia/Shanghai verified backup; skips while Docker is off; restores original service state; no stored password.' -Force | Select-Object TaskName, State
