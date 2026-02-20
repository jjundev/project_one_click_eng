package com.example.test.fragment.history;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test.BuildConfig;
import com.example.test.R;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

public class LearningHistoryFragment extends Fragment {

    private static final String TAG = "LearningHistoryFrag";

    private LearningHistoryViewModel viewModel;
    private LearningHistoryAdapter adapter;
    private TabLayout tabLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learning_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViewModel();
        initViews(view);
        observeViewModel();
    }

    private void initViews(View view) {
        tabLayout = view.findViewById(R.id.tab_layout_history);
        RecyclerView recyclerView = view.findViewById(R.id.rv_learning_history);

        setupTabs();

        adapter = new LearningHistoryAdapter(item -> {
            // Handle save/unsave click later
            logDebug("Item clicked: " + item.getType());
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("전체"));
        tabLayout.addTab(tabLayout.newTab().setText("단어"));
        tabLayout.addTab(tabLayout.newTab().setText("표현"));
        tabLayout.addTab(tabLayout.newTab().setText("문장"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                filterData(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                filterData(tab.getPosition());
            }
        });
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(LearningHistoryViewModel.class);
        // Load dummy data
        viewModel.loadDummyData();
    }

    private void observeViewModel() {
        viewModel.getHistoryItems().observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                filterData(tabLayout.getSelectedTabPosition());
            }
        });
    }

    private void filterData(int tabPosition) {
        List<HistoryItemWrapper> allItems = viewModel.getHistoryItems().getValue();
        if (allItems == null)
            return;

        adapter.submitList(allItems, tabPosition);
    }

    private void logDebug(String message) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message);
        }
    }
}
