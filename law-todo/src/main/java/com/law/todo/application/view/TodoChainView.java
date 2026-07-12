package com.law.todo.application.view;

import java.util.List;
import java.util.Map;

public record TodoChainView(Long rootTodoId,List<Map<String,Object>> nodes) {}
