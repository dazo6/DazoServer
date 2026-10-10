param(
    [string]$ConfigPath = 'D:/DazoServer/application.properties',
    [ValidateSet('Preview','Apply','Verify')][string]$Mode = 'Preview',
    [string]$BackupDirectory = '',
    [string]$JavaHome = 'C:/Program Files/Java/jdk1.8.0_202',
    [string]$DriverJar = "$env:USERPROFILE/.m2/repository/mysql/mysql-connector-java/8.0.28/mysql-connector-java-8.0.28.jar"
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$classes = Join-Path $repository 'target/crawler-artist-migration'
if (!$BackupDirectory) { $BackupDirectory = Join-Path $repository 'target/crawler-artist-backups' }
if (!(Test-Path -LiteralPath $DriverJar)) { throw 'MySQL JDBC driver not found; specify -DriverJar' }
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -d $classes (Join-Path $repository 'src/main/java/com/dazo66/util/CrawlerArtistUrl.java') (Join-Path $repository 'src/main/java/com/dazo66/util/CrawlerRequestArtistSchema.java') (Join-Path $PSScriptRoot 'CrawlerRequestArtistMigration.java')
if ($LASTEXITCODE -ne 0) { throw 'Migration compilation failed' }
& "$JavaHome/bin/java.exe" -cp "$classes;$DriverJar" CrawlerRequestArtistMigration $ConfigPath $Mode $BackupDirectory
if ($LASTEXITCODE -ne 0) { throw 'Migration failed; already committed batches have rollback SQL in BackupDirectory' }
