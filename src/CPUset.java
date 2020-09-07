import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import utils.StringToList;
import xzr.La.systemtoolbox.modules.java.LModule;
import xzr.La.systemtoolbox.ui.StandardCard;
import xzr.La.systemtoolbox.utils.process.ShellUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CPUset implements LModule {
    public static final String TAG="[CPUset]";
    ArrayList<String> group;

    HashMap<String,String> namemap=new HashMap(){{
        put("audio-app","音频服务");
        put("background","后台应用");
        put("camera-daemon","相机服务");
        put("display","显示服务");
        put("foreground","前台任务");
        put("restricted","受限应用");
        put("system-background","系统后台");
        put("top-app","当前界面任务");

    }};

    @Override
    public String classname() {
        return "cpua";
    }

    @Override
    public View init(Context context) {
        if(!ShellUtil.run("if [ -d /dev/cpuset ]\nthen\necho true\nfi\n",true).equals("true"))
            return null;
        LinearLayout linearLayout=new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView title= StandardCard.title(context);
        title.setText("CPU组");
        linearLayout.addView(title);
        TextView subtitle=StandardCard.subtitle(context);
        subtitle.setText("您可以在此处控制不同CPU组的CPU分配情况");
        linearLayout.addView(subtitle);
        int cpunum;
        try {
            cpunum = Integer.parseInt(ShellUtil.run("cat /proc/cpuinfo |grep \"processor\"|wc -l", true));
        }catch (Exception e){
            TextView textView=new TextView(context);
            textView.setText("无法获取CPU核心数量，此模块跳过加载");
            linearLayout.addView(textView);
            return linearLayout;
        }
        //用来存储组的名称
        String ret=ShellUtil.run("cd /dev/cpuset\n" +
                "for i in *\n" +
                "do\n" +
                "if [ -d $i ]\n" +
                "then\n" +
                "echo $i\n" +
                "fi\n" +
                "done\n",true);
        group= StringToList.to(ret);

        for(int i=0;i<group.size();i++){
            Button button=new Button(context);
            button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            button.setText(node2name(group.get(i)));
            linearLayout.addView(button);
            int finalI = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showdialog(context, group.get(finalI),cpunum);
                }
            });
        }

        return linearLayout;
    }

    void showdialog(Context context,String groupname,int cpunum){
        ScrollView scrollView=new ScrollView(context);
        LinearLayout linearLayout=new LinearLayout(context);
        scrollView.addView(linearLayout);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        ArrayList<CheckBox> checkBoxes=new ArrayList<>();
        List<Boolean> settings_now=decode(groupname,cpunum);
        for(int i=0;i<cpunum;i++){
            //创建布局
            CheckBox checkBox=new CheckBox(context);
            checkBox.setText("CPU"+i);
            checkBoxes.add(checkBox);
            linearLayout.addView(checkBox);
            if(settings_now.get(i)){
                checkBox.setChecked(true);
            }
        }
        new AlertDialog.Builder(context)
                .setTitle("编辑“"+node2name(groupname)+"”的CPU组配置")
                .setNegativeButton("取消",null)
                .setView(scrollView)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String value="";
                        for(int j=0;j<cpunum;j++){
                            if(checkBoxes.get(j).isChecked()){
                                value+=j+",";
                            }
                        }
                        ShellUtil.run("echo \""+value+"\" > /dev/cpuset/"+groupname+"/cpus",true);
                    }
                })
                .create().show();

    }

    List<Boolean> decode(String groupname,int cpunum){
        ArrayList<Boolean> cpus=new ArrayList<>();
        //初始化一下
        for(int i=0;i<cpunum;i++){
            cpus.add(Boolean.FALSE);
        }
        String code=ShellUtil.run("cat /dev/cpuset/"+groupname+"/cpus",true);
        //接下来开始解码
        int last=0;
        for(int i=0;i<code.length();i++){
            String c=code.substring(i,i+1);
            if(!c.equals("-")&&!c.equals(",")){
                //是个数字
                //无论如何，把这个数字对应的cpu选上一定不会错
                int cpunow;
                try {
                    cpunow=Integer.parseInt(c);
                }catch (Exception e){
                    //假如读出了一个奇怪的东西
                    Log.e(TAG,"Error when decoding cpuset for cpu"+cpunum+", c="+c);
                    continue;
                }
                cpus.set(cpunow, Boolean.TRUE);

                //然后回去找一找这是不是第二个数字
                try {
                    String pre = code.substring(i - 1, i);
                    if(pre.equals("-")){
                        for(int k=last+1;k<cpunow;k++){
                            cpus.set(k,Boolean.TRUE);
                        }
                    }
                }catch (Exception e){
                    //已经不能往前读取了
                }
                //一切都结束了
                last=cpunow;

            }
        }

        return cpus;

    }

    String node2name(String nodename){
        String ret=namemap.get(nodename);
        if(ret!=null){
            return ret;
        }
        return nodename;
    }

    @Override
    public String onBootApply() {
        if(!ShellUtil.run("if [ -d /dev/cpuset ]\nthen\necho true\nfi\n",true).equals("true"))
            return null;
        String cmd="";
        for(int i=0;i<group.size();i++){
            //一个一个组的写入命令
            cmd+="echo \""+ShellUtil.run("cat /dev/cpuset/"+group.get(i)+"/cpus",true)+"\" > /dev/cpuset/"+group.get(i)+"/cpus\n";
        }
        return cmd;
    }

    @Override
    public void onExit() {

    }
}
