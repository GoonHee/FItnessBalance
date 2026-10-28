const {before,after,beforeEach,test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,getDoc,getDocFromServer,setDoc,updateDoc,deleteDoc,writeBatch,runTransaction,serverTimestamp,Timestamp}=require('firebase/firestore');
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
  const qids=['planned','minutes','feedback'];const quests=[];for(const q of qids)quests.push(await tx.get(ref(d,`questProgress/${DAY}_${q}`)));
  const owned=[];for(const a of achievements)owned.push(await tx.get(ref(d,`unlockedAchievements/${a}`)));
  const u=user.data()||{},p=progress.data()||{},w=week.data()||{};const attempts=(p.completionCounts||{}).lesson_01||0,count=(w.completed||0)+1;
  const bonus=count>=3&&!w.bonusAwarded?30:0;const questBonus=quests.slice(0,2).filter(q=>!q.exists()).length*5;const xp=Math.round(10*(attempts===0?1:attempts===1?.5:.25))+2+questBonus+bonus,total=(u.totalXp||0)+xp;
  const unlocks=allAchievements?achievements:!owned[0].exists()?['first_step']:[];
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
