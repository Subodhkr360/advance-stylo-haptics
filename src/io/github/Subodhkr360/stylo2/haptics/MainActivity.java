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

import java.util.ArrayList;
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
    private Spinner mSpinnerVibrationType;
    private SeekBar mSeekbarGlobalStrength;
    private TextView mTvGlobalStrengthVal;
    private Button mBtnTestDrawing;
    private EditText mEtSearch;
    private ListView mLvApps;
    private TextView mTvWhitelistCount;

    private View mTabBtnSettings;
    private View mTabBtnApps;
    private View mPaneSettings;
    private View mPaneApps;

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
    }

    private void ensureAccessibilityServiceRunning() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String svc = getPackageName() + "/" + StyloAccessibilityService.class.getName();
                    Process p = Runtime.getRuntime().exec("su");
                    java.io.DataOutputStream os = new java.io.DataOutputStream(p.getOutputStream());
                    os.writeBytes("settings put secure enabled_accessibility_services " + svc + "\n");
                    os.writeBytes("settings put secure accessibility_enabled 1\n");
                    os.writeBytes("exit\n");
                    os.flush();
                    p.waitFor();
                } catch (Exception ignored) {}
            }
        }).start();
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
        mSwitchFunctionalFeedback = findViewById(R.id.switch_functional_feedback);
        mSwitchHandwritingFeedback = findViewById(R.id.switch_handwriting_feedback);
        mSwitchGlobalMode = findViewById(R.id.switch_global_mode);
        mSwitchShowSystemApps = findViewById(R.id.switch_show_system_apps);
        mSwitchShowLauncherIcon = findViewById(R.id.switch_show_launcher_icon);
        mSpinnerVibrationType = findViewById(R.id.spinner_vibration_type);
        mSeekbarGlobalStrength = findViewById(R.id.seekbar_global_strength);
        mTvGlobalStrengthVal = findViewById(R.id.tv_global_strength_val);
        mBtnTestDrawing = findViewById(R.id.btn_test_drawing);
        mEtSearch = findViewById(R.id.et_app_search);
        mLvApps = findViewById(R.id.lv_apps);
        mTvWhitelistCount = findViewById(R.id.tv_whitelist_count);

        mTabBtnSettings = findViewById(R.id.tab_btn_settings);
        mTabBtnApps = findViewById(R.id.tab_btn_apps);
        mPaneSettings = findViewById(R.id.pane_settings);
        mPaneApps = findViewById(R.id.pane_apps);

        SharedPreferences prefs = mManager.getPrefs();
        mSwitchFunctionalFeedback.setChecked(prefs.getBoolean(StylusHapticsManager.PREF_FUNCTIONAL_FEEDBACK, true));
        mSwitchHandwritingFeedback.setChecked(prefs.getBoolean(StylusHapticsManager.PREF_HANDWRITING_FEEDBACK, true));
        mSwitchGlobalMode.setChecked(prefs.getBoolean(StylusHapticsManager.PREF_GLOBAL_MODE, false));
        mSwitchShowSystemApps.setChecked(prefs.getBoolean("pref_show_system_apps", false));

        // Launcher Icon Setting
        if (mSwitchShowLauncherIcon != null) {
            ComponentName launcherComponent = new ComponentName(this, getPackageName() + ".LauncherAlias");
            int state = getPackageManager().getComponentEnabledSetting(launcherComponent);
            boolean isVisible = (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED 
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
            mSwitchShowLauncherIcon.setChecked(isVisible);
        }

        int globalStrength = prefs.getInt(StylusHapticsManager.PREF_GLOBAL_STRENGTH, 4);
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
                        mTvStatusChip.setText(status);
                        if (connected) {
                            mTvStatusChip.setTextColor(Color.parseColor("#34C759"));
                        } else {
                            mTvStatusChip.setTextColor(Color.parseColor("#FF453A"));
                        }
                    }
                });
            }
        });

        // Floating window navigation tabs
        if (mTabBtnSettings != null && mTabBtnApps != null && mPaneSettings != null && mPaneApps != null) {
            mTabBtnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPaneSettings.setVisibility(View.VISIBLE);
                    mPaneApps.setVisibility(View.GONE);
                    mTabBtnSettings.setBackgroundResource(R.drawable.tab_pill_active);
                    ((TextView) mTabBtnSettings).setTextColor(Color.WHITE);
                    mTabBtnApps.setBackgroundResource(R.drawable.tab_pill_inactive);
                    ((TextView) mTabBtnApps).setTextColor(getColor(R.color.text_secondary));
                }
            });

            mTabBtnApps.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPaneSettings.setVisibility(View.GONE);
                    mPaneApps.setVisibility(View.VISIBLE);
                    mTabBtnApps.setBackgroundResource(R.drawable.tab_pill_active);
                    ((TextView) mTabBtnApps).setTextColor(Color.WHITE);
                    mTabBtnSettings.setBackgroundResource(R.drawable.tab_pill_inactive);
                    ((TextView) mTabBtnSettings).setTextColor(getColor(R.color.text_secondary));
                }
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

    private void loadInstalledAppsAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);

                boolean showSystemApps = mManager.getPrefs().getBoolean("pref_show_system_apps", false);
                final List<AppListAdapter.AppEntry> entries = new ArrayList<>();
                for (ApplicationInfo info : installed) {
                    StylusHapticsManager.AppProfile profile = mManager.getAppProfile(info.packageName);
                    boolean isSelected = profile.enabled;
                    boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    if (!showSystemApps && !isSelected) {
                        if (isSystem || pm.getLaunchIntentForPackage(info.packageName) == null) {
                            continue;
                        }
                    }

                    String label = info.loadLabel(pm).toString();
                    Drawable icon = info.loadIcon(pm);
                    entries.add(new AppListAdapter.AppEntry(label, info.packageName, icon, isSelected, isSystem));
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
                    }
                });
            }
        }).start();
    }

    private void showAppProfileDialog(final AppListAdapter.AppEntry entry) {
        final StylusHapticsManager.AppProfile profile = mManager.getAppProfile(entry.packageName);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_profile, null);
        ImageView ivIcon = dialogView.findViewById(R.id.dialog_app_icon);
        TextView tvTitle = dialogView.findViewById(R.id.dialog_app_title);
        TextView tvPackage = dialogView.findViewById(R.id.dialog_app_package);
        final Switch swApp = dialogView.findViewById(R.id.dialog_app_switch);
        final Spinner spPattern = dialogView.findViewById(R.id.dialog_spinner_pattern);
        final SeekBar sbStrength = dialogView.findViewById(R.id.dialog_seekbar_strength);
        final TextView tvStrengthVal = dialogView.findViewById(R.id.dialog_tv_strength_val);
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
                    mManager.triggerPattern(position, profile.strength);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Auto-save: Strength Seekbar
        sbStrength.setMax(4);
        sbStrength.setProgress(Math.max(0, Math.min(4, profile.strength - 1)));
        final String[] strengthPercentages = {"20%", "40%", "60%", "80%", "100%"};
        tvStrengthVal.setText(strengthPercentages[sbStrength.getProgress()]);

        sbStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int level = progress + 1;
                tvStrengthVal.setText(strengthPercentages[progress]);
                if (fromUser) {
                    profile.strength = level;
                    mManager.saveAppProfile(profile);
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                    mManager.triggerPattern(profile.pattern, level);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
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

    private void updateWhitelistCounter() {
        Set<String> whitelist = mManager.getPrefs().getStringSet(StylusHapticsManager.PREF_WHITELIST, new HashSet<String>());
        mTvWhitelistCount.setText(whitelist.size() + " apps enabled");
    }

    private void runAutomatedRootSetup() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String pkg = getPackageName();
                    String svc = pkg + "/" + pkg + ".StyloAccessibilityService";
                    Process p = Runtime.getRuntime().exec("su");
                    java.io.DataOutputStream os = new java.io.DataOutputStream(p.getOutputStream());

                    // 1. Enable accessibility service automatically
                    os.writeBytes("settings put secure enabled_accessibility_services " + svc + "\n");
                    os.writeBytes("settings put secure accessibility_enabled 1\n");

                    // 2. Set touchpanel_pen and pencil_control permissions
                    os.writeBytes("chmod 666 /proc/touchpanel/pencil_control\n");
                    os.writeBytes("echo 3 > /proc/touchpanel/pencil_control\n");

                    // 3. Ensure background battery optimization exemption
                    os.writeBytes("dumpsys deviceidle whitelist +" + pkg + "\n");
                    os.writeBytes("dumpsys deviceidle whitelist +com.oplus.ipemanager\n");

                    os.writeBytes("exit\n");
                    os.flush();
                    p.waitFor();

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Root Permissions and Touchpanel Feedback Enabled", Toast.LENGTH_LONG).show();
                            mManager.bind();
                        }
                    });
                } catch (Exception e) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Root command error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
