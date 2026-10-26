package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;

/** Saved conversations, ChatGPT-style: a simple list with a back arrow. */
public final class HistoryActivity extends Activity {
    public static final String EXTRA_CONVERSATION = "conversation";
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);

        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.pageBackground(this));
        root.addView(Ui.bar(this, "History", "Saved on this device", this::finish));
        View rule = new View(this);
        rule.setBackgroundColor(Ui.LINE);
        rule.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        root.addView(rule);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = Ui.column(this);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        JSONArray conversations = prefs.conversations();
        if (conversations.length() == 0) {
            TextView emptyTitle = Ui.text(this, "No saved conversations yet", 17f, Ui.INK);
            emptyTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            list.addView(emptyTitle);
            TextView empty = Ui.text(this,
                    "Start a chat, then tap New chat. Your previous conversation will appear here "
                            + "(stored on this device only).",
                    13f, Ui.MUTED);
            empty.setPadding(0, Ui.dp(this, 6), 0, 0);
            list.addView(empty);
            return;
        }

        for (int index = 0; index < conversations.length(); index++) {
            final JSONObject item = conversations.optJSONObject(index);
            if (item == null) continue;
            LinearLayout card = Ui.cardBox(this);
            card.setLayoutParams(Ui.withTop(this, 8));
            String title = item.optString("title", "Conversation");
            TextView heading = Ui.text(this, title, 15f, Ui.INK);
            heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            card.addView(heading);
            long savedAt = item.optLong("savedAt", 0L);
            int turns = item.optJSONArray("turns") == null ? 0 : item.optJSONArray("turns").length();
            String when = savedAt <= 0 ? "" : DateFormat.getMediumDateFormat(this).format(new Date(savedAt));
            TextView meta = Ui.text(this, when + (when.isEmpty() ? "" : "  \u00B7  ") + turns + " messages",
                    11f, Ui.MUTED);
            meta.setPadding(0, Ui.dp(this, 4), 0, 0);
            card.addView(meta);
            card.setOnClickListener(view -> {
                Intent result = new Intent();
                result.putExtra(EXTRA_CONVERSATION, item.toString());
                setResult(RESULT_OK, result);
                finish();
            });
            list.addView(card);
        }

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 12), 0, 0);
        actions.addView(Ui.textAction(this, "Clear history", () -> confirmClear()));
        list.addView(actions);
    }

    private void confirmClear() {
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Clear history?")
                .setMessage("This removes the saved conversations on this device. Bookmark and notes are not affected.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    prefs.setConversations(new JSONArray());
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
