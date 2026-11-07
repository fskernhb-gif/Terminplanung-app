package de.fahrstunden.zeiten;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {

    private LinearLayout peopleBox;
    private ArrayList<Person> people = new ArrayList<>();
    private final String[] DAYS = {"Montag","Dienstag","Mittwoch","Donnerstag","Freitag","Samstag"};

    static class Person {
        String name;
        HashMap<String,String> times = new HashMap<>();

        Person(String name) {
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadData();
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12,12,12,12);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button add = new Button(this);
        add.setText("+ Person");
        add.setOnClickListener(v -> addPerson());

        Button text = new Button(this);
        text.setText("Text eingeben");
        text.setOnClickListener(v -> textInput());

        Button voice = new Button(this);
        voice.setText("🎤 Stimme");
        voice.setOnClickListener(v -> voiceInput());

        Button backup = new Button(this);
        backup.setText("Backup");
        backup.setOnClickListener(v -> backup());

        Button pdf = new Button(this);
        pdf.setText("PDF / Bild");
        pdf.setOnClickListener(v -> exportOverview());

        buttons.addView(add);
        buttons.addView(text);
        buttons.addView(voice);
        buttons.addView(backup);
        buttons.addView(pdf);

        root.addView(buttons);

        ScrollView scroll = new ScrollView(this);

        peopleBox = new LinearLayout(this);
        peopleBox.setOrientation(LinearLayout.VERTICAL);
        peopleBox.setPadding(4,8,4,8);

        scroll.addView(peopleBox);
        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1
        ));

        setContentView(root);

        refreshPeople();
    }

    private void refreshPeople() {
        peopleBox.removeAllViews();

        for (int i = 0; i < people.size(); i++) {
            Person p = people.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8,4,8,4);

            TextView name = new TextView(this);
            name.setText(p.name);
            name.setTextSize(18);
            name.setGravity(Gravity.CENTER_VERTICAL);

            row.addView(name, new LinearLayout.LayoutParams(
                    0, 60, 1
            ));

            Button edit = new Button(this);
            edit.setText("Bearbeiten");
            edit.setOnClickListener(v -> editPerson(p));

            Button delete = new Button(this);
            delete.setText("×");
            delete.setOnClickListener(v -> {
                people.remove(p);
                saveData();
                refreshPeople();
            });

            row.addView(edit);
            row.addView(delete);

            peopleBox.addView(row);
        }

        TextView title = new TextView(this);
        title.setText("\nWochenübersicht – Montag bis Samstag");
        title.setTextSize(18);
        title.setPadding(8,12,8,12);
        peopleBox.addView(title);

        addSchedule();
    }

    private void addPerson() {
        final EditText input = new EditText(this);
        input.setHint("Name der Person");

        new AlertDialog.Builder(this)
                .setTitle("Person hinzufügen")
                .setView(input)
                .setPositiveButton("Speichern", (d,w) -> {
                    String name = input.getText().toString().trim();

                    if (!name.isEmpty()) {
                        people.add(new Person(name));
                        saveData();
                        refreshPeople();
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void editPerson(Person p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20,10,20,10);

        EditText name = new EditText(this);
        name.setText(p.name);
        name.setHint("Name");

        box.addView(name);

        for (String day : DAYS) {
            EditText time = new EditText(this);
            time.setHint(day + " – z.B. 16:00-23:30");
            time.setText(p.times.getOrDefault(day,""));
            box.addView(time);
        }

        new AlertDialog.Builder(this)
                .setTitle("Person bearbeiten")
                .setView(box)
                .setPositiveButton("Speichern", (d,w) -> {
                    p.name = name.getText().toString().trim();

                    for (int i = 0; i < DAYS.length; i++) {
                        EditText e = (EditText) box.getChildAt(i + 1);
                        String value = e.getText().toString().trim();

                        if (!value.isEmpty()) {
                            p.times.put(DAYS[i], normalizeTime(value));
                        }
                    }

                    saveData();
                    refreshPeople();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private String normalizeTime(String value) {
        value = value.replace(" ","");

        if (value.matches("\\d{1,2}:\\d{2}")) {
            return value + "-23:30";
        }

        if (value.matches("\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}")) {
            return value;
        }

        if (value.toLowerCase().startsWith("ab")) {
            String t = value.substring(2);
            return t + "-23:30";
        }

        return value;
    }

    private void textInput() {
        EditText input = new EditText(this);
        input.setHint(
                "Beispiel:\nDaniela\nMontag ab 16:00\nDienstag 16:00-20:00"
        );
        input.setMinLines(8);

        new AlertDialog.Builder(this)
                .setTitle("Verfügbarkeit als Text")
                .setView(input)
                .setPositiveButton("Übernehmen", (d,w) -> {
                    parseText(input.getText().toString());
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void parseText(String text) {
        String[] lines = text.split("\\r?\\n");

        Person current = null;

        for (String raw : lines) {
            String line = raw.trim();

            if (line.isEmpty()) continue;

            String lower = line.toLowerCase();

            boolean dayLine =
                    lower.contains("montag") ||
                    lower.contains("dienstag") ||
                    lower.contains("mittwoch") ||
                    lower.contains("donnerstag") ||
                    lower.contains("freitag") ||
                    lower.contains("samstag");

            if (!dayLine) {
                current = findPerson(line);

                if (current == null) {
                    current = new Person(line);
                    people.add(current);
                }

                continue;
            }

            if (current == null) continue;

            for (String day : DAYS) {
                if (lower.contains(day.toLowerCase())) {
                    String value = line.substring(
                            lower.indexOf(day.toLowerCase()) + day.length()
                    ).trim();

                    value = normalizeTime(value);

                    current.times.put(day,value);
                    break;
                }
            }
        }

        saveData();
        refreshPeople();

        Toast.makeText(
                this,
                "Zeiten übernommen",
                Toast.LENGTH_SHORT
        ).show();
    }

    private Person findPerson(String name) {
        for (Person p : people) {
            if (p.name.equalsIgnoreCase(name)) {
                return p;
            }
        }

        return null;
    }

    private void voiceInput() {
        Intent intent = new Intent(
                android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE,
                Locale.GERMANY
        );

        intent.putExtra(
                android.speech.RecognizerIntent.EXTRA_PROMPT,
                "Person und Zeiten sprechen"
        );

        try {
            startActivityForResult(intent,100);
        } catch(Exception e) {
            Toast.makeText(
                    this,
                    "Spracheingabe nicht verfügbar",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == 100 &&
                resultCode == RESULT_OK &&
                data != null) {

            ArrayList<String> result =
                    data.getStringArrayListExtra(
                            android.speech.RecognizerIntent.EXTRA_RESULTS
                    );

            if (result != null && !result.isEmpty()) {
                parseText(result.get(0));
            }
        }
    }

    private void addSchedule() {
        TextView info = new TextView(this);

        StringBuilder s = new StringBuilder();

        s.append("\n");

        for (Person p : people) {
            s.append(p.name).append("\n");

            for (String day : DAYS) {
                String time = p.times.get(day);

                if (time != null && !time.isEmpty()) {
                    s.append("  ")
                            .append(day)
                            .append(": ")
                            .append(time)
                            .append("\n");
                }
            }

            s.append("\n");
        }

        info.setText(s.toString());
        info.setTextSize(15);

        peopleBox.addView(info);
    }

    private void backup() {
        StringBuilder data = new StringBuilder();

        for (Person p : people) {
            data.append(p.name).append("\n");

            for (String day : DAYS) {
                String t = p.times.get(day);

                if (t != null) {
                    data.append(day)
                            .append(": ")
                            .append(t)
                            .append("\n");
                }
            }

            data.append("\n");
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,data.toString());

        startActivity(Intent.createChooser(
                send,
                "Backup speichern"
        ));
    }

    private void exportOverview() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(20,20,20,20);

        TextView title = new TextView(this);
        title.setText(
                "Fahrstunden-Zeiten\nWochenübersicht"
        );
        title.setTextSize(22);

        view.addView(title);

        for (Person p : people) {
            TextView person = new TextView(this);

            StringBuilder s = new StringBuilder();
            s.append("\n").append(p.name).append("\n");

            for (String day : DAYS) {
                String t = p.times.get(day);

                if (t != null) {
                    s.append(day)
                            .append(": ")
                            .append(t)
                            .append("\n");
                }
            }

            person.setText(s.toString());
            person.setTextSize(15);

            view.addView(person);
        }

        view.measure(
                View.MeasureSpec.makeMeasureSpec(
                        1080,
                        View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                        0,
                        View.MeasureSpec.UNSPECIFIED
                )
        );

        view.layout(
                0,
                0,
                view.getMeasuredWidth(),
                view.getMeasuredHeight()
        );

        Bitmap bitmap = Bitmap.createBitmap(
                view.getMeasuredWidth(),
                view.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        try {
            String fileName =
                    "Fahrstunden-" +
                    System.currentTimeMillis() +
                    ".png";

            OutputStream out;

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();

                values.put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        fileName
                );

                values.put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/png"
                );

                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/Fahrstunden"
                );

                Uri uri =
                        getContentResolver().insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                values
                        );

                out = getContentResolver()
                        .openOutputStream(uri);

            } else {
                File file = new File(
                        getExternalFilesDir(null),
                        fileName
                );

                out = new FileOutputStream(file);
            }

            bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    out
            );

            out.close();

            Toast.makeText(
                    this,
                    "Übersicht als Bild gespeichert",
                    Toast.LENGTH_LONG
            ).show();

        } catch(Exception e) {
            Toast.makeText(
                    this,
                    "Bild konnte nicht gespeichert werden",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void saveData() {
        android.content.SharedPreferences p =
                getSharedPreferences(
                        "fahrstunden",
                        MODE_PRIVATE
                );

        StringBuilder s = new StringBuilder();

        for (Person person : people) {
            s.append(person.name).append("|");

            for (String day : DAYS) {
                s.append(day)
                        .append("=")
                        .append(person.times.getOrDefault(day,""))
                        .append(";");
            }

            s.append("\n");
        }

        p.edit()
                .putString("data",s.toString())
                .apply();
    }

    private void loadData() {
        android.content.SharedPreferences p =
                getSharedPreferences(
                        "fahrstunden",
                        MODE_PRIVATE
                );

        String data = p.getString("data","");

        if (data.isEmpty()) return;

        String[] rows = data.split("\\n");

        for (String row : rows) {
            if (row.trim().isEmpty()) continue;

            String[] parts = row.split("\\|",2);

            if (parts.length != 2) continue;

            Person person = new Person(parts[0]);

            String[] times = parts[1].split(";");

            for (String t : times) {
                String[] pair = t.split("=",2);

                if (pair.length == 2) {
                    person.times.put(
                            pair[0],
                            pair[1]
                    );
                }
            }

            people.add(person);
        }
    }
}
