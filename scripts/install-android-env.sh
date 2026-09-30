#!/bin/bash
# ============================================================
# 安卓开发环境一键安装脚本（CachyOS / Arch 系）
# 生成日期: 2026-09-16
# 用法:    bash scripts/install-android-env.sh
#          （以普通用户运行，脚本会在需要 sudo 的地方提示输密码）
# 对应文档: docs/06-工具链安装清单.md（路线 A + SDK 命令行预装）
# 预计耗时: 15~30 分钟（AUR 编译下载约 1.3GB + SDK 约 1GB）
# ============================================================
set -euo pipefail

say()  { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
warn() { printf '\n\033[1;33m!! %s\033[0m\n' "$*"; }

# 禁止用 sudo 运行整个脚本（第 4 步的 yay 不允许以 root 执行）
if [[ $EUID -eq 0 ]]; then
  warn "请不要用 sudo 运行本脚本；直接 ./scripts/install-android-env.sh，脚本内部会自动调用 sudo"
  exit 1
fi

# ------------------------------------------------------------
say "第 1/5 步：安装官方源软件包（会提示输入 sudo 密码）"
sudo pacman -S --needed --noconfirm base-devel git unzip \
    jdk21-openjdk android-tools android-udev

say "第 2/5 步：把系统默认 Java 切到 JDK 21"
sudo archlinux-java set java-21-openjdk
java -version

say "第 3/5 步：把你加入 adbusers 组（真机调试免 root）"
sudo usermod -aG adbusers "$USER"

say "第 4/5 步：从 AUR 编译安装 Android Studio（时间最长的一步）"
yay -S --needed --noconfirm android-studio

say "第 5/5 步：预装 Android SDK 到 ~/Android/Sdk（无需 sudo）"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
cd "$HOME/Android/Sdk/cmdline-tools"
if [[ ! -d latest ]]; then
  curl -fL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
  unzip -q cmdtools.zip
  mv cmdline-tools latest
  rm cmdtools.zip
fi
SDKMANAGER="$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager"
# yes 会被 SIGPIPE 结束，pipefail 下需 || true 兜底
yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"

say "写入环境变量（bash / zsh / fish，已有则跳过）"
ENV_BLOCK='
# === Android 开发环境（由 install-android-env.sh 添加）===
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
# === Android 环境结束 ==='
for rc in "$HOME/.bashrc" "$HOME/.zshrc"; do
  if [[ -f $rc ]] && ! grep -q "ANDROID_HOME" "$rc"; then
    printf '%s\n' "$ENV_BLOCK" >> "$rc"
    echo "  已写入 $rc"
  fi
done
FISH_CFG="$HOME/.config/fish/config.fish"
if command -v fish >/dev/null 2>&1 && [[ -f $FISH_CFG ]] && ! grep -q "ANDROID_HOME" "$FISH_CFG"; then
  cat >> "$FISH_CFG" <<'EOF'

# === Android 开发环境（由 install-android-env.sh 添加）===
set -gx JAVA_HOME /usr/lib/jvm/java-21-openjdk
set -gx ANDROID_HOME $HOME/Android/Sdk
set -gx ANDROID_SDK_ROOT $ANDROID_HOME
fish_add_path $ANDROID_HOME/platform-tools $ANDROID_HOME/cmdline-tools/latest/bin
# === Android 环境结束 ===
EOF
  echo "  已写入 $FISH_CFG"
fi

say "验收输出（请把这一段完整回传给主 agent）"
set +e
{
  echo "--- java -version"
  java -version 2>&1
  echo "--- adb version"
  adb version 2>&1 | head -2
  echo "--- sdkmanager --list_installed"
  "$SDKMANAGER" --list_installed 2>/dev/null
  echo "--- git --version"
  git --version
}
echo
say "全部完成！"
echo "  · Android Studio 启动: android-studio（首次向导一路默认即可，会识别已装好的 ~/Android/Sdk）"
echo "  · adbusers 组需【注销并重新登录】后才生效（影响真机调试）"
echo "  · 新开终端后 JAVA_HOME / adb / sdkmanager 才在 PATH 里生效"
