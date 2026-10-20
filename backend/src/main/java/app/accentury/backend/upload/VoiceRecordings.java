package app.accentury.backend.upload;

import app.accentury.backend.analysis.AnalysisDispatcher;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.testdefinition.TestDefinition;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 녹음 업로드 한 건의 검증 (§3.3) - 레벨테스트 업로드({@link VoiceUploadService})와 억양 학습 채점(KAN-267,
 * §3.19)이 같은 규칙을 쓴다. 검증 규칙이 두 곳에 갈라져 한쪽만 바뀌지 않게 여기 하나로 둔다.
 * <p>
 * 순서는 meta 파트(400), audio 파트 크기(413), WAV 규격(415), 길이(422)다. 길이의 정본은 클라이언트 신고값이
 * 아니라 WAV에서 계산한 값이다. 검증에 실패하면 이미 읽은 바이트를 0으로 덮고 던진다 - 사본의 파기는 우리 몫이다
 * (KAN-27). 성공하면 바이트의 소유권은 호출부로 넘어간다.
 */
public final class VoiceRecordings {

    /** 오디오 파트 상한 (§3.3) - multipart 설정과 같은 값이다. */
    static final long MAX_AUDIO_BYTES = 1_048_576;
    /** WAV 규격 (§3.3) - 16kHz, mono, 16-bit PCM. */
    static final int SAMPLE_RATE = 16_000;
    static final int CHANNELS = 1;
    static final int BITS_PER_SAMPLE = 16;

    private VoiceRecordings() {
    }

    /**
     * 검증을 통과한 녹음.
     *
     * @param audio      업로드 받은 WAV 바이트 그대로 - AI로 패스스루한다 (§4.1)
     * @param durationMs WAV에서 계산한 길이
     */
    public record Recording(byte[] audio, long durationMs) {
    }

    /** meta와 audio 파트를 검증해 녹음 하나로 만든다. 실패는 전부 {@link ApiException}이다. */
    public static Recording read(ObjectMapper objectMapper, @Nullable MultipartFile audio, @Nullable String metaJson) {
        VoiceUploadMeta.parse(objectMapper, metaJson);
        byte[] audioBytes = requireAudio(audio);
        boolean valid = false;
        try {
            WavAudio wav = WavAudio.parse(audioBytes);
            if (wav.sampleRate() != SAMPLE_RATE || wav.channels() != CHANNELS
                    || wav.bitsPerSample() != BITS_PER_SAMPLE) {
                throw new ApiException(ErrorCode.AUDIO_FORMAT_UNSUPPORTED);
            }
            // 상한은 전 문항 공통 상수다 - 앱의 자동 종료와 같은 값이어야 하므로 문항별로 두지 않는다.
            if (wav.durationMs() > TestDefinition.VOICE_MAX_DURATION_MS) {
                throw new ApiException(ErrorCode.AUDIO_TOO_LONG);
            }
            valid = true;
            return new Recording(audioBytes, wav.durationMs());
        } finally {
            if (!valid) {
                AnalysisDispatcher.AnalysisRequest.wipe(audioBytes);
            }
        }
    }

    private static byte[] requireAudio(@Nullable MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "audio 파트가 필요합니다.");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new ApiException(ErrorCode.AUDIO_TOO_LARGE);
        }
        try {
            return audio.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "audio 파트를 읽을 수 없습니다.");
        }
    }
}
