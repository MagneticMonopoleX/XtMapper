package xtr.keymapper.editor;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.compose.ui.platform.ComposeView;

import xtr.keymapper.R;
import xtr.keymapper.keymap.KeymapConfig;
import xtr.keymapper.server.RemoteServiceHelper;

public class EditorService extends ComposeLifecycleService {
    private static final String TAG = "EditorService";
    private EditorUI editor;
    private ComposeView composeView;

    private void editorCallback() {
        editor = null;
        composeView = null;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (editor != null) {
            EditorUI ui = editor;
            editor = null;
            ui.hideView();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String selectedProfile = intent.getStringExtra(EditorActivity.PROFILE_NAME);
        if (selectedProfile == null) {
            selectedProfile = "Default";
        }

        KeymapConfig keymapConfig = new KeymapConfig(this);

        if (keymapConfig.editorOverlay) {
            if (editor != null) {
                EditorUI ui = editor;
                editor = null;
                ui.hideView();
            }

            Context context = new ContextThemeWrapper(
                    EditorService.this,
                    R.style.Theme_XtMapper
            );

            // Create the ComposeView using the themed context.
            composeView = createComposeView(context);

            editor = new EditorUI(
                    context,
                    this::editorCallback,
                    selectedProfile,
                    EditorUI.START_EDITOR,
                    composeView
            );

            RemoteServiceHelper.getInstance(EditorService.this, remoteService -> {
                try {
                    if (editor != null) {
                        editor.registerOnKeyEventListener(remoteService);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to register key event listener: " + e.getMessage(), e);
                }
            });

            editor.open(true);

        } else {
            Intent newIntent = new Intent(this, EditorActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            newIntent.putExtra(
                    EditorActivity.PROFILE_NAME,
                    selectedProfile
            );

            startActivity(newIntent);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
