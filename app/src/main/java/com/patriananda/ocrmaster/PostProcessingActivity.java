package com.patriananda.ocrmaster;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

public class PostProcessingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_postprocessing);

        // default text
        String ocrResult = "";
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            ocrResult = extras.getString("OCR_RESULT");
        }

        TextView myTextView = findViewById(R.id.textBox);
        myTextView.setText(ocrResult);
    }

    public void onClickCopy(View view) {
        EditText editText = findViewById(R.id.textBox);

        if (editText.getText().toString().equals("")) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_LONG).show();
        } else {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("label", editText.getText());
            Objects.requireNonNull(clipboard).setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_LONG).show();
        }
    }

    public void onClickStarOver(View view) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
    }
}
