package com.ruoyi.web.controller.todo;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.law.todo.domain.TodoException;
import com.ruoyi.common.core.domain.AjaxResult;

@RestControllerAdvice
public class TodoExceptionAdvice
{
    @ExceptionHandler(TodoException.class) public AjaxResult handle(TodoException e){AjaxResult result=AjaxResult.error(e.getMessage());result.put("businessCode",e.getBusinessCode());return result;}
}
