package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoBusinessDirectoryAccess;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationBusinessObjectDirectoryTest
{
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoBusinessDirectoryAccess directory;
    private final Actor actor=new Actor(7L,"operator",2L);

    @Test void allFiveTypesOfferClearlyMarkedSamplesWhenThereIsNoBusinessData()
    {
        for(String type:List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER"))
        {
            when(directory.supports(type)).thenReturn(true);
            when(directory.search(type,null,0,20,actor)).thenReturn(new TodoBusinessDirectoryAccess.DirectoryPage(
                    List.of(),0,"NO_DATA","BUSINESS_DATA"));
            var page=service().businessObjects(type,null,1,20,actor);
            assertEquals("NO_DATA",page.emptyReason());
            assertTrue(page.sampleFallback());
            assertTrue(page.rows().get(0).sample());
            assertEquals("SAMPLE",page.rows().get(0).source());
        }
    }

    @Test void noPermissionIsDiagnosedWithoutPretendingThereIsNoData()
    {
        when(directory.supports("CASE")).thenReturn(true);
        when(directory.search("CASE",null,0,20,actor)).thenReturn(new TodoBusinessDirectoryAccess.DirectoryPage(
                List.of(),0,"NO_PERMISSION","BUSINESS_DATA"));

        var page=service().businessObjects("CASE",null,1,20,actor);

        assertEquals("NO_PERMISSION",page.emptyReason());
        assertFalse(page.sampleFallback());
        assertTrue(page.rows().isEmpty());
    }

    @Test void sampleIdsAreAcceptedOnlyByTheSimulationLookup()
    {
        TodoException error=assertThrows(TodoException.class,()->service().requireBusinessObject("LEAD",-1001L,actor));
        var sample=service().requireSimulationBusinessObject("LEAD",-1001L,actor);

        assertEquals("TODO_SAMPLE_BUSINESS_OBJECT_NOT_ALLOWED",error.getBusinessCode());
        assertTrue(sample.sample());
    }

    private TodoConfigurationQueryService service()
    {return new TodoConfigurationQueryService(mapper,List.of(directory),new TodoSimulationSampleCatalog());}
}
