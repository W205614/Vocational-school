package com.tianji.compact;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.context.ApplicationContext;
import java.util.LinkedHashSet;
import java.util.List;

/** Child refresh is not SpringApplication.run: runners must be invoked explicitly. */
final class ModuleStartup {
    private ModuleStartup() {}

    static void run(List<ApplicationContext> contexts, ApplicationArguments arguments) throws Exception {
        var runners = new LinkedHashSet<Object>();
        for (var context : contexts) {
            runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
            runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());
        }
        var ordered = new java.util.ArrayList<>(runners);
        AnnotationAwareOrderComparator.sort(ordered);
        for (var runner : ordered) {
            if (runner instanceof ApplicationRunner application) application.run(arguments);
            if (runner instanceof CommandLineRunner command) command.run(arguments.getSourceArgs());
        }
    }
}
