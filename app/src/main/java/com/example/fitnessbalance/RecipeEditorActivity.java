package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import org.json.JSONObject;
import java.util.*;

public final class RecipeEditorActivity extends AppCompatActivity {
    private String uid,id,mutation;
    private long revision;
    private boolean busy,dirty,initialized;
    private EditText name,yield;
    private TextView status;
    private LinearLayout rows;
    private final List<Map<String,Object>> ingredients=new ArrayList<>();
    private final ActivityResultLauncher<Intent> ingredient=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
        if(!same()||result.getResultCode()!=RESULT_OK||result.getData()==null)return;
        try{add(FoodJson.map(result.getData().getStringExtra("ingredient")));}catch(Exception invalid){error(invalid);}
    });
    @Override protected void onCreate(Bundle state){super.onCreate(state);FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){finish();return;}uid=user.getUid();
        id=state==null?getIntent().getStringExtra("recipeId"):state.getString("id");if(id==null)id=UUID.randomUUID().toString();
        mutation=state==null?UUID.randomUUID().toString():state.getString("mutation");
        initialized=!getIntent().hasExtra("recipeId")&&!getIntent().hasExtra("savedMealId");
        LinearLayout root=ProgressUi.column(this);root.setId(R.id.foodRoot);setContentView(root);ProgressUi.theme(this,root);FoodUi.insets(this);
        MaterialToolbar toolbar=new MaterialToolbar(this);toolbar.setTitle(R.string.rp_edit);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);toolbar.setNavigationOnClickListener(v->leave());root.addView(toolbar);ProgressUi.toolbar(this,toolbar);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout form=ProgressUi.column(this);int pad=ProgressUi.dp(this,16);form.setPadding(pad,pad,pad,pad);scroll.addView(form);
        ProgressUi.hero(this,form,R.string.rp_new,R.string.ui_recipe_intro);
        name=input(form,getString(R.string.sm_name),false);yield=input(form,getString(R.string.rp_yield),true);yield.setText("1");
        ProgressUi.text(this,form,getString(R.string.rp_note),14,false);
        ProgressUi.button(this,form,R.string.rp_add,()->{if(ready())search();});ProgressUi.button(this,form,R.string.rp_manual,()->{if(ready())manual();});
        ProgressUi.button(this,form,R.string.fl_retry,()->{if(busy||!same()||initialized)return;
            if(getIntent().hasExtra("recipeId"))load("recipes",getIntent().getStringExtra("recipeId"));
            else if(getIntent().hasExtra("savedMealId"))load("savedMeals",getIntent().getStringExtra("savedMealId"));});
        rows=ProgressUi.column(this);form.addView(rows);status=ProgressUi.text(this,form,"",15,false);
        ProgressUi.button(this,form,R.string.rp_preview,this::preview);ProgressUi.button(this,root,R.string.rp_save,this::save);
        if(state!=null&&state.getBoolean("initialized")&&uid.equals(state.getString("uid"))){try{ingredients.addAll(SavedMealService.items(FoodJson.map(state.getString("draft")).get("items")));}catch(Exception ignored){}
            name.setText(state.getString("name",""));yield.setText(state.getString("yield","1"));revision=state.getLong("revision");dirty=state.getBoolean("dirty");initialized=true;render();}
        else if(getIntent().hasExtra("recipeId"))load("recipes",getIntent().getStringExtra("recipeId"));
        else if(getIntent().hasExtra("savedMealId"))load("savedMeals",getIntent().getStringExtra("savedMealId"));
        else render();
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){public void handleOnBackPressed(){leave();}});
    }
    @Override protected void onResume(){super.onResume();if(!same())finish();}
    private boolean same(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return uid!=null&&u!=null&&uid.equals(u.getUid());}
    private boolean ready(){return initialized&&same()&&!busy&&!isDestroyed()&&!isFinishing();}
    private void leave(){if(busy)return;new AlertDialog.Builder(this).setMessage(R.string.rp_unsaved).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->finish()).show();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("uid",uid);state.putString("id",id);state.putString("mutation",mutation);state.putLong("revision",revision);state.putString("name",name.getText().toString());state.putString("yield",yield.getText().toString());Map<String,Object> data=new HashMap<>();data.put("items",ingredients);state.putString("draft",new JSONObject(data).toString());state.putBoolean("dirty",dirty);state.putBoolean("initialized",initialized);}
    private void load(String collection,String source){busy=true;status.setText(R.string.fl_loading);
        FirebaseFirestore.getInstance().collection("users").document(uid).collection(collection).document(source).get(Source.SERVER).addOnCompleteListener(task->{busy=false;if(!same()||isDestroyed()||isFinishing())return;
            try{if(!task.isSuccessful())throw task.getException();DocumentSnapshot doc=task.getResult();if(!doc.exists())throw new IllegalStateException("Recipe or meal no longer exists.");ingredients.clear();ingredients.addAll(SavedMealService.items(doc.get("items")));name.setText(doc.getString("name"));
                if(collection.equals("recipes")){revision=doc.getLong("revision");yield.setText(String.valueOf(doc.getDouble("yieldServings")));}dirty=false;initialized=true;status.setText("");render();
            }catch(Exception invalid){error(invalid);}});
    }
    private EditText input(LinearLayout form,String label,boolean numeric){ProgressUi.text(this,form,label,16,true);EditText e=new EditText(this);e.setHint(label);e.setInputType(numeric?8194:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);form.addView(e);return e;}
    private void add(Map<String,Object> entry){if(ingredients.size()>=25)throw new IllegalArgumentException("A recipe supports up to 25 ingredients.");ingredients.add(SavedMealService.snapshots(Arrays.asList(entry)).get(0));dirty=true;render();}
    private String summary(List<Map<String,Object>> items){Map<String,Object> total=FoodNutrition.totals(items);return FoodUi.nutrients(this,(Map<?,?>)total.get("nutrientTotals"),(Map<?,?>)total.get("nutrientCoverage"),items.size());}
    private void render(){rows.removeAllViews();for(int i=0;i<ingredients.size();i++){final int index=i;Map<String,Object> item=ingredients.get(i);LinearLayout card=ProgressUi.card(this,rows,Objects.toString(item.get("name"),""));
        ProgressUi.text(this,card,FoodUi.number(item.get("totalAmount"))+" "+Objects.toString(item.get("portionUnit"),"")+"\n"+summary(Arrays.asList(item)),15,false);
        ProgressUi.button(this,card,R.string.rp_scale,()->{if(ready())scale(index);});ProgressUi.button(this,card,R.string.rp_remove,()->{if(ready()){ingredients.remove(index);dirty=true;render();}});}}
    private void scale(int index){EditText factor=new EditText(this);factor.setInputType(8194);factor.setText("1");AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.rp_scale).setView(factor).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(!ready())return;try{ingredients.set(index,RecipeService.scaleIngredient(ingredients.get(index),Double.parseDouble(factor.getText().toString())));dirty=true;dialog.dismiss();render();}catch(RuntimeException invalid){factor.setError(invalid.getMessage());}}));dialog.show();}
    private void preview(){if(!ready())return;try{double count=Double.parseDouble(yield.getText().toString());status.setText(getString(R.string.rp_whole)+"\n"+summary(SavedMealService.snapshots(ingredients))+"\n\n"+getString(R.string.rp_per_serving)+"\n"+summary(RecipeService.portions(ingredients,count,1)));}catch(RuntimeException invalid){error(invalid);}}
    private void save(){if(!ready())return;try{String title=SavedMealService.name(name.getText().toString());double count=RecipeService.positive(Double.parseDouble(yield.getText().toString()));List<Map<String,Object>> items=SavedMealService.snapshots(ingredients);
        RecipeService.portions(items,count,1);busy=true;status.setText(R.string.fl_loading);
        new RecipeRepository(uid).save(id,revision,mutation,title,items,count).addOnCompleteListener(task->{busy=false;if(!ready())return;if(task.isSuccessful()){Toast.makeText(this,R.string.rp_saved,Toast.LENGTH_LONG).show();finish();}else error(task.getException());});
    }catch(RuntimeException invalid){error(invalid);}}
    private void manual(){LinearLayout form=ProgressUi.column(this);ProgressUi.text(this,form,getString(R.string.rp_manual_note),14,false);
        EditText foodName=input(form,getString(R.string.rp_name),false),amount=input(form,getString(R.string.rp_amount),true);amount.setText("100");Spinner unit=new Spinner(this);unit.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"g","ml"}));form.addView(unit);
        String[] labels={"Energy (kcal)","Protein (g)","Carbohydrate (g)","Fat (g)","Fibre (g)","Sodium (mg)"};List<EditText> values=new ArrayList<>();for(String label:labels)values.add(input(form,label,true));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.rp_manual).setView(scroll).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.rp_choose,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(!ready())return;try{Map<String,Object> row=new HashMap<>(),nutrients=new HashMap<>();row.put("name",SavedMealService.name(foodName.getText().toString()));double a=RecipeService.positive(Double.parseDouble(amount.getText().toString()));
            row.put("totalAmount",a);row.put("servingAmount",a);row.put("servings",1.0);row.put("portionUnit",unit.getSelectedItem().toString());row.put("g".equals(unit.getSelectedItem())?"portionGrams":"portionMillilitres",a);
            for(int i=0;i<values.size();i++){String text=values.get(i).getText().toString().trim();if(!text.isEmpty()){Double n=FoodNutrition.number(Double.parseDouble(text));if(n==null)throw new IllegalArgumentException("Nutrients must be finite and non-negative.");nutrients.put(FoodNutrition.KEYS[i],n);}}
            row.put("nutrients",nutrients);row.put("calculationMode","user_ingredient");row.put("energyMethod","user_entered");row.put("nutritionVerification","user_entered");row.put("sourceDatabase","User recipe ingredient");
            if(nutrients.containsKey("energy_kcal"))row.put("manualCaloriesPerServing",nutrients.get("energy_kcal"));add(row);dialog.dismiss();
        }catch(RuntimeException invalid){Toast.makeText(this,invalid.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();}
    private void search(){LinearLayout form=ProgressUi.column(this);EditText input=input(form,getString(R.string.rp_search),false);Button search=ProgressUi.button(this,form,R.string.rp_search_go,()->{});TextView message=ProgressUi.text(this,form,"",14,false);LinearLayout results=ProgressUi.column(this);form.addView(results);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.rp_add).setView(scroll).setNegativeButton(android.R.string.cancel,null).create();
        search.setOnClickListener(v->{if(!ready())return;String q=input.getText().toString().trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return;search.setEnabled(false);results.removeAllViews();message.setText(R.string.fl_loading);
            Query query=FirebaseFirestore.getInstance().collection("foods").orderBy("food_name_lowercase").startAt(q).endAt(q+"\uf8ff").limit(30);searchPage(query,dialog,results,message,search);});dialog.show();}
    private void searchPage(Query query,AlertDialog dialog,LinearLayout results,TextView message,Button search){query.get(Source.SERVER).addOnCompleteListener(task->{if(!ready()||!dialog.isShowing())return;search.setEnabled(true);if(!task.isSuccessful()){message.setText(task.getException().getMessage());return;}List<DocumentSnapshot> docs=task.getResult().getDocuments();message.setText(docs.isEmpty()?getString(R.string.fl_empty):"");
        for(DocumentSnapshot doc:docs){Button b=new Button(this);b.setText(doc.getString("food_name"));b.setAllCaps(false);results.addView(b);b.setOnClickListener(v->{if(!ready())return;dialog.dismiss();ingredient.launch(new Intent(this,FoodDetailsActivity.class).putExtra("foodId",doc.getId()).putExtra("recipeIngredient",true).putExtra("ingredientUid",uid));});}
        if(docs.size()==30){Button more=new Button(this);more.setText(R.string.fl_more);results.addView(more);more.setOnClickListener(v->{results.removeView(more);search.setEnabled(false);searchPage(query.startAfter(docs.get(docs.size()-1)),dialog,results,message,search);});}
    });}
    private void error(Exception e){status.setText(e==null?getString(R.string.sm_error,""):Objects.toString(e.getMessage(),"Operation failed."));}
}
