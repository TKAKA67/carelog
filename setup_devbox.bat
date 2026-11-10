@echo off
REM Pleos Playground 프로젝트 CRN을 에뮬레이터(데브박스)에 등록합니다.
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
"%ADB%" root
timeout /t 3 >nul
"%ADB%" shell su 0 "echo 'propId: 554696961 areaId: 0 values: did:cac:42dot:system:devbox::cbb8239a-4c0d-48ee-bd13-ee4d535eb939' > /data/vendor/vsomeip/vhal_fifo"
"%ADB%" reboot
echo Done. Emulator is rebooting.
pause
