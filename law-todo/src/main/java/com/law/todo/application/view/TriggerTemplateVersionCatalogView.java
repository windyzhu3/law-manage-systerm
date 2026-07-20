package com.law.todo.application.view;

/** Minimal published-version identity exposed to trigger-rule editors. */
public record TriggerTemplateVersionCatalogView(Long versionId, Integer versionNo, String status) { }
