Unicode True
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "x64.nsh"
!include "WinVer.nsh"
!ifndef PROJECT_DIR
 !define PROJECT_DIR "${__FILEDIR__}/.."
!endif
!ifndef INPUT_DIR
 !define INPUT_DIR "${PROJECT_DIR}/dist"
!endif
!ifndef OUTPUT_FILE
 !define OUTPUT_FILE "${PROJECT_DIR}/dist/Yoru-Setup.exe"
!endif
Name "Yoru"
OutFile "${OUTPUT_FILE}"
InstallDir "$LOCALAPPDATA\Programs\Yoru"
InstallDirRegKey HKCU "Software\Obscura\Yoru" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
ShowInstDetails show
ShowUninstDetails show
VIProductVersion "0.3.0.0"
VIAddVersionKey /LANG=1033 "ProductName" "Yoru"
VIAddVersionKey /LANG=1033 "CompanyName" "Obscura"
VIAddVersionKey /LANG=1033 "FileDescription" "Yoru Desktop and MovieBox TUI Setup"
VIAddVersionKey /LANG=1033 "FileVersion" "0.3.0"
VIAddVersionKey /LANG=1033 "LegalCopyright" "Yoru by Obscura. Original upstream copyrights retained."
Var StartMenuFolder
!define MUI_ABORTWARNING
!define MUI_ICON "${PROJECT_DIR}/installer/Yoru.ico"
!define MUI_UNICON "${PROJECT_DIR}/installer/Yoru.ico"
!define MUI_WELCOMEPAGE_TITLE "Welcome to Yoru Setup"
!define MUI_WELCOMEPAGE_TEXT "Created by Obscura.$\r$\n$\r$\nThis wizard installs Yoru Desktop, its required backend, and the optional original MovieBox TUI terminal app.$\r$\n$\r$\nWebView2 is checked and downloaded from Microsoft only if missing. VLC is optional and downloaded from VideoLAN only when selected. Internet is needed for these prerequisite steps.$\r$\n$\r$\nClose Yoru, MovieBox TUI, and players started by Yoru before installing or upgrading."
!define MUI_STARTMENUPAGE_REGISTRY_ROOT HKCU
!define MUI_STARTMENUPAGE_REGISTRY_KEY "Software\Obscura\Yoru"
!define MUI_STARTMENUPAGE_REGISTRY_VALUENAME "StartMenuFolder"
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "Launch Yoru"
!define MUI_FINISHPAGE_RUN_FUNCTION "LaunchYoru"
!define MUI_FINISHPAGE_RUN_NOTCHECKED
!define MUI_FINISHPAGE_TEXT "Yoru has been installed.$\r$\n$\r$\nWatch history and favorites stay on this computer. Use VLC or mpv for automatic playback-position tracking.$\r$\n$\r$\nFind creator, provider, and upstream acknowledgements in Settings > About Yoru & Credits."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${PROJECT_DIR}/installer/NOTICE-LICENSE.txt"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_STARTMENU Application $StartMenuFolder
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Function .onInit
 ${IfNot} ${RunningX64}
   MessageBox MB_ICONSTOP "This build needs 64-bit Windows 10 or later."
   Abort
 ${EndIf}
 ${IfNot} ${AtLeastWin10}
   MessageBox MB_ICONSTOP "Yoru requires Windows 10 or later with the current Microsoft WebView2 Runtime."
   Abort
 ${EndIf}
 SetShellVarContext current
FunctionEnd

Section "Microsoft WebView2 (only if missing; internet required)" SEC_RUNTIME
 SectionIn RO
 InitPluginsDir
 SetOutPath "$PLUGINSDIR"
 File /oname=Install-Prerequisite.ps1 "${PROJECT_DIR}/installer/Install-Prerequisite.ps1"
 runtime_retry:
 DetailPrint "Checking the required Microsoft WebView2 Runtime..."
 nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\Install-Prerequisite.ps1" -Kind WebView2'
 Pop $0
 ${If} $0 != "0"
   MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION "The required WebView2 Runtime could not be installed or verified. Check your internet connection, or install the official Microsoft WebView2 Runtime first.$\r$\n$\r$\nNo Yoru application files have been installed yet." IDRETRY runtime_retry
   Abort
 ${EndIf}
SectionEnd

Section "Yoru Desktop and required backend" SEC_DESKTOP
 SectionIn RO
 SetOutPath "$INSTDIR"
 ; Remove only the legacy unfiltered terminal bundled by previous Yoru setup.
 Delete /REBOOTOK "$INSTDIR\moviebox-tui.exe"
 Delete "$DESKTOP\Yoru Terminal (MovieBox TUI).lnk"
 File "${INPUT_DIR}/Yoru.exe"
 File "${INPUT_DIR}/LICENSE-MIT"
 File "${INPUT_DIR}/LICENSE-APACHE"
 File "${INPUT_DIR}/CREDITS.txt"
 File "${INPUT_DIR}/adult_anime_index.json"
 File "${INPUT_DIR}/ANIME-INDEX-NOTICE.txt"
 File "${INPUT_DIR}/ANIME-INDEX-LICENSE.txt"
 File "${INPUT_DIR}/THIRD-PARTY-LICENSES.txt"
 File /oname=README.txt "${PROJECT_DIR}/installer/README.txt"
SectionEnd

Section /o "VLC media player (optional download + official wizard)" SEC_VLC
 DetailPrint "VLC is independent software from VideoLAN; existing standard installs are kept."
 nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\Install-Prerequisite.ps1" -Kind VLC'
 Pop $0
 ${If} $0 != "0"
   MessageBox MB_OK|MB_ICONEXCLAMATION "VLC was skipped, cancelled, or could not be verified. Yoru is still installed. Install VLC from VideoLAN or use an existing mpv installation before playing media."
 ${EndIf}
SectionEnd

Section /o "Desktop shortcuts" SEC_SHORTCUTS
 CreateShortcut "$DESKTOP\Yoru.lnk" "$INSTDIR\Yoru.exe"
SectionEnd

Section -Register
 SetOutPath "$INSTDIR"
 !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
 CreateDirectory "$SMPROGRAMS\$StartMenuFolder"
 Delete "$SMPROGRAMS\$StartMenuFolder\Yoru Terminal (MovieBox TUI).lnk"
 CreateShortcut "$SMPROGRAMS\$StartMenuFolder\Yoru.lnk" "$INSTDIR\Yoru.exe"
 CreateShortcut "$SMPROGRAMS\$StartMenuFolder\About and Credits.lnk" "$INSTDIR\CREDITS.txt"
 CreateShortcut "$SMPROGRAMS\$StartMenuFolder\Uninstall Yoru.lnk" "$INSTDIR\Uninstall.exe"
 !insertmacro MUI_STARTMENU_WRITE_END
 WriteRegStr HKCU "Software\Obscura\Yoru" "InstallDir" "$INSTDIR"
 WriteUninstaller "$INSTDIR\Uninstall.exe"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru" "DisplayName" "Yoru"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru" "Publisher" "Obscura"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru" "DisplayVersion" "0.4.0"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru" "InstallLocation" "$INSTDIR"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru" "DisplayIcon" "$INSTDIR\Yoru.exe"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
 WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru" "NoModify" 1
 WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru" "NoRepair" 1
SectionEnd

Function LaunchYoru
 ExecShell "open" "$INSTDIR\Yoru.exe"
FunctionEnd

Section "Uninstall"
 SetShellVarContext current
 !insertmacro MUI_STARTMENU_GETFOLDER Application $StartMenuFolder
 Delete "$SMPROGRAMS\$StartMenuFolder\Yoru.lnk"
 Delete "$SMPROGRAMS\$StartMenuFolder\Yoru Terminal (MovieBox TUI).lnk"
 Delete "$SMPROGRAMS\$StartMenuFolder\About and Credits.lnk"
 Delete "$SMPROGRAMS\$StartMenuFolder\Uninstall Yoru.lnk"
 RMDir "$SMPROGRAMS\$StartMenuFolder"
 Delete "$DESKTOP\Yoru.lnk"
 Delete "$DESKTOP\Yoru Terminal (MovieBox TUI).lnk"
 Delete /REBOOTOK "$INSTDIR\Yoru.exe"
 Delete /REBOOTOK "$INSTDIR\moviebox-tui.exe"
 Delete "$INSTDIR\LICENSE-MIT"
 Delete "$INSTDIR\LICENSE-APACHE"
 Delete "$INSTDIR\CREDITS.txt"
 Delete "$INSTDIR\adult_anime_index.json"
 Delete "$INSTDIR\ANIME-INDEX-NOTICE.txt"
 Delete "$INSTDIR\ANIME-INDEX-LICENSE.txt"
 Delete "$INSTDIR\THIRD-PARTY-LICENSES.txt"
 Delete "$INSTDIR\README.txt"
 Delete /REBOOTOK "$INSTDIR\Uninstall.exe"
 RMDir "$INSTDIR"
 DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Yoru"
 DeleteRegKey HKCU "Software\Obscura\Yoru"
 ; Deliberately retain watch history, favorites, settings and separately installed VLC/WebView2.
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
 !insertmacro MUI_DESCRIPTION_TEXT ${SEC_RUNTIME} "Required web runtime. Only downloaded and installed from Microsoft when missing."
 !insertmacro MUI_DESCRIPTION_TEXT ${SEC_DESKTOP} "Yoru by Obscura: desktop interface, embedded provider backend, credits, and license notices."
 !insertmacro MUI_DESCRIPTION_TEXT ${SEC_VLC} "Optional official VideoLAN installer. Checks SHA256 and publisher signature, then shows VLC's own wizard. May request administrator permission."
 !insertmacro MUI_DESCRIPTION_TEXT ${SEC_SHORTCUTS} "Create desktop shortcuts for installed Yoru applications. Start menu shortcuts are always included."
!insertmacro MUI_FUNCTION_DESCRIPTION_END
