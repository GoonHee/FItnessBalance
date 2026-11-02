package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.health.connect.client.HealthConnectClient;
import com.example.fitnessbalance.health.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.text.DateFormat;
import java.time.*;
import java.util.*;

public final class WearableWorkoutsActivity extends AppCompatActivity {
    private String uid;private int version,shown=50;private boolean busy,permitted,comparisonReady;
    private HealthConnectBridge bridge;private WearableWorkoutStore store;private WearableWorkoutStore.Snapshot snapshot;
    private LinearLayout planPanel,body;private TextView status,permission;private Button connect,sync;
    private final Map<String,String> sourceLabels=new HashMap<>();
    private final List<long[]> guided=new ArrayList<>();private final Set<String> manualDays=new HashSet<>();
    private final ActivityResultLauncher<Set<String>> permissions=registerForActivityResult(HealthConnectBridge.permissionContract(),result->refresh());
    @Override protected void onCreate(Bundle state){super.onCreate(state);FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){finish();return;}uid=user.getUid();bridge=new HealthConnectBridge(this);store=new WearableWorkoutStore(this);
        LinearLayout root=ProgressUi.column(this);root.setId(R.id.foodRoot);setContentView(root);ProgressUi.theme(this,root);FoodUi.insets(this);
        com.google.android.material.appbar.MaterialToolbar toolbar=new com.google.android.material.appbar.MaterialToolbar(this);toolbar.setTitle(R.string.ww_title);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);toolbar.setNavigationOnClickListener(v->finish());ProgressUi.toolbar(this,toolbar);root.addView(toolbar);
        ScrollView scroll=new ScrollView(this);root.addView(scroll);LinearLayout page=ProgressUi.column(this);int p=ProgressUi.dp(this,16);page.setPadding(p,p,p,p);scroll.addView(page);
        ProgressUi.hero(this,page,R.string.ww_title,R.string.ww_intro);permission=ProgressUi.text(this,page,"",16,true);
        connect=ProgressUi.button(this,page,R.string.hc_connect,()->{if(busy)return;try{if(HealthConnectBridge.availability(this)==HealthConnectClient.SDK_AVAILABLE)permissions.launch(new HashSet<>(Arrays.asList(HealthConnectBridge.EXERCISE,HealthConnectBridge.ACTIVE_CALORIES)));else startActivity(new Intent(this,HealthConnectActivity.class));}catch(RuntimeException e){status.setText(R.string.hc_error);}});
        sync=ProgressUi.button(this,page,R.string.ww_sync,this::sync);ProgressUi.button(this,page,R.string.hc_retry,this::refresh);
        ProgressUi.button(this,page,R.string.hc_settings,()->{try{startActivity(HealthConnectBridge.settings(this));}catch(RuntimeException e){status.setText(R.string.hc_error);}});
        ProgressUi.button(this,page,R.string.ww_clear,()->{if(busy)return;version++;bridge.cancelPending();try{store.clear(uid);snapshot=null;status.setText(R.string.ww_cleared);render();}catch(RuntimeException e){status.setText(R.string.hc_cache_error);}});
        planPanel=ProgressUi.column(this);page.addView(planPanel);
        ProgressUi.text(this,page,getString(R.string.ww_note),15,false);status=ProgressUi.text(this,page,"",15,false);body=ProgressUi.column(this);page.addView(body);
        sync.setEnabled(false);
    }
    private boolean same(){FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();return user!=null&&uid!=null&&uid.equals(user.getUid());}
    private boolean current(int token){return token==version&&same()&&!isDestroyed()&&!isFinishing();}
    private void refresh(){if(bridge==null||!same())return;int token=++version;bridge.cancelPending();comparisonReady=false;guided.clear();manualDays.clear();busy=true;permitted=false;snapshot=null;body.removeAllViews();permission.setText(R.string.hc_permissions_checking);controls();
        try{if(HealthConnectBridge.availability(this)!=HealthConnectClient.SDK_AVAILABLE){busy=false;status.setText(R.string.hc_unavailable);controls();return;}}catch(RuntimeException e){busy=false;status.setText(R.string.hc_error);controls();return;}
        bridge.check(new HealthConnectBridge.Callback(){public void onResult(Map<String,? extends Object> ignored,Set<String> granted){if(!current(token))return;permitted=granted.contains(HealthConnectBridge.EXERCISE);busy=false;
            try{if(!permitted)store.clear(uid);else if(!granted.contains(HealthConnectBridge.ACTIVE_CALORIES))store.removeCalories(uid);snapshot=permitted?store.read(uid):null;status.setText("");render();if(permitted&&snapshot!=null&&!snapshot.records.isEmpty())compare(token);}catch(RuntimeException e){status.setText(R.string.hc_cache_error);controls();}}
            public void onError(){if(!current(token))return;busy=false;permission.setText(R.string.hc_permissions_unavailable);status.setText(R.string.hc_error);controls();}
        });
    }
    private void sync(){if(busy||!permitted||!same())return;int token=++version;comparisonReady=false;guided.clear();manualDays.clear();busy=true;controls();status.setText(R.string.hc_syncing);
        bridge.syncWorkouts(new HealthConnectBridge.Callback(){public void onResult(Map<String,? extends Object> data,Set<String> granted){if(!current(token))return;permitted=granted.contains(HealthConnectBridge.EXERCISE);
            try{if(!permitted){store.clear(uid);snapshot=null;}else{store.apply(uid,com.example.fitnessbalance.util.Values.map(data.get("workouts")));snapshot=store.read(uid);}busy=false;shown=50;status.setText("");render();if(permitted&&snapshot!=null&&!snapshot.records.isEmpty())compare(token);}
            catch(RuntimeException e){busy=false;try{store.failed(uid);snapshot=store.read(uid);}catch(RuntimeException ignored){snapshot=null;}status.setText(R.string.hc_cache_error);render();}}
            public void onError(){if(!current(token))return;busy=false;permitted=false;snapshot=null;try{store.failed(uid);}catch(RuntimeException ignored){}status.setText(R.string.hc_error);render();}
        });
    }
    private void controls(){sync.setEnabled(!busy&&permitted);connect.setEnabled(!busy);permission.setText(getString(R.string.ww_permission,getString(permitted?R.string.hc_granted:R.string.hc_not_granted)));}
    private String date(Object millis){return millis instanceof Number&&((Number)millis).longValue()>0?DateFormat.getDateTimeInstance().format(new Date(((Number)millis).longValue())):getString(R.string.fl_missing);}
    private String source(String pkg){if(sourceLabels.containsKey(pkg))return sourceLabels.get(pkg);String label;try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0))+" ("+pkg+")";}catch(Exception ignored){label=pkg;}sourceLabels.put(pkg,label);return label;}
    private void render(){controls();renderPlan();body.removeAllViews();if(!permitted||snapshot==null)return;
        if("error".equals(snapshot.meta.get("status")))ProgressUi.text(this,body,getString(R.string.ww_failed),15,true);
        Object synced=snapshot.meta.get("syncedAt");if(synced==null){ProgressUi.text(this,body,getString(R.string.ww_never),16,false);return;}
        ProgressUi.text(this,body,getString(R.string.ww_last,date(synced),snapshot.records.size()),17,true);
        ProgressUi.text(this,body,getString(R.string.ww_window,date(snapshot.meta.get("windowStart")),date(snapshot.meta.get("windowEnd"))),14,false);
        if(HealthSummaryPolicy.stale(synced,System.currentTimeMillis()))ProgressUi.text(this,body,getString(R.string.hc_stale),15,true);
        if(snapshot.records.isEmpty()){ProgressUi.text(this,body,getString(R.string.ww_none),16,false);return;}
        if(!comparisonReady)ProgressUi.text(this,body,getString(R.string.ww_checks_loading),14,false);
        for(int i=0;i<Math.min(shown,snapshot.records.size());i++){
            Map<String,Object> record=snapshot.records.get(i);long from=WearableWorkoutService.number(record.get("start")),to=WearableWorkoutService.number(record.get("end"));
            LinearLayout card=ProgressUi.accentCard(this,body,Objects.toString(record.get("activity"),"Workout"),ProgressUi.TEAL);
            String title=Objects.toString(record.get("title"),"");if(!title.isEmpty())ProgressUi.text(this,card,title,16,true);
            ProgressUi.text(this,card,date(from)+" — "+date(to),15,false);ProgressUi.text(this,card,getString(R.string.ww_elapsed,ProgressUi.number(this,(to-from)/60000.0)),17,true);
            ProgressUi.text(this,card,getString(R.string.ww_source,source((String)record.get("source"))),14,false);ProgressUi.text(this,card,getString(R.string.ww_updated,date(record.get("modified"))),14,false);ProgressUi.text(this,card,calories(record),14,false);
            boolean overlap=false;for(long[] interval:guided)overlap|=WearableWorkoutService.overlaps(from,to,interval[0],interval[1]);if(overlap)ProgressUi.text(this,card,getString(R.string.ww_overlap),15,true);
            boolean manual=false;for(String day:WearableWorkoutService.days(from,to,ZoneId.systemDefault()))manual|=manualDays.contains(day);if(manual)ProgressUi.text(this,card,getString(R.string.ww_manual),14,false);
            for(Map<String,Object> other:snapshot.records)if(!record.get("id").equals(other.get("id"))&&WearableWorkoutService.overlaps(from,to,WearableWorkoutService.number(other.get("start")),WearableWorkoutService.number(other.get("end")))){ProgressUi.text(this,card,getString(R.string.ww_other),14,false);break;}
            if(WearableWorkoutLinkService.matchesPlan(store.plan(uid),record))ProgressUi.button(this,card,R.string.ww_confirm,()->confirm(record));
            ProgressUi.button(this,card,R.string.ww_review,()->startActivity(new Intent(this,ExerciseActivity.class).putExtra("date",Instant.ofEpochMilli(from).atZone(ZoneId.systemDefault()).toLocalDate().toString())));
        }
        if(shown<snapshot.records.size())ProgressUi.button(this,body,R.string.ww_more,()->{shown+=50;render();});
    }
    private String calories(Map<String,Object> row){return "available".equals(row.get("calorieState"))?getString(R.string.ww_energy,ProgressUi.number(this,((Number)row.get("activeCalories")).doubleValue())):getString(R.string.ww_energy_missing,Objects.toString(row.get("calorieState"),"unavailable"));}
    private void renderPlan(){planPanel.removeAllViews();if(!permitted)return;Map<String,Object> plan=store.plan(uid);
        LinearLayout card=ProgressUi.accentCard(this,planPanel,getString(R.string.ww_track),ProgressUi.TEAL);
        if(!plan.isEmpty()){
            ProgressUi.text(this,card,getString(R.string.ww_waiting,plan.get("activity"),date(plan.get("startedAt"))),17,true);
            ProgressUi.text(this,card,getString(R.string.ww_band_steps),15,false);
            ProgressUi.button(this,card,R.string.ww_cancel_plan,()->{if(busy)return;store.cancelPlan(uid);render();});return;
        }
        int[] types={androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_YOGA,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_PILATES,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_HIKING,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT};
        String[] names=new String[types.length];for(int i=0;i<types.length;i++)names[i]=HealthConnectBridge.activityName(types[i]);Spinner choice=new Spinner(this);choice.setContentDescription(getString(R.string.ww_choose));choice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));card.addView(choice);
        ProgressUi.text(this,card,getString(R.string.ww_band_steps),15,false);
        ProgressUi.button(this,card,R.string.ww_start_plan,()->{if(busy)return;int i=choice.getSelectedItemPosition();store.startPlan(uid,types[i],names[i],System.currentTimeMillis());render();});
    }
    private void confirm(Map<String,Object> record){if(busy||!same()||!permitted)return;
        if(snapshot==null||!(snapshot.meta.get("syncedAt") instanceof Number)||(System.currentTimeMillis()<((Number)snapshot.meta.get("syncedAt")).longValue()||System.currentTimeMillis()-((Number)snapshot.meta.get("syncedAt")).longValue()>5*60000L)||"error".equals(snapshot.meta.get("status"))){status.setText(R.string.ww_sync_first);return;}
        Map<String,Object> plan=store.plan(uid);if(!WearableWorkoutLinkService.matchesPlan(plan,record))return;
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.ww_confirm)
            .setMessage(getString(R.string.ww_confirm_note)+"\n\n"+record.get("activity")+"\n"+date(record.get("start"))+"\n"+calories(record))
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.ww_confirm,(d,w)->saveConfirmed(plan,record)).show();
    }
    private void saveConfirmed(Map<String,Object> plan,Map<String,Object> record){if(busy||!same())return;busy=true;controls();int token=++version;
        bridge.check(new HealthConnectBridge.Callback(){public void onResult(Map<String,? extends Object> ignored,Set<String> granted){if(!current(token))return;
            if(!granted.contains(HealthConnectBridge.EXERCISE)){busy=false;refresh();return;}
            Map<String,Object> selected=new HashMap<>(record);if(!granted.contains(HealthConnectBridge.ACTIVE_CALORIES)){selected.remove("activeCalories");selected.put("calorieState","permission_required");store.removeCalories(uid);}
            new com.example.fitnessbalance.repository.ExerciseLogRepository(uid).confirmWearable(plan,selected).addOnCompleteListener(WearableWorkoutsActivity.this,task->{if(!current(token))return;busy=false;
                if(task.isSuccessful()){store.cancelPlan(uid);status.setText(R.string.ww_saved);render();compare(token);}
                else{status.setText(getString(R.string.ww_save_failed,task.getException()==null?"Unknown error":task.getException().getMessage()));render();}
            });
        }public void onError(){if(!current(token))return;busy=false;status.setText(R.string.hc_error);controls();}});
    }
    private void compare(int token){comparisonReady=false;guided.clear();manualDays.clear();long start=WearableWorkoutService.number(snapshot.meta.get("windowStart"));String first=Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate().toString(),last=Instant.ofEpochMilli(WearableWorkoutService.number(snapshot.meta.get("windowEnd"))).atZone(ZoneId.systemDefault()).toLocalDate().toString();
        DocumentReference user=FirebaseFirestore.getInstance().collection("users").document(uid);
        Task<QuerySnapshot> sessions=user.collection("workoutRecords").whereGreaterThanOrEqualTo("completedAt",new Date(start)).limit(501).get(Source.SERVER);
        Task<QuerySnapshot> manual=user.collection("exerciseLogs").whereGreaterThanOrEqualTo(FieldPath.documentId(),first).whereLessThanOrEqualTo(FieldPath.documentId(),last).limit(10).get(Source.SERVER);
        Tasks.whenAll(sessions,manual).addOnCompleteListener(this,task->{if(!current(token))return;comparisonReady=true;
            if(!task.isSuccessful()||sessions.getResult().size()>500||manual.getResult().size()>=10){status.setText(R.string.ww_checks_failed);render();return;}
            try{
            for(DocumentSnapshot doc:sessions.getResult()){com.google.firebase.Timestamp a=doc.getTimestamp("startedAt"),b=doc.getTimestamp("completedAt");if(a!=null&&b!=null)guided.add(new long[]{a.toDate().getTime(),b.toDate().getTime()});}
            for(DocumentSnapshot doc:manual.getResult()){List<Map<String,Object>> entries=new com.example.fitnessbalance.service.ExerciseLogService().entries(doc.getId(),doc.getData());for(Map<String,Object> entry:entries)if(Boolean.TRUE.equals(entry.get("completed"))){manualDays.add(doc.getId());break;}}
            render();
            }catch(RuntimeException invalid){guided.clear();manualDays.clear();status.setText(R.string.ww_checks_failed);render();}
        });
    }
    @Override protected void onResume(){super.onResume();if(uid!=null){if(!same())finish();else refresh();}}
    @Override protected void onStop(){version++;if(bridge!=null)bridge.cancelPending();super.onStop();}
    @Override protected void onDestroy(){if(bridge!=null)bridge.close();if(store!=null)store.close();super.onDestroy();}
}
