param(
    [string]$OutputJar = (Join-Path $PSScriptRoot 'build/hex-ai.jar')
)

$ErrorActionPreference = 'Stop'
$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
$javac = if ($javaBin) { Join-Path $javaBin 'javac.exe' } else { 'javac' }
$java = if ($javaBin) { Join-Path $javaBin 'java.exe' } else { 'java' }
$jar = if ($javaBin) { Join-Path $javaBin 'jar.exe' } else { 'jar' }
$core = Join-Path $PSScriptRoot 'libs/hex-core.jar'
$gson = Join-Path $PSScriptRoot '../Hex/app/libs/gson-2.2.4.jar'
$main = Join-Path $PSScriptRoot 'build/main-classes'
$tests = Join-Path $PSScriptRoot 'build/test-classes'
New-Item -ItemType Directory -Force -Path $main, $tests | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/com/hex/ai') -Filter '*.java' | ForEach-Object FullName)
& $javac -cp "$core;$gson" -d $main $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests/com/hex/ai') -Filter '*.java' | ForEach-Object FullName)
& $javac -cp "$main;$core;$gson" -d $tests $testSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
foreach ($test in @('TreeGameAITest', 'TreeTournamentTest')) {
    & $java -ea -cp "$tests;$main;$core;$gson" "com.hex.ai.$test"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputJar) | Out-Null
# Package only AI classes. Bundling an old hex-core class can shadow the app's core JAR.
& $jar --create --file $OutputJar -C $main com/hex/ai
exit $LASTEXITCODE
