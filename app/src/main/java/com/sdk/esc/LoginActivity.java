package com.sdk.esc;

import com.mphoto.mono.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private TextView errorText;
    private ProgressBar progressBar;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyLocaleFromPrefs();
        super.onCreate(savedInstanceState);

        tokenManager = TokenManager.getInstance(this);

        if (tokenManager.canEnterApp()) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_login);
        GlassDialogHelper.applyPinkSystemBars(this);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        Button guestButton = findViewById(R.id.guestButton);
        ImageButton languageButton = findViewById(R.id.languageButton);
        errorText = findViewById(R.id.errorText);
        progressBar = findViewById(R.id.progressBar);

        loginButton.setOnClickListener(v -> performLogin());
        if (guestButton != null) {
            guestButton.setOnClickListener(v -> enterGuestMode());
        }
        if (languageButton != null) {
            languageButton.setOnClickListener(v -> showLanguagePicker());
        }
    }

    private void applyLocaleFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String lang = prefs.getString("language", "vi");
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void showLanguagePicker() {
        View root = getLayoutInflater().inflate(R.layout.dialog_language, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .create();
        View.OnClickListener pick = v -> {
            String code;
            int id = v.getId();
            if (id == R.id.btnLanguageEn) {
                code = "en";
            } else if (id == R.id.btnLanguageKo) {
                code = "ko";
            } else {
                code = "vi";
            }
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putString("language", code)
                    .apply();
            dialog.dismiss();
            recreate();
        };
        root.findViewById(R.id.btnLanguageVi).setOnClickListener(pick);
        root.findViewById(R.id.btnLanguageEn).setOnClickListener(pick);
        root.findViewById(R.id.btnLanguageKo).setOnClickListener(pick);
        root.findViewById(R.id.btnCloseLanguage).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        GlassDialogHelper.applyGlassWindow(dialog);
    }

    private void enterGuestMode() {
        tokenManager.enterGuestMode();
        Toast.makeText(this, getString(R.string.login_guest_mode_hint), Toast.LENGTH_LONG).show();
        navigateToMain();
    }

    private void performLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty()) {
            showError(getString(R.string.login_please_enter_email));
            return;
        }
        if (password.isEmpty()) {
            showError(getString(R.string.login_please_enter_password));
            return;
        }

        showLoading(true);

        new Thread(() -> {
            try {
                JSONObject response = ApiService.login(email, password);
                runOnUiThread(() -> {
                    showLoading(false);
                    if (response != null && response.has("token")) {
                        try {
                            String token = response.getString("token");
                            tokenManager.saveToken(token);
                            if (response.has("user")) {
                                org.json.JSONObject user = response.getJSONObject("user");
                                String uid = parseUserIdFromJson(user);
                                if (uid != null) {
                                    tokenManager.setUserIdFromApi(uid);
                                }
                            }
                            if (response.has("name")) {
                                tokenManager.saveUsername(response.getString("name"));
                            } else if (response.has("username")) {
                                tokenManager.saveUsername(response.getString("username"));
                            } else if (response.has("user")) {
                                JSONObject user = response.getJSONObject("user");
                                if (user.has("name")) {
                                    tokenManager.saveUsername(user.getString("name"));
                                } else if (user.has("username")) {
                                    tokenManager.saveUsername(user.getString("username"));
                                }
                            }
                            MonoDriveServerSync.requestSyncIfLoggedIn(LoginActivity.this);
                            MonoGalleryCleanup.runInBackground(LoginActivity.this);
                            final String tokenForSync = token;
                            new Thread(() ->
                                    GalleryUploadMethodService.getInstance(LoginActivity.this)
                                            .syncFromServer(tokenForSync)
                            ).start();
                            Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show();
                            navigateToMain();
                        } catch (Exception e) {
                            showError(getString(R.string.login_error_processing));
                        }
                    } else {
                        showError(getString(R.string.login_invalid_credentials));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    showError(getString(R.string.login_connection_error));
                });
            }
        }).start();
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, Activity_Camera2.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            errorText.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            loginButton.setEnabled(true);
        }
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private static String parseUserIdFromJson(JSONObject user) {
        if (user == null || !user.has("_id") || user.isNull("_id")) {
            return null;
        }
        try {
            Object v = user.get("_id");
            if (v instanceof String) {
                return (String) v;
            }
            JSONObject o = user.optJSONObject("_id");
            if (o != null && o.has("$oid")) {
                return o.getString("$oid");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
