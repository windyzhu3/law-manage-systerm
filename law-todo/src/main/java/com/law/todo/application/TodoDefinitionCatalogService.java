package com.law.todo.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessValidator;
import com.law.todo.spi.TodoCompletionHandler;

/** Read-only discovery surface for definition tooling. */
@Service
public class TodoDefinitionCatalogService
{
    private final TodoMapper mapper;
    private final List<TodoCompletionHandler> handlers;
    private final List<TodoBusinessValidator> validators;

    @Autowired
    public TodoDefinitionCatalogService(TodoMapper mapper,List<TodoCompletionHandler> handlers,List<TodoBusinessValidator> validators)
    {this.mapper=mapper;this.handlers=handlers==null?List.of():List.copyOf(handlers);this.validators=validators==null?List.of():List.copyOf(validators);}
    public TodoDefinitionCatalogService(TodoMapper mapper){this(mapper,List.of(),List.of());}

    @Transactional(readOnly=true) public List<Map<String,Object>> events(){return immutableRows(mapper.selectEventCatalogs(),Comparator.comparing((Map<String,Object> row)->text(row.get("event_type")),Comparator.nullsFirst(String::compareTo)).thenComparingInt(row->integer(row.get("payload_version"))));}
    @Transactional(readOnly=true) public List<Map<String,Object>> decisions(){return immutableRows(mapper.selectDecisions(),Comparator.comparing(row->text(row.get("decision_code")),Comparator.nullsFirst(String::compareTo)));}
    public List<HandlerCatalogEntry> handlers(){return handlers.stream().map(handler->new HandlerCatalogEntry(handler.catalogCode(),handler.getClass().getName(),handler.supportsSimulation(),handler.simulationDescription())).sorted(Comparator.comparing(HandlerCatalogEntry::code)).toList();}
    public List<ValidatorCatalogEntry> validators(){return validators.stream().map(validator->new ValidatorCatalogEntry(
            validator.catalogCode(),validator.catalogName(),validator.getClass().getName(),validator.catalogDescription(),
            List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER").stream().filter(validator::supports).toList(),
            validator.parameterSchemaJson(),validator.exampleParametersJson())).sorted(Comparator.comparing(ValidatorCatalogEntry::code)).toList();}
    private List<Map<String,Object>> immutableRows(List<Map<String,Object>> rows,Comparator<Map<String,Object>> comparator){if(rows==null)return List.of();return rows.stream().map(row->java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row))).sorted(comparator).toList();}
    private String text(Object value){return value==null?null:String.valueOf(value);}private int integer(Object value){try{return value==null?0:Integer.parseInt(String.valueOf(value));}catch(NumberFormatException invalid){return 0;}}
    public record HandlerCatalogEntry(String code,String implementation,boolean simulatable,String description) { }
    public record ValidatorCatalogEntry(String code,String name,String implementation,String description,List<String> businessTypes,
            String parameterSchemaJson,String exampleParametersJson) { }
}
