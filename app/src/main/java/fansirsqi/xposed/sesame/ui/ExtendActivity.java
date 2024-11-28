package fansirsqi.xposed.sesame.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import fansirsqi.xposed.sesame.R;


/**
 * 扩展功能
 */
public class ExtendActivity extends BaseActivity {

    Button btnGetNewTreeItems;
    Button btnGetTreeItems;
    Button btnGetUnlockTreeItems;
    Button btnQueryAreaTrees;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_extend);
        this.btnGetTreeItems = findViewById(R.id.get_tree_items);
        this.btnGetNewTreeItems = findViewById(R.id.get_newTree_items);
        this.btnQueryAreaTrees = findViewById(R.id.query_area_trees);
        this.btnGetUnlockTreeItems = findViewById(R.id.get_unlock_treeItems);
        setBaseTitle("扩展功能");
        this.btnGetTreeItems.setOnClickListener(new AnonymousClass1());
        this.btnGetNewTreeItems.setOnClickListener(new AnonymousClass2());
        this.btnQueryAreaTrees.setOnClickListener(new AnonymousClass3());
        this.btnGetUnlockTreeItems.setOnClickListener(new AnonymousClass4());
    }


    class AnonymousClass1 implements View.OnClickListener {
        AnonymousClass1() {
        }

        @Override
        public final void onClick(View view) {
            ExtendActivity.this.sendItemsBroadcast("getTreeItems");
            Toast.makeText(ExtendActivity.this, "已发送查询请求，请在森林日志查看结果！", Toast.LENGTH_SHORT).show();
        }
    }


    class AnonymousClass2 implements View.OnClickListener {
        AnonymousClass2() {
        }

        @Override
        public final void onClick(View view) {
            ExtendActivity.this.sendItemsBroadcast("getNewTreeItems");
            Toast.makeText(ExtendActivity.this, "已发送查询请求，请在森林日志查看结果！", Toast.LENGTH_SHORT).show();
        }
    }


    class AnonymousClass3 implements View.OnClickListener {
        AnonymousClass3() {
        }

        @Override
        public final void onClick(View view) {
            ExtendActivity.this.sendItemsBroadcast("queryAreaTrees");
            Toast.makeText(ExtendActivity.this, "已发送查询请求，请在森林日志查看结果！", Toast.LENGTH_SHORT).show();
        }
    }


    class AnonymousClass4 implements View.OnClickListener {
        public AnonymousClass4() {
        }

        @Override
        public final void onClick(View view) {
            sendItemsBroadcast("getUnlockTreeItems");
            Toast.makeText(ExtendActivity.this, "已发送查询请求，请在森林日志查看结果！", Toast.LENGTH_SHORT).show();
        }
    }

    public void sendItemsBroadcast(String str) {
        Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.rpctest");
        intent.putExtra("method", "");
        intent.putExtra("data", "");
        intent.putExtra("type", str);
        sendBroadcast(intent);
    }


}
