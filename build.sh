#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG="io.github.Subodhkr360.stylo2.haptics"

if [ -z "$ANDROID_JAR" ]; then
    for candidate in \
        "/data/user/0/com.agy/files/android-sdk/android-34.jar" \
        "$HOME/android-sdk/android-34.jar" \
        "$PREFIX/share/java/android.jar" \
        "$ANDROID_HOME/platforms/android-34/android.jar" \
        "$ANDROID_SDK_ROOT/platforms/android-34/android.jar"; do
        if [ -f "$candidate" ]; then
            ANDROID_JAR="$candidate"
            break
        fi
    done
fi

if [ -z "$ANDROID_JAR" ] || [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar (API 34) not found! Please set ANDROID_JAR environment variable."
    exit 1
fi

cd "$PROJECT_DIR"
rm -rf build
mkdir -p build/apk build/obj build/module

echo "=== Step 1: Compiling resources with aapt2 ==="
aapt2 compile --dir res -o build/compiled_res.zip

echo "=== Step 2: Linking APK with aapt2 ==="
aapt2 link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --java src/ \
    -o build/apk/base.apk \
    build/compiled_res.zip

echo "=== Step 3: Compiling Java sources ==="
javac -cp "$ANDROID_JAR:src" -d build/obj $(find src -name "*.java")

echo "=== Step 4: Dexing class files with d8 ==="
d8 --lib "$ANDROID_JAR" --output build/apk/ $(find build/obj -name "*.class")

echo "=== Step 5: Packaging classes.dex and assets into APK ==="
mkdir -p build/apk/assets
if [ -d "$PROJECT_DIR/assets" ]; then
    cp -r "$PROJECT_DIR/assets/"* build/apk/assets/ 2>/dev/null || true
fi
cd build/apk
jar -uf base.apk classes.dex
if [ -d assets ] && [ "$(ls -A assets)" ]; then
    jar -uf base.apk assets/
fi
cd "$PROJECT_DIR"

echo "=== Step 5.5: 4-Byte ZipAligning APK ==="
python3 zipalign.py build/apk/base.apk build/apk/aligned.apk

echo "=== Step 6: Signing APK ==="
apksigner sign --ks debug.keystore \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out StyloHapticsStudio.apk \
    build/apk/aligned.apk

apksigner verify -v StyloHapticsStudio.apk

echo "=== Step 7: Packaging KernelSU / Magisk Module ==="
MODULE_DIR="build/module"
mkdir -p "$MODULE_DIR/system/priv-app/Stylo2Haptics"
mkdir -p "$MODULE_DIR/system/etc/permissions"
mkdir -p "$MODULE_DIR/product/etc/permissions"
mkdir -p "$MODULE_DIR/system_ext/etc/permissions"

cp StyloHapticsStudio.apk "$MODULE_DIR/system/priv-app/Stylo2Haptics/Stylo2Haptics.apk"

cat << 'EOF' > "$MODULE_DIR/module.prop"
id=stylo2-haptics-privapp
name=Advance Stylo Haptics (Priv-App)
version=2.2.0
versionCode=220
author=Subodhkr360 & Antigravity
description=Enables OnePlus Stylo 2 drawing haptics, Air View S-Pen suite, Air Command rotary wheel, and landscape compatibility with com.oplus.permission.safe.IOT.
EOF

cat << EOF > "$MODULE_DIR/system/etc/permissions/privapp-permissions-stylo2haptics.xml"
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="$PKG">
        <permission name="com.oplus.permission.safe.IOT"/>
        <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
    </privapp-permissions>
</permissions>
EOF

cp "$MODULE_DIR/system/etc/permissions/privapp-permissions-stylo2haptics.xml" "$MODULE_DIR/product/etc/permissions/"
cp "$MODULE_DIR/system/etc/permissions/privapp-permissions-stylo2haptics.xml" "$MODULE_DIR/system_ext/etc/permissions/"

cat << 'EOF' > "$MODULE_DIR/sepolicy.rule"
allow priv_app_36 input_device dir { search read open getattr }
allow priv_app_36 input_device chr_file { read open ioctl getattr }
allow priv_app input_device dir { search read open getattr }
allow priv_app input_device chr_file { read open ioctl getattr }
allow priv_app_36 sysfs dir { search read open getattr }
allow priv_app_36 sysfs file { read open getattr }
allow priv_app sysfs dir { search read open getattr }
allow priv_app sysfs file { read open getattr }
EOF

cat << 'EOF' > "$MODULE_DIR/service.sh"
#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# Ensure package is recognized by launcher
pm path io.github.Subodhkr360.stylo2.haptics >/dev/null 2>&1 || pm install -r /system/priv-app/Stylo2Haptics/Stylo2Haptics.apk 2>/dev/null || true

chmod 666 /dev/input/event*
chmod 666 /proc/touchpanel/pencil_control
chmod 666 /sys/devices/virtual/oplus_wireless/pencil/present 2>/dev/null || true
chmod 666 /sys/devices/virtual/oplus_wireless/pencil/tx_status 2>/dev/null || true
echo 3 > /proc/touchpanel/pencil_control

# Auto-enable StyloAccessibilityService for Air View & Smart Features
CUR=$(settings get secure enabled_accessibility_services)
SVC="io.github.Subodhkr360.stylo2.haptics/io.github.Subodhkr360.stylo2.haptics.StyloAccessibilityService"
case "$CUR" in
    *"$SVC"*) ;;
    null|""|" ") settings put secure enabled_accessibility_services "$SVC" ;;
    *) settings put secure enabled_accessibility_services "$CUR:$SVC" ;;
esac
settings put secure accessibility_enabled 1
dumpsys deviceidle whitelist +io.github.Subodhkr360.stylo2.haptics
dumpsys deviceidle whitelist +com.oplus.ipemanager
EOF
chmod 755 "$MODULE_DIR/service.sh"

cat << 'EOF' > "$MODULE_DIR/customize.sh"
SKIPUNZIP=0
ui_print "****************************************"
ui_print "*        Advance Stylo Haptics         *"
ui_print "*   Version 2.2.0 (Priv-App Module)    *"
ui_print "****************************************"
ui_print "- Setting permissions..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
# Auto-install APK to launcher database during flash so launcher shows correct icon immediately
pm install -r "$MODPATH/system/priv-app/Stylo2Haptics/Stylo2Haptics.apk" 2>/dev/null || true
ui_print "- Note: KernelSU / APatch on EROFS (ColorOS/OxygenOS 14+)"
ui_print "  requires an OverlayFS module (e.g. meta-overlayfs) for /system/priv-app mounting."
ui_print "- Installation complete! Please reboot."
EOF

python3 -c "
import zipfile, os
with zipfile.ZipFile('/storage/emulated/0/StyloHapticsModule.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk('build/module'):
        for f in files:
            path = os.path.join(root, f)
            arcname = os.path.relpath(path, 'build/module')
            z.write(path, arcname)
"

if [ "$1" == "--no-install" ]; then
    echo "=== Skipping installation (--no-install passed) ==="
    echo "=== SUCCESS: StyloHapticsStudio.apk generated at $PROJECT_DIR/StyloHapticsStudio.apk ==="
    exit 0
fi

if command -v su >/dev/null 2>&1; then
    echo "=== Step 8: Deploying to KernelSU active modules & live overlay ==="
    su -c "mkdir -p /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/system/priv-app/Stylo2Haptics && \
           mkdir -p /data/adb/modules/stylo2-haptics-privapp/system/priv-app/Stylo2Haptics && \
           mkdir -p /data/adb/modules/stylo2-haptics-privapp/system/etc/permissions && \
           mkdir -p /data/adb/modules/stylo2-haptics-privapp/product/etc/permissions && \
           mkdir -p /data/adb/modules/stylo2-haptics-privapp/system_ext/etc/permissions && \
           mkdir -p /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/system/etc/permissions && \
           mkdir -p /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/product/etc/permissions && \
           mkdir -p /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/system_ext/etc/permissions && \
           cp $PROJECT_DIR/build/module/system/etc/permissions/privapp-permissions-stylo2haptics.xml /data/adb/modules/stylo2-haptics-privapp/system/etc/permissions/ && \
           cp $PROJECT_DIR/build/module/system/etc/permissions/privapp-permissions-stylo2haptics.xml /data/adb/modules/stylo2-haptics-privapp/product/etc/permissions/ && \
           cp $PROJECT_DIR/build/module/system/etc/permissions/privapp-permissions-stylo2haptics.xml /data/adb/modules/stylo2-haptics-privapp/system_ext/etc/permissions/ && \
           cp $PROJECT_DIR/build/module/system/etc/permissions/privapp-permissions-stylo2haptics.xml /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/system/etc/permissions/ && \
           cp $PROJECT_DIR/build/module/system/etc/permissions/privapp-permissions-stylo2haptics.xml /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/product/etc/permissions/ && \
           cp $PROJECT_DIR/build/module/system/etc/permissions/privapp-permissions-stylo2haptics.xml /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/system_ext/etc/permissions/ && \
           cp $PROJECT_DIR/StyloHapticsStudio.apk /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/system/priv-app/Stylo2Haptics/Stylo2Haptics.apk && \
           cp $PROJECT_DIR/StyloHapticsStudio.apk /data/adb/modules/stylo2-haptics-privapp/system/priv-app/Stylo2Haptics/Stylo2Haptics.apk && \
           cp $PROJECT_DIR/StyloHapticsStudio.apk /storage/emulated/0/StyloHapticsStudio.apk && \
           cp $PROJECT_DIR/StyloHapticsStudio.apk /data/local/tmp/StyloHapticsStudio.apk && \
           chmod 644 /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/system/priv-app/Stylo2Haptics/Stylo2Haptics.apk && \
           chmod 644 /data/adb/modules/stylo2-haptics-privapp/system/priv-app/Stylo2Haptics/Stylo2Haptics.apk && \
           chmod 666 /data/local/tmp/StyloHapticsStudio.apk && \
           chcon -R u:object_r:system_file:s0 /data/adb/modules/meta-overlayfs/mnt/stylo2-haptics-privapp/system/priv-app/Stylo2Haptics && \
           chcon -R u:object_r:system_file:s0 /data/adb/modules/stylo2-haptics-privapp/system/priv-app/Stylo2Haptics && \
           pm uninstall io.github.yeetingwaterbottle.stylo2.haptics || true && \
           pm install -r /data/local/tmp/StyloHapticsStudio.apk"

    echo "=== Step 9: Auto-granting Accessibility and Permissions via Root ==="
    su -c "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS && \
           settings put secure enabled_accessibility_services $PKG/$PKG.StyloAccessibilityService && \
           settings put secure accessibility_enabled 1 && \
           settings put global cached_apps_freezer disabled && \
           chmod 666 /dev/input/event* && \
           chmod 666 /sys/devices/virtual/oplus_wireless/pencil/present 2>/dev/null || true && \
           chmod 666 /sys/devices/virtual/oplus_wireless/pencil/tx_status 2>/dev/null || true && \
           dumpsys deviceidle whitelist +$PKG && \
           dumpsys deviceidle whitelist +com.oplus.ipemanager && \
           for f in /sys/fs/cgroup/apps/uid_10210/*/cgroup.freeze /sys/fs/cgroup/apps/uid_10359/*/cgroup.freeze; do [ -f \$f ] && echo 0 > \$f; done; true"
else
    echo "=== Root (su) not found, skipping device installation ==="
fi

echo "=== SUCCESS: Advance Stylo Haptics Built! ==="
