package com.elsure.hyperglow.hook;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.util.Arrays;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed half of this app. Loaded by LSPosed into target processes (dormant in the app's own UI
 * process). This fork does NOT hook system_server, so the reliable place to intercept the stock
 * light policy is the CLIENT side: the miui.lights.ILightsManager binder proxy.
 *
 * Confirmed root cause of the camera privacy LED on piano/HyperOS:
 *   com.miui.securitycenter (.remote) calls
 *   ILightsManager.setColorCommon(color, "com.miui.securitycenter", styleType=7, userId=0)
 *   green on camera open, 0 on close. styleType 7 == privacy light.
 *
 * Policy applied to setColorCommon(styleType==7):
 *   takeover==1 : drop the call at the source so the root daemon owns the LED (no flash).
 *   privacy_mode==2 (hide)   : drop the call.
 *   privacy_mode==1 (custom) : rewrite the color to privacy_color (custom privacy LED, no flash).
 *   privacy_mode==0 (stock)  : pass through unchanged.
 * Every light call is logged and (best effort) mirrored to Settings.Global so the app can monitor
 * events even while takeover is active.
 *
 * Settings.Global keys (app writes via root; this hook reads):
 *   miuilight_takeover, miuilight_privacy_mode, miuilight_privacy_color
 * Event reporting (this hook writes best-effort; app reads):
 *   miuilight_evt_seq, miuilight_evt_last
 */
public class LightHook implements IXposedHookLoadPackage {

    private static final String TAG = "MiuiLightHook";
    private static final String PKG_SELF = "com.elsure.hyperglow";
    private static final String PKG_SYSTEM = "android";
    private static final String PKG_SECCENTER = "com.miui.securitycenter";
    private static final String PROXY = "miui.lights.ILightsManager$Stub$Proxy";

    private static final String K_TAKEOVER = "miuilight_takeover";
    private static final String K_PRIV_MODE = "miuilight_privacy_mode";
    private static final String K_PRIV_COLOR = "miuilight_privacy_color";
    private static final String K_MARK = "miuilight_hook_loaded";
    private static final String K_EVT_SEQ = "miuilight_evt_seq";
    private static final String K_EVT_LAST = "miuilight_evt_last";

    private static final int STYLE_PRIVACY = 7;

    // Fast-path candidates for securitycenter's internal ColorLightManager (obfuscated; changes per
    // version). BEST-EFFORT ONLY -- the stable ILightsManager$Stub$Proxy hook below covers the same
    // calls, so this may silently miss. discoverCaller() logs the CURRENT version's real class from
    // the call stack when a light event fires, so you can read it off logcat without a DEX scan
    // (a broad scan is deliberately avoided: it runs during securitycenter startup and risks ANRs).
    private static final String[] SECCENTER_CANDIDATES = {"d9.a", "p052d9.a"};
    private static volatile boolean callerDiscovered = false;

    private static void log(String msg) {
        try { XposedBridge.log(TAG + ": " + msg); } catch (Throwable ignored) {}
        try { Log.i(TAG, msg); } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if (PKG_SELF.equals(pkg)) return; // never hook our own UI process
        ClassLoader cl = lpparam.classLoader;
        if (PKG_SYSTEM.equals(pkg)) hookSystemServer(cl);
        if (PKG_SECCENTER.equals(pkg)) hookSecurityCenterDirect(cl);
        hookLightProxy(cl, pkg);
    }

    // ---- system_server (only effective on forks that hook system_server) ----
    private void hookSystemServer(ClassLoader cl) {
        log("loaded in system_server");
        Context sysCtx = getSystemContext();
        if (sysCtx != null) {
            try { Settings.Global.putInt(sysCtx.getContentResolver(), K_MARK, 1); } catch (Throwable ignored) {}
        }
        XC_MethodHook gate = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (intSetting(appContext(), K_TAKEOVER, 0) == 1) param.setResult(null);
            }
        };
        hook5int("com.android.server.lights.LightsService$LightImpl", cl, "setLightLocked", gate);
        hook5int("com.android.server.lights.MiuiLightsService$LightImpl", cl, "realSetLightLocked", gate);
        hook5int("com.android.server.lights.MiuiLightsService$LightImpl", cl, "setColorCommonLocked", gate);
    }

    // ---- com.miui.securitycenter obfuscated ColorLightManager (drives setColorfulLight) ----
    private void hookSecurityCenterDirect(ClassLoader cl) {
        XC_MethodHook gate = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                log("seccenter ColorLightManager mode=" + param.args[1]);
                if (intSetting((Context) param.args[0], K_TAKEOVER, 0) == 1) param.setResult(null);
            }
        };
        for (String name : SECCENTER_CANDIDATES) {
            try {
                Class<?> c = XposedHelpers.findClass(name, cl);
                XposedHelpers.findAndHookMethod(c, "a", Context.class, int.class, gate);
                log("hooked " + name + "#a(Context,int)");
                break;
            } catch (Throwable t) {
                log("hook " + name + " not found");
            }
        }
    }

    // ---- the light binder proxy: policy + monitoring for every scoped process ----
    private void hookLightProxy(ClassLoader cl, String proc) {
        Class<?> proxy = findProxy(cl);
        if (proxy == null) {
            log("proxy not found in " + proc);
            return;
        }

        // setColorCommon(int color, String pkg, int styleType, int userId)
        hook(proxy, "setColorCommon", new Class[]{int.class, String.class, int.class, int.class},
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        handlePrivacy(proc, param);
                    }
                });

        // setColorfulLight(String pkg, int styleType, int userId)
        hook(proxy, "setColorfulLight", new Class[]{String.class, int.class, int.class},
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        boolean t = intSetting(appContext(), K_TAKEOVER, 0) == 1;
                        log("LIGHT " + proc + " setColorfulLight args=" + Arrays.toString(param.args)
                                + " takeover=" + t);
                        reportEvent(proc, "setColorfulLight", -1, str(param.args[0]), intArg(param.args[1]));
                        if (t) param.setResult(null);
                    }
                });

        // setColorLed(int color, String pkg, int styleType, int userId, int category)
        hook(proxy, "setColorLed", new Class[]{int.class, String.class, int.class, int.class, int.class},
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        boolean t = intSetting(appContext(), K_TAKEOVER, 0) == 1;
                        log("LIGHT " + proc + " setColorLed args=" + Arrays.toString(param.args) + " takeover=" + t);
                        reportEvent(proc, "setColorLed", intArg(param.args[0]), str(param.args[1]), intArg(param.args[2]));
                        if (t) param.setResult(null);
                    }
                });

        // setCustomLight(int,int,int,int,int,String,int,int)
        hook(proxy, "setCustomLight", new Class[]{int.class, int.class, int.class, int.class, int.class,
                String.class, int.class, int.class},
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        boolean t = intSetting(appContext(), K_TAKEOVER, 0) == 1;
                        log("LIGHT " + proc + " setCustomLight args=" + Arrays.toString(param.args) + " takeover=" + t);
                        if (t) param.setResult(null);
                    }
                });

        log("proxy hooks installed in " + proc);
    }

    /** Privacy-light policy for setColorCommon(color, pkg, styleType, userId). */
    private void handlePrivacy(String proc, XC_MethodHook.MethodHookParam param) {
        int color = intArg(param.args[0]);
        String cpkg = str(param.args[1]);
        int styleType = intArg(param.args[2]);
        Context ctx = appContext();
        int takeover = intSetting(ctx, K_TAKEOVER, 0);

        log("LIGHT " + proc + " setColorCommon color=0x" + Integer.toHexString(color)
                + " pkg=" + cpkg + " style=" + styleType + " takeover=" + (takeover == 1));
        reportEvent(proc, "setColorCommon", color, cpkg, styleType);
        discoverCaller(proc);

        if (styleType != STYLE_PRIVACY) {
            if (takeover == 1) param.setResult(null);
            return;
        }
        if (takeover == 1) {
            // Daemon owns the LED; drop the stock privacy write at the source (no flash).
            param.setResult(null);
            return;
        }
        int mode = intSetting(ctx, K_PRIV_MODE, 0);
        if (mode == 2) {                 // hide privacy light
            param.setResult(null);
            return;
        }
        if (mode == 1 && color != 0) {   // custom privacy color
            int custom = intSetting(ctx, K_PRIV_COLOR, color);
            param.args[0] = custom;
            log("privacy color rewritten 0x" + Integer.toHexString(color) + " -> 0x" + Integer.toHexString(custom));
        }
        // mode 0: stock pass-through
    }

    /** Deliver an event to the app: primary via ContentProvider (reliable), secondary via Settings. */
    private static void reportEvent(String proc, String method, int color, String cpkg, int styleType) {
        Context ctx = appContext();
        if (ctx == null) return;
        String evt = System.currentTimeMillis() + "|" + proc + "|" + method + "|0x"
                + Integer.toHexString(color) + "|" + cpkg + "|" + styleType;
        // Primary: the app's ContentProvider (plain binder IPC; needs no special permission).
        try {
            ctx.getContentResolver().call(
                    android.net.Uri.parse("content://com.elsure.hyperglow.events"),
                    "event", evt, null);
        } catch (Throwable t) {
            log("reportEvent provider call failed: " + t);
        }
        // Secondary: mirror to Settings.Global (best-effort; needs WRITE_SECURE_SETTINGS).
        try {
            ContentResolver cr = ctx.getContentResolver();
            int seq = Settings.Global.getInt(cr, K_EVT_SEQ, 0) + 1;
            Settings.Global.putString(cr, K_EVT_LAST, evt);
            Settings.Global.putInt(cr, K_EVT_SEQ, seq);
        } catch (Throwable t) {
            log("reportEvent settings write failed: " + t);
        }
    }

    /**
     * Runtime discovery of securitycenter's real (obfuscated) caller for the light path. Runs once,
     * only when a light event actually fires, by walking the current call stack -- no class scan, so
     * it cannot ANR. The logged class#method is the current version's equivalent of `d9.a#a`, which
     * is how you keep up with obfuscation renames without a hardcoded version table.
     */
    private static void discoverCaller(String proc) {
        if (callerDiscovered) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                String cn = e.getClassName();
                if (cn.startsWith("com.elsure.hyperglow") || cn.startsWith("de.robv.")
                        || cn.startsWith("android.") || cn.startsWith("java.")
                        || cn.startsWith("dalvik.") || cn.startsWith("miui.lights.")) continue;
                sb.append(cn).append('#').append(e.getMethodName()).append(" <- ");
            }
            callerDiscovered = true;
            log("caller chain [" + proc + "]: " + sb);
        } catch (Throwable ignored) {
        }
    }

    private static int intArg(Object o) {
        try { return (Integer) o; } catch (Throwable t) { return 0; }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static int intSetting(Context ctx, String key, int def) {
        try {
            if (ctx == null) return def;
            return Settings.Global.getInt(ctx.getContentResolver(), key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static void hook(Class<?> clazz, String method, Class<?>[] types, XC_MethodHook cb) {
        try {
            Object[] params = new Object[types.length + 1];
            System.arraycopy(types, 0, params, 0, types.length);
            params[types.length] = cb;
            XposedHelpers.findAndHookMethod(clazz, method, params);
        } catch (Throwable t) {
            log("hook " + method + " failed: " + t);
        }
    }

    private static Context appContext() {
        try {
            Class<?> at = XposedHelpers.findClass("android.app.ActivityThread", null);
            return (Context) XposedHelpers.callStaticMethod(at, "currentApplication");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void hook5int(String className, ClassLoader cl, String method, XC_MethodHook cb) {
        try {
            Class<?> c = XposedHelpers.findClass(className, cl);
            XposedHelpers.findAndHookMethod(c, method,
                    int.class, int.class, int.class, int.class, int.class, cb);
            log("hooked " + className + "#" + method);
        } catch (Throwable t) {
            log("hook FAILED " + className + "#" + method + " : " + t);
        }
    }

    private static Context appContext() {
        try {
            return (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null),
                "currentApplication");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Context getSystemContext() {
        try {
            Class<?> atClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object at = XposedHelpers.callStaticMethod(atClass, "currentActivityThread");
            return (Context) XposedHelpers.callMethod(at, "getSystemContext");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Class<?> findProxy(ClassLoader cl) {
        try {
            return XposedHelpers.findClass(PROXY, cl);
        } catch (Throwable t) {
            try {
                return XposedHelpers.findClass(PROXY, ClassLoader.getSystemClassLoader());
            } catch (Throwable t2) {
                return null;
            }
        }
    }
}
