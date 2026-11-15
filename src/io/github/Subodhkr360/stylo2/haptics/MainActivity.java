package io.github.Subodhkr360.stylo2.haptics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.util.TypedValue;
import android.view.Gravity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private StylusHapticsManager mManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private TextView mTvStatusChip;
    private Button mBtnRootSetup;
    private Switch mSwitchFunctionalFeedback;
    private Switch mSwitchHandwritingFeedback;
    private Switch mSwitchGlobalMode;
    private Switch mSwitchShowSystemApps;
    private Switch mSwitchShowLauncherIcon;
    private Switch mSwitchAirViewPointer;
    private View mContainerPointerSize;
    private SeekBar mSeekbarPointerSize;
    private TextView mTvPointerSizeVal;
    private View mViewPointerPreview;
    private Switch mSwitchAirViewScroll;
    private View mContainerScrollSpeed;
    private SeekBar mSeekbarScrollSpeed;
    private TextView mTvScrollSpeedVal;
    private SeekBar mSeekbarEdgeZone;
    private TextView mTvEdgeZoneVal;
    private Switch mSwitchAirCommand;
    private Switch mSwitchAirCommandLabels;
    private Spinner mSpinnerVibrationType;
    private SeekBar mSeekbarGlobalStrength;
    private TextView mTvGlobalStrengthVal;
    private Button mBtnTestDrawing;
    private EditText mEtSearch;
    private ListView mLvApps;
    private TextView mTvWhitelistCount;

    private View mTabBtnSettings;
    private View mTabBtnApps;
    private View mTabBtnLandscape;
    private View mPaneSettings;
    private View mPaneApps;
    private View mPaneLandscape;

    private Switch mSwitchLandscapeShowSystem;
    private EditText mEtLandscapeSearch;
    private ListView mLvLandscapeApps;
    private LandscapeAppAdapter mLandscapeAdapter;
    private View mBtnCustomizeAirCommand;
    private final List<AppListAdapter.AppEntry> mAllInstalledApps = new ArrayList<>();

    private AppListAdapter mAdapter;
    private boolean mTestingDrawing = false;

    private final String[] PATTERN_NAMES = {
        "Pencil (Graphite Paper Texture)",
        "Eraser (Firm Tool Switch Friction)",
        "Ballpoint Pen (Crisp Roller Click)",
        "Fountain Pen (Smooth Fluid Glide)"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mManager = StylusHapticsManager.getInstance(this);

        initViews();
        setupListeners();
        setupVibrationControls();
        loadInstalledAppsAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mManager.bind();
        ensureAccessibilityServiceRunning();
        mManager.stopDrawingFeedback();
        updateStatusUI(mManager.isConnected(), mManager.isConnected() ? "Connected" : "Checking...");
        int strength = mManager.queryCurrentHardwareLevel();
        if (mSeekbarGlobalStrength != null) {
            mSeekbarGlobalStrength.setProgress(Math.max(0, Math.min(4, strength - 1)));
            updateStrengthLabel(strength);
        }
    }

    private void ensureAccessibilityServiceRunning() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!StylusHapticsManager.isAccessibilityServiceEnabled(MainActivity.this)) {
                        mManager.applyRootSetup();
                        // Poll for up to 2.5 seconds for system settings to take effect
                        for (int i = 0; i < 10; i++) {
                            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                            if (StylusHapticsManager.isAccessibilityServiceEnabled(MainActivity.this)) {
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateStatusUI(mManager.isConnected(), mManager.isConnected() ? "Connected" : "Checking...");
                    }
                });
            }
        }).start();
    }

    private void updateStatusUI(boolean connected, String status) {
        boolean accessEnabled = StylusHapticsManager.isAccessibilityServiceEnabled(this);
        boolean isModule = StylusHapticsManager.isInstalledAsModule(this);

        if (accessEnabled) {
            mTvStatusChip.setText("Connected");
            mTvStatusChip.setTextColor(Color.parseColor("#34C759"));
            if (mBtnRootSetup != null) {
                mBtnRootSetup.setVisibility(View.GONE);
            }
        } else if (isModule) {
            mTvStatusChip.setText("Accessibility Required");
            mTvStatusChip.setTextColor(Color.parseColor("#FF9500"));
            if (mBtnRootSetup != null) {
                mBtnRootSetup.setVisibility(View.VISIBLE);
                mBtnRootSetup.setText("Grant Permission");
            }
        } else {
            boolean hasRoot = mManager.checkRootAvailable();
            if (hasRoot) {
                mTvStatusChip.setText("Accessibility Required");
                mTvStatusChip.setTextColor(Color.parseColor("#FF9500"));
                if (mBtnRootSetup != null) {
                    mBtnRootSetup.setVisibility(View.VISIBLE);
                    mBtnRootSetup.setText("Enable Service");
                }
            } else {
                mTvStatusChip.setText("Permission Required");
                mTvStatusChip.setTextColor(Color.parseColor("#FF9500"));
                if (mBtnRootSetup != null) {
                    mBtnRootSetup.setVisibility(View.VISIBLE);
                    mBtnRootSetup.setText("Grant Permission");
                }
            }
        }
    }

    private void initViews() {
        View backBtn = findViewById(R.id.btn_back_header);
        if (backBtn != null) {
            backBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        mTvStatusChip = findViewById(R.id.tv_status_chip);
        mBtnRootSetup = findViewById(R.id.btn_root_setup);
        boolean ready = mManager.isConnected();
        updateStatusUI(ready, ready ? "Connected" : "Checking...");
        mSwitchFunctionalFeedback = findViewById(R.id.switch_functional_feedback);
        mSwitchHandwritingFeedback = findViewById(R.id.switch_handwriting_feedback);
        mSwitchGlobalMode = findViewById(R.id.switch_global_mode);
        mSwitchShowSystemApps = findViewById(R.id.switch_show_system_apps);
        mSwitchShowLauncherIcon = findViewById(R.id.switch_show_launcher_icon);
        mSwitchAirViewPointer = findViewById(R.id.switch_air_view_pointer);
        mContainerPointerSize = findViewById(R.id.container_pointer_size);
        mSeekbarPointerSize = findViewById(R.id.seekbar_pointer_size);
        mTvPointerSizeVal = findViewById(R.id.tv_pointer_size_val);
        mViewPointerPreview = findViewById(R.id.view_pointer_preview);
        mSwitchAirViewScroll = findViewById(R.id.switch_air_view_scroll);
        mContainerScrollSpeed = findViewById(R.id.container_scroll_speed);
        mSwitchAirCommand = findViewById(R.id.switch_air_command);
        mSwitchAirCommandLabels = findViewById(R.id.switch_air_command_labels);
        mSeekbarScrollSpeed = findViewById(R.id.seekbar_scroll_speed);
        mTvScrollSpeedVal = findViewById(R.id.tv_scroll_speed_val);
        mSeekbarEdgeZone = findViewById(R.id.seekbar_edge_zone);
        mTvEdgeZoneVal = findViewById(R.id.tv_edge_zone_val);
        mSpinnerVibrationType = findViewById(R.id.spinner_vibration_type);
        mSeekbarGlobalStrength = findViewById(R.id.seekbar_global_strength);
        mTvGlobalStrengthVal = findViewById(R.id.tv_global_strength_val);
        mBtnTestDrawing = findViewById(R.id.btn_test_drawing);
        mEtSearch = findViewById(R.id.et_app_search);
        mLvApps = findViewById(R.id.lv_apps);
        mTvWhitelistCount = findViewById(R.id.tv_whitelist_count);

        mTabBtnSettings = findViewById(R.id.tab_btn_settings);
        mTabBtnApps = findViewById(R.id.tab_btn_apps);
        mTabBtnLandscape = findViewById(R.id.tab_btn_landscape);
        mPaneSettings = findViewById(R.id.pane_settings);
        mPaneApps = findViewById(R.id.pane_apps);
        mPaneLandscape = findViewById(R.id.pane_landscape);

        mSwitchLandscapeShowSystem = findViewById(R.id.switch_landscape_show_system);
        mEtLandscapeSearch = findViewById(R.id.et_landscape_search);
        mLvLandscapeApps = findViewById(R.id.lv_landscape_apps);
        mBtnCustomizeAirCommand = findViewById(R.id.btn_customize_air_command);

        SharedPreferences prefs = mManager.getPrefs();
        mSwitchFunctionalFeedback.setChecked(prefs.getBoolean(StylusHapticsManager.PREF_FUNCTIONAL_FEEDBACK, true));
        mSwitchHandwritingFeedback.setChecked(prefs.getBoolean(StylusHapticsManager.PREF_HANDWRITING_FEEDBACK, true));
        mSwitchGlobalMode.setChecked(prefs.getBoolean(StylusHapticsManager.PREF_GLOBAL_MODE, false));
        mSwitchShowSystemApps.setChecked(prefs.getBoolean("pref_show_system_apps", false));
        if (mSwitchLandscapeShowSystem != null) {
            mSwitchLandscapeShowSystem.setChecked(prefs.getBoolean("pref_landscape_show_system", false));
        }

        boolean pointerEnabled = prefs.getBoolean(AirViewManager.PREF_AIR_VIEW_ENABLED, true);
        if (mSwitchAirViewPointer != null) {
            mSwitchAirViewPointer.setChecked(pointerEnabled);
        }
        if (mContainerPointerSize != null) {
            mContainerPointerSize.setVisibility(pointerEnabled ? View.VISIBLE : View.GONE);
        }
        if (mSeekbarPointerSize != null) {
            int savedPointerSize = prefs.getInt(AirViewManager.PREF_AIR_VIEW_POINTER_SIZE_DP, 4);
            int pointerProgress = 0;
            if (savedPointerSize <= 4) pointerProgress = 0;
            else if (savedPointerSize <= 6) pointerProgress = 1;
            else if (savedPointerSize <= 8) pointerProgress = 2;
            else if (savedPointerSize <= 12) pointerProgress = 3;
            else pointerProgress = 4;
            mSeekbarPointerSize.setProgress(pointerProgress);
            updatePointerSizeUI(savedPointerSize);
        }
        boolean scrollEnabled = prefs.getBoolean(AirViewManager.PREF_AIR_VIEW_SCROLL_ENABLED, false);
        if (mSwitchAirViewScroll != null) {
            mSwitchAirViewScroll.setChecked(scrollEnabled);
        }
        if (mContainerScrollSpeed != null) {
            mContainerScrollSpeed.setVisibility(scrollEnabled ? View.VISIBLE : View.GONE);
        }
        if (mSeekbarScrollSpeed != null) {
            int savedSpeed = prefs.getInt(AirViewManager.PREF_AIR_VIEW_SCROLL_SPEED, 2);
            mSeekbarScrollSpeed.setProgress(Math.max(0, Math.min(4, savedSpeed)));
            if (mTvScrollSpeedVal != null) {
                mTvScrollSpeedVal.setText(getScrollSpeedLabel(savedSpeed));
            }
        }
        if (mSwitchAirCommand != null) {
            mSwitchAirCommand.setChecked(prefs.getBoolean(AirCommandManager.PREF_AIR_COMMAND_ENABLED, true));
        }
        if (mSwitchAirCommandLabels != null) {
            mSwitchAirCommandLabels.setChecked(prefs.getBoolean(AirCommandManager.PREF_AIR_COMMAND_SHOW_LABELS, true));
        }
        if (mSeekbarEdgeZone != null) {
            int edgeZone = prefs.getInt(AirViewManager.PREF_AIR_VIEW_EDGE_ZONE_DP, 64);
            int progress = (edgeZone - 40) / 15;
            mSeekbarEdgeZone.setProgress(Math.max(0, Math.min(4, progress)));
            if (mTvEdgeZoneVal != null) {
                mTvEdgeZoneVal.setText(edgeZone + " dp");
            }
        }

        // Launcher Icon Setting
        if (mSwitchShowLauncherIcon != null) {
            ComponentName launcherComponent = new ComponentName(this, getPackageName() + ".LauncherAlias");
            int state = getPackageManager().getComponentEnabledSetting(launcherComponent);
            boolean isVisible = (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED 
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
            mSwitchShowLauncherIcon.setChecked(isVisible);
        }

        int globalStrength = mManager.queryCurrentHardwareLevel();
        mSeekbarGlobalStrength.setProgress(Math.max(0, Math.min(4, globalStrength - 1)));
        updateStrengthLabel(globalStrength);
    }

    private void updateStrengthLabel(int level) {
        int pct = Math.max(1, Math.min(5, level)) * 20;
        mTvGlobalStrengthVal.setText(pct + "%");
    }

    private void setupListeners() {
        mManager.setStateListener(new StylusHapticsManager.StateListener() {
            @Override
            public void onConnectionStateChanged(final boolean connected, final String status) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateStatusUI(connected, status);
                    }
                });
            }
        });

        // Navigation tabs
        if (mTabBtnSettings != null) {
            mTabBtnSettings.setOnClickListener(v -> switchTab(0));
        }
        if (mTabBtnApps != null) {
            mTabBtnApps.setOnClickListener(v -> switchTab(1));
        }
        if (mTabBtnLandscape != null) {
            mTabBtnLandscape.setOnClickListener(v -> switchTab(2));
        }

        if (mBtnCustomizeAirCommand != null) {
            mBtnCustomizeAirCommand.setOnClickListener(v -> showAirCommandToolsDialog());
        }

        if (mSwitchLandscapeShowSystem != null) {
            mSwitchLandscapeShowSystem.setOnCheckedChangeListener((b, isChecked) -> {
                mManager.getPrefs().edit().putBoolean("pref_landscape_show_system", isChecked).apply();
                refreshLandscapeAppList();
            });
        }

        if (mEtLandscapeSearch != null) {
            mEtLandscapeSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (mLandscapeAdapter != null) {
                        mLandscapeAdapter.getFilter().filter(s);
                    }
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        mSwitchFunctionalFeedback.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                mManager.getPrefs().edit().putBoolean(StylusHapticsManager.PREF_FUNCTIONAL_FEEDBACK, checked).apply();
                mManager.syncFunctionalFeedback(checked);
            }
        });

        mSwitchHandwritingFeedback.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                mManager.getPrefs().edit().putBoolean(StylusHapticsManager.PREF_HANDWRITING_FEEDBACK, checked).apply();
                mManager.syncHandwritingFeedbackState(checked);
            }
        });

        mSwitchGlobalMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                mManager.getPrefs().edit().putBoolean(StylusHapticsManager.PREF_GLOBAL_MODE, checked).apply();
            }
        });

        mSwitchShowSystemApps.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mManager.getPrefs().edit().putBoolean("pref_show_system_apps", isChecked).apply();
                loadInstalledAppsAsync();
            }
        });

        if (mSwitchShowLauncherIcon != null) {
            mSwitchShowLauncherIcon.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    ComponentName launcherComponent = new ComponentName(MainActivity.this, getPackageName() + ".LauncherAlias");
                    int newState = isChecked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED 
                                             : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    getPackageManager().setComponentEnabledSetting(launcherComponent, newState, PackageManager.DONT_KILL_APP);
                    Toast.makeText(MainActivity.this, isChecked ? "App icon added to launcher" : "App icon hidden from launcher (accessible from Settings)", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (mSwitchAirViewPointer != null) {
            mSwitchAirViewPointer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mManager.getPrefs().edit().putBoolean(AirViewManager.PREF_AIR_VIEW_ENABLED, isChecked).apply();
                    if (mContainerPointerSize != null) {
                        mContainerPointerSize.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                    AirViewManager airMgr = AirViewManager.getInstance(MainActivity.this);
                    if (airMgr != null) airMgr.loadPreferences();
                }
            });
        }

        if (mSeekbarPointerSize != null) {
            mSeekbarPointerSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int sizeDp;
                    switch (progress) {
                        case 0: sizeDp = 4; break;
                        case 1: sizeDp = 6; break;
                        case 2: sizeDp = 8; break;
                        case 3: default: sizeDp = 12; break; // Default OneUI size
                        case 4: sizeDp = 16; break;
                    }
                    updatePointerSizeUI(sizeDp);
                    mManager.getPrefs().edit().putInt(AirViewManager.PREF_AIR_VIEW_POINTER_SIZE_DP, sizeDp).apply();
                    AirViewManager airMgr = AirViewManager.getInstance(MainActivity.this);
                    if (airMgr != null) {
                        airMgr.setPointerSizeDp(sizeDp);
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (mSwitchAirViewScroll != null) {
            mSwitchAirViewScroll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mManager.getPrefs().edit().putBoolean(AirViewManager.PREF_AIR_VIEW_SCROLL_ENABLED, isChecked).apply();
                    if (mContainerScrollSpeed != null) {
                        mContainerScrollSpeed.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                    AirViewManager airMgr = AirViewManager.getInstance(MainActivity.this);
                    if (airMgr != null) airMgr.loadPreferences();
                }
            });
        }

        if (mSeekbarScrollSpeed != null) {
            mSeekbarScrollSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (mTvScrollSpeedVal != null) {
                        mTvScrollSpeedVal.setText(getScrollSpeedLabel(progress));
                    }
                    mManager.getPrefs().edit().putInt(AirViewManager.PREF_AIR_VIEW_SCROLL_SPEED, progress).apply();
                    AirViewManager airMgr = AirViewManager.getInstance();
                    if (airMgr != null) {
                        airMgr.setScrollSpeed(progress);
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (mSwitchAirCommand != null) {
            mSwitchAirCommand.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mManager.getPrefs().edit().putBoolean(AirCommandManager.PREF_AIR_COMMAND_ENABLED, isChecked).apply();
                    AirCommandManager airCmd = AirCommandManager.getInstance(MainActivity.this);
                    if (airCmd != null) {
                        airCmd.loadPreferencesAndSync();
                    }
                }
            });
        }

        if (mSwitchAirCommandLabels != null) {
            mSwitchAirCommandLabels.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mManager.getPrefs().edit().putBoolean(AirCommandManager.PREF_AIR_COMMAND_SHOW_LABELS, isChecked).apply();
                    AirCommandManager airCmd = AirCommandManager.getInstance(MainActivity.this);
                    if (airCmd != null) {
                        airCmd.loadPreferencesAndSync();
                    }
                }
            });
        }

        if (mSeekbarEdgeZone != null) {
            mSeekbarEdgeZone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int edgeZoneDp = 40 + progress * 15;
                    if (mTvEdgeZoneVal != null) {
                        mTvEdgeZoneVal.setText(edgeZoneDp + " dp");
                    }
                    if (fromUser) {
                        mManager.getPrefs().edit().putInt(AirViewManager.PREF_AIR_VIEW_EDGE_ZONE_DP, edgeZoneDp).apply();
                        AirViewManager airMgr = AirViewManager.getInstance();
                        if (airMgr != null) airMgr.loadPreferences();
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        mBtnTestDrawing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mTestingDrawing) {
                    mTestingDrawing = true;
                    int pattern = mSpinnerVibrationType.getSelectedItemPosition();
                    int strength = mSeekbarGlobalStrength.getProgress() + 1;
                    mManager.triggerPattern(pattern, strength);
                    mBtnTestDrawing.setText("Testing vibration...");
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mTestingDrawing = false;
                            mBtnTestDrawing.setText("Test vibration");
                        }
                    }, 2000);
                }
            }
        });

        mBtnRootSetup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAutomatedRootSetup();
            }
        });

        mEtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null) {
                    mAdapter.getFilter().filter(s);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupVibrationControls() {
        // ColorOS rounded dropdown adapter
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.item_coloros_spinner_selected, PATTERN_NAMES);
        spinnerAdapter.setDropDownViewResource(R.layout.item_coloros_dropdown);
        mSpinnerVibrationType.setAdapter(spinnerAdapter);

        int currentPattern = mManager.getPrefs().getInt(StylusHapticsManager.PREF_GLOBAL_PATTERN, StylusHapticsManager.PATTERN_PENCIL);
        if (currentPattern >= 0 && currentPattern < PATTERN_NAMES.length) {
            mSpinnerVibrationType.setSelection(currentPattern);
        }

        mSpinnerVibrationType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mManager.getPrefs().edit().putInt(StylusHapticsManager.PREF_GLOBAL_PATTERN, position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        mSeekbarGlobalStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int level = progress + 1;
                updateStrengthLabel(level);
                if (fromUser) {
                    mManager.getPrefs().edit().putInt(StylusHapticsManager.PREF_GLOBAL_STRENGTH, level).apply();
                    mManager.syncHardwareLevel(level);
                    mManager.triggerPattern(mSpinnerVibrationType.getSelectedItemPosition(), level);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void switchTab(int index) {
        boolean isTabletTwoPane = (findViewById(R.id.tab_btn_settings) == null);

        if (isTabletTwoPane) {
            // Tablet two-pane mode: left pane stays settings, right pane switches apps vs landscape
            if (mPaneApps != null) mPaneApps.setVisibility(index == 2 ? View.GONE : View.VISIBLE);
            if (mPaneLandscape != null) mPaneLandscape.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

            if (mTabBtnApps instanceof TextView) {
                mTabBtnApps.setBackgroundResource(index == 2 ? R.drawable.tab_pill_inactive : R.drawable.tab_pill_active);
                ((TextView) mTabBtnApps).setTextColor(index == 2 ? getColor(R.color.text_secondary) : Color.WHITE);
                ((TextView) mTabBtnApps).getPaint().setFakeBoldText(index != 2);
            }
            if (mTabBtnLandscape instanceof TextView) {
                mTabBtnLandscape.setBackgroundResource(index == 2 ? R.drawable.tab_pill_active : R.drawable.tab_pill_inactive);
                ((TextView) mTabBtnLandscape).setTextColor(index == 2 ? Color.WHITE : getColor(R.color.text_secondary));
                ((TextView) mTabBtnLandscape).getPaint().setFakeBoldText(index == 2);
            }
        } else {
            // Single pane mode (phone or narrow floating window)
            if (mPaneSettings != null) mPaneSettings.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
            if (mPaneApps != null) mPaneApps.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
            if (mPaneLandscape != null) mPaneLandscape.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

            if (mTabBtnSettings instanceof TextView) {
                mTabBtnSettings.setBackgroundResource(index == 0 ? R.drawable.tab_pill_active : R.drawable.tab_pill_inactive);
                ((TextView) mTabBtnSettings).setTextColor(index == 0 ? Color.WHITE : getColor(R.color.text_secondary));
            }
            if (mTabBtnApps instanceof TextView) {
                mTabBtnApps.setBackgroundResource(index == 1 ? R.drawable.tab_pill_active : R.drawable.tab_pill_inactive);
                ((TextView) mTabBtnApps).setTextColor(index == 1 ? Color.WHITE : getColor(R.color.text_secondary));
            }
            if (mTabBtnLandscape instanceof TextView) {
                mTabBtnLandscape.setBackgroundResource(index == 2 ? R.drawable.tab_pill_active : R.drawable.tab_pill_inactive);
                ((TextView) mTabBtnLandscape).setTextColor(index == 2 ? Color.WHITE : getColor(R.color.text_secondary));
            }
        }
    }

    private void refreshLandscapeAppList() {
        if (mLandscapeAdapter == null) return;
        boolean showSystem = mManager.getPrefs().getBoolean("pref_landscape_show_system", false);
        LandscapeViewManager lvm = LandscapeViewManager.getInstance(this);
        List<AppListAdapter.AppEntry> filtered = new ArrayList<>();
        synchronized (mAllInstalledApps) {
            for (AppListAdapter.AppEntry entry : mAllInstalledApps) {
                boolean isEnabled = lvm.isLandscapeCompatEnabled(entry.packageName);
                if (showSystem || isEnabled || !entry.isSystem) {
                    filtered.add(entry);
                }
            }
        }
        mLandscapeAdapter.updateList(filtered);
    }

    private void loadInstalledAppsAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);

                boolean showSystemApps = mManager.getPrefs().getBoolean("pref_show_system_apps", false);
                final List<AppListAdapter.AppEntry> allApps = new ArrayList<>();
                final List<AppListAdapter.AppEntry> entries = new ArrayList<>();

                for (ApplicationInfo info : installed) {
                    StylusHapticsManager.AppProfile profile = mManager.getAppProfile(info.packageName);
                    boolean isSelected = profile.enabled;
                    boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    String label = info.loadLabel(pm).toString();
                    Drawable icon = info.loadIcon(pm);
                    AppListAdapter.AppEntry entry = new AppListAdapter.AppEntry(label, info.packageName, icon, isSelected, isSystem);
                    allApps.add(entry);

                    if (!showSystemApps && !isSelected) {
                        if (isSystem || pm.getLaunchIntentForPackage(info.packageName) == null) {
                            continue;
                        }
                    }

                    entries.add(entry);
                }

                // Sort: Selected apps first, then alphabetical
                Collections.sort(entries, new Comparator<AppListAdapter.AppEntry>() {
                    @Override
                    public int compare(AppListAdapter.AppEntry a, AppListAdapter.AppEntry b) {
                        if (a.isSelected != b.isSelected) {
                            return a.isSelected ? -1 : 1;
                        }
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });

                Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));

                synchronized (mAllInstalledApps) {
                    mAllInstalledApps.clear();
                    mAllInstalledApps.addAll(allApps);
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter = new AppListAdapter(MainActivity.this, entries, mManager, new AppListAdapter.OnAppProfileClickListener() {
                            @Override
                            public void onConfigureProfile(AppListAdapter.AppEntry entry) {
                                showAppProfileDialog(entry);
                            }

                            @Override
                            public void onSelectionChanged() {
                                updateWhitelistCounter();
                            }
                        });
                        mLvApps.setAdapter(mAdapter);
                        updateWhitelistCounter();

                        // Initialize Landscape View Adapter
                        LandscapeViewManager lvm = LandscapeViewManager.getInstance(MainActivity.this);
                        mLandscapeAdapter = new LandscapeAppAdapter(MainActivity.this, new ArrayList<>(), lvm);
                        if (mLvLandscapeApps != null) {
                            mLvLandscapeApps.setAdapter(mLandscapeAdapter);
                        }
                        refreshLandscapeAppList();
                    }
                });
            }
        }).start();
    }

    private void showAirCommandToolsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_air_command_tools, null);
        LinearLayout toolsContainer = dialogView.findViewById(R.id.tools_container);
        View btnAddShortcut = dialogView.findViewById(R.id.btn_add_app_shortcut);
        Button btnDone = dialogView.findViewById(R.id.btn_tools_done);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        populateAirCommandToolsView(toolsContainer, dialog);

        btnAddShortcut.setOnClickListener(v -> showAppPickerForAirCommand(toolsContainer, dialog));

        btnDone.setOnClickListener(v -> {
            dialog.dismiss();
            AirCommandManager airCmd = AirCommandManager.getInstance(MainActivity.this);
            if (airCmd != null) {
                airCmd.loadPreferencesAndSync();
            }
        });

        dialog.show();
    }

    private void populateAirCommandToolsView(LinearLayout container, AlertDialog dialog) {
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        String toolsPref = mManager.getPrefs().getString(AirCommandManager.PREF_AIR_COMMAND_TOOLS, AirCommandManager.DEFAULT_TOOLS);
        List<String> toolList = new ArrayList<>(Arrays.asList(toolsPref.split(",")));
        PackageManager pm = getPackageManager();

        // Native tool definitions
        final String[][] nativeTools = {
                {"laser_pointer", "Laser pointer", "Presentation laser mode toggle"},
                {"quick_note", "Quick note", "Floating window note"},
                {"capture", "Capture", "Area crop screenshot"},
                {"circle_to_search", "Circle to Search", "Google screen search"},
                {"settings", "Stylo settings", "Open Stylo Haptics app"}
        };

        for (String[] def : nativeTools) {
            final String id = def[0];
            String title = def[1];
            String desc = def[2];
            boolean isChecked = toolList.contains(id);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView tvTitle = new TextView(this);
            tvTitle.setText(title);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tvTitle.setTextColor(Color.WHITE);
            tvTitle.getPaint().setFakeBoldText(true);
            textCol.addView(tvTitle);

            TextView tvDesc = new TextView(this);
            tvDesc.setText(desc);
            tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvDesc.setTextColor(getColor(R.color.text_secondary));
            textCol.addView(tvDesc);

            row.addView(textCol);

            Switch sw = new Switch(this);
            sw.setChecked(isChecked);
            sw.setOnCheckedChangeListener((btn, checked) -> {
                String current = mManager.getPrefs().getString(AirCommandManager.PREF_AIR_COMMAND_TOOLS, AirCommandManager.DEFAULT_TOOLS);
                List<String> list = new ArrayList<>(Arrays.asList(current.split(",")));
                list.remove(id);
                if (checked) {
                    list.add(id);
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    String s = list.get(i).trim();
                    if (!s.isEmpty()) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(s);
                    }
                }
                mManager.getPrefs().edit().putString(AirCommandManager.PREF_AIR_COMMAND_TOOLS, sb.toString()).apply();
            });
            row.addView(sw);

            container.addView(row);
        }

        // Custom User Apps added to Air Command
        for (String item : toolList) {
            String toolId = item.trim();
            if (toolId.startsWith("app:")) {
                final String pkg = toolId.substring(4);
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                    CharSequence label = pm.getApplicationLabel(appInfo);

                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

                    ImageView ivIcon = new ImageView(this);
                    ivIcon.setImageDrawable(pm.getApplicationIcon(appInfo));
                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams((int) (28 * density), (int) (28 * density));
                    iconParams.setMarginEnd((int) (10 * density));
                    row.addView(ivIcon, iconParams);

                    LinearLayout textCol = new LinearLayout(this);
                    textCol.setOrientation(LinearLayout.VERTICAL);
                    textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

                    TextView tvTitle = new TextView(this);
                    tvTitle.setText(label);
                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    tvTitle.setTextColor(Color.WHITE);
                    tvTitle.getPaint().setFakeBoldText(true);
                    textCol.addView(tvTitle);

                    TextView tvDesc = new TextView(this);
                    tvDesc.setText("App Shortcut");
                    tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    tvDesc.setTextColor(getColor(R.color.primary_accent));
                    textCol.addView(tvDesc);

                    row.addView(textCol);

                    TextView btnRemove = new TextView(this);
                    btnRemove.setText("Remove");
                    btnRemove.setTextColor(Color.parseColor("#FF453A"));
                    btnRemove.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    btnRemove.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));
                    btnRemove.setOnClickListener(v -> {
                        String current = mManager.getPrefs().getString(AirCommandManager.PREF_AIR_COMMAND_TOOLS, AirCommandManager.DEFAULT_TOOLS);
                        List<String> list = new ArrayList<>(Arrays.asList(current.split(",")));
                        list.remove("app:" + pkg);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < list.size(); i++) {
                            String s = list.get(i).trim();
                            if (!s.isEmpty()) {
                                if (sb.length() > 0) sb.append(",");
                                sb.append(s);
                            }
                        }
                        mManager.getPrefs().edit().putString(AirCommandManager.PREF_AIR_COMMAND_TOOLS, sb.toString()).apply();
                        populateAirCommandToolsView(container, dialog);
                    });
                    row.addView(btnRemove);

                    container.addView(row);
                } catch (Exception ignored) {}
            }
        }
    }

    private void showAppPickerForAirCommand(LinearLayout toolsContainer, AlertDialog toolsDialog) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_app_shortcut, null);
        EditText etSearch = dialogView.findViewById(R.id.et_search_app_shortcut);
        LinearLayout appListContainer = dialogView.findViewById(R.id.app_picker_container);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_app_picker);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        final AlertDialog pickerDialog = builder.create();
        if (pickerDialog.getWindow() != null) {
            pickerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        List<AppListAdapter.AppEntry> launchableApps = new ArrayList<>();
        synchronized (mAllInstalledApps) {
            for (AppListAdapter.AppEntry entry : mAllInstalledApps) {
                if (getPackageManager().getLaunchIntentForPackage(entry.packageName) != null) {
                    launchableApps.add(entry);
                }
            }
        }

        final float density = getResources().getDisplayMetrics().density;

        Runnable populateApps = new Runnable() {
            @Override
            public void run() {
                appListContainer.removeAllViews();
                String filter = etSearch.getText() != null ? etSearch.getText().toString().toLowerCase().trim() : "";
                for (AppListAdapter.AppEntry entry : launchableApps) {
                    if (!filter.isEmpty() && !entry.label.toLowerCase().contains(filter) && !entry.packageName.toLowerCase().contains(filter)) {
                        continue;
                    }

                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
                    row.setBackgroundResource(R.drawable.btn_secondary);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    rowParams.setMargins(0, 0, 0, (int) (6 * density));
                    row.setLayoutParams(rowParams);

                    ImageView iv = new ImageView(MainActivity.this);
                    iv.setImageDrawable(entry.icon);
                    int iconSize = (int) (36 * density);
                    row.addView(iv, new LinearLayout.LayoutParams(iconSize, iconSize));

                    LinearLayout col = new LinearLayout(MainActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setPadding((int) (12 * density), 0, 0, 0);
                    col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

                    TextView tvTitle = new TextView(MainActivity.this);
                    tvTitle.setText(entry.label);
                    tvTitle.setTextColor(Color.WHITE);
                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tvTitle.getPaint().setFakeBoldText(true);
                    col.addView(tvTitle);

                    TextView tvPkg = new TextView(MainActivity.this);
                    tvPkg.setText(entry.packageName);
                    tvPkg.setTextColor(Color.parseColor("#8E8E93"));
                    tvPkg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    col.addView(tvPkg);

                    row.addView(col);

                    row.setOnClickListener(v -> {
                        String current = mManager.getPrefs().getString(AirCommandManager.PREF_AIR_COMMAND_TOOLS, AirCommandManager.DEFAULT_TOOLS);
                        String newEntry = "app:" + entry.packageName;
                        if (!current.contains(newEntry)) {
                            String updated = current + "," + newEntry;
                            mManager.getPrefs().edit().putString(AirCommandManager.PREF_AIR_COMMAND_TOOLS, updated).apply();
                            populateAirCommandToolsView(toolsContainer, toolsDialog);
                            Toast.makeText(MainActivity.this, entry.label + " added to Air Command", Toast.LENGTH_SHORT).show();
                        }
                        pickerDialog.dismiss();
                    });

                    appListContainer.addView(row);
                }
            }
        };

        populateApps.run();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populateApps.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnCancel.setOnClickListener(v -> pickerDialog.dismiss());
        pickerDialog.show();
    }


    private void showAppProfileDialog(final AppListAdapter.AppEntry entry) {
        final StylusHapticsManager.AppProfile profile = mManager.getAppProfile(entry.packageName);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_profile, null);
        ImageView ivIcon = dialogView.findViewById(R.id.dialog_app_icon);
        TextView tvTitle = dialogView.findViewById(R.id.dialog_app_title);
        TextView tvPackage = dialogView.findViewById(R.id.dialog_app_package);
        final Switch swApp = dialogView.findViewById(R.id.dialog_app_switch);
        final Spinner spPattern = dialogView.findViewById(R.id.dialog_spinner_pattern);
        ivIcon.setImageDrawable(entry.icon);
        tvTitle.setText(entry.label);
        tvPackage.setText(entry.packageName);
        swApp.setChecked(profile.enabled);

        // Populate Pattern Spinner with ColorOS rounded dropdown styling
        ArrayAdapter<String> patternAdapter = new ArrayAdapter<>(this, R.layout.item_coloros_spinner_selected, PATTERN_NAMES);
        patternAdapter.setDropDownViewResource(R.layout.item_coloros_dropdown);
        spPattern.setAdapter(patternAdapter);
        spPattern.setSelection(Math.max(0, Math.min(PATTERN_NAMES.length - 1, profile.pattern)));

        // Auto-save: Switch toggle
        swApp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                profile.enabled = isChecked;
                entry.isSelected = isChecked;
                mManager.saveAppProfile(profile);
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                updateWhitelistCounter();
            }
        });

        // Auto-save: Pattern selection
        spPattern.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (profile.pattern != position) {
                    profile.pattern = position;
                    mManager.saveAppProfile(profile);
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                    int globalStrength = mManager.getPrefs().getInt(StylusHapticsManager.PREF_GLOBAL_STRENGTH, 4);
                    mManager.triggerPattern(position, globalStrength);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private String getScrollSpeedLabel(int speed) {
        switch (speed) {
            case 0: return "Ultra Slow";
            case 1: return "Slow";
            case 3: return "Fast";
            case 4: return "Very Fast";
            case 2: default: return "Normal";
        }
    }

    private void updatePointerSizeUI(int sizeDp) {
        if (mTvPointerSizeVal != null) {
            mTvPointerSizeVal.setText(sizeDp + " dp");
        }
        if (mViewPointerPreview != null) {
            float density = getResources().getDisplayMetrics().density;
            int previewPx = Math.round(sizeDp * density);
            android.view.ViewGroup.LayoutParams lp = mViewPointerPreview.getLayoutParams();
            if (lp != null) {
                lp.width = previewPx;
                lp.height = previewPx;
                mViewPointerPreview.setLayoutParams(lp);
            }
        }
    }

    private void updateWhitelistCounter() {
        Set<String> whitelist = mManager.getPrefs().getStringSet(StylusHapticsManager.PREF_WHITELIST, new HashSet<String>());
        mTvWhitelistCount.setText(whitelist.size() + " apps enabled");
    }

    private void runAutomatedRootSetup() {
        mTvStatusChip.setText("Configuring...");
        mTvStatusChip.setTextColor(Color.parseColor("#FFCC00"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = mManager.applyRootSetup();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        boolean accessEnabled = StylusHapticsManager.isAccessibilityServiceEnabled(MainActivity.this);
                        if (accessEnabled) {
                            Toast.makeText(MainActivity.this, "Service enabled successfully", Toast.LENGTH_SHORT).show();
                            mManager.bind();
                            updateStatusUI(true, "Connected");
                        } else {
                            Toast.makeText(MainActivity.this, "Opening Accessibility Settings...", Toast.LENGTH_SHORT).show();
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                startActivity(intent);
                            } catch (Throwable ignored) {}
                            updateStatusUI(false, "Accessibility Required");
                        }
                    }
                });
            }
        }).start();
    }
}
