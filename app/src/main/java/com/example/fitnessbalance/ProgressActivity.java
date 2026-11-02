package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ProgressActivity extends AppCompatActivity {
    private String uid,observedDay,pendingCsv;private LocalDate start;private int tab,version;private boolean net;
    private Button dates,next,export;private MaterialButton[] sections=new MaterialButton[4];private LinearLayout body;private ProgressService.Week week;
    private TreeMap<String,WeightHistoryService.Entry> weights;private boolean weekFailed,weightFailed;
    private final ActivityResultLauncher<String> exportFile=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),uri->{if(same())ProgressUi.export(this,uri,pendingCsv);pendingCsv=null;});
    @Override protected void onCreate(Bundle state){super.onCreate(state);FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){finish();return;}uid=user.getUid();observedDay=LocalDate.now().toString();start=ProgressService.week(LocalDate.now());
        if(state!=null){try{start=LocalDate.parse(state.getString("start"));}catch(Exception ignored){}observedDay=state.getString("observedDay",observedDay);tab=state.getInt("tab");net=state.getBoolean("net");if(uid.equals(state.getString("uid")))pendingCsv=state.getString("csv");}
        LinearLayout root=ProgressUi.column(this);root.setId(R.id.foodRoot);ProgressUi.theme(this,root);setContentView(root);FoodUi.insets(this);
        com.google.android.material.appbar.MaterialToolbar toolbar=new com.google.android.material.appbar.MaterialToolbar(this);
        toolbar.setTitle(R.string.ui_progress_title);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);toolbar.setNavigationContentDescription(R.string.back);toolbar.setNavigationOnClickListener(v->finish());ProgressUi.toolbar(this,toolbar);
        toolbar.getMenu().add(0,100,0,R.string.pr_refresh);toolbar.setOnMenuItemClickListener(item->{load();return true;});root.addView(toolbar);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout page=ProgressUi.column(this);int pad=ProgressUi.dp(this,16);page.setPadding(pad,pad,pad,pad);scroll.addView(page);
        ProgressUi.hero(this,page,R.string.ui_progress_intro,R.string.ui_progress_note);
        LinearLayout nav=new LinearLayout(this);page.addView(nav);Button prev=ProgressUi.action(this);prev.setText("‹");prev.setPadding(0,0,0,0);prev.setMinWidth(0);prev.setContentDescription(getString(R.string.pr_previous));nav.addView(prev,new LinearLayout.LayoutParams(ProgressUi.dp(this,52),-2));prev.setOnClickListener(v->{start=start.minusWeeks(1);load();});
        dates=ProgressUi.action(this);dates.setAllCaps(false);nav.addView(dates,new LinearLayout.LayoutParams(0,-2,1));dates.setOnClickListener(v->new android.app.DatePickerDialog(this,(d,y,m,day)->{start=ProgressService.week(LocalDate.of(y,m+1,day));load();},start.getYear(),start.getMonthValue()-1,start.getDayOfMonth()){{getDatePicker().setMaxDate(System.currentTimeMillis());}}.show());
        next=ProgressUi.action(this);next.setText("›");next.setPadding(0,0,0,0);next.setMinWidth(0);next.setContentDescription(getString(R.string.pr_next));nav.addView(next,new LinearLayout.LayoutParams(ProgressUi.dp(this,52),-2));next.setOnClickListener(v->{if(start.isBefore(ProgressService.week(LocalDate.now()))){start=start.plusWeeks(1);load();}});
        String[] labels=getResources().getStringArray(R.array.ui_progress_sections);
        for(int row=0;row<2;row++){LinearLayout strip=new LinearLayout(this);page.addView(strip);for(int col=0;col<2;col++){final int section=row*2+col;MaterialButton button=ProgressUi.action(this);button.setText(labels[section]);button.setTextSize(14);button.setSingleLine(false);button.setMaxLines(3);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(ProgressUi.dp(this,3),0,ProgressUi.dp(this,3),0);strip.addView(button,lp);sections[section]=button;button.setOnClickListener(v->selectSection(section));}}
        body=ProgressUi.column(this);page.addView(body);
        export=ProgressUi.button(this,page,R.string.pr_export_week,()->{if(week!=null){pendingCsv=ProgressCsv.week(week);exportFile.launch("fitnessbalance-week-"+start+".csv");}});
        selectSection(Math.max(0,Math.min(3,tab)));
        new com.example.fitnessbalance.util.LocalDayObserver(this,this,observedDay,()->{adjustDay();load();});
    }
    private void selectSection(int value){tab=value;for(int i=0;i<sections.length;i++){MaterialButton button=sections[i];if(button==null)continue;button.setSelected(i==value);button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this,i==value?R.color.gm_purple:R.color.gm_lavender)));button.setTextColor(i==value?Color.WHITE:ProgressUi.ink(this));}render();}
    private boolean same(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return uid!=null&&u!=null&&uid.equals(u.getUid());}
    private void adjustDay(){LocalDate today=LocalDate.now();try{if(!today.toString().equals(observedDay)&&start.equals(ProgressService.week(LocalDate.parse(observedDay))))start=ProgressService.week(today);}catch(Exception ignored){}if(start.isAfter(ProgressService.week(today)))start=ProgressService.week(today);observedDay=today.toString();}
    private void load(){if(!same()){finish();return;}int request=++version;adjustDay();week=null;weights=null;weekFailed=false;weightFailed=false;dates.setText(start+" — "+start.plusDays(6));next.setEnabled(start.isBefore(ProgressService.week(LocalDate.now())));render();
        new ProgressRepository().load(uid,start).addOnSuccessListener(this,data->{if(!current(request))return;week=data;render();}).addOnFailureListener(this,e->{if(!current(request))return;weekFailed=true;render();});
        new WeightHistoryRepository(uid).load().addOnSuccessListener(this,data->{if(!current(request))return;weights=data;render();}).addOnFailureListener(this,e->{if(!current(request))return;weightFailed=true;render();});
    }
    private boolean current(int request){return request==version&&same()&&!isFinishing()&&!isDestroyed();}
    private String n(Object value){return ProgressUi.number(this,value);}
    private String label(String key){int i=Arrays.asList(FoodNutrition.KEYS).indexOf(key);int[] ids={R.string.fl_nutrient_energy_kcal,R.string.fl_nutrient_protein_g,R.string.fl_nutrient_carbohydrate_g,R.string.fl_nutrient_fat_g,R.string.fl_nutrient_fiber_g,R.string.fl_nutrient_sodium_mg};return i<0?key:getString(ids[i]);}
    private void render(){if(body==null||export==null)return;body.removeAllViews();export.setEnabled(week!=null);
        if(week==null){LinearLayout c=ProgressUi.card(this,body,null);ProgressUi.text(this,c,getString(weekFailed?R.string.pr_load_error:R.string.fl_loading),16,false);if(weekFailed)ProgressUi.button(this,c,R.string.fl_retry,this::load);}
        else {ProgressUi.text(this,body,getString(R.string.pr_data_note),14,false);if(tab==0)overview();else if(tab==1)calories();else if(tab==2)nutrients();else macros();}
        if(tab==0&&week==null)weightCard();
    }
    private void bars(LinearLayout card,String key,boolean stacked,Double goal,int color){String[] labels=new String[7];double[][] values=new double[7][];for(int i=0;i<7;i++){ProgressService.Day d=week.days.get(i);labels[i]=LocalDate.parse(d.date).format(DateTimeFormatter.ofPattern("EEE",Locale.getDefault()));Double value=ProgressService.Week.value(d,key);values[i]=stacked&&value!=null?d.meals.clone():new double[]{value==null?Double.NaN:value};}ProgressChartView chart=ProgressUi.chart(this,card,320);chart.horizontalBars(labels,values,stacked?ProgressUi.MEAL_COLORS:new int[]{color},goal);
        StringBuilder description=new StringBuilder(key.equals("net")?getString(R.string.pr_net):key.equals("exercise")?getString(R.string.pr_exercise):label(key));for(ProgressService.Day day:week.days)description.append(". ").append(day.date).append(": ").append(n(ProgressService.Week.value(day,key))).append(day.partial(key)?" (partial)":"");chart.setContentDescription(description.toString());}
    private void summary(LinearLayout card,String key){ProgressUi.text(this,card,getString(R.string.pr_average,n(week.average(key)),week.count(key)),19,true);if(week.partial(key))ProgressUi.text(this,card,getString(R.string.pr_partial),14,false);}
    private void overview(){
        LinearLayout c=ProgressUi.accentCard(this,body,getString(R.string.ui_week_snapshot),ProgressUi.BLUE);
        ProgressUi.text(this,c,getString(R.string.ui_food_average),15,false);summary(c,"energy_kcal");
        ProgressUi.text(this,c,getString(R.string.ui_exercise_total)+": "+n(week.sum("exercise"))+" kcal",18,true);
        int recorded=0,total=0,known=0;boolean legacy=false;
        for(ProgressService.Day day:week.days){if(day.entries>0)recorded++;total+=day.entries;int count=com.example.fitnessbalance.util.Values.integer(day.coverage.get("energy_kcal"),0);known+=count;if(day.partialEnergy&&count>=day.entries)legacy=true;}
        ProgressUi.text(this,c,getString(R.string.ui_days_recorded,recorded),14,false);
        c=ProgressUi.accentCard(this,body,getString(R.string.ui_coverage),ProgressUi.TEAL);
        ProgressUi.text(this,c,legacy?getString(R.string.ui_coverage_legacy):getString(R.string.ui_coverage_count,known,total),17,true);
        if(week.partial("energy_kcal"))ProgressUi.text(this,c,getString(R.string.pr_partial),14,false);
        c=ProgressUi.accentCard(this,body,getString(R.string.ui_day_trail),ProgressUi.PURPLE);
        for(ProgressService.Day day:week.days){LinearLayout item=ProgressUi.column(this);int p=ProgressUi.dp(this,12);item.setPadding(p,p,p,p);ProgressUi.mealPanel(this,item,ProgressUi.mealIndex("Lunch"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=ProgressUi.dp(this,10);c.addView(item,lp);
            ProgressUi.text(this,item,LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE · d MMM",Locale.getDefault())),17,true);
            ProgressUi.text(this,item,getString(R.string.ui_day_values,n(day.food),n(day.exercise)),15,false);
            if(day.partialEnergy)ProgressUi.text(this,item,getString(R.string.fl_partial_note),14,false);
        }
        ProgressUi.button(this,c,R.string.pr_view_calories,()->selectSection(1));weightCard();
    }
    private void calories(){LinearLayout c=ProgressUi.card(this,body,getString(R.string.pr_calories));RadioGroup mode=new RadioGroup(this);mode.setOrientation(RadioGroup.VERTICAL);RadioButton total=new RadioButton(this),minus=new RadioButton(this);total.setId(View.generateViewId());minus.setId(View.generateViewId());total.setText(R.string.pr_total);minus.setText(R.string.pr_net);total.setTextColor(ProgressUi.ink(this));minus.setTextColor(ProgressUi.ink(this));mode.addView(total);mode.addView(minus);mode.check(net?minus.getId():total.getId());c.addView(mode);mode.setOnCheckedChangeListener((g,id)->{net=id==minus.getId();render();});String key=net?"net":"energy_kcal";bars(c,key,!net,net?null:week.goal("energy_kcal"),net?0xFF36B77C:ProgressUi.BLUE);summary(c,key);
        ProgressUi.text(this,c,getString(R.string.pr_recorded_total,n(week.sum(key))),17,false);
        if(net)ProgressUi.text(this,c,getString(R.string.pr_net_note),14,false);else{
            if(week.count("energy_kcal")>0)for(int i=0;i<5;i++){double sum=0;for(ProgressService.Day d:week.days)sum+=d.meals[i];TextView legend=ProgressUi.legend(this,c,ProgressService.MEALS[i]+": "+n(sum)+" kcal",ProgressUi.MEAL_COLORS[i]);legend.setTextColor(ProgressUi.ink(this));}
            Double goal=week.goal("energy_kcal");ProgressUi.text(this,c,getString(R.string.pr_goal_reference,n(goal)),16,false);
            if(goal!=null&&!week.partial("energy_kcal")){double sum=0;int days=0;for(ProgressService.Day d:week.days)if(d.food!=null&&!d.partialEnergy){sum+=d.food;days++;}if(days>0)ProgressUi.text(this,c,getString(R.string.pr_goal_difference,n(sum-goal*days),days),15,false);}
        }
        c=ProgressUi.card(this,body,getString(R.string.pr_daily_values));for(ProgressService.Day d:week.days)ProgressUi.text(this,c,d.date+" · "+n(ProgressService.Week.value(d,key))+(ProgressService.Week.value(d,key)==null?"":" kcal")+(d.partialEnergy?" · "+getString(R.string.fl_partial_note):""),14,false);
        c=ProgressUi.card(this,body,getString(R.string.ui_food_sources));ProgressUi.text(this,c,getString(R.string.ui_food_sources_note),14,false);if(week.highest.isEmpty())ProgressUi.text(this,c,getString(R.string.pr_no_foods),16,false);for(int i=0;i<Math.min(5,week.highest.size());i++){Map<String,Object> row=week.highest.get(i);ProgressUi.text(this,c,row.get("name")+" — "+n(row.get("calories"))+" kcal",16,false);}}
    private boolean legacyCoverage(){for(ProgressService.Day day:week.days)if(day.entries>0&&day.partialEnergy&&com.example.fitnessbalance.util.Values.integer(day.coverage.get("energy_kcal"),0)>=day.entries)return true;return false;}
    private void nutrients(){int total=0;for(ProgressService.Day d:week.days)total+=d.entries;
        for(int i=0;i<FoodNutrition.KEYS.length;i++){String key=FoodNutrition.KEYS[i];LinearLayout c=ProgressUi.accentCard(this,body,label(key),ProgressUi.MEAL_COLORS[i%5]);int known=0;
            // Coverage is displayed independently from the nutrient sum; missing is never zero.
            for(ProgressService.Day d:week.days)known+=com.example.fitnessbalance.util.Values.integer(d.coverage.get(key),0);
            ProgressUi.text(this,c,getString(R.string.pr_nutrient_row,n(week.sum(key)),n(week.average(key)),week.count(key),n(week.goal(key))),16,false);
            ProgressUi.text(this,c,legacyCoverage()?getString(R.string.ui_coverage_legacy):getString(R.string.ui_nutrient_coverage,known,total),14,false);if(week.partial(key))ProgressUi.text(this,c,getString(R.string.pr_partial),14,false);
        }ProgressUi.text(this,body,getString(R.string.pr_goal_note),14,false);
    }
    private void macros(){String[] keys={"carbohydrate_g","fat_g","protein_g"};int[] colors={ProgressUi.TEAL,ProgressUi.PURPLE,ProgressUi.AMBER};for(int i=0;i<3;i++){LinearLayout c=ProgressUi.card(this,body,label(keys[i]));summary(c,keys[i]);bars(c,keys[i],false,week.goal(keys[i]),colors[i]);ProgressUi.text(this,c,getString(R.string.pr_recorded_total,n(week.sum(keys[i]))),16,false);}
        LinearLayout c=ProgressUi.card(this,body,getString(R.string.pr_macro_share));if(!week.completeMacros()){ProgressUi.text(this,c,getString(R.string.pr_macro_incomplete),15,false);return;}double[] energy={week.sum(keys[0])*4,week.sum(keys[1])*9,week.sum(keys[2])*4};double total=energy[0]+energy[1]+energy[2];if(total<=0){ProgressUi.text(this,c,getString(R.string.pr_macro_incomplete),15,false);return;}ProgressUi.chart(this,c,100).shares(energy,colors);for(int i=0;i<3;i++)ProgressUi.legend(this,c,label(keys[i])+": "+n(100*energy[i]/total)+"%",colors[i]);ProgressUi.text(this,c,getString(R.string.pr_macro_share_note),14,false);}
    private void weightCard(){LinearLayout c=ProgressUi.accentCard(this,body,getString(R.string.ui_weight_journal),ProgressUi.PURPLE);
        if(weights==null)ProgressUi.text(this,c,getString(weightFailed?R.string.wh_load_error:R.string.fl_loading),16,false);
        else if(weights.isEmpty())ProgressUi.text(this,c,getString(R.string.wh_empty),16,false);
        else{WeightHistoryService.Entry first=weights.firstEntry().getValue(),last=weights.lastEntry().getValue();ProgressUi.text(this,c,getString(R.string.wh_summary,first.day,n(first.kg),last.day,n(last.kg),n(last.kg-first.kg)),17,true);}
        if(week!=null){Double profileWeight=FoodNutrition.number(week.profile.get("weight"));if(profileWeight!=null&&profileWeight>0)ProgressUi.text(this,c,getString(R.string.wh_profile,n(profileWeight)),14,false);}
        ProgressUi.button(this,c,R.string.wh_open,()->startActivity(new Intent(this,WeightHistoryActivity.class)));
    }
    @Override protected void onResume(){super.onResume();if(uid!=null)load();}
    @Override protected void onStop(){version++;super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(uid==null)return;state.putString("uid",uid);state.putString("start",start.toString());state.putString("observedDay",observedDay);state.putInt("tab",tab);state.putBoolean("net",net);state.putString("csv",pendingCsv);}
}
