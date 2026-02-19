# 섹션별 스켈레톤 UI 점진적 전환 계획

## Context

현재 Gemini Streaming API를 통해 6개 섹션(writingScore → grammar → conceptualBridge → naturalness → toneStyle → paraphrasing)이 순차적으로 도착한다. 하지만 첫 번째 섹션이 도착하는 순간 **전체 스켈레톤 UI가 한꺼번에 사라지고**, 아직 데이터가 없는 나머지 섹션은 빈 공간으로 남는다.

**목표**: 각 섹션의 데이터가 도착할 때까지 해당 섹션의 스켈레톤 애니메이션을 유지하여, 점진적으로 스켈레톤 → 실제 콘텐츠로 전환되도록 한다.

---

## 핵심 문제 분석

`ScriptChatFragment.java:851-854`에서 첫 `onSectionReady` 콜백 시 전체 스켈레톤을 `View.GONE`으로 처리:
```java
// 현재 코드 - 전체 스켈레톤을 한 번에 숨김
if (skeletonLocal != null && skeletonLocal.getVisibility() == View.VISIBLE) {
    skeletonLocal.setVisibility(View.GONE);
}
```

현재 구조: 스켈레톤(`<include>`)과 실제 콘텐츠(`layout_sentence_feedback`)가 분리된 형제 뷰
```
LinearLayout (parent)
  ├── <include skeleton />          ← 하나의 덩어리 (전체를 한 번에 GONE 처리)
  └── LinearLayout (실제 콘텐츠)    ← 6개 섹션 각각 visibility="gone"
```

---

## 접근 방식: 인라인 섹션별 스켈레톤

기존의 단일 스켈레톤 `<include>`를 제거하고, 각 실제 콘텐츠 섹션 **바로 앞에** 해당 섹션의 스켈레톤을 개별 `ShimmerFrameLayout`으로 배치한다.

```
LinearLayout (layout_sentence_feedback)
  ├── ShimmerFrameLayout (skeleton_writing_score)   ← 개별 스켈레톤
  ├── 실제 writingScore 뷰들                         ← visibility="gone"
  ├── ShimmerFrameLayout (skeleton_grammar)
  ├── 실제 grammar 뷰들
  ├── ShimmerFrameLayout (skeleton_conceptual_bridge)
  ├── 실제 conceptualBridge 뷰들
  ├── ShimmerFrameLayout (skeleton_naturalness)
  ├── 실제 naturalness 뷰들
  ├── ShimmerFrameLayout (skeleton_tone_style)
  ├── 실제 toneStyle 뷰들
  ├── ShimmerFrameLayout (skeleton_paraphrasing)
  └── 실제 paraphrasing 뷰들
```

**장점**: 스켈레톤과 실제 콘텐츠가 같은 위치에 있어 자연스러운 전환. 섹션 도착 시 해당 스켈레톤만 숨기고 실제 콘텐츠를 표시.

**참고**: 6개의 개별 `ShimmerFrameLayout`이 독립적으로 애니메이션하지만, 같은 `shimmer_duration="1200"`을 사용하고 사용자가 스크롤하며 1-2개 섹션만 동시에 보기 때문에 시각적 차이는 무시할 수 있다.

---

## 수정 파일 및 상세 변경 사항

### 1. `bottom_sheet_content_feedback.xml`

**변경**: 단일 `<include>` 제거 → 6개 인라인 스켈레톤 추가

- **삭제**: 기존 스켈레톤 `<include>` (line 314-319)
  ```xml
  <!-- 삭제 -->
  <include
      android:id="@+id/layout_sentence_feedback_skeleton"
      layout="@layout/layout_sentence_feedback_skeleton" ... />
  ```

- **추가**: `layout_sentence_feedback` LinearLayout 내부, 각 실제 섹션 앞에 해당 스켈레톤 배치

  각 스켈레톤의 내용은 기존 `layout_sentence_feedback_skeleton.xml`에서 해당 부분을 복사:

  | 스켈레톤 ID | 원본 위치 (skeleton.xml) | 배치 위치 (feedback.xml 내 실제 섹션 앞) |
  |---|---|---|
  | `skeleton_writing_score` | line 22-45 | `tv_writing_score_label` 앞 (line 335) |
  | `skeleton_grammar` | line 47-100 | `tv_grammar_label` 앞 (line 390) |
  | `skeleton_conceptual_bridge` | line 102-178 | `tv_conceptual_bridge_label` 앞 (line 474) |
  | `skeleton_naturalness` | line 180-253 | `tv_naturalness_label` 앞 (line 577) |
  | `skeleton_tone_style` | line 255-323 | `tv_tone_style_label` 앞 (line 712) |
  | `skeleton_paraphrasing` | line 325-456 | `tv_paraphrasing_label` 앞 (line 918) |

  각 스켈레톤 형식:
  ```xml
  <com.facebook.shimmer.ShimmerFrameLayout
      android:id="@+id/skeleton_writing_score"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:visibility="gone"
      app:shimmer_auto_start="true"
      app:shimmer_duration="1200">
      <LinearLayout
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:orientation="vertical">
          <!-- 기존 skeleton.xml에서 해당 섹션 placeholder 내용 복사 -->
      </LinearLayout>
  </com.facebook.shimmer.ShimmerFrameLayout>
  ```

---

### 2. `layout_sentence_feedback_skeleton.xml`

**변경**: 삭제 (또는 미사용 상태로 유지)

더 이상 단일 스켈레톤으로 사용하지 않으므로 삭제 가능. 내용은 `bottom_sheet_content_feedback.xml`에 분산 배치됨.

---

### 3. `SentenceFeedbackBinder.java`

**변경 1**: 스켈레톤 뷰 참조 추가

```java
// 새로 추가할 멤버 변수
private ShimmerFrameLayout skeletonWritingScore;
private ShimmerFrameLayout skeletonGrammar;
private ShimmerFrameLayout skeletonConceptualBridge;
private ShimmerFrameLayout skeletonNaturalness;
private ShimmerFrameLayout skeletonToneStyle;
private ShimmerFrameLayout skeletonParaphrasing;
```

**변경 2**: `bindViews()` 메서드에 스켈레톤 바인딩 추가

```java
skeletonWritingScore = rootView.findViewById(R.id.skeleton_writing_score);
skeletonGrammar = rootView.findViewById(R.id.skeleton_grammar);
skeletonConceptualBridge = rootView.findViewById(R.id.skeleton_conceptual_bridge);
skeletonNaturalness = rootView.findViewById(R.id.skeleton_naturalness);
skeletonToneStyle = rootView.findViewById(R.id.skeleton_tone_style);
skeletonParaphrasing = rootView.findViewById(R.id.skeleton_paraphrasing);
```

**변경 3**: 스켈레톤 관리 메서드 추가

```java
/** 모든 스켈레톤 표시 (분석 시작 시 호출) */
public void showAllSkeletons() {
    showSkeleton(skeletonWritingScore);
    showSkeleton(skeletonGrammar);
    showSkeleton(skeletonConceptualBridge);
    showSkeleton(skeletonNaturalness);
    showSkeleton(skeletonToneStyle);
    showSkeleton(skeletonParaphrasing);
}

/** 모든 스켈레톤 숨기기 (완료/실패 시 호출) */
public void hideAllSkeletons() {
    hideSkeleton(skeletonWritingScore);
    hideSkeleton(skeletonGrammar);
    hideSkeleton(skeletonConceptualBridge);
    hideSkeleton(skeletonNaturalness);
    hideSkeleton(skeletonToneStyle);
    hideSkeleton(skeletonParaphrasing);
}

/** 특정 섹션의 스켈레톤만 숨기기 */
private void hideSectionSkeleton(String sectionKey) {
    ShimmerFrameLayout target = null;
    switch (sectionKey) {
        case "writingScore":      target = skeletonWritingScore; break;
        case "grammar":           target = skeletonGrammar; break;
        case "conceptualBridge":  target = skeletonConceptualBridge; break;
        case "naturalness":       target = skeletonNaturalness; break;
        case "toneStyle":         target = skeletonToneStyle; break;
        case "paraphrasing":      target = skeletonParaphrasing; break;
    }
    hideSkeleton(target);
}

private void showSkeleton(ShimmerFrameLayout shimmer) {
    if (shimmer != null) {
        shimmer.setVisibility(View.VISIBLE);
        shimmer.startShimmer();
    }
}

private void hideSkeleton(ShimmerFrameLayout shimmer) {
    if (shimmer != null) {
        shimmer.stopShimmer();
        shimmer.setVisibility(View.GONE);
    }
}
```

**변경 4**: `bindSection()` 메서드 수정 — 스켈레톤 숨기기 추가

```java
public void bindSection(String sectionKey, SentenceFeedback partialFeedback) {
    this.feedback = partialFeedback;
    hideSectionSkeleton(sectionKey);  // ← 추가: 해당 섹션 스켈레톤 숨기기
    switch (sectionKey) {
        case "writingScore": bindWritingScore(); break;
        case "grammar": bindGrammar(); break;
        case "conceptualBridge": bindConceptualBridge(); break;
        case "naturalness": bindNaturalness(); break;
        case "toneStyle": bindToneStyle(); break;
        case "paraphrasing": bindParaphrasing(); break;
    }
}
```

---

### 4. `ScriptChatFragment.java`

**변경 1**: `skeletonLayout` 필드 제거 (line 90)
```java
// 삭제
private ShimmerFrameLayout skeletonLayout;
```

**변경 2**: `onSectionReady()` 콜백 수정 (line 839-861)

스켈레톤 숨기기 로직 제거 (bindSection 내부에서 처리됨):
```java
public void onSectionReady(String sectionKey, SentenceFeedback partialFeedback) {
    new Handler(Looper.getMainLooper()).post(() -> {
        if (bottomSheetContentContainer.getChildCount() > 0) {
            View content = bottomSheetContentContainer.getChildAt(0);
            View layoutSentenceFeedback = content.findViewById(R.id.layout_sentence_feedback);
            if (layoutSentenceFeedback != null
                    && layoutSentenceFeedback.getVisibility() != View.VISIBLE) {
                layoutSentenceFeedback.setVisibility(View.VISIBLE);
            }
            // 스켈레톤 숨기기 로직 삭제 — bindSection() 내부에서 처리
        }
        if (currentBinder != null) {
            currentBinder.bindSection(sectionKey, partialFeedback);
        }
    });
}
```

**변경 3**: `showSpeakingFeedbackContent()` 수정 (line 936-1013)

- line 961: `skeletonLayout = content.findViewById(...)` 삭제
- line 1002-1007 (분석 중 상태):
  ```java
  // 변경 전
  currentBinder.hideAllSections();
  if (skeletonLayout != null) skeletonLayout.setVisibility(View.VISIBLE);

  // 변경 후
  currentBinder.hideAllSections();
  currentBinder.showAllSkeletons();
  ```
- line 993-997 (이미 완료 상태):
  ```java
  // 변경 전
  if (skeletonLayout != null) skeletonLayout.setVisibility(View.GONE);

  // 변경 후
  currentBinder.hideAllSkeletons();
  ```
- line 1008-1012 (분석 실패 상태): 동일하게 `currentBinder.hideAllSkeletons()` 사용

**변경 4**: `updateSentenceFeedbackUI()` 수정 (line 1049-1085)

- line 1055: `View skeletonLocal = content.findViewById(...)` 삭제
- line 1062-1064, 1079-1081: `skeletonLocal.setVisibility(View.GONE)` → `currentBinder.hideAllSkeletons()` 사용
  - 참고: `currentBinder`는 line 1074에서 새로 생성되므로, 생성 후에 `hideAllSkeletons()` 호출

**변경 5**: 기타 `skeletonLayout` 직접 참조 부분 모두 `currentBinder` 메서드로 대체

---

## 전환 흐름 (변경 후)

```
[녹음 완료] → transitionToAnalyzingState()
    ↓
[Fluency 결과 도착] → showSpeakingFeedbackContent()
    → currentBinder.hideAllSections()     (실제 콘텐츠 모두 GONE)
    → currentBinder.showAllSkeletons()    (6개 스켈레톤 모두 VISIBLE + shimmer)
    ↓
[writingScore 도착] → onSectionReady("writingScore", ...)
    → currentBinder.bindSection("writingScore", ...)
        → hideSectionSkeleton("writingScore")  (writingScore 스켈레톤만 GONE)
        → bindWritingScore()                   (실제 writingScore VISIBLE)
    ※ 나머지 5개 섹션은 여전히 스켈레톤 애니메이션 유지
    ↓
[grammar 도착] → 동일 패턴, grammar 스켈레톤만 교체
    ↓
... (conceptualBridge → naturalness → toneStyle → paraphrasing)
    ↓
[onComplete] → updateSentenceFeedbackUI(fullFeedback)
    → currentBinder.hideAllSkeletons()  (혹시 남은 스켈레톤 정리)
    → currentBinder.bind(fullFeedback)  (전체 바인딩 확인)
```

---

## 검증 방법

1. **빌드 확인**: `./gradlew assembleDebug` 성공 여부
2. **스트리밍 동작 테스트**: 실제 Gemini API로 문장 분석 요청 후:
   - 초기 상태: 6개 스켈레톤 모두 shimmer 애니메이션 동작 확인
   - 첫 섹션 도착: 해당 섹션만 실제 콘텐츠로 전환, 나머지 스켈레톤 유지 확인
   - 순차적으로 각 섹션이 스켈레톤 → 콘텐츠로 전환되는지 확인
   - 마지막 섹션 도착 후 모든 스켈레톤이 사라졌는지 확인
3. **에지 케이스**:
   - 이미 결과가 있는 경우 (`pendingSentenceFeedback != null`): 스켈레톤 없이 바로 표시
   - 분석 실패 시: 모든 스켈레톤이 숨겨지는지 확인
   - 화면 회전/재생성 시 상태 복원 확인
