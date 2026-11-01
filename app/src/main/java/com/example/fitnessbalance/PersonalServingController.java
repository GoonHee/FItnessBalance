package com.example.fitnessbalance;

import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.function.*;

/** Named measurements only; never changes a food's nutrition basis. */
final class PersonalServingController {
    private final AppCompatActivity activity;
    private final Supplier<String> foodKey,unit;
    private final DoubleSupplier amount;
    private final BiConsumer<Double,String> apply;
    private boolean busy;
    PersonalServingController(AppCompatActivity activity,Supplier<String> key,DoubleSupplier amount,Supplier<String> unit,BiConsumer<Double,String> apply){this.activity=activity;this.foodKey=key;this.amount=amount;this.unit=unit;this.apply=apply;}
    private String uid(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();if(u==null)throw new IllegalStateException("Sign in first.");return u.getUid();}
    private boolean current(String owner,String key){try{return !activity.isFinishing()&&!activity.isDestroyed()&&owner.equals(uid())&&key.equals(foodKey.get());}catch(RuntimeException e){return false;}}
    private CollectionReference collection(String owner){return FirebaseFirestore.getInstance().collection("users").document(owner).collection("personalServings");}
    void save(){if(busy)return;try{edit(uid(),foodKey.get(),null,amount.getAsDouble(),unit.get());}catch(RuntimeException e){error(e);}}
    void choose(){if(busy)return;try{page(uid(),foodKey.get(),null);}catch(RuntimeException e){error(e);}}
    private void page(String owner,String key,DocumentSnapshot after){busy=true;Query q=collection(owner).whereEqualTo("foodKey",key).limit(30);if(after!=null)q=q.startAfter(after);
        q.get(Source.SERVER).addOnCompleteListener(task->{busy=false;if(!current(owner,key))return;if(!task.isSuccessful()){error(task.getException());return;}
            List<DocumentSnapshot> docs=task.getResult().getDocuments();if(docs.isEmpty()){Toast.makeText(activity,R.string.ps_empty,Toast.LENGTH_LONG).show();return;}
            List<String> names=new ArrayList<>();for(DocumentSnapshot doc:docs)names.add(doc.getString("name")+" · "+FoodUi.number(doc.get("amount"))+" "+doc.getString("unit"));boolean more=docs.size()==30;if(more)names.add(activity.getString(R.string.ps_more));
            new AlertDialog.Builder(activity).setTitle(R.string.ps_choose).setItems(names.toArray(new String[0]),(dialog,index)->{if(!current(owner,key))return;if(index==docs.size()){page(owner,key,docs.get(docs.size()-1));return;}DocumentSnapshot doc=docs.get(index);
                new AlertDialog.Builder(activity).setTitle(names.get(index)).setItems(new String[]{activity.getString(R.string.ps_use),activity.getString(R.string.ps_edit),activity.getString(R.string.ps_delete)},(d,action)->{
                    if(!current(owner,key)||busy)return;try{if(action==0){double a=doc.getDouble("amount");String u=doc.getString("unit");PersonalServingService.validate(doc.getString("name"),a,u);apply.accept(a,u);}else if(action==1)edit(owner,key,doc,doc.getDouble("amount"),doc.getString("unit"));else delete(owner,key,doc);}catch(RuntimeException invalid){error(invalid);}
                }).show();}).show();
        });}
    private void edit(String owner,String key,DocumentSnapshot old,double measure,String measureUnit){PersonalServingService.validate("portion",measure,measureUnit);
        LinearLayout form=ProgressUi.column(activity);EditText name=new EditText(activity);name.setHint(R.string.ps_name);if(old!=null)name.setText(old.getString("name"));form.addView(name);
        ProgressUi.text(activity,form,activity.getString(R.string.ps_amount)+" ("+measureUnit+")",16,true);EditText value=new EditText(activity);value.setInputType(8194);value.setText(String.valueOf(measure));form.addView(value);ProgressUi.text(activity,form,activity.getString(R.string.ps_note),14,false);
        DocumentReference ref=old==null?collection(owner).document():old.getReference();
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.ps_save).setView(form).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.sm_save,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(!current(owner,key)||busy)return;try{String title=SavedMealService.name(name.getText().toString());double a=Double.parseDouble(value.getText().toString());PersonalServingService.validate(title,a,measureUnit);busy=true;
            FirebaseFirestore.getInstance().runTransaction(tx->{DocumentSnapshot existing=tx.get(ref);
                if(old==null&&existing.exists()){
                    if(title.equals(existing.getString("name"))&&Double.compare(a,existing.getDouble("amount"))==0&&measureUnit.equals(existing.getString("unit")))return null;
                    throw new IllegalStateException("Serving already saved. Reopen it before changing it.");
                }
                if(old!=null&&(!existing.exists()||!Objects.equals(existing.getLong("revision"),old.getLong("revision"))))throw new IllegalStateException("Serving changed. Reopen it.");
                Map<String,Object> data=new HashMap<>();data.put("name",title);data.put("amount",a);data.put("unit",measureUnit);data.put("foodKey",key);data.put("revision",old==null?1:old.getLong("revision")+1);
                data.put("createdAt",old==null?FieldValue.serverTimestamp():existing.get("createdAt"));data.put("updatedAt",FieldValue.serverTimestamp());tx.set(ref,data);return null;
            }).addOnCompleteListener(task->{busy=false;if(!current(owner,key))return;if(task.isSuccessful()){dialog.dismiss();Toast.makeText(activity,R.string.ps_saved,Toast.LENGTH_SHORT).show();}else error(task.getException());});
        }catch(RuntimeException invalid){value.setError(invalid.getMessage());}}));dialog.show();}
    private void delete(String owner,String key,DocumentSnapshot old){new AlertDialog.Builder(activity).setTitle(R.string.ps_delete).setMessage(R.string.ps_delete_note).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.ps_delete,(d,w)->{
        if(!current(owner,key)||busy)return;busy=true;FirebaseFirestore.getInstance().runTransaction(tx->{DocumentSnapshot current=tx.get(old.getReference());if(!current.exists())return null;if(!Objects.equals(current.getLong("revision"),old.getLong("revision")))throw new IllegalStateException("Serving changed. Reopen it.");tx.delete(old.getReference());return null;
        }).addOnCompleteListener(task->{busy=false;if(!current(owner,key))return;if(task.isSuccessful())Toast.makeText(activity,R.string.ps_delete,Toast.LENGTH_SHORT).show();else error(task.getException());});}).show();}
    private void error(Exception e){Toast.makeText(activity,e==null?"Serving operation failed.":Objects.toString(e.getMessage(),"Serving operation failed."),Toast.LENGTH_LONG).show();}
}
