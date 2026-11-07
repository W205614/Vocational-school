param([string]$HomeDirectory, [string]$Project, [int]$PortOffset = 0, [string]$PythonPath)
$ErrorActionPreference = 'Stop'
$env:ZXT_COMPACT_HOME = $HomeDirectory
$env:ZXT_COMPACT_PROJECT = $Project
$env:ZXT_COMPACT_PORT_OFFSET = [string]$PortOffset
# A scheduled process needs Docker/JDK paths even when the shell profile was not loaded.
$env:PATH = (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin') + ';' + $env:PATH
& $PythonPath (Join-Path $PSScriptRoot 'maintenance.py') scheduled
exit $LASTEXITCODE
