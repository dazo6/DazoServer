@echo off
cd /d D:\DazoServer
"C:\Program Files\Java\jdk1.8.0_202\bin\java.exe" -Xmx3000m -jar DazoServer-1.0-SNAPSHOT.jar --spring.config.additional-location=D:\DazoServer\application.properties
exit /b %errorlevel%
