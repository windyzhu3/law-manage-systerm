package com.law.todo.application;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.law.todo.application.TodoConfigurationResourceCatalogService.DodRecipeResource;

/** Ranks completion recipes for the business context currently being configured. */
@Component
public class TodoDodRecipeMatcher
{
    public List<DodRecipeResource> match(List<DodRecipeResource> recipes,String businessType,
            String businessAction,String templateStage)
    {
        if(recipes==null||recipes.isEmpty())return List.of();
        return recipes.stream()
                .filter(recipe->eligible(recipe,businessType,businessAction,templateStage))
                .map(recipe->rank(recipe,businessType))
                .sorted(Comparator.comparingInt(RankedRecipe::actionSpecificity).reversed()
                        .thenComparing(Comparator.comparingInt(RankedRecipe::stageSpecificity).reversed())
                        .thenComparing(Comparator.comparingInt(RankedRecipe::businessSpecificity).reversed())
                        .thenComparing(Comparator.comparingInt(RankedRecipe::recommendationPriority).reversed())
                        .thenComparing(ranked->ranked.recipe().name(),Comparator.nullsLast(String::compareTo))
                        .thenComparing(ranked->ranked.recipe().code(),Comparator.nullsLast(String::compareTo)))
                .map(RankedRecipe::recipe)
                .toList();
    }

    private boolean eligible(DodRecipeResource recipe,String businessType,String businessAction,String templateStage)
    {
        return ("ALL".equals(recipe.businessType())||recipe.businessType().equals(businessType))
                &&(recipe.businessActions().isEmpty()||recipe.businessActions().contains(businessAction))
                &&(recipe.templateStages().isEmpty()||recipe.templateStages().contains(templateStage));
    }

    private RankedRecipe rank(DodRecipeResource recipe,String businessType)
    {
        return new RankedRecipe(recipe,recipe.businessActions().isEmpty()?0:1,
                recipe.templateStages().isEmpty()?0:1,recipe.businessType().equals(businessType)?1:0,
                recipe.recommendationPriority());
    }

    private record RankedRecipe(DodRecipeResource recipe,int actionSpecificity,int stageSpecificity,
            int businessSpecificity,int recommendationPriority) { }
}
