package com.github.tvbox.osc.api;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.blankj.utilcode.util.ToastUtils;
import com.github.UA;
import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveSourceBean;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AES;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.AlistDriveUtil;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.cache.CacheMode;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Response;
import com.lzy.okgo.request.GetRequest;
import com.orhanobut.hawk.Hawk;
import com.undcover.freedom.pyramid.PythonLoader;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class ApiConfig implements Serializable {
    private static ApiConfig instance;
    private LinkedHashMap<String, SourceBean> sourceBeanList;
    private SourceBean mHomeSource = new SourceBean();
    private ParseBean mDefaultParse;
    private List<LiveChannelGroup> liveChannelGroupList;
    private List<ParseBean> parseBeanList;
    private List<String> vipParseFlags;
    private List<IJKCode> ijkCodes = new ArrayList<>();
    private String spider = null;
    public String wallpaper = "";
    private SourceBean emptyHome = new SourceBean();

    private JarLoader jarLoader = new JarLoader();

    public static String userAgent = "okhttp/3.15";

    public static String requestAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9";

    private ApiConfig() {
        sourceBeanList = new LinkedHashMap<>();
        liveChannelGroupList = new ArrayList<>();
        parseBeanList = new ArrayList<>();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    public static void release() {
        instance = null;
    }

    public static String FindResult(String json, String configKey) {
        String content = json;
        try {
            if (AES.isJson(content)) return content;
            Pattern pattern = Pattern.compile("[A-Za-z0]{8}\\*\\*");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                content = content.substring(content.indexOf(matcher.group()) + 10);
                content = new String(Base64.decode(content, Base64.DEFAULT));
            }
            if (content.startsWith("2423")) {
                String data = content.substring(content.indexOf("2324") + 4, content.length() - 26);
                content = new String(AES.toBytes(content)).toLowerCase();
                String key = AES.rightPadding(content.substring(content.indexOf("$#") + 2, content.indexOf("#$")), "0", 16);
                String iv = AES.rightPadding(content.substring(content.length() - 13), "0", 16);
                json = AES.CBC(data, key, iv);
            } else if (configKey != null && !AES.isJson(content)) {
                json = AES.ECB(content, configKey);
            } else {
                json = content;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    private static byte[] getImgJar(String body) {
        Pattern pattern = Pattern.compile("[A-Za-z0]{8}\\*\\*");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            body = body.substring(body.indexOf(matcher.group()) + 10);
            return Base64.decode(body, Base64.DEFAULT);
        }
        return "".getBytes();
    }

    private boolean isConfigLoadCache;

    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        isConfigLoadCache = false;
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl.isEmpty()) {
            callback.error("-1");
            return;
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
        if (useCache && cache.exists()) {
            try {
                parseJson(apiUrl, cache);
                callback.success();
                return;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        String TempKey = null, configUrl = "", pk = ";pk;";
        if (apiUrl.contains(pk)) {
            String[] a = apiUrl.split(pk);
            TempKey = a[1];
            if (apiUrl.startsWith("clan")) {
                configUrl = clanToAddress(a[0]);
            } else if (apiUrl.startsWith("http")) {
                configUrl = a[0];
            } else {
                configUrl = "http://" + a[0];
            }
        } else if (apiUrl.startsWith("clan")) {
            configUrl = clanToAddress(apiUrl);
        } else if (!apiUrl.startsWith("http")) {
            configUrl = "http://" + configUrl;
        } else {
            configUrl = apiUrl;
        }
        String configKey = TempKey;
        GetRequest<String> stringGetRequest = OkGo.get(configUrl);
        if (configUrl.startsWith("https://gitea")) {
            stringGetRequest.headers("User-Agent", UA.random());
        } else {
            stringGetRequest.headers("User-Agent", userAgent);
        }
        stringGetRequest.cacheMode(CacheMode.IF_NONE_CACHE_REQUEST).cacheTime(3L * 24L * 60L * 60L * 1000L).execute(new AbsCallback<String>() {
            @Override
            public void onSuccess(Response<String> response) {
                if (isConfigLoadCache) {
                    return;
                }
                configLoad(response, configKey, apiUrl, cache, callback);
            }

            @Override
            public void onCacheSuccess(Response<String> response) {
                super.onCacheSuccess(response);
                try {
                    response.setBody(fixRelativePath(response.body(), apiUrl));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                isConfigLoadCache = true;
                configLoad(response, configKey, apiUrl, cache, callback);
            }

            @Override
            public void onError(Response<String> response) {
                super.onError(response);
                if (cache.exists()) {
                    try {
                        parseJson(apiUrl, cache);
                        callback.success();
                        return;
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
                callback.error("拉取配置失败\n" + (response.getException() != null ? response.getException().getMessage() : ""));
            }

            public String convertResponse(okhttp3.Response response) throws Throwable {
                String responseStr = "";
                if (response.body() != null) {
                    responseStr = response.body().string();
                }
                return fixRelativePath(responseStr, apiUrl);
            }
        });
    }

    private String fixRelativePath(String responseBoy, String apiUrl) throws IOException {
        String result = "";
        if (responseBoy == null) {
            result = "";
        } else {
            result = responseBoy;
        }
        if (apiUrl.startsWith("clan")) {
            result = clanContentFix(clanToAddress(apiUrl), result);
        }
        //假相對路徑
        result = fixContentPath(apiUrl, result);
        return result;
    }

    private void configLoad(Response<String> response, String configKey, String apiUrl, File cache, LoadConfigCallback callback) {
        try {
            String json = response.body();
            json = FindResult(json, configKey);
            parseJson(apiUrl, json);
            try {
                File cacheDir = cache.getParentFile();
                if (!cacheDir.exists()) cacheDir.mkdirs();
                if (cache.exists()) cache.delete();
                FileOutputStream fos = new FileOutputStream(cache);
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
                fos.close();
            } catch (Throwable th) {
                th.printStackTrace();
            }
            callback.success();
        } catch (Throwable th) {
            th.printStackTrace();
            callback.error("解析配置失败");
        }
    }

    private boolean isCacheReady;


    public void loadJar(boolean useCache, String spider, LoadConfigCallback callback) {
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");
        if (!md5.isEmpty() || useCache) {
            if (cache.exists() && useCache && MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                } else {
                    callback.error("jar 缓存加载失败");
                }
                return;
            }
        }
        boolean isJarInImg = jarUrl.startsWith("img+");
        jarUrl = jarUrl.replace("img+", "");
        GetRequest<File> request = OkGo.<File>get(jarUrl).tag("downLoadJar").cacheMode(CacheMode.FIRST_CACHE_THEN_REQUEST).cacheTime(10L * 60L * 60L * 1000);
        isCacheReady = false;
        if (jarUrl.startsWith("https://gitea")) {
            request.headers("User-Agent", UA.randomOne());
        } else {
            request.headers("User-Agent", userAgent);
        }
        request.execute(new AbsCallback<File>() {

            @Override
            public File convertResponse(okhttp3.Response response) throws Throwable {
                File cacheDir = cache.getParentFile();
                if (!cacheDir.exists()) cacheDir.mkdirs();
                if (cache.exists()) cache.delete();
                FileOutputStream fos = new FileOutputStream(cache);
                if (isJarInImg) {
                    String respData = response.body().string();
                    byte[] imgJar = getImgJar(respData);
                    fos.write(imgJar);
                } else {
                    fos.write(response.body().bytes());
                }
                fos.flush();
                fos.close();
                return cache;
            }

            @Override
            public void onSuccess(Response<File> response) {
                if (isCacheReady) {
                    return;
                }
                if (response.body().exists()) {
                    if (jarLoader.load(response.body().getAbsolutePath())) {
                        callback.success();
                    } else {
                        callback.error("");
                    }
                } else {
                    callback.error("jar不存在");
                }
            }

            @Override
            public void onCacheSuccess(Response<File> response) {
                super.onCacheSuccess(response);
                if (response.body().exists()) {
                    if (jarLoader.load(response.body().getAbsolutePath())) {
                        isCacheReady = true;
                        callback.success();
                    } else {
                        callback.error("");
                    }
                } else {
                    callback.error("jar不存在");
                }
            }

            @Override
            public void onError(Response<File> response) {
                super.onError(response);
                callback.error(response.getException().getMessage());
            }
        });
    }

    private void parseJson(String apiUrl, File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s = "";
        while ((s = bReader.readLine()) != null) {
            sb.append(s + "\n");
        }
        bReader.close();
        parseJson(apiUrl, sb.toString());
    }

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    private void parseJson(String apiUrl, String jsonStr) {
        JsonObject infoJson = new Gson().fromJson(jsonStr, JsonObject.class);
        // spider
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        // wallpaper
        wallpaper = DefaultConfig.safeJsonString(infoJson, "wallpaper", "");
        // 远端站点源
        SourceBean firstSite = null;
        for (JsonElement opt : infoJson.get("sites").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            SourceBean sb = new SourceBean();
            String siteKey = obj.get("key").getAsString().trim();
            sb.setKey(siteKey);
            sb.setName(obj.get("name").getAsString().trim());
            sb.setType(obj.get("type").getAsInt());
            sb.setApi(obj.get("api").getAsString().trim());
            sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
            sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
            sb.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
            sb.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
            if (obj.has("ext") && (obj.get("ext").isJsonObject() || obj.get("ext").isJsonArray())) {
                sb.setExt(obj.get("ext").toString());
            } else {
                sb.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
            }
            sb.setJar(DefaultConfig.safeJsonString(obj, "jar", ""));
            sb.setPlayerType(DefaultConfig.safeJsonInt(obj, "playerType", -1));
            sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
            sb.setClickSelector(DefaultConfig.safeJsonString(obj, "click", ""));
            if (firstSite == null) firstSite = sb;
            sourceBeanList.put(siteKey, sb);
            if (siteKey.toLowerCase().contains("alist") || sb.getApi().toLowerCase().contains("alist")) {
                executorService.execute(() -> OkGo.<String>get(sb.getExt()).execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            AlistDriveUtil.saveAlist(JsonParser.parseString(response.body().trim()).getAsJsonObject());
                        } catch (Exception e) {

                        }

                    }
                }));
            }
        }
        if (sourceBeanList != null && sourceBeanList.size() > 0) {
            String home = Hawk.get(HawkConfig.HOME_API, "");
            SourceBean sh = getSource(home);
            if (sh == null) {
                setSourceBean(firstSite);
            } else {
                setSourceBean(sh);
            }
        }
        //只有加载了py的情况下才开始解析py站点
        if (App.getInstance().getPyLoadSuccess()) {
            executorService.execute(() -> PythonLoader.getInstance().setConfig(jsonStr));
        }

        // 需要使用vip解析的flag
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags");
        // 解析地址
        parseBeanList.clear();
        if (infoJson.has("parses")) {
            JsonArray parses = infoJson.get("parses").getAsJsonArray();
            for (JsonElement opt : parses) {
                JsonObject obj = (JsonObject) opt;
                ParseBean pb = new ParseBean();
                pb.setName(obj.get("name").getAsString().trim());
                pb.setUrl(obj.get("url").getAsString().trim());
                String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
                pb.setExt(ext);
                pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                parseBeanList.add(pb);
            }
        }
        // 获取默认解析
        if (parseBeanList != null && parseBeanList.size() > 0) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse)) for (ParseBean pb : parseBeanList) {
                if (pb.getName().equals(defaultParse)) setDefaultParse(pb);
            }
            if (mDefaultParse == null) setDefaultParse(parseBeanList.get(0));
        }
        LiveSourceBean liveSourceBean = Hawk.get(HawkConfig.LIVE_SOURCE_URL_CURRENT, null);
        if (liveSourceBean != null) {//如果当前有选中的直播地址，则直接用选中的
            selectLiveUrlAndLoad(liveSourceBean);
            //如果当前选中的直播地址和当前线路不一致，则保存当前线路的直播地址
            if (!TextUtils.equals(apiUrl, liveSourceBean.getExtraKey())) {
                saveServerLivesUrl(getLiveUrlFromServer(apiUrl, infoJson));
            }
        } else {
            loadLiveSourceUrl(apiUrl, infoJson);
        }
        //video parse rule for host
        if (infoJson.has("rules")) {
            VideoParseRuler.clearRule();
            for (JsonElement oneHostRule : infoJson.getAsJsonArray("rules")) {
                JsonObject obj = (JsonObject) oneHostRule;
                String host = obj.get("host").getAsString();
                if (obj.has("rule")) {
                    JsonArray ruleJsonArr = obj.getAsJsonArray("rule");
                    ArrayList<String> rule = new ArrayList<>();
                    for (JsonElement one : ruleJsonArr) {
                        String oneRule = one.getAsString();
                        rule.add(oneRule);
                    }
                    if (rule.size() > 0) {
                        VideoParseRuler.addHostRule(host, rule);
                    }
                }
                if (obj.has("filter")) {
                    JsonArray filterJsonArr = obj.getAsJsonArray("filter");
                    ArrayList<String> filter = new ArrayList<>();
                    for (JsonElement one : filterJsonArr) {
                        String oneFilter = one.getAsString();
                        filter.add(oneFilter);
                    }
                    if (filter.size() > 0) {
                        VideoParseRuler.addHostFilter(host, filter);
                    }
                }
            }
        }
        // 广告地址
        if (AdBlocker.isEmpty()) {
            if (infoJson.has("ads")) {
                for (JsonElement host : infoJson.getAsJsonArray("ads")) {
                    AdBlocker.addAdHost(host.getAsString());
                }
            } else {
                for (JsonElement ads : AdBlocker.defaultJsonObject.getAsJsonArray("ads")) {
                    AdBlocker.addAdHost(ads.getAsString());
                }
            }
        }
        // IJK解码配置

        if (ijkCodes.isEmpty()) {
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "");
            for (JsonElement opt : AdBlocker.defaultJsonObject.get("ijk").getAsJsonArray()) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                ijkCodes.add(codec);
            }
            if (!foundOldSelect && ijkCodes.size() > 0) {
                ijkCodes.get(0).selected(true);
            }
        }

    }

    public void loadLiveSourceUrl(String apiUrl, JsonObject infoJson) {
        // 直播源
        liveChannelGroupList.clear();           //修复从后台切换重复加载频道列表
        String liveUrl = getLiveUrlFromServer(apiUrl, infoJson);
        if (!TextUtils.isEmpty(liveUrl)) {
            saveServerLivesUrl(liveUrl);
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setGroupName(liveUrl);
            liveChannelGroupList.add(liveChannelGroup);
        }
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 1;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = ((JsonObject) groupElement).get("group").getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1) liveChannelGroup.setGroupPassword(splitGroupName[1]);
            else liveChannelGroup.setGroupPassword("");
            channelIndex = 0;
            for (JsonElement channelElement : ((JsonObject) groupElement).get("channels").getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1) sourceNames.add(splitText[1]);
                    else sourceNames.add("源" + Integer.toString(sourceIndex));
                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            liveChannelGroupList.add(liveChannelGroup);
        }
        if (!liveChannelGroupList.isEmpty()) {
            LiveChannelGroup collectedGroup = Hawk.get(HawkConfig.LIVE_CHANELE_COLLECTD, new LiveChannelGroup());
            collectedGroup.setGroupName("收藏频道");
            collectedGroup.isCollected = true;
            liveChannelGroupList.add(0, collectedGroup);
        }
    }

    public String getSpider() {
        return spider;
    }

    public Spider getCSP(SourceBean sourceBean) {
        //pyramid-add-start
        if (sourceBean.getApi().startsWith("py_")) {
            try {
                return PythonLoader.getInstance().getSpider(sourceBean.getKey(), sourceBean.getExt());
            } catch (Exception e) {
                e.printStackTrace();
                return new SpiderNull();
            }
        }
        return jarLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
    }

    public Object[] proxyLocal(Map param) {
        //pyramid-add-start
        try {
            String doStr = param.get("do").toString();
            if (param.containsKey("api")) {
                if (doStr.equals("ck")) return PythonLoader.getInstance().proxyLocal("", "", param);
                SourceBean sourceBean = ApiConfig.get().getSource(doStr);
                return PythonLoader.getInstance().proxyLocal(sourceBean.getKey(), sourceBean.getExt(), param);
            } else {
                if (doStr.equals("live"))
                    return PythonLoader.getInstance().proxyLocal("", "", param);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jarLoader.proxyInvoke(param);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();

        void retry();

        void error(String msg);
    }

    public interface FastParseCallback {
        void success(boolean parse, String url, Map<String, String> header);

        void fail(int code, String msg);
    }

    public SourceBean getSource(String key) {
        if (!sourceBeanList.containsKey(key)) return null;
        return sourceBeanList.get(key);
    }

    public void setSourceBean(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
        Hawk.put(HawkConfig.HOME_API, sourceBean.getKey());
        if (sourceBean.getKey().startsWith("py_") && !App.getInstance().getPyLoadSuccess()) {
            PythonLoader.getInstance().setApplication(App.getInstance());
            App.getInstance().setPyLoadSuccess(true);
            ToastUtils.showShort("第一次加载py会有点慢，等等~");
        }
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParse != null) this.mDefaultParse.setDefault(false);
        this.mDefaultParse = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParse;
    }

    public List<SourceBean> getSourceBeanList() {
        return new ArrayList<>(sourceBeanList.values());
    }

    public List<ParseBean> getParseBeanList() {
        return parseBeanList;
    }

    public List<String> getVipParseFlags() {
        return vipParseFlags;
    }

    public SourceBean getHomeSourceBean() {
        return mHomeSource == null ? emptyHome : mHomeSource;
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        if (ijkCodes == null || ijkCodes.isEmpty()) {
            return null;
        }
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(name)) return code;
        }
        return ijkCodes.get(0);
    }

    public static String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://", fix);
    }

    String fixContentPath(String url, String content) {
        if (content.contains("\"./")) {
            if (!url.startsWith("http") && !url.startsWith("clan://")) {
                url = "http://" + url;
            }
            if (url.startsWith("clan://")) url = clanToAddress(url);
            content = content.replace("./", url.substring(0, url.lastIndexOf("/") + 1));
        }
        return content;
    }


    //获取配置文件中的直播地址
    private String getLiveUrlFromServer(String apiUrl, JsonObject infoJson) {
        if (infoJson == null) {
            return "";
        }
        try {
            JsonObject livesOBJ = infoJson.get("lives").getAsJsonArray().get(0).getAsJsonObject();
            String liveSource = livesOBJ.toString();
            int index = liveSource.indexOf("proxy://");
            if (index != -1) {
                int endIndex = liveSource.lastIndexOf("\"");
                String realUrl;
                realUrl = DefaultConfig.checkReplaceProxy(liveSource.substring(index, endIndex));
                //clan
                String extUrl = Uri.parse(realUrl).getQueryParameter("ext");
                if (extUrl != null && !extUrl.isEmpty()) {
                    String extUrlFix = "";
                    if (extUrl.startsWith("http") || extUrl.startsWith("https")) {
                        extUrlFix = extUrl;
                    } else {
                        extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                    }

                    if (extUrlFix.startsWith("clan://")) {
                        extUrlFix = clanContentFix(clanToAddress(extUrlFix), extUrlFix);
                        extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        realUrl = realUrl.replace(extUrl, extUrlFix);
                    }
                    //修复直播明文加载错误
                    if (extUrlFix.startsWith("http") || extUrlFix.startsWith("https")) {
                        extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        realUrl = realUrl.replace(extUrl, extUrlFix);
                    }
                }
                return realUrl;
            } else {
                String lives = infoJson.get("lives").getAsJsonArray().get(0).getAsJsonObject().toString();
                if (!lives.contains("type")) {
                    loadLives(infoJson.get("lives").getAsJsonArray());
                } else {
                    JsonObject fengMiLives = infoJson.get("lives").getAsJsonArray().get(0).getAsJsonObject();
                    String type = fengMiLives.get("type").getAsString();
                    if (type.equals("0")) {
                        String url = fengMiLives.get("url").getAsString();
                        if (url.startsWith("http")) {
                            url = Base64.encodeToString(url.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        }
                        url = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + url;
                        return url;
                    }
                }
            }
        } catch (Exception e) {
            return "";
        }

        return "";
    }

    //选中直播地址并重载
    public void selectLiveUrlAndLoad(LiveSourceBean liveSourceBean) {
        liveChannelGroupList.clear();
        if (liveSourceBean == null) {
            return;
        }
        LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
        String url = liveSourceBean.getSourceUrl();
        if (!url.startsWith("http://127.0.0.1")) {
            if (isBase64Url(url)) {
                url = new String(Base64.decode(url, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP));
            }
            url = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + url;
        }
        ArrayList<LiveSourceBean> liveSourceBeanArrayList = Hawk.get(HawkConfig.LIVE_SOURCE_URL_HISTORY, new ArrayList<>());
        liveSourceBeanArrayList.remove(liveSourceBean);
        liveSourceBeanArrayList.add(0, liveSourceBean);
        Hawk.put(HawkConfig.LIVE_SOURCE_URL_HISTORY, liveSourceBeanArrayList);
        liveChannelGroup.setGroupName(url);
        liveChannelGroupList.add(liveChannelGroup);
    }

    /**
     * 保存直播地址，但是不再切换到该地址
     */
    private void saveServerLivesUrl(String liveUrl) {//
        LiveSourceBean liveSourceBean = new LiveSourceBean();
        liveSourceBean.setSourceUrl(liveUrl);
        liveSourceBean.setOfficial(true);
        liveSourceBean.setExtraKey(Hawk.get(HawkConfig.API_URL));
        MoreSourceBean moreSourceBean = Hawk.get(HawkConfig.API_URL_BEAN);
        if (moreSourceBean != null) {
            liveSourceBean.setSourceName(moreSourceBean.getSourceName() + "直播");
        }
        ArrayList<LiveSourceBean> liveSourceBeanArrayList = Hawk.get(HawkConfig.LIVE_SOURCE_URL_HISTORY, new ArrayList<>());
        if (!liveSourceBeanArrayList.contains(liveSourceBean)) {
            liveSourceBeanArrayList.add(liveSourceBean);
        }
        Hawk.put(HawkConfig.LIVE_SOURCE_URL_HISTORY, liveSourceBeanArrayList);
    }

    public boolean isBase64Url(String originUrl) {
        String base64Pattern = "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$";
        return Pattern.matches(base64Pattern, originUrl);
    }

    public void refreshSavedLiveBeans() {
        ArrayList<LiveSourceBean> liveSourceBeanArrayList = Hawk.get(HawkConfig.LIVE_SOURCE_URL_HISTORY, new ArrayList<>());
        boolean needUpdate = false;
        for (LiveSourceBean liveSourceBean : liveSourceBeanArrayList) {
            String url = liveSourceBean.getSourceUrl();
            if (!url.startsWith("http://127.0.0.1")) {
                if (isBase64Url(url)) {
                    url = new String(Base64.decode(url, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP));
                }
                url = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + url;
                liveSourceBeanArrayList.remove(liveSourceBean);
                liveSourceBean.setSourceUrl(url);
                needUpdate = true;
            }
        }
        if (!liveSourceBeanArrayList.isEmpty() && needUpdate) {
            Hawk.put(HawkConfig.LIVE_SOURCE_URL_HISTORY, liveSourceBeanArrayList);
        }
        LiveSourceBean liveSourceBean = Hawk.get(HawkConfig.LIVE_SOURCE_URL_CURRENT, null);
        if (liveSourceBean != null) {
            String url = liveSourceBean.getSourceUrl();
            if (!url.startsWith("http://127.0.0.1")) {
                if (isBase64Url(url)) {
                    url = new String(Base64.decode(url, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP));
                }
                url = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + url;
                liveSourceBean.setSourceUrl(url);
                Hawk.put(HawkConfig.LIVE_SOURCE_URL_CURRENT, liveSourceBean);
            }
        }

    }
}
