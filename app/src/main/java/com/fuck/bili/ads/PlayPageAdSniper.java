package com.fuck.bili.ads;

import android.view.ViewGroup;
import android.widget.Space;

import com.fuck.bili.ads.core.NativeHooks;
import com.fuck.bili.ads.core.Reflect;
import com.fuck.bili.ads.ui.FbaLog;
import com.fuck.bili.ads.ui.FbaPrefs;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Signature-based playback-page ad hooks shared by hosts using the Gripper API. */
public final class PlayPageAdSniper {

    private static final String UNDER_PLAYER =
            "com.bilibili.gripper.api.ad.biz.videodetail.underplayer.IAdUnderPlayer";
    private static final String UNDER_CALLBACK = UNDER_PLAYER + "Callback";
    private static final String RELATE = "com.bilibili.gripper.api.ad.biz.videodetail.relate.IAdVideoRelate";
    private static final String RELATE_ENTRY = RELATE.replace("IAdVideoRelate", "IAdRelateViewEntry");

    private PlayPageAdSniper() {
    }

    static boolean isUnderPlayerFactory(String name) {
        return "getUpperAdView".equals(name)
                || "getUpperHDView".equals(name)
                || "getUpperNestView".equals(name);
    }

    static boolean isRelateFactory(String name) {
        return "getAdRelateView".equals(name);
    }

    /** Matches descriptor structure rather than an obfuscated implementation name. */
    static boolean hasSignature(Method method, String returnType, String... parameterTypes) {
        if (!returnType.equals(method.getReturnType().getName())) {
            return false;
        }
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!parameterTypes[i].equals(actual[i].getName())) {
                return false;
            }
        }
        return true;
    }

    /** Registers structurally validated names resolved by the shared DexKit worker. */
    public static int hook(ClassLoader loader, List<String> underPlayer, List<String> relate) {
        int hooked = 0;
        try {
            hooked += hookUnderPlayer(loader, underPlayer);
        } catch (Throwable error) {
            FbaLog.e("[PLAY] 播放器浮层 Hook 失败", error);
        }
        try {
            hooked += hookRelate(loader, relate);
        } catch (Throwable error) {
            FbaLog.e("[PLAY] 关联推广 Hook 失败", error);
        }
        FbaLog.i("[PLAY] Gripper 接口特征 Hook 数量=" + hooked);
        return hooked;
    }

    private static int hookUnderPlayer(ClassLoader loader, List<String> candidates) throws Throwable {
        if (candidates.isEmpty()) {
            return 0;
        }
        Class<?> api = Reflect.findClass(UNDER_PLAYER, loader);
        Class<?> callback = Reflect.findClass(UNDER_CALLBACK, loader);
        int hooked = 0;
        for (Class<?> implementation : resolveImplementations(loader, api, candidates)) {
            try {
                for (Method method : implementation.getDeclaredMethods()) {
                    if (!isUnderPlayerFactory(method.getName()) || method.getReturnType() != callback) {
                        continue;
                    }
                    NativeHooks.hook("PlayPageAdSniper.UnderPlayer", method, chain -> {
                        Object result = chain.proceed();
                        if (result == null || !FbaPrefs.blockPlay()) {
                            return result;
                        }
                        try {
                            Object parent = chain.getArg(0);
                            if (!(parent instanceof ViewGroup)) {
                                return result;
                            }
                            return blankUnderPlayer(result, callback,
                                    new Space(((ViewGroup) parent).getContext()));
                        } catch (Throwable error) {
                            // The host handles a missing under-player view as an empty slot.
                            FbaLog.e("[PLAY] 浮层空视图包装失败，使用宿主空实现", error);
                            return null;
                        }
                    });
                    hooked++;
                }
            } catch (Throwable error) {
                FbaLog.e("[PLAY] 浮层候选 Hook 失败: " + implementation.getName(), error);
            }
        }
        FbaLog.i("[PLAY] 播放器下方/标题上方浮层 Hook 数量=" + hooked);
        return hooked;
    }

    private static int hookRelate(ClassLoader loader, List<String> candidates) throws Throwable {
        if (candidates.isEmpty()) {
            return 0;
        }
        Class<?> api = Reflect.findClass(RELATE, loader);
        int hooked = 0;
        for (Class<?> implementation : resolveImplementations(loader, api, candidates)) {
            try {
                for (Method method : implementation.getDeclaredMethods()) {
                    if (!isRelateFactory(method.getName())
                            || !hasSignature(method, RELATE_ENTRY,
                            "android.view.ViewGroup", "int", "boolean")) {
                        continue;
                    }
                    NativeHooks.hook("PlayPageAdSniper.Relate", method, chain ->
                            FbaPrefs.blockPlay() ? null : chain.proceed());
                    hooked++;
                }
            } catch (Throwable error) {
                FbaLog.e("[PLAY] 关联推广候选 Hook 失败: " + implementation.getName(), error);
            }
        }
        FbaLog.i("[PLAY] 关联推广接口 Hook 数量=" + hooked);
        return hooked;
    }

    static List<Class<?>> resolveImplementations(
            ClassLoader loader, Class<?> api, List<String> candidates) {
        List<Class<?>> matches = new ArrayList<>();
        for (String candidate : candidates) {
            Class<?> implementation = Reflect.findClassIfExists(candidate, loader);
            if (implementation != null && api.isAssignableFrom(implementation)) {
                matches.add(implementation);
            }
        }
        return matches;
    }

    /** Reads APK paths supplied by the runtime class loader, including LSPatch hosts. */
    static List<String> sources(ClassLoader loader, String fallback) {
        Set<String> paths = new LinkedHashSet<>();
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            try {
                Object dexPathList = Reflect.getObjectField(current, "pathList");
                if (dexPathList == null) {
                    continue;
                }
                Object[] elements = (Object[]) Reflect.getObjectField(dexPathList, "dexElements");
                for (Object element : elements) {
                    Object dex = Reflect.getObjectField(element, "dexFile");
                    if (dex == null) {
                        continue;
                    }
                    String path = (String) dex.getClass().getMethod("getName").invoke(dex);
                    if (path != null && path.endsWith(".apk") && new File(path).isFile()) {
                        paths.add(path);
                    }
                }
            } catch (Throwable ignored) {
                // A different class-loader implementation simply uses the next source.
            }
        }
        paths.add(fallback);
        return new ArrayList<>(paths);
    }

    static List<String> findUnderPlayer(DexKitBridge bridge) {
        return implementations(bridge, UNDER_PLAYER);
    }

    static List<String> findRelate(DexKitBridge bridge) {
        return implementations(bridge, RELATE);
    }

    private static List<String> implementations(DexKitBridge bridge, String api) {
        List<String> names = new ArrayList<>();
        for (ClassData candidate : bridge.findClass(FindClass.create().matcher(
                ClassMatcher.create().addInterface(ClassMatcher.create().className(api))))) {
            names.add(candidate.getName());
        }
        return names;
    }

    /**
     * Keeps host construction and lifecycle intact while presenting its own blank-view contract.
     * No host model, view type or obfuscated callback implementation is modified.
     */
    private static Object blankUnderPlayer(Object delegate, Class<?> callback, Space blank) {
        return Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if ("equals".equals(method.getName())) {
                            return args != null && args.length == 1 && proxy == args[0];
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(method.getName())) {
                            return "FuckBiliAds blank under-player";
                        }
                    }
                    switch (method.getName()) {
                        case "getRootView":
                            return blank;
                        case "getViewHeight":
                        case "getViewColor":
                            return 0;
                        case "isBlankView":
                            return true;
                        case "isSupportAnimIn":
                        case "defaultContainerVisible":
                            return false;
                        case "getScrollBehavior":
                            if (method.getReturnType().isEnum()) {
                                try {
                                    // Hide is the host's zero-height behavior, not an implementation name.
                                    @SuppressWarnings({"rawtypes", "unchecked"})
                                    Object hide = Enum.valueOf((Class) method.getReturnType(), "Hide");
                                    return hide;
                                } catch (IllegalArgumentException ignored) {
                                    // Older interfaces can delegate their original behavior safely.
                                }
                            }
                            break;
                        default:
                            break;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause() == null ? error : error.getCause();
                    }
                });
    }
}
