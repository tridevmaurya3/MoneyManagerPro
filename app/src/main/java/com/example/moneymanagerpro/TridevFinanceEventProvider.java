package com.example.moneymanagerpro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.provider.Telephony;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Package-locked same-device IPC endpoint for Tridev finance events.
 *
 * Trust gates:
 * - SmartSMSPro: Binder caller UID must resolve to the exact package and that
 *   package must be the phone's current default SMS application.
 * - Family Hub: Binder caller UID must resolve to the exact package AND its APK
 *   must be signed with the same certificate as MoneyManagerPro.
 *
 * A caller cannot gain access by placing a package-name string in a Bundle.
 * Structured finance metadata only is accepted; raw SMS bodies and Family Hub
 * free-form notes are never accepted by this provider.
 */
public final class TridevFinanceEventProvider extends ContentProvider {

    public static final String AUTHORITY =
            "com.example.moneymanagerpro.tridev.finance";
    public static final String METHOD_ACCEPT_V1 =
            "accept_finance_event_v1";
    public static final String METHOD_CANCEL_V1 =
            "cancel_finance_event_v1";
    public static final String METHOD_ACCOUNT_CATALOG_V1 =
            "get_account_catalog_v1";

    private static final String TRUSTED_SMART_SMS_PACKAGE =
            "com.tridev.smartsmspro";
    private static final String TRUSTED_FAMILY_HUB_PACKAGE =
            "com.tridev.familyhub";

    private static final String KEY_EVENT_ID = "event_id";
    private static final String KEY_SOURCE_RECORD_ID = "source_record_id";
    private static final String KEY_EVENT_TYPE = "event_type";
    private static final String KEY_DIRECTION = "direction";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_FORCE_REVIEW = "force_review";
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
    private static final String RESULT_ACCOUNT_REFS = "account_refs";
    private static final String RESULT_ACCOUNT_LABELS = "account_labels";

    private enum CallerKind {
        SMART_SMS,
        FAMILY_HUB,
        NONE
    }

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
        Context context = getContext();
        if (context == null) {
            return response("FAILED", "", null, null,
                    "MoneyManager context is unavailable");
        }

        CallerKind caller = trustedCaller(context);
        if (caller == CallerKind.NONE) {
            return response("REJECTED", "", null, null,
                    "Caller is not an approved Tridev finance app");
        }

        if (METHOD_ACCOUNT_CATALOG_V1.equals(method)) {
            if (caller != CallerKind.FAMILY_HUB) {
                return response("REJECTED", "", null, null,
                        "This caller cannot read the MoneyManager account catalog");
            }
            return accountCatalog(context);
        }

        if (METHOD_CANCEL_V1.equals(method)) {
            if (caller != CallerKind.FAMILY_HUB) {
                return response("REJECTED", "", null, null,
                        "This caller cannot cancel Family Hub finance events");
            }
            return cancelFamilyHubGrocery(context, extras);
        }

        if (!METHOD_ACCEPT_V1.equals(method)) {
            return response("REJECTED", "", null, null,
                    "Unsupported integration method");
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
            String scopeValue = structured(
                    extras.getString(KEY_SCOPE), 20, true);
            boolean forceReview = caller == CallerKind.FAMILY_HUB
                    && extras.getBoolean(KEY_FORCE_REVIEW, false);
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
                    safeEventType(eventTypeValue, caller);
            TridevIntegrationContract.Direction direction =
                    safeDirection(directionValue);

            if (caller == CallerKind.FAMILY_HUB) {
                validateFamilyHubEvent(eventType, direction);
            }

            String sourceApp = caller == CallerKind.FAMILY_HUB
                    ? TridevIntegrationContract.APP_FAMILY_HUB
                    : TridevIntegrationContract.APP_SMART_SMS;
            TridevIntegrationContract.Scope scope = caller == CallerKind.FAMILY_HUB
                    ? familyScope(eventType, scopeValue)
                    : TridevIntegrationContract.Scope.PERSONAL;

            TridevIntegrationContract.References references =
                    TridevIntegrationContract.References.empty();
            if (caller == CallerKind.FAMILY_HUB) {
                boolean grocery = eventType
                        == TridevIntegrationContract.EventType.GROCERY_PURCHASE;
                references = new TridevIntegrationContract.References(
                        "",
                        "",
                        "",
                        grocery ? "" : sourceRecordId,
                        grocery ? sourceRecordId : "",
                        "",
                        "");
            }

            TridevIntegrationContract.Event event =
                    new TridevIntegrationContract.Event(
                            eventId,
                            sourceApp,
                            sourceRecordId,
                            eventType,
                            direction,
                            scope,
                            amountMinor,
                            TridevIntegrationContract.DEFAULT_CURRENCY,
                            occurredAt,
                            System.currentTimeMillis(),
                            accountHint,
                            merchantHint,
                            categoryHint,
                            "",
                            fingerprint,
                            forceReview
                                    ? TridevIntegrationContract.SyncState.NEEDS_REVIEW
                                    : TridevIntegrationContract.SyncState.PENDING,
                            TridevIntegrationContract.MatchConfidence.UNMATCHED,
                            references);

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

    private void validateFamilyHubEvent(
            @NonNull TridevIntegrationContract.EventType eventType,
            @NonNull TridevIntegrationContract.Direction direction) {
        if (eventType == TridevIntegrationContract.EventType.GROCERY_PURCHASE) {
            if (direction != TridevIntegrationContract.Direction.DEBIT) {
                throw new IllegalArgumentException(
                        "Family Hub grocery purchase must be a debit");
            }
            return;
        }
        if (eventType == TridevIntegrationContract.EventType.EXPENSE) {
            if (direction != TridevIntegrationContract.Direction.DEBIT) {
                throw new IllegalArgumentException(
                        "Family Hub expense must be a debit");
            }
            return;
        }
        if (eventType == TridevIntegrationContract.EventType.INCOME) {
            if (direction != TridevIntegrationContract.Direction.CREDIT) {
                throw new IllegalArgumentException(
                        "Family Hub income must be a credit");
            }
            return;
        }
        throw new IllegalArgumentException(
                "Unsupported Family Hub finance event type");
    }

    @NonNull
    private TridevIntegrationContract.Scope familyScope(
            @NonNull TridevIntegrationContract.EventType eventType,
            @NonNull String scopeValue) {
        // STEP 8 grocery events predate the explicit scope field and are always
        // family-domain source events.
        if (eventType == TridevIntegrationContract.EventType.GROCERY_PURCHASE) {
            return TridevIntegrationContract.Scope.FAMILY;
        }
        if ("PERSONAL".equalsIgnoreCase(scopeValue)) {
            return TridevIntegrationContract.Scope.PERSONAL;
        }
        if ("FAMILY".equalsIgnoreCase(scopeValue)) {
            return TridevIntegrationContract.Scope.FAMILY;
        }
        throw new IllegalArgumentException(
                "Family Finance visibility scope is required");
    }

    @NonNull
    private Bundle accountCatalog(@NonNull Context context) {
        try {
            TridevMoneyMappingEngine.Catalog catalog =
                    new TridevMoneyMappingEngine(context).readCatalog();
            ArrayList<String> refs = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();

            for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
                if (item == null || item.unavailableForNewPosting) continue;
                refs.add(item.canonicalRef);
                labels.add(accountLabel(item));
            }
            for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
                if (item == null || item.unavailableForNewPosting) continue;
                refs.add(item.canonicalRef);
                labels.add(accountLabel(item));
            }

            Bundle result = response("OK", "", null, null,
                    refs.isEmpty()
                            ? "No active MoneyManager account/card is available"
                            : "Active MoneyManager account catalog");
            result.putStringArrayList(RESULT_ACCOUNT_REFS, refs);
            result.putStringArrayList(RESULT_ACCOUNT_LABELS, labels);
            return result;
        } catch (RuntimeException unavailable) {
            return response("FAILED", "", null, null,
                    "MoneyManager account catalog is unavailable");
        }
    }

    @NonNull
    private String accountLabel(@NonNull TridevMoneyMappingEngine.CatalogItem item) {
        String label = safe(item.displayName);
        String type = safe(item.type);
        if (!type.isEmpty()) label += " • " + type;
        return label.length() <= 120 ? label : label.substring(0, 120).trim();
    }

    @NonNull
    private Bundle cancelFamilyHubGrocery(
            @NonNull Context context,
            @Nullable Bundle extras) {
        if (extras == null) {
            return response("REJECTED", "", null, null,
                    "Cancellation payload is missing");
        }
        try {
            String eventId = structured(extras.getString(KEY_EVENT_ID), 120, false);
            String sourceRecordId = structured(
                    extras.getString(KEY_SOURCE_RECORD_ID), 160, false);
            TridevFamilyHubCancellationManager.Result result =
                    new TridevFamilyHubCancellationManager(context)
                            .cancelGroceryPurchase(eventId, sourceRecordId);
            return response(
                    result.handled ? "CANCELLED" : "REJECTED",
                    eventId,
                    null,
                    null,
                    result.reason);
        } catch (RuntimeException invalidPayload) {
            return response("REJECTED", "", null, null,
                    "Cancellation failed validation");
        }
    }

    private CallerKind trustedCaller(@NonNull Context context) {
        int callingUid = Binder.getCallingUid();
        PackageManager packageManager = context.getPackageManager();
        String[] packages = packageManager.getPackagesForUid(callingUid);
        if (packages == null || packages.length == 0) return CallerKind.NONE;

        boolean smartSmsPackage = false;
        boolean familyHubPackage = false;
        for (String packageName : packages) {
            if (TRUSTED_SMART_SMS_PACKAGE.equals(packageName)) {
                smartSmsPackage = true;
            } else if (TRUSTED_FAMILY_HUB_PACKAGE.equals(packageName)) {
                familyHubPackage = true;
            }
        }

        if (smartSmsPackage) {
            String defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context);
            if (TRUSTED_SMART_SMS_PACKAGE.equals(defaultSmsPackage)) {
                return CallerKind.SMART_SMS;
            }
        }

        if (familyHubPackage) {
            int signatureResult = packageManager.checkSignatures(
                    callingUid,
                    Process.myUid());
            if (signatureResult == PackageManager.SIGNATURE_MATCH) {
                return CallerKind.FAMILY_HUB;
            }
        }

        return CallerKind.NONE;
    }

    private TridevIntegrationContract.EventType safeEventType(
            String value,
            CallerKind caller) {
        try {
            TridevIntegrationContract.EventType type =
                    TridevIntegrationContract.EventType.valueOf(value);
            if (caller == CallerKind.FAMILY_HUB) {
                if (type == TridevIntegrationContract.EventType.GROCERY_PURCHASE
                        || type == TridevIntegrationContract.EventType.INCOME
                        || type == TridevIntegrationContract.EventType.EXPENSE) {
                    return type;
                }
                throw new IllegalArgumentException(
                        "Unsupported Family Hub finance event type");
            }
            switch (type) {
                case SMS_FINANCIAL_SIGNAL:
                case LOAN_PAYMENT:
                case TRANSFER:
                case REFUND:
                    return type;
                default:
                    return TridevIntegrationContract.EventType.SMS_FINANCIAL_SIGNAL;
            }
        } catch (IllegalArgumentException invalid) {
            if (caller == CallerKind.FAMILY_HUB) {
                throw invalid;
            }
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
