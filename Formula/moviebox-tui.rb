class MovieboxTui < Formula
  VERSION = "0.1.29"
  MACOS_SHA256 = "5b013b5ec7fe1ccad3eee3280ff51f745d2ad6e6ecfd8777c1ed49dfd95dd64d"
  LINUX_X64_SHA256 = "b13cfb60e7af7ba8218f29f3f9ea74db7f351a90337cbb88ad3103c623b7bda5"
  LINUX_ARM64_SHA256 = "2fef248b68ef25a4887adb3a6cb32055e0de9c2edb45072846e424751acca205"

  desc "Stream movies, shows, anime, and live TV from your terminal"
  homepage "https://github.com/mesamirh/MovieBox-Tui"
  version VERSION
  license any_of: ["MIT", "Apache-2.0"]

  on_macos do
    url "https://github.com/mesamirh/MovieBox-Tui/releases/download/v#{VERSION}/MovieBox_macOS_Universal.tar.gz"
    sha256 MACOS_SHA256
  end

  on_linux do
    if Hardware::CPU.arm?
      url "https://github.com/mesamirh/MovieBox-Tui/releases/download/v#{VERSION}/MovieBox_Linux_arm64.tar.gz"
      sha256 LINUX_ARM64_SHA256
    else
      url "https://github.com/mesamirh/MovieBox-Tui/releases/download/v#{VERSION}/MovieBox_Linux_x64.tar.gz"
      sha256 LINUX_X64_SHA256
    end
  end

  def install
    bin.install "moviebox-tui"
  end

  test do
    system "#{bin}/moviebox-tui", "--version"
  end
end
