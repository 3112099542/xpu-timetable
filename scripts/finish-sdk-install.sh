#!/bin/bash
# ============================================================
# 补完脚本：修复 SDK 包名并完成剩余安装（无需 sudo，约 2~3 分钟）
# 原因: 官方仓库里 API 37 平台包的实际路径是 platforms;android-37.0
#       （Google 自 36.1 起给平台路径加了小数位），
#       install-android-env.sh 里的 platforms;android-37 不存在导致其第 5 步中止。
# 用法: bash scripts/finish-sdk-install.sh
# ============================================================
set -euo pipefail

say() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
SDKMANAGER="$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager"

say "第 1/3 步：接受 SDK 许可证"
yes | "$SDKMANAGER" --licenses >/dev/null || true

say "第 2/3 步：安装 SDK 组件（platform-tools + platforms;android-37.0 + build-tools;36.0.0）"
"$SDKMANAGER" "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"

say "第 3/3 步：写入环境变量（bash / zsh / fish，已有则跳过）"
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

say "验收输出（请完整回传给主 agent）"
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
say "完成！新开终端后 adb / sdkmanager 即在 PATH 中生效"
