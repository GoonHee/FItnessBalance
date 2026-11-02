package com.example.fitnessbalance;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Small dependency-free charts. Screen tables provide exact, accessible values. */
final class ProgressChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private String[] labels=new String[0];
    private boolean horizontal;
    private double[][] bars=new double[0][];private int[] colors=new int[0];private Double goal;
    private List<WeightHistoryService.Entry> weights=Collections.emptyList();private double[] shares;
    ProgressChartView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription(context.getString(R.string.pr_chart_description));}
    void bars(String[] labels,double[][] bars,int[] colors,Double goal){horizontal=false;this.labels=labels;this.bars=bars;this.colors=colors;this.goal=goal;weights=Collections.emptyList();shares=null;invalidate();}
    void horizontalBars(String[] labels,double[][] values,int[] colors,Double goal){bars(labels,values,colors,goal);horizontal=true;invalidate();}
    void weights(List<WeightHistoryService.Entry> entries){weights=entries;bars=new double[0][];shares=null;invalidate();}
    void shares(double[] values,int[] colors){shares=values;this.colors=colors;bars=new double[0][];weights=Collections.emptyList();invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(shares!=null){shareStrip(canvas);return;}if(!weights.isEmpty()){line(canvas);return;}if(horizontal)horizontalBar(canvas);else bar(canvas);}
    private void label(Canvas c,String text,float x,float y,int color){paint.setColor(color);paint.setTextSize(dp(11));paint.setStyle(Paint.Style.FILL);c.drawText(text,x,y,paint);}
    private void bar(Canvas c){
        float left=dp(46),right=getWidth()-dp(10),top=dp(20),bottom=getHeight()-dp(34);
        if(labels.length==0||right<=left)return;double max=goal!=null?Math.max(1,goal):1,min=0;
        for(double[] row:bars){double sum=0;boolean known=false;for(double value:row)if(Double.isFinite(value)){sum+=value;known=true;}if(known){max=Math.max(max,sum);min=Math.min(min,sum);}}
        double span=max-min;max+=span*.08;min=min<0?min-span*.08:0;final double low=min,high=max;
        for(int i=0;i<=2;i++){double value=low+(high-low)*i/2;float y=bottom-(float)((value-low)/(high-low))*(bottom-top);
            paint.setColor(theme(R.color.gm_border));paint.setStrokeWidth(dp(1));c.drawLine(left,y,right,y,paint);label(c,shortNumber(value),dp(2),y+dp(4),theme(R.color.gm_muted));}
        float slot=(right-left)/labels.length,width=Math.min(dp(28),slot*.55f);
        float zero=bottom-(float)((0-low)/(high-low))*(bottom-top);
        for(int i=0;i<labels.length;i++){
            float x=left+slot*(i+.5f);double cumulative=0;boolean known=false;
            if(i<bars.length)for(int s=0;s<bars[i].length;s++){
                double value=bars[i][s];if(!Double.isFinite(value))continue;known=true;
                double next=cumulative+value;float y1=bottom-(float)((cumulative-low)/(high-low))*(bottom-top),y2=bottom-(float)((next-low)/(high-low))*(bottom-top);
                paint.setColor(colors[s%colors.length]);c.drawRect(x-width/2,Math.min(y1,y2),x+width/2,Math.max(y1,y2),paint);cumulative=next;
            }
            if(!known)label(c,"—",x-dp(5),zero-dp(5),theme(R.color.gm_muted));
            label(c,labels[i],x-dp(10),bottom+dp(22),theme(R.color.gm_muted));
        }
        if(goal!=null){float y=bottom-(float)((goal-low)/(high-low))*(bottom-top);paint.setColor(theme(R.color.gm_ink));paint.setStrokeWidth(dp(1.5f));paint.setPathEffect(new DashPathEffect(new float[]{dp(5),dp(4)},0));c.drawLine(left,y,right,y,paint);paint.setPathEffect(null);}
    }
    private void line(Canvas c){
        float left=dp(46),right=getWidth()-dp(12),top=dp(20),bottom=getHeight()-dp(38);
        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;for(WeightHistoryService.Entry e:weights){min=Math.min(min,e.kg);max=Math.max(max,e.kg);}double pad=Math.max(.5,(max-min)*.15);min-=pad;max+=pad;
        long first=java.time.LocalDate.parse(weights.get(0).day).toEpochDay(),last=java.time.LocalDate.parse(weights.get(weights.size()-1).day).toEpochDay();
        for(int i=0;i<3;i++){double value=min+(max-min)*i/2;float y=bottom-(float)((value-min)/(max-min))*(bottom-top);paint.setColor(theme(R.color.gm_border));c.drawLine(left,y,right,y,paint);label(c,shortNumber(value),dp(2),y+dp(4),theme(R.color.gm_muted));}
        Path path=new Path();paint.setColor(0xFF9A65DE);paint.setStrokeWidth(dp(2.5f));paint.setStyle(Paint.Style.STROKE);
        float[] xs=new float[weights.size()],ys=new float[weights.size()];
        for(int i=0;i<weights.size();i++){WeightHistoryService.Entry e=weights.get(i);long day=java.time.LocalDate.parse(e.day).toEpochDay();float x=first==last?(left+right)/2:left+(right-left)*(day-first)/(float)(last-first),y=bottom-(float)((e.kg-min)/(max-min))*(bottom-top);xs[i]=x;ys[i]=y;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);for(int i=0;i<xs.length;i++)c.drawCircle(xs[i],ys[i],dp(3),paint);
        label(c,weights.get(0).day,left,bottom+dp(24),theme(R.color.gm_muted));if(first!=last)label(c,weights.get(weights.size()-1).day,Math.max(left,right-dp(72)),bottom+dp(24),theme(R.color.gm_muted));
    }
    private int theme(int resource){return androidx.core.content.ContextCompat.getColor(getContext(),resource);}
    private void horizontalBar(Canvas c){
        float left=dp(48),right=getWidth()-dp(14),top=dp(14),bottom=getHeight()-dp(30);
        if(labels.length==0||right<=left||colors.length==0)return;
        double low=0,high=goal!=null&&Double.isFinite(goal)?Math.max(1,goal):1;
        for(double[] row:bars){double sum=0;boolean known=false;for(double value:row)if(Double.isFinite(value)){sum+=value;known=true;}if(known&&Double.isFinite(sum)){low=Math.min(low,sum);high=Math.max(high,sum);}}
        double pad=(high-low)*.08;high+=pad;if(low<0)low-=pad;double span=high-low;
        float rowHeight=(bottom-top)/labels.length,zero=left+(float)((0-low)/span)*(right-left);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(theme(R.color.gm_border));paint.setStrokeWidth(dp(1));c.drawLine(zero,top,zero,bottom,paint);paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<labels.length;i++){float y=top+rowHeight*(i+.5f);label(c,labels[i],dp(2),y+dp(4),theme(R.color.gm_muted));double cumulative=0;boolean known=false;
            if(i<bars.length)for(int segment=0;segment<bars[i].length;segment++){double value=bars[i][segment];if(!Double.isFinite(value))continue;known=true;double next=cumulative+value;
                float x1=left+(float)((cumulative-low)/span)*(right-left),x2=left+(float)((next-low)/span)*(right-left);paint.setColor(colors[segment%colors.length]);
                if(value!=0)c.drawRoundRect(new RectF(Math.min(x1,x2),y-dp(8),Math.max(x1,x2),y+dp(8)),dp(3),dp(3),paint);cumulative=next;}
            if(!known)label(c,"—",zero+dp(4),y+dp(4),theme(R.color.gm_muted));
            else if(cumulative==0){paint.setColor(colors[0]);c.drawCircle(zero,y,dp(3),paint);}
        }
        if(goal!=null&&Double.isFinite(goal)){float x=left+(float)((goal-low)/span)*(right-left);paint.setColor(theme(R.color.gm_ink));paint.setStrokeWidth(dp(1.5f));paint.setPathEffect(new DashPathEffect(new float[]{dp(5),dp(4)},0));c.drawLine(x,top,x,bottom,paint);paint.setPathEffect(null);}
        label(c,shortNumber(low),left,bottom+dp(22),theme(R.color.gm_muted));label(c,shortNumber(high),Math.max(left,right-dp(45)),bottom+dp(22),theme(R.color.gm_muted));
    }
    private void shareStrip(Canvas c){double total=0;for(double value:shares)total+=value;if(total<=0||!Double.isFinite(total)||colors.length==0)return;
        float left=dp(8),right=getWidth()-dp(8),top=getHeight()*.25f,bottom=getHeight()*.7f,x=left;
        for(int i=0;i<shares.length;i++){float width=(float)(shares[i]/total)*(right-left);paint.setColor(colors[i%colors.length]);c.drawRect(x,top,x+width,bottom,paint);x+=width;}
    }
    private String shortNumber(double value){return Math.abs(value)>=1000?String.format(Locale.getDefault(),"%.1fk",value/1000):String.format(Locale.getDefault(),"%.1f",value);}
}
