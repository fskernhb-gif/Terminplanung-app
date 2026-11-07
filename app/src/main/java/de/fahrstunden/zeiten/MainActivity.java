package de.fahrstunden.zeiten;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.speech.RecognizerIntent;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.graphics.pdf.PdfDocument;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.net.Uri;
import java.io.OutputStream;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    static final String[] DAYS={"Montag","Dienstag","Mittwoch","Donnerstag","Freitag","Samstag"};
    static final int BLUE=Color.rgb(21,101,192);
    LinearLayout root, peopleBox, gridBox;
    ArrayList<Person> people=new ArrayList<>();
    android.content.SharedPreferences prefs;
    int[] colors={0xff1976d2,0xff2e7d32,0xff8e24aa,0xffef6c00,0xff00838f,0xffc62828,0xff6d4c41,0xff3949ab};

    static class Person {
        String name; HashMap<String,ArrayList<String[]>> times=new HashMap<>();
        Person(String n){name=n;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b); prefs=getSharedPreferences("data",0); load();
        buildUI(); renderAll();
    }

    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.DKGRAY); return t; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(14); b.setAllCaps(false); return b; }

    void buildUI(){
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(4,2,4,8);
        scroll.addView(root);

        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL); head.setBackgroundColor(BLUE); head.setPadding(6,3,6,4);
        TextView title=tv("Fahrstunden-Zeiten",17); title.setTextColor(Color.WHITE); title.setTypeface(null,1); head.addView(title);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add=btn("+ Person"); Button text=btn("Text eingeben"); Button voice=btn("🎤 Stimme"); Button backup=btn("Backup"); Button bild=btn("🖼 Bild"); Button pdf=btn("PDF");
        for(Button x:new Button[]{add,text,voice,backup}) actions.addView(x,new LinearLayout.LayoutParams(0,34,1));
        head.addView(actions);
        LinearLayout exports=new LinearLayout(this); exports.setOrientation(LinearLayout.HORIZONTAL);
        exports.addView(bild,new LinearLayout.LayoutParams(0,32,1));
        exports.addView(pdf,new LinearLayout.LayoutParams(0,32,1));
        head.addView(exports);
        root.addView(head);

        LinearLayout pPanel=new LinearLayout(this); pPanel.setOrientation(LinearLayout.VERTICAL); pPanel.setPadding(4,4,4,3);
        TextView ph=tv("Personen",15); ph.setTypeface(null,1); pPanel.addView(ph);
        peopleBox=new LinearLayout(this); peopleBox.setOrientation(LinearLayout.VERTICAL); pPanel.addView(peopleBox);
        root.addView(pPanel);

        LinearLayout importPanel=new LinearLayout(this); importPanel.setOrientation(LinearLayout.VERTICAL); importPanel.setPadding(4,3,4,3);
        TextView ih=tv("Verfügbarkeit als Text einfügen",15); ih.setTypeface(null,1); importPanel.addView(ih);
        EditText input=new EditText(this); input.setTag("import"); input.setHint("Daniela\nMontag ab 16:00\nDienstag 17:00-19:00"); input.setMinLines(2); input.setMaxLines(3); input.setGravity(Gravity.TOP); importPanel.addView(input);
        LinearLayout ir=new LinearLayout(this); Button take=btn("Übernehmen"); Button clear=btn("Leeren"); ir.addView(take,new LinearLayout.LayoutParams(0,WRAP,1)); ir.addView(clear,new LinearLayout.LayoutParams(0,WRAP,1)); importPanel.addView(ir);
        TextView note=tv("„ab 16:00“ → 16:00–23:30 · vorhandene Namen werden aktualisiert.",10); note.setPadding(8,5,8,5); importPanel.addView(note);
        root.addView(importPanel);

        TextView gh=tv("Wochenübersicht · Mo–Sa · 15-Minuten-Raster",11); gh.setPadding(4,3,4,2); root.addView(gh);
        HorizontalScrollView hsv=new HorizontalScrollView(this); gridBox=new LinearLayout(this); gridBox.setOrientation(LinearLayout.VERTICAL); hsv.addView(gridBox); root.addView(hsv);

        add.setOnClickListener(v->showAddDialog());
        text.setOnClickListener(v->{input.requestFocus(); input.postDelayed(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT),100);});
        take.setOnClickListener(v->importText(input.getText().toString()));
        clear.setOnClickListener(v->input.setText(""));
        voice.setOnClickListener(v->startVoice(input));
        backup.setOnClickListener(v->backup());
        bild.setOnClickListener(v->saveOverviewImage());
        pdf.setOnClickListener(v->saveOverviewPdf());
        setContentView(scroll);
    }

    void showAddDialog(){
        final EditText e=new EditText(this); e.setHint("Name");
        new AlertDialog.Builder(this).setTitle("Person hinzufügen").setView(e)
          .setNegativeButton("Abbrechen",null).setPositiveButton("Hinzufügen",(d,w)->{
              String n=e.getText().toString().trim(); if(n.isEmpty())return;
              if(find(n)==null){people.add(new Person(n));save();renderAll();}
          }).show();
    }
    void showEditDialog(int index){
        final EditText e=new EditText(this); e.setText(people.get(index).name); e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Person bearbeiten").setView(e)
          .setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
              String n=e.getText().toString().trim(); if(n.isEmpty())return;
              Person other=find(n); if(other!=null && other!=people.get(index)){toast("Name bereits vorhanden.");return;}
              people.get(index).name=n; save(); renderAll();
          }).setNeutralButton("Löschen",(d,w)->{
              people.remove(index); save(); renderAll();
          }).show();
    }

    Person find(String n){ for(Person p:people) if(p.name.equalsIgnoreCase(n.trim())) return p; return null; }

    void startVoice(EditText target){
        try{
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-DE");
            startActivityForResult(i,77);
        }catch(Exception e){toast("Spracherkennung ist auf diesem Gerät nicht verfügbar.");}
    }
    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==77&&c==RESULT_OK&&d!=null){ArrayList<String> a=d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if(a!=null&&!a.isEmpty()) importText(a.get(0));}
    }

    void importText(String raw){
        if(raw==null||raw.trim().isEmpty())return;
        String[] lines=raw.replace("\r","").split("\n");
        Person cur=null; int changed=0;
        for(String line:lines){
            line=line.trim(); if(line.isEmpty())continue;
            String[] colon=line.split(":",2);
            if(colon.length==2 && day(colon[0])==null && range(colon[0])==null && validName(clean(colon[0]))){
                cur=get(clean(colon[0])); line=colon[1];
            }
            String d=day(line); String[] r=range(line);
            if(d!=null&&r!=null){ if(cur!=null){add(cur,d,r);changed++;} continue; }
            if(validName(clean(line))) cur=get(clean(line));
        }
        save(); renderAll(); toast(changed>0?changed+" Zeit(en) übernommen.":"Keine Zeitangabe erkannt.");
    }
    String clean(String s){
        s=s.replaceFirst("(?i)^\\s*(person|name)\\s*[:\\-]?\\s*","");
        s=s.replaceFirst("^\\s*[+\\-]\\s*","");
        s=s.replaceFirst("(?i)\\s+(hinzufügen|hinzufuegen|add|neu)\\s*$","");
        return s.trim();
    }
    boolean validName(String s){return s.length()>0 && day(s)==null && range(s)==null && !s.matches("(?i)(person|name|ab|hinzufügen|hinzufuegen)");}
    String day(String s){
        String l=s.toLowerCase(Locale.GERMAN);
        String[][] a={{"montag","Montag"},{"mo","Montag"},{"dienstag","Dienstag"},{"di","Dienstag"},{"mittwoch","Mittwoch"},{"mi","Mittwoch"},{"donnerstag","Donnerstag"},{"do","Donnerstag"},{"freitag","Freitag"},{"fr","Freitag"},{"samstag","Samstag"},{"sa","Samstag"}};
        for(String[] x:a) if(l.matches(".*\\b"+x[0]+"\\b.*")) return x[1]; return null;
    }
    String[] range(String s){
        s=s.replace('–','-').replace('—','-');
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{1,2}(?::\\d{2})?)\\s*-\\s*(\\d{1,2}(?::\\d{2})?)").matcher(s);
        if(m.find())return new String[]{time(m.group(1)),time(m.group(2))};
        m=java.util.regex.Pattern.compile("(?i)\\bab\\s*(\\d{1,2}(?::\\d{2})?)").matcher(s);
        if(m.find())return new String[]{time(m.group(1)),"23:30"};
        m=java.util.regex.Pattern.compile("\\b(\\d{1,2}(?::\\d{2})?)\\b").matcher(s);
        if(m.find())return new String[]{time(m.group(1)),"23:30"};
        return null;
    }
    String time(String s){String[] a=s.split(":");return String.format(Locale.US,"%02d:%02d",Math.min(23,Integer.parseInt(a[0])),a.length>1?Integer.parseInt(a[1]):0);}
    Person get(String n){Person p=find(n);if(p==null){p=new Person(n);people.add(p);}return p;}
    void add(Person p,String d,String[] r){if(!p.times.containsKey(d))p.times.put(d,new ArrayList<>());for(String[] x:p.times.get(d))if(x[0].equals(r[0])&&x[1].equals(r[1]))return;p.times.get(d).add(r);}

    void renderAll(){renderPeople();renderGrid();}
    void renderPeople(){
        peopleBox.removeAllViews();
        for(int i=0;i<people.size();i++){
            final int idx=i; Person p=people.get(i);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(2,1,2,1);
            TextView dot=tv("●",13);dot.setTextColor(colors[i%colors.length]);row.addView(dot);
            TextView name=tv(p.name,14);name.setTypeface(null,1);row.addView(name,new LinearLayout.LayoutParams(0,WRAP,1));
            Button edit=btn("Bearbeiten"); edit.setMinHeight(30); edit.setMinWidth(0);row.addView(edit);edit.setOnClickListener(v->showEditDialog(idx));
            peopleBox.addView(row);
        }
    }
    int WRAP=LinearLayout.LayoutParams.WRAP_CONTENT;
    void renderGrid(){
        gridBox.removeAllViews();
        LinearLayout head=new LinearLayout(this); addCell(head,"Zeit",54,true);for(String d:DAYS)addCell(head,d.substring(0,2),72,true);gridBox.addView(head);
        ArrayList<LinearLayout> rows=new ArrayList<>();
        for(int m=420;m<=1410;m+=15){
            LinearLayout row=new LinearLayout(this);TextView t=cell(String.format(Locale.US,"%02d:%02d",m/60,m%60),54);row.addView(t);
            for(int j=0;j<6;j++)row.addView(cell("",72));gridBox.addView(row);rows.add(row);
        }
        for(int pi=0;pi<people.size();pi++){Person p=people.get(pi);for(int di=0;di<6;di++){ArrayList<String[]> rs=p.times.get(DAYS[di]);if(rs==null)continue;for(String[] x:rs){int a=Math.max(420,mins(x[0])),b=Math.min(1410,mins(x[1]));if(b<=a)continue;int f=(a-420)/15,l=(int)Math.ceil((b-420)/15.0)-1;TextView bar=cell(p.name,74);bar.setTextColor(Color.WHITE);bar.setBackgroundColor(colors[pi%colors.length]);bar.setTextSize(7);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(2,0,2,0);bar.setMinHeight(Math.max(8,(l-f+1)*12-2));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(80,Math.max(8,(l-f+1)*12-2));lp.gravity=Gravity.TOP;((LinearLayout)rows.get(f).getChildAt(di+1)).addView(bar,lp);}}}}
    TextView cell(String s,int w){TextView t=tv(s,10);t.setGravity(Gravity.CENTER);t.setPadding(2,2,2,2);t.setBackgroundColor(0xfff7f9fb);t.setMinHeight(12);t.setLayoutParams(new LinearLayout.LayoutParams(w,12));return t;}
    void addCell(LinearLayout r,String s,int w,boolean bold){TextView t=tv(s,10);t.setTextColor(Color.DKGRAY);if(bold)t.setTypeface(null,1);t.setGravity(Gravity.CENTER);t.setBackgroundColor(0xffe9eef4);r.addView(t,new LinearLayout.LayoutParams(w,24));}
    int mins(String s){String[] a=s.split(":");return Integer.parseInt(a[0])*60+Integer.parseInt(a[1]);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}


    void prepareOverview(){
        // Only the weekly overview is exported; input fields and controls are excluded.
        gridBox.measure(View.MeasureSpec.makeMeasureSpec(gridBox.getChildCount()>0 ? gridBox.getChildAt(0).getMeasuredWidth() : 486, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        gridBox.layout(0,0,gridBox.getMeasuredWidth(),gridBox.getMeasuredHeight());
    }

    Bitmap overviewBitmap(){
        prepareOverview();
        int w=Math.max(1,gridBox.getWidth()), h=Math.max(1,gridBox.getHeight());
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b); c.drawColor(Color.WHITE); gridBox.draw(c);
        return b;
    }

    void saveOverviewImage(){
        try{
            Bitmap b=overviewBitmap();
            String name="Fahrstunden-Uebersicht-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmm",Locale.GERMANY).format(new Date())+".png";
            ContentValues v=new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME,name);
            v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Fahrstunden");
            Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
            if(u==null) throw new Exception("Speicherziel konnte nicht erstellt werden");
            OutputStream out=getContentResolver().openOutputStream(u);
            b.compress(Bitmap.CompressFormat.PNG,100,out); out.close(); b.recycle();
            toast("Übersicht als Bild in Bilder/Fahrstunden gespeichert.");
        }catch(Exception e){toast("Bild konnte nicht gespeichert werden: "+e.getMessage());}
    }

    void saveOverviewPdf(){
        PdfDocument doc=new PdfDocument();
        try{
            Bitmap b=overviewBitmap();
            final int pageW=842, pageH=595; // A4 landscape at 72 dpi
            final int margin=24, contentW=pageW-2*margin, contentH=pageH-2*margin-24;
            float scale=Math.min(1f*contentW/b.getWidth(),1f*contentH/b.getHeight());
            int drawW=Math.max(1,(int)(b.getWidth()*scale));
            int drawH=Math.max(1,(int)(b.getHeight()*scale));
            int pages=Math.max(1,(int)Math.ceil((double)drawH/contentH));
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            for(int i=0;i<pages;i++){
                PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(pageW,pageH,i+1).create();
                PdfDocument.Page page=doc.startPage(info);
                Canvas c=page.getCanvas(); c.drawColor(Color.WHITE);
                int srcTop=(int)((i*contentH)/scale);
                int srcBottom=Math.min(b.getHeight(),(int)(((i+1)*contentH)/scale));
                android.graphics.Rect src=new android.graphics.Rect(0,srcTop,b.getWidth(),srcBottom);
                android.graphics.RectF dstRect=new android.graphics.RectF(margin,margin+20,margin+drawW,margin+20+(srcBottom-srcTop)*scale);
                paint.setFilterBitmap(true); c.drawBitmap(b,src,dstRect,paint);
                Paint tp=new Paint(Paint.ANTI_ALIAS_FLAG); tp.setColor(Color.DKGRAY); tp.setTextSize(11);
                c.drawText("Fahrstunden-Zeitübersicht",margin,margin+10,tp);
                doc.finishPage(page);
            }
            String name="Fahrstunden-Uebersicht-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmm",Locale.GERMANY).format(new Date())+".pdf";
            ContentValues v=new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME,name);
            v.put(MediaStore.Downloads.MIME_TYPE,"application/pdf");
            v.put(MediaStore.Downloads.RELATIVE_PATH,"Download/Fahrstunden");
            Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(u==null) throw new Exception("Speicherziel konnte nicht erstellt werden");
            OutputStream out=getContentResolver().openOutputStream(u); doc.writeTo(out); out.close();
            b.recycle();
            toast("PDF in Downloads/Fahrstunden gespeichert.");
        }catch(Exception e){toast("PDF konnte nicht gespeichert werden: "+e.getMessage());}
        finally{doc.close();}
    }

    void save(){
        JSONArray a=new JSONArray();
        try{for(Person p:people){JSONObject o=new JSONObject();o.put("name",p.name);JSONObject ts=new JSONObject();for(String d:p.times.keySet()){JSONArray ar=new JSONArray();for(String[] r:p.times.get(d)){JSONArray rr=new JSONArray();rr.put(r[0]);rr.put(r[1]);ar.put(rr);}ts.put(d,ar);}o.put("times",ts);a.put(o);}prefs.edit().putString("people",a.toString()).apply();}catch(Exception e){}
    }
    void load(){
        String s=prefs.getString("people","");if(s.isEmpty())return;try{JSONArray a=new JSONArray(s);for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Person p=new Person(o.getString("name"));JSONObject ts=o.optJSONObject("times");if(ts!=null)for(String d:DAYS){JSONArray ar=ts.optJSONArray(d);if(ar!=null){ArrayList<String[]> list=new ArrayList<>();for(int j=0;j<ar.length();j++){JSONArray rr=ar.getJSONArray(j);list.add(new String[]{rr.getString(0),rr.getString(1)});}p.times.put(d,list);}}people.add(p);}}catch(Exception e){}}
}
