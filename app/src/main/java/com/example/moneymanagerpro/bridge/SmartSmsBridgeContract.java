package com.example.moneymanagerpro.bridge;

public final class SmartSmsBridgeContract {

    public static final String ACTION_REVIEW_TRANSACTION =
            "com.example.moneymanagerpro.action.REVIEW_SMART_SMS_TRANSACTION";

    public static final String TRUSTED_SOURCE_PACKAGE = "com.tridev.smartsmspro";

    public static final String EXTRA_SOURCE_PACKAGE = "bridge_source_package";
    public static final String EXTRA_FINGERPRINT = "bridge_fingerprint";
    public static final String EXTRA_SMS_ID = "bridge_sms_id";
    public static final String EXTRA_THREAD_ID = "bridge_thread_id";
    public static final String EXTRA_DIRECTION = "bridge_direction";
    public static final String EXTRA_AMOUNT = "bridge_amount";
    public static final String EXTRA_SENDER = "bridge_sender";
    public static final String EXTRA_TIMESTAMP = "bridge_timestamp";
    public static final String EXTRA_METHOD = "bridge_method";
    public static final String EXTRA_BODY = "bridge_body";
    public static final String EXTRA_CATEGORY = "bridge_category";
    public static final String EXTRA_REFERENCE = "bridge_reference";

    public static final String DIRECTION_DEBIT = "DEBIT";
    public static final String DIRECTION_CREDIT = "CREDIT";

    private SmartSmsBridgeContract() {
    }
}
