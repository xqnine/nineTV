package com.github.tvbox.osc.ui.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;

import com.blankj.utilcode.util.CleanUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.AboutDialog;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.SourceStoreDialog;
import com.github.tvbox.osc.ui.dialog.XWalkInitDialog;
import com.github.tvbox.osc.ui.dialog.util.SourceLineDialogUtil;
import com.github.tvbox.osc.ui.view.SettingItemView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.KVStorage;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.db.CacheManager;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class ModelSettingFragment extends BaseLazyFragment {
//    private TextView tvDebugOpen;
    private SettingItemView tvMediaCodec;
    private SettingItemView tvParseWebView;
    private SettingItemView tvPlay;
    private SettingItemView tvRender;
    private SettingItemView tvScale;
    private SettingItemView tvApi;
    private SettingItemView tvHomeApi;
    private SettingItemView tvDns;
    private SettingItemView tvHomeRec;
    private SettingItemView tvHistoryNum;
    private SettingItemView tvSearchView;
    private SettingItemView tvShowPreviewText;
    private SettingItemView tvFastSearchText;
    private SettingItemView mClearDataTextView;

//    boolean isLastOpen = KVStorage.getBoolean(HawkConfig.VIDEO_SHOW_TIME, false);


    public static ModelSettingFragment newInstance() {
        return new ModelSettingFragment().setArguments();
    }

    public ModelSettingFragment setArguments() {
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_model_new;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        tvFastSearchText = findViewById(R.id.showFastSearch);
        mClearDataTextView = findViewById(R.id.clear_data);
        tvFastSearchText.setSubTitle(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) ? "已开启" : "已关闭");
        tvShowPreviewText = findViewById(R.id.showPreview);
        tvShowPreviewText.setSubTitle(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "开启" : "关闭");
//        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvParseWebView = findViewById(R.id.llParseWebVew);
        tvMediaCodec = findViewById(R.id.llMediaCodec);
        tvPlay = findViewById(R.id.llPlay);
        tvRender = findViewById(R.id.llRender);
        tvScale = findViewById(R.id.llScale);
        tvApi = findViewById(R.id.llApi);
//        tvEpgApi = findViewById(R.id.tvEpgApi);
        tvHomeApi = findViewById(R.id.llHomeApi);
        tvDns = findViewById(R.id.llDns);
        tvHomeRec = findViewById(R.id.llHomeRec);
        tvHistoryNum = findViewById(R.id.llHistoryNum);
        tvSearchView = findViewById(R.id.llSearchView);
        tvMediaCodec.setSubTitle(Hawk.get(HawkConfig.IJK_CODEC, ""));
//        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        tvParseWebView.setSubTitle(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        MoreSourceBean moreSourceBean = KVStorage.getBean(HawkConfig.API_URL_BEAN,MoreSourceBean.class);
        if (moreSourceBean == null) {
            tvApi.setSubTitle(apiUrl);
        } else if (TextUtils.equals(moreSourceBean.getSourceUrl(), apiUrl)) {
            tvApi.setSubTitle(moreSourceBean.getSourceName());
        }
//        tvEpgApi.setText("EPG地址已隐藏");
        tvDns.setSubTitle(OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        tvHomeRec.setSubTitle(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        tvHistoryNum.setSubTitle(HistoryHelper.getHistoryNumName(Hawk.get(HawkConfig.HISTORY_NUM, 0)));
        tvSearchView.setSubTitle(getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 1)));
        tvHomeApi.setSubTitle(ApiConfig.get().getHomeSourceBean().getName());
        tvScale.setSubTitle(PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0)));
        tvPlay.setSubTitle(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0)));
        tvRender.setSubTitle(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 2)));
//        findViewById(R.id.llDebug).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                FastClickCheckUtil.check(v);
//                Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
//                tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
//            }
//        });
        findViewById(R.id.llParseWebVew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean useSystem = !Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
                Hawk.put(HawkConfig.PARSE_WEBVIEW, useSystem);
                tvParseWebView.setSubTitle(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
                if (!useSystem) {
                    Toast.makeText(mContext, "注意: XWalkView只适用于部分低Android版本，Android5.0以上推荐使用系统自带", Toast.LENGTH_LONG).show();
                    XWalkInitDialog dialog = new XWalkInitDialog(mContext);
                    dialog.setOnListener(new XWalkInitDialog.OnListener() {
                        @Override
                        public void onchange() {
                        }
                    });
                    dialog.show();
                }
            }
        });
        findViewById(R.id.llBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                BackupDialog dialog = new BackupDialog(mActivity);
                dialog.show();
            }
        });
        findViewById(R.id.llAbout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                AboutDialog dialog = new AboutDialog(mActivity);
                dialog.show();
            }
        });
        SettingItemView about  = findViewById(R.id.llAbout);
        about.setSubTitle("V"+ BuildConfig.VERSION_NAME);
        findViewById(R.id.llWp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if (!ApiConfig.get().wallpaper.isEmpty())
                    OkGo.<File>get(ApiConfig.get().wallpaper).execute(new FileCallback(requireActivity().getFilesDir().getAbsolutePath(), "wp") {
                        @Override
                        public void onSuccess(Response<File> response) {
                            ((BaseActivity) requireActivity()).changeWallpaper(true);
                        }

                        @Override
                        public void onError(Response<File> response) {
                            super.onError(response);
                        }

                        @Override
                        public void downloadProgress(Progress progress) {
                            super.downloadProgress(progress);
                        }
                    });
            }
        });
        findViewById(R.id.llWpRecovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                File wp = new File(requireActivity().getFilesDir().getAbsolutePath() + "/wp");
                if (wp.exists())
                    wp.delete();
                ((BaseActivity) requireActivity()).changeWallpaper(true);
            }
        });
        findViewById(R.id.llHomeApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                List<SourceBean> sites = ApiConfig.get().getSourceBeanList();
                if (sites.size() > 0) {
                    SelectDialog<SourceBean> dialog = new SelectDialog<>(mActivity);
                    dialog.setTip("请选择首页数据源");
                    dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                        @Override
                        public void click(SourceBean value, int pos) {
                            ApiConfig.get().setSourceBean(value);
                            tvHomeApi.setSubTitle(ApiConfig.get().getHomeSourceBean().getName());
                            dialog.dismiss();
                            if (getActivity() != null) {
                                getActivity().onBackPressed();
                            }
                        }

                        @Override
                        public String getDisplay(SourceBean val) {
                            return val.getName();
                        }
                    }, new DiffUtil.ItemCallback<SourceBean>() {
                        @Override
                        public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                            return oldItem == newItem;
                        }

                        @Override
                        public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                            return oldItem.getKey().equals(newItem.getKey());
                        }
                    }, sites, sites.indexOf(ApiConfig.get().getHomeSourceBean()));
                    dialog.show();
                }
            }
        });
        findViewById(R.id.llDns).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            int dohUrl = Hawk.get(HawkConfig.DOH_URL, 0);

            SelectDialog<String> dialog = new SelectDialog<>(mActivity);
            dialog.setTip("请选择安全DNS");
            dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
                @Override
                public void click(String value, int pos) {
                    tvDns.setSubTitle(OkGoHelper.dnsHttpsList.get(pos));
                    Hawk.put(HawkConfig.DOH_URL, pos);
                    String url = OkGoHelper.getDohUrl(pos);
                    OkGoHelper.dnsOverHttps.setUrl(url.isEmpty() ? null : HttpUrl.get(url));
                    IjkMediaPlayer.toggleDotPort(pos > 0);
                }

                @Override
                public String getDisplay(String val) {
                    return val;
                }
            }, new DiffUtil.ItemCallback<String>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }
            }, OkGoHelper.dnsHttpsList, dohUrl);
            dialog.show();
        });
        findViewById(R.id.llApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                ApiDialog dialog = new ApiDialog(mActivity);
                EventBus.getDefault().register(dialog);
                dialog.setOnListener(new ApiDialog.OnListener() {
                    @Override
                    public void onchange(String api) {
                        Hawk.put(HawkConfig.API_URL, api);
                        tvApi.setSubTitle(api);
                    }
                });
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ((BaseActivity) mActivity).hideSysBar();
                        EventBus.getDefault().unregister(dialog);
                    }
                });
                dialog.show();
            }
        });

//        findViewById(R.id.epgApi).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                FastClickCheckUtil.check(v);
//                EpgDialog dialog = new EpgDialog(mActivity);
//                EventBus.getDefault().register(dialog);
//                dialog.setOnListener(new EpgDialog.OnListener() {
//                    @Override
//                    public void onchange(String api) {
//                        Hawk.put(HawkConfig.EPG_URL, api);
//                        tvEpgApi.setText(api);
//                    }
//                });
//                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
//                    @Override
//                    public void onDismiss(DialogInterface dialog) {
//                        ((BaseActivity) mActivity).hideSysBar();
//                        EventBus.getDefault().unregister(dialog);
//                    }
//                });
//                dialog.show();
//            }
//        });


        findViewById(R.id.llMediaCodec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<IJKCode> ijkCodes = ApiConfig.get().getIjkCodes();
                if (ijkCodes == null || ijkCodes.size() == 0)
                    return;
                FastClickCheckUtil.check(v);

                int defaultPos = 0;
                String ijkSel = Hawk.get(HawkConfig.IJK_CODEC, "");
                for (int j = 0; j < ijkCodes.size(); j++) {
                    if (ijkSel.equals(ijkCodes.get(j).getName())) {
                        defaultPos = j;
                        break;
                    }
                }

                SelectDialog<IJKCode> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择IJK解码");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<IJKCode>() {
                    @Override
                    public void click(IJKCode value, int pos) {
                        value.selected(true);
                        tvMediaCodec.setSubTitle(value.getName());
                    }

                    @Override
                    public String getDisplay(IJKCode val) {
                        return val.getName();
                    }
                }, new DiffUtil.ItemCallback<IJKCode>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                        return oldItem == newItem;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                        return oldItem.getName().equals(newItem.getName());
                    }
                }, ijkCodes, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llScale).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_SCALE, 0);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                players.add(3);
                players.add(4);
                players.add(5);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认画面缩放");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_SCALE, value);
                        tvScale.setSubTitle(PlayerHelper.getScaleName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getScaleName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, players, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
                int defaultPos = 0;
                ArrayList<Integer> players = PlayerHelper.getExistPlayerTypes();
                ArrayList<Integer> renders = new ArrayList<>();
                for(int p = 0; p<players.size(); p++) {
                    renders.add(p);
                    if (players.get(p) == playerType) {
                        defaultPos = p;
                    }
                }
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认播放器");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Integer thisPlayerType = players.get(pos);
                        Hawk.put(HawkConfig.PLAY_TYPE, thisPlayerType);
                        tvPlay.setSubTitle(PlayerHelper.getPlayerName(thisPlayerType));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        Integer playerType = players.get(val);
                        return PlayerHelper.getPlayerName(playerType);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llRender).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 2);
                ArrayList<Integer> renders = new ArrayList<>();
                renders.add(0);
                renders.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认渲染方式");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_RENDER, value);
                        tvRender.setSubTitle(PlayerHelper.getRenderName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getRenderName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llHomeRec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_REC, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择首页列表数据");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_REC, value);
                        tvHomeRec.setSubTitle(getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getHomeRecName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llSearchView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.SEARCH_VIEW, 1);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择搜索视图");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.SEARCH_VIEW, value);
                        tvSearchView.setSubTitle(getSearchView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getSearchView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
//                findViewById(R.id.llDebug).setVisibility(View.VISIBLE);
            }
        };

        findViewById(R.id.showPreview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.SHOW_PREVIEW, !Hawk.get(HawkConfig.SHOW_PREVIEW, true));
                tvShowPreviewText.setSubTitle(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "开启" : "关闭");
            }
        });
        findViewById(R.id.llHistoryNum).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HISTORY_NUM, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                types.add(3);
                types.add(4);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("保留历史记录数量");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HISTORY_NUM, value);
                        tvHistoryNum.setSubTitle(HistoryHelper.getHistoryNumName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return HistoryHelper.getHistoryNumName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.showFastSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.FAST_SEARCH_MODE, !Hawk.get(HawkConfig.FAST_SEARCH_MODE, false));
                tvFastSearchText.setSubTitle(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.more_source).setOnClickListener(v -> {
            new SourceLineDialogUtil(mActivity).
                    getData(() -> {
                        if (getActivity() != null) {
                            getActivity().onBackPressed();
                        }
                        return null;
                    });

        });
        findViewById(R.id.default_more_store).setOnClickListener(v -> {
            new SourceStoreDialog(mActivity).show();

        });
//        SettingItemView textView = findViewById(R.id.sys_time_switch);
//        setTimeSwitch(textView, isLastOpen);
//        textView.setOnClickListener(v -> {
//            isLastOpen = !isLastOpen;
//            setTimeSwitch(textView, isLastOpen);
//            KVStorage.putBoolean(HawkConfig.VIDEO_SHOW_TIME, isLastOpen);
//        });
        mClearDataTextView.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("确定要清空缓存？");
            builder.setMessage("清空缓存后，软件将变为初始安装状态");
            builder.setNegativeButton("取消", (dialog, which) -> {

            });
            builder.setPositiveButton("确定", (dialog, which) -> {
                CleanUtils.cleanExternalCache();
                CleanUtils.cleanInternalCache();
                CleanUtils.cleanInternalFiles();
                CleanUtils.cleanInternalSp();
                CacheManager.getInstance().clear();//清空接口缓存
                Hawk.deleteAll();
                KVStorage.deleteAll();
                ToastUtils.showShort("缓存清空完毕");
                ApiConfig.release();
                Intent intent = new Intent(mActivity, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus();
        });
    }

//    private void setTimeSwitch(SettingItemView textView, boolean isLastOpen) {
//        if (isLastOpen) {
//            textView.setSubTitle("已打开");
//        } else {
//            textView.setSubTitle("已关闭");
//        }
//    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SettingActivity.callback = null;
    }

    String getHomeRecName(int type) {
        if (type == 1) {
            return "站点推荐";
        } else if (type == 2) {
            return "观看历史";
        } else {
            return "豆瓣热播";
        }
    }

    @Subscribe
    public void onUrlChange(RefreshEvent refreshEvent) {
        if (refreshEvent == null) {
            return;
        }
        if (refreshEvent.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            tvApi.setSubTitle((String) refreshEvent.obj);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    String getSearchView(int type) {
        if (type == 0) {
            return "文字列表";
        } else {
            return "缩略图";
        }
    }
}