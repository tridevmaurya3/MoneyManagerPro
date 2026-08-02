package com.example.moneymanagerpro.notification;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts the best title and body that Android made available to a
 * NotificationListenerService. It supports standard, big-text and
 * MessagingStyle notifications without requesting SMS permissions.
 */
public final class NotificationTextExtractor {

    private NotificationTextExtractor() {
    }

    @NonNull
    public static Result extract(@NonNull Notification notification) {
        Result primary = extractSingle(notification);

        Notification publicVersion = notification.publicVersion;
        if (publicVersion == null || publicVersion == notification) {
            return primary;
        }

        Result publicResult = extractSingle(publicVersion);

        String title = chooseBetter(primary.title, publicResult.title, false);
        String body = chooseBetter(primary.body, publicResult.body, true);
        boolean redacted = isRedactedText(body)
                || (body.isEmpty() && (primary.redacted || publicResult.redacted));

        return new Result(title, body, redacted);
    }

    @NonNull
    private static Result extractSingle(@NonNull Notification notification) {
        Bundle extras = notification.extras == null
                ? Bundle.EMPTY
                : notification.extras;

        List<String> titleCandidates = new ArrayList<>();
        List<String> bodyCandidates = new ArrayList<>();

        addMessagingMessages(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES),
                titleCandidates,
                bodyCandidates
        );
        addMessagingMessages(
                extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES),
                titleCandidates,
                bodyCandidates
        );

        add(titleCandidates, extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        add(titleCandidates, extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
        add(titleCandidates, extras.getCharSequence(Notification.EXTRA_TITLE));
        add(titleCandidates, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));

        add(bodyCandidates, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        addLines(bodyCandidates, extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES));
        add(bodyCandidates, extras.getCharSequence(Notification.EXTRA_TEXT));
        add(bodyCandidates, extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
        add(bodyCandidates, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        addLines(bodyCandidates, extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY));

        String title = firstUseful(titleCandidates, false);
        String body = firstUseful(bodyCandidates, true);
        boolean redacted = containsRedactedCandidate(bodyCandidates)
                && (body.isEmpty() || isRedactedText(body));

        return new Result(title, body, redacted);
    }

    private static void addMessagingMessages(
            @Nullable Parcelable[] bundles,
            @NonNull List<String> titles,
            @NonNull List<String> bodies
    ) {
        if (bundles == null || bundles.length == 0) return;

        try {
            List<Notification.MessagingStyle.Message> messages =
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);

            for (int index = messages.size() - 1; index >= 0; index--) {
                Notification.MessagingStyle.Message message = messages.get(index);
                if (message == null) continue;

                add(bodies, message.getText());
                add(titles, message.getSender());
            }
        } catch (Exception ignored) {
            // Some OEM notification bundles are incomplete. Standard fields
            // below remain available as a fallback.
        }
    }

    private static void addLines(
            @NonNull List<String> values,
            @Nullable CharSequence[] lines
    ) {
        if (lines == null || lines.length == 0) return;

        StringBuilder combined = new StringBuilder();
        for (CharSequence line : lines) {
            String value = normalize(line);
            if (value.isEmpty()) continue;
            if (combined.length() > 0) combined.append(' ');
            combined.append(value);
        }

        if (combined.length() > 0) values.add(combined.toString());
    }

    private static void add(
            @NonNull List<String> values,
            @Nullable CharSequence value
    ) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) values.add(normalized);
    }

    @NonNull
    private static String firstUseful(
            @NonNull List<String> candidates,
            boolean skipRedacted
    ) {
        for (String candidate : candidates) {
            if (candidate.isEmpty()) continue;
            if (skipRedacted && isRedactedText(candidate)) continue;
            return candidate;
        }

        for (String candidate : candidates) {
            if (!candidate.isEmpty()) return candidate;
        }
        return "";
    }

    @NonNull
    private static String chooseBetter(
            @Nullable String first,
            @Nullable String second,
            boolean skipRedacted
    ) {
        String firstValue = first == null ? "" : first.trim();
        String secondValue = second == null ? "" : second.trim();

        if (!firstValue.isEmpty()
                && (!skipRedacted || !isRedactedText(firstValue))) {
            return firstValue;
        }

        if (!secondValue.isEmpty()
                && (!skipRedacted || !isRedactedText(secondValue))) {
            return secondValue;
        }

        return !firstValue.isEmpty() ? firstValue : secondValue;
    }

    private static boolean containsRedactedCandidate(@NonNull List<String> candidates) {
        for (String candidate : candidates) {
            if (isRedactedText(candidate)) return true;
        }
        return false;
    }

    public static boolean isRedactedText(@Nullable String value) {
        String lower = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);

        return lower.equals("sensitive notification content hidden")
                || lower.contains("sensitive content hidden")
                || lower.contains("notification content hidden")
                || lower.contains("message content hidden")
                || lower.contains("content hidden for privacy")
                || lower.contains("hidden for your privacy")
                || lower.contains("unlock to view content");
    }

    @NonNull
    private static String normalize(@Nullable CharSequence value) {
        if (value == null) return "";
        return value.toString()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static final class Result {
        @NonNull
        public final String title;

        @NonNull
        public final String body;

        public final boolean redacted;

        Result(
                @NonNull String title,
                @NonNull String body,
                boolean redacted
        ) {
            this.title = title;
            this.body = body;
            this.redacted = redacted;
        }
    }
}
