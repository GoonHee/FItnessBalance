package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ProgressActivity extends AppCompatActivity {
    private String uid,observedDay,pendingCsv;private LocalDate start;private int tab,version;private boolean net;
    private Button dates,next,export;private TabLayout tabs;private LinearLayout body;private ProgressService.Week week;
    private TreeMap<String,WeightHistoryService.Entry> weights;private boolean weekFailed,weightFailed;
    private final ActivityResultLauncher<String> exportFile=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),uri->{if(same())ProgressUi.export(this,uri,pendingCsv);pendingCsv=null;});
    @Override protected void onCreate(Bundle state){super.onCreate(state);FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){finish();return;}uid=user.getUid();observedDay=LocalDate.now().toString();start=ProgressService.week(LocalDate.now());
        if(state!=null){try{start=LocalDate.parse(state.getString("start"));}catch(Exception ignored){}observedDay=state.getString("observedDay",observedDay);tab=state.getInt("tab");net=state.getBoolean("net");if(uid.equals(state.getString("uid")))pendingCsv=state.getString("csv");}
        LinearLayout root=ProgressUi.column(this);root.setId(R.id.foodRoot);root.setBackgroundColor(0xFFF1F6FC);setContentView(root);FoodUi.insets(this);
        com.google.android.material.appbar.MaterialToolbar toolbar=new com.google.android.material.appbar.MaterialToolbar(this);
        toolbar.setTitle(R.string.pr_title);toolbar.setTitleTextColor(ProgressUi.INK);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationIconTint(ProgressUi.INK);toolbar.setNavigationContentDescription(R.string.back);toolbar.setNavigationOnClickListener(v->finish());
        toolbar.getMenu().add(0,100,0,R.string.pr_refresh);toolbar.setOnMenuItemClickListener(item->{load();return true;});root.addView(toolbar);
        tabs=new TabLayout(this);tabs.setTabTextColors(0xFF5D6878,ProgressUi.INK);tabs.setSelectedTabIndicatorColor(ProgressUi.BLUE);tabs.setBackgroundColor(Color.WHITE);String[] titles=getResources().getStringArray(R.array.pr_tabs);for(String title:titles)tabs.addTab(tabs.newTab().setText(title));root.addView(tabs);
        tabs.selectTab(tabs.getTabAt(Math.max(0,Math.min(3,tab))));tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener(){public void onTabSelected(TabLayout.Tab t){tab=t.getPosition();render();}public void onTabUnselected(TabLayout.Tab t){}public void onTabReselected(TabLayout.Tab t){}});
        LinearLayout nav=new LinearLayout(this);root.addView(nav);Button prev=new Button(this);prev.setText("‹");prev.setContentDescription(getString(R.string.pr_previous));nav.addView(prev,new LinearLayout.LayoutParams(ProgressUi.dp(this,50),-2));prev.setOnClickListener(v->{start=start.minusWeeks(1);load();});
        dates=new Button(this);dates.setAllCaps(false);nav.addView(dates,new LinearLayout.LayoutParams(0,-2,1));dates.setOnClickListener(v->new android.app.DatePickerDialog(this,(d,y,m,day)->{start=ProgressService.week(LocalDate.of(y,m+1,day));load();},start.getYear(),start.getMonthValue()-1,start.getDayOfMonth()){{getDatePicker().setMaxDate(System.currentTimeMillis());}}.show());
        next=new Button(this);next.setText("›");next.setContentDescription(getString(R.string.pr_next));nav.addView(next,new LinearLayout.LayoutParams(ProgressUi.dp(this,50),-2));next.setOnClickListener(v->{if(start.isBefore(ProgressService.week(LocalDate.now()))){start=start.plusWeeks(1);load();}});
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=ProgressUi.column(this);int pad=ProgressUi.dp(this,16);body.setPadding(pad,pad,pad,pad);scroll.addView(body);
        export=ProgressUi.button(this,root,R.string.pr_export_week,()->{if(week!=null){pendingCsv=ProgressCsv.week(week);exportFile.launch("fitnessbalance-week-"+start+".csv");}});
        new com.example.fitnessbalance.util.LocalDayObserver(this,this,observedDay,()->{adjustDay();load();});
    }
    private boolean same(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return uid!=null&&u!=null&&uid.equals(u.getUid());}
    private void adjustDay(){LocalDate today=LocalDate.now();try{if(!today.toString().equals(observedDay)&&start.equals(ProgressService.week(LocalDate.parse(observedDay))))start=ProgressService.week(today);}catch(Exception ignored){}if(start.isAfter(ProgressService.week(today)))start=ProgressService.week(today);observedDay=today.toString();}
    private void load(){if(!same()){finish();return;}int request=++version;adjustDay();week=null;weights=null;weekFailed=false;weightFailed=false;dates.setText(start+" — "+start.plusDays(6));next.setEnabled(start.isBefore(ProgressService.week(LocalDate.now())));render();
        new ProgressRepository().load(uid,start).addOnSuccessListener(this,data->{if(!current(request))return;week=data;render();}).addOnFailureListener(this,e->{if(!current(request))return;weekFailed=true;render();});
        new WeightHistoryRepository(uid).load().addOnSuccessListener(this,data->{if(!current(request))return;weights=data;render();}).addOnFailureListener(this,e->{if(!current(request))return;weightFailed=true;render();});
    }
    private boolean current(int request){return request==version&&same()&&!isFinishing()&&!isDestroyed();}
    private String n(Object value){return ProgressUi.number(this,value);}
    private String label(String key){int i=Arrays.asList(FoodNutrition.KEYS).indexOf(key);int[] ids={R.string.fl_nutrient_energy_kcal,R.string.fl_nutrient_protein_g,R.string.fl_nutrient_carbohydrate_g,R.string.fl_nutrient_fat_g,R.string.fl_nutrient_fiber_g,R.string.fl_nutrient_sodium_mg};return i<0?key:getString(ids[i]);}
    private void render(){if(body==null)return;body.removeAllViews();export.setEnabled(week!=null);
        if(week==null){LinearLayout c=ProgressUi.card(this,body,null);ProgressUi.text(this,c,getString(weekFailed?R.string.pr_load_error:R.string.fl_loading),16,false);if(weekFailed)ProgressUi.button(this,c,R.string.fl_retry,this::load);}
        else {ProgressUi.text(this,body,getString(R.string.pr_data_note),14,false);if(tab==0)overview();else if(tab==1)calories();else if(tab==2)nutrients();else macros();}
        if(tab==0&&week==null)weightCard();
    }
    private void bars(LinearLayout card,String key,boolean stacked,Double goal,int color){String[] labels=new String[7];double[][] values=new double[7][];for(int i=0;i<7;i++){ProgressService.Day d=week.days.get(i);labels[i]=LocalDate.parse(d.date).format(DateTimeFormatter.ofPattern("EEE",Locale.getDefault()));Double value=ProgressService.Week.value(d,key);values[i]=stacked&&value!=null?d.meals.clone():new double[]{value==null?Double.NaN:value};}ProgressChartView chart=ProgressUi.chart(this,card,205);chart.bars(labels,values,stacked?ProgressUi.MEAL_COLORS:new int[]{color},goal);
        StringBuilder description=new StringBuilder(key.equals("net")?getString(R.string.pr_net):key.equals("exercise")?getString(R.string.pr_exercise):label(key));for(ProgressService.Day day:week.days)description.append(". ").append(day.date).append(": ").append(n(ProgressService.Week.value(day,key))).append(day.partial(key)?" (partial)":"");chart.setContentDescription(description.toString());}
    private void summary(LinearLayout card,String key){ProgressUi.text(this,card,getString(R.string.pr_average,n(week.average(key)),week.count(key)),19,true);if(week.partial(key))ProgressUi.text(this,card,getString(R.string.pr_partial),14,false);}
    private void overview(){LinearLayout c=ProgressUi.card(this,body,getString(R.string.pr_calories));summary(c,"energy_kcal");bars(c,"energy_kcal",false,null,ProgressUi.BLUE);ProgressUi.button(this,c,R.string.pr_view_calories,()->tabs.selectTab(tabs.getTabAt(1)));weightCard();
        c=ProgressUi.card(this,body,getString(R.string.pr_exercise));summary(c,"exercise");bars(c,"exercise",false,null,0xFF36B77C);
        String[] keys={"carbohydrate_g","fat_g","protein_g"};int[] colors={ProgressUi.TEAL,ProgressUi.PURPLE,ProgressUi.AMBER};for(int i=0;i<3;i++){c=ProgressUi.card(this,body,label(keys[i]));summary(c,keys[i]);bars(c,keys[i],false,null,colors[i]);}}
    private void calories(){LinearLayout c=ProgressUi.card(this,body,getString(R.string.pr_calories));RadioGroup mode=new RadioGroup(this);mode.setOrientation(RadioGroup.VERTICAL);RadioButton total=new RadioButton(this),minus=new RadioButton(this);total.setId(View.generateViewId());minus.setId(View.generateViewId());total.setText(R.string.pr_total);minus.setText(R.string.pr_net);total.setTextColor(ProgressUi.INK);minus.setTextColor(ProgressUi.INK);mode.addView(total);mode.addView(minus);mode.check(net?minus.getId():total.getId());c.addView(mode);mode.setOnCheckedChangeListener((g,id)->{net=id==minus.getId();render();});String key=net?"net":"energy_kcal";bars(c,key,!net,net?null:week.goal("energy_kcal"),net?0xFF36B77C:ProgressUi.BLUE);summary(c,key);
        ProgressUi.text(this,c,getString(R.string.pr_recorded_total,n(week.sum(key))),17,false);
        if(net)ProgressUi.text(this,c,getString(R.string.pr_net_note),14,false);else{
            for(int i=0;i<5;i++){double sum=0;for(ProgressService.Day d:week.days)sum+=d.meals[i];TextView legend=ProgressUi.text(this,c,ProgressService.MEALS[i]+": "+n(sum)+" kcal",15,false);legend.setTextColor(ProgressUi.INK);}
            Double goal=week.goal("energy_kcal");ProgressUi.text(this,c,getString(R.string.pr_goal_reference,n(goal)),16,false);
            if(goal!=null){double sum=0;int days=0;for(ProgressService.Day d:week.days)if(d.food!=null&&!d.partialEnergy){sum+=d.food;days++;}if(days>0)ProgressUi.text(this,c,getString(R.string.pr_goal_difference,n(sum-goal*days),days),15,false);}
        }
        c=ProgressUi.card(this,body,getString(R.string.pr_daily_values));for(ProgressService.Day d:week.days)ProgressUi.text(this,c,d.date+" · "+n(ProgressService.Week.value(d,key))+(ProgressService.Week.value(d,key)==null?"":" kcal")+(d.partialEnergy?" · "+getString(R.string.fl_partial_note):""),14,false);
        c=ProgressUi.card(this,body,getString(R.string.pr_highest));if(week.highest.isEmpty())ProgressUi.text(this,c,getString(R.string.pr_no_foods),16,false);for(int i=0;i<Math.min(5,week.highest.size());i++){Map<String,Object> row=week.highest.get(i);ProgressUi.text(this,c,row.get("name")+" — "+n(row.get("calories"))+" kcal",16,false);}}
    private void nutrients(){LinearLayout c=ProgressUi.card(this,body,getString(R.string.pr_nutrients));for(String key:FoodNutrition.KEYS){ProgressUi.text(this,c,label(key),19,true);ProgressUi.text(this,c,getString(R.string.pr_nutrient_row,n(week.sum(key)),n(week.average(key)),week.count(key),n(week.goal(key))),15,false);if(week.partial(key))ProgressUi.text(this,c,getString(R.string.pr_partial),14,false);}ProgressUi.text(this,c,getString(R.string.pr_goal_note),14,false);}
    private void macros(){String[] keys={"carbohydrate_g","fat_g","protein_g"};int[] colors={ProgressUi.TEAL,ProgressUi.PURPLE,ProgressUi.AMBER};for(int i=0;i<3;i++){LinearLayout c=ProgressUi.card(this,body,label(keys[i]));summary(c,keys[i]);bars(c,keys[i],false,week.goal(keys[i]),colors[i]);ProgressUi.text(this,c,getString(R.string.pr_recorded_total,n(week.sum(keys[i]))),16,false);}
        LinearLayout c=ProgressUi.card(this,body,getString(R.string.pr_macro_share));if(!week.completeMacros()){ProgressUi.text(this,c,getString(R.string.pr_macro_incomplete),15,false);return;}double[] energy={week.sum(keys[0])*4,week.sum(keys[1])*9,week.sum(keys[2])*4};double total=energy[0]+energy[1]+energy[2];if(total<=0){ProgressUi.text(this,c,getString(R.string.pr_macro_incomplete),15,false);return;}ProgressUi.chart(this,c,200).shares(energy,colors);for(int i=0;i<3;i++)ProgressUi.text(this,c,label(keys[i])+": "+n(100*energy[i]/total)+"%",16,false).setTextColor(new int[]{0xFF0F716D,0xFF681293,0xFF946000}[i]);ProgressUi.text(this,c,getString(R.string.pr_macro_share_note),14,false);}
    private void weightCard(){LinearLayout c=ProgressUi.card(this,body,getString(R.string.pr_weight));if(weights==null)ProgressUi.text(this,c,getString(weightFailed?R.string.wh_load_error:R.string.fl_loading),16,false);else if(weights.isEmpty())ProgressUi.text(this,c,getString(R.string.wh_empty),16,false);else{WeightHistoryService.Entry first=weights.firstEntry().getValue(),last=weights.lastEntry().getValue();ProgressUi.text(this,c,getString(R.string.wh_summary,first.day,n(first.kg),last.day,n(last.kg),n(last.kg-first.kg)),17,true);ProgressUi.chart(this,c,200).weights(new ArrayList<>(weights.values()));}if(week!=null){Double profileWeight=FoodNutrition.number(week.profile.get("weight"));if(profileWeight!=null&&profileWeight>0)ProgressUi.text(this,c,getString(R.string.wh_profile,n(profileWeight)),14,false);}
        ProgressUi.button(this,c,R.string.wh_open,()->startActivity(new Intent(this,WeightHistoryActivity.class)));}
    @Override protected void onResume(){super.onResume();if(uid!=null)load();}
    @Override protected void onStop(){version++;super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(uid==null)return;state.putString("uid",uid);state.putString("start",start.toString());state.putString("observedDay",observedDay);state.putInt("tab",tab);state.putBoolean("net",net);state.putString("csv",pendingCsv);}
}
