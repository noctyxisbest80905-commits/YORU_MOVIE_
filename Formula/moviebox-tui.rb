class MovieboxTui < Formula
  VERSION = "0.1.28"
  MACOS_SHA256 = "8ba927e2273012a7abf48ce265210eafca586786a5c5bbd4909612b23e2c191b"
  LINUX_X64_SHA256 = "aedf2371ef0e4c8a517250dd926d5c1c2aedadbf3f3c12eb62d92247328fea5a"
  LINUX_ARM64_SHA256 = "4deaf277eba53aba49720e15d5e7ae3b77c7817936b8b6034f0b8327284618c6"

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
