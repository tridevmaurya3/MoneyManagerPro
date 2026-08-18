package com.example.moneymanagerpro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/** Trusted endpoint used only to correct an already-linked Family Hub ledger row. */
public final class TridevFamilyHubEditProvider extends ContentProvider {

    public static final String AUTHORITY =
            "com.example.moneymanagerpro.tridev.familyhubedit";
    public static final String METHOD_UPDATE = "update_family_event_v1";

    @Override
    public boolean onCreate() {
        return getContext() != null;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg,
                       @Nullable Bundle extras) {
        Context context = getContext();
        if (context == null) return response("FAILED", "", "MoneyManager context is unavailable");
        if (!TridevCompanionTrust.verifyCaller(
                context, Binder.getCallingUid(), TridevCompanionTrust.FAMILY_HUB_PACKAGE)) {
            return response("REJECTED", "", "Family Hub caller is not trusted");
        }
        if (!METHOD_UPDATE.equals(method)) {
            return response("REJECTED", "", "Unsupported Family Hub edit request");
        }
        return updateFamily(context, extras);
    }

    @NonNull
    private Bundle updateFamily(@NonNull Context context, @Nullable Bundle extras) {
        if (extras == null) return response("REJECTED", "", "Family Hub edit payload is missing");
        try {
            String canonicalEventId = structured(
                    extras.getString("canonical_event_id"), 120, false);
            String canonicalSource = structured(
                    extras.getString("canonical_source_record_id"), 160, false);
            String eventTypeValue = structured(extras.getString("event_type"), 40, false);
            String directionValue = structured(extras.getString("direction"), 20, false);
            String scopeValue = structured(extras.getString("scope"), 20, false);
            long amountMinor = extras.getLong("amount_minor", 0L);
            long occurredAt = extras.getLong("occurred_at", 0L);
            String accountHint = metadata(extras.getString("account_hint"), 160);
            String merchantHint = metadata(extras.getString("merchant_hint"), 120);
            String categoryHint = metadata(extras.getString("category_hint"), 80);
            String fingerprint = structured(extras.getString("fingerprint"), 160, true);

            if (amountMinor <= 0L || occurredAt <= 0L
                    || accountHint.isEmpty() || categoryHint.isEmpty()) {
                throw new IllegalArgumentException();
            }

            TridevIntegrationContract.EventType eventType = familyType(eventTypeValue);
            TridevIntegrationContract.Direction direction =
                    TridevIntegrationContract.Direction.valueOf(
                            directionValue.toUpperCase(Locale.ROOT));
            if (eventType == TridevIntegrationContract.EventType.INCOME) {
                if (direction != TridevIntegrationContract.Direction.CREDIT) {
                    throw new IllegalArgumentException();
                }
            } else if (direction != TridevIntegrationContract.Direction.DEBIT) {
                throw new IllegalArgumentException();
            }

            TridevIntegrationContract.Scope scope;
            if (eventType == TridevIntegrationContract.EventType.GROCERY_PURCHASE) {
                scope = TridevIntegrationContract.Scope.FAMILY;
            } else if ("FAMILY".equalsIgnoreCase(scopeValue)) {
                scope = TridevIntegrationContract.Scope.FAMILY;
            } else if ("PERSONAL".equalsIgnoreCase(scopeValue)) {
                scope = TridevIntegrationContract.Scope.PERSONAL;
            } else {
                throw new IllegalArgumentException();
            }

            boolean grocery = eventType == TridevIntegrationContract.EventType.GROCERY_PURCHASE;
            TridevIntegrationContract.References references =
                    new TridevIntegrationContract.References(
                            "", "", "",
                            grocery ? "" : canonicalSource,
                            grocery ? canonicalSource : "",
                            "", "");

            // The incoming payload may have a newer Family Hub version identity,
            // but the canonical queue/ledger marker intentionally stays unchanged.
            TridevIntegrationContract.Event updated = new TridevIntegrationContract.Event(
                    canonicalEventId,
                    TridevIntegrationContract.APP_FAMILY_HUB,
                    canonicalSource,
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
                    TridevIntegrationContract.SyncState.PENDING,
                    TridevIntegrationContract.MatchConfidence.UNMATCHED,
                    references);

            TridevFamilyHubEditManager.Result result =
                    new TridevFamilyHubEditManager(context)
                            .updateFamilyEvent(canonicalEventId, canonicalSource, updated);
            if (result.updated) {
                return response("UPDATED", result.transactionId, result.reason);
            }
            if (result.preserved) {
                return response("PRESERVED", result.transactionId, result.reason);
            }
            return response(result.handled ? "NEEDS_REVIEW" : "REJECTED",
                    result.transactionId, result.reason);
        } catch (RuntimeException invalid) {
            return response("REJECTED", "", "Family Hub edit failed validation");
        }
    }

    @NonNull
    private TridevIntegrationContract.EventType familyType(@NonNull String value) {
        TridevIntegrationContract.EventType type =
                TridevIntegrationContract.EventType.valueOf(value.toUpperCase(Locale.ROOT));
        if (type == TridevIntegrationContract.EventType.GROCERY_PURCHASE
                || type == TridevIntegrationContract.EventType.EXPENSE
                || type == TridevIntegrationContract.EventType.INCOME) {
            return type;
        }
        throw new IllegalArgumentException();
    }

    @NonNull
    private Bundle response(@NonNull String status, @Nullable String transactionId,
                            @NonNull String reason) {
        Bundle result = new Bundle();
        result.putString("status", status);
        result.putString("transaction_id", clean(transactionId));
        result.putString("reason", limit(clean(reason).replace('\n', ' ').replace('\r', ' '), 240));
        return result;
    }

    @NonNull
    private String structured(@Nullable String value, int max, boolean optional) {
        String clean = clean(value);
        if (!optional && clean.isEmpty()) throw new IllegalArgumentException();
        if (clean.length() > max || clean.indexOf('\n') >= 0 || clean.indexOf('\r') >= 0) {
            throw new IllegalArgumentException();
        }
        return clean;
    }

    @NonNull
    private String metadata(@Nullable String value, int max) {
        return limit(clean(value).replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " "), max);
    }

    @NonNull
    private String limit(@NonNull String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    @NonNull
    private String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
            @Nullable String selection, @Nullable String[] selectionArgs,
            @Nullable String sortOrder) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values,
            @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
