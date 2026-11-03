package com.darexsh.appvault;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public class ApkInstallStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmationIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmationIntent != null) {
                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmationIntent);
            }
            return;
        }

        int messageResId = status == PackageInstaller.STATUS_SUCCESS
                ? R.string.install_success
                : R.string.install_failed;
        Toast.makeText(context, messageResId, Toast.LENGTH_LONG).show();
    }
}
