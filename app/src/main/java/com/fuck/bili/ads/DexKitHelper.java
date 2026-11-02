package com.fuck.bili.ads;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fuck.bili.ads.core.Host;
import com.fuck.bili.ads.ui.FbaLog;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Properties;
import java.util.function.Function;

import android.content.pm.ApplicationInfo;

public class DexKitHelper {

    private final ClassLoader hostLoader;
    List<String> hdUnderPlayerClasses = Collections.emptyList();
    List<String> hdRelateClasses = Collections.emptyList();

    public DexKitHelper() {
        this(null);
    }

    public DexKitHelper(ClassLoader hostLoader) {
        this.hostLoader = hostLoader;
    }

    public String albumRecycleViewHolderClassName = "PJ0.e";
    public String publishArchiveCollectionFieldName = "a";
    public String albumSelectPageViewClassName = "OH0.X";
    public String RECYCLER_VIEW_FIELD_NAME = "f";
    public String CHRONOS_RPC_CLASS_NAME = "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.a";

    public String INTERNATIONAL_CHRONOS_RPC_CLASS_NAME = "com.bilibili.common.chronoscommon.o";
    public String INTERNATIONAL_INVOKE_METHOD_NAME = "f";
    public String DESCRIPTION_TEXTVIEW_CLASS_NAME = "Ym1.a";
    public String PEGASUS_MODEL_CLASS_NAME = "U9.h";

    // 开屏广告跳过相关的 fallback 值
    public String BASE_SPLASH_CLASS_NAME = "tv.danmaku.bili.splash.ad.page.BaseSplash";
    public String SPLASH_READY_METHOD_NAME = "k8";
    public String SPLASH_SKIP_METHOD_NAME = "Sf";

    // 新增：视频提及控件相关的 fallback 值
    public String VIDEO_MENTIONED_COMPONENT_CLASS_NAME = "com.bilibili.ship.theseus.ugc.intro.videomentioned.module.l";
    public String VIEW_ENTRY_CLASS_NAME = "com.bilibili.app.gemini.ui.UIComponent$ViewEntry";
    public String UI_COMPONENT_B_CLASS_NAME = "com.bilibili.app.gemini.ui.UIComponent$b";

    public String SEARCH_RESULT_ADAPTER_CLASS_NAME = "com.bilibili.search2.result.all.b";

    public String HOME_CARD_ADAPTER_CLASS_NAME = "ct0.c";

    public String CARD_INFO_SETTING_METHOD_NAME = "m";

    public String PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = "g";

    public String VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME = "d";

    public String VIP_PREDICT_REQUEST_CLASS_NAME = "retrofit2.h";

    public String NATIVE_WEBVIEW_SUB_CLASS_NAME = "com.bilibili.app.comm.bh.NativeWebView$d";
    //NetworkShowToastService
    public String NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME = "com.bilibili.ship.theseus.united.player.mediaplay.network.m";

    public String NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME = "f";

    public String REQUEST_INTERCEPTOR = "com.bilibili.app.comm.list.common.api.d";
    private static final String TAG = "dexkitHelper";
    static final String MOBILE_CACHE_SCHEMA = "2";

    /** Loads names only; a cache miss is resolved by the background worker. */
    public boolean loadCache(ApplicationInfo appInfo) {
        return loadCache(cacheFileFor(appInfo), cacheKeyFor(appInfo),
                Host.isHdPackage(appInfo.packageName));
    }

    boolean loadCache(File cacheFile, String cacheKey, boolean hd) {
        if (cacheFile != null && cacheFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(cacheFile)) {
                Properties cacheProps = new Properties();
                cacheProps.load(fis);
                if (cacheKey.equals(cacheProps.getProperty("cache_key"))) {
                    if (hd) {
                        String underPlayer = cacheProps.getProperty("hdUnderPlayerClasses");
                        String relate = cacheProps.getProperty("hdRelateClasses");
                        if (underPlayer == null || relate == null) {
                            return false;
                        }
                        hdUnderPlayerClasses = classNames(underPlayer);
                        hdRelateClasses = classNames(relate);
                        PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = cacheProps.getProperty(
                                "PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME", "l");
                        return true;
                    }
                    // Refresh caches that predate explicit optional misses and the VIP field fix.
                    if (!MOBILE_CACHE_SCHEMA.equals(cacheProps.getProperty("mobile_schema"))) {
                        return false;
                    }
                    applyMobileProperties(cacheProps);

                    return true;
                }
            } catch (Exception ignored) {
                // Missing, stale and damaged caches are refreshed by the worker.
            }
        }
        return false;
    }

    public void resolve(ApplicationInfo appInfo) {
        if (loadCache(appInfo)) {
            return;
        }
        if (Host.isHdPackage(appInfo.packageName)) {
            resolveHd(appInfo);
            return;
        }
        long start = System.currentTimeMillis();
        String cacheKey = cacheKeyFor(appInfo);
        File cacheFile = cacheFileFor(appInfo);
        if (appInfo.sourceDir != null) {
            System.loadLibrary("dexkit");
            Log.d(TAG, "Starting DexKit deep scan...");
            try (DexKitBridge bridge = DexKitBridge.create(appInfo.sourceDir);
                 MobileScan scan = beginMobileScan()) {
                if (bridge == null) {
                    Log.d(TAG, "DexKit initialization failed!");
                    return;
                }
                MethodMatcher matcher1 = MethodMatcher.create()
                        .name("onBindViewHolder")
                        .paramTypes("androidx.recyclerview.widget.RecyclerView$ViewHolder", "int");

                List<MethodData> result1 = bridge.findMethod(FindMethod.create().matcher(matcher1));
                if (!result1.isEmpty()) {
                    albumRecycleViewHolderClassName = result1.get(0).getClassName();
                    Log.d(TAG, "找到 AlbumRecycleViewHolder: " + albumRecycleViewHolderClassName);

                    FieldMatcher fieldMatcher = FieldMatcher.create()
                            .declaredClass(albumRecycleViewHolderClassName)
                            .type("java.util.List");

                    List<FieldData> fields = bridge.findField(FindField.create().matcher(fieldMatcher));
                    if (!fields.isEmpty()) {
                        publishArchiveCollectionFieldName = fields.get(0).getName();
                        Log.d(TAG, "找到 List 字段名: " + publishArchiveCollectionFieldName);
                    }
                }


                ClassMatcher viewBindingMatcher = ClassMatcher.create()
                        .addInterface(ClassMatcher.create().className("androidx.viewbinding.ViewBinding"))
                        .addMethod(MethodMatcher.create().name("inflate").paramTypes("android.view.LayoutInflater", "android.view.ViewGroup", "boolean"))
                        .addField(FieldMatcher.create().type("androidx.recyclerview.widget.RecyclerView"))
                        .addField(FieldMatcher.create().type("com.bilibili.magicasakura.widgets.TintConstraintLayout"))
                        .addField(FieldMatcher.create().type("com.bilibili.magicasakura.widgets.TintLinearLayout"));

                List<ClassData> result2 = bridge.findClass(FindClass.create().matcher(viewBindingMatcher));
                if (!result2.isEmpty()) {
                    albumSelectPageViewClassName = result2.get(0).getName();
                    Log.d(TAG, "找到 AlbumSelectPageView: " + albumSelectPageViewClassName);

                    FieldMatcher rvFieldMatcher = FieldMatcher.create()
                            .declaredClass(albumSelectPageViewClassName)
                            .type("androidx.recyclerview.widget.RecyclerView");

                    List<FieldData> fields = bridge.findField(FindField.create().matcher(rvFieldMatcher));
                    if (!fields.isEmpty()) {
                        RECYCLER_VIEW_FIELD_NAME = fields.get(0).getName();
                        Log.d(TAG, "找到 RecyclerView 字段名: " + RECYCLER_VIEW_FIELD_NAME);
                    }
                }

                MethodMatcher matcher3 = MethodMatcher.create()
                        .usingStrings("DmsegLoader");

                List<MethodData> result3 = bridge.findMethod(FindMethod.create().matcher(matcher3));
                if (!result3.isEmpty()) {
                    CHRONOS_RPC_CLASS_NAME = result3.get(0).getClassName();
                    Log.d(TAG, "找到 ChronosRpc: " + CHRONOS_RPC_CLASS_NAME);
                }

                List<ClassData> descriptionTextViewClassDataList = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings(
                                "UgcIntroductionComponent",
                                "onLongClick DescTagSpan"
                        ))
                );
                if (descriptionTextViewClassDataList.isEmpty()) {
                    Log.d(TAG, "DexKit fail to find descriptionTextViewClass");
                } else {
                    ClassData outerClass = getOuterClass(bridge, descriptionTextViewClassDataList.get(0));
                    if (outerClass != null) {
                        DESCRIPTION_TEXTVIEW_CLASS_NAME = outerClass.getName();
                    } else {
                        DESCRIPTION_TEXTVIEW_CLASS_NAME = descriptionTextViewClassDataList.get(0).getName();
                    }
                }

                List<ClassData> internationalChronosRPCClassDataList = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings(
                                "EnhancedChronosPackageRunner",
                                "DestroyInputSurface: chronos engine is invalid!"
                        ))
                );

                if (internationalChronosRPCClassDataList.isEmpty()) {
                    Log.d(TAG, "DexKit fail to find internationalChronosRPCClass");
                } else {
                    ClassData internationalChronosRPCClassData = getOuterClass(bridge, internationalChronosRPCClassDataList.get(0));

                    if (internationalChronosRPCClassData == null) {
                        internationalChronosRPCClassData = internationalChronosRPCClassDataList.get(0);
                    }
                    INTERNATIONAL_CHRONOS_RPC_CLASS_NAME = internationalChronosRPCClassData.getName();
                    List<MethodData> internationalInvokeMethodDataList = bridge.findMethod(FindMethod.create()
                            .searchInClass(Collections.singleton(internationalChronosRPCClassData))
                            .matcher(MethodMatcher.create()
                                    .paramCount(2)
                                    .returnType("void")
                                    .paramTypes("java.lang.Class", "kotlin.jvm.functions.Function6")));
                    INTERNATIONAL_INVOKE_METHOD_NAME = firstName(
                            internationalInvokeMethodDataList, MethodData::getName);
                }

                ClassMatcher pegasusMatcher = ClassMatcher.create()
                        .addInterface(ClassMatcher.create().className("com.bilibili.pegasus.data.base.BasePegasusPlayerData"))
                        .addField(FieldMatcher.create().type("com.bilibili.adcommon.data.AdInfo"))
                        .addField(FieldMatcher.create().type("com.bilibili.pegasus.HolderExtra"))
                        .addField(FieldMatcher.create().type("com.bilibili.ad.adview.pegasus.data.AdMode"));

                List<ClassData> pegasusResult = bridge.findClass(FindClass.create().matcher(pegasusMatcher));

                if (!pegasusResult.isEmpty()) {
                    PEGASUS_MODEL_CLASS_NAME = pegasusResult.get(0).getName();
                } else {
                    Log.d(TAG, "推荐流模型特征缺失，缓存为空名字");
                }


                ClassMatcher mentionedMatcher = ClassMatcher.create()
                        .usingStrings("VideoMentionedModuleComponent.TabContainer (VideoMentionedModuleComponent.kt");

                List<ClassData> mentionedResult = bridge.findClass(FindClass.create().matcher(mentionedMatcher));


                if (!mentionedResult.isEmpty()) {
                    ClassData mentionedClassData = mentionedResult.get(0);
                    VIDEO_MENTIONED_COMPONENT_CLASS_NAME = mentionedClassData.getName();
                    Log.d(TAG, "Found Video Mentioned Component: " + VIDEO_MENTIONED_COMPONENT_CLASS_NAME);

                    MethodMatcher createViewEntryMatcher = MethodMatcher.create()
                            .paramTypes("android.content.Context", "android.view.ViewGroup");

                    List<MethodData> createMethods = bridge.findMethod(FindMethod.create()
                            .searchInClass(Collections.singleton(mentionedClassData))
                            .matcher(createViewEntryMatcher));

                    for (MethodData md : createMethods) {
                        String returnType = md.getReturnTypeName();
                        if (!returnType.equals("java.lang.Object")) {
                            VIEW_ENTRY_CLASS_NAME = returnType;
                            Log.d(TAG, "动态推导出 ViewEntry 接口: " + VIEW_ENTRY_CLASS_NAME);
                            break;
                        }
                    }

                    int dollarIndex = VIEW_ENTRY_CLASS_NAME.indexOf('$');
                    if (dollarIndex != -1) {
                        String outerClassPrefix = VIEW_ENTRY_CLASS_NAME.substring(0, dollarIndex + 1);

                        ClassMatcher bMatcher = ClassMatcher.create()
                                .addInterface(ClassMatcher.create().className(VIEW_ENTRY_CLASS_NAME))
                                .addMethod(MethodMatcher.create().name("<init>").paramCount(1));

                        List<ClassData> bClassList = bridge.findClass(FindClass.create().matcher(bMatcher));

                        for (ClassData bClass : bClassList) {
                            if (bClass.getName().startsWith(outerClassPrefix)) {
                                UI_COMPONENT_B_CLASS_NAME = bClass.getName();
                                Log.d(TAG, "动态推导出 ViewEntry 实现类 (原 subclass b): " + UI_COMPONENT_B_CLASS_NAME);
                                break;
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "视频提及特征缺失，缓存为空名字");
                }
                ClassMatcher splashMatcher = ClassMatcher.create()
                        .usingStrings("onSplashReady， realReady = ", "showSkipButton, skip clicked");

                List<ClassData> splashClassResult = bridge.findClass(FindClass.create().matcher(splashMatcher));


                if (!splashClassResult.isEmpty()) {
                    ClassData splashClassData = splashClassResult.get(0);
                    BASE_SPLASH_CLASS_NAME = splashClassData.getName();
                    Log.d(TAG, "找到 BaseSplash 类: " + BASE_SPLASH_CLASS_NAME);

                    // 查找 onSplashReady 方法
                    MethodMatcher readyMethodMatcher = MethodMatcher.create()
                            .paramTypes("boolean")
                            .usingStrings("onSplashReady， realReady = ");

                    List<MethodData> readyMethodResult = bridge.findMethod(FindMethod.create()
                            .searchInClass(Collections.singleton(splashClassData))
                            .matcher(readyMethodMatcher));

                    if (!readyMethodResult.isEmpty()) {
                        SPLASH_READY_METHOD_NAME = readyMethodResult.get(0).getName();
                        Log.d(TAG, "找到 onSplashReady 方法: " + SPLASH_READY_METHOD_NAME);
                    }

                    // 查找 skip clicked 方法
                    MethodMatcher skipMethodMatcher = MethodMatcher.create()
                            .paramCount(0)
                            .usingStrings("showSkipButton, skip clicked");

                    List<MethodData> skipMethodResult = bridge.findMethod(FindMethod.create()
                            .searchInClass(Collections.singleton(splashClassData))
                            .matcher(skipMethodMatcher));

                    if (!skipMethodResult.isEmpty()) {
                        SPLASH_SKIP_METHOD_NAME = skipMethodResult.get(0).getName();
                        Log.d(TAG, "找到 SkipButton 方法: " + SPLASH_SKIP_METHOD_NAME);
                    }
                } else {
                    Log.d(TAG, "开屏特征缺失，缓存为空名字");
                }

                List<ClassData> searchResultAdapterClassDataList = bridge.findClass(FindClass.create()
                        .searchPackages("com.bilibili.search2.result.all")
                        .matcher(
                                ClassMatcher.create()
                                        .superClass("androidx.recyclerview.widget.RecyclerView$Adapter")
                                        .addMethod(
                                                MethodMatcher.create().name("getItemCount")
                                        )
                                        .addMethod(MethodMatcher.create().name("getItemViewType"))
                                        .addMethod(MethodMatcher.create().name("onCreateViewHolder"))
                                        .addMethod(MethodMatcher.create().name("onBindViewHolder"))
                        )
                );
                SEARCH_RESULT_ADAPTER_CLASS_NAME = firstName(searchResultAdapterClassDataList, ClassData::getName);

                List<ClassData> homeCardAdapterClassDataList = bridge.findClass(FindClass.create()
                        .matcher(
                                ClassMatcher.create()
                                        .usingStrings(
                                                "BannerV8Data(idx=",
                                                ", cardType=",
                                                ", extraRptFields="
                                        )
                        )
                );
                HOME_CARD_ADAPTER_CLASS_NAME = firstName(homeCardAdapterClassDataList, ClassData::getName);
                List<MethodData> cardInfoSettingMethodDataList = findMethodsInClasses(bridge,
                        homeCardAdapterClassDataList, MethodMatcher.create()
                                .paramCount(1).paramTypes("java.util.List"));
                CARD_INFO_SETTING_METHOD_NAME = firstName(cardInfoSettingMethodDataList, MethodData::getMethodName);

                List<ClassData> pegasusParsers = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().className("com.bilibili.pegasus.request.PegasusGsonParser")));
                List<MethodData> pegasusGsonParserParserMethodDataList = findMethodsInClasses(bridge,
                        pegasusParsers, MethodMatcher.create()
                                .paramCount(1).paramTypes("okhttp3.ResponseBody"));
                PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = firstName(
                        pegasusGsonParserParserMethodDataList, MethodData::getMethodName);

                List<ClassData> vipPredictClients = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().className("com.bilibili.tensorflow.model.mem.VipOpenMembershipPredictTFClient")));
                List<MethodData> vipOpenMembershipPredictScoreMethodDataList = findMethodsInClasses(bridge,
                        vipPredictClients, MethodMatcher.create()
                                .paramCount(2).paramTypes("java.util.List", "java.nio.ByteBuffer"));
                VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME = firstName(
                        vipOpenMembershipPredictScoreMethodDataList, MethodData::getMethodName);
                List<ClassData> vipPredictRequestClassDataList = bridge.findClass(FindClass.create()
                        .searchPackages("retrofit2")
                        .matcher(ClassMatcher.create()
                                .usingStrings("Response from ", " was null but response body type was declared as non-null")
                        )
                );
                VIP_PREDICT_REQUEST_CLASS_NAME = firstName(vipPredictRequestClassDataList, ClassData::getName);

                List<ClassData> nativeWebViewSubClassDataList = bridge.findClass(FindClass.create()
                        .searchPackages("com.bilibili.app.comm")
                        .matcher(ClassMatcher.create()
                                // 1. 父类必须是 WebViewClient
                                .superClass("android.webkit.WebViewClient")
                                // 2. 字段总数严格等于 2 (去掉对未知类名的字段类型匹配)
                                .fieldCount(2)
                                // 3. 实现了 shouldInterceptRequest(WebView, WebResourceRequest)
                                .addMethod(MethodMatcher.create()
                                        .name("shouldInterceptRequest")
                                        .paramTypes(
                                                "android.webkit.WebView",
                                                "android.webkit.WebResourceRequest"
                                        )
                                )
                                // 4. 重写了 Ssl 错误处理（加这一层双保险，彻底排除其它无关实现）
                                .addMethod(MethodMatcher.create()
                                        .name("onReceivedSslError")
                                        .paramTypes(
                                                "android.webkit.WebView",
                                                "android.webkit.SslErrorHandler",
                                                "android.net.http.SslError"
                                        )
                                )
                        )
                );
                NATIVE_WEBVIEW_SUB_CLASS_NAME = firstName(nativeWebViewSubClassDataList, ClassData::getName);
                List<ClassData> networkShowToastServiceClassDataList = bridge.findClass(FindClass.create()
                        .searchPackages("com.bilibili.ship.theseus.united.player.mediaplay.network")
                        .matcher(ClassMatcher.create()
                                .usingStrings("show cellular toast, app once: ", ", already show: ")
                        )
                );
                NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME = firstName(networkShowToastServiceClassDataList, ClassData::getName);

                List<MethodData> networkShowToastServiceMethodDataList = findMethodsInClasses(bridge,
                        networkShowToastServiceClassDataList, MethodMatcher.create()
                                .usingStrings("NetworkShowToastService", "not china sim user, skip free data guide toast"));
                NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME = firstName(
                        networkShowToastServiceMethodDataList, MethodData::getMethodName);

                List<ClassData> requestInterceptorClassDataList =
                        bridge.findClass(FindClass.create()
                                .searchPackages("com.bilibili.app.comm.list.common.api")
                                .matcher(ClassMatcher.create()
                                        .superClass(
                                                "com.bilibili.okretro.interceptor.DefaultRequestInterceptor"
                                        )
                                        .fieldCount(1)
                                )
                        );

                if (requestInterceptorClassDataList.isEmpty())
                    Log.e(TAG, "requestInterceptorClassDataList is empty");
                else REQUEST_INTERCEPTOR = requestInterceptorClassDataList.get(0).getName();

                Properties cacheProps = scan.cacheProperties(cacheKey);
                if (cacheProps == null) {
                    Log.d(TAG, "宿主特征缺失，保留原名字并在下次启动重试扫描");
                    return;
                }
                if (cacheFile == null) {
                    return;
                }
                cacheFile.getParentFile().mkdirs();
                // Atomic: write a sibling file first, then rename, so a second process
                // scanning in parallel can never read a half-written cache.
                File tmp = new File(cacheFile.getParentFile(), cacheFile.getName() + ".tmp");
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    cacheProps.store(fos, "DexKit Obfuscation Cache for BiliBili");
                }
                if (!tmp.renameTo(cacheFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    cacheFile.delete();
                    if (!tmp.renameTo(cacheFile)) {
                        Log.w(TAG, "cache rename failed, left " + tmp.getName());
                    }
                }
                Log.d(TAG, "DexKit scan complete in " + (System.currentTimeMillis() - start)
                        + "ms, cache saved, key=" + cacheKey);
            } catch (Exception e) {
                Log.d(TAG, "Exception during DexKit scan: " + e.getMessage());
            }
        }
    }

    /** Commits names only after a complete scan observes a host-specific feature. */
    final class MobileScan implements AutoCloseable {
        private final Properties previous = mobileCacheProperties("");
        private boolean accepted;

        private MobileScan() {
            clearMobileNames();
        }

        Properties cacheProperties(String cacheKey) {
            if (!hasMobileHostFeature()) {
                return null;
            }
            Properties props = mobileCacheProperties(cacheKey);
            accepted = true;
            return props;
        }

        @Override
        public void close() {
            if (!accepted) {
                applyMobileProperties(previous);
            }
        }
    }

    MobileScan beginMobileScan() {
        return new MobileScan();
    }

    private boolean hasMobileHostFeature() {
        // Reuse host-specific matches; generic adapters, ViewBinding and retrofit can occur in stubs.
        return !CHRONOS_RPC_CLASS_NAME.isEmpty()
                || !DESCRIPTION_TEXTVIEW_CLASS_NAME.isEmpty()
                || !INTERNATIONAL_CHRONOS_RPC_CLASS_NAME.isEmpty()
                || !PEGASUS_MODEL_CLASS_NAME.isEmpty()
                || !BASE_SPLASH_CLASS_NAME.isEmpty()
                || !VIDEO_MENTIONED_COMPONENT_CLASS_NAME.isEmpty()
                || !SEARCH_RESULT_ADAPTER_CLASS_NAME.isEmpty()
                || !HOME_CARD_ADAPTER_CLASS_NAME.isEmpty()
                || !PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME.isEmpty()
                || !VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME.isEmpty()
                || !NATIVE_WEBVIEW_SUB_CLASS_NAME.isEmpty()
                || !NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME.isEmpty()
                || !REQUEST_INTERCEPTOR.isEmpty();
    }

    private void applyMobileProperties(Properties props) {
        albumRecycleViewHolderClassName = props.getProperty("albumRecycleViewHolderClassName", albumRecycleViewHolderClassName);
        publishArchiveCollectionFieldName = props.getProperty("publishArchiveCollectionFieldName", publishArchiveCollectionFieldName);
        albumSelectPageViewClassName = props.getProperty("albumSelectPageViewClassName", albumSelectPageViewClassName);
        RECYCLER_VIEW_FIELD_NAME = props.getProperty("RECYCLER_VIEW_FIELD_NAME", RECYCLER_VIEW_FIELD_NAME);
        CHRONOS_RPC_CLASS_NAME = props.getProperty("CHRONOS_RPC_CLASS_NAME",
                props.getProperty("chronosRpcClassName", CHRONOS_RPC_CLASS_NAME));
        DESCRIPTION_TEXTVIEW_CLASS_NAME = props.getProperty("DESCRIPTION_TEXTVIEW_CLASS_NAME", DESCRIPTION_TEXTVIEW_CLASS_NAME);
        INTERNATIONAL_CHRONOS_RPC_CLASS_NAME = props.getProperty("INTERNATIONAL_CHRONOS_RPC_CLASS_NAME", INTERNATIONAL_CHRONOS_RPC_CLASS_NAME);
        INTERNATIONAL_INVOKE_METHOD_NAME = props.getProperty("INTERNATIONAL_INVOKE_METHOD_NAME", INTERNATIONAL_INVOKE_METHOD_NAME);
        PEGASUS_MODEL_CLASS_NAME = props.getProperty("PEGASUS_MODEL_CLASS_NAME", PEGASUS_MODEL_CLASS_NAME);
        BASE_SPLASH_CLASS_NAME = props.getProperty("BASE_SPLASH_CLASS_NAME", BASE_SPLASH_CLASS_NAME);
        SPLASH_READY_METHOD_NAME = props.getProperty("SPLASH_READY_METHOD_NAME", SPLASH_READY_METHOD_NAME);
        SPLASH_SKIP_METHOD_NAME = props.getProperty("SPLASH_SKIP_METHOD_NAME", SPLASH_SKIP_METHOD_NAME);
        VIDEO_MENTIONED_COMPONENT_CLASS_NAME = props.getProperty("VIDEO_MENTIONED_COMPONENT_CLASS_NAME", VIDEO_MENTIONED_COMPONENT_CLASS_NAME);
        UI_COMPONENT_B_CLASS_NAME = props.getProperty("UI_COMPONENT_B_CLASS_NAME", UI_COMPONENT_B_CLASS_NAME);
        VIEW_ENTRY_CLASS_NAME = props.getProperty("VIEW_ENTRY_CLASS_NAME", VIEW_ENTRY_CLASS_NAME);
        SEARCH_RESULT_ADAPTER_CLASS_NAME = props.getProperty("SEARCH_RESULT_ADAPTER_CLASS_NAME", SEARCH_RESULT_ADAPTER_CLASS_NAME);
        HOME_CARD_ADAPTER_CLASS_NAME = props.getProperty("HOME_CARD_ADAPTER_CLASS_NAME", HOME_CARD_ADAPTER_CLASS_NAME);
        CARD_INFO_SETTING_METHOD_NAME = props.getProperty("CARD_INFO_SETTING_METHOD_NAME", CARD_INFO_SETTING_METHOD_NAME);
        PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = props.getProperty("PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME", PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME);
        VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME = props.getProperty("VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME", VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME);
        VIP_PREDICT_REQUEST_CLASS_NAME = props.getProperty("VIP_PREDICT_REQUEST_CLASS_NAME", VIP_PREDICT_REQUEST_CLASS_NAME);
        NATIVE_WEBVIEW_SUB_CLASS_NAME = props.getProperty("NATIVE_WEBVIEW_SUB_CLASS_NAME", NATIVE_WEBVIEW_SUB_CLASS_NAME);
        NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME = props.getProperty("NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME", NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME);
        NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME = props.getProperty("NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME", NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME);
        REQUEST_INTERCEPTOR = props.getProperty("REQUEST_INTERCEPTOR", REQUEST_INTERCEPTOR);
    }

    /** Starts optional discovery with an explicit absent value for every mobile feature. */
    void clearMobileNames() {
        albumRecycleViewHolderClassName = "";
        publishArchiveCollectionFieldName = "";
        albumSelectPageViewClassName = "";
        RECYCLER_VIEW_FIELD_NAME = "";
        CHRONOS_RPC_CLASS_NAME = "";
        DESCRIPTION_TEXTVIEW_CLASS_NAME = "";
        INTERNATIONAL_CHRONOS_RPC_CLASS_NAME = "";
        INTERNATIONAL_INVOKE_METHOD_NAME = "";
        PEGASUS_MODEL_CLASS_NAME = "";
        BASE_SPLASH_CLASS_NAME = "";
        SPLASH_READY_METHOD_NAME = "";
        SPLASH_SKIP_METHOD_NAME = "";
        VIDEO_MENTIONED_COMPONENT_CLASS_NAME = "";
        UI_COMPONENT_B_CLASS_NAME = "";
        VIEW_ENTRY_CLASS_NAME = "";
        SEARCH_RESULT_ADAPTER_CLASS_NAME = "";
        HOME_CARD_ADAPTER_CLASS_NAME = "";
        CARD_INFO_SETTING_METHOD_NAME = "";
        PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = "";
        VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME = "";
        VIP_PREDICT_REQUEST_CLASS_NAME = "";
        NATIVE_WEBVIEW_SUB_CLASS_NAME = "";
        NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME = "";
        NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME = "";
        REQUEST_INTERCEPTOR = "";
    }

    Properties mobileCacheProperties(String cacheKey) {
        Properties props = new Properties();
        props.setProperty("cache_key", cacheKey);
        props.setProperty("mobile_schema", MOBILE_CACHE_SCHEMA);
        props.setProperty("albumRecycleViewHolderClassName", albumRecycleViewHolderClassName);
        props.setProperty("publishArchiveCollectionFieldName", publishArchiveCollectionFieldName);
        props.setProperty("albumSelectPageViewClassName", albumSelectPageViewClassName);
        props.setProperty("RECYCLER_VIEW_FIELD_NAME", RECYCLER_VIEW_FIELD_NAME);
        props.setProperty("CHRONOS_RPC_CLASS_NAME", CHRONOS_RPC_CLASS_NAME);
        props.setProperty("DESCRIPTION_TEXTVIEW_CLASS_NAME", DESCRIPTION_TEXTVIEW_CLASS_NAME);
        props.setProperty("INTERNATIONAL_CHRONOS_RPC_CLASS_NAME", INTERNATIONAL_CHRONOS_RPC_CLASS_NAME);
        props.setProperty("INTERNATIONAL_INVOKE_METHOD_NAME", INTERNATIONAL_INVOKE_METHOD_NAME);
        props.setProperty("PEGASUS_MODEL_CLASS_NAME", PEGASUS_MODEL_CLASS_NAME);
        props.setProperty("BASE_SPLASH_CLASS_NAME", BASE_SPLASH_CLASS_NAME);
        props.setProperty("SPLASH_READY_METHOD_NAME", SPLASH_READY_METHOD_NAME);
        props.setProperty("SPLASH_SKIP_METHOD_NAME", SPLASH_SKIP_METHOD_NAME);
        props.setProperty("VIDEO_MENTIONED_COMPONENT_CLASS_NAME", VIDEO_MENTIONED_COMPONENT_CLASS_NAME);
        props.setProperty("UI_COMPONENT_B_CLASS_NAME", UI_COMPONENT_B_CLASS_NAME);
        props.setProperty("VIEW_ENTRY_CLASS_NAME", VIEW_ENTRY_CLASS_NAME);
        props.setProperty("SEARCH_RESULT_ADAPTER_CLASS_NAME", SEARCH_RESULT_ADAPTER_CLASS_NAME);
        props.setProperty("HOME_CARD_ADAPTER_CLASS_NAME", HOME_CARD_ADAPTER_CLASS_NAME);
        props.setProperty("CARD_INFO_SETTING_METHOD_NAME", CARD_INFO_SETTING_METHOD_NAME);
        props.setProperty("PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME", PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME);
        props.setProperty("VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME", VIP_OPEN_MEMBERSHIP_PREDICT_SCORE_METHOD_NAME);
        props.setProperty("VIP_PREDICT_REQUEST_CLASS_NAME", VIP_PREDICT_REQUEST_CLASS_NAME);
        props.setProperty("NATIVE_WEBVIEW_SUB_CLASS_NAME", NATIVE_WEBVIEW_SUB_CLASS_NAME);
        props.setProperty("NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME", NETWORK_SHOW_TOAST_SERVICE_CLASS_NAME);
        props.setProperty("NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME", NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME);
        props.setProperty("REQUEST_INTERCEPTOR", REQUEST_INTERCEPTOR);
        return props;
    }

    static <T> String firstName(List<T> results, Function<T, String> name) {
        return results.isEmpty() ? "" : name.apply(results.get(0));
    }

    /** Keeps an absent class scope local, since an empty DexKit scope can widen the query. */
    static <T, R> List<R> queryInClasses(List<T> classes, Function<List<T>, List<R>> query) {
        return classes.isEmpty() ? Collections.emptyList() : query.apply(classes);
    }

    private static List<MethodData> findMethodsInClasses(DexKitBridge bridge,
                                                         List<ClassData> classes,
                                                         MethodMatcher matcher) {
        return queryInClasses(classes, scope -> bridge.findMethod(FindMethod.create()
                .searchInClass(scope).matcher(matcher)));
    }

    /**
     * Cache identity: module build + host APK fingerprint. LSPosed 注入的 classloader
     * 拿不到 CodeSource（null），所以不能用模块 APK 的 mtime 做 key。
     */
    private String cacheKeyFor(ApplicationInfo appInfo) {
        if (Host.isHdPackage(appInfo.packageName)) {
            return hdCacheKey(BuildConfig.VERSION_CODE,
                    PlayPageAdSniper.sources(hostLoader, appInfo.sourceDir));
        }
        long apkTime = 0L;
        long apkSize = 0L;
        try {
            File apk = new File(appInfo.sourceDir);
            apkTime = apk.lastModified();
            apkSize = apk.length();
        } catch (Throwable ignore) {
            // 只影响 key 精度
        }
        return "m" + BuildConfig.VERSION_CODE + "-h" + apkTime + "-s" + apkSize;
    }

    static String hdCacheKey(int moduleVersion, List<String> sources) {
        StringBuilder key = new StringBuilder("m").append(moduleVersion).append("-hd2");
        for (String source : sources) {
            File apk = new File(source);
            key.append('|').append(apk.getAbsolutePath())
                    .append(':').append(apk.lastModified()).append(':').append(apk.length());
        }
        return key.toString();
    }

    private static List<String> classNames(String value) {
        return value.isEmpty() ? Collections.emptyList() : Arrays.asList(value.split("\n"));
    }

    private static File cacheFileFor(ApplicationInfo appInfo) {
        try {
            return new File(appInfo.dataDir, "cache/dexkit_hook_cache.properties");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Shares each HD APK bridge between Pegasus and playback discovery on the worker. */
    private void resolveHd(ApplicationInfo appInfo) {
        long started = android.os.SystemClock.elapsedRealtime();
        File file = cacheFileFor(appInfo);
        String key = cacheKeyFor(appInfo);
        Set<String> underPlayer = new LinkedHashSet<>();
        Set<String> relate = new LinkedHashSet<>();
        boolean complete = true;
        boolean hostFound = false;
        PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = "l";
        System.loadLibrary("dexkit");
        for (String source : PlayPageAdSniper.sources(hostLoader, appInfo.sourceDir)) {
            try (DexKitBridge bridge = DexKitBridge.create(source)) {
                if (bridge == null) {
                    complete = false;
                    continue;
                }
                List<ClassData> parsers = bridge.findClass(FindClass.create().matcher(
                        ClassMatcher.create().className("com.bilibili.pegasus.request.PegasusGsonParser")));
                if (!parsers.isEmpty()) {
                    hostFound = true;
                    List<MethodData> methods = bridge.findMethod(FindMethod.create()
                            .searchInClass(parsers)
                            .matcher(MethodMatcher.create().paramCount(1).paramTypes("okhttp3.ResponseBody")));
                    if (!methods.isEmpty()) {
                        PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME = methods.get(0).getMethodName();
                    }
                }
                // LSPatch can expose the original host through a separate APK path.
                underPlayer.addAll(PlayPageAdSniper.findUnderPlayer(bridge));
                relate.addAll(PlayPageAdSniper.findRelate(bridge));
            } catch (Throwable error) {
                complete = false;
                FbaLog.e("[HD] DexKit 特征匹配失败: " + source, error);
            }
        }
        hdUnderPlayerClasses = new ArrayList<>(underPlayer);
        hdRelateClasses = new ArrayList<>(relate);
        FbaLog.i("[PERF] HD dexkit scan="
                + (android.os.SystemClock.elapsedRealtime() - started) + "ms"
                + " under=" + underPlayer.size() + " relate=" + relate.size()
                + " thread=" + Thread.currentThread().getName());
        hostFound |= !underPlayer.isEmpty() || !relate.isEmpty();
        if (!complete || !hostFound || file == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty("cache_key", key);
        props.setProperty("PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME",
                PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME);
        props.setProperty("hdUnderPlayerClasses", String.join("\n", hdUnderPlayerClasses));
        props.setProperty("hdRelateClasses", String.join("\n", hdRelateClasses));
        try {
            file.getParentFile().mkdirs();
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            try (FileOutputStream output = new FileOutputStream(tmp)) {
                props.store(output, "HD Pegasus and Gripper cache");
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            FbaLog.e("[HD] DexKit 缓存保存失败，下次启动重试", error);
        }
    }

    /** 后台线程扫描；{@code onDone} 一定回到主线程执行。 */
    public void resolveAsync(final ApplicationInfo appInfo, final Runnable onDone) {
        final Handler handler = new Handler(Looper.getMainLooper());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    resolve(appInfo);
                } catch (Throwable t) {
                    Log.d(TAG, "async resolve failed: " + t);
                } finally {
                    if (onDone != null) {
                        handler.post(onDone);
                    }
                }
            }
        }, "fba-dexkit");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    public ClassData getOuterClass(DexKitBridge bridge, ClassData innerClassData) {
        String innerClassName = innerClassData.getName();
        int dollarIndex = innerClassName.indexOf('$');
        if (dollarIndex == -1) {
            return null;
        }
        String outerClassName = innerClassName.substring(0, dollarIndex);
        return bridge.getClassData(outerClassName);
    }

    private static void listDataPrint(List<?> dataList) {
        if (dataList.isEmpty()) {
            Log.d(TAG, "error: data list is empty");
            return;
        }
        Log.d(TAG, "found " + dataList.size() + " items");
        for (Object data : dataList) {
            if (data instanceof ClassData) {
                Log.d(TAG, "found class name: " + ((ClassData) data).getName());
            } else if (data instanceof MethodData) {
                Log.d(TAG, "found method name: " + ((MethodData) data).getName());
            } else if (data instanceof FieldData) {
                Log.d(TAG, "found field name: " + ((FieldData) data).getName());
            }
        }
    }
}