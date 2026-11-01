package com.example.fitnessbalance;
import org.json.*;
import java.util.*;
final class FoodJson {
    private FoodJson(){}
    static Map<String,Object> map(String json)throws JSONException{return object(new JSONObject(json));}
    private static Map<String,Object> object(JSONObject object)throws JSONException{Map<String,Object> result=new HashMap<>();Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();result.put(key,value(object.get(key)));}return result;}
    private static Object value(Object v)throws JSONException{if(v==JSONObject.NULL)return null;if(v instanceof JSONObject)return object((JSONObject)v);if(v instanceof JSONArray){List<Object> list=new ArrayList<>();for(int i=0;i<((JSONArray)v).length();i++)list.add(value(((JSONArray)v).get(i)));return list;}return v;}
}
