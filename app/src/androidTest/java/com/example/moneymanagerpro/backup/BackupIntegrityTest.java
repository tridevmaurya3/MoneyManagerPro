package com.example.moneymanagerpro.backup;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BackupIntegrityTest {

    @Test
    public void checksumSurvivesJsonWriteAndRead()
            throws Exception {
        JSONObject backup =
                new JSONObject();

        backup.put(
                "appName",
                "Money Manager Pro"
        );

        JSONArray transactions =
                new JSONArray();

        transactions.put(
                new JSONObject()
                        .put("id", 1)
                        .put("amount", 100.0)
                        .put("fractionalAmount", 125.50)
                        .put("smallAmount", 0.0000001)
        );

        backup.put(
                "transactions",
                transactions
        );

        String checksum =
                BackupIntegrity.calculateSha256(
                        backup
                );

        backup.put(
                "integritySha256",
                checksum
        );

        JSONObject savedAndReadBackup =
                new JSONObject(
                        backup.toString(2)
                );

        assertTrue(
                BackupIntegrity.verify(
                        savedAndReadBackup,
                        savedAndReadBackup.getString(
                                "integritySha256"
                        )
                )
        );
    }
}
