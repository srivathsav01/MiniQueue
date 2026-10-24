package io.github.srivathsav01.miniqueue.spring;

import io.github.srivathsav01.miniqueue.client.MessageHandler;
import io.github.srivathsav01.miniqueue.client.MiniQueueClient;
import io.github.srivathsav01.miniqueue.client.MiniQueueConsumer;
import io.github.srivathsav01.miniqueue.client.ReceivedMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Turns @MiniQueueListener methods into running MiniQueueConsumers. It has two jobs, at
 * two different moments of the application's life:
 *
 * 1. Discovery (BeanPostProcessor): as Spring creates each bean, look for annotated
 *    methods and remember them. Nothing is started yet, because other beans the listener
 *    needs (a DataSource, a mail client...) may not exist so far.
 *
 * 2. Start and stop (SmartLifecycle): once the whole context is ready, start one consumer
 *    per listener. On shutdown, stop them before the rest of the context is destroyed, so
 *    an in-flight handler never finds its DataSource already closed.
 *
 * This is the same split Spring Kafka uses for @KafkaListener.
 */
public class MiniQueueListenerRegistrar implements BeanPostProcessor, SmartLifecycle, EnvironmentAware {

    private static final Log log = LogFactory.getLog(MiniQueueListenerRegistrar.class);

    /** One annotated method on one bean. */
    private record Endpoint(Object bean, String beanName, Method method, MiniQueueListener annotation) {
    }

    private final ObjectProvider<MiniQueueClient> clientProvider;
    private final ObjectProvider<MiniQueueProperties> propertiesProvider;
    private final List<Endpoint> endpoints = new CopyOnWriteArrayList<>();
    private final List<MiniQueueConsumer> consumers = new CopyOnWriteArrayList<>();
    private Environment environment;
    private volatile boolean running;

    public MiniQueueListenerRegistrar(ObjectProvider<MiniQueueClient> clientProvider,
                                      ObjectProvider<MiniQueueProperties> propertiesProvider) {
        this.clientProvider = clientProvider;
        this.propertiesProvider = propertiesProvider;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    // ---------------------------------------------------------------------------------
    // 1. Discovery
    // ---------------------------------------------------------------------------------

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // If the bean is a proxy (for @Transactional, for example), inspect the real class.
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        if (!AnnotationUtils.isCandidateClass(targetClass, MiniQueueListener.class)) {
            return bean; // cheap pre-check: skips the many beans that cannot have the annotation
        }

        Map<Method, MiniQueueListener> annotated = MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<MiniQueueListener>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, MiniQueueListener.class));

        annotated.forEach((method, annotation) -> {
            validate(method, beanName);
            endpoints.add(new Endpoint(bean, beanName, method, annotation));
        });
        return bean; // we only observe beans, never replace them
    }

    /** Fails application startup with a clear message instead of failing on the first message. */
    private static void validate(Method method, String beanName) {
        String where = beanName + "." + method.getName();
        if (Modifier.isPrivate(method.getModifiers())) {
            throw new IllegalStateException("@MiniQueueListener method " + where
                    + " must not be private: a private method called on a Spring proxy skips the proxy"
                    + " and sees uninitialised fields");
        }
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != 1
                || (parameters[0] != String.class && parameters[0] != ReceivedMessage.class)) {
            throw new IllegalStateException("@MiniQueueListener method " + where
                    + " must take exactly one parameter, of type String (the payload) or ReceivedMessage");
        }
    }

    // ---------------------------------------------------------------------------------
    // 2. Start and stop
    // ---------------------------------------------------------------------------------

    @Override
    public void start() {
        MiniQueueClient client = clientProvider.getObject();
        MiniQueueProperties.Consumer defaults = propertiesProvider.getObject().getConsumer();
        try {
            for (Endpoint endpoint : endpoints) {
                consumers.add(startConsumer(endpoint, client, defaults));
            }
        } catch (RuntimeException startupFailure) {
            stop(); // do not leave the listeners that did start running in the background
            throw startupFailure;
        }
        running = true;
    }

    private MiniQueueConsumer startConsumer(Endpoint endpoint, MiniQueueClient client,
                                            MiniQueueProperties.Consumer defaults) {
        MiniQueueListener annotation = endpoint.annotation();
        String queue = environment.resolveRequiredPlaceholders(annotation.queue());
        int concurrency = annotation.concurrency() > 0 ? annotation.concurrency() : defaults.getConcurrency();

        MiniQueueConsumer consumer = MiniQueueConsumer
                .builder(client, queue, handlerFor(endpoint))
                .concurrency(concurrency)
                .pollInterval(defaults.getMinPollInterval(), defaults.getMaxPollInterval())
                .shutdownTimeout(defaults.getShutdownTimeout())
                .build()
                .start();

        log.info("@MiniQueueListener " + endpoint.beanName() + "." + endpoint.method().getName()
                + " consuming queue '" + queue + "' with " + concurrency + " worker(s)");
        return consumer;
    }

    /** Adapts the annotated method to the SDK's MessageHandler interface. */
    private static MessageHandler handlerFor(Endpoint endpoint) {
        // On a JDK-interface proxy, find the matching method on the proxy, so calls go through it
        // (and through @Transactional or other advice) rather than around it.
        Method invocable = AopUtils.selectInvocableMethod(endpoint.method(), endpoint.bean().getClass());
        ReflectionUtils.makeAccessible(invocable);
        boolean wantsPayloadOnly = endpoint.method().getParameterTypes()[0] == String.class;

        return message -> {
            Object argument = wantsPayloadOnly ? message.payload() : message;
            try {
                invocable.invoke(endpoint.bean(), argument);
            } catch (InvocationTargetException wrapped) {
                // Reflection wraps whatever the method threw. Unwrap it, so the consumer logs
                // and nacks for the user's real exception, not for a reflection wrapper.
                Throwable cause = wrapped.getTargetException();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw wrapped;
            }
        };
    }

    @Override
    public void stop() {
        for (MiniQueueConsumer consumer : consumers) {
            consumer.close(); // each waits for its in-flight messages, up to shutdownTimeout
        }
        consumers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return propertiesProvider.getObject().getConsumer().isAutoStartup();
    }

    // getPhase() keeps SmartLifecycle's default, the highest phase: listeners start after
    // every other lifecycle bean and stop before them.
}