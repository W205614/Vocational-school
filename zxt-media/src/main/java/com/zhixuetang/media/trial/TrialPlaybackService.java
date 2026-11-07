package com.zhixuetang.media.trial;

import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.dto.course.SectionInfoDTO;
import com.zhixuetang.common.exceptions.*;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.media.cloud.TrialMediaJobs;
import com.zhixuetang.media.domain.vo.VideoPlayVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@ConditionalOnExpression(
        "${zxt.local-storage.enabled:false} or (${zxt.cloud-media.enabled:false} and '${zxt.cloud-media.role:api}' == 'api')")
public class TrialPlaybackService {
    private final TrialMediaJobs jobs;
    private final CourseClient courses;
    private final byte[] key;

    public TrialPlaybackService(TrialMediaJobs jobs, CourseClient courses, Environment env) {
        this.jobs = jobs;
        this.courses = courses;
        String secret =
                env.getProperty(
                        "zxt.cloud-media.signing-key",
                        env.getProperty("ZXT_INTERNAL_TOKEN", System.getenv("ZXT_INTERNAL_TOKEN")));
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("Trial signing key must contain at least 32 bytes");
        key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public VideoPlayVO request(long section) {
        long owner = UserContext.requireUser();
        var allowed = section(section);
        var task = jobs.request(allowed.getMediaId(), seconds(allowed));
        var result = new VideoPlayVO();
        String state = task.get("state").toString();
        result.setTrial(true);
        result.setTaskId(task.get("id").toString());
        result.setState(state.equals("READY") || state.equals("FAILED") ? state : "PROCESSING");
        result.setErrorCode((String) task.get("error_code"));
        if (state.equals("READY")) {
            String id = task.get("id").toString();
            long expires = Instant.now().getEpochSecond() + 600;
            String url =
                    "/api/v2/services/media/trial-content/"
                            + id
                            + "?section="
                            + section
                            + "&owner="
                            + owner
                            + "&expires="
                            + expires
                            + "&signature="
                            + sign(id, section, owner, expires);
            result.setProvider("TRIAL");
            result.setPlayUrl(url);
            result.setSignature(url);
            result.setExpiresAt(expires * 1000);
        }
        return result;
    }

    public Map<String, Object> authorize(
            String id, long section, long owner, long expires, String signature) {
        long now = Instant.now().getEpochSecond();
        if (id == null
                || !id.matches("[0-9a-f]{64}")
                || section <= 0
                || owner <= 0
                || expires <= now
                || expires > now + 600
                || signature == null
                || !MessageDigest.isEqual(
                        sign(id, section, owner, expires).getBytes(StandardCharsets.US_ASCII),
                        signature.getBytes(StandardCharsets.US_ASCII)))
            throw new ForbiddenException("试看地址已过期或无效");
        var allowed = section(section);
        var source = jobs.source(allowed.getMediaId());
        if (!id.equals(
                TrialMediaJobs.identity(
                        allowed.getMediaId(),
                        source.get("file_id").toString(),
                        source.get("source_version").toString(),
                        seconds(allowed)))) throw new ForbiddenException("试看配置或源视频已变化");
        var task = jobs.task(id);
        if (!"READY".equals(task.get("state"))) throw new ForbiddenException("试看片段尚未就绪");
        return task;
    }

    private SectionInfoDTO section(long section) {
        if (section <= 0) throw new BadRequestException("视频小节无效");
        var info = courses.sectionInfo(section);
        if (info == null
                || info.getCourseId() == null
                || info.getMediaId() == null
                || !Boolean.TRUE.equals(info.getTrailer()))
            throw new ForbiddenException("当前小节不支持试看");
        var course = courses.getCourseInfoById(info.getCourseId(), false, false);
        if (course == null
                || course.getStatus() == null
                || !Set.of(2, 4).contains(course.getStatus()))
            throw new ForbiddenException("课程已下架，不能试看");
        return info;
    }

    private static int seconds(SectionInfoDTO section) {
        Integer minutes = section.getFreeDuration();
        if (minutes == null || minutes < 1 || minutes > 30)
            throw new BadRequestException("课程试看时长无效");
        return minutes * 60;
    }

    private String sign(String id, long section, long owner, long expires) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of()
                    .formatHex(
                            mac.doFinal(
                                    ("trial-v1\n"
                                                    + id
                                                    + "\n"
                                                    + section
                                                    + "\n"
                                                    + owner
                                                    + "\n"
                                                    + expires)
                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }
}
