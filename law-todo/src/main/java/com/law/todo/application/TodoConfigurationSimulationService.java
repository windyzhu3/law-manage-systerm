package com.law.todo.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.view.TodoConfigurationViews.ConfigurationSimulationResult;
import com.law.todo.application.view.TodoSimulationView;

/**
 * Configuration simulation boundary. It delegates to the pure definition simulator and only writes a
 * redacted audit record; it deliberately has no runtime Todo or organization mutation dependency.
 */
@Service
public class TodoConfigurationSimulationService
{
    private final TodoDefinitionSimulationService definitions;
    private final TodoConfigurationSimulationAuditService audits;

    public TodoConfigurationSimulationService(TodoDefinitionSimulationService definitions,TodoConfigurationSimulationAuditService audits)
    {this.definitions=definitions;this.audits=audits;}

    public ConfigurationSimulationResult simulate(ConfigurationSimulationCommand command,Actor actor)
    {
        long started=System.nanoTime();
        TodoSimulationView simulation;
        try
        {
            simulation=definitions.simulate(command.versionId(),command.toDefinitionCommand(),command.eventType());
        }
        catch(RuntimeException failure)
        {
            try{audits.record(command,actor,duration(started),failedResult(command,failure));}
            catch(RuntimeException auditFailure){failure.addSuppressed(auditFailure);}
            throw failure;
        }
        long duration=duration(started);
        audits.record(command,actor,duration,orderedResult(simulation));
        return new ConfigurationSimulationResult(simulation,duration);
    }

    /** Keeps persisted result sections in the configured reader order. */
    private Map<String,Object> orderedResult(TodoSimulationView simulation)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        Map<String,Object> state=new LinkedHashMap<>();state.put("status",simulation.trigger().status());
        state.put("eventType",simulation.trigger().eventType());state.put("trace",simulation.trigger().trace());
        Map<String,Object> template=new LinkedHashMap<>();template.put("versionId",simulation.versionId());
        template.put("definitionHash",simulation.definitionHash());
        result.put("state",state);result.put("template",template);
        result.put("owner",simulation.owner());result.put("sla",simulation.sla());result.put("dod",simulation.form().dod());
        result.put("route",simulation.routes());result.put("card",simulation.form().ui());
        result.put("log",Map.of("autoActions",simulation.autoActions(),"handlers",simulation.handlers(),"issues",simulation.issues()));
        return result;
    }

    private Map<String,Object> failedResult(ConfigurationSimulationCommand command,RuntimeException failure)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("state",Map.of("status","FAILED"));
        result.put("template",Map.of("versionId",command.versionId()));result.put("owner",Map.of());result.put("sla",Map.of());
        result.put("dod",Map.of());result.put("route",List.of());result.put("card",Map.of());
        result.put("log",Map.of("errorType",failure.getClass().getSimpleName(),"message",message(failure)));
        return result;
    }

    private long duration(long started){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);}
    private String message(RuntimeException failure){return failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();}
}
