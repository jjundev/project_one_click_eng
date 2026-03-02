package com.jjundev.oneclickeng.fragment;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.DialogueLearningActivity;
import com.jjundev.oneclickeng.dialog.DialogueGenerateDialog;
import com.jjundev.oneclickeng.dialog.DialogueLearningSettingDialog;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.session.DialogueScriptStreamingSessionStore;
import com.jjundev.oneclickeng.others.ScriptSelectAdapter;
import com.jjundev.oneclickeng.others.ScriptTemplate;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DialogueSelectFragment extends Fragment
    implements DialogueGenerateDialog.OnScriptParamsSelectedListener {
  private static final String TAG = "DialogueSelectFragment";
  private static final String DIALOG_TAG_LEARNING_SETTINGS = "DialogueLearningSettingDialog";
  private static final int MIN_TURN_COUNT_TO_START = 2;
  private static final int DISPLAYED_SCRIPT_COUNT = 4;
  private static final long REFRESH_ANIMATION_DURATION_MS = 500L;

  private ImageButton btnBack;
  private ImageButton btnSettings;
  private ImageButton btnRefreshScripts;
  private RecyclerView rvScripts;
  private View layoutEmptyState;
  private AppCompatButton btnGenerate;
  private ScriptSelectAdapter adapter;
  private List<ScriptTemplate> allTemplateList;
  private List<ScriptTemplate> templateList;
  private IDialogueGenerateManager scriptGenerator;
  @Nullable
  private String pendingScriptSessionId;
  @Nullable
  private DialogueScriptStreamingSessionStore.Listener pendingScriptSessionListener;
  private long scriptPreparationRequestId = 0L;
  private boolean isRefreshing = false;
  private final Handler refreshHandler = new Handler(Looper.getMainLooper());

  private RewardedAd rewardedAd;
  private boolean isRewardEarned = false;
  private boolean isWaitingForAd = false;
  private android.app.Dialog chargeCreditDialog;
  private int adRetryAttempt = 0;
  private final android.os.Handler adRetryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean adLifecycleActive = false;
  private boolean isAdLoading = false;
  private boolean isAdShowing = false;
  private long adLoadGeneration = 0L;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_dialogue_select, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    scriptGenerator = resolveScriptGenerator();
    scriptGenerator.initializeCache(
        new IDialogueGenerateManager.InitCallback() {
          @Override
          public void onReady() {
            logStream("generator cache ready");
          }

          @Override
          public void onError(String error) {
            Log.e(TAG, "[DL_STREAM] generator cache init error: " + error);
          }
        });

    btnBack = view.findViewById(R.id.btn_back);
    btnSettings = view.findViewById(R.id.btn_settings);
    btnRefreshScripts = view.findViewById(R.id.btn_refresh_scripts);
    rvScripts = view.findViewById(R.id.rv_scripts);
    layoutEmptyState = view.findViewById(R.id.layout_empty_state);
    btnGenerate = view.findViewById(R.id.btn_generate_script);

    setupRecyclerView();
    setupListeners();
  }

  @Override
  public void onStart() {
    super.onStart();
    activateAdLifecycle();
  }

  @Override
  public void onStop() {
    softResetAdStateForStop();
    super.onStop();
  }

  @Override
  public void onDestroyView() {
    clearPendingScriptSession(true);
    scriptPreparationRequestId = 0L;
    refreshHandler.removeCallbacksAndMessages(null);
    isRefreshing = false;
    hardResetAdStateForDestroyView();
    super.onDestroyView();
  }

  private void setupRecyclerView() {
    allTemplateList = new ArrayList<>();
    // Sample data
    allTemplateList.add(
        new ScriptTemplate("☕", "카페에서 주문하기", "자연스러운 영어 회화", "안녕하세요! 따뜻한 아메리카노 한 잔 부탁합니다."));
    allTemplateList.add(
        new ScriptTemplate("🏢", "회사에서 자기소개", "전문적인 비즈니스 표현", "만나서 반갑습니다. 저는 마케팅 팀의 김현준입니다."));
    allTemplateList.add(
        new ScriptTemplate("✈️", "공항 입국 심사", "필수 여행 영어", "방문 목적은 관광입니다. 일주일 동안 머무를 예정이에요."));
    allTemplateList.add(
        new ScriptTemplate("🚕", "택시 목적지 말하기", "실전 생활 표현", "기사님, 강남역으로 가주세요. 얼마나 걸릴까요?"));
    allTemplateList.add(
        new ScriptTemplate("🍽️", "레스토랑 예약하기", "실전 식당 영어", "오늘 저녁 7시에 2명 예약 가능한가요?"));
    allTemplateList.add(
        new ScriptTemplate("🏨", "호텔 체크인", "여행 필수 표현", "체크인하려고 왔어요. 제 이름으로 예약되어 있습니다."));
    allTemplateList.add(
        new ScriptTemplate("🚇", "지하철 길 묻기", "도시 이동 표현", "시청역에 가려면 어느 노선을 타야 하나요?"));
    allTemplateList.add(
        new ScriptTemplate("🏥", "병원에서 증상 설명", "건강 관련 영어", "어제부터 목이 아프고 열이 조금 있어요."));
    allTemplateList.add(
        new ScriptTemplate("🛒", "환불 요청하기", "쇼핑 상황 대화", "이 제품을 환불하고 싶어요. 영수증은 여기 있어요."));
    allTemplateList.add(
        new ScriptTemplate("📦", "택배 배송 문제 문의", "고객센터 실전 표현", "배송 완료로 뜨는데 아직 못 받았어요."));
    allTemplateList.add(
        new ScriptTemplate("💼", "면접에서 자기소개", "커리어 영어", "안녕하세요. 데이터 분석 직무에 지원한 지원자입니다."));
    allTemplateList.add(
        new ScriptTemplate("📅", "회의 일정 조율", "비즈니스 협업 표현", "다음 주 화요일 오후 3시에 회의 가능하신가요?"));
    allTemplateList.add(
        new ScriptTemplate("📚", "수업 과제 질문하기", "학업 영어 표현", "과제 제출 마감일을 한 번 더 확인하고 싶어요."));
    allTemplateList.add(
        new ScriptTemplate("🚨", "분실물 신고하기", "긴급 상황 대처", "지갑을 잃어버렸는데 분실물 센터가 어디인가요?"));
    allTemplateList.add(
        new ScriptTemplate("🏦", "은행 계좌 개설하기", "금융 영어 표현", "새 계좌를 개설하고 싶은데 필요한 서류가 뭔가요?"));
    allTemplateList.add(
        new ScriptTemplate("🏠", "부동산에서 집 구하기", "주거 관련 표현", "이 근처에 월세 원룸이 있나요? 보증금은 얼마인가요?"));
    allTemplateList.add(
        new ScriptTemplate("🏋️", "헬스장 등록하기", "운동·건강 표현", "1개월 회원권은 얼마인가요? 체험 이용도 가능한가요?"));
    allTemplateList.add(
        new ScriptTemplate("💇", "미용실에서 머리하기", "뷰티·생활 표현", "앞머리를 좀 다듬고 싶어요. 자연스럽게 해주세요."));
    allTemplateList.add(
        new ScriptTemplate("🎬", "영화관 티켓 예매", "엔터테인먼트 표현", "오늘 저녁 8시 상영하는 영화 두 장 주세요."));
    allTemplateList.add(
        new ScriptTemplate("📖", "도서관에서 책 빌리기", "학습·문화 표현", "이 책을 빌리고 싶은데 대출 기간은 얼마나 되나요?"));
    allTemplateList.add(
        new ScriptTemplate("💊", "약국에서 약 사기", "건강·의료 표현", "두통약 있나요? 하루에 몇 번 먹어야 하나요?"));
    allTemplateList.add(
        new ScriptTemplate("📮", "우체국에서 택배 보내기", "생활 서비스 표현", "이 소포를 미국으로 보내고 싶어요. 배송비가 얼마죠?"));
    allTemplateList.add(
        new ScriptTemplate("👕", "세탁소에서 옷 맡기기", "일상 서비스 표현", "이 코트 드라이클리닝 가능한가요? 언제 찾을 수 있죠?"));
    allTemplateList.add(
        new ScriptTemplate("⛽", "주유소에서 주유하기", "자동차 생활 표현", "가득 채워주세요. 세차도 같이 할 수 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("👋", "새 이웃에게 인사하기", "사교·인사 표현", "안녕하세요! 옆집에 이사 온 김현준이라고 합니다."));
    allTemplateList.add(
        new ScriptTemplate("🛍️", "온라인 쇼핑 문의하기", "전자상거래 표현", "주문한 상품 배송 현황을 확인하고 싶어요."));
    allTemplateList.add(
        new ScriptTemplate("📞", "전화로 예약 변경하기", "전화 영어 표현", "예약 날짜를 변경하고 싶어요. 예약번호는 1234입니다."));
    allTemplateList.add(
        new ScriptTemplate("🍕", "음식 배달 주문하기", "배달앱 실전 표현", "피자 라지 하나랑 콜라 두 개 배달해주세요."));
    allTemplateList.add(
        new ScriptTemplate("🗺️", "여행 계획 세우기", "여행 준비 표현", "3박 4일로 제주도 여행을 계획 중이에요. 추천 코스 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("🌤️", "날씨에 대해 대화하기", "일상 스몰토크", "오늘 날씨가 정말 좋네요! 주말에도 맑을까요?"));
    allTemplateList.add(
        new ScriptTemplate("🎸", "취미 소개하기", "자기소개·취미 표현", "저는 주말마다 기타를 치고 있어요. 시작한 지 1년 됐어요."));
    allTemplateList.add(
        new ScriptTemplate("🐾", "반려동물 병원 방문", "동물병원 표현", "우리 강아지가 밥을 잘 안 먹어요. 진료 예약할게요."));
    allTemplateList.add(
        new ScriptTemplate("🍳", "요리 수업 등록하기", "문화·취미 표현", "초보자용 한식 쿠킹 클래스에 등록하고 싶어요."));
    allTemplateList.add(
        new ScriptTemplate("🎓", "졸업식에서 축하하기", "축하·감사 표현", "졸업 축하해! 정말 고생 많았어. 앞으로 더 멋진 일만 가득할 거야."));
    // 35~100: 다양한 주제 추가
    allTemplateList.add(
        new ScriptTemplate("🚗", "렌터카 빌리기", "여행·교통 표현", "소형차 2일 렌트하고 싶어요. 보험 포함인가요?"));
    allTemplateList.add(
        new ScriptTemplate("🦷", "치과 예약하기", "의료·건강 표현", "스케일링 예약하고 싶어요. 이번 주에 가능한 시간 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("🛃", "면세점에서 쇼핑하기", "쇼핑 영어 표현", "이 향수 면세 가격이 얼마인가요? 선물 포장도 되나요?"));
    allTemplateList.add(
        new ScriptTemplate("💻", "IT 지원 요청하기", "기술·IT 표현", "컴퓨터가 자꾸 꺼져요. 원격 지원 받을 수 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("📦", "이사 견적 문의하기", "이사·주거 표현", "다음 달에 이사 예정인데 견적 좀 받고 싶어요."));
    allTemplateList.add(
        new ScriptTemplate("🛡️", "보험 상담 받기", "금융·보험 표현", "여행자 보험에 가입하려는데 어떤 플랜이 좋을까요?"));
    allTemplateList.add(
        new ScriptTemplate("🪪", "운전면허 갱신하기", "관공서·행정 표현", "운전면허증을 갱신하러 왔어요. 필요한 서류가 뭔가요?"));
    allTemplateList.add(
        new ScriptTemplate("🤝", "동호회 가입하기", "사교·모임 표현", "등산 동호회에 가입하고 싶어요. 정기 모임은 언제인가요?"));
    allTemplateList.add(
        new ScriptTemplate("⛺", "캠핑장 예약하기", "아웃도어·레저 표현", "이번 주말 캠핑장 자리 있나요? 텐트 대여도 가능한가요?"));
    allTemplateList.add(
        new ScriptTemplate("🎭", "공연 티켓 예매하기", "문화·예술 표현", "이번 주 뮤지컬 공연 좌석이 남아 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("💒", "결혼식 축하 인사", "경조사 표현", "결혼 축하드려요! 두 분 정말 잘 어울리세요."));
    allTemplateList.add(
        new ScriptTemplate("🙋", "자원봉사 신청하기", "사회·봉사 표현", "주말 봉사활동에 참여하고 싶어요. 어떻게 신청하나요?"));
    allTemplateList.add(
        new ScriptTemplate("📱", "SNS 계정 문제 해결", "디지털 생활 표현", "계정이 잠겼는데 본인 인증은 어떻게 하나요?"));
    allTemplateList.add(
        new ScriptTemplate("📝", "이력서 피드백 부탁하기", "커리어 개발 표현", "이력서 좀 봐주실 수 있나요? 피드백 부탁드려요."));
    allTemplateList.add(
        new ScriptTemplate("🎒", "유학 상담 받기", "교육·유학 표현", "영국 대학원 유학을 준비 중인데 상담받고 싶어요."));
    allTemplateList.add(
        new ScriptTemplate("🏡", "홈스테이 문의하기", "숙박·유학 표현", "홈스테이 가족과 생활 규칙에 대해 알고 싶어요."));
    allTemplateList.add(
        new ScriptTemplate("💱", "환전하기", "금융·여행 표현", "원화를 달러로 환전하고 싶어요. 오늘 환율이 어떻게 되나요?"));
    allTemplateList.add(
        new ScriptTemplate("📶", "와이파이 연결 문의", "숙박·기술 표현", "와이파이 비밀번호가 뭔가요? 인터넷이 안 돼요."));
    allTemplateList.add(
        new ScriptTemplate("🧳", "세관 신고하기", "공항·여행 표현", "신고할 물품이 있어요. 세관 신고서는 어디서 작성하나요?"));
    allTemplateList.add(
        new ScriptTemplate("✈️", "비행기 기내 서비스", "항공 여행 표현", "담요 하나 더 주실 수 있나요? 그리고 음료 메뉴 볼게요."));
    allTemplateList.add(
        new ScriptTemplate("🏨", "호텔 컴플레인 하기", "숙박 문제 표현", "방에 온수가 안 나와요. 다른 방으로 바꿔주실 수 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("ℹ️", "관광 안내소 이용하기", "관광·여행 표현", "이 도시의 관광 지도 있나요? 추천 명소가 어디인가요?"));
    allTemplateList.add(
        new ScriptTemplate("🏛️", "박물관 관람하기", "문화·관광 표현", "오디오 가이드 대여 가능한가요? 한국어 지원되나요?"));
    allTemplateList.add(
        new ScriptTemplate("🎢", "놀이공원에서 즐기기", "레저·엔터 표현", "자유이용권은 얼마예요? 인기 놀이기구 대기 시간이 어떻게 되나요?"));
    allTemplateList.add(
        new ScriptTemplate("🏖️", "해변에서 휴식하기", "휴양·레저 표현", "파라솔 하나 빌릴 수 있나요? 수영 가능한 구역이 어디예요?"));
    allTemplateList.add(
        new ScriptTemplate("⛷️", "스키장 이용하기", "겨울 스포츠 표현", "스키 장비 풀세트 렌탈하고 싶어요. 초보자 강습도 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("⛳", "골프장 예약하기", "스포츠·레저 표현", "주말 라운드 예약하고 싶어요. 캐디 배정도 가능한가요?"));
    allTemplateList.add(
        new ScriptTemplate("🏊", "수영장 등록하기", "운동·건강 표현", "자유 수영 시간대가 언제인가요? 수영 강습도 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("📸", "사진관에서 증명사진 찍기", "생활 서비스 표현", "여권용 증명사진 찍으러 왔어요. 보정도 해주시나요?"));
    allTemplateList.add(
        new ScriptTemplate("💐", "꽃집에서 꽃 주문하기", "선물·기념일 표현", "생일 축하 꽃다발 하나 만들어주세요. 예산은 5만원이에요."));
    allTemplateList.add(
        new ScriptTemplate("🎂", "케이크 주문하기", "기념일·축하 표현", "생일 케이크를 주문하고 싶어요. 문구도 넣어주실 수 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("🏘️", "이사 인사하기", "이웃·사교 표현", "안녕하세요, 3층에 새로 이사 온 가족입니다. 잘 부탁드려요."));
    allTemplateList.add(
        new ScriptTemplate("🎉", "생일파티 초대하기", "파티·사교 표현", "이번 토요일에 생일파티 하는데 올 수 있어?"));
    allTemplateList.add(
        new ScriptTemplate("👥", "동창회 참석하기", "사교·추억 표현", "오랜만이야! 졸업하고 처음 보는 거 아니야? 그동안 잘 지냈어?"));
    allTemplateList.add(
        new ScriptTemplate("💕", "소개팅 대화하기", "소개팅·만남 표현", "만나서 반가워요! 주로 시간 나면 뭐 하세요?"));
    allTemplateList.add(
        new ScriptTemplate("📖", "스터디 모임 참여하기", "학습·모임 표현", "영어 스터디에 참여하고 싶어요. 매주 몇 번 모이나요?"));
    allTemplateList.add(
        new ScriptTemplate("📕", "독서 토론 참여하기", "문화·토론 표현", "이번 달 선정 도서에 대해 어떻게 생각하시나요?"));
    allTemplateList.add(
        new ScriptTemplate("🏃", "운동 약속 잡기", "헬스·사교 표현", "내일 저녁에 같이 러닝 어때? 한강공원 코스로 뛰자."));
    allTemplateList.add(
        new ScriptTemplate("🤔", "의사소통 오해 풀기", "대인관계 표현", "아까 내 말 뜻은 그게 아니었어. 오해가 있었나 봐."));
    allTemplateList.add(
        new ScriptTemplate("🙏", "사과하고 화해하기", "대인관계 표현", "정말 미안해. 내가 생각이 짧았어. 다시는 그러지 않을게."));
    allTemplateList.add(
        new ScriptTemplate("💝", "감사 인사 전하기", "감사·예절 표현", "정말 감사합니다. 덕분에 큰 도움이 됐어요."));
    allTemplateList.add(
        new ScriptTemplate("🎁", "선물 고르기", "쇼핑·기념일 표현", "친구 생일 선물을 찾고 있어요. 인기 있는 상품 추천해주세요."));
    allTemplateList.add(
        new ScriptTemplate("🌐", "인터넷 장애 신고하기", "통신·기술 표현", "인터넷이 끊겨서요. 언제 복구될까요?"));
    allTemplateList.add(
        new ScriptTemplate("📱", "스마트폰 수리 맡기기", "전자기기 수리 표현", "화면이 깨졌는데 수리비가 얼마나 나올까요?"));
    allTemplateList.add(
        new ScriptTemplate("🔧", "자동차 정비소 방문", "자동차 관리 표현", "엔진 오일 교환하러 왔어요. 타이어 점검도 같이 해주세요."));
    allTemplateList.add(
        new ScriptTemplate("🛡️", "보험 청구하기", "보험·행정 표현", "교통사고 보험 청구하려는데 필요한 서류가 뭔가요?"));
    allTemplateList.add(
        new ScriptTemplate("🧾", "세금 신고 상담하기", "세무·행정 표현", "종합소득세 신고를 도와주실 수 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("👨‍👩‍👧", "학부모 상담하기", "교육·학교 표현", "아이의 학교생활이 궁금해서요. 수업 태도는 어떤가요?"));
    allTemplateList.add(
        new ScriptTemplate("👶", "어린이집 등록하기", "육아·교육 표현", "3살 아이 등록하려는데 입소 대기는 얼마나 걸리나요?"));
    allTemplateList.add(
        new ScriptTemplate("👴", "노인 돌봄 서비스 신청", "복지·돌봄 표현", "어머니 재가 돌봄 서비스를 신청하려고요. 절차를 알려주세요."));
    allTemplateList.add(
        new ScriptTemplate("🕯️", "장례식 조문하기", "경조사 표현", "삼가 고인의 명복을 빕니다. 유가족분께 위로의 말씀을 드립니다."));
    allTemplateList.add(
        new ScriptTemplate("🪑", "인테리어 상담하기", "인테리어·주거 표현", "거실 인테리어를 바꾸고 싶어요. 모던 스타일로 추천해주세요."));
    allTemplateList.add(
        new ScriptTemplate("🚰", "정수기 설치 요청하기", "가전·생활 표현", "정수기 설치하러 왔다고요? 여기 주방에 놓아주세요."));
    allTemplateList.add(
        new ScriptTemplate("❄️", "에어컨 수리 문의하기", "가전 수리 표현", "에어컨에서 이상한 소리가 나요. AS 기사님 방문 가능한가요?"));
    allTemplateList.add(
        new ScriptTemplate("🚕", "택시에 물건 놓고 내리기", "분실물 대처 표현", "방금 탄 택시에 가방을 두고 내렸어요. 기사님 연락처 아시나요?"));
    allTemplateList.add(
        new ScriptTemplate("🍺", "숙취 해소 방법 묻기", "건강·일상 표현", "숙취가 너무 심해요. 해장국 맛집 아는 데 있어요?"));
    allTemplateList.add(
        new ScriptTemplate("🥗", "다이어트 상담하기", "건강·식단 표현", "체중 감량을 하고 싶어요. 어떤 식단이 좋을까요?"));
    allTemplateList.add(
        new ScriptTemplate("💪", "PT 수업 등록하기", "운동·피트니스 표현", "개인 트레이닝 받고 싶어요. 주 3회 가격이 어떻게 되나요?"));
    allTemplateList.add(
        new ScriptTemplate("🧘", "요가 수업 참여하기", "웰빙·운동 표현", "초보자도 참여할 수 있는 요가 수업이 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("💅", "네일샵에서 시술받기", "뷰티·패션 표현", "젤네일 하고 싶어요. 요즘 인기 있는 디자인 보여주세요."));
    allTemplateList.add(
        new ScriptTemplate("👓", "안경점에서 안경 맞추기", "건강·생활 표현", "시력 검사 받고 안경을 새로 맞추고 싶어요."));
    allTemplateList.add(
        new ScriptTemplate("🦷", "치과 교정 상담하기", "치과·의료 표현", "치아 교정을 하고 싶은데 비용이랑 기간이 얼마나 되나요?"));
    allTemplateList.add(
        new ScriptTemplate("🧴", "피부과 상담 받기", "의료·뷰티 표현", "요즘 피부 트러블이 심해서요. 어떤 치료를 받으면 좋을까요?"));
    allTemplateList.add(
        new ScriptTemplate("🌿", "한의원 진료 받기", "한방·건강 표현", "만성 피로가 심한데 한약 처방 받을 수 있나요?"));
    allTemplateList.add(
        new ScriptTemplate("🚑", "응급실 방문하기", "응급·의료 표현", "갑자기 배가 너무 아파요. 응급 진료 받을 수 있나요?"));

    // 전체 리스트에서 무작위 4개를 선택하여 표시용 리스트 생성
    templateList = new ArrayList<>();
    shuffleAndDisplayScripts();

    adapter = new ScriptSelectAdapter(
        templateList,
        template -> {
          hideKeyboard();
          DialogueGenerateDialog dialog = DialogueGenerateDialog.newInstance(template.getTitle());
          dialog.show(getChildFragmentManager(), "DialogueGenerateDialog");
        });

    rvScripts.setLayoutManager(new GridLayoutManager(getContext(), 2));
    rvScripts.setAdapter(adapter);

    // Apply layout animation to the RecyclerView
    android.view.animation.LayoutAnimationController controller = android.view.animation.AnimationUtils
        .loadLayoutAnimation(
            rvScripts.getContext(), R.anim.layout_anim_slide_fade_in);
    rvScripts.setLayoutAnimation(controller);

    updateEmptyState();
  }

  private IDialogueGenerateManager resolveScriptGenerator() {
    Context appContext = requireContext().getApplicationContext();
    AppSettings settings = new AppSettingsStore(appContext).getSettings();
    return LearningDependencyProvider.provideDialogueGenerateManager(
        appContext,
        settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
        settings.getLlmModelScript());
  }

  private void setupListeners() {
    btnBack.setOnClickListener(
        v -> {
          Navigation.findNavController(v).popBackStack();
        });

    btnSettings.setOnClickListener(v -> showDialogueLearningSettingDialog());

    btnGenerate.setOnClickListener(
        v -> {
          hideKeyboard(); // Ensure keyboard is hidden

          DialogueGenerateDialog dialogueGenerateDialog = new DialogueGenerateDialog();
          dialogueGenerateDialog.show(getChildFragmentManager(), "DialogueGenerateDialog");
        });

    btnRefreshScripts.setOnClickListener(
        v -> {
          if (isRefreshing)
            return;
          startRefreshWithAnimation();
        });
  }

  private void showDialogueLearningSettingDialog() {
    if (!isAdded()) {
      return;
    }

    FragmentManager fragmentManager = getChildFragmentManager();
    if (fragmentManager.isStateSaved()) {
      return;
    }

    Fragment existingDialog = fragmentManager.findFragmentByTag(DIALOG_TAG_LEARNING_SETTINGS);
    if (existingDialog != null && existingDialog.isAdded()) {
      return;
    }

    new DialogueLearningSettingDialog().show(fragmentManager, DIALOG_TAG_LEARNING_SETTINGS);
  }

  @Override
  public void onScriptParamsSelected(
      String level,
      String topic,
      String format,
      int length,
      int requiredCredit,
      DialogueGenerateDialog dialog) {
    if (dialog != null) {
      dialog.showLoading(true);
    }
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      if (dialog != null)
        dialog.showLoading(false);
      Toast.makeText(getContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
      return;
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .get()
        .addOnCompleteListener(
            task -> {
              if (!isAdded())
                return;
              if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot doc = task.getResult();
                Long creditObj = doc.getLong("credit");
                long credit = creditObj != null ? creditObj : 0L;
                int finalRequiredCredit = Math.max(1, requiredCredit);
                if (credit >= finalRequiredCredit) {
                  generateScriptStreaming(
                      level, topic, format, length, finalRequiredCredit, dialog);
                } else {
                  if (dialog != null)
                    dialog.dismiss();
                  Toast.makeText(getContext(), "크레딧이 부족해요", Toast.LENGTH_SHORT).show();
                  showChargeCreditDialog();
                }
              } else {
                if (dialog != null)
                  dialog.dismiss();
                Toast.makeText(getContext(), "크레딧 정보를 불러오지 못했어요.", Toast.LENGTH_SHORT).show();
              }
            });
  }

  private void generateScriptStreaming(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      int requiredCredit,
      @Nullable DialogueGenerateDialog dialog) {
    logStream(
        "prepare start: level="
            + level
            + ", topic="
            + safeText(topic)
            + ", format="
            + format
            + ", requestedLength="
            + Math.max(1, length)
            + ", requiredCredit="
            + Math.max(1, requiredCredit));
    clearPendingScriptSession(true);
    long requestId = ++scriptPreparationRequestId;
    DialogueScriptStreamingSessionStore sessionStore = LearningDependencyProvider
        .provideDialogueScriptStreamingSessionStore();

    String sessionId = sessionStore.startSession(scriptGenerator, level, topic, format, length);
    logStream("session started: requestId=" + requestId + ", sessionId=" + shortSession(sessionId));
    final boolean[] started = { false };
    final String userRequestedTopic = topic;
    final String[] streamedTopic = { userRequestedTopic };
    final int[] validTurnCount = { 0 };

    DialogueScriptStreamingSessionStore.Listener listener = new DialogueScriptStreamingSessionStore.Listener() {
      @Override
      public void onMetadata(
          @NonNull DialogueScriptStreamingSessionStore.ScriptMetadata metadata) {
        if (requestId != scriptPreparationRequestId || started[0]) {
          return;
        }
        String metadataTopic = trimToNull(metadata.getTopic());
        if (metadataTopic != null) {
          streamedTopic[0] = metadataTopic;
        }
        logStream(
            "prepare metadata: requestId="
                + requestId
                + ", topic="
                + safeText(streamedTopic[0]));
      }

      @Override
      public void onTurn(@NonNull IDialogueGenerateManager.ScriptTurnChunk turn) {
        if (requestId != scriptPreparationRequestId || started[0]) {
          return;
        }
        if (!isValidTurn(turn)) {
          return;
        }
        validTurnCount[0]++;
        logStream(
            "prepare turn: requestId="
                + requestId
                + ", validTurnCount="
                + validTurnCount[0]
                + "/"
                + MIN_TURN_COUNT_TO_START
                + ", role="
                + safeText(turn.getRole()));
        if (validTurnCount[0] >= MIN_TURN_COUNT_TO_START) {
          started[0] = true;
          logStream(
              "prepare threshold reached: requestId="
                  + requestId
                  + ", sessionId="
                  + shortSession(sessionId));
          startPreparedScriptStudy(
              dialog,
              requestId,
              level,
              Math.max(1, length),
              Math.max(1, requiredCredit),
              userRequestedTopic,
              sessionId);
        }
      }

      @Override
      public void onComplete(@Nullable String warningMessage) {
        if (requestId != scriptPreparationRequestId || started[0]) {
          return;
        }
        logStream(
            "prepare complete before start: requestId="
                + requestId
                + ", warning="
                + safeText(warningMessage));
        showScriptPreparationError(dialog, requestId);
      }

      @Override
      public void onFailure(@NonNull String error) {
        if (requestId != scriptPreparationRequestId || started[0]) {
          return;
        }
        logStream(
            "prepare failure before start: requestId="
                + requestId
                + ", error="
                + safeText(error));
        showScriptPreparationError(dialog, requestId);
      }
    };

    pendingScriptSessionId = sessionId;
    pendingScriptSessionListener = listener;
    DialogueScriptStreamingSessionStore.Snapshot snapshot = sessionStore.attach(sessionId, listener);
    if (snapshot == null) {
      logStream(
          "prepare attach failed: requestId="
              + requestId
              + ", sessionId="
              + shortSession(sessionId));
      showScriptPreparationError(dialog, requestId);
      return;
    }
    logStream(
        "prepare snapshot: requestId="
            + requestId
            + ", bufferedTurns="
            + snapshot.getBufferedTurns().size()
            + ", completed="
            + snapshot.isCompleted()
            + ", failure="
            + (trimToNull(snapshot.getFailureMessage()) != null));
    if (snapshot.getMetadata() != null) {
      String metadataTopic = trimToNull(snapshot.getMetadata().getTopic());
      if (metadataTopic != null) {
        streamedTopic[0] = metadataTopic;
      }
    }
    validTurnCount[0] = countValidTurns(snapshot.getBufferedTurns());
    if (hasStartableTurns(snapshot.getBufferedTurns())) {
      started[0] = true;
      logStream(
          "prepare threshold reached from snapshot: requestId="
              + requestId
              + ", sessionId="
              + shortSession(sessionId));
      startPreparedScriptStudy(
          dialog,
          requestId,
          level,
          Math.max(1, length),
          Math.max(1, requiredCredit),
          userRequestedTopic,
          sessionId);
      return;
    }
    if (trimToNull(snapshot.getFailureMessage()) != null || snapshot.isCompleted()) {
      logStream(
          "prepare cannot start from snapshot: requestId="
              + requestId
              + ", completed="
              + snapshot.isCompleted()
              + ", failure="
              + safeText(snapshot.getFailureMessage()));
      showScriptPreparationError(dialog, requestId);
    }
  }

  private void startPreparedScriptStudy(
      @Nullable DialogueGenerateDialog dialog,
      long requestId,
      @NonNull String level,
      int requestedLength,
      int requiredCredit,
      @NonNull String requestedTopic,
      @NonNull String sessionId) {
    logStream(
        "start learning activity: requestId="
            + requestId
            + ", level="
            + level
            + ", requestedLength="
            + requestedLength
            + ", requiredCredit="
            + Math.max(1, requiredCredit)
            + ", topic="
            + safeText(requestedTopic)
            + ", sessionId="
            + shortSession(sessionId));
    clearPendingScriptSession(false);
    finishScriptPreparation(dialog, requestId);
    if (dialog != null && dialog.isAdded()) {
      dialog.dismiss();
    }
    startScriptStudyStreaming(level, requestedLength, requiredCredit, requestedTopic, sessionId);
  }

  private void showScriptPreparationError(@Nullable DialogueGenerateDialog dialog, long requestId) {
    logStream("prepare error: requestId=" + requestId);
    clearPendingScriptSession(true);
    finishScriptPreparation(dialog, requestId);
    if (!isAdded()) {
      return;
    }
    Toast.makeText(getContext(), "대본 생성 중 오류가 발생했어요", Toast.LENGTH_SHORT).show();
  }

  private void finishScriptPreparation(@Nullable DialogueGenerateDialog dialog, long requestId) {
    if (dialog != null) {
      dialog.showLoading(false);
    }
    if (requestId == scriptPreparationRequestId) {
      scriptPreparationRequestId = 0L;
    }
  }

  private void clearPendingScriptSession(boolean releaseSession) {
    String sessionId = pendingScriptSessionId;
    DialogueScriptStreamingSessionStore.Listener listener = pendingScriptSessionListener;
    pendingScriptSessionId = null;
    pendingScriptSessionListener = null;

    if (sessionId == null) {
      return;
    }
    DialogueScriptStreamingSessionStore sessionStore = LearningDependencyProvider
        .provideDialogueScriptStreamingSessionStore();
    if (listener != null) {
      sessionStore.detach(sessionId, listener);
    }
    if (releaseSession) {
      sessionStore.release(sessionId);
    }
    logStream(
        "clear pending session: sessionId="
            + shortSession(sessionId)
            + ", release="
            + releaseSession);
  }

  private void startScriptStudyStreaming(
      @NonNull String level,
      int requestedLength,
      int requiredCredit,
      @NonNull String requestedTopic,
      @NonNull String sessionId) {
    if (!isAdded() || getActivity() == null) {
      logStream("start activity aborted: host unavailable, sessionId=" + shortSession(sessionId));
      LearningDependencyProvider.provideDialogueScriptStreamingSessionStore().release(sessionId);
      return;
    }
    int creditToDeduct = Math.max(1, requiredCredit);

    Intent intent = new Intent(getActivity(), DialogueLearningActivity.class);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_LEVEL, level);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_STREAM_SESSION_ID, sessionId);
    intent.putExtra(DialogueLearningActivity.EXTRA_REQUESTED_SCRIPT_LENGTH, requestedLength);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_TOPIC, requestedTopic);
    try {
      startActivity(intent);

      // 요청된 대본 길이에 맞춰 크레딧 차감(11줄부터 2)
      FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
      if (user != null) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.getUid())
            .update("credit", FieldValue.increment(-creditToDeduct))
            .addOnFailureListener(
                e -> logStream(
                    "Failed to decrement credit("
                        + creditToDeduct
                        + "): "
                        + e.getMessage()));
      }

    } catch (Exception e) {
      logStream(
          "start activity failed: sessionId="
              + shortSession(sessionId)
              + ", error="
              + safeText(e.getMessage()));
      LearningDependencyProvider.provideDialogueScriptStreamingSessionStore().release(sessionId);
      if (isAdded()) {
        Toast.makeText(getContext(), "대본 화면 이동 중 오류가 발생했어요", Toast.LENGTH_SHORT).show();
      }
      return;
    }
    logStream(
        "start activity success: sessionId="
            + shortSession(sessionId)
            + ", deductedCredit="
            + creditToDeduct);
    hideKeyboard();
  }

  private boolean hasStartableTurns(
      @Nullable List<IDialogueGenerateManager.ScriptTurnChunk> turns) {
    if (turns == null || turns.size() < MIN_TURN_COUNT_TO_START) {
      return false;
    }
    int validCount = 0;
    for (IDialogueGenerateManager.ScriptTurnChunk turn : turns) {
      if (isValidTurn(turn)) {
        validCount++;
      }
      if (validCount >= MIN_TURN_COUNT_TO_START) {
        return true;
      }
    }
    return false;
  }

  private boolean isValidTurn(@Nullable IDialogueGenerateManager.ScriptTurnChunk turn) {
    if (turn == null) {
      return false;
    }
    return trimToNull(turn.getKorean()) != null && trimToNull(turn.getEnglish()) != null;
  }

  private int countValidTurns(@Nullable List<IDialogueGenerateManager.ScriptTurnChunk> turns) {
    if (turns == null || turns.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (IDialogueGenerateManager.ScriptTurnChunk turn : turns) {
      if (isValidTurn(turn)) {
        count++;
      }
    }
    return count;
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private void logStream(@NonNull String message) {
    Log.d(TAG, "[DL_STREAM] " + message);
  }

  @NonNull
  private static String shortSession(@Nullable String sessionId) {
    String value = trimToNull(sessionId);
    if (value == null) {
      return "-";
    }
    if (value.length() <= 8) {
      return value;
    }
    return value.substring(0, 8);
  }

  @NonNull
  private static String safeText(@Nullable String value) {
    String text = trimToNull(value);
    if (text == null) {
      return "-";
    }
    if (text.length() <= 32) {
      return text;
    }
    return text.substring(0, 32) + "...";
  }

  private void shuffleAndDisplayScripts() {
    List<ScriptTemplate> shuffled = new ArrayList<>(allTemplateList);
    Collections.shuffle(shuffled, new Random());
    templateList.clear();
    int count = Math.min(DISPLAYED_SCRIPT_COUNT, shuffled.size());
    for (int i = 0; i < count; i++) {
      templateList.add(shuffled.get(i));
    }
  }

  private void startRefreshWithAnimation() {
    isRefreshing = true;

    // 1) 새로고침 버튼 360도 회전 애니메이션
    ObjectAnimator rotateAnim = ObjectAnimator.ofFloat(btnRefreshScripts, "rotation", 0f, 360f);
    rotateAnim.setDuration(REFRESH_ANIMATION_DURATION_MS);
    rotateAnim.setInterpolator(new LinearInterpolator());
    rotateAnim.start();

    // 2) 스켈레톤 모드 ON
    if (adapter != null) {
      adapter.setSkeletonMode(true);
    }

    // 3) 애니메이션 종료 후 실제 데이터 갱신
    refreshHandler.postDelayed(() -> {
      if (!isAdded())
        return;

      shuffleAndDisplayScripts();
      if (adapter != null) {
        adapter.setSkeletonMode(false);
        // templateList는 adapter 내부 templates와 같은 참조이므로
        // shuffleAndDisplayScripts()에서 이미 갱신됨 → notifyDataSetChanged만 호출
        adapter.notifyDataSetChanged();
      }

      // 레이아웃 애니메이션 재실행
      if (rvScripts != null) {
        rvScripts.scheduleLayoutAnimation();
      }

      isRefreshing = false;
    }, REFRESH_ANIMATION_DURATION_MS);
  }

  private void updateEmptyState() {
    if (templateList.isEmpty()) {
      layoutEmptyState.setVisibility(View.VISIBLE);
      rvScripts.setVisibility(View.GONE);
    } else {
      layoutEmptyState.setVisibility(View.GONE);
      rvScripts.setVisibility(View.VISIBLE);
      rvScripts.scheduleLayoutAnimation();
    }
  }

  private void hideKeyboard() {
    View view = getActivity().getCurrentFocus();
    if (view != null) {
      InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  private void showChargeCreditDialog() {
    View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_charge_credit, null);

    View layoutContent = dialogView.findViewById(R.id.layout_content);
    View layoutLoading = dialogView.findViewById(R.id.layout_loading);
    AppCompatButton btnGoToCreditStore = dialogView.findViewById(R.id.btn_go_to_credit_store);
    AppCompatButton btnCancel = dialogView.findViewById(R.id.btn_charge_cancel);
    AppCompatButton btnAd = dialogView.findViewById(R.id.btn_charge_ad);

    chargeCreditDialog = new android.app.Dialog(requireContext());
    chargeCreditDialog.setContentView(dialogView);
    if (chargeCreditDialog.getWindow() != null) {
      chargeCreditDialog
          .getWindow()
          .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int width = (int) (metrics.widthPixels * 0.9f);
      chargeCreditDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    chargeCreditDialog.setOnDismissListener(
        d -> {
          isWaitingForAd = false;
          chargeCreditDialog = null;
        });

    btnCancel.setOnClickListener(v -> chargeCreditDialog.dismiss());
    btnGoToCreditStore.setOnClickListener(
        v -> {
          if (!isAdded()) {
            return;
          }

          NavController navController = NavHostFragment.findNavController(this);
          if (navController.getCurrentDestination() != null
              && navController.getCurrentDestination().getId() == R.id.creditStoreFragment) {
            return;
          }

          chargeCreditDialog.dismiss();
          navController.navigate(R.id.action_scriptSelectFragment_to_creditStoreFragment);
        });
    btnAd.setOnClickListener(
        v -> {
          if (rewardedAd != null && isAdded() && isResumed()) {
            dismissChargeCreditDialog();
            showRewardedAdIfAvailable();
          } else {
            isWaitingForAd = true;
            layoutContent.setVisibility(View.GONE);
            layoutLoading.setVisibility(View.VISIBLE);
            chargeCreditDialog.setCancelable(false);

            ensureRewardedAdPreloaded();
          }
        });

    chargeCreditDialog.show();
  }

  private void activateAdLifecycle() {
    adLifecycleActive = true;
    adLoadGeneration++;
  }

  private void softResetAdStateForStop() {
    adLifecycleActive = false;
    adLoadGeneration++;
    adRetryHandler.removeCallbacksAndMessages(null);
    dismissChargeCreditDialog();
    isAdLoading = false;
    adRetryAttempt = 0;
  }

  private void hardResetAdStateForDestroyView() {
    softResetAdStateForStop();
    rewardedAd = null;
    isAdLoading = false;
    isAdShowing = false;
    isRewardEarned = false;
    adRetryAttempt = 0;
    chargeCreditDialog = null;
  }

  private void dismissChargeCreditDialog() {
    if (chargeCreditDialog != null && chargeCreditDialog.isShowing()) {
      chargeCreditDialog.dismiss();
    }
    chargeCreditDialog = null;
    isWaitingForAd = false;
  }

  private void ensureRewardedAdPreloaded() {
    if (!adLifecycleActive || isAdLoading || rewardedAd != null || !isAdded() || getContext() == null) {
      return;
    }

    AdRequest adRequest = new AdRequest.Builder().build();
    String adUnitId = BuildConfig.DEBUG
        ? "ca-app-pub-3940256099942544/5224354917"
        : BuildConfig.ADMOB_REWARDED_AD_UNIT_ID;
    logStream("Loading rewarded ad with Unit ID: " + adUnitId);
    isAdLoading = true;
    long generation = adLoadGeneration;
    RewardedAd.load(
        requireContext(),
        adUnitId,
        adRequest,
        new RewardedAdLoadCallback() {
          @Override
          public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
            if (generation != adLoadGeneration) {
              return;
            }
            isAdLoading = false;
            if (!adLifecycleActive) {
              return;
            }
            logStream("Failed to load rewarded ad: " + loadAdError.getMessage());
            rewardedAd = null;
            adRetryAttempt++;
            long retryDelayMillis = (long) Math.pow(2, Math.min(6, adRetryAttempt)) * 1000L;
            if (isWaitingForAd && adRetryAttempt <= 5 && adLifecycleActive) {
              logStream(
                  "Retrying ad load in "
                      + retryDelayMillis
                      + "ms (Attempt "
                      + adRetryAttempt
                      + ")");
              adRetryHandler.postDelayed(
                  () -> {
                    if (!adLifecycleActive || generation != adLoadGeneration) {
                      return;
                    }
                    ensureRewardedAdPreloaded();
                  },
                  retryDelayMillis);
            } else {
              if (isWaitingForAd) {
                isWaitingForAd = false;
                if (chargeCreditDialog != null && chargeCreditDialog.isShowing()) {
                  chargeCreditDialog.dismiss();
                }
                if (isAdded()) {
                  Toast.makeText(
                      getContext(), "광고를 불러오는 데 실패했어요. 나중에 다시 시도해주세요.", Toast.LENGTH_SHORT)
                      .show();
                }
              }
            }
          }

          @Override
          public void onAdLoaded(@NonNull RewardedAd ad) {
            if (generation != adLoadGeneration) {
              return;
            }
            isAdLoading = false;
            if (!adLifecycleActive || !isAdded()) {
              return;
            }
            logStream("Rewarded ad loaded.");
            rewardedAd = ad;
            adRetryAttempt = 0;
            setFullScreenContentCallback(ad);

            if (isWaitingForAd && isResumed()) {
              dismissChargeCreditDialog();
              showRewardedAdIfAvailable();
            }
          }
        });
  }

  private void showRewardedAdIfAvailable() {
    if (!adLifecycleActive || !isAdded() || !isResumed()) {
      return;
    }
    RewardedAd adToShow = rewardedAd;
    if (adToShow == null) {
      return;
    }

    rewardedAd = null;
    isAdShowing = true;
    isRewardEarned = false;
    adToShow.show(
        requireActivity(),
        rewardItem -> {
          isRewardEarned = true;
          increaseCredit();
        });
  }

  private void setFullScreenContentCallback(@NonNull RewardedAd ad) {
    ad.setFullScreenContentCallback(
        new FullScreenContentCallback() {
          @Override
          public void onAdShowedFullScreenContent() {
            isAdShowing = true;
          }

          @Override
          public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
            isAdShowing = false;
            if (!adLifecycleActive) {
              return;
            }
            logStream("Ad failed to show: " + adError.getMessage());
          }

          @Override
          public void onAdDismissedFullScreenContent() {
            isAdShowing = false;
            if (!adLifecycleActive) {
              return;
            }
            logStream("Ad dismissed");
            if (!isRewardEarned && isAdded()) {
              Toast.makeText(getContext(), "광고 시청을 완료하지 않아 크레딧이 지급되지 않았어요", Toast.LENGTH_SHORT)
                  .show();
            }
          }
        });
  }

  private void increaseCredit() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      Toast.makeText(getContext(), "로그인 정보를 확인할 수 없어요", Toast.LENGTH_SHORT).show();
      return;
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .update("credit", com.google.firebase.firestore.FieldValue.increment(1))
        .addOnSuccessListener(
            aVoid -> {
              if (isAdded()) {
                Toast.makeText(getContext(), "1 크레딧이 충전되었어요", Toast.LENGTH_SHORT).show();
              }
            })
        .addOnFailureListener(
            e -> {
              logStream("Failed to add credit: " + e.getMessage());
              if (isAdded()) {
                Toast.makeText(getContext(), "크레딧 충전에 실패했어요", Toast.LENGTH_SHORT).show();
              }
            });
  }
}
