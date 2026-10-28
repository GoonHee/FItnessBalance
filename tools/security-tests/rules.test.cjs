const {before,after,beforeEach,test}=require('node:test');
const fs=require('node:fs');
const assert=require('node:assert/strict');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,writeBatch,serverTimestamp,Timestamp}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-fitnessbalance',firestore:{host:'127.0.0.1',port:8080,rules:fs.readFileSync(require('node:path').resolve(__dirname,'../../firestore.rules'),'utf8')}})});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{const d=c.firestore();await setDoc(doc(d,'users/alice'),{fullName:'Alice',totalXp:0,level:1});await setDoc(doc(d,'users/bob'),{fullName:'Bob',totalXp:0,level:1});await setDoc(doc(d,'exercises/EX001'),{name:'March',active:true});await setDoc(doc(d,'users/alice/workoutSessions/session1'),{pathId:'beginner_foundation',lessonId:'lesson_01',plannedExercises:4,startedAt:Timestamp.fromMillis(Date.now()-60000)});});});
function db(user='alice'){return env.authenticatedContext(user).firestore()}
test('authenticated shared reads and unauthenticated denial',async()=>{await assertSucceeds(getDoc(doc(db(),'exercises/EX001')));await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'exercises/EX001')))});
test('shared content cannot be edited by an ordinary user',async()=>{await assertFails(setDoc(doc(db(),'exercises/EX001'),{name:'Changed'}))});
test('user can read own profile but not another user',async()=>{await assertSucceeds(getDoc(doc(db(),'users/alice')));await assertFails(getDoc(doc(db(),'users/bob')));await assertFails(updateDoc(doc(db(),'users/bob'),{totalXp:10}))});
test('private records reject anonymous access',async()=>{await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'users/alice/workoutRecords/session1')))});
test('merge profile preserves existing progress and legacy logs work',async()=>{const d=db();await assertSucceeds(setDoc(doc(d,'users/alice'),{fullName:'Alicia'},{merge:true}));assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,0);for(const name of ['foodLogs','exerciseLogs','dailyData'])await assertSucceeds(setDoc(doc(d,`users/alice/${name}/2026-10-28`),{test:true}));});
test('password fields and independent XP edits are denied',async()=>{await assertFails(updateDoc(doc(db(),'users/alice'),{password:'never-store'}));await assertFails(updateDoc(doc(db(),'users/alice'),{totalXp:100,level:2}))});
test('session start requires server time and is immutable',async()=>{const ref=doc(db(),'users/alice/workoutSessions/new');await assertSucceeds(setDoc(ref,{pathId:'beginner_foundation',lessonId:'lesson_01',plannedExercises:4,startedAt:serverTimestamp()}));await assertFails(updateDoc(ref,{plannedExercises:8}))});
async function complete(d,session='session1'){
 const started=(await getDoc(doc(d,`users/alice/workoutSessions/${session}`))).data().startedAt;
 const b=writeBatch(d);const r=doc(d,`users/alice/workoutRecords/${session}`);
 b.set(r,{pathId:'beginner_foundation',lessonId:'lesson_01',lessonName:'Basic Movement',startedAt:started,completedAt:serverTimestamp(),plannedExercises:4,completedExercises:4,skippedExercises:0,completionPercentage:100,durationSeconds:300,estimatedCaloriesBurned:25,xpEarned:22,starsEarned:3,difficultyFeedback:'suitable',feelingFeedback:'normal',discomfortReported:false,firstCompletion:true,totalXpAfter:22,weeklyCompleted:1,weeklyTarget:3,unlocked:['first_step','lesson_02'],category:'mixed',localDay:'2026-10-28',localWeek:'2026-10-26',timeZone:'Asia/Kuala_Lumpur'});
 b.set(doc(d,'users/alice'),{lastRecordId:session,totalXp:22,level:1,weeklyWorkoutTarget:3,weeklyCounts:{'2026-10-26':1},weeklyCategories:{'2026-10-26':['mixed']},metGoalWeeks:[],achievementIds:['first_step'],feedbackCount:1,lastDifficulty:'suitable',lastFeeling:'normal',lastWorkoutAt:serverTimestamp()},{merge:true});
 b.set(doc(d,'users/alice/pathProgress/beginner_foundation'),{completionCounts:{lesson_01:1},bestPercent:{lesson_01:100},bestStars:{lesson_01:3},updatedAt:serverTimestamp()});
 for(const [questId,xpEarned] of [['planned',5],['minutes',5],['feedback',0]])b.set(doc(d,`users/alice/questProgress/2026-10-28_${questId}`),{day:'2026-10-28',questId,completed:true,xpEarned,recordId:session,completedAt:serverTimestamp()});
 b.set(doc(d,'users/alice/unlockedAchievements/first_step'),{unlockedAt:serverTimestamp(),recordId:session});
 return b.commit();
}
test('complete atomic reward batch is accepted; replay cannot award twice',async()=>{const d=db();await assertSucceeds(complete(d));assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,22);await assertFails(complete(d));assert.equal((await getDoc(doc(d,'users/alice'))).data().totalXp,22);});
test('saved workout records cannot be edited and are isolated across users',async()=>{const d=db();await assertSucceeds(complete(d));await assertFails(updateDoc(doc(d,'users/alice/workoutRecords/session1'),{xpEarned:77}));await assertFails(getDoc(doc(db('bob'),'users/alice/workoutRecords/session1')))});
test('record cannot be written without atomic user progress',async()=>{await assertFails(setDoc(doc(db(),'users/alice/workoutRecords/invalid'),{xpEarned:9999}))});
test('new account registration remains compatible',async()=>{await assertSucceeds(setDoc(doc(db('charlie'),'users/charlie'),{fullName:'Charlie',weight:70,fitnessLevel:'Beginner',createdAt:serverTimestamp(),email:'c@example.test',isVerified:false,dailyCalorieGoal:2000}))});

test('first completion can atomically create a missing user profile',async()=>{await env.withSecurityRulesDisabled(async c=>{await require('firebase/firestore').deleteDoc(doc(c.firestore(),'users/alice'))});await assertSucceeds(complete(db()));assert.equal((await getDoc(doc(db(),'users/alice'))).data().totalXp,22)});
