package com.zhixuetang.user.privacy;

import com.zhixuetang.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import com.zhixuetang.common.autoconfigure.mvc.advice.WrapperResponseBodyAdvice;
import com.zhixuetang.common.exceptions.UnauthorizedException;
import com.zhixuetang.user.service.impl.AccountPrivacyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses Spring's actual response handling together with the application's envelope advice. */
class PrivacyDownloadControllerTest {
    @Test
    void authenticatedDownloadStreamsARealZipWithoutAnEnvelope() throws Exception {
        var tasks = mock(PrivacyTaskService.class);
        var privacy = mock(AccountPrivacyService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new PrivacyTaskController(tasks, privacy))
                        .setControllerAdvice(
                                new WrapperResponseBodyAdvice(), new CommonExceptionAdvice())
                        .setMessageConverters(new JacksonJsonHttpMessageConverter())
                        .build();
        String id = "12345678-1234-1234-1234-123456789abc";
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(
                    "{\"schema\":\"zhixuetang-privacy-export-v1\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        when(tasks.download(id)).thenReturn(new ByteArrayInputStream(bytes.toByteArray()));
        var response =
                mvc.perform(
                                post("/account-privacy/tasks/" + id + "/download")
                                        .header("x-request-from", "gateway")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"password\":\"current-operator-password\"}"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType("application/zip"))
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn()
                        .getResponse();
        assertArrayEquals(bytes.toByteArray(), response.getContentAsByteArray());
        try (var zip =
                new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertEquals("manifest.json", zip.getNextEntry().getName());
        }
        var order = inOrder(privacy, tasks);
        order.verify(privacy).verify("current-operator-password");
        order.verify(tasks).download(id);
    }

    @Test
    void invalidCurrentPasswordDoesNotOpenOrStreamTheArchive() throws Exception {
        var tasks = mock(PrivacyTaskService.class);
        var privacy = mock(AccountPrivacyService.class);
        doThrow(new UnauthorizedException("当前密码验证失败")).when(privacy).verify("wrong");
        var mvc =
                MockMvcBuilders.standaloneSetup(new PrivacyTaskController(tasks, privacy))
                        .setControllerAdvice(
                                new WrapperResponseBodyAdvice(), new CommonExceptionAdvice())
                        .setMessageConverters(new JacksonJsonHttpMessageConverter())
                        .build();
        mvc.perform(
                        post("/account-privacy/tasks/12345678-1234-1234-1234-123456789abc/download")
                                .header("x-request-from", "gateway")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        verifyNoInteractions(tasks);
    }
}
