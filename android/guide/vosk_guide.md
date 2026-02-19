# Vosk Android 실시간 STT 구현 가이드 (Markdown)
이 문서는 Vosk Android SDK를 사용하여 오프라인 환경에서 실시간 한국어 음성 인식(STT) 기능을 구현하려는 개발자를 위한 가이드입니다.

1. 프로젝트 환경 설정
Vosk는 오프라인 기반이며, 성능 최적화를 위해 네이티브 라이브러리를 사용합니다. 최신 안드로이드 하드웨어(16KB 페이지 크기 등)와의 호환성을 위해 최신 버전의 JNA 라이브러리를 병행 사용하는 것이 필수적입니다.

1.1 Gradle 의존성 추가
app/build.gradle 파일에 다음과 같이 저장소 및 라이브러리를 추가합니다.

```Gradle
repositories {
    mavenCentral()
}

dependencies {
    // Vosk Android SDK (최신 버전 0.3.75 권장) [2]
    implementation 'com.alphacephei:vosk-android:0.3.75'
    
    // JNA 라이브러리 (16KB 페이지 및 안드로이드 14/15 호환성을 위해 5.18.1 이상 필수)
    implementation 'net.java.dev.jna:jna:5.18.1'
}
```
2. 한국어 언어 모델 준비
Vosk는 모델 파일이 있어야 작동합니다. 모바일 환경에서는 성능과 메모리 효율을 고려하여 Small 모델 사용을 강력히 권장합니다.

권장 모델: vosk-model-small-ko-0.22 

다운로드: 모델 다운로드 링크

특징: 약 82MB(압축 전), 런타임 메모리 약 300MB 소모.

모델 배치 방법
다운로드한 모델의 압축을 풀고 폴더 이름을 model-ko로 변경한 뒤, 안드로이드 프로젝트의 src/main/assets 폴더 아래에 넣습니다.

3. 권한 및 매니페스트 설정
실시간 음성 인식을 위해 마이크 권한과 포그라운드 서비스 설정이 필요합니다.

AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<application...>
    <service
        android:name=".STTService"
        android:foregroundServiceType="microphone"
        android:exported="false" />
</application>
```

4. 핵심 소스 코드 구현 (Java/Kotlin)
4.1 모델 로딩 및 초기화
StorageService를 사용하여 assets에 있는 모델을 기기 내부 저장소로 복사하고 초기화합니다.

```Java
private Model model;

private void initModel() {
    // assets/model-ko 폴더를 기기 내부 "model" 폴더로 복사
    StorageService.unpack(this, "model-ko", "model",
        (model) -> {
            this.model = model;
            startRecognition(); 
        },
        (exception) -> {
            Log.e("Vosk", "모델 로드 실패: " + exception.getMessage());
        });
}
```

4.2 실시간 인식 엔진(SpeechService) 실행
SpeechService는 별도 스레드에서 마이크 입력을 처리하여 UI 스레드 차단을 방지합니다.


```Java
private SpeechService speechService;

private void startRecognition() {
    try {
        // 한국어 모델 샘플 레이트인 16000Hz 설정 [2, 8]
        KaldiRecognizer rec = new KaldiRecognizer(model, 16000.0f);
        
        // (선택) 어휘 제한을 통한 정확도 향상: 특정 단어만 인식하게 설정 가능 [9, 10]
        // rec.setGrammar("[\"예\", \"아니오\", \"정지\", \"시작\"]");

        speechService = new SpeechService(rec, 16000.0f);
        speechService.startListening(new RecognitionListener() {
            @Override
            public void onPartialResult(String hypothesis) {
                // 발화 중 실시간 결과 (JSON 형태) [11, 12]
                Log.d("Vosk", "중간 결과: " + hypothesis);
            }

            @Override
            public void onResult(String hypothesis) {
                // 문장 완료 후 확정 결과 [11, 12]
                Log.d("Vosk", "최종 결과: " + hypothesis);
            }

            @Override
            public void onFinalResult(String hypothesis) {
                // 서비스 종료 시 최종 결과
            }

            @Override
            public void onError(Exception exception) {
                Log.e("Vosk", "오류: " + exception.getMessage());
            }

            @Override
            public void onTimeout() { }
        });
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

5. 배포 최적화 및 주의사항
5.1 ProGuard 설정 (Release 빌드 시)
코드 난독화 시 네이티브 브릿지인 JNA 관련 클래스가 제거되지 않도록 규칙을 추가해야 합니다.

코드 스니펫
# JNA 및 네이티브 라이브러리 유지
```
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keep class org.vosk.** { *; }
```

5.2 메모리 및 리소스 관리
인스턴스 재사용: Model 객체는 용량이 크므로 앱 내에서 단일 인스턴스(Singleton)로 관리하고 재사용하는 것이 좋습니다.

리소스 해제: onDestroy() 등 앱 종료 시점에 반드시 speechService.stop() 및 shutdown()을 호출하여 네이티브 메모리 누수를 방지하십시오.

오류 대응: 안드로이드 14 이상에서 백그라운드에서 서비스를 시작할 경우 SecurityException이 발생할 수 있으므로, 항상 포그라운드 상태에서 마이크 접근을 시작해야 합니다.

6. 개발 최적화 팁
- 어휘 제한 (Grammar): 특정 명령어(예: "예", "아니오", "취소")만 인식해야 하는 경우, 인식기 생성 시 JSON 배열로 단어 리스트를 전달하면 정확도와 속도가 비약적으로 향상됩니다. 
- 리소스 해제: Activity나 Service가 종료될 때 반드시 speechService.stop() 및 speechService.shutdown()을 호출하여 네이티브 메모리 누수를 방지하십시오. 
-샘플 레이트: 안드로이드 마이크 설정과 Vosk 인식기의 샘플 레이트(16000.0f)를 반드시 일치시켜야 정확한 인식이 가능합니다. 