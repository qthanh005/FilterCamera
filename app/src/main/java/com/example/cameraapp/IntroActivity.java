package com.example.cameraapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

public class IntroActivity extends AppCompatActivity {
    
    private ViewPager2 viewPager;
    private IntroPagerAdapter adapter;
    private Button btnSkip;
    private Button btnNext;
    private View dot1, dot2;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);
        
        viewPager = findViewById(R.id.viewPager);
        btnSkip = findViewById(R.id.btnSkip);
        btnNext = findViewById(R.id.btnNext);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        
        adapter = new IntroPagerAdapter(this);
        viewPager.setAdapter(adapter);
        
        // Cập nhật dots khi swipe
        updateDots(0);
        
        // Nút Skip - chuyển thẳng vào MainActivity
        btnSkip.setOnClickListener(v -> {
            startMainActivity();
        });
        
        // Nút Next
        btnNext.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentItem + 1, true);
            } else {
                // Ở trang cuối, chuyển vào MainActivity
                startMainActivity();
            }
        });
        
        // Cập nhật text nút Next và dots khi swipe
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateDots(position);
                if (position == adapter.getItemCount() - 1) {
                    btnNext.setText("Bắt đầu");
                    btnSkip.setVisibility(View.GONE);
                } else {
                    btnNext.setText("Tiếp theo");
                    btnSkip.setVisibility(View.VISIBLE);
                }
            }
        });
    }
    
    private void updateDots(int position) {
        if (dot1 != null && dot2 != null) {
            if (position == 0) {
                // Dot 1 selected, dot 2 unselected
                dot1.setBackgroundResource(R.drawable.dot_indicator_selected);
                dot2.setBackgroundResource(R.drawable.dot_indicator_unselected);
                dot1.getLayoutParams().width = (int) (12 * getResources().getDisplayMetrics().density);
                dot1.getLayoutParams().height = (int) (12 * getResources().getDisplayMetrics().density);
                dot2.getLayoutParams().width = (int) (8 * getResources().getDisplayMetrics().density);
                dot2.getLayoutParams().height = (int) (8 * getResources().getDisplayMetrics().density);
            } else {
                // Dot 2 selected, dot 1 unselected
                dot1.setBackgroundResource(R.drawable.dot_indicator_unselected);
                dot2.setBackgroundResource(R.drawable.dot_indicator_selected);
                dot1.getLayoutParams().width = (int) (8 * getResources().getDisplayMetrics().density);
                dot1.getLayoutParams().height = (int) (8 * getResources().getDisplayMetrics().density);
                dot2.getLayoutParams().width = (int) (12 * getResources().getDisplayMetrics().density);
                dot2.getLayoutParams().height = (int) (12 * getResources().getDisplayMetrics().density);
            }
            dot1.requestLayout();
            dot2.requestLayout();
        }
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(IntroActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}

