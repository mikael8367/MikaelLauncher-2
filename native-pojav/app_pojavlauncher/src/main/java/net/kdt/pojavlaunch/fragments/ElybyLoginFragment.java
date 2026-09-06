package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Ely.by Yggdrasil-compatible login. Passwords are sent only over HTTPS and never stored. */
public class ElybyLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";

    private EditText username;
    private EditText password;
    private EditText twoFactor;
    private View loginButton;

    public ElybyLoginFragment() {
        super(R.layout.fragment_elyby_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        username = view.findViewById(R.id.elyby_username);
        password = view.findViewById(R.id.elyby_password);
        twoFactor = view.findViewById(R.id.elyby_two_factor);
        loginButton = view.findViewById(R.id.elyby_login_button);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        twoFactor.setInputType(InputType.TYPE_CLASS_NUMBER);
        loginButton.setOnClickListener(v -> authenticate());
    }

    private void authenticate() {
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        String token = twoFactor.getText().toString().trim();
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(requireContext(), R.string.elyby_missing_credentials, Toast.LENGTH_LONG).show();
            return;
        }
        loginButton.setEnabled(false);
        new Thread(() -> {
            try {
                String response = request(user, token.isEmpty() ? pass : pass + ":" + token);
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                if (!json.has("accessToken") || !json.has("selectedProfile")) {
                    throw new IllegalStateException(json.has("errorMessage") ? json.get("errorMessage").getAsString() : "Resposta Ely.by inválida");
                }
                JsonObject profile = json.getAsJsonObject("selectedProfile");
                MinecraftAccount account = new MinecraftAccount();
                account.username = profile.get("name").getAsString();
                account.profileId = profile.get("id").getAsString();
                account.accessToken = json.get("accessToken").getAsString();
                account.clientToken = json.has("clientToken") ? json.get("clientToken").getAsString() : UUID.randomUUID().toString();
                account.isMicrosoft = false;
                account.save();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), R.string.elyby_login_success, Toast.LENGTH_LONG).show();
                    Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
                });
            } catch (Exception error) {
                requireActivity().runOnUiThread(() -> {
                    loginButton.setEnabled(true);
                    Toast.makeText(requireContext(), getString(R.string.elyby_login_error, error.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String request(String user, String pass) throws Exception {
        URL url = new URL("https://authserver.ely.by/auth/authenticate");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        JsonObject body = new JsonObject();
        body.addProperty("username", user);
        body.addProperty("password", pass);
        body.addProperty("clientToken", "mikaellauncher-android");
        body.addProperty("requestUser", true);
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(data);
        }
        InputStream stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) throw new IllegalStateException("Sem resposta do Ely.by");
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        if (connection.getResponseCode() >= 400) throw new IllegalStateException(JsonParser.parseString(result.toString()).getAsJsonObject().has("errorMessage") ? JsonParser.parseString(result.toString()).getAsJsonObject().get("errorMessage").getAsString() : "Credenciais recusadas");
        return result.toString();
    }
}
