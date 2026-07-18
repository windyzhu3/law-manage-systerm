package com.ruoyi.web.controller.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.law.todo.application.TodoCalendarService;

class TodoCalendarControllerValidationTest
{
    @Test void rejectsCalendarUpdateWithoutExpectedVersion() throws Exception
    {
        LocalValidatorFactoryBean validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        TodoCalendarController controller=new TodoCalendarController(org.mockito.Mockito.mock(TodoCalendarService.class));
        MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build()
            .perform(put("/todo/calendar").contentType("application/json").content("""
                    {"calendarId":7,"calendarCode":"CN_DEFAULT","calendarName":"Default",
                     "timezone":"Asia/Shanghai","workDays":"1,2,3,4,5","workStart":"09:00",
                     "workEnd":"18:00","exceptionJson":"{}","status":"0","actionId":"calendar-no-version"}
                    """))
            .andExpect(status().isBadRequest());
    }
}
