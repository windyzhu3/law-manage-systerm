package com.ruoyi.system.service.lead;

import java.util.List;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.todo.schedule.TodoScheduleService.ScheduleWindowRule;
import com.ruoyi.common.exception.ServiceException;

/**
 * Single fail-closed definition of the approved Lead retry lifecycle.
 * Persisted/imported rules and API commands pass through this same validator.
 */
public class CanonicalRetryWindowValidator
{
    private static final List<Identity> APPROVED=List.of(
            new Identity("T0",0,0,true),
            new Identity("T1_AM",1,1,false),
            new Identity("T1_NOON",2,1,false),
            new Identity("T1_PM",3,1,false),
            new Identity("T2_AM",4,2,false),
            new Identity("T2_NOON",5,2,false),
            new Identity("T2_PM",6,2,false));

    public List<ScheduleWindowRule> validate(List<ScheduleWindowRule> windows)
    {
        require(windows!=null&&windows.size()==APPROVED.size(),
                "Retry policy must contain the approved seven windows");
        for(int index=0;index<APPROVED.size();index++)
        {
            Identity expected=APPROVED.get(index);
            ScheduleWindowRule actual=windows.get(index);
            require(actual!=null&&expected.code().equals(actual.windowCode())
                    &&actual.windowOrder()==expected.order()
                    &&actual.dayOffset()==expected.dayOffset(),
                    "Retry window code, order, or day offset is invalid");
            require(actual.maxAttempts()>0&&actual.occurrenceNo()>0,
                    "Retry window attempts and occurrence must be positive");
            boolean relative=actual.startOffsetMinutes()!=null||actual.durationMinutes()!=null;
            boolean clock=actual.startTime()!=null||actual.endTime()!=null;
            require(relative!=clock&&relative==expected.relative(),
                    "Retry window time mode is invalid");
            if(relative)
            {
                require(actual.startOffsetMinutes()!=null&&actual.startOffsetMinutes()>=0
                        &&actual.durationMinutes()!=null&&actual.durationMinutes()>0
                        &&actual.startTime()==null&&actual.endTime()==null,
                        "Relative retry window is invalid");
            }
            else require(actual.startTime()!=null&&actual.endTime()!=null
                    &&actual.endTime().isAfter(actual.startTime())
                    &&actual.startOffsetMinutes()==null&&actual.durationMinutes()==null,
                    "Clock retry window is invalid");
        }
        return List.copyOf(windows);
    }

    private void require(boolean condition,String message)
    {
        if(!condition)throw new ServiceException(message,
                BusinessErrorCode.PRECONDITION_FAILED.name());
    }

    private record Identity(String code,int order,int dayOffset,boolean relative) { }
}
