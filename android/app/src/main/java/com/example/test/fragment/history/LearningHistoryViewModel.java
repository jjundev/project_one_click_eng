package com.example.test.fragment.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.test.fragment.SessionSummaryBinder;
import com.example.test.fragment.dialoguelearning.model.SummaryData;

import java.util.ArrayList;
import java.util.List;

public class LearningHistoryViewModel extends ViewModel {

    private final MutableLiveData<List<HistoryItemWrapper>> historyItems = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<HistoryItemWrapper>> getHistoryItems() {
        return historyItems;
    }

    public void loadDummyData() {
        SummaryData dummyData = SessionSummaryBinder.createDummyData();
        List<HistoryItemWrapper> items = new ArrayList<>();

        // 1. Highlights (핵심)
        if (dummyData.getHighlights() != null) {
            for (SummaryData.HighlightItem item : dummyData.getHighlights()) {
                items.add(new HistoryItemWrapper(HistoryItemWrapper.TYPE_HIGHLIGHT, item));
            }
        }

        // 2. Expressions (표현)
        if (dummyData.getExpressions() != null) {
            for (SummaryData.ExpressionItem item : dummyData.getExpressions()) {
                items.add(new HistoryItemWrapper(HistoryItemWrapper.TYPE_EXPRESSION, item));
            }
        }

        // 3. Words (단어)
        if (dummyData.getWords() != null) {
            for (SummaryData.WordItem item : dummyData.getWords()) {
                items.add(new HistoryItemWrapper(HistoryItemWrapper.TYPE_WORD, item));
            }
        }

        // 4. Sentences (문장)
        if (dummyData.getLikedSentences() != null) {
            for (SummaryData.SentenceItem item : dummyData.getLikedSentences()) {
                items.add(new HistoryItemWrapper(HistoryItemWrapper.TYPE_SENTENCE, item));
            }
        }

        historyItems.setValue(items);
    }
}
