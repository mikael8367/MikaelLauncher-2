package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalLoginFragment extends Fragment {
    public static final String TAG = "LOCAL_LOGIN_FRAGMENT";

    private final Pattern mUsernameValidationPattern;
    private EditText mUsernameEditText;
    private ImageView mSkinPreview;
    private File mSelectedSkin;
    private final ActivityResultLauncher<String> mSkinPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || !isAdded()) return;
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(requireContext().getContentResolver().openInputStream(uri));
                    if (bitmap == null) throw new IllegalArgumentException("Imagem inválida");
                    if (bitmap.getWidth() < 32 || bitmap.getHeight() < 32)
                        throw new IllegalArgumentException("A skin deve ter pelo menos 32x32 pixels");
                    mSelectedSkin = new File(requireContext().getCacheDir(), "mikael-selected-skin.png");
                    try (InputStream input = requireContext().getContentResolver().openInputStream(uri);
                         FileOutputStream output = new FileOutputStream(mSelectedSkin)) {
                        byte[] buffer = new byte[8192]; int count;
                        while (input != null && (count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                    mSkinPreview.setImageBitmap(bitmap);
                } catch (Exception error) {
                    Tools.dialog(requireContext(), "Skin inválida", error.getMessage());
                }
            });

    public LocalLoginFragment(){
        super(R.layout.fragment_local_login);
        mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]*$");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.login_edit_email);
        mSkinPreview = view.findViewById(R.id.login_skin_preview);
        view.findViewById(R.id.login_skin_button).setOnClickListener(v -> mSkinPicker.launch("image/*"));
        view.findViewById(R.id.login_button).setOnClickListener(v -> {
            if(!checkEditText()) {
                Context context = v.getContext();
                Tools.dialog(context, context.getString(R.string.local_login_bad_username_title), context.getString(R.string.local_login_bad_username_text));
                return;
            }

            String username = mUsernameEditText.getText().toString();
            try {
                MinecraftAccount account = new MinecraftAccount();
                account.username = username;
                account.accessToken = "0";
                account.isMicrosoft = false;
                File accountDirectory = new File(Tools.DIR_ACCOUNT_NEW);
                if (!accountDirectory.exists()) accountDirectory.mkdirs();
                account.save();
                if (mSelectedSkin != null) saveOfflineSkin(username, mSelectedSkin);
                PojavProfile.setCurrentProfile(requireContext(), username);
            } catch (Exception error) {
                Tools.dialog(requireContext(), "Falha ao criar conta", error.getMessage());
                return;
            }

            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        });
    }

    private void saveOfflineSkin(String username, File source) throws Exception {
        File destination = new File(Tools.DIR_CACHE, username + ".png");
        try (InputStream input = new java.io.FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }


    /** @return Whether the mail (and password) text are eligible to make an auth request  */
    private boolean checkEditText(){

        String text = mUsernameEditText.getText().toString();

        Matcher matcher = mUsernameValidationPattern.matcher(text);
        return !(text.isEmpty()
                || text.length() < 3
                || text.length() > 16
                || !matcher.find()
                || new File(Tools.DIR_ACCOUNT_NEW + "/" + text + ".json").exists()
        );
    }
}
