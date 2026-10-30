package com.example.fitnessbalance;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.time.*;
import java.util.*;

public final class WeightHistoryActivity extends AppCompatActivity {
    private String uid,pendingCsv;private int version,range,shown=100;private boolean saving,failed,foreground;
    private LinearLayout body;private Button add,export;private TreeMap<String,WeightHistoryService.Entry> entries;
    private Map<String,Object> profile=Collections.emptyMap();private List<WeightHistoryService.Entry> displayed=new ArrayList<>();
    private final ActivityResultLauncher<String> exportFile=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),uri->{if(same())ProgressUi.export(this,uri,pendingCsv);pendingCsv=null;});
    @Override protected void onCreate(Bundle state){super.onCreate(state);FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){finish();return;}uid=user.getUid();
        if(state!=null){range=state.getInt("range");if(uid.equals(state.getString("uid")))pendingCsv=state.getString("csv");}
        LinearLayout root=ProgressUi.column(this);root.setId(R.id.foodRoot);root.setBackgroundColor(0xFFF1F6FC);setContentView(root);FoodUi.insets(this);
        com.google.android.material.appbar.MaterialToolbar toolbar=new com.google.android.material.appbar.MaterialToolbar(this);
        toolbar.setTitle(R.string.wh_title);toolbar.setTitleTextColor(ProgressUi.INK);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationIconTint(ProgressUi.INK);toolbar.setNavigationContentDescription(R.string.back);toolbar.setNavigationOnClickListener(v->finish());
        toolbar.getMenu().add(0,100,0,R.string.pr_refresh);toolbar.setOnMenuItemClickListener(item->{load();return true;});root.addView(toolbar);
        Spinner period=new Spinner(this);period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,getResources().getStringArray(R.array.wh_ranges)));root.addView(period);period.setSelection(Math.max(0,Math.min(3,range)));
        period.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){range=position;shown=100;render();}public void onNothingSelected(AdapterView<?> p){}});
        add=ProgressUi.button(this,root,R.string.wh_add,()->edit(null));add.setEnabled(false);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=ProgressUi.column(this);int pad=ProgressUi.dp(this,16);body.setPadding(pad,pad,pad,pad);scroll.addView(body);
        export=ProgressUi.button(this,root,R.string.wh_export,()->{pendingCsv=ProgressCsv.weights(displayed);exportFile.launch("fitnessbalance-weight-"+LocalDate.now()+".csv");});export.setEnabled(false);
    }
    private boolean same(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return uid!=null&&u!=null&&uid.equals(u.getUid());}
    private boolean current(int request){return request==version&&same()&&!isFinishing()&&!isDestroyed();}
    private String n(Object value){return ProgressUi.number(this,value);}
    private void load(){if(!same()){finish();return;}int request=++version;entries=null;failed=false;render();
        Task<TreeMap<String,WeightHistoryService.Entry>> history=new WeightHistoryRepository(uid).load();
        Task<DocumentSnapshot> user=FirebaseFirestore.getInstance().collection("users").document(uid).get(Source.SERVER);
        Tasks.whenAll(history,user).addOnSuccessListener(this,unused->{if(!current(request))return;if(!user.getResult().exists()){failed=true;render();return;}entries=history.getResult();profile=user.getResult().getData();render();})
                .addOnFailureListener(this,error->{if(!current(request))return;failed=true;render();});
    }
    private void render(){if(body==null||export==null)return;body.removeAllViews();add.setEnabled(entries!=null&&!saving);export.setEnabled(false);displayed=new ArrayList<>();
        if(entries==null){LinearLayout c=ProgressUi.card(this,body,null);ProgressUi.text(this,c,getString(failed?R.string.wh_load_error:R.string.fl_loading),16,false);if(failed)ProgressUi.button(this,c,R.string.fl_retry,this::load);return;}
        LinearLayout c=ProgressUi.card(this,body,getString(R.string.pr_weight));
        if(entries.isEmpty())ProgressUi.text(this,c,getString(R.string.wh_empty),17,false);else{
            WeightHistoryService.Entry first=entries.firstEntry().getValue(),last=entries.lastEntry().getValue();ProgressUi.text(this,c,getString(R.string.wh_summary,first.day,n(first.kg),last.day,n(last.kg),n(last.kg-first.kg)),17,true);
            LocalDate from=range==1?LocalDate.now().minusDays(29):range==2?LocalDate.now().minusDays(89):range==3?LocalDate.now().minusDays(364):LocalDate.MIN;
            for(WeightHistoryService.Entry e:entries.values())if(!LocalDate.parse(e.day).isBefore(from))displayed.add(e);
            if(displayed.isEmpty())ProgressUi.text(this,c,getString(R.string.wh_no_range),16,false);else ProgressUi.chart(this,c,260).weights(displayed);
        }
        Double currentWeight=FoodNutrition.number(profile.get("weight"));if(currentWeight!=null&&currentWeight>0)ProgressUi.text(this,c,getString(R.string.wh_profile,n(currentWeight)),15,false);
        ProgressUi.text(this,c,getString(R.string.wh_note),14,false);export.setEnabled(!displayed.isEmpty());
        c=ProgressUi.card(this,body,getString(R.string.wh_entries));List<WeightHistoryService.Entry> newest=new ArrayList<>(displayed);Collections.reverse(newest);
        for(int i=0;i<Math.min(shown,newest.size());i++){WeightHistoryService.Entry entry=newest.get(i);ProgressUi.text(this,c,entry.day+" — "+n(entry.kg)+" kg",18,true);LinearLayout buttons=new LinearLayout(this);c.addView(buttons);Button edit=ProgressUi.button(this,buttons,R.string.wh_edit,()->edit(entry)),remove=ProgressUi.button(this,buttons,R.string.fl_remove,()->remove(entry));edit.setEnabled(!saving);remove.setEnabled(!saving);}
        if(shown<newest.size())ProgressUi.button(this,c,R.string.wh_more,()->{shown+=100;render();});
    }
    private void edit(WeightHistoryService.Entry original){if(entries==null||saving)return;
        LinearLayout form=ProgressUi.column(this);form.setBackgroundColor(android.graphics.Color.WHITE);int pad=ProgressUi.dp(this,18);form.setPadding(pad,pad,pad,pad);
        String[] day={original==null?LocalDate.now().toString():original.day};long[] revision={original==null?(entries.containsKey(day[0])?entries.get(day[0]).revision:0):original.revision};
        Button date=ProgressUi.button(this,form,R.string.wh_date,()->{});date.setText(day[0]);date.setEnabled(original==null);
        TextView label=ProgressUi.text(this,form,getString(R.string.wh_kg),16,false);EditText weight=new EditText(this);weight.setId(View.generateViewId());weight.setTextColor(ProgressUi.INK);label.setLabelFor(weight.getId());weight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);form.addView(weight);
        WeightHistoryService.Entry initial=original==null?entries.get(day[0]):original;if(initial!=null)weight.setText(String.valueOf(initial.kg));
        CheckBox update=new CheckBox(this);update.setText(R.string.wh_update_profile);update.setTextColor(ProgressUi.INK);update.setChecked(day[0].equals(LocalDate.now().toString()));form.addView(update);
        ProgressUi.text(this,form,getString(R.string.wh_save_note),14,false);TextView error=ProgressUi.text(this,form,"",15,false);
        date.setOnClickListener(v->{LocalDate selected=LocalDate.parse(day[0]);android.app.DatePickerDialog picker=new android.app.DatePickerDialog(this,(d,y,m,dd)->{day[0]=LocalDate.of(y,m+1,dd).toString();date.setText(day[0]);WeightHistoryService.Entry existing=entries.get(day[0]);revision[0]=existing==null?0:existing.revision;weight.setText(existing==null?"":String.valueOf(existing.kg));update.setChecked(day[0].equals(LocalDate.now().toString()));},selected.getYear(),selected.getMonthValue()-1,selected.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();});
        String[] mutation={UUID.randomUUID().toString()},payload={""};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.wh_add).setView(form).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.fe_save,null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final double kg;try{kg=Double.parseDouble(weight.getText().toString().trim().replace(',','.'));new WeightHistoryService.Entry(day[0],kg,Math.max(1,revision[0]+1));if(LocalDate.parse(day[0]).isAfter(LocalDate.now()))throw new IllegalArgumentException();}catch(RuntimeException invalid){error.setText(R.string.wh_invalid);return;}
            String signature=day[0]+"|"+kg+"|"+update.isChecked();if(!signature.equals(payload[0])){mutation[0]=UUID.randomUUID().toString();payload[0]=signature;}
            saving=true;dialog.setCancelable(false);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);date.setEnabled(false);weight.setEnabled(false);update.setEnabled(false);error.setText(R.string.fl_saving);
            new WeightHistoryRepository(uid).save(day[0],revision[0],kg,false,update.isChecked(),mutation[0]).addOnSuccessListener(unused->{saving=false;if(isDestroyed()||isFinishing())return;dialog.dismiss();if(foreground)load();})
                    .addOnFailureListener(failure->{saving=false;if(isDestroyed()||isFinishing())return;dialog.setCancelable(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);date.setEnabled(original==null);weight.setEnabled(true);update.setEnabled(true);error.setText(R.string.wh_save_error);});
        }));dialog.show();
    }
    private void remove(WeightHistoryService.Entry entry){if(saving)return;new AlertDialog.Builder(this).setTitle(R.string.fl_remove).setMessage(getString(R.string.wh_delete_note,entry.day,n(entry.kg)))
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.fl_remove,(d,w)->{saving=true;render();new WeightHistoryRepository(uid).save(entry.day,entry.revision,entry.kg,true,false,UUID.randomUUID().toString()).addOnSuccessListener(unused->{saving=false;if(!isDestroyed()&&!isFinishing()&&foreground)load();}).addOnFailureListener(e->{saving=false;if(!isDestroyed()&&!isFinishing()&&foreground){Toast.makeText(this,R.string.wh_save_error,Toast.LENGTH_LONG).show();load();}});}).show();}
    @Override protected void onStart(){super.onStart();foreground=true;if(uid!=null&&same())FirebaseFirestore.getInstance().collection("users").document(uid).collection("weightHistory").document("state").addSnapshotListener(this,(document,error)->{if(error==null&&same())load();});}
    @Override protected void onResume(){super.onResume();if(uid!=null)load();}
    @Override protected void onStop(){foreground=false;version++;super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("uid",uid);state.putInt("range",range);state.putString("csv",pendingCsv);}
}
