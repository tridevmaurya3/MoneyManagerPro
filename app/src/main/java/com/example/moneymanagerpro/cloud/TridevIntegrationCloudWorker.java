package com.example.moneymanagerpro.cloud;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Background encrypted integration-state sync. */
public final class TridevIntegrationCloudWorker extends Worker {

    private static final long CALLBACK_TIMEOUT_SECONDS = 35L;

    public TridevIntegrationCloudWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            // Cloud integration is optional until the user signs in/verifies.
            return Result.success();
        }

        CloudBackupKeyVault vault = new CloudBackupKeyVault(getApplicationContext());
        if (!vault.hasSavedPassphrase(user.getUid())) {
            // Do not retry endlessly when encrypted cloud backup has not been unlocked yet.
            return Result.success();
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicReference<Boolean> success = new AtomicReference<>(false);

        new TridevIntegrationCloudManager(getApplicationContext()).syncNow(
                new TridevIntegrationCloudManager.SyncCallback() {
                    @Override
                    public void onSuccess(@NonNull TridevIntegrationCloudManager.CloudStatus status) {
                        success.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        failure.set(exception);
                        latch.countDown();
                    }
                });

        try {
            if (!latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        if (Boolean.TRUE.equals(success.get())) return Result.success();
        return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
    }
}
