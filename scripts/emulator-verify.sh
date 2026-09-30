#!/bin/bash
# emulator-verify.sh —— 用命令行模拟器做 M6 验收（无需 Android Studio GUI）
#
# 背景：本项目 shell 跑在容器内，看不到主机 /usr /opt，无法操作 Android Studio GUI。
# 但 Android SDK 的 emulator 是命令行工具（Android Studio 内部也调它），可直接驱动。
#
# 一次性准备（本脚本会自动检测并提示）：
#   export JAVA_HOME=/home/othc3/opt/jdk-21b
#   yes | $SDK/cmdline-tools/latest/bin/sdkmanager "system-images;android-36;default;x86_64"
#   echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd -n xpu_test \
#       -k "system-images;android-36;default;x86_64" -d pixel_6 --force
#
# 用法：bash scripts/emulator-verify.sh <start|wait|install|shot|stop>

set -u
SDK=/home/othc3/Android/Sdk
ADB=$SDK/platform-tools/adb
EMU=$SDK/emulator/emulator
AVD=xpu_test
APK=/home/othc3/WorkBuddy/安卓软件开发/app/build/outputs/apk/debug/app-debug.apk
PKG=com.gould.xputimetable
OUT=/tmp/emu

mkdir -p "$OUT"

case "${1:-help}" in
  create)
    echo "== 创建 AVD =="
    export JAVA_HOME=/home/othc3/opt/jdk-21b
    echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd \
        -n "$AVD" -k "system-images;android-36;default;x86_64" -d pixel_6 --force 2>&1 | tail -5
    ;;

  start)
    echo "== 启动模拟器（无头） =="
    # -no-window：容器内无 X display，必须无头；用 adb screencap 代替肉眼看
    # -gpu swiftshader_indirect：软件渲染（无 GPU 直通时必需）
    # -no-snapshot：每次冷启动，保证状态干净可复现
    nohup $EMU -avd "$AVD" \
        -no-window -no-audio -no-boot-anim -no-snapshot -no-metrics \
        -gpu swiftshader_indirect -memory 3072 -cores 4 \
        > "$OUT/emulator.log" 2>&1 &
    echo "  pid=$! 日志=$OUT/emulator.log"
    ;;

  wait)
    echo "== 等待开机完成 =="
    $ADB wait-for-device
    for i in $(seq 1 180); do
      done_boot=$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      if [ "$done_boot" = "1" ]; then
        echo "  开机完成（第 ${i} 次探测）"
        $ADB shell wm size | sed 's/^/  /'
        $ADB shell getprop ro.build.version.release | sed 's/^/  Android /'
        exit 0
      fi
      sleep 2
    done
    echo "  ✗ 超时 360s 仍未开机，查 $OUT/emulator.log"
    exit 1
    ;;

  install)
    echo "== 安装 APK =="
    $ADB install -r "$APK" 2>&1 | tail -3
    echo ""
    echo "== 复核权限（本机纪律：install -r 后 appops/pm 授予会被重置） =="
    $ADB shell dumpsys package $PKG 2>/dev/null | grep -E "POST_NOTIFICATIONS|SCHEDULE_EXACT_ALARM" | sed 's/^/  /'
    ;;

  shot)
    # 截屏前必须确认前台包名，避免截到用户隐私画面（本项目已两次踩坑）
    name="${2:-shot}"
    FRONT=$($ADB shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -1)
    echo "  前台: $FRONT"
    $ADB exec-out screencap -p > "$OUT/$name.png" 2>/dev/null
    echo "  → $OUT/$name.png  $(stat -c%s "$OUT/$name.png" 2>/dev/null) 字节"
    ;;

  stop)
    echo "== 关闭模拟器 =="
    $ADB emu kill 2>/dev/null
    sleep 3
    $ADB devices | sed 's/^/  /'
    ;;

  *)
    echo "用法: bash scripts/emulator-verify.sh <create|start|wait|install|shot <名称>|stop>"
    ;;
esac
