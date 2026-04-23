package com.devtools.ui.core.endpoint;

import com.devtools.ui.core.collector.DevToolsCollector;
import com.devtools.ui.core.model.EndpointDescriptor;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class EndpointCollector implements DevToolsCollector<EndpointDescriptor> {

    private final List<RequestMappingInfoHandlerMapping> handlerMappings;

    public EndpointCollector(List<RequestMappingInfoHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    @Override
    public String id() {
        return "endpoints";
    }

    @Override
    public List<EndpointDescriptor> collect() {
        return handlerMappings.stream()
                .flatMap(handlerMapping -> handlerMapping.getHandlerMethods().entrySet().stream())
                .flatMap(this::expandMapping)
                .sorted(Comparator.comparing(EndpointDescriptor::path).thenComparing(EndpointDescriptor::method))
                .toList();
    }

    private Stream<EndpointDescriptor> expandMapping(java.util.Map.Entry<RequestMappingInfo, HandlerMethod> entry) {
        RequestMappingInfo mappingInfo = entry.getKey();
        HandlerMethod handlerMethod = entry.getValue();

        Set<String> paths = mappingInfo.getPatternValues().isEmpty()
                ? Set.of("/")
                : mappingInfo.getPatternValues();
        Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();

        if (methods.isEmpty()) {
            return paths.stream().map(path -> descriptor("ALL", path, handlerMethod));
        }

        return methods.stream().flatMap(method -> paths.stream().map(path -> descriptor(method.name(), path, handlerMethod)));
    }

    private EndpointDescriptor descriptor(String method, String path, HandlerMethod handlerMethod) {
        return new EndpointDescriptor(
                method,
                path,
                handlerMethod.getBeanType().getSimpleName(),
                handlerMethod.getMethod().getName()
        );
    }
}
