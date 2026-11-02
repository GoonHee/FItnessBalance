const {before,after,beforeEach,test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,getDoc,getDocFromServer,getDocs,query,collection,where,setDoc,updateDoc,deleteDoc,writeBatch,runTransaction,serverTimestamp,Timestamp}=require('firebase/firestore');
let env;
const DAY='2026-10-28',WEEK='2026-10-26',PATH='beginner_foundation';
const achievements=['first_step','routine_builder','path_explorer','welcome_back','honest_reflection','balanced_week'];
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-fitnessbalance',firestore:{host:'127.0.0.1',port:8080,rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}})});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{const db=c.firestore(),b=writeBatch(db);const seed=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../exercise-seed.json'),'utf8'));for(const [key,value]of Object.entries(seed))b.set(doc(db,key),value);for(const uid of ['alice','bob'])b.set(doc(db,`users/${uid}`),{fullName:uid,totalXp:0,level:1});await b.commit()});});
const db=(uid='alice')=>env.authenticatedContext(uid).firestore();
const ref=(d,s)=>doc(d,`users/alice/${s}`);
async function start(d,id='session1',lessonId='lesson_01') {return setDoc(ref(d,`workoutSessions/${id}`),{pathId:PATH,lessonId,plannedExercises:4,startedAt:serverTimestamp()})}
// This exercises the same server-side atomic/idempotency contract, without claiming to run Android UI code.
async function complete(d,id='session1',allAchievements=false){
 return runTransaction(d,async tx=>{
  const record=ref(d,`workoutRecords/${id}`),saved=await tx.get(record);if(saved.exists())return saved.data().xpEarned;
  const session=await tx.get(ref(d,`workoutSessions/${id}`)),user=await tx.get(doc(d,'users/alice')),progress=await tx.get(ref(d,`pathProgress/${PATH}`)),week=await tx.get(ref(d,`exerciseWeeks/${WEEK}`));
  const manual=await tx.get(ref(d,`exerciseLogs/${DAY}`)),daily=await tx.get(ref(d,`dailyData/${DAY}`));
  const qids=['planned','minutes','feedback'];const quests=[];for(const q of qids)quests.push(await tx.get(ref(d,`questProgress/${DAY}_${q}`)));
  const owned=[];for(const a of achievements)owned.push(await tx.get(ref(d,`unlockedAchievements/${a}`)));
  const u=user.data()||{},p=progress.data()||{},w=week.data()||{};const attempts=(p.completionCounts||{}).lesson_01||0,count=(w.completed||0)+1;
  const bonus=count>=3&&!w.bonusAwarded?30:0;const questBonus=quests.slice(0,2).filter(q=>!q.exists()).length*5;const xp=Math.round(10*(attempts===0?1:attempts===1?.5:.25))+2+questBonus+bonus,total=(u.totalXp||0)+xp;
  const unlocks=allAchievements?achievements:!owned[0].exists()?['first_step']:[];
  const contributions={...(daily.data()?.structuredExerciseContributions||{})};contributions[id]={calories:25,activeSeconds:300};
  const manualItems=manualRows(manual.data(),daily.data());tx.set(ref(d,`dailyData/${DAY}`),dayTotals(manualItems,contributions),{merge:true});
  for(let n=0;n<3;n++)if(!quests[n].exists())tx.set(quests[n].ref,{day:DAY,questId:qids[n],completed:true,xpEarned:n===2?0:5,recordId:id,completedAt:serverTimestamp()});
  for(const a of unlocks)if(!owned[achievements.indexOf(a)].exists())tx.set(ref(d,`unlockedAchievements/${a}`),{recordId:id,unlockedAt:serverTimestamp()});
  tx.set(ref(d,`pathProgress/${PATH}`),{completionCounts:{lesson_01:attempts+1},bestPercent:{lesson_01:100},bestStars:{lesson_01:3},updatedAt:serverTimestamp()});
  tx.set(ref(d,`exerciseWeeks/${WEEK}`),{completed:count,categories:['mixed'],bonusAwarded:!!w.bonusAwarded||bonus>0,target:3,lastRecordId:id,updatedAt:serverTimestamp()});
  tx.set(doc(d,'users/alice'),{totalXp:total,level:Math.floor(total/100)+1,lastRecordId:id,feedbackCount:(u.feedbackCount||0)+1,achievementIds:[...new Set([...(u.achievementIds||[]),...unlocks])],metGoalWeeks:bonus?[WEEK]:u.metGoalWeeks||[],lastWorkoutAt:serverTimestamp()},{merge:true});
  tx.set(record,{pathId:PATH,lessonId:'lesson_01',lessonName:'Basic Movement',startedAt:session.data().startedAt,completedAt:serverTimestamp(),plannedExercises:4,completedExercises:4,skippedExercises:0,completionPercentage:100,durationSeconds:380,activeDurationSeconds:300,estimatedCaloriesBurned:25,xpEarned:xp,starsEarned:3,difficultyFeedback:'suitable',feelingFeedback:'normal',discomfortReported:false,firstCompletion:attempts===0,totalXpAfter:total,weeklyCompleted:count,weeklyTarget:3,unlocked:unlocks,localDay:DAY,localWeek:WEEK,timeZone:'Asia/Kuala_Lumpur',category:'mixed'});
  return xp;
 }).catch(async error=>{const confirmed=await getDocFromServer(ref(d,`workoutRecords/${id}`));if(confirmed.exists())return confirmed.data().xpEarned;throw error;});
}
test('authenticated catalog read, anonymous denial',async()=>{await assertSucceeds(getDoc(doc(db(),'exercises/EX001')));await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'exercises/EX001')))});
test('ordinary users cannot edit shared exercise content',async()=>{await assertFails(updateDoc(doc(db(),'exercises/EX001'),{name:'Modified'}))});
test('one user cannot read or update another profile',async()=>{await assertFails(getDoc(doc(db(),'users/bob')));await assertFails(updateDoc(doc(db(),'users/bob'),{totalXp:100}))});
test('anonymous users cannot read private records',async()=>{await assertFails(getDoc(ref(env.unauthenticatedContext().firestore(),'workoutRecords/session1')))});
test('new registration remains compatible',async()=>{await assertSucceeds(setDoc(doc(db('new-user'),'users/new-user'),{fullName:'New',email:'new@example.test',fitnessLevel:'Beginner',weight:70,dailyCalorieGoal:2000,isVerified:false,createdAt:serverTimestamp()}))});
test('profile merge preserves progress',async()=>{await assertSucceeds(setDoc(doc(db(),'users/alice'),{fullName:'Updated'},{merge:true}));assert.equal((await getDoc(doc(db(),'users/alice'))).data().totalXp,0)});
test('passwords and standalone reward edits are denied',async()=>{await assertFails(updateDoc(doc(db(),'users/alice'),{password:'never-store'}));await assertFails(updateDoc(doc(db(),'users/alice'),{totalXp:50,level:1}))});
test('existing food log and nutrient dashboard transaction stays authorized',async()=>{const d=db(),b=writeBatch(d);b.set(ref(d,`foodLogs/${DAY}`),{foodItems:[],totalCalories:0,nutrientTotals:{protein_g:0}});b.set(ref(d,`dailyData/${DAY}`),{caloriesConsumed:0,nutrientTotals:{protein_g:0}},{merge:true});await assertSucceeds(b.commit())});
test('Quick Exercise Log and daily calories remain compatible',async()=>{await assertSucceeds(setDoc(ref(db(),`exerciseLogs/${DAY}`),{exercises:[],totalCaloriesBurned:0}));await assertSucceeds(setDoc(ref(db(),`dailyData/${DAY}`),{caloriesBurned:0},{merge:true}))});
test('saved barcode foods are writable by their owner and isolated',async()=>{await assertSucceeds(setDoc(ref(db(),'barcodeFoods/12345678'),{food_name:'Saved product',barcode:'12345678',nutrition:{energy_kcal:100}}));await assertSucceeds(getDoc(ref(db(),'barcodeFoods/12345678')));await assertFails(getDoc(ref(db('bob'),'barcodeFoods/12345678')));await assertFails(setDoc(ref(db('bob'),'barcodeFoods/87654321'),{food_name:'Other'}))});
test('existing shared foods remain readable and cannot be edited',async()=>{await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'foods/test'),{food_name:'Example'}));await assertSucceeds(getDoc(doc(db(),'foods/test')));await assertFails(updateDoc(doc(db(),'foods/test'),{food_name:'Changed'}))});
test('new user can start first lesson but cannot start locked lesson',async()=>{await assertSucceeds(start(db()));await assertFails(start(db(),'locked','lesson_02'))});
test('recovery lesson is independently available',async()=>{await assertSucceeds(start(db(),'recovery-session','recovery'))});
test('session start must have server time and cannot be edited',async()=>{await assertFails(setDoc(ref(db(),'workoutSessions/bad'),{pathId:PATH,lessonId:'lesson_01',plannedExercises:4,startedAt:Timestamp.fromMillis(0)}));await start(db());await assertFails(updateDoc(ref(db(),'workoutSessions/session1'),{plannedExercises:99}))});
test('atomic record and all aggregate writes succeed',async()=>{const d=db();await start(d);assert.equal(await assertSucceeds(complete(d)),22);assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,22);assert.equal((await getDoc(ref(d,`exerciseWeeks/${WEEK}`))).data().completed,1);await assertSucceeds(start(d,'next','lesson_02'))});
test('same session retried or concurrently submitted awards once',async()=>{const d=db();await start(d);await assertSucceeds(Promise.all([complete(d),complete(d)]));await assertSucceeds(complete(d));assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,22);assert.equal((await getDoc(ref(d,`exerciseWeeks/${WEEK}`))).data().completed,1)});
test('repeats reduce workout XP and daily/weekly bonuses occur once',async()=>{const d=db();const earned=[];for(let n=0;n<4;n++){await start(d,'repeat'+n);earned.push(await complete(d,'repeat'+n));}assert.deepEqual(earned,[22,7,35,5]);assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,69)});
test('immutable record, quests and achievements cannot be rewritten',async()=>{const d=db();await start(d);await complete(d);await assertFails(updateDoc(ref(d,'workoutRecords/session1'),{xpEarned:77}));await assertFails(deleteDoc(ref(d,'workoutRecords/session1')));await assertFails(updateDoc(ref(d,`questProgress/${DAY}_planned`),{xpEarned:5}));await assertFails(updateDoc(ref(d,'unlockedAchievements/first_step'),{recordId:'another'}))});
test('another user cannot read saved workout or week progress',async()=>{const d=db();await start(d);await complete(d);await assertFails(getDoc(ref(db('bob'),'workoutRecords/session1')));await assertFails(getDoc(ref(db('bob'),`exerciseWeeks/${WEEK}`)))});
test('missing profile can be recreated by first completion',async()=>{const d=db();await env.withSecurityRulesDisabled(c=>deleteDoc(doc(c.firestore(),'users/alice')));await start(d);await assertSucceeds(complete(d));assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,22)});
test('maximum six achievement writes fit the atomic rule access budget',async()=>{const d=db();await start(d);await assertSucceeds(complete(d,'session1',true));for(const a of achievements)assert.equal((await getDoc(ref(d,`unlockedAchievements/${a}`))).exists(),true)});

// Integration contract for manual logs, guided credits and existing daily food data.
function dayTotals(manual,guided){let manualCalories=0,structured=0,seconds=0,count=0;for(const item of manual)if(item.completed===true){manualCalories+=Number(item.calories||0);seconds+=Number(item.duration||0)*60;count++;}for(const item of Object.values(guided)){structured+=Number(item.calories||0);seconds+=Number(item.activeSeconds||0);count++;}return {manualCaloriesBurned:Math.round(manualCalories),structuredCaloriesBurned:Math.round(structured),caloriesBurned:Math.round(manualCalories+structured),exerciseActiveSeconds:Math.round(seconds),exerciseEntryCount:count,exerciseTotalsVersion:2,structuredExerciseContributions:guided,lastUpdated:serverTimestamp()};}
async function saveManual(d,id,calories=120,minutes=30,remove=false){return runTransaction(d,async tx=>{const log=ref(d,`exerciseLogs/${DAY}`),daily=ref(d,`dailyData/${DAY}`),saved=await tx.get(log),summary=await tx.get(daily);let entries=manualRows(saved.data(),summary.data()).map(x=>({...x}));const index=entries.findIndex(x=>x.entryId===id);if(remove){if(index>=0)entries.splice(index,1);}else{const entry={entryId:id,name:'Walking',duration:minutes,calories,completed:true};if(index>=0)entries[index]={...entries[index],...entry};else entries.push(entry);}const totals=dayTotals(entries,summary.data()?.structuredExerciseContributions||{});tx.set(log,{date:DAY,exercises:entries,totalCaloriesBurned:totals.manualCaloriesBurned,timestamp:serverTimestamp()},{merge:true});tx.set(daily,totals,{merge:true});return totals;});}
async function backfill(d,records){if(!records){const found=await getDocs(query(collection(d,'users/alice/workoutRecords'),where('localDay','==',DAY)));records=found.docs.map(x=>({id:x.id,...x.data()}));}return runTransaction(d,async tx=>{const daily=ref(d,`dailyData/${DAY}`),manual=await tx.get(ref(d,`exerciseLogs/${DAY}`)),old=await tx.get(daily),contributions={...(old.data()?.structuredExerciseContributions||{})};for(const r of records)contributions[r.id]={calories:Number(r.estimatedCaloriesBurned||0),activeSeconds:Number(r.activeDurationSeconds||0)};const totals=dayTotals(manualRows(manual.data(),old.data()),contributions);tx.set(daily,totals,{merge:true});return totals;});}
test('manual save retries count once and do not grant exercise rewards',async()=>{const d=db();await saveManual(d,'manual1');await saveManual(d,'manual1');const totals=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(totals.caloriesBurned,120);assert.equal(totals.exerciseEntryCount,1);assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,0);assert.equal((await getDoc(ref(d,`pathProgress/${PATH}`))).exists(),false);assert.equal((await getDoc(ref(d,`questProgress/${DAY}_planned`))).exists(),false)});
test('guided completion and manual logging combine without replacing either source',async()=>{const d=db();await saveManual(d,'manual1');await start(d);await complete(d);let totals=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(totals.caloriesBurned,145);assert.equal(totals.manualCaloriesBurned,120);assert.equal(totals.structuredCaloriesBurned,25);assert.equal(totals.exerciseActiveSeconds,2100);await complete(d);totals=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(totals.caloriesBurned,145);assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,22)});
test('simultaneous manual and guided saves retain both contributions',async()=>{const d=db();await start(d);await assertSucceeds(Promise.all([saveManual(d,'manual1'),complete(d)]));const totals=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(totals.caloriesBurned,145);assert.equal(totals.exerciseEntryCount,2)});
test('manual editing and deletion leave guided records and rewards intact',async()=>{const d=db();await saveManual(d,'manual1');await start(d);await complete(d);await saveManual(d,'manual1',60,15);assert.equal((await getDoc(ref(d,`dailyData/${DAY}`))).data().caloriesBurned,85);await saveManual(d,'manual1',0,0,true);await saveManual(d,'manual1',0,0,true);assert.equal((await getDoc(ref(d,`dailyData/${DAY}`))).data().caloriesBurned,25);assert.equal((await getDoc(ref(d,'workoutRecords/session1'))).exists(),true);assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,22)});
test('exercise writes preserve food calories and full nutrient maps',async()=>{const d=db();await setDoc(ref(d,`dailyData/${DAY}`),{caloriesConsumed:500,nutrientTotals:{protein_g:20,fiber_g:5},foodEntryCount:2});await saveManual(d,'manual1');await start(d);await complete(d);const summary=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(summary.caloriesConsumed,500);assert.deepEqual(summary.nutrientTotals,{protein_g:20,fiber_g:5});assert.equal(summary.foodEntryCount,2);assert.equal(summary.caloriesBurned,145)});
test('food writes after exercise preserve both calorie sources',async()=>{const d=db();await saveManual(d,'manual1');await start(d);await complete(d);await setDoc(ref(d,`dailyData/${DAY}`),{caloriesConsumed:600,nutrientTotals:{protein_g:30}},{merge:true});const summary=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(summary.caloriesBurned,145);assert.equal(summary.caloriesConsumed,600);assert.equal(summary.structuredCaloriesBurned,25)});
test('older guided records can be reconciled repeatedly without awarding XP',async()=>{const d=db();await saveManual(d,'manual1');await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'users/alice/workoutRecords/old'),{lessonName:'Earlier guided workout',localDay:DAY,estimatedCaloriesBurned:40,activeDurationSeconds:180});});await backfill(d);await backfill(d);const totals=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(totals.caloriesBurned,160);assert.equal(totals.structuredCaloriesBurned,40);assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,0)});
test('backfill preserves a guided credit committed after its query snapshot',async()=>{const d=db();await start(d);await complete(d);await backfill(d,[{id:'older-record',estimatedCaloriesBurned:40,activeDurationSeconds:180}]);const totals=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(totals.caloriesBurned,65);assert.equal(Object.keys(totals.structuredExerciseContributions).length,2)});
test('legacy planned items stay excluded and original metadata is retained',async()=>{const d=db();await setDoc(ref(d,`exerciseLogs/${DAY}`),{exercises:[{name:'Old walk',duration:30,calories:115,completed:true},{name:'Planned run',duration:30,calories:250,completed:false}],date:DAY,totalCaloriesBurned:115,legacyMetadata:'keep'});await saveManual(d,'new',60,15);const old=(await getDoc(ref(d,`exerciseLogs/${DAY}`))).data();assert.equal(old.legacyMetadata,'keep');assert.equal(old.exercises[0].calories,115);assert.equal(old.exercises[1].completed,false);assert.equal((await getDoc(ref(d,`dailyData/${DAY}`))).data().caloriesBurned,175)});
test('another account cannot edit manual entries or activity totals',async()=>{await saveManual(db(),'manual1');await assertFails(updateDoc(ref(db('bob'),`exerciseLogs/${DAY}`),{exercises:[]}));await assertFails(updateDoc(ref(db('bob'),`dailyData/${DAY}`),{caloriesBurned:0}));await assertFails(getDoc(ref(db('bob'),`dailyData/${DAY}`)))});

function manualRows(manual={},daily={}){if(Array.isArray(manual.exercises))return manual.exercises;let fallback=Number(manual.totalCaloriesBurned||0);if(!Object.keys(manual).length){fallback=Number(daily.manualCaloriesBurned||0);if(!fallback&&!daily.exerciseTotalsVersion&&!daily.structuredExerciseContributions)fallback=Number(daily.caloriesBurned||0);}return fallback>0?[{entryId:'legacy_total_'+DAY,name:'',duration:0,calories:fallback,completed:true,legacySummary:true}]:[];}
test('dashboard-only historical calories survive refreshes and later new activities',async()=>{const d=db();await setDoc(ref(d,`dailyData/${DAY}`),{caloriesBurned:90,caloriesConsumed:500});await backfill(d);await backfill(d);assert.equal((await getDoc(ref(d,`dailyData/${DAY}`))).data().caloriesBurned,90);await start(d);await complete(d);await backfill(d);assert.equal((await getDoc(ref(d,`dailyData/${DAY}`))).data().caloriesBurned,115);await saveManual(d,'new');const totals=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(totals.caloriesBurned,235);assert.equal(totals.caloriesConsumed,500)});

// Progress weight history: owner-only dated records and matching atomic index.
async function saveWeight(d,day=DAY,kg=70,revision=1,syncProfile=false){
 const b=writeBatch(d),entry={day,weightKg:kg,revision,source:'manual',mutationId:'weight-'+revision,updateProfile:syncProfile,updatedAt:serverTimestamp()};
 b.set(ref(d,'weightHistory/state'),{entries:{[day]:{weightKg:kg,revision}},updatedAt:serverTimestamp()});
 b.set(ref(d,`weightLogs/${day}`),entry);
 if(syncProfile)b.set(doc(d,'users/alice'),{weight:kg},{merge:true});
 return b.commit();
}
test('weight log and index can be saved atomically by owner',async()=>{const d=db();await assertSucceeds(saveWeight(d,DAY,70,1,true));assert.equal((await getDoc(ref(d,`weightLogs/${DAY}`))).data().weightKg,70);assert.equal((await getDoc(doc(d,'users/alice'))).data().weight,70);assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,0)});
test('weight history is isolated from other users and anonymous clients',async()=>{await saveWeight(db());for(const client of [db('bob'),env.unauthenticatedContext().firestore()]){await assertFails(getDoc(ref(client,`weightLogs/${DAY}`)));await assertFails(getDoc(ref(client,'weightHistory/state')));await assertFails(saveWeight(client));}});
test('weight writes require matching fresh index and server timestamp',async()=>{const d=db();await assertFails(setDoc(ref(d,`weightLogs/${DAY}`),{day:DAY,weightKg:70,revision:1,source:'manual',mutationId:'one',updateProfile:false,updatedAt:serverTimestamp()}));const b=writeBatch(d);b.set(ref(d,'weightHistory/state'),{entries:{[DAY]:{weightKg:69,revision:1}},updatedAt:serverTimestamp()});b.set(ref(d,`weightLogs/${DAY}`),{day:DAY,weightKg:70,revision:1,source:'manual',mutationId:'one',updateProfile:false,updatedAt:serverTimestamp()});await assertFails(b.commit());});
test('invalid weight and revision updates are denied',async()=>{const d=db();await assertFails(saveWeight(d,DAY,0));await assertFails(saveWeight(d,DAY,301));await saveWeight(d);await assertFails(saveWeight(d,DAY,69,1));await assertSucceeds(saveWeight(d,DAY,69,2));});
test('weight deletion requires atomic index removal and preserves profile',async()=>{const d=db();await saveWeight(d,DAY,70,1,true);await assertFails(deleteDoc(ref(d,`weightLogs/${DAY}`)));const b=writeBatch(d);b.set(ref(d,'weightHistory/state'),{entries:{},updatedAt:serverTimestamp()});b.delete(ref(d,`weightLogs/${DAY}`));await assertSucceeds(b.commit());assert.equal((await getDoc(doc(d,'users/alice'))).data().weight,70)});
test('weight state rejects unknown fields and non-map index',async()=>{const d=db();await assertFails(setDoc(ref(d,'weightHistory/state'),{entries:[],updatedAt:serverTimestamp()}));await assertFails(setDoc(ref(d,'weightHistory/state'),{entries:{},updatedAt:serverTimestamp(),totalXp:100}));});
test('concurrent weight transactions preserve different measurement dates',async()=>{
 const d=db();
 async function add(day,kg){return runTransaction(d,async tx=>{const index=ref(d,'weightHistory/state'),record=ref(d,`weightLogs/${day}`);const old=await tx.get(index);await tx.get(record);const entries={...(old.data()?.entries||{}),[day]:{weightKg:kg,revision:1}};tx.set(index,{entries,updatedAt:serverTimestamp()});tx.set(record,{day,weightKg:kg,revision:1,source:'manual',mutationId:day,updateProfile:false,updatedAt:serverTimestamp()});});}
 await assertSucceeds(Promise.all([add('2026-10-27',71),add('2026-10-28',70)]));
 assert.equal(Object.keys((await getDoc(ref(d,'weightHistory/state'))).data().entries).length,2);
});

const mealData=()=>({name:'Breakfast',items:[{name:'Oats',nutrients:{energy_kcal:150},calories:150}],revision:1,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
test('saved meals owner can create read list rename and delete',async()=>{const d=db(),r=ref(d,'savedMeals/meal');await assertSucceeds(setDoc(r,mealData()));await assertSucceeds(getDoc(r));await assertSucceeds(getDocs(collection(d,'users/alice/savedMeals')));await assertSucceeds(updateDoc(r,{name:'Usual breakfast',revision:2,updatedAt:serverTimestamp()}));await assertSucceeds(deleteDoc(r))});
test('saved meals isolated from other users and anonymous callers',async()=>{await setDoc(ref(db(),'savedMeals/meal'),mealData());for(const d of [db('bob'),env.unauthenticatedContext().firestore()]){await assertFails(getDoc(ref(d,'savedMeals/meal')));await assertFails(setDoc(ref(d,'savedMeals/new'),mealData()));await assertFails(deleteDoc(ref(d,'savedMeals/meal')));await assertFails(getDocs(collection(d,'users/alice/savedMeals')))}});
test('saved meal schema rejects empty oversized and unexpected fields',async()=>{for(const patch of [{name:''},{name:'x'.repeat(81)},{items:[]},{items:Array(26).fill({name:'Food'})},{revision:2},{password:'bad'}])await assertFails(setDoc(ref(db(),'savedMeals/invalid'),{...mealData(),...patch}))});
test('saved meal revisions protect rename and snapshots remain immutable',async()=>{const r=ref(db(),'savedMeals/meal');await setDoc(r,mealData());await assertFails(updateDoc(r,{name:'Changed',updatedAt:serverTimestamp()}));await assertFails(updateDoc(r,{items:[{name:'Changed food'}],revision:2,updatedAt:serverTimestamp()}));await assertFails(updateDoc(r,{createdAt:Timestamp.fromMillis(0),revision:2,updatedAt:serverTimestamp()}))});
const op='11111111-1111-4111-8111-111111111111';
const receipt=()=>({date:DAY,mealId:'meal',entryCount:1,createdAt:serverTimestamp()});
test('saved meal receipt owner read create only',async()=>{const r=ref(db(),`savedMealLogs/${op}`);await assertSucceeds(setDoc(r,receipt()));await assertSucceeds(getDoc(r));await assertFails(updateDoc(r,{entryCount:2}));await assertFails(deleteDoc(r));await assertFails(getDoc(ref(db('bob'),`savedMealLogs/${op}`)));await assertFails(setDoc(ref(db('bob'),`savedMealLogs/${op}`),receipt()))});
test('saved meal receipt validates operation count date and timestamp',async()=>{for(const patch of [{entryCount:0},{entryCount:26},{date:'invalid'},{createdAt:Timestamp.fromMillis(0)}])await assertFails(setDoc(ref(db(),`savedMealLogs/${op}`),{...receipt(),...patch}));await assertFails(setDoc(ref(db(),'savedMealLogs/bad'),receipt()))});
test('saved meal logging transaction retries once and preserves exercise totals',async()=>{const d=db();await setDoc(ref(d,`dailyData/${DAY}`),{caloriesBurned:88,structuredCaloriesBurned:88});
 const log=()=>runTransaction(d,async tx=>{const ack=ref(d,`savedMealLogs/${op}`),day=ref(d,`foodLogs/${DAY}`),seen=await tx.get(ack);if(seen.exists())return;const old=await tx.get(day),items=old.exists()?old.data().foodItems:[];items.push({entryId:'stable-id',name:'Oats',calories:150,nutrients:{energy_kcal:150}});tx.set(day,{foodItems:items,totalCalories:150,nutrientTotals:{energy_kcal:150},nutrientCoverage:{energy_kcal:1}});tx.set(ref(d,`dailyData/${DAY}`),{caloriesConsumed:150,foodEntryCount:items.length},{merge:true});tx.set(ack,receipt());});
 await assertSucceeds(Promise.all([log(),log()]));await assertSucceeds(log());assert.equal((await getDoc(ref(d,`foodLogs/${DAY}`))).data().foodItems.length,1);const daily=(await getDoc(ref(d,`dailyData/${DAY}`))).data();assert.equal(daily.caloriesBurned,88);assert.equal(daily.foodEntryCount,1);
 await deleteDoc(ref(d,`foodLogs/${DAY}`));await assertSucceeds(log());assert.equal((await getDoc(ref(d,`foodLogs/${DAY}`))).exists(),false);
});

const recipeData=()=>({...mealData(),yieldServings:3,mutationId:op});
const servingData=()=>({name:'My rice bowl',foodKey:'food:rice',amount:150,unit:'g',revision:1,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
test('recipes owner CRUD and revision controlled edits',async()=>{const r=ref(db(),'recipes/recipe');await assertSucceeds(setDoc(r,recipeData()));await assertSucceeds(getDoc(r));await assertSucceeds(getDocs(collection(db(),'users/alice/recipes')));await assertSucceeds(updateDoc(r,{yieldServings:4,items:[{name:'New ingredient',nutrients:{energy_kcal:200}}],revision:2,updatedAt:serverTimestamp()}));await assertSucceeds(deleteDoc(r))});
test('recipes are private and invalid yields or stale revisions are denied',async()=>{const r=ref(db(),'recipes/recipe');await setDoc(r,recipeData());await assertFails(getDoc(ref(db('bob'),'recipes/recipe')));await assertFails(getDoc(ref(env.unauthenticatedContext().firestore(),'recipes/recipe')));await assertFails(deleteDoc(ref(db('bob'),'recipes/recipe')));for(const yieldServings of [0,-1,10001])await assertFails(updateDoc(r,{yieldServings,revision:2,updatedAt:serverTimestamp()}));await assertFails(updateDoc(r,{name:'Stale',revision:1,updatedAt:serverTimestamp()}));await assertFails(updateDoc(r,{createdAt:Timestamp.fromMillis(0),revision:2,updatedAt:serverTimestamp()}))});
test('personal servings are private owner CRUD with fixed food and unit',async()=>{const r=ref(db(),'personalServings/bowl');await assertSucceeds(setDoc(r,servingData()));await assertSucceeds(getDoc(r));await assertSucceeds(getDocs(query(collection(db(),'users/alice/personalServings'),where('foodKey','==','food:rice'))));await assertFails(getDoc(ref(db('bob'),'personalServings/bowl')));await assertFails(setDoc(ref(db('bob'),'personalServings/other'),servingData()));await assertFails(getDoc(ref(env.unauthenticatedContext().firestore(),'personalServings/bowl')));await assertSucceeds(updateDoc(r,{amount:180,revision:2,updatedAt:serverTimestamp()}));await assertFails(updateDoc(r,{foodKey:'food:soup',revision:3,updatedAt:serverTimestamp()}));await assertFails(updateDoc(r,{unit:'ml',revision:3,updatedAt:serverTimestamp()}));await assertSucceeds(deleteDoc(r))});
test('personal serving invalid amount units names and revisions denied',async()=>{for(const patch of [{amount:0},{amount:-1},{amount:10001},{unit:'bowl'},{name:''},{revision:2}])await assertFails(setDoc(ref(db(),'personalServings/bad'),{...servingData(),...patch}));const r=ref(db(),'personalServings/bowl');await setDoc(r,servingData());await assertFails(updateDoc(r,{amount:200,updatedAt:serverTimestamp()}))});
test('editing recipe or named serving leaves historical diary snapshots unchanged',async()=>{const d=db(),r=ref(d,'recipes/recipe'),s=ref(d,'personalServings/bowl');await setDoc(r,recipeData());await setDoc(s,servingData());await setDoc(ref(d,`foodLogs/${DAY}`),{foodItems:[{entryId:'history',recipeId:'recipe',recipeRevision:1,totalAmount:150,nutrients:{energy_kcal:150}}],totalCalories:150});await updateDoc(r,{yieldServings:6,revision:2,updatedAt:serverTimestamp()});await updateDoc(s,{amount:300,revision:2,updatedAt:serverTimestamp()});assert.equal((await getDoc(ref(d,`foodLogs/${DAY}`))).data().foodItems[0].nutrients.energy_kcal,150)});

const personalLabel=()=>({originalFoodId:'missing',food_name:'My food',nutrition:{energy_kcal:100},nutrition_basis:'per_100g',source_kind:'label',source_database:'Personal package-label nutrition',source_note:'Package label',nutrition_verification:'user_entered',label_amount:50,label_unit:'g',label_nutrition:{energy_kcal:50},revision:1,mutationId:op,updatedAt:serverTimestamp(),confirmedAt:serverTimestamp()});
test('personal nutrition owner CRUD does not change shared food',async()=>{await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'foods/missing'),{food_name:'My food',nutrition_basis:'unknown',nutrition:{}}));const d=db(),r=ref(d,'foodNutrition/missing');await assertSucceeds(setDoc(r,personalLabel()));await assertSucceeds(getDoc(r));await assertSucceeds(getDocs(collection(d,'users/alice/foodNutrition')));await assertSucceeds(updateDoc(r,{nutrition:{energy_kcal:120},revision:2,updatedAt:serverTimestamp(),confirmedAt:serverTimestamp()}));assert.equal((await getDoc(doc(d,'foods/missing'))).data().nutrition_basis,'unknown');await assertSucceeds(deleteDoc(r))});
test('personal nutrition is isolated from other accounts and anonymous users',async()=>{await setDoc(ref(db(),'foodNutrition/missing'),personalLabel());for(const d of [db('bob'),env.unauthenticatedContext().firestore()]){await assertFails(getDoc(ref(d,'foodNutrition/missing')));await assertFails(setDoc(ref(d,'foodNutrition/missing'),personalLabel()));await assertFails(deleteDoc(ref(d,'foodNutrition/missing')));await assertFails(getDocs(collection(d,'users/alice/foodNutrition')))}});
test('personal nutrition validates reference metadata identity and revisions',async()=>{for(const patch of [{originalFoodId:'other'},{nutrition_basis:'unknown'},{label_amount:0},{label_unit:'bowl'},{source_note:''},{source_kind:'verified'},{nutrition_basis:'per_100ml'},{nutrition_verification:'verified'},{revision:2}])await assertFails(setDoc(ref(db(),'foodNutrition/missing'),{...personalLabel(),...patch}));const r=ref(db(),'foodNutrition/missing');await setDoc(r,personalLabel());await assertFails(updateDoc(r,{nutrition:{energy_kcal:110},updatedAt:serverTimestamp(),confirmedAt:serverTimestamp()}))});
test('alternative nutrition requires explicit source identity and keeps private source kind',async()=>{const r=ref(db(),'foodNutrition/missing'),data=personalLabel();delete data.label_amount;delete data.label_unit;delete data.label_nutrition;Object.assign(data,{source_kind:'alternative',nutrition_verification:'approximate_substitution',alternativeFoodId:'other',alternativeName:'Similar cooked food'});await assertSucceeds(setDoc(r,data));assert.equal((await getDoc(r)).data().source_kind,'alternative')});
test('unknown calorie log and coverage metadata remain authorized without zero nutrient values',async()=>{const d=db(),batch=writeBatch(d);batch.set(ref(d,`foodLogs/${DAY}`),{foodItems:[{entryId:'unknown',name:'Unknown meal',nutrients:{},calculationMode:'unknown',nutritionStatus:'calories_unavailable'}],totalCalories:0,nutrientTotals:{},nutrientCoverage:{energy_kcal:0},knownCalorieCount:0,missingCalorieCount:1,caloriesComplete:false});batch.set(ref(d,`dailyData/${DAY}`),{caloriesConsumed:0,foodEntryCount:1,nutrientTotals:{},nutrientCoverage:{energy_kcal:0},knownCalorieCount:0,missingCalorieCount:1,caloriesComplete:false},{merge:true});await assertSucceeds(batch.commit());const row=(await getDoc(ref(d,`foodLogs/${DAY}`))).data().foodItems[0];assert.equal(Object.hasOwn(row.nutrients,'energy_kcal'),false);assert.equal((await getDoc(ref(d,`dailyData/${DAY}`))).data().caloriesComplete,false)});
test('confirmed source reference uses existing private nutrition schema',async()=>{const data=personalLabel();Object.assign(data,{source_database:'User-confirmed source reference',source_note:'Confirmed source header: per 100 g',label_amount:100,label_nutrition:{energy_kcal:130,protein_g:2.3},nutrition:{energy_kcal:130,protein_g:2.3}});const refData=ref(db(),'foodNutrition/missing');await assertSucceeds(setDoc(refData,data));assert.equal((await getDoc(refData)).data().nutrition.energy_kcal,130)});

test('wearable confirmation receipt is private and immutable',async()=>{
 const id='wearable_'+'a'.repeat(64),d=db(),r=ref(d,`wearableWorkoutLinks/${id}`);
 const value={date:DAY,entryId:id,createdAt:serverTimestamp()};
 await assertSucceeds(setDoc(r,value));await assertSucceeds(getDoc(r));
 await assertFails(getDoc(ref(db('bob'),`wearableWorkoutLinks/${id}`)));
 await assertFails(updateDoc(r,{date:'2026-10-29'}));await assertFails(deleteDoc(r));
});
test('wearable receipt rejects malformed IDs and payloads',async()=>{
 const d=db(),id='wearable_'+'b'.repeat(64),value={date:DAY,entryId:id,createdAt:serverTimestamp()};
 await assertFails(setDoc(ref(d,'wearableWorkoutLinks/random'),value));
 await assertFails(setDoc(ref(d,`wearableWorkoutLinks/${id}`),{...value,entryId:'different'}));
 await assertFails(setDoc(ref(d,`wearableWorkoutLinks/${id}`),{...value,xpEarned:50}));
 await assertFails(setDoc(ref(d,`wearableWorkoutLinks/${id}`),{...value,createdAt:Timestamp.fromMillis(1)}));
});
test('anonymous and other users cannot create wearable receipts',async()=>{
 const id='wearable_'+'c'.repeat(64),value={date:DAY,entryId:id,createdAt:serverTimestamp()};
 await assertFails(setDoc(ref(env.unauthenticatedContext().firestore(),`wearableWorkoutLinks/${id}`),value));
 await assertFails(setDoc(ref(db('bob'),`wearableWorkoutLinks/${id}`),value));
});
test('wearable confirmation can atomically save activity, totals and receipt',async()=>{
 const d=db(),id='wearable_'+'d'.repeat(64),b=writeBatch(d);
 b.set(ref(d,`wearableWorkoutLinks/${id}`),{date:DAY,entryId:id,createdAt:serverTimestamp()});
 b.set(ref(d,`exerciseLogs/${DAY}`),{exercises:[{entryId:id,name:'Running',duration:30,calories:180,completed:true,source:'health_connect',calorieSource:'wearable_active'}],totalCaloriesBurned:180});
 b.set(ref(d,`dailyData/${DAY}`),{manualCaloriesBurned:180,caloriesBurned:180,exerciseActiveSeconds:1800,exerciseEntryCount:1},{merge:true});
 await assertSucceeds(b.commit());assert.equal((await getDoc(ref(d,`dailyData/${DAY}`))).data().caloriesBurned,180);
 const retry=writeBatch(d);retry.set(ref(d,`wearableWorkoutLinks/${id}`),{date:DAY,entryId:id,createdAt:serverTimestamp()});retry.set(ref(d,`dailyData/${DAY}`),{caloriesBurned:360},{merge:true});
 await assertFails(retry.commit());assert.equal((await getDoc(ref(d,`dailyData/${DAY}`))).data().caloriesBurned,180);
});

// Wearable reward contract: independent immutable award + date lock, shared quests and week.
async function wearableReward(id,options={},attempt=0) {
 const d=db(),user=doc(d,'users/alice'),end=Date.now()-60000,startMs=end-600000;
 const day=options.day||new Date(startMs).toISOString().slice(0,10),cal=new Date(day+'T12:00:00Z');cal.setUTCDate(cal.getUTCDate()-((cal.getUTCDay()+6)%7));const week=cal.toISOString().slice(0,10);
 return runTransaction(d,async tx=>{
  const awardRef=ref(d,`wearableWorkoutRewards/${id}`),existing=await tx.get(awardRef);if(existing.exists())return existing.data();
  const profile=await tx.get(user),weekly=await tx.get(ref(d,`exerciseWeeks/${week}`)),lock=await tx.get(ref(d,`wearableRewardDays/${day}`)),receipt=await tx.get(ref(d,`wearableWorkoutLinks/${id}`));
  const planned=await tx.get(ref(d,`questProgress/${day}_planned`)),minutes=await tx.get(ref(d,`questProgress/${day}_minutes`)),first=await tx.get(ref(d,'unlockedAchievements/first_step'));
  const p=profile.data()||{},w=weekly.data()||{},newDay=!lock.exists(),base=newDay?20:0,quest=(planned.exists()?0:5)+(minutes.exists()?0:5),count=(w.completed||0)+(newDay?1:0),target=p.weeklyWorkoutTarget||3,bonus=count>=target&&!w.bonusAwarded&&!(p.metGoalWeeks||[]).includes(week)?30:0;
  const unlocked=options.unlocked||(!first.exists()?['first_step']:[]),xp=options.xpOverride??base+quest+bonus,total=(p.totalXp||0)+xp;
  const ownedAchievements={};for(const achievement of unlocked)ownedAchievements[achievement]=await tx.get(ref(d,`unlockedAchievements/${achievement}`));
  const category=options.category||'cardio';
  const a={source:'health_connect',activity:'Running',entryId:id,day,week,category,baseXp:base,questXp:quest,weeklyBonusXp:bonus,xpEarned:xp,weeklyCompleted:count,weeklyTarget:target,totalXpAfter:total,newDayCredit:newDay,unlocked,ruleVersion:'wearable-rewards-v1',durationSeconds:options.short?60:600,startedAtMillis:options.short?end-60000:startMs,endedAtMillis:end,createdAt:serverTimestamp()};
  if(!receipt.exists())tx.set(ref(d,`wearableWorkoutLinks/${id}`),{date:day,entryId:id,createdAt:serverTimestamp()});
  tx.set(awardRef,a);if(newDay&&!options.skipLock)tx.set(ref(d,`wearableRewardDays/${day}`),{day,week,awardId:id,createdAt:serverTimestamp()});
  if(!planned.exists()&&!options.skipQuest)tx.set(ref(d,`questProgress/${day}_planned`),{day,questId:options.feedback?'feedback':'planned',completed:true,xpEarned:5,recordId:id,completedAt:serverTimestamp()});
  if(!minutes.exists())tx.set(ref(d,`questProgress/${day}_minutes`),{day,questId:'minutes',completed:true,xpEarned:5,recordId:id,completedAt:serverTimestamp()});
  for(const achievement of unlocked)if(!ownedAchievements[achievement].exists())tx.set(ref(d,`unlockedAchievements/${achievement}`),{recordId:id,unlockedAt:serverTimestamp()});
  tx.set(ref(d,`exerciseWeeks/${week}`),{completed:count,categories:[...new Set([...(w.categories||[]),category])],bonusAwarded:!!w.bonusAwarded||bonus>0,target,lastRecordId:id,updatedAt:serverTimestamp()});
  const goals=[...(p.metGoalWeeks||[])];if(bonus&&!goals.includes(week))goals.push(week);
  tx.set(user,{lastRecordId:id,totalXp:total,level:Math.floor(total/100)+1,metGoalWeeks:goals,achievementIds:[...new Set([...(p.achievementIds||[]),...unlocked])],lastWorkoutAt:p.lastWorkoutAt&&p.lastWorkoutAt.toMillis()>end?p.lastWorkoutAt:Timestamp.fromMillis(end),...(options.profileOverride||{})},{merge:true});
  return a;
 }).catch(error=>{if(attempt===0&&['permission-denied','aborted','unavailable'].includes(error.code))return wearableReward(id,options,1);throw error;});
}
const wearableId=n=>'wearable_'+n.toString(16).padStart(64,'0');
test('eligible wearable workout awards XP, shared quests, weekly credit and first achievement',async()=>{
 const r=await assertSucceeds(wearableReward(wearableId(1)));assert.equal(r.xpEarned,30);assert.equal(r.weeklyCompleted,1);
 assert.equal((await getDoc(doc(db(),'users/alice'))).data().totalXp,30);
 assert.equal((await getDoc(ref(db(),'unlockedAchievements/first_step'))).data().recordId,wearableId(1));
});
test('wearable award replay gives no extra XP or progress',async()=>{
 await wearableReward(wearableId(2));await assertSucceeds(wearableReward(wearableId(2)));assert.equal((await getDoc(doc(db(),'users/alice'))).data().totalXp,30);
});
test('two distinct wearable sessions on one date share the daily cap',async()=>{
 await wearableReward(wearableId(3));const r=await assertSucceeds(wearableReward(wearableId(4)));assert.equal(r.xpEarned,0);assert.equal(r.weeklyCompleted,1);
});
test('concurrent wearable confirmations serialize the daily reward cap',async()=>{
 const result=await Promise.all([wearableReward(wearableId(5)),wearableReward(wearableId(6))]);assert.equal(result.reduce((n,r)=>n+r.xpEarned,0),30);assert.equal((await getDoc(doc(db(),'users/alice'))).data().totalXp,30);
});
test('wearable can earn the shared weekly target bonus',async()=>{
 await updateDoc(doc(db(),'users/alice'),{weeklyWorkoutTarget:1});const r=await assertSucceeds(wearableReward(wearableId(7)));assert.equal(r.weeklyBonusXp,30);assert.equal(r.xpEarned,60);const second=await wearableReward(wearableId(8));assert.equal(second.weeklyBonusXp,0);
});
test('wearable awards cannot inflate XP or omit the shared quest writes',async()=>{
 await assertFails(wearableReward(wearableId(9),{xpOverride:100}));await assertFails(wearableReward(wearableId(10),{skipQuest:true}));await assertFails(wearableReward(wearableId(11),{skipLock:true}));
});
test('wearable rewards reject sessions shorter than five minutes',async()=>{await assertFails(wearableReward(wearableId(12),{short:true}))});
test('wearable award cannot grant path or feedback achievements',async()=>{await assertFails(wearableReward(wearableId(13),{unlocked:['path_explorer']}));await assertFails(wearableReward(wearableId(14),{feedback:true}))});
test('wearable award cannot change protected feedback or invent goal weeks',async()=>{
 await assertFails(wearableReward(wearableId(15),{profileOverride:{feedbackCount:500}}));await assertFails(wearableReward(wearableId(16),{profileOverride:{metGoalWeeks:['invented']}}));
});
test('wearable reward ledgers and date locks are owner-private and immutable',async()=>{
 const r=await wearableReward(wearableId(17)),d=db();
 for(const path of [`wearableWorkoutRewards/${wearableId(17)}`,`wearableRewardDays/${r.day}`]){
  await assertFails(getDoc(ref(db('bob'),path)));await assertFails(getDoc(ref(env.unauthenticatedContext().firestore(),path)));await assertFails(updateDoc(ref(d,path),{xpEarned:200}));await assertFails(deleteDoc(ref(d,path)));
 }
});
test('wearable and guided workouts share daily quest receipts',async()=>{
 const day=new Date(Date.now()-660000).toISOString().slice(0,10);
 await env.withSecurityRulesDisabled(async c=>{
  const d=c.firestore();for(const q of ['planned','minutes'])await setDoc(doc(d,`users/alice/questProgress/${day}_${q}`),{day,questId:q,completed:true,xpEarned:5,recordId:'guided-prior',completedAt:Timestamp.now()});
 });
 const r=await assertSucceeds(wearableReward(wearableId(18)));assert.equal(r.questXp,0);assert.equal(r.xpEarned,20);
 assert.equal((await getDoc(ref(db(),`questProgress/${day}_planned`))).data().recordId,'guided-prior');
});
test('wearable cannot declare unearned general achievements',async()=>{
 await assertFails(wearableReward(wearableId(19),{unlocked:['routine_builder']}));await assertFails(wearableReward(wearableId(20),{unlocked:['welcome_back']}));await assertFails(wearableReward(wearableId(21),{unlocked:['balanced_week']}));
});
test('previously confirmed wearable can claim rewards without changing calorie logs',async()=>{
 const id=wearableId(22),day=new Date(Date.now()-660000).toISOString().slice(0,10),d=db();
 await setDoc(ref(d,`wearableWorkoutLinks/${id}`),{date:day,entryId:id,createdAt:serverTimestamp()});
 await setDoc(ref(d,`exerciseLogs/${day}`),{exercises:[{entryId:id,source:'health_connect',calories:180,duration:10,completed:true}],totalCaloriesBurned:180});
 await assertSucceeds(wearableReward(id));assert.equal((await getDoc(ref(d,`exerciseLogs/${day}`))).data().totalCaloriesBurned,180);
});
test('wearable may legitimately unlock consistency, comeback and balanced-week achievements',async()=>{
 const day=new Date(Date.now()-660000).toISOString().slice(0,10),cal=new Date(day+'T12:00:00Z');cal.setUTCDate(cal.getUTCDate()-((cal.getUTCDay()+6)%7));const week=cal.toISOString().slice(0,10);
 await env.withSecurityRulesDisabled(async c=>{const d=c.firestore();await updateDoc(doc(d,'users/alice'),{metGoalWeeks:['2026-09-07','2026-09-14'],lastWorkoutAt:Timestamp.fromMillis(Date.now()-8*86400000)});await setDoc(doc(d,`users/alice/exerciseWeeks/${week}`),{completed:0,categories:['strength','recovery'],bonusAwarded:false,target:3});});
 const r=await assertSucceeds(wearableReward(wearableId(23),{category:'mobility',unlocked:['first_step','routine_builder','welcome_back','balanced_week']}));assert.equal(r.xpEarned,30);
 for(const id of ['first_step','routine_builder','welcome_back','balanced_week'])assert.equal((await getDoc(ref(db(),`unlockedAchievements/${id}`))).data().recordId,wearableId(23));
});
