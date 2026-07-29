#!/system/bin/sh
# Runs once at module install. Create the runtime state directory used by the app and daemon.
mkdir -p /data/adb/miuilight
chmod 755 /data/adb/miuilight 2>/dev/null
exit 0