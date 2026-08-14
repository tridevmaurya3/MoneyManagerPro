package com.example.moneymanagerpro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Package-locked same-device IPC endpoint for Tridev finance events.
 *
 * Only the real Android caller UID is trusted. A caller cannot gain access by
 * placing a package-name string in a Bundle. SmartSMSPro sends structured
 * financial metadata only; raw SMS bodies are not accepted by this provider.
 */
public final class TridevFinanceEventProvider extends ContentProvider {

    public static final String AUTHORITY =
            "com.example.moneymanagerpro.tridev.finance";
    public static final String METHOD_ACCEPT_V1 =
            "accept_finance_event_v1";

    private static final String TRUSTED_SMART_SMS_PACKAGE =
            "com.tridev.smartsmspro";

    private static final String KEY_EVENT_ID = "event_id";
    private static final String KEY_SOURCE_RECORD_ID = "source_record_id";
    private static final String KEY_EVENT_TYPE = "event_type";
    private static final String KEY_DIRECTION = "direction";
    private static final String KEY_AMOUNT_MINOR = "amount_minor";
    private static final String KEY_CURRENCY = "currency";
    private static final String KEY_OCCURRED_AT = "occurred_at";
    private static final String KEY_ACCOUNT_HINT = "account_hint";
    private static final String KEY_MERCHANT_HINT = "merchant_hint";
    private static final String KEY_CATEGORY_HINT = "category_hint";
    private static final String KEY_FINGERPRINT = "fingerprint";

    private static final String RESULT_STATUS = "status";
    private static final String RESULT_EVENT_ID = "event_id";
    private static final String RESULT_CANONICAL_EVENT_ID = "canonical_event_id";
    private static final String RESULT_TRANSACTION_ID = "transaction_id";
    private static final String RESULT_REASON = "reason";

    @Override
    public boolean onCreate() {
        return getContext() != null;
    }

    @Nullable
    @Override
    public Bundle call(
            @NonNull String method,
            @Nullable String arg,
            @Nullable Bundle extras) {
        if (!METHOD_ACCEPT_V1.equals(method)) {
            return response("REJECTED", "", null, null,
                    "Unsupported integration method");
        }

        Context context = getContext();
        if (context == null) {
            return response("FAILED", "", null, null,
                    "MoneyManager context is unavailable");
        }

        if (!isTrustedCaller(context)) {
            return response("REJECTED", "", null, null,
                    "Caller is not an approved Tridev app");
        }

        if (extras == null) {
            return response("REJECTED", "", null, null,
                    "Finance event payload is missing");
        }

        try {
            String eventId = structured(extras.getString(KEY_EVENT_ID), 120, false);
            String sourceRecordId = structured(
                    extras.getString(KEY_SOURCE_RECORD_ID), 160, false);
            String eventTypeValue = structured(
                    extras.getString(KEY_EVENT_TYPE), 40, false);
            String directionValue = structured(
                    extras.getString(KEY_DIRECTION), 20, false);
            long amountMinor = extras.getLong(KEY_AMOUNT_MINOR, 0L);
            String currency = structured(
                    extras.getString(KEY_CURRENCY), 8, false)
                    .toUpperCase(Locale.ROOT);
            long occurredAt = extras.getLong(KEY_OCCURRED_AT, 0L);
            String accountHint = metadata(extras.getString(KEY_ACCOUNT_HINT), 160);
            String merchantHint = metadata(extras.getString(KEY_MERCHANT_HINT), 120);
            String categoryHint = metadata(extras.getString(KEY_CATEGORY_HINT), 80);
            String fingerprint = structured(
                    extras.getString(KEY_FINGERPRINT), 160, true);

            if (amountMinor <= 0L) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }
            if (!TridevIntegrationContract.DEFAULT_CURRENCY.equalsIgnoreCase(currency)) {
                throw new IllegalArgumentException("Only INR is accepted by this endpoint");
            }
            if (occurredAt <= 0L) {
                throw new IllegalArgumentException("Transaction time is required");
            }

            TridevIntegrationContract.EventType eventType =
                    safeEventType(eventTypeValue);
            TridevIntegrationContract.Direction direction =
                    safeDirection(directionValue);

            TridevIntegrationContract.Event event =
                    new TridevIntegrationContract.Event(
                            eventId,
                            TridevIntegrationContract.APP_SMART_SMS,
                            sourceRecordId,
                            eventType,
                            direction,
                            TridevIntegrationContract.Scope.PERSONAL,
                            amountMinor,
                            TridevIntegrationContract.DEFAULT_CURRENCY,
                            occurredAt,
                            System.currentTimeMillis(),
                            accountHint,
                            merchantHint,
                            categoryHint,
                            "",
                            fingerprint,
                            TridevIntegrationContract.SyncState.PENDING,
                            TridevIntegrationContract.MatchConfidence.UNMATCHED,
                            TridevIntegrationContract.References.empty());

            TridevFinanceIntegrationCoordinator.Result result =
                    new TridevFinanceIntegrationCoordinator(context)
                            .acceptAndProcess(event);

            return response(
                    result.outcome.name(),
                    result.eventId,
                    result.canonicalEventId,
                    result.moneyManagerTransactionId,
                    result.reason);
        } catch (RuntimeException invalidPayload) {
            return response("REJECTED", "", null, null,
                    "Finance event failed validation");
        }
    }

    private boolean isTrustedCaller(Context context) {
        int callingUid = Binder.getCallingUid();
        PackageManager packageManager = context.getPackageManager();
        String[] packages = packageManager.getPackagesForUid(callingUid);
        if (packages == null) return false;
        for (String packageName : packages) {
            if (TRUSTED_SMART_SMS_PACKAGE.equals(packageName)) return true;
        }
        return false;
    }

    private TridevIntegrationContract.EventType safeEventType(String value) {
        try {
            TridevIntegrationContract.EventType type =
                    TridevIntegrationContract.EventType.valueOf(value);
            switch (type) {
                case SMS_FINANCIAL_SIGNAL:
                case LOAN_PAYMENT:
                case TRANSFER:
                case REFUND:
                    return type;
                default:
                    return TridevIntegrationContract.EventType.SMS_FINANCIAL_SIGNAL;
            }
        } catch (IllegalArgumentException ignored) {
            return TridevIntegrationContract.EventType.SMS_FINANCIAL_SIGNAL;
        }
    }

    private TridevIntegrationContract.Direction safeDirection(String value) {
        try {
            TridevIntegrationContract.Direction direction =
                    TridevIntegrationContract.Direction.valueOf(value);
            if (direction == TridevIntegrationContract.Direction.DEBIT
                    || direction == TridevIntegrationContract.Direction.CREDIT
                    || direction == TridevIntegrationContract.Direction.TRANSFER) {
                return direction;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return TridevIntegrationContract.Direction.UNKNOWN;
    }

    private Bundle response(
            String status,
            String eventId,
            @Nullable String canonicalEventId,
            @Nullable String transactionId,
            String reason) {
        Bundle result = new Bundle();
        result.putString(RESULT_STATUS, safe(status));
        result.putString(RESULT_EVENT_ID, safe(eventId));
        result.putString(RESULT_CANONICAL_EVENT_ID, safe(canonicalEventId));
        result.putString(RESULT_TRANSACTION_ID, safe(transactionId));
        result.putString(RESULT_REASON, safeReason(reason));
        return result;
    }

    private String structured(
            @Nullable String value,
            int maxLength,
            boolean optional) {
        String safe = safe(value);
        if (!optional && safe.isEmpty()) {
            throw new IllegalArgumentException("Required structured value is missing");
        }
        if (safe.length() > maxLength
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Structured value is invalid");
        }
        return safe;
    }

    private String metadata(@Nullable String value, int maxLength) {
        String safe = safe(value);
        if (safe.length() > maxLength
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Metadata is invalid");
        }
        return safe;
    }

    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private String safeReason(@Nullable String value) {
        String safe = safe(value).replace('\n', ' ').replace('\r', ' ');
        return safe.length() <= 240 ? safe : safe.substring(0, 240).trim();
    }

    // This provider exposes no database CRUD surface. IPC is call()-only.
    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }
}
