package com.fastfood.order.config;

import com.fastfood.order.soap.contract.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.JdkHttpClientMessageSender;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class SoapClientConfig {

    @Bean
    Jaxb2Marshaller comprobantesMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(GenerarComprobanteRequest.class, GenerarComprobanteResponse.class,
                ConsultarComprobanteRequest.class, ConsultarComprobanteResponse.class);
        marshaller.setSchema(new ClassPathResource("soap/comprobantes-v1.xsd"));
        marshaller.setSupportDtd(false);
        marshaller.setProcessExternalEntities(false);
        return marshaller;
    }

    @Bean
    WebServiceTemplate comprobantesTemplate(Jaxb2Marshaller comprobantesMarshaller,
            @Value("${fastfood.soap.url}") String url) throws Exception {
        WebServiceTemplate template = new WebServiceTemplate(comprobantesMarshaller);
        template.setDefaultUri(url);
        JdkHttpClientMessageSender sender = new JdkHttpClientMessageSender(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        sender.setRequestTimeout(Duration.ofSeconds(3));
        sender.afterPropertiesSet();
        template.setMessageSender(sender);
        return template;
    }
}
