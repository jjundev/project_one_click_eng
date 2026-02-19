Android 프로젝트 구성 및 Java SDK 통합Android 개발자가 Gemini API의 캐싱 기능을 사용하기 위해서는 우선적으로 Google GenAI SDK 또는 Vertex AI SDK를 통합해야 한다. 2025년 5월 이후 Google은 기존의 레거시 라이브러리 대신 통합된 google-genai 라이브러리 사용을 강력히 권장하고 있다.Gradle 의존성 설정Java 프로젝트에서 명시적 캐싱 기능을 구현하기 위해서는 build.gradle 파일에 다음과 같은 종속성을 추가해야 한다. 최신 기능을 활용하기 위해 3.46.0 이상의 버전을 권장한다.Gradledependencies {
    // Vertex AI Java SDK (Prompt Caching 지원 버전)
    implementation("com.google.cloud:google-cloud-aiplatform:3.46.0")
    
    // GenAI SDK for Java
    implementation("com.google.api:google-api-client:2.0.0")
}
주의할 점은 Firebase AI Logic SDK(구 Vertex AI in Firebase)는 현재 시점에서 명시적 프롬프트 캐싱 기능을 지원하지 않는다는 사실이다. 따라서 캐싱 기능이 필수적인 Android 앱의 경우, Firebase를 통하지 않고 Vertex AI Java SDK를 직접 사용하거나 REST API 호출 방식을 선택해야 한다.Java를 이용한 명시적 캐시 구현 절차명시적 캐싱의 구현은 '생성 - 참조 - 관리 - 삭제'의 4단계 라이프사이클을 따른다.1단계: 캐시 리소스 생성 (CachedContent.create)캐시를 생성할 때는 대상이 되는 모델 명칭, 저장할 콘텐츠, 그리고 캐시의 유효 기간(TTL)을 지정해야 한다. 기본 TTL은 1시간이며, 필요에 따라 특정 시각(expire_time)을 지정할 수도 있다.Javaimport com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.CachedContent;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.Part;
import com.google.protobuf.Duration;

public class GeminiCacheService {
    public String createContextCache(String projectId, String location) throws Exception {
        try (VertexAI vertexAi = new VertexAI(projectId, location)) {
            // 캐시할 대용량 컨텍스트 준비
            Content staticContext = Content.newBuilder()
               .setRole("user")
               .addParts(Part.newBuilder().setText("여기에 분석할 수만 줄의 코드나 대규모 문서를 삽입합니다."))
               .build();

            // CachedContent 설정
            CachedContent cacheConfig = CachedContent.newBuilder()
               .setModel("projects/" + projectId + "/locations/" + location + "/publishers/google/models/gemini-1.5-pro-001")
               .setDisplayName("LargeDocumentCache")
               .addContents(staticContext)
               .setTtl(Duration.newBuilder().setSeconds(3600).build()) // 1시간 유지
               .build();

            // 예측 서비스 클라이언트를 통한 캐시 생성
            var client = vertexAi.getPredictionServiceClient();
            CachedContent response = client.createCachedContent(
                "projects/" + projectId + "/locations/" + location, 
                cacheConfig
            );

            // 생성된 캐시의 고유 이름 반환 (format: projects/.../locations/.../cachedContents/ID)
            return response.getName();
        }
    }
}
2단계: 캐시를 이용한 콘텐츠 생성 (GenerateContent)캐시가 생성되면 해당 리소스 이름을 generateContent 요청의 cached_content 필드에 포함시켜 모델에 전달한다. 이때 모델은 캐시된 내용을 프롬프트의 접두사로 인식하여 추론을 시작한다.Javapublic String queryWithCache(String cacheName, String newUserQuery) {
    // GenerativeModel 설정 시 캐시 이름을 전달
    GenerativeModel model = new GenerativeModel("gemini-1.5-pro", vertexAi);
    
    // 캐시된 컨텍스트를 기반으로 질문 수행
    // 주의: 캐시에 포함된 system_instructions나 tools는 여기서 다시 설정하지 않음 [14]
    var response = model.setCachedContent(cacheName)
                       .generateContent(newUserQuery);
                        
    return response.getText();
}
3단계: 캐시 메타데이터 조회 및 TTL 업데이트캐시는 생성된 이후에도 유효 기간을 연장할 수 있다. 캐시가 만료되면 자동으로 삭제되며 복구할 수 없으므로, 지속적인 사용이 예상되는 경우 patch 메서드를 통해 TTL을 갱신해야 한다.관리 작업메서드 및 목적목록 조회cachedContents.list: 현재 프로젝트 내 모든 활성 캐시 메타데이터 확인 상세 정보cachedContents.get: 특정 캐시의 생성 시간, 만료 시간, 토큰 사용량 확인 TTL 갱신cachedContents.patch: 유효 기간을 연장하여 캐시 폐기 방지 수동 삭제cachedContents.delete: 비용 발생을 중단하기 위해 즉시 리소스 제거 