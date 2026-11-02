package com.example.fitnessbalance;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** A private reusable meal library, sharing the existing diary write path. */
public final class SavedMealsActivity extends AppCompatActivity {
    private String uid,date;
    private LinearLayout body;
    private TextView status;
    private Button dateButton,retry,saveFromDiary,mealsTabButton,recipesTabButton;
    private SavedMealRepository repository;
    private final List<DocumentSnapshot> meals=new ArrayList<>();
    private DocumentSnapshot cursor;
    private boolean busy,more=true,recipeTab;
    private int version;
    private Map<String,Object> pending;
    private com.example.fitnessbalance.health.HealthSummaryStore pendingStore;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){finish();return;}uid=user.getUid();
        recipeTab=state!=null&&state.getBoolean("recipes");
        repository=new SavedMealRepository(uid,recipeTab);
        pendingStore=new com.example.fitnessbalance.health.HealthSummaryStore(this,uid,"saved_meal_pending");
        Map<String,Object> cached=pendingStore.read();if(!cached.isEmpty())pending=cached;
        date=state==null?getIntent().getStringExtra("date"):state.getString("date");
        if(!FoodUi.validDate(date))date=LocalDate.now().toString();
        if(pending==null && state!=null && uid.equals(state.getString("uid"))){try{String value=state.getString("pending");if(value!=null)pending=decode(new JSONObject(value));}catch(Exception ignored){pending=null;}}
        LinearLayout root=ProgressUi.column(this);ProgressUi.theme(this,root);setContentView(root);ProgressUi.theme(this,root);FoodUi.insets(this);
        MaterialToolbar toolbar=new MaterialToolbar(this);toolbar.setTitle(R.string.library_title);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);toolbar.setNavigationOnClickListener(v->finish());root.addView(toolbar);ProgressUi.toolbar(this,toolbar);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout page=ProgressUi.column(this);int pad=ProgressUi.dp(this,16);page.setPadding(pad,pad,pad,pad);scroll.addView(page);
        LinearLayout tabs=new LinearLayout(this);page.addView(tabs);
        mealsTabButton=ProgressUi.button(this,tabs,R.string.rp_tab_meals,()->switchTab(false));
        recipesTabButton=ProgressUi.button(this,tabs,R.string.rp_tab_recipes,()->switchTab(true));
        ProgressUi.button(this,page,R.string.rp_new,()->{if(ready())startActivity(new android.content.Intent(this,RecipeEditorActivity.class));});
        dateButton=ProgressUi.button(this,page,R.string.sm_title,()->{if(ready())pick(date,value->{date=value;dateButton.setText(getString(R.string.sm_target,date));});});
        dateButton.setText(getString(R.string.sm_target,date));
        saveFromDiary=ProgressUi.button(this,page,R.string.sm_create,()->{if(ready())pick(date,this::chooseFoods);});
        ProgressUi.button(this,page,R.string.sm_refresh,()->{if(!busy)load(true);});
        retry=ProgressUi.button(this,page,R.string.sm_retry,this::submitPending);
        status=ProgressUi.text(this,page,"",14,false);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body=ProgressUi.column(this);page.addView(body);
        render();
    }
    @Override protected void onResume(){super.onResume();if(!same()){finish();return;}if(!busy)load(true);}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("recipes",recipeTab);state.putString("date",date);state.putString("uid",uid);if(pending!=null)state.putString("pending",new JSONObject(pending).toString());}
    private boolean same(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return u!=null&&uid!=null&&uid.equals(u.getUid());}
    private boolean visible(){return same()&&!isDestroyed()&&!isFinishing();}
    private boolean ready(){return visible()&&!busy&&pending==null;}
    private void pick(String initial,java.util.function.Consumer<String> action){LocalDate d=LocalDate.parse(initial);new DatePickerDialog(this,(view,y,m,day)->{if(ready())action.accept(LocalDate.of(y,m+1,day).toString());},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();}
    private void switchTab(boolean recipes){if(!ready())return;recipeTab=recipes;repository=new SavedMealRepository(uid,recipes);load(true);}
    private void load(boolean reset){
        if(!same()||busy)return;busy=true;int request=++version;
        if(reset){meals.clear();cursor=null;more=true;}
        status.setText(R.string.fl_loading);
        repository.page(cursor).addOnCompleteListener(task->{busy=false;if(!visible()||request!=version)return;
            if(task.isSuccessful()){List<DocumentSnapshot> page=task.getResult().getDocuments();meals.addAll(page);if(!page.isEmpty())cursor=page.get(page.size()-1);more=page.size()==30;status.setText("");}
            else error(task.getException());render();});
    }
    private void render(){
        if(body==null)return;
        Button[] tabs={mealsTabButton,recipesTabButton};for(int i=0;i<2;i++){boolean active=(i==1)==recipeTab;tabs[i].setSelected(active);tabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this,active?R.color.gm_purple:R.color.gm_lavender)));tabs[i].setTextColor(active?android.graphics.Color.WHITE:ProgressUi.ink(this));}
        saveFromDiary.setVisibility(recipeTab?android.view.View.GONE:android.view.View.VISIBLE);body.removeAllViews();retry.setVisibility(pending==null?android.view.View.GONE:android.view.View.VISIBLE);retry.setEnabled(!busy);
        if(pending!=null){
            ProgressUi.text(this,body,getString(R.string.sm_pending),16,true);
            ProgressUi.button(this,body,R.string.sm_discard,()->{if(!visible()||busy)return;
                new AlertDialog.Builder(this).setTitle(R.string.sm_discard).setMessage(R.string.sm_discard_note)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.sm_discard,(d,w)->{
                        if(busy||!visible())return;pendingStore.clear();pending=null;status.setText("");render();
                    }).show();});
        }
        ProgressUi.hero(this,body,R.string.ui_library_intro,R.string.ui_library_note);
        ProgressUi.text(this,body,getString(recipeTab?R.string.rp_note:R.string.sm_note),14,false);
        ProgressUi.text(this,body,getString(recipeTab?R.string.rp_tab_recipes:R.string.rp_tab_meals),22,true);
        if(meals.isEmpty())ProgressUi.text(this,body,getString(recipeTab?R.string.rp_empty:R.string.sm_empty),16,false);
        for(DocumentSnapshot meal:meals){try{
            List<Map<String,Object>> items=SavedMealService.items(meal.get("items"));
            LinearLayout card=ProgressUi.card(this,body,meal.getString("name"));
            ProgressUi.text(this,card,getString(R.string.sm_count,items.size()),14,false);
            ProgressUi.text(this,card,recipeTab?getString(R.string.rp_per_serving):getString(R.string.sm_count,items.size()),15,false);
            ProgressUi.text(this,card,summary(recipeTab?RecipeService.portions(items,meal.getDouble("yieldServings"),1):items),15,false);
            ProgressUi.button(this,card,recipeTab?R.string.rp_log:R.string.sm_log,()->{if(ready()){if(recipeTab)reviewRecipe(meal,items);else review(meal,items);}});
            ProgressUi.button(this,card,recipeTab?R.string.rp_edit:R.string.rp_from_meal,()->{if(ready())startActivity(new android.content.Intent(this,RecipeEditorActivity.class).putExtra(recipeTab?"recipeId":"savedMealId",meal.getId()));});
            ProgressUi.button(this,card,R.string.sm_rename,()->{if(ready())rename(meal);});
            ProgressUi.button(this,card,recipeTab?R.string.rp_delete:R.string.sm_delete,()->{if(ready())delete(meal);});
        }catch(RuntimeException invalid){ProgressUi.text(this,body,getString(R.string.sm_error,"Invalid saved meal. Refresh the library."),14,false);}}
        if(more)ProgressUi.button(this,body,R.string.sm_more,()->{if(!busy)load(false);});
    }
    private String summary(List<Map<String,Object>> items){Map<String,Object> total=FoodNutrition.totals(items);return FoodUi.nutrients(this,(Map<?,?>)total.get("nutrientTotals"),(Map<?,?>)total.get("nutrientCoverage"),items.size());}
    private String description(Map<String,Object> row){Object amount=row.get("totalAmount");if(amount==null)amount=row.get("portionGrams");String unit=Objects.toString(row.get("portionUnit"),row.containsKey("portionGrams")?"g":"");return Objects.toString(row.get("name"),"")+" · "+(amount==null?getString(R.string.sm_count,1):FoodUi.number(amount)+" "+unit);}
    private void chooseFoods(String sourceDate){
        if(!ready())return;busy=true;status.setText(R.string.fl_loading);
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("foodLogs").document(sourceDate).get(Source.SERVER)
            .addOnCompleteListener(task->{busy=false;if(!visible())return;if(!task.isSuccessful()){error(task.getException());return;}
                status.setText("");try{
                    List<Map<String,Object>> rows=FoodLogRepository.entries(task.getResult());if(rows.isEmpty()){status.setText(R.string.fl_empty);return;}
                    String[] names=new String[rows.size()];boolean[] selected=new boolean[rows.size()];
                    for(int i=0;i<rows.size();i++)names[i]=Objects.toString(rows.get(i).get("meal"),"")+": "+description(rows.get(i));
                    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getString(R.string.sm_pick)+" · "+sourceDate)
                        .setMultiChoiceItems(names,selected,(d,index,checked)->selected[index]=checked)
                        .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.sm_save,null).create();
                    dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
                        if(!ready())return;List<Map<String,Object>> chosen=new ArrayList<>();for(int i=0;i<rows.size();i++)if(selected[i])chosen.add(rows.get(i));
                        try{List<Map<String,Object>> snapshots=SavedMealService.snapshots(chosen);dialog.dismiss();nameMeal(snapshots);}catch(RuntimeException invalid){Toast.makeText(this,invalid.getMessage(),Toast.LENGTH_LONG).show();}
                    }));dialog.show();
                }catch(RuntimeException invalid){error(invalid);}
            });
    }
    private void nameMeal(List<Map<String,Object>> items){
        EditText name=new EditText(this);name.setHint(R.string.sm_name);name.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        String id=UUID.randomUUID().toString();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.sm_save).setView(name).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.sm_save,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(!ready())return;try{
            String title=SavedMealService.name(name.getText().toString());busy=true;status.setText(R.string.fl_loading);
            repository.create(id,title,items).addOnCompleteListener(task->{busy=false;if(!visible())return;if(task.isSuccessful()){dialog.dismiss();Toast.makeText(this,R.string.sm_saved,Toast.LENGTH_SHORT).show();load(true);}else error(task.getException());});
        }catch(RuntimeException invalid){name.setError(invalid.getMessage());}}));dialog.show();
    }
    private void review(DocumentSnapshot template,List<Map<String,Object>> items){
        LinearLayout form=ProgressUi.column(this);ProgressUi.text(this,form,getString(R.string.sm_target,date),16,true);
        Spinner meal=new Spinner(this);meal.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,getResources().getStringArray(R.array.fs_meals)));form.addView(meal);
        for(Map<String,Object> item:items)ProgressUi.text(this,form,description(item),15,false);
        ProgressUi.text(this,form,summary(items),15,false);ProgressUi.text(this,form,getString(R.string.sm_note),14,false);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        new AlertDialog.Builder(this).setTitle(template.getString("name")).setView(scroll).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.sm_log,(d,w)->{if(!ready())return;
                try{String operation=UUID.randomUUID().toString();Map<String,Object> data=new HashMap<>();data.put("operation",operation);data.put("date",date);data.put("mealId",template.getId());
                    data.put("entries",SavedMealService.loggingEntries(items,template.getId(),template.getString("name"),operation,meal.getSelectedItem().toString()));pending=data;submitPending();
                }catch(RuntimeException invalid){error(invalid);}
            }).show();
    }
    private void reviewRecipe(DocumentSnapshot recipe,List<Map<String,Object>> ingredients){
        LinearLayout form=ProgressUi.column(this);ProgressUi.text(this,form,getString(R.string.sm_target,date),16,true);
        Spinner meal=new Spinner(this);meal.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,getResources().getStringArray(R.array.fs_meals)));form.addView(meal);
        ProgressUi.text(this,form,getString(R.string.rp_count),16,true);EditText count=new EditText(this);count.setInputType(8194);count.setText("1");form.addView(count);
        TextView nutrition=ProgressUi.text(this,form,"",15,false);
        Runnable preview=()->{try{nutrition.setText(summary(RecipeService.portions(ingredients,recipe.getDouble("yieldServings"),Double.parseDouble(count.getText().toString()))));}catch(RuntimeException invalid){nutrition.setText(invalid.getMessage());}};
        ProgressUi.button(this,form,R.string.rp_preview,preview);preview.run();
        ProgressUi.text(this,form,getString(R.string.rp_note),14,false);ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(recipe.getString("name")).setView(scroll).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.rp_log,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(!ready())return;try{
            double servings=Double.parseDouble(count.getText().toString());String operation=UUID.randomUUID().toString();
            Map<String,Object> data=new HashMap<>();data.put("operation",operation);data.put("date",date);data.put("mealId","recipe:"+recipe.getId());
            data.put("entries",RecipeService.log(ingredients,recipe.getDouble("yieldServings"),servings,recipe.getId(),recipe.getString("name"),recipe.getLong("revision"),operation,meal.getSelectedItem().toString()));
            pending=data;dialog.dismiss();submitPending();
        }catch(RuntimeException invalid){count.setError(invalid.getMessage());}}));dialog.show();
    }
    private void submitPending(){
        if(!visible()||busy||pending==null)return;busy=true;render();status.setText(R.string.fl_loading);
        final Map<String,Object> request=pending;
        try{
            pendingStore.write(request);
            List<Map<String,Object>> entries=rawRows(request.get("entries"));
            FoodLogRepository.saveMeal(uid,(String)request.get("date"),(String)request.get("mealId"),(String)request.get("operation"),entries)
                .addOnCompleteListener(task->{busy=false;if(task.isSuccessful()){
                    if(Objects.equals(pendingStore.read().get("operation"),request.get("operation")))pendingStore.clear();
                    if(pending==request)pending=null;
                }if(!visible())return;
                    if(task.isSuccessful()){status.setText(R.string.sm_logged);Toast.makeText(this,R.string.sm_logged,Toast.LENGTH_LONG).show();}else error(task.getException());render();});
        }catch(Exception invalid){busy=false;error(invalid);render();}
    }
    private void rename(DocumentSnapshot meal){
        EditText name=new EditText(this);name.setHint(R.string.sm_name);name.setText(meal.getString("name"));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.sm_rename).setView(name).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.sm_save,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(!ready())return;try{
            String title=SavedMealService.name(name.getText().toString());busy=true;
            repository.rename(meal.getId(),meal.getLong("revision"),title).addOnCompleteListener(task->{busy=false;if(!visible())return;if(task.isSuccessful()){dialog.dismiss();load(true);}else error(task.getException());});
        }catch(RuntimeException invalid){name.setError(invalid.getMessage());}}));dialog.show();
    }
    private void delete(DocumentSnapshot meal){new AlertDialog.Builder(this).setTitle(recipeTab?R.string.rp_delete:R.string.sm_delete).setMessage(R.string.sm_delete_note).setNegativeButton(android.R.string.cancel,null)
        .setPositiveButton(R.string.sm_delete,(d,w)->{if(!ready())return;busy=true;
            repository.delete(meal.getId(),meal.getLong("revision")).addOnCompleteListener(task->{busy=false;if(!visible())return;if(task.isSuccessful())load(true);else error(task.getException());});}).show();}
    private void error(Exception error){status.setText(getString(R.string.sm_error,error==null?"":Objects.toString(error.getMessage(),"")));}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rawRows(Object raw){if(!(raw instanceof List))throw new IllegalArgumentException("Invalid pending meal.");List<Map<String,Object>> out=new ArrayList<>();for(Object row:(List<?>)raw){if(!(row instanceof Map))throw new IllegalArgumentException("Invalid pending food.");out.add(new HashMap<>((Map<String,Object>)row));}return out;}
    private static Map<String,Object> decode(JSONObject object)throws JSONException{Map<String,Object> map=new HashMap<>();Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();map.put(key,decodeValue(object.get(key)));}return map;}
    private static Object decodeValue(Object value)throws JSONException{if(value==JSONObject.NULL)return null;if(value instanceof JSONObject)return decode((JSONObject)value);if(value instanceof JSONArray){List<Object> list=new ArrayList<>();JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)list.add(decodeValue(array.get(i)));return list;}return value;}
}
