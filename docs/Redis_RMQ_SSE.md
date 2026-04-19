# Redis & RabbitMQ 상세 아키텍처 가이드 (v2.5)

이 문서는 WithBuddy 백엔드에서 Redis 캐싱과 RabbitMQ 메시징을 실제 구현하기 위한 상세 아키텍처 가이드 v2.5 완성형이다. OCI A1.Flex 환경(12GB RAM, MySQL + Redis + RabbitMQ + Object Storage 공존)을 전제로 한다.

---

## 1. 아키텍처 다이어그램 및 흐름도

기존 1-2 다이어그램을 유지하되, DLX 및 Object Storage 직접 다운로드 경로를 강조한다.

```jsx
[Client] ────── (1) API Request / SSE Connection ──────┐
   │                                                   │
   ▼                                                   ▼
[Spring Boot API Server (api-wb.itsdev.com)] ◀── (2) Auth & Session Check ──▶ [Redis Cache]
   │                                                                       (sse:session / presigned:url)
   ├── (3) Metadata Read/Write ──▶ [MySQL DB]
   │
   ├── (4) Async Event Publish ──▶ [RabbitMQ Exchange]
   │                                     │
   │           ┌─────────────────────────┴────────────────────────┐
   │           ▼                                                 ▼
   │    [Queue: q.nudge]                                  [Queue: q.analytics]
   │           │                                                 │
   │           ▼                                                 ▼
   │    [Consumer: 알림/SSE]                              [Consumer: 로그/분석]
   │           │
   │           │ (5) Get Pre-signed URL & Route Session
   │           └──────────▶ [Redis] ──▶ [OCI Object Storage]
   │                          │               │
   ▼                          ▼               │ (7) Direct Download
(6) SSE Push (Real-time Message + OCI URL) ◀──┘

[실패 시 DLX 흐름]
[Queue: q.nudge] ──3회 실패──▶ [Dead Letter Exchange: withbuddy.dlx]
                                         │
                                         ▼
                                  [Queue: q.dlq.nudge]
                                  (수동 처리 / 알람)
```

---

## 2. 인프라 및 도메인 설정

- **API Endpoint**: `https://api-wb.itsdev.com`
- **환경**: OCI A1.Flex 2 OCPU / 12GB RAM
- **JVM**: `-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200`
- **커널**: `net.core.somaxconn=1024`, `vm.overcommit_memory=1`
- **메모리 배분**: MySQL innodb_buffer_pool 6GB / Redis maxmemory 3GB / OS+JVM 3GB

> ⚠️ 12GB 공유 환경에서 JVM `-Xmx8g` 설정은 Redis·RabbitMQ·OS 여유 메모리를 4GB로 압박한다. `-Xmx2g`로 제한하고 Redis maxmemory를 명시적으로 설정한다.
> 

```bash
# /etc/redis/redis.conf
maxmemory 3gb
maxmemory-policy allkeys-lru   # TTL 미설정 키 포함 전체 LRU 제거
tcp-keepalive 60
timeout 300
save ""
```

### OCI NSG (Network Security Group) 보안 규칙

RabbitMQ 관리 콘솔(15672)과 Redis(6379)는 외부 노출 시 보안 위험이 크다. 아래 규칙에 따라 내부 VCN 대역에서만 접근을 허용한다.

| 방향 | 소스 / 목적지 | 포트 | 설명 |
| --- | --- | --- | --- |
| Inbound | `0.0.0.0/0` | 8080 | API 서버 (외부 공개) |
| Inbound | VCN 내부만 (`10.0.0.0/16`) | 6379 | Redis (외부 차단) |
| Inbound | VCN 내부만 (`10.0.0.0/16`) | 5672 | RabbitMQ AMQP (외부 차단) |
| Inbound | 관리자 IP만 | 15672 | RabbitMQ 관리 콘솔 (외부 차단) |
| Inbound | VCN 내부만 (`10.0.0.0/16`) | 3306 | MySQL (외부 차단) |

---

## 3. 핵심 도메인 정의

### 3-1. NudgeType 및 NudgeEvent (멱등성 보장)

`record`에 `eventId`를 추가하고, 누락된 enum을 정의한다.

```java
public enum NudgeType {
    GENERAL, FILE, RAG_RESULT, SYSTEM_ALERT
}

public record NudgeEvent(
    String eventId,   // UUID 기반 멱등성 키
    Long userId,
    String message,
    String fileId,    // OCI Object ID (FILE 타입일 때만 사용)
    NudgeType type
) {}
```

### 3-2. 캐시 키 관리 테이블 (완성형)

누락되었던 모든 서비스 및 인프라 키를 통합한다.

| 도메인 | 키 패턴 | TTL | 설명 |
| --- | --- | --- | --- |
| 세션 | `session:token:{token}` | 24시간 | 로그인 세션 정보 관리 |
| 사용자 | `user:profile:{userId}` | 30분 | 자주 조회되는 사용자 프로필 |
| 버디 | `buddy:list:{userId}:page:{n}` | 10분 | 버디 목록 페이지네이션 캐시 |
| 매칭 | `match:status:{userId}` | 5분 | 실시간 매칭 상태 데이터 |
| SSE | `sse:session:{userId}` | 32분 | 연결된 서버 인스턴스 ID (라우팅) |
| SSE | `sse:missed:{userId}` | 5분 | 오프라인 시 미수신 메시지 큐 (Redis List) |
| 파일 | `presigned:url:{fileId}` | 10분 | URL(15분 유효) 캐시, 안전 마진 5분 |
| RAG | `rag:status:{reqId}` | 5분 | AI답변 처리상태 (PENDING, COMPLETED, TIMEOUT) |
| 알림 | `notif:read:{userId}:{notifId}` | 7일 | 알림 읽음 처리 여부 확인용 |
| 온보딩 | `buddy:day:{userId}` | 24시간 | 사용자의 현재 온보딩 일차 (D+N) |
| 온보딩 | `nudge:sent:{userId}:{day}` | 48시간 | 동일 D+N 중복 발송 방지 (멱등성) |
| 온보딩 | `quicktap:{day}` | 1시간 | D+0·D+3 등 시점별 Quick Tap 버튼 목록 |
| 대화 | `conversation:{sessionId}` | 30분 | RAG 답변용 이전 대화 흐름 |
| 동기화 | `lock:{resource}:{id}` | 10초 | RedisLockManager 분산 락 |
| 문서 | `form:generated:{userId}:{formType}` | 24시간 | 생성 완료된 문서의 objectKey |
| 문서 | `docs:list:{userId}:{formType}:first` | 5분 | 문서 목록 첫 페이지 캐시 |

---

## 4. RabbitMQ & Redis 상세 구성

### 4-1. RabbitMQConfig (DLX 및 메시지 신뢰성)

```java
@Configuration
@Slf4j
public class RabbitMQConfig {

    public static final String MAIN_EXCHANGE   = "withbuddy.events";
    public static final String DLX_EXCHANGE    = "withbuddy.dlx";
    public static final String NUDGE_QUEUE     = "q.nudge";
    public static final String ANALYTICS_QUEUE = "q.analytics";
    public static final String DLQ_NUDGE       = "q.dlq.nudge";

    @Bean
    public TopicExchange mainExchange() {
        return ExchangeBuilder.topicExchange(MAIN_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue nudgeQueue() {
        return QueueBuilder.durable(NUDGE_QUEUE)
            .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "dlq.nudge")
            .withArgument("x-message-ttl", 86400000)  // 24시간
            .build();
    }

    @Bean
    public Queue analyticsQueue() {
        return QueueBuilder.durable(ANALYTICS_QUEUE)
            .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "dlq.analytics")
            .build();
    }

    @Bean
    public Queue dlqNudgeQueue() {
        return QueueBuilder.durable(DLQ_NUDGE).build();
    }

    @Bean
    public Binding nudgeBinding() {
        return BindingBuilder.bind(nudgeQueue()).to(mainExchange()).with("nudge.#");
    }

    @Bean
    public Binding analyticsBinding() {
        return BindingBuilder.bind(analyticsQueue()).to(mainExchange()).with("analytics.#");
    }

    @Bean
    public Binding dlqNudgeBinding() {
        return BindingBuilder.bind(dlqNudgeQueue()).to(dlxExchange()).with("dlq.nudge");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2MessageConverter());
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) log.error("[RMQ] 발행 실패. cause={}, data={}", cause, correlationData);
        });
        template.setReturnsCallback(returned ->
            log.error("[RMQ] 라우팅 실패. routingKey={}, replyText={}",
                returned.getRoutingKey(), returned.getReplyText())
        );
        template.setMandatory(true);
        return template;
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2MessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2MessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);  // 실패 시 DLX로 이동
        return factory;
    }
}
```

### 4-2. RedisConfig (Lettuce & Serializer)

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(2))
            .clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                    .keepAlive(true)
                    .connectTimeout(Duration.ofSeconds(2))
                    .build())
                .build())
            .build();

        RedisStandaloneConfiguration serverConfig =
            new RedisStandaloneConfiguration("<REDIS_PRIVATE_IP>", 6379);

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        Jackson2JsonRedisSerializer<Object> jsonSerializer =
            new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL
        );
        jsonSerializer.setObjectMapper(om);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("userProfile", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("buddyList",   defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("matchStatus", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}
```

### 4-3. RedisLockManager (분산 락)

중복 요청 방지(매칭 신청 중복 등)를 위한 분산 락 구현이다.

```java
@Component
@RequiredArgsConstructor
public class RedisLockManager {

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(String key, long timeoutSeconds) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent("lock:" + key, "locked", Duration.ofSeconds(timeoutSeconds));
        return Boolean.TRUE.equals(result);
    }

    public void unlock(String key) {
        redisTemplate.delete("lock:" + key);
    }

    public <T> T withLock(String key, long timeoutSec, Supplier<T> action) {
        if (!tryLock(key, timeoutSec)) {
            throw new ConflictException("이미 처리 중인 요청입니다.");
        }
        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }
}
```

---

## 5. SSE & Consumer 연계

`SseEventRouter.route()`의 3개 인자 형식을 준수하여 수정한다.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final SseEventRouter eventRouter;
    private final FileService fileService;
    private final EventLogRepository eventLogRepository;

    @RabbitListener(queues = "q.nudge",
        containerFactory = "rabbitListenerContainerFactory")
    public void handleNudge(
            @Payload NudgeEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        try {
            // 1. 멱등성 1차 필터 — Redis SETNX (Race Condition 방지)
            // 동일 eventId가 수 ms 간격으로 중복 전달될 경우 exists→save 사이 경합 발생 가능.
            // SETNX로 선점 후 DB 저장하여 원자적으로 중복을 차단한다.
            // EventLog 테이블의 eventId 컬럼에 UNIQUE 제약 조건을 추가하면 DB 레벨 fallback도 보장된다.
            String idempotencyKey = "idempotency:nudge:" + event.eventId();
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "processing", Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(isNew)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            // 2. DB 중복 체크 (UNIQUE 제약 조건 fallback)
            if (eventLogRepository.existsByEventId(event.eventId())) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 파일 URL 준비 (presigned:url 캐시 활용)
            // FILE 외에 RAG_RESULT도 답변이 PDF/텍스트 파일로 제공될 수 있으므로
            // fileId가 존재하는 모든 타입에 대해 Pre-signed URL을 생성한다.
            boolean needsFileUrl = (event.type() == NudgeType.FILE
                                 || event.type() == NudgeType.RAG_RESULT)
                                 && event.fileId() != null;
            String actionUrl = needsFileUrl
                ? fileService.getPresignedUrl(event.fileId())
                : null;

            // 4. SSE 라우팅 (v1 시그니처: userId, eventName, data)
            eventRouter.route(
                event.userId(),
                "nudge_arrival",
                new SsePayload(event.message(), actionUrl)
            );

            // 5. 처리 완료 기록
            eventLogRepository.save(new EventLog(event.eventId()));
            channel.basicAck(deliveryTag, false);
            log.info("[NUDGE] 처리 완료. eventId={}", event.eventId());

        } catch (Exception e) {
            log.error("[NUDGE] 처리 실패. eventId={}", event.eventId(), e);
            try { channel.basicNack(deliveryTag, false, false); }
            catch (IOException io) { log.error("[NUDGE] Nack 실패", io); }
        }
    }
}
```

### 5-1. FileService (Pre-signed URL 생성 및 캐싱)

```java
@Service
@RequiredArgsConstructor
public class FileService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectStorage objectStorageClient;

    private static final Duration URL_VALID_DURATION = Duration.ofMinutes(15);
    private static final Duration CACHE_TTL          = Duration.ofMinutes(10); // 안전 마진 5분

    public String getPresignedUrl(String fileId) {
        String cacheKey = "presigned:url:" + fileId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        // OCI SDK로 Pre-signed URL 생성 (15분 유효)
        CreateAuthenticatedRequestDetails requestDetails =
            CreateAuthenticatedRequestDetails.builder()
                .bucketName("withbuddy-files")
                .objectName(fileId)
                .accessType(CreateAuthenticatedRequestDetails.AccessType.ObjectRead)
                .timeExpires(Date.from(Instant.now().plus(URL_VALID_DURATION)))
                .build();

        String freshUrl = objectStorageClient
            .createAuthenticatedRequest(requestDetails)
            .getAuthenticatedRequest()
            .getAccessUri().toString();

        // Redis에 10분 캐싱 (URL 만료 5분 전에 캐시 만료 → 항상 유효한 URL 반환)
        redisTemplate.opsForValue().set(cacheKey, freshUrl, CACHE_TTL);
        return freshUrl;
    }
}
```

### 5-2. SseEventRouter & Redis Pub/Sub (서버 간 이벤트 전파)

`api-wb.itsdev.com`이 다중 인스턴스로 동작할 때, 특정 사용자의 SSE 연결이 A 서버에 있고 Consumer는 B 서버에서 실행될 수 있다. Redis Pub/Sub으로 서버 간 이벤트를 브리지한다.

> **채널 설계 전략**: 각 인스턴스가 `sse:route:{instanceId}` 개별 채널을 구독하는 **타겟팅 방식**이다. Consumer가 메시지를 처리할 때 `sse:session:{userId}`에서 대상 서버를 먼저 조회한 뒤 해당 채널에만 Publish하므로, 모든 서버가 불필요한 메시지를 수신하는 브로드캐스트(`sse:broadcast`) 방식 대비 트래픽과 CPU 효율이 우월하다.
> 

```java
// Publisher — Consumer에서 route() 호출 시 동작
@Component
@RequiredArgsConstructor
public class SseEventRouter {

    private final SseSessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${server.instance-id}")
    private String instanceId;

    public void route(Long userId, String eventName, Object data) {
        // 1. 로컬에 세션이 있으면 직접 전송
        sessionManager.get(userId).ifPresentOrElse(
            emitter -> sendToEmitter(emitter, eventName, data),
            () -> {
                // 2. 다른 서버에 연결된 경우 Redis Pub/Sub으로 전파
                String targetInstance = redisTemplate.opsForValue()
                    .get("sse:session:" + userId);
                if (targetInstance != null) {
                    String channel = "sse:route:" + targetInstance;
                    String payload = serialize(Map.of(
                        "userId",    userId,
                        "eventName", eventName,
                        "data",      data
                    ));
                    redisTemplate.convertAndSend(channel, payload);
                }
                // targetInstance == null → 오프라인, FCM 폴백
            }
        );
    }

    private void sendToEmitter(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private String serialize(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
```

```java
// Subscriber — 애플리케이션 기동 시 자신의 인스턴스 채널 구독
@Configuration
public class RedisPubSubConfig {

    @Value("${server.instance-id}")
    private String instanceId;

    @Bean
    public RedisMessageListenerContainer redisMessageListener(
            RedisConnectionFactory connectionFactory,
            SseEventRouter router,
            ObjectMapper objectMapper) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 자신의 인스턴스 채널만 구독 (sse:route:{instanceId})
        container.addMessageListener((message, pattern) -> {
            try {
                Map<String, Object> payload =
                    objectMapper.readValue(message.getBody(), Map.class);
                Long userId    = Long.valueOf(payload.get("userId").toString());
                String evtName = payload.get("eventName").toString();
                Object data    = payload.get("data");
                router.route(userId, evtName, data);
            } catch (IOException e) {
                log.error("[PubSub] 역직렬화 실패", e);
            }
        }, new ChannelTopic("sse:route:" + instanceId));

        return container;
    }
}
```

```java
// 로컬 SSE 세션 레지스트리
@Component
public class SseSessionManager {

    private final ConcurrentHashMap<Long, SseEmitter> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, SseEmitter emitter) { sessions.put(userId, emitter); }
    public void remove(Long userId)                        { sessions.remove(userId); }
    public Optional<SseEmitter> get(Long userId)           { return Optional.ofNullable(sessions.get(userId)); }
}
```

---

## 6. 캐시 일관성 정책

데이터 정합성을 위해 Look-aside와 Cache Eviction 전략을 병행한다.

### 6-1. Read: Look-aside (조회 성능 최적화)

`presigned:url` 및 사용자 프로필 등 조회 시 활용한다.

```jsx
Client Request (Read)
     │
     ▼
[Service Layer]
     │
     ├── (1) redisTemplate.get(key)
     │           │
     │      ┌────┴────┐
     │   HIT│         │ MISS (2)
     │      ▼         ▼
     │   return    [MySQL / OCI SDK Query]
     │   cached       │
     │   data         ▼ (3)
     │             redisTemplate.set(key, data, TTL)
     │                  │
     └──────────────────▼
                   return data
```

### 6-2. Write: Cache Eviction (정합성 보장)

DB 업데이트 시 캐시를 즉시 삭제하여 `api-wb.itsdev.com`과 DB 간 일관성을 유지한다.

```jsx
Client Request (Write)
     │
     ▼
[Service Layer]
     │
     ├── (1) [MySQL] INSERT / UPDATE
     │
     └── (2) [Redis] DELETE(key)  ◀─ Cache Eviction (정합성 보장)
```

---

## 업데이트 요약 (v2.5)

1. **시그니처 일치**: `eventRouter.route()` 호출 시 `eventName("nudge_arrival")`을 추가하여 v1 구현체와 컴파일 수준에서 맞추었다.
2. **데이터 무결성**: `eventId` 필드를 부활시켜 중복 수신 문제를 원천 차단하였다.
3. **안전 마진 명시**: URL 유효 기간 15분, 캐시 10분으로 공식화하여 안전 마진 5분을 확보하였다.
4. **NudgeType enum 정의**: `GENERAL`, `FILE`, `RAG_RESULT`, `SYSTEM_ALERT` 4종을 추가하였다.
5. **캐시 키 완성**: 누락된 `buddy:day`, `nudge:sent`, `quicktap:`, `conversation:`, `sse:missed:`, `docs:list:`, `form:generated:` 등 전체 키를 복구하였다.
6. **섹션 복구**: 분산 락(4-3), RedisConfig(4-2), RabbitMQConfig(4-1), FileService(5-1) 상세 구현을 추가하여 진정한 의미의 Full Version으로 구성하였다.