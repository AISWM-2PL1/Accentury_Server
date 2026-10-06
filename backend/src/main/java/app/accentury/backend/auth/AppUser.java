package app.accentury.backend.auth;

import app.accentury.backend.session.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 앱 로그인 사용자 = 계정 1행 (KAN-223, 명세서 §3.9, §5.5).
 * <p>
 * 계정 식별은 (provider, provider_user_id)이고 이메일은 식별자가 아니다 - 같은 이메일로 다른 IdP에 로그인하면
 * 별개 계정이다 (계정 연동 FR-AC-04는 범위 밖).
 * <p>
 * <b>프로필 완료 판정은 이메일, 이름, 생년월일, 성별, 출신지역 다섯 개로만 한다.</b> 다섯 개가 다 있으면
 * {@code profileCompletedAt}이 찍히고, 그 전에는 세션을 만들 수 없다 (§3.1 403 {@code AUTH_PROFILE_INCOMPLETE}).
 * 닉네임과 프로필 이미지는 IdP가 줄 때만 남기는 보조 정보라 판정과 무관하다.
 * <p>
 * 이메일, 이름, 생년월일은 로그에 남기지 않는다 (§2.6). {@link #toString()}을 재정의하지 않는 것도 그래서다 -
 * 기본 구현은 값을 찍지 않는다.
 * <p>
 * {@link Persistable}인 이유는 {@code TestSession}과 같다 - 식별자를 직접 정하는 엔티티라 {@code save()}가
 * merge로 가면 가입마다 빗나가는 SELECT 한 번을 낸다.
 */
@Entity
@Table(name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "ux_app_user_provider_subject",
                columnNames = {"provider", "provider_user_id"}))
public class AppUser implements Persistable<UUID> {

    @Id
    @Column
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Provider provider;

    /** IdP의 사용자 id - 구글과 애플 {@code sub}, 카카오와 네이버 {@code id}. */
    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(length = 254)
    private @Nullable String email;

    @Column(length = 50)
    private @Nullable String name;

    @Column(name = "birth_date")
    private @Nullable LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private @Nullable Gender gender;

    /** 출신지역 코드 ({@link Region}의 10개 중 하나). 계정 세션의 {@code test_session.region}이 된다 (§3.1). */
    @Column(length = 16)
    private @Nullable String region;

    @Column(length = 100)
    private @Nullable String nickname;

    @Column(name = "profile_image_url", length = 1024)
    private @Nullable String profileImageUrl;

    /** 다섯 항목이 처음 다 채워진 시각 - null이면 프로필 미완료다. */
    @Column(name = "profile_completed_at")
    private @Nullable Instant profileCompletedAt;

    /** 가입 때 받은 개인정보 수집 이용 동의 시각 (§3.9). 재로그인은 바꾸지 않는다. */
    @Column(name = "privacy_consent_at", nullable = false)
    private Instant privacyConsentAt;

    /** 동의한 개인정보처리방침 버전 문자열. */
    @Column(name = "privacy_policy_version", nullable = false, length = 32)
    private String privacyPolicyVersion;

    /**
     * 음성 저장(학습 활용) 선택 동의의 버전 (KAN-269). null이면 한 번도 동의하지 않은 계정이다. 개인정보 동의와
     * 별개이고, 동의하지 않아도 서비스 이용에는 제한이 없다.
     */
    @Column(name = "voice_consent_version", length = 32)
    private @Nullable String voiceConsentVersion;

    @Column(name = "voice_consent_at")
    private @Nullable Instant voiceConsentAt;

    /** 음성 저장 동의를 철회한 시각 - 값이 있으면 동의가 없는 것으로 본다. 버전과 동의 시각은 이력으로 남는다. */
    @Column(name = "voice_consent_withdrawn_at")
    private @Nullable Instant voiceConsentWithdrawnAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 탈퇴 시각 (FR-AC-09, KAN-241). 값이 있는 계정은 없는 사용자로 본다 - {@link #withdraw} 참고. */
    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @Transient
    private boolean isNew = false;

    protected AppUser() {
        // JPA 전용
    }

    /**
     * 가입 - IdP가 준 값으로 채운다. 동의는 호출 쪽({@code AuthService})이 이미 확인했다.
     */
    AppUser(IdpProfile profile, String privacyPolicyVersion, Instant now) {
        this.isNew = true;
        this.id = UUID.randomUUID();
        this.provider = profile.provider();
        this.providerUserId = profile.subject();
        this.email = profile.email();
        this.name = profile.name();
        this.birthDate = profile.birthDate();
        this.gender = profile.gender();
        this.nickname = profile.nickname();
        this.profileImageUrl = profile.profileImageUrl();
        this.privacyConsentAt = now;
        this.privacyPolicyVersion = privacyPolicyVersion;
        this.createdAt = now;
        this.updatedAt = now;
        markCompletionIfReady(now);
    }

    /**
     * 재로그인 - 비어 있는 항목만 IdP 값으로 채운다 (§3.9). 사용자가 추가 정보 화면에서 이미 입력한 값은
     * 덮지 않는다: IdP의 값이 바뀌었다고 사용자가 고친 이름이 되돌아가면 안 된다.
     *
     * @return 바뀐 항목이 있었는가
     */
    boolean fillBlanksFrom(IdpProfile profile, Instant now) {
        boolean changed = false;
        if (email == null && profile.email() != null) {
            email = profile.email();
            changed = true;
        }
        if (name == null && profile.name() != null) {
            name = profile.name();
            changed = true;
        }
        if (birthDate == null && profile.birthDate() != null) {
            birthDate = profile.birthDate();
            changed = true;
        }
        if (gender == null && profile.gender() != null) {
            gender = profile.gender();
            changed = true;
        }
        if (nickname == null && profile.nickname() != null) {
            nickname = profile.nickname();
            changed = true;
        }
        if (profileImageUrl == null && profile.profileImageUrl() != null) {
            profileImageUrl = profile.profileImageUrl();
            changed = true;
        }
        if (changed) {
            updatedAt = now;
            markCompletionIfReady(now);
        }
        return changed;
    }

    /**
     * 추가 정보 화면의 다섯 항목 (§3.10). 검증은 호출 쪽이 끝냈다. 이미 완료된 프로필에 다시 부르면 값만
     * 갱신하고 완료 시각은 처음 것을 둔다 (멱등).
     */
    void updateProfile(String email, String name, LocalDate birthDate, Gender gender, Region region, Instant now) {
        this.email = email;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.region = region.name();
        this.updatedAt = now;
        markCompletionIfReady(now);
    }

    /** 탈퇴 행의 {@code provider_user_id} 접두사 (KAN-241). IdP 사용자 id는 이 문자로 시작하지 않는다. */
    static final String WITHDRAWN_SUBJECT_PREFIX = "deleted:";

    /**
     * 탈퇴 (FR-AC-09, KAN-241, 명세서 §3.14) - 행은 남기고 개인 정보는 지금 지운다.
     * <p>
     * 이메일, 이름, 생년월일, 성별, 출신지역, 닉네임, 프로필 이미지를 null로 덮는다. {@code provider_user_id}는
     * {@code deleted:<계정 id>}로 바꿔 유일 제약 (provider, provider_user_id)의 자리를 비운다 - 같은 IdP 계정으로
     * 다시 로그인하면 새 계정으로 가입된다. IdP 사용자 id의 해시가 아니라 계정 id인 이유는 둘이다: 같은 IdP 계정이
     * 두 번 탈퇴해도 값이 겹치지 않고, 탈퇴 행에서 IdP 계정으로 되짚어 갈 길이 남지 않는다.
     * <p>
     * 동의 시각과 방침 버전, 프로필 완료 시각, 생성 시각은 개인 식별 정보가 아니라 남긴다. 탈퇴 행은 이력 용도이고
     * 물리 삭제는 별도 배치다.
     */
    void withdraw(Instant now) {
        this.providerUserId = WITHDRAWN_SUBJECT_PREFIX + id;
        this.email = null;
        this.name = null;
        this.birthDate = null;
        this.gender = null;
        this.region = null;
        this.nickname = null;
        this.profileImageUrl = null;
        this.updatedAt = now;
        this.deletedAt = now;
    }

    /** 음성 저장에 동의한다 (KAN-269). 철회했던 계정이 다시 동의하면 철회 시각을 지우고 새 버전과 시각으로 덮는다. */
    void consentToVoice(String version, Instant now) {
        this.voiceConsentVersion = version;
        this.voiceConsentAt = now;
        this.voiceConsentWithdrawnAt = null;
        this.updatedAt = now;
    }

    /** 음성 저장 동의를 철회한다 - 동의한 적이 없거나 이미 철회했으면 아무것도 바꾸지 않는다 (멱등). */
    void withdrawVoiceConsent(Instant now) {
        if (voiceConsentVersion == null || voiceConsentWithdrawnAt != null) {
            return;
        }
        this.voiceConsentWithdrawnAt = now;
        this.updatedAt = now;
    }

    /**
     * 지금 음성 저장 동의가 유효한가 - 동의했고, 철회하지 않았고, 탈퇴하지 않은 계정이다. 버전은 가르지 않는다:
     * 동의 문구가 바뀌어도 이미 받은 동의는 그 버전으로 유효하고, 어느 버전인지는 라벨에 남는다.
     */
    public boolean hasVoiceConsent() {
        return voiceConsentVersion != null && voiceConsentAt != null
                && voiceConsentWithdrawnAt == null && deletedAt == null;
    }

    private void markCompletionIfReady(Instant now) {
        if (profileCompletedAt == null && hasAllProfileFields()) {
            profileCompletedAt = now;
        }
    }

    private boolean hasAllProfileFields() {
        return email != null && name != null && birthDate != null && gender != null && region != null;
    }

    /** 다섯 항목이 다 있는가 (§3.9 {@code profileStatus}). */
    public boolean isProfileComplete() {
        return profileCompletedAt != null && hasAllProfileFields();
    }

    @PostPersist
    void markPersisted() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID id() {
        return id;
    }

    public Provider provider() {
        return provider;
    }

    public @Nullable String email() {
        return email;
    }

    public @Nullable String name() {
        return name;
    }

    public @Nullable LocalDate birthDate() {
        return birthDate;
    }

    public @Nullable Gender gender() {
        return gender;
    }

    /** 저장된 출신지역 코드 - 프로필 완료 전에는 null이다. */
    public @Nullable Region region() {
        return region != null ? Region.valueOf(region) : null;
    }

    public @Nullable String nickname() {
        return nickname;
    }

    public @Nullable String profileImageUrl() {
        return profileImageUrl;
    }

    public @Nullable Instant profileCompletedAt() {
        return profileCompletedAt;
    }

    public Instant privacyConsentAt() {
        return privacyConsentAt;
    }

    public String privacyPolicyVersion() {
        return privacyPolicyVersion;
    }

    public @Nullable String voiceConsentVersion() {
        return voiceConsentVersion;
    }

    public @Nullable Instant voiceConsentAt() {
        return voiceConsentAt;
    }

    /** IdP의 사용자 id - 탈퇴한 계정은 {@code deleted:<계정 id>}다. */
    public String providerUserId() {
        return providerUserId;
    }

    /** 탈퇴 시각 - 살아 있는 계정은 null이다 (KAN-241). */
    public @Nullable Instant deletedAt() {
        return deletedAt;
    }
}
