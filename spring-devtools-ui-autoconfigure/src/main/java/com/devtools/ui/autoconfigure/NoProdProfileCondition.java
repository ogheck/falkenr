package com.devtools.ui.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.env.Profiles;

public class NoProdProfileCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean prodActive = context.getEnvironment().acceptsProfiles(Profiles.of("prod"));

        if (prodActive) {
            return ConditionOutcome.noMatch(
                    ConditionMessage.forCondition("Falkenr").because("the prod profile is active")
            );
        }

        return ConditionOutcome.match(
                ConditionMessage.forCondition("Falkenr").because("no prod profile is active")
        );
    }
}
