package com.example.test.fragment.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.test.fragment.SessionSummaryBinder;
import com.example.test.fragment.dialoguelearning.model.SummaryData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LearningHistoryViewModel extends ViewModel {

    private final MutableLiveData<List<HistoryItemWrapper>> historyItems = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<HistoryItemWrapper>> getHistoryItems() {
        return historyItems;
    }

    public void loadDummyData() {
        SummaryData dummyData = SessionSummaryBinder.createDummyData();
        List<HistoryItemWrapper> items = new ArrayList<>();
        Random rnd = new Random();
        long now = System.currentTimeMillis();
        long msInDay = 24L * 60 * 60 * 1000;

        // 1. Highlights (핵심)
        if (dummyData.getHighlights() != null) {
            for (SummaryData.HighlightItem item : dummyData.getHighlights()) {
                long time = now - (long) (rnd.nextDouble() * 25 * msInDay);
                items.add(new HistoryItemWrapper(HistoryItemWrapper.TYPE_HIGHLIGHT, item, time));
            }
        }

        // 2. Expressions (표현)
        if (dummyData.getExpressions() != null) {
            for (SummaryData.ExpressionItem item : dummyData.getExpressions()) {
                long time = now - (long) (rnd.nextDouble() * 25 * msInDay);
                items.add(new HistoryItemWrapper(HistoryItemWrapper.TYPE_EXPRESSION, item, time));
            }
        }

        // 3. Words (단어)
        if (dummyData.getWords() != null) {
            for (SummaryData.WordItem item : dummyData.getWords()) {
                long time = now - (long) (rnd.nextDouble() * 25 * msInDay);
                items.add(new HistoryItemWrapper(HistoryItemWrapper.TYPE_WORD, item, time));
            }
        }

        // 4. Sentences (문장)
        if (dummyData.getLikedSentences() != null) {
            for (SummaryData.SentenceItem item : dummyData.getLikedSentences()) {
                long time = now - (long) (rnd.nextDouble() * 25 * msInDay);
                items.add(new HistoryItemWrapper(HistoryItemWrapper.TYPE_SENTENCE, item, time));
            }
        }

        historyItems.setValue(items);
    }

    public SummaryData generateQuizSeed(int periodBucket, int currentTab) {
        List<HistoryItemWrapper> allItems = historyItems.getValue();
        if (allItems == null || allItems.isEmpty()) {
            return null;
        }

        long now = System.currentTimeMillis();
        long oneWeek = 7L * 24 * 60 * 60 * 1000;
        long startTime = 0;
        long endTime = now;

        if (periodBucket == HistoryQuizConfigDialog.PERIOD_1W) {
            startTime = now - oneWeek;
        } else if (periodBucket == HistoryQuizConfigDialog.PERIOD_2W) {
            startTime = now - 2 * oneWeek;
            endTime = now - oneWeek;
        } else if (periodBucket == HistoryQuizConfigDialog.PERIOD_3W) {
            startTime = now - 3 * oneWeek;
            endTime = now - 2 * oneWeek;
        } else if (periodBucket == HistoryQuizConfigDialog.PERIOD_OLDER) {
            endTime = now - 3 * oneWeek;
        }

        List<SummaryData.WordItem> words = new ArrayList<>();
        List<SummaryData.ExpressionItem> expressions = new ArrayList<>();

        for (HistoryItemWrapper item : allItems) {
            long epochMs = item.getLearnedAtEpochMs();
            if (epochMs >= startTime && epochMs <= endTime) {
                if (currentTab == 1 && item.getType() == HistoryItemWrapper.TYPE_WORD) {
                    words.add((SummaryData.WordItem) item.getData());
                } else if (currentTab == 0 && item.getType() == HistoryItemWrapper.TYPE_EXPRESSION) {
                    expressions.add((SummaryData.ExpressionItem) item.getData());
                } else if (currentTab == 2 && item.getType() == HistoryItemWrapper.TYPE_SENTENCE) {
                    SummaryData.SentenceItem sentence = (SummaryData.SentenceItem) item.getData();
                    expressions.add(new SummaryData.ExpressionItem("sentence", sentence.getKorean(), "",
                            sentence.getEnglish(), ""));
                }
            }
        }

        if (words.isEmpty() && expressions.isEmpty()) {
            return null;
        }

        SummaryData result = new SummaryData();
        result.setWords(words);
        result.setExpressions(expressions);
        return result;
    }
}
