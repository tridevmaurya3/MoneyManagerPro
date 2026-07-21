package com.example.moneymanagerpro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.moneymanagerpro.model.InvestmentItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InvestmentStore {

    private static final String PREFS_NAME = "investment_tracker_storage";
    private static final String KEY_INVESTMENTS = "saved_investments";

    private InvestmentStore() {
    }

    public static List<InvestmentItem> getAll(Context context) {
        List<InvestmentItem> investments = new ArrayList<>();

        try {
            SharedPreferences preferences = context.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
            );

            String savedData = preferences.getString(KEY_INVESTMENTS, "[]");
            JSONArray jsonArray = new JSONArray(savedData);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject object = jsonArray.getJSONObject(i);

                InvestmentItem item = new InvestmentItem();

                item.setId(object.optString("id"));
                item.setName(object.optString("name"));
                item.setType(object.optString("type"));
                item.setStartDate(object.optString("startDate"));
                item.setNote(object.optString("note"));

                item.setInvestedAmount(object.optDouble("investedAmount", 0));
                item.setCurrentValue(object.optDouble("currentValue", 0));
                item.setMonthlyContribution(
                        object.optDouble("monthlyContribution", 0)
                );

                item.setCreatedAt(object.optLong("createdAt", 0));

                investments.add(item);
            }

        } catch (Exception ignored) {
        }

        Collections.sort(investments, (first, second) ->
                Long.compare(second.getCreatedAt(), first.getCreatedAt())
        );

        return investments;
    }

    public static void add(Context context, InvestmentItem item) {
        List<InvestmentItem> investments = getAll(context);

        item.setId(UUID.randomUUID().toString());
        item.setCreatedAt(System.currentTimeMillis());

        investments.add(item);

        saveAll(context, investments);
    }

    public static void update(Context context, InvestmentItem updatedItem) {
        List<InvestmentItem> investments = getAll(context);

        for (int i = 0; i < investments.size(); i++) {
            if (investments.get(i).getId().equals(updatedItem.getId())) {
                investments.set(i, updatedItem);
                break;
            }
        }

        saveAll(context, investments);
    }

    public static void delete(Context context, String investmentId) {
        List<InvestmentItem> investments = getAll(context);

        for (int i = investments.size() - 1; i >= 0; i--) {
            if (investments.get(i).getId().equals(investmentId)) {
                investments.remove(i);
            }
        }

        saveAll(context, investments);
    }

    private static void saveAll(
            Context context,
            List<InvestmentItem> investments
    ) {
        JSONArray jsonArray = new JSONArray();

        try {
            for (InvestmentItem item : investments) {
                JSONObject object = new JSONObject();

                object.put("id", item.getId());
                object.put("name", item.getName());
                object.put("type", item.getType());
                object.put("startDate", item.getStartDate());
                object.put("note", item.getNote());

                object.put("investedAmount", item.getInvestedAmount());
                object.put("currentValue", item.getCurrentValue());
                object.put(
                        "monthlyContribution",
                        item.getMonthlyContribution()
                );

                object.put("createdAt", item.getCreatedAt());

                jsonArray.put(object);
            }

        } catch (Exception ignored) {
        }

        context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                ).edit()
                .putString(KEY_INVESTMENTS, jsonArray.toString())
                .apply();
    }
}