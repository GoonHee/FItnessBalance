package com.example.fitnessbalance;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

/** Owns in-flight operations across rotation without retaining an Activity or password. */
public final class AuthFlowViewModel extends ViewModel {
    static final class State {
        final boolean busy, gate, home, needsProfile;
        final String message;
        State(boolean busy, boolean gate, boolean home, boolean needsProfile, String message) {
            this.busy=busy; this.gate=gate; this.home=home; this.needsProfile=needsProfile; this.message=message;
        }
    }
    final MutableLiveData<State> state = new MutableLiveData<>(new State(false,false,false,false,""));
    private final FirebaseAuth auth=FirebaseAuth.getInstance();
    private boolean busy, cleared;
    private static final Map<String,Long> LAST_SENT = new HashMap<>();
    private void sent(FirebaseUser user) { LAST_SENT.put(user.getUid(),android.os.SystemClock.elapsedRealtime()); }
    private String operationUid;
    private void emit(boolean gate, boolean home, boolean profile, String message) {
        if(!cleared) state.setValue(new State(busy,gate,home,profile,message));
    }
    private boolean begin() { if(busy)return false; busy=true;emit(false,false,false,"");return true; }
    private boolean same(FirebaseUser user) { return user!=null && auth.getCurrentUser()!=null && user.getUid().equals(auth.getCurrentUser().getUid()); }
    private void end(String message, boolean gate, boolean home, boolean profile) { busy=false;emit(gate,home,profile,message); }
    void login(String email,String password) {
        if(!begin())return;
        auth.signInWithEmailAndPassword(email,password).addOnCompleteListener(task->{
            end(task.isSuccessful()?"":AuthInput.error(task.getException(),"Incorrect email or password. Please try again."),task.isSuccessful(),false,false);
        });
    }
    void reset(String email) {
        if(!begin())return;
        auth.sendPasswordResetEmail(email).addOnCompleteListener(task->{
            Exception error=task.getException();
            if(task.isSuccessful() || error instanceof FirebaseAuthInvalidUserException) end(AuthInput.RESET_ACCEPTED,false,false,false);
            else end(AuthInput.error(error,"Could not process the reset request. Try again later."),false,false,false);
        });
    }
    void register(String name,String email,String password,double weight,String level) {
        if(!AuthInput.validName(name) || !AuthInput.validWeight(weight) || !AuthInput.validLevel(level)){end("Check your profile details.",false,false,false);return;}
        if(!begin()) return;
        auth.createUserWithEmailAndPassword(email,password).addOnCompleteListener(task->{
            if(!task.isSuccessful()){ end(AuthInput.error(task.getException(),"Could not create account. If you already registered, sign in to continue setup."),false,false,false);return; }
            FirebaseUser user=task.getResult().getUser();
            if(!same(user)){end("Account changed. Please sign in again.",false,false,false);return;}
            saveProfile(user,name,weight,level,()->user.sendEmailVerification().addOnCompleteListener(sent->{
                if(sent.isSuccessful())sent(user);
                end(sent.isSuccessful()?"Verification email sent. Open the link, then check verification here.":AuthInput.error(sent.getException(),"Account created. Verification email could not be sent; use Resend verification."),true,false,false);
            }),error->end(AuthInput.error(error,"Account created, but profile setup was not saved. Continue setup below."),true,false,true));
        });
    }
    private void saveProfile(FirebaseUser user,String name,double weight,String level,Runnable success,java.util.function.Consumer<Exception> failure) {
        DocumentReference ref=FirebaseFirestore.getInstance().collection("users").document(user.getUid());
        FirebaseFirestore.getInstance().runTransaction(tx->{
            if(!same(user))throw new IllegalStateException("Account changed");
            DocumentSnapshot old=tx.get(ref);
            Map<String,Object> fields=new HashMap<>();fields.put("fullName",name);fields.put("weight",weight);fields.put("fitnessLevel",level);fields.put("email",user.getEmail());
            if(!old.exists()){fields.put("createdAt",FieldValue.serverTimestamp());fields.put("nutritionGoalSource","not_set");}
            tx.set(ref,fields,SetOptions.merge());return null;
        }).addOnSuccessListener(v->{if(same(user))success.run();else end("Account changed. Please sign in again.",false,false,false);}).addOnFailureListener(failure::accept);
    }
    void repair(String name,double weight,String level) {
        if(!begin())return;
        FirebaseUser user=auth.getCurrentUser();
        if(user==null){end("Please sign in again.",false,false,true);return;}
        saveProfile(user,name,weight,level,()->{busy=false;check();},e->end(AuthInput.error(e,"Profile could not be saved. Please retry."),false,false,true));
    }
    void check() {
        if(!begin())return;
        FirebaseUser user=auth.getCurrentUser();
        if(user==null){end("Please sign in again.",false,false,false);return;}
        operationUid=user.getUid();
        user.reload().continueWithTask(task->{
            if(!task.isSuccessful())throw task.getException();
            if(!same(user))throw new IllegalStateException("Account changed");
            return user.getIdToken(true);
        }).continueWithTask(task->{
            if(!task.isSuccessful())throw task.getException();
            if(!same(user))throw new IllegalStateException("Account changed");
            return FirebaseFirestore.getInstance().collection("users").document(operationUid).get(Source.SERVER);
        }).addOnCompleteListener(task->{
            if(!same(user)){end("Please sign in again.",false,false,false);return;}
            if(!task.isSuccessful()){end(AuthInput.error(task.getException(),"Could not check your account. Retry or sign out."),false,false,false);return;}
            boolean missing=!AuthInput.completeProfile(task.getResult().getData());
            end(missing?"Complete your profile to finish setup.":user.isEmailVerified()?"":"Verify your email, then tap Check verification. You can resend the email if needed.",false,!missing&&user.isEmailVerified(),missing);
        });
    }
    void resend() {
        if (busy) return;
        FirebaseUser user=auth.getCurrentUser();if(user==null)return;
        long now=android.os.SystemClock.elapsedRealtime();
        Long lastSent=LAST_SENT.get(user.getUid());
        if(lastSent!=null && now-lastSent<60000){emit(false,false,state.getValue().needsProfile,"Please wait one minute before resending.");return;}
        if(!begin())return;
        user.sendEmailVerification().addOnCompleteListener(task->{
            if(task.isSuccessful())sent(user);
            end(task.isSuccessful()?"Verification email sent. Check your inbox and spam folder.":AuthInput.error(task.getException(),"Could not send verification email. Try again later."),false,false,false);
        });
    }
    @Override protected void onCleared(){cleared=true;}
}
