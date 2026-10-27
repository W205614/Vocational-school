package com.tianji.user.security;

import com.tianji.user.controller.StudentController;
import com.tianji.user.service.IStudentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;

class LegacyStudentPasswordRouteTest {
    @Test void retiredPasswordRouteHasNoControllerEvenWithMissingOrForgedVerification()throws Exception{
        var students=mock(IStudentService.class);var controller=new StudentController();ReflectionTestUtils.setField(controller,"studentService",students);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        for(String proof:new String[]{"",",\"code\":null",",\"code\":\"\"",",\"code\":\"1234\""})
            mvc.perform(put("/students/password").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cellPhone\":\"13800010001\",\"password\":\"replacement-password\""+proof+"}"))
                    .andExpect(status().isNotFound());
        verifyNoInteractions(students);
    }
}
