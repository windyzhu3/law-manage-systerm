package com.ruoyi.system.service.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.law.todo.schedule.TodoScheduleService.ScheduleWindowRule;
import com.ruoyi.common.exception.ServiceException;

class CanonicalRetryWindowValidatorTest
{
    @Test
    void exactApprovedSevenWindowLifecycleIsAccepted()
    {
        assertEquals(List.of("T0","T1_AM","T1_NOON","T1_PM",
                "T2_AM","T2_NOON","T2_PM"),validator().validate(valid()).stream()
                        .map(ScheduleWindowRule::windowCode).toList());
    }

    @Test
    void missingReversedWrongDayDuplicateAndUnsupportedWindowsFailClosed()
    {
        List<ScheduleWindowRule> missing=new ArrayList<>(valid());
        missing.remove(3);
        assertThrows(ServiceException.class,()->validator().validate(missing));

        List<ScheduleWindowRule> reversed=new ArrayList<>(valid());
        reversed.set(0,clock("T2_PM",0,0));
        reversed.set(6,clock("T0",6,2));
        assertThrows(ServiceException.class,()->validator().validate(reversed));

        List<ScheduleWindowRule> wrongDay=new ArrayList<>(valid());
        wrongDay.set(1,clock("T1_AM",1,2));
        assertThrows(ServiceException.class,()->validator().validate(wrongDay));

        List<ScheduleWindowRule> duplicate=new ArrayList<>(valid());
        duplicate.set(2,clock("T1_AM",2,1));
        assertThrows(ServiceException.class,()->validator().validate(duplicate));

        List<ScheduleWindowRule> unsupported=new ArrayList<>(valid());
        unsupported.set(1,clock("VIP",1,1));
        assertThrows(ServiceException.class,()->validator().validate(unsupported));
    }

    private CanonicalRetryWindowValidator validator()
    {
        return new CanonicalRetryWindowValidator();
    }

    private List<ScheduleWindowRule> valid()
    {
        return List.of(relative("T0",0,0),clock("T1_AM",1,1),
                clock("T1_NOON",2,1),clock("T1_PM",3,1),
                clock("T2_AM",4,2),clock("T2_NOON",5,2),
                clock("T2_PM",6,2));
    }

    private ScheduleWindowRule relative(String code,int order,int day)
    {
        return new ScheduleWindowRule(code,order,day,null,null,0,120,3,1);
    }

    private ScheduleWindowRule clock(String code,int order,int day)
    {
        return new ScheduleWindowRule(code,order,day,LocalTime.of(9,0),
                LocalTime.of(11,0),null,null,1,1);
    }
}
