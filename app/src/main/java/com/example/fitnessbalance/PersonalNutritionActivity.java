package com.example.fitnessbalance;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class PersonalNutritionActivity extends AppCompatActivity {
    private String uid,foodId,foodName,mutation;private long revision;
    private boolean busy,loaded;
    private PersonalNutritionRepository repository;
    private Map<String,Object> existing=Collections.emptyMap();
    private LinearLayout body;private TextView status;
    @Override protected void onCreate(Bundle state){super.onCreate(state);FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){finish();return;}uid=user.getUid();foodId=getIntent().getStringExtra("foodId");foodName=getIntent().getStringExtra("foodName");
        if(foodId==null||foodId.isEmpty()||foodId.contains("/")){finish();return;}repository=new PersonalNutritionRepository(uid,foodId);mutation=state==null?UUID.randomUUID().toString():state.getString("mutation",UUID.randomUUID().toString());
        LinearLayout root=ProgressUi.column(this);setContentView(root);ProgressUi.theme(this,root);FoodUi.insets(this);MaterialToolbar toolbar=new MaterialToolbar(this);toolbar.setTitle(R.string.na_personal);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);toolbar.setNavigationOnClickListener(v->finish());root.addView(toolbar);ProgressUi.toolbar(this,toolbar);
        ScrollView scroll=new ScrollView(this);root.addView(scroll);body=ProgressUi.column(this);int p=ProgressUi.dp(this,16);body.setPadding(p,p,p,p);scroll.addView(body);
        ProgressUi.text(this,body,Objects.toString(foodName,foodId),22,true);status=ProgressUi.text(this,body,"",15,false);
        ProgressUi.button(this,body,R.string.na_label,()->{if(ready())label();});ProgressUi.button(this,body,R.string.na_alternative,()->{if(ready())alternatives();});
        ProgressUi.button(this,body,R.string.na_delete,()->{if(ready()&&revision>0)new AlertDialog.Builder(this).setMessage(R.string.na_remove_note).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.na_delete,(d,w)->{if(!ready())return;busy=true;repository.delete(revision).addOnCompleteListener(task->{busy=false;if(!visible())return;if(task.isSuccessful()){setResult(RESULT_OK);finish();}else error(task.getException());});}).show();});
        ProgressUi.button(this,body,R.string.fl_retry,()->{if(!busy)load();});load();
    }
    @Override protected void onResume(){super.onResume();if(!visible())finish();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("mutation",mutation);}
    private boolean visible(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return !isDestroyed()&&!isFinishing()&&u!=null&&uid.equals(u.getUid());}
    private boolean ready(){return visible()&&loaded&&!busy;}
    private void load(){busy=true;loaded=false;status.setText(R.string.fl_loading);repository.get().addOnCompleteListener(task->{busy=false;if(!visible())return;if(task.isSuccessful()){DocumentSnapshot doc=task.getResult();revision=doc.exists()?doc.getLong("revision"):0;existing=doc.exists()?doc.getData():Collections.emptyMap();loaded=true;status.setText(doc.exists()?Objects.toString(doc.get("source_database"),"")+"\n"+Objects.toString(doc.get("source_note"),""):getString(R.string.na_label_note));}else error(task.getException());});}
    private EditText input(LinearLayout form,String label,boolean numeric){ProgressUi.text(this,form,label,16,true);EditText edit=new EditText(this);edit.setHint(label);edit.setInputType(numeric?8194:1);form.addView(edit);return edit;}
    private void label(){LinearLayout form=ProgressUi.column(this);ProgressUi.text(this,form,getString(R.string.na_label_note),14,false);EditText reference=input(form,getString(R.string.na_reference),true);reference.setText("100");Spinner unit=new Spinner(this);unit.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"g","ml"}));form.addView(unit);
        String[] labels={"Calories (kcal)","Protein (g)","Carbohydrate (g)","Fat (g)","Fibre (g)","Sodium (mg)"};List<EditText> fields=new ArrayList<>();for(String name:labels)fields.add(input(form,name,true));EditText note=input(form,getString(R.string.na_source_note),false);CheckBox confirm=new CheckBox(this);confirm.setText(R.string.na_confirm);form.addView(confirm);
        if("label".equals(existing.get("source_kind"))){reference.setText(Objects.toString(existing.get("label_amount"),"100"));unit.setSelection("ml".equals(existing.get("label_unit"))?1:0);note.setText(Objects.toString(existing.get("source_note"),""));
            if(existing.get("label_nutrition") instanceof Map)for(int i=0;i<fields.size();i++)fields.get(i).setText(Objects.toString(((Map<?,?>)existing.get("label_nutrition")).get(FoodNutrition.KEYS[i]),""));}
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.na_label).setView(scroll).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.sm_save,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(!ready())return;try{if(!confirm.isChecked())throw new IllegalArgumentException(getString(R.string.na_confirm));Map<String,Object> values=new HashMap<>();for(int i=0;i<fields.size();i++){String text=fields.get(i).getText().toString().trim();if(!text.isEmpty())values.put(FoodNutrition.KEYS[i],parse(text));}
            Map<String,Object> result=PersonalNutritionService.label(foodId,Objects.toString(foodName,foodId),parse(reference.getText().toString()),unit.getSelectedItem().toString(),values,note.getText().toString());save(result,dialog);
        }catch(RuntimeException invalid){Toast.makeText(this,invalid.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();}
    private double parse(String text){return Double.parseDouble(text.trim().replace(',','.'));}
    private void alternatives(){LinearLayout form=ProgressUi.column(this);ProgressUi.text(this,form,getString(R.string.na_alt_note),14,false);EditText search=input(form,getString(R.string.rp_search),false);Button go=ProgressUi.button(this,form,R.string.rp_search_go,()->{});TextView message=ProgressUi.text(this,form,"",14,false);LinearLayout results=ProgressUi.column(this);form.addView(results);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.na_alternative).setView(scroll).setNegativeButton(android.R.string.cancel,null).create();
        go.setOnClickListener(v->{if(!ready())return;String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return;go.setEnabled(false);results.removeAllViews();message.setText(R.string.fl_loading);Query query=FirebaseFirestore.getInstance().collection("foods").orderBy("food_name_lowercase").startAt(q).endAt(q+"\uf8ff").limit(30);page(query,dialog,go,message,results);});dialog.show();}
    private void page(Query query,AlertDialog dialog,Button go,TextView message,LinearLayout results){query.get(Source.SERVER).addOnCompleteListener(task->{if(!ready()||!dialog.isShowing())return;go.setEnabled(true);if(!task.isSuccessful()){message.setText(task.getException().getMessage());return;}List<DocumentSnapshot> docs=task.getResult().getDocuments();message.setText(R.string.na_no_alt);int visibleCount=0;
        for(DocumentSnapshot candidate:docs){if(foodId.equals(candidate.getId()))continue;try{Map<String,Object> data=PersonalNutritionService.alternative(foodId,Objects.toString(foodName,foodId),candidate.getId(),candidate.getData());String basis=Objects.toString(data.get("nutrition_basis"),"");visibleCount++;
            LinearLayout card=ProgressUi.card(this,results,candidate.getString("food_name"));ProgressUi.text(this,card,Objects.toString(candidate.get("source_database"),"")+" · "+basis+"\n"+FoodUi.nutrients(this,(Map<?,?>)candidate.get("nutrition"),null,0),14,false);
            ProgressUi.button(this,card,R.string.na_substitute,()->{if(!ready())return;new AlertDialog.Builder(this).setTitle(R.string.na_substitute).setMessage(candidate.getString("food_name")+"\n"+getString(R.string.na_alt_note)+"\n\n"+getString(R.string.na_confirm)).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.sm_save,(d,w)->{if(ready())save(data,dialog);}).show();});
        }catch(RuntimeException unusable){}}
        if(visibleCount>0)message.setText("");
        if(docs.size()==30){Button more=new Button(this);more.setText(R.string.fl_more);results.addView(more);more.setOnClickListener(v->{if(ready()&&go.isEnabled()){results.removeView(more);go.setEnabled(false);page(query.startAfter(docs.get(docs.size()-1)),dialog,go,message,results);}});}
    });}
    private void save(Map<String,Object> data,AlertDialog dialog){busy=true;status.setText(R.string.fl_loading);repository.save(data,revision,mutation).addOnCompleteListener(task->{busy=false;if(!visible())return;if(task.isSuccessful()){dialog.dismiss();Toast.makeText(this,R.string.na_saved,Toast.LENGTH_LONG).show();setResult(RESULT_OK);finish();}else error(task.getException());});}
    private void error(Exception e){status.setText(getString(R.string.sm_error,e==null?"":Objects.toString(e.getMessage(),"")));}
}
