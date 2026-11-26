package com.example.fitnessbalance.repository;

import android.content.Context;
import com.example.fitnessbalance.health.HealthSummaryStore;
import com.example.fitnessbalance.service.ContextualBanditService;
import com.example.fitnessbalance.util.Values;
import java.io.IOException;
import java.util.*;

/** Local per-account learning, deliberately no health data or Firestore model writes. */
public final class BanditStore {
  public static final String EXTRA="cmabDecision";
  private final HealthSummaryStore cache;
  public BanditStore(Context context,String uid){cache=new HealthSummaryStore(context,uid,"workout_bandit");}
  public boolean enabled(){return enabled(cache.read());}

  static boolean enabled(Map<String,Object> state) {
    return !Boolean.FALSE.equals(state.get("enabled"));
  }

  public void reset() throws IOException {
    synchronized (BanditStore.class) {
      cache.write(Values.of("enabled", enabled(cache.read())));
    }
  }
  public void enable(boolean enabled)throws IOException{ synchronized(BanditStore.class){
    Map<String,Object> state=cache.read();if(!enabled)state.clear();state.put("enabled",enabled);cache.write(state);
  }}
  public List<Map<String,Object>> observations(){return rows(cache.read().get("observations"));}
  public String record(Map<String,Object> audit)throws IOException{ synchronized(BanditStore.class){
    Map<String,Object> state=cache.read();if(!enabled(state))return null;
    Map<String,Object> decisions=Values.map(state.get("decisions"));
    // Only unstarted review decisions can be evicted here; pending sessions use their own map.
    if(decisions.size()>=8)decisions.clear();
    String id=UUID.randomUUID().toString();decisions.put(id,new HashMap<>(audit));state.put("decisions",decisions);cache.write(state);return id;
  }}
  public void bind(String id,String session,String lesson)throws IOException{ synchronized(BanditStore.class){
    if(id==null)return;Map<String,Object> state=cache.read();if(!enabled(state))return;
    Map<String,Object> decisions=Values.map(state.get("decisions"));Map<String,Object> d=Values.map(decisions.get(id));
    long created=d.get("createdAt") instanceof Number?((Number)d.get("createdAt")).longValue():0;
    if(!lesson.equals(d.get("lessonId"))||created>System.currentTimeMillis()||System.currentTimeMillis()-created>86400000L)return;
    Map<String,Object> pending=Values.map(state.get("pending"));if(pending.size()>=8){
      String oldest=null;long time=Long.MAX_VALUE;
      for(String key:pending.keySet()){Object value=Values.map(pending.get(key)).get("createdAt");long t=value instanceof Number?((Number)value).longValue():0;
        if(t<time){time=t;oldest=key;}}
      if(oldest!=null)pending.remove(oldest);
    }
    pending.put(session,d);decisions.remove(id);state.put("pending",pending);state.put("decisions",decisions);cache.write(state);
  }}
  public void learn(List<Map<String,Object>> records)throws IOException{ synchronized(BanditStore.class){
    Map<String,Object> state=cache.read();if(!enabled(state))return;
    Map<String,Object> pending=Values.map(state.get("pending"));List<Map<String,Object>> observations=rows(state.get("observations"));
    boolean changed = observations.removeIf(row -> !ContextualBanditService.VERSION.equals(row.get("version")));
    changed |= pending.values().removeIf(row -> !ContextualBanditService.VERSION.equals(Values.map(row).get("version")));
    for(Map<String,Object> r:records){String id=Values.text(r.get("recordId"),"");Map<String,Object> d=Values.map(pending.get(id));
      if (!d.isEmpty() && ContextualBanditService.credit(id, d, r, observations)) {
        pending.remove(id);
        changed = true;
      }
    }
    if (!changed) return;
    while(observations.size()>80)observations.remove(0);
    state.put("pending",pending);state.put("observations",observations);cache.write(state);
  }}
  private static List<Map<String,Object>> rows(Object v){List<Map<String,Object>> out=new ArrayList<>();if(v instanceof List)for(Object row:(List<?>)v)if(row instanceof Map)out.add(new HashMap<>(Values.map(row)));return out;}
}
