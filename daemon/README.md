# MiuiLight Daemon (KernelSU module)

A root daemon that drives `/sys/class/leds/{red,green,blue}/brightness` directly, so the app's
color / breathing / blink survives app-freezing and beats the HyperOS camera privacy-light
override (which this LSPosed fork cannot block, because it does not hook system_server).

## Two ways to run the daemon

### A. From the app (no flashing, survives until reboot)
1. Grant the app root in KernelSU/SukiSU.
2. In the app, toggle "完全接管" ON. The app copies the bundled daemon to
   `/data/adb/miuilight/miuilightd` and starts it. The chosen color/animation is enforced
   immediately and re-asserted whenever the system tries to change the LED.

### B. As a KSU module (persists across reboot)
1. Zip the four files in `module/` (module.prop, customize.sh, service.sh, miuilightd) so they
   sit at the ROOT of the zip. A ready-made zip is provided at `daemon/miuilight_daemon.zip`.
2. Flash it in the KernelSU/SukiSU manager -> Modules -> Install from storage. Reboot.
3. The daemon auto-starts at boot. The app still controls color/animation/takeover via
   `/data/adb/miuilight/state`.

## Files
- `/data/adb/miuilight/miuilightd`     the daemon script
- `/data/adb/miuilight/state`          desired state CSV: `takeover,mode,R,G,B,period_ms`
                                        takeover 1=enforce 0=release; mode 0=off 1=solid 2=breath 3=blink
- `/data/adb/miuilight/miuilightd.pid` single-instance pidfile

## Notes
- When takeover=0 the daemon releases the LED and the stock policy (camera green, battery,
  notifications) behaves normally.
- The in-app Xposed module (LightHook) is optional/best-effort on this fork; the daemon is the
  reliable mechanism.