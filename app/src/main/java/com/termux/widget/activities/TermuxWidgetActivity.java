package com.termux.widget.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.file.FileUtils;
import com.termux.shared.file.TermuxFileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.models.errors.Error;
import com.termux.shared.packages.PackageUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.widget.R;
import com.termux.widget.ShortcutFile;
import com.termux.widget.TermuxWidgetProvider;
import com.termux.widget.utils.ShortcutUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TermuxWidgetActivity extends AppCompatActivity {

    private static final String LOG_TAG = "TermuxWidgetActivity";

    /** Termux:Widget app data home directory path. */
    public static final String TERMUX_WIDGET_DATA_HOME_DIR_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/widget"; // Default: "/data/data/com.termux/files/home/.termux/widget"

    /** Termux:Widget app directory path to store scripts/binaries to be used as dynamic shortcuts. */
    public static final String TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH = TERMUX_WIDGET_DATA_HOME_DIR_PATH + "/dynamic_shortcuts"; // Default: "/data/data/com.termux/files/home/.termux/widget/dynamic_shortcuts"

    public static final String MAX_SHORTCUTS_LIMIT_DOCS_URL = TermuxConstants.TERMUX_WIDGET_GITHUB_REPO_URL + "#max-shortcuts-limit-optional"; // Default: "https://github.com/termux/termux-widget#max-shortcuts-limit-optional"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Logger.logDebug(LOG_TAG, "onCreate");

        setContentView(R.layout.activity_termux_widget);

        TextView pluginInfo = findViewById(R.id.textview_plugin_info);
        pluginInfo.setText(getString(R.string.plugin_info, TermuxConstants.TERMUX_GITHUB_REPO_URL,
                TermuxConstants.TERMUX_WIDGET_GITHUB_REPO_URL));

        setDisableLauncherIconViews();
        setDynamicShortcutsViews();
        setRefreshAllWidgetsViews();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        setMaxShortcutsLimitView();
    }

    private void setMaxShortcutsLimitView() {
        LinearLayout maxShortcutsInfoLinearLayout = findViewById(R.id.linearlayout_max_shortcuts_limit_info);
        maxShortcutsInfoLinearLayout.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = ShortcutUtils.getShortcutManager(this, LOG_TAG, true);
            TextView maxShortcutsInfoTextView = findViewById(R.id.textview_max_shortcuts_limit_info);
            if (shortcutManager != null) {
                maxShortcutsInfoLinearLayout.setVisibility(View.VISIBLE);
                maxShortcutsInfoTextView.setText(getString(R.string.msg_max_shortcuts_limit_info,
                        shortcutManager.getMaxShortcutCountPerActivity(), MAX_SHORTCUTS_LIMIT_DOCS_URL));
            }
        }
    }

    private void setDisableLauncherIconViews() {
        Button disableLauncherIconButton = findViewById(R.id.btn_disable_launcher_icon);
        disableLauncherIconButton.setOnClickListener(v -> {
            String message = getString(R.string.msg_disabling_launcher_icon, TermuxConstants.TERMUX_WIDGET_APP_NAME);
            Logger.logInfo(LOG_TAG, message);
            PackageUtils.setComponentState(TermuxWidgetActivity.this,
                    TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME, TermuxConstants.TERMUX_WIDGET.TERMUX_WIDGET_ACTIVITY_NAME,
                    false, message, true);
        });
    }

    private void setDynamicShortcutsViews() {
        LinearLayout dynamicShortcutsLinearLayout = findViewById(R.id.linearlayout_dynamic_shortcuts);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            dynamicShortcutsLinearLayout.setVisibility(View.VISIBLE);

            TextView dynamicShortcutsInfoTextView = findViewById(R.id.textview_dynamic_shortcuts_info);
            dynamicShortcutsInfoTextView.setText(getString(R.string.msg_dynamic_shortcuts_info,
                    TermuxFileUtils.getUnExpandedTermuxPath(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH)));

            Button createDynamicShortcutsButton = findViewById(R.id.btn_create_dynamic_shortcuts);
            createDynamicShortcutsButton.setOnClickListener(v -> createDynamicShortcuts(TermuxWidgetActivity.this));
            
            Button removeDynamicShortcutsButton = findViewById(R.id.btn_remove_dynamic_shortcuts);
            removeDynamicShortcutsButton.setOnClickListener(v -> removeDynamicShortcuts(TermuxWidgetActivity.this));
        } else {
            dynamicShortcutsLinearLayout.setVisibility(View.GONE);
        }
    }

    private void setRefreshAllWidgetsViews() {
        Button refreshAllWidgetsIconButton = findViewById(R.id.btn_refresh_all_widgets);
        refreshAllWidgetsIconButton.setOnClickListener(v -> TermuxWidgetProvider.sendIntentToRefreshAllWidgets(TermuxWidgetActivity.this, LOG_TAG));
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private void createDynamicShortcuts(@NonNull Context context) {
        ShortcutManager shortcutManager = ShortcutUtils.getShortcutManager(context, LOG_TAG, true);
        if (shortcutManager == null) return;

        // Create directory if necessary so user more easily finds where to put shortcuts
        Error error = FileUtils.createDirectoryFile(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH);
        if (error != null) {
            Logger.logError(LOG_TAG, error.toString());
            Logger.showToast(this, Error.getMinimalErrorLogString(error), true);
        }

        List<ShortcutFile> shortcutFiles = new ArrayList<>();
        ShortcutUtils.enumerateShortcutFiles(shortcutFiles, new File(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH), false);

        if (shortcutFiles.size() == 0) {
            Logger.showToast(context, getString(R.string.msg_no_shortcut_files_found_in_directory,
                    TermuxFileUtils.getUnExpandedTermuxPath(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH)), true);
            return;
        }

        List<ShortcutInfo> shortcuts = new ArrayList<>();
        for (ShortcutFile shortcutFile : shortcutFiles) {
            shortcuts.add(shortcutFile.getShortcutInfo(context, false));
        }

        // Remove shortcuts that can not be added.
        int maxShortcuts = shortcutManager.getMaxShortcutCountPerActivity();
        Logger.logDebug(LOG_TAG, "Found " + shortcutFiles.size() + " shortcuts and max shortcuts limit is " + maxShortcuts);
        if (shortcuts.size() > maxShortcuts) {
            Logger.logErrorAndShowToast(context, LOG_TAG, getString(R.string.msg_dynamic_shortcuts_limit_reached, maxShortcuts));
            while (shortcuts.size() > maxShortcuts) {
                String message = getString(R.string.msg_skipping_shortcut,
                        shortcuts.get(shortcuts.size() - 1).getId().replaceAll(
                                "^" + Pattern.quote(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH + "/"), ""));
                Logger.showToast(context, message, false);
                Logger.logDebug(LOG_TAG, message);
                shortcuts.remove(shortcuts.size() - 1);
            }
        }

        shortcutManager.removeAllDynamicShortcuts();
        shortcutManager.addDynamicShortcuts(shortcuts);
        Logger.logDebugAndShowToast(context, LOG_TAG, getString(R.string.msg_created_dynamic_shortcuts_successfully, shortcuts.size()));
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private void removeDynamicShortcuts(@NonNull Context context) {
        ShortcutManager shortcutManager = ShortcutUtils.getShortcutManager(context, LOG_TAG, true);
        if (shortcutManager == null) return;

        List<ShortcutInfo> shortcuts = shortcutManager.getDynamicShortcuts();
        if (shortcuts != null && shortcuts.size() == 0) {
            Logger.logDebugAndShowToast(context, LOG_TAG, getString(R.string.msg_no_dynamic_shortcuts_currently_created));
            return;
        }

        shortcutManager.removeAllDynamicShortcuts();
        Logger.logDebugAndShowToast(context, LOG_TAG, getString(R.string.msg_removed_dynamic_shortcuts_successfully));
    }

}
