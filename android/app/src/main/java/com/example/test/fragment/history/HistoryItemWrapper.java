package com.example.test.fragment.history;

public class HistoryItemWrapper {
    public static final int TYPE_WORD = 0;
    public static final int TYPE_EXPRESSION = 1;
    public static final int TYPE_SENTENCE = 2;
    public static final int TYPE_HIGHLIGHT = 3;

    private final int type;
    private final Object data;

    public HistoryItemWrapper(int type, Object data) {
        this.type = type;
        this.data = data;
    }

    public int getType() {
        return type;
    }

    public Object getData() {
        return data;
    }
}
