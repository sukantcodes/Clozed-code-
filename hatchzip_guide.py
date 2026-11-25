#!/usr/bin/env python3
"""
HatchZip on Windows — Download, Install & Run Guide (Interactive Python Script)
===============================================================================
A self-contained Python guide explaining what HatchZip is, how to safely
download and install it on Windows 10/11 (x64), and how to run it via
File Explorer, the Desktop GUI, or the Command Line (CLI).

Usage:
    python hatchzip_guide.py          # Interactive menu
    python hatchzip_guide.py --all    # Print complete guide and exit
"""

import platform
import shutil
import subprocess
import sys
import webbrowser

OFFICIAL_WEBSITE = "https://hatchzip.dev/"
GITHUB_RELEASES = "https://github.com/xevrion/hatchzip/releases"
POWERSHELL_INSTALL_CMD = "irm https://raw.githubusercontent.com/xevrion/hatchzip/main/install.ps1 | iex"

DIVIDER = "=" * 72
SUBDIV = "-" * 72


SECTIONS = {
    "1": (
        "What is HatchZip?",
        f"""
HatchZip is a free, open-source archive extractor for Windows (10 & 11),
macOS, and Linux with both a desktop GUI app and a command-line tool.

Key Highlights:
  * In-Place Extraction: Shrinks the archive as files extract, so you only
    need free disk space for the extracted files — NOT double the space.
  * Supported Formats: ZIP (including ZIP64 & self-extracting .exe), 7z,
    RAR (RAR4/RAR5), tar, .tar.gz / .tgz, .tar.xz / .txz, and split
    archives (.001, .002, .part1.rar, etc.).
  * Safe & Crash-Resistant: Checks available disk space before touching a
    single byte, verifies CRC-32 checksums, keeps a 2 GB free-space safety
    margin by default, and resumes cleanly after Ctrl+C or a power cut.
  * Optional Keep-Archive Mode: Can extract normally without deleting the
    original archive whenever you have enough free disk space.

Official Sources ONLY:
  * Website : {OFFICIAL_WEBSITE}
  * GitHub  : https://github.com/xevrion/hatchzip
  * WARNING : Avoid unofficial lookalike domains (e.g., hatchzip.com).
"""
    ),
    "2": (
        "How to Download & Install HatchZip on Windows 10/11 (x64)",
        f"""
Choose ANY ONE of the following official installation methods:

METHOD A — Standard Windows Installer (Recommended)
  1. Open {OFFICIAL_WEBSITE} or {GITHUB_RELEASES}
  2. Download: HatchZip-x64-setup.exe
     - Installs per user (no admin rights required).
     - Adds HatchZip to the File Explorer right-click menu.
     - Adds 'hatchzip' (and short alias 'hz') to your system PATH.
  3. Double-click HatchZip-x64-setup.exe to run the installer.
  4. Windows SmartScreen Note:
     If Windows shows "Windows protected your PC",
     click "More info", then click "Run anyway".

METHOD B — Portable Version (No Installation Required)
  1. Download HatchZip-x64-portable.zip from {GITHUB_RELEASES}
  2. Unzip it into any folder and double-click: hatchzip-gui.exe
  3. Optional: Inside the app, go to Settings > Integration and enable
     "Show in the Explorer context menu".

METHOD C — MSI Package (IT / Silent Install)
  1. Download HatchZip-x64.msi from {GITHUB_RELEASES}
  2. Double-click to install, OR run silently in Command Prompt:
     msiexec /i HatchZip-x64.msi /qn

METHOD D — Windows PowerShell One-Line Install
  Open PowerShell and run:
     {POWERSHELL_INSTALL_CMD}

METHOD E — Package Managers (Chocolatey / Scoop / Cargo)
  * Chocolatey (App + CLI):
      choco install hatchzip
  * Scoop (CLI):
      scoop bucket add xevrion https://github.com/xevrion/scoop-bucket
      scoop install xevrion/hatchzip
  * Rust / Cargo (CLI):
      cargo binstall hatchzip
"""
    ),
    "3": (
        "How to Run & Use HatchZip on Windows",
        """
Once installed, you can run HatchZip in three ways:

1. FILE EXPLORER RIGHT-CLICK MENU (Easiest)
   * Right-click any .zip, .7z, .rar, .tar.gz, or split archive.
   * On Windows 11: Click "Show more options" first.
   * Hover over "HatchZip" and choose:
       - "Extract here"               : Extracts in place (removes archive
                                        as files extract to save space).
       - "Extract files..."           : Pick a custom destination folder.
       - "Extract here (keep archive)": Extracts normally & keeps the archive.
   * Tip: To always keep original archives by default, open HatchZip
     Settings and uncheck "Remove the archive during extraction".

2. DESKTOP GUI APPLICATION
   * Press the Windows key, type "HatchZip", and press Enter
     (or run hatchzip-gui.exe if using the portable version).
   * Open an archive or drag-and-drop it onto the HatchZip window.
   * Preview the folder tree and disk-space plan before anything changes.
   * Click "Extract".

3. COMMAND LINE (PowerShell / Command Prompt)
   The installer adds both 'hatchzip' and its short alias 'hz' to PATH.

   * Check if an archive fits without modifying anything:
       hatchzip check game.zip

   * Extract an archive in place (frees space as it extracts):
       hatchzip extract game.zip
       (or short form: hz extract game.zip)

   * Extract normally and KEEP the original archive:
       hatchzip extract --keep-archive game.zip

   * Skip files that already exist in the destination:
       hatchzip extract --skip-existing game.zip

   * Change the free-space safety margin (default is 2 GB):
       hatchzip extract --keep-free 5G game.zip

   * Password-protected archives:
       hatchzip extract secret.7z
       (Prompts safely and verifies the password before deleting anything.)

   * Resume an interrupted extraction (after Ctrl+C or a crash):
       Run the exact same command again:
       hatchzip extract game.zip
"""
    ),
    "4": (
        "Verify Your Download (Security Check)",
        r"""
HatchZip never connects to the internet, and every official release
includes a SHA-256 checksum and signed GitHub build provenance.

In Windows PowerShell, verify the SHA-256 hash of your installer:
    Get-FileHash .\HatchZip-x64-setup.exe -Algorithm SHA256

Compare the output hash against the matching .sha256 file listed on:
    https://github.com/xevrion/hatchzip/releases

If you have GitHub CLI (gh) installed, you can also verify provenance:
    gh attestation verify HatchZip-x64-setup.exe --repo xevrion/hatchzip
"""
    ),
}


def print_section(key: str) -> None:
    title, body = SECTIONS[key]
    print(f"\n{DIVIDER}")
    print(f"  {key}. {title.upper()}")
    print(DIVIDER)
    print(body.strip())
    print()


def print_full_guide() -> None:
    print(DIVIDER)
    print("  HATCHZIP FOR WINDOWS — COMPLETE DOWNLOAD, INSTALL & RUN GUIDE")
    print(DIVIDER)
    for key in sorted(SECTIONS.keys()):
        print_section(key)


def check_installation() -> None:
    print(f"\n{SUBDIV}")
    print(f"Detected OS: {platform.system()} {platform.release()} ({platform.machine()})")
    exe = shutil.which("hatchzip") or shutil.which("hz")
    if exe:
        print(f"[OK] HatchZip CLI found at: {exe}")
        try:
            res = subprocess.run([exe, "--version"], capture_output=True, text=True, timeout=5)
            out = (res.stdout or res.stderr).strip()
            if out:
                print(f"     Version: {out}")
        except Exception as err:
            print(f"     Could not read version: {err}")
    else:
        print("[!] 'hatchzip' / 'hz' was NOT found in PATH.")
        print("    Install it using Option 2 in this guide, then restart your terminal.")
    print(f"{SUBDIV}\n")


def run_windows_powershell_installer() -> None:
    if platform.system() != "Windows":
        print(f"\n[!] You are currently on {platform.system()}, not Windows.")
        print("    Copy and run this command inside Windows PowerShell instead:")
        print(f"    {POWERSHELL_INSTALL_CMD}\n")
        return

    print("\nAbout to run the official HatchZip PowerShell installer:")
    print(f"  {POWERSHELL_INSTALL_CMD}")
    confirm = input("Proceed? (y/N): ").strip().lower()
    if confirm != "y":
        print("Cancelled.\n")
        return

    try:
        subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", POWERSHELL_INSTALL_CMD],
            check=False,
        )
    except FileNotFoundError:
        print("[!] PowerShell was not found. Download HatchZip-x64-setup.exe from https://hatchzip.dev/\n")


def interactive_menu() -> None:
    while True:
        print(DIVIDER)
        print("  HATCHZIP WINDOWS GUIDE — INTERACTIVE MENU")
        print(DIVIDER)
        print("  1. What is HatchZip?")
        print("  2. How to Download & Install on Windows (EXE / Portable / CLI)")
        print("  3. How to Run & Use HatchZip (Right-Click / GUI / Commands)")
        print("  4. How to Verify Download Checksum (SHA-256)")
        print("  5. Print Complete Guide (All Sections)")
        print("  6. Check if HatchZip is Installed on This PC")
        print("  7. Open Official HatchZip Download Page in Browser")
        print("  8. Run Official Windows PowerShell Installer")
        print("  0. Exit")
        print(SUBDIV)

        choice = input("Select an option (0-8): ").strip()

        if choice in SECTIONS:
            print_section(choice)
        elif choice == "5":
            print_full_guide()
        elif choice == "6":
            check_installation()
        elif choice == "7":
            print(f"Opening {OFFICIAL_WEBSITE} ...")
            webbrowser.open(OFFICIAL_WEBSITE)
        elif choice == "8":
            run_windows_powershell_installer()
        elif choice == "0":
            print("Goodbye!")
            break
        else:
            print("Invalid choice. Please enter a number between 0 and 8.\n")


def main() -> None:
    if "--all" in sys.argv:
        print_full_guide()
    else:
        interactive_menu()


if __name__ == "__main__":
    main()
