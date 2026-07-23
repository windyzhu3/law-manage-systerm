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
                .filter(recipe->matchesBusinessType(recipe,businessType))
                .map(recipe->new RankedRecipe(recipe,score(recipe,businessAction,templateStage)))
                .filter(ranked->ranked.score()>=0)
                .sorted(Comparator.comparingInt(RankedRecipe::score).reversed()
                        .thenComparing(ranked->ranked.recipe().name(),Comparator.nullsLast(String::compareTo))
                        .thenComparing(ranked->ranked.recipe().code(),Comparator.nullsLast(String::compareTo)))
                .map(RankedRecipe::recipe)
                .toList();
    }

    private boolean matchesBusinessType(DodRecipeResource recipe,String businessType)
    {
        return "ALL".equals(recipe.businessType())||recipe.businessType().equals(businessType);
    }

    private int score(DodRecipeResource recipe,String action,String stage)
    {
        int score=recipe.businessActions().isEmpty()?0:recipe.businessActions().contains(action)?100:-1000;
        score+=recipe.templateStages().isEmpty()?0:recipe.templateStages().contains(stage)?20:-1000;
        return score+recipe.recommendationPriority();
    }

    private record RankedRecipe(DodRecipeResource recipe,int score) { }
}
