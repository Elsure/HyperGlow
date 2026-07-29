#!/system/bin/sh
# KernelSU late-start service: launch miuilightd at boot so full takeover + breathing persist.
MODDIR=${0%/*}
DIR=/data/adb/miuilight
mkdir -p "$DIR"

# Prefer the app-installed daemon (kept up to date by the app); seed from the module if absent.
if [ ! -f "$DIR/miuilightd" ]; then
  cp "$MODDIR/miuilightd" "$DIR/miuilightd" 2>/dev/null
fi
chmod 755 "$DIR/miuilightd" 2>/dev/null

# Wait for boot to complete so sysfs/settings are ready.
while [ "$(getprop sys.boot_completed)" != "1" ]; do
  sleep 1
done
sleep 3

# Start only if not already running (the daemon also self-guards via its pidfile).
oldpid=$(cat "$DIR/miuilightd.pid" 2>/dev/null)
if [ -n "$oldpid" ] && [ -d "/proc/$oldpid" ]; then
  exit 0
fi
setsid /system/bin/sh "$DIR/miuilightd" >/dev/null 2>&1 &
exit 0